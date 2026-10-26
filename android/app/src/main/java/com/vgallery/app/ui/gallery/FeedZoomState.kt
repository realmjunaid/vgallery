package com.vgallery.app.ui.gallery

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue

/** Feed pinch-zoom bounds: 1x (fit) up to 10x. */
const val MIN_FEED_ZOOM = 1f
const val MAX_FEED_ZOOM = 10f

/**
 * One zoom state for the whole vertical feed, Google Drive PDF-viewer style: the feed
 * container owns it, so a pinch scales every visible item together instead of a single one.
 *
 * The feed's own list scroll stays in charge of vertical movement, so this state only needs
 * [scale] and a horizontally clamped [offsetX]. That is what lets the user keep scrolling
 * through all items while zoomed in.
 */
@Stable
class FeedZoomState {

    var scale by mutableFloatStateOf(MIN_FEED_ZOOM)
        private set

    var offsetX by mutableFloatStateOf(0f)
        private set

    private var viewportWidth = 0f

    /** True while zoomed past fit: horizontal drags pan, vertical drags keep scrolling. */
    val isZoomed: Boolean get() = scale > MIN_FEED_ZOOM

    /** The feed's measured width; the sideways pan limit follows it. */
    fun onViewport(widthPx: Int) {
        viewportWidth = widthPx.toFloat().coerceAtLeast(0f)
        clampOffset()
    }

    /**
     * One pinch step: [zoomChange] is the incremental factor from `calculateZoom()` and
     * [centroidX] the pinch centroid in viewport pixels. Zoom is anchored at the centroid, so
     * the content under the fingers stays under them.
     */
    fun pinch(zoomChange: Float, centroidX: Float) {
        val previous = scale
        val next = (previous * zoomChange).coerceIn(MIN_FEED_ZOOM, MAX_FEED_ZOOM)
        if (next <= MIN_FEED_ZOOM) {
            reset()
            return
        }
        if (next == previous) {
            clampOffset()
            return
        }
        // Keep the point under the centroid fixed: measure the centroid from the centre,
        // then scale the existing offset by the same factor as the content.
        val factor = next / previous
        val pivot = centroidX - viewportWidth / 2f
        offsetX = pivot * (1f - factor) + offsetX * factor
        scale = next
        clampOffset()
    }

    /** Horizontal pan while zoomed; vertical panning is left to the feed's scroll. */
    fun panBy(dx: Float) {
        if (!isZoomed) return
        offsetX += dx
        clampOffset()
    }

    fun reset() {
        scale = MIN_FEED_ZOOM
        offsetX = 0f
    }

    /** At `s`x the content is wider by `(s - 1)`, split evenly per side; never show blank space. */
    private fun clampOffset() {
        val limit = viewportWidth * (scale - 1f) / 2f
        offsetX = offsetX.coerceIn(-limit, limit)
    }
}
