package dev.msbs.cyclauncher

import dev.msbs.cyclauncher.data.AppActionsManager
import dev.msbs.cyclauncher.utils.getSafeStorageContext
import dev.msbs.cyclauncher.data.AutoTagsPreview
import dev.msbs.cyclauncher.data.TagsBackupPreview
import dev.msbs.cyclauncher.model.AppInfo
import dev.msbs.cyclauncher.model.FavoriteItem
import dev.msbs.cyclauncher.model.Tag
import dev.msbs.cyclauncher.ui.theme.AccentColor
import dev.msbs.cyclauncher.ui.theme.PopupTheme
import dev.msbs.cyclauncher.ui.theme.PrimaryTextColor

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Represents the user's preferred hand orientation for the launcher UI layout.
 */
enum class HandSide { LEFT, RIGHT }

/**
 * Represents the application search layout/method preference.
 */
enum class SearchMethod { WHEEL, SIDE_ALPHABET, TEXT }

/**
 * Main ViewModel for the launcher, exposing state, settings, search, and app actions.
 */
class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val safeContext = application.getSafeStorageContext()
    private val actionsManager = AppActionsManager(safeContext)

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    
    private val _selectedLetter = MutableStateFlow('A')
    val selectedLetter: StateFlow<Char> = _selectedLetter

    private val _searchListAlignment = MutableStateFlow(TextAlign.Start)
    val searchListAlignment: StateFlow<TextAlign> = _searchListAlignment

    private val _handSide = MutableStateFlow(HandSide.LEFT)
    val handSide: StateFlow<HandSide> = _handSide

    private val _accentColor = MutableStateFlow(AccentColor.SKY)
    val accentColor: StateFlow<AccentColor> = _accentColor

    private val _primaryTextColor = MutableStateFlow(PrimaryTextColor.WHITE)
    val primaryTextColor: StateFlow<PrimaryTextColor> = _primaryTextColor

    private val _buttonTextColor = MutableStateFlow(PrimaryTextColor.BLACK)
    val buttonTextColor: StateFlow<PrimaryTextColor> = _buttonTextColor

    private val _popupTheme = MutableStateFlow(PopupTheme.DARK)
    val popupTheme: StateFlow<PopupTheme> = _popupTheme

    private val _showShadows = MutableStateFlow(true)
    val showShadows: StateFlow<Boolean> = _showShadows

    private val _shadowColor = MutableStateFlow(PrimaryTextColor.BLACK)
    val shadowColor: StateFlow<PrimaryTextColor> = _shadowColor

    private val _hideStatusBar = MutableStateFlow(false)
    val hideStatusBar: StateFlow<Boolean> = _hideStatusBar

    private val _animationsEnabled = MutableStateFlow(true)
    val animationsEnabled: StateFlow<Boolean> = _animationsEnabled

    private val _isWallpaperDark = MutableStateFlow(AccentColor.isWallpaperDark(safeContext))
    val isWallpaperDark: StateFlow<Boolean> = _isWallpaperDark

    private val _searchMethod = MutableStateFlow(SearchMethod.SIDE_ALPHABET)
    val searchMethod: StateFlow<SearchMethod> = _searchMethod

    private var lastAlphabetSearchMethod: SearchMethod = SearchMethod.SIDE_ALPHABET

    private val _sideAlphabetButtonYRatio = MutableStateFlow(0.23f)
    val sideAlphabetButtonYRatio: StateFlow<Float> = _sideAlphabetButtonYRatio

    private val _isTextSearchMode = MutableStateFlow(false)
    val isTextSearchMode: StateFlow<Boolean> = _isTextSearchMode

    private val _searchText = MutableStateFlow("")
    val searchText: StateFlow<String> = _searchText

    val tags: StateFlow<List<Tag>> = actionsManager.tags
    val appTags: StateFlow<Map<String, List<String>>> = actionsManager.appTags

    private val _autoTagsPreview = MutableStateFlow<AutoTagsPreview?>(null)
    val autoTagsPreview: StateFlow<AutoTagsPreview?> = _autoTagsPreview

    private val _resetRequest = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val resetRequest = _resetRequest.asSharedFlow()

    private val _historyScrollToBottomTrigger = MutableStateFlow(0L)
    val historyScrollToBottomTrigger: StateFlow<Long> = _historyScrollToBottomTrigger

    fun requestReset() {
        if (_isTextSearchMode.value) {
            _isTextSearchMode.value = false
            _searchText.value = ""
            _searchMethod.value = lastAlphabetSearchMethod
        }
        _historyScrollToBottomTrigger.value = System.currentTimeMillis()
        _resetRequest.tryEmit(Unit)
    }

    fun requestHistoryScrollToBottom() {
        _historyScrollToBottomTrigger.value = System.currentTimeMillis()
    }

    val apps: StateFlow<List<AppInfo>> = combine(_apps, actionsManager.customLabels, actionsManager.customCharMappings) { all, customLabels, customMappings ->
        all.map { app ->
            val customLabel = customLabels[app.componentKey]
            val effectiveLabel = customLabel ?: app.label
            val symbol = extractFirstSymbol(effectiveLabel)
            val searchChar = mapToSearchChar(symbol, customMappings)
            if (customLabel != null || app.searchChar != searchChar) {
                app.copy(label = effectiveLabel, searchChar = searchChar)
            } else {
                app
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val filteredApps: StateFlow<List<AppInfo>> = combine(apps, _selectedLetter) { all, letter ->
        all.filter { it.searchChar == letter }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favorites: StateFlow<List<String>> = actionsManager.favorites

    val historyApps: StateFlow<List<AppInfo>> = combine(apps, actionsManager.history) { all, ids ->
        val appMap = all.associateBy { it.componentKey }
        ids.mapNotNull { id -> appMap[id] }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val recentlyUpdatedApps: StateFlow<Set<String>> = actionsManager.recentlyUpdated

    val favoriteItems: StateFlow<List<FavoriteItem>> = combine(
        apps,
        tags,
        appTags,
        actionsManager.favorites
    ) { allApps, allTags, allAppTags, ids ->
        val appMap = allApps.associateBy { it.componentKey }
        val tagMap = allTags.associateBy { it.id }
        val tagToAppsMap = mutableMapOf<String, MutableList<AppInfo>>()
        allApps.forEach { app ->
            val tagIds = allAppTags[app.componentKey] ?: allAppTags[app.packageName] ?: emptyList()
            tagIds.forEach { tagId ->
                tagToAppsMap.getOrPut(tagId) { mutableListOf() }.add(app)
            }
        }
        ids.mapNotNull { id ->
            if (id.startsWith("tag:")) {
                val tagId = id.removePrefix("tag:")
                val tag = tagMap[tagId] ?: return@mapNotNull null
                val taggedApps = tagToAppsMap[tag.id] ?: emptyList()
                FavoriteItem.TagFolder(tag, taggedApps)
            } else {
                val app = appMap[id] ?: return@mapNotNull null
                FavoriteItem.App(app)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favoriteApps: StateFlow<List<AppInfo>> = favoriteItems.map { items ->
        items.mapNotNull { (it as? FavoriteItem.App)?.appInfo }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val textFilteredApps: StateFlow<List<AppInfo>> = combine(apps, _searchText) { all, query ->
        if (query.isEmpty()) all
        else all.filter { it.label.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _showTutorial = MutableStateFlow(false)
    val showTutorial: StateFlow<Boolean> = _showTutorial

    private val _tutorialStep = MutableStateFlow(0)
    val tutorialStep: StateFlow<Int> = _tutorialStep

    init {
        val prefs = safeContext.getSharedPreferences("launcher_prefs", android.content.Context.MODE_PRIVATE)
        val savedHand = prefs.getString("hand_side", HandSide.LEFT.name) ?: HandSide.LEFT.name
        _handSide.value = try { HandSide.valueOf(savedHand) } catch (e: Exception) { HandSide.LEFT }
        
        val savedColor = prefs.getString("accent_color", AccentColor.SKY.name) ?: AccentColor.SKY.name
        _accentColor.value = AccentColor.fromName(savedColor, safeContext)
        
        val savedTextColor = prefs.getString("primary_text_color", PrimaryTextColor.WHITE.name) ?: PrimaryTextColor.WHITE.name
        _primaryTextColor.value = PrimaryTextColor.fromName(savedTextColor)

        val savedButtonTextColor = prefs.getString("button_text_color", PrimaryTextColor.BLACK.name) ?: PrimaryTextColor.BLACK.name
        _buttonTextColor.value = PrimaryTextColor.fromName(savedButtonTextColor)

        val savedPopupTheme = prefs.getString("popup_theme", PopupTheme.DARK.name) ?: PopupTheme.DARK.name
        _popupTheme.value = PopupTheme.fromName(savedPopupTheme)
        
        if (!prefs.contains("show_shadows")) {
            _showShadows.value = true 
        } else {
            _showShadows.value = prefs.getBoolean("show_shadows", true)
        }

        val savedShadowColor = prefs.getString("shadow_color", PrimaryTextColor.BLACK.name) ?: PrimaryTextColor.BLACK.name
        _shadowColor.value = PrimaryTextColor.fromName(savedShadowColor)

        _hideStatusBar.value = prefs.getBoolean("hide_status_bar", false)
        _animationsEnabled.value = prefs.getBoolean("animations_enabled", true)

        val savedSearchMethod = prefs.getString("search_method", SearchMethod.SIDE_ALPHABET.name) ?: SearchMethod.SIDE_ALPHABET.name
        val initialMethod = try { SearchMethod.valueOf(savedSearchMethod) } catch (e: Exception) { SearchMethod.SIDE_ALPHABET }
        _searchMethod.value = initialMethod
        _isTextSearchMode.value = (initialMethod == SearchMethod.TEXT)

        val savedLastAlphabet = prefs.getString("last_alphabet_search_method", SearchMethod.SIDE_ALPHABET.name) ?: SearchMethod.SIDE_ALPHABET.name
        val initialLastAlphabet = try { SearchMethod.valueOf(savedLastAlphabet) } catch (e: Exception) { SearchMethod.SIDE_ALPHABET }
        lastAlphabetSearchMethod = if (initialLastAlphabet == SearchMethod.TEXT) SearchMethod.SIDE_ALPHABET else initialLastAlphabet

        _sideAlphabetButtonYRatio.value = prefs.getFloat("side_alphabet_button_y_ratio", 0.23f).coerceIn(0.05f, 0.85f)

        val isTutorialCompleted = prefs.getBoolean("is_tutorial_completed", false)
        if (!isTutorialCompleted) {
            _showTutorial.value = true
            _tutorialStep.value = 0
        }

        loadInstalledApps()
        _searchListAlignment.value = if (_handSide.value == HandSide.LEFT) TextAlign.End else TextAlign.Start
    }

    fun startTutorial() {
        _tutorialStep.value = 0
        _showTutorial.value = true
    }

    fun setTutorialStep(step: Int) {
        _tutorialStep.value = step.coerceIn(0, 5)
    }

    fun nextTutorialStep() {
        if (_tutorialStep.value < 5) {
            _tutorialStep.value += 1
        } else {
            completeTutorial()
        }
    }

    fun completeTutorial() {
        _showTutorial.value = false
        _tutorialStep.value = 0
        val prefs = safeContext.getSharedPreferences("launcher_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putBoolean("is_tutorial_completed", true).apply()
    }

    fun setSelectedLetter(letter: Char) { _selectedLetter.value = letter }

    fun setSideAlphabetButtonYRatio(ratio: Float) {
        val clamped = ratio.coerceIn(0.05f, 0.85f)
        _sideAlphabetButtonYRatio.value = clamped
        val prefs = safeContext.getSharedPreferences("launcher_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putFloat("side_alphabet_button_y_ratio", clamped).apply()
    }

    fun setSearchMethod(method: SearchMethod) {
        _searchMethod.value = method
        if (method != SearchMethod.TEXT) {
            lastAlphabetSearchMethod = method
        }
        _isTextSearchMode.value = (method == SearchMethod.TEXT)
        if (method != SearchMethod.TEXT) {
            _searchText.value = ""
        }
        val prefs = safeContext.getSharedPreferences("launcher_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit()
            .putString("search_method", method.name)
            .putString("last_alphabet_search_method", lastAlphabetSearchMethod.name)
            .apply()
    }

    fun toggleTextSearchMode() {
        val nextMethod = if (_searchMethod.value == SearchMethod.TEXT) {
            lastAlphabetSearchMethod
        } else {
            SearchMethod.TEXT
        }
        setSearchMethod(nextMethod)
    }

    fun setSearchText(text: String) { _searchText.value = text }

    fun setHandSide(side: HandSide) {
        _handSide.value = side
        _searchListAlignment.value = if (side == HandSide.LEFT) {
            TextAlign.End
        } else {
            TextAlign.Start
        }
        val prefs = safeContext.getSharedPreferences("launcher_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("hand_side", side.name).apply()
    }

    fun setHideStatusBar(hide: Boolean) {
        _hideStatusBar.value = hide
        val prefs = safeContext.getSharedPreferences("launcher_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putBoolean("hide_status_bar", hide).apply()
    }

    fun setAnimationsEnabled(enabled: Boolean) {
        _animationsEnabled.value = enabled
        val prefs = safeContext.getSharedPreferences("launcher_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putBoolean("animations_enabled", enabled).apply()
    }

    fun setAccentColor(color: AccentColor) {
        _accentColor.value = color
        val prefs = safeContext.getSharedPreferences("launcher_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("accent_color", color.name).apply()
    }

    fun refreshDynamicWallpaperColor(context: Context) {
        val currentDark = AccentColor.isWallpaperDark(context)
        if (_isWallpaperDark.value != currentDark) {
            _isWallpaperDark.value = currentDark
        }
        if (_accentColor.value.isDynamicWallpaper) {
            val updated = AccentColor.wallpaper(context)
            if (_accentColor.value.color != updated.color) {
                _accentColor.value = updated
            }
        }
    }

    fun setPrimaryTextColor(color: PrimaryTextColor) {
        _primaryTextColor.value = color
        val prefs = safeContext.getSharedPreferences("launcher_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("primary_text_color", color.name).apply()
    }

    fun setButtonTextColor(color: PrimaryTextColor) {
        _buttonTextColor.value = color
        val prefs = safeContext.getSharedPreferences("launcher_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("button_text_color", color.name).apply()
    }

    fun setPopupTheme(theme: PopupTheme) {
        _popupTheme.value = theme
        val prefs = safeContext.getSharedPreferences("launcher_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("popup_theme", theme.name).apply()
    }

    fun setShowShadows(enabled: Boolean) {
        _showShadows.value = enabled
        val prefs = safeContext.getSharedPreferences("launcher_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putBoolean("show_shadows", enabled).apply()
    }

    fun setShadowColor(color: PrimaryTextColor) {
        _shadowColor.value = color
        val prefs = safeContext.getSharedPreferences("launcher_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("shadow_color", color.name).apply()
    }

    fun logAppLaunch(componentKey: String) {
        actionsManager.logAppLaunch(componentKey)
    }

    fun toggleFavorite(componentKey: String) {
        actionsManager.toggleFavorite(componentKey)
    }

    fun reorderFavorites(fromIndex: Int, toIndex: Int) {
        actionsManager.reorderFavorites(fromIndex, toIndex)
    }
    
    val isHistoryPaused: StateFlow<Boolean> = actionsManager.isHistoryPaused

    fun toggleHistoryPaused() {
        actionsManager.toggleHistoryPaused()
    }

    fun clearHistory() {
        actionsManager.clearHistory()
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
        val context = getApplication<Application>()
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
        val resolveInfo = context.packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        if (resolveInfo?.activityInfo?.packageName == context.packageName) {
            return true
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(android.app.role.RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)) {
                if (roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)) {
                    return true
                }
            }
        }
        return false
    }

    fun getDefaultLauncherPackage(): String? {
        val context = getApplication<Application>()
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
        val resolveInfo = context.packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        val pkg = resolveInfo?.activityInfo?.packageName
        return if (pkg != context.packageName) pkg else null
    }

    fun exitToSystemHome(context: Context) {
        val defaultPkg = getDefaultLauncherPackage()
        if (defaultPkg != null) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(defaultPkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(launchIntent)
                    return
                } catch (e: Exception) {
                }
            }
        }

        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(homeIntent)
            return
        } catch (e: Exception) {
        }

        val activity = context as? android.app.Activity
        if (activity != null) {
            if (!activity.moveTaskToBack(true)) {
                activity.finish()
            }
        }
    }

    fun openDefaultLauncherSettings(context: Context) {
        val activity = context as? android.app.Activity

        val homeSettingsIntent = Intent(android.provider.Settings.ACTION_HOME_SETTINGS)
        if (activity == null) {
            homeSettingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            if (activity != null) {
                activity.startActivity(homeSettingsIntent)
            } else {
                context.startActivity(homeSettingsIntent)
            }
            return
        } catch (e: Exception) {
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            try {
                val roleManager = context.getSystemService(android.app.role.RoleManager::class.java)
                if (roleManager != null && roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)) {
                    val roleIntent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME)
                    if (activity == null) {
                        roleIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (activity != null) {
                        activity.startActivity(roleIntent)
                    } else {
                        context.startActivity(roleIntent)
                    }
                    return
                }
            } catch (e: Exception) {
            }
        }

        val fallbackIntent = Intent(android.provider.Settings.ACTION_SETTINGS)
        if (activity == null) {
            fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            if (activity != null) {
                activity.startActivity(fallbackIntent)
            } else {
                context.startActivity(fallbackIntent)
            }
        } catch (e: Exception) {
        }
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
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://discord.gg/Zw4EBe92Qn")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        getApplication<Application>().startActivity(intent)
    }

    fun openKeepAndroidOpenPage() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://keepandroidopen.org/")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        getApplication<Application>().startActivity(intent)
    }

    fun exportAppNamesJson(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val list = apps.value
                actionsManager.exportAppNamesToUri(uri, list)
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Exported ${list.size} apps", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun exportAppNamesText(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val list = apps.value
                actionsManager.exportAppNamesToUriAsText(uri, list)
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Exported ${list.size} apps", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun importAppNamesPreview(uri: Uri, onResult: (labelCount: Int, favCount: Int) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = actionsManager.importAppNamesFromUri(uri, apps.value)
                if (result.labels.isNotEmpty()) {
                    actionsManager.applyAppLabels(result.labels)
                }
                if (result.favorites.isNotEmpty()) {
                    actionsManager.importFavorites(result.favorites)
                }
                withContext(Dispatchers.Main) {
                    onResult(result.labels.size, result.favorites.size)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Import failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun loadAutoTagsPreview(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val preview = actionsManager.parseAutoTags(uri, apps.value)
                withContext(Dispatchers.Main) {
                    _autoTagsPreview.value = preview
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Failed to parse tags: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun applyAutoTags() {
        _autoTagsPreview.value?.let { preview ->
            actionsManager.applyAutoTags(preview)
            _autoTagsPreview.value = null
        }
    }

    fun dismissAutoTagsPreview() {
        _autoTagsPreview.value = null
    }

    private val _tagsBackupPreview = MutableStateFlow<TagsBackupPreview?>(null)
    val tagsBackupPreview: StateFlow<TagsBackupPreview?> = _tagsBackupPreview

    fun exportTagsBackup(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val list = apps.value
                actionsManager.exportTagsBackupToUri(uri, list)
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Backup exported (${tags.value.size} tags, ${list.size} apps)", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun loadTagsBackupPreview(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val preview = actionsManager.parseTagsBackup(uri)
                withContext(Dispatchers.Main) {
                    _tagsBackupPreview.value = preview
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Failed to parse tags file: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun applyTagsBackup() {
        _tagsBackupPreview.value?.let { preview ->
            actionsManager.applyTagsBackup(preview, apps.value)
            _tagsBackupPreview.value = null
        }
    }

    fun dismissTagsBackupPreview() {
        _tagsBackupPreview.value = null
    }

    val customCharMappings: StateFlow<Map<String, Char>> = actionsManager.customCharMappings

    fun addOrUpdateCharMapping(symbol: String, targetChar: Char) {
        val updated = actionsManager.addOrUpdateCharMapping(symbol, targetChar)
        reindexApps(updated)
    }

    fun addCharMappings(mappings: Map<String, Char>) {
        val updated = actionsManager.addCharMappings(mappings)
        reindexApps(updated)
    }

    fun removeCharMapping(symbol: String) {
        val updated = actionsManager.removeCharMapping(symbol)
        reindexApps(updated)
    }

    fun resetCharMappings() {
        val updated = actionsManager.resetCharMappings()
        reindexApps(updated)
    }

    fun exportCharMappingsJson(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val count = customCharMappings.value.size
                actionsManager.exportCharMappingsToUri(uri)
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Exported $count mappings", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun importCharMappingsJson(uri: Uri, merge: Boolean = true, onResult: (Result<Int>) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val count = actionsManager.importCharMappingsFromUri(uri, merge)
                withContext(Dispatchers.Main) {
                    reindexApps(actionsManager.customCharMappings.value)
                    onResult(Result.success(count))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(Result.failure(e))
                }
            }
        }
    }

    private fun reindexApps(customMappings: Map<String, Char> = actionsManager.customCharMappings.value) {
        val currentApps = _apps.value
        if (currentApps.isEmpty()) return
        val reindexed = currentApps.map { app ->
            val firstSymbol = extractFirstSymbol(app.label)
            val newSearchChar = mapToSearchChar(firstSymbol, customMappings)
            if (app.searchChar != newSearchChar) {
                app.copy(searchChar = newSearchChar)
            } else {
                app
            }
        }
        _apps.value = reindexed
    }

    /**
     * Extracts the first symbol or character from a string using BreakIterator.
     */
    fun extractFirstSymbol(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ""
        val iterator = java.text.BreakIterator.getCharacterInstance()
        iterator.setText(trimmed)
        val end = iterator.next()
        return if (end != java.text.BreakIterator.DONE) trimmed.substring(0, end) else trimmed.take(1)
    }

    /**
     * Maps a symbol to an alphabet index ('A'..'Z' or '#'), checking custom mappings first.
     */
    fun mapToSearchChar(symbol: String, customMappings: Map<String, Char> = actionsManager.customCharMappings.value): Char {
        if (symbol.isEmpty()) return '#'

        customMappings[symbol]?.let { return it }
        customMappings[symbol.uppercase()]?.let { return it }

        if (symbol.length == 1) {
            val char = symbol[0]
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

        return '#'
    }

    fun onPackageRemoved(packageName: String) {
        invalidateIconCache(packageName)
        actionsManager.onPackageRemoved(packageName)
        refreshApps()
    }

    fun onPackageAddedOrUpdated(packageName: String) {
        invalidateIconCache(packageName)
        viewModelScope.launch(Dispatchers.IO) {
            val pm = getApplication<Application>().packageManager
            val launchIntent = pm.getLaunchIntentForPackage(packageName)
            val component = launchIntent?.component
            if (component != null) {
                val compKey = "${component.packageName}/${component.className}"
                actionsManager.onAppInstalledOrUpdated(compKey)
            }
            loadInstalledApps()
        }
    }

    private fun invalidateIconCache(packageName: String) {
        try {
            val imageLoader = coil3.SingletonImageLoader.get(getApplication())
            imageLoader.memoryCache?.let { memoryCache ->
                val keysToRemove = memoryCache.keys.filter { key ->
                    key.toString().contains(packageName)
                }
                keysToRemove.forEach { memoryCache.remove(it) }
            }
        } catch (_: Exception) {}
    }

    private var loadAppsJob: kotlinx.coroutines.Job? = null

    private fun loadInstalledApps() {
        loadAppsJob?.cancel()
        loadAppsJob = viewModelScope.launch(Dispatchers.IO) {
            val pm = getApplication<Application>().packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
            val resolvedInfos = pm.queryIntentActivities(mainIntent, 0)

            val distinctPkgs = resolvedInfos.map { it.activityInfo.packageName }.distinct()
            val updateTimeMap = distinctPkgs.associateWith { pkgName ->
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        pm.getPackageInfo(pkgName, android.content.pm.PackageManager.PackageInfoFlags.of(0)).lastUpdateTime
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getPackageInfo(pkgName, 0).lastUpdateTime
                    }
                } catch (_: Exception) {
                    0L
                }
            }

            val currentUpdateTimes = mutableMapOf<String, Long>()
            val newlyInstalledOrUpdated = mutableListOf<Pair<String, Long>>()

            val prevUpdateTimes = actionsManager.loadAppUpdateTimes()
            val isFirstTimeTracking = prevUpdateTimes.isEmpty()

            val appList = resolvedInfos.map { info ->
                val pkgName = info.activityInfo.packageName
                val actName = info.activityInfo.name
                val compKey = "$pkgName/$actName"

                val updateTime = updateTimeMap[pkgName] ?: 0L
                currentUpdateTimes[pkgName] = updateTime

                if (!isFirstTimeTracking && updateTime > 0L) {
                    val prevTime = prevUpdateTimes[pkgName]
                    if (prevTime == null || updateTime > prevTime) {
                        newlyInstalledOrUpdated.add(compKey to updateTime)
                    }
                }

                try {
                    val label = try {
                        info.loadLabel(pm).toString().trim().ifEmpty {
                            info.activityInfo.applicationInfo.loadLabel(pm).toString().trim().ifEmpty {
                                info.activityInfo.name.split(".").last().ifEmpty {
                                    info.activityInfo.packageName
                                }
                            }
                        }
                    } catch (e: Exception) {
                        info.activityInfo.packageName
                    }
                    val firstSymbol = extractFirstSymbol(label)
                    AppInfo(
                        label = label,
                        packageName = pkgName,
                        activityName = actName,
                        iconKey = compKey,
                        searchChar = mapToSearchChar(firstSymbol, actionsManager.customCharMappings.value)
                    )
                } catch (e: Exception) {
                    AppInfo(
                        label = info.activityInfo?.packageName ?: "Unknown",
                        packageName = pkgName,
                        activityName = actName,
                        iconKey = compKey,
                        searchChar = '#'
                    )
                }
            }.sortedBy { it.label.lowercase() }

            _apps.value = appList

            if (!isFirstTimeTracking && newlyInstalledOrUpdated.isNotEmpty()) {
                val sortedKeys = newlyInstalledOrUpdated
                    .sortedBy { it.second }
                    .map { it.first }
                actionsManager.onAppsInstalledOrUpdated(sortedKeys)
            } else if (isFirstTimeTracking && actionsManager.history.value.isEmpty()) {
                val topRecent = resolvedInfos
                    .mapNotNull { info ->
                        val pkg = info.activityInfo.packageName
                        val time = currentUpdateTimes[pkg] ?: 0L
                        if (time > 0L) ("$pkg/${info.activityInfo.name}" to time) else null
                    }
                    .sortedBy { it.second }
                    .takeLast(5)
                    .map { it.first }
                if (topRecent.isNotEmpty()) {
                    actionsManager.setInitialHistory(topRecent)
                }
            }

            actionsManager.saveAppUpdateTimes(currentUpdateTimes)
        }
    }
}
