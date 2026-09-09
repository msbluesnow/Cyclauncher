package dev.msbs.cyclauncher.ui.components

import dev.msbs.cyclauncher.LauncherViewModel
import dev.msbs.cyclauncher.HandSide
import dev.msbs.cyclauncher.SideAlphabetSlotMode
import dev.msbs.cyclauncher.model.AppInfo
import dev.msbs.cyclauncher.ui.theme.AccentColor
import dev.msbs.cyclauncher.ui.theme.PrimaryTextColor
import dev.msbs.cyclauncher.ui.theme.LocalShadowSettings
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.HistoryToggleOff
import androidx.compose.ui.graphics.graphicsLayer
import dev.msbs.cyclauncher.ui.theme.LocalAnimationsEnabled
import dev.msbs.cyclauncher.ui.theme.PopupTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize

/**
 * One-handed search layout with a side-aligned alphabet grid and dynamic app list.
 */
@Composable
fun SideAlphabetSearchLayout(
    viewModel: LauncherViewModel,
    handSide: HandSide,
    appWidgetHost: AppWidgetHost? = null,
    appWidgetManager: AppWidgetManager? = null,
    onPickSideWidget: () -> Unit = {},
    onPickSideAlphabetWidget: () -> Unit = {},
    onAppClick: (String) -> Unit,
    onAppLongClick: (AppInfo, Offset) -> Unit
) {
    val filteredApps by viewModel.filteredApps.collectAsState()
    val selectedLetter by viewModel.selectedLetter.collectAsState()
    val selectedColor by viewModel.selectedColor.collectAsState()
    val historyApps by viewModel.searchHistoryApps.collectAsState()
    val showSearchHistory by viewModel.showSearchHistory.collectAsState()
    val sideAlphabetSlotMode by viewModel.sideAlphabetSlotMode.collectAsState()
    val showSearchWidgets by viewModel.showSearchWidgets.collectAsState()
    val accentColor by viewModel.accentColor.collectAsState()
    val primaryTextColor by viewModel.primaryTextColor.collectAsState()
    val showShadows by viewModel.showShadows.collectAsState()
    val shadowSettings = LocalShadowSettings.current
    val shadow = primaryTextColor.getShadow(showShadows, shadowSettings.shadowColorOverride)
    val savedYRatio by viewModel.sideAlphabetButtonYRatio.collectAsState()
    var localYRatio by remember(savedYRatio) { mutableStateOf(savedYRatio) }

    var isLayoutSwapped by remember { mutableStateOf(false) }

    val alphabet = remember { listOf('#') + ('A'..'Z').toList() }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    val currentSelectedLetter by rememberUpdatedState(selectedLetter)
    val currentOnLetterSelected by rememberUpdatedState { char: Char ->
        if (char != currentSelectedLetter) {
            viewModel.setSelectedLetter(char)
        }
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    val effectiveLettersOnLeft = if (handSide == HandSide.LEFT) !isLayoutSwapped else isLayoutSwapped

    val isHistoryPaused by viewModel.isHistoryPaused.collectAsState()
    val popupTheme by viewModel.popupTheme.collectAsState()

    var selectedHistoryMenuOffset by remember { mutableStateOf<Offset?>(null) }
    var isHistoryEditMode by remember { mutableStateOf(false) }

    LaunchedEffect(historyApps.isEmpty()) {
        if (historyApps.isEmpty()) {
            isHistoryEditMode = false
            selectedHistoryMenuOffset = null
        }
    }

    LaunchedEffect(selectedLetter, selectedColor) {
        if (selectedLetter != null || selectedColor != null) {
            isHistoryEditMode = false
            selectedHistoryMenuOffset = null
        }
    }

    BackHandler(enabled = isHistoryEditMode || selectedHistoryMenuOffset != null) {
        if (selectedHistoryMenuOffset != null) {
            selectedHistoryMenuOffset = null
        } else {
            isHistoryEditMode = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
        val totalWidth = maxWidth
        val totalHeight = maxHeight

        val swapIconWidth = 36.dp
        val fixedAlphabetWidth = totalWidth * 0.4045f

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = if (handSide == HandSide.LEFT) swapIconWidth else 0.dp,
                        end = if (handSide == HandSide.RIGHT) swapIconWidth else 0.dp
                    )
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    if (effectiveLettersOnLeft) {
                        Column(
                            modifier = Modifier
                                .width(fixedAlphabetWidth)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.Bottom,
                            horizontalAlignment = Alignment.Start
                        ) {
                            when (sideAlphabetSlotMode) {
                                SideAlphabetSlotMode.HISTORY -> {
                                    if (historyApps.isNotEmpty()) {
                                        SideSearchHistoryBlock(
                                            history = historyApps,
                                            accentColor = accentColor,
                                            primaryTextColor = primaryTextColor,
                                            showShadows = showShadows,
                                            viewModel = viewModel,
                                            isEditMode = isHistoryEditMode,
                                            onAppClick = onAppClick,
                                            onAppLongClick = onAppLongClick,
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxWidth()
                                                .padding(bottom = 2.dp)
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                                SideAlphabetSlotMode.WIDGET -> {
                                    SideAlphabetWidgetSlot(
                                        viewModel = viewModel,
                                        appWidgetHost = appWidgetHost,
                                        appWidgetManager = appWidgetManager,
                                        onPickWidget = onPickSideAlphabetWidget,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth()
                                            .padding(bottom = 6.dp)
                                    )
                                }
                                SideAlphabetSlotMode.DISABLED -> {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }

                            SideAlphabetColorHeader(
                                selectedColor = selectedColor,
                                onColorSelected = { color ->
                                    viewModel.setSelectedColor(color)
                                },
                                showHistoryIcon = sideAlphabetSlotMode == SideAlphabetSlotMode.HISTORY && (historyApps.isNotEmpty() || isHistoryEditMode),
                                isHistoryEditMode = isHistoryEditMode,
                                isHistoryPaused = isHistoryPaused,
                                accentColor = accentColor,
                                primaryTextColor = primaryTextColor,
                                showShadows = showShadows,
                                shadowSettings = shadowSettings,
                                onHistoryIconLongPress = { offset ->
                                    selectedHistoryMenuOffset = offset
                                },
                                onExitEditMode = {
                                    isHistoryEditMode = false
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 4.dp, end = 4.dp, bottom = 6.dp)
                            )

                            SideAlphabetGrid(
                                alphabet = alphabet,
                                selectedLetter = selectedLetter,
                                accentColor = accentColor,
                                primaryTextColor = primaryTextColor,
                                showShadows = showShadows,
                                onLetterSelected = currentOnLetterSelected,
                                maxGridHeight = totalHeight * 0.50f
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            if (selectedLetter == null && selectedColor == null) {
                                if (showSearchWidgets) {
                                    SideSearchWidgetSlot(
                                        viewModel = viewModel,
                                        appWidgetHost = appWidgetHost,
                                        appWidgetManager = appWidgetManager,
                                        onPickWidget = onPickSideWidget,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 4.dp, vertical = 6.dp)
                                    )
                                }
                            } else {
                                SideAppListContent(
                                    apps = filteredApps,
                                    handSide = handSide,
                                    primaryTextColor = primaryTextColor,
                                    showShadows = showShadows,
                                    onAppClick = onAppClick,
                                    onAppLongClick = onAppLongClick
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            if (selectedLetter == null && selectedColor == null) {
                                if (showSearchWidgets) {
                                    SideSearchWidgetSlot(
                                        viewModel = viewModel,
                                        appWidgetHost = appWidgetHost,
                                        appWidgetManager = appWidgetManager,
                                        onPickWidget = onPickSideWidget,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 4.dp, vertical = 6.dp)
                                    )
                                }
                            } else {
                                SideAppListContent(
                                    apps = filteredApps,
                                    handSide = handSide,
                                    primaryTextColor = primaryTextColor,
                                    showShadows = showShadows,
                                    onAppClick = onAppClick,
                                    onAppLongClick = onAppLongClick
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .width(fixedAlphabetWidth)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.Bottom,
                            horizontalAlignment = Alignment.End
                        ) {
                            when (sideAlphabetSlotMode) {
                                SideAlphabetSlotMode.HISTORY -> {
                                    if (historyApps.isNotEmpty()) {
                                        SideSearchHistoryBlock(
                                            history = historyApps,
                                            accentColor = accentColor,
                                            primaryTextColor = primaryTextColor,
                                            showShadows = showShadows,
                                            viewModel = viewModel,
                                            isEditMode = isHistoryEditMode,
                                            onAppClick = onAppClick,
                                            onAppLongClick = onAppLongClick,
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxWidth()
                                                .padding(bottom = 2.dp)
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                                SideAlphabetSlotMode.WIDGET -> {
                                    SideAlphabetWidgetSlot(
                                        viewModel = viewModel,
                                        appWidgetHost = appWidgetHost,
                                        appWidgetManager = appWidgetManager,
                                        onPickWidget = onPickSideAlphabetWidget,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth()
                                            .padding(bottom = 6.dp)
                                    )
                                }
                                SideAlphabetSlotMode.DISABLED -> {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }

                            SideAlphabetColorHeader(
                                selectedColor = selectedColor,
                                onColorSelected = { color ->
                                    viewModel.setSelectedColor(color)
                                },
                                showHistoryIcon = sideAlphabetSlotMode == SideAlphabetSlotMode.HISTORY && (historyApps.isNotEmpty() || isHistoryEditMode),
                                isHistoryEditMode = isHistoryEditMode,
                                isHistoryPaused = isHistoryPaused,
                                accentColor = accentColor,
                                primaryTextColor = primaryTextColor,
                                showShadows = showShadows,
                                shadowSettings = shadowSettings,
                                onHistoryIconLongPress = { offset ->
                                    selectedHistoryMenuOffset = offset
                                },
                                onExitEditMode = {
                                    isHistoryEditMode = false
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 4.dp, end = 4.dp, bottom = 6.dp)
                            )

                            SideAlphabetGrid(
                                alphabet = alphabet,
                                selectedLetter = selectedLetter,
                                accentColor = accentColor,
                                primaryTextColor = primaryTextColor,
                                showShadows = showShadows,
                                onLetterSelected = currentOnLetterSelected,
                                maxGridHeight = totalHeight * 0.50f
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = if (handSide == HandSide.LEFT) Arrangement.Start else Arrangement.End
                ) {
                    IconButton(onClick = { viewModel.toggleTextSearchMode() }) {
                        Text(
                            "⌨",
                            color = accentColor.color.copy(alpha = 0.8f),
                            fontSize = 32.sp,
                            style = MaterialTheme.typography.bodyLarge.copy(shadow = shadow)
                        )
                    }
                }
            }

            val totalHeightPx = with(density) { totalHeight.toPx() }
            val minYRatio = 0.05f
            val effectiveYRatio = localYRatio.coerceIn(minYRatio, 0.85f)

            Box(
                modifier = Modifier
                    .align(if (handSide == HandSide.LEFT) Alignment.BottomStart else Alignment.BottomEnd)
                    .padding(bottom = totalHeight * effectiveYRatio)
            ) {
                SwapSemiCircleButton(
                    handSide = handSide,
                    accentColor = accentColor,
                    primaryTextColor = primaryTextColor,
                    showShadows = showShadows,
                    onClick = {
                        isLayoutSwapped = !isLayoutSwapped
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onVerticalDrag = { deltaPx ->
                        if (totalHeightPx > 0f) {
                            val deltaRatio = -deltaPx / totalHeightPx
                            localYRatio = (localYRatio + deltaRatio).coerceIn(minYRatio, 0.85f)
                        }
                    },
                    onDragEnd = {
                        viewModel.setSideAlphabetButtonYRatio(localYRatio.coerceIn(minYRatio, 0.85f))
                    }
                )
            }
        }
    }

    selectedHistoryMenuOffset?.let { offset ->
        HistoryActionMenu(
            isHistoryPaused = isHistoryPaused,
            hasHistoryItems = historyApps.isNotEmpty(),
            offset = offset,
            onDismiss = { selectedHistoryMenuOffset = null },
            onEditHistory = {
                selectedHistoryMenuOffset = null
                isHistoryEditMode = true
            },
            onTogglePause = {
                selectedHistoryMenuOffset = null
                viewModel.toggleHistoryPaused()
            },
            onClearHistory = {
                selectedHistoryMenuOffset = null
                viewModel.clearSearchHistory()
            },
            accentColor = accentColor,
            primaryTextColor = primaryTextColor,
            popupTheme = popupTheme
        )
    }
}
}

/**
 * Floating semi-circle button for swapping grid/list sides and adjusting vertical offset.
 */
@Composable
private fun SwapSemiCircleButton(
    handSide: HandSide,
    accentColor: AccentColor,
    primaryTextColor: PrimaryTextColor,
    showShadows: Boolean,
    onClick: () -> Unit,
    onVerticalDrag: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val shadow = primaryTextColor.getShadow(showShadows, LocalShadowSettings.current.shadowColorOverride)
    val shape = if (handSide == HandSide.LEFT) {
        RoundedCornerShape(topEnd = 27.dp, bottomEnd = 27.dp, topStart = 0.dp, bottomStart = 0.dp)
    } else {
        RoundedCornerShape(topStart = 27.dp, bottomStart = 27.dp, topEnd = 0.dp, bottomEnd = 0.dp)
    }

    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnVerticalDrag by rememberUpdatedState(onVerticalDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    var isBeingDragged by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .width(36.dp)
            .height(54.dp)
            .clip(shape)
            .background(
                if (isBeingDragged) {
                    accentColor.color.copy(alpha = 0.28f)
                } else {
                    primaryTextColor.color.copy(alpha = 0.14f)
                }
            )
            .border(
                1.dp,
                if (isBeingDragged) {
                    accentColor.color.copy(alpha = 0.6f)
                } else {
                    primaryTextColor.color.copy(alpha = 0.22f)
                },
                shape
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var isDrag = false
                    var totalDragY = 0f

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            if (!isDrag) {
                                currentOnClick()
                            } else {
                                isBeingDragged = false
                                currentOnDragEnd()
                            }
                            break
                        }

                        val deltaY = change.positionChange().y
                        totalDragY += deltaY

                        if (!isDrag && kotlin.math.abs(totalDragY) > viewConfiguration.touchSlop) {
                            isDrag = true
                            isBeingDragged = true
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }

                        if (isDrag) {
                            change.consume()
                            currentOnVerticalDrag(deltaY)
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "⇆",
            color = accentColor.color,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium.copy(shadow = shadow)
        )
    }
}

/**
 * Grid rendering 27 characters (A-Z, #) in a 4-column layout occupying up to 50% screen height.
 * Supports direct touch selection and drag scrubbing across letter tiles.
 */
@Composable
private fun SideAlphabetGrid(
    alphabet: List<Char>,
    selectedLetter: Char?,
    accentColor: AccentColor,
    primaryTextColor: PrimaryTextColor,
    showShadows: Boolean,
    onLetterSelected: (Char) -> Unit,
    maxGridHeight: androidx.compose.ui.unit.Dp
) {
    val cols = 4
    val rows = 7
    var gridBoundsSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }

    fun processTouchOffset(offset: Offset) {
        if (gridBoundsSize.width > 0f && gridBoundsSize.height > 0f) {
            val cellW = gridBoundsSize.width / cols
            val cellH = gridBoundsSize.height / rows
            val c = (offset.x / cellW).toInt().coerceIn(0, cols - 1)
            val r = (offset.y / cellH).toInt().coerceIn(0, rows - 1)
            val index = r * cols + c
            if (index in alphabet.indices) {
                onLetterSelected(alphabet[index])
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxGridHeight)
            .padding(bottom = 8.dp, start = 4.dp, end = 4.dp)
            .onGloballyPositioned { gridBoundsSize = it.size.toSize() }
            .pointerInput(alphabet) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    processTouchOffset(down.position)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        processTouchOffset(change.position)
                    }
                }
            }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (r in 0 until rows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (c in 0 until cols) {
                        val index = r * cols + c
                        if (index in alphabet.indices) {
                            val char = alphabet[index]
                            val isSelected = char == selectedLetter
                            LetterTile(
                                char = char,
                                isSelected = isSelected,
                                accentColor = accentColor,
                                primaryTextColor = primaryTextColor,
                                showShadows = showShadows,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Individual letter tile component inside the side alphabet grid.
 */
@Composable
private fun LetterTile(
    char: Char,
    isSelected: Boolean,
    accentColor: AccentColor,
    primaryTextColor: PrimaryTextColor,
    showShadows: Boolean,
    modifier: Modifier = Modifier
) {
    val shadow = primaryTextColor.getShadow(showShadows, LocalShadowSettings.current.shadowColorOverride)
    val tileBackground = if (isSelected) accentColor.color.copy(alpha = 0.30f) else primaryTextColor.color.copy(alpha = 0.05f)
    val tileBorderColor = if (isSelected) accentColor.color else primaryTextColor.color.copy(alpha = 0.12f)
    val textColor = if (isSelected) accentColor.color else primaryTextColor.color

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(tileBackground)
            .border(1.dp, tileBorderColor, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = char.toString(),
            color = textColor,
            fontSize = 16.sp,
            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
            style = MaterialTheme.typography.bodyMedium.copy(shadow = shadow)
        )
    }
}

/**
 * App list rendering component for the side alphabet layout.
 */
@Composable
private fun SideAppListContent(
    apps: List<AppInfo>,
    handSide: HandSide,
    primaryTextColor: PrimaryTextColor,
    showShadows: Boolean,
    onAppClick: (String) -> Unit,
    onAppLongClick: (AppInfo, Offset) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = if (handSide == HandSide.LEFT) Alignment.Start else Alignment.End
    ) {
        items(
            items = apps,
            key = { it.componentKey }
        ) { app ->
            AppListItemWithIcon(
                app = app,
                handSide = handSide,
                fontSize = 16,
                iconSize = 36,
                onClick = { onAppClick("${app.packageName}/${app.activityName}") },
                onLongClick = { offset -> onAppLongClick(app, offset) },
                primaryTextColor = primaryTextColor,
                showShadows = showShadows
            )
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

/**
 * Serpentine history block with 3 columns of icons connected by a smooth snake line,
 * with a history icon situated underneath the history rows, between the letters and
 * the history list. Supports long-press for HistoryActionMenu (pause, edit, clear)
 * and interactive edit mode for dynamically deleting individual search history items.
 */
@Composable
private fun SideSearchHistoryBlock(
    history: List<AppInfo>,
    accentColor: AccentColor,
    primaryTextColor: PrimaryTextColor,
    showShadows: Boolean,
    viewModel: LauncherViewModel,
    isEditMode: Boolean,
    onAppClick: (String) -> Unit,
    onAppLongClick: (AppInfo, Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    val shadowSettings = LocalShadowSettings.current

    Box(
        modifier = modifier.fillMaxWidth()
    ) {
        SnakeHistoryLazyColumn(
            history = history,
            isEditMode = isEditMode,
            showShadows = showShadows,
            accentColor = accentColor,
            primaryTextColor = primaryTextColor,
            shadowSettings = shadowSettings,
            onAppClick = onAppClick,
            onAppLongClick = onAppLongClick,
            onRemoveFromHistory = { componentKey ->
                viewModel.removeFromSearchHistory(componentKey)
            }
        )
    }
}

@Composable
private fun SnakeHistoryLazyColumn(
    history: List<AppInfo>,
    isEditMode: Boolean,
    showShadows: Boolean,
    accentColor: AccentColor,
    primaryTextColor: PrimaryTextColor,
    shadowSettings: dev.msbs.cyclauncher.ui.theme.ShadowSettings,
    onAppClick: (String) -> Unit,
    onAppLongClick: (AppInfo, Offset) -> Unit,
    onRemoveFromHistory: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val rowCount = (history.size + 2) / 3
    val rowHeight = 60.dp

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        reverseLayout = true,
        verticalArrangement = Arrangement.Bottom
    ) {
        items(
            count = rowCount,
            key = { r ->
                val base = 3 * r
                val k0 = if (r % 2 == 0) history.getOrNull(base)?.componentKey else history.getOrNull(base + 2)?.componentKey
                val k1 = history.getOrNull(base + 1)?.componentKey
                val k2 = if (r % 2 == 0) history.getOrNull(base + 2)?.componentKey else history.getOrNull(base)?.componentKey
                "$k0-$k1-$k2-$r"
            }
        ) { r ->
            val base = 3 * r
            val col0App = if (r % 2 == 0) history.getOrNull(base) else history.getOrNull(base + 2)
            val col1App = history.getOrNull(base + 1)
            val col2App = if (r % 2 == 0) history.getOrNull(base + 2) else history.getOrNull(base)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .drawBehind {
                        val strokeWidth = 2.5.dp.toPx()
                        val cornerRadius = 14.dp.toPx()
                        val pathEffect = PathEffect.cornerPathEffect(cornerRadius)

                        val x0 = size.width * (1f / 6f)
                        val x1 = size.width * 0.5f
                        val x2 = size.width * (5f / 6f)
                        val cy = size.height * 0.5f

                        val path = Path().apply {
                            if (r % 2 == 0) {
                                if (r > 0) {
                                    moveTo(x0, size.height)
                                    lineTo(x0, cy)
                                } else {
                                    moveTo(x0, cy)
                                }
                                if (history.size > base + 1) {
                                    if (history.size > base + 2) {
                                        lineTo(x2, cy)
                                        if (history.size > base + 3) {
                                            lineTo(x2, 0f)
                                        }
                                    } else {
                                        lineTo(x1, cy)
                                    }
                                }
                            } else {
                                moveTo(x2, size.height)
                                lineTo(x2, cy)
                                if (history.size > base + 1) {
                                    if (history.size > base + 2) {
                                        lineTo(x0, cy)
                                        if (history.size > base + 3) {
                                            lineTo(x0, 0f)
                                        }
                                    } else {
                                        lineTo(x1, cy)
                                    }
                                }
                            }
                        }

                        drawPath(
                            path = path,
                            color = accentColor.color.copy(alpha = 0.65f),
                            style = Stroke(
                                width = strokeWidth,
                                cap = StrokeCap.Butt,
                                join = StrokeJoin.Round,
                                pathEffect = pathEffect
                            )
                        )
                    }
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        col0App?.let { app ->
                            SnakeAppIcon(
                                app = app,
                                iconSize = 44.dp,
                                isEditMode = isEditMode,
                                onClick = { onAppClick("${app.packageName}/${app.activityName}") },
                                onLongClick = { offset -> onAppLongClick(app, offset) },
                                onRemove = { onRemoveFromHistory(app.componentKey) }
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        col1App?.let { app ->
                            SnakeAppIcon(
                                app = app,
                                iconSize = 44.dp,
                                isEditMode = isEditMode,
                                onClick = { onAppClick("${app.packageName}/${app.activityName}") },
                                onLongClick = { offset -> onAppLongClick(app, offset) },
                                onRemove = { onRemoveFromHistory(app.componentKey) }
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        col2App?.let { app ->
                            SnakeAppIcon(
                                app = app,
                                iconSize = 44.dp,
                                isEditMode = isEditMode,
                                onClick = { onAppClick("${app.packageName}/${app.activityName}") },
                                onLongClick = { offset -> onAppLongClick(app, offset) },
                                onRemove = { onRemoveFromHistory(app.componentKey) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SnakeAppIcon(
    app: AppInfo,
    iconSize: Dp,
    isEditMode: Boolean,
    onClick: () -> Unit,
    onLongClick: (Offset) -> Unit,
    onRemove: () -> Unit
) {
    var itemPosition by remember { mutableStateOf(Offset.Zero) }
    val painter = rememberAppIconPainter(app.iconKey, 48)
    val haptic = LocalHapticFeedback.current
    val animationsEnabled = LocalAnimationsEnabled.current

    val shakeRotation by if (isEditMode && animationsEnabled) {
        val infiniteTransition = rememberInfiniteTransition(label = "snake_edit_shake")
        infiniteTransition.animateFloat(
            initialValue = -2.5f,
            targetValue = 2.5f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 110, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "snake_shake_rot"
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    Box(
        modifier = Modifier
            .size(iconSize)
            .graphicsLayer { rotationZ = shakeRotation }
            .onGloballyPositioned { itemPosition = it.positionInRoot() }
            .pointerInput(app.componentKey, isEditMode) {
                detectTapGestures(
                    onTap = {
                        if (isEditMode) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onRemove()
                        } else {
                            onClick()
                        }
                    },
                    onLongPress = {
                        if (isEditMode) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onRemove()
                        } else {
                            onLongClick(itemPosition + it)
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painter,
            contentDescription = app.label,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
        )

        if (isEditMode) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp)
                    .background(Color(0xFFE53935), CircleShape)
                    .border(1.5.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Remove from history",
                    tint = Color.White,
                    modifier = Modifier.size(11.dp)
                )
            }
        }
    }
}

