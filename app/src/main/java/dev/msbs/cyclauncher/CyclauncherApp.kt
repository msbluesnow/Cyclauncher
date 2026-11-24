package dev.msbs.cyclauncher

import android.app.Application
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.request.crossfade
import dev.msbs.cyclauncher.coil.AppIconFetcher
import dev.msbs.cyclauncher.icons.IconShapeHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application class initializing Coil image loader with memory caching for app icons.
 */
class CyclauncherApp : Application(), SingletonImageLoader.Factory {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var imageLoader: ImageLoader? = null

    private var lastConfig: Configuration? = null

    override fun onCreate() {
        super.onCreate()
        lastConfig = Configuration(resources.configuration)
        appScope.launch {
            try {
                val legacyCache = cacheDir.resolve("image_cache")
                if (legacyCache.exists()) {
                    legacyCache.deleteRecursively()
                }
            } catch (_: Exception) {}
            try {
                IconShapeHelper.preload(this@CyclauncherApp)
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
        get() = imageLoader?.memoryCache

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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val prev = lastConfig
        val diff = prev?.diff(newConfig) ?: 0
        lastConfig = Configuration(newConfig)
        val iconAffectingMask = ActivityInfo.CONFIG_UI_MODE or
            ActivityInfo.CONFIG_DENSITY or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ActivityInfo.CONFIG_ASSETS_PATHS else 0
        if ((diff and iconAffectingMask) != 0) {
            try {
                memoryCache?.clear()
            } catch (_: Exception) {}
        }
    }

    private companion object {
        const val MAX_MEMORY_BYTES = 24L * 1024L * 1024L
        const val TRIM_CRITICAL_MEMORY_BYTES = 4L * 1024L * 1024L
    }
}
