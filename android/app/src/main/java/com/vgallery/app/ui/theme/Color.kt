package com.vgallery.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.vgallery.app.data.preferences.AccentChoice

/**
 * The app's own palette, in one object, provided through [LocalGalleryColors].
 *
 * Every screen reads it through the named accessors at the bottom of this file (`TextPrimary`,
 * `Violet500`, `Black`…), which is why the whole app follows the theme without a screen knowing
 * anything about themes: the names never changed, only where their values come from.
 */
data class GalleryColors(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val surfaceElevated: Color,
    val surfaceCard: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val textDisabled: Color,
    val accent: Color,
    val accentMid: Color,
    val accentSoft: Color,
    val accentFaint: Color,
    val accentStrong: Color,
    /** Readable on top of a filled [accent] surface (button labels, filled icons). */
    val onAccent: Color,
    val error: Color,
    val success: Color,
    val warning: Color,
    val newFolderDot: Color,
    val overlay: Color,
    val overlayGradientTop: Color,
    val overlayGradientBottom: Color,
    val isDark: Boolean,
    /** Kept so the full-screen viewer can stay dark while keeping the chosen accent. */
    val accentChoice: AccentChoice
)

// ── Fixed colours ─────────────────────────────────────────────────────────────────────────
// Not part of the palette, because they sit on the viewer's black backdrop, which is black in
// every theme: a photo judged against a white background reads wrong, so the viewer is never lit.

/** The viewer's backdrop. */
val ViewerBackdrop = Color(0xFF000000)

// ── Palette construction ──────────────────────────────────────────────────────────────────

private val NewFolderGreen = Color(0xFF34C759)

/** The accent each choice resolves to, per theme: bright in the dark, saturated in the light. */
private fun accentFor(choice: AccentChoice, dark: Boolean, systemAccent: Color?): Color = when (choice) {
    // Monochrome is the app's own look: white on black, black on white.
    AccentChoice.MONOCHROME -> if (dark) Color(0xFFFFFFFF) else Color(0xFF1A1A1A)
    AccentChoice.VIOLET -> if (dark) Color(0xFFA78BFA) else Color(0xFF7C3AED)
    AccentChoice.BLUE -> if (dark) Color(0xFF60A5FA) else Color(0xFF2563EB)
    AccentChoice.GREEN -> if (dark) Color(0xFF4ADE80) else Color(0xFF15803D)
    AccentChoice.AMBER -> if (dark) Color(0xFFFBBF24) else Color(0xFFB45309)
    AccentChoice.PINK -> if (dark) Color(0xFFF472B6) else Color(0xFFDB2777)
    // "System": the wallpaper's colour on Android 12+, and violet where it is not available.
    AccentChoice.SYSTEM -> systemAccent ?: if (dark) Color(0xFFA78BFA) else Color(0xFF7C3AED)
}

/**
 * Builds the palette for one theme. The accent's softer shades are mixed towards the background, so
 * "dimmer" means dimmer in the dark and paler in the light rather than always darker.
 */
fun galleryColors(
    dark: Boolean,
    amoled: Boolean = true,
    accent: AccentChoice = AccentChoice.MONOCHROME,
    systemAccent: Color? = null
): GalleryColors {
    val accentColor = accentFor(accent, dark, systemAccent)
    val background = if (dark) (if (amoled) Color(0xFF000000) else Color(0xFF0E0E11)) else Color(0xFFFAFAFA)
    val surface = if (dark) (if (amoled) Color(0xFF0A0A0A) else Color(0xFF15151A)) else Color(0xFFFFFFFF)
    val surfaceVariant = if (dark) (if (amoled) Color(0xFF141414) else Color(0xFF1D1D23)) else Color(0xFFF1F1F3)
    val surfaceElevated = if (dark) (if (amoled) Color(0xFF1A1A1A) else Color(0xFF23232A)) else Color(0xFFE7E7EA)
    return GalleryColors(
        background = background,
        surface = surface,
        surfaceVariant = surfaceVariant,
        surfaceElevated = surfaceElevated,
        surfaceCard = if (dark) (if (amoled) Color(0xFF1E1E1E) else Color(0xFF202027)) else Color(0xFFF4F4F6),
        textPrimary = if (dark) Color(0xFFE5E5E5) else Color(0xFF1A1A1A),
        textSecondary = if (dark) Color(0xFFA3A3A3) else Color(0xFF5C5C5C),
        textMuted = if (dark) Color(0xFF737373) else Color(0xFF8A8A8A),
        textDisabled = if (dark) Color(0xFF525252) else Color(0xFFBDBDBD),
        accent = accentColor,
        accentMid = lerp(accentColor, background, 0.18f),
        accentSoft = lerp(accentColor, background, 0.35f),
        accentFaint = lerp(accentColor, background, 0.72f),
        accentStrong = lerp(accentColor, background, 0.80f),
        // Dark accents (the light theme's monochrome accent, for instance) need a light label on
        // them; bright ones need a dark label.
        onAccent = if (accentColor.luminance() > 0.5f) Color(0xFF111111) else Color(0xFFF7F7F7),
        error = if (dark) Color(0xFFE5E5E5) else Color(0xFFB3261E),
        success = if (dark) Color(0xFFE5E5E5) else Color(0xFF1B5E20),
        warning = if (dark) Color(0xFFE5E5E5) else Color(0xFF8D5A00),
        newFolderDot = NewFolderGreen,
        // Always black: these sit on top of photographs, not on the app's own background.
        overlay = Color(0x80000000),
        overlayGradientTop = Color(0xB3000000),
        overlayGradientBottom = Color(0xCC000000),
        isDark = dark,
        accentChoice = accent
    )
}

/** Falls back to the monochrome dark palette, so a composable previewed on its own still looks right. */
val LocalGalleryColors = staticCompositionLocalOf { galleryColors(dark = true, amoled = true) }

// ── Names every screen already uses ───────────────────────────────────────────────────────

val Violet500: Color
    @Composable @ReadOnlyComposable get() = LocalGalleryColors.current.accent

val Violet600: Color
    @Composable @ReadOnlyComposable get() = LocalGalleryColors.current.accentMid

val Violet400: Color
    @Composable @ReadOnlyComposable get() = LocalGalleryColors.current.accentSoft

val Black: Color
    @Composable @ReadOnlyComposable get() = LocalGalleryColors.current.background

val SurfaceVariant: Color
    @Composable @ReadOnlyComposable get() = LocalGalleryColors.current.surfaceVariant

val SurfaceElevated: Color
    @Composable @ReadOnlyComposable get() = LocalGalleryColors.current.surfaceElevated

val SurfaceCard: Color
    @Composable @ReadOnlyComposable get() = LocalGalleryColors.current.surfaceCard

val TextPrimary: Color
    @Composable @ReadOnlyComposable get() = LocalGalleryColors.current.textPrimary

/** Readable on top of a filled [Violet500] surface (button labels, the chosen accent dot). */
val OnAccent: Color
    @Composable @ReadOnlyComposable get() = LocalGalleryColors.current.onAccent

val TextSecondary: Color
    @Composable @ReadOnlyComposable get() = LocalGalleryColors.current.textSecondary

val TextMuted: Color
    @Composable @ReadOnlyComposable get() = LocalGalleryColors.current.textMuted

val TextDisabled: Color
    @Composable @ReadOnlyComposable get() = LocalGalleryColors.current.textDisabled

val ErrorRed: Color
    @Composable @ReadOnlyComposable get() = LocalGalleryColors.current.error

val NewFolderDot: Color
    @Composable @ReadOnlyComposable get() = LocalGalleryColors.current.newFolderDot
