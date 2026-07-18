package dev.msbs.cyclauncher.data

import dev.msbs.cyclauncher.model.Tag

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Manages persisted user actions, including favorite apps, launch history, custom app labels,
 * and custom tags with their application assignments.
 *
 * @param context The application context used to load shared preferences and access resources.
 */
class AppActionsManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)

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
     * Toggles the favorite status of the specified application.
     * Shows a confirmation toast and updates persistence.
     *
     * @param componentKey The unique application key (formatted as "packageName/activityName").
     */
    fun toggleFavorite(componentKey: String) {
        val current = _favorites.value.toMutableList()
        val label = componentKey.split("/").first().split(".").last().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        
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

    /**
     * Logs an application launch event. Updates the recent history list,
     * placing the app at the top and maintaining a size limit of 15.
     *
     * @param componentKey The application key.
     */
    fun logAppLaunch(componentKey: String) {
        val current = _history.value.toMutableList()
        current.remove(componentKey)
        current.add(0, componentKey)
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
        val label = componentKey.split("/").first().split(".").last().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        Toast.makeText(context, "Removed \"$label\" from History", Toast.LENGTH_SHORT).show()
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
     * Cleans up persistence data by removing any applications that are no longer installed on the device.
     *
     * @param validKeys The set of component keys for all currently installed applications.
     */
    fun cleanupInvalidApps(validKeys: Set<String>) {
        val newFavorites = _favorites.value.filter { it in validKeys }
        if (newFavorites.size != _favorites.value.size) {
            _favorites.value = newFavorites
            saveList("favorites", newFavorites)
        }
        
        val newHistory = _history.value.filter { it in validKeys }
        if (newHistory.size != _history.value.size) {
            _history.value = newHistory
            saveList("history", newHistory)
        }

        val newLabels = _customLabels.value.filter { it.key in validKeys }
        if (newLabels.size != _customLabels.value.size) {
            _customLabels.value = newLabels
            saveMap("custom_labels", newLabels)
        }

        val newAppTags = _appTags.value.filter { it.key in validKeys }
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
     * Imports custom app labels from a JSON file at the given URI.
     *
     * @param uri The source URI.
     * @param currentApps The current list of installed applications.
     * @return A map of application component keys to custom labels.
     */
    fun importAppNamesFromUri(uri: Uri, currentApps: List<dev.msbs.cyclauncher.model.AppInfo>): Map<String, String> {
        val jsonString = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: throw IllegalArgumentException("Cannot read file")
        val array = JSONArray(jsonString)
        val packageNameToApps = currentApps.groupBy { it.packageName }
        val imported = mutableMapOf<String, String>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val pkg = obj.optString("package")
            val label = obj.optString("label")
            if (pkg.isNotEmpty() && label.isNotEmpty()) {
                packageNameToApps[pkg]?.forEach { app ->
                    val key = "${app.packageName}/${app.activityName}"
                    imported[key] = label
                }
            }
        }
        return imported
    }

    /**
     * Applies a map of custom labels to the database and updates flows.
     *
     * @param map The map containing customized application labels.
     */
    fun applyAppLabels(map: Map<String, String>) {
        _customLabels.value = map
        saveMap("custom_labels", map)
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
     *
     * @param uri The source URI of the backup file.
     * @return A [TagsBackupPreview] containing parsed data.
     */
    fun parseTagsBackup(uri: Uri): TagsBackupPreview {
        val jsonString = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: throw IllegalArgumentException("Cannot read file")
        val root = JSONObject(jsonString)

        val existingNames = _tags.value.map { it.name.lowercase() }.toMutableSet()
        val tagsToCreate = mutableListOf<TagsBackupPreview.TagInfo>()

        root.optJSONArray("tags")?.let { array ->
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val name = obj.optString("name").trim()
                val colorHex = obj.optString("color")
                if (name.isEmpty()) continue
                if (name.lowercase() !in existingNames) {
                    tagsToCreate.add(TagsBackupPreview.TagInfo(name = name, color = parseHexColor(colorHex)))
                }
            }
        }

        val assignments = mutableListOf<TagsBackupPreview.AssignmentInfo>()
        root.optJSONObject("assignments")?.let { obj ->
            obj.keys().forEach { componentKey ->
                val arr = obj.getJSONArray(componentKey)
                val names = mutableListOf<String>()
                for (idx in 0 until arr.length()) {
                    arr.optString(idx).takeIf { it.isNotBlank() }?.let { names.add(it) }
                }
                if (names.isNotEmpty()) {
                    assignments.add(TagsBackupPreview.AssignmentInfo(componentKey, names))
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
     * wires up every assignment (matched by tag name), preserving any tags/apps
     * that already existed.
     */
    fun applyTagsBackup(preview: TagsBackupPreview) {
        val currentTags = _tags.value.toMutableList()
        val nameToId = currentTags.associate { it.name.lowercase() to it.id }.toMutableMap()

        preview.newTags.forEach { info ->
            val newTag = Tag(
                id = UUID.randomUUID().toString(),
                name = info.name,
                color = info.color
            )
            currentTags.add(newTag)
            nameToId[info.name.lowercase()] = newTag.id
        }
        _tags.value = currentTags
        saveTags(currentTags)

        val currentAppTags = _appTags.value.toMutableMap()
        preview.parsedAssignments.forEach { (componentKey, tagNames) ->
            val list = currentAppTags[componentKey]?.toMutableList() ?: mutableListOf()
            tagNames.forEach { name ->
                val id = nameToId[name.lowercase()]
                if (id != null && id !in list) list.add(id)
            }
            currentAppTags[componentKey] = list
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
     *
     * @param uri The URI of the JSON file containing the tagged results.
     * @param apps The current list of installed apps to match against.
     * @return An [AutoTagsPreview] detailing match metrics and tag metadata.
     */
    fun parseAutoTags(uri: Uri, apps: List<dev.msbs.cyclauncher.model.AppInfo>): AutoTagsPreview {
        val inputStream = context.contentResolver.openInputStream(uri)
        val jsonString = inputStream?.bufferedReader()?.use { it.readText() }
            ?: throw IllegalArgumentException("Cannot read file")

        val array = JSONArray(jsonString)
        val uniqueTags = mutableMapOf<String, Color>()
        val packageToTag = mutableMapOf<String, String>()

        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val pkg = obj.getString("package")
            val tagName = obj.getString("tag")
            val colorHex = obj.getString("color")
            val color = parseHexColor(colorHex)

            uniqueTags[tagName] = color
            packageToTag[pkg] = tagName
        }

        // Build componentKey -> tagName mapping for apps that exist on device
        val componentTagMap = mutableMapOf<String, String>()
        val matchedPackages = mutableSetOf<String>()
        apps.forEach { app ->
            if (app.packageName in packageToTag) {
                val componentKey = "${app.packageName}/${app.activityName}"
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
        // Create tags that don't exist yet
        val currentTags = _tags.value.toMutableList()
        val tagNameToId = mutableMapOf<String, String>()

        currentTags.forEach { tag ->
            tagNameToId[tag.name] = tag.id
        }

        preview.tags.forEach { autoTag ->
            if (autoTag.name !in tagNameToId) {
                val newTag = Tag(
                    id = UUID.randomUUID().toString(),
                    name = autoTag.name,
                    color = autoTag.color
                )
                currentTags.add(newTag)
                tagNameToId[autoTag.name] = newTag.id
            }
        }
        _tags.value = currentTags
        saveTags(currentTags)

        // Assign tags to apps
        val currentAppTags = _appTags.value.toMutableMap()

        preview.componentTagMap.forEach { (componentKey, tagName) ->
            val tagId = tagNameToId[tagName]
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

    private fun parseHexColor(hex: String): Color {
        val cleaned = hex.removePrefix("#")
        val argb = try {
            AndroidColor.parseColor("#$cleaned")
        } catch (e: Exception) {
            0xFF888888.toInt()
        }
        return Color(argb)
    }

    private fun colorToHex(color: Color): String {
        val argb = color.value.toInt()
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
