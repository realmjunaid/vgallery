package com.vgallery.app.data.gallery

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.vgallery.app.data.gallery.MoveResult
import com.vgallery.app.data.gallery.TrashedItem
import com.vgallery.app.data.preferences.AppPreferences
import com.vgallery.app.data.gallery.filterTrashed
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of a MediaStore delete request. */
sealed interface DeleteResult {
    data object Deleted : DeleteResult
    /**
     * System consent dialog required (Android 10+): launch the sender, then call
     * [DeviceGalleryRepository.deleteMedia] again with [pending] on OK. [pending] holds the
     * primary-volume URIs that still need deleting (not the synthetic `external` ones).
     */
    data class NeedsConsent(val intentSender: android.content.IntentSender, val pending: List<Uri>) : DeleteResult
    data class Failed(val message: String) : DeleteResult
}

/**
 * The read permissions for [sdkInt], as a pure function so every API level is unit-testable
 * offline (a Robolectric check can only cover the version it runs on).
 *
 * API 33+ splits access into READ_MEDIA_IMAGES and READ_MEDIA_VIDEO; API 34+ also wants
 * READ_MEDIA_VISUAL_USER_SELECTED, which is what makes the "Select photos" option appear at all
 * (a partial grant is still a working gallery). API 32 and below uses READ_EXTERNAL_STORAGE.
 */
fun deviceGalleryReadPermissionsFor(sdkInt: Int): Array<String> = when {
    sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
    )
    sdkInt >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO
    )
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

/** [deviceGalleryReadPermissionsFor] for the phone the app is running on. */
fun deviceGalleryReadPermissions(): Array<String> =
    deviceGalleryReadPermissionsFor(Build.VERSION.SDK_INT)

/**
 * WRITE_EXTERNAL_STORAGE, only while it is both declared and useful: on Android 10 (API 29) it is
 * what makes MediaStore offer its per-file delete consent dialog instead of a bare
 * SecurityException. Android 11 and 12 ignore it, and the manifest caps it at API 29 — asking for
 * an undeclared permission there makes the whole request fail, which used to leave Android 11/12
 * users stuck on the permission prompt forever.
 */
fun deviceGalleryPermissionsFor(sdkInt: Int): Array<String> =
    if (sdkInt <= Build.VERSION_CODES.Q) {
        deviceGalleryReadPermissionsFor(sdkInt) + Manifest.permission.WRITE_EXTERNAL_STORAGE
    } else {
        deviceGalleryReadPermissionsFor(sdkInt)
    }

/** Everything the one runtime request should include on this phone. */
fun deviceGalleryPermissions(): Array<String> =
    deviceGalleryPermissionsFor(Build.VERSION.SDK_INT)

/**
 * True when the gallery can be read. Any granted read permission is enough: the granular ones are
 * independent (a phone may allow photos but not videos) and Android 14's "Select photos" grants
 * only READ_MEDIA_VISUAL_USER_SELECTED. Requiring all of them is what used to leave a phone that
 * allowed exactly one of them stuck on the permission prompt with an empty gallery behind it.
 */
fun hasDeviceGalleryPermission(context: Context): Boolean =
    deviceGalleryReadPermissions().any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

/** MediaStore's provider authority ("content://media/..."). */
private const val MEDIA_AUTHORITY = "media"

/**
 * Rewrites a synthetic-volume MediaStore URI (`content://media/external/…`) to the real primary
 * volume (`content://media/external_primary/…`), keeping the item id and collection.
 *
 * The synthetic `external` volume is readable but not writable, and MediaStore's batch delete
 * request rejects it outright — so a permanent delete (and the consent dialog that follows it)
 * has to address `external_primary`. Anything already on a real volume is returned untouched.
 */
internal fun mediaStorePrimaryUri(uri: Uri): Uri {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return uri
    if (uri.authority != MEDIA_AUTHORITY) return uri
    val segments = uri.pathSegments
    if (segments.size != 4 || segments[0] != MediaStore.VOLUME_EXTERNAL || segments[2] != "media") return uri
    val id = segments[3].toLongOrNull() ?: return uri
    val collection = when (segments[1]) {
        "images" -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        "video" -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        "audio" -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        "files" -> MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else -> return uri
    }
    return ContentUris.withAppendedId(collection, id)
}

@Singleton
class DeviceGalleryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences
) {
    fun hasPermission(): Boolean = hasDeviceGalleryPermission(context)

    fun requiredPermissions(): Array<String> = deviceGalleryPermissions()

    /** In-memory snapshot: album switches filter this instantly (no rescan, no spinner). */
    @Volatile
    private var cachedPhotos: List<DevicePhotoRow>? = null

    /**
     * All device photos and videos. Empty when permission is missing —
     * the UI shows the permission prompt instead of crashing.
     * [fresh] rescans MediaStore (first load / pull-to-refresh); otherwise the cache is used.
     *
     * A scan never makes the gallery smaller than it has to be: a collection whose query failed
     * keeps its previous rows ([mergeScan]), and a scan that came back with nothing while rows
     * were already known is asked once more before it is believed — MediaStore answers mid-scan,
     * and publishing that would drop folders until the next refresh put them back.
     */
    suspend fun loadAllPhotos(fresh: Boolean = false): List<DevicePhotoRow> = withContext(Dispatchers.IO) {
        if (!hasPermission()) {
            cachedPhotos = null
            return@withContext emptyList()
        }
        if (!fresh) {
            cachedPhotos?.let { return@withContext it }
        }
        val previous = cachedPhotos.orEmpty()
        var images = loadImages()
        var videos = loadVideos()
        if (shouldRetryEmptyScan(images, videos, previous)) {
            images = loadImages()
            videos = loadVideos()
        }
        val rows = mergeScan(previous, images, videos)
        // Respect the Settings sort order (default A–Z); the queries stay newest-first.
        val sorted = sortDevicePhotos(rows, appPreferences.settings.first().sortOrder)
        // Hide anything sitting in the Trash until it is restored or permanently deleted.
        val trashed = appPreferences.trashedMedia.first().map { it.uri }.toSet()
        // Animated WebPs keep the tab a previous scan already found for them, so a refresh does
        // not shuffle them (and their folders) between Photos and Videos.
        val visible = applyAnimatedWebpFlags(
            filterTrashed(sorted, trashed),
            appPreferences.animatedWebps.first()
        )
        // A fresh scan also spots folders added since last time (the home green dot).
        refreshFolderMarkers(visible)
        cachedPhotos = visible
        visible
    }

    /**
     * Reconciles the device's folder names with the persisted markers. The first scan only
     * seeds the baseline (no dots); later scans flag folders that were not there before.
     */
    private suspend fun refreshFolderMarkers(rows: List<DevicePhotoRow>) {
        val names = rows.map { it.bucketName.ifBlank { "Unknown" } }.toSet()
        val state = FolderMarkerState(
            known = appPreferences.knownFolders.first(),
            flagged = appPreferences.newFolders.first()
        )
        val next = reconcileFolderMarkers(names, state)
        if (next != state) appPreferences.updateFolderMarkers(next.known, next.flagged)
    }

    /** Folder names currently flagged "new" (never opened): the home green dot. */
    suspend fun newFolderNames(): Set<String> = appPreferences.newFolders.first()

    /** Marks a folder opened, clearing its "new" dot. */
    suspend fun markFolderOpened(name: String): Boolean = appPreferences.clearNewFolder(name)

    private fun loadImages(): ScanPart {
        val rows = mutableListOf<DevicePhotoRow>()
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
        var ok = false
        try {
            context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                val addedCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                val widthCol = cursor.getColumnIndex(MediaStore.Images.Media.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.Images.Media.HEIGHT)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(collection, id)
                    val mime = cursor.getString(mimeCol) ?: ""
                    rows.add(
                        DevicePhotoRow(
                            id = id,
                            uri = uri,
                            name = cursor.getString(nameCol) ?: "Image",
                            bucketName = cursor.getString(bucketCol)?.takeIf { it.isNotBlank() } ?: "Unknown",
                            dateModified = cursor.getLong(dateCol),
                            size = try { cursor.getLong(sizeCol) } catch (_: Exception) { 0L },
                            mimeType = mime,
                            width = if (widthCol >= 0) try { cursor.getInt(widthCol) } catch (_: Exception) { 0 } else 0,
                            height = if (heightCol >= 0) try { cursor.getInt(heightCol) } catch (_: Exception) { 0 } else 0,
                            dateAdded = if (addedCol >= 0) try { cursor.getLong(addedCol) * 1000L } catch (_: Exception) { 0L } else 0L
                        )
                    )
                }
                ok = true
            }
        } catch (_: SecurityException) {
            // Denied or half-read: a failed query, never "this kind of media does not exist".
        } catch (_: Exception) {
            // Same: the previous rows for this kind are kept instead of losing their folders.
        }
        return ScanPart(rows, ok)
    }

    private fun loadVideos(): ScanPart {
        val rows = mutableListOf<DevicePhotoRow>()
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT
        )
        val sortOrder = "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
        var ok = false
        try {
            context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                val addedCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val widthCol = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    // Offset video IDs so album/grid keys never collide with image IDs.
                    rows.add(
                        DevicePhotoRow(
                            id = -(id + 1),
                            uri = ContentUris.withAppendedId(collection, id),
                            name = cursor.getString(nameCol) ?: "Video",
                            bucketName = cursor.getString(bucketCol)?.takeIf { it.isNotBlank() } ?: "Unknown",
                            dateModified = cursor.getLong(dateCol),
                            size = try { cursor.getLong(sizeCol) } catch (_: Exception) { 0L },
                            // These rows came out of the video table, so they are videos whatever
                            // the stored MIME says: a missing or wrong MIME used to file one under
                            // Photos, which left its folder missing from the Videos tab.
                            mimeType = cursor.getString(mimeCol)?.takeIf { it.startsWith("video/") } ?: "video/*",
                            durationMs = try { cursor.getLong(durationCol) } catch (_: Exception) { 0L },
                            width = if (widthCol >= 0) try { cursor.getInt(widthCol) } catch (_: Exception) { 0 } else 0,
                            height = if (heightCol >= 0) try { cursor.getInt(heightCol) } catch (_: Exception) { 0 } else 0,
                            dateAdded = if (addedCol >= 0) try { cursor.getLong(addedCol) * 1000L } catch (_: Exception) { 0L } else 0L
                        )
                    )
                }
                ok = true
            }
        } catch (_: SecurityException) {
            // Denied or half-read: a failed query, never "this kind of media does not exist".
        } catch (_: Exception) {
            // Same: the previous rows for this kind are kept instead of losing their folders.
        }
        return ScanPart(rows, ok)
    }

    /** Reads the first bytes of a WebP file and checks the VP8X animation flag. */
    private fun isAnimatedWebpUri(uri: android.net.Uri): Boolean {        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val header = ByteArray(24)
                var read = 0
                while (read < header.size) {
                    val n = input.read(header, read, header.size - read)
                    if (n <= 0) break
                    read += n
                }
                isAnimatedWebpHeader(header)
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    /** The current in-memory snapshot (for background flag refreshes). */
    fun cachedSnapshot(): List<DevicePhotoRow> = cachedPhotos.orEmpty()

    /**
     * Permanently deletes [uris] from the device.
     *
     * Files the app owns (and everything on Android 9 and below) go away straight away. Other apps'
     * media needs the user's approval: Android 10 asks per file through [RecoverableSecurityException],
     * Android 11+ asks once for the whole batch through `createDeleteRequest` (the system performs
     * that deletion itself). [DeleteResult.NeedsConsent] carries the still-pending URIs to retry with.
     *
     * The outcome is verified before reporting success, so [DeleteResult.Deleted] really means the
     * rows are gone; anything still listed comes back as [DeleteResult.Failed]. The cache is dropped
     * on success so the next load rescans.
     */
    suspend fun deleteMedia(uris: List<Uri>): DeleteResult = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext DeleteResult.Deleted
        // Address the writable primary volume, never the read-only synthetic `external` one.
        val targets = uris.map { mediaStorePrimaryUri(it) }
        val blocked = mutableListOf<Uri>()
        var message: String? = null

        for (uri in targets) {
            try {
                context.contentResolver.delete(uri, null, null)
            } catch (e: RecoverableSecurityException) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    // Android 10 grants write access one file at a time; keep the rest queued.
                    return@withContext DeleteResult.NeedsConsent(
                        e.userAction.actionIntent.intentSender,
                        targets
                    )
                }
                blocked += uri
            } catch (e: SecurityException) {
                blocked += uri
                if (message == null) message = e.message ?: "Permission denied"
            } catch (e: Exception) {
                blocked += uri
                if (message == null) message = e.message ?: "Delete failed"
            }
        }

        if (blocked.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // One system dialog for the whole batch; on approval the system deletes the files.
            return@withContext try {
                val pending = MediaStore.createDeleteRequest(context.contentResolver, blocked)
                DeleteResult.NeedsConsent(pending.intentSender, blocked)
            } catch (e: Exception) {
                DeleteResult.Failed(message ?: e.message ?: "Delete failed")
            }
        }

        val left = targets.filter { mediaExists(it) }
        if (left.isNotEmpty()) {
            return@withContext DeleteResult.Failed(
                message ?: "Couldn't delete ${left.size} item(s)"
            )
        }
        cachedPhotos = null
        DeleteResult.Deleted
    }

    /** The URIs from [uris] MediaStore no longer lists: used to prune the Trash after a delete round. */
    suspend fun goneUris(uris: List<Uri>): List<Uri> = withContext(Dispatchers.IO) {
        uris.filter { mediaDefinitelyGone(mediaStorePrimaryUri(it)) }
    }

    /** True while MediaStore still lists [uri]; an unreadable URI counts as gone. */
    private fun mediaExists(uri: Uri): Boolean = try {
        context.contentResolver
            .query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)
            ?.use { it.moveToFirst() } ?: false
    } catch (_: Exception) {
        false
    }

    /**
     * True only when MediaStore definitively no longer lists [uri]. A failed or empty query reads as
     * "still there": this drives the daily trash prune, where reading a permission error or a
     * provider hiccup as "gone" would empty the user's Trash while the files are still on disk.
     */
    private fun mediaDefinitelyGone(uri: Uri): Boolean = try {
        context.contentResolver
            .query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)
            ?.use { !it.moveToFirst() } ?: false
    } catch (_: Exception) {
        false
    }

    // ── Trash ─────────────────────────────────────────────────────────────────────────

    /** Trashed items, oldest first (delegates to AppPreferences). */
    val trashedMedia: kotlinx.coroutines.flow.Flow<List<TrashedItem>> = appPreferences.trashedMedia

    suspend fun trashMedia(items: List<TrashedItem>): Boolean = appPreferences.trashMedia(items)

    suspend fun restoreTrashedMedia(uris: List<String>): Boolean = appPreferences.restoreTrashedMedia(uris)

    suspend fun removeTrashedMedia(uris: List<String>): Boolean = appPreferences.removeTrashedMedia(uris)

    suspend fun clearTrash(): Boolean = appPreferences.clearTrash()

    // ── Move to folder ────────────────────────────────────────────────────────────────


    private fun mediaCollections(): List<Uri> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            listOf(
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            )
        } else {
            listOf(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            )
        }

    /**
     * Fills in animated-WebP flags after the first paint: opening one stream per
     * WebP is slow, so it never blocks the initial scan. Returns new row copies.
     *
     * What it finds is remembered ([AppPreferences.updateAnimatedWebps]), so the next scan starts
     * with those files already in the Videos tab instead of showing them under Photos first.
     */
    suspend fun sniffAnimatedWebps(rows: List<DevicePhotoRow>): List<DevicePhotoRow> =
        withContext(Dispatchers.IO) {
            if (rows.none { it.mimeType == "image/webp" && !it.isAnimatedWebp }) return@withContext rows
            val animated = mutableSetOf<String>()
            val refreshed = rows.map { row ->
                if (row.mimeType == "image/webp" && !row.isAnimatedWebp && isAnimatedWebpUri(row.uri)) {
                    animated += row.uri.toString()
                    row.copy(isAnimatedWebp = true)
                } else {
                    row
                }
            }
            if (animated.isEmpty()) return@withContext rows
            appPreferences.updateAnimatedWebps(webpUris(rows), animated)
            // Keep the cache in sync so album switches stay instant AND flagged.
            cachedPhotos?.let { cached ->
                if (cached.size == refreshed.size) cachedPhotos = refreshed
            }
            refreshed
        }

    companion object {
        const val ALL_PHOTOS_ALBUM = "All Photos"
    }
}
