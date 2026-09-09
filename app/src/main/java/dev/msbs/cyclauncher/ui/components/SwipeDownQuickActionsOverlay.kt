package dev.msbs.cyclauncher.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.msbs.cyclauncher.HandSide
import dev.msbs.cyclauncher.ui.theme.AccentColor
import dev.msbs.cyclauncher.ui.theme.LocalAnimationsEnabled
import dev.msbs.cyclauncher.ui.theme.PopupTheme
import dev.msbs.cyclauncher.ui.theme.PrimaryTextColor
import kotlin.math.roundToInt

/**
 * Available targets when swiping down on the favorites bar.
 */
enum class SwipeDownTarget {
    NOTIFICATIONS,
    QUICK_SETTINGS
}

/**
 * Real-time state representing a swipe-down quick action overlay session.
 */
data class SwipeDownOverlayState(
    val anchorPosition: Offset,
    val currentPosition: Offset,
    val handSide: HandSide,
    val selectedTarget: SwipeDownTarget = SwipeDownTarget.NOTIFICATIONS
)

/**
 * Calculates the active [SwipeDownTarget] based on gesture positions and handedness.
 * With gap = 0, the seam between both cards defines the selection boundary.
 */
fun resolveSwipeDownTarget(
    anchorX: Float,
    currentX: Float,
    handSide: HandSide,
    screenWidthPx: Float,
    cardWidthPx: Float,
    screenMarginPx: Float
): SwipeDownTarget {
    var notifLeftX: Float
    var qsLeftX: Float

    if (handSide == HandSide.LEFT) {
        notifLeftX = (anchorX - cardWidthPx / 2f).coerceAtLeast(screenMarginPx)
        qsLeftX = notifLeftX + cardWidthPx
        if (qsLeftX + cardWidthPx > screenWidthPx - screenMarginPx) {
            val overflow = (qsLeftX + cardWidthPx) - (screenWidthPx - screenMarginPx)
            notifLeftX -= overflow
            qsLeftX -= overflow
        }
        val seamX = notifLeftX + cardWidthPx
        return if (currentX < seamX) SwipeDownTarget.NOTIFICATIONS else SwipeDownTarget.QUICK_SETTINGS
    } else {
        notifLeftX = (anchorX - cardWidthPx / 2f).coerceAtMost(screenWidthPx - screenMarginPx - cardWidthPx)
        qsLeftX = notifLeftX - cardWidthPx
        if (qsLeftX < screenMarginPx) {
            val underflow = screenMarginPx - qsLeftX
            qsLeftX += underflow
            notifLeftX += underflow
        }
        val seamX = notifLeftX
        return if (currentX >= seamX) SwipeDownTarget.NOTIFICATIONS else SwipeDownTarget.QUICK_SETTINGS
    }
}

/**
 * Full-screen overlay appearing when dragging down the favorites shade.
 * Displays two tightly adjoining rectangles (Notifications and Quick Settings) at the gesture's Y coordinate.
 */
@Composable
fun SwipeDownQuickActionsOverlay(
    state: SwipeDownOverlayState,
    accentColor: AccentColor,
    primaryTextColor: PrimaryTextColor,
    popupTheme: PopupTheme,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    val cardWidth = 160.dp
    val cardHeight = 98.dp
    val screenMargin = 16.dp

    val cardWidthPx = with(density) { cardWidth.toPx() }
    val cardHeightPx = with(density) { cardHeight.toPx() }
    val screenMarginPx = with(density) { screenMargin.toPx() }

    val topInsetPx = with(density) { 60.dp.toPx() }
    val bottomInsetPx = with(density) { 80.dp.toPx() }

    val (notifOffset, qsOffset) = remember(
        state.anchorPosition,
        state.handSide,
        screenWidthPx,
        screenHeightPx,
        cardWidthPx,
        cardHeightPx,
        screenMarginPx,
        topInsetPx,
        bottomInsetPx
    ) {
        val cardCenterY = state.anchorPosition.y.coerceIn(
            topInsetPx + cardHeightPx / 2f,
            screenHeightPx - bottomInsetPx - cardHeightPx / 2f
        )
        val cardTopY = cardCenterY - cardHeightPx / 2f

        var notifLeftX: Float
        var qsLeftX: Float

        if (state.handSide == HandSide.LEFT) {
            notifLeftX = (state.anchorPosition.x - cardWidthPx / 2f).coerceAtLeast(screenMarginPx)
            qsLeftX = notifLeftX + cardWidthPx
            if (qsLeftX + cardWidthPx > screenWidthPx - screenMarginPx) {
                val overflow = (qsLeftX + cardWidthPx) - (screenWidthPx - screenMarginPx)
                notifLeftX -= overflow
                qsLeftX -= overflow
            }
        } else {
            notifLeftX = (state.anchorPosition.x - cardWidthPx / 2f).coerceAtMost(screenWidthPx - screenMarginPx - cardWidthPx)
            qsLeftX = notifLeftX - cardWidthPx
            if (qsLeftX < screenMarginPx) {
                val underflow = screenMarginPx - qsLeftX
                qsLeftX += underflow
                notifLeftX += underflow
            }
        }

        IntOffset(notifLeftX.roundToInt(), cardTopY.roundToInt()) to IntOffset(qsLeftX.roundToInt(), cardTopY.roundToInt())
    }

    val currentTarget = state.selectedTarget

    // Haptic feedback tick on target switch
    var previousTarget by remember { mutableStateOf<SwipeDownTarget?>(null) }
    LaunchedEffect(currentTarget) {
        if (previousTarget != null && previousTarget != currentTarget) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        previousTarget = currentTarget
    }

    val notifTitle = "Notifications"
    val notifSubtitle = "Notification Shade"
    val qsTitle = "Quick Settings"
    val qsSubtitle = "Wi-Fi, Bluetooth"

    val dismissModifier = remember(onDismiss) {
        if (onDismiss != null) {
            Modifier.pointerInput(onDismiss) {
                detectTapGestures(
                    onTap = { onDismiss() }
                )
            }
        } else {
            Modifier
        }
    }

    val (notifShape, qsShape) = remember(state.handSide) {
        if (state.handSide == HandSide.LEFT) {
            RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp, topEnd = 4.dp, bottomEnd = 4.dp) to
                    RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 22.dp, bottomEnd = 22.dp)
        } else {
            RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 22.dp, bottomEnd = 22.dp) to
                    RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp, topEnd = 4.dp, bottomEnd = 4.dp)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .then(dismissModifier)
    ) {
        // Notifications Target Card
        ActionTargetCard(
            title = notifTitle,
            subtitle = notifSubtitle,
            icon = Icons.Outlined.Notifications,
            isSelected = currentTarget == SwipeDownTarget.NOTIFICATIONS,
            accentColor = accentColor,
            primaryTextColor = primaryTextColor,
            popupTheme = popupTheme,
            shape = notifShape,
            modifier = Modifier
                .offset { notifOffset }
                .width(cardWidth)
                .height(cardHeight)
        )

        // Quick Settings Target Card
        ActionTargetCard(
            title = qsTitle,
            subtitle = qsSubtitle,
            icon = Icons.Outlined.Tune,
            isSelected = currentTarget == SwipeDownTarget.QUICK_SETTINGS,
            accentColor = accentColor,
            primaryTextColor = primaryTextColor,
            popupTheme = popupTheme,
            shape = qsShape,
            modifier = Modifier
                .offset { qsOffset }
                .width(cardWidth)
                .height(cardHeight)
        )
    }
}

@Composable
private fun ActionTargetCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    accentColor: AccentColor,
    primaryTextColor: PrimaryTextColor,
    popupTheme: PopupTheme,
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier
) {
    val animationsEnabled = LocalAnimationsEnabled.current
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.04f else 0.98f,
        animationSpec = if (animationsEnabled) spring(dampingRatio = 0.75f, stiffness = 500f) else snap(),
        label = "targetCardScale"
    )

    val textColor = if (popupTheme == PopupTheme.LIGHT) Color.Black else Color.White

    val backgroundBrush = remember(isSelected, accentColor.color, popupTheme.backgroundColor, popupTheme.solidBackgroundColor) {
        if (isSelected) {
            Brush.verticalGradient(
                colors = listOf(
                    accentColor.color.copy(alpha = 0.35f),
                    popupTheme.solidBackgroundColor.copy(alpha = 0.95f)
                )
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    popupTheme.backgroundColor.copy(alpha = 0.88f),
                    popupTheme.solidBackgroundColor.copy(alpha = 0.92f)
                )
            )
        }
    }

    val borderStroke = remember(isSelected, accentColor.color, popupTheme.borderColor) {
        if (isSelected) {
            BorderStroke(2.5.dp, accentColor.color)
        } else {
            BorderStroke(1.dp, popupTheme.borderColor)
        }
    }

    val iconBgColor = remember(isSelected, accentColor.color, popupTheme) {
        if (isSelected) accentColor.color.copy(alpha = 0.25f)
        else if (popupTheme == PopupTheme.LIGHT) Color.Black.copy(alpha = 0.06f)
        else Color.White.copy(alpha = 0.08f)
    }

    val iconTint = remember(isSelected, accentColor.color, textColor) {
        if (isSelected) accentColor.color else textColor.copy(alpha = 0.85f)
    }

    Box(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .background(backgroundBrush)
            .border(border = borderStroke, shape = shape)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = title,
                color = textColor,
                fontWeight = FontWeight.Bold,
                fontSize = 15.5.sp,
                maxLines = 1,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subtitle,
                color = textColor.copy(alpha = 0.8f),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}
