package dev.msbs.cyclauncher

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SearchScreen(
    viewModel: LauncherViewModel,
    onAppClick: (String) -> Unit,
    onAppLongClick: (AppInfo, Offset) -> Unit
) {
    val isTextSearchMode by viewModel.isTextSearchMode.collectAsState()
    val handSide by viewModel.handSide.collectAsState()

    if (isTextSearchMode) {
        TextSearchInterface(
            viewModel = viewModel,
            onAppClick = onAppClick,
            onAppLongClick = onAppLongClick
        )
    } else {
        WheelSearchLayout(
            viewModel = viewModel,
            handSide = handSide,
            onAppClick = onAppClick,
            onAppLongClick = onAppLongClick
        )
    }
}

@Composable
fun WheelSearchLayout(
    viewModel: LauncherViewModel,
    handSide: HandSide,
    onAppClick: (String) -> Unit,
    onAppLongClick: (AppInfo, Offset) -> Unit
) {
    val filteredApps by viewModel.filteredApps.collectAsState()
    val listAlignment by viewModel.searchListAlignment.collectAsState()
    val accentColor by viewModel.accentColor.collectAsState()
    val showShadows by viewModel.showShadows.collectAsState()
    val scope = rememberCoroutineScope()
    val scrollOffset = remember { Animatable(0f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Upper section with App List and Side Scroll Area
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val scrollModifier = Modifier
                .fillMaxHeight()
                .weight(1f)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                scrollOffset.snapTo(scrollOffset.value + dragAmount / 35f)
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
                }

            if (listAlignment == TextAlign.End) {
                Box(modifier = scrollModifier)
                Box(modifier = Modifier.weight(1f)) {
                    AppListContent(filteredApps, listAlignment, showShadows, onAppClick, onAppLongClick)
                }
            } else {
                Box(modifier = Modifier.weight(1f)) {
                    AppListContent(filteredApps, listAlignment, showShadows, onAppClick, onAppLongClick)
                }
                Box(modifier = scrollModifier)
            }
        }

        // Alphabet Wheel
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            RectangularAlphabetWheel(
                scrollOffset = scrollOffset,
                onLetterSelected = { viewModel.setSelectedLetter(it) },
                apps = filteredApps,
                onAppClick = onAppClick,
                onAppLongClick = { componentKey, offset -> 
                    filteredApps.find { "${it.packageName}/${it.activityName}" == componentKey }?.let { app ->
                        onAppLongClick(app, offset)
                    }
                },
                accentColor = accentColor
            )
        }

        // Bottom Bar
        SearchToggleBar(handSide, accentColor, showShadows) { viewModel.toggleTextSearchMode() }
    }
}

@Composable
private fun AppListContent(
    apps: List<AppInfo>,
    alignment: TextAlign,
    showShadows: Boolean,
    onAppClick: (String) -> Unit,
    onAppLongClick: (AppInfo, Offset) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxHeight().padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = if (alignment == TextAlign.Start) Alignment.Start else Alignment.End
    ) {
        apps.take(15).forEach { app ->
            AppListItem(
                app = app, 
                onClick = { onAppClick("${app.packageName}/${app.activityName}") },
                onLongClick = { offset -> onAppLongClick(app, offset) },
                textAlign = alignment,
                showShadows = showShadows
            )
        }
    }
}

@Composable
private fun SearchToggleBar(
    handSide: HandSide, 
    accentColor: AccentColor,
    showShadows: Boolean,
    onToggle: () -> Unit
) {
    val shadow = if (showShadows) {
        Shadow(
            color = Color.Black.copy(alpha = 0.6f),
            offset = Offset(2f, 2f),
            blurRadius = 4f
        )
    } else null

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = if (handSide == HandSide.LEFT) Arrangement.Start else Arrangement.End
    ) {
        IconButton(onClick = onToggle) {
            Text(
                "⌨", 
                color = accentColor.color.copy(alpha = 0.8f), 
                fontSize = 32.sp,
                style = MaterialTheme.typography.bodyLarge.copy(shadow = shadow)
            )
        }
    }
}
