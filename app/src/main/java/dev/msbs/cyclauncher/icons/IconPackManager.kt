package dev.msbs.cyclauncher.icons

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.Settings
import androidx.core.content.res.ResourcesCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Information about an installed icon pack on the device.
 */
data class IconPackInfo(
    val packageName: String,
    val name: String,
    val icon: Drawable?
)

/**
 * High-performance singleton manager for scanning, parsing, and retrieving icons from standard
 * Android icon packs (supporting Nova, Lawnchair, ADW, Apex, Go, etc. formats) as well as
 * auto-detecting custom ROM / system-wide active icon packs.
 */
object IconPackManager {

    private val THEME_INTENT_ACTIONS = listOf(
        "org.adw.launcher.THEMES",
        "com.novalauncher.THEME",
        "com.teslacoilsw.launcher.THEME",
        "com.anddoes.launcher.THEME",
        "app.lawnchair.icons.action.APPLICATION",
        "com.fede.launcher.THEME_ICONPACK",
        "com.gau.go.launcherex.theme",
        "com.dlto.atom.launcher.THEME",
        "com.sonymobile.home.ICON_PACK",
        "ch.deletescape.lawnchair.ICONPACK"
    )

    private val THEME_CATEGORIES = listOf(
        "com.fede.launcher.THEME_ICONPACK",
        "com.anddoes.launcher.THEME",
        "com.teslacoilsw.launcher.THEME"
    )

    @Volatile
    var activePackageName: String? = null
        private set

    private var activeResources: Resources? = null
    private val componentToDrawableMap = ConcurrentHashMap<String, String>()
    private val packageToDrawableMap = ConcurrentHashMap<String, String>()
    private val drawableToResIdMap = ConcurrentHashMap<String, Int>()

    /**
     * Attempts to auto-detect the active icon pack package configured in the system / custom ROM settings.
     */
    fun getSystemIconPackPackage(context: Context): String? {
        val cr = context.contentResolver

        // 1. AOSP ThemePicker / Styles & Wallpapers theme_customization_overlay_packages JSON
        try {
            val jsonStr = Settings.Secure.getString(cr, "theme_customization_overlay_packages")
            if (!jsonStr.isNullOrBlank()) {
                val json = JSONObject(jsonStr)
                val iconPackKeys = listOf(
                    "android.theme.customization.icon_pack.android",
                    "android.theme.customization.icon_pack.launcher",
                    "android.theme.customization.icon_pack.systemui",
                    "android.theme.customization.icon_pack.settings",
                    "android.theme.customization.icon_pack",
                    "android.theme.customization.iconpack.icon"
                )
                for (key in iconPackKeys) {
                    if (json.has(key)) {
                        val pkg = json.optString(key)
                        if (pkg.isNotBlank() && isPackageInstalled(context, pkg)) {
                            return pkg
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. Custom ROM Settings.Secure & Settings.System keys (crDroid, LineageOS, EvolutionX, OxygenOS, NothingOS, etc.)
        val settingKeys = listOf(
            "icon_pack",
            "theme_icon_pack_package",
            "current_icon_pack",
            "custom_icon_pack",
            "iconpack_name",
            "op_custom_icon_pack_package",
            "nothing_icon_pack_package",
            "asus_icon_pack"
        )
        for (key in settingKeys) {
            try {
                val pkg = Settings.Secure.getString(cr, key)
                    ?: Settings.System.getString(cr, key)
                if (!pkg.isNullOrBlank() && isPackageInstalled(context, pkg)) {
                    return pkg
                }
            } catch (_: Exception) {}
        }

        return null
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Scans the system for installed icon packs.
     */
    suspend fun getInstalledIconPacks(context: Context): List<IconPackInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val discoveredPackages = mutableSetOf<String>()
        val result = mutableListOf<IconPackInfo>()

        // 1. Query by actions
        for (action in THEME_INTENT_ACTIONS) {
            val intent = Intent(action)
            val list = queryIntentActivities(pm, intent)
            for (resolveInfo in list) {
                val pkg = resolveInfo.activityInfo?.packageName ?: continue
                if (pkg != context.packageName && discoveredPackages.add(pkg)) {
                    val name = resolveInfo.loadLabel(pm).toString()
                    val icon = resolveInfo.loadIcon(pm)
                    result.add(IconPackInfo(pkg, name, icon))
                }
            }
        }

        // 2. Query by categories
        for (cat in THEME_CATEGORIES) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(cat)
            val list = queryIntentActivities(pm, intent)
            for (resolveInfo in list) {
                val pkg = resolveInfo.activityInfo?.packageName ?: continue
                if (pkg != context.packageName && discoveredPackages.add(pkg)) {
                    val name = resolveInfo.loadLabel(pm).toString()
                    val icon = resolveInfo.loadIcon(pm)
                    result.add(IconPackInfo(pkg, name, icon))
                }
            }
        }

        // 3. Add system custom ROM icon pack if not already discovered
        val systemPkg = getSystemIconPackPackage(context)
        if (systemPkg != null && discoveredPackages.add(systemPkg)) {
            try {
                val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getApplicationInfo(systemPkg, PackageManager.ApplicationInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getApplicationInfo(systemPkg, 0)
                }
                val name = appInfo.loadLabel(pm).toString()
                val icon = appInfo.loadIcon(pm)
                result.add(IconPackInfo(systemPkg, name, icon))
            } catch (_: Exception) {}
        }

        result.sortBy { it.name.lowercase() }
        result
    }

    private fun queryIntentActivities(pm: PackageManager, intent: Intent) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }

    /**
     * Asynchronously loads and parses the icon pack's appfilter.xml mapping.
     * If [packageName] is null or blank, automatically attempts to load the custom ROM's
     * active system icon pack.
     */
    suspend fun loadIconPack(context: Context, packageName: String?) = withContext(Dispatchers.IO) {
        val targetPackage = if (!packageName.isNullOrBlank()) {
            packageName
        } else {
            getSystemIconPackPackage(context)
        }

        if (targetPackage.isNullOrBlank()) {
            activePackageName = null
            activeResources = null
            componentToDrawableMap.clear()
            packageToDrawableMap.clear()
            drawableToResIdMap.clear()
            return@withContext
        }

        if (targetPackage == activePackageName && activeResources != null && componentToDrawableMap.isNotEmpty()) {
            return@withContext
        }

        try {
            val pm = context.packageManager
            val res = pm.getResourcesForApplication(targetPackage)
            val compMap = HashMap<String, String>(2048)
            val pkgMap = HashMap<String, String>(1024)

            // Try opening appfilter.xml from assets or res/xml
            var parsed = parseXmlFromAssets(res, compMap, pkgMap, "appfilter.xml")
            if (!parsed) {
                parsed = parseXmlFromRes(res, targetPackage, compMap, pkgMap, "appfilter")
            }
            if (!parsed) {
                parsed = parseXmlFromRes(res, targetPackage, compMap, pkgMap, "theme_resources")
            }
            if (!parsed) {
                parsed = parseXmlFromRes(res, targetPackage, compMap, pkgMap, "iconpack")
            }
            if (!parsed) {
                parseXmlFromAssets(res, compMap, pkgMap, "iconpack.xml")
            }

            componentToDrawableMap.clear()
            componentToDrawableMap.putAll(compMap)
            packageToDrawableMap.clear()
            packageToDrawableMap.putAll(pkgMap)
            drawableToResIdMap.clear()
            activeResources = res
            activePackageName = targetPackage
        } catch (_: Exception) {
            activePackageName = null
            activeResources = null
            componentToDrawableMap.clear()
            packageToDrawableMap.clear()
            drawableToResIdMap.clear()
        }
    }

    private fun parseXmlFromAssets(
        res: Resources,
        compMap: MutableMap<String, String>,
        pkgMap: MutableMap<String, String>,
        fileName: String
    ): Boolean {
        var inputStream: InputStream? = null
        return try {
            inputStream = res.assets.open(fileName)
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(inputStream, "UTF-8")
            parseAppFilter(parser, compMap, pkgMap)
            true
        } catch (_: Exception) {
            false
        } finally {
            try { inputStream?.close() } catch (_: Exception) {}
        }
    }

    private fun parseXmlFromRes(
        res: Resources,
        packageName: String,
        compMap: MutableMap<String, String>,
        pkgMap: MutableMap<String, String>,
        resName: String
    ): Boolean {
        val resId = res.getIdentifier(resName, "xml", packageName)
        if (resId == 0) return false
        var parser: XmlResourceParser? = null
        return try {
            parser = res.getXml(resId)
            parseAppFilter(parser, compMap, pkgMap)
            true
        } catch (_: Exception) {
            false
        } finally {
            parser?.close()
        }
    }

    private fun parseAppFilter(
        parser: XmlPullParser,
        compMap: MutableMap<String, String>,
        pkgMap: MutableMap<String, String>
    ) {
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "item") {
                val componentAttr = parser.getAttributeValue(null, "component")
                val drawableAttr = parser.getAttributeValue(null, "drawable")
                if (!componentAttr.isNullOrBlank() && !drawableAttr.isNullOrBlank()) {
                    val key = normalizeComponentKey(componentAttr)
                    if (key.isNotEmpty()) {
                        compMap[key] = drawableAttr
                        val pkg = key.substringBefore('/')
                        if (!pkgMap.containsKey(pkg)) {
                            pkgMap[pkg] = drawableAttr
                        }
                    }
                }
            }
            eventType = parser.next()
        }
    }

    private fun normalizeComponentKey(raw: String): String {
        val trimmed = raw.removePrefix("ComponentInfo{").removeSuffix("}").trim()
        val parts = trimmed.split('/', limit = 2)
        if (parts.isEmpty()) return ""
        val pkg = parts[0].trim()
        if (parts.size == 1 || parts[1].isBlank()) return pkg
        val act = parts[1].trim()
        val fullAct = if (act.startsWith(".")) "$pkg$act" else act
        return "$pkg/$fullAct"
    }

    /**
     * Retrieves a themed Drawable from the currently active icon pack for the given componentKey.
     * Returns null if no icon pack is active or no mapping exists.
     */
    fun getIcon(componentKey: String): Drawable? {
        val res = activeResources ?: return null
        val pkgName = activePackageName ?: return null

        val drawableName = componentToDrawableMap[componentKey]
            ?: packageToDrawableMap[componentKey.substringBefore('/')]
            ?: return null

        val resId = drawableToResIdMap.getOrPut(drawableName) {
            res.getIdentifier(drawableName, "drawable", pkgName)
        }
        if (resId == 0) return null

        return try {
            ResourcesCompat.getDrawable(res, resId, null)
        } catch (_: Exception) {
            null
        }
    }
}
