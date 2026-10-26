package com.vgallery.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vgallery.app.data.gallery.FolderMarkerState
import com.vgallery.app.data.gallery.cleared
import com.vgallery.app.data.gallery.reconcileFolderMarkers
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The home "new folder" dot: the first scan only seeds a baseline, later scans flag folders that
 * appeared since, and opening a folder clears its own flag for good.
 */
@RunWith(AndroidJUnit4::class)
class NewFolderMarkerTest {

    @Test
    fun firstScanIsABaselineWithNoDots() {
        val state = reconcileFolderMarkers(setOf("Camera", "Download"), FolderMarkerState())
        assertEquals(setOf("Camera", "Download"), state.known)
        assertEquals(emptySet<String>(), state.flagged)
    }

    @Test
    fun laterScanFlagsOnlyFoldersAddedSince() {
        val seeded = FolderMarkerState(known = setOf("Camera"))
        val state = reconcileFolderMarkers(setOf("Camera", "Screenshots"), seeded)
        assertEquals(setOf("Camera", "Screenshots"), state.known)
        assertEquals(setOf("Screenshots"), state.flagged)
    }

    @Test
    fun openingAFolderClearsItsFlagButKeepsItKnown() {
        val seeded = FolderMarkerState(known = setOf("Camera", "Old"))
        val flagged = reconcileFolderMarkers(setOf("Camera", "Old", "New"), seeded)
        assertEquals(setOf("New"), flagged.flagged)

        val opened = flagged.cleared("New")
        assertEquals(setOf("Camera", "Old", "New"), opened.known)
        assertEquals(emptySet<String>(), opened.flagged)

        // The next scan must not re-flag an already-opened folder.
        assertEquals(
            emptySet<String>(),
            reconcileFolderMarkers(setOf("Camera", "Old", "New"), opened).flagged
        )
    }

    @Test
    fun foldersThatDisappearLoseTheirFlag() {
        val state = FolderMarkerState(known = setOf("Camera"), flagged = setOf("Camera", "Gone"))
        assertEquals(setOf("Camera"), reconcileFolderMarkers(setOf("Camera"), state).flagged)
    }
}
