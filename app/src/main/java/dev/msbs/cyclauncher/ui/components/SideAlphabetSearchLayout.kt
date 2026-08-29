package dev.msbs.cyclauncher.ui.components

import dev.msbs.cyclauncher.LauncherViewModel
import dev.msbs.cyclauncher.HandSide
import dev.msbs.cyclauncher.model.AppInfo
import dev.msbs.cyclauncher.ui.theme.AccentColor
import dev.msbs.cyclauncher.ui.theme.PrimaryTextColor
import dev.msbs.cyclauncher.ui.theme.LocalShadowSettings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize

/**
 * One-handed search layout with a side-aligned alphabet grid and dynamic app list.
 */
@Composable
fun SideAlphabetSearchLayout(
    viewModel: LauncherViewModel,
    handSide: HandSide,
    onAppClick: (String) -> Unit,
    onAppLongClick: (AppInfo, Offset) -> Unit
) {
    val filteredApps by viewModel.filteredApps.collectAsState()
    val selectedLetter by viewModel.selectedLetter.collectAsState()
    val accentColor by viewModel.accentColor.collectAsState()
    val primaryTextColor by viewModel.primaryTextColor.collectAsState()
    val showShadows by viewModel.showShadows.collectAsState()
    val shadowSettings = LocalShadowSettings.current
    val shadow = primaryTextColor.getShadow(showShadows, shadowSettings.shadowColorOverride)
    val savedYRatio by viewModel.sideAlphabetButtonYRatio.collectAsState()
    var localYRatio by remember(savedYRatio) { mutableStateOf(savedYRatio) }

    var isLayoutSwapped by remember { mutableStateOf(false) }

    val alphabet = remember { listOf('#') + ('A'..'Z').toList() }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    val currentSelectedLetter by rememberUpdatedState(selectedLetter)
    val currentOnLetterSelected by rememberUpdatedState { char: Char ->
        if (char != currentSelectedLetter) {
            viewModel.setSelectedLetter(char)
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    val effectiveLettersOnLeft = if (handSide == HandSide.LEFT) !isLayoutSwapped else isLayoutSwapped

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        val totalWidth = maxWidth
        val totalHeight = maxHeight

        val swapIconWidth = 36.dp
        val fixedAlphabetWidth = totalWidth * 0.4045f

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = if (handSide == HandSide.LEFT) swapIconWidth else 0.dp,
                        end = if (handSide == HandSide.RIGHT) swapIconWidth else 0.dp
                    )
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    if (effectiveLettersOnLeft) {
                        Box(
                            modifier = Modifier
                                .width(fixedAlphabetWidth)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            SideAlphabetGrid(
                                alphabet = alphabet,
                                selectedLetter = selectedLetter,
                                accentColor = accentColor,
                                primaryTextColor = primaryTextColor,
                                showShadows = showShadows,
                                onLetterSelected = currentOnLetterSelected,
                                maxGridHeight = totalHeight * 0.50f
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            SideAppListContent(
                                apps = filteredApps,
                                handSide = handSide,
                                primaryTextColor = primaryTextColor,
                                showShadows = showShadows,
                                onAppClick = onAppClick,
                                onAppLongClick = onAppLongClick
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            SideAppListContent(
                                apps = filteredApps,
                                handSide = handSide,
                                primaryTextColor = primaryTextColor,
                                showShadows = showShadows,
                                onAppClick = onAppClick,
                                onAppLongClick = onAppLongClick
                            )
                        }

                        Box(
                            modifier = Modifier
                                .width(fixedAlphabetWidth)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            SideAlphabetGrid(
                                alphabet = alphabet,
                                selectedLetter = selectedLetter,
                                accentColor = accentColor,
                                primaryTextColor = primaryTextColor,
                                showShadows = showShadows,
                                onLetterSelected = currentOnLetterSelected,
                                maxGridHeight = totalHeight * 0.50f
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = if (handSide == HandSide.LEFT) Arrangement.Start else Arrangement.End
                ) {
                    IconButton(onClick = { viewModel.toggleTextSearchMode() }) {
                        Text(
                            "⌨",
                            color = accentColor.color.copy(alpha = 0.8f),
                            fontSize = 32.sp,
                            style = MaterialTheme.typography.bodyLarge.copy(shadow = shadow)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(if (handSide == HandSide.LEFT) Alignment.BottomStart else Alignment.BottomEnd)
                    .padding(bottom = totalHeight * localYRatio)
            ) {
                SwapSemiCircleButton(
                    handSide = handSide,
                    accentColor = accentColor,
                    primaryTextColor = primaryTextColor,
                    showShadows = showShadows,
                    onClick = {
                        isLayoutSwapped = !isLayoutSwapped
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onVerticalDrag = { deltaPx ->
                        val totalHeightPx = with(density) { totalHeight.toPx() }
                        if (totalHeightPx > 0f) {
                            val deltaRatio = -deltaPx / totalHeightPx
                            localYRatio = (localYRatio + deltaRatio).coerceIn(0.05f, 0.85f)
                        }
                    },
                    onDragEnd = {
                        viewModel.setSideAlphabetButtonYRatio(localYRatio)
                    }
                )
            }
        }
    }
}

/**
 * Floating semi-circle button for swapping grid/list sides and adjusting vertical offset.
 */
@Composable
private fun SwapSemiCircleButton(
    handSide: HandSide,
    accentColor: AccentColor,
    primaryTextColor: PrimaryTextColor,
    showShadows: Boolean,
    onClick: () -> Unit,
    onVerticalDrag: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val shadow = primaryTextColor.getShadow(showShadows, LocalShadowSettings.current.shadowColorOverride)
    val shape = if (handSide == HandSide.LEFT) {
        RoundedCornerShape(topEnd = 27.dp, bottomEnd = 27.dp, topStart = 0.dp, bottomStart = 0.dp)
    } else {
        RoundedCornerShape(topStart = 27.dp, bottomStart = 27.dp, topEnd = 0.dp, bottomEnd = 0.dp)
    }

    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnVerticalDrag by rememberUpdatedState(onVerticalDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    var isBeingDragged by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .width(36.dp)
            .height(54.dp)
            .clip(shape)
            .background(
                if (isBeingDragged) {
                    accentColor.color.copy(alpha = 0.28f)
                } else {
                    primaryTextColor.color.copy(alpha = 0.14f)
                }
            )
            .border(
                1.dp,
                if (isBeingDragged) {
                    accentColor.color.copy(alpha = 0.6f)
                } else {
                    primaryTextColor.color.copy(alpha = 0.22f)
                },
                shape
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var isDrag = false
                    var totalDragY = 0f

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            if (!isDrag) {
                                currentOnClick()
                            } else {
                                isBeingDragged = false
                                currentOnDragEnd()
                            }
                            break
                        }

                        val deltaY = change.positionChange().y
                        totalDragY += deltaY

                        if (!isDrag && kotlin.math.abs(totalDragY) > viewConfiguration.touchSlop) {
                            isDrag = true
                            isBeingDragged = true
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }

                        if (isDrag) {
                            change.consume()
                            currentOnVerticalDrag(deltaY)
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "⇆",
            color = accentColor.color,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium.copy(shadow = shadow)
        )
    }
}

/**
 * Grid rendering 27 characters (A-Z, #) in a 4-column layout occupying up to 50% screen height.
 * Supports direct touch selection and drag scrubbing across letter tiles.
 */
@Composable
private fun SideAlphabetGrid(
    alphabet: List<Char>,
    selectedLetter: Char,
    accentColor: AccentColor,
    primaryTextColor: PrimaryTextColor,
    showShadows: Boolean,
    onLetterSelected: (Char) -> Unit,
    maxGridHeight: androidx.compose.ui.unit.Dp
) {
    val cols = 4
    val rows = 7
    var gridBoundsSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }

    fun processTouchOffset(offset: Offset) {
        if (gridBoundsSize.width > 0f && gridBoundsSize.height > 0f) {
            val cellW = gridBoundsSize.width / cols
            val cellH = gridBoundsSize.height / rows
            val c = (offset.x / cellW).toInt().coerceIn(0, cols - 1)
            val r = (offset.y / cellH).toInt().coerceIn(0, rows - 1)
            val index = r * cols + c
            if (index in alphabet.indices) {
                onLetterSelected(alphabet[index])
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxGridHeight)
            .padding(bottom = 8.dp, start = 4.dp, end = 4.dp)
            .onGloballyPositioned { gridBoundsSize = it.size.toSize() }
            .pointerInput(alphabet) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    processTouchOffset(down.position)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        processTouchOffset(change.position)
                    }
                }
            }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (r in 0 until rows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (c in 0 until cols) {
                        val index = r * cols + c
                        if (index in alphabet.indices) {
                            val char = alphabet[index]
                            val isSelected = char == selectedLetter
                            LetterTile(
                                char = char,
                                isSelected = isSelected,
                                accentColor = accentColor,
                                primaryTextColor = primaryTextColor,
                                showShadows = showShadows,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Individual letter tile component inside the side alphabet grid.
 */
@Composable
private fun LetterTile(
    char: Char,
    isSelected: Boolean,
    accentColor: AccentColor,
    primaryTextColor: PrimaryTextColor,
    showShadows: Boolean,
    modifier: Modifier = Modifier
) {
    val shadow = primaryTextColor.getShadow(showShadows, LocalShadowSettings.current.shadowColorOverride)
    val tileBackground = if (isSelected) accentColor.color.copy(alpha = 0.30f) else primaryTextColor.color.copy(alpha = 0.05f)
    val tileBorderColor = if (isSelected) accentColor.color else primaryTextColor.color.copy(alpha = 0.12f)
    val textColor = if (isSelected) accentColor.color else primaryTextColor.color

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(tileBackground)
            .border(1.dp, tileBorderColor, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = char.toString(),
            color = textColor,
            fontSize = 16.sp,
            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
            style = MaterialTheme.typography.bodyMedium.copy(shadow = shadow)
        )
    }
}

/**
 * App list rendering component for the side alphabet layout.
 */
@Composable
private fun SideAppListContent(
    apps: List<AppInfo>,
    handSide: HandSide,
    primaryTextColor: PrimaryTextColor,
    showShadows: Boolean,
    onAppClick: (String) -> Unit,
    onAppLongClick: (AppInfo, Offset) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = if (handSide == HandSide.LEFT) Alignment.Start else Alignment.End
    ) {
        items(
            items = apps,
            key = { it.componentKey }
        ) { app ->
            AppListItemWithIcon(
                app = app,
                handSide = handSide,
                fontSize = 16,
                iconSize = 36,
                onClick = { onAppClick("${app.packageName}/${app.activityName}") },
                onLongClick = { offset -> onAppLongClick(app, offset) },
                primaryTextColor = primaryTextColor,
                showShadows = showShadows
            )
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}
