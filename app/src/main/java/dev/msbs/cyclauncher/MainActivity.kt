package dev.msbs.cyclauncher

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
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()
    
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
        registerReceiver(packageReceiver, filter)
        
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
                var showActionMenuFor by remember { mutableStateOf<AppInfo?>(null) }
                var menuSource by remember { mutableStateOf("none") }
                var menuOffset by remember { mutableStateOf(Offset.Zero) }
                val haptic = LocalHapticFeedback.current
                val handSide by viewModel.handSide.collectAsState()
                val accentColor by viewModel.accentColor.collectAsState()
                val showShadows by viewModel.showShadows.collectAsState()

                LaunchedEffect(verticalPagerState.currentPage, horizontalPagerState.currentPage) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                                        ) {
                                            MainMenuScreen(
                                                viewModel = viewModel,
                                                onAppClick = ::openApp,
                                                onAppLongClick = { app, offset -> 
                                                    showActionMenuFor = app
                                                    menuOffset = offset
                                                    menuSource = "history_or_favorites" 
                                                },
                                                onSwipeUp = {
                                                    scope.launch { verticalPagerState.animateScrollToPage(1) }
                                                },
                                                onSwipeDown = ::openNotifications
                                            )
                                        }
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
                                                .pointerInput(Unit) {
                                                    detectHorizontalDragGestures { _, dragAmount ->
                                                        if (dragAmount < -45) {
                                                            scope.launch {
                                                                verticalPagerState.animateScrollToPage(0)
                                                            }
                                                        }
                                                    }
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
                                accentColor = accentColor
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

@Composable
private fun CyclauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme { content() }
}
