package com.vgallery.app

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vgallery.app.data.gallery.DevicePhotoRow
import com.vgallery.app.data.gallery.HomeTab
import com.vgallery.app.data.gallery.ScanPart
import com.vgallery.app.data.gallery.applyAnimatedWebpFlags
import com.vgallery.app.data.gallery.groupIntoAlbums
import com.vgallery.app.data.gallery.inTab
import com.vgallery.app.data.gallery.mergeScan
import com.vgallery.app.data.gallery.shouldRetryEmptyScan
import com.vgallery.app.data.gallery.webpUris
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What a scan may and may not do to the gallery it replaces.
 *
 * A failed query and a device without media both look like "no rows", and treating them the same
 * is what makes folders vanish on a refresh and come back on the next one. A WebP that animates
 * belongs in Videos from the first paint, not a second later.
 */
@RunWith(AndroidJUnit4::class)
class ScanMergeTest {

    private fun image(id: Long, bucket: String = "Camera", name: String = "IMG_$id.jpg") =
        DevicePhotoRow(
            id = id,
            uri = Uri.parse("content://media/external/images/media/$id"),
            name = name,
            bucketName = bucket,
            dateModified = id * 1000L,
            size = 1000L,
            mimeType = "image/jpeg"
        )

    private fun video(id: Long, bucket: String = "Camera") =
        DevicePhotoRow(
            id = -(id + 1),
            uri = Uri.parse("content://media/external/video/media/$id"),
            name = "VID_$id.mp4",
            bucketName = bucket,
            dateModified = id * 1000L,
            size = 1000L,
            mimeType = "video/mp4"
        )

    private fun webp(id: Long, bucket: String = "Stickers") =
        image(id, bucket, "sticker_$id.webp").copy(mimeType = "image/webp")

    private fun mixedFolder() = listOf(image(1, "Camera"), image(2, "Camera"), video(3, "Camera"))

    // ── Failed queries ──────────────────────────────────────────────────────────────────────

    @Test
    fun failedVideoQuery_keepsTheVideosTheLastScanHad() {
        val previous = mixedFolder()
        val scan = mergeScan(
            previous = previous,
            images = ScanPart(listOf(image(1), image(2)), ok = true),
            videos = ScanPart(emptyList(), ok = false)
        )

        assertEquals(setOf(1L, 2L, -4L), scan.map { it.id }.toSet())
        // The mixed folder still has its video side, so it still shows under Videos.
        assertEquals(listOf("Camera"), groupIntoAlbums(scan.filter { it.inTab(HomeTab.VIDEOS) }).map { it.name })
        assertEquals(listOf("Camera"), groupIntoAlbums(scan.filter { it.inTab(HomeTab.PHOTOS) }).map { it.name })
    }

    @Test
    fun failedImageQuery_keepsTheImagesTheLastScanHad() {
        val previous = mixedFolder()
        val scan = mergeScan(
            previous = previous,
            images = ScanPart(emptyList(), ok = false),
            videos = ScanPart(listOf(video(3)), ok = true)
        )

        assertEquals(setOf(1L, 2L, -4L), scan.map { it.id }.toSet())
        assertEquals(2, groupIntoAlbums(scan.filter { it.inTab(HomeTab.PHOTOS) }).single().count)
    }

    @Test
    fun answeredCollections_replaceWhatTheLastScanHad() {
        val previous = mixedFolder()
        val scan = mergeScan(
            previous = previous,
            images = ScanPart(listOf(image(9)), ok = true),
            videos = ScanPart(listOf(video(8)), ok = true)
        )

        // A move or a delete is exactly this shape: the new rows, the old ones gone.
        assertEquals(setOf(9L, -9L), scan.map { it.id }.toSet())
    }

    @Test
    fun aRowBothPathsReport_appearsOnce() {
        val previous = listOf(image(5))
        val scan = mergeScan(
            previous = previous,
            images = ScanPart(emptyList(), ok = false),
            videos = ScanPart(emptyList(), ok = true)
        )

        assertEquals(listOf(5L), scan.map { it.id })
    }

    @Test
    fun anEmptyScan_overKnownRowsAsksAgainBeforeTrustingIt() {
        assertTrue(
            shouldRetryEmptyScan(
                images = ScanPart(emptyList(), ok = true),
                videos = ScanPart(emptyList(), ok = true),
                previous = mixedFolder()
            )
        )
        // Nothing was known before, an empty answer is the real answer.
        assertFalse(
            shouldRetryEmptyScan(
                images = ScanPart(emptyList(), ok = true),
                videos = ScanPart(emptyList(), ok = true),
                previous = emptyList()
            )
        )
        assertFalse(
            shouldRetryEmptyScan(
                images = ScanPart(listOf(image(1)), ok = true),
                videos = ScanPart(emptyList(), ok = true),
                previous = mixedFolder()
            )
        )
    }

    // ── Animated WebP flags ─────────────────────────────────────────────────────────────────

    @Test
    fun aSniffedAnimatedWebp_isInVideosFromTheFirstPaintOfTheNextScan() {
        val rows = listOf(webp(1), image(2))
        // A scan lists a WebP before anything has looked inside the file.
        assertTrue(rows.first().inTab(HomeTab.PHOTOS))

        val flagged = applyAnimatedWebpFlags(rows, webpUris(rows))

        assertTrue(flagged.first().isAnimatedWebp)
        assertTrue(flagged.first().inTab(HomeTab.VIDEOS))
        assertFalse(flagged.first().inTab(HomeTab.PHOTOS))
        assertEquals(setOf("content://media/external/images/media/1"), webpUris(rows))
    }

    @Test
    fun aWebpThatDoesNotAnimate_staysWhereItWas() {
        val rows = listOf(webp(1))
        val flagged = applyAnimatedWebpFlags(rows, emptySet())

        assertEquals(rows, flagged)
        assertTrue(flagged.single().inTab(HomeTab.PHOTOS))
    }

    @Test
    fun aStickersFolderWithOnlyAnimatedWebps_leavesThePhotosTabOnTheFirstPaint() {
        val rows = listOf(webp(1, "Stickers"), webp(2, "Stickers"), image(3, "Camera"))
        val flagged = applyAnimatedWebpFlags(rows, webpUris(rows))

        val photos = groupIntoAlbums(flagged.filter { it.inTab(HomeTab.PHOTOS) }).map { it.name }
        val videos = groupIntoAlbums(flagged.filter { it.inTab(HomeTab.VIDEOS) }).map { it.name }

        assertEquals(listOf("Camera"), photos)
        assertEquals(listOf("Stickers"), videos)
    }
}
