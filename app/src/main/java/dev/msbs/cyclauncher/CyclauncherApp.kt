package dev.msbs.cyclauncher

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.request.crossfade
import dev.msbs.cyclauncher.coil.AppIconFetcher

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application class initializing Coil image loader with memory caching for app icons.
 */
class CyclauncherApp : Application(), SingletonImageLoader.Factory {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var imageLoader: ImageLoader? = null

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            try {
                val legacyCache = cacheDir.resolve("image_cache")
                if (legacyCache.exists()) {
                    legacyCache.deleteRecursively()
                }
            } catch (_: Exception) {}
        }
    }

    override fun newImageLoader(context: Context): ImageLoader {
        val loader = ImageLoader.Builder(context)
            .components {
                add(dev.msbs.cyclauncher.coil.AppIconKeyer())
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

    private val memoryCache: MemoryCache?
        get() = imageLoader?.memoryCache ?: SingletonImageLoader.get(this).memoryCache

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        try {
            val cache = memoryCache ?: return
            when {
                level >= TRIM_MEMORY_COMPLETE -> cache.trimToSize(TRIM_CRITICAL_MEMORY_BYTES)
                level >= TRIM_MEMORY_MODERATE || level == TRIM_MEMORY_RUNNING_CRITICAL -> cache.trimToSize(cache.size / 2)
            }
        } catch (_: Exception) {}
    }

    override fun onLowMemory() {
        super.onLowMemory()
        try {
            memoryCache?.clear()
        } catch (_: Exception) {}
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        try {
            memoryCache?.clear()
        } catch (_: Exception) {}
    }

    private companion object {
        const val MAX_MEMORY_BYTES = 24L * 1024L * 1024L
        const val TRIM_CRITICAL_MEMORY_BYTES = 4L * 1024L * 1024L
    }
}
