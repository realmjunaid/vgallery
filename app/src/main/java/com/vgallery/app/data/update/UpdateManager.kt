package com.vgallery.app.data.update

import com.vgallery.app.BuildConfig
import com.vgallery.app.data.preferences.AppPreferences
import com.vgallery.app.di.ApplicationScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** What the updater is doing right now; both the prompt and the Settings row render from this. */
sealed interface UpdateState {

    /** Nothing to say. */
    data object Idle : UpdateState

    /** A check is in flight (only shown as a spinner; never a prompt by itself). */
    data object Checking : UpdateState

    /** Reported after a manual check only — an automatic one stays quiet about being up to date. */
    data class UpToDate(val versionName: String) : UpdateState

    /** A newer release. [manual] decides whether dismissing it is remembered as a skip. */
    data class Available(val release: AppRelease, val manual: Boolean) : UpdateState

    /** [progress] is 0..1, or -1 while the host has not declared how big the APK is. */
    data class Downloading(val release: AppRelease, val progress: Float) : UpdateState

    /** The APK is downloaded *and verified*; the system installer is the last step. */
    data class ReadyToInstall(val release: AppRelease, val filePath: String) : UpdateState

    /** Something went wrong; [message] is written for the user, [phase] says which step stopped. */
    data class Failed(val message: String, val phase: Phase = Phase.CHECK) : UpdateState {

        /** The step that failed, so the prompt and the Settings row can name it honestly. */
        enum class Phase { CHECK, DOWNLOAD }
    }
}

/**
 * The app's updater: finds the newest release, offers it, downloads it, verifies it and opens the
 * system installer with it.
 *
 * This is the only part of VGallery that touches the network. The check the app runs on launch is
 * throttled, silent and can be switched off; nothing is ever uploaded, and no photo, name or
 * identifier leaves the device — the updater only ever asks "is there a newer tag?".
 */
@Singleton
class UpdateManager @Inject constructor(
    private val checker: UpdateChecker,
    private val installer: UpdateInstaller,
    private val preferences: AppPreferences,
    @ApplicationScope private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)

    /** The updater's current step; the prompt in the app renders straight from this. */
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** The running build, shown in the prompts and compared against the published tags. */
    val currentVersionName: String = BuildConfig.VERSION_NAME

    /** Whether the app checks by itself when it opens (the Settings switch). */
    val autoCheckEnabled: Flow<Boolean> = preferences.updateCheckEnabled

    private var job: Job? = null

    /** The release most recently offered, so a failed download can be retried without a new check. */
    private var offered: AppRelease? = null
    private var downloadFailed = false

    /**
     * The check the app runs by itself when it opens: at most once every six hours, silent when
     * there is nothing new, and skipped entirely when automatic checks are turned off.
     */
    fun checkOnLaunch() {
        scope.launch {
            if (!preferences.updateCheckEnabled.first()) return@launch
            val lastCheck = preferences.lastUpdateCheck.first()
            if (System.currentTimeMillis() - lastCheck < CHECK_INTERVAL_MS) return@launch
            runCheck(manual = false)
        }
    }

    /** The Settings row: always asks, always reports — even with automatic checks turned off. */
    fun checkNow() {
        scope.launch { runCheck(manual = true) }
    }

    fun setAutoCheckEnabled(enabled: Boolean) {
        scope.launch { preferences.updateUpdateCheckEnabled(enabled) }
    }

    /** "Update now": download, verify, then open the system installer. */
    fun downloadAndInstall() {
        val available = _state.value as? UpdateState.Available ?: return
        if (job?.isActive == true) return
        offered = available.release
        downloadFailed = false
        job = scope.launch {
            _state.value = UpdateState.Downloading(available.release, -1f)
            try {
                val file = installer.download(available.release) { progress ->
                    _state.value = UpdateState.Downloading(available.release, progress)
                }
                _state.value = UpdateState.ReadyToInstall(available.release, file.absolutePath)
                beginInstall()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                downloadFailed = true
                _state.value = UpdateState.Failed(
                    e.message?.takeIf { it.isNotBlank() } ?: "The update could not be downloaded.",
                    UpdateState.Failed.Phase.DOWNLOAD
                )
            }
        }
    }

    /**
     * Opens the installer for the verified APK. Android only allows the install after the user has
     * allowed it for this app, so that permission is requested first (and the prompt stays open, so
     * "Install now" works once they come back).
     */
    fun beginInstall() {
        val ready = _state.value as? UpdateState.ReadyToInstall ?: return
        val file = File(ready.filePath)
        if (!file.isFile) {
            _state.value = UpdateState.Failed(
                "The downloaded update is gone. Download it again.",
                UpdateState.Failed.Phase.DOWNLOAD
            )
            downloadFailed = true
            return
        }
        if (installer.canRequestPackageInstalls()) {
            installer.install(file)
        } else {
            installer.openUnknownSourcesSettings()
        }
    }

    /** Cancel during a download: stops the transfer and keeps the update on offer. */
    fun cancelDownload() {
        val current = _state.value
        job?.cancel()
        job = null
        downloadFailed = false
        _state.value = if (current is UpdateState.Downloading) {
            UpdateState.Available(current.release, manual = true)
        } else {
            UpdateState.Idle
        }
    }

    /**
     * "Cancel" on the prompt. An automatic check remembers the release it was told about, so the app
     * never nags about the *same* version again — the next release asks again. A manual check is not
     * remembered, since the user actively asked and can bring the prompt back.
     */
    fun dismissPrompt() {
        val current = _state.value
        if (current is UpdateState.Available && !current.manual) {
            val version = current.release.versionName
            scope.launch { preferences.updateSkippedUpdate(version) }
        }
        _state.value = UpdateState.Idle
    }

    /** "Try again" after a failure: re-offer the release, or run the check again if that is what failed. */
    fun retry() {
        val release = offered
        if (downloadFailed && release != null) {
            _state.value = UpdateState.Available(release, manual = true)
            downloadAndInstall()
        } else {
            checkNow()
        }
    }

    /** Closes an outcome dialog ("You're up to date", "Install later", a failure notice). */
    fun clear() {
        if (_state.value !is UpdateState.Downloading) {
            downloadFailed = false
            _state.value = UpdateState.Idle
        }
    }

    private suspend fun runCheck(manual: Boolean) {
        val current = _state.value
        if (current is UpdateState.Checking || current is UpdateState.Downloading) return
        _state.value = UpdateState.Checking
        // Recorded before the request: a host that is unreachable must not be hammered every launch.
        preferences.updateLastUpdateCheck(System.currentTimeMillis())

        val release = try {
            checker.latest()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

        if (release == null) {
            downloadFailed = false
            _state.value = if (manual) {
                UpdateState.Failed("Could not reach GitHub. Check your connection and try again.")
            } else {
                UpdateState.Idle
            }
            return
        }

        if (!isNewerVersion(currentVersionName, release.versionName)) {
            downloadFailed = false
            _state.value = if (manual) UpdateState.UpToDate(currentVersionName) else UpdateState.Idle
            return
        }

        if (!manual && preferences.skippedUpdate.first() == release.versionName) {
            _state.value = UpdateState.Idle
            return
        }

        offered = release
        downloadFailed = false
        _state.value = UpdateState.Available(release, manual)
    }

    private companion object {
        /** How often the app may ask by itself, however often it is opened. */
        const val CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L
    }
}
