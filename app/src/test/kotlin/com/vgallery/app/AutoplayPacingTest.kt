package com.vgallery.app

import com.vgallery.app.data.gallery.AUTOPLAY_DEFAULT_SECONDS
import com.vgallery.app.data.gallery.AUTOPLAY_MAX_SECONDS
import com.vgallery.app.data.gallery.AUTOPLAY_MIN_SECONDS
import com.vgallery.app.data.gallery.AUTOPLAY_VIDEO_FALLBACK_MS
import com.vgallery.app.data.gallery.autoplayDwellMs
import com.vgallery.app.data.gallery.autoplayGlideMs
import com.vgallery.app.data.gallery.autoplaySeconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Autoplay's two times: how long a photo holds the screen (the user's setting, kept inside its
 * range) and how long the glide on to the next item takes. A video keeps its own length — the
 * setting is about photos.
 */
class AutoplayPacingTest {

    @Test
    fun photosHoldForTheChosenSeconds() {
        assertEquals(1_000L, autoplayDwellMs(isVideo = false, durationMs = 0L, seconds = 1))
        assertEquals(5_000L, autoplayDwellMs(isVideo = false, durationMs = 0L, seconds = 5))
        assertEquals(15_000L, autoplayDwellMs(isVideo = false, durationMs = 0L, seconds = 15))
    }

    @Test
    fun anOutOfRangeSettingIsClamped() {
        assertEquals(AUTOPLAY_MIN_SECONDS, autoplaySeconds(0))
        assertEquals(AUTOPLAY_MIN_SECONDS, autoplaySeconds(-3))
        assertEquals(AUTOPLAY_MAX_SECONDS, autoplaySeconds(120))
        assertEquals(AUTOPLAY_DEFAULT_SECONDS, autoplaySeconds(AUTOPLAY_DEFAULT_SECONDS))
        assertEquals(1_000L, autoplayDwellMs(isVideo = false, durationMs = 0L, seconds = 0))
    }

    @Test
    fun videosRunToTheirOwnEndWhateverTheSetting() {
        assertEquals(9_000L, autoplayDwellMs(isVideo = true, durationMs = 7_000L, seconds = 1))
        assertEquals(9_000L, autoplayDwellMs(isVideo = true, durationMs = 7_000L, seconds = 15))
        // A video whose length MediaStore never reported still moves on, eventually.
        assertEquals(
            AUTOPLAY_VIDEO_FALLBACK_MS + 2_000L,
            autoplayDwellMs(isVideo = true, durationMs = 0L, seconds = 5)
        )
    }

    @Test
    fun theGlideIsShortButNeverSnappy() {
        // A slow autoplay glides more calmly than a fast one, inside a fixed window.
        assertTrue(autoplayGlideMs(1) in 200L..400L)
        assertTrue(autoplayGlideMs(15) in 400L..700L)
        assertTrue(autoplayGlideMs(15) > autoplayGlideMs(1))
    }
}
