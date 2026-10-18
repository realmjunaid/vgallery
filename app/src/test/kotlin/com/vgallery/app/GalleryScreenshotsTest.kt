package com.vgallery.app

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import org.robolectric.annotation.GraphicsMode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.vgallery.app.data.gallery.DeviceAlbum
import com.vgallery.app.data.gallery.DevicePhotoRow
import com.vgallery.app.data.gallery.HomeTab
import com.vgallery.app.data.preferences.AccentChoice
import com.vgallery.app.data.preferences.ThemeMode
import com.vgallery.app.data.preferences.ThemeSettings
import com.vgallery.app.data.update.AppRelease
import com.vgallery.app.data.update.ReleaseAsset
import com.vgallery.app.data.update.UpdateState
import com.vgallery.app.ui.gallery.AlbumGrid
import com.vgallery.app.ui.gallery.EmptyState
import com.vgallery.app.ui.gallery.HomeTabs
import com.vgallery.app.ui.gallery.MediaList
import com.vgallery.app.ui.gallery.PhotoGrid
import com.vgallery.app.ui.gallery.SelectionBar
import com.vgallery.app.ui.settings.SettingsScreen
import com.vgallery.app.ui.theme.Black
import com.vgallery.app.ui.theme.VGalleryTheme
import com.vgallery.app.ui.theme.galleryColors
import com.vgallery.app.ui.update.UpdatePromptHost
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The app, as it actually draws: every screen Roborazzi renders to a PNG under
 * `app/build/roborazzi/`, so the theme can be looked at instead of imagined.
 *
 * Recorded with `./gradlew :app:testDebugUnitTest --tests "com.vallery.app.GalleryScreenshotsTest"`;
 * a screenshot is only written, never compared, so these are references to review by eye.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@org.robolectric.annotation.Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
@LargeTest
class GalleryScreenshotsTest {

    @get:Rule
    val composeRule = createComposeRule()

    // ── Home ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun homeDarkAmoled() = with(composeRule) {
        setContent {
            VGalleryTheme(colors = galleryColors(dark = true, amoled = true)) {
                Column(Modifier.fillMaxSize().background(Black)) {
                    HomeTabs(selected = HomeTab.PHOTOS, onSelect = {})
                    AlbumGrid(
                        albums = sampleAlbums,
                        newFolders = setOf("Screenshots"),
                        onAlbumClick = {},
                        selected = setOf("Camera"),
                        onAlbumLongClick = {}
                    )
                }
            }
        }
        onRoot().captureRoboImage("build/roborazzi/home_dark_amoled.png")
    }

    @Test
    fun homeLight() = with(composeRule) {
        setContent {
            VGalleryTheme(colors = galleryColors(dark = false)) {
                Column(Modifier.fillMaxSize().background(Black)) {
                    HomeTabs(selected = HomeTab.PHOTOS, onSelect = {})
                    AlbumGrid(
                        albums = sampleAlbums,
                        newFolders = emptySet(),
                        onAlbumClick = {},
                        selected = emptySet(),
                        onAlbumLongClick = {}
                    )
                }
            }
        }
        onRoot().captureRoboImage("build/roborazzi/home_light.png")
    }

    @Test
    fun homeLightVioletAccent() = with(composeRule) {
        setContent {
            VGalleryTheme(colors = galleryColors(dark = false, accent = AccentChoice.VIOLET)) {
                Column(Modifier.fillMaxSize().background(Black)) {
                    HomeTabs(selected = HomeTab.VIDEOS, onSelect = {})
                    AlbumGrid(
                        albums = sampleAlbums,
                        newFolders = emptySet(),
                        onAlbumClick = {},
                        selected = emptySet(),
                        onAlbumLongClick = {}
                    )
                }
            }
        }
        onRoot().captureRoboImage("build/roborazzi/home_light_violet.png")
    }

    // ── Folder contents ─────────────────────────────────────────────────────────────────────

    @Test
    fun gridDark() = with(composeRule) {
        setContent {
            VGalleryTheme(colors = galleryColors(dark = true, amoled = true)) {
                Column(Modifier.fillMaxSize().background(Black)) {
                    PhotoGrid(
                        photos = samplePhotos,
                        onPhotoClick = {},
                        selectedIds = setOf(samplePhotos[3].id),
                        onPhotoLongClick = {}
                    )
                }
            }
        }
        onRoot().captureRoboImage("build/roborazzi/grid_dark.png")
    }

    @Test
    fun listLight() = with(composeRule) {
        setContent {
            VGalleryTheme(colors = galleryColors(dark = false)) {
                Column(Modifier.fillMaxSize().background(Black)) {
                    MediaList(
                        photos = samplePhotos,
                        onPhotoClick = {},
                        selectedIds = setOf(samplePhotos[0].id),
                        onPhotoLongClick = {}
                    )
                }
            }
        }
        onRoot().captureRoboImage("build/roborazzi/list_light.png")
    }

    @Test
    fun selectionBarLight() = with(composeRule) {
        setContent {
            VGalleryTheme(colors = galleryColors(dark = false, accent = AccentChoice.VIOLET)) {
                Box(Modifier.fillMaxSize().background(Black)) {
                    SelectionBar(
                        count = 3,
                        noun = "photos",
                        onShare = {},
                        onMove = {},
                        onInfo = {},
                        onDelete = {},
                        onClear = {}
                    )
                }
            }
        }
        onRoot().captureRoboImage("build/roborazzi/selection_light.png")
    }

    @Test
    fun emptyState() = with(composeRule) {
        setContent {
            VGalleryTheme(colors = galleryColors(dark = true)) {
                Box(Modifier.fillMaxSize().background(Black)) {
                    EmptyState("No photos in this folder")
                }
            }
        }
        onRoot().captureRoboImage("build/roborazzi/empty_dark.png")
    }

    // ── Settings ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun settingsDark() = with(composeRule) {
        setContent {
            VGalleryTheme(colors = galleryColors(dark = true)) {
                SettingsScreen(
                    onBack = {},
                    updateSummary = "Up to date",
                    autoCheckEnabled = true,
                    onCheckNow = {},
                    onAutoCheckChange = {},
                    appearance = ThemeSettings(mode = ThemeMode.DARK, accent = AccentChoice.MONOCHROME)
                )
            }
        }
        onRoot().captureRoboImage("build/roborazzi/settings_dark.png")
    }

    @Test
    fun settingsLight() = with(composeRule) {
        setContent {
            VGalleryTheme(colors = galleryColors(dark = false, accent = AccentChoice.BLUE)) {
                SettingsScreen(
                    onBack = {},
                    updateSummary = "Up to date",
                    autoCheckEnabled = true,
                    onCheckNow = {},
                    onAutoCheckChange = {},
                    appearance = ThemeSettings(mode = ThemeMode.LIGHT, accent = AccentChoice.BLUE)
                )
            }
        }
        onRoot().captureRoboImage("build/roborazzi/settings_light.png")
    }

    // ── Update prompt ────────────────────────────────────────────────────────────────────────

    @Test
    fun updateAvailable() = with(composeRule) {
        setContent {
            VGalleryTheme(colors = galleryColors(dark = true)) {
                Box(Modifier.fillMaxSize().background(Black)) {
                    UpdatePromptHost(
                        state = UpdateState.Available(sampleRelease, manual = false),
                        onUpdateNow = {},
                        onCancelPrompt = {},
                        onCancelDownload = {},
                        onInstall = {},
                        onTryAgain = {},
                        onClose = {}
                    )
                }
            }
        }
        onRoot().captureRoboImage("build/roborazzi/update_available.png")
    }

    // ── Samples ─────────────────────────────────────────────────────────────────────────────

    private val sampleAlbums = listOf(
        DeviceAlbum(
            name = "Camera",
            count = 128,
            coverUri = Uri.parse("content://media/external/images/media/1"),
            coverIsVideo = false,
            sizeBytes = 412_345_678L
        ),
        DeviceAlbum(
            name = "Screenshots",
            count = 42,
            coverUri = Uri.parse("content://media/external/images/media/2"),
            coverIsVideo = false,
            sizeBytes = 88_123_456L
        ),
        DeviceAlbum(
            name = "Downloads",
            count = 7,
            coverUri = Uri.parse("content://media/external/video/media/3"),
            coverIsVideo = true,
            sizeBytes = 12_345_678L
        ),
        DeviceAlbum(
            name = "WhatsApp Images",
            count = 356,
            coverUri = Uri.parse("content://media/external/images/media/4"),
            coverIsVideo = false,
            sizeBytes = 234_567_890L
        )
    )

    private val samplePhotos = listOf(
        DevicePhotoRow(
            id = 1L,
            uri = Uri.parse("content://media/external/images/media/11"),
            name = "IMG_20260118_143022.jpg",
            bucketName = "Camera",
            dateModified = 1_768_746_622_000L,
            size = 4_210_368L,
            mimeType = "image/jpeg",
            width = 4032,
            height = 3024
        ),
        DevicePhotoRow(
            id = 2L,
            uri = Uri.parse("content://media/external/video/media/12"),
            name = "VID_20260120_091544.mp4",
            bucketName = "Camera",
            dateModified = 1_768_900_544_000L,
            size = 28_451_840L,
            mimeType = "video/mp4",
            durationMs = 12_400L,
            width = 1920,
            height = 1080
        ),
        DevicePhotoRow(
            id = 3L,
            uri = Uri.parse("content://media/external/images/media/13"),
            name = "IMG_20260121_184210.jpg",
            bucketName = "Camera",
            dateModified = 1_768_958_930_000L,
            size = 3_880_128L,
            mimeType = "image/jpeg",
            width = 3024,
            height = 3024
        ),
        DevicePhotoRow(
            id = 4L,
            uri = Uri.parse("content://media/external/images/media/14"),
            name = "animation.gif",
            bucketName = "Downloads",
            dateModified = 1_769_000_000_000L,
            size = 1_204_531L,
            mimeType = "image/gif",
            width = 480,
            height = 480
        ),
        DevicePhotoRow(
            id = 5L,
            uri = Uri.parse("content://media/external/images/media/15"),
            name = "webp_animated.webp",
            bucketName = "Downloads",
            dateModified = 1_769_005_000_000L,
            size = 812_044L,
            mimeType = "image/webp",
            width = 640,
            height = 640
        )
    )

    private val sampleRelease = AppRelease(
        versionName = "1.1.0",
        tag = "v1.1.0",
        notes = """
            - **Light theme** - the whole app follows your phone's light mode now
            - **Accent colours** - six accents, or your wallpaper on Android 12+
            - **Trash** - emptying it no longer needs the app open
            - Smaller APK and faster cold start
        """.trimIndent(),
        apkUrl = "https://github.com/realmjunaid/vgallery/releases/download/v1.1.0/app-release.apk",
        apkSha256 = "2ba474137cf920cebcc6c1397dbca287d49f06f88f95cfd94bdbb61eb82704f8",
        source = "github"
    )
}
