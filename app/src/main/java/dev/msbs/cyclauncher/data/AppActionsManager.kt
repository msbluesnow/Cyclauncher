package dev.msbs.cyclauncher.data

import dev.msbs.cyclauncher.R
import dev.msbs.cyclauncher.model.AppInfo
import dev.msbs.cyclauncher.model.Tag
import dev.msbs.cyclauncher.utils.LocaleUtils
import dev.msbs.cyclauncher.utils.getSafeStorageContext
import dev.msbs.cyclauncher.utils.isUserUnlockedSafe

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
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

/**
 * Manages persisted user actions: favorites, launch history, custom labels, custom character mappings, and tags.
 * File I/O and backup/export/import parsing are delegated to [LauncherBackupManager].
 */
class AppActionsManager(context: Context) {
    private val context: Context = context.getSafeStorageContext()
    private val prefs: SharedPreferences = this.context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
    private val cachePrefs: SharedPreferences = this.context.getSharedPreferences("launcher_cache_prefs", Context.MODE_PRIVATE)
    private val backupManager = LauncherBackupManager(this.context, prefs)
    private val stateLock = Any()
    private val storageExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "AppActionsStorageThread").apply { isDaemon = true }
    }

    @Volatile
    private var isShutdown = false
    private var cachedAppUpdateTimes: Map<String, Long>? = null

    private fun enqueueStorageWrite(task: () -> Unit) {
        if (isShutdown || storageExecutor.isShutdown) return
        try {
            storageExecutor.execute {
                try {
                    task()
                } catch (_: Exception) {}
            }
        } catch (_: RejectedExecutionException) {
            // Executor already shut down during ViewModel teardown
        }
    }

    /**
     * Flushes all queued storage tasks and commits both launcher_prefs and launcher_cache_prefs
     * synchronously to disk (used before process relaunch so async writes are never lost).
     */
    fun flushPendingWrites() {
        if (isShutdown || storageExecutor.isShutdown) {
            try { prefs.edit().commit() } catch (_: Exception) {}
            try { cachePrefs.edit().commit() } catch (_: Exception) {}
            return
        }
        try {
            val future = storageExecutor.submit {
                try { prefs.edit().commit() } catch (_: Exception) {}
                try { cachePrefs.edit().commit() } catch (_: Exception) {}
            }
            future.get(2L, TimeUnit.SECONDS)
        } catch (_: Exception) {
            try { prefs.edit().commit() } catch (_: Exception) {}
            try { cachePrefs.edit().commit() } catch (_: Exception) {}
        }
    }

    /** Shuts down the background persistence executor when the owner ViewModel is cleared. */
    fun shutdown() {
        synchronized(stateLock) {
            isShutdown = true
            storageExecutor.shutdown()
        }
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
        val toastMsg: String = synchronized(stateLock) {
            var current = _favorites.value.toMutableList()
            val isTag = componentKey.startsWith("tag:")
            val label = if (isTag) {
                val tagId = componentKey.removePrefix("tag:")
                _tags.value.find { it.id == tagId }?.name ?: "Tag"
            } else {
                componentKey.substringBefore('/').substringAfterLast('.').replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }

            val msg = if (current.contains(componentKey)) {
                current.remove(componentKey)
                context.getString(R.string.toast_removed_from_favorites, label)
            } else {
                val existingTagIds = _tags.value.map { it.id }.toSet()
                current = current.filter { key ->
                    if (key.startsWith("tag:")) {
                        existingTagIds.contains(key.removePrefix("tag:"))
                    } else true
                }.toMutableList()

                if (current.size >= 13) {
                    return@synchronized context.getString(R.string.toast_favorites_limit)
                }
                current.add(componentKey)
                context.getString(R.string.toast_added_to_favorites, label)
            }
            val capped = current.distinct().take(13)
            _favorites.value = capped
            saveListLocked("favorites", capped)
            msg
        }
        showToast(toastMsg)
    }

    fun reorderFavorites(fromIndex: Int, toIndex: Int) {
        synchronized(stateLock) {
            val current = _favorites.value
            if (fromIndex in current.indices && toIndex in current.indices && fromIndex != toIndex) {
                val fromKey = current[fromIndex]
                val toKey = current[toIndex]
                reorderFavoritesByKeysLocked(fromKey, toKey)
            }
        }
    }

    fun reorderFavoritesByKeys(fromKey: String, toKey: String) {
        synchronized(stateLock) {
            reorderFavoritesByKeysLocked(fromKey, toKey)
        }
    }

    private fun reorderFavoritesByKeysLocked(fromKey: String, toKey: String) {
        val current = _favorites.value.toMutableList()
        val fromPos = current.indexOf(fromKey)
        val toPos = current.indexOf(toKey)
        if (fromPos != -1 && toPos != -1 && fromPos != toPos) {
            val item = current.removeAt(fromPos)
            current.add(toPos, item)
            _favorites.value = current
            saveListLocked("favorites", current)
        }
    }

    fun isFavorite(componentKey: String): Boolean {
        return _favorites.value.contains(componentKey)
    }

    private val _isHistoryPaused = MutableStateFlow<Boolean>(prefs.getBoolean("is_history_paused", false))
    val isHistoryPaused: StateFlow<Boolean> = _isHistoryPaused

    fun toggleHistoryPaused(): Boolean {
        val newVal = synchronized(stateLock) {
            val next = !_isHistoryPaused.value
            _isHistoryPaused.value = next
            enqueueStorageWrite {
                prefs.edit().putBoolean("is_history_paused", next).apply()
            }
            next
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

    private fun saveRecentlyUpdatedLocked(set: Set<String>) {
        val snapshot = set.toSet()
        enqueueStorageWrite {
            val json = JSONArray()
            snapshot.forEach { json.put(it) }
            prefs.edit().putString("recently_updated_apps", json.toString()).apply()
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
            if (context.isUserUnlockedSafe()) {
                saveListLocked("search_history", initial)
            }
            return initial
        }
        return emptyList()
    }

    /**
     * Reloads all persisted user action state from SharedPreferences (e.g. after Direct Boot CE->DE migration).
     */
    fun reloadFromPrefs() {
        synchronized(stateLock) {
            cachedAppUpdateTimes = null
            _favorites.value = loadList("favorites").take(13)
            _history.value = loadList("history")
            _customLabels.value = loadMap("custom_labels")
            _tags.value = loadTags()
            _appTags.value = loadAppTags()
            _tagAppOrders.value = loadTagAppOrders()
            _customCharMappings.value = loadCustomCharMappings()
            _isHistoryPaused.value = prefs.getBoolean("is_history_paused", false)
            _recentlyUpdated.value = loadRecentlyUpdated()
            _searchHistory.value = loadSearchHistory()
        }
    }

    fun removeFromSearchHistory(componentKey: String) {
        synchronized(stateLock) {
            val pkg = componentKey.substringBefore('/')
            val currentSearch = _searchHistory.value.filterNot { it == componentKey || it == pkg || it.startsWith("$pkg/") }
            _searchHistory.value = currentSearch
            saveListLocked("search_history", currentSearch)

            val recentSet = _recentlyUpdated.value
            val currentMain = _history.value.filterNot { key ->
                (key == componentKey || key == pkg || key.startsWith("$pkg/")) &&
                    !recentSet.contains(key) && !recentSet.contains(pkg)
            }
            _history.value = currentMain
            saveListLocked("history", currentMain)
        }
    }

    fun clearSearchHistory() {
        synchronized(stateLock) {
            _searchHistory.value = emptyList()
            saveListLocked("search_history", emptyList())

            val recentSet = _recentlyUpdated.value
            val preservedMainHistory = _history.value.filter { key ->
                recentSet.contains(key) || recentSet.contains(key.substringBefore('/'))
            }
            _history.value = preservedMainHistory
            saveListLocked("history", preservedMainHistory)
        }
        showToast(context.getString(R.string.toast_search_history_cleared))
    }

    fun logAppLaunch(componentKey: String) {
        synchronized(stateLock) {
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
                enqueueStorageWrite {
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
                }
            }
        }
    }

    fun onAppInstalledOrUpdated(componentKey: String) {
        synchronized(stateLock) {
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

            enqueueStorageWrite {
                val editor = prefs.edit()
                val json = JSONArray()
                updatedSet.forEach { json.put(it) }
                editor.putString("recently_updated_apps", json.toString())
                if (updatedHistory != null) {
                    editor.putString("history", JSONArray(updatedHistory).toString())
                }
                editor.apply()
            }
        }
    }

    fun onAppsInstalledOrUpdated(componentKeys: List<String>) {
        if (componentKeys.isEmpty()) return
        synchronized(stateLock) {
            val updatedSet = _recentlyUpdated.value + componentKeys
            _recentlyUpdated.value = updatedSet
            saveRecentlyUpdatedLocked(updatedSet)

            if (_isHistoryPaused.value) return
            val current = _history.value.toMutableList()
            for (key in componentKeys) {
                current.remove(key)
                current.add(0, key)
            }
            val limited = current.take(15)
            _history.value = limited
            saveListLocked("history", limited)
        }
    }

    fun setInitialHistory(componentKeys: List<String>) {
        synchronized(stateLock) {
            if (_history.value.isEmpty() && componentKeys.isNotEmpty()) {
                val limited = componentKeys.reversed().distinct().take(15)
                _history.value = limited
                saveListLocked("history", limited)
            }
        }
    }

    fun removeFromHistory(componentKey: String) {
        synchronized(stateLock) {
            val current = _history.value.toMutableList()
            current.remove(componentKey)
            _history.value = current
            saveListLocked("history", current)
            if (_recentlyUpdated.value.contains(componentKey)) {
                val updatedSet = _recentlyUpdated.value - componentKey
                _recentlyUpdated.value = updatedSet
                saveRecentlyUpdatedLocked(updatedSet)
            }
        }
        val label = componentKey.substringBefore('/').substringAfterLast('.').replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        showToast(context.getString(R.string.toast_removed_from_history, label))
    }

    fun clearHistory() {
        synchronized(stateLock) {
            _searchHistory.value = emptyList()
            saveListLocked("search_history", emptyList())
            _history.value = emptyList()
            saveListLocked("history", emptyList())
            _recentlyUpdated.value = emptySet()
            saveRecentlyUpdatedLocked(emptySet())
        }
        showToast(context.getString(R.string.toast_history_cleared))
    }

    fun renameApp(componentKey: String, newLabel: String) {
        synchronized(stateLock) {
            val current = _customLabels.value.toMutableMap()
            if (newLabel.isBlank()) {
                current.remove(componentKey)
            } else {
                current[componentKey] = newLabel
            }
            _customLabels.value = current
            saveMapLocked("custom_labels", current)
        }
    }

    fun createTag(tag: Tag) {
        synchronized(stateLock) {
            val current = _tags.value.toMutableList()
            val resolvedTag = if (current.any { it.id == tag.id }) {
                tag.copy(id = UUID.randomUUID().toString())
            } else {
                tag
            }
            current.add(resolvedTag)
            _tags.value = current
            saveTagsLocked(current)
        }
    }

    fun updateTag(updatedTag: Tag) {
        synchronized(stateLock) {
            val current = _tags.value.map { if (it.id == updatedTag.id) updatedTag else it }
            _tags.value = current
            saveTagsLocked(current)
        }
    }

    fun reorderTags(fromIndex: Int, toIndex: Int) {
        synchronized(stateLock) {
            val current = _tags.value.toMutableList()
            if (fromIndex in current.indices && toIndex in current.indices && fromIndex != toIndex) {
                val item = current.removeAt(fromIndex)
                current.add(toIndex, item)
                _tags.value = current
                saveTagsLocked(current)
            }
        }
    }

    fun reorderTagById(fromTagId: String, toTagId: String) {
        synchronized(stateLock) {
            val current = _tags.value.toMutableList()
            val fromIndex = current.indexOfFirst { it.id == fromTagId }
            val toIndex = current.indexOfFirst { it.id == toTagId }
            if (fromIndex != -1 && toIndex != -1 && fromIndex != toIndex) {
                val item = current.removeAt(fromIndex)
                current.add(toIndex, item)
                _tags.value = current
                saveTagsLocked(current)
            }
        }
    }

    fun sortTagsByName(ascending: Boolean = true) {
        synchronized(stateLock) {
            val current = _tags.value.toMutableList()
            val sorted = if (ascending) {
                current.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            } else {
                current.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name })
            }
            _tags.value = sorted
            saveTagsLocked(sorted)
        }
    }

    fun sortTagsByAppCount(ascending: Boolean = false) {
        synchronized(stateLock) {
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
            saveTagsLocked(sorted)
        }
    }

    fun deleteTag(tagId: String) {
        synchronized(stateLock) {
            val currentTags = _tags.value.filter { it.id != tagId }
            _tags.value = currentTags
            saveTagsLocked(currentTags)

            val tagKey = "tag:$tagId"
            if (_favorites.value.contains(tagKey)) {
                val currentFavorites = _favorites.value.filter { it != tagKey }
                _favorites.value = currentFavorites
                saveListLocked("favorites", currentFavorites)
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
                saveAppTagsLocked(currentAppTags)
            }

            val currentOrders = _tagAppOrders.value.toMutableMap()
            if (currentOrders.remove(tagId) != null) {
                _tagAppOrders.value = currentOrders
                saveTagAppOrdersLocked(currentOrders)
            }
        }
    }

    fun toggleTagForApp(componentKey: String, tagId: String) {
        synchronized(stateLock) {
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
            saveAppTagsLocked(current)

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
                saveTagAppOrdersLocked(orders)
            }
        }
    }

    fun reorderAppInTag(tagId: String, fromIndex: Int, toIndex: Int, currentApps: List<AppInfo>) {
        synchronized(stateLock) {
            val currentKeys = currentApps.map { it.componentKey }.toMutableList()
            if (fromIndex in currentKeys.indices && toIndex in currentKeys.indices && fromIndex != toIndex) {
                val item = currentKeys.removeAt(fromIndex)
                currentKeys.add(toIndex, item)
                val currentMap = _tagAppOrders.value.toMutableMap()
                currentMap[tagId] = currentKeys
                _tagAppOrders.value = currentMap
                saveTagAppOrdersLocked(currentMap)
            }
        }
    }

    fun updateTagAppOrder(tagId: String, orderedKeys: List<String>) {
        synchronized(stateLock) {
            val currentMap = _tagAppOrders.value.toMutableMap()
            currentMap[tagId] = orderedKeys
            _tagAppOrders.value = currentMap
            saveTagAppOrdersLocked(currentMap)
        }
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

        synchronized(stateLock) {
            val favs = _favorites.value
            if (favs.any { it.endsWith(oldHashSuffix) }) {
                val updated = favs.map(::migrateKey).distinct()
                _favorites.value = updated
                saveListLocked("favorites", updated)
            }

            val hist = _history.value
            if (hist.any { it.endsWith(oldHashSuffix) }) {
                val updated = hist.map(::migrateKey).distinct()
                _history.value = updated
                saveListLocked("history", updated)
            }

            val searchHist = _searchHistory.value
            if (searchHist.any { it.endsWith(oldHashSuffix) }) {
                val updated = searchHist.map(::migrateKey).distinct()
                _searchHistory.value = updated
                saveListLocked("search_history", updated)
            }

            val recent = _recentlyUpdated.value
            if (recent.any { it.endsWith(oldHashSuffix) }) {
                val updated = recent.map(::migrateKey).toSet()
                _recentlyUpdated.value = updated
                saveRecentlyUpdatedLocked(updated)
            }

            val labels = _customLabels.value
            if (labels.keys.any { it.endsWith(oldHashSuffix) }) {
                val updated = labels.entries.associate { (k, v) -> migrateKey(k) to v }
                _customLabels.value = updated
                saveMapLocked("custom_labels", updated)
            }

            val tagsMap = _appTags.value
            if (tagsMap.keys.any { it.endsWith(oldHashSuffix) }) {
                val updated = tagsMap.entries.associate { (k, v) -> migrateKey(k) to v }
                _appTags.value = updated
                saveAppTagsLocked(updated)
            }

            val orders = _tagAppOrders.value
            if (orders.values.any { list -> list.any { it.endsWith(oldHashSuffix) } }) {
                val updated = orders.mapValues { (_, list) -> list.map(::migrateKey).distinct() }
                _tagAppOrders.value = updated
                saveTagAppOrdersLocked(updated)
            }
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

        synchronized(stateLock) {
            val newRecent = _recentlyUpdated.value.filterNot(::matchesRemovedPackage).toSet()
            if (newRecent.size != _recentlyUpdated.value.size) {
                _recentlyUpdated.value = newRecent
                saveRecentlyUpdatedLocked(newRecent)
            }

            if (isPersonalProfile) {
                val updateTimes = loadAppUpdateTimesLocked().toMutableMap()
                if (updateTimes.remove(packageName) != null) {
                    saveAppUpdateTimesLocked(updateTimes)
                }
            }

            val newFavs = _favorites.value.filterNot(::matchesRemovedPackage)
            if (newFavs.size != _favorites.value.size) {
                _favorites.value = newFavs
                saveListLocked("favorites", newFavs)
            }

            val newHistory = _history.value.filterNot(::matchesRemovedPackage)
            if (newHistory.size != _history.value.size) {
                _history.value = newHistory
                saveListLocked("history", newHistory)
            }

            val newSearchHistory = _searchHistory.value.filterNot(::matchesRemovedPackage)
            if (newSearchHistory.size != _searchHistory.value.size) {
                _searchHistory.value = newSearchHistory
                saveListLocked("search_history", newSearchHistory)
            }

            val currentAppTags = _appTags.value.toMutableMap()
            val appTagKeysToRemove = currentAppTags.keys.filter(::matchesRemovedPackage)
            if (appTagKeysToRemove.isNotEmpty()) {
                appTagKeysToRemove.forEach { currentAppTags.remove(it) }
                _appTags.value = currentAppTags
                saveAppTagsLocked(currentAppTags)
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
                saveTagAppOrdersLocked(currentOrders)
            }

            val currentLabels = _customLabels.value.toMutableMap()
            val labelKeysToRemove = currentLabels.keys.filter(::matchesRemovedPackage)
            if (labelKeysToRemove.isNotEmpty()) {
                labelKeysToRemove.forEach { currentLabels.remove(it) }
                _customLabels.value = currentLabels
                saveMapLocked("custom_labels", currentLabels)
            }
        }
    }

    /**
     * Loads the map of tracked package names and their last known update timestamps.
     * Stored in dedicated `launcher_cache_prefs` with one-time migration from `launcher_prefs`.
     */
    fun loadAppUpdateTimes(): Map<String, Long> = synchronized(stateLock) {
        loadAppUpdateTimesLocked()
    }

    private fun loadAppUpdateTimesLocked(): Map<String, Long> {
        cachedAppUpdateTimes?.let { return it }
        val fromCache = cachePrefs.getString("app_last_update_times", null)
        val raw = if (fromCache != null) {
            fromCache
        } else {
            val legacyRaw = prefs.getString("app_last_update_times", null)
            if (legacyRaw != null && context.isUserUnlockedSafe()) {
                enqueueStorageWrite {
                    cachePrefs.edit().putString("app_last_update_times", legacyRaw).apply()
                    prefs.edit().remove("app_last_update_times").apply()
                }
            }
            legacyRaw
        } ?: return emptyMap<String, Long>().also { cachedAppUpdateTimes = it }

        val parsed = try {
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
        cachedAppUpdateTimes = parsed
        return parsed
    }

    /**
     * Saves the map of tracked package names and their last known update timestamps.
     */
    fun saveAppUpdateTimes(map: Map<String, Long>) {
        synchronized(stateLock) {
            saveAppUpdateTimesLocked(map)
        }
    }

    private fun saveAppUpdateTimesLocked(map: Map<String, Long>) {
        val snapshot = map.toMap()
        cachedAppUpdateTimes = snapshot
        enqueueStorageWrite {
            val json = JSONObject()
            snapshot.forEach { (k, v) -> json.put(k, v) }
            cachePrefs.edit().putString("app_last_update_times", json.toString()).apply()
            if (prefs.contains("app_last_update_times") && context.isUserUnlockedSafe()) {
                prefs.edit().remove("app_last_update_times").apply()
            }
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

    private fun saveCustomCharMappingsLocked(map: Map<String, Char>) {
        val snapshot = map.toMap()
        enqueueStorageWrite {
            val json = JSONObject()
            snapshot.forEach { (k, v) -> json.put(k, v.toString()) }
            prefs.edit().putString("custom_char_mappings", json.toString()).apply()
        }
    }

    fun addOrUpdateCharMapping(symbol: String, targetChar: Char): Map<String, Char> = synchronized(stateLock) {
        val trimmed = symbol.trim()
        if (trimmed.isEmpty()) return@synchronized _customCharMappings.value
        val updated = _customCharMappings.value.toMutableMap()
        updated[trimmed] = targetChar.uppercaseChar()
        _customCharMappings.value = updated
        saveCustomCharMappingsLocked(updated)
        updated
    }

    fun addCharMappings(mappings: Map<String, Char>): Map<String, Char> = synchronized(stateLock) {
        if (mappings.isEmpty()) return@synchronized _customCharMappings.value
        val updated = _customCharMappings.value.toMutableMap()
        mappings.forEach { (k, v) ->
            val trimmed = k.trim()
            if (trimmed.isNotEmpty()) {
                updated[trimmed] = v.uppercaseChar()
            }
        }
        _customCharMappings.value = updated
        saveCustomCharMappingsLocked(updated)
        updated
    }

    fun removeCharMapping(symbol: String): Map<String, Char> = synchronized(stateLock) {
        val updated = _customCharMappings.value.toMutableMap()
        updated.remove(symbol)
        _customCharMappings.value = updated
        saveCustomCharMappingsLocked(updated)
        updated
    }

    fun resetCharMappings(): Map<String, Char> = synchronized(stateLock) {
        _customCharMappings.value = emptyMap()
        enqueueStorageWrite {
            prefs.edit().remove("custom_char_mappings").apply()
        }
        emptyMap()
    }

    fun writeStringToUri(uri: Uri, content: String) {
        backupManager.writeStringToUri(uri, content)
    }

    fun readStringFromUri(uri: Uri, maxBytes: Long = LauncherBackupManager.DEFAULT_MAX_IMPORT_BYTES): String {
        return backupManager.readStringFromUri(uri, maxBytes)
    }

    fun exportCharMappingsToUri(uri: Uri) {
        val mappingsSnapshot = synchronized(stateLock) { _customCharMappings.value }
        backupManager.exportCharMappingsToUri(uri, mappingsSnapshot)
    }

    fun importCharMappingsFromUri(uri: Uri, merge: Boolean = true): Int {
        val parsed = backupManager.parseCharMappingsFromUri(uri)
        synchronized(stateLock) {
            val result = if (merge) {
                val combined = _customCharMappings.value.toMutableMap()
                combined.putAll(parsed)
                combined
            } else {
                parsed
            }
            _customCharMappings.value = result
            saveCustomCharMappingsLocked(result)
        }
        return parsed.size
    }

    fun exportAppNamesToUri(uri: Uri, apps: List<AppInfo>) {
        val (favs, tagsSnapshot, appTagsSnapshot) = synchronized(stateLock) {
            Triple(_favorites.value.toSet(), _tags.value, _appTags.value)
        }
        backupManager.exportAppNamesToUri(
            uri = uri,
            apps = apps,
            favorites = favs,
            tags = tagsSnapshot,
            appTags = appTagsSnapshot
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
        synchronized(stateLock) {
            val current = _favorites.value.toMutableList()
            favoritesToImport.forEach { favKey ->
                if (current.size >= 13) return@forEach
                if (!current.contains(favKey)) {
                    current.add(favKey)
                }
            }
            val capped = current.distinct().take(13)
            _favorites.value = capped
            saveListLocked("favorites", capped)
        }
    }

    fun applyAppLabels(map: Map<String, String>) {
        synchronized(stateLock) {
            applyAppLabelsLocked(map)
        }
    }

    private fun applyAppLabelsLocked(map: Map<String, String>) {
        val current = _customLabels.value.toMutableMap()
        current.putAll(map)
        _customLabels.value = current
        saveMapLocked("custom_labels", current)
    }

    private data class BackupExportSnapshot(
        val tags: List<Tag>,
        val appTags: Map<String, List<String>>,
        val customLabels: Map<String, String>,
        val favorites: List<String>,
        val tagAppOrders: Map<String, List<String>>,
        val customCharMappings: Map<String, Char>
    )

    fun exportTagsBackupToUri(uri: Uri, apps: List<AppInfo> = emptyList()) {
        val snapshot = synchronized(stateLock) {
            BackupExportSnapshot(
                tags = _tags.value,
                appTags = _appTags.value,
                customLabels = _customLabels.value,
                favorites = _favorites.value,
                tagAppOrders = _tagAppOrders.value,
                customCharMappings = _customCharMappings.value
            )
        }
        backupManager.exportTagsBackupToUri(
            uri = uri,
            tags = snapshot.tags,
            appTags = snapshot.appTags,
            customLabels = snapshot.customLabels,
            favorites = snapshot.favorites,
            tagAppOrders = snapshot.tagAppOrders,
            customCharMappings = snapshot.customCharMappings,
            apps = apps
        )
    }

    fun parseTagsBackup(uri: Uri): TagsBackupPreview {
        val tagsSnapshot = synchronized(stateLock) { _tags.value }
        return backupManager.parseTagsBackup(uri, tagsSnapshot)
    }

    /**
     * Applies a [TagsBackupPreview] by creating tags, updating assignments, labels, and favorites.
     */
    fun applyTagsBackup(preview: TagsBackupPreview, installedApps: List<AppInfo> = emptyList()) {
        var languageToApply: String? = null

        synchronized(stateLock) {
            val currentTags = _tags.value.toMutableList()
            val existingIds = currentTags.mapTo(HashSet()) { it.id }
            val nameToId = currentTags.associate { it.name.lowercase().trim() to it.id }.toMutableMap()
            val oldIdToNewId = mutableMapOf<String, String>()

            currentTags.forEach { tag ->
                oldIdToNewId[tag.id] = tag.id
                oldIdToNewId[tag.name.lowercase().trim()] = tag.id
            }

            preview.newTags.forEach { info ->
                val lower = info.name.lowercase().trim()
                if (lower !in nameToId) {
                    val candidateId = info.oldId?.trim()?.ifEmpty { null }
                    val resolvedId = if (candidateId != null && candidateId !in existingIds) {
                        candidateId
                    } else {
                        UUID.randomUUID().toString()
                    }
                    existingIds.add(resolvedId)
                    val newTag = Tag(
                        id = resolvedId,
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
            val dedupedTags = currentTags.distinctBy { it.id }
            _tags.value = dedupedTags
            saveTagsLocked(dedupedTags)

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
            saveAppTagsLocked(currentAppTags)

            if (preview.customLabels.isNotEmpty()) {
                applyAppLabelsLocked(preview.customLabels)
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
                    saveListLocked("favorites", cappedFavorites)
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
                saveTagAppOrdersLocked(currentOrders)
            }

            if (preview.customCharMappings.isNotEmpty()) {
                val current = _customCharMappings.value.toMutableMap()
                current.putAll(preview.customCharMappings)
                _customCharMappings.value = current
                saveCustomCharMappingsLocked(current)
            }

            if (preview.settings.isNotEmpty()) {
                val editor = prefs.edit()
                val existingPrefs = prefs.all
                preview.settings.forEach { (key, value) ->
                    if (key == "pref_app_language" || key == "app_language") {
                        languageToApply = value
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
                _isHistoryPaused.value = prefs.getBoolean("is_history_paused", false)
            }
        }

        languageToApply?.let { lang ->
            try {
                LocaleUtils.setAppLanguage(context, lang)
            } catch (_: Exception) {}
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
        synchronized(stateLock) {
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
                        color = autoTag.color,
                        emoji = autoTag.emoji
                    )
                    currentTags.add(newTag)
                    tagNameToId[lower] = newTag.id
                }
            }
            val dedupedTags = currentTags.distinctBy { it.id }
            _tags.value = dedupedTags
            saveTagsLocked(dedupedTags)

            val currentAppTags = _appTags.value.toMutableMap()

            preview.componentTagsMap.forEach { (componentKey, tagNames) ->
                val list = currentAppTags[componentKey]?.toMutableList() ?: mutableListOf()
                var changed = false
                tagNames.forEach { tagName ->
                    val tagId = tagNameToId[tagName.lowercase().trim()]
                    if (tagId != null && tagId !in list) {
                        list.add(tagId)
                        changed = true
                    }
                }
                if (changed) {
                    currentAppTags[componentKey] = list
                }
            }

            _appTags.value = currentAppTags
            saveAppTagsLocked(currentAppTags)
        }

        showToast(context.getString(R.string.toast_applied_tags, preview.tags.size, preview.matchedAppsCount))
    }

    private fun saveListLocked(key: String, list: List<String>) {
        val snapshot = list.toList()
        enqueueStorageWrite {
            prefs.edit().putString(key, JSONArray(snapshot).toString()).apply()
        }
    }

    private fun loadList(key: String): List<String> {
        val json = prefs.getString(key, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            List(array.length()) { array.getString(it) }.distinct()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveMapLocked(key: String, map: Map<String, String>) {
        val snapshot = HashMap(map)
        enqueueStorageWrite {
            val json = JSONObject(snapshot)
            prefs.edit().putString(key, json.toString()).apply()
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

    private fun saveTagsLocked(list: List<Tag>) {
        val snapshot = list.toList()
        enqueueStorageWrite {
            val array = JSONArray()
            snapshot.forEach { array.put(it.toJsonObject()) }
            prefs.edit().putString("tags", array.toString()).apply()
        }
    }

    private fun loadTags(): List<Tag> {
        val json = prefs.getString("tags", null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            List(array.length()) { Tag.fromJsonObject(array.getJSONObject(it)) }.distinctBy { it.id }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveAppTagsLocked(map: Map<String, List<String>>) {
        val snapshot = map.mapValues { it.value.toList() }
        enqueueStorageWrite {
            val json = JSONObject()
            snapshot.forEach { (k, v) -> json.put(k, JSONArray(v)) }
            prefs.edit().putString("app_tags", json.toString()).apply()
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

    private fun saveTagAppOrdersLocked(map: Map<String, List<String>>) {
        val snapshot = map.mapValues { it.value.toList() }
        enqueueStorageWrite {
            val json = JSONObject()
            snapshot.forEach { (k, v) -> json.put(k, JSONArray(v)) }
            prefs.edit().putString("tag_app_orders", json.toString()).apply()
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
