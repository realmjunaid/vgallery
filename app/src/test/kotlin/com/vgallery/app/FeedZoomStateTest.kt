package com.vgallery.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vgallery.app.ui.gallery.FeedZoomState
import com.vgallery.app.ui.gallery.MAX_FEED_ZOOM
import com.vgallery.app.ui.gallery.MIN_FEED_ZOOM
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The feed zoom is one global state, Google Drive PDF-viewer style: a pinch scales every item
 * together about the centroid while the feed's own scroll keeps handling vertical movement.
 * These tests pin the zoom/pan maths that make that predictable.
 */
@RunWith(AndroidJUnit4::class)
class FeedZoomStateTest {

    private fun state(widthPx: Int = 1000) = FeedZoomState().apply { onViewport(widthPx) }

    private fun assertClose(expected: Float, actual: Float, delta: Float = 0.001f) {
        assertEquals(expected, actual, delta)
    }

    @Test
    fun startsFittedAndNotZoomed() {
        val zoom = state()
        assertEquals(MIN_FEED_ZOOM, zoom.scale, 0f)
        assertEquals(0f, zoom.offsetX, 0f)
        assertFalse(zoom.isZoomed)
    }

    @Test
    fun pinchInScalesUpToMax() {
        val zoom = state()
        zoom.pinch(zoomChange = 100f, centroidX = 500f)
        assertEquals(MAX_FEED_ZOOM, zoom.scale, 0f)
        assertTrue(zoom.isZoomed)
    }

    @Test
    fun repeatedPinchesAccumulate() {
        val zoom = state()
        zoom.pinch(zoomChange = 2f, centroidX = 500f)
        zoom.pinch(zoomChange = 2f, centroidX = 500f)
        assertEquals(4f, zoom.scale, 0.001f)
    }

    @Test
    fun pinchAnchorsAtCentroid() {
        val zoom = state(widthPx = 1000)
        // Zooming 2x around the point 750 keeps the content under the fingers fixed:
        // screen = (content - 500) * 2 + 500 - 250 = 750.
        zoom.pinch(zoomChange = 2f, centroidX = 750f)
        assertEquals(2f, zoom.scale, 0.001f)
        assertClose(-250f, zoom.offsetX)
    }

    @Test
    fun pinchOutToFitResets() {
        val zoom = state(widthPx = 1000)
        zoom.pinch(zoomChange = 2f, centroidX = 750f)
        zoom.pinch(zoomChange = 0.4f, centroidX = 500f)
        assertEquals(MIN_FEED_ZOOM, zoom.scale, 0f)
        assertEquals(0f, zoom.offsetX, 0f)
        assertFalse(zoom.isZoomed)
    }

    @Test
    fun panDoesNothingAtFit() {
        val zoom = state()
        zoom.panBy(-300f)
        assertEquals(0f, zoom.offsetX, 0f)
    }

    @Test
    fun panClampsToContentEdges() {
        val zoom = state(widthPx = 1000)
        zoom.pinch(zoomChange = 2f, centroidX = 500f)
        // At 2x the content is 1000px wider, so 500px of travel is allowed per side.
        zoom.panBy(-5000f)
        assertClose(-500f, zoom.offsetX)
        zoom.panBy(10000f)
        assertClose(500f, zoom.offsetX)
    }

    @Test
    fun panLimitFollowsViewportWidth() {
        val zoom = state(widthPx = 400)
        zoom.pinch(zoomChange = 3f, centroidX = 200f)
        zoom.panBy(-9999f)
        assertClose(-400f, zoom.offsetX)
    }

    @Test
    fun resetClearsZoomAndOffset() {
        val zoom = state(widthPx = 1000)
        zoom.pinch(zoomChange = 2f, centroidX = 750f)
        zoom.reset()
        assertEquals(MIN_FEED_ZOOM, zoom.scale, 0f)
        assertEquals(0f, zoom.offsetX, 0f)
    }
}
