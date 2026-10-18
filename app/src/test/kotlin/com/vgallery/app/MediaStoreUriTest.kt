package com.vgallery.app

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vgallery.app.data.gallery.mediaStorePrimaryUri
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Permanent delete addresses the writable `external_primary` volume: the synthetic `external`
 * volume is readable but not writable, and MediaStore's batch delete request rejects it — which
 * is what made "Delete forever" / "Empty trash" silently do nothing. These tests pin the rewrite
 * so every scanned URI can be deleted and consented for.
 */
@RunWith(AndroidJUnit4::class)
class MediaStoreUriTest {

    private fun rewrite(uri: String) = mediaStorePrimaryUri(Uri.parse(uri)).toString()

    @Test
    fun syntheticExternalImageUriBecomesPrimary() {
        assertEquals(
            "content://media/external_primary/images/media/42",
            rewrite("content://media/external/images/media/42")
        )
    }

    @Test
    fun syntheticExternalVideoUriBecomesPrimary() {
        assertEquals(
            "content://media/external_primary/video/media/7",
            rewrite("content://media/external/video/media/7")
        )
    }

    @Test
    fun primaryVolumeUriIsLeftAlone() {
        val uri = "content://media/external_primary/images/media/42"
        assertEquals(uri, rewrite(uri))
    }

    @Test
    fun realSecondaryVolumeUriIsLeftAlone() {
        val uri = "content://media/1234-5678/images/media/3"
        assertEquals(uri, rewrite(uri))
    }

    @Test
    fun collectionUriWithoutIdIsLeftAlone() {
        val uri = "content://media/external/images/media"
        assertEquals(uri, rewrite(uri))
    }

    @Test
    fun nonMediaAuthorityIsLeftAlone() {
        val uri = "content://com.example.documents/document/5"
        assertEquals(uri, rewrite(uri))
    }

    @Test
    fun nonNumericIdIsLeftAlone() {
        val uri = "content://media/external/images/media/not-an-id"
        assertEquals(uri, rewrite(uri))
    }
}
