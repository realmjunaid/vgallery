package com.vgallery.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vgallery.app.ui.settings.SettingsScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Settings carries the appearance, playback and library rows, the updater, and the two About pages:
 * the autoplay speed and the home grouping switch live here now, and so does the Trash. "About
 * Developer" is the entry that says who builds VGallery. The screen's state is hoisted, so these
 * tests drive it with plain values.
 */
@RunWith(AndroidJUnit4::class)
class SettingsAboutTest {

    @get:Rule
    val compose = createComposeRule()

    private fun openSettings(
        updateSummary: String = "Version 1.0.7",
        autoCheckEnabled: Boolean = true,
        onCheckNow: () -> Unit = {},
        onAutoCheckChange: (Boolean) -> Unit = {},
        onOpenLink: (String) -> Unit = {},
        onTrashClick: () -> Unit = {},
        groupSimilarFolders: Boolean = false,
        onGroupSimilarFoldersChange: (Boolean) -> Unit = {},
        autoplaySeconds: Int = 5,
        onAutoplaySecondsChange: (Int) -> Unit = {},
    ) {
        compose.setContent {
            SettingsScreen(
                onBack = {},
                updateSummary = updateSummary,
                autoCheckEnabled = autoCheckEnabled,
                onCheckNow = onCheckNow,
                onAutoCheckChange = onAutoCheckChange,
                onOpenLink = onOpenLink,
                onTrashClick = onTrashClick,
                groupSimilarFolders = groupSimilarFolders,
                onGroupSimilarFoldersChange = onGroupSimilarFoldersChange,
                autoplaySeconds = autoplaySeconds,
                onAutoplaySecondsChange = onAutoplaySecondsChange,
            )
        }
    }

    @Test
    fun settingsOffersUpdatesAndTheAboutPages() {
        openSettings()

        compose.onNodeWithText("Check for updates").assertExists()
        compose.onNodeWithText("Automatic update check").assertExists()
        compose.onNodeWithText("About VGallery").assertExists()
        compose.onNodeWithText("About Developer").assertExists()

        // The removed options must not creep back in.
        compose.onNodeWithText("Landscape Video Mode").assertDoesNotExist()
        compose.onNodeWithText("Sort Order").assertDoesNotExist()
        compose.onNodeWithText("Haptic Feedback").assertDoesNotExist()
        compose.onNodeWithText("Auto-Advance Images").assertDoesNotExist()
    }

    @Test
    fun settingsOffersThePlaybackAndLibraryRows() {
        openSettings()

        compose.onNodeWithText("Autoplay speed").assertExists()
        compose.onNodeWithText("Group similar folders").assertExists()
        compose.onNodeWithText("Trash").assertExists()
    }

    @Test
    fun theAutoplayStepperWalksTheSpeedUpAndDown() {
        val steps = mutableListOf<Int>()
        openSettings(autoplaySeconds = 5, onAutoplaySecondsChange = { steps += it })

        compose.onNodeWithContentDescription("Slower autoplay").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Faster autoplay").performScrollTo().performClick()

        // Slower means more seconds per photo; faster means fewer.
        assertEquals(listOf(6, 4), steps)
    }

    @Test
    fun theGroupingSwitchAndTheTrashRowReport() {
        var grouped: Boolean? = null
        var trashed = 0
        openSettings(
            onTrashClick = { trashed++ },
            onGroupSimilarFoldersChange = { grouped = it }
        )

        compose.onNodeWithText("Group similar folders").performScrollTo().performClick()
        compose.onNodeWithText("Trash").performScrollTo().performClick()

        assertEquals(true, grouped)
        assertEquals(1, trashed)
    }

    @Test
    fun tappingCheckForUpdatesAsksTheUpdater() {
        var checks = 0
        openSettings(onCheckNow = { checks++ })

        compose.onNodeWithText("Check for updates").performScrollTo().performClick()

        assertEquals(1, checks)
    }

    @Test
    fun theUpdateRowShowsWhatTheUpdaterIsDoing() {
        openSettings(updateSummary = "Version 1.0.8 available")

        compose.onNodeWithText("Version 1.0.8 available").assertExists()
    }

    @Test
    fun turningAutomaticChecksOffIsReported() {
        var enabled: Boolean? = null
        openSettings(autoCheckEnabled = true, onAutoCheckChange = { enabled = it })

        compose.onNodeWithText("Automatic update check").performScrollTo().performClick()

        assertEquals(false, enabled)
    }

    @Test
    fun aboutDeveloperPageDescribesTheDeveloper() {
        openSettings()

        compose.onNodeWithText("About Developer").performScrollTo().performClick()

        compose.onNodeWithText("M Junaid").assertExists()
        compose.onNodeWithText("Android developer \u2022 mjunaid.me").assertExists()
        compose.onNodeWithText("mjunaid.me").assertExists()
        compose.onNodeWithText("Source code").assertExists()
        compose.onNodeWithText("Contact").assertExists()
    }

    @Test
    fun theWebsiteAndTheSourceCodeOpenTheirLinks() {
        val opened = mutableListOf<String>()
        openSettings(onOpenLink = { opened += it })

        compose.onNodeWithText("About Developer").performScrollTo().performClick()
        compose.onNodeWithText("Source code").performScrollTo().performClick()
        compose.onNodeWithText("mjunaid.me").performScrollTo().performClick()
        compose.onNodeWithText("Contact").performScrollTo().performClick()

        assertEquals(
            listOf(
                "https://github.com/realmjunaid/vgallery",
                "https://mjunaid.me",
                "https://github.com/realmjunaid/vgallery/issues",
            ),
            opened
        )
    }

    @Test
    fun aboutPageReportsPrivacyAndFeatures() {
        openSettings()

        compose.onNodeWithText("About VGallery").performScrollTo().performClick()

        compose.onNodeWithText("Version ${BuildConfig.VERSION_NAME}").assertExists()
        compose.onNodeWithText("Folder-wise gallery").assertExists()
        compose.onNodeWithText("Online for updates only").assertExists()
        compose.onNodeWithText("Your photos stay yours").assertExists()
    }
}
