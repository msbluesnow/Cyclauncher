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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Widgets
import dev.msbs.cyclauncher.SideAlphabetSlotMode
import dev.msbs.cyclauncher.model.AppColorBucket
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import dev.msbs.cyclauncher.R
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
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
    HISTORY_POSITION_TOGGLE,
    QUICK_PALETTE
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
    var tutorialPaletteOverlayState by remember { mutableStateOf<QuickPaletteOverlayState?>(null) }
    var tutorialPaletteColor by remember { mutableStateOf<AppColorBucket?>(null) }
    var tutorialSelectedFeedback by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(stepIndex) {
        tutorialOverlayState = null
        tutorialPaletteOverlayState = null
        tutorialSelectedFeedback = null
    }

    val context = LocalContext.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val cardWidthPx = with(density) { 160.dp.toPx() }
    val screenMarginPx = with(density) { 16.dp.toPx() }

    val steps = remember(handSide, context) {
        listOf(
            TutorialStepInfo(
                title = context.getString(R.string.tutorial_step1_title),
                description = context.getString(R.string.tutorial_step1_desc),
                gestureType = GestureType.SWIPE_UP,
                hintText = context.getString(R.string.tutorial_step1_hint)
            ),
            TutorialStepInfo(
                title = context.getString(R.string.tutorial_step2_title),
                description = context.getString(R.string.tutorial_step2_desc),
                gestureType = GestureType.SIDE_BACK,
                hintText = context.getString(R.string.tutorial_step2_hint)
            ),
            TutorialStepInfo(
                title = context.getString(R.string.tutorial_step3_title),
                description = context.getString(R.string.tutorial_step3_desc),
                gestureType = GestureType.SWIPE_DOWN,
                hintText = context.getString(R.string.tutorial_step3_hint)
            ),
            TutorialStepInfo(
                title = context.getString(R.string.tutorial_step4_title),
                description = context.getString(R.string.tutorial_step4_desc),
                gestureType = GestureType.SWIPE_HIGHLIGHTS,
                hintText = context.getString(R.string.tutorial_step4_hint)
            ),
            TutorialStepInfo(
                title = context.getString(R.string.tutorial_step5_title),
                description = context.getString(R.string.tutorial_step5_desc),
                gestureType = GestureType.LONG_PRESS,
                hintText = context.getString(R.string.tutorial_step5_hint)
            ),
            TutorialStepInfo(
                title = context.getString(R.string.tutorial_step6_title),
                description = context.getString(R.string.tutorial_step6_desc),
                gestureType = GestureType.FAVORITES_HISTORY,
                hintText = context.getString(R.string.tutorial_step6_hint)
            ),
            TutorialStepInfo(
                title = context.getString(R.string.tutorial_step7_title),
                description = context.getString(R.string.tutorial_step7_desc),
                gestureType = GestureType.HISTORY_POSITION_TOGGLE,
                hintText = context.getString(R.string.tutorial_step7_hint)
            ),
            TutorialStepInfo(
                title = context.getString(R.string.tutorial_step8_title),
                description = context.getString(R.string.tutorial_step8_desc),
                gestureType = GestureType.QUICK_PALETTE,
                hintText = context.getString(R.string.tutorial_step8_hint)
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

    if (showTutorial && tutorialPaletteOverlayState != null) {
        BackHandler {
            tutorialPaletteOverlayState = null
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
                                            context.getString(R.string.quick_action_notifications_title)
                                        } else {
                                            context.getString(R.string.quick_action_quick_settings_title)
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
                    } else if (currentStep.gestureType == GestureType.QUICK_PALETTE) {
                        detectTapGestures(
                            onTap = { offset ->
                                if (tutorialPaletteOverlayState == null) {
                                    tutorialPaletteOverlayState = QuickPaletteOverlayState(
                                        anchorPosition = offset,
                                        handSide = handSide
                                    )
                                }
                            }
                        )
                    }
                }
        ) {
            val isCompactHeight = configuration.screenHeightDp < 720
            val isVeryCompactHeight = configuration.screenHeightDp < 600

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Top status bar with progress dots and skip button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = if (configuration.screenWidthDp > 600) 32.dp else 20.dp,
                            vertical = if (isCompactHeight) 8.dp else 14.dp
                        ),
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

                    val skipInteractionSource = remember { MutableInteractionSource() }
                    TextButton(
                        onClick = {
                            viewModel.completeTutorial()
                            onNavigateToMain()
                        },
                        interactionSource = skipInteractionSource,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.8f))
                    ) {
                        TouchMarqueeText(
                            text = stringResource(R.string.tutorial_skip),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            interactionSource = skipInteractionSource
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.common_close),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // 2. Interactive Stage: takes all available vertical space between top bar and bottom card
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    GestureAnimationCanvas(
                        gestureType = currentStep.gestureType,
                        handSide = handSide,
                        accentColor = accentColor,
                        popupTheme = popupTheme,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Success feedback pill showing which action was triggered in the trial
                    androidx.compose.animation.AnimatedVisibility(
                        visible = tutorialSelectedFeedback != null,
                        enter = if (animationsEnabled) fadeIn() + scaleIn() else EnterTransition.None,
                        exit = if (animationsEnabled) fadeOut() else ExitTransition.None,
                        modifier = Modifier.align(Alignment.Center)
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
                                    text = stringResource(R.string.tutorial_tested_feedback, tutorialSelectedFeedback ?: ""),
                                    color = buttonTextColor.color,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // 3. Adaptive Bottom Description & Controls Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = if (configuration.screenWidthDp > 600) 32.dp else 16.dp,
                            end = if (configuration.screenWidthDp > 600) 32.dp else 16.dp,
                            bottom = if (isCompactHeight) 8.dp else 16.dp,
                            top = 4.dp
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val cardShape = remember(isCompactHeight) {
                        RoundedCornerShape(if (isCompactHeight) 18.dp else 22.dp)
                    }
                    val maxCardHeight = (configuration.screenHeightDp * 0.44f).dp

                    Card(
                        modifier = Modifier
                            .widthIn(max = 480.dp)
                            .fillMaxWidth()
                            .shadow(if (isCompactHeight) 10.dp else 16.dp, cardShape),
                        shape = cardShape,
                        border = BorderStroke(1.dp, popupTheme.borderColor),
                        colors = CardDefaults.cardColors(
                            containerColor = if (popupTheme == PopupTheme.LIGHT) Color(0xFFF6F6F6).copy(alpha = 0.96f) else Color(0xFF1C1C1E).copy(alpha = 0.94f)
                        )
                    ) {
                        val scrollState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = maxCardHeight)
                                .verticalScroll(scrollState)
                                .padding(
                                    horizontal = if (isCompactHeight) 16.dp else 22.dp,
                                    vertical = if (isCompactHeight) 12.dp else 18.dp
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = currentStep.title,
                                color = popupTheme.contentColor,
                                fontSize = if (isVeryCompactHeight) 16.sp else if (isCompactHeight) 17.5.sp else 19.5.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(if (isCompactHeight) 6.dp else 8.dp))

                            Text(
                                text = currentStep.description,
                                color = popupTheme.secondaryContentColor,
                                fontSize = if (isVeryCompactHeight) 12.sp else if (isCompactHeight) 13.sp else 13.5.sp,
                                lineHeight = if (isVeryCompactHeight) 16.sp else if (isCompactHeight) 18.sp else 19.5.sp,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(if (isCompactHeight) 8.dp else 12.dp))

                            Surface(
                                shape = RoundedCornerShape(50),
                                color = accentColor.copy(alpha = if (popupTheme == PopupTheme.LIGHT) 0.12f else 0.2f),
                                border = BorderStroke(1.dp, accentColor.copy(alpha = if (popupTheme == PopupTheme.LIGHT) 0.35f else 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(
                                        horizontal = if (isCompactHeight) 12.dp else 14.dp,
                                        vertical = if (isCompactHeight) 5.dp else 7.dp
                                    ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = accentColor,
                                        modifier = Modifier.size(if (isCompactHeight) 14.dp else 16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = currentStep.hintText,
                                        color = accentColor,
                                        fontSize = if (isVeryCompactHeight) 11.5.sp else 12.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(if (isCompactHeight) 10.dp else 14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (stepIndex > 0) {
                                    val backInteractionSource = remember { MutableInteractionSource() }
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
                                        interactionSource = backInteractionSource,
                                        colors = ButtonDefaults.textButtonColors(contentColor = popupTheme.secondaryContentColor)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = stringResource(R.string.common_back),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        TouchMarqueeText(
                                            text = stringResource(R.string.common_back),
                                            interactionSource = backInteractionSource
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.width(1.dp))
                                }

                                val nextInteractionSource = remember { MutableInteractionSource() }
                                Button(
                                    onClick = { triggerSuccessAndNext() },
                                    interactionSource = nextInteractionSource,
                                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.heightIn(min = if (isCompactHeight) 36.dp else 40.dp)
                                ) {
                                    val buttonText = stringResource(if (stepIndex == steps.lastIndex) R.string.tutorial_finish else R.string.tutorial_next)
                                    TouchMarqueeText(
                                        text = buttonText,
                                        color = buttonTextColor.color,
                                        fontWeight = FontWeight.Bold,
                                        interactionSource = nextInteractionSource
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = buttonText,
                                        tint = buttonTextColor.color,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

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

            // Live interactive quick palette overlay when testing step 8
            tutorialPaletteOverlayState?.let { overlayState ->
                QuickPaletteOverlay(
                    state = overlayState,
                    selectedColor = tutorialPaletteColor,
                    isHistoryPaused = false,
                    slotMode = SideAlphabetSlotMode.HISTORY,
                    accentColor = accentColorEnum,
                    primaryTextColor = primaryTextColor,
                    popupTheme = popupTheme,
                    onSelectColor = { bucket ->
                        tutorialPaletteOverlayState = null
                        tutorialPaletteColor = bucket
                        tutorialSelectedFeedback = context.getString(R.string.side_overlay_colors_title)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        coroutineScope.launch {
                            delay(850)
                            triggerSuccessAndNext()
                        }
                    },
                    onOpenHistoryMenu = {
                        tutorialPaletteOverlayState = null
                        tutorialSelectedFeedback = context.getString(R.string.history_menu_title)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        coroutineScope.launch {
                            delay(850)
                            triggerSuccessAndNext()
                        }
                    },
                    onDismiss = {
                        tutorialPaletteOverlayState = null
                    }
                )
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
                    val cardCenterY = centerY - 25.dp.toPx()
                    val leftIsNotifications = handSide == HandSide.LEFT
                    val notifX = if (leftIsNotifications) centerX - 67.5.dp.toPx() else centerX + 67.5.dp.toPx()
                    val qsX = if (leftIsNotifications) centerX + 67.5.dp.toPx() else centerX - 67.5.dp.toPx()
                    val startY = cardCenterY - 100.dp.toPx()

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
                    val boxHeight = 56.dp.toPx()
                    val boxLeft = centerX - boxWidth / 2f

                    val startBoxY = centerY + 40.dp.toPx()
                    val endBoxY = centerY - 65.dp.toPx()
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

                GestureType.QUICK_PALETTE -> {
                    val triggerX = centerX
                    val triggerY = centerY + 3.dp.toPx()
                    val targetX = centerX
                    val targetY = centerY - 55.dp.toPx()

                    val currentX: Float
                    val currentY: Float

                    if (progress < 0.40f) {
                        currentX = triggerX
                        currentY = triggerY

                        // Tap 1 pulse on Trigger Bar
                        if (progress in 0.18f..0.38f) {
                            val p1 = (progress - 0.18f) / 0.20f
                            drawCircle(
                                color = accentColor.copy(alpha = alpha * (1f - p1) * 0.8f),
                                radius = 12.dp.toPx() + (22.dp.toPx() * p1),
                                center = Offset(triggerX, triggerY),
                                style = Stroke(width = 2.5.dp.toPx())
                            )
                        }
                    } else if (progress < 0.55f) {
                        // Smooth movement between tap 1 and tap 2
                        val pMove = (progress - 0.40f) / 0.15f
                        currentX = triggerX + (targetX - triggerX) * pMove
                        currentY = triggerY + (targetY - triggerY) * pMove
                    } else {
                        currentX = targetX
                        currentY = targetY

                        // Tap 2 pulse on Color target
                        if (progress in 0.62f..0.85f) {
                            val p2 = (progress - 0.62f) / 0.23f
                            drawCircle(
                                color = accentColor.copy(alpha = alpha * (1f - p2) * 0.8f),
                                radius = 12.dp.toPx() + (22.dp.toPx() * p2),
                                center = Offset(targetX, targetY),
                                style = Stroke(width = 2.5.dp.toPx())
                            )
                        }
                    }

                    drawCircle(
                        color = accentColor.copy(alpha = alpha * 0.28f),
                        radius = 24.dp.toPx(),
                        center = Offset(currentX, currentY)
                    )
                    drawCircle(
                        color = accentColor.copy(alpha = alpha),
                        radius = 12.dp.toPx(),
                        center = Offset(currentX, currentY)
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
                            contentDescription = stringResource(R.string.tutorial_favorites),
                            tint = accentColor,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.tutorial_favorites),
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
                            contentDescription = stringResource(R.string.history_menu_title),
                            tint = accentColor,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.history_menu_title),
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
                            contentDescription = stringResource(R.string.tutorial_tags),
                            tint = accentColor,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.tutorial_tags),
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
                    contentDescription = stringResource(R.string.highlight_title),
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
                    RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp, topEnd = 0.dp, bottomEnd = 0.dp)
                else
                    RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 18.dp, bottomEnd = 18.dp)
            }
            val qsShape = remember(leftIsNotifications) {
                if (leftIsNotifications)
                    RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 18.dp, bottomEnd = 18.dp)
                else
                    RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp, topEnd = 0.dp, bottomEnd = 0.dp)
            }

            Row(
                modifier = Modifier.offset(y = (-25).dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (leftIsNotifications) {
                    TutorialPreviewCard(
                        title = stringResource(R.string.quick_action_notifications_title),
                        subtitle = stringResource(R.string.quick_action_notifications_subtitle),
                        icon = Icons.Outlined.Notifications,
                        isActive = isNotifActive,
                        shape = notifShape,
                        accentColor = accentColor,
                        popupTheme = popupTheme,
                        textColor = previewTextColor
                    )
                    TutorialPreviewCard(
                        title = stringResource(R.string.quick_action_quick_settings_title),
                        subtitle = stringResource(R.string.quick_action_quick_settings_subtitle),
                        icon = Icons.Outlined.Tune,
                        isActive = isQsActive,
                        shape = qsShape,
                        accentColor = accentColor,
                        popupTheme = popupTheme,
                        textColor = previewTextColor
                    )
                } else {
                    TutorialPreviewCard(
                        title = stringResource(R.string.quick_action_quick_settings_title),
                        subtitle = stringResource(R.string.quick_action_quick_settings_subtitle),
                        icon = Icons.Outlined.Tune,
                        isActive = isQsActive,
                        shape = qsShape,
                        accentColor = accentColor,
                        popupTheme = popupTheme,
                        textColor = previewTextColor
                    )
                    TutorialPreviewCard(
                        title = stringResource(R.string.quick_action_notifications_title),
                        subtitle = stringResource(R.string.quick_action_notifications_subtitle),
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

        if (gestureType == GestureType.QUICK_PALETTE) {
            val previewCardShape = remember { RoundedCornerShape(18.dp) }
            val paletteAlpha = when {
                progress < 0.32f -> 0f
                progress < 0.42f -> (progress - 0.32f) / 0.10f
                progress > 0.90f -> (1f - progress) / 0.10f
                else -> 1f
            }
            val isColorSelected = progress > 0.70f

            Column(
                modifier = Modifier
                    .offset(y = (-20).dp)
                    .width(236.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Floating Quick Palette card preview (animates open on tap)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .alpha(paletteAlpha)
                        .clip(RoundedCornerShape(14.dp))
                        .background(popupTheme.solidBackgroundColor.copy(alpha = 0.96f))
                        .border(1.dp, popupTheme.borderColor, RoundedCornerShape(14.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Palette,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = stringResource(R.string.side_overlay_colors_title),
                                color = if (popupTheme == PopupTheme.LIGHT) Color.Black else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }

                        // Mini color dots row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val demoColors = remember {
                                listOf(
                                    Color(0xFFFF3B30), // Red
                                    Color(0xFFFF9500), // Orange
                                    Color(0xFFFFCC00), // Yellow
                                    Color(0xFF34C759), // Green (target)
                                    Color(0xFF30B0C7), // Cyan
                                    Color(0xFF007AFF), // Blue
                                    Color(0xFFAF52DE)  // Purple
                                )
                            }
                            demoColors.forEachIndexed { idx, col ->
                                val isTarget = idx == 3 // Green
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(col)
                                        .border(
                                            width = if (isTarget && isColorSelected) 2.dp else 1.dp,
                                            color = if (isTarget && isColorSelected) Color.White else Color.White.copy(alpha = 0.3f),
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isTarget && isColorSelected) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Outer alphabet column container
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(previewCardShape)
                        .background(popupTheme.solidBackgroundColor.copy(alpha = 0.95f))
                        .border(1.5.dp, popupTheme.borderColor, previewCardShape)
                        .padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Trigger Bar Preview
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isColorSelected) accentColor.copy(alpha = 0.22f)
                                else accentColor.copy(alpha = 0.12f)
                            )
                            .border(
                                width = if (progress in 0.18f..0.38f) 2.dp else 1.dp,
                                color = if (progress in 0.18f..0.38f) accentColor else accentColor.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isColorSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(Color(0xFF34C759))
                                        .border(1.dp, Color.White, RoundedCornerShape(5.dp))
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.Palette,
                                    contentDescription = stringResource(R.string.side_overlay_colors_title),
                                    tint = accentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(16.dp)
                                    .background(Color.White.copy(alpha = 0.22f))
                            )
                            Icon(
                                imageVector = Icons.Outlined.History,
                                contentDescription = stringResource(R.string.history_menu_title),
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Mini Alphabet Grid representation
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf("#", "A", "B", "C", "D", "E").forEach { letter ->
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White.copy(alpha = 0.08f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = letter,
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
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
    val cardInteractionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .width(140.dp)
            .height(84.dp)
            .clip(shape)
            .background(
                if (isActive) accentColor.copy(alpha = 0.28f)
                else popupTheme.solidBackgroundColor.copy(alpha = 0.92f)
            )
            .border(
                width = if (isActive) 2.dp else 1.dp,
                color = if (isActive) accentColor else popupTheme.borderColor,
                shape = shape
            )
            .clickable(
                interactionSource = cardInteractionSource,
                indication = null,
                onClick = {}
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isActive) accentColor else textColor.copy(alpha = 0.85f),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            TouchMarqueeText(
                text = title,
                color = textColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                interactionSource = cardInteractionSource
            )
            TouchMarqueeText(
                text = subtitle,
                color = textColor.copy(alpha = 0.8f),
                fontSize = 9.5.sp,
                textAlign = TextAlign.Center,
                interactionSource = cardInteractionSource
            )
        }
    }
}
