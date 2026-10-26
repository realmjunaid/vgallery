package com.vgallery.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vgallery.app.data.gallery.deviceGalleryPermissions
import com.vgallery.app.data.gallery.deviceGalleryPermissionsFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The runtime request has to match the platform and the manifest.
 *
 * Android 11/12 (API 30-32) refuse WRITE_EXTERNAL_STORAGE — the manifest caps it at API 29 — so
 * asking for it made the whole request fail and left the gallery stuck on the permission prompt.
 * API 34 also needs READ_MEDIA_VISUAL_USER_SELECTED, or the "Select photos" option never appears.
 *
 * The per-API policy is a pure function, so every level is covered without a Robolectric sandbox
 * per version; the last test checks the current level against the merged manifest.
 */
@RunWith(AndroidJUnit4::class)
class GalleryPermissionTest {

    private val read = Manifest.permission.READ_EXTERNAL_STORAGE
    private val write = Manifest.permission.WRITE_EXTERNAL_STORAGE
    private val images = Manifest.permission.READ_MEDIA_IMAGES
    private val video = Manifest.permission.READ_MEDIA_VIDEO
    private val userSelected = Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED

    @Test
    fun android8And9AskForReadAndWrite() {
        assertEquals(listOf(read, write), deviceGalleryPermissionsFor(26).toList())
        assertEquals(listOf(read, write), deviceGalleryPermissionsFor(28).toList())
    }

    @Test
    fun android10KeepsWriteForTheDeleteConsentDialog() {
        assertEquals(listOf(read, write), deviceGalleryPermissionsFor(29).toList())
    }

    @Test
    fun android11And12AskForReadOnly() {
        // The regression: asking for an undeclared WRITE here failed the whole request.
        assertEquals(listOf(read), deviceGalleryPermissionsFor(30).toList())
        assertEquals(listOf(read), deviceGalleryPermissionsFor(31).toList())
        assertEquals(listOf(read), deviceGalleryPermissionsFor(32).toList())
    }

    @Test
    fun android13AsksForTheGranularReads() {
        assertEquals(listOf(images, video), deviceGalleryPermissionsFor(33).toList())
    }

    @Test
    fun android14AndUpAlsoAskForPartialAccess() {
        for (sdk in intArrayOf(34, 35, 36)) {
            assertEquals("sdk $sdk", listOf(images, video, userSelected), deviceGalleryPermissionsFor(sdk).toList())
        }
    }

    /**
     * Android fails the *entire* request when it contains a permission the app never declared, so
     * this is the invariant the API 30-32 bug broke.
     */
    @Test
    fun everyPermissionAskedForIsDeclaredInTheManifest() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val declared = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            .orEmpty()
            .toSet()
        deviceGalleryPermissions().forEach { permission ->
            assertTrue("$permission is requested but not declared", permission in declared)
        }
    }
}
