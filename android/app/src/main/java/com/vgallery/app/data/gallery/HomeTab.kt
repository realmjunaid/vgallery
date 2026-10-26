package com.vgallery.app.data.gallery

/**
 * The two home tabs.
 *
 * Photos holds still images; Videos holds everything that plays — real videos plus
 * GIFs and animated WebPs, which MediaStore reports as images. The split is
 * exclusive: no row belongs to both tabs.
 */
enum class HomeTab { PHOTOS, VIDEOS }

/** True when this row plays back (video, GIF or animated WebP), i.e. it lives in Videos. */
val DevicePhotoRow.inVideosTab: Boolean
    get() = isVideo || isMotionImage

/** True when this row belongs in [tab]. */
fun DevicePhotoRow.inTab(tab: HomeTab): Boolean =
    if (tab == HomeTab.VIDEOS) inVideosTab else !inVideosTab
