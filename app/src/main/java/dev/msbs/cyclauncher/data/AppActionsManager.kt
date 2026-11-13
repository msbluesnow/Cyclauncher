package dev.msbs.cyclauncher.data

import dev.msbs.cyclauncher.R
import dev.msbs.cyclauncher.model.AppInfo
import dev.msbs.cyclauncher.model.Tag
import dev.msbs.cyclauncher.utils.LocaleUtils
import dev.msbs.cyclauncher.utils.getSafeStorageContext

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Manages persisted user actions: favorites, launch history, custom labels, custom character mappings, and tags.
 * File I/O and backup/export/import parsing are delegated to [LauncherBackupManager].
 */
class AppActionsManager(context: Context) {
    private val context: Context = context.getSafeStorageContext()
    private val prefs: SharedPreferences = this.context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
    private val backupManager = LauncherBackupManager(this.context, prefs)
    private val storageExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "AppActionsStorageThread").apply { isDaemon = true }
    }

    /** Shuts down the background persistence executor when the owner ViewModel is cleared. */
    fun shutdown() {
        storageExecutor.shutdown()
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentToast: Toast? = null

    private fun showToast(msg: String, length: Int = Toast.LENGTH_SHORT) {
        val display = Runnable {
            try {
                currentToast?.cancel()
                currentToast = Toast.makeText(context, msg, length).also { it.show() }
            } catch (_: Exception) {}
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            display.run()
        } else {
            mainHandler.post(display)
        }
    }

    private val _favorites = MutableStateFlow<List<String>>(loadList("favorites").take(13))
    val favorites: StateFlow<List<String>> = _favorites

    private val _history = MutableStateFlow<List<String>>(loadList("history"))
    val history: StateFlow<List<String>> = _history

    private val _customLabels = MutableStateFlow<Map<String, String>>(loadMap("custom_labels"))
    val customLabels: StateFlow<Map<String, String>> = _customLabels

    private val _tags = MutableStateFlow<List<Tag>>(loadTags())
    val tags: StateFlow<List<Tag>> = _tags

    private val _appTags = MutableStateFlow<Map<String, List<String>>>(loadAppTags())
    val appTags: StateFlow<Map<String, List<String>>> = _appTags

    private val _tagAppOrders = MutableStateFlow<Map<String, List<String>>>(loadTagAppOrders())
    val tagAppOrders: StateFlow<Map<String, List<String>>> = _tagAppOrders

    private val _customCharMappings = MutableStateFlow<Map<String, Char>>(loadCustomCharMappings())
    val customCharMappings: StateFlow<Map<String, Char>> = _customCharMappings

    fun toggleFavorite(componentKey: String) {
        var current = _favorites.value.toMutableList()
        val isTag = componentKey.startsWith("tag:")
        val label = if (isTag) {
            val tagId = componentKey.removePrefix("tag:")
            _tags.value.find { it.id == tagId }?.name ?: "Tag"
        } else {
            componentKey.substringBefore('/').substringAfterLast('.').replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }

        if (current.contains(componentKey)) {
            current.remove(componentKey)
            showToast(context.getString(R.string.toast_removed_from_favorites, label))
        } else {
            val existingTagIds = _tags.value.map { it.id }.toSet()
            current = current.filter { key ->
                if (key.startsWith("tag:")) {
                    existingTagIds.contains(key.removePrefix("tag:"))
                } else true
            }.toMutableList()

            if (current.size >= 13) {
                showToast(context.getString(R.string.toast_favorites_limit))
                return
            }
            current.add(componentKey)
            showToast(context.getString(R.string.toast_added_to_favorites, label))
        }
        val capped = current.take(13)
        _favorites.value = capped
        saveList("favorites", capped)
    }

    fun reorderFavorites(fromIndex: Int, toIndex: Int) {
        val current = _favorites.value.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices && fromIndex != toIndex) {
            val fromKey = current[fromIndex]
            val toKey = current[toIndex]
            reorderFavoritesByKeys(fromKey, toKey)
        }
    }

    fun reorderFavoritesByKeys(fromKey: String, toKey: String) {
        val current = _favorites.value.toMutableList()
        val fromPos = current.indexOf(fromKey)
        val toPos = current.indexOf(toKey)
        if (fromPos != -1 && toPos != -1 && fromPos != toPos) {
            val item = current.removeAt(fromPos)
            current.add(toPos, item)
            _favorites.value = current
            saveList("favorites", current)
        }
    }

    fun isFavorite(componentKey: String): Boolean {
        return _favorites.value.contains(componentKey)
    }

    private val _isHistoryPaused = MutableStateFlow<Boolean>(prefs.getBoolean("is_history_paused", false))
    val isHistoryPaused: StateFlow<Boolean> = _isHistoryPaused

    fun toggleHistoryPaused(): Boolean {
        val newVal = !_isHistoryPaused.value
        _isHistoryPaused.value = newVal
        storageExecutor.execute {
            prefs.edit().putBoolean("is_history_paused", newVal).apply()
        }
        showToast(context.getString(if (newVal) R.string.toast_history_recording_paused else R.string.toast_history_recording_resumed))
        return newVal
    }

    private val _recentlyUpdated = MutableStateFlow<Set<String>>(loadRecentlyUpdated())
    val recentlyUpdated: StateFlow<Set<String>> = _recentlyUpdated

    private fun loadRecentlyUpdated(): Set<String> {
        val raw = prefs.getString("recently_updated_apps", null) ?: return emptySet()
        return try {
            val json = JSONArray(raw)
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
        val snapshot = set.toSet()
        storageExecutor.execute {
            try {
                val json = JSONArray()
                snapshot.forEach { json.put(it) }
                prefs.edit().putString("recently_updated_apps", json.toString()).apply()
            } catch (_: Exception) {}
        }
    }

    private val _searchHistory = MutableStateFlow<List<String>>(loadSearchHistory())
    val searchHistory: StateFlow<List<String>> = _searchHistory

    private fun loadSearchHistory(): List<String> {
        val list = loadList("search_history")
        if (list.isNotEmpty()) {
            return list.filter { !_recentlyUpdated.value.contains(it) }
        }
        val initial = _history.value.filter { !_recentlyUpdated.value.contains(it) }
        if (initial.isNotEmpty()) {
            saveList("search_history", initial)
            return initial
        }
        return emptyList()
    }

    fun removeFromSearchHistory(componentKey: String) {
        val pkg = componentKey.substringBefore('/')
        val currentSearch = _searchHistory.value.filterNot { it == componentKey || it == pkg || it.startsWith("$pkg/") }
        _searchHistory.value = currentSearch
        saveList("search_history", currentSearch)

        val recentSet = _recentlyUpdated.value
        val currentMain = _history.value.filterNot { key ->
            (key == componentKey || key == pkg || key.startsWith("$pkg/")) &&
                    !recentSet.contains(key) && !recentSet.contains(pkg)
        }
        _history.value = currentMain
        saveList("history", currentMain)
    }

    fun clearSearchHistory() {
        _searchHistory.value = emptyList()
        saveList("search_history", emptyList())

        val recentSet = _recentlyUpdated.value
        val preservedMainHistory = _history.value.filter { key ->
            recentSet.contains(key) || recentSet.contains(key.substringBefore('/'))
        }
        _history.value = preservedMainHistory
        saveList("history", preservedMainHistory)

        showToast(context.getString(R.string.toast_search_history_cleared))
    }

    fun logAppLaunch(componentKey: String) {
        val pkg = componentKey.substringBefore('/')
        val hasRecentlyUpdated = _recentlyUpdated.value.contains(componentKey) || _recentlyUpdated.value.contains(pkg)
        val updatedRecentlyUpdated = if (hasRecentlyUpdated) {
            val set = _recentlyUpdated.value.filter { key ->
                key != componentKey && key != pkg && !key.startsWith("$pkg/")
            }.toSet()
            _recentlyUpdated.value = set
            set
        } else null

        val (updatedHistory, updatedSearchHistory) = if (!_isHistoryPaused.value) {
            val current = _history.value.toMutableList()
            current.remove(componentKey)
            current.add(0, componentKey)
            val limited = current.take(15)
            _history.value = limited

            val currentSearch = _searchHistory.value.toMutableList()
            currentSearch.remove(componentKey)
            currentSearch.add(0, componentKey)
            val limitedSearch = currentSearch.take(20)
            _searchHistory.value = limitedSearch

            Pair(limited, limitedSearch)
        } else Pair(null, null)

        if (updatedRecentlyUpdated != null || updatedHistory != null || updatedSearchHistory != null) {
            storageExecutor.execute {
                try {
                    val editor = prefs.edit()
                    if (updatedRecentlyUpdated != null) {
                        val json = JSONArray()
                        updatedRecentlyUpdated.forEach { json.put(it) }
                        editor.putString("recently_updated_apps", json.toString())
                    }
                    if (updatedHistory != null) {
                        editor.putString("history", JSONArray(updatedHistory).toString())
                    }
                    if (updatedSearchHistory != null) {
                        editor.putString("search_history", JSONArray(updatedSearchHistory).toString())
                    }
                    editor.apply()
                } catch (_: Exception) {}
            }
        }
    }

    fun onAppInstalledOrUpdated(componentKey: String) {
        val updatedSet = _recentlyUpdated.value + componentKey
        _recentlyUpdated.value = updatedSet

        val updatedHistory = if (!_isHistoryPaused.value) {
            val current = _history.value.toMutableList()
            current.remove(componentKey)
            current.add(0, componentKey)
            val limited = current.take(15)
            _history.value = limited
            limited
        } else null

        storageExecutor.execute {
            try {
                val editor = prefs.edit()
                val json = JSONArray()
                updatedSet.forEach { json.put(it) }
                editor.putString("recently_updated_apps", json.toString())
                if (updatedHistory != null) {
                    editor.putString("history", JSONArray(updatedHistory).toString())
                }
                editor.apply()
            } catch (_: Exception) {}
        }
    }

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

    fun setInitialHistory(componentKeys: List<String>) {
        if (_history.value.isEmpty() && componentKeys.isNotEmpty()) {
            val limited = componentKeys.reversed().take(15)
            _history.value = limited
            saveList("history", limited)
        }
    }

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
        val label = componentKey.substringBefore('/').substringAfterLast('.').replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        showToast(context.getString(R.string.toast_removed_from_history, label))
    }

    fun clearHistory() {
        _searchHistory.value = emptyList()
        saveList("search_history", emptyList())
        _history.value = emptyList()
        saveList("history", emptyList())
        _recentlyUpdated.value = emptySet()
        saveRecentlyUpdated(emptySet())
        showToast(context.getString(R.string.toast_history_cleared))
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

    fun reorderTags(fromIndex: Int, toIndex: Int) {
        val current = _tags.value.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices && fromIndex != toIndex) {
            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)
            _tags.value = current
            saveTags(current)
        }
    }

    fun reorderTagById(fromTagId: String, toTagId: String) {
        val current = _tags.value.toMutableList()
        val fromIndex = current.indexOfFirst { it.id == fromTagId }
        val toIndex = current.indexOfFirst { it.id == toTagId }
        if (fromIndex != -1 && toIndex != -1 && fromIndex != toIndex) {
            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)
            _tags.value = current
            saveTags(current)
        }
    }

    fun sortTagsByName(ascending: Boolean = true) {
        val current = _tags.value.toMutableList()
        val sorted = if (ascending) {
            current.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        } else {
            current.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name })
        }
        _tags.value = sorted
        saveTags(sorted)
    }

    fun sortTagsByAppCount(ascending: Boolean = false) {
        val current = _tags.value.toMutableList()
        val tagCounts = mutableMapOf<String, Int>()
        _appTags.value.values.forEach { list ->
            list.forEach { tagId ->
                tagCounts[tagId] = (tagCounts[tagId] ?: 0) + 1
            }
        }
        val sorted = if (ascending) {
            current.sortedBy { tagCounts[it.id] ?: 0 }
        } else {
            current.sortedByDescending { tagCounts[it.id] ?: 0 }
        }
        _tags.value = sorted
        saveTags(sorted)
    }

    fun deleteTag(tagId: String) {
        val currentTags = _tags.value.filter { it.id != tagId }
        _tags.value = currentTags
        saveTags(currentTags)

        val tagKey = "tag:$tagId"
        if (_favorites.value.contains(tagKey)) {
            val currentFavorites = _favorites.value.filter { it != tagKey }
            _favorites.value = currentFavorites
            saveList("favorites", currentFavorites)
        }

        val currentAppTags = _appTags.value.toMutableMap()
        var appTagsChanged = false
        currentAppTags.forEach { (key, list) ->
            if (list.contains(tagId)) {
                currentAppTags[key] = list.filter { it != tagId }
                appTagsChanged = true
            }
        }
        if (appTagsChanged) {
            _appTags.value = currentAppTags
            saveAppTags(currentAppTags)
        }

        val currentOrders = _tagAppOrders.value.toMutableMap()
        if (currentOrders.remove(tagId) != null) {
            _tagAppOrders.value = currentOrders
            saveTagAppOrders(currentOrders)
        }
    }

    fun toggleTagForApp(componentKey: String, tagId: String) {
        val current = _appTags.value.toMutableMap()
        if (!componentKey.contains('#') && componentKey.contains('/')) {
            val pkg = componentKey.substringBefore('/')
            val pkgTags = current.remove(pkg)
            if (pkgTags != null) {
                val merged = LinkedHashSet<String>()
                current[componentKey]?.let { merged.addAll(it) }
                merged.addAll(pkgTags)
                if (merged.isNotEmpty()) {
                    current[componentKey] = merged.toList()
                }
            }
        }
        val list = current[componentKey]?.toMutableList() ?: mutableListOf()
        val wasAssigned = list.contains(tagId)
        if (wasAssigned) {
            list.remove(tagId)
        } else {
            list.add(tagId)
        }
        if (list.isEmpty()) {
            current.remove(componentKey)
        } else {
            current[componentKey] = list
        }
        _appTags.value = current
        saveAppTags(current)

        val orders = _tagAppOrders.value.toMutableMap()
        val tagOrder = orders[tagId]?.toMutableList()
        if (tagOrder != null) {
            if (wasAssigned) {
                tagOrder.remove(componentKey)
            } else if (!tagOrder.contains(componentKey)) {
                tagOrder.add(componentKey)
            }
            orders[tagId] = tagOrder
            _tagAppOrders.value = orders
            saveTagAppOrders(orders)
        }
    }

    fun reorderAppInTag(tagId: String, fromIndex: Int, toIndex: Int, currentApps: List<AppInfo>) {
        val currentKeys = currentApps.map { it.componentKey }.toMutableList()
        if (fromIndex in currentKeys.indices && toIndex in currentKeys.indices && fromIndex != toIndex) {
            val item = currentKeys.removeAt(fromIndex)
            currentKeys.add(toIndex, item)
            val currentMap = _tagAppOrders.value.toMutableMap()
            currentMap[tagId] = currentKeys
            _tagAppOrders.value = currentMap
            saveTagAppOrders(currentMap)
        }
    }

    fun updateTagAppOrder(tagId: String, orderedKeys: List<String>) {
        val currentMap = _tagAppOrders.value.toMutableMap()
        currentMap[tagId] = orderedKeys
        _tagAppOrders.value = currentMap
        saveTagAppOrders(currentMap)
    }

    /**
     * Migrates any persisted secondary profile keys from legacy `#hashCode` suffix to `#serialNumber`.
     */
    fun migrateProfileKeys(oldHashSuffix: String, newSerialSuffix: String) {
        if (oldHashSuffix == newSerialSuffix) return

        fun migrateKey(key: String): String {
            return if (key.endsWith(oldHashSuffix)) {
                key.removeSuffix(oldHashSuffix) + newSerialSuffix
            } else key
        }

        val favs = _favorites.value
        if (favs.any { it.endsWith(oldHashSuffix) }) {
            val updated = favs.map(::migrateKey).distinct()
            _favorites.value = updated
            saveList("favorites", updated)
        }

        val hist = _history.value
        if (hist.any { it.endsWith(oldHashSuffix) }) {
            val updated = hist.map(::migrateKey).distinct()
            _history.value = updated
            saveList("history", updated)
        }

        val searchHist = _searchHistory.value
        if (searchHist.any { it.endsWith(oldHashSuffix) }) {
            val updated = searchHist.map(::migrateKey).distinct()
            _searchHistory.value = updated
            saveList("search_history", updated)
        }

        val recent = _recentlyUpdated.value
        if (recent.any { it.endsWith(oldHashSuffix) }) {
            val updated = recent.map(::migrateKey).toSet()
            _recentlyUpdated.value = updated
            saveRecentlyUpdated(updated)
        }

        val labels = _customLabels.value
        if (labels.keys.any { it.endsWith(oldHashSuffix) }) {
            val updated = labels.entries.associate { (k, v) -> migrateKey(k) to v }
            _customLabels.value = updated
            saveMap("custom_labels", updated)
        }

        val tagsMap = _appTags.value
        if (tagsMap.keys.any { it.endsWith(oldHashSuffix) }) {
            val updated = tagsMap.entries.associate { (k, v) -> migrateKey(k) to v }
            _appTags.value = updated
            saveAppTags(updated)
        }

        val orders = _tagAppOrders.value
        if (orders.values.any { list -> list.any { it.endsWith(oldHashSuffix) } }) {
            val updated = orders.mapValues { (_, list) -> list.map(::migrateKey).distinct() }
            _tagAppOrders.value = updated
            saveTagAppOrders(updated)
        }
    }

    fun onPackageRemoved(packageName: String, user: UserHandle = Process.myUserHandle()) {
        val isPersonalProfile = user == Process.myUserHandle()
        val hashSuffix = "#${user.hashCode()}"
        val serialNumber = try {
            (context.getSystemService(Context.USER_SERVICE) as? UserManager)?.getSerialNumberForUser(user) ?: -1L
        } catch (_: Exception) {
            -1L
        }
        val serialSuffix = if (serialNumber >= 0L) "#$serialNumber" else hashSuffix

        fun matchesRemovedPackage(key: String): Boolean {
            return if (isPersonalProfile) {
                key == packageName || (key.startsWith("$packageName/") && !key.contains('#'))
            } else {
                key.startsWith("$packageName/") && (key.endsWith(serialSuffix) || key.endsWith(hashSuffix))
            }
        }

        val newRecent = _recentlyUpdated.value.filterNot(::matchesRemovedPackage).toSet()
        if (newRecent.size != _recentlyUpdated.value.size) {
            _recentlyUpdated.value = newRecent
            saveRecentlyUpdated(newRecent)
        }

        if (isPersonalProfile) {
            val updateTimes = loadAppUpdateTimes().toMutableMap()
            if (updateTimes.remove(packageName) != null) {
                saveAppUpdateTimes(updateTimes)
            }
        }

        val newFavs = _favorites.value.filterNot(::matchesRemovedPackage)
        if (newFavs.size != _favorites.value.size) {
            _favorites.value = newFavs
            saveList("favorites", newFavs)
        }

        val newHistory = _history.value.filterNot(::matchesRemovedPackage)
        if (newHistory.size != _history.value.size) {
            _history.value = newHistory
            saveList("history", newHistory)
        }

        val newSearchHistory = _searchHistory.value.filterNot(::matchesRemovedPackage)
        if (newSearchHistory.size != _searchHistory.value.size) {
            _searchHistory.value = newSearchHistory
            saveList("search_history", newSearchHistory)
        }

        val currentAppTags = _appTags.value.toMutableMap()
        val appTagKeysToRemove = currentAppTags.keys.filter(::matchesRemovedPackage)
        if (appTagKeysToRemove.isNotEmpty()) {
            appTagKeysToRemove.forEach { currentAppTags.remove(it) }
            _appTags.value = currentAppTags
            saveAppTags(currentAppTags)
        }

        val currentOrders = _tagAppOrders.value.toMutableMap()
        var ordersChanged = false
        currentOrders.forEach { (tagId, list) ->
            val filtered = list.filterNot(::matchesRemovedPackage)
            if (filtered.size != list.size) {
                currentOrders[tagId] = filtered
                ordersChanged = true
            }
        }
        if (ordersChanged) {
            _tagAppOrders.value = currentOrders
            saveTagAppOrders(currentOrders)
        }

        val currentLabels = _customLabels.value.toMutableMap()
        val labelKeysToRemove = currentLabels.keys.filter(::matchesRemovedPackage)
        if (labelKeysToRemove.isNotEmpty()) {
            labelKeysToRemove.forEach { currentLabels.remove(it) }
            _customLabels.value = currentLabels
            saveMap("custom_labels", currentLabels)
        }
    }

    /**
     * Loads the map of tracked package names and their last known update timestamps.
     */
    fun loadAppUpdateTimes(): Map<String, Long> {
        val raw = prefs.getString("app_last_update_times", null) ?: return emptyMap()
        return try {
            val json = JSONObject(raw)
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
        val snapshot = map.toMap()
        storageExecutor.execute {
            try {
                val json = JSONObject()
                snapshot.forEach { (k, v) -> json.put(k, v) }
                prefs.edit().putString("app_last_update_times", json.toString()).apply()
            } catch (_: Exception) {}
        }
    }

    private fun loadCustomCharMappings(): Map<String, Char> {
        val raw = prefs.getString("custom_char_mappings", null) ?: return emptyMap()
        return try {
            val json = JSONObject(raw)
            val map = mutableMapOf<String, Char>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = json.getString(k)
                if (v.isNotEmpty()) {
                    map[k] = v[0].uppercaseChar()
                }
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun saveCustomCharMappings(map: Map<String, Char>) {
        val snapshot = map.toMap()
        storageExecutor.execute {
            try {
                val json = JSONObject()
                snapshot.forEach { (k, v) -> json.put(k, v.toString()) }
                prefs.edit().putString("custom_char_mappings", json.toString()).apply()
            } catch (_: Exception) {}
        }
    }

    fun addOrUpdateCharMapping(symbol: String, targetChar: Char): Map<String, Char> {
        val trimmed = symbol.trim()
        if (trimmed.isEmpty()) return _customCharMappings.value
        val updated = _customCharMappings.value.toMutableMap()
        updated[trimmed] = targetChar.uppercaseChar()
        _customCharMappings.value = updated
        saveCustomCharMappings(updated)
        return updated
    }

    fun addCharMappings(mappings: Map<String, Char>): Map<String, Char> {
        if (mappings.isEmpty()) return _customCharMappings.value
        val updated = _customCharMappings.value.toMutableMap()
        mappings.forEach { (k, v) ->
            val trimmed = k.trim()
            if (trimmed.isNotEmpty()) {
                updated[trimmed] = v.uppercaseChar()
            }
        }
        _customCharMappings.value = updated
        saveCustomCharMappings(updated)
        return updated
    }

    fun removeCharMapping(symbol: String): Map<String, Char> {
        val updated = _customCharMappings.value.toMutableMap()
        updated.remove(symbol)
        _customCharMappings.value = updated
        saveCustomCharMappings(updated)
        return updated
    }

    fun resetCharMappings(): Map<String, Char> {
        _customCharMappings.value = emptyMap()
        storageExecutor.execute {
            prefs.edit().remove("custom_char_mappings").apply()
        }
        return emptyMap()
    }

    fun writeStringToUri(uri: Uri, content: String) {
        backupManager.writeStringToUri(uri, content)
    }

    fun readStringFromUri(uri: Uri, maxBytes: Long = 10 * 1024 * 1024L): String {
        return backupManager.readStringFromUri(uri, maxBytes)
    }

    fun exportCharMappingsToUri(uri: Uri) {
        backupManager.exportCharMappingsToUri(uri, _customCharMappings.value)
    }

    fun importCharMappingsFromUri(uri: Uri, merge: Boolean = true): Int {
        val parsed = backupManager.parseCharMappingsFromUri(uri)
        val result = if (merge) {
            val combined = _customCharMappings.value.toMutableMap()
            combined.putAll(parsed)
            combined
        } else {
            parsed
        }
        _customCharMappings.value = result
        saveCustomCharMappings(result)
        return parsed.size
    }

    fun exportAppNamesToUri(uri: Uri, apps: List<AppInfo>) {
        backupManager.exportAppNamesToUri(
            uri = uri,
            apps = apps,
            favorites = _favorites.value.toSet(),
            tags = _tags.value,
            appTags = _appTags.value
        )
    }

    fun exportAppNamesToUriAsText(uri: Uri, apps: List<AppInfo>) {
        backupManager.exportAppNamesToUriAsText(uri, apps)
    }

    fun importAppNamesFromUri(uri: Uri, currentApps: List<AppInfo>): LauncherBackupManager.AppNamesImportResult {
        return backupManager.importAppNamesFromUri(uri, currentApps)
    }

    fun importFavorites(favoritesToImport: List<String>) {
        if (favoritesToImport.isEmpty()) return
        val current = _favorites.value.toMutableList()
        favoritesToImport.forEach { favKey ->
            if (current.size >= 13) return@forEach
            if (!current.contains(favKey)) {
                current.add(favKey)
            }
        }
        val capped = current.distinct().take(13)
        _favorites.value = capped
        saveList("favorites", capped)
    }

    fun applyAppLabels(map: Map<String, String>) {
        val current = _customLabels.value.toMutableMap()
        current.putAll(map)
        _customLabels.value = current
        saveMap("custom_labels", current)
    }

    fun exportTagsBackupToUri(uri: Uri, apps: List<AppInfo> = emptyList()) {
        backupManager.exportTagsBackupToUri(
            uri = uri,
            tags = _tags.value,
            appTags = _appTags.value,
            customLabels = _customLabels.value,
            favorites = _favorites.value,
            tagAppOrders = _tagAppOrders.value,
            customCharMappings = _customCharMappings.value,
            apps = apps
        )
    }

    fun parseTagsBackup(uri: Uri): TagsBackupPreview {
        return backupManager.parseTagsBackup(uri, _tags.value)
    }

    /**
     * Applies a [TagsBackupPreview] by creating tags, updating assignments, labels, and favorites.
     */
    fun applyTagsBackup(preview: TagsBackupPreview, installedApps: List<AppInfo> = emptyList()) {
        val currentTags = _tags.value.toMutableList()
        val nameToId = currentTags.associate { it.name.lowercase().trim() to it.id }.toMutableMap()
        val oldIdToNewId = mutableMapOf<String, String>()

        currentTags.forEach { tag ->
            oldIdToNewId[tag.id] = tag.id
            oldIdToNewId[tag.name.lowercase().trim()] = tag.id
        }

        preview.newTags.forEach { info ->
            val lower = info.name.lowercase().trim()
            if (lower !in nameToId) {
                val newTag = Tag(
                    id = info.oldId?.ifEmpty { null } ?: UUID.randomUUID().toString(),
                    name = info.name,
                    color = info.color,
                    emoji = info.emoji
                )
                currentTags.add(newTag)
                nameToId[lower] = newTag.id
                if (!info.oldId.isNullOrBlank()) {
                    oldIdToNewId[info.oldId] = newTag.id
                }
            } else {
                val existingId = nameToId[lower]!!
                if (!info.oldId.isNullOrBlank()) {
                    oldIdToNewId[info.oldId] = existingId
                }
            }
        }
        _tags.value = currentTags
        saveTags(currentTags)

        val packageToApps = installedApps.groupBy { it.packageName }
        val componentMap = installedApps.associateBy { it.componentKey }
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

        if (preview.customLabels.isNotEmpty()) {
            applyAppLabels(preview.customLabels)
        }

        if (preview.favorites.isNotEmpty()) {
            val resolvedFavorites = mutableListOf<String>()
            preview.favorites.forEach { favKey ->
                if (favKey.startsWith("tag:")) {
                    val tagRef = favKey.removePrefix("tag:")
                    val targetTagId = oldIdToNewId[tagRef] ?: nameToId[tagRef.lowercase().trim()]
                    if (targetTagId != null) {
                        val newKey = "tag:$targetTagId"
                        if (!resolvedFavorites.contains(newKey)) {
                            resolvedFavorites.add(newKey)
                        }
                    }
                } else {
                    if (componentMap.containsKey(favKey)) {
                        if (!resolvedFavorites.contains(favKey)) {
                            resolvedFavorites.add(favKey)
                        }
                    } else if (packageToApps.containsKey(favKey)) {
                        val appKey = packageToApps[favKey]?.firstOrNull()?.componentKey ?: favKey
                        if (!resolvedFavorites.contains(appKey)) {
                            resolvedFavorites.add(appKey)
                        }
                    } else if (installedApps.isEmpty()) {
                        if (!resolvedFavorites.contains(favKey)) {
                            resolvedFavorites.add(favKey)
                        }
                    }
                }
            }
            val cappedFavorites = resolvedFavorites.distinct().take(13)
            if (cappedFavorites.isNotEmpty()) {
                _favorites.value = cappedFavorites
                saveList("favorites", cappedFavorites)
            }
        }

        if (preview.tagAppOrders.isNotEmpty()) {
            val currentOrders = _tagAppOrders.value.toMutableMap()
            preview.tagAppOrders.forEach { (tagName, orderList) ->
                val tagId = nameToId[tagName.lowercase().trim()]
                if (tagId != null && orderList.isNotEmpty()) {
                    currentOrders[tagId] = orderList
                }
            }
            _tagAppOrders.value = currentOrders
            saveTagAppOrders(currentOrders)
        }

        if (preview.customCharMappings.isNotEmpty()) {
            val current = _customCharMappings.value.toMutableMap()
            current.putAll(preview.customCharMappings)
            _customCharMappings.value = current
            saveCustomCharMappings(current)
        }

        if (preview.settings.isNotEmpty()) {
            val editor = prefs.edit()
            val existingPrefs = prefs.all
            preview.settings.forEach { (key, value) ->
                if (key == "pref_app_language" || key == "app_language") {
                    try {
                        LocaleUtils.setAppLanguage(context, value)
                    } catch (_: Exception) {}
                    return@forEach
                }
                if (key in LauncherBackupManager.INTERNAL_PREF_KEYS) {
                    return@forEach
                }
                if (key == "icon_pack_package" && !LauncherBackupManager.isValidPackageName(value)) {
                    return@forEach
                }
                val existingVal = existingPrefs[key]
                when {
                    existingVal is Boolean -> {
                        value.lowercase().toBooleanStrictOrNull()?.let { editor.putBoolean(key, it) }
                    }
                    existingVal is Int -> {
                        value.toIntOrNull()?.let { editor.putInt(key, it) }
                    }
                    existingVal is Long -> {
                        value.toLongOrNull()?.let { editor.putLong(key, it) }
                    }
                    existingVal is Float -> {
                        value.toFloatOrNull()?.takeIf { it.isFinite() }?.let { editor.putFloat(key, it) }
                    }
                    value.equals("true", ignoreCase = true) -> editor.putBoolean(key, true)
                    value.equals("false", ignoreCase = true) -> editor.putBoolean(key, false)
                    key.endsWith("_id") ||
                        key.endsWith("_height") ||
                        key.endsWith("_step") ||
                        key == "cached_colors_algo_version" -> {
                        value.toIntOrNull()?.let { editor.putInt(key, it) }
                    }
                    key.endsWith("_version") -> {
                        value.toLongOrNull()?.let { editor.putLong(key, it) }
                    }
                    key.endsWith("_alpha") ||
                        key.contains("ratio") ||
                        key.endsWith("_fraction") ||
                        key.endsWith("_offset_x") ||
                        key.endsWith("_offset_y") -> {
                        value.toFloatOrNull()?.takeIf { it.isFinite() }?.let { editor.putFloat(key, it) }
                    }
                    else -> {
                        editor.putString(key, value)
                    }
                }
            }
            editor.apply()
        }

        val created = preview.newTags.size
        val parts = mutableListOf<String>()
        if (preview.parsedAssignments.isNotEmpty()) {
            val assignmentsStr = context.getString(R.string.toast_backup_import_assignments, preview.parsedAssignments.size)
            val newTagsStr = if (created > 0) " " + context.getString(R.string.toast_backup_import_new_tags, created) else ""
            parts.add(assignmentsStr + newTagsStr)
        }
        if (preview.customLabels.isNotEmpty()) {
            parts.add(context.getString(R.string.toast_backup_import_labels, preview.customLabels.size))
        }
        if (preview.favorites.isNotEmpty()) {
            parts.add(context.getString(R.string.toast_backup_import_favorites, preview.favorites.size))
        }
        if (preview.settings.isNotEmpty()) {
            parts.add(context.getString(R.string.toast_backup_import_settings))
        }
        val msg = if (parts.isNotEmpty()) parts.joinToString(", ") else context.getString(R.string.toast_backup_imported_success)
        showToast(msg)
    }

    fun parseAutoTags(uri: Uri, apps: List<AppInfo>): AutoTagsPreview {
        return backupManager.parseAutoTags(uri, apps)
    }

    /**
     * Applies the matched auto-tags preview by creating tags and updating app assignments.
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

        showToast(context.getString(R.string.toast_applied_tags, preview.tags.size, preview.matchedAppsCount))
    }

    private fun saveList(key: String, list: List<String>) {
        val snapshot = list.toList()
        storageExecutor.execute {
            try {
                prefs.edit().putString(key, JSONArray(snapshot).toString()).apply()
            } catch (_: Exception) {}
        }
    }

    private fun loadList(key: String): List<String> {
        val json = prefs.getString(key, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            List(array.length()) { array.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveMap(key: String, map: Map<String, String>) {
        val snapshot = HashMap(map)
        storageExecutor.execute {
            try {
                val json = JSONObject(snapshot)
                prefs.edit().putString(key, json.toString()).apply()
            } catch (_: Exception) {}
        }
    }

    private fun loadMap(key: String): Map<String, String> {
        val jsonString = prefs.getString(key, null) ?: return emptyMap()
        return try {
            val json = JSONObject(jsonString)
            val map = mutableMapOf<String, String>()
            json.keys().forEach { k ->
                map[k] = json.getString(k)
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun saveTags(list: List<Tag>) {
        val snapshot = list.toList()
        storageExecutor.execute {
            try {
                val array = JSONArray()
                snapshot.forEach { array.put(it.toJsonObject()) }
                prefs.edit().putString("tags", array.toString()).apply()
            } catch (_: Exception) {}
        }
    }

    private fun loadTags(): List<Tag> {
        val json = prefs.getString("tags", null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            List(array.length()) { Tag.fromJsonObject(array.getJSONObject(it)) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveAppTags(map: Map<String, List<String>>) {
        val snapshot = map.mapValues { it.value.toList() }
        storageExecutor.execute {
            try {
                val json = JSONObject()
                snapshot.forEach { (k, v) -> json.put(k, JSONArray(v)) }
                prefs.edit().putString("app_tags", json.toString()).apply()
            } catch (_: Exception) {}
        }
    }

    private fun loadAppTags(): Map<String, List<String>> {
        val jsonString = prefs.getString("app_tags", null) ?: return emptyMap()
        return try {
            val json = JSONObject(jsonString)
            val map = mutableMapOf<String, List<String>>()
            json.keys().forEach { k ->
                val array = json.getJSONArray(k)
                map[k] = List(array.length()) { array.getString(it) }
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun saveTagAppOrders(map: Map<String, List<String>>) {
        val snapshot = map.mapValues { it.value.toList() }
        storageExecutor.execute {
            try {
                val json = JSONObject()
                snapshot.forEach { (k, v) -> json.put(k, JSONArray(v)) }
                prefs.edit().putString("tag_app_orders", json.toString()).apply()
            } catch (_: Exception) {}
        }
    }

    private fun loadTagAppOrders(): Map<String, List<String>> {
        val jsonString = prefs.getString("tag_app_orders", null) ?: return emptyMap()
        return try {
            val json = JSONObject(jsonString)
            val map = mutableMapOf<String, List<String>>()
            json.keys().forEach { k ->
                val array = json.getJSONArray(k)
                map[k] = List(array.length()) { array.getString(it) }
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
