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
    
    private val _selectedLetter = MutableStateFlow('A')
    val selectedLetter: StateFlow<Char> = _selectedLetter

    private val _searchListAlignment = MutableStateFlow(TextAlign.Start)
    val searchListAlignment: StateFlow<TextAlign> = _searchListAlignment

    private val _handSide = MutableStateFlow(HandSide.LEFT)
    val handSide: StateFlow<HandSide> = _handSide

    private val _accentColor = MutableStateFlow(AccentColor.CYAN)
    val accentColor: StateFlow<AccentColor> = _accentColor

    private val _showShadows = MutableStateFlow(true)
    val showShadows: StateFlow<Boolean> = _showShadows

    private val _isTextSearchMode = MutableStateFlow(false)
    val isTextSearchMode: StateFlow<Boolean> = _isTextSearchMode

    private val _searchText = MutableStateFlow("")
    val searchText: StateFlow<String> = _searchText

    val tags: StateFlow<List<Tag>> = actionsManager.tags
    val appTags: StateFlow<Map<String, List<String>>> = actionsManager.appTags

    val apps: StateFlow<List<AppInfo>> = combine(_apps, actionsManager.customLabels) { all, customLabels ->
        all.map { app ->
            val key = "${app.packageName}/${app.activityName}"
            val customLabel = customLabels[key]
            if (customLabel != null) {
                app.copy(label = customLabel, searchChar = mapToSearchChar(customLabel.firstOrNull() ?: ' '))
            } else {
                app
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredApps: StateFlow<List<AppInfo>> = combine(apps, _selectedLetter) { all, letter ->
        all.filter { it.searchChar == letter }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val historyApps: StateFlow<List<AppInfo>> = combine(apps, actionsManager.history) { all, ids ->
        ids.mapNotNull { id -> all.find { "${it.packageName}/${it.activityName}" == id } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteApps: StateFlow<List<AppInfo>> = combine(apps, actionsManager.favorites) { all, ids ->
        ids.mapNotNull { id -> all.find { "${it.packageName}/${it.activityName}" == id } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val textFilteredApps: StateFlow<List<AppInfo>> = combine(apps, _searchText) { all, query ->
        if (query.isEmpty()) all
        else all.filter { it.label.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        val prefs = getApplication<Application>().getSharedPreferences("launcher_prefs", android.content.Context.MODE_PRIVATE)
        val savedHand = prefs.getString("hand_side", HandSide.LEFT.name) ?: HandSide.LEFT.name
        _handSide.value = try { HandSide.valueOf(savedHand) } catch (e: Exception) { HandSide.LEFT }
        
        val savedColor = prefs.getString("accent_color", AccentColor.CYAN.name) ?: AccentColor.CYAN.name
        _accentColor.value = AccentColor.fromName(savedColor)
        
        if (!prefs.contains("show_shadows")) {
            _showShadows.value = true 
        } else {
            _showShadows.value = prefs.getBoolean("show_shadows", true)
        }

        loadInstalledApps()
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

    fun setAccentColor(color: AccentColor) {
        _accentColor.value = color
        val prefs = getApplication<Application>().getSharedPreferences("launcher_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("accent_color", color.name).apply()
    }

    fun setShowShadows(enabled: Boolean) {
        _showShadows.value = enabled
        val prefs = getApplication<Application>().getSharedPreferences("launcher_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putBoolean("show_shadows", enabled).apply()
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

    fun renameApp(componentKey: String, newLabel: String) {
        actionsManager.renameApp(componentKey, newLabel)
    }

    fun createTag(tag: Tag) = actionsManager.createTag(tag)
    fun updateTag(tag: Tag) = actionsManager.updateTag(tag)
    fun deleteTag(tagId: String) = actionsManager.deleteTag(tagId)
    fun toggleTagForApp(componentKey: String, tagId: String) = actionsManager.toggleTagForApp(componentKey, tagId)

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

    fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
        val resolveInfo = getApplication<Application>().packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName == getApplication<Application>().packageName
    }

    fun openDefaultLauncherSettings() {
        val intent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            Intent(android.provider.Settings.ACTION_HOME_SETTINGS)
        } else {
            Intent(android.provider.Settings.ACTION_SETTINGS)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        getApplication<Application>().startActivity(intent)
    }

    fun openSupportPage() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://web.tribute.tg/e/1dW")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        getApplication<Application>().startActivity(intent)
    }

    fun openGitHubPage() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/msbluesnow/Cyclauncher")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        getApplication<Application>().startActivity(intent)
    }

    fun openDiscordPage() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://discord.gg/9cnf49JnM")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        getApplication<Application>().startActivity(intent)
    }

    fun exportLabels() = actionsManager.exportCustomLabels()
    
    fun saveLabelsToUri(uri: Uri) = actionsManager.saveLabelsToUri(uri)
    
    fun importLabels(uri: Uri) = actionsManager.importCustomLabels(uri)

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
            
            val validKeys = appList.map { "${it.packageName}/${it.activityName}" }.toSet()
            actionsManager.cleanupInvalidApps(validKeys)
        }
    }
}
