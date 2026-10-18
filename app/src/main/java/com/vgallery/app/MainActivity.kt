package com.vgallery.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vgallery.app.data.update.UpdateManager
import com.vgallery.app.navigation.VGalleryNavGraph
import com.vgallery.app.ui.theme.VGalleryTheme
import com.vgallery.app.ui.update.UpdatePromptHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var updateManager: UpdateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VGalleryTheme {
                VGalleryNavGraph()

                // One update prompt for the whole app, so a new release is offered wherever the
                // user happens to be — home, a folder view or Settings. It draws nothing at all
                // unless there is something to say.
                val updateState by updateManager.state.collectAsStateWithLifecycle()
                UpdatePromptHost(
                    state = updateState,
                    currentVersionName = updateManager.currentVersionName,
                    onUpdateNow = { updateManager.downloadAndInstall() },
                    onCancelPrompt = { updateManager.dismissPrompt() },
                    onCancelDownload = { updateManager.cancelDownload() },
                    onInstall = { updateManager.beginInstall() },
                    onTryAgain = { updateManager.retry() },
                    onClose = { updateManager.clear() },
                )

                // The throttled, silent check the app runs when it opens. It never draws anything
                // by itself: only a genuinely newer release raises the prompt.
                LaunchedEffect(Unit) { updateManager.checkOnLaunch() }
            }
        }
    }
}
