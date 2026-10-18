package com.vgallery.app.ui.gallery

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue

/** Feed pinch-zoom bounds: 1x (fit) up to 10x. */
const val MIN_FEED_ZOOM = 1f
const val MAX_FEED_ZOOM = 10f

/**
 * A gesture batch drives the zoom when two fingers are down, or when a single
 * finger is down on an item that is already zoomed in. One finger at 1x is left
 * alone — that is what keeps the feed scrolling normally.
 */
fun zoomGestureApplies(pointerCount: Int, isZoomed: Boolean): Boolean =
    pointerCount >= 2 || isZoomed

/**
 * Two-finger pinch-zoom and drag-pan for one feed item, shared by photos and videos.
 *
 * The pan is clamped to the zoomed content, so an item can never be flung out of view.
 * Zooming back to 1x resets the offsets and clears [isZoomed], which is what lets the
 * feed start scrolling again.
 */
@Stable
class ZoomPanState {

    var scale by mutableFloatStateOf(MIN_FEED_ZOOM)
        private set

    var offsetX by mutableFloatStateOf(0f)
        private set

    var offsetY by mutableFloatStateOf(0f)
        private set

    /** True while zoomed past fit: the feed pauses scrolling and drags pan instead. */
    val isZoomed: Boolean get() = scale > MIN_FEED_ZOOM

    private var width = 0f
    private var height = 0f

    /** The item's measured size; the pan limits follow it. */
    fun onBounds(widthPx: Int, heightPx: Int) {
        width = widthPx.toFloat()
        height = heightPx.toFloat()
    }

    /**
     * One gesture step: [zoom] is the pinch factor, ([panX], [panY]) the drag delta —
     * both straight from `calculateZoom()` and `calculatePan()`.
     */
    fun transform(zoom: Float, panX: Float, panY: Float) {
        val next = (scale * zoom).coerceIn(MIN_FEED_ZOOM, MAX_FEED_ZOOM)
        if (next <= MIN_FEED_ZOOM) {
            reset()
            return
        }
        // At `next`x the content is wider than the item by (next - 1), split evenly per side.
        val limitX = width * (next - 1f) / 2f
        val limitY = height * (next - 1f) / 2f
        scale = next
        offsetX = (offsetX + panX).coerceIn(-limitX, limitX)
        offsetY = (offsetY + panY).coerceIn(-limitY, limitY)
    }

    fun reset() {
        scale = MIN_FEED_ZOOM
        offsetX = 0f
        offsetY = 0f
    }
}
