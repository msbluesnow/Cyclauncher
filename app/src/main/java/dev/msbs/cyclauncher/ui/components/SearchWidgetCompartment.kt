package dev.msbs.cyclauncher.ui.components

import dev.msbs.cyclauncher.ui.theme.capsuleColor
import dev.msbs.cyclauncher.ui.theme.capsuleBorderColor
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Context
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.SwapVert
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.res.stringResource
import dev.msbs.cyclauncher.R
import dev.msbs.cyclauncher.HandSide
import dev.msbs.cyclauncher.LauncherViewModel
import dev.msbs.cyclauncher.SideDualWidgetConfig
import dev.msbs.cyclauncher.ui.theme.AccentColor
import dev.msbs.cyclauncher.ui.theme.PrimaryTextColor
import dev.msbs.cyclauncher.ui.theme.PopupTheme
import dev.msbs.cyclauncher.ui.theme.ShadowSettings
import dev.msbs.cyclauncher.ui.theme.LocalShadowSettings
import dev.msbs.cyclauncher.widget.LauncherAppWidgetHostView
import kotlin.math.roundToInt

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
        val defaultTitle = if (isLeft) stringResource(R.string.widget_left_title) else stringResource(R.string.widget_right_title)
        val customLabel = remember(targetWidgetId) {
            targetWidgetId?.let { id ->
                try {
                    manager.getAppWidgetInfo(id)?.loadLabel(context.packageManager)
                } catch (_: Exception) {
                    null
                }
            }
        }
        val widgetLabel = customLabel ?: defaultTitle
        val dialogShape = RoundedCornerShape(20.dp)

        AlertDialog(
            onDismissRequest = { slotForOptions = null },
            modifier = Modifier.border(1.dp, popupTheme.borderColor, dialogShape),
            tonalElevation = 0.dp,
            containerColor = popupTheme.backgroundColor,
            shape = dialogShape,
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
                            Text(stringResource(R.string.widget_action_change), fontSize = 15.sp)
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
                            Text(stringResource(R.string.widget_action_reset_split), fontSize = 15.sp)
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
                            Text(stringResource(R.string.widget_action_remove), fontSize = 15.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { slotForOptions = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = popupTheme.contentColor)
                ) {
                    Text(stringResource(R.string.common_close))
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
                .background(dev.msbs.cyclauncher.ui.theme.capsuleFill(primaryTextColor, 0.05f))
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
                    contentDescription = stringResource(R.string.widget_action_add),
                    tint = accentColor.copy(alpha = 0.8f),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.widget_action_add),
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
                    try {
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
                    } catch (_: Throwable) {
                        android.widget.FrameLayout(ctx)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    try {
                        val displayDensity = context.resources.displayMetrics.density
                        val targetWidthDp = (slotWidthPx / displayDensity).toInt().coerceAtLeast(30)
                        val targetHeightDp = (slotHeightPx / displayDensity).toInt().coerceAtLeast(30)
                        (view as? LauncherAppWidgetHostView)?.applyWidgetSize(targetWidthDp, targetHeightDp)
                    } catch (_: Throwable) {}
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
                    contentDescription = stringResource(R.string.widget_action_options),
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

/**
 * Unified slot component hosting up to two custom Android widgets arranged vertically.
 * Allows independent/split ratio adjustment, drag-to-reorder, drag-to-move, resizing,
 * adding a second widget, swapping positions, and removing individual widgets.
 */
@Composable
fun SideDualWidgetSlot(
    config: SideDualWidgetConfig,
    appWidgetHost: AppWidgetHost?,
    appWidgetManager: AppWidgetManager?,
    onPickWidget: (isTop: Boolean) -> Unit,
    onUpdateSplitRatio: (Float) -> Unit,
    onSwapWidgets: () -> Unit,
    onUpdateSize: (heightDp: Int?, widthFraction: Float) -> Unit,
    onUpdateOffset: (offsetX: Float, offsetY: Float) -> Unit,
    onResetLayout: () -> Unit,
    onRemoveWidget: (isTop: Boolean) -> Unit,
    accentColor: AccentColor,
    primaryTextColor: PrimaryTextColor,
    buttonTextColor: PrimaryTextColor,
    popupTheme: PopupTheme,
    showShadows: Boolean,
    shadowSettings: ShadowSettings,
    handSide: HandSide = HandSide.RIGHT,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    val manager = appWidgetManager ?: remember { AppWidgetManager.getInstance(context.applicationContext) }

    val topWidgetId = config.topWidgetId
    val bottomWidgetId = config.bottomWidgetId
    val hasTop = topWidgetId != null
    val hasBottom = bottomWidgetId != null
    val isDual = hasTop && hasBottom
    val hasAny = hasTop || hasBottom

    var slotForOptions by remember { mutableStateOf<Boolean?>(null) }
    var showResize by remember { mutableStateOf(false) }
    var isMoving by remember(config.topWidgetId, config.bottomWidgetId) { mutableStateOf(false) }

    var liveOffsetX by remember { mutableStateOf<Float?>(null) }
    var liveOffsetY by remember { mutableStateOf<Float?>(null) }

    BackHandler(enabled = isMoving) { isMoving = false }

    val layout = config.layout
    val currentLayout by rememberUpdatedState(layout)

    var cachedTopHostView by remember(topWidgetId) { mutableStateOf<LauncherAppWidgetHostView?>(null) }
    var cachedBottomHostView by remember(bottomWidgetId) { mutableStateOf<LauncherAppWidgetHostView?>(null) }

    val topInfo = remember(topWidgetId) {
        topWidgetId?.let {
            try { manager.getAppWidgetInfo(it) } catch (_: Exception) { null }
        }
    }
    val bottomInfo = remember(bottomWidgetId) {
        bottomWidgetId?.let {
            try { manager.getAppWidgetInfo(it) } catch (_: Exception) { null }
        }
    }

    BoxWithConstraints(
        modifier = modifier.then(
            if (isMoving) Modifier.pointerInput(Unit) { detectTapGestures { isMoving = false } } else Modifier
        )
    ) {
        val totalWidth = maxWidth
        val totalHeight = maxHeight

        if (!hasAny || (hasTop && topInfo == null && !hasBottom) || (hasBottom && bottomInfo == null && !hasTop)) {
            // Empty placeholder card
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(primaryTextColor.capsuleColor(0.08f))
                    .border(1.dp, primaryTextColor.capsuleBorderColor(0.12f), RoundedCornerShape(12.dp))
                    .clickable { onPickWidget(true) },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = stringResource(R.string.widget_action_add),
                        tint = accentColor.color.copy(alpha = 0.8f),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.widget_action_add),
                        color = primaryTextColor.color.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            val widgetWidth = totalWidth * layout.widthFraction
            val widgetHeight = layout.heightDp?.dp?.coerceAtMost(totalHeight) ?: totalHeight
            val freeWidthPx = with(density) { (totalWidth - widgetWidth).toPx() }.coerceAtLeast(0f)
            val freeHeightPx = with(density) { (totalHeight - widgetHeight).toPx() }.coerceAtLeast(0f)
            val offsetFractionX = liveOffsetX ?: layout.offsetX
            val offsetFractionY = liveOffsetY ?: layout.offsetY

            val currentFreeWidthPx by rememberUpdatedState(freeWidthPx)
            val currentFreeHeightPx by rememberUpdatedState(freeHeightPx)

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (freeWidthPx * offsetFractionX).roundToInt(),
                            (freeHeightPx * offsetFractionY).roundToInt()
                        )
                    }
                    .size(widgetWidth, widgetHeight)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                if (isDual && topInfo != null && bottomInfo != null) {
                    val dividerHeight = 16.dp
                    val availableHeight = (widgetHeight - dividerHeight).coerceAtLeast(0.dp)
                    val availableHeightPx = with(density) { availableHeight.toPx() }

                    var localRatio by remember(config.splitRatio) { mutableFloatStateOf(config.splitRatio) }
                    var isDraggingDivider by remember { mutableStateOf(false) }

                    LaunchedEffect(config.splitRatio) {
                        localRatio = config.splitRatio
                    }

                    val minHeightPx = with(density) { 40.dp.toPx() }
                    val minRatio = if (availableHeightPx > 0f) (minHeightPx / availableHeightPx).coerceIn(0.12f, 0.40f) else 0.15f
                    val maxRatio = 1f - minRatio

                    val topHeight = availableHeight * localRatio
                    val bottomHeight = (availableHeight - topHeight).coerceAtLeast(0.dp)

                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Top Widget Slot
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(topHeight)
                                .clip(RoundedCornerShape(12.dp))
                        ) {
                            SingleEmbeddedWidgetView(
                                widgetId = topWidgetId,
                                widgetInfo = topInfo,
                                appWidgetHost = appWidgetHost,
                                cachedHostView = cachedTopHostView,
                                onCacheView = { cachedTopHostView = it },
                                targetWidthDp = widgetWidth.value.roundToInt().coerceAtLeast(30),
                                targetHeightDp = topHeight.value.roundToInt().coerceAtLeast(30)
                            )

                            if (!isMoving) {
                                Box(
                                    modifier = Modifier
                                        .align(if (handSide == HandSide.LEFT) Alignment.BottomStart else Alignment.BottomEnd)
                                        .padding(6.dp)
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.38f))
                                        .clickable { slotForOptions = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.MoreVert,
                                        contentDescription = stringResource(R.string.widget_action_options),
                                        tint = Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }

                        // Draggable Divider Between Top and Bottom Widgets
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(dividerHeight)
                                .pointerInput(availableHeightPx) {
                                    detectTapGestures(
                                        onDoubleTap = {
                                            localRatio = 0.5f
                                            onUpdateSplitRatio(0.5f)
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                    )
                                }
                                .pointerInput(availableHeightPx) {
                                    detectVerticalDragGestures(
                                        onDragStart = { isDraggingDivider = true },
                                        onDragEnd = {
                                            isDraggingDivider = false
                                            onUpdateSplitRatio(localRatio)
                                        },
                                        onDragCancel = {
                                            isDraggingDivider = false
                                            onUpdateSplitRatio(localRatio)
                                        },
                                        onVerticalDrag = { change, dragAmount ->
                                            change.consume()
                                            if (availableHeightPx > 0f) {
                                                val deltaRatio = dragAmount / availableHeightPx
                                                val oldRatio = localRatio
                                                val newRatio = (oldRatio + deltaRatio).coerceIn(minRatio, maxRatio)
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
                                    .width(38.dp)
                                    .height(3.5.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        if (isDraggingDivider) accentColor.color.copy(alpha = 0.85f)
                                        else primaryTextColor.color.copy(alpha = 0.35f)
                                    )
                            )
                        }

                        // Bottom Widget Slot
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(bottomHeight)
                                .clip(RoundedCornerShape(12.dp))
                        ) {
                            SingleEmbeddedWidgetView(
                                widgetId = bottomWidgetId,
                                widgetInfo = bottomInfo,
                                appWidgetHost = appWidgetHost,
                                cachedHostView = cachedBottomHostView,
                                onCacheView = { cachedBottomHostView = it },
                                targetWidthDp = widgetWidth.value.roundToInt().coerceAtLeast(30),
                                targetHeightDp = bottomHeight.value.roundToInt().coerceAtLeast(30)
                            )

                            if (!isMoving) {
                                Box(
                                    modifier = Modifier
                                        .align(if (handSide == HandSide.LEFT) Alignment.BottomStart else Alignment.BottomEnd)
                                        .padding(6.dp)
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.38f))
                                        .clickable { slotForOptions = false },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.MoreVert,
                                        contentDescription = stringResource(R.string.widget_action_options),
                                        tint = Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (isDraggingDivider) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .pointerInput(Unit) {
                                    detectTapGestures { /* absorb touches */ }
                                }
                        )
                    }
                } else {
                    // Exactly ONE widget active: occupies full height
                    val isTopActive = topWidgetId != null && topInfo != null
                    val activeId = topWidgetId ?: bottomWidgetId ?: return@BoxWithConstraints
                    val activeInfo = topInfo ?: bottomInfo ?: return@BoxWithConstraints
                    val activeCachedView = if (isTopActive) cachedTopHostView else cachedBottomHostView

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        SingleEmbeddedWidgetView(
                            widgetId = activeId,
                            widgetInfo = activeInfo,
                            appWidgetHost = appWidgetHost,
                            cachedHostView = activeCachedView,
                            onCacheView = {
                                if (isTopActive) cachedTopHostView = it else cachedBottomHostView = it
                            },
                            targetWidthDp = widgetWidth.value.roundToInt().coerceAtLeast(30),
                            targetHeightDp = widgetHeight.value.roundToInt().coerceAtLeast(30)
                        )

                        if (!isMoving) {
                            Row(
                                modifier = Modifier
                                    .align(if (handSide == HandSide.LEFT) Alignment.BottomStart else Alignment.BottomEnd)
                                    .padding(6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Direct "+" button to add second widget
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.45f))
                                        .clickable { onPickWidget(!isTopActive) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Add,
                                        contentDescription = stringResource(R.string.widget_action_add_second),
                                        tint = accentColor.color.copy(alpha = 0.9f),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }

                                // 3-dots Options button
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.38f))
                                        .clickable { slotForOptions = isTopActive },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.MoreVert,
                                        contentDescription = stringResource(R.string.widget_action_options),
                                        tint = Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Drag/Move overlay
                if (isMoving) {
                    var accumulatedDragY by remember { mutableFloatStateOf(0f) }

                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(accentColor.color.copy(alpha = 0.14f))
                            .border(2.dp, accentColor.color, RoundedCornerShape(12.dp))
                            .pointerInput(Unit) {
                                detectTapGestures { isMoving = false }
                            }
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = {
                                        liveOffsetX = currentLayout.offsetX
                                        liveOffsetY = currentLayout.offsetY
                                        accumulatedDragY = 0f
                                    },
                                    onDragEnd = {
                                        onUpdateOffset(
                                            liveOffsetX ?: currentLayout.offsetX,
                                            liveOffsetY ?: currentLayout.offsetY
                                        )
                                        liveOffsetX = null
                                        liveOffsetY = null
                                        accumulatedDragY = 0f
                                    },
                                    onDragCancel = {
                                        liveOffsetX = null
                                        liveOffsetY = null
                                        accumulatedDragY = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        if (isDual) {
                                            accumulatedDragY += dragAmount.y
                                            val swapThreshold = with(density) { 55.dp.toPx() }
                                            if (accumulatedDragY > swapThreshold || accumulatedDragY < -swapThreshold) {
                                                onSwapWidgets()
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                accumulatedDragY = 0f
                                            }
                                        }

                                        fun step(current: Float, delta: Float, free: Float): Float {
                                            if (free <= 0f) return current
                                            val next = (current + delta / free).coerceIn(0f, 1f)
                                            val atEdge = next == 0f || next == 1f
                                            val crossedCenter = (current - 0.5f) * (next - 0.5f) < 0f
                                            if ((atEdge && next != current) || crossedCenter) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                            return next
                                        }

                                        if (currentFreeWidthPx > 0f) {
                                            liveOffsetX = step(liveOffsetX ?: currentLayout.offsetX, dragAmount.x, currentFreeWidthPx)
                                        }
                                        if (currentFreeHeightPx > 0f) {
                                            liveOffsetY = step(liveOffsetY ?: currentLayout.offsetY, dragAmount.y, currentFreeHeightPx)
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.OpenWith,
                            contentDescription = stringResource(R.string.widget_action_move),
                            tint = accentColor.color,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .align(if (handSide == HandSide.LEFT) Alignment.BottomStart else Alignment.BottomEnd)
                            .padding(6.dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(accentColor.color)
                            .clickable {
                                isMoving = false
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = stringResource(R.string.common_done),
                            tint = buttonTextColor.color,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (showResize) {
                val maxHeightDp = totalHeight.value.roundToInt().coerceAtLeast(71)
                WidgetResizeDialog(
                    initialHeightDp = layout.heightDp ?: maxHeightDp,
                    initialWidthFraction = layout.widthFraction,
                    accentColor = accentColor,
                    primaryTextColor = primaryTextColor,
                    buttonTextColor = buttonTextColor,
                    popupTheme = popupTheme,
                    showShadows = showShadows,
                    shadowSettings = shadowSettings,
                    heightRange = 70..maxHeightDp,
                    onDismiss = { showResize = false },
                    onApply = { newHeight, newWidth ->
                        onUpdateSize(
                            newHeight.takeIf { it < maxHeightDp },
                            newWidth
                        )
                    }
                )
            }
        }
    }

    slotForOptions?.let { isTop ->
        val targetWidgetId = if (isTop) topWidgetId else bottomWidgetId
        val defaultTitle = if (isDual) {
            if (isTop) stringResource(R.string.widget_top_title) else stringResource(R.string.widget_bottom_title)
        } else {
            stringResource(R.string.widget_title)
        }
        val customLabel = remember(targetWidgetId) {
            targetWidgetId?.let { id ->
                try {
                    manager.getAppWidgetInfo(id)?.loadLabel(context.packageManager)
                } catch (_: Exception) {
                    null
                }
            }
        }
        val widgetLabel = customLabel ?: defaultTitle
        val dialogShape = RoundedCornerShape(20.dp)

        AlertDialog(
            onDismissRequest = { slotForOptions = null },
            modifier = Modifier.border(1.dp, popupTheme.borderColor, dialogShape),
            tonalElevation = 0.dp,
            containerColor = popupTheme.backgroundColor,
            shape = dialogShape,
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
                    WidgetOptionButton(
                        icon = Icons.Outlined.Edit,
                        label = stringResource(R.string.widget_action_change),
                        iconTint = accentColor.color,
                        contentColor = popupTheme.contentColor
                    ) {
                        slotForOptions = null
                        onPickWidget(isTop)
                    }

                    if (!isDual) {
                        WidgetOptionButton(
                            icon = Icons.Outlined.Add,
                            label = stringResource(R.string.widget_action_add_second),
                            iconTint = accentColor.color,
                            contentColor = popupTheme.contentColor
                        ) {
                            slotForOptions = null
                            onPickWidget(!isTop)
                        }
                    } else {
                        WidgetOptionButton(
                            icon = Icons.Outlined.SwapVert,
                            label = stringResource(R.string.widget_action_swap),
                            iconTint = accentColor.color,
                            contentColor = popupTheme.contentColor
                        ) {
                            slotForOptions = null
                            onSwapWidgets()
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }

                        WidgetOptionButton(
                            icon = Icons.Outlined.RestartAlt,
                            label = stringResource(R.string.widget_action_reset_split),
                            iconTint = accentColor.color,
                            contentColor = popupTheme.contentColor
                        ) {
                            slotForOptions = null
                            onUpdateSplitRatio(0.5f)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    }

                    WidgetOptionButton(
                        icon = Icons.Outlined.AspectRatio,
                        label = stringResource(R.string.widget_action_resize),
                        iconTint = accentColor.color,
                        contentColor = popupTheme.contentColor
                    ) {
                        slotForOptions = null
                        showResize = true
                    }

                    WidgetOptionButton(
                        icon = Icons.Outlined.OpenWith,
                        label = stringResource(R.string.widget_action_move),
                        iconTint = accentColor.color,
                        contentColor = popupTheme.contentColor
                    ) {
                        slotForOptions = null
                        isMoving = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }

                    WidgetOptionButton(
                        icon = Icons.Outlined.RestartAlt,
                        label = stringResource(R.string.widget_action_reset_layout),
                        iconTint = accentColor.color,
                        contentColor = popupTheme.contentColor
                    ) {
                        slotForOptions = null
                        onResetLayout()
                    }

                    WidgetOptionButton(
                        icon = Icons.Outlined.Delete,
                        label = stringResource(R.string.widget_action_remove),
                        iconTint = Color(0xFFFF5252),
                        contentColor = Color(0xFFFF5252)
                    ) {
                        slotForOptions = null
                        onRemoveWidget(isTop)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { slotForOptions = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = popupTheme.contentColor)
                ) {
                    Text(stringResource(R.string.common_close))
                }
            }
        )
    }
}

@Composable
private fun SingleEmbeddedWidgetView(
    widgetId: Int,
    widgetInfo: android.appwidget.AppWidgetProviderInfo,
    appWidgetHost: AppWidgetHost?,
    cachedHostView: LauncherAppWidgetHostView?,
    onCacheView: (LauncherAppWidgetHostView) -> Unit,
    targetWidthDp: Int,
    targetHeightDp: Int
) {
    AndroidView(
        factory = { ctx ->
            try {
                val view = cachedHostView ?: run {
                    val host = appWidgetHost
                    val created = (host?.createView(ctx, widgetId, widgetInfo) as? LauncherAppWidgetHostView)
                        ?: LauncherAppWidgetHostView(ctx).apply {
                            setAppWidget(widgetId, widgetInfo)
                        }
                    onCacheView(created)
                    created
                }
                (view.parent as? ViewGroup)?.removeView(view)
                view
            } catch (_: Throwable) {
                android.widget.FrameLayout(ctx)
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = { view ->
            try {
                (view as? LauncherAppWidgetHostView)?.applyWidgetSize(
                    targetWidthDp.coerceAtLeast(30),
                    targetHeightDp.coerceAtLeast(30)
                )
            } catch (_: Throwable) {}
        }
    )
}

/**
 * Side Search Widget field component supporting up to two widgets arranged vertically.
 */
@Composable
fun SideSearchWidgetSlot(
    viewModel: LauncherViewModel,
    appWidgetHost: AppWidgetHost?,
    appWidgetManager: AppWidgetManager?,
    onPickWidget: (isTop: Boolean) -> Unit,
    handSide: HandSide = HandSide.RIGHT,
    modifier: Modifier = Modifier
) {
    val config by viewModel.sideSearchWidgetsConfig.collectAsState()
    val accentColor by viewModel.accentColor.collectAsState()
    val primaryTextColor by viewModel.primaryTextColor.collectAsState()
    val buttonTextColor by viewModel.buttonTextColor.collectAsState()
    val popupTheme by viewModel.popupTheme.collectAsState()
    val showShadows by viewModel.showShadows.collectAsState()
    val shadowSettings = LocalShadowSettings.current

    SideDualWidgetSlot(
        config = config,
        appWidgetHost = appWidgetHost,
        appWidgetManager = appWidgetManager,
        onPickWidget = onPickWidget,
        onUpdateSplitRatio = { viewModel.updateSideSearchWidgetSplitRatio(it) },
        onSwapWidgets = { viewModel.swapSideSearchWidgets() },
        onUpdateSize = { h, w -> viewModel.updateSideSearchWidgetSize(h, w) },
        onUpdateOffset = { x, y -> viewModel.updateSideSearchWidgetOffset(x, y) },
        onResetLayout = { viewModel.resetSideSearchWidgetLayout() },
        onRemoveWidget = { isTop ->
            val id = if (isTop) config.topWidgetId else config.bottomWidgetId
            id?.let { try { appWidgetHost?.deleteAppWidgetId(it) } catch (_: Exception) {} }
            viewModel.removeSideSearchWidget(isTop)
        },
        accentColor = accentColor,
        primaryTextColor = primaryTextColor,
        buttonTextColor = buttonTextColor,
        popupTheme = popupTheme,
        showShadows = showShadows,
        shadowSettings = shadowSettings,
        handSide = handSide,
        modifier = modifier
    )
}

@Composable
fun SideSearchWidgetSlot(
    viewModel: LauncherViewModel,
    appWidgetHost: AppWidgetHost?,
    appWidgetManager: AppWidgetManager?,
    onPickWidget: () -> Unit,
    handSide: HandSide = HandSide.RIGHT,
    modifier: Modifier = Modifier
) = SideSearchWidgetSlot(
    viewModel = viewModel,
    appWidgetHost = appWidgetHost,
    appWidgetManager = appWidgetManager,
    onPickWidget = { _ -> onPickWidget() },
    handSide = handSide,
    modifier = modifier
)

@Composable
private fun WidgetOptionButton(
    icon: ImageVector,
    label: String,
    iconTint: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.textButtonColors(contentColor = contentColor)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(icon, contentDescription = null, tint = iconTint)
            Spacer(modifier = Modifier.width(12.dp))
            Text(label, fontSize = 15.sp)
        }
    }
}

/**
 * Side Alphabet Widget slot component displayed above the alphabet grid (in place of search history),
 * supporting up to two widgets arranged vertically with identical mechanics.
 */
@Composable
fun SideAlphabetWidgetSlot(
    viewModel: LauncherViewModel,
    appWidgetHost: AppWidgetHost?,
    appWidgetManager: AppWidgetManager?,
    onPickWidget: (isTop: Boolean) -> Unit,
    handSide: HandSide = HandSide.RIGHT,
    modifier: Modifier = Modifier
) {
    val config by viewModel.sideAlphabetWidgetsConfig.collectAsState()
    val accentColor by viewModel.accentColor.collectAsState()
    val primaryTextColor by viewModel.primaryTextColor.collectAsState()
    val buttonTextColor by viewModel.buttonTextColor.collectAsState()
    val popupTheme by viewModel.popupTheme.collectAsState()
    val showShadows by viewModel.showShadows.collectAsState()
    val shadowSettings = LocalShadowSettings.current

    SideDualWidgetSlot(
        config = config,
        appWidgetHost = appWidgetHost,
        appWidgetManager = appWidgetManager,
        onPickWidget = onPickWidget,
        onUpdateSplitRatio = { viewModel.updateSideAlphabetWidgetSplitRatio(it) },
        onSwapWidgets = { viewModel.swapSideAlphabetWidgets() },
        onUpdateSize = { h, w -> viewModel.updateSideAlphabetWidgetSize(h, w) },
        onUpdateOffset = { x, y -> viewModel.updateSideAlphabetWidgetOffset(x, y) },
        onResetLayout = { viewModel.resetSideAlphabetWidgetLayout() },
        onRemoveWidget = { isTop ->
            val id = if (isTop) config.topWidgetId else config.bottomWidgetId
            id?.let { try { appWidgetHost?.deleteAppWidgetId(it) } catch (_: Exception) {} }
            viewModel.removeSideAlphabetWidget(isTop)
        },
        accentColor = accentColor,
        primaryTextColor = primaryTextColor,
        buttonTextColor = buttonTextColor,
        popupTheme = popupTheme,
        showShadows = showShadows,
        shadowSettings = shadowSettings,
        handSide = handSide,
        modifier = modifier
    )
}

@Composable
fun SideAlphabetWidgetSlot(
    viewModel: LauncherViewModel,
    appWidgetHost: AppWidgetHost?,
    appWidgetManager: AppWidgetManager?,
    onPickWidget: () -> Unit,
    handSide: HandSide = HandSide.RIGHT,
    modifier: Modifier = Modifier
) = SideAlphabetWidgetSlot(
    viewModel = viewModel,
    appWidgetHost = appWidgetHost,
    appWidgetManager = appWidgetManager,
    onPickWidget = { _ -> onPickWidget() },
    handSide = handSide,
    modifier = modifier
)
