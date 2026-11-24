package dev.msbs.cyclauncher.coil

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.mutableLongStateOf
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options
import dev.msbs.cyclauncher.icons.IconPackManager
import dev.msbs.cyclauncher.icons.IconShapeHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Manager tracking per-package version epochs with Compose snapshot reactivity
 * for immediate Coil memory cache invalidation and single-package recomposition.
 */
object IconEpochManager {
    private val packageEpochs = ConcurrentHashMap<String, Long>()
    private val composePackageStates = ConcurrentHashMap<String, MutableLongState>()
    private val globalClearCounter = AtomicLong(0L)
    private val composeGlobalClearVersion = mutableLongStateOf(0L)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun getEpoch(packageName: String): Long = packageEpochs[packageName] ?: 0L

    @Composable
    fun observeGlobalClearVersion(): Long = composeGlobalClearVersion.longValue

    /**
     * Compose-tracked read of a package's icon epoch.
     * Only icons for [packageName] recompose when [incrementEpoch] is called for it;
     * all icons recompose when [clear] is invoked.
     */
    @Composable
    fun observeEpoch(packageName: String): Long {
        // Subscribe to global cache clear version
        composeGlobalClearVersion.longValue
        val state = composePackageStates.computeIfAbsent(packageName) {
            mutableLongStateOf(packageEpochs[packageName] ?: 0L)
        }
        val observed = state.longValue
        val authoritative = packageEpochs[packageName] ?: 0L
        return if (authoritative == 0L) observed else maxOf(observed, authoritative)
    }

    fun incrementEpoch(packageName: String): Long {
        val next = packageEpochs.compute(packageName) { _, current -> (current ?: 0L) + 1L } ?: 1L
        val state = composePackageStates.computeIfAbsent(packageName) { mutableLongStateOf(next) }
        val updateSnapshot = Runnable {
            state.longValue = packageEpochs[packageName] ?: next
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            updateSnapshot.run()
        } else {
            mainHandler.post(updateSnapshot)
        }
        return next
    }

    fun clear() {
        packageEpochs.clear()
        val nextClear = globalClearCounter.incrementAndGet()
        val updateSnapshot = Runnable {
            for ((pkg, state) in composePackageStates) {
                state.longValue = packageEpochs[pkg] ?: 0L
            }
            composeGlobalClearVersion.longValue = nextClear
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            updateSnapshot.run()
        } else {
            mainHandler.post(updateSnapshot)
        }
    }
}

/**
 * Key representing an application icon in "packageName/activityName" format with version epoch,
 * active icon pack version, and system icon shape generation.
 */
data class AppIconKey(
    val componentKey: String,
    val epoch: Long = IconEpochManager.getEpoch(componentKey.substringBefore('/').substringBefore('#')),
    val iconPackVersionKey: String = "${IconPackManager.activePackageName.orEmpty()}@${IconPackManager.packGeneration}",
    val shapeGeneration: Long = IconShapeHelper.shapeGeneration
)

/** Coil 3 Keyer for computing stable memory cache keys for [AppIconKey]. */
class AppIconKeyer : Keyer<AppIconKey> {
    override fun key(data: AppIconKey, options: Options): String {
        return "appicon:${data.componentKey}:e=${data.epoch}:p=${data.iconPackVersionKey}:s=${data.shapeGeneration}"
    }
}

/** Coil 3 Fetcher for loading installed application icons via PackageManager. */
internal class AppIconFetcher private constructor(
    private val context: Context,
    private val key: AppIconKey,
    private val options: Options,
) : Fetcher {

    private data class ResolvedDrawable(
        val drawable: Drawable,
        val isFallback: Boolean
    )

    companion object {
        private const val SERIAL_PREFIX = "serial:"
        private const val HASH_PREFIX = "hash:"
        private val iconDispatcher = Dispatchers.IO.limitedParallelism(4)

        @Volatile
        private var userProfilesCache: Map<String, UserHandle> = emptyMap()

        fun updateUserProfiles(profiles: List<UserHandle>, userManager: UserManager? = null) {
            val newMap = HashMap<String, UserHandle>(profiles.size * 2)
            for (p in profiles) {
                newMap["$HASH_PREFIX${p.hashCode()}"] = p
                try {
                    val serial = userManager?.getSerialNumberForUser(p)
                    if (serial != null && serial >= 0L) {
                        newMap["$SERIAL_PREFIX$serial"] = p
                    }
                } catch (_: Throwable) {}
            }
            userProfilesCache = newMap
        }

        fun evictProfileIcons(imageLoader: ImageLoader, userSuffixes: Collection<String>) {
            try {
                val memoryCache = imageLoader.memoryCache ?: return
                val exactMarkers = userSuffixes
                    .filter { it.isNotBlank() }
                    .map { "#$it:e=" }
                if (exactMarkers.isEmpty()) return
                val keysToRemove = memoryCache.keys.filter { cacheKey ->
                    val keyStr = cacheKey.key
                    exactMarkers.any { marker -> keyStr.contains(marker) }
                }
                for (k in keysToRemove) {
                    memoryCache.remove(k)
                }
            } catch (_: Exception) {}
        }
    }

    override suspend fun fetch(): FetchResult? = withContext(iconDispatcher) {
        try {
            val slashIndex = key.componentKey.indexOf('/')
            val pkg: String
            val activityWithUser: String?
            if (slashIndex > 0) {
                pkg = key.componentKey.substring(0, slashIndex)
                activityWithUser = key.componentKey.substring(slashIndex + 1).takeIf { it.isNotBlank() }
            } else {
                pkg = key.componentKey.substringBefore('#')
                activityWithUser = null
            }
            if (pkg.isBlank()) return@withContext null
            val pm = context.packageManager

            val userIdentifierStr = key.componentKey.substringAfter('#', "")
            val myUser = Process.myUserHandle()
            val targetUser = resolveTargetUser(context, userIdentifierStr, myUser)

            // Never load or cache icons for a secondary profile while it is in quiet/locked mode
            if (targetUser != myUser) {
                val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager
                val isQuiet = try {
                    userManager?.isQuietModeEnabled(targetUser) == true
                } catch (_: Throwable) {
                    false
                }
                if (isQuiet) {
                    return@withContext null
                }
            }

            val iconPackDrawable: Drawable? = try {
                val rawPackIcon = IconPackManager.getIcon(key.componentKey)
                if (rawPackIcon != null && targetUser != myUser) {
                    try {
                        pm.getUserBadgedIcon(rawPackIcon, targetUser)
                    } catch (_: Throwable) {
                        rawPackIcon
                    }
                } else {
                    rawPackIcon
                }
            } catch (_: Throwable) {
                null
            }

            val resolved: ResolvedDrawable = if (iconPackDrawable != null) {
                ResolvedDrawable(iconPackDrawable, isFallback = false)
            } else {
                try {
                    if (activityWithUser != null) {
                        resolveIcon(context, pm, pkg, activityWithUser, targetUser, myUser)
                    } else {
                        val appDrawable = pm.getApplicationIcon(pkg)
                        val badged = if (targetUser != myUser) {
                            try { pm.getUserBadgedIcon(appDrawable, targetUser) } catch (_: Throwable) { appDrawable }
                        } else {
                            appDrawable
                        }
                        ResolvedDrawable(badged, isFallback = false)
                    }
                } catch (_: Throwable) {
                    try {
                        val appDrawable = pm.getApplicationIcon(pkg)
                        val badged = if (targetUser != myUser) {
                            try { pm.getUserBadgedIcon(appDrawable, targetUser) } catch (_: Throwable) { appDrawable }
                        } else {
                            appDrawable
                        }
                        ResolvedDrawable(badged, isFallback = targetUser != myUser)
                    } catch (_: Throwable) {
                        ResolvedDrawable(pm.defaultActivityIcon, isFallback = true)
                    }
                }
            }

            val targetSize = resolveTargetSize(resolved.drawable, options)
            val bitmap = drawableToBitmap(resolved.drawable, targetSize) ?: return@withContext null

            // Mark fallback images shareable=false so Coil 3's MemoryCacheService never caches them
            ImageFetchResult(
                image = bitmap.asImage(shareable = !resolved.isFallback),
                isSampled = resolved.isFallback,
                dataSource = if (resolved.isFallback) DataSource.NETWORK else DataSource.DISK,
            )
        } catch (_: Throwable) {
            null
        }
    }

    private fun resolveTargetUser(context: Context, userIdentifierStr: String, myUser: UserHandle): UserHandle {
        if (userIdentifierStr.isEmpty()) return myUser
        val cache = userProfilesCache
        cache["$SERIAL_PREFIX$userIdentifierStr"]?.let { return it }
        cache["$HASH_PREFIX$userIdentifierStr"]?.let { return it }

        val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager ?: return myUser
        val asLong = userIdentifierStr.toLongOrNull()
        val foundBySerial = if (asLong != null) {
            try { userManager.getUserForSerialNumber(asLong) } catch (_: Throwable) { null }
        } else null
        if (foundBySerial != null) {
            val updated = HashMap(userProfilesCache)
            updated["$SERIAL_PREFIX$userIdentifierStr"] = foundBySerial
            userProfilesCache = updated
            return foundBySerial
        }
        val foundByHash = try {
            userManager.userProfiles.find { it.hashCode().toString() == userIdentifierStr }
        } catch (_: Throwable) {
            null
        }
        if (foundByHash != null) {
            val updated = HashMap(userProfilesCache)
            updated["$HASH_PREFIX$userIdentifierStr"] = foundByHash
            userProfilesCache = updated
            return foundByHash
        }
        return myUser
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

    private fun drawableToBitmap(drawable: Drawable, size: Int): Bitmap? {
        val safeSize = size.coerceAtLeast(1)
        val bitmap = try {
            Bitmap.createBitmap(safeSize, safeSize, Bitmap.Config.ARGB_8888)
        } catch (_: Throwable) {
            return null
        }
        val canvas = Canvas(bitmap)

        // Apply bilinear filtering and dithering to eliminate pixelation and banding (matching AOSP Launcher3 BaseIconFactory)
        canvas.drawFilter = android.graphics.PaintFlagsDrawFilter(
            android.graphics.Paint.DITHER_FLAG,
            android.graphics.Paint.FILTER_BITMAP_FLAG
        )

        val scaledPath = IconShapeHelper.getScaledPath(context, safeSize)

        canvas.save()
        canvas.clipPath(scaledPath)

        val workingDrawable = try {
            drawable.mutate()
        } catch (_: Throwable) {
            drawable
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && workingDrawable is android.graphics.drawable.AdaptiveIconDrawable) {
            workingDrawable.setBounds(0, 0, safeSize, safeSize)
            workingDrawable.draw(canvas)
        } else {
            if (workingDrawable is BitmapDrawable) {
                workingDrawable.isFilterBitmap = true
                workingDrawable.setDither(true)
            }
            val inset = (safeSize * 0.10f).toInt()
            workingDrawable.setBounds(inset, inset, safeSize - inset, safeSize - inset)
            workingDrawable.draw(canvas)
        }

        canvas.restore()
        return bitmap
    }

    private fun resolveIcon(
        context: Context,
        pm: PackageManager,
        pkg: String,
        activityWithUser: String,
        targetUser: UserHandle,
        myUser: UserHandle
    ): ResolvedDrawable {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val iconDpi = am?.launcherLargeIconDensity?.takeIf { it > 0 } ?: context.resources.displayMetrics.densityDpi
        val activity = activityWithUser.substringBefore('#')

        // Fast path for primary personal profile: load high-density launcher asset via PackageManager
        if (targetUser == myUser) {
            val component = android.content.ComponentName(pkg, activity)
            loadActivityDrawable(pm, component, iconDpi)?.let {
                return ResolvedDrawable(it, isFallback = false)
            }
        }

        // Secondary profiles (Work Profile / Private Space): resolve badged icon via LauncherApps using preferred density
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? android.content.pm.LauncherApps
        if (launcherApps != null) {
            try {
                val list = launcherApps.getActivityList(pkg, targetUser)
                val activityInfo = list.firstOrNull { it.componentName.className == activity } ?: list.firstOrNull()
                if (activityInfo != null) {
                    return ResolvedDrawable(activityInfo.getBadgedIcon(iconDpi), isFallback = false)
                }
            } catch (_: Exception) {}
        }

        val component = android.content.ComponentName(pkg, activity)
        val loaded = loadActivityDrawable(pm, component, iconDpi)
        if (loaded != null) {
            val badged = if (targetUser != myUser) {
                try { pm.getUserBadgedIcon(loaded, targetUser) } catch (_: Throwable) { loaded }
            } else {
                loaded
            }
            return ResolvedDrawable(badged, isFallback = targetUser != myUser)
        }
        val fallback = if (targetUser != myUser) {
            try { pm.getUserBadgedIcon(pm.defaultActivityIcon, targetUser) } catch (_: Throwable) { pm.defaultActivityIcon }
        } else {
            pm.defaultActivityIcon
        }
        return ResolvedDrawable(fallback, isFallback = true)
    }

    private fun loadActivityDrawable(
        pm: PackageManager,
        component: android.content.ComponentName,
        iconDpi: Int
    ): Drawable? {
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
            return try {
                pm.getActivityIcon(component)
            } catch (_: Exception) {
                null
            }
        }
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
