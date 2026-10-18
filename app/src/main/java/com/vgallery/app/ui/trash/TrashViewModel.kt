package com.vgallery.app.ui.trash

import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vgallery.app.data.gallery.DeleteResult
import com.vgallery.app.data.gallery.DeviceGalleryRepository
import com.vgallery.app.data.gallery.TrashedItem
import com.vgallery.app.data.gallery.expiredTrashUris
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val repository: DeviceGalleryRepository
) : ViewModel() {

    private val _trashed = MutableStateFlow<List<TrashedItem>>(emptyList())
    val trashed: StateFlow<List<TrashedItem>> = _trashed.asStateFlow()

    private val _consentRequests = MutableSharedFlow<IntentSender>(extraBufferCapacity = 1)
    val consentRequests: SharedFlow<IntentSender> = _consentRequests.asSharedFlow()
    private var consentGate: CompletableDeferred<Boolean>? = null

    init {
        viewModelScope.launch {
            repository.trashedMedia.collect { _trashed.value = it }
        }
        // Auto-purge on open: items past the 30-day TTL are permanently deleted.
        viewModelScope.launch { autoPurgeExpired() }
    }

    fun answerConsent(granted: Boolean) {
        consentGate?.complete(granted)
        consentGate = null
    }

    /** Restores items from Trash: removes them from the list (files were never touched). */
    fun restore(uris: List<String>, onDone: (Boolean, String) -> Unit) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val ok = repository.restoreTrashedMedia(uris)
            val label = if (uris.size > 1) "Restored ${uris.size} items" else "Restored"
            onDone(ok, if (ok) label else "Restore failed")
        }
    }

    /** Permanently deletes items from Trash (consent dialogs on Android 10+). */
    fun deleteForever(uris: List<Uri>, onDone: (Boolean, String) -> Unit) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            var remaining = uris
            repeat(uris.size + 2) {
                when (val result = repository.deleteMedia(remaining)) {
                    is DeleteResult.Deleted -> {
                        repository.removeTrashedMedia(remaining.map { it.toString() })
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

    /** Empties the entire Trash (every item is permanently deleted). */
    fun emptyTrash(onDone: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val items = _trashed.value
            if (items.isEmpty()) {
                onDone(true, "Trash is empty")
                return@launch
            }
            deleteForever(items.map { Uri.parse(it.uri) }) { ok, msg ->
                onDone(ok, if (ok) "Trash emptied" else msg)
            }
        }
    }

    /** Permanently deletes items whose 30-day TTL has elapsed (called on screen open). */
    private suspend fun autoPurgeExpired() {
        val items = _trashed.value
        val expired = expiredTrashUris(items, System.currentTimeMillis())
        if (expired.isEmpty()) return
        deleteForever(expired.map { Uri.parse(it) }) { _, _ -> }
    }
}
