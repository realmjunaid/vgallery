package com.vgallery.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vgallery.app.ui.settings.SettingsScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Settings is now just the About pages: every playback option was removed on purpose, and
 * "About Developer" is the new entry that says who builds VGallery.
 */
@RunWith(AndroidJUnit4::class)
class SettingsAboutTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun settingsOffersOnlyTheAboutPages() {
        compose.setContent { SettingsScreen(onBack = {}) }

        compose.onNodeWithText("About VGallery").assertExists()
        compose.onNodeWithText("About Developer").assertExists()

        // The removed options must not creep back in.
        compose.onNodeWithText("Landscape Video Mode").assertDoesNotExist()
        compose.onNodeWithText("Sort Order").assertDoesNotExist()
        compose.onNodeWithText("Haptic Feedback").assertDoesNotExist()
        compose.onNodeWithText("Auto-Advance Images").assertDoesNotExist()
    }

    @Test
    fun aboutDeveloperPageDescribesTheDeveloper() {
        compose.setContent { SettingsScreen(onBack = {}) }

        compose.onNodeWithText("About Developer").performClick()

        compose.onNodeWithText("realmjunaid").assertExists()
        compose.onNodeWithText("Android developer \u2022 VGallery").assertExists()
        compose.onNodeWithText("Source code").assertExists()
        compose.onNodeWithText("Contact").assertExists()
    }

    @Test
    fun aboutPageReportsPrivacyAndFeatures() {
        compose.setContent { SettingsScreen(onBack = {}) }

        compose.onNodeWithText("About VGallery").performClick()

        compose.onNodeWithText("Version ${BuildConfig.VERSION_NAME}").assertExists()
        compose.onNodeWithText("Folder-wise gallery").assertExists()
        compose.onNodeWithText("Works fully offline").assertExists()
        compose.onNodeWithText("Your photos stay yours").assertExists()
    }
}
