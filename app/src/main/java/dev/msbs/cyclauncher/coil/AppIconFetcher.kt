package dev.msbs.cyclauncher.coil

import android.app.ActivityManager
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
import coil3.key.Keyer
import coil3.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import dev.msbs.cyclauncher.icons.IconPackManager

/** Key representing an application icon in "packageName/activityName" format. */
data class AppIconKey(val componentKey: String)

/** Coil 3 Keyer for computing stable memory cache keys for [AppIconKey]. */
class AppIconKeyer : Keyer<AppIconKey> {
    override fun key(data: AppIconKey, options: Options): String {
        return data.componentKey
    }
}

/** Coil 3 Fetcher for loading installed application icons via PackageManager. */
internal class AppIconFetcher private constructor(
    private val context: Context,
    private val key: AppIconKey,
    private val options: Options,
) : Fetcher {

    companion object {
        private val iconDispatcher = Dispatchers.IO.limitedParallelism(4)
        private val userProfilesCache = java.util.concurrent.ConcurrentHashMap<String, android.os.UserHandle>()

        fun updateUserProfiles(profiles: List<android.os.UserHandle>) {
            userProfilesCache.clear()
            for (p in profiles) {
                userProfilesCache[p.hashCode().toString()] = p
            }
        }

        fun evictProfileIcons(imageLoader: ImageLoader, userHashCodeStr: String) {
            try {
                val memoryCache = imageLoader.memoryCache ?: return
                val suffix = "#$userHashCodeStr"
                val keysToRemove = memoryCache.keys.filter { it.toString().contains(suffix) }
                for (k in keysToRemove) {
                    memoryCache.remove(k)
                }
            } catch (_: Exception) {}
        }
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
            dataSource = if (isFallback) DataSource.NETWORK else DataSource.DISK,
        )
    }

    private fun resolveTargetSize(drawable: Drawable, options: Options): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val standardSize = am?.launcherLargeIconSize?.takeIf { it > 0 }
            ?: (context.resources.displayMetrics.density * 48).toInt()

        val reqPx = (options.size.width as? coil3.size.Dimension.Pixels)?.px
        if (reqPx != null && reqPx > 0) {
            // Never render below standard launcher icon size so small preview icons (e.g. 16dp tags, 20dp menus)
            // don't pollute the Coil MemoryCache with low-resolution bitmaps for the main UI.
            return maxOf(reqPx, standardSize).coerceIn(48, 288)
        }
        val intrinsic = drawable.intrinsicWidth
        return if (intrinsic > 0) maxOf(intrinsic, standardSize).coerceIn(48, 288) else standardSize
    }

    private fun drawableToBitmap(drawable: Drawable, size: Int): Bitmap {
        val safeSize = size.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(safeSize, safeSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Apply bilinear filtering and dithering to eliminate pixelation and banding (matching AOSP Launcher3 BaseIconFactory)
        canvas.drawFilter = android.graphics.PaintFlagsDrawFilter(
            android.graphics.Paint.DITHER_FLAG,
            android.graphics.Paint.FILTER_BITMAP_FLAG
        )

        val scaledPath = dev.msbs.cyclauncher.icons.IconShapeHelper.getScaledPath(context, safeSize)

        canvas.save()
        canvas.clipPath(scaledPath)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && drawable is android.graphics.drawable.AdaptiveIconDrawable) {
            drawable.setBounds(0, 0, safeSize, safeSize)
            drawable.draw(canvas)
        } else {
            if (drawable is BitmapDrawable) {
                drawable.isFilterBitmap = true
                drawable.setDither(true)
            }
            val inset = (safeSize * 0.10f).toInt()
            drawable.setBounds(inset, inset, safeSize - inset, safeSize - inset)
            drawable.draw(canvas)
        }

        canvas.restore()
        return bitmap
    }

    private fun resolveIcon(context: Context, pm: PackageManager, pkg: String, activityWithUser: String): Drawable {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val iconDpi = am?.launcherLargeIconDensity?.takeIf { it > 0 } ?: context.resources.displayMetrics.densityDpi
        val activity = activityWithUser.substringBefore('#')
        val userHashCodeStr = activityWithUser.substringAfter('#', "")

        val myUser = android.os.Process.myUserHandle()
        val targetUser = if (userHashCodeStr.isNotEmpty()) {
            userProfilesCache[userHashCodeStr] ?: run {
                val userManager = context.getSystemService(Context.USER_SERVICE) as? android.os.UserManager
                val found = userManager?.userProfiles?.find { it.hashCode().toString() == userHashCodeStr }
                if (found != null) {
                    userProfilesCache[userHashCodeStr] = found
                    found
                } else {
                    myUser
                }
            }
        } else {
            myUser
        }

        // Fast path for primary personal profile: load high-density launcher asset via PackageManager
        if (targetUser == myUser) {
            val component = android.content.ComponentName(pkg, activity)
            try {
                val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getActivityInfo(component, PackageManager.ComponentInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getActivityInfo(component, 0)
                }
                val iconRes = info.iconResource.takeIf { it != 0 } ?: info.applicationInfo.icon
                if (iconRes != 0) {
                    try {
                        val res = pm.getResourcesForApplication(info.applicationInfo)
                        val dr = res.getDrawableForDensity(iconRes, iconDpi, null)
                        if (dr != null) return dr
                    } catch (_: Exception) {}
                }
                return info.loadIcon(pm)
            } catch (_: Exception) {
                try {
                    return pm.getActivityIcon(component)
                } catch (_: Exception) {}
            }
        }

        // Secondary profiles (Work Profile / Private Space): resolve badged icon via LauncherApps using preferred density
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? android.content.pm.LauncherApps
        if (launcherApps != null) {
            try {
                val list = launcherApps.getActivityList(pkg, targetUser)
                val activityInfo = list.firstOrNull { it.componentName.className == activity } ?: list.firstOrNull()
                if (activityInfo != null) {
                    return activityInfo.getBadgedIcon(iconDpi)
                }
            } catch (_: Exception) {}
        }

        val component = android.content.ComponentName(pkg, activity)
        val drawable = try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getActivityInfo(component, PackageManager.ComponentInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getActivityInfo(component, 0)
            }
            val iconRes = info.iconResource.takeIf { it != 0 } ?: info.applicationInfo.icon
            if (iconRes != 0) {
                try {
                    val res = pm.getResourcesForApplication(info.applicationInfo)
                    val dr = res.getDrawableForDensity(iconRes, iconDpi, null)
                    dr ?: info.loadIcon(pm)
                } catch (_: Exception) {
                    info.loadIcon(pm)
                }
            } else {
                info.loadIcon(pm)
            }
        } catch (_: Exception) {
            try {
                pm.getActivityIcon(component)
            } catch (_: Exception) {
                pm.defaultActivityIcon
            }
        }

        return pm.getUserBadgedIcon(drawable, targetUser)
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
