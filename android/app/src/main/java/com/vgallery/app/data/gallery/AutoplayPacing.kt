package com.vgallery.app.data.gallery

/**
 * Autoplay pacing: how long each item holds the screen, and how long the feed takes to glide on to
 * the next one. Plain arithmetic, so the setting, the store and the viewer all agree on the numbers.
 */

/** Slowest and fastest the user can make autoplay, in seconds per photo. */
const val AUTOPLAY_MIN_SECONDS = 1
const val AUTOPLAY_MAX_SECONDS = 15
const val AUTOPLAY_DEFAULT_SECONDS = 5

/** Assumed for a video whose length MediaStore never reported. */
const val AUTOPLAY_VIDEO_FALLBACK_MS = 30_000L

/** Videos are advanced by the player the moment they end; this only guards one that never says so. */
private const val AUTOPLAY_VIDEO_SLACK_MS = 2_000L

/** A GIF or an animated WebP has no duration MediaStore can report; let it play this long. */
const val AUTOPLAY_MOTION_IMAGE_MS = 5_000L

/** Seconds per photo, kept inside what the setting offers. */
fun autoplaySeconds(value: Int): Int = value.coerceIn(AUTOPLAY_MIN_SECONDS, AUTOPLAY_MAX_SECONDS)

/** How long an item holds the screen before autoplay moves on. */
fun autoplayDwellMs(
    isVideo: Boolean,
    isMotionImage: Boolean,
    durationMs: Long,
    seconds: Int
): Long = when {
    isVideo -> (durationMs.takeIf { it > 0L } ?: AUTOPLAY_VIDEO_FALLBACK_MS) + AUTOPLAY_VIDEO_SLACK_MS
    isMotionImage -> AUTOPLAY_MOTION_IMAGE_MS
    else -> autoplaySeconds(seconds) * 1_000L
}

/** Glide length is kept inside these bounds, however short or long the step turns out to be. */
private const val AUTOPLAY_GLIDE_MIN_MS = 250L
private const val AUTOPLAY_GLIDE_MAX_MS = 1_000L

/** Slower autoplay earns a longer glide: this much per second of the setting. */
private const val AUTOPLAY_GLIDE_PER_SECOND_MS = 60L

/** And a step that covers more screen earns a little longer still: a millisecond per these pixels. */
private const val AUTOPLAY_GLIDE_STRETCH_PX = 6L
private const val AUTOPLAY_GLIDE_STRETCH_MAX_MS = 400L

/**
 * How long the glide from one item to the next takes. A slower autoplay earns a longer, calmer move
 * so the feed never looks like it is racing the picture it has just left, and a step that covers most
 * of the screen — a tall photo getting out of the way — is given a little longer still so it glides
 * rather than flicks. [distancePx] is that step, in pixels.
 */
fun autoplayGlideMs(seconds: Int, distancePx: Int = 0): Long {
    val pace = autoplaySeconds(seconds) * AUTOPLAY_GLIDE_PER_SECOND_MS
    val stretch = (distancePx / AUTOPLAY_GLIDE_STRETCH_PX)
        .coerceIn(0L, AUTOPLAY_GLIDE_STRETCH_MAX_MS)
    return (pace + stretch).coerceIn(AUTOPLAY_GLIDE_MIN_MS, AUTOPLAY_GLIDE_MAX_MS)
}
