package dev.msbs.cyclauncher

import dev.msbs.cyclauncher.data.AppActionsManager
import dev.msbs.cyclauncher.utils.getSafeStorageContext
import dev.msbs.cyclauncher.data.AutoTagsPreview
import dev.msbs.cyclauncher.data.TagsBackupPreview
import dev.msbs.cyclauncher.model.AppInfo
import dev.msbs.cyclauncher.model.Tag
import dev.msbs.cyclauncher.ui.theme.AccentColor

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Represents the user's preferred hand orientation for the launcher UI layout.
 */
enum class HandSide { LEFT, RIGHT }

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

    private val _accentColor = MutableStateFlow(AccentColor.CYAN)
    /** The selected UI theme accent color. */
    val accentColor: StateFlow<AccentColor> = _accentColor

    private val _showShadows = MutableStateFlow(true)
    /** Whether adaptive text/icon drop shadows are enabled. */
    val showShadows: StateFlow<Boolean> = _showShadows

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

    /** List of all installed applications, enriched with user-defined custom labels. */
    val apps: StateFlow<List<AppInfo>> = combine(_apps, actionsManager.customLabels) { all, customLabels ->
        all.map { app ->
            val customLabel = customLabels[app.componentKey]
            if (customLabel != null) {
                app.copy(label = customLabel, searchChar = mapToSearchChar(customLabel.firstOrNull() ?: ' '))
            } else {
                app
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** List of installed applications that match the currently selected alphabet wheel letter. */
    val filteredApps: StateFlow<List<AppInfo>> = combine(apps, _selectedLetter) { all, letter ->
        all.filter { it.searchChar == letter }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** List of recently opened applications in historical order. */
    val historyApps: StateFlow<List<AppInfo>> = combine(apps, actionsManager.history) { all, ids ->
        ids.mapNotNull { id -> all.find { it.componentKey == id } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** List of applications marked as favorites. */
    val favoriteApps: StateFlow<List<AppInfo>> = combine(apps, actionsManager.favorites) { all, ids ->
        ids.mapNotNull { id -> all.find { it.componentKey == id } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** List of installed applications matching the active keyboard search query. */
    val textFilteredApps: StateFlow<List<AppInfo>> = combine(apps, _searchText) { all, query ->
        if (query.isEmpty()) all
        else all.filter { it.label.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        val prefs = safeContext.getSharedPreferences("launcher_prefs", android.content.Context.MODE_PRIVATE)
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

    /**
     * Sets the active selected letter on the alphabet wheel.
     *
     * @param letter The character chosen by the user.
     */
    fun setSelectedLetter(letter: Char) { _selectedLetter.value = letter }

    /**
     * Toggles the keyboard text search mode on/off and resets the active search query.
     */
    fun toggleTextSearchMode() {
        _isTextSearchMode.value = !_isTextSearchMode.value
        if (!_isTextSearchMode.value) _searchText.value = ""
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
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
        val resolveInfo = getApplication<Application>().packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName == getApplication<Application>().packageName
    }

    /**
     * Opens system settings to choose the default launcher application.
     */
    fun openDefaultLauncherSettings() {
        val intent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            Intent(android.provider.Settings.ACTION_HOME_SETTINGS)
        } else {
            Intent(android.provider.Settings.ACTION_SETTINGS)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        getApplication<Application>().startActivity(intent)
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
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://discord.gg/9cnf49JnM")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        getApplication<Application>().startActivity(intent)
    }

    /** Exports installed application names to JSON format at the given URI. */
    fun exportAppNamesJson(uri: Uri) {
        actionsManager.exportAppNamesToUri(uri, _apps.value)
    }

    /** Exports installed application names to plain text format at the given URI. */
    fun exportAppNamesText(uri: Uri) {
        actionsManager.exportAppNamesToUriAsText(uri, _apps.value)
    }

    /** Imports custom app labels from JSON and applies them. */
    fun importAppNamesPreview(uri: Uri, onResult: (Int) -> Unit) {
        viewModelScope.launch {
            try {
                val map = actionsManager.importAppNamesFromUri(uri, _apps.value)
                actionsManager.applyAppLabels(map)
                onResult(map.size)
            } catch (e: Exception) {
                Toast.makeText(getApplication(), "Import failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Loads and parses AI-generated auto-tagging preview details. */
    fun loadAutoTagsPreview(uri: Uri) {
        viewModelScope.launch {
            try {
                val preview = actionsManager.parseAutoTags(uri, _apps.value)
                _autoTagsPreview.value = preview
            } catch (e: Exception) {
                Toast.makeText(getApplication(), "Failed to parse tags: ${e.message}", Toast.LENGTH_SHORT).show()
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
        actionsManager.exportTagsBackupToUri(uri)
    }

    /** Loads and parses a tags backup JSON file to prepare import details. */
    fun loadTagsBackupPreview(uri: Uri) {
        viewModelScope.launch {
            try {
                _tagsBackupPreview.value = actionsManager.parseTagsBackup(uri)
            } catch (e: Exception) {
                Toast.makeText(getApplication(), "Failed to parse tags file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Applies the currently loaded tags backup configuration. */
    fun applyTagsBackup() {
        _tagsBackupPreview.value?.let { preview ->
            actionsManager.applyTagsBackup(preview)
            _tagsBackupPreview.value = null
        }
    }

    /** Discards the active tags backup preview data. */
    fun dismissTagsBackupPreview() {
        _tagsBackupPreview.value = null
    }

    /**
     * Maps Cyrillic and special characters to their Latin equivalents for alphabet wheel navigation,
     * falling back to '#' if the character cannot be mapped to A-Z.
     *
     * @param char The raw character to map.
     * @return The resolved character ('A'..'Z' or '#').
     */
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

    /**
     * Loads installed launchable applications asynchronously, resolving their display labels,
     * package names, activity names, and starting index characters. Icons are intentionally
     * not loaded here — they are fetched on demand by Coil in the UI layer, which keeps this
     * list lightweight and lets the OS evict bitmaps under memory pressure.
     */
    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = getApplication<Application>().packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
            val resolvedInfos = pm.queryIntentActivities(mainIntent, 0)
            val appList = resolvedInfos.map { info ->
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
                    val firstChar = label.firstOrNull() ?: ' '
                    AppInfo(
                        label = label,
                        packageName = info.activityInfo.packageName,
                        activityName = info.activityInfo.name,
                        iconKey = "${info.activityInfo.packageName}/${info.activityInfo.name}",
                        searchChar = mapToSearchChar(firstChar)
                    )
                } catch (e: Exception) {
                    AppInfo(
                        label = info.activityInfo?.packageName ?: "Unknown",
                        packageName = info.activityInfo?.packageName ?: "",
                        activityName = info.activityInfo?.name ?: "",
                        iconKey = info.activityInfo?.let { "${it.packageName}/${it.name}" }.orEmpty(),
                        searchChar = '#'
                    )
                }
            }.sortedBy { it.label.lowercase() }

            _apps.value = appList

            val validKeys = appList.map { it.componentKey }.toSet()
            actionsManager.cleanupInvalidApps(validKeys)
        }
    }
}
