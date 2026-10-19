package com.vgallery.app.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.vgallery.app.data.gallery.TrashedItem
import com.vgallery.app.data.gallery.appendTrashEntries
import com.vgallery.app.data.gallery.decodeTrashList
import com.vgallery.app.data.gallery.encodeTrashItem
import com.vgallery.app.data.gallery.removeTrashEntries
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** The settings file name (`datastore/vgallery_settings.preferences_pb`), unchanged since the first release. */
const val SETTINGS_DATASTORE_NAME = "vgallery_settings"

enum class SortOrder {
    NAME_ASC, NAME_DESC, DATE_NEWEST, DATE_OLDEST, SIZE_LARGEST, SIZE_SMALLEST, TYPE_VIDEO_FIRST, TYPE_IMAGE_FIRST
}

enum class AspectRatioMode {
    SMART, FILL, FIT
}

data class AppSettings(
    val loopVideos: Boolean = true,
    val shuffleMedia: Boolean = false,
    val sortOrder: SortOrder = SortOrder.NAME_ASC,
    val autoAdvanceImages: Boolean = false,
    val autoAdvanceDelaySeconds: Int = 5,
    val hapticFeedback: Boolean = true,
    val showFileName: Boolean = true,
    val aspectRatioMode: AspectRatioMode = AspectRatioMode.SMART,
    val landscapeVideoMode: Boolean = false
)

/**
 * Settings, Recents and Trash over the app's single settings [DataStore] (provided by `AppModule`,
 * with a corruption handler that replaces an unreadable file with empty preferences).
 *
 * Storage failures never crash: a read `IOException` falls back to defaults, and every edit
 * returns `false` instead of throwing, so the UI keeps its last-known value.
 */
@Singleton
class AppPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val LOOP_VIDEOS = booleanPreferencesKey("loop_videos")
        val SHUFFLE_MEDIA = booleanPreferencesKey("shuffle_media")
        val SORT_ORDER = stringPreferencesKey("sort_order")
        val AUTO_ADVANCE_IMAGES = booleanPreferencesKey("auto_advance_images")
        val AUTO_ADVANCE_DELAY = intPreferencesKey("auto_advance_delay")
        val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
        val SHOW_FILE_NAME = booleanPreferencesKey("show_file_name")
        val ASPECT_RATIO_MODE = stringPreferencesKey("aspect_ratio_mode")
        val LANDSCAPE_VIDEO_MODE = booleanPreferencesKey("landscape_video_mode")
        val RECENT_FOLDERS = stringPreferencesKey("recent_folders")
        val TRASHED_MEDIA = stringPreferencesKey("trashed_media")
        val NEW_FOLDERS = stringSetPreferencesKey("new_folders")
        val KNOWN_FOLDERS = stringSetPreferencesKey("known_folders")
        val ANIMATED_WEBPS = stringSetPreferencesKey("animated_webps")
        val UPDATE_CHECK_ENABLED = booleanPreferencesKey("update_check_enabled")
        val UPDATE_LAST_CHECK = longPreferencesKey("update_last_check")
        val UPDATE_SKIPPED_VERSION = stringPreferencesKey("update_skipped_version")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT_CHOICE = stringPreferencesKey("accent_choice")
        val AMOLED_BLACK = booleanPreferencesKey("amoled_black")
    }

    /** The stored preferences; an `IOException` while reading yields empty preferences (defaults). */
    private val data: Flow<Preferences> = dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    val settings: Flow<AppSettings> = data.map { prefs ->
        AppSettings(
            loopVideos = prefs[Keys.LOOP_VIDEOS] ?: true,
            shuffleMedia = prefs[Keys.SHUFFLE_MEDIA] ?: false,
            sortOrder = try {
                SortOrder.valueOf(prefs[Keys.SORT_ORDER] ?: SortOrder.NAME_ASC.name)
            } catch (_: Exception) {
                SortOrder.NAME_ASC
            },
            autoAdvanceImages = prefs[Keys.AUTO_ADVANCE_IMAGES] ?: false,
            autoAdvanceDelaySeconds = prefs[Keys.AUTO_ADVANCE_DELAY] ?: 5,
            hapticFeedback = prefs[Keys.HAPTIC_FEEDBACK] ?: true,
            showFileName = prefs[Keys.SHOW_FILE_NAME] ?: true,
            aspectRatioMode = try {
                AspectRatioMode.valueOf(prefs[Keys.ASPECT_RATIO_MODE] ?: AspectRatioMode.SMART.name)
            } catch (_: Exception) {
                AspectRatioMode.SMART
            },
            landscapeVideoMode = prefs[Keys.LANDSCAPE_VIDEO_MODE] ?: false
        )
    }

    val recentFolders: Flow<List<String>> = data.map { prefs ->
        val raw = prefs[Keys.RECENT_FOLDERS] ?: ""
        if (raw.isBlank()) emptyList() else raw.split("|||")
    }

    /** Trashed items, oldest first (insertion order preserved by the store). */
    val trashedMedia: Flow<List<TrashedItem>> = data.map { prefs ->
        decodeTrashList(prefs[Keys.TRASHED_MEDIA] ?: "")
    }

    /** Folder names flagged "new" (detected on the device but never opened); drives the green dot. */
    val newFolders: Flow<Set<String>> = data.map { prefs ->
        prefs[Keys.NEW_FOLDERS] ?: emptySet()
    }

    /** Folder names seen at least once: the baseline for spotting folders added later. */
    val knownFolders: Flow<Set<String>> = data.map { prefs ->
        prefs[Keys.KNOWN_FOLDERS] ?: emptySet()
    }

    /**
     * URIs of the WebP files known to animate. MediaStore files an animated WebP as an image, and
     * only the header sniff tells the truth, so this is remembered: a rescan can then put each one
     * straight into its own tab instead of moving it from Photos to Videos a second later.
     */
    val animatedWebps: Flow<Set<String>> = data.map { prefs ->
        prefs[Keys.ANIMATED_WEBPS] ?: emptySet()
    }

    /** Runs an edit; returns `false` (and changes nothing) if storage fails. */
    private suspend fun safeEdit(transform: suspend (MutablePreferences) -> Unit): Boolean =
        try {
            dataStore.edit(transform)
            true
        } catch (_: IOException) {
            false
        }

    suspend fun updateLoopVideos(value: Boolean): Boolean =
        safeEdit { it[Keys.LOOP_VIDEOS] = value }

    suspend fun updateShuffleMedia(value: Boolean): Boolean =
        safeEdit { it[Keys.SHUFFLE_MEDIA] = value }

    suspend fun updateSortOrder(order: SortOrder): Boolean =
        safeEdit { it[Keys.SORT_ORDER] = order.name }

    suspend fun updateAutoAdvanceImages(value: Boolean): Boolean =
        safeEdit { it[Keys.AUTO_ADVANCE_IMAGES] = value }

    suspend fun updateAutoAdvanceDelay(seconds: Int): Boolean =
        safeEdit { it[Keys.AUTO_ADVANCE_DELAY] = seconds }

    suspend fun updateHapticFeedback(value: Boolean): Boolean =
        safeEdit { it[Keys.HAPTIC_FEEDBACK] = value }

    suspend fun updateShowFileName(value: Boolean): Boolean =
        safeEdit { it[Keys.SHOW_FILE_NAME] = value }

    suspend fun updateAspectRatioMode(mode: AspectRatioMode): Boolean =
        safeEdit { it[Keys.ASPECT_RATIO_MODE] = mode.name }

    suspend fun updateLandscapeVideoMode(value: Boolean): Boolean =
        safeEdit { it[Keys.LANDSCAPE_VIDEO_MODE] = value }

    suspend fun addRecentFolder(uriString: String, displayName: String): Boolean = safeEdit { prefs ->
        val existing = (prefs[Keys.RECENT_FOLDERS] ?: "")
            .split("|||")
            .filter { it.isNotBlank() && !it.startsWith("$uriString<<>>") }
        val entry = "$uriString<<>>$displayName<<>>0"
        val updated = (listOf(entry) + existing).take(10)
        prefs[Keys.RECENT_FOLDERS] = updated.joinToString("|||")
    }

    /**
     * Saves the viewer position for a listed folder: the full-list [index] and, when known, the
     * item's URI ([itemUri], the optional 4th field). Unlisted folders are left alone.
     */
    suspend fun updateLastViewed(folderUri: String, index: Int, itemUri: String? = null): Boolean = safeEdit { prefs ->
        val allFolders = (prefs[Keys.RECENT_FOLDERS] ?: "")
            .split("|||")
            .filter { it.isNotBlank() }

        val updated = allFolders.map { entry ->
            if (entry.startsWith("$folderUri<<>>")) {
                val parts = entry.split("<<>>")
                if (parts.size >= 2) {
                    val base = "${parts[0]}<<>>${parts[1]}<<>>$index"
                    if (itemUri.isNullOrEmpty()) base else "$base<<>>$itemUri"
                } else entry
            } else {
                entry
            }
        }
        prefs[Keys.RECENT_FOLDERS] = updated.joinToString("|||")
    }

    suspend fun removeRecentFolder(uriString: String): Boolean = safeEdit { prefs ->
        val existing = (prefs[Keys.RECENT_FOLDERS] ?: "")
            .split("|||")
            .filter { it.isNotBlank() && !it.startsWith("$uriString<<>>") }
        prefs[Keys.RECENT_FOLDERS] = existing.joinToString("|||")
    }

    /** The Recent entry for [folderUri], if listed. */
    suspend fun recentFolder(folderUri: String): RecentFolderInfo? =
        recentFolders.first()
            .find { it.startsWith("$folderUri<<>>") }
            ?.let { parseRecentFolderEntry(it) }

    /** Parses `uri<<>>name<<>>index` (every released version) or `uri<<>>name<<>>index<<>>itemUri`. */
    fun parseRecentFolderEntry(entry: String): RecentFolderInfo? {
        val parts = entry.split("<<>>")
        return if (parts.size >= 2) {
            val lastIndex = parts.getOrNull(2)?.toIntOrNull() ?: 0
            val lastItemUri = parts.getOrNull(3)?.takeIf { it.isNotEmpty() }
            RecentFolderInfo(parts[0], parts[1], lastIndex, lastItemUri)
        } else null
    }

    // ── Updates ───────────────────────────────────────────────────────────────────────────

    /**
     * Whether the app may ask GitHub for a new release when it opens (on unless turned off). This is
     * the switch that keeps "no network unless you allow it" honest for offline-only users.
     */
    val updateCheckEnabled: Flow<Boolean> = data.map { prefs ->
        prefs[Keys.UPDATE_CHECK_ENABLED] ?: true
    }

    /** When the last check ran, so launches close together never hit the network twice. */
    val lastUpdateCheck: Flow<Long> = data.map { prefs ->
        prefs[Keys.UPDATE_LAST_CHECK] ?: 0L
    }

    /** The release version the user dismissed, so the exact same one is never offered again. */
    val skippedUpdate: Flow<String> = data.map { prefs ->
        prefs[Keys.UPDATE_SKIPPED_VERSION] ?: ""
    }

    suspend fun updateUpdateCheckEnabled(value: Boolean): Boolean =
        safeEdit { it[Keys.UPDATE_CHECK_ENABLED] = value }

    suspend fun updateLastUpdateCheck(timeMs: Long): Boolean =
        safeEdit { it[Keys.UPDATE_LAST_CHECK] = timeMs }

    suspend fun updateSkippedUpdate(version: String): Boolean =
        safeEdit { it[Keys.UPDATE_SKIPPED_VERSION] = version }

    // ── Appearance ────────────────────────────────────────────────────────────────────────

    /**
     * How the app looks. Defaults are the look VGallery shipped with (follow the phone, monochrome
     * accent, pure black), so an install that never touches Appearance is unchanged. An unknown
     * stored value falls back the same way rather than crashing.
     */
    val themeSettings: Flow<ThemeSettings> = data.map { prefs ->
        ThemeSettings(
            mode = prefs[Keys.THEME_MODE].toEnumOrDefault(ThemeMode.SYSTEM),
            accent = prefs[Keys.ACCENT_CHOICE].toEnumOrDefault(AccentChoice.MONOCHROME),
            amoledBlack = prefs[Keys.AMOLED_BLACK] ?: true
        )
    }

    private inline fun <reified T : Enum<T>> String?.toEnumOrDefault(fallback: T): T =
        this?.let { stored -> enumValues<T>().firstOrNull { it.name == stored } } ?: fallback

    suspend fun updateThemeMode(mode: ThemeMode): Boolean =
        safeEdit { it[Keys.THEME_MODE] = mode.name }

    suspend fun updateAccent(accent: AccentChoice): Boolean =
        safeEdit { it[Keys.ACCENT_CHOICE] = accent.name }

    suspend fun updateAmoledBlack(value: Boolean): Boolean =
        safeEdit { it[Keys.AMOLED_BLACK] = value }

    // ── Trash ─────────────────────────────────────────────────────────────────────────────

    /**
     * Moves items to trash: replaces any existing entry for the same uri, preserving
     * insertion order for items that are re-trashed.
     */
    suspend fun trashMedia(items: List<TrashedItem>): Boolean = safeEdit { prefs ->
        val raw = prefs[Keys.TRASHED_MEDIA] ?: ""
        prefs[Keys.TRASHED_MEDIA] = appendTrashEntries(raw, items)
    }

    /**
     * Restores items from trash: removes their entries. The read-modify-write happens inside
     * DataStore's own transaction, so a delete that lands at the same moment cannot be lost.
     */
    suspend fun restoreTrashedMedia(uris: List<String>): Boolean {
        if (uris.isEmpty()) return false
        return safeEdit { prefs ->
            prefs[Keys.TRASHED_MEDIA] = removeTrashEntries(prefs[Keys.TRASHED_MEDIA] ?: "", uris.toSet())
        }
    }

    /** Drops items from the trash list without touching MediaStore (used after a successful hard delete). */
    suspend fun removeTrashedMedia(uris: List<String>): Boolean {
        if (uris.isEmpty()) return false
        return safeEdit { prefs ->
            prefs[Keys.TRASHED_MEDIA] = removeTrashEntries(prefs[Keys.TRASHED_MEDIA] ?: "", uris.toSet())
        }
    }

    /** Clears the entire trash list. */
    suspend fun clearTrash(): Boolean = safeEdit { it[Keys.TRASHED_MEDIA] = "" }

    // ── New-folder markers ────────────────────────────────────────────────────────────────

    /** Stores the reconciled folder markers after a scan. */
    suspend fun updateFolderMarkers(known: Set<String>, flagged: Set<String>): Boolean = safeEdit { prefs ->
        prefs[Keys.KNOWN_FOLDERS] = known
        prefs[Keys.NEW_FOLDERS] = flagged
    }

    /** Marks [name] as opened: drops its "new folder" flag, keeping it known. */
    suspend fun clearNewFolder(name: String): Boolean = safeEdit { prefs ->
        val flagged = prefs[Keys.NEW_FOLDERS] ?: emptySet()
        if (name in flagged) prefs[Keys.NEW_FOLDERS] = flagged - name
    }

    // ── Animated WebP flags ───────────────────────────────────────────────────────────────

    /**
     * Records which WebPs animate. Only [presentWebpUris] are kept, so the stored set is bounded
     * by the WebP files actually on the device: deleted ones drop out on the next scan.
     */
    suspend fun updateAnimatedWebps(presentWebpUris: Set<String>, animated: Set<String>): Boolean =
        safeEdit { prefs ->
            val known = prefs[Keys.ANIMATED_WEBPS] ?: emptySet()
            prefs[Keys.ANIMATED_WEBPS] = (known intersect presentWebpUris) + animated
        }
}

data class RecentFolderInfo(
    val uri: String,
    val name: String,
    val lastIndex: Int,
    val lastItemUri: String? = null,
)
