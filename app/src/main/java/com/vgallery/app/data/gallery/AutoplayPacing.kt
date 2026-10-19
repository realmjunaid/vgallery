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

/** Seconds per photo, kept inside what the setting offers. */
fun autoplaySeconds(value: Int): Int = value.coerceIn(AUTOPLAY_MIN_SECONDS, AUTOPLAY_MAX_SECONDS)

/** How long an item holds the screen before autoplay moves on. */
fun autoplayDwellMs(isVideo: Boolean, durationMs: Long, seconds: Int): Long =
    if (isVideo) {
        (durationMs.takeIf { it > 0L } ?: AUTOPLAY_VIDEO_FALLBACK_MS) + AUTOPLAY_VIDEO_SLACK_MS
    } else {
        autoplaySeconds(seconds) * 1_000L
    }

/**
 * How long the glide to the next item takes. A slower autoplay earns a longer, calmer move, so the
 * feed never looks like it is racing the picture it has just left.
 */
fun autoplayGlideMs(seconds: Int): Long =
    (autoplaySeconds(seconds) * 60L).coerceIn(250L, 600L)
