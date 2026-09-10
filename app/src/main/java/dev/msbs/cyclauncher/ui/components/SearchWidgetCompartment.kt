package dev.msbs.cyclauncher.ui.components

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Context
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.msbs.cyclauncher.HandSide
import dev.msbs.cyclauncher.LauncherViewModel
import dev.msbs.cyclauncher.widget.LauncherAppWidgetHostView

/**
 * Top compartment hosting two custom Android widgets side-by-side separated by a draggable divider.
 * Allows real-time split adjustment, widget selection, replacement, and removal.
 */
@Composable
fun SearchWidgetCompartment(
    viewModel: LauncherViewModel,
    appWidgetHost: AppWidgetHost?,
    appWidgetManager: AppWidgetManager?,
    onPickWidget: (isLeft: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    val config by viewModel.searchWidgetsConfig.collectAsState()
    val accentColor by viewModel.accentColor.collectAsState()
    val primaryTextColor by viewModel.primaryTextColor.collectAsState()
    val popupTheme by viewModel.popupTheme.collectAsState()

    var localRatio by remember(config.splitRatio) { mutableFloatStateOf(config.splitRatio) }
    var isDraggingDivider by remember { mutableStateOf(false) }
    var slotForOptions by remember { mutableStateOf<Boolean?>(null) }

    val manager = appWidgetManager ?: remember { AppWidgetManager.getInstance(context.applicationContext) }

    BoxWithConstraints(modifier = modifier) {
        val totalWidth = maxWidth
        val totalHeight = maxHeight
        val dividerWidth = 16.dp
        val availableWidth = (totalWidth - dividerWidth).coerceAtLeast(0.dp)
        val leftWidth = availableWidth * localRatio
        val rightWidth = (availableWidth - leftWidth).coerceAtLeast(0.dp)

        val totalAvailableWidthPx = with(density) { availableWidth.toPx() }
        val slotHeightPx = with(density) { totalHeight.toPx() }

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Widget Slot
            Box(
                modifier = Modifier
                    .width(leftWidth)
                    .fillMaxHeight()
            ) {
                SingleWidgetSlot(
                    isLeft = true,
                    widgetId = config.leftWidgetId,
                    appWidgetHost = appWidgetHost,
                    manager = manager,
                    slotWidthPx = with(density) { leftWidth.toPx() },
                    slotHeightPx = slotHeightPx,
                    accentColor = accentColor.color,
                    primaryTextColor = primaryTextColor.color,
                    onPick = { onPickWidget(true) },
                    onOptions = { slotForOptions = true }
                )
            }

            // Central Draggable Divider Line
            Box(
                modifier = Modifier
                    .width(dividerWidth)
                    .fillMaxHeight()
                    .pointerInput(totalAvailableWidthPx) {
                        detectTapGestures(
                            onDoubleTap = {
                                localRatio = 0.5f
                                viewModel.updateSearchWidgetSplitRatio(0.5f)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        )
                    }
                    .pointerInput(totalAvailableWidthPx) {
                        detectHorizontalDragGestures(
                            onDragStart = { isDraggingDivider = true },
                            onDragEnd = {
                                isDraggingDivider = false
                                viewModel.updateSearchWidgetSplitRatio(localRatio)
                            },
                            onDragCancel = {
                                isDraggingDivider = false
                                viewModel.updateSearchWidgetSplitRatio(localRatio)
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                if (totalAvailableWidthPx > 0f) {
                                    val deltaRatio = dragAmount / totalAvailableWidthPx
                                    val oldRatio = localRatio
                                    val newRatio = (oldRatio + deltaRatio).coerceIn(0.15f, 0.85f)
                                    // Subtle tactile click when passing through the balanced 50/50 point
                                    if ((oldRatio < 0.50f && newRatio >= 0.50f) || (oldRatio > 0.50f && newRatio <= 0.50f)) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    localRatio = newRatio
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(38.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (isDraggingDivider) {
                                accentColor.color.copy(alpha = 0.85f)
                            } else {
                                primaryTextColor.color.copy(alpha = 0.35f)
                            }
                        )
                )
            }

            // Right Widget Slot
            Box(
                modifier = Modifier
                    .width(rightWidth)
                    .fillMaxHeight()
            ) {
                SingleWidgetSlot(
                    isLeft = false,
                    widgetId = config.rightWidgetId,
                    appWidgetHost = appWidgetHost,
                    manager = manager,
                    slotWidthPx = with(density) { rightWidth.toPx() },
                    slotHeightPx = slotHeightPx,
                    accentColor = accentColor.color,
                    primaryTextColor = primaryTextColor.color,
                    onPick = { onPickWidget(false) },
                    onOptions = { slotForOptions = false }
                )
            }
        }
    }

    // Context options dialog for a configured widget
    slotForOptions?.let { isLeft ->
        val targetWidgetId = if (isLeft) config.leftWidgetId else config.rightWidgetId
        val widgetLabel = remember(targetWidgetId) {
            targetWidgetId?.let { id ->
                try {
                    manager.getAppWidgetInfo(id)?.loadLabel(context.packageManager)
                } catch (_: Exception) {
                    null
                }
            } ?: (if (isLeft) "Left Widget" else "Right Widget")
        }

        AlertDialog(
            onDismissRequest = { slotForOptions = null },
            containerColor = popupTheme.backgroundColor,
            title = {
                Text(
                    text = widgetLabel,
                    color = accentColor.color,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = {
                            slotForOptions = null
                            onPickWidget(isLeft)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(contentColor = popupTheme.contentColor)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Icon(Icons.Outlined.Edit, contentDescription = null, tint = accentColor.color)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Change Widget", fontSize = 15.sp)
                        }
                    }

                    TextButton(
                        onClick = {
                            slotForOptions = null
                            localRatio = 0.5f
                            viewModel.updateSearchWidgetSplitRatio(0.5f)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(contentColor = popupTheme.contentColor)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Icon(Icons.Outlined.RestartAlt, contentDescription = null, tint = accentColor.color)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Reset Split (50/50)", fontSize = 15.sp)
                        }
                    }

                    TextButton(
                        onClick = {
                            slotForOptions = null
                            targetWidgetId?.let { id ->
                                try {
                                    appWidgetHost?.deleteAppWidgetId(id)
                                } catch (_: Exception) {}
                            }
                            viewModel.removeSearchWidget(isLeft)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF5252))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = null, tint = Color(0xFFFF5252))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Remove Widget", fontSize = 15.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { slotForOptions = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = popupTheme.contentColor)
                ) {
                    Text("Close")
                }
            }
        )
    }
}

/**
 * Individual widget slot rendering either an interactive Android widget view or a "+ Add Widget" card.
 */
@Composable
private fun SingleWidgetSlot(
    isLeft: Boolean,
    widgetId: Int?,
    appWidgetHost: AppWidgetHost?,
    manager: AppWidgetManager,
    slotWidthPx: Float,
    slotHeightPx: Float,
    accentColor: Color,
    primaryTextColor: Color,
    onPick: () -> Unit,
    onOptions: () -> Unit
) {
    val context = LocalContext.current
    val widgetInfo = remember(widgetId) {
        widgetId?.let {
            try {
                manager.getAppWidgetInfo(it)
            } catch (_: Exception) {
                null
            }
        }
    }

    var cachedHostView by remember(widgetId) { mutableStateOf<LauncherAppWidgetHostView?>(null) }

    if (widgetId == null || widgetInfo == null) {
        // Empty Slot Placeholder
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(primaryTextColor.copy(alpha = 0.05f))
                .border(1.dp, primaryTextColor.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                .clickable(onClick = onPick),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "Add Widget",
                    tint = accentColor.copy(alpha = 0.8f),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Add Widget",
                    color = primaryTextColor.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    } else {
        // Active Embedded AppWidgetHostView
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
        ) {
            AndroidView(
                factory = { ctx ->
                    val view = cachedHostView ?: run {
                        val host = appWidgetHost
                        val newView = (host?.createView(ctx, widgetId, widgetInfo) as? LauncherAppWidgetHostView)
                            ?: LauncherAppWidgetHostView(ctx).apply {
                                setAppWidget(widgetId, widgetInfo)
                            }
                        cachedHostView = newView
                        newView
                    }
                    (view.parent as? ViewGroup)?.removeView(view)
                    view
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    val displayDensity = context.resources.displayMetrics.density
                    val targetWidthDp = (slotWidthPx / displayDensity).toInt().coerceAtLeast(30)
                    val targetHeightDp = (slotHeightPx / displayDensity).toInt().coerceAtLeast(30)
                    view.applyWidgetSize(targetWidthDp, targetHeightDp)
                }
            )

            // Discreet options button overlay in the upper corner
            Box(
                modifier = Modifier
                    .align(if (isLeft) Alignment.TopEnd else Alignment.TopStart)
                    .padding(4.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.38f))
                    .clickable(onClick = onOptions),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = "Widget Options",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

/**
 * Single widget slot component used in SideAlphabetSearchLayout.
 * When empty, shows "+ Add Widget" card.
 * When populated, shows the embedded LauncherAppWidgetHostView with an options button.
 * Remembers cached host view for instant re-attachment without IPC / RemoteViews re-inflation.
 */
@Composable
fun SideSearchWidgetSlot(
    viewModel: LauncherViewModel,
    appWidgetHost: AppWidgetHost?,
    appWidgetManager: AppWidgetManager?,
    onPickWidget: () -> Unit,
    handSide: HandSide = HandSide.RIGHT,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    val widgetId by viewModel.sideSearchWidgetId.collectAsState()
    val accentColor by viewModel.accentColor.collectAsState()
    val primaryTextColor by viewModel.primaryTextColor.collectAsState()
    val popupTheme by viewModel.popupTheme.collectAsState()

    var showOptions by remember { mutableStateOf(false) }

    val manager = appWidgetManager ?: remember { AppWidgetManager.getInstance(context.applicationContext) }

    val widgetInfo = remember(widgetId) {
        widgetId?.let {
            try {
                manager.getAppWidgetInfo(it)
            } catch (_: Exception) {
                null
            }
        }
    }

    // Cache the host view across letter switches to prevent heavy IPC & RemoteViews re-inflation
    var cachedHostView by remember(widgetId) { mutableStateOf<LauncherAppWidgetHostView?>(null) }

    val currentWidgetId = widgetId

    BoxWithConstraints(modifier = modifier) {
        val totalWidth = maxWidth
        val totalHeight = maxHeight
        val slotWidthPx = with(density) { totalWidth.toPx() }
        val slotHeightPx = with(density) { totalHeight.toPx() }

        if (currentWidgetId == null || widgetInfo == null) {
            // Empty placeholder card
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(primaryTextColor.color.copy(alpha = 0.05f))
                    .border(1.dp, primaryTextColor.color.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                    .clickable(onClick = onPickWidget),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "Add Widget",
                        tint = accentColor.color.copy(alpha = 0.8f),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Add Widget",
                        color = primaryTextColor.color.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // Active embedded widget
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
            ) {
                AndroidView(
                    factory = { ctx ->
                        cachedHostView ?: run {
                            val host = appWidgetHost
                            val view = (host?.createView(ctx, currentWidgetId, widgetInfo) as? LauncherAppWidgetHostView)
                                ?: LauncherAppWidgetHostView(ctx).apply {
                                    setAppWidget(currentWidgetId, widgetInfo)
                                }
                            (view.parent as? ViewGroup)?.removeView(view)
                            cachedHostView = view
                            view
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { view ->
                        val displayDensity = context.resources.displayMetrics.density
                        val targetWidthDp = (slotWidthPx / displayDensity).toInt().coerceAtLeast(30)
                        val targetHeightDp = (slotHeightPx / displayDensity).toInt().coerceAtLeast(30)
                        view.applyWidgetSize(targetWidthDp, targetHeightDp)
                    }
                )

                // Corner options button — positioned at the bottom on the hand-side edge
                Box(
                    modifier = Modifier
                        .align(if (handSide == HandSide.LEFT) Alignment.BottomStart else Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.38f))
                        .clickable { showOptions = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = "Widget Options",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }

    if (showOptions) {
        val widgetLabel = remember(widgetId) {
            widgetId?.let { id ->
                try {
                    manager.getAppWidgetInfo(id)?.loadLabel(context.packageManager)
                } catch (_: Exception) {
                    null
                }
            } ?: "Widget"
        }

        AlertDialog(
            onDismissRequest = { showOptions = false },
            containerColor = popupTheme.backgroundColor,
            title = {
                Text(
                    text = widgetLabel,
                    color = accentColor.color,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = {
                            showOptions = false
                            onPickWidget()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(contentColor = popupTheme.contentColor)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Icon(Icons.Outlined.Edit, contentDescription = null, tint = accentColor.color)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Change Widget", fontSize = 15.sp)
                        }
                    }

                    TextButton(
                        onClick = {
                            showOptions = false
                            widgetId?.let { id ->
                                try {
                                    appWidgetHost?.deleteAppWidgetId(id)
                                } catch (_: Exception) {}
                            }
                            cachedHostView = null
                            viewModel.removeSideSearchWidget()
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF5252))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = null, tint = Color(0xFFFF5252))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Remove Widget", fontSize = 15.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showOptions = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = popupTheme.contentColor)
                ) {
                    Text("Close")
                }
            }
        )
    }
}

/**
 * Embedded widget slot displayed above the alphabet grid in the side search layout.
 */
@Composable
fun SideAlphabetWidgetSlot(
    viewModel: LauncherViewModel,
    appWidgetHost: AppWidgetHost?,
    appWidgetManager: AppWidgetManager?,
    onPickWidget: () -> Unit,
    handSide: HandSide = HandSide.RIGHT,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    val widgetId by viewModel.sideAlphabetWidgetId.collectAsState()
    val accentColor by viewModel.accentColor.collectAsState()
    val primaryTextColor by viewModel.primaryTextColor.collectAsState()
    val popupTheme by viewModel.popupTheme.collectAsState()

    var showOptions by remember { mutableStateOf(false) }

    val manager = appWidgetManager ?: remember { AppWidgetManager.getInstance(context.applicationContext) }

    val widgetInfo = remember(widgetId) {
        widgetId?.let {
            try {
                manager.getAppWidgetInfo(it)
            } catch (_: Exception) {
                null
            }
        }
    }

    var cachedHostView by remember(widgetId) { mutableStateOf<LauncherAppWidgetHostView?>(null) }

    val currentWidgetId = widgetId

    BoxWithConstraints(modifier = modifier) {
        val totalWidth = maxWidth
        val totalHeight = maxHeight
        val slotWidthPx = with(density) { totalWidth.toPx() }
        val slotHeightPx = with(density) { totalHeight.toPx() }

        if (currentWidgetId == null || widgetInfo == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(primaryTextColor.color.copy(alpha = 0.05f))
                    .border(1.dp, primaryTextColor.color.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                    .clickable(onClick = onPickWidget),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "Add Widget",
                        tint = accentColor.color.copy(alpha = 0.8f),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Add Widget",
                        color = primaryTextColor.color.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
            ) {
                AndroidView(
                    factory = { ctx ->
                        cachedHostView ?: run {
                            val host = appWidgetHost
                            val view = (host?.createView(ctx, currentWidgetId, widgetInfo) as? LauncherAppWidgetHostView)
                                ?: LauncherAppWidgetHostView(ctx).apply {
                                    setAppWidget(currentWidgetId, widgetInfo)
                                }
                            (view.parent as? ViewGroup)?.removeView(view)
                            cachedHostView = view
                            view
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { view ->
                        val displayDensity = context.resources.displayMetrics.density
                        val targetWidthDp = (slotWidthPx / displayDensity).toInt().coerceAtLeast(30)
                        val targetHeightDp = (slotHeightPx / displayDensity).toInt().coerceAtLeast(30)
                        view.applyWidgetSize(targetWidthDp, targetHeightDp)
                    }
                )

                // Options button — bottom, hand-side edge
                Box(
                    modifier = Modifier
                        .align(if (handSide == HandSide.LEFT) Alignment.BottomStart else Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.38f))
                        .clickable { showOptions = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = "Widget Options",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }

    if (showOptions) {
        val widgetLabel = remember(widgetId) {
            widgetId?.let { id ->
                try {
                    manager.getAppWidgetInfo(id)?.loadLabel(context.packageManager)
                } catch (_: Exception) {
                    null
                }
            } ?: "Widget"
        }

        AlertDialog(
            onDismissRequest = { showOptions = false },
            containerColor = popupTheme.backgroundColor,
            title = {
                Text(
                    text = widgetLabel,
                    color = accentColor.color,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = {
                            showOptions = false
                            onPickWidget()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(contentColor = popupTheme.contentColor)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Icon(Icons.Outlined.Edit, contentDescription = null, tint = accentColor.color)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Change Widget", fontSize = 15.sp)
                        }
                    }

                    TextButton(
                        onClick = {
                            showOptions = false
                            widgetId?.let { id ->
                                try {
                                    appWidgetHost?.deleteAppWidgetId(id)
                                } catch (_: Exception) {}
                            }
                            cachedHostView = null
                            viewModel.removeSideAlphabetWidget()
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF5252))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = null, tint = Color(0xFFFF5252))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Remove Widget", fontSize = 15.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showOptions = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = popupTheme.contentColor)
                ) {
                    Text("Close")
                }
            }
        )
    }
}
