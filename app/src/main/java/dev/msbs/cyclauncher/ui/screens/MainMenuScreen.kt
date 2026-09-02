package dev.msbs.cyclauncher.ui.screens

import dev.msbs.cyclauncher.LauncherViewModel
import dev.msbs.cyclauncher.HandSide
import dev.msbs.cyclauncher.model.AppInfo
import dev.msbs.cyclauncher.model.FavoriteItem
import dev.msbs.cyclauncher.model.Tag
import dev.msbs.cyclauncher.ui.theme.AccentColor
import dev.msbs.cyclauncher.ui.theme.PrimaryTextColor
import dev.msbs.cyclauncher.ui.theme.LocalShadowSettings
import dev.msbs.cyclauncher.ui.components.AppListItemWithIcon
import dev.msbs.cyclauncher.ui.components.AppIconItem
import dev.msbs.cyclauncher.ui.components.TagFolderIcon
import dev.msbs.cyclauncher.ui.components.TagFolderActionMenu
import dev.msbs.cyclauncher.ui.components.TagSectionActionMenu
import dev.msbs.cyclauncher.ui.components.TagSortPopup
import dev.msbs.cyclauncher.ui.components.TagFolderItem
import dev.msbs.cyclauncher.ui.components.TagFolderPopup
import dev.msbs.cyclauncher.ui.components.HistoryActionMenu
import dev.msbs.cyclauncher.ui.components.rememberAppIconPainter
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import kotlin.math.roundToInt
import dev.msbs.cyclauncher.ui.theme.LocalAnimationsEnabled
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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.zIndex
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.outlined.Folder
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
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.LayoutDirection

/**
 * Main launcher home screen displaying favorite apps and recent launch history.
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
    val tagAppOrders by viewModel.tagAppOrders.collectAsState()
    val apps by viewModel.apps.collectAsState()
    val handSide by viewModel.handSide.collectAsState()
    val showShadows by viewModel.showShadows.collectAsState()
    val accentColor by viewModel.accentColor.collectAsState()
    val primaryTextColor by viewModel.primaryTextColor.collectAsState()
    val popupTheme by viewModel.popupTheme.collectAsState()
    val isHistoryPaused by viewModel.isHistoryPaused.collectAsState()
    val recentlyUpdatedApps by viewModel.recentlyUpdatedApps.collectAsState()
    var isReorderMode by remember { mutableStateOf(false) }
    var isHistoryEditMode by remember { mutableStateOf(false) }
    var isTagFolderReorderMode by remember { mutableStateOf(false) }
    var selectedTagForPopup by remember { mutableStateOf<Triple<Tag, List<AppInfo>, Offset>?>(null) }
    var selectedTagForMenu by remember { mutableStateOf<Triple<Tag, List<AppInfo>, Offset>?>(null) }
    var selectedTagSectionMenuOffset by remember { mutableStateOf<Offset?>(null) }
    var selectedTagSortPopupOffset by remember { mutableStateOf<Offset?>(null) }
    var selectedHistoryMenuOffset by remember { mutableStateOf<Offset?>(null) }
    var isTagPopupEditMode by remember { mutableStateOf(false) }

    val popularTagsWithApps by viewModel.popularTagsWithApps.collectAsState()

    BackHandler(enabled = isTagFolderReorderMode) {
        isTagFolderReorderMode = false
    }

    LaunchedEffect(isActive) {
        if (!isActive) {
            isReorderMode = false
            isHistoryEditMode = false
            isTagFolderReorderMode = false
            selectedTagForPopup = null
            selectedTagForMenu = null
            selectedTagSectionMenuOffset = null
            selectedTagSortPopupOffset = null
            selectedHistoryMenuOffset = null
            isTagPopupEditMode = false
        }
    }

    LaunchedEffect(isActionMenuOpen) {
        if (isActionMenuOpen) {
            isReorderMode = false
            isHistoryEditMode = false
            isTagFolderReorderMode = false
            selectedTagForPopup = null
            selectedTagForMenu = null
            selectedTagSectionMenuOffset = null
            selectedTagSortPopupOffset = null
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
                isTagFolderReorderMode = false
                selectedTagForPopup = null
                selectedTagForMenu = null
                selectedTagSectionMenuOffset = null
                selectedTagSortPopupOffset = null
                selectedHistoryMenuOffset = null
                isTagPopupEditMode = false
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
            isTagFolderReorderMode = false
            selectedTagForPopup = null
            selectedTagForMenu = null
            selectedTagSectionMenuOffset = null
            selectedTagSortPopupOffset = null
            selectedHistoryMenuOffset = null
            isTagPopupEditMode = false
        }
    }

    var lastItemActionTime by remember { mutableLongStateOf(0L) }
    val markItemAction = { lastItemActionTime = System.currentTimeMillis() }

    val handleAppClick: (String) -> Unit = { key ->
        markItemAction()
        onAppClick(key)
    }

    val handleAppLongClick: (AppInfo, Offset) -> Unit = { app, offset ->
        markItemAction()
        isReorderMode = false
        isHistoryEditMode = false
        isTagFolderReorderMode = false
        selectedTagForPopup = null
        selectedTagForMenu = null
        selectedTagSectionMenuOffset = null
        selectedTagSortPopupOffset = null
        selectedHistoryMenuOffset = null
        isTagPopupEditMode = false
        onAppLongClick(app, offset)
    }

    val handleTagFolderClick: (Tag, List<AppInfo>, Offset) -> Unit = { tag, taggedApps, offset ->
        markItemAction()
        selectedTagForMenu = null
        selectedTagSectionMenuOffset = null
        selectedTagSortPopupOffset = null
        selectedHistoryMenuOffset = null
        isTagPopupEditMode = false
        selectedTagForPopup = Triple(tag, taggedApps, offset)
    }

    val handleTagFolderLongClick: (Tag, List<AppInfo>, Offset) -> Unit = { tag, taggedApps, offset ->
        markItemAction()
        selectedTagForPopup = null
        selectedTagSectionMenuOffset = null
        selectedTagSortPopupOffset = null
        selectedHistoryMenuOffset = null
        isTagPopupEditMode = false
        isReorderMode = false
        isHistoryEditMode = false
        isTagFolderReorderMode = false
        selectedTagForMenu = Triple(tag, taggedApps, offset)
    }

    val isAnyEditMode =
        isReorderMode || isHistoryEditMode || isTagFolderReorderMode || selectedTagForPopup != null || selectedTagForMenu != null || selectedTagSectionMenuOffset != null || selectedTagSortPopupOffset != null || selectedHistoryMenuOffset != null
    val currentOnSettingsClick by rememberUpdatedState(onSettingsClick)
    val currentOnSwipeUp by rememberUpdatedState(onSwipeUp)
    val currentOnSwipeDown by rememberUpdatedState(onSwipeDown)

    val safeOnSettingsClick: () -> Unit = {
        if (isActive && !isActionMenuOpen && !isAnyEditMode && System.currentTimeMillis() - lastItemActionTime > 400L) {
            currentOnSettingsClick()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(isActive, isAnyEditMode, isActionMenuOpen) {
                if (!isActive || isAnyEditMode || isActionMenuOpen) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true)
                    var isDrag = false
                    var totalDragY = 0f
                    var isLongPressHandled = false

                    val timeoutMillis = viewConfiguration.longPressTimeoutMillis

                    val dragOrTimeout = withTimeoutOrNull(timeoutMillis) {
                        while (true) {
                            val event = awaitPointerEvent(pass = PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            if (change.isConsumed) {
                                return@withTimeoutOrNull false
                            }

                            val positionChange = change.positionChange()
                            totalDragY += positionChange.y

                            if (kotlin.math.abs(totalDragY) > viewConfiguration.touchSlop) {
                                isDrag = true
                                change.consume()
                                return@withTimeoutOrNull true
                            }
                        }
                        false
                    }

                    if (dragOrTimeout == null && !isDrag) {
                        safeOnSettingsClick()
                        isLongPressHandled = true
                        while (true) {
                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                            event.changes.forEach { it.consume() }
                            if (!event.changes.any { it.pressed }) break
                        }
                    }

                    if (isDrag && !isLongPressHandled) {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                if (isActive && !isActionMenuOpen && !isAnyEditMode) {
                                    if (totalDragY > 40f) {
                                        currentOnSwipeDown()
                                    }
                                }
                                break
                            }

                            val positionChange = change.positionChange()
                            totalDragY += positionChange.y
                            change.consume()
                        }
                    }
                }
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
                    setReorderMode = { isReorderMode = it },
                    onReorder = { from, to -> viewModel.reorderFavorites(from, to) },
                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                    onAppClick = handleAppClick,
                    onAppLongClick = handleAppLongClick,
                    onTagFolderClick = handleTagFolderClick,
                    onTagFolderLongClick = handleTagFolderLongClick,
                    onSwipeUp = onSwipeUp,
                    onSwipeDown = onSwipeDown,
                    onSettingsClick = safeOnSettingsClick,
                    isActive = isActive
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
                    isTagFolderReorderMode = isTagFolderReorderMode,
                    setTagFolderReorderMode = { isTagFolderReorderMode = it },
                    onReorderTag = { from, to ->
                        if (from in popularTagsWithApps.indices && to in popularTagsWithApps.indices) {
                            viewModel.reorderTagById(popularTagsWithApps[from].first.id, popularTagsWithApps[to].first.id)
                        }
                    },
                    onRemoveFromHistory = { viewModel.removeFromHistory(it) },
                    onHistoryIconClick = { offset ->
                        markItemAction()
                        selectedTagForMenu = null
                        selectedTagForPopup = null
                        selectedTagSectionMenuOffset = null
                        selectedHistoryMenuOffset = offset
                    },
                    onTagSectionIconClick = { offset ->
                        markItemAction()
                        selectedTagForMenu = null
                        selectedTagForPopup = null
                        selectedHistoryMenuOffset = null
                        selectedTagSectionMenuOffset = offset
                    },
                    onAppClick = handleAppClick,
                    onAppLongClick = handleAppLongClick,
                    onTagFolderClick = handleTagFolderClick,
                    onTagFolderLongClick = handleTagFolderLongClick,
                    onSettingsClick = safeOnSettingsClick,
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
                    isTagFolderReorderMode = isTagFolderReorderMode,
                    setTagFolderReorderMode = { isTagFolderReorderMode = it },
                    onReorderTag = { from, to ->
                        if (from in popularTagsWithApps.indices && to in popularTagsWithApps.indices) {
                            viewModel.reorderTagById(popularTagsWithApps[from].first.id, popularTagsWithApps[to].first.id)
                        }
                    },
                    onRemoveFromHistory = { viewModel.removeFromHistory(it) },
                    onHistoryIconClick = { offset ->
                        markItemAction()
                        selectedTagForMenu = null
                        selectedTagForPopup = null
                        selectedTagSectionMenuOffset = null
                        selectedHistoryMenuOffset = offset
                    },
                    onTagSectionIconClick = { offset ->
                        markItemAction()
                        selectedTagForMenu = null
                        selectedTagForPopup = null
                        selectedHistoryMenuOffset = null
                        selectedTagSectionMenuOffset = offset
                    },
                    onAppClick = handleAppClick,
                    onAppLongClick = handleAppLongClick,
                    onTagFolderClick = handleTagFolderClick,
                    onTagFolderLongClick = handleTagFolderLongClick,
                    onSettingsClick = safeOnSettingsClick,
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
                    setReorderMode = { isReorderMode = it },
                    onReorder = { from, to -> viewModel.reorderFavorites(from, to) },
                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                    onAppClick = handleAppClick,
                    onAppLongClick = handleAppLongClick,
                    onTagFolderClick = handleTagFolderClick,
                    onTagFolderLongClick = handleTagFolderLongClick,
                    onSwipeUp = onSwipeUp,
                    onSwipeDown = onSwipeDown,
                    onSettingsClick = safeOnSettingsClick,
                    isActive = isActive
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
                primaryTextColor = primaryTextColor,
                popupTheme = popupTheme
            )
        }

        selectedTagForMenu?.let { (tag, taggedApps, offset) ->
            val currentTag = tags.find { it.id == tag.id } ?: tag
            TagFolderActionMenu(
                tag = currentTag,
                isFavorite = viewModel.isFavorite("tag:${currentTag.id}"),
                offset = offset,
                onDismiss = { selectedTagForMenu = null },
                onEditGroup = {
                    selectedTagForMenu = null
                    isTagPopupEditMode = true
                    selectedTagForPopup = Triple(currentTag, taggedApps, offset)
                },
                onToggleFavorite = {
                    selectedTagForMenu = null
                    viewModel.toggleFavorite("tag:${currentTag.id}")
                },
                accentColor = accentColor,
                primaryTextColor = primaryTextColor,
                popupTheme = popupTheme
            )
        }

        selectedTagSectionMenuOffset?.let { offset ->
            TagSectionActionMenu(
                offset = offset,
                onDismiss = { selectedTagSectionMenuOffset = null },
                onReorderFolders = {
                    selectedTagSectionMenuOffset = null
                    isTagFolderReorderMode = true
                },
                onOpenSortMenu = {
                    val curOffset = offset
                    selectedTagSectionMenuOffset = null
                    selectedTagSortPopupOffset = curOffset
                },
                accentColor = accentColor,
                primaryTextColor = primaryTextColor,
                popupTheme = popupTheme
            )
        }

        selectedTagSortPopupOffset?.let { offset ->
            TagSortPopup(
                offset = offset,
                onDismiss = { selectedTagSortPopupOffset = null },
                onSortByNameAsc = {
                    selectedTagSortPopupOffset = null
                    viewModel.sortTagsByName(true)
                },
                onSortByNameDesc = {
                    selectedTagSortPopupOffset = null
                    viewModel.sortTagsByName(false)
                },
                onSortByAppCountDesc = {
                    selectedTagSortPopupOffset = null
                    viewModel.sortTagsByAppCount(false)
                },
                onSortByAppCountAsc = {
                    selectedTagSortPopupOffset = null
                    viewModel.sortTagsByAppCount(true)
                },
                accentColor = accentColor,
                primaryTextColor = primaryTextColor,
                popupTheme = popupTheme
            )
        }

        selectedTagForPopup?.let { (tag, _, offset) ->
            val currentTag = tags.find { it.id == tag.id } ?: tag
            val currentTaggedApps = remember(tags, appTags, tagAppOrders, apps, currentTag.id) {
                val rawApps = apps.filter { app ->
                    val tagIds = appTags[app.componentKey] ?: appTags[app.packageName] ?: emptyList()
                    tagIds.contains(currentTag.id)
                }
                dev.msbs.cyclauncher.orderTagApps(rawApps, tagAppOrders[currentTag.id])
            }

            TagFolderPopup(
                tag = currentTag,
                apps = currentTaggedApps,
                offset = offset,
                isEditMode = isTagPopupEditMode,
                onAppClick = onAppClick,
                onAppLongClick = onAppLongClick,
                onRemoveAppFromTag = { tagId, componentKey ->
                    viewModel.toggleTagForApp(componentKey, tagId)
                },
                onReorderApp = { fromIndex, toIndex ->
                    viewModel.reorderAppInTag(currentTag.id, fromIndex, toIndex, currentTaggedApps)
                },
                onEditTag = { tagToEdit ->
                    selectedTagForPopup = null
                    onEditTag(tagToEdit)
                },
                onExitEditMode = { isTagPopupEditMode = false },
                onDismiss = {
                    selectedTagForPopup = null
                    isTagPopupEditMode = false
                },
                primaryTextColor = primaryTextColor,
                showShadows = showShadows,
                accentColor = accentColor,
                popupTheme = popupTheme
            )
        }
    }
}

/**
 * Section displaying recent app history and popular tag folders.
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
    isTagFolderReorderMode: Boolean = false,
    setTagFolderReorderMode: (Boolean) -> Unit = {},
    onReorderTag: (Int, Int) -> Unit = { _, _ -> },
    onRemoveFromHistory: (String) -> Unit,
    onHistoryIconClick: (Offset) -> Unit,
    onTagSectionIconClick: (Offset) -> Unit = {},
    onAppClick: (String) -> Unit,
    onAppLongClick: (AppInfo, Offset) -> Unit,
    onTagFolderClick: (Tag, List<AppInfo>, Offset) -> Unit,
    onTagFolderLongClick: (Tag, List<AppInfo>, Offset) -> Unit,
    onSettingsClick: () -> Unit,
    isActive: Boolean
) {
    val listState = rememberLazyListState()
    val tagGridState = rememberLazyGridState()
    val currentOnSettingsClick by rememberUpdatedState(onSettingsClick)
    var isHistoryShiftedUp by remember { mutableStateOf(false) }
    val scrollTrigger by viewModel.historyScrollToBottomTrigger.collectAsState()
    val viewConfiguration = LocalViewConfiguration.current

    LaunchedEffect(scrollTrigger, isActive) {
        if (isActive) {
            isHistoryShiftedUp = false
            setHistoryEditMode(false)
            setTagFolderReorderMode(false)
            if (history.isNotEmpty() && (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0)) {
                listState.scrollToItem(0)
            }
            if (popularTags.isNotEmpty() && (tagGridState.firstVisibleItemIndex != 0 || tagGridState.firstVisibleItemScrollOffset != 0)) {
                tagGridState.scrollToItem(0)
            }
        }
    }

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
                    .fillMaxWidth(),
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
                    isHistoryPaused = isHistoryPaused,
                    isHistoryEditMode = isHistoryEditMode,
                    setHistoryEditMode = setHistoryEditMode,
                    onRemoveFromHistory = onRemoveFromHistory,
                    onHistoryIconClick = onHistoryIconClick,
                    onSettingsClick = currentOnSettingsClick,
                    onAppClick = onAppClick,
                    onAppLongClick = onAppLongClick
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .sectionBottomGestures(
                        isAtEdge = { tagGridState.firstVisibleItemIndex == 0 && tagGridState.firstVisibleItemScrollOffset == 0 },
                        isEditMode = isHistoryEditMode || isTagFolderReorderMode,
                        touchSlop = viewConfiguration.touchSlop,
                        onSwipeUp = { isHistoryShiftedUp = false }
                    ),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = if (handSide == HandSide.RIGHT) Alignment.End else Alignment.Start
            ) {
                TagsContentBlock(
                    gridState = tagGridState,
                    popularTags = popularTags,
                    handSide = handSide,
                    primaryTextColor = primaryTextColor,
                    showShadows = showShadows,
                    accentColor = accentColor,
                    isReorderMode = isTagFolderReorderMode,
                    setReorderMode = setTagFolderReorderMode,
                    onReorderTag = onReorderTag,
                    onTagSectionIconClick = onTagSectionIconClick,
                    onSettingsClick = currentOnSettingsClick,
                    onTagFolderClick = onTagFolderClick,
                    onTagFolderLongClick = onTagFolderLongClick
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxHeight(0.5f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = if (handSide == HandSide.RIGHT) Alignment.End else Alignment.Start
            ) {
                TagsContentBlock(
                    gridState = tagGridState,
                    popularTags = popularTags,
                    handSide = handSide,
                    primaryTextColor = primaryTextColor,
                    showShadows = showShadows,
                    accentColor = accentColor,
                    isReorderMode = isTagFolderReorderMode,
                    setReorderMode = setTagFolderReorderMode,
                    onReorderTag = onReorderTag,
                    onTagSectionIconClick = onTagSectionIconClick,
                    onSettingsClick = currentOnSettingsClick,
                    onTagFolderClick = onTagFolderClick,
                    onTagFolderLongClick = onTagFolderLongClick
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .sectionBottomGestures(
                        isAtEdge = { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 },
                        isEditMode = isHistoryEditMode || isTagFolderReorderMode,
                        touchSlop = viewConfiguration.touchSlop,
                        onSwipeUp = { isHistoryShiftedUp = true }
                    ),
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
                    isHistoryPaused = isHistoryPaused,
                    isHistoryEditMode = isHistoryEditMode,
                    setHistoryEditMode = setHistoryEditMode,
                    onRemoveFromHistory = onRemoveFromHistory,
                    onHistoryIconClick = onHistoryIconClick,
                    onSettingsClick = currentOnSettingsClick,
                    onAppClick = onAppClick,
                    onAppLongClick = onAppLongClick
                )
            }
        }
    }
}

private fun Modifier.sectionBottomGestures(
    isAtEdge: () -> Boolean,
    isEditMode: Boolean,
    touchSlop: Float,
    onSwipeUp: () -> Unit
): Modifier = this
    .pointerInput(isEditMode) {
        if (isEditMode) return@pointerInput
        awaitEachGesture {
            awaitFirstDown(pass = PointerEventPass.Initial)
            val startedAtBottom = isAtEdge()
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
                    if (kotlin.math.abs(totalDragY) > touchSlop) {
                        if (startedAtBottom && !hasDraggedDown && totalDragY < -18f) {
                            onSwipeUp()
                            change.consume()
                            break
                        }
                    }
                }
            } while (event.changes.any { it.pressed })
        }
    }

@Composable
private fun ColumnScope.TagsContentBlock(
    gridState: LazyGridState,
    popularTags: List<Pair<Tag, List<AppInfo>>>,
    handSide: HandSide,
    primaryTextColor: PrimaryTextColor,
    showShadows: Boolean,
    accentColor: AccentColor,
    isReorderMode: Boolean = false,
    setReorderMode: (Boolean) -> Unit = {},
    onReorderTag: (Int, Int) -> Unit = { _, _ -> },
    onTagSectionIconClick: (Offset) -> Unit = {},
    onSettingsClick: () -> Unit,
    onTagFolderClick: (Tag, List<AppInfo>, Offset) -> Unit,
    onTagFolderLongClick: (Tag, List<AppInfo>, Offset) -> Unit
) {
    if (popularTags.isEmpty()) return

    val layoutDirection = if (handSide == HandSide.RIGHT) LayoutDirection.Rtl else LayoutDirection.Ltr
    val isRtl = handSide == HandSide.RIGHT
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val animationsEnabled = LocalAnimationsEnabled.current

    val infiniteTransition = rememberInfiniteTransition(label = "tag_shake")
    val shakeRotation by infiniteTransition.animateFloat(
        initialValue = -3.5f,
        targetValue = 3.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 105, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shake_rot"
    )
    val shakeTranslation by infiniteTransition.animateFloat(
        initialValue = -1.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 125, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shake_trans"
    )

    var draggingTagId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var itemWidthPx by remember { mutableFloatStateOf(0f) }
    var itemHeightPx by remember { mutableFloatStateOf(0f) }

    val localPopularTags = remember(popularTags) { mutableStateListOf(*popularTags.toTypedArray()) }
    LaunchedEffect(popularTags, draggingTagId) {
        if (draggingTagId == null) {
            localPopularTags.clear()
            localPopularTags.addAll(popularTags)
        }
    }

    val currentOnReorderTag by rememberUpdatedState(onReorderTag)

    LaunchedEffect(isReorderMode) {
        if (!isReorderMode) {
            draggingTagId = null
            dragOffset = Offset.Zero
        }
    }

    val colSpacingPx = with(density) { 8.dp.toPx() }
    val rowSpacingPx = with(density) { 8.dp.toPx() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f, fill = false)
            .padding(bottom = 8.dp),
        horizontalAlignment = if (handSide == HandSide.RIGHT) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.Bottom
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = gridState,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                reverseLayout = true,
                userScrollEnabled = true
            ) {
                itemsIndexed(localPopularTags, key = { _, item -> item.first.id }) { index, (tag, taggedApps) ->
                    val isDraggingThis = draggingTagId == tag.id

                    val scale by animateFloatAsState(
                        targetValue = if (isDraggingThis) 1.15f else 1.0f,
                        animationSpec = if (animationsEnabled) spring() else snap(),
                        label = "tag_scale"
                    )
                    val alpha by animateFloatAsState(
                        targetValue = if (isDraggingThis) 0.82f else 1.0f,
                        animationSpec = if (animationsEnabled) spring() else snap(),
                        label = "tag_alpha"
                    )

                    val itemAnimModifier = if (animationsEnabled && !isDraggingThis) Modifier.animateItem() else Modifier

                    val itemRotation = if (isReorderMode && !isDraggingThis) {
                        if (index % 2 == 0) shakeRotation else -shakeRotation
                    } else 0f

                    val itemTranslation = if (isReorderMode && !isDraggingThis) {
                        if ((index / 2) % 2 == 0) shakeTranslation else -shakeTranslation
                    } else 0f

                    val gestureModifier = if (isReorderMode) {
                        Modifier.pointerInput(tag.id, localPopularTags.size) {
                            detectDragGestures(
                                onDragStart = {
                                    draggingTagId = tag.id
                                    dragOffset = Offset.Zero
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffset += dragAmount

                                    if (itemWidthPx > 0f && itemHeightPx > 0f) {
                                        val colStep = itemWidthPx + colSpacingPx
                                        val rowStep = itemHeightPx + rowSpacingPx

                                        val effectiveDx = if (isRtl) -dragOffset.x else dragOffset.x
                                        val effectiveDy = -dragOffset.y

                                        val colDelta = (effectiveDx / colStep).roundToInt()
                                        val rowDelta = (effectiveDy / rowStep).roundToInt()

                                        val curIndex = localPopularTags.indexOfFirst { it.first.id == tag.id }
                                        if (curIndex != -1) {
                                            val curCol = curIndex % 3
                                            val curRow = curIndex / 3
                                            val targetCol = (curCol + colDelta).coerceIn(0, 2)
                                            val targetRow = (curRow + rowDelta).coerceAtLeast(0)
                                            val targetIndex = (targetRow * 3 + targetCol).coerceIn(0, localPopularTags.size - 1)

                                            if (targetIndex != curIndex && targetIndex in localPopularTags.indices) {
                                                val actualColChange = targetCol - curCol
                                                val actualRowChange = targetRow - curRow

                                                val dxToSub = if (isRtl) -actualColChange * colStep else actualColChange * colStep
                                                val dyToSub = -actualRowChange * rowStep

                                                dragOffset = Offset(dragOffset.x - dxToSub, dragOffset.y - dyToSub)
                                                val moved = localPopularTags.removeAt(curIndex)
                                                localPopularTags.add(targetIndex, moved)
                                                currentOnReorderTag(curIndex, targetIndex)
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                        }
                                    }
                                },
                                onDragEnd = {
                                    draggingTagId = null
                                    dragOffset = Offset.Zero
                                },
                                onDragCancel = {
                                    draggingTagId = null
                                    dragOffset = Offset.Zero
                                }
                            )
                        }
                    } else {
                        Modifier
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(itemAnimModifier)
                            .zIndex(if (isDraggingThis) 10f else 1f)
                            .onGloballyPositioned { coordinates ->
                                if (itemWidthPx == 0f && coordinates.size.width > 0) {
                                    itemWidthPx = coordinates.size.width.toFloat()
                                    itemHeightPx = coordinates.size.height.toFloat()
                                }
                            }
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                this.alpha = alpha
                                if (isDraggingThis) {
                                    translationX = dragOffset.x
                                    translationY = dragOffset.y
                                    rotationZ = 0f
                                } else if (isReorderMode) {
                                    rotationZ = itemRotation
                                    translationX = itemTranslation
                                    translationY = if (index % 2 == 0) itemTranslation * 0.4f else -itemTranslation * 0.4f
                                }
                            }
                            .then(gestureModifier),
                        contentAlignment = Alignment.Center
                    ) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            TagFolderItem(
                                tag = tag,
                                apps = taggedApps,
                                onClick = { offset ->
                                    if (!isReorderMode) {
                                        onTagFolderClick(tag, taggedApps, offset)
                                    }
                                },
                                onLongClick = { offset ->
                                    if (!isReorderMode) {
                                        onTagFolderLongClick(tag, taggedApps, offset)
                                    }
                                },
                                primaryTextColor = primaryTextColor,
                                showShadows = showShadows
                            )
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
                val showPaddingOnLeft = handSide == HandSide.RIGHT
                val showPaddingOnRight = handSide == HandSide.LEFT

                if (showPaddingOnLeft) {
                    Box(
                        modifier = Modifier.size(32.dp),
                        contentAlignment = Alignment.Center
                    ) {}
                    Spacer(modifier = Modifier.width(4.dp))
                }

                var tagIconPosition by remember { mutableStateOf(Offset.Zero) }

                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(44.dp)
                        .onGloballyPositioned { tagIconPosition = it.positionInRoot() }
                        .pointerInput(isReorderMode) {
                            detectTapGestures(
                                onLongPress = {
                                    if (!isReorderMode) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onTagSectionIconClick(tagIconPosition + it)
                                    }
                                },
                                onTap = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (isReorderMode) {
                                        setReorderMode(false)
                                    } else {
                                        onTagSectionIconClick(tagIconPosition + it)
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
                                        .size(22.dp)
                                        .offset(1.dp, 1.dp)
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Exit Tag Reorder Mode",
                                tint = accentColor.color,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            val tagIcon = Icons.Outlined.Folder
                            if (showShadows) {
                                val shadowSettings = LocalShadowSettings.current
                                Icon(
                                    imageVector = tagIcon,
                                    contentDescription = null,
                                    tint = primaryTextColor.getShadowColor(shadowSettings.shadowColorOverride),
                                    modifier = Modifier
                                        .size(22.dp)
                                        .offset(1.dp, 1.dp)
                                )
                            }
                            Icon(
                                imageVector = tagIcon,
                                contentDescription = "Tag Folders",
                                tint = accentColor.color,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                if (showPaddingOnRight) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier.size(32.dp),
                        contentAlignment = Alignment.Center
                    ) {}
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
    onSettingsClick: () -> Unit,
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
            val isRecentlyUpdated =
                recentlyUpdatedApps.contains(appKey) || recentlyUpdatedApps.contains(app.componentKey)

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
                                Box(contentAlignment = Alignment.Center) {
                                    if (showShadows) {
                                        val shadowSettings = LocalShadowSettings.current
                                        Icon(
                                            imageVector = Icons.Default.RemoveCircle,
                                            contentDescription = null,
                                            tint = primaryTextColor.getShadowColor(shadowSettings.shadowColorOverride),
                                            modifier = Modifier
                                                .size(24.dp)
                                                .offset(1.dp, 1.dp)
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.RemoveCircle,
                                        contentDescription = "Remove from History",
                                        tint = Color.Red.copy(alpha = 0.8f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
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
                                Box(contentAlignment = Alignment.Center) {
                                    if (showShadows) {
                                        val shadowSettings = LocalShadowSettings.current
                                        Icon(
                                            imageVector = Icons.Default.RemoveCircle,
                                            contentDescription = null,
                                            tint = primaryTextColor.getShadowColor(shadowSettings.shadowColorOverride),
                                            modifier = Modifier
                                                .size(24.dp)
                                                .offset(1.dp, 1.dp)
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.RemoveCircle,
                                        contentDescription = "Remove from History",
                                        tint = Color.Red.copy(alpha = 0.8f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
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
                        val shadowSettings = LocalShadowSettings.current
                        Icon(
                            imageVector = historyIcon,
                            contentDescription = null,
                            tint = primaryTextColor.getShadowColor(shadowSettings.shadowColorOverride),
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
 * Section displaying favorite apps and folders with drag-to-reorder support.
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
    val shadow = primaryTextColor.getShadow(showShadows, LocalShadowSettings.current.shadowColorOverride)

    val haptic = LocalHapticFeedback.current
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragVerticalOffset by remember { mutableStateOf(0f) }
    var itemHeightPx by remember { mutableStateOf(0f) }

    val localFavorites = remember(favorites) { mutableStateListOf(*favorites.toTypedArray()) }
    LaunchedEffect(favorites, draggingKey) {
        if (draggingKey == null) {
            localFavorites.clear()
            localFavorites.addAll(favorites)
        }
    }

    val currentSetReorderMode by rememberUpdatedState(setReorderMode)
    val currentOnReorder by rememberUpdatedState(onReorder)
    LaunchedEffect(isReorderMode) {
        if (!isReorderMode) {
            draggingKey = null
            dragVerticalOffset = 0f
        }
    }

    val currentOnSwipeUp by rememberUpdatedState(onSwipeUp)
    val currentOnSwipeDown by rememberUpdatedState(onSwipeDown)
    val currentOnSettingsClick by rememberUpdatedState(onSettingsClick)
    val currentIsActive by rememberUpdatedState(isActive)
    val viewConfiguration = LocalViewConfiguration.current

    Box(
        modifier = modifier
            .fillMaxHeight()
            .pointerInput(isReorderMode, isActive) {
                if (isReorderMode || !isActive) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
                    var isDrag = false
                    var totalDragY = 0f
                    var totalDragX = 0f
                    var isLongPressHandled = false

                    val timeoutMillis = viewConfiguration.longPressTimeoutMillis

                    val dragOrTimeout = withTimeoutOrNull(timeoutMillis) {
                        while (true) {
                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break

                            val positionChange = change.positionChange()
                            totalDragY += positionChange.y
                            totalDragX += positionChange.x

                            if (kotlin.math.abs(totalDragY) > viewConfiguration.touchSlop && kotlin.math.abs(totalDragY) > kotlin.math.abs(totalDragX)) {
                                isDrag = true
                                change.consume()
                                return@withTimeoutOrNull true
                            }
                        }
                        false
                    }

                    if (dragOrTimeout == null && !isDrag) {
                        val lastEvent = awaitPointerEvent(pass = PointerEventPass.Main)
                        val change = lastEvent.changes.firstOrNull { it.id == down.id }
                        val isConsumedByChild = change?.isConsumed == true

                        if (!isConsumedByChild) {
                            currentOnSettingsClick()
                            isLongPressHandled = true
                            while (true) {
                                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                event.changes.forEach { it.consume() }
                                if (!event.changes.any { it.pressed }) break
                            }
                        }
                    }

                    if (!isLongPressHandled) {
                        while (true) {
                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                if (isDrag && currentIsActive) {
                                    if (totalDragY < -40f) {
                                        currentOnSwipeUp()
                                    } else if (totalDragY > 40f) {
                                        currentOnSwipeDown()
                                    }
                                }
                                break
                            }

                            val positionChange = change.positionChange()
                            totalDragY += positionChange.y
                            totalDragX += positionChange.x

                            if (!isDrag && kotlin.math.abs(totalDragY) > viewConfiguration.touchSlop && kotlin.math.abs(totalDragY) > kotlin.math.abs(totalDragX)) {
                                isDrag = true
                            }

                            if (isDrag) {
                                change.consume()
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom
        ) {

            LazyColumn(
                modifier = Modifier
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Bottom),
                horizontalAlignment = Alignment.CenterHorizontally,
                reverseLayout = true,
                userScrollEnabled = isReorderMode
            ) {
                itemsIndexed(localFavorites, key = { _, item -> item.key }) { index, item ->
                    val itemKey = item.key
                    val isDraggingThis = draggingKey == itemKey

                    val animationsEnabled = LocalAnimationsEnabled.current
                    val scale by animateFloatAsState(
                        targetValue = if (isDraggingThis) 1.15f else 1.0f,
                        animationSpec = if (animationsEnabled) spring() else snap(),
                        label = "scale"
                    )
                    val alpha by animateFloatAsState(
                        targetValue = if (isDraggingThis) 0.8f else 1.0f,
                        animationSpec = if (animationsEnabled) spring() else snap(),
                        label = "alpha"
                    )

                    val density = LocalDensity.current
                    val fallbackItemHeightPx = with(density) { 48.dp.toPx() }
                    val spacingPx = with(density) { 12.dp.toPx() }

                    val itemAnimModifier = if (animationsEnabled && !isDraggingThis) Modifier.animateItem() else Modifier

                    val gestureModifier = if (isReorderMode) {
                        Modifier.pointerInput(itemKey, localFavorites.size) {
                            detectDragGestures(
                                onDragStart = {
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

                                    val stepHeight = (if (itemHeightPx > 0f) itemHeightPx else fallbackItemHeightPx) + spacingPx
                                    val effectiveDy = -dragVerticalOffset
                                    val rowDelta = (effectiveDy / stepHeight).roundToInt()

                                    val curIndex = localFavorites.indexOfFirst { it.key == itemKey }
                                    if (curIndex != -1) {
                                        val targetIndex = (curIndex + rowDelta).coerceIn(0, localFavorites.size - 1)
                                        if (targetIndex != curIndex) {
                                            val actualChange = targetIndex - curIndex
                                            val dyToSub = -actualChange * stepHeight
                                            dragVerticalOffset -= dyToSub
                                            val moved = localFavorites.removeAt(curIndex)
                                            localFavorites.add(targetIndex, moved)
                                            currentOnReorder(curIndex, targetIndex)
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    }
                                }
                            )
                        }
                    } else Modifier

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .then(itemAnimModifier)
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
                            }
                            .then(gestureModifier),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            contentAlignment = Alignment.Center
                        ) {
                            when (item) {
                                is FavoriteItem.App -> {
                                    val currentAppItem by rememberUpdatedState(item)
                                    if (isReorderMode) {
                                        val painter = rememberAppIconPainter(item.appInfo.iconKey, 48)
                                        Image(
                                            painter = painter,
                                            contentDescription = item.appInfo.label,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                        )
                                    } else {
                                        AppIconItem(
                                            app = item.appInfo,
                                            onClick = { onAppClick(itemKey) },
                                            onLongClick = { offset -> onAppLongClick(currentAppItem.appInfo, offset) }
                                        )
                                    }
                                }

                                is FavoriteItem.TagFolder -> {
                                    var folderPosition by remember { mutableStateOf(Offset.Zero) }
                                    val currentTagItem by rememberUpdatedState(item)
                                    val currentOnTagFolderClick by rememberUpdatedState(onTagFolderClick)
                                    val currentOnTagFolderLongClick by rememberUpdatedState(onTagFolderLongClick)

                                    val tagModifier = if (!isReorderMode) {
                                        Modifier
                                            .onGloballyPositioned { folderPosition = it.positionInRoot() }
                                            .pointerInput(itemKey) {
                                                detectTapGestures(
                                                    onTap = {
                                                        currentOnTagFolderClick(
                                                            currentTagItem.tag,
                                                            currentTagItem.apps,
                                                            folderPosition + it
                                                        )
                                                    },
                                                    onLongPress = {
                                                        currentOnTagFolderLongClick(
                                                            currentTagItem.tag,
                                                            currentTagItem.apps,
                                                            folderPosition + it
                                                        )
                                                    }
                                                )
                                            }
                                    } else {
                                        Modifier
                                    }

                                    TagFolderIcon(
                                        tag = item.tag,
                                        apps = item.apps,
                                        modifier = tagModifier
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
                                Box(contentAlignment = Alignment.Center) {
                                    if (showShadows) {
                                        val shadowSettings = LocalShadowSettings.current
                                        Icon(
                                            imageVector = Icons.Default.RemoveCircle,
                                            contentDescription = null,
                                            tint = primaryTextColor.getShadowColor(shadowSettings.shadowColorOverride),
                                            modifier = Modifier
                                                .size(24.dp)
                                                .offset(1.dp, 1.dp)
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.RemoveCircle,
                                        contentDescription = "Remove",
                                        tint = Color.Red.copy(alpha = 0.8f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
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
                                tint = primaryTextColor.getShadowColor(LocalShadowSettings.current.shadowColorOverride)
                                    .copy(alpha = 0.6f),
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
