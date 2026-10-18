package com.vgallery.app.data.gallery

/** How long a trashed item stays recoverable before permanent auto-deletion. */
const val TRASH_TTL_MS = 30L * 24 * 60 * 60 * 1000 // 30 days, like Google Photos.

/**
 * One trashed item, stored self-contained (uri + name + mime + timestamp) so the Trash
 * screen can render thumbnails without re-querying MediaStore — the file may already be
 * gone from the scan by the time the user opens Trash.
 */
data class TrashedItem(
    val uri: String,
    val name: String,
    val mimeType: String,
    val trashedAtMs: Long
) {
    val isVideo: Boolean get() = mimeType.startsWith("video/")
}

// ── Encoding ──────────────────────────────────────────────────────────────────────────────
// One DataStore string entry per item: "uri<<>>name<<>>mime<<>>epochMs".
// Entries joined by "|||". Same convention as Recents (AppPreferences).

private const val FIELD_SEP = "<<>>"
private const val ENTRY_SEP = "|||"

fun encodeTrashItem(item: TrashedItem): String =
    "${item.uri}$FIELD_SEP${item.name}$FIELD_SEP${item.mimeType}$FIELD_SEP${item.trashedAtMs}"

fun decodeTrashEntry(entry: String): TrashedItem? {
    val parts = entry.split(FIELD_SEP)
    if (parts.size < 4) return null
    val uri = parts[0]
    val name = parts[1]
    val mime = parts[2]
    val ts = parts[3].toLongOrNull() ?: return null
    return TrashedItem(uri, name, mime, ts)
}

fun decodeTrashList(raw: String): List<TrashedItem> =
    if (raw.isBlank()) emptyList()
    else raw.split(ENTRY_SEP).mapNotNull { decodeTrashEntry(it) }

fun encodeTrashList(items: List<TrashedItem>): String =
    items.joinToString(ENTRY_SEP) { encodeTrashItem(it) }

fun trashEntryUri(entry: String): String? =
    entry.split(FIELD_SEP).firstOrNull()?.takeIf { it.isNotBlank() }

/** Removes entries whose uri matches one in [uris]. */
fun removeTrashEntries(raw: String, uris: Set<String>): String =
    if (uris.isEmpty()) raw
    else raw.split(ENTRY_SEP).filter { trashEntryUri(it) !in uris }.joinToString(ENTRY_SEP)

/** Appends [items] to the raw trash list, replacing any existing entry for the same uri. */
fun appendTrashEntries(raw: String, items: List<TrashedItem>): String {
    val existing = if (raw.isBlank()) emptyList() else raw.split(ENTRY_SEP).filter { it.isNotBlank() }
    val uris = items.map { it.uri }.toSet()
    val kept = existing.filter { trashEntryUri(it) !in uris }
    return (kept + items.map { encodeTrashItem(it) }).joinToString(ENTRY_SEP)
}

// ── Pure helpers ─────────────────────────────────────────────────────────────────────────

/** Excludes rows whose uri is in the trashed set. */
fun filterTrashed(rows: List<DevicePhotoRow>, trashedUris: Set<String>): List<DevicePhotoRow> =
    if (trashedUris.isEmpty()) rows
    else rows.filter { it.uri.toString() !in trashedUris }

/** URIs of items whose trash TTL has expired. */
fun expiredTrashUris(items: List<TrashedItem>, nowMs: Long, ttlMs: Long = TRASH_TTL_MS): List<String> =
    items.filter { nowMs - it.trashedAtMs >= ttlMs }.map { it.uri }
