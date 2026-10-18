package com.vgallery.app

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vgallery.app.data.gallery.DevicePhotoRow
import com.vgallery.app.data.gallery.TRASH_TTL_MS
import com.vgallery.app.data.gallery.TrashedItem
import com.vgallery.app.data.gallery.appendTrashEntries
import com.vgallery.app.data.gallery.decodeTrashEntry
import com.vgallery.app.data.gallery.decodeTrashList
import com.vgallery.app.data.gallery.encodeTrashList
import com.vgallery.app.data.gallery.expiredTrashUris
import com.vgallery.app.data.gallery.filterTrashed
import com.vgallery.app.data.gallery.removeTrashEntries
import com.vgallery.app.data.gallery.trashEntryUri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Trash is app-managed: "delete" stores a self-contained record and the scan hides it,
 * "restore" drops the record, and only "delete forever" touches MediaStore. These tests
 * pin the pure store/filter rules that make that safe.
 */
@RunWith(AndroidJUnit4::class)
class TrashStoreTest {

    private fun item(
        id: Long,
        name: String = "IMG_$id.jpg",
        mime: String = "image/jpeg",
        trashedAt: Long = 1_000L
    ) = TrashedItem("content://media/external/media/$id", name, mime, trashedAt)

    private fun row(id: Long, name: String = "IMG_$id.jpg", mime: String = "image/jpeg") =
        DevicePhotoRow(
            id = id,
            uri = Uri.parse("content://media/external/media/$id"),
            name = name,
            bucketName = "Camera",
            dateModified = id * 1000L,
            size = 1000L,
            mimeType = mime
        )

    @Test
    fun encodeDecode_roundTripsEveryField() {
        val original = item(7, name = "holiday clip.mp4", mime = "video/mp4", trashedAt = 123_456L)
        val decoded = decodeTrashEntry(com.vgallery.app.data.gallery.encodeTrashItem(original))
        assertEquals(original, decoded)
    }

    @Test
    fun decode_rejectsMalformedEntries() {
        assertNull(decodeTrashEntry(""))
        assertNull(decodeTrashEntry("content://media/external/media/1"))
        assertNull(decodeTrashEntry("content://media/external/media/1<<>>name<<>>image/jpeg"))
        assertNull(decodeTrashEntry("content://media/external/media/1<<>>name<<>>image/jpeg<<>>notanumber"))
    }

    @Test
    fun listRoundTrip_keepsOrderAndSkipsGarbage() {
        val items = listOf(item(1), item(2, name = "b.png", mime = "image/png"), item(3))
        val raw = encodeTrashList(items)
        assertEquals(items, decodeTrashList(raw))

        val dirty = raw + "|||garbage-entry"
        assertEquals(items, decodeTrashList(dirty))
    }

    @Test
    fun append_replacesExistingEntryForSameUri_andKeepsOthers() {
        val first = appendTrashEntries("", listOf(item(1, trashedAt = 100L), item(2, trashedAt = 100L)))
        // Re-trashing item 1 (e.g. after a restore followed by another delete) replaces its record.
        val second = appendTrashEntries(first, listOf(item(1, trashedAt = 999L)))

        val decoded = decodeTrashList(second)
        assertEquals(2, decoded.size)
        assertEquals(999L, decoded.first { it.uri.endsWith("/1") }.trashedAtMs)
        assertEquals(100L, decoded.first { it.uri.endsWith("/2") }.trashedAtMs)
    }

    @Test
    fun remove_dropsOnlyTheNamedUris() {
        val raw = encodeTrashList(listOf(item(1), item(2), item(3)))
        val afterRemove = removeTrashEntries(raw, setOf(item(2).uri))
        assertEquals(listOf(item(1).uri, item(3).uri), decodeTrashList(afterRemove).map { it.uri })
    }

    @Test
    fun remove_noUris_leavesListUntouched() {
        val raw = encodeTrashList(listOf(item(1), item(2)))
        assertEquals(raw, removeTrashEntries(raw, emptySet()))
    }

    @Test
    fun trashEntryUri_readsTheFirstFieldOnly() {
        val encoded = com.vgallery.app.data.gallery.encodeTrashItem(item(5))
        assertEquals(item(5).uri, trashEntryUri(encoded))
        assertNull(trashEntryUri(""))
    }

    /** The gallery scan must never surface a trashed row. */
    @Test
    fun filterTrashed_hidesExactlyTheTrashedRows() {
        val rows = listOf(row(1), row(2), row(3), row(4))
        val trashed = setOf(row(2).uri.toString(), row(4).uri.toString())
        val visible = filterTrashed(rows, trashed)
        assertEquals(listOf(1L, 3L), visible.map { it.id })
    }

    @Test
    fun filterTrashed_emptySet_keepsEverything() {
        val rows = listOf(row(1), row(2))
        assertEquals(rows, filterTrashed(rows, emptySet()))
    }

    /** 30-day TTL: exactly at the boundary counts as expired, one ms earlier does not. */
    @Test
    fun expiredTrashUris_respectsTtlBoundary() {
        val now = 1_000_000_000L
        val fresh = TrashedItem("content://m/a", "a.jpg", "image/jpeg", now - TRASH_TTL_MS + 1)
        val boundary = TrashedItem("content://m/b", "b.jpg", "image/jpeg", now - TRASH_TTL_MS)
        val old = TrashedItem("content://m/c", "c.jpg", "image/jpeg", now - TRASH_TTL_MS - 1)

        val expired = expiredTrashUris(listOf(fresh, boundary, old), now)
        assertEquals(listOf(boundary.uri, old.uri), expired)
    }

    @Test
    fun trashedItem_classifiesVideoByMime() {
        assertTrue(TrashedItem("u", "clip.mp4", "video/mp4", 0L).isVideo)
        assertTrue(TrashedItem("u", "clip.mkv", "video/x-matroska", 0L).isVideo)
        assertFalse(TrashedItem("u", "pic.jpg", "image/jpeg", 0L).isVideo)
        // A GIF is an image by mime, so the trash tile shows it as a still.
        assertFalse(TrashedItem("u", "anim.gif", "image/gif", 0L).isVideo)
    }
}
