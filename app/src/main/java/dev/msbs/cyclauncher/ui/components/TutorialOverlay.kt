package dev.msbs.cyclauncher.ui.components

import dev.msbs.cyclauncher.HandSide
import dev.msbs.cyclauncher.LauncherViewModel
import dev.msbs.cyclauncher.ui.theme.AccentColor
import dev.msbs.cyclauncher.ui.theme.PopupTheme
import dev.msbs.cyclauncher.ui.theme.PrimaryTextColor

import dev.msbs.cyclauncher.ui.theme.LocalAnimationsEnabled
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Data class representing a single step in the interactive tutorial.
 */
data class TutorialStepInfo(
    val title: String,
    val description: String,
    val gestureType: GestureType,
    val hintText: String
)

enum class GestureType {
    SWIPE_UP,
    SIDE_BACK,
    SWIPE_DOWN,
    SWIPE_HIGHLIGHTS,
    LONG_PRESS,
    FAVORITES_HISTORY,
    HISTORY_POSITION_TOGGLE
}

/**
 * Interactive tutorial overlay demonstrating launcher gestures.
 */
@Composable
fun TutorialOverlay(
    viewModel: LauncherViewModel,
    onNavigateToSearch: () -> Unit = {},
    onNavigateToMain: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val showTutorial by viewModel.showTutorial.collectAsState()
    val stepIndex by viewModel.tutorialStep.collectAsState()
    val handSide by viewModel.handSide.collectAsState()
    val accentColorEnum by viewModel.accentColor.collectAsState()
    val primaryTextColor by viewModel.primaryTextColor.collectAsState()
    val buttonTextColor by viewModel.buttonTextColor.collectAsState()
    val popupTheme by viewModel.popupTheme.collectAsState()

    val accentColor = accentColorEnum.color
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    var isSuccessFlash by remember { mutableStateOf(false) }

    var tutorialOverlayState by remember { mutableStateOf<SwipeDownOverlayState?>(null) }
    var tutorialSelectedFeedback by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(stepIndex) {
        tutorialOverlayState = null
        tutorialSelectedFeedback = null
    }

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val cardWidthPx = with(density) { 160.dp.toPx() }
    val screenMarginPx = with(density) { 16.dp.toPx() }

    val steps = remember(handSide) {
        listOf(
            TutorialStepInfo(
                title = "Swipe Up — App Search",
                description = "Swipe upwards over the Favorites icons area on the Home Screen to open application search.",
                gestureType = GestureType.SWIPE_UP,
                hintText = "Swipe up over the Favorites icons area!"
            ),
            TutorialStepInfo(
                title = "System Back — Return to Home",
                description = "Use your device's native Back gesture (swipe from edge) or press the system Back button to return to the Home Screen from Search or Settings.",
                gestureType = GestureType.SIDE_BACK,
                hintText = "Use the native Back gesture or Back button!"
            ),
            TutorialStepInfo(
                title = "Swipe Down — Quick Actions",
                description = "Swipe downwards over the Favorites area to activate quick action cards. Release directly under your finger to open Notifications, or slide to the adjacent card to open Quick Settings.",
                gestureType = GestureType.SWIPE_DOWN,
                hintText = "Try it below: swipe down, slide between cards, and release!"
            ),
            TutorialStepInfo(
                title = "Swipe Sideways — Highlights",
                description = "Swipe horizontally from the center area (left-to-right or right-to-left based on your hand preference) to open the Highlights workspace. Access launcher overview metrics and customizable Android widgets.",
                gestureType = GestureType.SWIPE_HIGHLIGHTS,
                hintText = "Swipe horizontally from the center area!"
            ),
            TutorialStepInfo(
                title = "Long Press — Menu & Settings",
                description = "Press and hold any empty area of the screen to open Launcher Settings, or long-press an app item for quick actions.",
                gestureType = GestureType.LONG_PRESS,
                hintText = "Press and hold on the screen!"
            ),
            TutorialStepInfo(
                title = "Favorites, History & Tags",
                description = "Press and hold a Favorite item to reorder or remove it. Tap the History icon to open the menu (edit list, pause/resume recording, or clear history). Tap a Tag folder to open apps, or long-press it to edit the group or add/remove from Favorites.",
                gestureType = GestureType.FAVORITES_HISTORY,
                hintText = "Hold Favorite, tap History icon, or tap/hold Tag folder!"
            ),
            TutorialStepInfo(
                title = "History Position Shift",
                description = "Swipe UP on the History list when it is at the bottom to shift it to the top section of the screen. Swipe DOWN on the Tags area when history is at the top to swap their positions.",
                gestureType = GestureType.HISTORY_POSITION_TOGGLE,
                hintText = "Swipe UP on History or DOWN on Tags area!"
            )
        )
    }

    val currentStep = steps.getOrNull(stepIndex) ?: return

    fun triggerSuccessAndNext() {
        if (!isSuccessFlash) {
            isSuccessFlash = true
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            val currentGesture = currentStep.gestureType
            viewModel.nextTutorialStep()
            isSuccessFlash = false

            if (currentGesture == GestureType.SWIPE_UP) {
                onNavigateToSearch()
            } else if (currentGesture == GestureType.SIDE_BACK) {
                onNavigateToMain()
            }
        }
    }

    if (showTutorial && currentStep.gestureType == GestureType.SIDE_BACK) {
        BackHandler {
            triggerSuccessAndNext()
        }
    }

    val animationsEnabled = LocalAnimationsEnabled.current

    AnimatedVisibility(
        visible = showTutorial,
        enter = if (animationsEnabled) fadeIn(animationSpec = tween(400)) else EnterTransition.None,
        exit = if (animationsEnabled) fadeOut(animationSpec = tween(300)) else ExitTransition.None
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.82f))
                .pointerInput(stepIndex, handSide) {
                    var totalDragX = 0f
                    var totalDragY = 0f
                    var startOffset = Offset.Zero
                    var isTutorialSwipeDownActive = false
                    var lastTarget = SwipeDownTarget.NOTIFICATIONS

                    detectDragGestures(
                        onDragStart = { offset ->
                            totalDragX = 0f
                            totalDragY = 0f
                            startOffset = offset
                            isTutorialSwipeDownActive = false
                            lastTarget = SwipeDownTarget.NOTIFICATIONS
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            totalDragX += dragAmount.x
                            totalDragY += dragAmount.y

                            if (currentStep.gestureType == GestureType.SWIPE_DOWN) {
                                val dragThreshold = 20f
                                if (totalDragY > dragThreshold || isTutorialSwipeDownActive) {
                                    isTutorialSwipeDownActive = true
                                    val currentPos = change.position
                                    val target = resolveSwipeDownTarget(
                                        anchorX = startOffset.x,
                                        currentX = currentPos.x,
                                        handSide = handSide,
                                        screenWidthPx = screenWidthPx,
                                        cardWidthPx = cardWidthPx,
                                        screenMarginPx = screenMarginPx
                                    )
                                    lastTarget = target
                                    if (tutorialOverlayState == null) {
                                        tutorialOverlayState = SwipeDownOverlayState(
                                            anchorPosition = startOffset,
                                            currentPosition = currentPos,
                                            handSide = handSide,
                                            selectedTarget = target
                                        )
                                    } else if (tutorialOverlayState?.selectedTarget != target) {
                                        tutorialOverlayState = tutorialOverlayState?.copy(
                                            selectedTarget = target
                                        )
                                    }
                                }
                            }
                        },
                        onDragEnd = {
                            val threshold = 60f
                            when (currentStep.gestureType) {
                                GestureType.SWIPE_UP -> if (totalDragY < -threshold) triggerSuccessAndNext()
                                GestureType.SWIPE_DOWN -> {
                                    if (isTutorialSwipeDownActive || totalDragY > threshold) {
                                        tutorialOverlayState = null
                                        val chosen = lastTarget
                                        tutorialSelectedFeedback = if (chosen == SwipeDownTarget.NOTIFICATIONS) {
                                            "Notifications"
                                        } else {
                                            "Quick Settings"
                                        }
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        coroutineScope.launch {
                                            delay(850)
                                            triggerSuccessAndNext()
                                        }
                                    }
                                }
                                GestureType.SIDE_BACK -> {
                                    val isBackDirection = if (handSide == HandSide.LEFT) totalDragX < -threshold else totalDragX > threshold
                                    if (isBackDirection || kotlin.math.abs(totalDragX) > threshold) triggerSuccessAndNext()
                                }
                                GestureType.SWIPE_HIGHLIGHTS -> {
                                    val isMatch = when (handSide) {
                                        HandSide.RIGHT -> totalDragX > threshold
                                        HandSide.LEFT -> totalDragX < -threshold
                                    }
                                    if (isMatch || kotlin.math.abs(totalDragX) > threshold) triggerSuccessAndNext()
                                }
                                GestureType.HISTORY_POSITION_TOGGLE -> {
                                    if (kotlin.math.abs(totalDragY) > threshold) triggerSuccessAndNext()
                                }
                                else -> {}
                            }
                        },
                        onDragCancel = {
                            tutorialOverlayState = null
                            isTutorialSwipeDownActive = false
                        }
                    )
                }
                .pointerInput(stepIndex) {
                    if (currentStep.gestureType == GestureType.LONG_PRESS || currentStep.gestureType == GestureType.FAVORITES_HISTORY) {
                        detectTapGestures(
                            onLongPress = {
                                triggerSuccessAndNext()
                            }
                        )
                    }
                }
        ) {
            GestureAnimationCanvas(
                gestureType = currentStep.gestureType,
                handSide = handSide,
                accentColor = accentColor,
                popupTheme = popupTheme,
                modifier = Modifier.fillMaxSize()
            )

            // Live interactive trial overlay when swiping down in the tutorial
            tutorialOverlayState?.let { overlayState ->
                SwipeDownQuickActionsOverlay(
                    state = overlayState,
                    accentColor = accentColorEnum,
                    primaryTextColor = primaryTextColor,
                    popupTheme = popupTheme,
                    onDismiss = null
                )
            }

            // Success feedback pill showing which action was triggered in the trial
            AnimatedVisibility(
                visible = tutorialSelectedFeedback != null,
                enter = if (animationsEnabled) fadeIn() + scaleIn() else EnterTransition.None,
                exit = if (animationsEnabled) fadeOut() else ExitTransition.None,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = 80.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = accentColor,
                    shadowElevation = 10.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = buttonTextColor.color,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Tested: $tutorialSelectedFeedback!",
                            color = buttonTextColor.color,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    steps.indices.forEach { index ->
                        val active = index == stepIndex
                        Box(
                            modifier = Modifier
                                .size(if (active) 12.dp else 8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (active) accentColor else Color.White.copy(alpha = 0.4f)
                                )
                        )
                    }
                }

                TextButton(
                    onClick = {
                        viewModel.completeTutorial()
                        onNavigateToMain()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.8f))
                ) {
                    Text(text = "Skip", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(24.dp)
                    .fillMaxWidth()
                    .shadow(16.dp, RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, popupTheme.borderColor),
                colors = CardDefaults.cardColors(
                    containerColor = if (popupTheme == PopupTheme.LIGHT) Color(0xFFF6F6F6).copy(alpha = 0.96f) else Color(0xFF1C1C1E).copy(alpha = 0.94f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = currentStep.title,
                        color = popupTheme.contentColor,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = currentStep.description,
                        color = popupTheme.secondaryContentColor,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = accentColor.copy(alpha = if (popupTheme == PopupTheme.LIGHT) 0.12f else 0.2f),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = if (popupTheme == PopupTheme.LIGHT) 0.35f else 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = currentStep.hintText,
                                color = accentColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (stepIndex > 0) {
                            TextButton(
                                onClick = {
                                    val prevStep = stepIndex - 1
                                    viewModel.setTutorialStep(prevStep)
                                    if (prevStep == 0) {
                                        onNavigateToMain()
                                    } else if (prevStep == 1) {
                                        onNavigateToSearch()
                                    }
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = popupTheme.secondaryContentColor)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Back")
                            }
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }

                        Button(
                            onClick = { triggerSuccessAndNext() },
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = if (stepIndex == steps.lastIndex) "Finish" else "Next",
                                color = buttonTextColor.color,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Next",
                                tint = buttonTextColor.color,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Animated Canvas drawing hand gesture guidance pointers for the tutorial.
 */
@Composable
private fun GestureAnimationCanvas(
    gestureType: GestureType,
    handSide: HandSide,
    accentColor: Color,
    popupTheme: PopupTheme = PopupTheme.DARK,
    modifier: Modifier = Modifier
) {
    val animationsEnabled = LocalAnimationsEnabled.current
    val progress = if (animationsEnabled) {
        val infiniteTransition = rememberInfiniteTransition(label = "GestureAnim")
        val animProgress by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "Progress"
        )
        animProgress
    } else {
        0.5f
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val centerX = width / 2f
            val centerY = height / 2f

            val alpha = when {
                progress < 0.15f -> progress / 0.15f
                progress > 0.85f -> (1f - progress) / 0.15f
                else -> 1f
            }

            when (gestureType) {
                GestureType.SWIPE_UP -> {
                    val startY = height * 0.7f
                    val endY = height * 0.35f
                    val currentY = startY + (endY - startY) * progress

                    drawLine(
                        color = accentColor.copy(alpha = alpha * 0.4f),
                        start = Offset(centerX, startY),
                        end = Offset(centerX, currentY),
                        strokeWidth = 4.dp.toPx()
                    )

                    drawCircle(
                        color = accentColor.copy(alpha = alpha * 0.25f),
                        radius = 28.dp.toPx(),
                        center = Offset(centerX, currentY)
                    )
                    drawCircle(
                        color = accentColor.copy(alpha = alpha),
                        radius = 12.dp.toPx(),
                        center = Offset(centerX, currentY)
                    )
                }

                GestureType.SIDE_BACK -> {
                    val isLeftHand = handSide == HandSide.LEFT
                    val startX = if (isLeftHand) width * 0.8f else width * 0.2f
                    val endX = if (isLeftHand) width * 0.2f else width * 0.8f
                    val currentX = startX + (endX - startX) * progress

                    drawLine(
                        color = accentColor.copy(alpha = alpha * 0.4f),
                        start = Offset(startX, centerY),
                        end = Offset(currentX, centerY),
                        strokeWidth = 4.dp.toPx()
                    )

                    drawCircle(
                        color = accentColor.copy(alpha = alpha * 0.25f),
                        radius = 28.dp.toPx(),
                        center = Offset(currentX, centerY)
                    )
                    drawCircle(
                        color = accentColor.copy(alpha = alpha),
                        radius = 12.dp.toPx(),
                        center = Offset(currentX, centerY)
                    )
                }

                GestureType.SWIPE_DOWN -> {
                    val cardCenterY = centerY - 50.dp.toPx()
                    val leftIsNotifications = handSide == HandSide.LEFT
                    val notifX = if (leftIsNotifications) centerX - 67.5.dp.toPx() else centerX + 67.5.dp.toPx()
                    val qsX = if (leftIsNotifications) centerX + 67.5.dp.toPx() else centerX - 67.5.dp.toPx()
                    val startY = cardCenterY - 130.dp.toPx()

                    val currentX: Float
                    val currentY: Float

                    if (progress < 0.40f) {
                        val p1 = progress / 0.40f
                        currentX = notifX
                        currentY = startY + (cardCenterY - startY) * p1

                        drawLine(
                            color = accentColor.copy(alpha = alpha * 0.45f),
                            start = Offset(notifX, startY),
                            end = Offset(notifX, currentY),
                            strokeWidth = 4.dp.toPx()
                        )
                    } else if (progress < 0.75f) {
                        val p2 = (progress - 0.40f) / 0.35f
                        currentX = notifX + (qsX - notifX) * p2
                        currentY = cardCenterY

                        drawLine(
                            color = accentColor.copy(alpha = alpha * 0.45f),
                            start = Offset(notifX, startY),
                            end = Offset(notifX, cardCenterY),
                            strokeWidth = 4.dp.toPx()
                        )
                        drawLine(
                            color = accentColor.copy(alpha = alpha * 0.45f),
                            start = Offset(notifX, cardCenterY),
                            end = Offset(currentX, cardCenterY),
                            strokeWidth = 4.dp.toPx()
                        )
                    } else {
                        val p3 = (progress - 0.75f) / 0.25f
                        currentX = qsX
                        currentY = cardCenterY

                        drawLine(
                            color = accentColor.copy(alpha = alpha * 0.35f),
                            start = Offset(notifX, startY),
                            end = Offset(notifX, cardCenterY),
                            strokeWidth = 4.dp.toPx()
                        )
                        drawLine(
                            color = accentColor.copy(alpha = alpha * 0.35f),
                            start = Offset(notifX, cardCenterY),
                            end = Offset(qsX, cardCenterY),
                            strokeWidth = 4.dp.toPx()
                        )

                        // Pulse ring on selection release
                        drawCircle(
                            color = accentColor.copy(alpha = (1f - p3) * 0.7f),
                            radius = 16.dp.toPx() + (30.dp.toPx() * p3),
                            center = Offset(qsX, cardCenterY),
                            style = Stroke(width = 3.dp.toPx())
                        )
                    }

                    drawCircle(
                        color = accentColor.copy(alpha = alpha * 0.3f),
                        radius = 26.dp.toPx(),
                        center = Offset(currentX, currentY)
                    )
                    drawCircle(
                        color = accentColor.copy(alpha = alpha),
                        radius = 12.dp.toPx(),
                        center = Offset(currentX, currentY)
                    )
                }

                GestureType.SWIPE_HIGHLIGHTS -> {
                    val isRightHand = handSide == HandSide.RIGHT
                    val startX = if (isRightHand) width * 0.35f else width * 0.65f
                    val endX = if (isRightHand) width * 0.75f else width * 0.25f
                    val currentX = startX + (endX - startX) * progress

                    val trackY = centerY - 20.dp.toPx()

                    drawLine(
                        color = accentColor.copy(alpha = alpha * 0.4f),
                        start = Offset(startX, trackY),
                        end = Offset(currentX, trackY),
                        strokeWidth = 4.dp.toPx()
                    )

                    drawCircle(
                        color = accentColor.copy(alpha = alpha * 0.25f),
                        radius = 28.dp.toPx(),
                        center = Offset(currentX, trackY)
                    )
                    drawCircle(
                        color = accentColor.copy(alpha = alpha),
                        radius = 12.dp.toPx(),
                        center = Offset(currentX, trackY)
                    )
                }

                GestureType.LONG_PRESS -> {
                    val ringRadius = (16.dp.toPx() + (40.dp.toPx() * progress))
                    val ringAlpha = (1f - progress) * alpha

                    drawCircle(
                        color = accentColor.copy(alpha = ringAlpha * 0.6f),
                        radius = ringRadius,
                        center = Offset(centerX, centerY),
                        style = Stroke(width = 3.dp.toPx())
                    )

                    drawCircle(
                        color = accentColor.copy(alpha = alpha * 0.3f),
                        radius = 24.dp.toPx(),
                        center = Offset(centerX, centerY)
                    )

                    drawCircle(
                        color = accentColor.copy(alpha = alpha),
                        radius = 14.dp.toPx(),
                        center = Offset(centerX, centerY)
                    )
                }

                GestureType.FAVORITES_HISTORY -> {
                    val targetX = centerX - 44.dp.toPx()
                    val targetY = centerY - 20.dp.toPx()

                    val ringRadius = (20.dp.toPx() + (36.dp.toPx() * progress))
                    val ringAlpha = (1f - progress) * alpha

                    drawCircle(
                        color = accentColor.copy(alpha = ringAlpha * 0.7f),
                        radius = ringRadius,
                        center = Offset(targetX, targetY),
                        style = Stroke(width = 3.dp.toPx())
                    )

                    drawCircle(
                        color = accentColor.copy(alpha = alpha * 0.3f),
                        radius = 24.dp.toPx(),
                        center = Offset(targetX, targetY)
                    )

                    drawCircle(
                        color = accentColor.copy(alpha = alpha),
                        radius = 12.dp.toPx(),
                        center = Offset(targetX, targetY)
                    )
                }

                GestureType.HISTORY_POSITION_TOGGLE -> {
                    val boxWidth = 180.dp.toPx()
                    val boxHeight = 60.dp.toPx()
                    val boxLeft = centerX - boxWidth / 2f

                    val startBoxY = height * 0.62f
                    val endBoxY = height * 0.28f
                    val currentBoxY = startBoxY + (endBoxY - startBoxY) * progress

                    drawRoundRect(
                        color = accentColor.copy(alpha = 0.2f),
                        topLeft = Offset(boxLeft, currentBoxY),
                        size = Size(boxWidth, boxHeight),
                        cornerRadius = CornerRadius(16.dp.toPx())
                    )
                    drawRoundRect(
                        color = accentColor.copy(alpha = 0.7f),
                        topLeft = Offset(boxLeft, currentBoxY),
                        size = Size(boxWidth, boxHeight),
                        cornerRadius = CornerRadius(16.dp.toPx()),
                        style = Stroke(width = 2.dp.toPx())
                    )

                    drawLine(
                        color = accentColor.copy(alpha = alpha * 0.6f),
                        start = Offset(centerX, startBoxY + boxHeight / 2f),
                        end = Offset(centerX, currentBoxY + boxHeight / 2f),
                        strokeWidth = 4.dp.toPx()
                    )

                    drawCircle(
                        color = accentColor.copy(alpha = alpha * 0.3f),
                        radius = 26.dp.toPx(),
                        center = Offset(centerX, currentBoxY + boxHeight / 2f)
                    )
                    drawCircle(
                        color = accentColor.copy(alpha = alpha),
                        radius = 12.dp.toPx(),
                        center = Offset(centerX, currentBoxY + boxHeight / 2f)
                    )
                }
            }
        }

        if (gestureType == GestureType.FAVORITES_HISTORY) {
            Row(
                modifier = Modifier
                    .offset(y = (-20).dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .shadow(12.dp, CircleShape)
                            .clip(CircleShape)
                            .background(popupTheme.solidBackgroundColor)
                            .border(2.dp, accentColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Star,
                            contentDescription = "Favorites",
                            tint = accentColor,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Favorites",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .shadow(12.dp, CircleShape)
                            .clip(CircleShape)
                            .background(popupTheme.solidBackgroundColor)
                            .border(2.dp, accentColor.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = "History",
                            tint = accentColor,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "History",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .shadow(12.dp, RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                            .background(popupTheme.solidBackgroundColor)
                            .border(2.dp, accentColor.copy(alpha = 0.8f), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Label,
                            contentDescription = "Tag Folder",
                            tint = accentColor,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tags",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        if (gestureType == GestureType.SWIPE_HIGHLIGHTS) {
            Box(
                modifier = Modifier
                    .offset(y = (-60).dp)
                    .size(64.dp)
                    .shadow(12.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(popupTheme.solidBackgroundColor)
                    .border(2.dp, accentColor, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Widgets,
                    contentDescription = "Highlights",
                    tint = accentColor,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        if (gestureType == GestureType.SWIPE_DOWN) {
            val leftIsNotifications = handSide == HandSide.LEFT
            val isNotifActive = progress in 0.30f..0.52f
            val isQsActive = progress > 0.52f
            val previewTextColor = if (popupTheme == PopupTheme.LIGHT) Color.Black else Color.White

            val notifShape = remember(leftIsNotifications) {
                if (leftIsNotifications)
                    RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp, topEnd = 3.dp, bottomEnd = 3.dp)
                else
                    RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp, topEnd = 18.dp, bottomEnd = 18.dp)
            }
            val qsShape = remember(leftIsNotifications) {
                if (leftIsNotifications)
                    RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp, topEnd = 18.dp, bottomEnd = 18.dp)
                else
                    RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp, topEnd = 3.dp, bottomEnd = 3.dp)
            }

            Row(
                modifier = Modifier.offset(y = (-50).dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (leftIsNotifications) {
                    TutorialPreviewCard(
                        title = "Notifications",
                        subtitle = "Notification Shade",
                        icon = Icons.Outlined.Notifications,
                        isActive = isNotifActive,
                        shape = notifShape,
                        accentColor = accentColor,
                        popupTheme = popupTheme,
                        textColor = previewTextColor
                    )
                    TutorialPreviewCard(
                        title = "Quick Settings",
                        subtitle = "Wi-Fi, Bluetooth",
                        icon = Icons.Outlined.Tune,
                        isActive = isQsActive,
                        shape = qsShape,
                        accentColor = accentColor,
                        popupTheme = popupTheme,
                        textColor = previewTextColor
                    )
                } else {
                    TutorialPreviewCard(
                        title = "Quick Settings",
                        subtitle = "Wi-Fi, Bluetooth",
                        icon = Icons.Outlined.Tune,
                        isActive = isQsActive,
                        shape = qsShape,
                        accentColor = accentColor,
                        popupTheme = popupTheme,
                        textColor = previewTextColor
                    )
                    TutorialPreviewCard(
                        title = "Notifications",
                        subtitle = "Notification Shade",
                        icon = Icons.Outlined.Notifications,
                        isActive = isNotifActive,
                        shape = notifShape,
                        accentColor = accentColor,
                        popupTheme = popupTheme,
                        textColor = previewTextColor
                    )
                }
            }
        }
    }
}

@Composable
private fun TutorialPreviewCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean,
    shape: RoundedCornerShape,
    accentColor: Color,
    popupTheme: PopupTheme,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(135.dp)
            .height(84.dp)
            .scale(if (isActive) 1.05f else 0.98f)
            .clip(shape)
            .background(
                if (isActive) accentColor.copy(alpha = 0.28f)
                else popupTheme.solidBackgroundColor.copy(alpha = 0.92f)
            )
            .border(
                width = if (isActive) 2.5.dp else 1.dp,
                color = if (isActive) accentColor else popupTheme.borderColor,
                shape = shape
            )
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isActive) accentColor else textColor.copy(alpha = 0.85f),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                color = textColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                color = textColor.copy(alpha = 0.8f),
                fontSize = 9.5.sp
            )
        }
    }
}
