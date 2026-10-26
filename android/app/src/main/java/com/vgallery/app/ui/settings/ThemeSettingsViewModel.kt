package com.vgallery.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vgallery.app.data.preferences.AccentChoice
import com.vgallery.app.data.preferences.AppPreferences
import com.vgallery.app.data.preferences.ThemeMode
import com.vgallery.app.data.preferences.ThemeSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The Appearance section's state: what the user picked, read from and written to the same
 * DataStore the activity reads its theme from, so a choice here repaints the app immediately.
 */
@HiltViewModel
class ThemeSettingsViewModel @Inject constructor(
    private val preferences: AppPreferences
) : ViewModel() {

    val theme: StateFlow<ThemeSettings> = preferences.themeSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeSettings())

    fun setMode(mode: ThemeMode) {
        viewModelScope.launch { preferences.updateThemeMode(mode) }
    }

    fun setAccent(accent: AccentChoice) {
        viewModelScope.launch { preferences.updateAccent(accent) }
    }

    fun setAmoledBlack(value: Boolean) {
        viewModelScope.launch { preferences.updateAmoledBlack(value) }
    }
}
