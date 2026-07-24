package dev.msbs.cyclauncher.ui.screens

import dev.msbs.cyclauncher.LauncherViewModel
import dev.msbs.cyclauncher.HandSide
import dev.msbs.cyclauncher.model.AppInfo
import dev.msbs.cyclauncher.ui.theme.AccentColor
import dev.msbs.cyclauncher.ui.theme.PrimaryTextColor
import dev.msbs.cyclauncher.ui.components.AppListItem
import dev.msbs.cyclauncher.ui.components.RectangularAlphabetWheel

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

/**
 * The search screen Composable. Automatically decides between the alphabet wheel search
 * or the keyboard-based text search interface depending on the view model state.
 *
 * @param viewModel The view model supplying state data.
 * @param onAppClick Callback when an application is clicked/opened.
 * @param onAppLongClick Callback when an application is long-pressed (provides coordinates).
 */
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

/**
 * Layout presenting the rectangular alphabet wheel search mechanism.
 * Uses vertical drag gestures to scroll through letters and display filtered apps.
 *
 * @param viewModel The view model supplying state data.
 * @param handSide User preferred hand side layout.
 * @param onAppClick Callback when an application is clicked.
 * @param onAppLongClick Callback when an application is long-pressed.
 */
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
    val primaryTextColor by viewModel.primaryTextColor.collectAsState()
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
        // Upper section containing the app list on one side and the drag-scroll interception area on the other side.
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val scrollModifier = Modifier
                .fillMaxHeight()
                .weight(1f)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                scrollOffset.snapTo(scrollOffset.value - (dragAmount / 100f))
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
                    AppListContent(filteredApps, listAlignment, primaryTextColor, showShadows, onAppClick, onAppLongClick)
                }
            } else {
                Box(modifier = Modifier.weight(1f)) {
                    AppListContent(filteredApps, listAlignment, primaryTextColor, showShadows, onAppClick, onAppLongClick)
                }
                Box(modifier = scrollModifier)
            }
        }

        // The custom rectangular alphabet wheel component.
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
                accentColor = accentColor,
                primaryTextColor = primaryTextColor,
                showShadows = showShadows
            )
        }

        // Bottom toggle bar allowing users to switch to the keyboard search interface.
        SearchToggleBar(handSide, accentColor, primaryTextColor, showShadows) { viewModel.toggleTextSearchMode() }
    }
}

/**
 * Renders a column list of application items matching the selected search criteria.
 * Limits the list to the top 15 results to optimize rendering performance.
 *
 * @param apps Filtered list of applications.
 * @param alignment Text alignment configuration.
 * @param showShadows Whether to apply drop shadows to item texts.
 * @param onAppClick Callback when an app item is clicked.
 * @param onAppLongClick Callback when an app item is long-pressed.
 */
@Composable
private fun AppListContent(
    apps: List<AppInfo>,
    alignment: TextAlign,
    primaryTextColor: PrimaryTextColor,
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
                primaryTextColor = primaryTextColor,
                showShadows = showShadows
            )
        }
    }
}

/**
 * Bottom toggle bar that displays a button to switch between the rectangular
 * alphabet wheel search mode and the full text search interface.
 *
 * @param handSide Layout orientation side (left/right hand side alignment).
 * @param accentColor Theme accent color.
 * @param primaryTextColor Primary text color option.
 * @param showShadows Whether to apply drop shadows.
 * @param onToggle Callback triggered when clicking the search mode toggle button.
 */
@Composable
private fun SearchToggleBar(
    handSide: HandSide, 
    accentColor: AccentColor,
    primaryTextColor: PrimaryTextColor,
    showShadows: Boolean,
    onToggle: () -> Unit
) {
    val shadow = primaryTextColor.getShadow(showShadows)

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
