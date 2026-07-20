package dev.msbs.cyclauncher

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.crossfade
import dev.msbs.cyclauncher.coil.AppIconFetcher
import okio.Path.Companion.toOkioPath

/**
 * Application entry point. Sets up Coil's singleton [ImageLoader] with:
 *  - the custom [AppIconFetcher] wired into the component chain;
 *  - an LRU memory cache with hard caps so decoded app icons can be evicted under memory pressure;
 *  - a bounded disk cache so icons survive process death and don't re-decode on every reload.
 *
 * Registering here (rather than inline in Composables) keeps image loading off the main thread
 * and out of the ViewModel, which is the whole point of moving off the in-memory `ImageBitmap`
 * field on [dev.msbs.cyclauncher.model.AppInfo].
 */
class CyclauncherApp : Application(), SingletonImageLoader.Factory {

    private var imageLoader: ImageLoader? = null

    override fun newImageLoader(context: Context): ImageLoader {
        val loader = ImageLoader.Builder(context)
            .components {
                add(AppIconFetcher.Factory(context))
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizeBytes(MAX_MEMORY_BYTES)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("app_icons").toOkioPath())
                    .maxSizeBytes(MAX_DISK_BYTES)
                    .build()
            }
            .crossfade(false)
            .build()
        imageLoader = loader
        return loader
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // If the UI is hidden (app goes to background) or memory is critical, clear the memory cache.
        if (level >= TRIM_MEMORY_UI_HIDDEN) {
            imageLoader?.memoryCache?.clear()
            // Force the Garbage Collector to run immediately, reclaiming all memory allocated 
            // by native bitmaps and wrapper objects, dropping background PSS footprint instantly.
            System.gc()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        imageLoader?.memoryCache?.clear()
        System.gc()
    }

    private companion object {
        // 24 MiB of decoded bitmaps in memory is plenty for a launcher grid (most icons are
        // 96–144 px). Beyond this Coil evicts least-recently-used entries — exactly the
        // behavior the old in-memory `ImageBitmap` field lacked.
        const val MAX_MEMORY_BYTES = 24L * 1024L * 1024L
        // 64 MiB on disk is enough for thousands of 96 px PNGs and still leaves the rest of
        // the app's cache budget untouched.
        const val MAX_DISK_BYTES = 64L * 1024L * 1024L
    }
}
