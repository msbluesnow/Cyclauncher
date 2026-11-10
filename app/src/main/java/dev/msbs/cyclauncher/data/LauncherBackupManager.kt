package dev.msbs.cyclauncher.data

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.msbs.cyclauncher.model.AppInfo
import dev.msbs.cyclauncher.model.HighlightSection
import dev.msbs.cyclauncher.model.Tag
import dev.msbs.cyclauncher.utils.LocaleUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.abs

/**
 * Handles SAF file I/O, JSON/text serialization, character mapping import/export,
 * app names import/export, AI auto-tag parsing, and full launcher backup import/export.
 */
class LauncherBackupManager(
    private val context: Context,
    private val prefs: SharedPreferences
) {

    companion object {
        val INTERNAL_PREF_KEYS = setOf(
            "tags", "app_tags", "custom_labels", "favorites", "tag_app_orders",
            "custom_char_mappings", "history", "search_history", "app_icon_colors",
            "cached_colors_algo_version", "cached_colors_icon_pack_version",
            "app_last_update_times", "recently_updated_apps", "prefs_migrated_to_de",
            "is_default_launcher"
        )
        const val MAX_LABEL_LENGTH = 256
        const val MAX_TAG_NAME_LENGTH = 128
        const val MAX_SYMBOL_LENGTH = 32
        private val PACKAGE_NAME_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+$")

        fun isValidPackageName(value: String): Boolean {
            return value.length in 3..256 && PACKAGE_NAME_REGEX.matches(value)
        }
    }

    /**
     * Holds the result of importing application metadata from JSON or plain text formats.
     */
    data class AppNamesImportResult(
        val labels: Map<String, String>,
        val favorites: List<String>
    )

    /**
     * Writes a UTF-8 string to a content [uri] reliably using Android SAF.
     * Uses "wt" (write-truncate) mode with explicit buffer flushing and sync to avoid 0-byte file issues on Android 10-17+.
     */
    fun writeStringToUri(uri: Uri, content: String) {
        val outputStream = try {
            context.contentResolver.openOutputStream(uri, "wt")
        } catch (_: Exception) {
            null
        } ?: context.contentResolver.openOutputStream(uri)
          ?: throw IOException("Cannot open output stream for URI: $uri")

        outputStream.use { os ->
            os.writer(Charsets.UTF_8).use { writer ->
                writer.write(content)
                writer.flush()
            }
            os.flush()
            try {
                (os as? FileOutputStream)?.fd?.sync()
            } catch (_: Exception) {
                // Ignore sync error if unsupported by document provider
            }
        }
    }

    /**
     * Reads a UTF-8 string from a content [uri], handling UTF-8 BOM if present.
     * Enforces a maximum size limit ([maxBytes], default 10MB) to prevent OutOfMemoryError
     * if an unexpected or malicious large file (e.g. video, binary archive) is selected.
     */
    fun readStringFromUri(uri: Uri, maxBytes: Long = 10 * 1024 * 1024L): String {
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                        val size = cursor.getLong(sizeIndex)
                        if (size > maxBytes) {
                            throw IOException("File size ($size bytes) exceeds limit of $maxBytes bytes")
                        }
                    }
                }
            }
        } catch (e: IOException) {
            throw e
        } catch (_: Exception) {
            // Some providers don't report size; bounded stream read below guarantees enforcement
        }

        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Cannot open input stream for URI: $uri")

        val buffer = ByteArray(8192)
        val baos = ByteArrayOutputStream()
        var totalBytes = 0L

        inputStream.use { stream ->
            while (true) {
                val read = stream.read(buffer)
                if (read == -1) break
                totalBytes += read
                if (totalBytes > maxBytes) {
                    throw IOException("File exceeds maximum allowed size of $maxBytes bytes")
                }
                baos.write(buffer, 0, read)
            }
        }

        val content = baos.toString("UTF-8")
        return if (content.startsWith("\uFEFF")) content.substring(1) else content
    }

    /**
     * Exports all custom character mappings to a JSON file at the specified [uri].
     */
    fun exportCharMappingsToUri(uri: Uri, mappings: Map<String, Char>) {
        val mappingsObj = JSONObject()
        mappings.forEach { (k, v) ->
            mappingsObj.put(k, v.toString())
        }
        val root = JSONObject()
        root.put("version", 1)
        root.put("mappings", mappingsObj)

        writeStringToUri(uri, root.toString(2))
    }

    /**
     * Parses custom character mappings from a JSON or TXT file at the specified [uri].
     * Supports multiple formats: JSON dictionary, structured wrapper, array, and key-value text.
     */
    fun parseCharMappingsFromUri(uri: Uri): Map<String, Char> {
        val content = readStringFromUri(uri)
        val trimmed = content.trim()
        val parsed = mutableMapOf<String, Char>()

        if (trimmed.startsWith("{")) {
            val json = JSONObject(trimmed)
            val mappingsJson = if (json.has("mappings")) json.optJSONObject("mappings") ?: JSONObject() else json
            val keys = mappingsJson.keys()
            while (keys.hasNext()) {
                val rawKey = keys.next()
                val key = rawKey.trim().take(MAX_SYMBOL_LENGTH)
                if (key.isEmpty() || key == "version") continue
                val value = mappingsJson.optString(rawKey).trim()
                if (value.isNotEmpty()) {
                    parsed[key] = value[0].uppercaseChar()
                }
            }
        } else if (trimmed.startsWith("[")) {
            val array = JSONArray(trimmed)
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val symbol = obj.optString("symbol").ifEmpty { obj.optString("char") }.ifEmpty { obj.optString("from") }.trim().take(MAX_SYMBOL_LENGTH)
                val target = obj.optString("target").ifEmpty { obj.optString("letter") }.ifEmpty { obj.optString("to") }.trim()
                if (symbol.isNotEmpty() && target.isNotEmpty()) {
                    parsed[symbol] = target[0].uppercaseChar()
                }
            }
        } else {
            trimmed.lines().forEach { line ->
                val lineTrimmed = line.trim()
                if (lineTrimmed.isNotEmpty() && !lineTrimmed.startsWith("#") && !lineTrimmed.startsWith("//")) {
                    val parts = when {
                        lineTrimmed.contains("->") -> lineTrimmed.split("->", limit = 2)
                        lineTrimmed.contains("➔") -> lineTrimmed.split("➔", limit = 2)
                        lineTrimmed.contains("=") -> lineTrimmed.split("=", limit = 2)
                        lineTrimmed.contains(":") -> lineTrimmed.split(":", limit = 2)
                        lineTrimmed.contains("\t") -> lineTrimmed.split("\t", limit = 2)
                        else -> null
                    }
                    if (parts != null && parts.size == 2) {
                        val sym = parts[0].trim().take(MAX_SYMBOL_LENGTH)
                        val tgt = parts[1].trim()
                        if (sym.isNotEmpty() && tgt.isNotEmpty()) {
                            parsed[sym] = tgt[0].uppercaseChar()
                        }
                    }
                }
            }
        }

        if (parsed.isEmpty()) {
            throw IllegalArgumentException("No valid character mappings found in file")
        }
        return parsed
    }

    /**
     * Exports the list of installed apps to a JSON file at the specified URI,
     * including custom labels, favorite status, and assigned tags.
     */
    fun exportAppNamesToUri(
        uri: Uri,
        apps: List<AppInfo>,
        favorites: Set<String>,
        tags: List<Tag>,
        appTags: Map<String, List<String>>
    ) {
        val idToName = tags.associate { it.id to it.name }
        val array = JSONArray()
        apps.forEach { app ->
            val obj = JSONObject()
            obj.put("package", app.packageName)
            obj.put("component", app.componentKey)
            obj.put("label", app.label)
            obj.put("is_favorite", favorites.contains(app.componentKey) || favorites.contains(app.packageName))
            val tagIds = appTags[app.componentKey] ?: appTags[app.packageName] ?: emptyList()
            val tagNames = tagIds.mapNotNull { idToName[it] }
            if (tagNames.isNotEmpty()) {
                obj.put("tags", JSONArray(tagNames))
                obj.put("tag", tagNames.first())
            }
            array.put(obj)
        }
        writeStringToUri(uri, array.toString(2))
    }

    fun exportAppNamesToUriAsText(uri: Uri, apps: List<AppInfo>) {
        val text = buildString {
            apps.forEach { app ->
                append(app.label)
                append(" — ")
                append(app.packageName)
                append('\n')
            }
        }
        writeStringToUri(uri, text)
    }

    /**
     * Imports custom app labels and favorite flags from a JSON or text file.
     */
    fun importAppNamesFromUri(uri: Uri, currentApps: List<AppInfo>): AppNamesImportResult {
        val raw = readStringFromUri(uri)
        val trimmed = cleanJsonString(raw)
        val packageToApps = currentApps.groupBy { it.packageName }
        val importedLabels = mutableMapOf<String, String>()
        val importedFavorites = mutableListOf<String>()

        if (trimmed.startsWith("[")) {
            val array = JSONArray(trimmed)
            for (i in 0 until array.length()) {
                val item = array.opt(i)
                if (item is JSONObject) {
                    parseAppLabelAndFavoriteItem(item, packageToApps, importedLabels, importedFavorites)
                }
            }
        } else if (trimmed.startsWith("{")) {
            val root = JSONObject(trimmed)
            val nestedArray = root.optJSONArray("apps") ?: root.optJSONArray("labels") ?: root.optJSONArray("custom_labels")
            val nestedObj = root.optJSONObject("custom_labels") ?: root.optJSONObject("labels") ?: root.optJSONObject("apps")
            val favoritesArray = root.optJSONArray("favorites")

            if (favoritesArray != null) {
                for (i in 0 until favoritesArray.length()) {
                    val favKey = favoritesArray.optString(i).trim()
                    if (favKey.isNotEmpty()) {
                        importedFavorites.add(favKey)
                    }
                }
            }

            if (nestedArray != null) {
                for (i in 0 until nestedArray.length()) {
                    val item = nestedArray.optJSONObject(i) ?: continue
                    parseAppLabelAndFavoriteItem(item, packageToApps, importedLabels, importedFavorites)
                }
            } else {
                val targetObj = nestedObj ?: root
                targetObj.keys().forEach { key ->
                    val label = targetObj.optString(key).trim().take(MAX_LABEL_LENGTH)
                    if (label.isNotEmpty()) {
                        if (key.contains("/")) {
                            importedLabels[key] = label
                        } else {
                            val matchingApps = packageToApps[key]
                            if (!matchingApps.isNullOrEmpty()) {
                                matchingApps.forEach { app -> importedLabels[app.componentKey] = label }
                            } else {
                                importedLabels[key] = label
                            }
                        }
                    }
                }
            }
        } else {
            trimmed.lines().forEach { line ->
                val l = line.trim()
                if (l.contains("—")) {
                    val parts = l.split("—", limit = 2).map { it.trim() }
                    if (parts.size == 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()) {
                        val label = parts[0].take(MAX_LABEL_LENGTH)
                        val pkg = parts[1]
                        val matchingApps = packageToApps[pkg]
                        if (!matchingApps.isNullOrEmpty()) {
                            matchingApps.forEach { app -> importedLabels[app.componentKey] = label }
                        } else {
                            importedLabels[pkg] = label
                        }
                    }
                } else if (l.contains(":") || l.contains("=")) {
                    val delimiter = if (l.contains(":")) ":" else "="
                    val parts = l.split(delimiter, limit = 2).map { it.trim() }
                    if (parts.size == 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()) {
                        val pkg = parts[0]
                        val label = parts[1].take(MAX_LABEL_LENGTH)
                        val matchingApps = packageToApps[pkg]
                        if (!matchingApps.isNullOrEmpty()) {
                            matchingApps.forEach { app -> importedLabels[app.componentKey] = label }
                        } else {
                            importedLabels[pkg] = label
                        }
                    }
                }
            }
        }
        return AppNamesImportResult(
            labels = importedLabels,
            favorites = importedFavorites.distinct()
        )
    }

    private fun parseAppLabelAndFavoriteItem(
        item: JSONObject,
        packageToApps: Map<String, List<AppInfo>>,
        importedLabels: MutableMap<String, String>,
        importedFavorites: MutableList<String>
    ) {
        val pkg = item.optString("package").ifEmpty { item.optString("packageName").ifEmpty { item.optString("app") } }.trim()
        val component = item.optString("component").ifEmpty { item.optString("componentKey") }.trim()
        val label = item.optString("label").ifEmpty { item.optString("name").ifEmpty { item.optString("customLabel") } }.trim().take(MAX_LABEL_LENGTH)
        val isFav = extractIsFavorite(item)

        if (label.isNotEmpty()) {
            if (component.isNotEmpty()) {
                importedLabels[component] = label
            } else if (pkg.isNotEmpty()) {
                val matchingApps = packageToApps[pkg]
                if (!matchingApps.isNullOrEmpty()) {
                    matchingApps.forEach { app -> importedLabels[app.componentKey] = label }
                } else {
                    importedLabels[pkg] = label
                }
            }
        }

        if (isFav) {
            if (component.isNotEmpty()) {
                importedFavorites.add(component)
            } else if (pkg.isNotEmpty()) {
                val matchingApps = packageToApps[pkg]
                if (!matchingApps.isNullOrEmpty()) {
                    matchingApps.forEach { app -> importedFavorites.add(app.componentKey) }
                } else {
                    importedFavorites.add(pkg)
                }
            }
        }
    }

    /**
     * Exports a full launcher backup (tags, assignments, custom labels, favorites, settings, and app metadata) to JSON at [uri].
     */
    fun exportTagsBackupToUri(
        uri: Uri,
        tags: List<Tag>,
        appTags: Map<String, List<String>>,
        customLabels: Map<String, String>,
        favorites: List<String>,
        tagAppOrders: Map<String, List<String>>,
        customCharMappings: Map<String, Char>,
        apps: List<AppInfo> = emptyList()
    ) {
        val idToName = tags.associate { it.id to it.name }
        val tagsArray = JSONArray()
        tags.forEach { tag ->
            val obj = JSONObject()
            obj.put("id", tag.id)
            obj.put("name", tag.name)
            obj.put("color", colorToHex(tag.color))
            if (!tag.emoji.isNullOrBlank()) {
                obj.put("emoji", tag.emoji)
            }
            tagsArray.put(obj)
        }

        val assignments = JSONObject()
        appTags.forEach { (componentKey, tagIds) ->
            val names = tagIds.mapNotNull { idToName[it] }
            if (names.isNotEmpty()) {
                assignments.put(componentKey, JSONArray(names))
            }
        }

        val root = JSONObject()
        root.put("version", 1)
        root.put("tags", tagsArray)
        root.put("assignments", assignments)

        val customLabelsObj = JSONObject()
        customLabels.forEach { (k, v) ->
            customLabelsObj.put(k, v)
        }
        if (customLabels.isNotEmpty()) {
            root.put("custom_labels", customLabelsObj)
        }

        val favoritesArray = JSONArray()
        favorites.forEach { favKey ->
            favoritesArray.put(favKey)
        }
        if (favorites.isNotEmpty()) {
            root.put("favorites", favoritesArray)
        }

        val tagOrdersObj = JSONObject()
        tagAppOrders.forEach { (tagId, orderList) ->
            val tagName = idToName[tagId] ?: tagId
            tagOrdersObj.put(tagName, JSONArray(orderList))
        }
        if (tagAppOrders.isNotEmpty()) {
            root.put("tag_app_orders", tagOrdersObj)
        }

        val customCharMappingsObj = JSONObject()
        customCharMappings.forEach { (k, v) ->
            customCharMappingsObj.put(k, v.toString())
        }
        if (customCharMappings.isNotEmpty()) {
            root.put("custom_char_mappings", customCharMappingsObj)
        }

        val settingsObj = JSONObject()
        prefs.all.forEach { (key, value) ->
            if (key !in INTERNAL_PREF_KEYS && value != null) {
                settingsObj.put(key, value.toString())
            }
        }

        if (!settingsObj.has("highlight_section_order")) {
            settingsObj.put(
                "highlight_section_order",
                HighlightSection.values().joinToString(",") { it.name }
            )
        }
        if (!settingsObj.has("side_alphabet_slot_mode")) {
            val mode = if (prefs.contains("show_search_history") && !prefs.getBoolean("show_search_history", true)) "DISABLED" else "HISTORY"
            settingsObj.put("side_alphabet_slot_mode", mode)
        }
        if (!settingsObj.has("search_method")) settingsObj.put("search_method", "SIDE_ALPHABET")
        if (!settingsObj.has("hand_side")) settingsObj.put("hand_side", "LEFT")
        if (!settingsObj.has("accent_color")) settingsObj.put("accent_color", "SKY")
        if (!settingsObj.has("primary_text_color")) settingsObj.put("primary_text_color", "WHITE")
        if (!settingsObj.has("button_text_color")) settingsObj.put("button_text_color", "BLACK")
        if (!settingsObj.has("popup_theme")) settingsObj.put("popup_theme", "DARK")
        if (!settingsObj.has("show_shadows")) settingsObj.put("show_shadows", "true")
        if (!settingsObj.has("shadow_color")) settingsObj.put("shadow_color", "BLACK")
        if (!settingsObj.has("hide_status_bar")) settingsObj.put("hide_status_bar", "false")
        if (!settingsObj.has("show_search_widgets")) settingsObj.put("show_search_widgets", "true")
        if (!settingsObj.has("show_search_history")) settingsObj.put("show_search_history", "true")
        if (!settingsObj.has("animations_enabled")) settingsObj.put("animations_enabled", "true")
        if (!settingsObj.has("marquee_enabled")) settingsObj.put("marquee_enabled", "true")
        if (!settingsObj.has("haptic_feedback_enabled")) settingsObj.put("haptic_feedback_enabled", "true")
        if (!settingsObj.has("monochrome_history")) settingsObj.put("monochrome_history", "false")
        if (!settingsObj.has("monochrome_tags")) settingsObj.put("monochrome_tags", "false")
        if (!settingsObj.has("show_tags_area")) settingsObj.put("show_tags_area", "true")
        if (!settingsObj.has("show_history_area")) settingsObj.put("show_history_area", "true")
        if (!settingsObj.has("show_keep_android_open_days")) settingsObj.put("show_keep_android_open_days", "true")
        if (!settingsObj.has("screen_scrim_alpha")) settingsObj.put("screen_scrim_alpha", "0.0")
        if (!settingsObj.has("capsule_alpha")) settingsObj.put("capsule_alpha", "0.0")
        if (!settingsObj.has("popup_alpha")) settingsObj.put("popup_alpha", "0.81")
        if (!settingsObj.has("side_alphabet_button_y_ratio")) settingsObj.put("side_alphabet_button_y_ratio", "0.23")
        if (!settingsObj.has("side_search_widget_width_fraction")) settingsObj.put("side_search_widget_width_fraction", "1.0")
        if (!settingsObj.has("side_search_widget_offset_x")) settingsObj.put("side_search_widget_offset_x", "0.5")
        if (!settingsObj.has("side_search_widget_offset_y")) settingsObj.put("side_search_widget_offset_y", "0.5")
        if (!settingsObj.has("last_alphabet_search_method")) settingsObj.put("last_alphabet_search_method", "SIDE_ALPHABET")

        val appLang = LocaleUtils.getCurrentLanguageCode(context)
        if (appLang.isNotBlank()) {
            settingsObj.put("pref_app_language", appLang)
        }

        if (settingsObj.length() > 0) {
            root.put("settings", settingsObj)
        }

        if (apps.isNotEmpty()) {
            val favs = favorites.toSet()
            val appsArray = JSONArray()
            apps.forEach { app ->
                val appObj = JSONObject()
                appObj.put("package", app.packageName)
                appObj.put("component", app.componentKey)
                appObj.put("label", app.label)
                appObj.put("is_favorite", favs.contains(app.componentKey) || favs.contains(app.packageName))
                val tagIds = appTags[app.componentKey] ?: appTags[app.packageName] ?: emptyList()
                val tagNames = tagIds.mapNotNull { idToName[it] }
                if (tagNames.isNotEmpty()) {
                    appObj.put("tags", JSONArray(tagNames))
                    appObj.put("tag", tagNames.first())
                }
                appsArray.put(appObj)
            }
            root.put("apps", appsArray)
        }

        writeStringToUri(uri, root.toString(2))
    }

    /**
     * Parses a backup or AI-tagged JSON file into a preview without applying changes yet.
     */
    fun parseTagsBackup(uri: Uri, existingTags: List<Tag>): TagsBackupPreview {
        val raw = readStringFromUri(uri)
        val trimmed = cleanJsonString(raw)

        val existingNames = existingTags.map { it.name.lowercase().trim() }.toMutableSet()
        val tagsToCreate = mutableListOf<TagsBackupPreview.TagInfo>()
        val createdNamesSet = mutableSetOf<String>()
        val assignments = mutableListOf<TagsBackupPreview.AssignmentInfo>()
        val parsedCustomLabels = mutableMapOf<String, String>()
        val parsedFavorites = mutableListOf<String>()
        val parsedTagAppOrders = mutableMapOf<String, List<String>>()
        val parsedSettings = mutableMapOf<String, String>()
        val parsedCharMappings = mutableMapOf<String, Char>()

        if (trimmed.startsWith("[")) {
            val array = JSONArray(trimmed)
            val packageToTagNames = mutableMapOf<String, MutableList<String>>()
            val tagColors = mutableMapOf<String, Color>()
            val tagIcons = mutableMapOf<String, String>()

            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val pkg = obj.optString("package").ifEmpty { obj.optString("packageName") }.trim()
                val component = obj.optString("component").ifEmpty { obj.optString("componentKey") }.trim()
                val label = obj.optString("label").ifEmpty { obj.optString("name") }.trim().take(MAX_LABEL_LENGTH)
                val colorHex = obj.optString("color").trim()
                val color = if (colorHex.isNotEmpty()) parseHexColor(colorHex) else Color.Unspecified
                val isFav = extractIsFavorite(obj)
                val iconVal = (obj.optString("icon").ifEmpty { obj.optString("emoji") }).trim().take(MAX_SYMBOL_LENGTH).takeIf { it.isNotEmpty() }

                val tagNames = mutableListOf<String>()
                val singleTag = obj.optString("tag").ifEmpty { obj.optString("tagName") }.trim().take(MAX_TAG_NAME_LENGTH)
                if (singleTag.isNotEmpty()) tagNames.add(singleTag)
                obj.optJSONArray("tags")?.let { tagsArr ->
                    for (j in 0 until tagsArr.length()) {
                        tagsArr.optString(j).trim().take(MAX_TAG_NAME_LENGTH).takeIf { it.isNotEmpty() }?.let { tagNames.add(it) }
                    }
                }

                tagNames.forEach { tagName ->
                    val lower = tagName.lowercase().trim()
                    if (color != Color.Unspecified && !tagColors.containsKey(lower)) {
                        tagColors[lower] = color
                    }
                    if (iconVal != null && !tagIcons.containsKey(lower)) {
                        tagIcons[lower] = iconVal
                    }
                    if (lower !in existingNames && lower !in createdNamesSet) {
                        val resolvedColor = tagColors[lower] ?: generateTagColor(tagName)
                        val resolvedIcon = tagIcons[lower]
                        tagsToCreate.add(TagsBackupPreview.TagInfo(name = tagName, color = resolvedColor, emoji = resolvedIcon))
                        createdNamesSet.add(lower)
                    }
                    val targetKey = component.ifEmpty { pkg }
                    if (targetKey.isNotEmpty()) {
                        packageToTagNames.getOrPut(targetKey) { mutableListOf() }.apply {
                            if (!contains(tagName)) add(tagName)
                        }
                    }
                }

                if (label.isNotEmpty()) {
                    val key = component.ifEmpty { pkg }
                    if (key.isNotEmpty()) {
                        parsedCustomLabels[key] = label
                    }
                }

                if (isFav) {
                    val key = component.ifEmpty { pkg }
                    if (key.isNotEmpty()) {
                        parsedFavorites.add(key)
                    }
                }
            }

            packageToTagNames.forEach { (pkg, names) ->
                assignments.add(TagsBackupPreview.AssignmentInfo(pkg, names))
            }
        } else if (trimmed.startsWith("{")) {
            val root = JSONObject(trimmed)
            val tagsArray = root.optJSONArray("tags")
            val assignmentsObj = root.optJSONObject("assignments")
            val customLabelsObj = root.optJSONObject("custom_labels") ?: root.optJSONObject("labels")
            val favoritesArray = root.optJSONArray("favorites")
            val appsArray = root.optJSONArray("apps")
            val tagOrdersObj = root.optJSONObject("tag_app_orders")
            val settingsObj = root.optJSONObject("settings")
            val charMappingsObj = root.optJSONObject("custom_char_mappings")
                ?: root.optJSONObject("mappings")
                ?: settingsObj?.optJSONObject("custom_char_mappings")

            if (charMappingsObj != null) {
                val keys = charMappingsObj.keys()
                while (keys.hasNext()) {
                    val rawKey = keys.next()
                    val k = rawKey.trim().take(MAX_SYMBOL_LENGTH)
                    val v = charMappingsObj.optString(rawKey).trim()
                    if (k.isNotEmpty() && v.isNotEmpty()) {
                        parsedCharMappings[k] = v[0].uppercaseChar()
                    }
                }
            } else if (settingsObj != null && settingsObj.has("custom_char_mappings")) {
                val rawMappings = settingsObj.optString("custom_char_mappings", "").trim()
                if (rawMappings.startsWith("{")) {
                    try {
                        val json = JSONObject(rawMappings)
                        val keys = json.keys()
                        while (keys.hasNext()) {
                            val rawKey = keys.next()
                            val k = rawKey.trim().take(MAX_SYMBOL_LENGTH)
                            val v = json.optString(rawKey).trim()
                            if (k.isNotEmpty() && v.isNotEmpty()) {
                                parsedCharMappings[k] = v[0].uppercaseChar()
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            if (customLabelsObj != null) {
                val keys = customLabelsObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val label = customLabelsObj.optString(key).trim().take(MAX_LABEL_LENGTH)
                    if (label.isNotEmpty()) {
                        parsedCustomLabels[key] = label
                    }
                }
            }

            if (favoritesArray != null) {
                for (i in 0 until favoritesArray.length()) {
                    val favKey = favoritesArray.optString(i).trim()
                    if (favKey.isNotEmpty()) {
                        parsedFavorites.add(favKey)
                    }
                }
            }

            if (tagOrdersObj != null) {
                val keys = tagOrdersObj.keys()
                while (keys.hasNext()) {
                    val rawTagName = keys.next()
                    val tagName = rawTagName.trim().take(MAX_TAG_NAME_LENGTH)
                    val arr = tagOrdersObj.optJSONArray(rawTagName)
                    if (tagName.isNotEmpty() && arr != null) {
                        val orderList = mutableListOf<String>()
                        for (idx in 0 until arr.length()) {
                            val k = arr.optString(idx).trim()
                            if (k.isNotEmpty()) orderList.add(k)
                        }
                        if (orderList.isNotEmpty()) {
                            parsedTagAppOrders[tagName] = orderList
                        }
                    }
                }
            }

            if (settingsObj != null) {
                val keys = settingsObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next().trim()
                    if (k.isEmpty() || k in INTERNAL_PREF_KEYS) continue
                    val v = settingsObj.optString(k).trim()
                    if (v.isEmpty()) continue
                    if (k == "icon_pack_package" && !isValidPackageName(v)) continue
                    parsedSettings[k] = v
                }
            }

            if (appsArray != null) {
                for (i in 0 until appsArray.length()) {
                    val item = appsArray.optJSONObject(i) ?: continue
                    val pkg = item.optString("package").ifEmpty { item.optString("packageName") }.trim()
                    val component = item.optString("component").ifEmpty { item.optString("componentKey") }.trim()
                    val label = item.optString("label").ifEmpty { item.optString("name") }.trim().take(MAX_LABEL_LENGTH)
                    val isFav = extractIsFavorite(item)
                    val key = component.ifEmpty { pkg }
                    if (label.isNotEmpty() && key.isNotEmpty()) {
                        parsedCustomLabels[key] = label
                    }
                    if (isFav && key.isNotEmpty()) {
                        parsedFavorites.add(key)
                    }
                }
            }

            if (tagsArray != null || assignmentsObj != null) {
                tagsArray?.let { array ->
                    for (i in 0 until array.length()) {
                        val obj = array.optJSONObject(i) ?: continue
                        val oldId = obj.optString("id").trim().takeIf { it.isNotEmpty() }
                        val name = obj.optString("name").trim().take(MAX_TAG_NAME_LENGTH)
                        val colorHex = obj.optString("color").trim()
                        val emoji = (if (obj.has("emoji")) obj.optString("emoji", "") else if (obj.has("icon")) obj.optString("icon", "") else "").trim().take(MAX_SYMBOL_LENGTH).takeIf { it.isNotBlank() }
                        if (name.isEmpty()) continue
                        val lower = name.lowercase().trim()
                        if (lower !in existingNames && lower !in createdNamesSet) {
                            val resolvedColor = if (colorHex.isNotEmpty()) parseHexColor(colorHex) else generateTagColor(name)
                            tagsToCreate.add(TagsBackupPreview.TagInfo(name = name, color = resolvedColor, emoji = emoji, oldId = oldId))
                            createdNamesSet.add(lower)
                        }
                    }
                }

                assignmentsObj?.let { obj ->
                    obj.keys().forEach { componentKey ->
                        val arr = obj.optJSONArray(componentKey)
                        val names = mutableListOf<String>()
                        if (arr != null) {
                            for (idx in 0 until arr.length()) {
                                arr.optString(idx).trim().take(MAX_TAG_NAME_LENGTH).takeIf { it.isNotBlank() }?.let { names.add(it) }
                            }
                        } else {
                            obj.optString(componentKey).trim().take(MAX_TAG_NAME_LENGTH).takeIf { it.isNotBlank() }?.let { names.add(it) }
                        }
                        if (names.isNotEmpty()) {
                            assignments.add(TagsBackupPreview.AssignmentInfo(componentKey, names))
                        }
                    }
                }
            } else {
                val packageToTagNames = mutableMapOf<String, MutableList<String>>()
                root.keys().forEach { rawTagName ->
                    if (rawTagName in setOf("custom_labels", "labels", "favorites", "apps", "version", "tag_app_orders", "settings")) return@forEach
                    val tagName = rawTagName.trim().take(MAX_TAG_NAME_LENGTH)
                    if (tagName.isEmpty()) return@forEach
                    val lower = tagName.lowercase().trim()
                    if (lower !in existingNames && lower !in createdNamesSet) {
                        tagsToCreate.add(TagsBackupPreview.TagInfo(name = tagName, color = generateTagColor(tagName)))
                        createdNamesSet.add(lower)
                    }
                    val arr = root.optJSONArray(rawTagName)
                    if (arr != null) {
                        for (idx in 0 until arr.length()) {
                            val pkg = arr.optString(idx).trim()
                            if (pkg.isNotEmpty()) {
                                packageToTagNames.getOrPut(pkg) { mutableListOf() }.apply {
                                    if (!contains(tagName)) add(tagName)
                                }
                            }
                        }
                    }
                }
                packageToTagNames.forEach { (pkg, names) ->
                    assignments.add(TagsBackupPreview.AssignmentInfo(pkg, names))
                }
            }
        }

        return TagsBackupPreview(
            newTags = tagsToCreate,
            assignmentCount = assignments.sumOf { it.tagNames.size },
            parsedAssignments = assignments,
            existingTagCount = existingTags.size,
            customLabels = parsedCustomLabels,
            favorites = parsedFavorites.distinct(),
            tagAppOrders = parsedTagAppOrders,
            settings = parsedSettings,
            customCharMappings = parsedCharMappings
        )
    }

    /**
     * Parses an AI-generated tagged application JSON file to create a preview.
     */
    fun parseAutoTags(uri: Uri, apps: List<AppInfo>): AutoTagsPreview {
        val jsonString = readStringFromUri(uri)
        val trimmed = cleanJsonString(jsonString)

        val uniqueTags = mutableMapOf<String, Color>()
        val packageToTag = mutableMapOf<String, String>()

        if (trimmed.startsWith("[")) {
            val array = JSONArray(trimmed)
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val pkg = obj.optString("package").ifEmpty { obj.optString("packageName") }.trim()
                val tagName = obj.optString("tag").ifEmpty { obj.optString("name") }.trim().take(MAX_TAG_NAME_LENGTH)
                val colorHex = obj.optString("color").trim()
                val color = if (colorHex.isNotEmpty()) parseHexColor(colorHex) else generateTagColor(tagName)

                if (tagName.isNotEmpty() && pkg.isNotEmpty()) {
                    uniqueTags[tagName] = color
                    packageToTag[pkg] = tagName
                }
            }
        } else if (trimmed.startsWith("{")) {
            val root = JSONObject(trimmed)
            val tagsArray = root.optJSONArray("tags")
            val assignmentsObj = root.optJSONObject("assignments")

            if (tagsArray != null) {
                for (i in 0 until tagsArray.length()) {
                    val obj = tagsArray.optJSONObject(i) ?: continue
                    val name = obj.optString("name").trim().take(MAX_TAG_NAME_LENGTH)
                    val colorHex = obj.optString("color").trim()
                    if (name.isNotEmpty()) {
                        uniqueTags[name] = if (colorHex.isNotEmpty()) parseHexColor(colorHex) else generateTagColor(name)
                    }
                }
            }

            if (assignmentsObj != null) {
                assignmentsObj.keys().forEach { key ->
                    val arr = assignmentsObj.optJSONArray(key)
                    val firstTag = (if (arr != null && arr.length() > 0) arr.optString(0) else assignmentsObj.optString(key)).trim().take(MAX_TAG_NAME_LENGTH)
                    if (firstTag.isNotBlank()) {
                        val pkg = key.split("/").first()
                        packageToTag[pkg] = firstTag
                        if (!uniqueTags.containsKey(firstTag)) {
                            uniqueTags[firstTag] = generateTagColor(firstTag)
                        }
                    }
                }
            } else {
                root.keys().forEach { rawTagName ->
                    val tagName = rawTagName.trim().take(MAX_TAG_NAME_LENGTH)
                    if (tagName.isEmpty()) return@forEach
                    val color = generateTagColor(tagName)
                    uniqueTags[tagName] = color
                    val arr = root.optJSONArray(rawTagName)
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val pkg = arr.optString(i).trim()
                            if (pkg.isNotEmpty()) {
                                packageToTag[pkg] = tagName
                            }
                        }
                    }
                }
            }
        }

        val componentTagMap = mutableMapOf<String, String>()
        val matchedPackages = mutableSetOf<String>()
        apps.forEach { app ->
            if (app.packageName in packageToTag) {
                val componentKey = app.componentKey
                componentTagMap[componentKey] = packageToTag[app.packageName]!!
                matchedPackages.add(app.packageName)
            }
        }

        val unmatchedPackages = packageToTag.keys.filter { it !in matchedPackages }

        return AutoTagsPreview(
            tags = uniqueTags.map { (name, color) ->
                AutoTagsPreview.AutoTagInfo(name = name, color = color)
            },
            matchedAppsCount = componentTagMap.size,
            unmatchedAppPackages = unmatchedPackages,
            componentTagMap = componentTagMap
        )
    }

    private fun extractIsFavorite(obj: JSONObject): Boolean {
        return when {
            obj.has("is_favorite") -> obj.optBoolean("is_favorite", false)
            obj.has("isFavorite") -> obj.optBoolean("isFavorite", false)
            obj.has("favorite") -> obj.optBoolean("favorite", false)
            else -> false
        }
    }

    private fun cleanJsonString(input: String): String {
        var s = input.trim()
        if (s.startsWith("```json", ignoreCase = true)) {
            s = s.substring(7).trim()
        } else if (s.startsWith("```")) {
            s = s.substring(3).trim()
        }
        if (s.endsWith("```")) {
            s = s.substring(0, s.length - 3).trim()
        }
        return s
    }

    private fun generateTagColor(name: String): Color {
        val palette = listOf(
            Color(0xFFE53935), Color(0xFFD81B60), Color(0xFF8E24AA),
            Color(0xFF5E35B1), Color(0xFF3949AB), Color(0xFF1E88E5),
            Color(0xFF039BE5), Color(0xFF00ACC1), Color(0xFF00897B),
            Color(0xFF43A047), Color(0xFF7CB342), Color(0xFFFB8C00),
            Color(0xFFF4511E), Color(0xFF6D4C41), Color(0xFF546E7A)
        )
        val index = abs(name.hashCode()) % palette.size
        return palette[index]
    }

    private fun parseHexColor(hex: String): Color {
        val cleaned = hex.removePrefix("#").trim()
        if (cleaned.isEmpty()) return Color(0xFF888888.toInt())
        return try {
            val colorInt = when (cleaned.length) {
                6 -> AndroidColor.parseColor("#$cleaned")
                8 -> AndroidColor.parseColor("#$cleaned")
                else -> AndroidColor.GRAY
            }
            Color(colorInt)
        } catch (_: Exception) {
            Color.Gray
        }
    }

    private fun colorToHex(color: Color): String {
        val argb = color.toArgb()
        return String.format("#%06X", 0xFFFFFF and argb)
    }
}

/**
 * Preview representing the output of parsed auto-tag configurations before application.
 */
data class AutoTagsPreview(
    val tags: List<AutoTagInfo>,
    val matchedAppsCount: Int,
    val unmatchedAppPackages: List<String>,
    val componentTagMap: Map<String, String> // componentKey -> tagName
) {
    /**
     * Holds name and color information for an auto tag recommendation.
     */
    data class AutoTagInfo(
        val name: String,
        val color: Color
    )
}

/**
 * Preview of a tags-backup file, shown in a confirmation dialog before applying.
 */
data class TagsBackupPreview(
    val newTags: List<TagInfo>,
    val assignmentCount: Int,
    val parsedAssignments: List<AssignmentInfo>,
    val existingTagCount: Int,
    val customLabels: Map<String, String> = emptyMap(),
    val favorites: List<String> = emptyList(),
    val tagAppOrders: Map<String, List<String>> = emptyMap(),
    val settings: Map<String, String> = emptyMap(),
    val customCharMappings: Map<String, Char> = emptyMap()
) {
    /**
     * Holds basic tag definition metadata in a backup.
     */
    data class TagInfo(
        val name: String,
        val color: Color,
        val emoji: String? = null,
        val oldId: String? = null
    )

    /**
     * Holds assignment mapping of tags to an application component.
     */
    data class AssignmentInfo(val componentKey: String, val tagNames: List<String>)
}
