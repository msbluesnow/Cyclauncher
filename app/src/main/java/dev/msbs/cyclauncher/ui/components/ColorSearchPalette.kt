package dev.msbs.cyclauncher.ui.components

import dev.msbs.cyclauncher.HandSide
import dev.msbs.cyclauncher.SideAlphabetSlotMode
import dev.msbs.cyclauncher.ui.theme.LocalPopupAlpha
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
import dev.msbs.cyclauncher.ui.theme.capsuleColor
import dev.msbs.cyclauncher.ui.theme.capsuleBorderColor

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

    val popupAlpha = LocalPopupAlpha.current
    val backgroundBrush = remember(accentColor.color, popupTheme, popupAlpha) {
        Brush.verticalGradient(
            colors = listOf(
                popupTheme.backgroundColor(popupAlpha),
                popupTheme.solidBackgroundColor(popupAlpha)
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
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.Center
                            ) {
                                TouchMarqueeText(
                                    text = if (isHistoryEditMode) stringResource(R.string.history_menu_done_edit)
                                           else stringResource(R.string.history_menu_title),
                                    color = textColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.5.sp
                                )
                                TouchMarqueeText(
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
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.Center
                            ) {
                                TouchMarqueeText(
                                    text = stringResource(R.string.widget_title),
                                    color = textColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.5.sp
                                )
                                TouchMarqueeText(
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
                else primaryTextColor.capsuleColor(0.08f)
            )
            .border(
                width = 1.dp,
                color = if (isColorSelected) accentColor.color.copy(alpha = 0.55f)
                else primaryTextColor.capsuleBorderColor(0.16f),
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
                        .background(primaryTextColor.capsuleBorderColor(0.22f))
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

