package dev.msbs.cyclauncher.ui.components

import dev.msbs.cyclauncher.model.AppInfo
import dev.msbs.cyclauncher.ui.theme.AccentColor

import android.graphics.Paint
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A custom interactive alphabet wheel arranged in a rectangular path around the screen.
 * Displays letters from A to Z and '#', allowing drag gestures to choose active letters
 * and selecting applications starting with that letter.
 *
 * @param scrollOffset The animating scroll position value of the wheel.
 * @param onLetterSelected Callback when a new letter becomes active.
 * @param apps The list of applications that match the active letter.
 * @param onAppClick Callback when an application icon inside the wheel is tapped.
 * @param onAppLongClick Callback when an application icon is long-pressed (provides coordinates).
 * @param accentColor The active UI accent color.
 * @param modifier Modifier for configuration.
 */
@Composable
fun RectangularAlphabetWheel(
    scrollOffset: Animatable<Float, AnimationVector1D>,
    onLetterSelected: (Char) -> Unit,
    apps: List<AppInfo>,
    onAppClick: (String) -> Unit,
    onAppLongClick: (String, Offset) -> Unit = { _, _ -> },
    accentColor: AccentColor = AccentColor.CYAN,
    modifier: Modifier = Modifier
) {
    val alphabet = remember { ('A'..'Z').toList() + '#' }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    
    val currentOnLetterSelected by rememberUpdatedState(onLetterSelected)
    
    var wheelPosition by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(modifier = modifier) {
        // Dynamic scaling based on screen width, reduced by exactly 7% as requested
        val baseWidth = 360.dp
        val scaleFactor = ((maxWidth / baseWidth).coerceIn(0.7f, 1.2f)) * 0.93f
        
        val stepSize = 34.dp * scaleFactor
        val fontSize = 16.sp * scaleFactor.toDouble()
        val s = with(density) { stepSize.toPx() }
        val offsetDist = with(density) { 4.dp.toPx() * scaleFactor }
        
        val floatIndex by remember {
            derivedStateOf {
                val count = alphabet.size
                val raw = ((scrollOffset.value % count) + count) % count
                raw.coerceIn(0f, count - 0.0001f)
            }
        }

        val activeIndex by remember {
            derivedStateOf { floatIndex.roundToInt() % alphabet.size }
        }

        LaunchedEffect(activeIndex) {
            currentOnLetterSelected(alphabet[activeIndex])
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }

        fun getRectPosition(i: Int): Offset {
            val normalized = Math.floorMod(i, 27)
            return when {
                normalized < 10 -> Offset(normalized * s, 0f)
                normalized < 14 -> Offset(9 * s, (normalized - 9) * s)
                normalized < 23 -> Offset((22 - normalized) * s, 4 * s)
                normalized < 26 -> Offset(0f, (26 - normalized) * s)
                else -> Offset(-s, 0f)
            }
        }

        fun getIndicatorPoint(i: Int): Offset {
            val normalized = Math.floorMod(i, 27)
            val pos = getRectPosition(normalized)
            val c = s / 2f
            val cx = pos.x + c
            val cy = pos.y + c
            
            return when {
                normalized < 10 -> Offset(cx, -offsetDist)
                normalized < 14 -> Offset(9 * s + s + offsetDist, cy)
                normalized < 23 -> Offset(cx, 4 * s + s + offsetDist)
                normalized < 26 -> Offset(-offsetDist, cy)
                else -> Offset(cx, -offsetDist)
            }
        }

        // Precompute distances for perfect dot synchronization
        val segmentDistances = remember(density, s, offsetDist) {
            val o = offsetDist
            val hLeft = -s + with(density) { (10.dp * scaleFactor).toPx() }
            val rX = 10 * s + o
            val bY = 5 * s + o
            val lX = -o
            val tY = -o

            FloatArray(27) { i ->
                when (i) {
                    in 0..8 -> s // A-J
                    9 -> (rX - getIndicatorPoint(9).x) + (getIndicatorPoint(10).y - tY) // J-K Corner
                    in 10..12 -> s // K-N
                    13 -> (bY - getIndicatorPoint(13).y) + (rX - getIndicatorPoint(14).x) // N-O Corner
                    in 14..21 -> s // O-W
                    22 -> (getIndicatorPoint(22).x - lX) + (bY - getIndicatorPoint(23).y) // W-X Corner
                    23, 24 -> s // X-Z
                    25 -> (getIndicatorPoint(25).x - hLeft) + (getIndicatorPoint(25).y - tY) + (getIndicatorPoint(26).x - hLeft) // Z-#
                    26 -> (getIndicatorPoint(0).x - getIndicatorPoint(26).x) // #-A
                    else -> 0f
                }
            }
        }
        
        val cumulativeDistances = remember(segmentDistances) {
            val arr = FloatArray(28)
            var sum = 0f
            for (i in 0..26) {
                arr[i] = sum
                sum += segmentDistances[i]
            }
            arr[27] = sum
            arr
        }

        val indicatorPath = remember(density, s, offsetDist) {
            Path().apply {
                val tY = -offsetDist
                val rX = 10 * s + offsetDist
                val bY = 5 * s + offsetDist
                val lX = -offsetDist
                val hLeft = -s + with(density) { (10.dp * scaleFactor).toPx() }

                moveTo(getIndicatorPoint(0).x, tY)
                for (i in 1..9) lineTo(getIndicatorPoint(i).x, tY)
                lineTo(rX, tY)
                for (i in 10..13) lineTo(rX, getIndicatorPoint(i).y)
                lineTo(rX, bY)
                for (i in 14..22) lineTo(getIndicatorPoint(i).x, bY)
                lineTo(lX, bY)
                for (i in 23..25) lineTo(lX, getIndicatorPoint(i).y)
                
                lineTo(hLeft, getIndicatorPoint(25).y)
                lineTo(hLeft, tY)
                lineTo(getIndicatorPoint(26).x, tY)
                close()
            }
        }

        val pathMeasure = remember(indicatorPath) { PathMeasure().apply { setPath(indicatorPath, false) } }
        val totalLength = pathMeasure.length

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(stepSize * 9 + 100.dp, stepSize * 4 + 100.dp)
                .onGloballyPositioned { wheelPosition = it.positionInRoot() }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            val sensitivity = 1f + (abs(dragAmount) / 45f).coerceAtMost(2.5f)
                            scope.launch {
                                scrollOffset.snapTo(scrollOffset.value - (dragAmount / 100f) * sensitivity)
                            }
                        },
                        onDragEnd = {
                            scope.launch {
                                scrollOffset.animateTo(
                                    targetValue = scrollOffset.value.roundToInt().toFloat(),
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessLow
                                    )
                                )
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Internal container for the wheel to keep it centered and shifted for #
            Box(
                modifier = Modifier
                    .size(stepSize * 10, stepSize * 5)
                    .graphicsLayer { translationX = s / 2f } // Shift right to accommodate #
                    .drawWithCache {
                        val paint = Paint().apply {
                            isAntiAlias = true
                            style = Paint.Style.STROKE
                            strokeCap = Paint.Cap.BUTT
                            strokeJoin = Paint.Join.MITER
                        }

                        onDrawBehind {
                            val lower = floatIndex.toInt()
                            val progress = floatIndex - lower
                            
                            val totalDistRef = cumulativeDistances[27]
                            val rawDist = (cumulativeDistances[lower] + progress * segmentDistances[lower])
                            val scale = totalLength / totalDistRef
                            val currentDist = (rawDist * scale) % totalLength
                            
                            val halfLen = with(density) { 16.dp.toPx() * scaleFactor }
                            val segmentPath = Path()
                            val start = currentDist - halfLen
                            val end = currentDist + halfLen
                            
                            if (start < 0) {
                                pathMeasure.getSegment(start + totalLength, totalLength, segmentPath, true)
                                pathMeasure.getSegment(0f, end, segmentPath, true)
                            } else if (end > totalLength) {
                                pathMeasure.getSegment(start, totalLength, segmentPath, true)
                                pathMeasure.getSegment(0f, end - totalLength, segmentPath, true)
                            } else {
                                pathMeasure.getSegment(start, end, segmentPath, true)
                            }

                            drawIntoCanvas { canvas ->
                                val nativeCanvas = canvas.nativeCanvas
                                paint.color = accentColor.color.toArgb()
                                paint.strokeWidth = with(density) { 4.dp.toPx() * scaleFactor }
                                nativeCanvas.drawPath(segmentPath.asAndroidPath(), paint)
                                
                                val centerPos = pathMeasure.getPosition(currentDist)
                                paint.style = Paint.Style.FILL
                                nativeCanvas.drawCircle(centerPos.x, centerPos.y, with(density) { 2.dp.toPx() * scaleFactor }, paint)
                                paint.style = Paint.Style.STROKE
                            }
                        }
                    }
            ) {
                // Letter rendering
                alphabet.forEachIndexed { index, letter ->
                    AlphabetLetterItem(
                        letter = letter,
                        index = index,
                        floatIndexProvider = { floatIndex },
                        stepSize = stepSize,
                        fontSize = fontSize,
                        accentColor = accentColor,
                        pos = getRectPosition(index)
                    )
                }

                AppsGrid(
                    apps = apps,
                    s = s,
                    density = density,
                    wheelPosition = wheelPosition,
                    stepSize = stepSize,
                    onAppClick = onAppClick,
                    onAppLongClick = onAppLongClick,
                    scaleFactor = scaleFactor
                )
            }
        }
    }
}

/**
 * Renders the grid of application icons centered inside the rectangular alphabet wheel.
 * Dynamically adjusts icon size and column count based on the number of apps to display.
 *
 * @param apps The list of applications matching the selected letter.
 * @param s The step size in pixels corresponding to the path structure.
 * @param density Context density provider.
 * @param wheelPosition The absolute root offset coordinate of the alphabet wheel.
 * @param stepSize The step size in Dp unit.
 * @param onAppClick Callback when an application icon is tapped.
 * @param onAppLongClick Callback when an application icon is long-pressed (provides coordinates).
 * @param scaleFactor Dynamic scaling factor calculated from screen size.
 */

@Composable
fun AppsGrid(
    apps: List<AppInfo>,
    s: Float,
    density: Density,
    wheelPosition: Offset,
    stepSize: Dp,
    onAppClick: (String) -> Unit,
    onAppLongClick: (String, Offset) -> Unit,
    scaleFactor: Float
) {
    val displayApps = apps.take(20)
    if (displayApps.isEmpty()) return

    val currentOnAppClick by rememberUpdatedState(onAppClick)
    val currentOnAppLongClick by rememberUpdatedState(onAppLongClick)

    val dynamicSize = (when {
        displayApps.size <= 4 -> 48.dp
        displayApps.size <= 8 -> 40.dp
        displayApps.size <= 12 -> 34.dp
        else -> 28.dp
    }) * scaleFactor

    val dynamicSizePx = with(density) { dynamicSize.toPx() }
    val spacingPx = with(density) { 4.dp.toPx() * scaleFactor }
    val maxWidthPx = 10 * s
    val maxCols = ((maxWidthPx - 2 * s) / (dynamicSizePx + spacingPx)).toInt().coerceAtLeast(1)
    val rowsNeeded = (displayApps.size + maxCols - 1) / maxCols
    val totalBlockHeightPx = rowsNeeded * dynamicSizePx + (rowsNeeded - 1).coerceAtLeast(0) * spacingPx

    val letterHeightPx = with(density) { (22.sp * scaleFactor.toDouble()).toPx() }
    val innerTopY = letterHeightPx
    val innerBottomY = 4 * s
    val availableHeight = innerBottomY - innerTopY
    val startYPx = innerTopY + (availableHeight - totalBlockHeightPx) / 2f

    displayApps.forEachIndexed { index, app ->
        val row = index / maxCols
        val col = index % maxCols
        val itemsInThisRow = if (row < rowsNeeded - 1) maxCols else displayApps.size - (row * maxCols)
        val currentRowWidthPx = itemsInThisRow * dynamicSizePx + (itemsInThisRow - 1) * spacingPx
        val rowStartXPx = (10 * s - currentRowWidthPx) / 2f

        val x = rowStartXPx + col * (dynamicSizePx + spacingPx)
        val y = startYPx + row * (dynamicSizePx + spacingPx)

        // Coil-backed icon. We always reserve the cell (no early return), so the grid layout
        // stays stable while the painter asynchronously resolves from memory/disk cache.
        val iconSizeDp = dynamicSize.value.toInt()
        val painter = rememberAppIconPainter(app.iconKey, iconSizeDp)

        Image(
            painter = painter,
            contentDescription = app.label,
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            modifier = Modifier
                .size(dynamicSize)
                .graphicsLayer {
                    translationX = x
                    translationY = y
                }
                .clip(CircleShape)
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.15f),
                    shape = CircleShape
                )
                .pointerInput(app.componentKey) {
                    detectTapGestures(
                        onTap = { currentOnAppClick(app.componentKey) },
                        onLongPress = { offset ->
                            val outerWidthPx = with(density) { (stepSize * 10).toPx() }
                            val outerHeightPx = with(density) { (stepSize * 5).toPx() }
                            val paddingXPx = (outerWidthPx - 9 * s) / 2f
                            val paddingYPx = (outerHeightPx - 4 * s) / 2f

                            currentOnAppLongClick(app.componentKey, wheelPosition + Offset(paddingXPx + x + offset.x, paddingYPx + y + offset.y))
                        }
                    )
                }
        )
    }
}

/**
 * Individual alphabet letter item rendered on the path, featuring dynamic sizing
 * and glowing animations when closest to the active scroll index.
 */
@Composable
private fun AlphabetLetterItem(
    letter: Char,
    index: Int,
    floatIndexProvider: () -> Float,
    stepSize: Dp,
    fontSize: androidx.compose.ui.unit.TextUnit,
    accentColor: AccentColor,
    pos: Offset,
    modifier: Modifier = Modifier
) {
    val isExactState = remember {
        derivedStateOf {
            val floatIndex = floatIndexProvider()
            val dist = abs(floatIndex - index).let { if (it > 13.5) 27 - it else it }
            dist < 0.5f
        }
    }

    Box(
        modifier = modifier
            .size(stepSize)
            .graphicsLayer {
                val floatIndex = floatIndexProvider()
                val dist = abs(floatIndex - index).let { if (it > 13.5) 27 - it else it }.toFloat()
                val scale = (1.5f - (dist * 0.4f)).coerceIn(1.0f, 1.5f)
                val alpha = (1.0f - (dist * 0.2f)).coerceIn(0.4f, 1.0f)
                
                translationX = pos.x
                translationY = pos.y
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .drawBehind {
                val floatIndex = floatIndexProvider()
                val dist = abs(floatIndex - index).let { if (it > 13.5) 27 - it else it }.toFloat()
                val glowAlpha = (1f - dist * 2f).coerceIn(0f, 1f)
                
                if (glowAlpha > 0f) {
                    val radius = (stepSize * 0.85f).toPx() / 2f
                    drawCircle(
                        color = accentColor.glowColor.copy(alpha = accentColor.glowColor.alpha * glowAlpha),
                        radius = radius
                    )
                    drawCircle(
                        color = accentColor.color.copy(alpha = 0.25f * glowAlpha),
                        radius = radius,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val isExact by isExactState
        Text(
            text = letter.toString(),
            fontSize = fontSize,
            fontWeight = if (isExact) FontWeight.Bold else FontWeight.Normal,
            color = if (isExact) accentColor.color else Color.White
        )
    }
}
