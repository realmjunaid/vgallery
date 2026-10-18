package com.vgallery.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vgallery.app.data.gallery.buildFolderTargets
import com.vgallery.app.data.gallery.folderDisplayName
import com.vgallery.app.data.gallery.normalizeRelativePath
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * "Move to folder" rewrites MediaStore's RELATIVE_PATH, so the destination must always be a
 * well-formed path with exactly one trailing slash. These tests pin the pure path helpers and
 * the bucket-to-destination collapsing that the folder picker relies on.
 */
@RunWith(AndroidJUnit4::class)
class MediaMoveTest {

    @Test
    fun normalize_addsExactlyOneTrailingSlash() {
        assertEquals("Pictures/", normalizeRelativePath("Pictures"))
        assertEquals("Pictures/", normalizeRelativePath("Pictures/"))
        assertEquals("Pictures/", normalizeRelativePath("/Pictures//"))
        assertEquals("DCIM/Camera/", normalizeRelativePath("DCIM/Camera"))
    }

    @Test
    fun normalize_rootBecomesEmpty() {
        assertEquals("", normalizeRelativePath(""))
        assertEquals("", normalizeRelativePath("/"))
        assertEquals("", normalizeRelativePath("   "))
    }

    @Test
    fun displayName_isTheLastSegment() {
        assertEquals("Camera", folderDisplayName("DCIM/Camera/"))
        assertEquals("Camera", folderDisplayName("DCIM/Camera"))
        assertEquals("Pictures", folderDisplayName("Pictures/"))
    }

    @Test
    fun displayName_rootIsInternalStorage() {
        assertEquals("Internal storage", folderDisplayName(""))
        assertEquals("Internal storage", folderDisplayName("/"))
    }

    @Test
    fun buildTargets_collapsesBucketsAndCountsItems() {
        val targets = buildFolderTargets(
            listOf(
                "DCIM/Camera/",
                "DCIM/Camera/",
                "Pictures/",
                "DCIM/Camera",
                "Download/"
            )
        )
        assertEquals(listOf("Camera", "Download", "Pictures"), targets.map { it.displayName })
        assertEquals(3, targets.first { it.displayName == "Camera" }.count)
        assertEquals(1, targets.first { it.displayName == "Download" }.count)
        assertEquals("DCIM/Camera/", targets.first { it.displayName == "Camera" }.relativePath)
    }

    @Test
    fun buildTargets_sortsCaseInsensitively() {
        val targets = buildFolderTargets(listOf("zebra/", "Apple/", "mango/"))
        assertEquals(listOf("Apple", "mango", "zebra"), targets.map { it.displayName })
    }

    @Test
    fun buildTargets_emptyInput_isEmpty() {
        assertEquals(emptyList<String>(), buildFolderTargets(emptyList()).map { it.displayName })
    }

    @Test
    fun buildTargets_rootShowsAsInternalStorage() {
        val targets = buildFolderTargets(listOf(""))
        assertEquals("Internal storage", targets.single().displayName)
        assertEquals("", targets.single().relativePath)
    }
}
