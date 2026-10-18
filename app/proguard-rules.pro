# VGallery ProGuard Rules
-keepattributes *Annotation*

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Media3 ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Coil
-keep class coil3.** { *; }
-dontwarn coil3.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# WorkManager instantiates workers by the class named in the WorkRequest.
-keep class * extends androidx.work.ListenableWorker { *; }

# Enum names are written to DataStore (sort, aspect, theme, accent). Renaming them would
# reset a user's settings on the next update.
-keepnames enum com.vgallery.app.data.preferences.SortOrder
-keepnames enum com.vgallery.app.data.preferences.AspectRatioMode
-keepnames enum com.vgallery.app.data.preferences.ThemeMode
-keepnames enum com.vgallery.app.data.preferences.AccentChoice
