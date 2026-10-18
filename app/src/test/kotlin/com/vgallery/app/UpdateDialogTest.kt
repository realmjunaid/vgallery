package com.vgallery.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vgallery.app.data.update.AppRelease
import com.vgallery.app.data.update.UpdateState
import com.vgallery.app.ui.update.UpdatePromptHost
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The prompt as the user meets it: the change list and two buttons, with no markdown and no
 * announcement paragraph, and a failure named after the step that actually failed.
 */
@RunWith(AndroidJUnit4::class)
class UpdateDialogTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(state: UpdateState) {
        compose.setContent {
            UpdatePromptHost(
                state = state,
                onUpdateNow = {},
                onCancelPrompt = {},
                onCancelDownload = {},
                onInstall = {},
                onTryAgain = {},
                onClose = {},
            )
        }
    }

    @Test
    fun thePromptShowsTheChangelogAndNothingElse() {
        show(UpdateState.Available(release, manual = true))

        compose.onNodeWithText("VGallery 1.0.9 is available").assertExists()
        compose.onNodeWithText("What's new").assertExists()
        compose.onNodeWithText("Autoplay is a small icon", substring = true).assertExists()
        compose.onNodeWithText("**", substring = true).assertDoesNotExist()
        compose.onNodeWithText("You have 1.0.7", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Update now").assertExists()
        compose.onNodeWithText("Cancel").assertExists()
    }

    @Test
    fun aDownloadFailureIsNamedAnUpdateFailure() {
        show(UpdateState.Failed("The download failed.", UpdateState.Failed.Phase.DOWNLOAD))

        compose.onNodeWithText("Update failed").assertExists()
        compose.onNodeWithText("The download failed.").assertExists()
    }

    @Test
    fun aCheckFailureIsNamedACheckFailure() {
        show(UpdateState.Failed("Could not reach GitHub.", UpdateState.Failed.Phase.CHECK))

        compose.onNodeWithText("Check failed").assertExists()
    }

    private companion object {
        val release = AppRelease(
            versionName = "1.0.9",
            tag = "v1.0.9",
            notes = "**What's new**\n\n- **Autoplay is a small icon** - no badge behind it.",
            apkUrl = "https://github.com/realmjunaid/vgallery/releases/download/v1.0.9/" +
                "vgallery_v1.0.9-release.apk",
            apkSha256 = null,
            source = "GitHub",
        )
    }
}
