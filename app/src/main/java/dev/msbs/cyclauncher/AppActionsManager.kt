package dev.msbs.cyclauncher

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray

class AppActionsManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)

    private val _favorites = MutableStateFlow<List<String>>(loadList("favorites"))
    val favorites: StateFlow<List<String>> = _favorites

    private val _history = MutableStateFlow<List<String>>(loadList("history"))
    val history: StateFlow<List<String>> = _history

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
    }

    private fun saveList(key: String, list: List<String>) {
        prefs.edit().putString(key, JSONArray(list).toString()).apply()
    }

    private fun loadList(key: String): List<String> {
        val json = prefs.getString(key, null) ?: return emptyList()
        val array = JSONArray(json)
        return List(array.length()) { array.getString(it) }
    }
}
