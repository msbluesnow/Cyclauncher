package dev.msbs.cyclauncher

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class HandSide { LEFT, RIGHT }

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val actionsManager = AppActionsManager(application)

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps

    private val _selectedLetter = MutableStateFlow('A')
    val selectedLetter: StateFlow<Char> = _selectedLetter

    private val _searchListAlignment = MutableStateFlow(TextAlign.Start)
    val searchListAlignment: StateFlow<TextAlign> = _searchListAlignment

    private val _handSide = MutableStateFlow(HandSide.LEFT)
    val handSide: StateFlow<HandSide> = _handSide

    private val _isTextSearchMode = MutableStateFlow(false)
    val isTextSearchMode: StateFlow<Boolean> = _isTextSearchMode

    private val _searchText = MutableStateFlow("")
    val searchText: StateFlow<String> = _searchText

    val filteredApps: StateFlow<List<AppInfo>> = combine(_apps, _selectedLetter) { apps, letter ->
        apps.filter { it.searchChar == letter }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val historyApps: StateFlow<List<AppInfo>> = combine(_apps, actionsManager.history) { all, ids ->
        ids.mapNotNull { id -> all.find { "${it.packageName}/${it.activityName}" == id } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteApps: StateFlow<List<AppInfo>> = combine(_apps, actionsManager.favorites) { all, ids ->
        ids.mapNotNull { id -> all.find { "${it.packageName}/${it.activityName}" == id } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val textFilteredApps: StateFlow<List<AppInfo>> = combine(_apps, _searchText) { apps, query ->
        if (query.isEmpty()) apps
        else apps.filter { it.label.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        val prefs = getApplication<Application>().getSharedPreferences("launcher_prefs", android.content.Context.MODE_PRIVATE)
        val savedHand = prefs.getString("hand_side", HandSide.LEFT.name) ?: HandSide.LEFT.name
        _handSide.value = try { HandSide.valueOf(savedHand) } catch (e: Exception) { HandSide.LEFT }
        
        loadInstalledApps()
        // Initialize alignment based on default handSide
        _searchListAlignment.value = if (_handSide.value == HandSide.LEFT) TextAlign.End else TextAlign.Start
    }

    fun setSelectedLetter(letter: Char) { _selectedLetter.value = letter }

    fun toggleTextSearchMode() {
        _isTextSearchMode.value = !_isTextSearchMode.value
        if (!_isTextSearchMode.value) _searchText.value = ""
    }

    fun setSearchText(text: String) { _searchText.value = text }

    fun setHandSide(side: HandSide) {
        _handSide.value = side
        _searchListAlignment.value = if (side == HandSide.LEFT) {
            TextAlign.End
        } else {
            TextAlign.Start
        }
        val prefs = getApplication<Application>().getSharedPreferences("launcher_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("hand_side", side.name).apply()
    }

    fun logAppLaunch(componentKey: String) {
        actionsManager.logAppLaunch(componentKey)
    }

    fun toggleFavorite(componentKey: String) {
        actionsManager.toggleFavorite(componentKey)
    }
    
    fun removeFromHistory(componentKey: String) {
        actionsManager.removeFromHistory(componentKey)
     }

    fun refreshApps() {
        loadInstalledApps()
    }

    fun uninstallApp(packageName: String) {
        try {
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.fromParts("package", packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            getApplication<Application>().startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(getApplication(), "Could not open uninstaller", Toast.LENGTH_SHORT).show()
        }
    }

    fun isFavorite(componentKey: String): Boolean = actionsManager.isFavorite(componentKey)

    private fun mapToSearchChar(char: Char): Char {
        val mapped = when (char.uppercaseChar()) {
            'А' -> 'A'; 'Б' -> 'B'; 'В' -> 'V'; 'Г' -> 'G'; 'Д' -> 'D'
            'Е', 'Ё', 'Э' -> 'E'; 'Ж' -> 'J'; 'З' -> 'Z'; 'И', 'Й', 'Ы' -> 'I'
            'К' -> 'K'; 'Л' -> 'L'; 'М' -> 'M'; 'Н' -> 'N'; 'О' -> 'O'
            'П' -> 'P'; 'Р' -> 'R'; 'С' -> 'S'; 'Т' -> 'T'; 'У' -> 'U'
            'Ф' -> 'F'; 'Х' -> 'H'; 'Ц' -> 'C'; 'Ч' -> 'C'; 'Ш', 'Щ' -> 'S'
            'Ю' -> 'U'; 'Я' -> 'Y'
            else -> char.uppercaseChar()
        }
        return if (mapped in 'A'..'Z') mapped else '#'
    }

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = getApplication<Application>().packageManager
            val defaultIcon = pm.defaultActivityIcon
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
            val resolvedInfos = pm.queryIntentActivities(mainIntent, 0)
            val appList = resolvedInfos.map { info ->
                async {
                    val label = info.loadLabel(pm).toString().trim().ifEmpty {
                        info.activityInfo.applicationInfo.loadLabel(pm).toString().trim().ifEmpty {
                            info.activityInfo.name.split(".").last().ifEmpty {
                                info.activityInfo.packageName
                            }
                        }
                    }
                    val firstChar = label.firstOrNull() ?: ' '
                    val iconBitmap = try {
                        val iconDrawable = info.loadIcon(pm) ?: defaultIcon
                        iconDrawable.toBitmap(width = 120, height = 120).asImageBitmap()
                    } catch (e: Exception) {
                        try {
                            defaultIcon.toBitmap(width = 120, height = 120).asImageBitmap()
                        } catch (ex: Exception) {
                            null
                        }
                    }
                    
                    AppInfo(
                        label = label,
                        packageName = info.activityInfo.packageName,
                        activityName = info.activityInfo.name,
                        icon = iconBitmap,
                        searchChar = mapToSearchChar(firstChar)
                    )
                }
            }.awaitAll().sortedBy { it.label.lowercase() }
            
            _apps.value = appList
            
            // Cleanup: remove uninstalled apps from actionsManager
            val validKeys = appList.map { "${it.packageName}/${it.activityName}" }.toSet()
            actionsManager.cleanupInvalidApps(validKeys)
        }
    }
}
