package dev.msbs.cyclauncher

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class AppActionsManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)

    private val _favorites = MutableStateFlow<List<String>>(loadList("favorites"))
    val favorites: StateFlow<List<String>> = _favorites

    private val _history = MutableStateFlow<List<String>>(loadList("history"))
    val history: StateFlow<List<String>> = _history

    private val _customLabels = MutableStateFlow<Map<String, String>>(loadMap("custom_labels"))
    val customLabels: StateFlow<Map<String, String>> = _customLabels

    private val _tags = MutableStateFlow<List<Tag>>(loadTags())
    val tags: StateFlow<List<Tag>> = _tags

    private val _appTags = MutableStateFlow<Map<String, List<String>>>(loadAppTags())
    val appTags: StateFlow<Map<String, List<String>>> = _appTags

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

    fun isFavorite(componentKey: String): Boolean {
        return _favorites.value.contains(componentKey)
    }

    fun logAppLaunch(componentKey: String) {
        val current = _history.value.toMutableList()
        current.remove(componentKey)
        current.add(0, componentKey)
        val limited = current.take(15)
        _history.value = limited
        saveList("history", limited)
    }

    fun removeFromHistory(componentKey: String) {
        val current = _history.value.toMutableList()
        current.remove(componentKey)
        _history.value = current
        saveList("history", current)
        val label = componentKey.split("/").first().split(".").last().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        Toast.makeText(context, "Removed \"$label\" from History", Toast.LENGTH_SHORT).show()
    }

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

    fun createTag(tag: Tag) {
        val current = _tags.value.toMutableList()
        current.add(tag)
        _tags.value = current
        saveTags(current)
    }

    fun updateTag(updatedTag: Tag) {
        val current = _tags.value.map { if (it.id == updatedTag.id) updatedTag else it }
        _tags.value = current
        saveTags(current)
    }

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

    fun exportCustomLabels() {
        try {
            val json = JSONObject(_customLabels.value).toString(4)
            val file = File(context.cacheDir, "cyclauncher_labels_backup.json")
            file.writeText(json)
            
            val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Export Custom Labels").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun saveLabelsToUri(uri: Uri) {
        try {
            val json = JSONObject(_customLabels.value).toString(4)
            context.contentResolver.openOutputStream(uri)?.use { 
                it.write(json.toByteArray())
            }
            Toast.makeText(context, "Labels saved successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun importCustomLabels(uri: Uri) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val jsonString = inputStream?.bufferedReader()?.use { it.readText() }
            if (jsonString != null) {
                val json = JSONObject(jsonString)
                val map = mutableMapOf<String, String>()
                json.keys().forEach { k ->
                    map[k] = json.getString(k)
                }
                _customLabels.value = map
                saveMap("custom_labels", map)
                Toast.makeText(context, "Imported ${map.size} labels", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
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
}
