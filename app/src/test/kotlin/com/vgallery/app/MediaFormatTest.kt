package com.vgallery.app

import com.vgallery.app.data.gallery.formatBytes
import com.vgallery.app.data.gallery.formatMediaDate
import com.vgallery.app.data.gallery.formatResolution
import com.vgallery.app.data.gallery.homeSummary
import org.junit.Assert.assertEquals
import org.junit.Test

/** Formatting used by the details sheet: size, resolution and date. */
class MediaFormatTest {

    @Test
    fun formatBytes_scalesUnits() {
        assertEquals("—", formatBytes(0))
        assertEquals("—", formatBytes(-5))
        assertEquals("512 KB", formatBytes(512 * 1024L))
        assertEquals("2.4 MB", formatBytes((2.4 * 1024 * 1024).toLong()))
        assertEquals("812 KB", formatBytes(812 * 1024L))
    }

    @Test
    fun formatResolution_showsDimensionsOrDash() {
        assertEquals("4032 × 3024", formatResolution(4032, 3024))
        assertEquals("—", formatResolution(0, 0))
        assertEquals("—", formatResolution(100, 0))
    }

    @Test
    fun formatMediaDate_handlesBadInput() {
        assertEquals("—", formatMediaDate(0))
        assertEquals("—", formatMediaDate(-1))
    }

    @Test
    fun homeSummary_countsFoldersAndTotalSize() {
        // The old line ended in "(auto)", a mode label with nothing to say; the size does.
        assertEquals("1204 items • 38 folders • 12.00 GB", homeSummary(1204, 38, 12L * 1024 * 1024 * 1024))
        assertEquals("46 items • 1 folders • 2.4 MB", homeSummary(46, 1, (2.4 * 1024 * 1024).toLong()))
        // No size available: the counts still stand, without a stray separator or a dash.
        assertEquals("46 items • 1 folders", homeSummary(46, 1, 0L))
    }
}
