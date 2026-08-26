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
import coil3.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Key representing an application icon in "packageName/activityName" format. */
data class AppIconKey(val componentKey: String)

/** Coil 3 Fetcher for loading installed application icons via PackageManager. */
internal class AppIconFetcher private constructor(
    private val context: Context,
    private val key: AppIconKey,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val parts = key.componentKey.split("/", limit = 2)
        if (parts.size != 2) return@withContext null
        val (pkg, activity) = parts

        val drawable: Drawable = try {
            resolveIcon(pm, pkg, activity)
        } catch (_: Exception) {
            pm.defaultActivityIcon
        }

        val targetSize = resolveTargetSize(drawable, options)
        val bitmap = drawableToBitmap(drawable, targetSize)

        ImageFetchResult(
            image = bitmap.asImage(),
            isSampled = false,
            dataSource = DataSource.DISK,
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

    private fun resolveIcon(pm: PackageManager, pkg: String, activity: String): Drawable {
        val component = android.content.ComponentName(pkg, activity)
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
                    if (data.startsWith("/")) return null
                    val uri = data.toUri()
                    if (uri.scheme != null) return null
                    if (!data.contains('/')) return null
                    AppIconKey(data)
                }
                else -> return null
            }
            return AppIconFetcher(context.applicationContext, key, options)
        }
    }
}
