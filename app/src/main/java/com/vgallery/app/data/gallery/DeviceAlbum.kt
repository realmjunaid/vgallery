package com.vgallery.app.data.gallery

import android.net.Uri
import com.vgallery.app.data.preferences.SortOrder

/** One device folder (MediaStore bucket), like Google Photos groups. */
data class DeviceAlbum(
    val name: String,
    val count: Int,
    val coverUri: Uri,
    val latestDateModified: Long = 0L,
    val coverIsVideo: Boolean = false,
    /** Sum of every item's size, shown small next to the item count on home. */
    val sizeBytes: Long = 0L,
    /** Newest DATE_ADDED in the folder: the home sort key (date added, not modified). */
    val dateAdded: Long = 0L
)

/** One image row from MediaStore. Pure data so grouping is unit-testable. */
data class DevicePhotoRow(
    val id: Long,
    val uri: Uri,
    val name: String,
    val bucketName: String,
    val dateModified: Long,
    val size: Long,
    val mimeType: String = "",
    val durationMs: Long = 0L,
    val isAnimatedWebp: Boolean = false,
    val width: Int = 0,
    val height: Int = 0,
    /** MediaStore DATE_ADDED in milliseconds; 0 when the column was unavailable. */
    val dateAdded: Long = 0L
) {
    /** GIF always animates; WebP may be animated — both decode cheaper for smooth playback. */
    val needsLightDecode: Boolean
        get() = mimeType == "image/gif" || mimeType == "image/webp"

    val isVideo: Boolean
        get() = mimeType.startsWith("video/")

    /** GIF and animated WebP: MediaStore files them as images, but they play back. */
    val isMotionImage: Boolean
        get() = mimeType == "image/gif" || isAnimatedWebp
}

/**
 * Sniffs a WebP header for the VP8X animation flag. Animated WebPs always use the
 * VP8X container with bit 1 of the feature-flags byte (offset 20) set.
 */
fun isAnimatedWebpHeader(header: ByteArray): Boolean {
    if (header.size < 21) return false
    fun tag(offset: Int, text: String): Boolean =
        header[offset] == text[0].code.toByte() &&
            header[offset + 1] == text[1].code.toByte() &&
            header[offset + 2] == text[2].code.toByte() &&
            header[offset + 3] == text[3].code.toByte()
    if (!tag(0, "RIFF") || !tag(8, "WEBP") || !tag(12, "VP8X")) return false
    return (header[20].toInt() and 0x02) != 0
}

/**
 * Sorts flat MediaStore rows per the user's Settings order (same options as the viewer).
 */
fun sortDevicePhotos(rows: List<DevicePhotoRow>, order: SortOrder): List<DevicePhotoRow> =
    when (order) {
        // A–Z is [naturalCompare], so the numbers inside a name decide their own order: a folder of
        // `1(2), 1(10), 1(100)` lists as 1(2), then 1(10), then 1(100) instead of text order.
        SortOrder.NAME_ASC -> rows.sortedWith { a, b -> naturalCompare(a.name, b.name) }
        SortOrder.NAME_DESC -> rows.sortedWith { a, b -> naturalCompare(b.name, a.name) }
        SortOrder.DATE_NEWEST -> rows.sortedByDescending { it.dateModified }
        SortOrder.DATE_OLDEST -> rows.sortedBy { it.dateModified }
        SortOrder.SIZE_LARGEST -> rows.sortedByDescending { it.size }
        SortOrder.SIZE_SMALLEST -> rows.sortedBy { it.size }
        SortOrder.TYPE_VIDEO_FIRST ->
            rows.sortedWith(compareByDescending<DevicePhotoRow> { it.isVideo }.thenByDescending { it.dateModified })
        SortOrder.TYPE_IMAGE_FIRST ->
            rows.sortedWith(compareBy<DevicePhotoRow> { it.isVideo }.thenByDescending { it.dateModified })
    }

/**
 * Groups flat MediaStore rows into albums (folder-wise).
 * One album per [DevicePhotoRow.bucketName], cover = latest photo.
 * Sorted newest-added first, so freshly created folders surface at the top.
 */
fun groupIntoAlbums(rows: List<DevicePhotoRow>): List<DeviceAlbum> {
    if (rows.isEmpty()) return emptyList()
    return rows.groupBy { it.bucketName.ifBlank { "Unknown" } }
        .map { (bucket, photos) ->
            val latest = photos.maxByOrNull { it.dateModified }
            val added = photos.maxOfOrNull { it.dateAdded } ?: 0L
            DeviceAlbum(
                name = bucket,
                count = photos.size,
                coverUri = latest?.uri ?: photos.first().uri,
                latestDateModified = latest?.dateModified ?: 0L,
                coverIsVideo = latest?.isVideo ?: false,
                sizeBytes = photos.sumOf { it.size },
                // DATE_ADDED can be missing on some providers; the latest date works just as well.
                dateAdded = added.takeIf { it > 0L } ?: (latest?.dateModified ?: 0L)
            )
        }
        .sortedByDescending { it.dateAdded }
}

/**
 * Home ordering: folders flagged "new" ([newFolders], never opened) always sit first, then the
 * rest newest-date-added first, name as a stable tie-break.
 */
fun sortAlbumsForHome(albums: List<DeviceAlbum>, newFolders: Set<String>): List<DeviceAlbum> =
    albums.sortedWith(
        compareByDescending<DeviceAlbum> { it.name in newFolders }
            .thenByDescending { it.dateAdded }
            // Same natural ordering as a folder's contents, so "Screenshot (2)" leads "(10)".
            .then(Comparator { a, b -> naturalCompare(a.name, b.name) })
    )

/**
 * Folders whose names open the same way, gathered under one heading while home is grouped.
 * [title] is that opening word as the user wrote it — "WhatsApp" for "WhatsApp Images" — and
 * [albums] keeps the order home already had them in.
 */
data class AlbumGroup(val title: String, val albums: List<DeviceAlbum>) {
    /** Two or more folders share the name; one folder on its own needs no heading over it. */
    val isFamily: Boolean get() = albums.size > 1
}

/**
 * Gathers folders that differ only in what follows their first word.
 *
 * "WhatsApp Images" and "WhatsApp Video" belong together, and so do "Camera" and "Camera Roll",
 * or "Download" and "Downloads": a plural is not a different folder name, and neither are the
 * digits in "Camera2", "Screenshot (1)" or "100ANDRO"/"101ANDRO". A folder with no relatives
 * keeps its own group, so grouping only changes the grid where there is a family to gather.
 * Families come in the order their first folder does — a fresh folder still surfaces at the top.
 */
fun groupAlbums(albums: List<DeviceAlbum>): List<AlbumGroup> {
    val families = LinkedHashMap<String, MutableList<DeviceAlbum>>()
    albums.forEach { album ->
        families.getOrPut(albumFamilyKey(album.name)) { mutableListOf() } += album
    }
    return families.map { (key, members) -> AlbumGroup(albumFamilyTitle(key, members), members) }
}

/**
 * The word a folder name opens with, once the digits and the punctuation around it are gone: the
 * part two names of a family agree on. "Camera2" is the Camera family, and "100ANDRO" opens on
 * "ANDRO" so roll 100 and roll 101 gather — a name with no letters at all ("2024") has no word to
 * share and stands alone.
 */
private fun albumFamilyKey(name: String): String {
    val word = albumFamilyWord(name) ?: return name.trim().lowercase()
    val lowered = word.lowercase()
    val base = lowered.trim { it.isDigit() }.ifBlank { lowered }
    // "Downloads" is "Download" to anyone looking at a folder list.
    return if (base.length > 4 && base.endsWith("s") && !base.endsWith("ss")) base.dropLast(1) else base
}

/**
 * The shared word as the user wrote it — "WhatsApp", not "whatsapp". Prefers the spelling that is
 * the family word itself, so a heading reads "Camera" and not "Camera2".
 */
private fun albumFamilyTitle(key: String, members: List<DeviceAlbum>): String {
    val words = members.mapNotNull { albumFamilyWord(it.name) }
    return words.firstOrNull { it.lowercase() == key }
        ?: words.minByOrNull { it.length }
        ?: members.first().name.trim()
}

/**
 * The word that identifies a folder among its siblings: the first one with a letter in it, so a
 * numbering prefix ("100ANDRO", "2024 Camera") never hides the name. A name with no letters at all
 * falls back to its first word, so "2024 (1)" still belongs with "2024".
 */
private fun albumFamilyWord(name: String): String? {
    val words = name.split(ALBUM_NAME_SEPARATORS).filter { it.isNotBlank() }
    return words.firstOrNull { word -> word.any(Char::isLetter) } ?: words.firstOrNull()
}

/** A folder name split into its words: anything that is not a letter or a digit separates them. */
private val ALBUM_NAME_SEPARATORS = Regex("[^\\p{L}\\p{N}]+")

/**
 * Home search: the folders whose name contains [query], case-insensitively, in the order they were
 * given. Home is a folder picker — a query there means "where is that folder" — so files never
 * appear in the results. A blank query returns every folder untouched.
 */
fun filterAlbumsByName(albums: List<DeviceAlbum>, query: String): List<DeviceAlbum> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return albums
    return albums.filter { it.name.contains(trimmed, ignoreCase = true) }
}
