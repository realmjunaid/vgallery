package com.vgallery.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vgallery.app.data.gallery.AUTOPLAY_DEFAULT_SECONDS
import com.vgallery.app.data.preferences.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The Library and Playback sections' state: home's folder grouping and how long autoplay holds each
 * photo. Written to the same store the gallery reads, so a choice here is in force the moment the
 * gallery is looked at again.
 */
@HiltViewModel
class LibrarySettingsViewModel @Inject constructor(
    private val preferences: AppPreferences
) : ViewModel() {

    val groupSimilarFolders: StateFlow<Boolean> = preferences.settings
        .map { it.groupSimilarFolders }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val autoplaySeconds: StateFlow<Int> = preferences.settings
        .map { it.autoAdvanceDelaySeconds }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AUTOPLAY_DEFAULT_SECONDS)

    fun setGroupSimilarFolders(value: Boolean) {
        viewModelScope.launch { preferences.updateGroupSimilarFolders(value) }
    }

    fun setAutoplaySeconds(seconds: Int) {
        viewModelScope.launch { preferences.updateAutoAdvanceDelay(seconds) }
    }
}
