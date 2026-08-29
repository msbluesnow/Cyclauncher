package dev.msbs.cyclauncher

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.request.crossfade
import dev.msbs.cyclauncher.coil.AppIconFetcher

/**
 * Application class initializing Coil image loader with memory caching for app icons.
 */
class CyclauncherApp : Application(), SingletonImageLoader.Factory {

    private var imageLoader: ImageLoader? = null

    override fun onCreate() {
        super.onCreate()
        try {
            cacheDir.resolve("image_cache").deleteRecursively()
        } catch (_: Exception) {}
    }

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
            .diskCache(null)
            .crossfade(false)
            .build()
        imageLoader = loader
        return loader
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        try {
            val cache = imageLoader?.memoryCache ?: SingletonImageLoader.get(this).memoryCache
            if (level >= TRIM_MEMORY_COMPLETE) {
                cache?.clear()
            } else if (level >= TRIM_MEMORY_RUNNING_CRITICAL || level >= TRIM_MEMORY_MODERATE) {
                cache?.let { c ->
                    c.trimToSize(c.size / 2)
                }
            }
        } catch (_: Exception) {}
    }

    override fun onLowMemory() {
        super.onLowMemory()
        try {
            (imageLoader?.memoryCache ?: SingletonImageLoader.get(this).memoryCache)?.clear()
        } catch (_: Exception) {}
    }

    private companion object {
        const val MAX_MEMORY_BYTES = 24L * 1024L * 1024L
    }
}
