package dev.msbs.cyclauncher

import dev.msbs.cyclauncher.data.AutoTagsPreview
import dev.msbs.cyclauncher.data.TagsBackupPreview
import dev.msbs.cyclauncher.model.AppInfo
import dev.msbs.cyclauncher.model.Tag
import dev.msbs.cyclauncher.ui.theme.AccentColor
import dev.msbs.cyclauncher.ui.theme.PopupTheme
import dev.msbs.cyclauncher.ui.theme.PrimaryTextColor
import dev.msbs.cyclauncher.ui.components.AppActionMenu
import dev.msbs.cyclauncher.ui.components.RenameDialog
import dev.msbs.cyclauncher.ui.components.TagEditDialog
import dev.msbs.cyclauncher.ui.components.TagSelectionDialog
import dev.msbs.cyclauncher.ui.components.ScreenScrim
import dev.msbs.cyclauncher.ui.components.TutorialOverlay
import dev.msbs.cyclauncher.ui.screens.HighlightScreen
import dev.msbs.cyclauncher.ui.screens.MainMenuScreen
import dev.msbs.cyclauncher.ui.screens.SearchScreen
import dev.msbs.cyclauncher.ui.screens.SettingsScreen
import dev.msbs.cyclauncher.utils.LocaleUtils
import dev.msbs.cyclauncher.utils.isUserUnlockedSafe

import android.annotation.SuppressLint
import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import androidx.compose.animation.togetherWith
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Main activity of Cyclauncher, managing edge-to-edge layout, receivers, and navigation pagers.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()

    private var isDefaultLauncherCached = false
    private var isHighlightScreenActive = false
    private var wallpaperColorsListener: Any? = null
    private var launcherAppsHandlerThread: android.os.HandlerThread? = null
    private var launcherAppsCallback: android.content.pm.LauncherApps.Callback? = null
    private val appWidgetHostState = mutableStateOf<AppWidgetHost?>(null)
    private val appWidgetManagerState = mutableStateOf<AppWidgetManager?>(null)
    private val widgetUnlockGenerationState = mutableIntStateOf(0)
    private val appWidgetHost: AppWidgetHost?
        get() = appWidgetHostState.value
    private val appWidgetManager: AppWidgetManager?
        get() = appWidgetManagerState.value
    private var unlockReceiver: BroadcastReceiver? = null
    private var hideStatusBarRetryRunnable: Runnable? = null
    private var lastConfiguration: Configuration? = null

    companion object {
        private const val REQUEST_CONFIGURE_WIDGET_BASE = 2000
        private const val REQUEST_CONFIGURE_WIDGET_MAX = 2999
    }

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            when (action) {
                "android.intent.action.PROFILE_AVAILABLE",
                "android.intent.action.MANAGED_PROFILE_AVAILABLE" -> {
                    widgetUnlockGenerationState.intValue += 1
                    val user = androidx.core.content.IntentCompat.getParcelableExtra(intent, Intent.EXTRA_USER, android.os.UserHandle::class.java)
                    if (user != null) {
                        viewModel.onProfileAvailabilityChanged(user, isAvailable = true)
                    } else {
                        viewModel.refreshApps()
                    }
                }
                "android.intent.action.PROFILE_UNAVAILABLE",
                "android.intent.action.MANAGED_PROFILE_UNAVAILABLE" -> {
                    widgetUnlockGenerationState.intValue += 1
                    val user = androidx.core.content.IntentCompat.getParcelableExtra(intent, Intent.EXTRA_USER, android.os.UserHandle::class.java)
                    if (user != null) {
                        viewModel.onProfileAvailabilityChanged(user, isAvailable = false)
                    } else {
                        viewModel.refreshApps()
                    }
                }
                Intent.ACTION_USER_UNLOCKED -> {
                    initAppWidgets()
                    viewModel.onUserUnlocked(application)
                }
                Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE,
                Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE,
                "android.intent.action.PROFILE_ACCESSIBLE",
                "android.intent.action.PROFILE_INACCESSIBLE" -> {
                    widgetUnlockGenerationState.intValue += 1
                    viewModel.refreshApps()
                }
                "android.intent.action.OVERLAY_CHANGED",
                "android.intent.action.THEME_CHANGED",
                "com.samsung.android.theme.SAMSUNG_THEME_CHANGED" -> {
                    viewModel.invalidateAllIconCaches()
                }
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleUtils.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lastConfiguration = Configuration(resources.configuration)
        viewModel.onLocaleChangedIfNeeded(resources.configuration)
        viewModel.setWidgetHostDeleter { widgetId ->
            try {
                appWidgetHost?.deleteAppWidgetId(widgetId)
            } catch (_: Exception) {}
        }

        initAppWidgetsIfUnlocked()

        isDefaultLauncherCached = viewModel.isDefaultLauncher()
        val onBackPressedCallback = object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!isDefaultLauncherCached) {
                    if (!moveTaskToBack(true)) {
                        viewModel.exitToSystemHome(this@MainActivity)
                    }
                } else {
                    viewModel.requestReset()
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
        
        val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as? android.content.pm.LauncherApps
        if (launcherApps != null) {
            val thread = android.os.HandlerThread("LauncherAppsCallbackThread").apply { start() }
            launcherAppsHandlerThread = thread
            val handler = android.os.Handler(thread.looper)
            val callback = object : android.content.pm.LauncherApps.Callback() {
                override fun onPackageAdded(packageName: String, user: android.os.UserHandle) {
                    viewModel.onPackageAddedOrUpdated(packageName, user)
                }
                override fun onPackageChanged(packageName: String, user: android.os.UserHandle) {
                    viewModel.onPackageAddedOrUpdated(packageName, user)
                }
                override fun onPackageRemoved(packageName: String, user: android.os.UserHandle) {
                    viewModel.onPackageRemoved(packageName, user)
                }
                override fun onPackagesAvailable(packageNames: Array<out String>, user: android.os.UserHandle, replacing: Boolean) {
                    if (user != android.os.Process.myUserHandle()) {
                        viewModel.onProfileAvailabilityChanged(user, isAvailable = true)
                    } else {
                        viewModel.refreshApps()
                    }
                }
                override fun onPackagesUnavailable(packageNames: Array<out String>, user: android.os.UserHandle, replacing: Boolean) {
                    if (replacing) return
                    if (user != android.os.Process.myUserHandle()) {
                        viewModel.onProfileAvailabilityChanged(user, isAvailable = false)
                    } else {
                        viewModel.refreshApps()
                    }
                }
            }
            launcherAppsCallback = callback
            launcherApps.registerCallback(callback, handler)
        }

        val systemFilter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_UNLOCKED)
            addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE)
            addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE)
            addAction("android.intent.action.OVERLAY_CHANGED")
            addAction("android.intent.action.THEME_CHANGED")
            addAction("com.samsung.android.theme.SAMSUNG_THEME_CHANGED")
            addAction("android.intent.action.PROFILE_AVAILABLE")
            addAction("android.intent.action.PROFILE_UNAVAILABLE")
            addAction("android.intent.action.PROFILE_ACCESSIBLE")
            addAction("android.intent.action.PROFILE_INACCESSIBLE")
            addAction("android.intent.action.MANAGED_PROFILE_AVAILABLE")
            addAction("android.intent.action.MANAGED_PROFILE_UNAVAILABLE")
        }
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            systemReceiver,
            systemFilter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            val wpManager = getSystemService(android.app.WallpaperManager::class.java)
            val listener = android.app.WallpaperManager.OnColorsChangedListener { colors, _ ->
                viewModel.refreshDynamicWallpaperColor(this@MainActivity, colors)
            }
            try {
                wpManager?.addOnColorsChangedListener(listener, android.os.Handler(android.os.Looper.getMainLooper()))
                wallpaperColorsListener = listener
            } catch (_: Exception) {}
        }

        enableEdgeToEdge()

        try {
            val currentVersionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageManager.getPackageInfo(packageName, 0).longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0).versionCode
            }
            org.woheller69.freeDroidWarn.FreeDroidWarn.showWarningOnUpgrade(this, currentVersionCode)
        } catch (_: Exception) {
        }

        setContent {
            CyclauncherTheme {
                LauncherCompositionLocals(viewModel) {
                    StatusBarController(
                        viewModel = viewModel,
                        onUpdateStatusBar = ::updateStatusBarVisibility
                    )

                    val animationsEnabled = dev.msbs.cyclauncher.ui.theme.LocalAnimationsEnabled.current
                    val horizontalPagerState = rememberPagerState { 2 }
                    val verticalPagerState = rememberPagerState { 2 }
                    val scope = rememberCoroutineScope()
                    val fastAnimSpec = remember { tween<Float>(durationMillis = 200, easing = FastOutSlowInEasing) }
                    val slideAnimSpec = remember { tween<androidx.compose.ui.unit.IntOffset>(durationMillis = 180, easing = FastOutSlowInEasing) }

                    var showActionMenuFor by remember { mutableStateOf<AppInfo?>(null) }
                    var showRenameDialogFor by remember { mutableStateOf<AppInfo?>(null) }
                    var showTagDialogFor by remember { mutableStateOf<AppInfo?>(null) }
                    var tagToEditForDialog by remember { mutableStateOf<Tag?>(null) }
                    var isHighlightScreenVisible by remember { mutableStateOf(false) }

                    var menuSource by remember { mutableStateOf("none") }
                    var menuOffset by remember { mutableStateOf(Offset.Zero) }

                    val dismissTransientDialogs = {
                        showActionMenuFor = null
                        showRenameDialogFor = null
                        showTagDialogFor = null
                        tagToEditForDialog = null
                        viewModel.dismissAutoTagsPreview()
                        viewModel.dismissTagsBackupPreview()
                    }

                    LaunchedEffect(Unit) {
                        viewModel.resetRequest.collect {
                            dismissTransientDialogs()
                            isHighlightScreenVisible = false
                            if (horizontalPagerState.currentPage != 0 ||
                                horizontalPagerState.targetPage != 0 ||
                                horizontalPagerState.currentPageOffsetFraction != 0f
                            ) {
                                horizontalPagerState.scrollToPage(0)
                            }
                            if (verticalPagerState.currentPage != 0 ||
                                verticalPagerState.targetPage != 0 ||
                                verticalPagerState.currentPageOffsetFraction != 0f
                            ) {
                                verticalPagerState.scrollToPage(0)
                            }
                        }
                    }

                    BackHandler(enabled = isHighlightScreenVisible) {
                        isHighlightScreenVisible = false
                    }

                    TutorialResetEffect(
                        viewModel = viewModel,
                        horizontalPagerState = horizontalPagerState,
                        verticalPagerState = verticalPagerState,
                        onHideHighlight = { isHighlightScreenVisible = false }
                    )

                    val isOnMainScreen by remember {
                        derivedStateOf {
                            horizontalPagerState.currentPage == 0 &&
                            verticalPagerState.currentPage == 0 &&
                            horizontalPagerState.targetPage == 0 &&
                            verticalPagerState.targetPage == 0 &&
                            !isHighlightScreenVisible
                        }
                    }

                    val isSettingsActive by remember {
                        derivedStateOf {
                            horizontalPagerState.currentPage == 1 || horizontalPagerState.targetPage == 1
                        }
                    }
                    val isSearchActive by remember {
                        derivedStateOf {
                            horizontalPagerState.currentPage == 0 &&
                            (verticalPagerState.currentPage == 1 || verticalPagerState.targetPage == 1)
                        }
                    }

                    val shouldListenWidgets by remember {
                        derivedStateOf { isHighlightScreenVisible || isSearchActive }
                    }

                    LaunchedEffect(isSearchActive) {
                        if (!isSearchActive) {
                            viewModel.resetSearchFilters()
                        }
                    }

                    LaunchedEffect(verticalPagerState.currentPage, horizontalPagerState.currentPage, isHighlightScreenVisible) {
                        val isMenuDismissNeeded = when (menuSource) {
                            "search" -> verticalPagerState.currentPage != 1 || horizontalPagerState.currentPage != 0 || isHighlightScreenVisible
                            else -> verticalPagerState.currentPage != 0 || horizontalPagerState.currentPage != 0 || isHighlightScreenVisible
                        }
                        if (isMenuDismissNeeded) {
                            dismissTransientDialogs()
                        }
                    }

                    val currentHost = appWidgetHostState.value
                    val currentManager = appWidgetManagerState.value
                    val widgetUnlockGeneration = widgetUnlockGenerationState.intValue

                    LaunchedEffect(shouldListenWidgets, currentHost) {
                        isHighlightScreenActive = shouldListenWidgets
                        if (shouldListenWidgets) {
                            if (isHighlightScreenVisible) {
                                dismissTransientDialogs()
                            }
                            try { currentHost?.startListening() } catch (_: Exception) {}
                        } else {
                            try { currentHost?.stopListening() } catch (_: Exception) {}
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color.Transparent
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            ScreenScrimLayer(viewModel = viewModel)

                            HorizontalPager(
                                state = horizontalPagerState,
                                modifier = Modifier.fillMaxSize(),
                                beyondViewportPageCount = 0,
                                userScrollEnabled = false
                            ) { hIndex ->
                                if (hIndex == 0) {
                                    VerticalPager(
                                        state = verticalPagerState,
                                        modifier = Modifier.fillMaxSize(),
                                        beyondViewportPageCount = 1,
                                        userScrollEnabled = false
                                    ) { vIndex ->
                                        if (vIndex == 0) {
                                            val isActionMenuOpen = showActionMenuFor != null || showRenameDialogFor != null || showTagDialogFor != null || tagToEditForDialog != null
                                            androidx.compose.animation.AnimatedContent(
                                                targetState = isHighlightScreenVisible,
                                                transitionSpec = {
                                                    if (animationsEnabled) {
                                                        val direction = if (targetState == (viewModel.handSide.value == HandSide.RIGHT)) 1 else -1
                                                        (androidx.compose.animation.slideInHorizontally(
                                                            initialOffsetX = { fullWidth -> direction * fullWidth },
                                                            animationSpec = slideAnimSpec
                                                        ) + androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween<Float>(150)))
                                                        .togetherWith(
                                                            androidx.compose.animation.slideOutHorizontally(
                                                                targetOffsetX = { fullWidth -> -direction * fullWidth },
                                                                animationSpec = slideAnimSpec
                                                            ) + androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween<Float>(150))
                                                        )
                                                    } else {
                                                        androidx.compose.animation.ContentTransform(
                                                            targetContentEnter = androidx.compose.animation.EnterTransition.None,
                                                            initialContentExit = androidx.compose.animation.ExitTransition.None,
                                                            sizeTransform = null
                                                        )
                                                    }
                                                },
                                                label = "MainHighlightScreenTransition"
                                            ) { showHighlight ->
                                                if (showHighlight) {
                                                    HighlightScreen(
                                                        viewModel = viewModel,
                                                        appWidgetHost = currentHost,
                                                        appWidgetManager = currentManager,
                                                        widgetUnlockGeneration = widgetUnlockGeneration,
                                                        onClose = { isHighlightScreenVisible = false },
                                                        onAppClick = ::openApp,
                                                        onConfigureWidget = ::startWidgetConfiguration,
                                                        onAppLongClick = { app, offset ->
                                                            showActionMenuFor = app
                                                            menuOffset = offset
                                                            menuSource = "highlight"
                                                        }
                                                    )
                                                } else {
                                                    MainMenuScreen(
                                                        viewModel = viewModel,
                                                        isActive = isOnMainScreen,
                                                        isActionMenuOpen = isActionMenuOpen,
                                                        onAppClick = ::openApp,
                                                        onAppLongClick = { app, offset ->
                                                            showActionMenuFor = app
                                                            menuOffset = offset
                                                            menuSource = "history_or_favorites"
                                                        },
                                                        onSwipeUp = {
                                                            scope.launch { verticalPagerState.scrollToPageCompat(1, animationsEnabled, fastAnimSpec) }
                                                        },
                                                        onSwipeDown = ::openNotifications,
                                                        onOpenQuickSettings = ::openQuickSettings,
                                                        onSettingsClick = {
                                                            scope.launch { horizontalPagerState.scrollToPageCompat(1, animationsEnabled, fastAnimSpec) }
                                                        },
                                                        onOpenHighlightScreen = {
                                                            isHighlightScreenVisible = true
                                                        },
                                                        onEditTag = { tag -> tagToEditForDialog = tag }
                                                    )
                                                }
                                            }
                                        } else {
                                            SearchScreen(
                                                viewModel = viewModel,
                                                enabled = isSearchActive,
                                                appWidgetHost = currentHost,
                                                appWidgetManager = currentManager,
                                                widgetUnlockGeneration = widgetUnlockGeneration,
                                                onConfigureWidget = ::startWidgetConfiguration,
                                                onBackToMain = {
                                                    scope.launch { verticalPagerState.scrollToPageCompat(0, animationsEnabled, fastAnimSpec) }
                                                },
                                                onAppClick = { appKey ->
                                                    viewModel.setSelectedLetter(null)
                                                    openApp(appKey)
                                                },
                                                onAppLongClick = { app, offset ->
                                                    showActionMenuFor = app
                                                    menuOffset = offset
                                                    menuSource = "search"
                                                }
                                            )
                                        }
                                    }
                                } else {
                                    SettingsScreen(
                                        viewModel = viewModel,
                                        enabled = isSettingsActive,
                                        onBack = {
                                            scope.launch { horizontalPagerState.scrollToPageCompat(0, animationsEnabled, fastAnimSpec) }
                                        }
                                    )
                                }
                            }

                            LauncherDialogOverlays(
                                viewModel = viewModel,
                                showActionMenuFor = showActionMenuFor,
                                menuOffset = menuOffset,
                                showRenameDialogFor = showRenameDialogFor,
                                showTagDialogFor = showTagDialogFor,
                                tagToEditForDialog = tagToEditForDialog,
                                onDismissActionMenu = { showActionMenuFor = null },
                                onRequestRename = { showRenameDialogFor = it },
                                onRequestTags = { showTagDialogFor = it },
                                onDismissRename = { showRenameDialogFor = null },
                                onDismissTagDialog = { showTagDialogFor = null },
                                onDismissTagEdit = { tagToEditForDialog = null },
                                onUninstallApp = ::uninstallApp,
                                onOpenAppInfo = ::openAppInfo
                            )

                            TutorialOverlay(
                                viewModel = viewModel,
                                onNavigateToSearch = {
                                    scope.launch { verticalPagerState.scrollToPageCompat(1, animationsEnabled, fastAnimSpec) }
                                },
                                onNavigateToMain = {
                                    scope.launch { verticalPagerState.scrollToPageCompat(0, animationsEnabled, fastAnimSpec) }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun initAppWidgetsIfUnlocked() {
        if (isUserUnlockedSafe()) {
            initAppWidgets()
        } else if (unlockReceiver == null) {
            val filter = android.content.IntentFilter(Intent.ACTION_USER_UNLOCKED)
            val receiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == Intent.ACTION_USER_UNLOCKED) {
                        try { unregisterReceiver(this) } catch (_: Exception) {}
                        unlockReceiver = null
                        initAppWidgets()
                    }
                }
            }
            unlockReceiver = receiver
            androidx.core.content.ContextCompat.registerReceiver(
                this,
                receiver,
                filter,
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
    }

    private fun initAppWidgets() {
        try {
            if (appWidgetHostState.value == null) {
                val appCtx = applicationContext
                val host = dev.msbs.cyclauncher.widget.LauncherAppWidgetHost(appCtx, 1024)
                val manager = AppWidgetManager.getInstance(appCtx)
                appWidgetHostState.value = host
                appWidgetManagerState.value = manager
                viewModel.refreshWidgetProviderMapAsync()
                if (isHighlightScreenActive && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                    try {
                        host.startListening()
                    } catch (_: Exception) {}
                }
            }
            widgetUnlockGenerationState.intValue += 1
        } catch (_: Exception) {}
    }

    override fun onStart() {
        super.onStart()
        if (isHighlightScreenActive) {
            try {
                appWidgetHost?.startListening()
            } catch (_: Exception) {}
        }
    }

    override fun onPause() {
        super.onPause()
        if (pendingProfileLaunchComponent != null) {
            pendingProfileLaunchWasPaused = true
        }
        hideStatusBarRetryRunnable?.let {
            window.decorView.removeCallbacks(it)
            hideStatusBarRetryRunnable = null
        }
    }

    override fun onStop() {
        super.onStop()
        clearPendingProfileLaunch()
        try {
            appWidgetHost?.stopListening()
        } catch (_: Exception) {}
    }

    private var pendingProfileLaunchComponent: android.content.ComponentName? = null
    private var pendingProfileLaunchUser: android.os.UserHandle? = null
    private var pendingProfileLaunchTimeMs: Long = 0L
    private var pendingProfileLaunchRetried: Boolean = false
    private var pendingProfileLaunchWasPaused: Boolean = false
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var retryLaunchRunnable: Runnable? = null

    private fun clearPendingProfileLaunch() {
        retryLaunchRunnable?.let { mainHandler.removeCallbacks(it) }
        retryLaunchRunnable = null
        pendingProfileLaunchComponent = null
        pendingProfileLaunchUser = null
        pendingProfileLaunchTimeMs = 0L
        pendingProfileLaunchRetried = false
        pendingProfileLaunchWasPaused = false
    }

    private fun executeProfileLaunchRetry() {
        retryLaunchRunnable = null
        val comp = pendingProfileLaunchComponent
        val user = pendingProfileLaunchUser
        pendingProfileLaunchComponent = null
        pendingProfileLaunchUser = null
        if (comp != null && user != null && !isFinishing && !isDestroyed &&
            lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
        ) {
            val decor = window.decorView
            val width = decor.width.coerceAtLeast(1)
            val height = decor.height.coerceAtLeast(1)
            val optionsBundle = try {
                android.app.ActivityOptions.makeClipRevealAnimation(decor, width / 2, height / 2, width, height).toBundle()
            } catch (_: Exception) {
                null
            }
            try {
                val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as? android.content.pm.LauncherApps
                launcherApps?.startMainActivity(comp, user, null, optionsBundle)
            } catch (_: Exception) {}
        }
    }

    private val pendingWidgetConfigureCallbacks = mutableMapOf<Int, (Boolean) -> Unit>()
    private val configureRequestCodeToWidgetId = mutableMapOf<Int, Int>()
    private var nextConfigureRequestCode = REQUEST_CONFIGURE_WIDGET_BASE

    private fun allocateConfigureRequestCode(): Int {
        val span = REQUEST_CONFIGURE_WIDGET_MAX - REQUEST_CONFIGURE_WIDGET_BASE + 1
        repeat(span) {
            val candidate = nextConfigureRequestCode
            nextConfigureRequestCode = if (candidate >= REQUEST_CONFIGURE_WIDGET_MAX) {
                REQUEST_CONFIGURE_WIDGET_BASE
            } else {
                candidate + 1
            }
            if (!configureRequestCodeToWidgetId.containsKey(candidate)) {
                return candidate
            }
        }
        return REQUEST_CONFIGURE_WIDGET_BASE
    }

    private fun cancelPendingWidgetConfigurations() {
        val pending = pendingWidgetConfigureCallbacks.values.toList()
        pendingWidgetConfigureCallbacks.clear()
        configureRequestCodeToWidgetId.clear()
        pending.forEach { callback ->
            try {
                callback(false)
            } catch (_: Exception) {}
        }
    }

    fun startWidgetConfiguration(widgetId: Int, isReconfigure: Boolean, options: Bundle? = null, callback: (Boolean) -> Unit) {
        val host = appWidgetHost ?: run {
            callback(false)
            return
        }
        // Clean up any stale entry for the same widgetId before registering a new one
        configureRequestCodeToWidgetId.entries.removeAll { it.value == widgetId }
        pendingWidgetConfigureCallbacks.remove(widgetId)?.let { stale ->
            try { stale(false) } catch (_: Exception) {}
        }
        val requestCode = allocateConfigureRequestCode()
        pendingWidgetConfigureCallbacks[widgetId] = callback
        configureRequestCodeToWidgetId[requestCode] = widgetId
        try {
            host.startAppWidgetConfigureActivityForResult(
                this,
                widgetId,
                0,
                requestCode,
                options
            )
        } catch (_: Exception) {
            configureRequestCodeToWidgetId.remove(requestCode)
            pendingWidgetConfigureCallbacks.remove(widgetId)
            callback(false)
        }
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode in REQUEST_CONFIGURE_WIDGET_BASE..REQUEST_CONFIGURE_WIDGET_MAX) {
            val widgetId = configureRequestCodeToWidgetId.remove(requestCode)
            if (widgetId != null) {
                val callback = pendingWidgetConfigureCallbacks.remove(widgetId)
                callback?.invoke(resultCode == Activity.RESULT_OK)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (appWidgetHost == null && isUserUnlockedSafe()) {
            initAppWidgets()
        }
        viewModel.onUserUnlockedIfNeeded(application)
        if (pendingProfileLaunchComponent != null && pendingProfileLaunchWasPaused && !pendingProfileLaunchRetried) {
            val elapsed = android.os.SystemClock.elapsedRealtime() - pendingProfileLaunchTimeMs
            val targetUser = pendingProfileLaunchUser
            val userManager = getSystemService(Context.USER_SERVICE) as? android.os.UserManager
            val isStillQuiet = targetUser != null && try {
                userManager?.isQuietModeEnabled(targetUser) == true
            } catch (_: Throwable) {
                false
            }
            if (elapsed in 0L..600L && !isStillQuiet) {
                pendingProfileLaunchRetried = true
                retryLaunchRunnable?.let { mainHandler.removeCallbacks(it) }
                val runnable = Runnable { executeProfileLaunchRetry() }
                retryLaunchRunnable = runnable
                mainHandler.postDelayed(runnable, 100L)
            } else {
                clearPendingProfileLaunch()
            }
        }
        viewModel.refreshDynamicWallpaperColor(this)
        val shouldHideStatusBar = viewModel.hideStatusBar.value
        updateStatusBarVisibility(shouldHideStatusBar)
        scheduleDelayedStatusBarUpdate(shouldHideStatusBar)
        viewModel.updateDefaultLauncherStatus { isDefault ->
            val changed = isDefaultLauncherCached != isDefault
            isDefaultLauncherCached = isDefault
            if (changed) {
                viewModel.refreshApps()
            }
        }
        if (!viewModel.hasLoadedApps) {
            if (!viewModel.isAppsLoading) {
                viewModel.refreshApps()
            }
        } else {
            viewModel.checkProfileStateAsync()
            viewModel.prewarmActiveIcons()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            val shouldHideStatusBar = viewModel.hideStatusBar.value
            updateStatusBarVisibility(shouldHideStatusBar)
            scheduleDelayedStatusBarUpdate(shouldHideStatusBar)
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        val prevConfig = lastConfiguration
        val diff = prevConfig?.diff(newConfig) ?: -1
        lastConfiguration = Configuration(newConfig)

        if (diff == -1 || (diff and ActivityInfo.CONFIG_UI_MODE) != 0) {
            enableEdgeToEdge()
            val shouldHideStatusBar = viewModel.hideStatusBar.value
            updateStatusBarVisibility(shouldHideStatusBar)
            viewModel.refreshDynamicWallpaperColor(this)
        }

        if (diff == -1 || (diff and ActivityInfo.CONFIG_LOCALE) != 0) {
            viewModel.onLocaleChangedIfNeeded(newConfig)
        }

        val iconCacheMask = ActivityInfo.CONFIG_UI_MODE or
            ActivityInfo.CONFIG_DENSITY or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ActivityInfo.CONFIG_ASSETS_PATHS else 0
        if (diff == -1 || (diff and iconCacheMask) != 0) {
            viewModel.invalidateAllIconCaches()
        }
    }

    override fun onDestroy() {
        viewModel.setWidgetHostDeleter(null)
        cancelPendingWidgetConfigurations()
        clearPendingProfileLaunch()
        hideStatusBarRetryRunnable?.let {
            window.decorView.removeCallbacks(it)
            hideStatusBarRetryRunnable = null
        }
        unlockReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) {}
            unlockReceiver = null
        }
        try {
            unregisterReceiver(systemReceiver)
        } catch (_: Exception) {}
        launcherAppsCallback?.let { cb ->
            val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as? android.content.pm.LauncherApps
            try {
                launcherApps?.unregisterCallback(cb)
            } catch (_: Exception) {}
            launcherAppsCallback = null
        }
        launcherAppsHandlerThread?.quitSafely()
        launcherAppsHandlerThread = null
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1 && wallpaperColorsListener != null) {
            val wpManager = getSystemService(android.app.WallpaperManager::class.java)
            try {
                (wallpaperColorsListener as? android.app.WallpaperManager.OnColorsChangedListener)?.let {
                    wpManager?.removeOnColorsChangedListener(it)
                }
            } catch (_: Exception) {}
            wallpaperColorsListener = null
        }
        try {
            appWidgetHost?.stopListening()
        } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        clearPendingProfileLaunch()
        cancelPendingWidgetConfigurations()
        setIntent(intent)
        viewModel.requestReset()
    }

    private fun openApp(componentKey: String) {
        clearPendingProfileLaunch()
        viewModel.resetSearchFilters()
        viewModel.logAppLaunch(componentKey)
        viewModel.requestHistoryScrollToBottom()

        val decor = window.decorView
        val width = decor.width.coerceAtLeast(1)
        val height = decor.height.coerceAtLeast(1)
        val optionsBundle = try {
            android.app.ActivityOptions.makeClipRevealAnimation(decor, width / 2, height / 2, width, height).toBundle()
        } catch (_: Exception) {
            null
        }

        val parts = componentKey.substringBefore('#').split("/")
        if (parts.size == 2) {
            val packageName = parts[0]
            val activityName = parts[1]
            val componentName = android.content.ComponentName(packageName, activityName)

            val app = viewModel.apps.value.find { it.componentKey == componentKey }
            val userHandle = app?.userHandle ?: run {
                val profileIdPart = componentKey.substringAfter('#', "")
                if (profileIdPart.isNotEmpty()) {
                    val um = getSystemService(Context.USER_SERVICE) as? android.os.UserManager
                    val serialVal = profileIdPart.toLongOrNull()
                    val bySerial = if (serialVal != null) {
                        try { um?.getUserForSerialNumber(serialVal) } catch (_: Exception) { null }
                    } else null
                    val hashVal = profileIdPart.toIntOrNull()
                    bySerial
                        ?: (if (hashVal != null) um?.userProfiles?.find { it.hashCode() == hashVal } else null)
                        ?: android.os.Process.myUserHandle()
                } else {
                    android.os.Process.myUserHandle()
                }
            }

            val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as? android.content.pm.LauncherApps
            val launched = try {
                if (launcherApps != null) {
                    if (userHandle != android.os.Process.myUserHandle()) {
                        pendingProfileLaunchComponent = componentName
                        pendingProfileLaunchUser = userHandle
                        pendingProfileLaunchTimeMs = android.os.SystemClock.elapsedRealtime()
                        pendingProfileLaunchRetried = false
                        pendingProfileLaunchWasPaused = false
                    }
                    launcherApps.startMainActivity(componentName, userHandle, null, optionsBundle)
                    true
                } else false
            } catch (_: Exception) {
                clearPendingProfileLaunch()
                false
            }

            if (!launched) {
                if (userHandle == android.os.Process.myUserHandle()) {
                    try {
                        val intent = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_LAUNCHER)
                            component = componentName
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                        }
                        startActivity(intent, optionsBundle)
                    } catch (_: Exception) {
                        try {
                            packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
                                startActivity(intent, optionsBundle)
                            } ?: run {
                                Toast.makeText(this, getString(R.string.toast_open_app_failed), Toast.LENGTH_SHORT).show()
                            }
                        } catch (_: Exception) {
                            Toast.makeText(this, getString(R.string.toast_open_app_failed), Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(this, getString(R.string.toast_open_profile_app_failed), Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            try {
                packageManager.getLaunchIntentForPackage(componentKey)?.let { intent ->
                    startActivity(intent, optionsBundle)
                } ?: run {
                    Toast.makeText(this, getString(R.string.toast_open_app_failed), Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Toast.makeText(this, getString(R.string.toast_open_app_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun uninstallApp(app: AppInfo) {
        try {
            val uninstallIntent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.fromParts("package", app.packageName, null)
                if (app.userHandle != android.os.Process.myUserHandle()) {
                    putExtra(Intent.EXTRA_USER, app.userHandle)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            Toast.makeText(this, getString(R.string.toast_opening_uninstaller), Toast.LENGTH_SHORT).show()
            startActivity(uninstallIntent)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.toast_uninstaller_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun openAppInfo(app: AppInfo) {
        try {
            val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as? android.content.pm.LauncherApps
            if (launcherApps != null) {
                val componentName = android.content.ComponentName(app.packageName, app.activityName)
                launcherApps.startAppDetailsActivity(componentName, app.userHandle, null, null)
                return
            }
        } catch (_: Exception) {}

        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", app.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.toast_open_app_info_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private var cachedStatusBarService: Any? = null
    private var cachedExpandNotificationsMethod: java.lang.reflect.Method? = null
    private var cachedExpandSettingsMethod: java.lang.reflect.Method? = null
    private var isStatusBarReflectionInitialized = false

    @SuppressLint("WrongConstant")
    private fun ensureStatusBarReflection() {
        if (isStatusBarReflectionInitialized) return
        try {
            val service = getSystemService("statusbar")
            val managerClass = Class.forName("android.app.StatusBarManager")
            cachedStatusBarService = service
            cachedExpandNotificationsMethod = managerClass.methods.firstOrNull { it.name == "expandNotificationsPanel" }
                ?: managerClass.getMethod("expandNotificationsPanel")
            cachedExpandSettingsMethod = managerClass.methods.firstOrNull { it.name == "expandSettingsPanel" }
                ?: managerClass.getMethod("expandSettingsPanel")
        } catch (e: Exception) {
            android.util.Log.e("Cyclauncher", "Failed to cache StatusBarManager reflection", e)
        } finally {
            isStatusBarReflectionInitialized = true
        }
    }

    @SuppressLint("WrongConstant")
    private fun openNotifications() {
        android.util.Log.d("Cyclauncher", "openNotifications invoked")
        try {
            ensureStatusBarReflection()
            val service = cachedStatusBarService ?: getSystemService("statusbar")
            val method = cachedExpandNotificationsMethod
                ?: Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel")
            method.invoke(service)
            return
        } catch (e: Exception) {
            android.util.Log.e("Cyclauncher", "openNotifications failed via reflection", e)
        }

        try {
            @Suppress("DEPRECATION")
            sendBroadcast(Intent("android.intent.action.EXPAND_STATUS_BAR"))
        } catch (fallbackEx: Exception) {
            android.util.Log.e("Cyclauncher", "openNotifications fallback failed", fallbackEx)
        }
    }

    @SuppressLint("WrongConstant")
    private fun openQuickSettings() {
        android.util.Log.d("Cyclauncher", "openQuickSettings invoked")
        try {
            ensureStatusBarReflection()
            val service = cachedStatusBarService ?: getSystemService("statusbar")
            val method = cachedExpandSettingsMethod
                ?: Class.forName("android.app.StatusBarManager").getMethod("expandSettingsPanel")
            if (method.parameterTypes.isEmpty()) {
                method.invoke(service)
            } else {
                method.invoke(service, null as String?)
            }
            return
        } catch (e: Exception) {
            android.util.Log.e("Cyclauncher", "openQuickSettings failed via reflection", e)
        }

        try {
            val intent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
            } else {
                Intent(Settings.ACTION_SETTINGS)
            }
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (fallbackEx: Exception) {
            android.util.Log.e("Cyclauncher", "openQuickSettings fallback failed", fallbackEx)
        }
    }

    private fun updateStatusBarVisibility(hide: Boolean) {
        val decorView = window.decorView
        decorView.post {
            if (!isFinishing && !isDestroyed) {
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, decorView)
                if (hide) {
                    insetsController.systemBarsBehavior =
                        androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
                } else {
                    insetsController.systemBarsBehavior =
                        androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                    insetsController.show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
                }
            }
        }
    }

    private fun scheduleDelayedStatusBarUpdate(hide: Boolean) {
        if (!hide) return
        hideStatusBarRetryRunnable?.let { window.decorView.removeCallbacks(it) }
        val runnable = Runnable {
            if (!isFinishing && !isDestroyed) {
                updateStatusBarVisibility(true)
            }
        }
        hideStatusBarRetryRunnable = runnable
        window.decorView.postDelayed(runnable, 350L)
    }
}

@Composable
private fun CyclauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme { content() }
}

@Composable
private fun TagPreviewItemRow(
    color: Color,
    name: String,
    contentColor: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            name,
            color = contentColor,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun BaseLauncherConfirmDialog(
    title: String,
    confirmButtonText: String,
    accentColor: AccentColor,
    buttonTextColor: PrimaryTextColor = PrimaryTextColor.BLACK,
    popupTheme: PopupTheme = PopupTheme.DARK,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val dialogShape = RoundedCornerShape(20.dp)
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.border(1.dp, popupTheme.borderColor, dialogShape),
        tonalElevation = 0.dp,
        title = {
            Text(
                title,
                color = accentColor.color,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(content = content)
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor.color,
                    contentColor = buttonTextColor.color
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(confirmButtonText, color = buttonTextColor.color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.common_cancel), color = popupTheme.secondaryContentColor)
            }
        },
        containerColor = popupTheme.solidBackgroundColor,
        textContentColor = popupTheme.contentColor,
        shape = dialogShape
    )
}

@Composable
private fun AutoTagsConfirmDialog(
    preview: AutoTagsPreview,
    accentColor: AccentColor,
    buttonTextColor: PrimaryTextColor = PrimaryTextColor.BLACK,
    popupTheme: PopupTheme = PopupTheme.DARK,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    BaseLauncherConfirmDialog(
        title = stringResource(R.string.dialog_auto_tags_title),
        confirmButtonText = stringResource(R.string.dialog_auto_tags_confirm),
        accentColor = accentColor,
        buttonTextColor = buttonTextColor,
        popupTheme = popupTheme,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    ) {
        Text(
            stringResource(R.string.dialog_auto_tags_summary, preview.matchedAppsCount, preview.tags.size),
            color = popupTheme.contentColor.copy(alpha = 0.85f),
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 260.dp)
                .verticalScroll(rememberScrollState())
        ) {
            preview.tags.forEach { tagInfo ->
                TagPreviewItemRow(color = tagInfo.color, name = tagInfo.name, contentColor = popupTheme.contentColor)
            }
        }
        if (preview.unmatchedAppPackages.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.dialog_auto_tags_unmatched, preview.unmatchedAppPackages.size),
                color = popupTheme.secondaryContentColor,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun TagsBackupConfirmDialog(
    preview: TagsBackupPreview,
    accentColor: AccentColor,
    buttonTextColor: PrimaryTextColor = PrimaryTextColor.BLACK,
    popupTheme: PopupTheme = PopupTheme.DARK,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val newTagsLabel = stringResource(R.string.dialog_backup_new_tags, preview.newTags.size)
    val existingTagsLabel = stringResource(R.string.dialog_backup_existing_tags, preview.existingTagCount)
    val assignmentsLabel = stringResource(R.string.dialog_backup_assignments, preview.assignmentCount)
    val customLabelsLabel = stringResource(R.string.dialog_backup_custom_labels, preview.customLabels.size)
    val favoritesLabel = stringResource(R.string.dialog_backup_favorites, preview.favorites.size)
    val settingsLabel = stringResource(R.string.dialog_backup_settings, preview.settings.size)
    val charMappingsLabel = stringResource(R.string.dialog_backup_char_mappings, preview.customCharMappings.size)

    BaseLauncherConfirmDialog(
        title = stringResource(R.string.dialog_backup_import_title),
        confirmButtonText = stringResource(R.string.dialog_backup_import_confirm),
        accentColor = accentColor,
        buttonTextColor = buttonTextColor,
        popupTheme = popupTheme,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    ) {
        Text(
            buildString {
                if (preview.newTags.isNotEmpty() || preview.existingTagCount > 0 || preview.assignmentCount > 0) {
                    append(newTagsLabel)
                    append("\n").append(existingTagsLabel)
                    append("\n").append(assignmentsLabel)
                }
                if (preview.customLabels.isNotEmpty()) {
                    if (isNotEmpty()) append("\n")
                    append(customLabelsLabel)
                }
                if (preview.favorites.isNotEmpty()) {
                    if (isNotEmpty()) append("\n")
                    append(favoritesLabel)
                }
                if (preview.settings.isNotEmpty()) {
                    if (isNotEmpty()) append("\n")
                    append(settingsLabel)
                }
                if (preview.customCharMappings.isNotEmpty()) {
                    if (isNotEmpty()) append("\n")
                    append(charMappingsLabel)
                }
            },
            color = popupTheme.contentColor.copy(alpha = 0.85f),
            fontSize = 14.sp
        )
        if (preview.newTags.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                preview.newTags.take(12).forEach { tagInfo ->
                    TagPreviewItemRow(color = tagInfo.color, name = tagInfo.name, contentColor = popupTheme.contentColor)
                }
            }
            if (preview.newTags.size > 12) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    stringResource(R.string.dialog_backup_more_tags, preview.newTags.size - 12),
                    color = popupTheme.secondaryContentColor,
                    fontSize = 11.sp
                )
            }
        }
    }
}

private suspend fun androidx.compose.foundation.pager.PagerState.scrollToPageCompat(
    page: Int,
    animated: Boolean,
    animationSpec: androidx.compose.animation.core.AnimationSpec<Float>
) {
    if (animated) animateScrollToPage(page, animationSpec = animationSpec) else scrollToPage(page)
}

@Composable
private fun LauncherCompositionLocals(
    viewModel: LauncherViewModel,
    content: @Composable () -> Unit
) {
    val animationsEnabled by viewModel.animationsEnabled.collectAsState()
    val marqueeEnabled by viewModel.marqueeEnabled.collectAsState()
    val capsuleAlpha by viewModel.capsuleAlpha.collectAsState()
    val capsuleIsDark by viewModel.capsuleIsDark.collectAsState()
    val popupAlpha by viewModel.popupAlpha.collectAsState()
    val popupTheme by viewModel.popupTheme.collectAsState()
    val hapticFeedbackEnabled by viewModel.hapticFeedbackEnabled.collectAsState()
    val showShadows by viewModel.showShadows.collectAsState()
    val shadowColorOverride by viewModel.shadowColor.collectAsState()
    val iconPackVersion by viewModel.iconPackVersion.collectAsState()

    val currentHaptic = LocalHapticFeedback.current
    val customHaptic = remember(hapticFeedbackEnabled, currentHaptic) {
        object : androidx.compose.ui.hapticfeedback.HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                if (hapticFeedbackEnabled) {
                    currentHaptic.performHapticFeedback(hapticFeedbackType)
                }
            }
        }
    }
    val shadowSettings = remember(showShadows, shadowColorOverride) {
        dev.msbs.cyclauncher.ui.theme.ShadowSettings(showShadows, shadowColorOverride)
    }

    CompositionLocalProvider(
        LocalHapticFeedback provides customHaptic,
        dev.msbs.cyclauncher.ui.theme.LocalShadowSettings provides shadowSettings,
        dev.msbs.cyclauncher.ui.theme.LocalAnimationsEnabled provides animationsEnabled,
        dev.msbs.cyclauncher.ui.theme.LocalMarqueeEnabled provides marqueeEnabled,
        dev.msbs.cyclauncher.ui.theme.LocalCapsuleAlpha provides capsuleAlpha,
        dev.msbs.cyclauncher.ui.theme.LocalCapsuleIsDark provides capsuleIsDark,
        dev.msbs.cyclauncher.ui.theme.LocalPopupTheme provides popupTheme,
        dev.msbs.cyclauncher.ui.theme.LocalPopupAlpha provides popupAlpha,
        dev.msbs.cyclauncher.ui.theme.LocalIconPackVersion provides iconPackVersion,
        content = content
    )
}

@Composable
private fun StatusBarController(
    viewModel: LauncherViewModel,
    onUpdateStatusBar: (Boolean) -> Unit
) {
    val hideStatusBar by viewModel.hideStatusBar.collectAsState()
    val currentOnUpdate by rememberUpdatedState(onUpdateStatusBar)
    LaunchedEffect(hideStatusBar) {
        currentOnUpdate(hideStatusBar)
    }
}

@Composable
private fun ScreenScrimLayer(viewModel: LauncherViewModel) {
    val screenScrimAlpha by viewModel.screenScrimAlpha.collectAsState()
    val screenScrimIsDark by viewModel.screenScrimIsDark.collectAsState()
    ScreenScrim(alpha = screenScrimAlpha, isDark = screenScrimIsDark) {}
}

@Composable
private fun TutorialResetEffect(
    viewModel: LauncherViewModel,
    horizontalPagerState: androidx.compose.foundation.pager.PagerState,
    verticalPagerState: androidx.compose.foundation.pager.PagerState,
    onHideHighlight: () -> Unit
) {
    val showTutorial by viewModel.showTutorial.collectAsState()
    val currentOnHideHighlight by rememberUpdatedState(onHideHighlight)
    LaunchedEffect(showTutorial) {
        if (showTutorial) {
            horizontalPagerState.scrollToPage(0)
            verticalPagerState.scrollToPage(0)
            currentOnHideHighlight()
        }
    }
}

@Composable
private fun LauncherDialogOverlays(
    viewModel: LauncherViewModel,
    showActionMenuFor: AppInfo?,
    menuOffset: Offset,
    showRenameDialogFor: AppInfo?,
    showTagDialogFor: AppInfo?,
    tagToEditForDialog: Tag?,
    onDismissActionMenu: () -> Unit,
    onRequestRename: (AppInfo) -> Unit,
    onRequestTags: (AppInfo) -> Unit,
    onDismissRename: () -> Unit,
    onDismissTagDialog: () -> Unit,
    onDismissTagEdit: () -> Unit,
    onUninstallApp: (AppInfo) -> Unit,
    onOpenAppInfo: (AppInfo) -> Unit
) {
    val autoTagsPreview by viewModel.autoTagsPreview.collectAsState()
    val tagsBackupPreview by viewModel.tagsBackupPreview.collectAsState()

    val hasAnyOverlay = showActionMenuFor != null ||
        showRenameDialogFor != null ||
        showTagDialogFor != null ||
        tagToEditForDialog != null ||
        autoTagsPreview != null ||
        tagsBackupPreview != null

    if (!hasAnyOverlay) return

    ActiveLauncherDialogOverlays(
        viewModel = viewModel,
        showActionMenuFor = showActionMenuFor,
        menuOffset = menuOffset,
        showRenameDialogFor = showRenameDialogFor,
        showTagDialogFor = showTagDialogFor,
        tagToEditForDialog = tagToEditForDialog,
        autoTagsPreview = autoTagsPreview,
        tagsBackupPreview = tagsBackupPreview,
        onDismissActionMenu = onDismissActionMenu,
        onRequestRename = onRequestRename,
        onRequestTags = onRequestTags,
        onDismissRename = onDismissRename,
        onDismissTagDialog = onDismissTagDialog,
        onDismissTagEdit = onDismissTagEdit,
        onUninstallApp = onUninstallApp,
        onOpenAppInfo = onOpenAppInfo
    )
}

@Composable
private fun ActiveLauncherDialogOverlays(
    viewModel: LauncherViewModel,
    showActionMenuFor: AppInfo?,
    menuOffset: Offset,
    showRenameDialogFor: AppInfo?,
    showTagDialogFor: AppInfo?,
    tagToEditForDialog: Tag?,
    autoTagsPreview: AutoTagsPreview?,
    tagsBackupPreview: TagsBackupPreview?,
    onDismissActionMenu: () -> Unit,
    onRequestRename: (AppInfo) -> Unit,
    onRequestTags: (AppInfo) -> Unit,
    onDismissRename: () -> Unit,
    onDismissTagDialog: () -> Unit,
    onDismissTagEdit: () -> Unit,
    onUninstallApp: (AppInfo) -> Unit,
    onOpenAppInfo: (AppInfo) -> Unit
) {
    val accentColor by viewModel.accentColor.collectAsState()
    val buttonTextColor by viewModel.buttonTextColor.collectAsState()
    val popupTheme = dev.msbs.cyclauncher.ui.theme.LocalPopupTheme.current

    showActionMenuFor?.let { app ->
        val componentKey = app.componentKey
        AppActionMenu(
            app = app,
            isFavorite = viewModel.isFavorite(componentKey),
            offset = menuOffset,
            onDismiss = onDismissActionMenu,
            onToggleFavorite = { viewModel.toggleFavorite(componentKey) },
            onUninstall = { onUninstallApp(app) },
            onInfo = { onOpenAppInfo(app) },
            onRename = { onRequestRename(app) },
            onTagsClick = { onRequestTags(app) },
            accentColor = accentColor,
            popupTheme = popupTheme
        )
    }

    showRenameDialogFor?.let { app ->
        RenameDialog(
            initialValue = app.label,
            accentColor = accentColor,
            buttonTextColor = buttonTextColor,
            popupTheme = popupTheme,
            onDismiss = onDismissRename,
            onConfirm = { newName ->
                viewModel.renameApp(app.componentKey, newName)
                onDismissRename()
            }
        )
    }

    showTagDialogFor?.let { app ->
        val allTags by viewModel.tags.collectAsState()
        val appTagsMap by viewModel.appTags.collectAsState()
        val key = app.componentKey
        TagSelectionDialog(
            app = app,
            allTags = allTags,
            assignedTagIds = appTagsMap[key]
                ?: (if (app.profileType == dev.msbs.cyclauncher.model.ProfileType.PERSONAL) appTagsMap[app.packageName] else null)
                ?: emptyList(),
            onToggleTag = { tagId -> viewModel.toggleTagForApp(key, tagId) },
            onCreateTag = { name, color, emoji -> viewModel.createTag(Tag(name = name, color = color, emoji = emoji)) },
            onUpdateTag = { tag -> viewModel.updateTag(tag) },
            onDeleteTag = { tagId -> viewModel.deleteTag(tagId) },
            onDismiss = onDismissTagDialog,
            accentColor = accentColor,
            buttonTextColor = buttonTextColor,
            popupTheme = popupTheme
        )
    }

    tagToEditForDialog?.let { tag ->
        TagEditDialog(
            tag = tag,
            onDismiss = onDismissTagEdit,
            onConfirm = { name, color, emoji ->
                viewModel.updateTag(tag.copy(name = name, color = color, emoji = emoji))
                onDismissTagEdit()
            },
            onDelete = {
                viewModel.deleteTag(tag.id)
                onDismissTagEdit()
            },
            accentColor = accentColor,
            buttonTextColor = buttonTextColor,
            popupTheme = popupTheme
        )
    }

    autoTagsPreview?.let { preview ->
        AutoTagsConfirmDialog(
            preview = preview,
            accentColor = accentColor,
            buttonTextColor = buttonTextColor,
            popupTheme = popupTheme,
            onConfirm = { viewModel.applyAutoTags() },
            onDismiss = { viewModel.dismissAutoTagsPreview() }
        )
    }

    tagsBackupPreview?.let { preview ->
        TagsBackupConfirmDialog(
            preview = preview,
            accentColor = accentColor,
            buttonTextColor = buttonTextColor,
            popupTheme = popupTheme,
            onConfirm = { viewModel.applyTagsBackup() },
            onDismiss = { viewModel.dismissTagsBackupPreview() }
        )
    }
}
