package com.vgallery.app.data.gallery

import android.content.IntentSender
import android.net.Uri

/**
 * A destination folder for a move, derived from an existing MediaStore bucket so the
 * destination always corresponds to a folder the user can already see in the gallery.
 *
 * [relativePath] is what a scoped-storage update writes to `MediaColumns.RELATIVE_PATH`
 * (Android 10+); [absoluteDir] is only used on older releases, where a move updates the
 * legacy `MediaColumns.DATA` path.
 */
data class DeviceFolderTarget(
    val displayName: String,
    val relativePath: String,
    val absoluteDir: String? = null,
    val count: Int = 0
)

/** Outcome of a move request. */
sealed interface MoveResult {
    data object Moved : MoveResult

    /**
     * System consent required (Android 10+): launch the sender, then call
     * [DeviceGalleryRepository.moveMedia] again with [pending] on OK.
     */
    data class NeedsConsent(
        val intentSender: IntentSender,
        val pending: List<Uri>,
        val target: DeviceFolderTarget
    ) : MoveResult

    data class Failed(val message: String) : MoveResult
}

// ── Pure helpers (unit-testable, no Android types) ────────────────────────────────────────

/** Normalises a `RELATIVE_PATH` so it is well-formed: `"Pictures/New"` → `"Pictures/New/"`. */
fun normalizeRelativePath(raw: String): String {
    val trimmed = raw.trim().trim('/')
    return if (trimmed.isEmpty()) "" else "$trimmed/"
}

/** The friendly folder label: the last path segment, or "Internal storage" for the root. */
fun folderDisplayName(relativePath: String): String {
    val trimmed = relativePath.trim().trim('/')
    return trimmed.substringAfterLast('/').ifBlank { "Internal storage" }
}

/**
 * Collapses the scan's `RELATIVE_PATH` values into distinct move destinations, counting how
 * many items live in each and sorting by name (case-insensitive) for a stable dialog order.
 */
fun buildFolderTargets(relativePaths: List<String>): List<DeviceFolderTarget> {
    val counts = LinkedHashMap<String, Int>()
    for (raw in relativePaths) {
        val norm = normalizeRelativePath(raw)
        counts[norm] = (counts[norm] ?: 0) + 1
    }
    return counts.entries
        .map { (norm, count) -> DeviceFolderTarget(folderDisplayName(norm), norm, count = count) }
        .sortedWith(compareBy({ it.displayName.lowercase() }, { it.relativePath }))
}
