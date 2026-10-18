package com.vgallery.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vgallery.app.ui.gallery.MAX_FEED_ZOOM
import com.vgallery.app.ui.gallery.MIN_FEED_ZOOM
import com.vgallery.app.ui.gallery.ZoomPanState
import com.vgallery.app.ui.gallery.zoomGestureApplies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The feed's pinch-zoom math: 1x up to 10x, pan clamped to the zoomed content so an
 * item can never be flung out of view, and reset back to fit on the way out.
 */
@RunWith(AndroidJUnit4::class)
class ZoomPanStateTest {

    /** An item as big as the pane, so the pan limits are easy to reason about. */
    private fun state(width: Int = 1000, height: Int = 2000) =
        ZoomPanState().apply { onBounds(width, height) }

    @Test
    fun startsFittedAndNotZoomed() {
        val zoom = state()
        assertEquals(MIN_FEED_ZOOM, zoom.scale, 0f)
        assertEquals(0f, zoom.offsetX, 0f)
        assertEquals(0f, zoom.offsetY, 0f)
        assertFalse(zoom.isZoomed)
    }

    @Test
    fun pinchOut_zoomsIn() {
        val zoom = state()
        zoom.transform(zoom = 1.5f, panX = 0f, panY = 0f)
        assertEquals(1.5f, zoom.scale, 0.0001f)
        assertTrue(zoom.isZoomed)
    }

    @Test
    fun zoomNeverGoesPastTenTimes() {
        val zoom = state()
        repeat(20) { zoom.transform(zoom = 2f, panX = 0f, panY = 0f) }
        assertEquals(MAX_FEED_ZOOM, zoom.scale, 0f)
    }

    @Test
    fun pinchBackToFit_resetsScaleAndOffsets() {
        val zoom = state()
        zoom.transform(zoom = 4f, panX = 0f, panY = 0f)
        zoom.transform(zoom = 1f, panX = 400f, panY = 400f)
        assertTrue(zoom.isZoomed)

        zoom.transform(zoom = 0.1f, panX = 0f, panY = 0f)
        assertEquals(MIN_FEED_ZOOM, zoom.scale, 0f)
        assertEquals(0f, zoom.offsetX, 0f)
        assertEquals(0f, zoom.offsetY, 0f)
        assertFalse(zoom.isZoomed)
    }

    @Test
    fun panAppliesTheDragDelta() {
        val zoom = state()
        zoom.transform(zoom = 2f, panX = 0f, panY = 0f)
        zoom.transform(zoom = 1f, panX = 100f, panY = -50f)
        assertEquals(100f, zoom.offsetX, 0.0001f)
        assertEquals(-50f, zoom.offsetY, 0.0001f)
    }

    @Test
    fun panIsClampedToTheZoomedContent() {
        val zoom = state()
        zoom.transform(zoom = 2f, panX = 0f, panY = 0f)

        // At 2x the content overflows by half its size on each side.
        zoom.transform(zoom = 1f, panX = 10_000f, panY = 10_000f)
        assertEquals(500f, zoom.offsetX, 0.0001f)
        assertEquals(1000f, zoom.offsetY, 0.0001f)

        zoom.transform(zoom = 1f, panX = -10_000f, panY = -10_000f)
        assertEquals(-500f, zoom.offsetX, 0.0001f)
        assertEquals(-1000f, zoom.offsetY, 0.0001f)
    }

    @Test
    fun zoomingBackOut_reclampsTheExistingPan() {
        val zoom = state()
        zoom.transform(zoom = 4f, panX = 0f, panY = 0f)
        zoom.transform(zoom = 1f, panX = 10_000f, panY = 10_000f)
        assertEquals(1500f, zoom.offsetX, 0.0001f)

        // At 2x there is far less room, so the earlier offset has to shrink with it.
        zoom.transform(zoom = 0.5f, panX = 0f, panY = 0f)
        assertEquals(2f, zoom.scale, 0.0001f)
        assertEquals(500f, zoom.offsetX, 0.0001f)
        assertEquals(1000f, zoom.offsetY, 0.0001f)
    }

    @Test
    fun panBeforeTheItemIsMeasured_staysCentred() {
        val zoom = ZoomPanState()
        zoom.transform(zoom = 2f, panX = 500f, panY = 500f)
        assertTrue(zoom.isZoomed)
        assertEquals(0f, zoom.offsetX, 0f)
        assertEquals(0f, zoom.offsetY, 0f)

        // Once the size is known the limits apply again.
        zoom.onBounds(1000, 2000)
        zoom.transform(zoom = 1f, panX = 500f, panY = 500f)
        assertEquals(500f, zoom.offsetX, 0.0001f)
        assertEquals(500f, zoom.offsetY, 0.0001f)
    }

    @Test
    fun oneFingerAtFit_isLeftToTheFeed() {
        // Capturing a single finger at 1x would stop the feed from scrolling.
        assertFalse(zoomGestureApplies(pointerCount = 1, isZoomed = false))
    }

    @Test
    fun twoFingers_zoomAtAnyScale_andOneFingerPansOnceZoomed() {
        assertTrue(zoomGestureApplies(pointerCount = 2, isZoomed = false))
        assertTrue(zoomGestureApplies(pointerCount = 2, isZoomed = true))
        assertTrue(zoomGestureApplies(pointerCount = 1, isZoomed = true))
    }
}
