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
import com.vgallery.app.data.preferences.AppPreferences
import com.vgallery.app.data.gallery.TrashedItem
import com.vgallery.app.data.gallery.filterTrashed
import dagger.hilt.android.qualifiers.ApplicationContext
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
     * [DeviceGalleryRepository.deleteMedia] again with [pending] on OK.
     */
    data class NeedsConsent(val intentSender: android.content.IntentSender, val pending: List<Uri>) : DeleteResult
    data class Failed(val message: String) : DeleteResult
}

/**
 * MediaStore permissions for the auto device gallery (no manual folder pick needed).
 *
 * API 33+ uses the granular READ_MEDIA_* permissions. Below that, READ_EXTERNAL_STORAGE
 * covers scanning, and WRITE_EXTERNAL_STORAGE is also requested because a direct (pre-consent)
 * delete on Android 10 needs it — without it, deleting throws a plain SecurityException
 * that used to surface as "Permission denied".
 */
fun deviceGalleryPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        // API 29-32 and pre-Q: read via READ_EXTERNAL_STORAGE; write (delete forever) needs WRITE on 29.
        arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }

fun hasDeviceGalleryPermission(context: Context): Boolean =
    deviceGalleryPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

@Singleton
class DeviceGalleryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences
) {
    fun hasPermission(): Boolean = hasDeviceGalleryPermission(context)

    fun requiredPermissions(): Array<String> = deviceGalleryPermissions()

    /** In-memory snapshot: album switches filter this instantly (no rescan, no spinner). */
    private var cachedPhotos: List<DevicePhotoRow>? = null

    /**
     * All device photos and videos. Empty when permission is missing —
     * the UI shows the permission prompt instead of crashing.
     * [fresh] rescans MediaStore (first load / pull-to-refresh); otherwise the cache is used.
     */
    suspend fun loadAllPhotos(fresh: Boolean = false): List<DevicePhotoRow> = withContext(Dispatchers.IO) {
        if (!hasPermission()) {
            cachedPhotos = null
            return@withContext emptyList()
        }
        if (!fresh) {
            cachedPhotos?.let { return@withContext it }
        }
        val rows = mutableListOf<DevicePhotoRow>()
        loadImages(rows)
        loadVideos(rows)
        // Respect the Settings sort order (default A–Z); the queries stay newest-first.
        val sorted = sortDevicePhotos(rows, appPreferences.settings.first().sortOrder)
        // Hide anything sitting in the Trash until it is restored or permanently deleted.
        val trashed = appPreferences.trashedMedia.first().map { it.uri }.toSet()
        filterTrashed(sorted, trashed)
            .also { cachedPhotos = it }
    }

    private fun loadImages(rows: MutableList<DevicePhotoRow>) {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
        try {
            context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
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
                            height = if (heightCol >= 0) try { cursor.getInt(heightCol) } catch (_: Exception) { 0 } else 0
                        )
                    )
                }
            }
        } catch (_: SecurityException) {
            return
        } catch (_: Exception) {
            return
        }
    }

    private fun loadVideos(rows: MutableList<DevicePhotoRow>) {
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT
        )
        val sortOrder = "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
        try {
            context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
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
                            mimeType = cursor.getString(mimeCol) ?: "video/*",
                            durationMs = try { cursor.getLong(durationCol) } catch (_: Exception) { 0L },
                            width = if (widthCol >= 0) try { cursor.getInt(widthCol) } catch (_: Exception) { 0 } else 0,
                            height = if (heightCol >= 0) try { cursor.getInt(heightCol) } catch (_: Exception) { 0 } else 0
                        )
                    )
                }
            }
        } catch (_: SecurityException) {
            return
        } catch (_: Exception) {
            return
        }
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
     * Deletes items from the device. Tries direct deletes first; leftovers go
     * through one system consent dialog on Android 10+ ([DeleteResult.NeedsConsent]
     * carries the still-pending URIs for the retry after OK).
     * The cache is dropped on success so the next load rescans.
     */
    suspend fun deleteMedia(uris: List<Uri>): DeleteResult = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext DeleteResult.Deleted
        try {
            val survivors = mutableListOf<Uri>()
            for (uri in uris) {
                try {
                    context.contentResolver.delete(uri, null, null)
                } catch (e: RecoverableSecurityException) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        // Batch below: one consent dialog for all leftovers.
                        survivors += uri
                    } else {
                        return@withContext DeleteResult.NeedsConsent(
                            e.userAction.actionIntent.intentSender,
                            uris
                        )
                    }
                } catch (_: SecurityException) {
                    return@withContext DeleteResult.Failed("Permission denied")
                } catch (e: Exception) {
                    return@withContext DeleteResult.Failed(e.message ?: "Delete failed")
                }
            }
            if (survivors.isEmpty()) {
                cachedPhotos = null
                return@withContext DeleteResult.Deleted
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return@withContext try {
                    val pending = MediaStore.createDeleteRequest(context.contentResolver, survivors)
                    DeleteResult.NeedsConsent(pending.intentSender, survivors)
                } catch (e: Exception) {
                    DeleteResult.Failed(e.message ?: "Delete failed")
                }
            }
            // Below Android 11 every survivor already reported its own consent above.
            DeleteResult.Failed("Delete failed")
        } catch (e: Exception) {
            DeleteResult.Failed(e.message ?: "Delete failed")
        }
    }

    /** Single-item convenience for [deleteMedia]. */
    suspend fun deleteMedia(uri: Uri): DeleteResult = deleteMedia(listOf(uri))

    // ── Trash ─────────────────────────────────────────────────────────────────────────

    /** Trashed items, oldest first (delegates to AppPreferences). */
    val trashedMedia: kotlinx.coroutines.flow.Flow<List<TrashedItem>> = appPreferences.trashedMedia

    /** Current set of trashed uris, for callers that need a snapshot. */
    suspend fun trashedUriSet(): Set<String> = appPreferences.trashedMedia.first().map { it.uri }.toSet()

    suspend fun trashMedia(items: List<TrashedItem>): Boolean = appPreferences.trashMedia(items)

    suspend fun restoreTrashedMedia(uris: List<String>): Boolean = appPreferences.restoreTrashedMedia(uris)

    suspend fun removeTrashedMedia(uris: List<String>): Boolean = appPreferences.removeTrashedMedia(uris)

    suspend fun clearTrash(): Boolean = appPreferences.clearTrash()

    // ── Move to folder ────────────────────────────────────────────────────────────────

    /**
     * The folders a move can target: the distinct buckets already in MediaStore, so every
     * destination is a folder the gallery already shows. Empty without permission.
     */
    suspend fun folderTargets(): List<DeviceFolderTarget> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()
        val relativePaths = mutableListOf<String>()
        val absoluteDirs = LinkedHashMap<String, String>()
        val legacy = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
        for (collection in mediaCollections()) {
            val projection = if (legacy) {
                arrayOf(MediaStore.MediaColumns.DATA)
            } else {
                arrayOf(MediaStore.MediaColumns.RELATIVE_PATH)
            }
            try {
                context.contentResolver.query(collection, projection, null, null, null)?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val value = cursor.getString(0)
                        if (!legacy) {
                            if (value != null) relativePaths += value
                        } else if (value != null) {
                            // Pre-Android-10: derive the folder from the absolute DATA path.
                            val dir = value.substringBeforeLast('/')
                            if (dir.isNotBlank()) {
                                val norm = normalizeRelativePath(dir.substringAfter("/emulated/0/", dir))
                                absoluteDirs.putIfAbsent(norm, dir)
                                relativePaths += norm
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // One unreadable collection shouldn't hide the others.
            }
        }
        buildFolderTargets(relativePaths).map { it.copy(absoluteDir = absoluteDirs[it.relativePath]) }
    }

    /**
     * Moves items into [target]. On Android 10+ a move rewrites `RELATIVE_PATH`, which needs a
     * consent dialog for media the app doesn't own ([MoveResult.NeedsConsent] carries the
     * still-pending URIs). The cache is dropped on success so the next load rescans.
     */
    suspend fun moveMedia(uris: List<Uri>, target: DeviceFolderTarget): MoveResult =
        withContext(Dispatchers.IO) {
            if (uris.isEmpty()) return@withContext MoveResult.Moved
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                return@withContext moveLegacy(uris, target)
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.RELATIVE_PATH, target.relativePath)
            }
            try {
                val survivors = mutableListOf<Uri>()
                for (uri in uris) {
                    try {
                        context.contentResolver.update(uri, values, null, null)
                    } catch (e: RecoverableSecurityException) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            // Batch below: one consent dialog for all leftovers.
                            survivors += uri
                        } else {
                            return@withContext MoveResult.NeedsConsent(
                                e.userAction.actionIntent.intentSender, uris, target
                            )
                        }
                    } catch (_: SecurityException) {
                        return@withContext MoveResult.Failed("Permission denied")
                    }
                }
                if (survivors.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val pending = MediaStore.createWriteRequest(context.contentResolver, survivors)
                    return@withContext MoveResult.NeedsConsent(pending.intentSender, survivors, target)
                }
                cachedPhotos = null
                MoveResult.Moved
            } catch (e: Exception) {
                MoveResult.Failed(e.message ?: "Move failed")
            }
        }

    /** Pre-Android-10 move: rewrite the legacy absolute `DATA` path (needs WRITE_EXTERNAL_STORAGE). */
    private fun moveLegacy(uris: List<Uri>, target: DeviceFolderTarget): MoveResult {
        val dir = target.absoluteDir ?: return MoveResult.Failed("Moving isn't supported here")
        return try {
            for (uri in uris) {
                val name = displayNameOf(uri) ?: return MoveResult.Failed("Move failed")
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DATA, "$dir/$name")
                }
                context.contentResolver.update(uri, values, null, null)
            }
            cachedPhotos = null
            MoveResult.Moved
        } catch (e: Exception) {
            MoveResult.Failed(e.message ?: "Move failed")
        }
    }

    private fun displayNameOf(uri: Uri): String? =
        try {
            context.contentResolver
                .query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        } catch (_: Exception) {
            null
        }

    /** The image and video collections to scan, across API levels. */
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
     */
    suspend fun sniffAnimatedWebps(rows: List<DevicePhotoRow>): List<DevicePhotoRow> =
        withContext(Dispatchers.IO) {
            if (rows.none { it.mimeType == "image/webp" && !it.isAnimatedWebp }) return@withContext rows
            val refreshed = rows.map { row ->
                if (row.mimeType == "image/webp" && !row.isAnimatedWebp && isAnimatedWebpUri(row.uri)) {
                    row.copy(isAnimatedWebp = true)
                } else {
                    row
                }
            }
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
