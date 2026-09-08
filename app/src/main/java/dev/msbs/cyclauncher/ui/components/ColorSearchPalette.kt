package dev.msbs.cyclauncher.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.HistoryToggleOff
import androidx.compose.material3.Icon
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import dev.msbs.cyclauncher.model.AppColorBucket
import dev.msbs.cyclauncher.ui.theme.AccentColor
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
                            isHistoryEditMode -> "Done Editing"
                            isHistoryPaused -> "History (Paused)"
                            else -> "History"
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
