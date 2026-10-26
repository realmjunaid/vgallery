package com.vgallery.app

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vgallery.app.data.gallery.DeviceAlbum
import com.vgallery.app.data.gallery.DevicePhotoRow
import com.vgallery.app.data.gallery.filterAlbumsByName
import com.vgallery.app.data.gallery.groupIntoAlbums
import com.vgallery.app.data.gallery.isAnimatedWebpHeader
import com.vgallery.app.data.gallery.naturalCompare
import com.vgallery.app.data.gallery.sortAlbumsForHome
import com.vgallery.app.data.gallery.sortDevicePhotos
import com.vgallery.app.data.preferences.SortOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Auto device gallery grouping: flat MediaStore rows become folder-wise albums
 * (Google Photos style), cover = latest photo, sorted newest album first.
 */
@RunWith(AndroidJUnit4::class)
class DeviceAlbumTest {

    private fun row(id: Long, bucket: String, date: Long): DevicePhotoRow =
        DevicePhotoRow(
            id = id,
            uri = Uri.parse("content://media/external/images/media/$id"),
            name = "IMG_$id.jpg",
            bucketName = bucket,
            dateModified = date,
            size = 1000L
        )

    @Test
    fun emptyRows_yieldNoAlbums() {
        assertTrue(groupIntoAlbums(emptyList()).isEmpty())
    }

    @Test
    fun rows_groupByBucketWithCountsAndLatestCover() {
        val rows = listOf(
            row(1, "Camera", 1000L),
            row(2, "Camera", 3000L),
            row(3, "Download", 2000L)
        )
        val albums = groupIntoAlbums(rows)
        assertEquals(2, albums.size)
        // Newest album first: Camera (3000) before Download (2000).
        assertEquals("Camera", albums[0].name)
        assertEquals(2, albums[0].count)
        assertEquals("content://media/external/images/media/2", albums[0].coverUri.toString())
        assertEquals("Download", albums[1].name)
        assertEquals(1, albums[1].count)
    }

    @Test
    fun blankBucket_fallsBackToUnknown() {
        val albums = groupIntoAlbums(listOf(row(1, "", 1000L)))
        assertEquals(1, albums.size)
        assertEquals("Unknown", albums[0].name)
    }

    @Test
    fun albums_alwaysDateWiseRegardlessOfRowOrder() {
        // Home folders never follow Settings sort: newest activity first, always.
        val rows = listOf(
            row(1, "Camera", 1000L),
            row(2, "Camera", 3000L),
            row(3, "Download", 2000L)
        )
        val forward = groupIntoAlbums(rows).map { it.name }
        val reversed = groupIntoAlbums(rows.reversed()).map { it.name }
        val shuffled = groupIntoAlbums(listOf(rows[2], rows[0], rows[1])).map { it.name }
        assertEquals(listOf("Camera", "Download"), forward)
        assertEquals(forward, reversed)
        assertEquals(forward, shuffled)
    }

    @Test
    fun webpHeader_detectsAnimationFlag() {
        // RIFF....WEBPVP8X........ + flags byte: bit 1 = animated.
        fun header(flags: Byte): ByteArray {
            val riff = "RIFF".toByteArray() + ByteArray(4) + "WEBP".toByteArray() +
                "VP8X".toByteArray() + ByteArray(4) + byteArrayOf(flags) + ByteArray(3)
            return riff
        }
        assertTrue(isAnimatedWebpHeader(header(0x02)))
        assertEquals(false, isAnimatedWebpHeader(header(0x00)))
        assertEquals(false, isAnimatedWebpHeader(ByteArray(10)))
        assertEquals(false, isAnimatedWebpHeader("RIFFxxxxWEBPNOTVP8X0000".toByteArray()))
    }

    @Test
    fun sort_nameAsc_ordersCaseInsensitively() {        val rows = listOf(
            DevicePhotoRow(1, Uri.parse("content://m/1"), "b.jpg", "C", 1000L, 10L),
            DevicePhotoRow(2, Uri.parse("content://m/2"), "A.jpg", "C", 3000L, 30L),
            DevicePhotoRow(3, Uri.parse("content://m/3"), "c.jpg", "C", 2000L, 20L),
        )
        val names = sortDevicePhotos(rows, SortOrder.NAME_ASC).map { it.name }
        assertEquals(listOf("A.jpg", "b.jpg", "c.jpg"), names)
        val newestFirst = sortDevicePhotos(rows, SortOrder.DATE_NEWEST).map { it.id }
        assertEquals(listOf(2L, 3L, 1L), newestFirst)
        val smallestFirst = sortDevicePhotos(rows, SortOrder.SIZE_SMALLEST).map { it.id }
        assertEquals(listOf(1L, 3L, 2L), smallestFirst)
    }

    @Test
    fun albums_sumSizesAndSortByDateAdded() {
        val rows = listOf(
            DevicePhotoRow(1, Uri.parse("content://m/1"), "a.jpg", "Camera", 5000L, 1_000_000L, dateAdded = 100L),
            DevicePhotoRow(2, Uri.parse("content://m/2"), "b.jpg", "Camera", 4000L, 2_000_000L, dateAdded = 300L),
            DevicePhotoRow(3, Uri.parse("content://m/3"), "c.jpg", "Download", 6000L, 500L, dateAdded = 200L),
        )
        val albums = groupIntoAlbums(rows).associateBy { it.name }
        assertEquals(3_000_000L, albums.getValue("Camera").sizeBytes)
        assertEquals(500L, albums.getValue("Download").sizeBytes)
        // Sort key is DATE_ADDED, so Camera (300) leads Download (200) despite the newer modified date.
        assertEquals(listOf("Camera", "Download"), groupIntoAlbums(rows).map { it.name })
        assertEquals(300L, albums.getValue("Camera").dateAdded)
    }

    @Test
    fun homeSort_putsNewFoldersFirstThenNewestAdded() {
        fun album(name: String, added: Long) =
            DeviceAlbum(name, 1, Uri.parse("content://m/$name"), dateAdded = added)

        val albums = listOf(album("Old", 100L), album("Fresh", 50L), album("Newest", 900L))
        // A flagged folder always leads, even though it is the oldest by date added.
        assertEquals(
            listOf("Fresh", "Newest", "Old"),
            sortAlbumsForHome(albums, newFolders = setOf("Fresh")).map { it.name }
        )
        // With nothing flagged it is plain "newest date added first".
        assertEquals(
            listOf("Newest", "Old", "Fresh"),
            sortAlbumsForHome(albums, newFolders = emptySet()).map { it.name }
        )
    }

    @Test
    fun sort_nameAsc_readsNumbersInsideANameAsNumbers() {
        // The reported case: a folder of clones named "1(1).png" … "1(101).png" listed as
        // 1(1), 1(10), 1(100), 1(101), 1(11) — text order, not file order.
        val names = listOf(
            "1(10).png", "1(2).png", "1(101).png", "1(1).png", "1(11).png", "1(100).png"
        )
        val rows = names.mapIndexed { index, name ->
            DevicePhotoRow(
                id = index.toLong() + 1,
                uri = Uri.parse("content://m/$index"),
                name = name,
                bucketName = "C",
                dateModified = 1000L,
                size = 10L
            )
        }
        assertEquals(
            listOf("1(1).png", "1(2).png", "1(10).png", "1(11).png", "1(100).png", "1(101).png"),
            sortDevicePhotos(rows, SortOrder.NAME_ASC).map { it.name }
        )
        // And the reverse order is exactly that, reversed.
        assertEquals(
            listOf("1(101).png", "1(100).png", "1(11).png", "1(10).png", "1(2).png", "1(1).png"),
            sortDevicePhotos(rows, SortOrder.NAME_DESC).map { it.name }
        )
    }

    @Test
    fun naturalCompare_ordersNumberedAndPlainNames() {
        // Numbers inside a name win over the surrounding text.
        assertTrue(naturalCompare("1(2).png", "1(10).png") < 0)
        assertTrue(naturalCompare("IMG2.jpg", "IMG10.jpg") < 0)
        assertTrue(naturalCompare("Screenshot (9).png", "Screenshot (10).png") < 0)
        // Letters are still compared case-insensitively.
        assertTrue(naturalCompare("A.jpg", "b.jpg") < 0)
        assertTrue(naturalCompare("b.jpg", "A.jpg") > 0)
        // Leading zeros are the same number, and the exact text settles such a pair.
        assertTrue(naturalCompare("IMG007.jpg", "IMG7.jpg") != 0)
        assertEquals(naturalCompare("IMG7.jpg", "IMG7.jpg"), 0)
        // A name that is a prefix of another comes first.
        assertTrue(naturalCompare("IMG", "IMG1.jpg") < 0)
        // Same letters and numbers, different case: decided, not left to chance.
        assertTrue(naturalCompare("IMG1.jpg", "img1.jpg") != 0)
        // Every pair has a direction: sorting a shuffled pile is stable and repeatable.
        val pile = listOf("10.png", "2.png", "A.png", "a10.png", "1.png", "01.png")
        assertEquals(
            pile.sortedWith { a, b -> naturalCompare(a, b) },
            pile.sortedWith { a, b -> naturalCompare(a, b) }
        )
    }

    @Test
    fun filterAlbumsByName_returnsFoldersAndNeverFiles() {
        fun album(name: String) = DeviceAlbum(name, 1, Uri.parse("content://m/$name"))

        val albums = listOf(album("Camera"), album("Screenshots"), album("WhatsApp Images"))
        // Case-insensitive, matches anywhere in the folder name.
        assertEquals(listOf("Camera"), filterAlbumsByName(albums, "cam").map { it.name })
        assertEquals(listOf("Camera"), filterAlbumsByName(albums, " CAMERA ").map { it.name })
        assertEquals(
            listOf("Screenshots", "WhatsApp Images"),
            filterAlbumsByName(albums, "s").map { it.name }
        )
        // A file name is not a folder name: nothing to show, and the screen says so.
        assertTrue(filterAlbumsByName(albums, "IMG_2024.jpg").isEmpty())
        // Blank query = every folder, untouched, in the order it came in.
        assertEquals(albums, filterAlbumsByName(albums, "   "))
    }
}
