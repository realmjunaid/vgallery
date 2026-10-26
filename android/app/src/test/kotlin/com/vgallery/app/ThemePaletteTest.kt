package com.vgallery.app

import androidx.compose.ui.graphics.Color
import com.vgallery.app.data.preferences.AccentChoice
import com.vgallery.app.ui.theme.galleryColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The palette, tested as pure data: the light theme must be light, the AMOLED option must be pure
 * black, and every accent must land on a label colour that can actually be read on top of it.
 */
class ThemePaletteTest {

    @Test
    fun amoledIsPureBlackAndLiftedDarkIsNot() {
        val amoled = galleryColors(dark = true, amoled = true)
        val lifted = galleryColors(dark = true, amoled = false)

        assertEquals(Color(0xFF000000), amoled.background)
        assertNotEquals(amoled.background, lifted.background)
        // Surfaces stay distinguishable from the background in both.
        assertNotEquals(amoled.background, amoled.surface)
        assertNotEquals(lifted.background, lifted.surface)
    }

    @Test
    fun lightThemeIsLightAndReadable() {
        val light = galleryColors(dark = false)

        assertEquals(Color(0xFF1A1A1A), light.textPrimary)
        assertNotEquals(light.background, light.surface)
        // Body text must not sit on a background of the same colour.
        assertNotEquals(light.textPrimary, light.background)
        assertNotEquals(light.textSecondary, light.surface)
    }

    @Test
    fun monochromeAccentFlipsWithTheTheme() {
        val dark = galleryColors(dark = true, accent = AccentChoice.MONOCHROME)
        val light = galleryColors(dark = false, accent = AccentChoice.MONOCHROME)

        assertEquals(Color(0xFFFFFFFF), dark.accent)
        assertEquals(Color(0xFF1A1A1A), light.accent)
        // And the label on top of each is the readable opposite.
        assertEquals(Color(0xFF111111), dark.onAccent)
        assertEquals(Color(0xFFF7F7F7), light.onAccent)
    }

    @Test
    fun everyAccentResolvesToAColorWithAReadableLabel() {
        AccentChoice.entries.forEach { choice ->
            listOf(true, false).forEach { dark ->
                val colors = galleryColors(dark = dark, accent = choice)
                assertNotEquals(
                    "accent $choice in dark=$dark has the same colour as its label",
                    colors.accent,
                    colors.onAccent
                )
                // The label must contrast with the accent it sits on: anything closer than this
                // would be a dot on a dot.
                val contrast = contrastRatio(colors.accent, colors.onAccent)
                assertTrue(
                    "accent $choice in dark=$dark has a label contrast of $contrast",
                    contrast > 2.0
                )
            }
        }
    }

    @Test
    fun functionalColoursAreNeutralInDarkAndMeaningfulInLight() {
        val dark = galleryColors(dark = true)
        val light = galleryColors(dark = false)

        // The dark theme is deliberately monochrome: errors read as text, not as red.
        assertEquals(dark.textPrimary, dark.error)
        // The light theme uses real signal colours.
        assertNotEquals(light.textPrimary, light.error)
        assertNotEquals(light.textPrimary, light.success)
        assertNotEquals(light.textPrimary, light.warning)
    }

    /** WCAG relative-luminance ratio, so the accent labels can be held to a real standard. */
    private fun contrastRatio(left: Color, right: Color): Double {
        fun channel(value: Float): Double {
            val c = value.toDouble()
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }

        fun luminance(color: Color): Double =
            0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

        val a = luminance(left)
        val b = luminance(right)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }
}
