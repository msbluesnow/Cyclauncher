package dev.msbs.cyclauncher.ui.screens

import dev.msbs.cyclauncher.HandSide
import dev.msbs.cyclauncher.HighlightWidgetConfig
import dev.msbs.cyclauncher.LauncherViewModel
import dev.msbs.cyclauncher.model.AppInfo
import dev.msbs.cyclauncher.ui.components.CustomWidgetPickerSheet
import dev.msbs.cyclauncher.ui.components.rememberAppIconPainter
import dev.msbs.cyclauncher.ui.theme.AccentColor
import dev.msbs.cyclauncher.ui.theme.LocalAnimationsEnabled
import dev.msbs.cyclauncher.ui.theme.LocalShadowSettings
import dev.msbs.cyclauncher.ui.theme.PrimaryTextColor
import dev.msbs.cyclauncher.ui.theme.ShadowSettings
import dev.msbs.cyclauncher.widget.LauncherAppWidgetHost
import dev.msbs.cyclauncher.widget.LauncherAppWidgetHostView

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationEndReason
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog

/**
 * Calculates a native optimal height in DP for an AppWidget based on target cells or minHeight.
 */
private fun calculateOptimalWidgetHeight(info: AppWidgetProviderInfo?, density: Float): Int {
    if (info == null) return 150
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.targetCellHeight > 0) {
        return (info.targetCellHeight * 74).coerceIn(70, 420)
    }
    val minHeightDp = if (info.minHeight > 0) (info.minHeight / density).toInt() else 140
    val estimatedCells = kotlin.math.max(1, (minHeightDp + 15) / 65)
    return (estimatedCells * 74).coerceIn(minHeightDp.coerceAtLeast(70), 420)
}

/**
 * HighlightScreen: Secondary launcher screen with:
 * - Transparent live wallpaper background
 * - Custom in-app Widget Picker sheet with app search, accordion grouping, and graphical previews
 * - Real widget configuration activity launcher (fixing Yahoo Mail and other configure-required widgets)
 * - Pencil button (Icons.Outlined.Edit) for reconfiguring existing widgets
 * - Hand-side adaptive layout:
 *     - Left hand mode: Widgets dock flush right, leaving left corridor for thumb scrolling
 *     - Right hand mode: Widgets dock flush left, leaving right corridor for thumb scrolling
 * - Keyed widget lifecycle preventing deletion/replacement bugs
 * - Professional LauncherAppWidgetHost integration (zero padding, touch disallow parent intercept)
 * - Edge-only dismiss gesture to prevent interference with scrollable/interactive widgets
 * - Widget width & height resizing with continuous slider and presets
 * - Replicated tag-like animated delete line from title to trash icon
 */
@Composable
fun HighlightScreen(
    viewModel: LauncherViewModel,
    appWidgetHost: AppWidgetHost? = null,
    appWidgetManager: AppWidgetManager? = null,
    onClose: () -> Unit,
    onConfigureWidget: ((widgetId: Int, isReconfigure: Boolean, options: Bundle?, callback: (Boolean) -> Unit) -> Unit)? = null
) {
    val context = LocalContext.current
    val handSide by viewModel.handSide.collectAsState()
    val accentColor by viewModel.accentColor.collectAsState()
    val primaryTextColor by viewModel.primaryTextColor.collectAsState()
    val buttonTextColor by viewModel.buttonTextColor.collectAsState()
    val showShadows by viewModel.showShadows.collectAsState()
    val shadowSettings = LocalShadowSettings.current
    val animationsEnabled = LocalAnimationsEnabled.current

    val apps by viewModel.apps.collectAsState()
    val tags by viewModel.tags.collectAsState()
    val favoriteItems by viewModel.favoriteItems.collectAsState()
    val historyApps by viewModel.historyApps.collectAsState()
    val recentlyUpdatedKeys by viewModel.recentlyUpdatedApps.collectAsState()
    val highlightWidgets by viewModel.highlightWidgets.collectAsState()

    val viewConfiguration = LocalViewConfiguration.current
    val density = LocalDensity.current
    val closeSwipeThresholdPx = remember(density) { density.run { 50.dp.toPx() } }

    val host = appWidgetHost ?: remember { LauncherAppWidgetHost(context.applicationContext, 1024) }
    val manager = appWidgetManager ?: remember { AppWidgetManager.getInstance(context.applicationContext) }

    var showCustomWidgetPicker by remember { mutableStateOf(false) }
    var pendingBindWidgetId by remember { mutableIntStateOf(AppWidgetManager.INVALID_APPWIDGET_ID) }
    var pendingBindProvider by remember { mutableStateOf<AppWidgetProviderInfo?>(null) }

    // Today's Installs and Updates computed on background thread via LauncherViewModel (filtered for real user activity in last 24h)
    val todayActivity by viewModel.todayActivity.collectAsState()
    val (todayInstalls, todayUpdates) = todayActivity

    // Helper: checks if configuration is required and launches configure activity via host or adds directly
    val checkConfigureAndAdd: (Int, AppWidgetProviderInfo, Bundle?) -> Unit = { widgetId, providerInfo, optionsBundle ->
        if (providerInfo.configure != null && onConfigureWidget != null) {
            onConfigureWidget(widgetId, false, optionsBundle) { success ->
                if (success) {
                    val defaultHeight = calculateOptimalWidgetHeight(providerInfo, density.density)
                    viewModel.addHighlightWidget(widgetId, defaultHeight, 1.0f)
                } else {
                    try {
                        host.deleteAppWidgetId(widgetId)
                    } catch (_: Exception) {}
                }
            }
        } else {
            val defaultHeight = calculateOptimalWidgetHeight(providerInfo, density.density)
            viewModel.addHighlightWidget(widgetId, defaultHeight, 1.0f)
        }
    }

    // Activity result launcher for BIND_APPWIDGET permission dialog
    val bindWidgetLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && pendingBindWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            val provider = manager.getAppWidgetInfo(pendingBindWidgetId) ?: pendingBindProvider
            if (provider != null) {
                val displayDensity = context.resources.displayMetrics.density
                val optimalHeight = calculateOptimalWidgetHeight(provider, displayDensity)
                val screenWidthDp = (context.resources.displayMetrics.widthPixels / displayDensity).toInt()
                val targetWidthDp = (screenWidthDp * 1.0f).toInt() - 24
                val options = Bundle().apply {
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, targetWidthDp)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, targetWidthDp)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, optimalHeight)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, optimalHeight)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val sizes = arrayListOf(android.util.SizeF(targetWidthDp.toFloat(), optimalHeight.toFloat()))
                        putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, sizes)
                    }
                }
                checkConfigureAndAdd(pendingBindWidgetId, provider, options)
            }
        } else {
            if (pendingBindWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                try {
                    host.deleteAppWidgetId(pendingBindWidgetId)
                } catch (_: Exception) {}
            }
        }
        pendingBindWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        pendingBindProvider = null
    }

    // Callback when user taps a widget from the CustomWidgetPickerSheet
    val onSelectWidgetFromPicker: (AppWidgetProviderInfo) -> Unit = { providerInfo ->
        showCustomWidgetPicker = false
        val newWidgetId = host.allocateAppWidgetId()

        val displayDensity = context.resources.displayMetrics.density
        val optimalHeight = calculateOptimalWidgetHeight(providerInfo, displayDensity)
        val screenWidthDp = (context.resources.displayMetrics.widthPixels / displayDensity).toInt()
        val targetWidthDp = (screenWidthDp * 1.0f).toInt() - 24

        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, targetWidthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, targetWidthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, optimalHeight)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, optimalHeight)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val sizes = arrayListOf(android.util.SizeF(targetWidthDp.toFloat(), optimalHeight.toFloat()))
                putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, sizes)
            }
        }

        val canBind = try {
            val profile = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                providerInfo.profile ?: android.os.Process.myUserHandle()
            } else {
                null
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && profile != null) {
                manager.bindAppWidgetIdIfAllowed(newWidgetId, profile, providerInfo.provider, options)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                manager.bindAppWidgetIdIfAllowed(newWidgetId, providerInfo.provider, options)
            } else {
                manager.bindAppWidgetIdIfAllowed(newWidgetId, providerInfo.provider)
            }
        } catch (_: Exception) {
            false
        }

        try {
            manager.updateAppWidgetOptions(newWidgetId, options)
        } catch (_: Exception) {}

        if (canBind) {
            val finalInfo = manager.getAppWidgetInfo(newWidgetId) ?: providerInfo
            checkConfigureAndAdd(newWidgetId, finalInfo, options)
        } else {
            pendingBindWidgetId = newWidgetId
            pendingBindProvider = providerInfo
            try {
                val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, newWidgetId)
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, providerInfo.provider)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, providerInfo.profile ?: android.os.Process.myUserHandle())
                    }
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, options)
                }
                bindWidgetLauncher.launch(bindIntent)
            } catch (_: Exception) {
                checkConfigureAndAdd(newWidgetId, providerInfo, options)
            }
        }
    }

    BackHandler(onBack = onClose)

    // Transparent Surface so system wallpaper is directly visible underneath.
    // Edge-only swipe detection to ensure NO touch conflict with scrollable or interactive widgets!
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(handSide) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startX = down.position.x
                    val screenWidth = size.width.toFloat()

                    // Only track dismiss if gesture originates strictly at the screen edge (outer 8%)
                    val isEdgeStart = when (handSide) {
                        HandSide.RIGHT -> startX >= screenWidth * 0.92f
                        HandSide.LEFT -> startX <= screenWidth * 0.08f
                    }

                    if (isEdgeStart) {
                        var totalDragX = 0f
                        var totalDragY = 0f
                        var isHorizontal = false

                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break

                            val delta = change.positionChange()
                            totalDragX += delta.x
                            totalDragY += delta.y

                            if (!isHorizontal && kotlin.math.abs(totalDragX) > viewConfiguration.touchSlop &&
                                kotlin.math.abs(totalDragX) > kotlin.math.abs(totalDragY) * 1.3f
                            ) {
                                isHorizontal = true
                            }

                            if (isHorizontal) {
                                val isDismissDirection = when (handSide) {
                                    HandSide.RIGHT -> totalDragX < -closeSwipeThresholdPx
                                    HandSide.LEFT -> totalDragX > closeSwipeThresholdPx
                                }
                                if (isDismissDirection) {
                                    change.consume()
                                    onClose()
                                    break
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
            },
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top Bar matching Settings layout
            HighlightTopBar(
                title = "HIGHLIGHTS",
                handSide = handSide,
                accentColor = accentColor,
                primaryTextColor = primaryTextColor,
                showShadows = showShadows,
                shadowSettings = shadowSettings,
                onClose = onClose
            )

            // Scrollable Workspace Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Collapsible Overview Dashboard
                Box(modifier = Modifier.padding(horizontal = 24.dp)) {
                    CompactCollapsibleOverview(
                        totalApps = apps.size,
                        tagsCount = tags.size,
                        favoritesCount = favoriteItems.size,
                        historyCount = historyApps.size,
                        accentColor = accentColor,
                        primaryTextColor = primaryTextColor,
                        showShadows = showShadows,
                        shadowSettings = shadowSettings,
                        animationsEnabled = animationsEnabled
                    )
                }

                // Separate Collapsible Menu: Today's Installs
                Box(modifier = Modifier.padding(horizontal = 24.dp)) {
                    CollapsibleAppSection(
                        title = "TODAY'S INSTALLS",
                        icon = Icons.Outlined.Download,
                        apps = todayInstalls,
                        emptyMessage = "No new apps installed today",
                        accentColor = accentColor,
                        primaryTextColor = primaryTextColor,
                        showShadows = showShadows,
                        shadowSettings = shadowSettings,
                        animationsEnabled = animationsEnabled,
                        onAppClick = { componentKey ->
                            val parts = componentKey.split("/")
                            if (parts.size == 2) {
                                context.packageManager.getLaunchIntentForPackage(parts[0])?.let { intent ->
                                    context.startActivity(intent)
                                }
                            }
                        }
                    )
                }

                // Separate Collapsible Menu: Today's Updates
                Box(modifier = Modifier.padding(horizontal = 24.dp)) {
                    CollapsibleAppSection(
                        title = "TODAY'S UPDATES",
                        icon = Icons.Outlined.Update,
                        apps = todayUpdates,
                        emptyMessage = "No apps updated today",
                        accentColor = accentColor,
                        primaryTextColor = primaryTextColor,
                        showShadows = showShadows,
                        shadowSettings = shadowSettings,
                        animationsEnabled = animationsEnabled,
                        onAppClick = { componentKey ->
                            val parts = componentKey.split("/")
                            if (parts.size == 2) {
                                context.packageManager.getLaunchIntentForPackage(parts[0])?.let { intent ->
                                    context.startActivity(intent)
                                }
                            }
                        }
                    )
                }

                // Widgets Section with hand-side docking, custom picker, and pencil reconfigure button
                WidgetsSection(
                    widgetConfigs = highlightWidgets,
                    handSide = handSide,
                    appWidgetHost = host,
                    appWidgetManager = manager,
                    accentColor = accentColor,
                    buttonTextColor = buttonTextColor,
                    showShadows = showShadows,
                    shadowSettings = shadowSettings,
                    primaryTextColor = primaryTextColor,
                    animationsEnabled = animationsEnabled,
                    onAddWidget = { showCustomWidgetPicker = true },
                    onConfigureWidget = onConfigureWidget,
                    onUpdateSize = { widgetId, heightDp, widthFraction ->
                        viewModel.updateHighlightWidgetSize(widgetId, heightDp, widthFraction)
                    },
                    onDeleteWidget = { widgetId ->
                        viewModel.removeHighlightWidget(widgetId)
                        try {
                            host.deleteAppWidgetId(widgetId)
                        } catch (_: Exception) {}
                    }
                )

                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }

    // Custom In-App Widget Picker Sheet
    if (showCustomWidgetPicker) {
        CustomWidgetPickerSheet(
            apps = apps,
            accentColor = accentColor,
            primaryTextColor = primaryTextColor,
            showShadows = showShadows,
            onDismiss = { showCustomWidgetPicker = false },
            onSelectWidget = onSelectWidgetFromPicker
        )
    }
}

/**
 * Top bar matching the Settings page top bar pattern.
 */
@Composable
private fun HighlightTopBar(
    title: String,
    handSide: HandSide,
    accentColor: AccentColor,
    primaryTextColor: PrimaryTextColor,
    showShadows: Boolean,
    shadowSettings: ShadowSettings,
    onClose: () -> Unit
) {
    val shadow = primaryTextColor.getShadow(showShadows, shadowSettings.shadowColorOverride)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        val backIcon = when (handSide) {
            HandSide.RIGHT -> Icons.AutoMirrored.Outlined.ArrowForward
            HandSide.LEFT -> Icons.AutoMirrored.Outlined.ArrowBack
        }
        val buttonAlignment = if (handSide == HandSide.RIGHT) Alignment.CenterEnd else Alignment.CenterStart

        IconButton(
            onClick = onClose,
            modifier = Modifier.align(buttonAlignment)
        ) {
            ShadowedIcon(
                imageVector = backIcon,
                contentDescription = "Close Highlights",
                tint = accentColor.color,
                modifier = Modifier.size(24.dp),
                showShadows = showShadows,
                primaryTextColor = primaryTextColor,
                shadowSettings = shadowSettings
            )
        }

        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                shadow = shadow
            ),
            color = accentColor.color,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

/**
 * Compact Overview bar styled with Settings Card design logic.
 */
@Composable
private fun CompactCollapsibleOverview(
    totalApps: Int,
    tagsCount: Int,
    favoritesCount: Int,
    historyCount: Int,
    accentColor: AccentColor,
    primaryTextColor: PrimaryTextColor,
    showShadows: Boolean,
    shadowSettings: ShadowSettings,
    animationsEnabled: Boolean
) {
    var isExpanded by remember { mutableStateOf(false) }
    val shadow = primaryTextColor.getShadow(showShadows, shadowSettings.shadowColorOverride)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = primaryTextColor.color.copy(alpha = 0.05f)),
        border = BorderStroke(1.dp, primaryTextColor.color.copy(alpha = if (showShadows) 0.22f else 0.12f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "OVERVIEW",
                    color = accentColor.color,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    style = TextStyle(shadow = shadow)
                )

                ShadowedIcon(
                    imageVector = if (isExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse Overview" else "Expand Overview",
                    tint = primaryTextColor.color.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp),
                    showShadows = showShadows,
                    primaryTextColor = primaryTextColor,
                    shadowSettings = shadowSettings
                )
            }

            // Expanded Details (2x2 Dashboard grid of core launcher metrics)
            AnimatedVisibility(
                visible = isExpanded,
                enter = if (animationsEnabled) expandVertically(animationSpec = tween(180)) + fadeIn(animationSpec = tween(150)) else EnterTransition.None,
                exit = if (animationsEnabled) shrinkVertically(animationSpec = tween(150)) + fadeOut(animationSpec = tween(120)) else ExitTransition.None
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(
                        color = primaryTextColor.color.copy(alpha = 0.08f),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Row 1: Installed Apps & Tags
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CompactMetricItem(
                            modifier = Modifier.weight(1f),
                            label = "Installed Apps",
                            value = totalApps.toString(),
                            icon = Icons.Outlined.Apps,
                            accentColor = accentColor,
                            primaryTextColor = primaryTextColor,
                            showShadows = showShadows,
                            shadowSettings = shadowSettings
                        )
                        CompactMetricItem(
                            modifier = Modifier.weight(1f),
                            label = "Created Tags",
                            value = tagsCount.toString(),
                            icon = Icons.AutoMirrored.Outlined.Label,
                            accentColor = accentColor,
                            primaryTextColor = primaryTextColor,
                            showShadows = showShadows,
                            shadowSettings = shadowSettings
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Row 2: Favorites & Recent Launches
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CompactMetricItem(
                            modifier = Modifier.weight(1f),
                            label = "Favorites",
                            value = favoritesCount.toString(),
                            icon = Icons.Outlined.Favorite,
                            accentColor = accentColor,
                            primaryTextColor = primaryTextColor,
                            showShadows = showShadows,
                            shadowSettings = shadowSettings
                        )
                        CompactMetricItem(
                            modifier = Modifier.weight(1f),
                            label = "Recent Launches",
                            value = historyCount.toString(),
                            icon = Icons.Outlined.History,
                            accentColor = accentColor,
                            primaryTextColor = primaryTextColor,
                            showShadows = showShadows,
                            shadowSettings = shadowSettings
                        )
                    }
                }
            }
        }
    }
}

/**
 * Generic collapsible section for Today's Installs and Today's Updates.
 */
@Composable
private fun CollapsibleAppSection(
    title: String,
    icon: ImageVector,
    apps: List<AppInfo>,
    emptyMessage: String,
    accentColor: AccentColor,
    primaryTextColor: PrimaryTextColor,
    showShadows: Boolean,
    shadowSettings: ShadowSettings,
    animationsEnabled: Boolean,
    onAppClick: (String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val shadow = primaryTextColor.getShadow(showShadows, shadowSettings.shadowColorOverride)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = primaryTextColor.color.copy(alpha = 0.05f)),
        border = BorderStroke(1.dp, primaryTextColor.color.copy(alpha = if (showShadows) 0.22f else 0.12f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ShadowedIcon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor.color,
                        modifier = Modifier.size(18.dp),
                        showShadows = showShadows,
                        primaryTextColor = primaryTextColor,
                        shadowSettings = shadowSettings
                    )
                    Text(
                        text = title,
                        color = primaryTextColor.color,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        style = TextStyle(shadow = shadow)
                    )

                    // Count Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (apps.isNotEmpty()) accentColor.color.copy(alpha = 0.18f)
                                else primaryTextColor.color.copy(alpha = 0.08f)
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = apps.size.toString(),
                            color = if (apps.isNotEmpty()) accentColor.color else primaryTextColor.color.copy(alpha = 0.6f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            style = TextStyle(shadow = shadow)
                        )
                    }
                }

                ShadowedIcon(
                    imageVector = if (isExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = primaryTextColor.color.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp),
                    showShadows = showShadows,
                    primaryTextColor = primaryTextColor,
                    shadowSettings = shadowSettings
                )
            }

            // Expandable Apps List
            AnimatedVisibility(
                visible = isExpanded,
                enter = if (animationsEnabled) expandVertically(animationSpec = tween(180)) + fadeIn(animationSpec = tween(150)) else EnterTransition.None,
                exit = if (animationsEnabled) shrinkVertically(animationSpec = tween(150)) + fadeOut(animationSpec = tween(120)) else ExitTransition.None
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(
                        color = primaryTextColor.color.copy(alpha = 0.08f),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    if (apps.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(apps) { app ->
                                RecentAppChip(
                                    app = app,
                                    primaryTextColor = primaryTextColor,
                                    showShadows = showShadows,
                                    shadowSettings = shadowSettings,
                                    onClick = { onAppClick(app.componentKey) }
                                )
                            }
                        }
                    } else {
                        Text(
                            text = emptyMessage,
                            fontSize = 12.5.sp,
                            color = primaryTextColor.color.copy(alpha = 0.6f),
                            style = TextStyle(shadow = shadow),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactMetricItem(
    modifier: Modifier,
    label: String,
    value: String,
    icon: ImageVector,
    accentColor: AccentColor,
    primaryTextColor: PrimaryTextColor,
    showShadows: Boolean,
    shadowSettings: ShadowSettings
) {
    val shadow = primaryTextColor.getShadow(showShadows, shadowSettings.shadowColorOverride)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(primaryTextColor.color.copy(alpha = 0.06f))
            .border(1.dp, primaryTextColor.color.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ShadowedIcon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor.color,
                    modifier = Modifier.size(16.dp),
                    showShadows = showShadows,
                    primaryTextColor = primaryTextColor,
                    shadowSettings = shadowSettings
                )
                Text(
                    text = value,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryTextColor.color,
                    style = TextStyle(shadow = shadow)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 11.5.sp,
                color = primaryTextColor.color.copy(alpha = 0.7f),
                style = TextStyle(shadow = shadow)
            )
        }
    }
}

@Composable
private fun RecentAppChip(
    app: AppInfo,
    primaryTextColor: PrimaryTextColor,
    showShadows: Boolean,
    shadowSettings: ShadowSettings,
    onClick: () -> Unit
) {
    val painter = rememberAppIconPainter(app.componentKey, 24)
    val shadow = primaryTextColor.getShadow(showShadows, shadowSettings.shadowColorOverride)

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(primaryTextColor.color.copy(alpha = 0.08f))
            .border(1.dp, primaryTextColor.color.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
        )
        Text(
            text = app.label,
            fontSize = 12.5.sp,
            color = primaryTextColor.color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(shadow = shadow)
        )
    }
}

/**
 * Widgets section allowing user to host, render, customize size, and delete Android AppWidgets.
 * - Left hand mode: widgets shift completely to the RIGHT edge (padding start 56dp, end 0dp),
 *   leaving the left zone completely free for thumb scrolling.
 * - Right hand mode: widgets shift completely to the LEFT edge (padding start 0dp, end 56dp),
 *   leaving the right zone completely free for thumb scrolling.
 */
@Composable
private fun WidgetsSection(
    widgetConfigs: List<HighlightWidgetConfig>,
    handSide: HandSide,
    appWidgetHost: AppWidgetHost,
    appWidgetManager: AppWidgetManager,
    accentColor: AccentColor,
    buttonTextColor: PrimaryTextColor,
    showShadows: Boolean,
    shadowSettings: ShadowSettings,
    primaryTextColor: PrimaryTextColor,
    animationsEnabled: Boolean,
    onAddWidget: () -> Unit,
    onConfigureWidget: ((widgetId: Int, isReconfigure: Boolean, options: Bundle?, callback: (Boolean) -> Unit) -> Unit)? = null,
    onUpdateSize: (widgetId: Int, heightDp: Int, widthFraction: Float) -> Unit,
    onDeleteWidget: (Int) -> Unit
) {
    val shadow = primaryTextColor.getShadow(showShadows, shadowSettings.shadowColorOverride)

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Section Header with Launcher-Styled Add Button (centered with 24.dp margin)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ShadowedIcon(
                    imageVector = Icons.Outlined.Widgets,
                    contentDescription = null,
                    tint = accentColor.color,
                    modifier = Modifier.size(18.dp),
                    showShadows = showShadows,
                    primaryTextColor = primaryTextColor,
                    shadowSettings = shadowSettings
                )
                Text(
                    text = "WIDGETS",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = primaryTextColor.color,
                    style = TextStyle(shadow = shadow)
                )
            }

            // Launcher-styled Add Widget Button (Buttons do NOT inherit text/icon shadow)
            Button(
                onClick = onAddWidget,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor.color,
                    contentColor = buttonTextColor.color
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "Add Widget",
                    tint = buttonTextColor.color,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Add Widget",
                    color = buttonTextColor.color,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }

        // Empty state placeholder
        if (widgetConfigs.isEmpty()) {
            Box(modifier = Modifier.padding(horizontal = 24.dp)) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = primaryTextColor.color.copy(alpha = 0.05f)),
                    border = BorderStroke(1.dp, primaryTextColor.color.copy(alpha = if (showShadows) 0.22f else 0.12f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ShadowedIcon(
                                imageVector = Icons.Outlined.Widgets,
                                contentDescription = null,
                                tint = accentColor.color.copy(alpha = 0.7f),
                                modifier = Modifier.size(32.dp),
                                showShadows = showShadows,
                                primaryTextColor = primaryTextColor,
                                shadowSettings = shadowSettings
                            )
                            Text(
                                text = "No widgets added yet",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = primaryTextColor.color,
                                style = TextStyle(shadow = shadow)
                            )
                            Text(
                                text = "Tap Add Widget to browse widgets with previews and app search.",
                                fontSize = 12.sp,
                                color = primaryTextColor.color.copy(alpha = 0.7f),
                                lineHeight = 16.sp,
                                style = TextStyle(shadow = shadow)
                            )
                        }
                    }
                }
            }
        } else {
            // Adaptive hand-side padding:
            // Left Hand: widgets shifted flush right (padding start 56dp, end 0dp), leaving left corridor for thumb scrolling
            // Right Hand: widgets shifted flush left (padding start 0dp, end 56dp), leaving right corridor for thumb scrolling
            val handPadding = when (handSide) {
                HandSide.LEFT -> PaddingValues(start = 56.dp, end = 0.dp)
                HandSide.RIGHT -> PaddingValues(start = 0.dp, end = 56.dp)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(handPadding),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                for (config in widgetConfigs) {
                    key(config.id) {
                        WidgetCard(
                            config = config,
                            handSide = handSide,
                            appWidgetHost = appWidgetHost,
                            appWidgetManager = appWidgetManager,
                            accentColor = accentColor,
                            buttonTextColor = buttonTextColor,
                            primaryTextColor = primaryTextColor,
                            showShadows = showShadows,
                            shadowSettings = shadowSettings,
                            animationsEnabled = animationsEnabled,
                            onConfigureWidget = onConfigureWidget,
                            onUpdateSize = { heightDp, widthFraction ->
                                onUpdateSize(config.id, heightDp, widthFraction)
                            },
                            onDelete = { onDeleteWidget(config.id) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Individual widget host container with hand-side edge docking, pencil reconfigure button,
 * maximized widget area, and smooth hold-to-delete line animation.
 */
@Composable
private fun WidgetCard(
    config: HighlightWidgetConfig,
    handSide: HandSide,
    appWidgetHost: AppWidgetHost,
    appWidgetManager: AppWidgetManager,
    accentColor: AccentColor,
    buttonTextColor: PrimaryTextColor,
    primaryTextColor: PrimaryTextColor,
    showShadows: Boolean,
    shadowSettings: ShadowSettings,
    animationsEnabled: Boolean,
    onConfigureWidget: ((widgetId: Int, isReconfigure: Boolean, options: Bundle?, callback: (Boolean) -> Unit) -> Unit)? = null,
    onUpdateSize: (heightDp: Int, widthFraction: Float) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val widgetInfo = remember(config.id) {
        try {
            appWidgetManager.getAppWidgetInfo(config.id)
        } catch (_: Exception) {
            null
        }
    }

    if (widgetInfo == null) {
        LaunchedEffect(config.id) { onDelete() }
        return
    }

    val label = remember(widgetInfo) {
        try {
            widgetInfo.loadLabel(context.packageManager)
        } catch (_: Exception) {
            "Widget"
        }
    }

    // Keep reference to detach cleanly on disposal
    var currentHostView by remember { mutableStateOf<LauncherAppWidgetHostView?>(null) }
    DisposableEffect(config.id) {
        onDispose {
            (currentHostView?.parent as? ViewGroup)?.removeView(currentHostView)
            currentHostView = null
        }
    }

    var showResizeDialog by remember { mutableStateOf(false) }
    val shadow = primaryTextColor.getShadow(showShadows, shadowSettings.shadowColorOverride)

    // Hold-to-delete progress line state matching TagEditHeader
    val haptic = LocalHapticFeedback.current
    var isDeletePressed by remember { mutableStateOf(false) }
    val deleteProgress = remember { Animatable(0f) }
    val deleteColor = Color(0xFFEF4444)

    LaunchedEffect(isDeletePressed) {
        if (isDeletePressed) {
            val result = deleteProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = if (animationsEnabled) 1300 else 100,
                    easing = LinearEasing
                )
            )
            if (result.endReason == AnimationEndReason.Finished && deleteProgress.value >= 0.99f) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onDelete()
            }
        } else {
            deleteProgress.snapTo(0f)
        }
    }

    // Docking alignment and edge shape:
    // Left hand: card aligns right, flush to screen edge (right corners 0dp, left corners 14dp)
    // Right hand: card aligns left, flush to screen edge (left corners 0dp, right corners 14dp)
    val cardAlignment = when (handSide) {
        HandSide.LEFT -> Alignment.CenterEnd
        HandSide.RIGHT -> Alignment.CenterStart
    }
    val cardShape = when (handSide) {
        HandSide.LEFT -> RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp, topEnd = 0.dp, bottomEnd = 0.dp)
        HandSide.RIGHT -> RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 14.dp, bottomEnd = 14.dp)
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = cardAlignment
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(config.widthFraction),
            shape = cardShape,
            colors = CardDefaults.cardColors(containerColor = primaryTextColor.color.copy(alpha = 0.04f)),
            border = BorderStroke(0.8.dp, primaryTextColor.color.copy(alpha = if (showShadows) 0.20f else 0.10f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                // Header Row: Label, Animated Progress Line from Title to Recycle Bin, Configure Button, Resize Button, Trash Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = primaryTextColor.color.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(shadow = shadow)
                    )

                    // Animated Progress Line connecting Title to Trash Icon (appears/fills smoothly on hold)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                            .height(2.5.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (deleteProgress.value > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight()
                                    .clip(CircleShape)
                                    .background(deleteColor.copy(alpha = 0.18f))
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(deleteProgress.value)
                                    .fillMaxHeight()
                                    .clip(CircleShape)
                                    .background(deleteColor)
                            )
                        }
                    }

                    // Native Reconfigure Button (Pencil icon) if widget supports configuration
                    if (widgetInfo.configure != null && onConfigureWidget != null) {
                        IconButton(
                            onClick = {
                                onConfigureWidget(config.id, true, null) { /* Provider re-renders on result */ }
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            ShadowedIcon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = "Configure widget",
                                tint = primaryTextColor.color.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp),
                                showShadows = showShadows,
                                primaryTextColor = primaryTextColor,
                                shadowSettings = shadowSettings
                            )
                        }
                        Spacer(modifier = Modifier.width(2.dp))
                    }

                    // Resize Button
                    IconButton(
                        onClick = { showResizeDialog = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        ShadowedIcon(
                            imageVector = Icons.Outlined.AspectRatio,
                            contentDescription = "Resize widget",
                            tint = primaryTextColor.color.copy(alpha = 0.7f),
                            modifier = Modifier.size(17.dp),
                            showShadows = showShadows,
                            primaryTextColor = primaryTextColor,
                            shadowSettings = shadowSettings
                        )
                    }

                    Spacer(modifier = Modifier.width(2.dp))

                    // Safety Hold-To-Delete Recycle Bin Button
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (deleteProgress.value > 0f) deleteColor.copy(alpha = 0.2f) else Color.Transparent)
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    isDeletePressed = true
                                    waitForUpOrCancellation()
                                    isDeletePressed = false
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        ShadowedIcon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Hold to delete widget",
                            tint = if (deleteProgress.value > 0f) deleteColor else deleteColor.copy(alpha = 0.85f),
                            modifier = Modifier.size(17.dp),
                            showShadows = showShadows,
                            primaryTextColor = primaryTextColor,
                            shadowSettings = shadowSettings
                        )
                    }
                }

                // Native AppWidgetHostView taking maximum available space with zero-padding
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(config.heightDp.dp)
                        .clip(RoundedCornerShape(10.dp))
                ) {
                    AndroidView(
                        factory = { ctx ->
                            val view = (appWidgetHost.createView(ctx, config.id, widgetInfo) as? LauncherAppWidgetHostView)
                                ?: LauncherAppWidgetHostView(ctx).apply {
                                    setAppWidget(config.id, widgetInfo)
                                }
                            (view.parent as? ViewGroup)?.removeView(view)
                            currentHostView = view
                            view
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { view ->
                            val displayDensity = context.resources.displayMetrics.density
                            val screenWidthDp = (context.resources.displayMetrics.widthPixels / displayDensity).toInt()
                            val targetWidthDp = (screenWidthDp * config.widthFraction).toInt() - 24
                            view.applyWidgetSize(targetWidthDp, config.heightDp)
                        }
                    )
                }
            }
        }
    }

    if (showResizeDialog) {
        WidgetResizeDialog(
            widgetConfig = config,
            widgetLabel = label,
            accentColor = accentColor,
            primaryTextColor = primaryTextColor,
            buttonTextColor = buttonTextColor,
            showShadows = showShadows,
            shadowSettings = shadowSettings,
            onDismiss = { showResizeDialog = false },
            onApply = { newHeight, newWidth ->
                onUpdateSize(newHeight, newWidth)
            }
        )
    }
}

/**
 * Modal dialog allowing real-time adjustment of widget height and width.
 */
@Composable
private fun WidgetResizeDialog(
    widgetConfig: HighlightWidgetConfig,
    widgetLabel: String,
    accentColor: AccentColor,
    primaryTextColor: PrimaryTextColor,
    buttonTextColor: PrimaryTextColor,
    showShadows: Boolean,
    shadowSettings: ShadowSettings,
    onDismiss: () -> Unit,
    onApply: (heightDp: Int, widthFraction: Float) -> Unit
) {
    var heightDp by remember { mutableIntStateOf(widgetConfig.heightDp) }
    var widthFraction by remember { mutableFloatStateOf(widgetConfig.widthFraction) }
    val shadow = primaryTextColor.getShadow(showShadows, shadowSettings.shadowColorOverride)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = primaryTextColor.getShadowColor(shadowSettings.shadowColorOverride).copy(alpha = 0.95f)),
            border = BorderStroke(1.dp, primaryTextColor.color.copy(alpha = 0.2f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RESIZE WIDGET",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor.color,
                        style = TextStyle(shadow = shadow)
                    )
                    Text(
                        text = "${heightDp}dp • ${(widthFraction * 100).toInt()}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = primaryTextColor.color.copy(alpha = 0.7f),
                        style = TextStyle(shadow = shadow)
                    )
                }

                HorizontalDivider(color = primaryTextColor.color.copy(alpha = 0.12f))

                // Height Slider
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Height: $heightDp dp",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryTextColor.color,
                        style = TextStyle(shadow = shadow)
                    )
                    Slider(
                        value = heightDp.toFloat(),
                        onValueChange = { heightDp = it.toInt() },
                        valueRange = 70f..420f,
                        steps = 35,
                        colors = SliderDefaults.colors(
                            thumbColor = accentColor.color,
                            activeTrackColor = accentColor.color,
                            inactiveTrackColor = primaryTextColor.color.copy(alpha = 0.2f)
                        )
                    )

                    // Quick Height Presets Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val presets = listOf("Compact" to 80, "Medium" to 150, "Large" to 220, "Tall" to 320)
                        for ((name, valDp) in presets) {
                            val isSelected = heightDp == valDp
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) accentColor.color.copy(alpha = 0.25f) else primaryTextColor.color.copy(alpha = 0.08f))
                                    .border(1.dp, if (isSelected) accentColor.color else primaryTextColor.color.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                    .clickable { heightDp = valDp }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = name,
                                    fontSize = 10.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) accentColor.color else primaryTextColor.color,
                                    style = TextStyle(shadow = shadow)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = primaryTextColor.color.copy(alpha = 0.12f))

                // Width Options
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Width",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryTextColor.color,
                        style = TextStyle(shadow = shadow)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val widthOptions = listOf("Full (100%)" to 1.0f, "75%" to 0.75f, "Half (50%)" to 0.5f)
                        for ((wLabel, wFraction) in widthOptions) {
                            val isSelected = kotlin.math.abs(widthFraction - wFraction) < 0.05f
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) accentColor.color.copy(alpha = 0.25f) else primaryTextColor.color.copy(alpha = 0.08f))
                                    .border(1.dp, if (isSelected) accentColor.color else primaryTextColor.color.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                    .clickable { widthFraction = wFraction }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = wLabel,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) accentColor.color else primaryTextColor.color,
                                    style = TextStyle(shadow = shadow)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = "Cancel",
                            color = primaryTextColor.color.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onApply(heightDp, widthFraction)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentColor.color,
                            contentColor = buttonTextColor.color
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Apply",
                            color = buttonTextColor.color,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Reusable icon component with an adaptive drop shadow layer underneath.
 */
@Composable
private fun ShadowedIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    tint: Color,
    modifier: Modifier = Modifier,
    showShadows: Boolean,
    primaryTextColor: PrimaryTextColor,
    shadowSettings: ShadowSettings
) {
    Box(contentAlignment = Alignment.Center) {
        if (showShadows) {
            Icon(
                imageVector = imageVector,
                contentDescription = null,
                tint = primaryTextColor.getShadowColor(shadowSettings.shadowColorOverride).copy(alpha = 0.25f),
                modifier = modifier.offset(1.dp, 1.dp)
            )
        }
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = tint,
            modifier = modifier
        )
    }
}
