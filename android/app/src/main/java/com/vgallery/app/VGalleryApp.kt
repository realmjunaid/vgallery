package com.vgallery.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.gif.AnimatedImageDecoder
import coil3.memory.MemoryCache
import coil3.video.VideoFrameDecoder
import coil3.request.crossfade
import com.vgallery.app.data.gallery.scheduleTrashMaintenance
import com.vgallery.app.di.ApplicationScope
import com.vgallery.app.util.thumbnail.VideoThumbnailFetcher
import com.vgallery.app.util.thumbnail.VideoThumbnailKeyer
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltAndroidApp
class VGalleryApp : Application(), SingletonImageLoader.Factory {

    @Inject
    lateinit var videoThumbnailFetcherFactory: VideoThumbnailFetcher.Factory

    @Inject
    @field:ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // Daily: drop trash entries whose files are already gone. Expired files wait for the
        // user — Android will not delete them from the background.
        scheduleTrashMaintenance(this)
        applicationScope.launch(Dispatchers.IO) {
            // The pre-1.3 thumbnail store (unbounded, keyed on the URI only). Replaced by
            // noBackupFilesDir/video_thumbs.
            File(filesDir, LEGACY_THUMBNAIL_DIR).deleteRecursively()
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                // Animated WebP/GIF first, so motion photos play instead of freezing.
                add(AnimatedImageDecoder.Factory())
                // Video tiles decode a frame thumbnail from the video URI.
                add(VideoFrameDecoder.Factory())
                // Video tiles use VideoThumbnail data; images keep Coil's native pipeline.
                add(VideoThumbnailKeyer())
                add(videoThumbnailFetcherFactory)
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
    }

    private companion object {
        const val LEGACY_THUMBNAIL_DIR = "app_thumbnails"
    }
}
