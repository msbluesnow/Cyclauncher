package dev.msbs.cyclauncher.data

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import dev.msbs.cyclauncher.icons.IconPackManager
import dev.msbs.cyclauncher.model.AppColorBucket
import dev.msbs.cyclauncher.model.AppInfo
import dev.msbs.cyclauncher.utils.AppColorExtractor
import dev.msbs.cyclauncher.utils.getSafeStorageContext
import dev.msbs.cyclauncher.utils.isUserUnlockedSafe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Manages persistent caching and background indexing of application icon colors.
 */
class AppColorManager(context: Context) {
    private val appContext = context.getSafeStorageContext()
    private val legacyPrefs: SharedPreferences = appContext.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
    private val prefs: SharedPreferences = appContext.getSharedPreferences("launcher_cache_prefs", Context.MODE_PRIVATE)
    private val stateLock = Any()

    companion object {
        private const val CURRENT_ALGO_VERSION = 5
    }

    init {
        migrateLegacyColorCacheIfNeeded()
    }

    private fun migrateLegacyColorCacheIfNeeded() {
        try {
            val hasNewData = prefs.contains("app_icon_colors") || prefs.contains("cached_colors_algo_version")
            val hasLegacyData = legacyPrefs.contains("app_icon_colors") ||
                legacyPrefs.contains("cached_colors_algo_version") ||
                legacyPrefs.contains("cached_colors_icon_pack_version")
            if (!hasLegacyData) return
            if (!hasNewData) {
                val editor = prefs.edit()
                legacyPrefs.getString("app_icon_colors", null)?.let { editor.putString("app_icon_colors", it) }
                if (legacyPrefs.contains("cached_colors_icon_pack_version")) {
                    editor.putLong("cached_colors_icon_pack_version", legacyPrefs.getLong("cached_colors_icon_pack_version", -1L))
                }
                if (legacyPrefs.contains("cached_colors_algo_version")) {
                    editor.putInt("cached_colors_algo_version", legacyPrefs.getInt("cached_colors_algo_version", 0))
                }
                if (appContext.isUserUnlockedSafe()) {
                    editor.apply()
                }
            }
            if (appContext.isUserUnlockedSafe()) {
                legacyPrefs.edit()
                    .remove("app_icon_colors")
                    .remove("cached_colors_icon_pack_version")
                    .remove("cached_colors_algo_version")
                    .apply()
            }
        } catch (_: Exception) {}
    }

    private var lastIndexedIconPackVersion: Long = prefs.getLong(
        "cached_colors_icon_pack_version",
        legacyPrefs.getLong("cached_colors_icon_pack_version", -1L)
    )
    private var indexingJob: Job? = null

    private val _appColors = MutableStateFlow<Map<String, AppColorBucket>>(loadCachedColors())
    val appColors: StateFlow<Map<String, AppColorBucket>> = _appColors

    private fun loadCachedColors(): Map<String, AppColorBucket> {
        val sourcePrefs = if (prefs.contains("cached_colors_algo_version") || prefs.contains("app_icon_colors")) prefs else legacyPrefs
        val savedAlgoVersion = sourcePrefs.getInt("cached_colors_algo_version", 0)
        if (savedAlgoVersion < CURRENT_ALGO_VERSION) {
            if (appContext.isUserUnlockedSafe()) {
                prefs.edit()
                    .remove("app_icon_colors")
                    .remove("cached_colors_icon_pack_version")
                    .putInt("cached_colors_algo_version", CURRENT_ALGO_VERSION)
                    .apply()
            }
            lastIndexedIconPackVersion = -1L
            return emptyMap()
        }

        val jsonStr = sourcePrefs.getString("app_icon_colors", null) ?: return emptyMap()
        val result = mutableMapOf<String, AppColorBucket>()
        try {
            val json = JSONObject(jsonStr)
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val bucketName = if (json.isNull(key)) null else json.optString(key).takeIf { it.isNotEmpty() }
                val bucket = AppColorBucket.fromNameOrNull(bucketName)
                if (bucket != null) {
                    result[key] = bucket
                }
            }
        } catch (_: Exception) {}
        return result
    }

    private fun saveCachedColors(colors: Map<String, AppColorBucket>, iconPackVersion: Long) {
        if (!appContext.isUserUnlockedSafe()) return
        try {
            val json = JSONObject()
            for ((k, v) in colors) {
                json.put(k, v.name)
            }
            prefs.edit()
                .putString("app_icon_colors", json.toString())
                .putLong("cached_colors_icon_pack_version", iconPackVersion)
                .putInt("cached_colors_algo_version", CURRENT_ALGO_VERSION)
                .apply()
        } catch (_: Exception) {}
    }

    /**
     * Reloads persisted icon color cache from preferences (e.g. after Direct Boot CE->DE migration).
     */
    fun reloadFromPrefs() {
        synchronized(stateLock) {
            indexingJob?.cancel()
            indexingJob = null
            migrateLegacyColorCacheIfNeeded()
            lastIndexedIconPackVersion = prefs.getLong("cached_colors_icon_pack_version", -1L)
            _appColors.value = loadCachedColors()
        }
    }

    /**
     * Synchronously commits any pending SharedPreferences edits to disk (used before process relaunch).
     */
    fun flushPendingWrites() {
        synchronized(stateLock) {
            try {
                prefs.edit().commit()
            } catch (_: Exception) {}
        }
    }

    /**
     * Asynchronously indexes colors for the given apps without blocking the UI.
     * Set [pruneMissing] to false when indexing a subset (e.g. a single unlocked profile)
     * so colors cached for other profiles are preserved.
     */
    fun indexApps(
        scope: CoroutineScope,
        apps: List<AppInfo>,
        iconPackVersion: Long,
        pruneMissing: Boolean = true
    ) {
        if (apps.isEmpty()) return
        val jobToStart: Job
        synchronized(stateLock) {
            indexingJob?.cancel()
            val newJob = scope.launch(Dispatchers.Default, start = CoroutineStart.LAZY) {
                val (isIconPackChanged, baseMap) = synchronized(stateLock) {
                    val changed = iconPackVersion != lastIndexedIconPackVersion
                    val map = if (changed) {
                        mutableMapOf()
                    } else {
                        _appColors.value.toMutableMap()
                    }
                    changed to map
                }

                val activeKeys = apps.mapTo(HashSet(apps.size * 2)) { it.componentKey }
                val staleRemoved = if (pruneMissing && !isIconPackChanged && apps.isNotEmpty()) {
                    baseMap.keys.removeAll { it !in activeKeys }
                } else {
                    false
                }

                val pm = appContext.packageManager
                val newlyExtracted = mutableMapOf<String, AppColorBucket>()

                val missingApps = apps.filter { !baseMap.containsKey(it.componentKey) }
                if (missingApps.isNotEmpty()) {
                    val indexingDispatcher = Dispatchers.IO.limitedParallelism(3)
                    val chunks = missingApps.chunked(16)
                    for (chunk in chunks) {
                        if (!isActive) return@launch
                        val chunkResults = coroutineScope {
                            chunk.map { app ->
                                async(indexingDispatcher) {
                                    val drawable = resolveAppIcon(pm, app.componentKey, app.packageName, app.activityName, app.userHandle)
                                    if (drawable != null) {
                                        val bucket = AppColorExtractor.extractColorBucket(drawable)
                                        Pair(app.componentKey, bucket)
                                    } else null
                                }
                            }.awaitAll().filterNotNull()
                        }

                        for ((compKey, bucket) in chunkResults) {
                            baseMap[compKey] = bucket
                            newlyExtracted[compKey] = bucket
                        }
                    }
                }

                if (newlyExtracted.isNotEmpty() || isIconPackChanged || staleRemoved) {
                    synchronized(stateLock) {
                        if (!isActive || indexingJob !== coroutineContext[Job]) return@launch
                        val finalMap = if (!pruneMissing && !isIconPackChanged) {
                            _appColors.value.toMutableMap().apply { putAll(newlyExtracted) }
                        } else {
                            baseMap
                        }
                        lastIndexedIconPackVersion = iconPackVersion
                        _appColors.value = finalMap
                        saveCachedColors(finalMap, iconPackVersion)
                    }
                }
            }
            indexingJob = newJob
            jobToStart = newJob
        }
        jobToStart.start()
    }

    private fun resolveAppIcon(
        pm: PackageManager,
        compKey: String,
        pkg: String,
        activity: String,
        userHandle: android.os.UserHandle
    ): Drawable? {
        val iconPackDrawable: Drawable? = try {
            IconPackManager.getIcon(compKey)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        }
        if (iconPackDrawable != null) return iconPackDrawable

        if (userHandle != android.os.Process.myUserHandle()) {
            try {
                val launcherApps = appContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? android.content.pm.LauncherApps
                val list = launcherApps?.getActivityList(pkg, userHandle)
                val info = list?.firstOrNull { it.componentName.className == activity } ?: list?.firstOrNull()
                if (info != null) {
                    return info.getIcon(appContext.resources.displayMetrics.densityDpi)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {}
        }

        return try {
            val component = android.content.ComponentName(pkg, activity)
            try {
                pm.getActivityIcon(component)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getActivityInfo(component, PackageManager.ComponentInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getActivityInfo(component, 0)
                }
                info.loadIcon(pm)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            try {
                pm.getApplicationIcon(pkg)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                try {
                    pm.defaultActivityIcon
                } catch (_: Throwable) {
                    null
                }
            }
        }
    }

    fun invalidateCache() {
        synchronized(stateLock) {
            indexingJob?.cancel()
            indexingJob = null
            lastIndexedIconPackVersion = -1L
            _appColors.value = emptyMap()
            if (appContext.isUserUnlockedSafe()) {
                prefs.edit().remove("app_icon_colors").remove("cached_colors_icon_pack_version").apply()
            }
        }
    }
}
