package com.vgallery.app.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.vgallery.app.data.preferences.AccentChoice
import com.vgallery.app.data.preferences.ThemeMode
import com.vgallery.app.data.preferences.ThemeSettings

/** The whole app in one call: theme choice in, palette and Material colours out. */
@Composable
fun VGalleryTheme(
    settings: ThemeSettings = ThemeSettings(),
    content: @Composable () -> Unit,
) {
    val dark = when (settings.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val systemAccent = settings.accent.systemAccent(context, dark)
    VGalleryTheme(
        colors = galleryColors(
            dark = dark,
            amoled = settings.amoledBlack,
            accent = settings.accent,
            systemAccent = systemAccent
        ),
        content = content
    )
}

/** The same theme with a palette that is already decided (used by tests and by the viewer). */
@Composable
fun VGalleryTheme(
    colors: GalleryColors,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalGalleryColors provides colors) {
        MaterialTheme(
            colorScheme = colors.materialColorScheme(),
            typography = Typography,
            content = content
        )
    }
}

/**
 * A dark island inside a light app: the full-screen viewer, which stays black in every theme (a
 * photograph judged against a white backdrop reads wrong). The chosen accent is kept, because it
 * only ever lands on that black.
 */
@Composable
fun DarkViewerTheme(content: @Composable () -> Unit) {
    val current = LocalGalleryColors.current
    VGalleryTheme(
        colors = galleryColors(
            dark = true,
            amoled = true,
            accent = current.accentChoice,
            systemAccent = null
        ),
        content = content
    )
}

/** The wallpaper's colour, where the platform offers one (Android 12+). */
private fun AccentChoice.systemAccent(context: Context, dark: Boolean): Color? {
    if (this != AccentChoice.SYSTEM || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    return if (dark) dynamicDarkColorScheme(context).primary else dynamicLightColorScheme(context).primary
}

/** Material's scheme, derived from the palette: one mapping, whichever way the theme went. */
private fun GalleryColors.materialColorScheme() = if (isDark) {
    darkColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentStrong,
        onPrimaryContainer = textPrimary,
        secondary = accentSoft,
        onSecondary = onAccent,
        secondaryContainer = surfaceElevated,
        onSecondaryContainer = textSecondary,
        tertiary = accentFaint,
        background = background,
        onBackground = textPrimary,
        surface = surface,
        onSurface = textPrimary,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = textSecondary,
        error = error,
        onError = onAccent,
        outline = textDisabled,
        outlineVariant = surfaceElevated
    )
} else {
    lightColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentStrong,
        onPrimaryContainer = textPrimary,
        secondary = accentSoft,
        onSecondary = textPrimary,
        secondaryContainer = surfaceElevated,
        onSecondaryContainer = textSecondary,
        tertiary = accentFaint,
        background = background,
        onBackground = textPrimary,
        surface = surface,
        onSurface = textPrimary,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = textSecondary,
        error = error,
        onError = onAccent,
        outline = textDisabled,
        outlineVariant = surfaceElevated
    )
}
