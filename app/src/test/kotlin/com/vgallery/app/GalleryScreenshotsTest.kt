package com.vgallery.app

import android.net.Uri
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
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
import com.vgallery.app.data.update.UpdateState
import com.vgallery.app.screenshots.installDemoImageLoader
import com.vgallery.app.screenshots.resetDemoImageLoader
import com.vgallery.app.ui.gallery.AlbumGrid
import com.vgallery.app.ui.gallery.EmptyState
import com.vgallery.app.ui.gallery.HomeTabs
import com.vgallery.app.ui.gallery.MediaList
import com.vgallery.app.ui.gallery.PhotoGrid
import com.vgallery.app.ui.gallery.SelectionBar
import com.vgallery.app.ui.gallery.StaticGalleryImage
import com.vgallery.app.ui.gallery.ViewerCaption
import com.vgallery.app.ui.gallery.ViewerCounter
import com.vgallery.app.ui.gallery.ViewerTopBar
import com.vgallery.app.ui.settings.SettingsScreen
import com.vgallery.app.ui.theme.Black
import com.vgallery.app.ui.theme.VGalleryTheme
import com.vgallery.app.ui.theme.galleryColors
import com.vgallery.app.ui.update.UpdatePromptHost
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The app, as it actually draws: every screen Roborazzi renders to a PNG under
 * `app/build/roborazzi/`, so the theme can be looked at instead of imagined. The copies
 * under `docs/screenshots/`, shown in the README, come from here.
 *
 * Photos are drawn by [installDemoImageLoader] — a device-less run has no camera roll —
 * so the grids show pictures rather than the system's empty placeholder.
 *
 * Record with `./gradlew :app:testDebugUnitTest --tests "com.vgallery.app.GalleryScreenshotsTest"`;
 * a screenshot is only written, never compared, so these are references to review by eye.
 * The README pictures under `docs/screenshots/` are the same PNGs, halved and renamed.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
@LargeTest
class GalleryScreenshotsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun serveDemoPhotos() = installDemoImageLoader()

    @After
    fun restoreImageLoader() = resetDemoImageLoader()

    /**
     * Composes one screen and writes it out. Images land one dispatch after the first
     * frame, so the queue is drained before the pixels are taken.
     */
    private fun record(name: String, content: @Composable () -> Unit) = with(composeRule) {
        setContent { content() }
        waitForIdle()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        waitForIdle()
        onRoot().captureRoboImage("build/roborazzi/$name.png")
    }

    // ── Home ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun homeDarkAmoled() = record("home_dark_amoled") {
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

    @Test
    fun homeLight() = record("home_light") {
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

    @Test
    fun homeLightVioletAccent() = record("home_light_violet") {
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

    /** Home with grouping on — the switch lives in Settings › Library now. */
    @Test
    fun homeGroupedDarkAmoled() = record("home_grouped_dark") {
        VGalleryTheme(colors = galleryColors(dark = true, amoled = true)) {
            Column(Modifier.fillMaxSize().background(Black)) {
                HomeTabs(selected = HomeTab.PHOTOS, onSelect = {})
                AlbumGrid(
                    albums = groupedAlbums,
                    newFolders = emptySet(),
                    onAlbumClick = {},
                    selected = emptySet(),
                    onAlbumLongClick = {},
                    grouped = true
                )
            }
        }
    }

    // ── Folder contents ─────────────────────────────────────────────────────────────────────

    @Test
    fun gridDark() = record("grid_dark") {
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

    @Test
    fun listLight() = record("list_light") {
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

    @Test
    fun selectionBarLight() = record("selection_light") {
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

    @Test
    fun emptyState() = record("empty_dark") {
        VGalleryTheme(colors = galleryColors(dark = true)) {
            Box(Modifier.fillMaxSize().background(Black)) {
                EmptyState("No photos in this folder")
            }
        }
    }

    // ── Settings ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun settingsDark() = record("settings_dark") {
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

    @Test
    fun settingsLight() = record("settings_light") {
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

    // ── Full view ───────────────────────────────────────────────────────────────────────────

    /** The feed's own chrome over a full-width photo — the Reels-style screen, as drawn. */
    @Test
    fun viewerDarkAmoled() = record("viewer_dark") {
        VGalleryTheme(colors = galleryColors(dark = true, amoled = true)) {
            Scaffold(
                containerColor = Color.Black,
                topBar = {
                    ViewerTopBar(
                        visible = true,
                        title = "Camera",
                        photoCount = 128,
                        current = samplePhotos[6],
                        autoPlay = false,
                        onDismiss = {},
                        onToggleAutoPlay = {},
                        onShare = {},
                        onInfo = {},
                        onDelete = {}
                    )
                }
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding).background(Color.Black)) {
                    Column(Modifier.fillMaxWidth()) {
                        listOf(samplePhotos[6], samplePhotos[9]).forEach { photo ->
                            StaticGalleryImage(
                                uri = photo.uri,
                                contentDescription = photo.name,
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(photo.width.toFloat() / photo.height)
                            )
                        }
                    }
                    ViewerCounter(
                        currentIndex = 6,
                        total = 128,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                    ViewerCaption(
                        name = samplePhotos[6].name,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }
    }

    // ── Update prompt ────────────────────────────────────────────────────────────────────────

    @Test
    fun updateAvailable() = record("update_available") {
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

    // ── Samples ─────────────────────────────────────────────────────────────────────────────

    private val sampleAlbums = listOf(
        album("Camera", count = 128, coverId = 1, sizeBytes = 412_345_678L),
        album("Screenshots", count = 42, coverId = 2, sizeBytes = 88_123_456L),
        album("Downloads", count = 7, coverId = 3, sizeBytes = 12_345_678L, coverIsVideo = true),
        album("WhatsApp Images", count = 356, coverId = 4, sizeBytes = 234_567_890L)
    )

    /** Folder names a real phone grows: two families that share a word, and two loners. */
    private val groupedAlbums = listOf(
        album("Camera", count = 128, coverId = 1, sizeBytes = 412_345_678L),
        album("Camera Roll", count = 64, coverId = 5, sizeBytes = 210_112_384L),
        album("WhatsApp Images", count = 356, coverId = 4, sizeBytes = 234_567_890L),
        album("WhatsApp Video", count = 42, coverId = 6, sizeBytes = 512_000_000L),
        album("Screenshots", count = 42, coverId = 2, sizeBytes = 88_123_456L),
        album("Downloads", count = 7, coverId = 3, sizeBytes = 12_345_678L)
    )

    private fun album(
        name: String,
        count: Int,
        coverId: Int,
        sizeBytes: Long,
        coverIsVideo: Boolean = false
    ) = DeviceAlbum(
        name = name,
        count = count,
        coverUri = Uri.parse("content://media/external/images/media/$coverId"),
        coverIsVideo = coverIsVideo,
        sizeBytes = sizeBytes
    )

    /** Twelve items, so the grid and the list both fill the screen the way a real folder does. */    private val samplePhotos = listOf(
        photo(1L, "IMG_20260118_143022.jpg", "image/jpeg", 4_210_368L, 4032, 3024),
        photo(2L, "VID_20260120_091544.mp4", "video/mp4", 28_451_840L, 1920, 1080, durationMs = 12_400L),
        photo(3L, "IMG_20260121_184210.jpg", "image/jpeg", 3_880_128L, 3024, 3024),
        photo(4L, "animation.gif", "image/gif", 1_204_531L, 480, 480),
        photo(5L, "IMG_20260124_072233.jpg", "image/jpeg", 5_120_640L, 4032, 3024),
        photo(6L, "webp_animated.webp", "image/webp", 812_044L, 640, 640),
        photo(7L, "IMG_20260126_201133.jpg", "image/jpeg", 4_760_320L, 3024, 4032),
        photo(8L, "VID_20260128_180215.mp4", "video/mp4", 41_943_040L, 3840, 2160, durationMs = 47_000L),
        photo(9L, "IMG_20260201_093318.jpg", "image/jpeg", 3_336_192L, 4032, 3024),
        photo(10L, "IMG_20260203_172940.jpg", "image/jpeg", 4_010_240L, 4032, 2268),
        photo(11L, "IMG_20260205_064812.jpg", "image/jpeg", 2_998_272L, 3024, 3024),
        photo(12L, "IMG_20260208_221503.jpg", "image/jpeg", 5_582_848L, 3024, 4032)
    )

    private fun photo(
        id: Long,
        name: String,
        mimeType: String,
        size: Long,
        width: Int,
        height: Int,
        durationMs: Long = 0L
    ) = DevicePhotoRow(
        id = id,
        uri = Uri.parse(
            if (mimeType.startsWith("video")) "content://media/external/video/media/$id"
            else "content://media/external/images/media/$id"
        ),
        name = name,
        bucketName = "Camera",
        dateModified = 1_768_746_622_000L + id * 86_400_000L,
        size = size,
        mimeType = mimeType,
        durationMs = durationMs,
        width = width,
        height = height
    )

    private val sampleRelease = AppRelease(
        versionName = "1.1.4",
        tag = "v1.1.4",
        notes = """
            - **Smooth autoplay** - the feed keeps showing as many photos per screen as before, and the item being played is the one in the middle: each step glides up into the middle in a single movement, with no jump up or down
            - **Autoplay speed** - Settings > Playback: how long each photo holds the screen, 1 to 15 seconds (a video still plays to its own end)
        """.trimIndent(),
        apkUrl = "https://github.com/realmjunaid/vgallery/releases/download/v1.1.4/vgallery_v1.1.4-release.apk",
        apkSha256 = "3522f2723e1eda6278cbe93c142c9f4614ee8ee69621b1c6b69eeabff6ff15c5",
        source = "github"
    )
}
