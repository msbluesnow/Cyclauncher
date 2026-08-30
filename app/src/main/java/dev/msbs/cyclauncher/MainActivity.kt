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
import dev.msbs.cyclauncher.ui.components.TutorialOverlay
import dev.msbs.cyclauncher.ui.screens.MainMenuScreen
import dev.msbs.cyclauncher.ui.screens.SearchScreen
import dev.msbs.cyclauncher.ui.screens.SettingsScreen

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
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
    private var wallpaperColorsListener: Any? = null

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            val packageName = intent?.data?.schemeSpecificPart
            val isReplacing = intent?.getBooleanExtra(Intent.EXTRA_REPLACING, false) ?: false

            if (action == Intent.ACTION_PACKAGE_REMOVED && !isReplacing && !packageName.isNullOrEmpty()) {
                viewModel.onPackageRemoved(packageName)
            } else if (!packageName.isNullOrEmpty() && (action == Intent.ACTION_PACKAGE_ADDED || action == Intent.ACTION_PACKAGE_REPLACED)) {
                viewModel.onPackageAddedOrUpdated(packageName)
            } else {
                viewModel.refreshApps()
            }
        }
    }

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            viewModel.refreshApps()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val onBackPressedCallback = object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!isDefaultLauncherCached) {
                    finish()
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
        
        val packageFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(packageReceiver, packageFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(packageReceiver, packageFilter)
        }

        val systemFilter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_UNLOCKED)
            addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE)
            addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(systemReceiver, systemFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(systemReceiver, systemFilter)
        }
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            val wpManager = getSystemService(android.app.WallpaperManager::class.java)
            val listener = android.app.WallpaperManager.OnColorsChangedListener { _, _ ->
                viewModel.refreshDynamicWallpaperColor(this@MainActivity)
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
                val hideStatusBar by viewModel.hideStatusBar.collectAsState()
                val animationsEnabled by viewModel.animationsEnabled.collectAsState()
                val showShadows by viewModel.showShadows.collectAsState()
                val shadowColorOverride by viewModel.shadowColor.collectAsState()
                val iconPackVersion by viewModel.iconPackVersion.collectAsState()

                CompositionLocalProvider(
                    dev.msbs.cyclauncher.ui.theme.LocalShadowSettings provides dev.msbs.cyclauncher.ui.theme.ShadowSettings(showShadows, shadowColorOverride),
                    dev.msbs.cyclauncher.ui.theme.LocalAnimationsEnabled provides animationsEnabled,
                    dev.msbs.cyclauncher.ui.theme.LocalIconPackVersion provides iconPackVersion
                ) {
                    LaunchedEffect(hideStatusBar) {
                        updateStatusBarVisibility(hideStatusBar)
                    }

                    val horizontalPagerState = rememberPagerState { 2 }
                    val verticalPagerState = rememberPagerState { 2 }
                    val scope = rememberCoroutineScope()
                    val fastAnimSpec = remember { tween<Float>(durationMillis = 150, easing = FastOutSlowInEasing) }
                    
                    var showActionMenuFor by remember { mutableStateOf<AppInfo?>(null) }
                    var showRenameDialogFor by remember { mutableStateOf<AppInfo?>(null) }
                    var showTagDialogFor by remember { mutableStateOf<AppInfo?>(null) }
                    var tagToEditForDialog by remember { mutableStateOf<Tag?>(null) }
                    
                    var menuSource by remember { mutableStateOf("none") }
                    var menuOffset by remember { mutableStateOf(Offset.Zero) }

                    LaunchedEffect(Unit) {
                        viewModel.resetRequest.collect {
                            showActionMenuFor = null
                            showRenameDialogFor = null
                            showTagDialogFor = null
                            tagToEditForDialog = null
                            scope.launch {
                                if (horizontalPagerState.currentPage != 0) {
                                    horizontalPagerState.scrollToPage(0)
                                }
                                if (verticalPagerState.currentPage != 0) {
                                    verticalPagerState.scrollToPage(0)
                                }
                            }
                        }
                    }

                    val showTutorial by viewModel.showTutorial.collectAsState()

                    LaunchedEffect(showTutorial) {
                        if (showTutorial) {
                            horizontalPagerState.scrollToPage(0)
                            verticalPagerState.scrollToPage(0)
                        }
                    }

                    val isOnMainScreen by remember {
                        derivedStateOf {
                            horizontalPagerState.currentPage == 0 &&
                            verticalPagerState.currentPage == 0 &&
                            horizontalPagerState.targetPage == 0 &&
                            verticalPagerState.targetPage == 0
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
                    
                    val handSide by viewModel.handSide.collectAsState()
                    val accentColor by viewModel.accentColor.collectAsState()
                    val buttonTextColor by viewModel.buttonTextColor.collectAsState()
                    val popupTheme by viewModel.popupTheme.collectAsState()
                    val allTags by viewModel.tags.collectAsState()
                    val appTagsMap by viewModel.appTags.collectAsState()
                    val autoTagsPreview by viewModel.autoTagsPreview.collectAsState()
                    val tagsBackupPreview by viewModel.tagsBackupPreview.collectAsState()

                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color.Transparent
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            HorizontalPager(
                                state = horizontalPagerState,
                                modifier = Modifier.fillMaxSize(),
                                beyondViewportPageCount = 1,
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
                                                    scope.launch {
                                                        if (animationsEnabled) {
                                                            verticalPagerState.animateScrollToPage(1, animationSpec = fastAnimSpec)
                                                        } else {
                                                            verticalPagerState.scrollToPage(1)
                                                        }
                                                    }
                                                },
                                                onSwipeDown = ::openNotifications,
                                                onSettingsClick = {
                                                    scope.launch {
                                                        if (animationsEnabled) {
                                                            horizontalPagerState.animateScrollToPage(1, animationSpec = fastAnimSpec)
                                                        } else {
                                                            horizontalPagerState.scrollToPage(1)
                                                        }
                                                    }
                                                },
                                                onEditTag = { tag -> tagToEditForDialog = tag }
                                            )
                                        } else {
                                            SearchScreen(
                                                viewModel = viewModel,
                                                enabled = isSearchActive,
                                                onBackToMain = {
                                                    scope.launch {
                                                        if (animationsEnabled) {
                                                            verticalPagerState.animateScrollToPage(0, animationSpec = fastAnimSpec)
                                                        } else {
                                                            verticalPagerState.scrollToPage(0)
                                                        }
                                                    }
                                                },
                                                onAppClick = ::openApp,
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
                                            scope.launch {
                                                if (animationsEnabled) {
                                                    horizontalPagerState.animateScrollToPage(0, animationSpec = fastAnimSpec)
                                                } else {
                                                    horizontalPagerState.scrollToPage(0)
                                                }
                                            }
                                        }
                                    )
                                }
                            }

                            showActionMenuFor?.let { app ->
                                val componentKey = "${app.packageName}/${app.activityName}"
                                AppActionMenu(
                                    app = app,
                                    isFavorite = viewModel.isFavorite(componentKey),
                                    offset = menuOffset,
                                    onDismiss = { showActionMenuFor = null },
                                    onToggleFavorite = { viewModel.toggleFavorite(componentKey) },
                                    onUninstall = { uninstallApp(app.packageName) },
                                    onInfo = { openAppInfo(app.packageName) },
                                    onRename = { showRenameDialogFor = app },
                                    onTagsClick = { showTagDialogFor = app },
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
                                    onDismiss = { showRenameDialogFor = null },
                                    onConfirm = { newName ->
                                        viewModel.renameApp("${app.packageName}/${app.activityName}", newName)
                                        showRenameDialogFor = null
                                    }
                                )
                            }

                            showTagDialogFor?.let { app ->
                                val key = "${app.packageName}/${app.activityName}"
                                TagSelectionDialog(
                                    app = app,
                                    allTags = allTags,
                                    assignedTagIds = appTagsMap[key] ?: appTagsMap[app.packageName] ?: emptyList(),
                                    onToggleTag = { tagId -> viewModel.toggleTagForApp(key, tagId) },
                                    onCreateTag = { name, color -> viewModel.createTag(Tag(name = name, color = color)) },
                                    onUpdateTag = { tag -> viewModel.updateTag(tag) },
                                    onDeleteTag = { tagId -> viewModel.deleteTag(tagId) },
                                    onDismiss = { showTagDialogFor = null },
                                    accentColor = accentColor,
                                    buttonTextColor = buttonTextColor,
                                    popupTheme = popupTheme
                                )
                            }

                            tagToEditForDialog?.let { tag ->
                                TagEditDialog(
                                    tag = tag,
                                    onDismiss = { tagToEditForDialog = null },
                                    onConfirm = { name, color ->
                                        viewModel.updateTag(tag.copy(name = name, color = color))
                                        tagToEditForDialog = null
                                    },
                                    onDelete = {
                                        viewModel.deleteTag(tag.id)
                                        tagToEditForDialog = null
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

                            TutorialOverlay(
                                viewModel = viewModel,
                                onNavigateToSearch = {
                                    scope.launch {
                                        if (animationsEnabled) {
                                            verticalPagerState.animateScrollToPage(1, animationSpec = fastAnimSpec)
                                        } else {
                                            verticalPagerState.scrollToPage(1)
                                        }
                                    }
                                },
                                onNavigateToMain = {
                                    scope.launch {
                                        if (animationsEnabled) {
                                            verticalPagerState.animateScrollToPage(0, animationSpec = fastAnimSpec)
                                        } else {
                                            verticalPagerState.scrollToPage(0)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshDynamicWallpaperColor(this)
        updateStatusBarVisibility(viewModel.hideStatusBar.value)
        isDefaultLauncherCached = viewModel.isDefaultLauncher()
        if (viewModel.apps.value.isEmpty()) {
            viewModel.refreshApps()
        }
        viewModel.requestReset()
        viewModel.requestHistoryScrollToBottom()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        viewModel.refreshDynamicWallpaperColor(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(packageReceiver)
        unregisterReceiver(systemReceiver)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1 && wallpaperColorsListener != null) {
            val wpManager = getSystemService(android.app.WallpaperManager::class.java)
            try {
                (wallpaperColorsListener as? android.app.WallpaperManager.OnColorsChangedListener)?.let {
                    wpManager?.removeOnColorsChangedListener(it)
                }
            } catch (_: Exception) {}
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        isDefaultLauncherCached = viewModel.isDefaultLauncher()
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            if (!isDefaultLauncherCached) {
                finish()
                return
            }
        }
        viewModel.requestReset()
        viewModel.requestHistoryScrollToBottom()
    }

    private fun openApp(componentKey: String) {
        val parts = componentKey.split("/")
        if (parts.size == 2) {
            val packageName = parts[0]
            val activityName = parts[1]
            val componentName = android.content.ComponentName(packageName, activityName)

            viewModel.logAppLaunch(componentKey)
            viewModel.requestHistoryScrollToBottom()

            val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as? android.content.pm.LauncherApps
            var launched = false
            if (launcherApps != null) {
                try {
                    launcherApps.startMainActivity(componentName, android.os.Process.myUserHandle(), null, null)
                    launched = true
                } catch (_: Exception) {}
            }

            if (!launched) {
                try {
                    val intent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_LAUNCHER)
                        component = componentName
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
                        startActivity(intent)
                    }
                }
            }
        } else {
            viewModel.logAppLaunch(componentKey)
            viewModel.requestHistoryScrollToBottom()
            packageManager.getLaunchIntentForPackage(componentKey)?.let { intent ->
                startActivity(intent)
            }
        }
    }

    private fun uninstallApp(packageName: String) {
        try {
            val uninstallIntent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.fromParts("package", packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            Toast.makeText(this, "Opening uninstaller...", Toast.LENGTH_SHORT).show()
            startActivity(uninstallIntent)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open uninstaller", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openAppInfo(packageName: String) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open app info", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("WrongConstant")
    private fun openNotifications() {
        try {
            val statusBarService = getSystemService("statusbar")
            val statusBarManager = Class.forName("android.app.StatusBarManager")
            val expandMethod = statusBarManager.getMethod("expandNotificationsPanel")
            expandMethod.invoke(statusBarService)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateStatusBarVisibility(hide: Boolean) {
        val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (hide) {
            insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
        } else {
            insetsController.show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
        }
    }
}

@Composable
private fun CyclauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme { content() }
}

@Composable
private fun AutoTagsConfirmDialog(
    preview: AutoTagsPreview,
    accentColor: AccentColor,
    buttonTextColor: PrimaryTextColor = PrimaryTextColor.WHITE,
    popupTheme: PopupTheme = PopupTheme.DARK,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Apply Auto Tags?",
                color = accentColor.color,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    "${preview.matchedAppsCount} apps will be tagged into ${preview.tags.size} categories:",
                    color = popupTheme.contentColor.copy(alpha = 0.85f),
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                preview.tags.forEach { tagInfo ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(tagInfo.color)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            tagInfo.name,
                            color = popupTheme.contentColor,
                            fontSize = 13.sp
                        )
                    }
                }
                if (preview.unmatchedAppPackages.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "${preview.unmatchedAppPackages.size} apps not found on device",
                        color = popupTheme.secondaryContentColor,
                        fontSize = 11.sp
                    )
                }
            }
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
                Text("Apply", color = buttonTextColor.color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Cancel", color = popupTheme.secondaryContentColor)
            }
        },
        containerColor = popupTheme.solidBackgroundColor,
        textContentColor = popupTheme.contentColor,
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun TagsBackupConfirmDialog(
    preview: TagsBackupPreview,
    accentColor: AccentColor,
    buttonTextColor: PrimaryTextColor = PrimaryTextColor.WHITE,
    popupTheme: PopupTheme = PopupTheme.DARK,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Import Backup / Tags?",
                color = accentColor.color,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    buildString {
                        if (preview.newTags.isNotEmpty() || preview.existingTagCount > 0 || preview.assignmentCount > 0) {
                            append("New tags to create: ${preview.newTags.size}")
                            append("\nExisting tags kept: ${preview.existingTagCount}")
                            append("\nTag assignments: ${preview.assignmentCount}")
                        }
                        if (preview.customLabels.isNotEmpty()) {
                            if (isNotEmpty()) append("\n")
                            append("Custom labels to restore: ${preview.customLabels.size}")
                        }
                        if (preview.favorites.isNotEmpty()) {
                            if (isNotEmpty()) append("\n")
                            append("Favorites to restore: ${preview.favorites.size}")
                        }
                    },
                    color = popupTheme.contentColor.copy(alpha = 0.85f),
                    fontSize = 14.sp
                )
                if (preview.newTags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    preview.newTags.take(12).forEach { tagInfo ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(tagInfo.color)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                tagInfo.name,
                                color = popupTheme.contentColor,
                                fontSize = 13.sp
                            )
                        }
                    }
                    if (preview.newTags.size > 12) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "… and ${preview.newTags.size - 12} more",
                            color = popupTheme.secondaryContentColor,
                            fontSize = 11.sp
                        )
                    }
                }
            }
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
                Text("Import", color = buttonTextColor.color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Cancel", color = popupTheme.secondaryContentColor)
            }
        },
        containerColor = popupTheme.solidBackgroundColor,
        textContentColor = popupTheme.contentColor,
        shape = RoundedCornerShape(20.dp)
    )
}
