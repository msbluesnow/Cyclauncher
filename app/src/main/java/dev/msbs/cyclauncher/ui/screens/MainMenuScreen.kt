package dev.msbs.cyclauncher.ui.screens

import dev.msbs.cyclauncher.LauncherViewModel
import dev.msbs.cyclauncher.HandSide
import dev.msbs.cyclauncher.model.AppInfo
import dev.msbs.cyclauncher.model.FavoriteItem
import dev.msbs.cyclauncher.model.Tag
import dev.msbs.cyclauncher.ui.theme.AccentColor
import dev.msbs.cyclauncher.ui.theme.PrimaryTextColor
import dev.msbs.cyclauncher.ui.components.AppListItemWithIcon
import dev.msbs.cyclauncher.ui.components.AppIconItem
import dev.msbs.cyclauncher.ui.components.TagFolderIcon
import dev.msbs.cyclauncher.ui.components.TagFolderActionMenu
import dev.msbs.cyclauncher.ui.components.TagFolderItem
import dev.msbs.cyclauncher.ui.components.TagFolderPopup
import dev.msbs.cyclauncher.ui.components.HistoryActionMenu

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.HistoryToggleOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

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
    isActionMenuOpen: Boolean = false,
    onAppClick: (String) -> Unit,
    onAppLongClick: (AppInfo, Offset) -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    onSettingsClick: () -> Unit,
    onEditTag: (Tag) -> Unit = {}
) {
    val favoriteItems by viewModel.favoriteItems.collectAsState()
    val history by viewModel.historyApps.collectAsState()
    val tags by viewModel.tags.collectAsState()
    val appTags by viewModel.appTags.collectAsState()
    val apps by viewModel.apps.collectAsState()
    val handSide by viewModel.handSide.collectAsState()
    val showShadows by viewModel.showShadows.collectAsState()
    val accentColor by viewModel.accentColor.collectAsState()
    val primaryTextColor by viewModel.primaryTextColor.collectAsState()
    val isHistoryPaused by viewModel.isHistoryPaused.collectAsState()
    val recentlyUpdatedApps by viewModel.recentlyUpdatedApps.collectAsState()
    var isReorderMode by remember { mutableStateOf(false) }
    var isHistoryEditMode by remember { mutableStateOf(false) }
    var selectedTagForPopup by remember { mutableStateOf<Triple<Tag, List<AppInfo>, Offset>?>(null) }
    var selectedTagForMenu by remember { mutableStateOf<Triple<Tag, List<AppInfo>, Offset>?>(null) }
    var selectedHistoryMenuOffset by remember { mutableStateOf<Offset?>(null) }
    var isTagPopupEditMode by remember { mutableStateOf(false) }

    val popularTagsWithApps = remember(tags, appTags, apps, favoriteItems) {
        val favoritedTagIds = favoriteItems.mapNotNull { (it as? FavoriteItem.TagFolder)?.tag?.id }.toSet()
        tags
            .filter { tag -> tag.id !in favoritedTagIds }
            .map { tag ->
                val taggedApps = apps.filter { app ->
                    val tagIds = appTags[app.componentKey] ?: appTags[app.packageName] ?: emptyList()
                    tagIds.contains(tag.id)
                }
                tag to taggedApps
            }
            .sortedByDescending { it.second.size }
    }

    LaunchedEffect(isActive) {
        if (!isActive) {
            isReorderMode = false
            isHistoryEditMode = false
            selectedTagForPopup = null
            selectedTagForMenu = null
            selectedHistoryMenuOffset = null
            isTagPopupEditMode = false
        }
    }

    LaunchedEffect(isActionMenuOpen) {
        if (isActionMenuOpen) {
            isReorderMode = false
            isHistoryEditMode = false
            selectedTagForPopup = null
            selectedTagForMenu = null
            selectedHistoryMenuOffset = null
            isTagPopupEditMode = false
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE ||
                event == androidx.lifecycle.Lifecycle.Event.ON_STOP ||
                event == androidx.lifecycle.Lifecycle.Event.ON_RESUME
            ) {
                isReorderMode = false
                isHistoryEditMode = false
                selectedTagForPopup = null
                selectedTagForMenu = null
                selectedHistoryMenuOffset = null
                isTagPopupEditMode = false
            }
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.requestHistoryScrollToBottom()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.resetRequest.collect {
            isReorderMode = false
            isHistoryEditMode = false
            selectedTagForPopup = null
            selectedTagForMenu = null
            selectedHistoryMenuOffset = null
            isTagPopupEditMode = false
        }
    }

    val handleAppLongClick: (AppInfo, Offset) -> Unit = { app, offset ->
        isReorderMode = false
        isHistoryEditMode = false
        selectedTagForPopup = null
        selectedTagForMenu = null
        selectedHistoryMenuOffset = null
        isTagPopupEditMode = false
        onAppLongClick(app, offset)
    }

    val handleTagFolderClick: (Tag, List<AppInfo>, Offset) -> Unit = { tag, taggedApps, offset ->
        selectedTagForMenu = null
        selectedHistoryMenuOffset = null
        isTagPopupEditMode = false
        selectedTagForPopup = Triple(tag, taggedApps, offset)
    }

    val handleTagFolderLongClick: (Tag, List<AppInfo>, Offset) -> Unit = { tag, taggedApps, offset ->
        selectedTagForPopup = null
        selectedHistoryMenuOffset = null
        isTagPopupEditMode = false
        isReorderMode = false
        isHistoryEditMode = false
        selectedTagForMenu = Triple(tag, taggedApps, offset)
    }

    val isAnyEditMode = isReorderMode || isHistoryEditMode || selectedTagForPopup != null || selectedTagForMenu != null || selectedHistoryMenuOffset != null
    val currentOnSettingsClick by rememberUpdatedState(onSettingsClick)
    val currentOnSwipeUp by rememberUpdatedState(onSwipeUp)
    val currentOnSwipeDown by rememberUpdatedState(onSwipeDown)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(isActive, isAnyEditMode) {
                if (!isActive || isAnyEditMode) return@pointerInput
                detectTapGestures(
                    onLongPress = { currentOnSettingsClick() }
                )
            }
            .pointerInput(isActive, isAnyEditMode) {
                if (!isActive || isAnyEditMode) return@pointerInput
                var totalDragY = 0f
                detectVerticalDragGestures(
                    onDragStart = { totalDragY = 0f },
                    onDragEnd = {
                        if (totalDragY < -50f) {
                            currentOnSwipeUp()
                        } else if (totalDragY > 50f) {
                            currentOnSwipeDown()
                        }
                        totalDragY = 0f
                    },
                    onDragCancel = {
                        totalDragY = 0f
                    },
                    onVerticalDrag = { _, dragAmount ->
                        totalDragY += dragAmount
                    }
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            val favoritesWeight = 1f
            val historyWeight = 1.6f

            if (handSide == HandSide.LEFT) {
                FavoritesSection(
                    Modifier.weight(favoritesWeight),
                    favoriteItems,
                    handSide,
                    accentColor,
                    primaryTextColor,
                    showShadows,
                    isReorderMode,
                    { isReorderMode = it },
                    { from, to -> viewModel.reorderFavorites(from, to) },
                    { viewModel.toggleFavorite(it) },
                    onAppClick,
                    handleAppLongClick,
                    onTagFolderClick = handleTagFolderClick,
                    onTagFolderLongClick = handleTagFolderLongClick,
                    onSwipeUp,
                    onSwipeDown,
                    onSettingsClick,
                    isActive
                )
                Spacer(modifier = Modifier.width(16.dp))
                HistorySection(
                    viewModel = viewModel,
                    modifier = Modifier.weight(historyWeight),
                    history = history,
                    popularTags = popularTagsWithApps,
                    recentlyUpdatedApps = recentlyUpdatedApps,
                    handSide = handSide,
                    primaryTextColor = primaryTextColor,
                    showShadows = showShadows,
                    accentColor = accentColor,
                    isHistoryPaused = isHistoryPaused,
                    isHistoryEditMode = isHistoryEditMode,
                    setHistoryEditMode = { isHistoryEditMode = it },
                    onRemoveFromHistory = { viewModel.removeFromHistory(it) },
                    onHistoryIconClick = { offset ->
                        selectedTagForMenu = null
                        selectedTagForPopup = null
                        selectedHistoryMenuOffset = offset
                    },
                    onAppClick = onAppClick,
                    onAppLongClick = handleAppLongClick,
                    onTagFolderClick = handleTagFolderClick,
                    onTagFolderLongClick = handleTagFolderLongClick,
                    onSettingsClick = onSettingsClick,
                    isActive = isActive
                )
            } else {
                HistorySection(
                    viewModel = viewModel,
                    modifier = Modifier.weight(historyWeight),
                    history = history,
                    popularTags = popularTagsWithApps,
                    recentlyUpdatedApps = recentlyUpdatedApps,
                    handSide = handSide,
                    primaryTextColor = primaryTextColor,
                    showShadows = showShadows,
                    accentColor = accentColor,
                    isHistoryPaused = isHistoryPaused,
                    isHistoryEditMode = isHistoryEditMode,
                    setHistoryEditMode = { isHistoryEditMode = it },
                    onRemoveFromHistory = { viewModel.removeFromHistory(it) },
                    onHistoryIconClick = { offset ->
                        selectedTagForMenu = null
                        selectedTagForPopup = null
                        selectedHistoryMenuOffset = offset
                    },
                    onAppClick = onAppClick,
                    onAppLongClick = handleAppLongClick,
                    onTagFolderClick = handleTagFolderClick,
                    onTagFolderLongClick = handleTagFolderLongClick,
                    onSettingsClick = onSettingsClick,
                    isActive = isActive
                )
                Spacer(modifier = Modifier.width(16.dp))
                FavoritesSection(
                    Modifier.weight(favoritesWeight),
                    favoriteItems,
                    handSide,
                    accentColor,
                    primaryTextColor,
                    showShadows,
                    isReorderMode,
                    { isReorderMode = it },
                    { from, to -> viewModel.reorderFavorites(from, to) },
                    { viewModel.toggleFavorite(it) },
                    onAppClick,
                    handleAppLongClick,
                    onTagFolderClick = handleTagFolderClick,
                    onTagFolderLongClick = handleTagFolderLongClick,
                    onSwipeUp,
                    onSwipeDown,
                    onSettingsClick,
                    isActive
                )
            }
        }

        selectedHistoryMenuOffset?.let { offset ->
            HistoryActionMenu(
                isHistoryPaused = isHistoryPaused,
                hasHistoryItems = history.isNotEmpty(),
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
                    viewModel.clearHistory()
                },
                accentColor = accentColor,
                primaryTextColor = primaryTextColor
            )
        }

        selectedTagForMenu?.let { (tag, taggedApps, offset) ->
            TagFolderActionMenu(
                tag = tag,
                isFavorite = viewModel.isFavorite("tag:${tag.id}"),
                offset = offset,
                onDismiss = { selectedTagForMenu = null },
                onEditGroup = {
                    selectedTagForMenu = null
                    isTagPopupEditMode = true
                    selectedTagForPopup = Triple(tag, taggedApps, offset)
                },
                onToggleFavorite = {
                    selectedTagForMenu = null
                    viewModel.toggleFavorite("tag:${tag.id}")
                },
                accentColor = accentColor,
                primaryTextColor = primaryTextColor
            )
        }

        selectedTagForPopup?.let { (tag, _, offset) ->
            val currentTaggedApps = remember(tags, appTags, apps, tag.id) {
                apps.filter { app ->
                    val tagIds = appTags[app.componentKey] ?: appTags[app.packageName] ?: emptyList()
                    tagIds.contains(tag.id)
                }
            }

            TagFolderPopup(
                tag = tag,
                apps = currentTaggedApps,
                offset = offset,
                isEditMode = isTagPopupEditMode,
                onAppClick = onAppClick,
                onAppLongClick = onAppLongClick,
                onRemoveAppFromTag = { tagId, componentKey ->
                    viewModel.toggleTagForApp(componentKey, tagId)
                },
                onEditTag = { tagToEdit ->
                    selectedTagForPopup = null
                    onEditTag(tagToEdit)
                },
                onDismiss = { selectedTagForPopup = null },
                primaryTextColor = primaryTextColor,
                showShadows = showShadows,
                accentColor = accentColor
            )
        }
    }
}

/**
 * Renders the launch history list of applications. Displays recently used apps.
 * Supports toggle edit mode via long-pressing the history icon to remove items from history.
 *
 * @param viewModel The view model supplying state data and reset notifications.
 * @param modifier Modifier for UI configurations.
 * @param history The list of recently launched applications.
 * @param handSide Preferred layout orientation side.
 * @param primaryTextColor User selected primary text color.
 * @param showShadows Whether to apply drop shadows.
 * @param accentColor Theme accent color.
 * @param isHistoryEditMode True if history edit mode is active.
 * @param setHistoryEditMode Callback to toggle history edit mode.
 * @param onRemoveFromHistory Callback when a history item is removed.
 * @param onAppClick Callback when a history item is clicked.
 * @param onAppLongClick Callback when a history item is long-pressed.
 * @param onSettingsClick Callback to transition to the Settings screen on empty space tap.
 * @param isActive True if this page is currently active.
 */
@Composable
private fun HistorySection(
    viewModel: LauncherViewModel,
    modifier: Modifier,
    history: List<AppInfo>,
    popularTags: List<Pair<Tag, List<AppInfo>>>,
    recentlyUpdatedApps: Set<String>,
    handSide: HandSide,
    primaryTextColor: PrimaryTextColor,
    showShadows: Boolean,
    accentColor: AccentColor,
    isHistoryPaused: Boolean,
    isHistoryEditMode: Boolean,
    setHistoryEditMode: (Boolean) -> Unit,
    onRemoveFromHistory: (String) -> Unit,
    onHistoryIconClick: (Offset) -> Unit,
    onAppClick: (String) -> Unit,
    onAppLongClick: (AppInfo, Offset) -> Unit,
    onTagFolderClick: (Tag, List<AppInfo>, Offset) -> Unit,
    onTagFolderLongClick: (Tag, List<AppInfo>, Offset) -> Unit,
    onSettingsClick: () -> Unit,
    isActive: Boolean
) {
    val haptic = LocalHapticFeedback.current
    val shadow = primaryTextColor.getShadow(showShadows)

    val listState = rememberLazyListState()
    val tagGridState = rememberLazyGridState()
    val currentOnSettingsClick by rememberUpdatedState(onSettingsClick)
    var isHistoryShiftedUp by remember { mutableStateOf(false) }
    val scrollTrigger by viewModel.historyScrollToBottomTrigger.collectAsState()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.requestHistoryScrollToBottom()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Reset history edit mode and scroll to bottom whenever scrollTrigger changes or screen becomes active
    LaunchedEffect(scrollTrigger, isActive) {
        if (isActive && history.isNotEmpty()) {
            isHistoryShiftedUp = false
            setHistoryEditMode(false)
            listState.scrollToItem(0)
        }
    }

    // Automatically exit history edit mode if all history items were removed
    LaunchedEffect(history.isEmpty()) {
        if (history.isEmpty()) {
            setHistoryEditMode(false)
        }
    }

    Column(
        modifier = modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = if (handSide == HandSide.RIGHT) Alignment.End else Alignment.Start
    ) {
        if (isHistoryShiftedUp) {
            Column(
                modifier = Modifier
                    .fillMaxHeight(0.5f)
                    .fillMaxWidth()
                    .pointerInput(isHistoryShiftedUp) {
                        if (!isHistoryShiftedUp) return@pointerInput
                        awaitEachGesture {
                            awaitFirstDown(pass = PointerEventPass.Initial)
                            val startedAtEdge = (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0)
                            var totalDragY = 0f
                            do {
                                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                val change = event.changes.firstOrNull() ?: break
                                if (change.pressed) {
                                    val deltaY = change.positionChange().y
                                    totalDragY += deltaY
                                    if (startedAtEdge && totalDragY > 18f) {
                                        isHistoryShiftedUp = false
                                        change.consume()
                                        break
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    },
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = if (handSide == HandSide.RIGHT) Alignment.End else Alignment.Start
            ) {
                HistoryContentBlock(
                    listState = listState,
                    history = history,
                    recentlyUpdatedApps = recentlyUpdatedApps,
                    handSide = handSide,
                    primaryTextColor = primaryTextColor,
                    showShadows = showShadows,
                    accentColor = accentColor,
                    isHistoryEditMode = isHistoryEditMode,
                    setHistoryEditMode = setHistoryEditMode,
                    onRemoveFromHistory = onRemoveFromHistory,
                    isHistoryPaused = isHistoryPaused,
                    onHistoryIconClick = onHistoryIconClick,
                    onAppClick = onAppClick,
                    onAppLongClick = onAppLongClick
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .pointerInput(isHistoryShiftedUp) {
                        if (!isHistoryShiftedUp) return@pointerInput
                        awaitEachGesture {
                            awaitFirstDown(pass = PointerEventPass.Initial)
                            val startedAtEdge = (tagGridState.firstVisibleItemIndex == 0 && tagGridState.firstVisibleItemScrollOffset == 0)
                            var totalDragY = 0f
                            var hasDraggedDown = false
                            do {
                                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                val change = event.changes.firstOrNull() ?: break
                                if (change.pressed) {
                                    val deltaY = change.positionChange().y
                                    if (deltaY > 5f) {
                                        hasDraggedDown = true
                                    }
                                    totalDragY += deltaY
                                    if (startedAtEdge && !hasDraggedDown && totalDragY < -18f) {
                                        isHistoryShiftedUp = false
                                        change.consume()
                                        break
                                    }
                                    if (hasDraggedDown && totalDragY > 18f) {
                                        isHistoryShiftedUp = false
                                        change.consume()
                                        break
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    },
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = if (handSide == HandSide.RIGHT) Alignment.End else Alignment.Start
            ) {
                TagsContentBlock(
                    gridState = tagGridState,
                    popularTags = popularTags,
                    handSide = handSide,
                    primaryTextColor = primaryTextColor,
                    showShadows = showShadows,
                    onTagFolderClick = onTagFolderClick,
                    onTagFolderLongClick = onTagFolderLongClick
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxHeight(0.5f)
                    .fillMaxWidth()
                    .pointerInput(isHistoryShiftedUp) {
                        if (isHistoryShiftedUp) return@pointerInput
                        awaitEachGesture {
                            awaitFirstDown(pass = PointerEventPass.Initial)
                            val startedAtEdge = (tagGridState.firstVisibleItemIndex == 0 && tagGridState.firstVisibleItemScrollOffset == 0)
                            var totalDragY = 0f
                            do {
                                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                val change = event.changes.firstOrNull() ?: break
                                if (change.pressed) {
                                    val deltaY = change.positionChange().y
                                    totalDragY += deltaY
                                    if (startedAtEdge && totalDragY < -18f) {
                                        isHistoryShiftedUp = true
                                        change.consume()
                                        break
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    },
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = if (handSide == HandSide.RIGHT) Alignment.End else Alignment.Start
            ) {
                TagsContentBlock(
                    gridState = tagGridState,
                    popularTags = popularTags,
                    handSide = handSide,
                    primaryTextColor = primaryTextColor,
                    showShadows = showShadows,
                    onTagFolderClick = onTagFolderClick,
                    onTagFolderLongClick = onTagFolderLongClick
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .pointerInput(isHistoryShiftedUp) {
                        if (isHistoryShiftedUp) return@pointerInput
                        awaitEachGesture {
                            awaitFirstDown(pass = PointerEventPass.Initial)
                            val startedAtBottom = (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0)
                            var totalDragY = 0f
                            var hasDraggedDown = false
                            do {
                                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                val change = event.changes.firstOrNull() ?: break
                                if (change.pressed) {
                                    val deltaY = change.positionChange().y
                                    if (deltaY > 5f) {
                                        hasDraggedDown = true
                                    }
                                    totalDragY += deltaY
                                    if (startedAtBottom && !hasDraggedDown && totalDragY < -18f) {
                                        isHistoryShiftedUp = true
                                        change.consume()
                                        break
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    },
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = if (handSide == HandSide.RIGHT) Alignment.End else Alignment.Start
            ) {
                HistoryContentBlock(
                    listState = listState,
                    history = history,
                    recentlyUpdatedApps = recentlyUpdatedApps,
                    handSide = handSide,
                    primaryTextColor = primaryTextColor,
                    showShadows = showShadows,
                    accentColor = accentColor,
                    isHistoryEditMode = isHistoryEditMode,
                    setHistoryEditMode = setHistoryEditMode,
                    onRemoveFromHistory = onRemoveFromHistory,
                    isHistoryPaused = isHistoryPaused,
                    onHistoryIconClick = onHistoryIconClick,
                    onAppClick = onAppClick,
                    onAppLongClick = onAppLongClick
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.TagsContentBlock(
    gridState: LazyGridState,
    popularTags: List<Pair<Tag, List<AppInfo>>>,
    handSide: HandSide,
    primaryTextColor: PrimaryTextColor,
    showShadows: Boolean,
    onTagFolderClick: (Tag, List<AppInfo>, Offset) -> Unit,
    onTagFolderLongClick: (Tag, List<AppInfo>, Offset) -> Unit
) {
    if (popularTags.isEmpty()) return

    val layoutDirection = if (handSide == HandSide.RIGHT) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .padding(bottom = 8.dp),
            contentAlignment = if (handSide == HandSide.RIGHT) Alignment.BottomEnd else Alignment.BottomStart
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = gridState,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
                reverseLayout = true
            ) {
                items(popularTags, key = { it.first.id }) { (tag, taggedApps) ->
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        TagFolderItem(
                            tag = tag,
                            apps = taggedApps,
                            onClick = { offset -> onTagFolderClick(tag, taggedApps, offset) },
                            onLongClick = { offset -> onTagFolderLongClick(tag, taggedApps, offset) },
                            primaryTextColor = primaryTextColor,
                            showShadows = showShadows
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.HistoryContentBlock(
    listState: androidx.compose.foundation.lazy.LazyListState,
    history: List<AppInfo>,
    recentlyUpdatedApps: Set<String>,
    handSide: HandSide,
    primaryTextColor: PrimaryTextColor,
    showShadows: Boolean,
    accentColor: AccentColor,
    isHistoryPaused: Boolean,
    isHistoryEditMode: Boolean,
    setHistoryEditMode: (Boolean) -> Unit,
    onRemoveFromHistory: (String) -> Unit,
    onHistoryIconClick: (Offset) -> Unit,
    onAppClick: (String) -> Unit,
    onAppLongClick: (AppInfo, Offset) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Bottom),
        modifier = Modifier
            .weight(1f, fill = false)
            .fillMaxWidth(),
        reverseLayout = true,
        userScrollEnabled = true
    ) {
        items(history, key = { "${it.packageName}/${it.activityName}" }) { app ->
            val appKey = "${app.packageName}/${app.activityName}"
            val isRecentlyUpdated = recentlyUpdatedApps.contains(appKey) || recentlyUpdatedApps.contains(app.componentKey)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (handSide == HandSide.RIGHT) Arrangement.End else Arrangement.Start,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                val showMinusOnLeft = handSide == HandSide.RIGHT
                val showMinusOnRight = handSide == HandSide.LEFT

                if (showMinusOnLeft) {
                    Box(
                        modifier = Modifier.size(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isHistoryEditMode) {
                            IconButton(
                                onClick = { onRemoveFromHistory(appKey) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.RemoveCircle,
                                    contentDescription = "Remove from History",
                                    tint = Color.Red.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                AppListItemWithIcon(
                    app = app,
                    handSide = handSide,
                    iconSize = 48,
                    fontSize = 22,
                    modifier = Modifier.weight(1f),
                    isRecentlyUpdated = isRecentlyUpdated,
                    accentColor = accentColor,
                    onClick = {
                        if (!isHistoryEditMode) {
                            onAppClick(appKey)
                        }
                    },
                    onLongClick = { offset ->
                        if (!isHistoryEditMode) {
                            onAppLongClick(app, offset)
                        }
                    },
                    primaryTextColor = primaryTextColor,
                    showShadows = showShadows
                )

                if (showMinusOnRight) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier.size(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isHistoryEditMode) {
                            IconButton(
                                onClick = { onRemoveFromHistory(appKey) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.RemoveCircle,
                                    contentDescription = "Remove from History",
                                    tint = Color.Red.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (handSide == HandSide.RIGHT) Arrangement.End else Arrangement.Start,
        modifier = Modifier.fillMaxWidth()
    ) {
        val showMinusOnLeft = handSide == HandSide.RIGHT
        val showMinusOnRight = handSide == HandSide.LEFT

        if (showMinusOnLeft) {
            Box(
                modifier = Modifier.size(32.dp),
                contentAlignment = Alignment.Center
            ) {}
            Spacer(modifier = Modifier.width(4.dp))
        }

        var historyIconPosition by remember { mutableStateOf(Offset.Zero) }

        Box(
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .size(44.dp)
                .onGloballyPositioned { historyIconPosition = it.positionInRoot() }
                .pointerInput(isHistoryEditMode) {
                    detectTapGestures(
                        onLongPress = {
                            if (!isHistoryEditMode) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onHistoryIconClick(historyIconPosition + it)
                            }
                        },
                        onTap = {
                            if (isHistoryEditMode) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                setHistoryEditMode(false)
                            } else {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onHistoryIconClick(historyIconPosition + it)
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            if (isHistoryEditMode) {
                Box(contentAlignment = Alignment.Center) {
                    if (showShadows) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = Color.Black.copy(alpha = 0.6f),
                            modifier = Modifier
                                .size(22.dp)
                                .offset(1.dp, 1.dp)
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Exit History Edit Mode",
                        tint = accentColor.color,
                        modifier = Modifier.size(22.dp)
                    )
                }
            } else {
                Box(contentAlignment = Alignment.Center) {
                    val historyIcon = if (isHistoryPaused) Icons.Outlined.HistoryToggleOff else Icons.Outlined.History
                    if (showShadows) {
                        Icon(
                            imageVector = historyIcon,
                            contentDescription = null,
                            tint = primaryTextColor.shadowColor,
                            modifier = Modifier
                                .size(22.dp)
                                .offset(1.dp, 1.dp)
                        )
                    }
                    Icon(
                        imageVector = historyIcon,
                        contentDescription = if (isHistoryPaused) "History (Paused)" else "History",
                        tint = if (isHistoryPaused) accentColor.color.copy(alpha = 0.5f) else accentColor.color,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        if (showMinusOnRight) {
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier.size(32.dp),
                contentAlignment = Alignment.Center
            ) {}
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
    favorites: List<FavoriteItem>,
    handSide: HandSide,
    accentColor: AccentColor,
    primaryTextColor: PrimaryTextColor,
    showShadows: Boolean,
    isReorderMode: Boolean,
    setReorderMode: (Boolean) -> Unit,
    onReorder: (Int, Int) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onAppClick: (String) -> Unit,
    onAppLongClick: (AppInfo, Offset) -> Unit,
    onTagFolderClick: (Tag, List<AppInfo>, Offset) -> Unit,
    onTagFolderLongClick: (Tag, List<AppInfo>, Offset) -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    onSettingsClick: () -> Unit,
    isActive: Boolean
) {
    val shadow = primaryTextColor.getShadow(showShadows)

    val haptic = LocalHapticFeedback.current
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragVerticalOffset by remember { mutableStateOf(0f) }
    var itemHeightPx by remember { mutableStateOf(0f) }

    val currentOnSettingsClick by rememberUpdatedState(onSettingsClick)
    val currentSetReorderMode by rememberUpdatedState(setReorderMode)
    val currentOnSwipeDown by rememberUpdatedState(onSwipeDown)
    val currentOnSwipeUp by rememberUpdatedState(onSwipeUp)
    val currentIsReorderMode by rememberUpdatedState(isReorderMode)
    val currentOnReorder by rememberUpdatedState(onReorder)

    // Reset dragging state if reorder mode is exited
    LaunchedEffect(isReorderMode) {
        if (!isReorderMode) {
            draggingKey = null
            dragVerticalOffset = 0f
        }
    }

    Box(
        modifier = modifier.fillMaxHeight(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Bottom),
                horizontalAlignment = Alignment.CenterHorizontally,
                reverseLayout = true,
                userScrollEnabled = isReorderMode
            ) {
                itemsIndexed(favorites, key = { _, item -> item.key }) { index, item ->
                    val itemKey = item.key
                    val isDraggingThis = draggingKey == itemKey
                    
                    // Track the current index and list size to avoid stale closures during drag
                    val currentIndex by rememberUpdatedState(index)
                    val currentSize by rememberUpdatedState(favorites.size)

                    val scale by animateFloatAsState(if (isDraggingThis) 1.25f else 1.0f, label = "scale")
                    val alpha by animateFloatAsState(if (isDraggingThis) 0.8f else 1.0f, label = "alpha")

                    val density = LocalDensity.current
                    val fallbackItemHeightPx = with(density) { 48.dp.toPx() }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .animateItem()
                            .onGloballyPositioned { coordinates ->
                                if (itemHeightPx == 0f && coordinates.size.height > 0) {
                                    itemHeightPx = coordinates.size.height.toFloat()
                                }
                            }
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                this.alpha = alpha
                                if (isDraggingThis) {
                                    translationY = dragVerticalOffset
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = if (isReorderMode) {
                                Modifier.pointerInput(itemKey, favorites.size) {
                                    var accumulatedDragForSwap = 0f
                                    detectDragGestures(
                                        onDragStart = { 
                                            accumulatedDragForSwap = 0f 
                                            dragVerticalOffset = 0f
                                            draggingKey = itemKey
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDragEnd = { 
                                            draggingKey = null 
                                            dragVerticalOffset = 0f
                                        },
                                        onDragCancel = { 
                                            draggingKey = null 
                                            dragVerticalOffset = 0f
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragVerticalOffset += dragAmount.y
                                            accumulatedDragForSwap += dragAmount.y
                                            
                                            val targetHeight = if (itemHeightPx > 0f) itemHeightPx else fallbackItemHeightPx
                                            val swapThreshold = targetHeight * 0.5f

                                            if (accumulatedDragForSwap < -swapThreshold && currentIndex < currentSize - 1) {
                                                currentOnReorder(currentIndex, currentIndex + 1)
                                                accumulatedDragForSwap = 0f
                                                dragVerticalOffset += targetHeight
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            } else if (accumulatedDragForSwap > swapThreshold && currentIndex > 0) {
                                                currentOnReorder(currentIndex, currentIndex - 1)
                                                accumulatedDragForSwap = 0f
                                                dragVerticalOffset -= targetHeight
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                        }
                                    )
                                }
                            } else Modifier,
                            contentAlignment = Alignment.Center
                        ) {
                            when (item) {
                                is FavoriteItem.App -> {
                                    AppIconItem(
                                        app = item.appInfo,
                                        onClick = { 
                                            if (isReorderMode) setReorderMode(false)
                                            else onAppClick(itemKey)
                                        },
                                        onLongClick = { offset -> 
                                            if (!isReorderMode) onAppLongClick(item.appInfo, offset)
                                        }
                                    )
                                }
                                is FavoriteItem.TagFolder -> {
                                    var folderPosition by remember { mutableStateOf(Offset.Zero) }
                                    val currentOnTagFolderClick by rememberUpdatedState(onTagFolderClick)
                                    val currentOnTagFolderLongClick by rememberUpdatedState(onTagFolderLongClick)
                                    val currentIsReorderMode by rememberUpdatedState(isReorderMode)
                                    val currentSetReorderMode by rememberUpdatedState(setReorderMode)

                                    TagFolderIcon(
                                        tag = item.tag,
                                        apps = item.apps,
                                        modifier = Modifier
                                            .onGloballyPositioned { folderPosition = it.positionInRoot() }
                                            .pointerInput(itemKey, isReorderMode) {
                                                detectTapGestures(
                                                    onTap = {
                                                        if (currentIsReorderMode) currentSetReorderMode(false)
                                                        else currentOnTagFolderClick(item.tag, item.apps, folderPosition + it)
                                                    },
                                                    onLongPress = {
                                                        if (!currentIsReorderMode) {
                                                            currentOnTagFolderLongClick(item.tag, item.apps, folderPosition + it)
                                                        }
                                                    }
                                                )
                                            }
                                    )
                                }
                            }
                        }

                        val showMinusOnLeft = handSide == HandSide.LEFT
                        val showMinusOnRight = handSide == HandSide.RIGHT

                        if (isReorderMode) {
                            val minusOffset = if (showMinusOnLeft) (-48).dp else 48.dp
                            IconButton(
                                onClick = { onToggleFavorite(itemKey) },
                                modifier = Modifier
                                    .offset(x = minusOffset)
                                    .size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.RemoveCircle,
                                    contentDescription = "Remove",
                                    tint = Color.Red.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            
            Box(
                modifier = Modifier
                    .size(44.dp)
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
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isReorderMode) {
                    Box(contentAlignment = Alignment.Center) {
                        if (showShadows) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                tint = Color.Black.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .size(24.dp)
                                    .offset(1.dp, 1.dp)
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Exit Edit Mode",
                            tint = accentColor.color,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else {
                    Text(
                        text = "★", 
                        color = accentColor.color, 
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge.copy(shadow = shadow)
                    )
                }
            }
        }
    }
}
