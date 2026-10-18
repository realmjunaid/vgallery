package com.vgallery.app.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vgallery.app.data.update.UpdateManager
import com.vgallery.app.data.update.UpdateState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Thin bridge from the process-wide [UpdateManager] to the screens that show it. The manager owns
 * the state, so the prompt in the app and the Settings row always agree.
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val manager: UpdateManager
) : ViewModel() {

    val state: StateFlow<UpdateState> = manager.state

    val currentVersionName: String = manager.currentVersionName

    /** Automatic checks are on by default; only a value already read is ever shown. */
    val autoCheckEnabled: StateFlow<Boolean> = manager.autoCheckEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun checkNow() = manager.checkNow()

    fun setAutoCheckEnabled(enabled: Boolean) = manager.setAutoCheckEnabled(enabled)
}
