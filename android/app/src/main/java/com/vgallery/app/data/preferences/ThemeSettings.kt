package com.vgallery.app.data.preferences

/** Light, dark, or whatever the phone is set to. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** The accent the UI is tinted with. [SYSTEM] means the wallpaper's colour on Android 12+. */
enum class AccentChoice { MONOCHROME, VIOLET, BLUE, GREEN, AMBER, PINK, SYSTEM }

/**
 * How the app looks. The app shipped as an AMOLED-black, monochrome gallery, so those are the
 * defaults: anyone who never opens the Appearance section keeps exactly the old look.
 */
data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val accent: AccentChoice = AccentChoice.MONOCHROME,
    /** Pure #000000 surfaces instead of the lifted near-black ones. Dark theme only. */
    val amoledBlack: Boolean = true
)
