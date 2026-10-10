package dev.msbs.cyclauncher.ui.components

import dev.msbs.cyclauncher.HandSide
import dev.msbs.cyclauncher.SideAlphabetSlotMode
import androidx.compose.material.icons.outlined.Widgets
import kotlin.math.roundToInt
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.HistoryToggleOff
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PauseCircleOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import dev.msbs.cyclauncher.R
import dev.msbs.cyclauncher.model.AppColorBucket
import dev.msbs.cyclauncher.model.AppInfo
import dev.msbs.cyclauncher.ui.theme.AccentColor
import dev.msbs.cyclauncher.ui.theme.LocalAnimationsEnabled
import dev.msbs.cyclauncher.ui.theme.PopupTheme
import dev.msbs.cyclauncher.ui.theme.PrimaryTextColor
import dev.msbs.cyclauncher.ui.theme.ShadowSettings

private val MULTICOLOR_GRADIENT = listOf(
    Color(0xFFEA4335), // Red
    Color(0xFFFBBC05), // Amber / Yellow
    Color(0xFF34A853), // Green
    Color(0xFF4285F4), // Blue
    Color(0xFF9C27B0)  // Purple
)

private val MONOCHROME_GRADIENT = listOf(
    Color(0xFFE2E8F0),
    Color(0xFF334155)
)

private val BASIC_COLORS = listOf(
    AppColorBucket.RED,
    AppColorBucket.ORANGE,
    AppColorBucket.YELLOW,
    AppColorBucket.GREEN,
    AppColorBucket.BLUE,
    AppColorBucket.PURPLE
)

/**
 * Maps a pointer offset within the header bounds to the corresponding [AppColorBucket].
 * Top row: Left = MULTICOLOR, Center = null (History icon), Right = MONOCHROME.
 * Bottom row: 6 basic colors distributed evenly from left to right.
 */
private fun getBucketAtOffset(offset: Offset, size: androidx.compose.ui.geometry.Size): AppColorBucket? {
    if (size.width <= 0f || size.height <= 0f) return null
    val x = offset.x
    val y = offset.y
    val rowDividerY = size.height * 0.48f

    return if (y < rowDividerY) {
        val leftBoundary = size.width * 0.35f
        val rightBoundary = size.width * 0.65f
        when {
            x < leftBoundary -> AppColorBucket.MULTICOLOR
            x > rightBoundary -> AppColorBucket.MONOCHROME
            else -> null // Center History icon dead-zone during drag
        }
    } else {
        val colWidth = size.width / 6f
        val col = (x / colWidth).toInt().coerceIn(0, 5)
        BASIC_COLORS[col]
    }
}

/**
 * Aerodesign color & history search header situated directly above the alphabet grid.
 *
 * Layout:
 * - Upper row: Rainbow (left) --- History Clock Icon (center) --- Monochrome (right)
 * - Lower row: 6 basic colors in a single line (Red, Orange, Yellow, Green, Blue, Purple)
 * - All 8 color squares have the exact same size.
 * - Supports seamless single-gesture swipe/scrubbing across any color tiles without lifting finger,
 *   as well as direct tap toggling and History long-press actions.
 */
@Composable
fun SideAlphabetColorHeader(
    selectedColor: AppColorBucket?,
    onColorSelected: (AppColorBucket?) -> Unit,
    showHistoryIcon: Boolean,
    isHistoryEditMode: Boolean,
    isHistoryPaused: Boolean,
    accentColor: AccentColor,
    primaryTextColor: PrimaryTextColor,
    showShadows: Boolean,
    shadowSettings: ShadowSettings,
    onHistoryIconLongPress: (Offset) -> Unit,
    onExitEditMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val currentSelectedColor by rememberUpdatedState(selectedColor)
    val currentOnColorSelected by rememberUpdatedState(onColorSelected)
    val currentOnHistoryLongPress by rememberUpdatedState(onHistoryIconLongPress)
    val currentOnExitEditMode by rememberUpdatedState(onExitEditMode)

    var headerSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    var historyIconOffsetInRoot by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { headerSize = it.size.toSize() }
            .pointerInput(showHistoryIcon, isHistoryEditMode) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downPos = down.position
                    val isHistoryArea = showHistoryIcon &&
                        downPos.y < headerSize.height * 0.48f &&
                        downPos.x in (headerSize.width * 0.35f .. headerSize.width * 0.65f)

                    if (isHistoryArea) {
                        var isDrag = false
                        var isLongPressed = false
                        val longPressTimeout = viewConfiguration.longPressTimeoutMillis
                        val startTime = System.currentTimeMillis()

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                if (!isDrag && !isLongPressed) {
                                    if (isHistoryEditMode) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        currentOnExitEditMode()
                                    }
                                }
                                break
                            }

                            val distance = (change.position - downPos).getDistance()
                            if (distance > viewConfiguration.touchSlop) {
                                isDrag = true
                                change.consume()
                                // Transitioned from History icon into color scrubbing
                                val bucket = getBucketAtOffset(change.position, headerSize)
                                if (bucket != null && bucket != currentSelectedColor) {
                                    currentOnColorSelected(bucket)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                // Continue drag tracking
                                while (true) {
                                    val dragEvent = awaitPointerEvent()
                                    val dragChange = dragEvent.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!dragChange.pressed) break
                                    dragChange.consume()
                                    val b = getBucketAtOffset(dragChange.position, headerSize)
                                    if (b != null && b != currentSelectedColor) {
                                        currentOnColorSelected(b)
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                                break
                            }

                            if (!isLongPressed && !isHistoryEditMode && (System.currentTimeMillis() - startTime >= longPressTimeout)) {
                                isLongPressed = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentOnHistoryLongPress(historyIconOffsetInRoot + downPos)
                                change.consume()
                            }
                        }
                    } else {
                        // Touch started on color tiles area
                        val initialBucket = getBucketAtOffset(downPos, headerSize)
                        var isDrag = false

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                if (!isDrag && initialBucket != null) {
                                    if (initialBucket == currentSelectedColor) {
                                        currentOnColorSelected(null)
                                    } else {
                                        currentOnColorSelected(initialBucket)
                                    }
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                break
                            }

                            val distance = (change.position - downPos).getDistance()
                            if (!isDrag && distance > viewConfiguration.touchSlop) {
                                isDrag = true
                                val b = getBucketAtOffset(change.position, headerSize)
                                if (b != null && b != currentSelectedColor) {
                                    currentOnColorSelected(b)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }

                            if (isDrag) {
                                change.consume()
                                val bucket = getBucketAtOffset(change.position, headerSize)
                                if (bucket != null && bucket != currentSelectedColor) {
                                    currentOnColorSelected(bucket)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        }
                    }
                }
            }
    ) {
        val totalW = maxWidth
        // Calculate unified tile size ensuring all 8 tiles have exact same dimensions
        val gap = 4.dp
        val computedTileSize = ((totalW - (gap * 5)) / 6).coerceIn(18.dp, 23.dp)
        val tileSize = computedTileSize

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Upper Row: Rainbow (left) --- History Icon (center) --- Monochrome (right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ColorSquareTile(
                    bucket = AppColorBucket.MULTICOLOR,
                    isSelected = selectedColor == AppColorBucket.MULTICOLOR,
                    size = tileSize
                )

                if (showHistoryIcon) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .onGloballyPositioned { historyIconOffsetInRoot = it.positionInRoot() },
                        contentAlignment = Alignment.Center
                    ) {
                        val iconSize = 22.dp
                        val historyIcon = when {
                            isHistoryEditMode -> Icons.Outlined.Check
                            isHistoryPaused -> Icons.Outlined.HistoryToggleOff
                            else -> Icons.Outlined.History
                        }
                        val iconTint = when {
                            isHistoryEditMode -> accentColor.color
                            isHistoryPaused -> accentColor.color.copy(alpha = 0.5f)
                            else -> accentColor.color
                        }
                        val contentDesc = when {
                            isHistoryEditMode -> stringResource(R.string.palette_done_editing)
                            isHistoryPaused -> stringResource(R.string.history_menu_paused)
                            else -> stringResource(R.string.history_menu_title)
                        }

                        if (showShadows) {
                            Icon(
                                imageVector = historyIcon,
                                contentDescription = null,
                                tint = primaryTextColor.getShadowColor(shadowSettings.shadowColorOverride),
                                modifier = Modifier
                                    .size(iconSize)
                                    .offset(1.dp, 1.dp)
                            )
                        }
                        Icon(
                            imageVector = historyIcon,
                            contentDescription = contentDesc,
                            tint = iconTint,
                            modifier = Modifier.size(iconSize)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(tileSize))
                }

                ColorSquareTile(
                    bucket = AppColorBucket.MONOCHROME,
                    isSelected = selectedColor == AppColorBucket.MONOCHROME,
                    size = tileSize
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Lower Row: 6 basic colors in a single line
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BASIC_COLORS.forEach { bucket ->
                    ColorSquareTile(
                        bucket = bucket,
                        isSelected = selectedColor == bucket,
                        size = tileSize
                    )
                }
            }
        }
    }
}

/**
 * Individual rounded square color tile component.
 * Features smooth spring scaling when selected and high-contrast outline.
 */
@Composable
fun ColorSquareTile(
    bucket: AppColorBucket,
    isSelected: Boolean,
    size: Dp = 22.dp,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.15f else 1.0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "color_tile_scale"
    )

    val shape = remember { RoundedCornerShape(6.dp) }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .scale(scale)
                .clip(shape)
                .then(
                    when (bucket) {
                        AppColorBucket.MULTICOLOR -> Modifier.background(
                            Brush.linearGradient(MULTICOLOR_GRADIENT)
                        )
                        AppColorBucket.MONOCHROME -> Modifier.background(
                            Brush.linearGradient(MONOCHROME_GRADIENT)
                        )
                        else -> Modifier.background(bucket.displayColor)
                    }
                )
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) Color.White else Color.Black.copy(alpha = 0.22f),
                    shape = shape
                )
        )
    }
}

/**
 * State representing an active quick palette overlay session.
 */
data class QuickPaletteOverlayState(
    val anchorPosition: Offset,
    val handSide: HandSide
)

/**
 * Determines whether white or black should be used for checkmark and border outlines
 * depending on the lightness / luminance of the color bucket.
 */
fun getBucketContrastColor(bucket: AppColorBucket): Color {
    return when (bucket) {
        AppColorBucket.MULTICOLOR -> Color.White
        AppColorBucket.MONOCHROME -> Color.White
        AppColorBucket.YELLOW -> Color.Black
        else -> {
            val c = bucket.displayColor
            val luminance = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue
            if (luminance > 0.60f) Color.Black else Color.White
        }
    }
}

/**
 * Quick Actions overlay for color palette selection and history/widget actions.
 * Supports continuous drag-and-release navigation as well as direct taps.
 */
@Composable
fun QuickPaletteOverlay(
    state: QuickPaletteOverlayState,
    selectedColor: AppColorBucket?,
    isHistoryPaused: Boolean,
    slotMode: SideAlphabetSlotMode = SideAlphabetSlotMode.HISTORY,
    isHistoryEditMode: Boolean = false,
    hasWidget: Boolean = false,
    accentColor: AccentColor,
    primaryTextColor: PrimaryTextColor,
    popupTheme: PopupTheme,
    onSelectColor: (AppColorBucket?) -> Unit,
    onOpenHistoryMenu: (Offset) -> Unit,
    onOpenWidgetAction: () -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val density = androidx.compose.ui.platform.LocalDensity.current

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    val cardWidth = 296.dp
    val cardHeight = if (slotMode == SideAlphabetSlotMode.DISABLED) 144.dp else 224.dp
    val screenMargin = 16.dp

    val cardWidthPx = with(density) { cardWidth.toPx() }
    val cardHeightPx = with(density) { cardHeight.toPx() }
    val screenMarginPx = with(density) { screenMargin.toPx() }

    val topInsetPx = with(density) { 60.dp.toPx() }
    val bottomInsetPx = with(density) { 80.dp.toPx() }

    val cardTopY = remember(state.anchorPosition.y, screenHeightPx, cardHeightPx, topInsetPx, bottomInsetPx) {
        val desired = state.anchorPosition.y - with(density) { 60.dp.toPx() }
        desired.coerceIn(topInsetPx, screenHeightPx - bottomInsetPx - cardHeightPx)
    }

    val cardLeftX = remember(state.handSide, screenWidthPx, cardWidthPx, screenMarginPx) {
        if (state.handSide == HandSide.LEFT) {
            screenMarginPx
        } else {
            screenWidthPx - screenMarginPx - cardWidthPx
        }
    }

    val cardOffset = remember(cardLeftX, cardTopY) {
        androidx.compose.ui.unit.IntOffset(cardLeftX.roundToInt(), cardTopY.roundToInt())
    }

    val textColor = if (popupTheme == PopupTheme.LIGHT) Color.Black else Color.White

    val backgroundBrush = remember(accentColor.color, popupTheme.backgroundColor, popupTheme.solidBackgroundColor) {
        Brush.verticalGradient(
            colors = listOf(
                popupTheme.backgroundColor.copy(alpha = 0.94f),
                popupTheme.solidBackgroundColor.copy(alpha = 0.98f)
            )
        )
    }

    val cardShape = remember { RoundedCornerShape(20.dp) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .pointerInput(onDismiss) {
                detectTapGestures(onTap = { onDismiss() })
            }
    ) {
        Box(
            modifier = Modifier
                .offset { cardOffset }
                .width(cardWidth)
                .height(cardHeight)
                .clip(cardShape)
                .background(backgroundBrush)
                .border(BorderStroke(1.dp, popupTheme.borderColor), cardShape)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { /* consume inside click */ })
                }
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header: Color filter title (without reset text button)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(accentColor.color.copy(alpha = 0.20f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Palette,
                            contentDescription = null,
                            tint = accentColor.color,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = stringResource(R.string.side_overlay_colors_title),
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                // 2x4 Color Grid
                val row1 = remember {
                    listOf(
                        AppColorBucket.MULTICOLOR,
                        AppColorBucket.RED,
                        AppColorBucket.ORANGE,
                        AppColorBucket.YELLOW
                    )
                }
                val row2 = remember {
                    listOf(
                        AppColorBucket.GREEN,
                        AppColorBucket.BLUE,
                        AppColorBucket.PURPLE,
                        AppColorBucket.MONOCHROME
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    OverlayColorRow(
                        buckets = row1,
                        selectedColor = selectedColor,
                        onSelect = { bucket -> onSelectColor(bucket) }
                    )
                    OverlayColorRow(
                        buckets = row2,
                        selectedColor = selectedColor,
                        onSelect = { bucket -> onSelectColor(bucket) }
                    )
                }

                if (slotMode != SideAlphabetSlotMode.DISABLED) {
                    HorizontalDivider(
                        color = textColor.copy(alpha = 0.10f),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    if (slotMode == SideAlphabetSlotMode.HISTORY) {
                        // History Action Card
                        val histBgBrush = remember(accentColor.color, popupTheme) {
                            Brush.verticalGradient(
                                listOf(
                                    if (popupTheme == PopupTheme.LIGHT) Color.Black.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.06f),
                                    if (popupTheme == PopupTheme.LIGHT) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.09f)
                                )
                            )
                        }

                        val histBorder = remember(popupTheme.borderColor) {
                            BorderStroke(1.dp, popupTheme.borderColor.copy(alpha = 0.6f))
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(58.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(histBgBrush)
                                .border(histBorder, RoundedCornerShape(14.dp))
                                .clickable {
                                    onOpenHistoryMenu(Offset(cardLeftX + cardWidthPx / 2f, cardTopY + cardHeightPx))
                                }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(accentColor.color.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isHistoryEditMode) Icons.Outlined.Check
                                        else if (isHistoryPaused) Icons.Outlined.HistoryToggleOff
                                        else Icons.Outlined.History,
                                    contentDescription = stringResource(R.string.history_menu_title),
                                    tint = accentColor.color,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = if (isHistoryEditMode) stringResource(R.string.history_menu_done_edit)
                                           else stringResource(R.string.history_menu_title),
                                    color = textColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.5.sp
                                )
                                Text(
                                    text = if (isHistoryEditMode) stringResource(R.string.history_menu_done_edit)
                                           else if (isHistoryPaused) stringResource(R.string.history_menu_paused)
                                           else stringResource(R.string.side_overlay_history_desc),
                                    color = textColor.copy(alpha = 0.70f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    } else if (slotMode == SideAlphabetSlotMode.WIDGET) {
                        // Widget Action Card
                        val widgetBgBrush = remember(accentColor.color, popupTheme) {
                            Brush.verticalGradient(
                                listOf(
                                    if (popupTheme == PopupTheme.LIGHT) Color.Black.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.06f),
                                    if (popupTheme == PopupTheme.LIGHT) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.09f)
                                )
                            )
                        }

                        val widgetBorder = remember(popupTheme.borderColor) {
                            BorderStroke(1.dp, popupTheme.borderColor.copy(alpha = 0.6f))
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(58.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(widgetBgBrush)
                                .border(widgetBorder, RoundedCornerShape(14.dp))
                                .clickable {
                                    onOpenWidgetAction()
                                }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(accentColor.color.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Widgets,
                                    contentDescription = stringResource(R.string.widget_title),
                                    tint = accentColor.color,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.widget_title),
                                    color = textColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.5.sp
                                )
                                Text(
                                    text = if (hasWidget) stringResource(R.string.widget_action_options)
                                           else stringResource(R.string.widget_action_add),
                                    color = textColor.copy(alpha = 0.70f),
                                    fontSize = 11.sp
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
private fun OverlayColorRow(
    buckets: List<AppColorBucket>,
    selectedColor: AppColorBucket?,
    onSelect: (AppColorBucket) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        buckets.forEach { bucket ->
            val isSelected = selectedColor == bucket
            val shape = remember { RoundedCornerShape(9.dp) }
            val contrastColor = remember(bucket) { getBucketContrastColor(bucket) }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(shape)
                    .then(
                        when (bucket) {
                            AppColorBucket.MULTICOLOR -> Modifier.background(Brush.linearGradient(MULTICOLOR_GRADIENT))
                            AppColorBucket.MONOCHROME -> Modifier.background(Brush.linearGradient(MONOCHROME_GRADIENT))
                            else -> Modifier.background(bucket.displayColor)
                        }
                    )
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) contrastColor.copy(alpha = 0.85f)
                                else contrastColor.copy(alpha = 0.22f),
                        shape = shape
                    )
                    .clickable { onSelect(bucket) },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = "Selected",
                        tint = contrastColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Compact bar situated directly above the Side Alphabet Grid.
 * Displays the current active color status and slot status (History/Widget/None).
 * Tap opens the [QuickPaletteOverlay].
 */
@Composable
fun SideAlphabetColorTriggerBar(
    selectedColor: AppColorBucket?,
    slotMode: SideAlphabetSlotMode,
    isHistoryPaused: Boolean,
    isHistoryEditMode: Boolean,
    accentColor: AccentColor,
    primaryTextColor: PrimaryTextColor,
    showShadows: Boolean,
    shadowSettings: ShadowSettings,
    onClick: (Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    var triggerPositionInRoot by remember { mutableStateOf(Offset.Zero) }
    var triggerSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }

    val shape = remember { RoundedCornerShape(10.dp) }
    val isColorSelected = selectedColor != null

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .onGloballyPositioned { coordinates ->
                triggerPositionInRoot = coordinates.positionInRoot()
                triggerSize = coordinates.size.toSize()
            }
            .clip(shape)
            .background(
                if (isColorSelected) accentColor.color.copy(alpha = 0.16f)
                else primaryTextColor.color.copy(alpha = 0.08f)
            )
            .border(
                width = 1.dp,
                color = if (isColorSelected) accentColor.color.copy(alpha = 0.55f)
                else primaryTextColor.color.copy(alpha = 0.16f),
                shape = shape
            )
            .clickable {
                val center = triggerPositionInRoot + Offset(
                    triggerSize.width / 2f,
                    triggerSize.height / 2f
                )
                onClick(center)
            }
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        if (slotMode == SideAlphabetSlotMode.DISABLED) {
            // When slot is disabled, show single centered color status indicator
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(26.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedColor != null) {
                        val tileShape = remember { RoundedCornerShape(6.dp) }
                        val contrastColor = remember(selectedColor) { getBucketContrastColor(selectedColor) }
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(tileShape)
                                .then(
                                    when (selectedColor) {
                                        AppColorBucket.MULTICOLOR -> Modifier.background(Brush.linearGradient(MULTICOLOR_GRADIENT))
                                        AppColorBucket.MONOCHROME -> Modifier.background(Brush.linearGradient(MONOCHROME_GRADIENT))
                                        else -> Modifier.background(selectedColor.displayColor)
                                    }
                                )
                                .border(1.5.dp, contrastColor.copy(alpha = 0.9f), tileShape)
                        )
                    } else {
                        val paletteIcon = Icons.Outlined.Palette
                        if (showShadows) {
                            Icon(
                                imageVector = paletteIcon,
                                contentDescription = null,
                                tint = primaryTextColor.getShadowColor(shadowSettings.shadowColorOverride),
                                modifier = Modifier
                                    .size(22.dp)
                                    .offset(1.dp, 1.dp)
                            )
                        }
                        Icon(
                            imageVector = paletteIcon,
                            contentDescription = stringResource(R.string.side_overlay_colors_title),
                            tint = accentColor.color,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Color Status Indicator
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedColor != null) {
                        val tileShape = remember { RoundedCornerShape(6.dp) }
                        val contrastColor = remember(selectedColor) { getBucketContrastColor(selectedColor) }
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(tileShape)
                                .then(
                                    when (selectedColor) {
                                        AppColorBucket.MULTICOLOR -> Modifier.background(Brush.linearGradient(MULTICOLOR_GRADIENT))
                                        AppColorBucket.MONOCHROME -> Modifier.background(Brush.linearGradient(MONOCHROME_GRADIENT))
                                        else -> Modifier.background(selectedColor.displayColor)
                                    }
                                )
                                .border(1.5.dp, contrastColor.copy(alpha = 0.9f), tileShape)
                        )
                    } else {
                        val paletteIcon = Icons.Outlined.Palette
                        if (showShadows) {
                            Icon(
                                imageVector = paletteIcon,
                                contentDescription = null,
                                tint = primaryTextColor.getShadowColor(shadowSettings.shadowColorOverride),
                                modifier = Modifier
                                    .size(22.dp)
                                    .offset(1.dp, 1.dp)
                            )
                        }
                        Icon(
                            imageVector = paletteIcon,
                            contentDescription = stringResource(R.string.side_overlay_colors_title),
                            tint = accentColor.color,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Subtle vertical separator
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(16.dp)
                        .background(primaryTextColor.color.copy(alpha = 0.22f))
                )

                // 2. Slot Status Indicator (History or Widget)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    if (slotMode == SideAlphabetSlotMode.WIDGET) {
                        if (showShadows) {
                            Icon(
                                imageVector = Icons.Outlined.Widgets,
                                contentDescription = null,
                                tint = primaryTextColor.getShadowColor(shadowSettings.shadowColorOverride),
                                modifier = Modifier
                                    .size(22.dp)
                                    .offset(1.dp, 1.dp)
                            )
                        }
                        Icon(
                            imageVector = Icons.Outlined.Widgets,
                            contentDescription = stringResource(R.string.widget_title),
                            tint = accentColor.color,
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        val historyIcon = if (isHistoryEditMode) Icons.Outlined.Check
                            else if (isHistoryPaused) Icons.Outlined.HistoryToggleOff
                            else Icons.Outlined.History
                        val historyTint = if (isHistoryEditMode) accentColor.color
                            else if (isHistoryPaused) accentColor.color.copy(alpha = 0.45f)
                            else accentColor.color
                        if (showShadows) {
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
                            contentDescription = if (isHistoryEditMode) stringResource(R.string.history_menu_done_edit)
                                else if (isHistoryPaused) stringResource(R.string.history_menu_paused)
                                else stringResource(R.string.history_menu_title),
                            tint = historyTint,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

