package dev.msbs.cyclauncher.data

import dev.msbs.cyclauncher.model.Tag

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

import dev.msbs.cyclauncher.utils.getSafeStorageContext

/**
 * Manages persisted user actions, including favorite apps, launch history, custom app labels,
 * and custom tags with their application assignments.
 *
 * @param context The application context used to load shared preferences and access resources.
 */
class AppActionsManager(context: Context) {
    private val context: Context = context.getSafeStorageContext()
    private val prefs: SharedPreferences = this.context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)

    private val _favorites = MutableStateFlow<List<String>>(loadList("favorites"))
    /** Stream of favorite application component keys. */
    val favorites: StateFlow<List<String>> = _favorites

    private val _history = MutableStateFlow<List<String>>(loadList("history"))
    /** Stream of recently launched application component keys. */
    val history: StateFlow<List<String>> = _history

    private val _customLabels = MutableStateFlow<Map<String, String>>(loadMap("custom_labels"))
    /** Stream of custom user-defined labels mapped by application component keys. */
    val customLabels: StateFlow<Map<String, String>> = _customLabels

    private val _tags = MutableStateFlow<List<Tag>>(loadTags())
    /** Stream of all created tags. */
    val tags: StateFlow<List<Tag>> = _tags

    private val _appTags = MutableStateFlow<Map<String, List<String>>>(loadAppTags())
    /** Stream mapping application component keys to a list of assigned tag IDs. */
    val appTags: StateFlow<Map<String, List<String>>> = _appTags

    /**
     * Toggles the favorite status of the specified application or tag folder.
     * Shows a confirmation toast and updates persistence.
     *
     * @param componentKey The unique application key (formatted as "packageName/activityName") or tag key ("tag:$tagId").
     */
    fun toggleFavorite(componentKey: String) {
        val current = _favorites.value.toMutableList()
        val isTag = componentKey.startsWith("tag:")
        val label = if (isTag) {
            val tagId = componentKey.removePrefix("tag:")
            _tags.value.find { it.id == tagId }?.name ?: "Tag"
        } else {
            componentKey.split("/").first().split(".").last().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
        
        if (current.contains(componentKey)) {
            current.remove(componentKey)
            Toast.makeText(context, "Removed \"$label\" from Favorites", Toast.LENGTH_SHORT).show()
        } else {
            current.add(componentKey)
            Toast.makeText(context, "Added \"$label\" to Favorites", Toast.LENGTH_SHORT).show()
        }
        _favorites.value = current
        saveList("favorites", current)
    }

    /**
     * Reorders the list of favorite applications by moving an item from [fromIndex] to [toIndex].
     *
     * @param fromIndex The original index of the item.
     * @param toIndex The new target index for the item.
     */
    fun reorderFavorites(fromIndex: Int, toIndex: Int) {
        val current = _favorites.value.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices) {
            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)
            _favorites.value = current
            saveList("favorites", current)
        }
    }

    /**
     * Checks if the specified application is in the favorites list.
     *
     * @param componentKey The application key.
     * @return true if the application is favorited, false otherwise.
     */
    fun isFavorite(componentKey: String): Boolean {
        return _favorites.value.contains(componentKey)
    }

    private val _isHistoryPaused = MutableStateFlow<Boolean>(prefs.getBoolean("is_history_paused", false))
    /** Stream indicating whether recording app launches to history is paused. */
    val isHistoryPaused: StateFlow<Boolean> = _isHistoryPaused

    /**
     * Toggles whether recording application launches to history is paused.
     * Shows a confirmation toast and updates persistence.
     *
     * @return The new paused state (true if paused, false if active).
     */
    fun toggleHistoryPaused(): Boolean {
        val newVal = !_isHistoryPaused.value
        _isHistoryPaused.value = newVal
        prefs.edit().putBoolean("is_history_paused", newVal).apply()
        Toast.makeText(
            context,
            if (newVal) "History recording paused" else "History recording resumed",
            Toast.LENGTH_SHORT
        ).show()
        return newVal
    }

    private val _recentlyUpdated = MutableStateFlow<Set<String>>(loadRecentlyUpdated())
    /** Stream of application component keys that were recently installed or updated and have not yet been launched. */
    val recentlyUpdated: StateFlow<Set<String>> = _recentlyUpdated

    private fun loadRecentlyUpdated(): Set<String> {
        val raw = prefs.getString("recently_updated_apps", null) ?: return emptySet()
        return try {
            val json = org.json.JSONArray(raw)
            val set = mutableSetOf<String>()
            for (i in 0 until json.length()) {
                set.add(json.getString(i))
            }
            set
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun saveRecentlyUpdated(set: Set<String>) {
        try {
            val json = org.json.JSONArray()
            set.forEach { json.put(it) }
            prefs.edit().putString("recently_updated_apps", json.toString()).apply()
        } catch (_: Exception) {}
    }

    /**
     * Logs an application launch event. Updates the recent history list,
     * placing the app at the top and maintaining a size limit of 15.
     * Also clears the "recently updated" badge once the app is launched.
     * Ignored if history recording is currently paused.
     *
     * @param componentKey The application key.
     */
    fun logAppLaunch(componentKey: String) {
        if (_recentlyUpdated.value.contains(componentKey)) {
            val updatedSet = _recentlyUpdated.value - componentKey
            _recentlyUpdated.value = updatedSet
            saveRecentlyUpdated(updatedSet)
        }
        if (_isHistoryPaused.value) return
        val current = _history.value.toMutableList()
        current.remove(componentKey)
        current.add(0, componentKey)
        val limited = current.take(15)
        _history.value = limited
        saveList("history", limited)
    }

    /**
     * Records a newly installed or updated application in the launch history list.
     * Places the app at the top of the history list (up to max 15 items) and marks it as recently updated.
     * Ignored if history recording is currently paused.
     *
     * @param componentKey The application key (formatted as "packageName/activityName").
     */
    fun onAppInstalledOrUpdated(componentKey: String) {
        val updatedSet = _recentlyUpdated.value + componentKey
        _recentlyUpdated.value = updatedSet
        saveRecentlyUpdated(updatedSet)

        if (_isHistoryPaused.value) return
        val current = _history.value.toMutableList()
        current.remove(componentKey)
        current.add(0, componentKey)
        val limited = current.take(15)
        _history.value = limited
        saveList("history", limited)
    }

    /**
     * Records multiple installed or updated applications in chronological order.
     *
     * @param componentKeys List of application keys ordered from oldest to newest update.
     */
    fun onAppsInstalledOrUpdated(componentKeys: List<String>) {
        if (componentKeys.isNotEmpty()) {
            val updatedSet = _recentlyUpdated.value + componentKeys
            _recentlyUpdated.value = updatedSet
            saveRecentlyUpdated(updatedSet)
        }

        if (_isHistoryPaused.value || componentKeys.isEmpty()) return
        val current = _history.value.toMutableList()
        for (key in componentKeys) {
            current.remove(key)
            current.add(0, key)
        }
        val limited = current.take(15)
        _history.value = limited
        saveList("history", limited)
    }

    /**
     * Removes the specified application from the launch history.
     * Shows a confirmation toast and updates persistence.
     *
     * @param componentKey The application key.
     */
    fun removeFromHistory(componentKey: String) {
        val current = _history.value.toMutableList()
        current.remove(componentKey)
        _history.value = current
        saveList("history", current)
        if (_recentlyUpdated.value.contains(componentKey)) {
            val updatedSet = _recentlyUpdated.value - componentKey
            _recentlyUpdated.value = updatedSet
            saveRecentlyUpdated(updatedSet)
        }
        val label = componentKey.split("/").first().split(".").last().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        Toast.makeText(context, "Removed \"$label\" from History", Toast.LENGTH_SHORT).show()
    }

    /**
     * Clears all entries from the launch history.
     * Shows a confirmation toast and updates persistence.
     */
    fun clearHistory() {
        _history.value = emptyList()
        saveList("history", emptyList())
        _recentlyUpdated.value = emptySet()
        saveRecentlyUpdated(emptySet())
        Toast.makeText(context, "History cleared", Toast.LENGTH_SHORT).show()
    }

    /**
     * Renames an application with a custom user-defined label.
     * If the new label is blank, the custom label is removed.
     *
     * @param componentKey The application key.
     * @param newLabel The new custom name for the app.
     */
    fun renameApp(componentKey: String, newLabel: String) {
        val current = _customLabels.value.toMutableMap()
        if (newLabel.isBlank()) {
            current.remove(componentKey)
        } else {
            current[componentKey] = newLabel
        }
        _customLabels.value = current
        saveMap("custom_labels", current)
    }

    /**
     * Creates a new tag and persists it.
     *
     * @param tag The tag instance to create.
     */
    fun createTag(tag: Tag) {
        val current = _tags.value.toMutableList()
        current.add(tag)
        _tags.value = current
        saveTags(current)
    }

    /**
     * Updates an existing tag with new property values (e.g. name or color).
     *
     * @param updatedTag The tag instance with updated properties.
     */
    fun updateTag(updatedTag: Tag) {
        val current = _tags.value.map { if (it.id == updatedTag.id) updatedTag else it }
        _tags.value = current
        saveTags(current)
    }

    /**
     * Deletes a tag, updates persistence, and cleans up any references in application assignments.
     *
     * @param tagId The unique identifier of the tag to delete.
     */
    fun deleteTag(tagId: String) {
        val currentTags = _tags.value.filter { it.id != tagId }
        _tags.value = currentTags
        saveTags(currentTags)

        // Also remove from favorites if favorited
        val tagKey = "tag:$tagId"
        if (_favorites.value.contains(tagKey)) {
            val currentFavorites = _favorites.value.filter { it != tagKey }
            _favorites.value = currentFavorites
            saveList("favorites", currentFavorites)
        }

        // Also remove assignments
        val currentAppTags = _appTags.value.toMutableMap()
        currentAppTags.forEach { (key, list) ->
            if (list.contains(tagId)) {
                currentAppTags[key] = list.filter { it != tagId }
            }
        }
        _appTags.value = currentAppTags
        saveAppTags(currentAppTags)
    }

    /**
     * Toggles the assignment of a tag to a specific application.
     *
     * @param componentKey The application key.
     * @param tagId The unique identifier of the tag.
     */
    fun toggleTagForApp(componentKey: String, tagId: String) {
        val current = _appTags.value.toMutableMap()
        val list = current[componentKey]?.toMutableList() ?: mutableListOf()
        if (list.contains(tagId)) {
            list.remove(tagId)
        } else {
            list.add(tagId)
        }
        current[componentKey] = list
        _appTags.value = current
        saveAppTags(current)
    }
    
    /**
     * Cleans up stored favorites, history, custom labels, and app tags for a specific package that was uninstalled.
     *
     * @param packageName The package name of the uninstalled application.
     */
    fun onPackageRemoved(packageName: String) {
        val newFavorites = _favorites.value.filterNot { it.startsWith("$packageName/") || it == packageName }
        if (newFavorites.size != _favorites.value.size) {
            _favorites.value = newFavorites
            saveList("favorites", newFavorites)
        }

        val newHistory = _history.value.filterNot { it.startsWith("$packageName/") || it == packageName }
        if (newHistory.size != _history.value.size) {
            _history.value = newHistory
            saveList("history", newHistory)
        }

        val newLabels = _customLabels.value.filterKeys { !it.startsWith("$packageName/") && it != packageName }
        if (newLabels.size != _customLabels.value.size) {
            _customLabels.value = newLabels
            saveMap("custom_labels", newLabels)
        }

        val newAppTags = _appTags.value.filterKeys { !it.startsWith("$packageName/") && it != packageName }
        if (newAppTags.size != _appTags.value.size) {
            _appTags.value = newAppTags
            saveAppTags(newAppTags)
        }

        val newRecent = _recentlyUpdated.value.filterNot { it.startsWith("$packageName/") || it == packageName }.toSet()
        if (newRecent.size != _recentlyUpdated.value.size) {
            _recentlyUpdated.value = newRecent
            saveRecentlyUpdated(newRecent)
        }

        val updateTimes = loadAppUpdateTimes().toMutableMap()
        if (updateTimes.remove(packageName) != null) {
            saveAppUpdateTimes(updateTimes)
        }
    }

    /**
     * Loads the map of tracked package names and their last known update timestamps.
     */
    fun loadAppUpdateTimes(): Map<String, Long> {
        val raw = prefs.getString("app_last_update_times", null) ?: return emptyMap()
        return try {
            val json = org.json.JSONObject(raw)
            val map = mutableMapOf<String, Long>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = json.getLong(k)
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /**
     * Saves the map of tracked package names and their last known update timestamps.
     */
    fun saveAppUpdateTimes(map: Map<String, Long>) {
        try {
            val json = org.json.JSONObject()
            map.forEach { (k, v) -> json.put(k, v) }
            prefs.edit().putString("app_last_update_times", json.toString()).apply()
        } catch (_: Exception) {}
    }

    /**
     * Safely cleans up references to uninstalled apps by querying PackageManager for each package individually.
     * This avoids accidentally wiping user data during system boot when bulk activity queries might be incomplete.
     *
     * @param pm The system PackageManager instance.
     */
    fun cleanupUninstalledApps(pm: android.content.pm.PackageManager) {
        fun isPackageInstalled(key: String): Boolean {
            val pkgName = key.split("/").firstOrNull() ?: return false
            return try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(pkgName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkgName, 0)
                }
                true
            } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
                false
            } catch (e: Exception) {
                true // Fallback: keep if status couldn't be definitively checked
            }
        }

        val newFavorites = _favorites.value.filter { key ->
            if (key.startsWith("tag:")) {
                val tagId = key.removePrefix("tag:")
                _tags.value.any { it.id == tagId }
            } else {
                isPackageInstalled(key)
            }
        }
        if (newFavorites.size != _favorites.value.size) {
            _favorites.value = newFavorites
            saveList("favorites", newFavorites)
        }

        val newHistory = _history.value.filter { isPackageInstalled(it) }
        if (newHistory.size != _history.value.size) {
            _history.value = newHistory
            saveList("history", newHistory)
        }

        val newLabels = _customLabels.value.filterKeys { isPackageInstalled(it) }
        if (newLabels.size != _customLabels.value.size) {
            _customLabels.value = newLabels
            saveMap("custom_labels", newLabels)
        }

        val newAppTags = _appTags.value.filterKeys { isPackageInstalled(it) }
        if (newAppTags.size != _appTags.value.size) {
            _appTags.value = newAppTags
            saveAppTags(newAppTags)
        }
    }

    // App list export / import (unified — used by both Settings and AutoTags).
    // Exports the list of installed apps as { "package", "label" } objects.
    // JSON is machine-friendly (the original format), TXT is human-readable.

    /**
     * Exports the list of installed apps to a JSON file at the specified URI.
     *
     * @param uri The destination URI.
     * @param apps The list of applications to export.
     */
    fun exportAppNamesToUri(uri: Uri, apps: List<dev.msbs.cyclauncher.model.AppInfo>) {
        try {
            val array = JSONArray()
            apps.forEach { app ->
                val obj = JSONObject()
                obj.put("package", app.packageName)
                obj.put("label", app.label)
                array.put(obj)
            }
            val json = array.toString(2)
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(json.toByteArray())
            }
            Toast.makeText(context, "Exported ${apps.size} apps", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Exports the list of installed apps to a plain-text file at the specified URI,
     * formatting each line as "Label — package".
     *
     * @param uri The destination URI.
     * @param apps The list of applications to export.
     */
    fun exportAppNamesToUriAsText(uri: Uri, apps: List<dev.msbs.cyclauncher.model.AppInfo>) {
        try {
            val text = buildString {
                apps.forEach { app ->
                    append(app.label)
                    append(" — ")
                    append(app.packageName)
                    append('\n')
                }
            }
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(text.toByteArray())
            }
            Toast.makeText(context, "Exported ${apps.size} apps", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Imports custom app labels from a JSON or text file at the given URI.
     * Supports multiple JSON structures (arrays, key-value maps, nested objects) and plain text.
     *
     * @param uri The source URI.
     * @param currentApps The current list of installed applications.
     * @return A map of application component keys to custom labels.
     */
    fun importAppNamesFromUri(uri: Uri, currentApps: List<dev.msbs.cyclauncher.model.AppInfo>): Map<String, String> {
        val jsonString = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: throw IllegalArgumentException("Cannot read file")
        val trimmed = jsonString.trim()
        val packageToApps = currentApps.groupBy { it.packageName }
        val imported = mutableMapOf<String, String>()

        if (trimmed.startsWith("[")) {
            val array = JSONArray(trimmed)
            for (i in 0 until array.length()) {
                val item = array.opt(i)
                if (item is JSONObject) {
                    val pkg = item.optString("package").ifEmpty { item.optString("packageName").ifEmpty { item.optString("app") } }.trim()
                    val component = item.optString("component").ifEmpty { item.optString("componentKey") }.trim()
                    val label = item.optString("label").ifEmpty { item.optString("name").ifEmpty { item.optString("customLabel") } }.trim()

                    if (label.isNotEmpty()) {
                        if (component.isNotEmpty()) {
                            imported[component] = label
                        } else if (pkg.isNotEmpty()) {
                            val matchingApps = packageToApps[pkg]
                            if (!matchingApps.isNullOrEmpty()) {
                                matchingApps.forEach { app -> imported[app.componentKey] = label }
                            } else {
                                imported[pkg] = label
                            }
                        }
                    }
                }
            }
        } else if (trimmed.startsWith("{")) {
            val root = JSONObject(trimmed)
            val nestedArray = root.optJSONArray("apps") ?: root.optJSONArray("labels") ?: root.optJSONArray("custom_labels")
            val nestedObj = root.optJSONObject("custom_labels") ?: root.optJSONObject("labels") ?: root.optJSONObject("apps")

            if (nestedArray != null) {
                for (i in 0 until nestedArray.length()) {
                    val item = nestedArray.optJSONObject(i) ?: continue
                    val pkg = item.optString("package").ifEmpty { item.optString("packageName").ifEmpty { item.optString("app") } }.trim()
                    val component = item.optString("component").ifEmpty { item.optString("componentKey") }.trim()
                    val label = item.optString("label").ifEmpty { item.optString("name").ifEmpty { item.optString("customLabel") } }.trim()
                    if (label.isNotEmpty()) {
                        if (component.isNotEmpty()) {
                            imported[component] = label
                        } else if (pkg.isNotEmpty()) {
                            val matchingApps = packageToApps[pkg]
                            if (!matchingApps.isNullOrEmpty()) {
                                matchingApps.forEach { app -> imported[app.componentKey] = label }
                            } else {
                                imported[pkg] = label
                            }
                        }
                    }
                }
            } else {
                val targetObj = nestedObj ?: root
                targetObj.keys().forEach { key ->
                    val label = targetObj.optString(key).trim()
                    if (label.isNotEmpty()) {
                        if (key.contains("/")) {
                            imported[key] = label
                        } else {
                            val matchingApps = packageToApps[key]
                            if (!matchingApps.isNullOrEmpty()) {
                                matchingApps.forEach { app -> imported[app.componentKey] = label }
                            } else {
                                imported[key] = label
                            }
                        }
                    }
                }
            }
        } else {
            // Plain text lines format ("Label — package" or "package: Label" or "package = Label")
            trimmed.lines().forEach { line ->
                val l = line.trim()
                if (l.contains("—")) {
                    val parts = l.split("—", limit = 2).map { it.trim() }
                    if (parts.size == 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()) {
                        val label = parts[0]
                        val pkg = parts[1]
                        val matchingApps = packageToApps[pkg]
                        if (!matchingApps.isNullOrEmpty()) {
                            matchingApps.forEach { app -> imported[app.componentKey] = label }
                        } else {
                            imported[pkg] = label
                        }
                    }
                } else if (l.contains(":") || l.contains("=")) {
                    val delimiter = if (l.contains(":")) ":" else "="
                    val parts = l.split(delimiter, limit = 2).map { it.trim() }
                    if (parts.size == 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()) {
                        val pkg = parts[0]
                        val label = parts[1]
                        val matchingApps = packageToApps[pkg]
                        if (!matchingApps.isNullOrEmpty()) {
                            matchingApps.forEach { app -> imported[app.componentKey] = label }
                        } else {
                            imported[pkg] = label
                        }
                    }
                }
            }
        }
        return imported
    }

    /**
     * Merges a map of custom labels into the database and updates flows.
     *
     * @param map The map containing customized application labels.
     */
    fun applyAppLabels(map: Map<String, String>) {
        val current = _customLabels.value.toMutableMap()
        current.putAll(map)
        _customLabels.value = current
        saveMap("custom_labels", current)
    }

    // Tags backup export / import (tags + assignments). Unified across the app.

    /**
     * Exports every defined tag (name + color) and every tag assignment
     * (componentKey -> list of tag names) to a JSON object written to [uri].
     *
     * @param uri The destination URI.
     */
    fun exportTagsBackupToUri(uri: Uri) {
        try {
            val idToName = _tags.value.associate { it.id to it.name }
            val tagsArray = JSONArray()
            _tags.value.forEach { tag ->
                val obj = JSONObject()
                obj.put("name", tag.name)
                obj.put("color", colorToHex(tag.color))
                tagsArray.put(obj)
            }

            // Map componentKey -> tag names (only keep tags that still exist).
            val assignments = JSONObject()
            _appTags.value.forEach { (componentKey, tagIds) ->
                val names = tagIds.mapNotNull { id -> idToName[id] }
                if (names.isNotEmpty()) {
                    assignments.put(componentKey, JSONArray(names))
                }
            }

            val root = JSONObject()
            root.put("version", 1)
            root.put("tags", tagsArray)
            root.put("assignments", assignments)

            context.contentResolver.openOutputStream(uri)?.use {
                it.write(root.toString(2).toByteArray())
            }
            Toast.makeText(context, "Exported ${_tags.value.size} tags", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Parses a tags-backup JSON into a preview without applying anything.
     * Supports unified backup format, AutoTags format, and tag dictionary format.
     *
     * @param uri The source URI of the backup file.
     * @return A [TagsBackupPreview] containing parsed data.
     */
    fun parseTagsBackup(uri: Uri): TagsBackupPreview {
        val jsonString = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: throw IllegalArgumentException("Cannot read file")
        val trimmed = jsonString.trim()

        val existingNames = _tags.value.map { it.name.lowercase().trim() }.toMutableSet()
        val tagsToCreate = mutableListOf<TagsBackupPreview.TagInfo>()
        val createdNamesSet = mutableSetOf<String>()
        val assignments = mutableListOf<TagsBackupPreview.AssignmentInfo>()

        if (trimmed.startsWith("[")) {
            // AutoTags array format: [{"package": "...", "tag": "...", "color": "..."}]
            val array = JSONArray(trimmed)
            val packageToTagNames = mutableMapOf<String, MutableList<String>>()
            val tagColors = mutableMapOf<String, Color>()

            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val pkg = obj.optString("package").ifEmpty { obj.optString("packageName") }.trim()
                val colorHex = obj.optString("color").trim()
                val color = if (colorHex.isNotEmpty()) parseHexColor(colorHex) else Color.Unspecified

                val tagNames = mutableListOf<String>()
                val singleTag = obj.optString("tag").ifEmpty { obj.optString("name") }.trim()
                if (singleTag.isNotEmpty()) tagNames.add(singleTag)
                obj.optJSONArray("tags")?.let { tagsArr ->
                    for (j in 0 until tagsArr.length()) {
                        tagsArr.optString(j).trim().takeIf { it.isNotEmpty() }?.let { tagNames.add(it) }
                    }
                }

                tagNames.forEach { tagName ->
                    val lower = tagName.lowercase().trim()
                    if (color != Color.Unspecified && !tagColors.containsKey(lower)) {
                        tagColors[lower] = color
                    }
                    if (lower !in existingNames && lower !in createdNamesSet) {
                        val resolvedColor = tagColors[lower] ?: generateTagColor(tagName)
                        tagsToCreate.add(TagsBackupPreview.TagInfo(name = tagName, color = resolvedColor))
                        createdNamesSet.add(lower)
                    }
                    if (pkg.isNotEmpty()) {
                        packageToTagNames.getOrPut(pkg) { mutableListOf() }.apply {
                            if (!contains(tagName)) add(tagName)
                        }
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

            if (tagsArray != null || assignmentsObj != null) {
                tagsArray?.let { array ->
                    for (i in 0 until array.length()) {
                        val obj = array.optJSONObject(i) ?: continue
                        val name = obj.optString("name").trim()
                        val colorHex = obj.optString("color").trim()
                        if (name.isEmpty()) continue
                        val lower = name.lowercase().trim()
                        if (lower !in existingNames && lower !in createdNamesSet) {
                            val resolvedColor = if (colorHex.isNotEmpty()) parseHexColor(colorHex) else generateTagColor(name)
                            tagsToCreate.add(TagsBackupPreview.TagInfo(name = name, color = resolvedColor))
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
                                arr.optString(idx).trim().takeIf { it.isNotBlank() }?.let { names.add(it) }
                            }
                        } else {
                            obj.optString(componentKey).trim().takeIf { it.isNotBlank() }?.let { names.add(it) }
                        }
                        if (names.isNotEmpty()) {
                            assignments.add(TagsBackupPreview.AssignmentInfo(componentKey, names))
                        }
                    }
                }
            } else {
                // Dictionary format {"TagName": ["pkg1", "pkg2"]}
                val packageToTagNames = mutableMapOf<String, MutableList<String>>()
                root.keys().forEach { tagName ->
                    val lower = tagName.lowercase().trim()
                    if (lower !in existingNames && lower !in createdNamesSet) {
                        tagsToCreate.add(TagsBackupPreview.TagInfo(name = tagName, color = generateTagColor(tagName)))
                        createdNamesSet.add(lower)
                    }
                    val arr = root.optJSONArray(tagName)
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
            existingTagCount = _tags.value.size
        )
    }

    /**
     * Applies a previously-parsed [TagsBackupPreview]: creates missing tags and
     * wires up every assignment (matched by tag name), resolving package names to installed apps.
     */
    fun applyTagsBackup(preview: TagsBackupPreview, installedApps: List<dev.msbs.cyclauncher.model.AppInfo> = emptyList()) {
        val currentTags = _tags.value.toMutableList()
        val nameToId = currentTags.associate { it.name.lowercase().trim() to it.id }.toMutableMap()

        preview.newTags.forEach { info ->
            val lower = info.name.lowercase().trim()
            if (lower !in nameToId) {
                val newTag = Tag(
                    id = UUID.randomUUID().toString(),
                    name = info.name,
                    color = info.color
                )
                currentTags.add(newTag)
                nameToId[lower] = newTag.id
            }
        }
        _tags.value = currentTags
        saveTags(currentTags)

        val packageToApps = installedApps.groupBy { it.packageName }
        val currentAppTags = _appTags.value.toMutableMap()

        preview.parsedAssignments.forEach { (targetKey, tagNames) ->
            val resolvedIds = tagNames.mapNotNull { nameToId[it.lowercase().trim()] }
            if (resolvedIds.isEmpty()) return@forEach

            if (targetKey.contains("/")) {
                val list = currentAppTags[targetKey]?.toMutableList() ?: mutableListOf()
                resolvedIds.forEach { id -> if (id !in list) list.add(id) }
                currentAppTags[targetKey] = list
            } else {
                val matchingApps = packageToApps[targetKey]
                if (!matchingApps.isNullOrEmpty()) {
                    matchingApps.forEach { app ->
                        val key = app.componentKey
                        val list = currentAppTags[key]?.toMutableList() ?: mutableListOf()
                        resolvedIds.forEach { id -> if (id !in list) list.add(id) }
                        currentAppTags[key] = list
                    }
                } else {
                    val list = currentAppTags[targetKey]?.toMutableList() ?: mutableListOf()
                    resolvedIds.forEach { id -> if (id !in list) list.add(id) }
                    currentAppTags[targetKey] = list
                }
            }
        }
        _appTags.value = currentAppTags
        saveAppTags(currentAppTags)

        val created = preview.newTags.size
        Toast.makeText(
            context,
            "Imported ${preview.parsedAssignments.size} tag assignments" +
                if (created > 0) " ($created new tags)" else "",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun saveList(key: String, list: List<String>) {
        prefs.edit().putString(key, JSONArray(list).toString()).apply()
    }

    private fun loadList(key: String): List<String> {
        val json = prefs.getString(key, null) ?: return emptyList()
        val array = JSONArray(json)
        return List(array.length()) { array.getString(it) }
    }

    private fun saveMap(key: String, map: Map<String, String>) {
        val json = JSONObject(map)
        prefs.edit().putString(key, json.toString()).apply()
    }

    private fun loadMap(key: String): Map<String, String> {
        val jsonString = prefs.getString(key, null) ?: return emptyMap()
        val json = JSONObject(jsonString)
        val map = mutableMapOf<String, String>()
        json.keys().forEach { k ->
            map[k] = json.getString(k)
        }
        return map
    }

    private fun saveTags(list: List<Tag>) {
        val array = JSONArray()
        list.forEach { array.put(it.toJsonObject()) }
        prefs.edit().putString("tags", array.toString()).apply()
    }

    private fun loadTags(): List<Tag> {
        val json = prefs.getString("tags", null) ?: return emptyList()
        val array = JSONArray(json)
        return List(array.length()) { Tag.fromJsonObject(array.getJSONObject(it)) }
    }

    private fun saveAppTags(map: Map<String, List<String>>) {
        val json = JSONObject()
        map.forEach { (k, v) -> json.put(k, JSONArray(v)) }
        prefs.edit().putString("app_tags", json.toString()).apply()
    }

    private fun loadAppTags(): Map<String, List<String>> {
        val jsonString = prefs.getString("app_tags", null) ?: return emptyMap()
        val json = JSONObject(jsonString)
        val map = mutableMapOf<String, List<String>>()
        json.keys().forEach { k ->
            val array = json.getJSONArray(k)
            map[k] = List(array.length()) { array.getString(it) }
        }
        return map
    }

    /**
     * Parses an AI-generated tagged application JSON file to create a preview mapping.
     * Supports both array and unified backup JSON structures.
     *
     * @param uri The URI of the JSON file containing the tagged results.
     * @param apps The current list of installed apps to match against.
     * @return An [AutoTagsPreview] detailing match metrics and tag metadata.
     */
    fun parseAutoTags(uri: Uri, apps: List<dev.msbs.cyclauncher.model.AppInfo>): AutoTagsPreview {
        val inputStream = context.contentResolver.openInputStream(uri)
        val jsonString = inputStream?.bufferedReader()?.use { it.readText() }
            ?: throw IllegalArgumentException("Cannot read file")
        val trimmed = jsonString.trim()

        val uniqueTags = mutableMapOf<String, Color>()
        val packageToTag = mutableMapOf<String, String>()

        if (trimmed.startsWith("[")) {
            val array = JSONArray(trimmed)
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val pkg = obj.optString("package").ifEmpty { obj.optString("packageName") }.trim()
                val tagName = obj.optString("tag").ifEmpty { obj.optString("name") }.trim()
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
                    val name = obj.optString("name").trim()
                    val colorHex = obj.optString("color").trim()
                    if (name.isNotEmpty()) {
                        uniqueTags[name] = if (colorHex.isNotEmpty()) parseHexColor(colorHex) else generateTagColor(name)
                    }
                }
            }

            if (assignmentsObj != null) {
                assignmentsObj.keys().forEach { key ->
                    val arr = assignmentsObj.optJSONArray(key)
                    val firstTag = if (arr != null && arr.length() > 0) arr.optString(0) else assignmentsObj.optString(key)
                    if (firstTag.isNotBlank()) {
                        val pkg = key.split("/").first()
                        packageToTag[pkg] = firstTag.trim()
                        if (!uniqueTags.containsKey(firstTag.trim())) {
                            uniqueTags[firstTag.trim()] = generateTagColor(firstTag.trim())
                        }
                    }
                }
            }
        }

        // Build componentKey -> tagName mapping for apps that exist on device
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

    /**
     * Applies the matched auto-tags preview by creating non-existent tags and assigning
     * them to their corresponding applications.
     *
     * @param preview The auto-tags preview to apply.
     */
    fun applyAutoTags(preview: AutoTagsPreview) {
        val currentTags = _tags.value.toMutableList()
        val tagNameToId = mutableMapOf<String, String>()

        currentTags.forEach { tag ->
            tagNameToId[tag.name.lowercase().trim()] = tag.id
        }

        preview.tags.forEach { autoTag ->
            val lower = autoTag.name.lowercase().trim()
            if (lower !in tagNameToId) {
                val newTag = Tag(
                    id = UUID.randomUUID().toString(),
                    name = autoTag.name,
                    color = autoTag.color
                )
                currentTags.add(newTag)
                tagNameToId[lower] = newTag.id
            }
        }
        _tags.value = currentTags
        saveTags(currentTags)

        val currentAppTags = _appTags.value.toMutableMap()

        preview.componentTagMap.forEach { (componentKey, tagName) ->
            val tagId = tagNameToId[tagName.lowercase().trim()]
            if (tagId != null) {
                val list = currentAppTags[componentKey]?.toMutableList() ?: mutableListOf()
                if (tagId !in list) {
                    list.add(tagId)
                }
                currentAppTags[componentKey] = list
            }
        }

        _appTags.value = currentAppTags
        saveAppTags(currentAppTags)

        Toast.makeText(context, "Applied ${preview.tags.size} tags to ${preview.matchedAppsCount} apps", Toast.LENGTH_SHORT).show()
    }

    private fun generateTagColor(name: String): Color {
        val palette = listOf(
            Color(0xFFE53935), Color(0xFFD81B60), Color(0xFF8E24AA),
            Color(0xFF5E35B1), Color(0xFF3949AB), Color(0xFF1E88E5),
            Color(0xFF039BE5), Color(0xFF00ACC1), Color(0xFF00897B),
            Color(0xFF43A047), Color(0xFF7CB342), Color(0xFFFB8C00),
            Color(0xFFF4511E), Color(0xFF6D4C41), Color(0xFF546E7A)
        )
        val index = kotlin.math.abs(name.hashCode()) % palette.size
        return palette[index]
    }

    private fun parseHexColor(hex: String): Color {
        val cleaned = hex.removePrefix("#").trim()
        if (cleaned.isEmpty()) return Color(0xFF888888.toInt())
        val argb = try {
            AndroidColor.parseColor("#$cleaned")
        } catch (e: Exception) {
            0xFF888888.toInt()
        }
        return Color(argb)
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
    val existingTagCount: Int
) {
    /**
     * Holds basic tag definition metadata in a backup.
     */
    data class TagInfo(val name: String, val color: Color)

    /**
     * Holds assignment mapping of tags to an application component.
     */
    data class AssignmentInfo(val componentKey: String, val tagNames: List<String>)
}
