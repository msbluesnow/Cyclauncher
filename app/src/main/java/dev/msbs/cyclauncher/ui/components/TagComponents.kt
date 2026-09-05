package dev.msbs.cyclauncher.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import dev.msbs.cyclauncher.model.AppInfo
import dev.msbs.cyclauncher.model.Tag
import dev.msbs.cyclauncher.ui.theme.AccentColor
import dev.msbs.cyclauncher.ui.theme.PopupTheme
import dev.msbs.cyclauncher.ui.theme.PrimaryTextColor
import dev.msbs.cyclauncher.ui.theme.LocalAnimationsEnabled
import dev.msbs.cyclauncher.ui.theme.LocalShadowSettings
import kotlin.math.roundToInt

/**
 * Tag folder item displaying a colored border and a 2x2 preview of assigned app icons.
 */
@Composable
fun TagFolderItem(
    tag: Tag,
    apps: List<AppInfo>,
    onClick: (Offset) -> Unit,
    onLongClick: (Offset) -> Unit = {},
    primaryTextColor: PrimaryTextColor = PrimaryTextColor.WHITE,
    showShadows: Boolean = false
) {
    val previewApps = remember(apps) { apps.take(4) }
    var itemPosition by remember { mutableStateOf(Offset.Zero) }
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { itemPosition = it.positionInRoot() }
            .pointerInput(tag.id) {
                detectTapGestures(
                    onTap = { currentOnClick(itemPosition + it) },
                    onLongPress = { currentOnLongClick(itemPosition + it) }
                )
            }
            .padding(vertical = 4.dp, horizontal = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black.copy(alpha = 0.45f))
                .background(tag.color.copy(alpha = 0.12f))
                .border(BorderStroke(1.5.dp, tag.color), shape = RoundedCornerShape(14.dp))
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            val hasCustomIcon = !tag.emoji.isNullOrBlank()
            val vectorIcon = TagIconRegistry.getVectorIcon(tag.emoji)

            // Layer 1: App icons in background (dimmed if custom icon is active, or full opacity if default)
            if (previewApps.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            if (hasCustomIcon) {
                                alpha = 0.22f
                            }
                        }
                ) {
                    TagFolderAppIconsGrid(previewApps = previewApps, iconSizeDp = 16.dp)
                }
            }

            // Layer 2: Custom Vector Icon or Emoji on foreground
            if (vectorIcon != null) {
                Icon(
                    imageVector = vectorIcon,
                    contentDescription = null,
                    tint = tag.color,
                    modifier = Modifier.size(28.dp)
                )
            } else if (!tag.emoji.isNullOrBlank()) {
                Text(
                    text = tag.emoji,
                    fontSize = 26.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = tag.name,
            fontSize = 11.sp,
            color = primaryTextColor.color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall.copy(
                shadow = primaryTextColor.getShadow(showShadows, LocalShadowSettings.current.shadowColorOverride)
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun TagFolderAppIconsGrid(
    previewApps: List<AppInfo>,
    iconSizeDp: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxSize()
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (previewApps.isNotEmpty()) {
                MiniAppIconPreview(app = previewApps[0], sizeDp = iconSizeDp)
            }
            if (previewApps.size > 1) {
                MiniAppIconPreview(app = previewApps[1], sizeDp = iconSizeDp)
            } else if (previewApps.size == 1) {
                Spacer(modifier = Modifier.size(iconSizeDp))
            }
        }
        if (previewApps.size > 2) {
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                MiniAppIconPreview(app = previewApps[2], sizeDp = iconSizeDp)
                if (previewApps.size > 3) {
                    MiniAppIconPreview(app = previewApps[3], sizeDp = iconSizeDp)
                } else {
                    Spacer(modifier = Modifier.size(iconSizeDp))
                }
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun MiniAppIconPreview(app: AppInfo, sizeDp: androidx.compose.ui.unit.Dp = 16.dp) {
    val painter = rememberAppIconPainter(app.iconKey, sizeDp.value.toInt())
    Image(
        painter = painter,
        contentDescription = null,
        modifier = Modifier
            .size(sizeDp)
            .clip(CircleShape),
        contentScale = ContentScale.Fit
    )
}

/**
 * Popup dialog showing all applications within a tag folder with edit, reordering, and management actions.
 */
@Composable
fun TagFolderPopup(
    tag: Tag,
    apps: List<AppInfo>,
    offset: Offset,
    isEditMode: Boolean = false,
    onAppClick: (String) -> Unit = {},
    onAppLongClick: (AppInfo, Offset) -> Unit = { _, _ -> },
    onRemoveAppFromTag: (String, String) -> Unit = { _, _ -> },
    onReorderApp: ((Int, Int) -> Unit)? = null,
    onEditTag: (Tag) -> Unit = {},
    onExitEditMode: () -> Unit = {},
    onDismiss: () -> Unit,
    primaryTextColor: PrimaryTextColor = PrimaryTextColor.WHITE,
    showShadows: Boolean = false,
    accentColor: AccentColor = AccentColor.SKY,
    popupTheme: PopupTheme = PopupTheme.DARK
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val animationsEnabled = LocalAnimationsEnabled.current

    val popupWidth = 260.dp
    val popupWidthPx = with(density) { popupWidth.toPx() }

    val rows = if (apps.isEmpty()) 1 else ((apps.size + 2) / 3).coerceIn(1, 4)
    val estimatedHeight = if (apps.isEmpty()) 110.dp else (76 + rows * 80).dp
    val popupHeightPx = with(density) { estimatedHeight.toPx() }
    val borderPadding = with(density) { 16.dp.toPx() }

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    var x = offset.x
    var y = offset.y

    if (x + popupWidthPx > screenWidthPx - borderPadding) x = screenWidthPx - popupWidthPx - borderPadding
    if (x < borderPadding) x = borderPadding
    if (y + popupHeightPx > screenHeightPx - borderPadding) y = screenHeightPx - popupHeightPx - borderPadding
    if (y < borderPadding) y = borderPadding

    // Reorder drag tracking states
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var cellWidthPx by remember { mutableFloatStateOf(0f) }
    var cellHeightPx by remember { mutableFloatStateOf(0f) }

    val currentOnReorderApp by rememberUpdatedState(onReorderApp)
    val currentApps by rememberUpdatedState(apps)

    LaunchedEffect(isEditMode) {
        if (!isEditMode) {
            draggingKey = null
            dragOffset = Offset.Zero
        }
    }

    Popup(
        offset = IntOffset(x.roundToInt(), y.roundToInt()),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier
                .width(popupWidth)
                .heightIn(max = 360.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(popupTheme.backgroundColor)
                .border(1.dp, popupTheme.borderColor, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val vectorIcon = TagIconRegistry.getVectorIcon(tag.emoji)
                        if (vectorIcon != null) {
                            Icon(
                                imageVector = vectorIcon,
                                contentDescription = null,
                                tint = tag.color,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        } else if (!tag.emoji.isNullOrBlank()) {
                            Text(
                                text = tag.emoji,
                                fontSize = 18.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(tag.color)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        BoxWithConstraints(modifier = Modifier.weight(1f)) {
                            var fontSize by remember(tag.name, maxWidth) { mutableStateOf(17.sp) }
                            var readyToDraw by remember(tag.name, maxWidth) { mutableStateOf(tag.name.length <= 10) }

                            Text(
                                text = tag.name,
                                color = if (readyToDraw) popupTheme.contentColor else Color.Transparent,
                                fontWeight = FontWeight.Bold,
                                fontSize = fontSize,
                                maxLines = 2,
                                lineHeight = (fontSize.value * 1.15f).sp,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = true,
                                onTextLayout = { textLayoutResult ->
                                    if (textLayoutResult.hasVisualOverflow && fontSize.value > 11f) {
                                        fontSize = (fontSize.value * 0.88f).sp
                                    } else {
                                        readyToDraw = true
                                    }
                                }
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isEditMode) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(accentColor.color.copy(alpha = 0.2f))
                                    .border(1.dp, popupTheme.borderColor, RoundedCornerShape(8.dp))
                                    .clickable { onExitEditMode() }
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Done",
                                    color = accentColor.color,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { onEditTag(tag) }
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (showShadows) {
                                    val shadowSettings = LocalShadowSettings.current
                                    Icon(
                                        imageVector = Icons.Outlined.Edit,
                                        contentDescription = null,
                                        tint = primaryTextColor.getShadowColor(shadowSettings.shadowColorOverride).copy(alpha = 0.25f),
                                        modifier = Modifier
                                            .size(17.dp)
                                            .offset(1.dp, 1.dp)
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = "Edit Tag",
                                    tint = popupTheme.secondaryContentColor,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(tag.color.copy(alpha = 0.15f))
                                .border(0.8.dp, popupTheme.borderColor, RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${apps.size}",
                                color = tag.color,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (apps.isEmpty()) {
                    Text(
                        text = "No apps in this tag",
                        color = popupTheme.secondaryContentColor,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    val colSpacingPx = with(density) { 8.dp.toPx() }
                    val rowSpacingPx = with(density) { 12.dp.toPx() }

                    val infiniteTransition = rememberInfiniteTransition(label = "tag_popup_shake")
                    val shakeRotation by infiniteTransition.animateFloat(
                        initialValue = -3.2f,
                        targetValue = 3.2f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 110, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "popup_shake_rot"
                    )
                    val shakeTranslation by infiniteTransition.animateFloat(
                        initialValue = -1.2f,
                        targetValue = 1.2f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 130, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "popup_shake_trans"
                    )

                    val localApps = remember(apps) { mutableStateListOf(*apps.toTypedArray()) }
                    LaunchedEffect(apps, draggingKey) {
                        if (draggingKey == null) {
                            localApps.clear()
                            localApps.addAll(apps)
                        }
                    }

                    val gridState = rememberLazyGridState()

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = gridState,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(localApps, key = { _, item -> "${item.packageName}/${item.activityName}" }) { index, app ->
                            val appKey = "${app.packageName}/${app.activityName}"
                            val isDraggingThis = draggingKey == appKey

                            val scale by animateFloatAsState(
                                targetValue = if (isDraggingThis) 1.15f else 1.0f,
                                animationSpec = if (animationsEnabled) spring() else snap(),
                                label = "scale"
                            )
                            val alpha by animateFloatAsState(
                                targetValue = if (isDraggingThis) 0.88f else 1.0f,
                                animationSpec = if (animationsEnabled) spring() else snap(),
                                label = "alpha"
                            )

                            val itemAnimModifier = if (animationsEnabled && !isDraggingThis) Modifier.animateItem() else Modifier

                            val itemRotation = if (isEditMode && !isDraggingThis) {
                                if (index % 2 == 0) shakeRotation else -shakeRotation
                            } else 0f

                            val itemTranslation = if (isEditMode && !isDraggingThis) {
                                if ((index / 2) % 2 == 0) shakeTranslation else -shakeTranslation
                            } else 0f

                            val gestureModifier = if (isEditMode) {
                                Modifier.pointerInput(appKey, localApps.size) {
                                    detectDragGestures(
                                        onDragStart = {
                                            draggingKey = appKey
                                            dragOffset = Offset.Zero
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffset += dragAmount

                                            if (currentOnReorderApp != null) {
                                                val visibleItems = gridState.layoutInfo.visibleItemsInfo
                                                val curIndex = localApps.indexOfFirst { "${it.packageName}/${it.activityName}" == appKey }
                                                val draggedItem = visibleItems.firstOrNull { it.index == curIndex }

                                                if (draggedItem != null && curIndex != -1) {
                                                    val draggedCenterX = draggedItem.offset.x + draggedItem.size.width / 2f + dragOffset.x
                                                    val draggedCenterY = draggedItem.offset.y + draggedItem.size.height / 2f + dragOffset.y

                                                    val targetItem = visibleItems
                                                        .filter { it.index != curIndex && it.index in localApps.indices }
                                                        .minByOrNull { item ->
                                                            val itemCenterX = item.offset.x + item.size.width / 2f
                                                            val itemCenterY = item.offset.y + item.size.height / 2f
                                                            val dx = draggedCenterX - itemCenterX
                                                            val dy = draggedCenterY - itemCenterY
                                                            dx * dx + dy * dy
                                                        }

                                                    if (targetItem != null) {
                                                        val targetCenterX = targetItem.offset.x + targetItem.size.width / 2f
                                                        val targetCenterY = targetItem.offset.y + targetItem.size.height / 2f
                                                        val distSq = (draggedCenterX - targetCenterX) * (draggedCenterX - targetCenterX) +
                                                                     (draggedCenterY - targetCenterY) * (draggedCenterY - targetCenterY)

                                                        val threshold = (targetItem.size.width.coerceAtLeast(targetItem.size.height) * 0.65f)
                                                        if (distSq < threshold * threshold) {
                                                            val deltaX = (targetItem.offset.x - draggedItem.offset.x).toFloat()
                                                            val deltaY = (targetItem.offset.y - draggedItem.offset.y).toFloat()
                                                            dragOffset = Offset(dragOffset.x - deltaX, dragOffset.y - deltaY)
                                                            val item = localApps.removeAt(curIndex)
                                                            localApps.add(targetItem.index, item)
                                                            currentOnReorderApp?.invoke(curIndex, targetItem.index)
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        onDragEnd = {
                                            draggingKey = null
                                            dragOffset = Offset.Zero
                                        },
                                        onDragCancel = {
                                            draggingKey = null
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
                                        if (cellWidthPx == 0f && coordinates.size.width > 0) {
                                            cellWidthPx = coordinates.size.width.toFloat()
                                            cellHeightPx = coordinates.size.height.toFloat()
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
                                        } else if (isEditMode) {
                                            rotationZ = itemRotation
                                            translationX = itemTranslation
                                            translationY = if (index % 2 == 0) itemTranslation * 0.4f else -itemTranslation * 0.4f
                                        }
                                    }
                                    .then(gestureModifier)
                            ) {
                                TagFolderAppItem(
                                    app = app,
                                    isEditMode = isEditMode,
                                    isDragging = isDraggingThis,
                                    onClick = {
                                        if (!isEditMode) {
                                            onAppClick("${app.packageName}/${app.activityName}")
                                            onDismiss()
                                        }
                                    },
                                    onLongClick = { appOffset ->
                                        if (!isEditMode) {
                                            onDismiss()
                                            onAppLongClick(app, Offset(x, y) + appOffset)
                                        }
                                    },
                                    onRemoveAppFromTag = onRemoveAppFromTag,
                                    tagId = tag.id,
                                    primaryTextColor = primaryTextColor,
                                    showShadows = showShadows,
                                    popupTheme = popupTheme
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TagFolderAppItem(
    app: AppInfo,
    isEditMode: Boolean,
    isDragging: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (Offset) -> Unit,
    onRemoveAppFromTag: (String, String) -> Unit,
    tagId: String,
    primaryTextColor: PrimaryTextColor,
    showShadows: Boolean,
    popupTheme: PopupTheme = PopupTheme.DARK
) {
    var itemPosition by remember { mutableStateOf(Offset.Zero) }
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val currentOnRemoveApp by rememberUpdatedState(onRemoveAppFromTag)
    val appKey = "${app.packageName}/${app.activityName}"

    val itemTapModifier = if (!isEditMode) {
        Modifier.pointerInput(appKey) {
            detectTapGestures(
                onTap = { currentOnClick() },
                onLongPress = { currentOnLongClick(itemPosition + it) }
            )
        }
    } else {
        Modifier
    }

    val iconPainter = rememberAppIconPainter(app.iconKey, 48)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { itemPosition = it.positionInRoot() }
            .then(itemTapModifier)
            .padding(4.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Image(
                painter = iconPainter,
                contentDescription = app.label,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
            )

            if (isEditMode) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444))
                        .border(1.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                        .clickable { currentOnRemoveApp(tagId, appKey) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Remove from tag",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = app.label,
            color = popupTheme.contentColor,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Preview icon representing a tag folder within the Favorites section.
 */
@Composable
fun TagFolderIcon(
    tag: Tag,
    apps: List<AppInfo>,
    size: Int = 48,
    modifier: Modifier = Modifier
) {
    val previewApps = remember(apps) { apps.take(4) }
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .background(tag.color.copy(alpha = 0.12f))
            .border(BorderStroke(1.5.dp, tag.color), shape = RoundedCornerShape(13.dp))
            .padding(3.dp),
        contentAlignment = Alignment.Center
    ) {
        val hasCustomIcon = !tag.emoji.isNullOrBlank()
        val vectorIcon = TagIconRegistry.getVectorIcon(tag.emoji)

        // Layer 1: App icons in background (dimmed if custom icon is active, or full opacity if default)
        if (previewApps.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (hasCustomIcon) {
                            alpha = 0.22f
                        }
                    }
            ) {
                TagFolderAppIconsGrid(previewApps = previewApps, iconSizeDp = (size * 0.33f).dp)
            }
        }

        // Layer 2: Custom Vector Icon or Emoji on foreground
        if (vectorIcon != null) {
            Icon(
                imageVector = vectorIcon,
                contentDescription = null,
                tint = tag.color,
                modifier = Modifier.size((size * 0.58f).dp)
            )
        } else if (!tag.emoji.isNullOrBlank()) {
            Text(
                text = tag.emoji,
                fontSize = (size * 0.55f).sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Context action menu for tag folders (edit group, toggle favorite).
 */
@Composable
fun TagFolderActionMenu(
    tag: Tag,
    isFavorite: Boolean,
    offset: Offset,
    onDismiss: () -> Unit,
    onEditGroup: () -> Unit,
    onToggleFavorite: () -> Unit,
    accentColor: AccentColor = AccentColor.SKY,
    primaryTextColor: PrimaryTextColor = PrimaryTextColor.WHITE,
    popupTheme: PopupTheme = PopupTheme.DARK
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    val menuWidth = 240.dp
    val menuWidthPx = with(density) { menuWidth.toPx() }

    val itemsCount = 2
    val menuHeightPx = with(density) { (60 + itemsCount * 48).dp.toPx() }
    val borderPadding = with(density) { 16.dp.toPx() }

    var x = offset.x
    var y = offset.y

    if (x + menuWidthPx > screenWidthPx) x = screenWidthPx - menuWidthPx - borderPadding
    if (x < borderPadding) x = borderPadding
    if (y + menuHeightPx > screenHeightPx) y = screenHeightPx - menuHeightPx - borderPadding
    if (y < borderPadding) y = borderPadding

    Popup(
        offset = IntOffset(x.roundToInt(), y.roundToInt()),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier
                .width(menuWidth)
                .clip(RoundedCornerShape(16.dp))
                .background(popupTheme.backgroundColor)
                .border(1.dp, popupTheme.borderColor, RoundedCornerShape(16.dp))
                .padding(vertical = 8.dp)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(tag.color)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = tag.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = popupTheme.contentColor,
                        fontWeight = FontWeight.Bold
                    )
                }

                HorizontalDivider(
                    color = popupTheme.dividerColor,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismiss()
                            onEditGroup()
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = null,
                        tint = accentColor.color,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Edit Group",
                        color = popupTheme.contentColor,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismiss()
                            onToggleFavorite()
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Outlined.Star else Icons.Outlined.StarOutline,
                        contentDescription = null,
                        tint = accentColor.color,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (isFavorite) "Remove from Favorites" else "Add to Favorites",
                        color = popupTheme.contentColor,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

/**
 * Context action menu for the Tag Folders section icon (Reorder Folders, Sort by...).
 */
@Composable
fun TagSectionActionMenu(
    offset: Offset,
    onDismiss: () -> Unit,
    onReorderFolders: () -> Unit,
    onOpenSortMenu: () -> Unit,
    accentColor: AccentColor = AccentColor.SKY,
    primaryTextColor: PrimaryTextColor = PrimaryTextColor.WHITE,
    popupTheme: PopupTheme = PopupTheme.DARK
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    val menuWidth = 240.dp
    val menuWidthPx = with(density) { menuWidth.toPx() }

    val itemsCount = 2
    val menuHeightPx = with(density) { (60 + itemsCount * 48).dp.toPx() }
    val borderPadding = with(density) { 16.dp.toPx() }

    var x = offset.x
    var y = offset.y

    if (x + menuWidthPx > screenWidthPx) x = screenWidthPx - menuWidthPx - borderPadding
    if (x < borderPadding) x = borderPadding
    if (y + menuHeightPx > screenHeightPx) y = screenHeightPx - menuHeightPx - borderPadding
    if (y < borderPadding) y = borderPadding

    Popup(
        offset = IntOffset(x.roundToInt(), y.roundToInt()),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier
                .width(menuWidth)
                .clip(RoundedCornerShape(16.dp))
                .background(popupTheme.backgroundColor)
                .border(1.dp, popupTheme.borderColor, RoundedCornerShape(16.dp))
                .padding(vertical = 8.dp)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Folder,
                        contentDescription = null,
                        tint = accentColor.color,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Tag Folders",
                        style = MaterialTheme.typography.titleSmall,
                        color = popupTheme.contentColor,
                        fontWeight = FontWeight.Bold
                    )
                }

                HorizontalDivider(
                    color = popupTheme.dividerColor,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismiss()
                            onReorderFolders()
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.OpenWith,
                        contentDescription = null,
                        tint = accentColor.color,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Reorder Folders",
                        color = popupTheme.contentColor,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismiss()
                            onOpenSortMenu()
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Sort,
                        contentDescription = null,
                        tint = accentColor.color,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Sort by...",
                        color = popupTheme.contentColor,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

/**
 * Compact Popup for sorting tag folders (By Name, By App Count with ascending & descending icon buttons).
 */
@Composable
fun TagSortPopup(
    offset: Offset,
    onDismiss: () -> Unit,
    onSortByNameAsc: () -> Unit,
    onSortByNameDesc: () -> Unit,
    onSortByAppCountDesc: () -> Unit,
    onSortByAppCountAsc: () -> Unit,
    accentColor: AccentColor = AccentColor.SKY,
    primaryTextColor: PrimaryTextColor = PrimaryTextColor.WHITE,
    popupTheme: PopupTheme = PopupTheme.DARK
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    val menuWidth = 260.dp
    val menuWidthPx = with(density) { menuWidth.toPx() }

    val menuHeightPx = with(density) { 170.dp.toPx() }
    val borderPadding = with(density) { 16.dp.toPx() }

    var x = offset.x
    var y = offset.y

    if (x + menuWidthPx > screenWidthPx) x = screenWidthPx - menuWidthPx - borderPadding
    if (x < borderPadding) x = borderPadding
    if (y + menuHeightPx > screenHeightPx) y = screenHeightPx - menuHeightPx - borderPadding
    if (y < borderPadding) y = borderPadding

    Popup(
        offset = IntOffset(x.roundToInt(), y.roundToInt()),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier
                .width(menuWidth)
                .clip(RoundedCornerShape(16.dp))
                .background(popupTheme.backgroundColor)
                .border(1.dp, popupTheme.borderColor, RoundedCornerShape(16.dp))
                .padding(vertical = 12.dp, horizontal = 14.dp)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Sort,
                        contentDescription = null,
                        tint = accentColor.color,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Sort Folders",
                        style = MaterialTheme.typography.titleSmall,
                        color = popupTheme.contentColor,
                        fontWeight = FontWeight.Bold
                    )
                }

                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = popupTheme.secondaryContentColor.copy(alpha = 0.2f),
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                // Row 1: By Name
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "By Name",
                        color = popupTheme.contentColor,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(popupTheme.contentColor.copy(alpha = 0.08f))
                                .clickable {
                                    onDismiss()
                                    onSortByNameAsc()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.KeyboardArrowUp,
                                contentDescription = "Name A to Z",
                                tint = accentColor.color,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(popupTheme.contentColor.copy(alpha = 0.08f))
                                .clickable {
                                    onDismiss()
                                    onSortByNameDesc()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.KeyboardArrowDown,
                                contentDescription = "Name Z to A",
                                tint = accentColor.color,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Row 2: By App Count
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "By App Count",
                        color = popupTheme.contentColor,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(popupTheme.contentColor.copy(alpha = 0.08f))
                                .clickable {
                                    onDismiss()
                                    onSortByAppCountDesc()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.KeyboardArrowDown,
                                contentDescription = "App Count High to Low",
                                tint = accentColor.color,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(popupTheme.contentColor.copy(alpha = 0.08f))
                                .clickable {
                                    onDismiss()
                                    onSortByAppCountAsc()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.KeyboardArrowUp,
                                contentDescription = "App Count Low to High",
                                tint = accentColor.color,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}


