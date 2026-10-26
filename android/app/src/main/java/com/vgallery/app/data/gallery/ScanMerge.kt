package com.vgallery.app.data.gallery

/**
 * One MediaStore collection's answer for a scan: the rows it returned and whether the query
 * itself succeeded. The two are not the same thing — a query can throw, and a thrown query must
 * never read as "the device has no such media".
 */
data class ScanPart(
    val rows: List<DevicePhotoRow>,
    val ok: Boolean
)

/** Whether a scan found nothing at all. */
fun isEmptyScan(images: ScanPart, videos: ScanPart): Boolean =
    images.rows.isEmpty() && videos.rows.isEmpty()

/**
 * Whether an empty scan should be re-run before it is believed. MediaStore answers while it is
 * still indexing, so a scan that comes back with nothing while the previous one had rows is
 * usually a busy provider, not a device that just lost every photo. One more query settles it.
 */
fun shouldRetryEmptyScan(images: ScanPart, videos: ScanPart, previous: List<DevicePhotoRow>): Boolean =
    isEmptyScan(images, videos) && previous.isNotEmpty()

/**
 * Folds a scan into the rows the app already had.
 *
 * A collection whose query failed keeps its rows from [previous] — the media is still on the
 * device, the query just did not manage to list it — so a single hiccup cannot make a folder
 * disappear from the gallery until the next refresh brings it back. A collection that answered
 * replaces its previous rows completely, so deletions and moves still show up right away.
 *
 * Rows are deduplicated by [DevicePhotoRow.id], and the scanned copy of a row always wins, so a
 * row that arrives from both paths is the one the scan just read.
 */
fun mergeScan(
    previous: List<DevicePhotoRow>,
    images: ScanPart,
    videos: ScanPart
): List<DevicePhotoRow> {
    val merged = ArrayList<DevicePhotoRow>(images.rows.size + videos.rows.size + previous.size)
    merged += images.rows
    merged += videos.rows
    if (!images.ok) merged += previous.filterNot { it.isVideo }
    if (!videos.ok) merged += previous.filter { it.isVideo }
    return merged.distinctBy { it.id }
}

/**
 * Applies the animation flags a previous scan already discovered.
 *
 * WebP is the one format whose tab (Photos or Videos) needs a look at the file itself, and the
 * sniff happens after the first paint. Without this, every fresh scan would put those files back
 * in Photos and then move them to Videos a moment later — the folder visibly jumping tabs on
 * every refresh. [animatedUris] makes the first paint already correct.
 */
fun applyAnimatedWebpFlags(rows: List<DevicePhotoRow>, animatedUris: Set<String>): List<DevicePhotoRow> =
    if (animatedUris.isEmpty()) rows
    else rows.map { row ->
        if (!row.isAnimatedWebp && row.uri.toString() in animatedUris) {
            row.copy(isAnimatedWebp = true)
        } else {
            row
        }
    }

/** The WebP rows of [rows], which are the only ones an animation flag applies to. */
fun webpUris(rows: List<DevicePhotoRow>): Set<String> =
    rows.filter { it.mimeType == "image/webp" }.map { it.uri.toString() }.toSet()
