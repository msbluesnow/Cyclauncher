package dev.msbs.cyclauncher

import dev.msbs.cyclauncher.data.AutoTagsPreview
import dev.msbs.cyclauncher.data.TagsBackupPreview
import dev.msbs.cyclauncher.model.AppInfo
import dev.msbs.cyclauncher.model.Tag
import dev.msbs.cyclauncher.ui.theme.AccentColor
import dev.msbs.cyclauncher.ui.components.AppActionMenu
import dev.msbs.cyclauncher.ui.components.RenameDialog
import dev.msbs.cyclauncher.ui.components.TagSelectionDialog
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
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
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
 * The main activity of Cyclauncher, handling launcher setups, edge-to-edge layout,
 * broadcast receivers for package changes, and managing user interface navigation paths (horizontal/vertical pagers).
 */
class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()
    
    /**
     * BroadcastReceiver to dynamically refresh the app list when applications are installed,
     * uninstalled, or updated.
     */
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            viewModel.refreshApps()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(packageReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(packageReceiver, filter)
        }
        
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        window.setBackgroundDrawableResource(android.R.color.transparent)

        enableEdgeToEdge()

        setContent {
            CyclauncherTheme {
                val horizontalPagerState = rememberPagerState { 2 } // [MainCluster, Settings]
                val verticalPagerState = rememberPagerState { 2 } // [Main, Search]
                val scope = rememberCoroutineScope()
                
                BackHandler(enabled = horizontalPagerState.currentPage != 0 || verticalPagerState.currentPage != 0) {
                    scope.launch {
                        if (horizontalPagerState.currentPage != 0) {
                            horizontalPagerState.animateScrollToPage(0)
                        } else if (verticalPagerState.currentPage != 0) {
                            verticalPagerState.animateScrollToPage(0)
                        }
                    }
                }

                var showActionMenuFor by remember { mutableStateOf<AppInfo?>(null) }
                var showRenameDialogFor by remember { mutableStateOf<AppInfo?>(null) }
                var showTagDialogFor by remember { mutableStateOf<AppInfo?>(null) }
                
                var menuSource by remember { mutableStateOf("none") }
                var menuOffset by remember { mutableStateOf(Offset.Zero) }
                
                val haptic = LocalHapticFeedback.current
                val handSide by viewModel.handSide.collectAsState()
                val accentColor by viewModel.accentColor.collectAsState()
                val allTags by viewModel.tags.collectAsState()
                val appTagsMap by viewModel.appTags.collectAsState()
                val autoTagsPreview by viewModel.autoTagsPreview.collectAsState()
                val tagsBackupPreview by viewModel.tagsBackupPreview.collectAsState()

                LaunchedEffect(verticalPagerState.currentPage, horizontalPagerState.currentPage) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }

                // True only when the Main screen is the settled/active page of both pagers.
                // Used as a key for gesture pointerInputs so any in-progress gesture is
                // cancelled (its coroutine is disposed) the moment we navigate away —
                // preventing e.g. a long-press-into-settings from leaking a swipe-down
                // into the notification panel.
                val isOnMainScreen by remember {
                    derivedStateOf {
                        horizontalPagerState.currentPage == 0 &&
                        verticalPagerState.currentPage == 0 &&
                        horizontalPagerState.targetPage == 0 &&
                        verticalPagerState.targetPage == 0
                    }
                }

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
                                        MainMenuScreen(
                                            viewModel = viewModel,
                                            isActive = isOnMainScreen,
                                            onAppClick = ::openApp,
                                            onAppLongClick = { app, offset -> 
                                                showActionMenuFor = app
                                                menuOffset = offset
                                                menuSource = "history_or_favorites" 
                                            },
                                            onSwipeUp = {
                                                scope.launch { verticalPagerState.animateScrollToPage(1) }
                                            },
                                            onSwipeDown = ::openNotifications,
                                            onSettingsClick = {
                                                scope.launch { horizontalPagerState.animateScrollToPage(1) }
                                            }
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .pointerInput(Unit) {
                                                    detectTapGestures(
                                                        onLongPress = {
                                                            scope.launch {
                                                                horizontalPagerState.animateScrollToPage(1)
                                                            }
                                                        }
                                                    )
                                                }
                                                .pointerInput(handSide) {
                                                    var navigated = false
                                                    detectHorizontalDragGestures(
                                                        onDragStart = { navigated = false },
                                                        onHorizontalDrag = { _, dragAmount ->
                                                            if (!navigated) {
                                                                val isBackGesture = if (handSide == HandSide.LEFT) {
                                                                    dragAmount < -30
                                                                } else {
                                                                    dragAmount > 30 
                                                                }

                                                                if (isBackGesture) {
                                                                    navigated = true
                                                                    scope.launch {
                                                                        verticalPagerState.animateScrollToPage(0)
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    )
                                                }
                                        ) {
                                            SearchScreen(
                                                viewModel = viewModel,
                                                onAppClick = ::openApp,
                                                onAppLongClick = { app, offset -> 
                                                    showActionMenuFor = app
                                                    menuOffset = offset
                                                    menuSource = "search"
                                                }
                                            )
                                        }
                                    }
                                }
                            } else {
                                SettingsScreen(
                                    viewModel = viewModel,
                                    onBack = {
                                        scope.launch {
                                            horizontalPagerState.animateScrollToPage(0)
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
                                showRemoveFromHistory = menuSource == "history_or_favorites",
                                offset = menuOffset,
                                onDismiss = { showActionMenuFor = null },
                                onToggleFavorite = { viewModel.toggleFavorite(componentKey) },
                                onUninstall = { uninstallApp(app.packageName) },
                                onRemoveFromHistory = { viewModel.removeFromHistory(componentKey) },
                                onRename = { showRenameDialogFor = app },
                                onTagsClick = { showTagDialogFor = app },
                                accentColor = accentColor
                            )
                        }

                        showRenameDialogFor?.let { app ->
                            RenameDialog(
                                initialValue = app.label,
                                accentColor = accentColor,
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
                                assignedTagIds = appTagsMap[key] ?: emptyList(),
                                onToggleTag = { tagId -> viewModel.toggleTagForApp(key, tagId) },
                                onCreateTag = { name, color -> viewModel.createTag(Tag(name = name, color = color)) },
                                onUpdateTag = { tag -> viewModel.updateTag(tag) },
                                onDeleteTag = { tagId -> viewModel.deleteTag(tagId) },
                                onDismiss = { showTagDialogFor = null },
                                accentColor = accentColor
                            )
                        }

                        autoTagsPreview?.let { preview ->
                            AutoTagsConfirmDialog(
                                preview = preview,
                                accentColor = accentColor,
                                showShadows = viewModel.showShadows.collectAsState().value,
                                onConfirm = { viewModel.applyAutoTags() },
                                onDismiss = { viewModel.dismissAutoTagsPreview() }
                            )
                        }

                        tagsBackupPreview?.let { preview ->
                            TagsBackupConfirmDialog(
                                preview = preview,
                                accentColor = accentColor,
                                showShadows = viewModel.showShadows.collectAsState().value,
                                onConfirm = { viewModel.applyTagsBackup() },
                                onDismiss = { viewModel.dismissTagsBackupPreview() }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(packageReceiver)
    }

    /**
     * Attempts to open the application represented by the given component key.
     * Uses a specific class-name-based intent if possible, falling back to a package-launch intent.
     * Logs the application launch event inside the view model.
     *
     * @param componentKey The component key (formatted as "packageName/activityName" or package name).
     */
    private fun openApp(componentKey: String) {
        val parts = componentKey.split("/")
        if (parts.size == 2) {
            val packageName = parts[0]
            val activityName = parts[1]
            try {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    setClassName(packageName, activityName)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                }
                viewModel.logAppLaunch(componentKey)
                startActivity(intent)
            } catch (e: Exception) {
                packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
                    startActivity(intent)
                }
            }
        } else {
            packageManager.getLaunchIntentForPackage(componentKey)?.let { intent ->
                viewModel.logAppLaunch(componentKey)
                startActivity(intent)
            }
        }
    }

    /**
     * Launches the system delete package intent to uninstall the specified application.
     *
     * @param packageName The application package name to uninstall.
     */
    private fun uninstallApp(packageName: String) {
        try {
            val uninstallIntent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.fromParts("package", packageName, null)
            }
            Toast.makeText(this, "Opening uninstaller...", Toast.LENGTH_SHORT).show()
            startActivity(uninstallIntent)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open uninstaller", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Expands the system notification panel reflection-style (requires EXPAND_STATUS_BAR permission).
     */
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
}

/**
 * Custom MaterialTheme theme wrapper for Cyclauncher.
 */
@Composable
private fun CyclauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme { content() }
}

/**
 * Dialog prompting confirmation before applying automatic AI tags configuration.
 */
@Composable
private fun AutoTagsConfirmDialog(
    preview: AutoTagsPreview,
    accentColor: AccentColor,
    showShadows: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val shadow = if (showShadows) {
        androidx.compose.ui.graphics.Shadow(
            color = Color.Black.copy(alpha = 0.6f),
            offset = androidx.compose.ui.geometry.Offset(2f, 2f),
            blurRadius = 4f
        )
    } else null

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
                    color = Color.White.copy(alpha = 0.85f),
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
                            color = Color.White,
                            fontSize = 13.sp
                        )
                    }
                }
                if (preview.unmatchedAppPackages.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "${preview.unmatchedAppPackages.size} apps not found on device",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Apply", color = accentColor.color, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Gray)
            }
        },
        containerColor = Color(0xFF1E1E1E),
        textContentColor = Color.White
    )
}

/**
 * Dialog prompting confirmation before restoring tag configurations from backup.
 */
@Composable
private fun TagsBackupConfirmDialog(
    preview: TagsBackupPreview,
    accentColor: AccentColor,
    showShadows: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val shadow = if (showShadows) {
        androidx.compose.ui.graphics.Shadow(
            color = Color.Black.copy(alpha = 0.6f),
            offset = androidx.compose.ui.geometry.Offset(2f, 2f),
            blurRadius = 4f
        )
    } else null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Import Tags?",
                color = accentColor.color,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    buildString {
                        append("New tags to create: ${preview.newTags.size}")
                        append("\nExisting tags kept: ${preview.existingTagCount}")
                        append("\nTag assignments: ${preview.assignmentCount}")
                    },
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                    style = androidx.compose.ui.text.TextStyle(shadow = shadow)
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
                                color = Color.White,
                                fontSize = 13.sp
                            )
                        }
                    }
                    if (preview.newTags.size > 12) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "… and ${preview.newTags.size - 12} more",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Import", color = accentColor.color, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Gray)
            }
        },
        containerColor = Color(0xFF1E1E1E),
        textContentColor = Color.White
    )
}
