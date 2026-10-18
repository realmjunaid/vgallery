package com.vgallery.app

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vgallery.app.data.preferences.AppPreferences
import com.vgallery.app.data.preferences.ThemeSettings
import com.vgallery.app.data.update.UpdateManager
import com.vgallery.app.navigation.VGalleryNavGraph
import com.vgallery.app.data.update.UpdateState
import com.vgallery.app.ui.theme.LocalGalleryColors
import com.vgallery.app.ui.theme.VGalleryTheme
import com.vgallery.app.ui.trash.TrashPurgePrompt
import com.vgallery.app.ui.update.UpdatePromptHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var updateManager: UpdateManager

    @Inject
    lateinit var preferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // The theme the user chose. DataStore is the single source, so Settings edits the app's
            // look live, from anywhere, without a restart.
            val theme by preferences.themeSettings
                .collectAsStateWithLifecycle(initialValue = ThemeSettings())

            VGalleryTheme(settings = theme) {
                SystemBarAppearance()
                VGalleryNavGraph()

                // One update prompt for the whole app, so a new release is offered wherever the
                // user happens to be — home, a folder view or Settings. It draws nothing at all
                // unless there is something to say.
                val updateState by updateManager.state.collectAsStateWithLifecycle()
                UpdatePromptHost(
                    state = updateState,
                    onUpdateNow = { updateManager.downloadAndInstall() },
                    onCancelPrompt = { updateManager.dismissPrompt() },
                    onCancelDownload = { updateManager.cancelDownload() },
                    onInstall = { updateManager.beginInstall() },
                    onTryAgain = { updateManager.retry() },
                    onClose = { updateManager.clear() },
                )

                // Expired trash is offered here, not deleted in the background: Android requires
                // the user's approval, and this stays down while the update prompt is up.
                TrashPurgePrompt(
                    enabled = updateState is UpdateState.Idle ||
                        updateState is UpdateState.Checking ||
                        updateState is UpdateState.UpToDate,
                )

                // The throttled, silent check the app runs when it opens. It never draws anything
                // by itself: only a genuinely newer release raises the prompt.
                LaunchedEffect(Unit) { updateManager.checkOnLaunch() }
            }
        }
    }
}

/**
 * Status- and navigation-bar icons follow the theme: dark icons on the light theme, light icons on
 * the dark one. Without this, a light app would keep white icons on a near-white bar.
 */
@Composable
private fun SystemBarAppearance() {
    val dark = LocalGalleryColors.current.isDark
    val activity = LocalContext.current as? Activity
    SideEffect {
        if (activity !is ComponentActivity) return@SideEffect
        val style = if (dark) {
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        }
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
}
