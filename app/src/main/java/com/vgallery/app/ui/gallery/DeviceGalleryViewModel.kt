package com.vgallery.app.ui.gallery

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vgallery.app.data.gallery.DeviceAlbum
import com.vgallery.app.data.gallery.DeviceFolderTarget
import com.vgallery.app.data.gallery.DeviceGalleryRepository
import com.vgallery.app.data.gallery.DevicePhotoRow
import com.vgallery.app.data.gallery.HomeTab
import com.vgallery.app.data.gallery.MoveResult
import com.vgallery.app.data.gallery.TrashedItem
import com.vgallery.app.data.gallery.groupIntoAlbums
import com.vgallery.app.data.gallery.inTab
import com.vgallery.app.data.gallery.sortAlbumsForHome
import com.vgallery.app.data.gallery.sortDevicePhotos
import com.vgallery.app.data.preferences.SortOrder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeviceGalleryUiState(
    val albums: List<DeviceAlbum> = emptyList(),
    val photos: List<DevicePhotoRow> = emptyList(),
    /** null = album list (folder-wise); non-null = inside one folder. */
    val selectedAlbum: String? = null,
    /** Home tab: Photos (still images) or Videos (videos, GIFs, animated WebPs). */
    val selectedTab: HomeTab = HomeTab.PHOTOS,
    val isLoading: Boolean = true,
    val hasPermission: Boolean = false,
    /** Items across the selected tab's albums. */
    val totalCount: Int = 0,
    /** Folders detected on the device but never opened yet: the home green dot. */
    val newFolders: Set<String> = emptySet(),
    /** Pull-to-refresh spinner (content stays visible underneath). */
    val isRefreshing: Boolean = false,
    /** Name filter inside the open album. */
    val searchQuery: String = ""
)

@HiltViewModel
class DeviceGalleryViewModel @Inject constructor(
    private val repository: DeviceGalleryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceGalleryUiState())
    val uiState: StateFlow<DeviceGalleryUiState> = _uiState.asStateFlow()

    // No silent background check on launch: the app is fully offline.

    fun requiredPermissions(): Array<String> = repository.requiredPermissions()

    /** Called on screen entry and after the permission dialog resolves. */
    fun refresh(hasPermission: Boolean) {
        _uiState.update { it.copy(hasPermission = hasPermission) }
        if (!hasPermission) {
            _uiState.update { it.copy(isLoading = false, albums = emptyList(), photos = emptyList()) }
            return
        }
        loadAlbums()
    }

    fun loadAlbums() {
        // Stale-while-revalidate: a scan that already has folders must not flip isLoading, or the
        // home grid is replaced by a spinner and its scroll position is lost on the way back.
        val keepVisible = _uiState.value.albums.isNotEmpty() || repository.cachedSnapshot().isNotEmpty()
        _uiState.update { it.copy(isLoading = !keepVisible, isRefreshing = false) }
        viewModelScope.launch { reload() }
    }

    /** Pull-to-refresh: picks up new photos/folders without the full loading screen. */
    fun pullRefresh() {
        val state = _uiState.value
        if (state.isLoading || state.isRefreshing || !state.hasPermission) return
        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch { reload() }
    }

    private suspend fun reload() {
        val rows = repository.loadAllPhotos(fresh = true)
        val newFolders = repository.newFolderNames()
        _uiState.update { state ->
            derive(
                state.copy(
                    isLoading = false,
                    isRefreshing = false,
                    // A scan that found nothing because access was revoked must show the prompt,
                    // not an empty gallery.
                    hasPermission = repository.hasPermission(),
                    newFolders = newFolders
                ),
                rows
            )
        }
        // Paint first, sniff later: animated-WebP flags upgrade silently in background.
        refreshAnimatedFlags()
    }

    private suspend fun refreshAnimatedFlags() {
        // An empty cache means the scan did not publish (permission just went away, or a delete
        // reset it): deriving the gallery from it would blank every folder for a frame.
        val snapshot = repository.cachedSnapshot()
        if (snapshot.isEmpty()) return
        val refreshed = repository.sniffAnimatedWebps(snapshot)
        // Nothing new to flag: keep the state as it is instead of deriving an identical one.
        if (refreshed === snapshot) return
        _uiState.update { derive(it, refreshed) }
    }

    /**
     * Albums and open-folder rows for [state]'s tab, derived from [rows].
     *
     * The tab filters [rows] first, so a folder only ever lists its own tab's media:
     * Videos = videos + GIFs + animated WebPs, Photos = still images.
     */
    private fun derive(state: DeviceGalleryUiState, rows: List<DevicePhotoRow>): DeviceGalleryUiState {
        val tabRows = rows.filter { it.inTab(state.selectedTab) }
        // Home folders: new ones first, then newest date added (the green dot comes from state).
        val albums = sortAlbumsForHome(groupIntoAlbums(tabRows), state.newFolders)
        val selected = state.selectedAlbum
        return state.copy(
            albums = albums,
            totalCount = albums.sumOf { it.count },
            photos = if (selected == null) {
                emptyList()
            } else {
                // Inside a folder the items are always A–Z, whatever the viewer's sort setting is.
                sortDevicePhotos(
                    tabRows.filter { row ->
                        row.bucketName.ifBlank { "Unknown" } == selected ||
                            selected == DeviceGalleryRepository.ALL_PHOTOS_ALBUM
                    },
                    SortOrder.NAME_ASC
                )
            }
        )
    }

    fun openAlbum(name: String?) {
        if (name == null) {
            // Back to the folders: re-order now that an opened folder may have lost its flag.
            _uiState.update {
                derive(
                    it.copy(selectedAlbum = null, photos = emptyList(), searchQuery = ""),
                    repository.cachedSnapshot()
                )
            }
            return
        }
        // Cache is warm after the first scan: open instantly, no loading flash.
        val instant = _uiState.value.albums.isNotEmpty()
        // Opening a folder clears its "new" dot: drop it from state now, persist in the background.
        _uiState.update {
            it.copy(
                selectedAlbum = name,
                isLoading = !instant,
                searchQuery = "",
                newFolders = it.newFolders - name
            )
        }
        viewModelScope.launch {
            repository.markFolderOpened(name)
            val rows = repository.loadAllPhotos(fresh = false)
            _uiState.update { derive(it.copy(isLoading = false), rows) }
        }
    }

    /** Switches the home tab; albums and any open folder re-derive from the cached scan. */
    fun selectTab(tab: HomeTab) {
        if (_uiState.value.selectedTab == tab) return
        _uiState.update {
            derive(
                it.copy(selectedTab = tab, selectedAlbum = null, searchQuery = ""),
                repository.cachedSnapshot()
            )
        }
    }

    fun backToAlbums() = openAlbum(null)

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    /** Photos of the open album matching the search box (empty query = everything). */
    fun visiblePhotos(photos: List<DevicePhotoRow>, query: String): List<DevicePhotoRow> =
        if (query.isBlank()) photos
        else photos.filter { it.name.contains(query.trim(), ignoreCase = true) }

    /** Rescans after a consent grant or a viewer delete. */
    fun refreshAfterDelete() {
        viewModelScope.launch { reload() }
    }

    /**
     * Moves items to the Trash (Google-Photos style): the files stay on disk and are
     * simply hidden from every scan, so this needs no MediaStore write permission and no
     * consent dialog — which is what used to fail with "Permission denied". The items
     * can be restored from the Trash screen until they are permanently deleted.
     */
    fun moveToTrash(rows: List<DevicePhotoRow>, onDone: (Boolean, String) -> Unit) {
        if (rows.isEmpty()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val items = rows.map {
                TrashedItem(it.uri.toString(), it.name, it.mimeType, now)
            }
            val ok = repository.trashMedia(items)
            reload()
            val label = if (rows.size > 1) "Moved ${rows.size} items to trash" else "Moved to trash"
            onDone(ok, if (ok) label else "Couldn't move to trash")
        }
    }

    // ── Move to folder ────────────────────────────────────────────────────────────────

    /**
     * Write-consent dialogs surface through [consentRequests]; the screen launches them and
     * answers via [answerConsent]. Android 10+ needs consent to move media the app doesn't own.
     */
    private val _consentRequests = MutableSharedFlow<IntentSender>(extraBufferCapacity = 1)
    val consentRequests: SharedFlow<IntentSender> = _consentRequests.asSharedFlow()
    private var consentGate: CompletableDeferred<Boolean>? = null

    fun answerConsent(granted: Boolean) {
        consentGate?.complete(granted)
        consentGate = null
    }

    /** Loads the folders a move can target (queries MediaStore, so it's async). */
    fun loadMoveTargets(onReady: (List<DeviceFolderTarget>) -> Unit) {
        viewModelScope.launch { onReady(repository.folderTargets()) }
    }

    /**
     * Moves [rows] into [target]. A move rewrites MediaStore's RELATIVE_PATH, so other apps'
     * media needs one write-consent dialog on Android 10+; consent surfaces on
     * [consentRequests] and the retry happens through [answerConsent].
     */
    fun moveToFolder(
        rows: List<DevicePhotoRow>,
        target: DeviceFolderTarget,
        onDone: (Boolean, String) -> Unit
    ) {
        if (rows.isEmpty()) return
        val label = if (rows.size > 1) "Moved ${rows.size} items to ${target.displayName}"
        else "Moved to ${target.displayName}"
        viewModelScope.launch {
            var pending = rows.map { it.uri }
            repeat(3) {
                when (val result = repository.moveMedia(pending, target)) {
                    is MoveResult.Moved -> {
                        reload()
                        onDone(true, label)
                        return@launch
                    }
                    is MoveResult.NeedsConsent -> {
                        val gate = CompletableDeferred<Boolean>()
                        consentGate = gate
                        _consentRequests.emit(result.intentSender)
                        if (!gate.await()) {
                            onDone(false, "Move cancelled")
                            return@launch
                        }
                        pending = result.pending
                    }
                    is MoveResult.Failed -> {
                        onDone(false, result.message)
                        return@launch
                    }
                }
            }
            onDone(false, "Move failed")
        }
    }

    /** Rows inside the given folders for the selected tab (for folder share/info/delete). */
    fun photosInFolders(names: Set<String>): List<DevicePhotoRow> {
        if (names.isEmpty()) return emptyList()
        val normalized = names.map { it.ifBlank { "Unknown" } }.toSet()
        val tab = _uiState.value.selectedTab
        return repository.cachedSnapshot()
            .filter { it.bucketName.ifBlank { "Unknown" } in normalized && it.inTab(tab) }
    }
}
