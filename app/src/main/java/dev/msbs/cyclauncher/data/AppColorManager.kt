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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Manages persistent caching and background indexing of application icon colors.
 */
class AppColorManager(context: Context) {
    private val appContext = context.getSafeStorageContext()
    private val prefs: SharedPreferences = appContext.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val CURRENT_ALGO_VERSION = 5
    }

    private val _appColors = MutableStateFlow<Map<String, AppColorBucket>>(loadCachedColors())
    val appColors: StateFlow<Map<String, AppColorBucket>> = _appColors

    private var lastIndexedIconPackVersion: Long = prefs.getLong("cached_colors_icon_pack_version", -1L)
    private var indexingJob: Job? = null

    private fun loadCachedColors(): Map<String, AppColorBucket> {
        val savedAlgoVersion = prefs.getInt("cached_colors_algo_version", 0)
        if (savedAlgoVersion < CURRENT_ALGO_VERSION) {
            prefs.edit()
                .remove("app_icon_colors")
                .remove("cached_colors_icon_pack_version")
                .putInt("cached_colors_algo_version", CURRENT_ALGO_VERSION)
                .apply()
            lastIndexedIconPackVersion = -1L
            return emptyMap()
        }

        val jsonStr = prefs.getString("app_icon_colors", null) ?: return emptyMap()
        val result = mutableMapOf<String, AppColorBucket>()
        try {
            val json = JSONObject(jsonStr)
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val bucketName = json.optString(key).takeIf { it.isNotEmpty() }
                val bucket = AppColorBucket.fromNameOrNull(bucketName)
                if (bucket != null) {
                    result[key] = bucket
                }
            }
        } catch (_: Exception) {}
        return result
    }

    private fun saveCachedColors(colors: Map<String, AppColorBucket>, iconPackVersion: Long) {
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
     * Asynchronously indexes colors for the given apps without blocking the UI.
     */
    fun indexApps(scope: CoroutineScope, apps: List<AppInfo>, iconPackVersion: Long) {
        indexingJob?.cancel()
        indexingJob = scope.launch(Dispatchers.Default) {
            val isIconPackChanged = iconPackVersion != lastIndexedIconPackVersion
            val currentMap = if (isIconPackChanged) {
                mutableMapOf<String, AppColorBucket>()
            } else {
                _appColors.value.toMutableMap()
            }

            val pm = appContext.packageManager
            var hasNewItems = false

            val missingApps = apps.filter { !currentMap.containsKey(it.componentKey) }
            if (missingApps.isNotEmpty()) {
                val chunks = missingApps.chunked(16)
                for (chunk in chunks) {
                    val chunkResults = coroutineScope {
                        chunk.map { app ->
                            async(Dispatchers.Default) {
                                val drawable = resolveAppIcon(pm, app.componentKey, app.packageName, app.activityName)
                                if (drawable != null) {
                                    val bucket = AppColorExtractor.extractColorBucket(drawable)
                                    Pair(app.componentKey, bucket)
                                } else null
                            }
                        }.awaitAll().filterNotNull()
                    }

                    for ((compKey, bucket) in chunkResults) {
                        currentMap[compKey] = bucket
                        hasNewItems = true
                    }
                }
            }

            if (hasNewItems || isIconPackChanged) {
                lastIndexedIconPackVersion = iconPackVersion
                _appColors.value = currentMap
                saveCachedColors(currentMap, iconPackVersion)
            }
        }
    }

    private fun resolveAppIcon(pm: PackageManager, compKey: String, pkg: String, activity: String): Drawable? {
        val iconPackDrawable: Drawable? = try {
            IconPackManager.getIcon(compKey)
        } catch (_: Exception) {
            null
        }

        return iconPackDrawable ?: try {
            val component = android.content.ComponentName(pkg, activity)
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getActivityInfo(component, PackageManager.ComponentInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getActivityInfo(component, 0)
            }
            info.loadIcon(pm)
        } catch (_: Exception) {
            try {
                pm.getApplicationIcon(pkg)
            } catch (_: Exception) {
                pm.defaultActivityIcon
            }
        }
    }

    fun invalidateCache() {
        lastIndexedIconPackVersion = -1
        _appColors.value = emptyMap()
        prefs.edit().remove("app_icon_colors").remove("cached_colors_icon_pack_version").apply()
    }
}
