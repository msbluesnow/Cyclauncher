package dev.msbs.cyclauncher.ui.components

import dev.msbs.cyclauncher.HandSide
import dev.msbs.cyclauncher.LauncherViewModel
import dev.msbs.cyclauncher.ui.theme.AccentColor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    LONG_PRESS,
    FAVORITES_HISTORY,
    HISTORY_POSITION_TOGGLE
}

/**
 * Full-screen interactive tutorial overlay demonstrating launcher gestures in English.
 * Performs real screen switches (Main Screen <-> Search Screen) during gesture walkthrough steps.
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
    val buttonTextColor by viewModel.buttonTextColor.collectAsState()

    val accentColor = accentColorEnum.color
    val haptic = LocalHapticFeedback.current
    var isSuccessFlash by remember { mutableStateOf(false) }

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
                title = "Swipe Down — Notifications",
                description = "Swipe downwards over the Favorites icons area on the Home Screen to pull down the notification shade.",
                gestureType = GestureType.SWIPE_DOWN,
                hintText = "Swipe down over the Favorites icons area!"
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

    // Capture system Back button gesture on step 1 (Side Back) to advance tutorial & navigate home automatically
    if (showTutorial && currentStep.gestureType == GestureType.SIDE_BACK) {
        BackHandler {
            triggerSuccessAndNext()
        }
    }

    AnimatedVisibility(
        visible = showTutorial,
        enter = fadeIn(animationSpec = tween(400)),
        exit = fadeOut(animationSpec = tween(300))
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.82f))
                .pointerInput(stepIndex, handSide) {
                    var totalDragX = 0f
                    var totalDragY = 0f

                    detectDragGestures(
                        onDragStart = {
                            totalDragX = 0f
                            totalDragY = 0f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            totalDragX += dragAmount.x
                            totalDragY += dragAmount.y
                        },
                        onDragEnd = {
                            val threshold = 60f
                            when (currentStep.gestureType) {
                                GestureType.SWIPE_UP -> if (totalDragY < -threshold) triggerSuccessAndNext()
                                GestureType.SWIPE_DOWN -> if (totalDragY > threshold) triggerSuccessAndNext()
                                GestureType.SIDE_BACK -> {
                                    val isBackDirection = if (handSide == HandSide.LEFT) totalDragX < -threshold else totalDragX > threshold
                                    if (isBackDirection || kotlin.math.abs(totalDragX) > threshold) triggerSuccessAndNext()
                                }
                                GestureType.HISTORY_POSITION_TOGGLE -> {
                                    if (kotlin.math.abs(totalDragY) > threshold) triggerSuccessAndNext()
                                }
                                else -> {}
                            }
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
            // Animated Gesture Visualizer
            GestureAnimationCanvas(
                gestureType = currentStep.gestureType,
                handSide = handSide,
                accentColor = accentColor,
                modifier = Modifier.fillMaxSize()
            )

            // Header Controls: Step Indicator & Skip Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Step Dots
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

                // Skip Button
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

            // Bottom Information Card
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(24.dp)
                    .fillMaxWidth()
                    .shadow(16.dp, RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1C1C1E).copy(alpha = 0.94f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = currentStep.title,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = currentStep.description,
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Pulsing interactive hint badge
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = accentColor.copy(alpha = 0.2f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.5f))
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

                    // Next / Finish Controls
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
                                colors = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.7f))
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
 * Animated Canvas drawing hand gesture guidance pointers and theme icon previews for the tutorial.
 */
@Composable
private fun GestureAnimationCanvas(
    gestureType: GestureType,
    handSide: HandSide,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "GestureAnim")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Progress"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // Overlay Canvas for gesture paths, pointers, and rings
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
                    val startY = height * 0.25f
                    val endY = height * 0.65f
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

                    // Animate history block moving from bottom to top
                    val startBoxY = height * 0.62f
                    val endBoxY = height * 0.28f
                    val currentBoxY = startBoxY + (endBoxY - startBoxY) * progress

                    // History container mock
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

                    // Directional Up Arrow
                    drawLine(
                        color = accentColor.copy(alpha = alpha * 0.6f),
                        start = Offset(centerX, startBoxY + boxHeight / 2f),
                        end = Offset(centerX, currentBoxY + boxHeight / 2f),
                        strokeWidth = 4.dp.toPx()
                    )

                    // Touch Indicator
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

        // Dedicated UI elements preview for Favorites & History step
        if (gestureType == GestureType.FAVORITES_HISTORY) {
            Row(
                modifier = Modifier
                    .offset(y = (-20).dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Favorites Icon Card
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .shadow(12.dp, CircleShape)
                            .clip(CircleShape)
                            .background(Color(0xFF1C1C1E))
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

                // History Icon Card
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .shadow(12.dp, CircleShape)
                            .clip(CircleShape)
                            .background(Color(0xFF1C1C1E))
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

                // Tag Folder Card (Rounded Rectangle with Tag Icon in Center)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .shadow(12.dp, RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF1C1C1E))
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
    }
}
