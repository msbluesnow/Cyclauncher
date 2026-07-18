package dev.msbs.cyclauncher.ui.screens

import dev.msbs.cyclauncher.LauncherViewModel
import dev.msbs.cyclauncher.HandSide
import dev.msbs.cyclauncher.model.AppInfo
import dev.msbs.cyclauncher.ui.theme.AccentColor
import dev.msbs.cyclauncher.ui.components.AppListItemWithIcon
import dev.msbs.cyclauncher.ui.components.AppIconItem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * The default launcher home screen, hosting favorite application shortcuts and a scrollable recent launch history list.
 * Supports swipe gestures (up for search screen, down to pull notifications) and long press to open settings.
 *
 * @param viewModel The view model supplying state data.
 * @param isActive True if this screen is current active page in horizontal and vertical pagers.
 * @param onAppClick Callback when an application is clicked.
 * @param onAppLongClick Callback when an application is long-pressed (provides coordinates).
 * @param onSwipeUp Callback when a swipe up gesture is detected.
 * @param onSwipeDown Callback when a swipe down gesture is detected.
 * @param onSettingsClick Callback to transition to the Settings screen.
 */
@Composable
fun MainMenuScreen(
    viewModel: LauncherViewModel,
    isActive: Boolean,
    onAppClick: (String) -> Unit,
    onAppLongClick: (AppInfo, Offset) -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val favorites by viewModel.favoriteApps.collectAsState()
    val history by viewModel.historyApps.collectAsState()
    val handSide by viewModel.handSide.collectAsState()
    val showShadows by viewModel.showShadows.collectAsState()
    val accentColor by viewModel.accentColor.collectAsState()

    var isReorderMode by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
    ) {
        val favoritesWeight = 1f
        val historyWeight = 1.2f

        if (handSide == HandSide.LEFT) {
            FavoritesSection(
                Modifier.weight(favoritesWeight),
                favorites,
                accentColor,
                showShadows,
                isReorderMode,
                { isReorderMode = it },
                { from, to -> viewModel.reorderFavorites(from, to) },
                { viewModel.toggleFavorite(it) },
                onAppClick,
                onAppLongClick,
                onSwipeUp,
                onSwipeDown,
                onSettingsClick,
                isActive
            )
            Spacer(modifier = Modifier.width(16.dp))
            HistorySection(Modifier.weight(historyWeight), history, handSide, showShadows, accentColor, onAppClick, onAppLongClick, onSettingsClick, isActive)
        } else {
            HistorySection(Modifier.weight(historyWeight), history, handSide, showShadows, accentColor, onAppClick, onAppLongClick, onSettingsClick, isActive)
            Spacer(modifier = Modifier.width(16.dp))
            FavoritesSection(
                Modifier.weight(favoritesWeight),
                favorites,
                accentColor,
                showShadows,
                isReorderMode,
                { isReorderMode = it },
                { from, to -> viewModel.reorderFavorites(from, to) },
                { viewModel.toggleFavorite(it) },
                onAppClick,
                onAppLongClick,
                onSwipeUp,
                onSwipeDown,
                onSettingsClick,
                isActive
            )
        }
    }
}

/**
 * Renders the launch history list of applications. Displays recently used apps.
 *
 * @param modifier Modifier for UI configurations.
 * @param history The list of recently launched applications.
 * @param handSide Preferred layout orientation side.
 * @param showShadows Whether to apply drop shadows.
 * @param accentColor Theme accent color.
 * @param onAppClick Callback when a history item is clicked.
 * @param onAppLongClick Callback when a history item is long-pressed.
 * @param onSettingsClick Callback to transition to the Settings screen on empty space tap.
 * @param isActive True if this page is currently active.
 */
@Composable
private fun HistorySection(
    modifier: Modifier,
    history: List<AppInfo>,
    handSide: HandSide,
    showShadows: Boolean,
    accentColor: AccentColor,
    onAppClick: (String) -> Unit,
    onAppLongClick: (AppInfo, Offset) -> Unit,
    onSettingsClick: () -> Unit,
    isActive: Boolean
) {
    val shadow = if (showShadows) {
        Shadow(
            color = Color.Black.copy(alpha = 0.6f),
            offset = Offset(2f, 2f),
            blurRadius = 4f
        )
    } else null

    val listState = rememberLazyListState()
    val currentOnSettingsClick by rememberUpdatedState(onSettingsClick)

    // Whenever the Main screen becomes active again (e.g. the user navigated away
    // and came back), scroll the history list back to the bottom so the most
    // recently opened apps remain visible. With reverseLayout = true, index 0 is
    // the newest app and sits at the bottom of the visible area.
    LaunchedEffect(isActive) {
        if (isActive && history.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .pointerInput(isActive) {
                detectTapGestures(onLongPress = { currentOnSettingsClick() })
            },
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = if (handSide == HandSide.LEFT) Alignment.End else Alignment.Start
    ) {
        Text(
            "HISTORY",
            color = accentColor.color,
            style = MaterialTheme.typography.titleSmall.copy(shadow = shadow),
            fontWeight = FontWeight.Bold,
            textAlign = if (handSide == HandSide.LEFT) TextAlign.End else TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.Bottom),
            modifier = Modifier.fillMaxWidth(),
            reverseLayout = true
        ) {
            items(history, key = { "${it.packageName}/${it.activityName}" }) { app ->
                Box(
                    modifier = Modifier.pointerInput(isActive) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                        }
                    }
                ) {
                    AppListItemWithIcon(
                        app = app,
                        handSide = if (handSide == HandSide.LEFT) HandSide.RIGHT else HandSide.LEFT,
                        iconSize = 44,
                        fontSize = 20,
                        onClick = { onAppClick("${app.packageName}/${app.activityName}") },
                        onLongClick = { offset -> onAppLongClick(app, offset) },
                        showShadows = showShadows
                    )
                }
            }
        }
    }
}

/**
 * Renders the favorites section containing custom layout grid of favorite application icons.
 * Also handles drag gestures for reordering items when in edit/reorder mode.
 *
 * @param modifier Modifier for UI configurations.
 * @param favorites The list of favorite applications.
 * @param accentColor Theme accent color.
 * @param showShadows Whether to apply drop shadows.
 * @param isReorderMode True if reorder (edit) mode is active.
 * @param setReorderMode Callback to enable/disable reorder mode.
 * @param onReorder Callback to swap positions of favorite items.
 * @param onToggleFavorite Callback to remove/add a favorite item.
 * @param onAppClick Callback when a favorite app icon is clicked.
 * @param onAppLongClick Callback when a favorite app icon is long-pressed.
 * @param onSwipeUp Callback when a swipe up gesture is detected.
 * @param onSwipeDown Callback when a swipe down gesture is detected.
 * @param onSettingsClick Callback to transition to the Settings screen on empty space tap.
 * @param isActive True if this page is currently active.
 */
@Composable
private fun FavoritesSection(
    modifier: Modifier,
    favorites: List<AppInfo>,
    accentColor: AccentColor,
    showShadows: Boolean,
    isReorderMode: Boolean,
    setReorderMode: (Boolean) -> Unit,
    onReorder: (Int, Int) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onAppClick: (String) -> Unit,
    onAppLongClick: (AppInfo, Offset) -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    onSettingsClick: () -> Unit,
    isActive: Boolean
) {
    val shadow = if (showShadows) {
        Shadow(
            color = Color.Black.copy(alpha = 0.6f),
            offset = Offset(2f, 2f),
            blurRadius = 4f
        )
    } else null

    val haptic = LocalHapticFeedback.current
    var draggingAppKey by remember { mutableStateOf<String?>(null) }
    var dragVerticalOffset by remember { mutableStateOf(0f) }

    val currentOnSettingsClick by rememberUpdatedState(onSettingsClick)
    val currentSetReorderMode by rememberUpdatedState(setReorderMode)
    val currentOnSwipeDown by rememberUpdatedState(onSwipeDown)
    val currentOnSwipeUp by rememberUpdatedState(onSwipeUp)
    val currentIsReorderMode by rememberUpdatedState(isReorderMode)
    val currentOnReorder by rememberUpdatedState(onReorder)

    // Reset dragging state if reorder mode is exited
    LaunchedEffect(isReorderMode) {
        if (!isReorderMode) {
            draggingAppKey = null
            dragVerticalOffset = 0f
        }
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            // Intercept vertical swipes on Initial pass (parent-first).
            // Runs BEFORE children (icons, LazyColumn) can consume events,
            // so swipes work from anywhere — including the icon area.
            .pointerInput(isReorderMode, isActive) {
                if (isReorderMode) return@pointerInput
                val swipeThreshold = 40f

                awaitEachGesture {
                    // Wait for first pointer down on Initial pass
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var totalDy = 0f
                    var swipeHandled = false

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break

                        if (change.isConsumed) break
                        if (change.changedToUp()) break

                        totalDy += change.positionChange().y

                        if (kotlin.math.abs(totalDy) > swipeThreshold) {
                            // Consume to prevent children from processing further
                            event.changes.forEach { it.consume() }
                            if (totalDy > 0) currentOnSwipeDown()
                            else currentOnSwipeUp()
                            break
                        }
                    }
                }
            }
            // Handle tap and long-press on empty space only (Main pass, default).
            // detectTapGestures uses requireUnconsumed=true by default, so it
            // won't activate on icons — icons consume their own down events.
            .pointerInput(isReorderMode, isActive) {
                detectTapGestures(
                    onLongPress = { if (!currentIsReorderMode) currentOnSettingsClick() },
                    onTap = { if (currentIsReorderMode) currentSetReorderMode(false) }
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Bottom),
            horizontalAlignment = Alignment.CenterHorizontally,
            reverseLayout = true
        ) {
            itemsIndexed(favorites, key = { _, app -> "${app.packageName}/${app.activityName}" }) { index, app ->
                val appKey = "${app.packageName}/${app.activityName}"
                val isDraggingThis = draggingAppKey == appKey
                
                // Track the current index and list size to avoid stale closures during drag
                val currentIndex by rememberUpdatedState(index)
                val currentSize by rememberUpdatedState(favorites.size)

                val scale by animateFloatAsState(if (isDraggingThis) 1.25f else 1.0f, label = "scale")
                val alpha by animateFloatAsState(if (isDraggingThis) 0.8f else 1.0f, label = "alpha")

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                            // Visual follow-finger offset
                            if (isDraggingThis) {
                                translationY = dragVerticalOffset
                            }
                        }
                ) {
                    if (isReorderMode) {
                        IconButton(
                            onClick = { onToggleFavorite(appKey) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.RemoveCircle,
                                contentDescription = "Remove",
                                tint = Color.Red.copy(alpha = 0.8f)
                            )
                        }
                    }

                    Box(
                        modifier = (if (isReorderMode) {
                            Modifier.pointerInput(appKey) {
                                var accumulatedDragForSwap = 0f
                                detectDragGestures(
                                    onDragStart = { 
                                        accumulatedDragForSwap = 0f 
                                        dragVerticalOffset = 0f
                                        draggingAppKey = appKey
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDragEnd = { 
                                        draggingAppKey = null 
                                        dragVerticalOffset = 0f
                                    },
                                    onDragCancel = { 
                                        draggingAppKey = null 
                                        dragVerticalOffset = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragVerticalOffset += dragAmount.y
                                        accumulatedDragForSwap += dragAmount.y
                                        
                                        val threshold = 60f 
                                        if (accumulatedDragForSwap < -threshold && currentIndex < currentSize - 1) {
                                            currentOnReorder(currentIndex, currentIndex + 1)
                                            accumulatedDragForSwap = 0f
                                            dragVerticalOffset = 0f
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        } else if (accumulatedDragForSwap > threshold && currentIndex > 0) {
                                            currentOnReorder(currentIndex, currentIndex - 1)
                                            accumulatedDragForSwap = 0f
                                            dragVerticalOffset = 0f
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    }
                                )
                            }
                        } else Modifier).pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                down.consume()
                            }
                        }
                    ) {
                        AppIconItem(
                            app = app,
                            onClick = { 
                                if (isReorderMode) setReorderMode(false)
                                else onAppClick(appKey)
                            },
                            onLongClick = { offset -> 
                                if (!isReorderMode) onAppLongClick(app, offset)
                            }
                        )
                    }

                    if (isReorderMode) {
                        Spacer(modifier = Modifier.size(32.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        
        Box(
            modifier = Modifier
                .pointerInput(isReorderMode) {
                    detectTapGestures(
                        onLongPress = {
                            if (!isReorderMode) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                setReorderMode(true)
                            }
                        },
                        onTap = { 
                            if (isReorderMode) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                setReorderMode(false) 
                            }
                        }
                    )
                }
        ) {
            if (isReorderMode) {
                // Use a Box with shadow to match the text shadow
                Box(contentAlignment = Alignment.Center) {
                    if (showShadows) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = Color.Black.copy(alpha = 0.6f),
                            modifier = Modifier
                                .size(28.dp)
                                .offset(2.dp, 2.dp)
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Exit Edit Mode",
                        tint = accentColor.color,
                        modifier = Modifier.size(28.dp)
                    )
                }
            } else {
                Text(
                    text = "★", 
                    color = accentColor.color, 
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge.copy(shadow = shadow)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}
