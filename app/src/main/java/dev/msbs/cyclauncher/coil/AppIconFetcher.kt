package dev.msbs.cyclauncher.coil

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import dev.msbs.cyclauncher.icons.IconPackManager

/** Key representing an application icon in "packageName/activityName" format. */
data class AppIconKey(val componentKey: String)

/** Coil 3 Fetcher for loading installed application icons via PackageManager. */
internal class AppIconFetcher private constructor(
    private val context: Context,
    private val key: AppIconKey,
    private val options: Options,
) : Fetcher {

    private companion object {
        val iconDispatcher = Dispatchers.IO.limitedParallelism(4)
    }

    override suspend fun fetch(): FetchResult? = withContext(iconDispatcher) {
        val slashIndex = key.componentKey.indexOf('/')
        if (slashIndex <= 0 || slashIndex >= key.componentKey.length - 1) return@withContext null
        val pkg = key.componentKey.substring(0, slashIndex)
        val activity = key.componentKey.substring(slashIndex + 1)
        val pm = context.packageManager

        val iconPackDrawable: Drawable? = try {
            IconPackManager.getIcon(key.componentKey)
        } catch (_: Exception) {
            null
        }

        var isFallback = false
        val drawable: Drawable = iconPackDrawable ?: try {
            resolveIcon(context, pm, pkg, activity)
        } catch (_: Exception) {
            try {
                pm.getApplicationIcon(pkg)
            } catch (_: Exception) {
                isFallback = true
                pm.defaultActivityIcon
            }
        }

        val targetSize = resolveTargetSize(drawable, options)
        val bitmap = drawableToBitmap(drawable, targetSize)

        ImageFetchResult(
            image = bitmap.asImage(),
            isSampled = isFallback,
            dataSource = if (isFallback) DataSource.NETWORK else DataSource.MEMORY,
        )
    }

    private fun resolveTargetSize(drawable: Drawable, options: Options): Int {
        val reqPx = (options.size.width as? coil3.size.Dimension.Pixels)?.px
        if (reqPx != null && reqPx > 0) {
            return reqPx.coerceIn(32, 288)
        }
        val intrinsic = drawable.intrinsicWidth
        return if (intrinsic > 0) intrinsic.coerceIn(48, 192) else 144
    }

    private fun drawableToBitmap(drawable: Drawable, size: Int): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            val src = drawable.bitmap
            if (src.width == size && src.height == size) {
                return src
            }
            if (src.width > 0 && src.height > 0) {
                return Bitmap.createScaledBitmap(src, size, size, true)
            }
        }
        val safeSize = size.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(safeSize, safeSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, safeSize, safeSize)
        drawable.draw(canvas)
        return bitmap
    }

    private fun resolveIcon(context: Context, pm: PackageManager, pkg: String, activity: String): Drawable {
        val component = android.content.ComponentName(pkg, activity)
        try {
            return pm.getActivityIcon(component)
        } catch (_: Exception) {}

        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? android.content.pm.LauncherApps
        if (launcherApps != null) {
            try {
                val list = launcherApps.getActivityList(pkg, android.os.Process.myUserHandle())
                val activityInfo = list.firstOrNull { it.componentName.className == activity } ?: list.firstOrNull()
                if (activityInfo != null) {
                    return activityInfo.getIcon(0)
                }
            } catch (_: Exception) {}
        }

        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getActivityInfo(component, PackageManager.ComponentInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.getActivityInfo(component, 0)
        }
        return info.loadIcon(pm)
    }

    class Factory(private val context: Context) : Fetcher.Factory<Any> {
        override fun create(data: Any, options: Options, imageLoader: ImageLoader): Fetcher? {
            val key = when (data) {
                is AppIconKey -> data
                is String -> {
                    val slash = data.indexOf('/')
                    if (slash <= 0 || slash >= data.length - 1 || data.contains("://")) return null
                    AppIconKey(data)
                }
                else -> return null
            }
            return AppIconFetcher(context.applicationContext, key, options)
        }
    }
}
