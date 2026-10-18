package com.vgallery.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vgallery.app.ui.settings.SettingsScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Settings carries the updater row and the two About pages, and nothing else: the playback options
 * it used to show were removed on purpose, and "About Developer" is the entry that says who builds
 * VGallery. The screen's state is hoisted, so these tests drive it with plain values.
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
    ) {
        compose.setContent {
            SettingsScreen(
                onBack = {},
                updateSummary = updateSummary,
                autoCheckEnabled = autoCheckEnabled,
                onCheckNow = onCheckNow,
                onAutoCheckChange = onAutoCheckChange,
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
    fun tappingCheckForUpdatesAsksTheUpdater() {
        var checks = 0
        openSettings(onCheckNow = { checks++ })

        compose.onNodeWithText("Check for updates").performClick()

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

        compose.onNodeWithText("Automatic update check").performClick()

        assertEquals(false, enabled)
    }

    @Test
    fun aboutDeveloperPageDescribesTheDeveloper() {
        openSettings()

        compose.onNodeWithText("About Developer").performClick()

        compose.onNodeWithText("realmjunaid").assertExists()
        compose.onNodeWithText("Android developer \u2022 VGallery").assertExists()
        compose.onNodeWithText("Source code").assertExists()
        compose.onNodeWithText("Contact").assertExists()
    }

    @Test
    fun aboutPageReportsPrivacyAndFeatures() {
        openSettings()

        compose.onNodeWithText("About VGallery").performClick()

        compose.onNodeWithText("Version ${BuildConfig.VERSION_NAME}").assertExists()
        compose.onNodeWithText("Folder-wise gallery").assertExists()
        compose.onNodeWithText("Online for updates only").assertExists()
        compose.onNodeWithText("Your photos stay yours").assertExists()
    }
}
