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
 * The main ViewModel for the launcher, providing application state, user preferences,
 * search queries, custom tag assignments, and backup actions to the UI.
 *
 * @param application The android Application instance context.
 */
class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val safeContext = application.getSafeStorageContext()
    private val actionsManager = AppActionsManager(safeContext)

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    
    private val _selectedLetter = MutableStateFlow('A')
    /** The character currently selected on the alphabet wheel. */
    val selectedLetter: StateFlow<Char> = _selectedLetter

    private val _searchListAlignment = MutableStateFlow(TextAlign.Start)
    /** Text alignment for the search app list, dynamic based on the preferred hand side. */
    val searchListAlignment: StateFlow<TextAlign> = _searchListAlignment

    private val _handSide = MutableStateFlow(HandSide.LEFT)
    /** The user's hand side layout preference. */
    val handSide: StateFlow<HandSide> = _handSide

    private val _accentColor = MutableStateFlow(AccentColor.SKY)
    /** The selected UI theme accent color. */
    val accentColor: StateFlow<AccentColor> = _accentColor

    private val _primaryTextColor = MutableStateFlow(PrimaryTextColor.WHITE)
    /** The selected primary (non-accent) text color theme. */
    val primaryTextColor: StateFlow<PrimaryTextColor> = _primaryTextColor

    private val _buttonTextColor = MutableStateFlow(PrimaryTextColor.BLACK)
    /** The selected button text color (White or Black). */
    val buttonTextColor: StateFlow<PrimaryTextColor> = _buttonTextColor

    private val _popupTheme = MutableStateFlow(PopupTheme.DARK)
    /** The selected background theme for popups and dialogs (Dark or Light). */
    val popupTheme: StateFlow<PopupTheme> = _popupTheme

    private val _showShadows = MutableStateFlow(true)
    /** Whether adaptive text/icon drop shadows are enabled. */
    val showShadows: StateFlow<Boolean> = _showShadows

    private val _shadowColor = MutableStateFlow(PrimaryTextColor.BLACK)
    /** The selected shadow color override (WHITE = white shadow, BLACK = black shadow). */
    val shadowColor: StateFlow<PrimaryTextColor> = _shadowColor

    private val _hideStatusBar = MutableStateFlow(false)
    /** Controls whether the Android system status bar is hidden for an immersive fullscreen launcher layout. */
    val hideStatusBar: StateFlow<Boolean> = _hideStatusBar

    private val _animationsEnabled = MutableStateFlow(true)
    /** Controls whether animations and cursor blinking are enabled throughout the launcher. */
    val animationsEnabled: StateFlow<Boolean> = _animationsEnabled

    private val _searchMethod = MutableStateFlow(SearchMethod.SIDE_ALPHABET)
    /** The active application search layout method. */
    val searchMethod: StateFlow<SearchMethod> = _searchMethod

    private var lastAlphabetSearchMethod: SearchMethod = SearchMethod.SIDE_ALPHABET

    private val _sideAlphabetButtonYRatio = MutableStateFlow(0.23f)
    /** Vertical screen position ratio for the side alphabet grid toggle button (0.05f..0.85f). */
    val sideAlphabetButtonYRatio: StateFlow<Float> = _sideAlphabetButtonYRatio

    private val _isTextSearchMode = MutableStateFlow(false)
    /** Whether keyboard-based text search mode is active. */
    val isTextSearchMode: StateFlow<Boolean> = _isTextSearchMode

    private val _searchText = MutableStateFlow("")
    /** The current keyboard search text query. */
    val searchText: StateFlow<String> = _searchText

    /** List of all custom tags. */
    val tags: StateFlow<List<Tag>> = actionsManager.tags
    /** Map linking application component keys to assigned tag IDs. */
    val appTags: StateFlow<Map<String, List<String>>> = actionsManager.appTags

    private val _autoTagsPreview = MutableStateFlow<AutoTagsPreview?>(null)
    /** Holds the preview state of the AI auto-tagging process before it is applied. */
    val autoTagsPreview: StateFlow<AutoTagsPreview?> = _autoTagsPreview

    private val _resetRequest = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Flow signaling that the UI should scroll back to the main home screen. */
    val resetRequest = _resetRequest.asSharedFlow()

    private val _historyScrollToBottomTrigger = MutableStateFlow(0L)
    /** Trigger signaling that the recent history list should scroll to the bottom. */
    val historyScrollToBottomTrigger: StateFlow<Long> = _historyScrollToBottomTrigger

    /** Requests the home screen UI to reset to the main cluster and page, exiting text search mode if active. */
    fun requestReset() {
        if (_isTextSearchMode.value) {
            _isTextSearchMode.value = false
            _searchText.value = ""
            _searchMethod.value = lastAlphabetSearchMethod
        }
        _historyScrollToBottomTrigger.value = System.currentTimeMillis()
        _resetRequest.tryEmit(Unit)
    }

    /** Requests the recent history list to scroll to the bottom. */
    fun requestHistoryScrollToBottom() {
        _historyScrollToBottomTrigger.value = System.currentTimeMillis()
    }

    /** List of all installed applications with custom labels and search indexing applied. */
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

    /** Applications filtered for the currently selected search letter. */
    val filteredApps: StateFlow<List<AppInfo>> = combine(apps, _selectedLetter) { all, letter ->
        all.filter { it.searchChar == letter }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** List of all favorite keys (app component keys and "tag:$tagId" entries). */
    val favorites: StateFlow<List<String>> = actionsManager.favorites

    /** Recent application list in order of launch. */
    val historyApps: StateFlow<List<AppInfo>> = combine(apps, actionsManager.history) { all, ids ->
        val appMap = all.associateBy { it.componentKey }
        ids.mapNotNull { id -> appMap[id] }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** Set of application component keys that were recently installed or updated and not yet launched. */
    val recentlyUpdatedApps: StateFlow<Set<String>> = actionsManager.recentlyUpdated

    /** Ordered list of favorite items (both applications and tag folders). */
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

    /** Favorite applications (filtered for backwards compatibility). */
    val favoriteApps: StateFlow<List<AppInfo>> = favoriteItems.map { items ->
        items.mapNotNull { (it as? FavoriteItem.App)?.appInfo }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Applications filtered by the active text search query. */
    val textFilteredApps: StateFlow<List<AppInfo>> = combine(apps, _searchText) { all, query ->
        if (query.isEmpty()) all
        else all.filter { it.label.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Controls whether the interactive tutorial overlay is visible. */
    private val _showTutorial = MutableStateFlow(false)
    val showTutorial: StateFlow<Boolean> = _showTutorial

    /** Current active step in the tutorial sequence (0..4). */
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

    /** Starts or restarts the interactive tutorial from step 0. */
    fun startTutorial() {
        _tutorialStep.value = 0
        _showTutorial.value = true
    }

    /** Sets the current step of the tutorial. */
    fun setTutorialStep(step: Int) {
        _tutorialStep.value = step.coerceIn(0, 5)
    }

    /** Advances to the next tutorial step, or completes if on the last step. */
    fun nextTutorialStep() {
        if (_tutorialStep.value < 5) {
            _tutorialStep.value += 1
        } else {
            completeTutorial()
        }
    }

    /** Marks the tutorial as completed and hides the overlay. */
    fun completeTutorial() {
        _showTutorial.value = false
        _tutorialStep.value = 0
        val prefs = safeContext.getSharedPreferences("launcher_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putBoolean("is_tutorial_completed", true).apply()
    }

    /**
     * Sets the active selected letter on the alphabet wheel.
     *
     * @param letter The character chosen by the user.
     */
    fun setSelectedLetter(letter: Char) { _selectedLetter.value = letter }

    /**
     * Sets the vertical position ratio for the side alphabet grid toggle button and persists it.
     *
     * @param ratio Vertical position ratio between 0.05f and 0.85f.
     */
    fun setSideAlphabetButtonYRatio(ratio: Float) {
        val clamped = ratio.coerceIn(0.05f, 0.85f)
        _sideAlphabetButtonYRatio.value = clamped
        val prefs = safeContext.getSharedPreferences("launcher_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putFloat("side_alphabet_button_y_ratio", clamped).apply()
    }

    /**
     * Sets the active search layout method and persists the preference.
     *
     * @param method The selected [SearchMethod].
     */
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

    /**
     * Toggles the keyboard text search mode on/off and persists the active search layout state.
     */
    fun toggleTextSearchMode() {
        val nextMethod = if (_searchMethod.value == SearchMethod.TEXT) {
            lastAlphabetSearchMethod
        } else {
            SearchMethod.TEXT
        }
        setSearchMethod(nextMethod)
    }

    /**
     * Updates the keyboard search filter text.
     *
     * @param text The new search query string.
     */
    fun setSearchText(text: String) { _searchText.value = text }

    /**
     * Sets the hand side preference layout and saves it to local settings.
     *
     * @param side The hand preference (LEFT or RIGHT).
     */
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

    /**
     * Sets whether the Android system status bar should be hidden for an immersive launcher layout and persists the setting.
     *
     * @param hide True to hide status bar, false to show it.
     */
    fun setHideStatusBar(hide: Boolean) {
        _hideStatusBar.value = hide
        val prefs = safeContext.getSharedPreferences("launcher_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putBoolean("hide_status_bar", hide).apply()
    }

    /**
     * Sets whether launcher animations and cursor blinking should be enabled and persists the setting.
     *
     * @param enabled True to enable animations, false to disable them.
     */
    fun setAnimationsEnabled(enabled: Boolean) {
        _animationsEnabled.value = enabled
        val prefs = safeContext.getSharedPreferences("launcher_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putBoolean("animations_enabled", enabled).apply()
    }

    /**
     * Sets the UI theme accent color preference and persists it.
     *
     * @param color The chosen [AccentColor] instance.
     */
    fun setAccentColor(color: AccentColor) {
        _accentColor.value = color
        val prefs = safeContext.getSharedPreferences("launcher_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("accent_color", color.name).apply()
    }

    /**
     * Refreshes the dynamic wallpaper accent color if the current theme is set to WALLPAPER.
     */
    fun refreshDynamicWallpaperColor(context: Context) {
        if (_accentColor.value.isDynamicWallpaper) {
            val updated = AccentColor.wallpaper(context)
            if (_accentColor.value.color != updated.color) {
                _accentColor.value = updated
            }
        }
    }

    /**
     * Sets the primary text color preference (White or Black) and persists it.
     *
     * @param color The chosen [PrimaryTextColor] instance.
     */
    fun setPrimaryTextColor(color: PrimaryTextColor) {
        _primaryTextColor.value = color
        val prefs = safeContext.getSharedPreferences("launcher_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("primary_text_color", color.name).apply()
    }

    /**
     * Sets the button text color preference (White or Black) and persists it.
     *
     * @param color The chosen [PrimaryTextColor] instance.
     */
    fun setButtonTextColor(color: PrimaryTextColor) {
        _buttonTextColor.value = color
        val prefs = safeContext.getSharedPreferences("launcher_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("button_text_color", color.name).apply()
    }

    /**
     * Sets the popup and dialog background theme (Dark or Light) and persists it.
     *
     * @param theme The chosen [PopupTheme] instance.
     */
    fun setPopupTheme(theme: PopupTheme) {
        _popupTheme.value = theme
        val prefs = safeContext.getSharedPreferences("launcher_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("popup_theme", theme.name).apply()
    }

    /**
     * Sets the visibility preference for drop shadows on text/icons and persists it.
     *
     * @param enabled True to show shadows, false to hide.
     */
    fun setShowShadows(enabled: Boolean) {
        _showShadows.value = enabled
        val prefs = safeContext.getSharedPreferences("launcher_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putBoolean("show_shadows", enabled).apply()
    }

    /**
     * Sets the shadow color override (WHITE = white shadow, BLACK = black shadow) and persists it.
     *
     * @param color The chosen [PrimaryTextColor] instance for shadow.
     */
    fun setShadowColor(color: PrimaryTextColor) {
        _shadowColor.value = color
        val prefs = safeContext.getSharedPreferences("launcher_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("shadow_color", color.name).apply()
    }

    /**
     * Logs an application launch to update historical order.
     *
     * @param componentKey The application key.
     */
    fun logAppLaunch(componentKey: String) {
        actionsManager.logAppLaunch(componentKey)
    }

    /**
     * Toggles the favorite state of an application.
     *
     * @param componentKey The application key.
     */
    fun toggleFavorite(componentKey: String) {
        actionsManager.toggleFavorite(componentKey)
    }

    /**
     * Reorders the list of favorite applications by moving an item from [fromIndex] to [toIndex].
     *
     * @param fromIndex The original index of the item.
     * @param toIndex The new target index for the item.
     */
    fun reorderFavorites(fromIndex: Int, toIndex: Int) {
        actionsManager.reorderFavorites(fromIndex, toIndex)
    }
    
    /** Stream indicating whether recording app launches to history is paused. */
    val isHistoryPaused: StateFlow<Boolean> = actionsManager.isHistoryPaused

    /** Toggles whether recording application launches to history is paused. */
    fun toggleHistoryPaused() {
        actionsManager.toggleHistoryPaused()
    }

    /** Clears all entries from the launch history. */
    fun clearHistory() {
        actionsManager.clearHistory()
    }

    /**
     * Removes an application from the launch history.
     *
     * @param componentKey The application key.
     */
    fun removeFromHistory(componentKey: String) {
        actionsManager.removeFromHistory(componentKey)
    }

    /**
     * Renames an application with a custom user-defined label.
     *
     * @param componentKey The application key.
     * @param newLabel The new label for the application.
     */
    fun renameApp(componentKey: String, newLabel: String) {
        actionsManager.renameApp(componentKey, newLabel)
    }

    /** Creates a new custom tag. */
    fun createTag(tag: Tag) = actionsManager.createTag(tag)
    /** Updates properties of an existing tag. */
    fun updateTag(tag: Tag) = actionsManager.updateTag(tag)
    /** Deletes a tag by its ID. */
    fun deleteTag(tagId: String) = actionsManager.deleteTag(tagId)
    /** Toggles tag assignment for a given application component. */
    fun toggleTagForApp(componentKey: String, tagId: String) = actionsManager.toggleTagForApp(componentKey, tagId)

    /**
     * Refreshes the list of installed applications.
     */
    fun refreshApps() {
        loadInstalledApps()
    }

    /**
     * Launches the system intent to uninstall the specified application package.
     *
     * @param packageName The Android package name to uninstall.
     */
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

    /** Checks if an application is favorited. */
    fun isFavorite(componentKey: String): Boolean = actionsManager.isFavorite(componentKey)

    /**
     * Checks if Cyclauncher is currently configured as the default device launcher.
     *
     * @return True if Cyclauncher is default, false otherwise.
     */
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

    /**
     * Resolves the current default device launcher package name if it is not Cyclauncher.
     */
    fun getDefaultLauncherPackage(): String? {
        val context = getApplication<Application>()
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
        val resolveInfo = context.packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        val pkg = resolveInfo?.activityInfo?.packageName
        return if (pkg != context.packageName) pkg else null
    }

    /**
     * Navigates out of Cyclauncher to the system default home launcher screen.
     */
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
                    // fallback below
                }
            }
        }

        // Generic HOME intent fallback
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(homeIntent)
            return
        } catch (e: Exception) {
            // fallback below
        }

        val activity = context as? android.app.Activity
        if (activity != null) {
            if (!activity.moveTaskToBack(true)) {
                activity.finish()
            }
        }
    }

    /**
     * Opens system settings to choose the default home launcher application.
     * Follows Lawnchair / AOSP Launcher3 standard intent resolution cascade.
     */
    fun openDefaultLauncherSettings(context: Context) {
        val activity = context as? android.app.Activity

        // 1. Primary standard: ACTION_HOME_SETTINGS (supported across Android 7 - 15)
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
            // Fallback to RoleManager or generic settings if OEM overrides ACTION_HOME_SETTINGS
        }

        // 2. Secondary fallback: RoleManager (Android 10 - 12)
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
                // Fallback to generic Settings below
            }
        }

        // 3. Ultimate fallback: System Settings
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
            // Ignore
        }
    }

    /** Opens the external Tribute contribution/support page in a browser. */
    fun openSupportPage() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://web.tribute.tg/e/1dW")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        getApplication<Application>().startActivity(intent)
    }

    /** Opens the project GitHub page in a browser. */
    fun openGitHubPage() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/msbluesnow/Cyclauncher")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        getApplication<Application>().startActivity(intent)
    }

    /** Opens the project Discord support server in a browser. */
    fun openDiscordPage() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://discord.gg/Zw4EBe92Qn")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        getApplication<Application>().startActivity(intent)
    }

    /** Opens the Keep Android Open initiative website in a browser. */
    fun openKeepAndroidOpenPage() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://keepandroidopen.org/")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        getApplication<Application>().startActivity(intent)
    }

    /** Exports installed application names to JSON format at the given URI. */
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

    /** Exports installed application names to plain text format at the given URI. */
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

    /** Imports custom app labels from JSON and applies them. */
    fun importAppNamesPreview(uri: Uri, onResult: (Int) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val map = actionsManager.importAppNamesFromUri(uri, apps.value)
                actionsManager.applyAppLabels(map)
                withContext(Dispatchers.Main) {
                    onResult(map.size)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Import failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /** Loads and parses AI-generated auto-tagging preview details. */
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

    /** Applies the currently loaded auto-tagging configurations. */
    fun applyAutoTags() {
        _autoTagsPreview.value?.let { preview ->
            actionsManager.applyAutoTags(preview)
            _autoTagsPreview.value = null
        }
    }

    /** Discards the active auto-tagging preview data. */
    fun dismissAutoTagsPreview() {
        _autoTagsPreview.value = null
    }

    // ---- Tags backup (tags + assignments), unified across Settings & AutoTags) ----

    private val _tagsBackupPreview = MutableStateFlow<TagsBackupPreview?>(null)
    /** Holds the preview state of tags backup file import. */
    val tagsBackupPreview: StateFlow<TagsBackupPreview?> = _tagsBackupPreview

    /** Exports tags and assignments to a JSON file at the given URI. */
    fun exportTagsBackup(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                actionsManager.exportTagsBackupToUri(uri)
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Exported ${tags.value.size} tags", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /** Loads and parses a tags backup JSON file to prepare import details. */
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

    /** Applies the currently loaded tags backup configuration. */
    fun applyTagsBackup() {
        _tagsBackupPreview.value?.let { preview ->
            actionsManager.applyTagsBackup(preview, apps.value)
            _tagsBackupPreview.value = null
        }
    }

    /** Discards the active tags backup preview data. */
    fun dismissTagsBackupPreview() {
        _tagsBackupPreview.value = null
    }

    /** Stream of custom first-character to search-letter mappings. */
    val customCharMappings: StateFlow<Map<String, Char>> = actionsManager.customCharMappings

    /**
     * Adds or updates a custom character mapping and re-indexes installed apps.
     */
    fun addOrUpdateCharMapping(symbol: String, targetChar: Char) {
        val updated = actionsManager.addOrUpdateCharMapping(symbol, targetChar)
        reindexApps(updated)
    }

    /**
     * Adds multiple character mappings at once and re-indexes installed apps.
     */
    fun addCharMappings(mappings: Map<String, Char>) {
        val updated = actionsManager.addCharMappings(mappings)
        reindexApps(updated)
    }

    /**
     * Removes a custom character mapping and re-indexes installed apps.
     */
    fun removeCharMapping(symbol: String) {
        val updated = actionsManager.removeCharMapping(symbol)
        reindexApps(updated)
    }

    /**
     * Resets all custom character mappings to default and re-indexes installed apps.
     */
    fun resetCharMappings() {
        val updated = actionsManager.resetCharMappings()
        reindexApps(updated)
    }

    /**
     * Exports all custom character mappings to a JSON file at [uri].
     */
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

    /**
     * Imports custom character mappings from the specified [uri] and re-indexes apps.
     */
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

    /**
     * Re-calculates searchChar for all loaded apps in-place using the given mappings.
     */
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
     * Extracts the first graphical symbol, emoji, or character from a string.
     * Uses [java.text.BreakIterator] to properly handle surrogate pairs and composite emoji sequences.
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
     * Maps a character, emoji, or foreign language symbol to an alphabet index ('A'..'Z' or '#').
     * Prioritizes custom user-defined mappings before falling back to built-in rules.
     *
     * @param symbol The raw symbol string (character or emoji).
     * @param customMappings Active custom character mapping table.
     * @return The resolved character ('A'..'Z' or '#').
     */
    fun mapToSearchChar(symbol: String, customMappings: Map<String, Char> = actionsManager.customCharMappings.value): Char {
        if (symbol.isEmpty()) return '#'

        // 1. Direct user custom mapping lookup (exact symbol or uppercase)
        customMappings[symbol]?.let { return it }
        customMappings[symbol.uppercase()]?.let { return it }

        // 2. Fallback single character mapping (Cyrillic to Latin & standard uppercase)
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

    /**
     * Called when an application package is explicitly removed/uninstalled from the device.
     *
     * @param packageName The package name of the uninstalled app.
     */
    fun onPackageRemoved(packageName: String) {
        invalidateIconCache(packageName)
        actionsManager.onPackageRemoved(packageName)
        refreshApps()
    }

    /**
     * Called when an application package is installed or updated on the device.
     *
     * @param packageName The package name of the newly installed or updated app.
     */
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

    /**
     * Evicts cached icon bitmaps for the given package from Coil's memory cache.
     */
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

    /**
     * Loads installed launchable applications asynchronously, resolving their display labels,
     * package names, activity names, and starting index characters. Icons are intentionally
     * not loaded here — they are fetched on demand by Coil in the UI layer, which keeps this
     * list lightweight and lets the OS evict bitmaps under memory pressure.
     * Also detects newly installed or updated applications and adds them to recent history.
     */
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
            val newlyInstalledOrUpdated = mutableListOf<Pair<String, Long>>() // componentKey to updateTime

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
                // Sort by updateTime ascending so that the newest ends up at index 0 of history
                val sortedKeys = newlyInstalledOrUpdated
                    .sortedBy { it.second }
                    .map { it.first }
                actionsManager.onAppsInstalledOrUpdated(sortedKeys)
            } else if (isFirstTimeTracking && actionsManager.history.value.isEmpty()) {
                // On first run, populate history with the most recently installed/updated apps without badge
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
