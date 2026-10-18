package com.vgallery.app.ui.gallery

import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vgallery.app.data.gallery.DeleteResult
import com.vgallery.app.data.gallery.DeviceAlbum
import com.vgallery.app.data.gallery.DeviceGalleryRepository
import com.vgallery.app.data.gallery.DevicePhotoRow
import com.vgallery.app.data.gallery.HomeTab
import com.vgallery.app.data.gallery.groupIntoAlbums
import com.vgallery.app.data.gallery.inTab
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
        _uiState.update { it.copy(isLoading = true, isRefreshing = false) }
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
        _uiState.update {
            derive(
                it.copy(isLoading = false, isRefreshing = false, hasPermission = true),
                rows
            )
        }
        // Paint first, sniff later: animated-WebP flags upgrade silently in background.
        refreshAnimatedFlags()
    }

    private suspend fun refreshAnimatedFlags() {
        val refreshed = repository.sniffAnimatedWebps(repository.cachedSnapshot())
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
        val albums = groupIntoAlbums(tabRows)
        val selected = state.selectedAlbum
        return state.copy(
            albums = albums,
            totalCount = albums.sumOf { it.count },
            photos = if (selected == null) {
                emptyList()
            } else {
                tabRows.filter { row ->
                    row.bucketName.ifBlank { "Unknown" } == selected ||
                        selected == DeviceGalleryRepository.ALL_PHOTOS_ALBUM
                }
            }
        )
    }

    fun openAlbum(name: String?) {
        if (name == null) {
            _uiState.update { it.copy(selectedAlbum = null, photos = emptyList(), searchQuery = "") }
            return
        }
        // Cache is warm after the first scan: open instantly, no loading flash.
        val instant = _uiState.value.albums.isNotEmpty()
        _uiState.update { it.copy(selectedAlbum = name, isLoading = !instant, searchQuery = "") }
        viewModelScope.launch {
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

    /**
     * Deletes items (single or batch). System consent dialogs surface through
     * [consentRequests]; the screen launches them and answers via [answerConsent].
     * [onDone] receives true when everything is gone.
     */
    private val _consentRequests = MutableSharedFlow<IntentSender>(extraBufferCapacity = 1)
    val consentRequests: SharedFlow<IntentSender> = _consentRequests.asSharedFlow()
    private var consentGate: CompletableDeferred<Boolean>? = null

    fun answerConsent(granted: Boolean) {
        consentGate?.complete(granted)
        consentGate = null
    }

    fun requestDelete(uris: List<Uri>, onDone: (Boolean, String) -> Unit) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            var remaining = uris
            repeat(uris.size + 2) {
                when (val result = repository.deleteMedia(remaining)) {
                    is DeleteResult.Deleted -> {
                        reload()
                        val label = if (uris.size > 1) "Deleted ${uris.size} items" else "Deleted"
                        onDone(true, label)
                        return@launch
                    }
                    is DeleteResult.NeedsConsent -> {
                        val gate = CompletableDeferred<Boolean>()
                        consentGate = gate
                        _consentRequests.emit(result.intentSender)
                        if (!gate.await()) {
                            onDone(false, "Delete cancelled")
                            return@launch
                        }
                        remaining = result.pending
                    }
                    is DeleteResult.Failed -> {
                        onDone(false, result.message)
                        return@launch
                    }
                }
            }
            onDone(false, "Delete failed")
        }
    }

    /** Rescans after a consent grant or a viewer delete. */
    fun refreshAfterDelete() {
        viewModelScope.launch { reload() }
    }

    /** Rows inside the given folders for the selected tab (for folder share/info/delete). */
    fun photosInFolders(names: Set<String>): List<DevicePhotoRow> {
        if (names.isEmpty()) return emptyList()
        val normalized = names.map { it.ifBlank { "Unknown" } }.toSet()
        val tab = _uiState.value.selectedTab
        return repository.cachedSnapshot()
            .filter { it.bucketName.ifBlank { "Unknown" } in normalized && it.inTab(tab) }
    }

    /** Name search across every folder of the selected tab (home search icon). */
    fun searchAll(query: String): List<DevicePhotoRow> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val tab = _uiState.value.selectedTab
        return repository.cachedSnapshot()
            .filter { it.name.contains(q, ignoreCase = true) && it.inTab(tab) }
    }
}
