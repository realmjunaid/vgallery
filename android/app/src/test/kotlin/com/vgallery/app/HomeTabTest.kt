package com.vgallery.app

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vgallery.app.data.gallery.DevicePhotoRow
import com.vgallery.app.data.gallery.HomeTab
import com.vgallery.app.data.gallery.groupIntoAlbums
import com.vgallery.app.data.gallery.inTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Home's two tabs split the MediaStore scan exclusively: Photos holds still images,
 * Videos holds everything that plays — real videos plus GIFs and animated WebPs,
 * which MediaStore reports as images.
 */
@RunWith(AndroidJUnit4::class)
class HomeTabTest {

    private fun row(
        id: Long,
        name: String,
        bucket: String,
        mime: String,
        date: Long = id * 1000L,
        animatedWebp: Boolean = false
    ): DevicePhotoRow = DevicePhotoRow(
        id = id,
        uri = Uri.parse("content://media/external/media/$id"),
        name = name,
        bucketName = bucket,
        dateModified = date,
        size = 1000L,
        mimeType = mime,
        isAnimatedWebp = animatedWebp
    )

    private fun rowsOf(tab: HomeTab, rows: List<DevicePhotoRow>) = rows.filter { it.inTab(tab) }

    @Test
    fun stillImages_arePhotosOnly() {
        val stills = listOf(
            row(1, "a.jpg", "Camera", "image/jpeg"),
            row(2, "b.png", "Camera", "image/png"),
            row(3, "c.webp", "Camera", "image/webp"),
            row(4, "d.bmp", "Camera", "image/bmp")
        )
        stills.forEach { still ->
            assertTrue(still.name, still.inTab(HomeTab.PHOTOS))
            assertFalse(still.name, still.inTab(HomeTab.VIDEOS))
        }
    }

    @Test
    fun videosGifsAndAnimatedWebps_areVideosOnly() {
        val playable = listOf(
            row(1, "a.mp4", "Camera", "video/mp4"),
            row(2, "b.mkv", "Camera", "video/x-matroska"),
            row(3, "c.gif", "Camera", "image/gif"),
            row(4, "d.webp", "Camera", "image/webp", animatedWebp = true)
        )
        playable.forEach { item ->
            assertTrue(item.name, item.inTab(HomeTab.VIDEOS))
            assertFalse(item.name, item.inTab(HomeTab.PHOTOS))
        }
    }

    @Test
    fun animatedWebp_movesToVideosOnceSniffed() {
        // The WebP header sniff flips this flag shortly after the first paint.
        val beforeSniff = row(1, "anim.webp", "Camera", "image/webp", animatedWebp = false)
        assertTrue(beforeSniff.inTab(HomeTab.PHOTOS))

        val afterSniff = beforeSniff.copy(isAnimatedWebp = true)
        assertTrue(afterSniff.inTab(HomeTab.VIDEOS))
        assertFalse(afterSniff.inTab(HomeTab.PHOTOS))
    }

    @Test
    fun mixedFolder_appearsInBothTabsCountingOnlyItsOwnKind() {
        val rows = listOf(
            row(1, "IMG_1.jpg", "Camera", "image/jpeg", date = 1000L),
            row(2, "IMG_2.png", "Camera", "image/png", date = 2000L),
            row(3, "VID_1.mp4", "Camera", "video/mp4", date = 3000L),
            row(4, "Anim.gif", "Camera", "image/gif", date = 4000L)
        )

        val photoAlbums = groupIntoAlbums(rowsOf(HomeTab.PHOTOS, rows))
        assertEquals(1, photoAlbums.size)
        assertEquals("Camera", photoAlbums.single().name)
        assertEquals(2, photoAlbums.single().count)
        assertFalse(photoAlbums.single().coverIsVideo)
        assertEquals("content://media/external/media/2", photoAlbums.single().coverUri.toString())

        val videoAlbums = groupIntoAlbums(rowsOf(HomeTab.VIDEOS, rows))
        assertEquals(1, videoAlbums.size)
        assertEquals("Camera", videoAlbums.single().name)
        assertEquals(2, videoAlbums.single().count)
        assertEquals("content://media/external/media/4", videoAlbums.single().coverUri.toString())
    }

    @Test
    fun folderWithOnlyOneKind_isMissingFromTheOtherTab() {
        val rows = listOf(
            row(1, "IMG_1.jpg", "Screenshots", "image/jpeg"),
            row(2, "clip.mp4", "Movies", "video/mp4")
        )
        assertEquals(
            listOf("Screenshots"),
            groupIntoAlbums(rowsOf(HomeTab.PHOTOS, rows)).map { it.name }
        )
        assertEquals(
            listOf("Movies"),
            groupIntoAlbums(rowsOf(HomeTab.VIDEOS, rows)).map { it.name }
        )
    }

    @Test
    fun everyRow_landsInExactlyOneTab() {
        val rows = listOf(
            row(1, "a.jpg", "Camera", "image/jpeg"),
            row(2, "b.mp4", "Camera", "video/mp4"),
            row(3, "c.gif", "Camera", "image/gif"),
            row(4, "d.webp", "Camera", "image/webp", animatedWebp = true),
            row(5, "e.webp", "Camera", "image/webp")
        )
        assertEquals(
            rows.size,
            rowsOf(HomeTab.PHOTOS, rows).size + rowsOf(HomeTab.VIDEOS, rows).size
        )
    }
}
