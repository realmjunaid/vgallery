package com.vgallery.app.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.net.Uri
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.map.Mapper
import coil3.request.CachePolicy
import coil3.request.Options
import kotlinx.coroutines.Dispatchers
import kotlin.math.abs
import kotlin.random.Random

/**
 * Stand-in photos for the README screenshots.
 *
 * A device-less test run has no camera roll to show, and the real `content://` URIs a
 * screenshot test can hand to the UI resolve to nothing — which is why the published
 * screenshots used to be a grid of grey boxes. These photos are drawn from a seed, so the
 * same URI always renders the same picture and the screenshots (and their diffs) stay stable;
 * they exist only in the test source set and never ship in the APK.
 */
internal object DemoImages {

    /** Every rendered photo, keyed by URI + size: a screenshot run re-draws each one once. */
    private val cache = LinkedHashMap<String, Bitmap>()

    /** The photo that stands in for [uri], always the same one for the same URI. */
    fun photoFor(uri: Uri?, width: Int = 720, height: Int = 960): Bitmap {
        val seed = uri?.toString()?.hashCode() ?: 0
        return cache.getOrPut("$seed@${width}x$height") {
            render(A_SKY_ROLL[abs(seed) % A_SKY_ROLL.size], Random(seed), width, height)
        }
    }

    /** The sky, the sun, its hills and its water — a different palette makes a different photo. */
    private data class Sky(
        val top: Int,
        val bottom: Int,
        val sun: Int?,
        val hills: List<Int>,
        val water: Int?
    )

    private val A_SKY_ROLL = listOf(
        // Sunset
        Sky(
            top = 0xFF1B2A5B.toInt(), bottom = 0xFFF6A96B.toInt(), sun = 0xFFFFD08A.toInt(),
            hills = listOf(0xFF3B2A4A.toInt(), 0xFF241C33.toInt()), water = 0xFF6A4A5F.toInt()
        ),
        // Clear day
        Sky(
            top = 0xFF2E7DD1.toInt(), bottom = 0xFFA8DCF2.toInt(), sun = 0xFFFFF3C4.toInt(),
            hills = listOf(0xFF2F6B4F.toInt(), 0xFF1E4A38.toInt()), water = null
        ),
        // Desert morning
        Sky(
            top = 0xFFF6C77A.toInt(), bottom = 0xFFFDE7C0.toInt(), sun = 0xFFFFFFFF.toInt(),
            hills = listOf(0xFFE0A15C.toInt(), 0xFFBE7A3C.toInt()), water = null
        ),
        // Night sky
        Sky(
            top = 0xFF070A1F.toInt(), bottom = 0xFF2B2145.toInt(), sun = 0xFFF2F0FF.toInt(),
            hills = listOf(0xFF161335.toInt(), 0xFF0A0820.toInt()), water = null
        ),
        // Forest mist
        Sky(
            top = 0xFF9FC4B0.toInt(), bottom = 0xFFE4F1E7.toInt(), sun = null,
            hills = listOf(0xFF4E7C63.toInt(), 0xFF2C4F3D.toInt()), water = null
        ),
        // Ocean dusk
        Sky(
            top = 0xFF3A2E6E.toInt(), bottom = 0xFFE88B7A.toInt(), sun = 0xFFFFC9A3.toInt(),
            hills = listOf(), water = 0xFF2A2350.toInt()
        ),
        // Winter
        Sky(
            top = 0xFF8FB8DE.toInt(), bottom = 0xFFF2F7FB.toInt(), sun = null,
            hills = listOf(0xFFB8CDE0.toInt(), 0xFF8EA9C4.toInt()), water = null
        ),
        // Violet abstract
        Sky(
            top = 0xFF7C3AED.toInt(), bottom = 0xFFE9D5FF.toInt(), sun = 0xFFFFE9B8.toInt(),
            hills = listOf(0xFF4C1D95.toInt(), 0xFF2E1065.toInt()), water = null
        ),
        // Lagoon
        Sky(
            top = 0xFF0FA3B1.toInt(), bottom = 0xFFD7F9F8.toInt(), sun = 0xFFFFF6C2.toInt(),
            hills = listOf(0xFF0B6E77.toInt(), 0xFF084C52.toInt()), water = 0xFF0A8C99.toInt()
        ),
        // Autumn
        Sky(
            top = 0xFF7C4A21.toInt(), bottom = 0xFFF5C77A.toInt(), sun = 0xFFFFE6A7.toInt(),
            hills = listOf(0xFF9C5A2A.toInt(), 0xFF6B3A1B.toInt()), water = null
        ),
        // Storm over the sea
        Sky(
            top = 0xFF2C3E50.toInt(), bottom = 0xFF9AA7B1.toInt(), sun = null,
            hills = listOf(0xFF1F2D38.toInt()), water = 0xFF33485A.toInt()
        ),
        // Dawn city
        Sky(
            top = 0xFF12314F.toInt(), bottom = 0xFFF0A57C.toInt(), sun = 0xFFFFD9A8.toInt(),
            hills = listOf(0xFF1B2438.toInt(), 0xFF0C1120.toInt()), water = null
        )
    )

    /** Paints one photo: a sky, an optional sun, hills back to front, then a soft vignette. */
    private fun render(sky: Sky, random: Random, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val w = width.toFloat()
        val h = height.toFloat()

        paint.shader = LinearGradient(0f, 0f, 0f, h, sky.top, sky.bottom, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        sky.sun?.let { sun ->
            val cx = w * (0.35f + random.nextFloat() * 0.35f)
            val cy = h * (0.16f + random.nextFloat() * 0.14f)
            paint.shader = RadialGradient(
                cx, cy, w * 0.62f,
                intArrayOf(withAlpha(sun, 190), withAlpha(sun, 40), Color.TRANSPARENT),
                floatArrayOf(0f, 0.35f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, w, h, paint)
            paint.shader = null
            // Clouds, but only where the sky is light enough to show them: the dark
            // palettes get stars instead, further down.
            if (brightness(sky.top) > 180) {
                paint.color = withAlpha(Color.WHITE, 38)
                repeat(2 + random.nextInt(4)) {
                    val cloudY = h * (0.08f + random.nextFloat() * 0.3f)
                    val cloudW = w * (0.3f + random.nextFloat() * 0.5f)
                    val cloudH = h * (0.012f + random.nextFloat() * 0.02f)
                    canvas.drawOval(
                        w * random.nextFloat() * 0.7f, cloudY,
                        w * random.nextFloat() * 0.7f + cloudW, cloudY + cloudH,
                        paint
                    )
                }
            }
            paint.color = withAlpha(sun, 235)
            canvas.drawCircle(cx, cy, w * 0.07f, paint)
        }
        // Stars, for the night skies.
        if (brightness(sky.top) < 120) {
            paint.color = withAlpha(Color.WHITE, 200)
            repeat(70) {
                canvas.drawCircle(
                    w * random.nextFloat(), h * random.nextFloat() * 0.5f,
                    w * (0.0015f + random.nextFloat() * 0.0025f), paint
                )
            }
        }

        // Hills: each one starts lower than the last, with a gently rolling ridge line.
        var ridge = h * (0.42f + random.nextFloat() * 0.2f)
        sky.hills.forEachIndexed { index, color ->
            val path = Path()
            path.moveTo(0f, h)
            path.lineTo(0f, ridge)
            var x = 0f
            var y = ridge
            val step = w / (3f + index)
            while (x < w) {
                x += step * (0.7f + random.nextFloat() * 0.6f)
                y = ridge + (random.nextFloat() - 0.5f) * h * 0.06f
                path.lineTo(x.coerceAtMost(w), y)
            }
            path.lineTo(w, h)
            path.close()
            paint.color = color
            canvas.drawPath(path, paint)
            ridge += h * (0.10f + random.nextFloat() * 0.05f)
        }

        sky.water?.let { water ->
            paint.shader = LinearGradient(0f, ridge, 0f, h, withAlpha(water, 210), water, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, ridge, w, h, paint)
            paint.shader = null
        }

        // A vignette, so the flat drawing reads a little more like a photo.
        paint.shader = RadialGradient(
            w / 2f, h / 2f, maxOf(w, h) * 0.75f,
            intArrayOf(Color.TRANSPARENT, withAlpha(Color.BLACK, 90)),
            floatArrayOf(0.65f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
        return bitmap
    }

    /** Rough brightness of a colour: enough to tell a day sky from a night one. */
    private fun brightness(color: Int): Int =
        Color.red(color) + Color.green(color) + Color.blue(color)

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
}

/**
 * Serves [DemoImages] in place of the real MediaStore: every `content://` a screenshot asks
 * for is mapped to a drawn bitmap, and Coil's default `BitmapFetcher` takes it from there.
 */
internal class DemoMediaMapper : Mapper<Uri, Bitmap> {
    override fun map(data: Uri, options: Options): Bitmap? =
        if (data.scheme == "content") DemoImages.photoFor(data) else null
}

/**
 * Points Coil at [DemoMediaMapper] for the duration of a screenshot test. Everything the
 * pipeline still needs runs on the main dispatcher, so an image is on screen by the time
 * the composable idles — no waiting on a background decode that Robolectric never drives.
 */
@OptIn(DelicateCoilApi::class)
internal fun installDemoImageLoader() {
    SingletonImageLoader.setUnsafe { context ->
        ImageLoader.Builder(context)
            .components { add(DemoMediaMapper()) }
            .diskCachePolicy(CachePolicy.DISABLED)
            .networkCachePolicy(CachePolicy.DISABLED)
            .fetcherCoroutineContext(Dispatchers.Main.immediate)
            .decoderCoroutineContext(Dispatchers.Main.immediate)
            .interceptorCoroutineContext(Dispatchers.Main.immediate)
            .build()
    }
}

/** Hands the singleton loader back to the application between tests. */
@OptIn(DelicateCoilApi::class)
internal fun resetDemoImageLoader() {
    SingletonImageLoader.reset()
}
