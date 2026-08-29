package dev.msbs.cyclauncher.ui.screens

import dev.msbs.cyclauncher.LauncherViewModel
import dev.msbs.cyclauncher.HandSide
import dev.msbs.cyclauncher.model.AppInfo
import dev.msbs.cyclauncher.ui.theme.AccentColor
import dev.msbs.cyclauncher.ui.theme.PrimaryTextColor
import dev.msbs.cyclauncher.ui.theme.LocalShadowSettings
import dev.msbs.cyclauncher.ui.theme.LocalAnimationsEnabled
import dev.msbs.cyclauncher.ui.components.AppListItem
import dev.msbs.cyclauncher.ui.components.RectangularAlphabetWheel

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.msbs.cyclauncher.ui.components.alphabetWheelDragGesture
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

import androidx.activity.compose.BackHandler
import dev.msbs.cyclauncher.SearchMethod
import dev.msbs.cyclauncher.ui.components.SideAlphabetSearchLayout

/**
 * Search screen supporting alphabet wheel, side alphabet grid, or text search mode.
 */
@Composable
fun SearchScreen(
    viewModel: LauncherViewModel,
    enabled: Boolean = true,
    onBackToMain: () -> Unit = {},
    onAppClick: (String) -> Unit,
    onAppLongClick: (AppInfo, Offset) -> Unit
) {
    val isTextSearchMode by viewModel.isTextSearchMode.collectAsState()

    BackHandler(enabled = enabled) {
        if (isTextSearchMode) {
            viewModel.toggleTextSearchMode()
        } else {
            onBackToMain()
        }
    }

    val searchMethod by viewModel.searchMethod.collectAsState()
    val handSide by viewModel.handSide.collectAsState()

    when (searchMethod) {
        SearchMethod.TEXT -> {
            TextSearchInterface(
                viewModel = viewModel,
                onAppClick = onAppClick,
                onAppLongClick = onAppLongClick
            )
        }
        SearchMethod.SIDE_ALPHABET -> {
            SideAlphabetSearchLayout(
                viewModel = viewModel,
                handSide = handSide,
                onAppClick = onAppClick,
                onAppLongClick = onAppLongClick
            )
        }
        SearchMethod.WHEEL -> {
            WheelSearchLayout(
                viewModel = viewModel,
                handSide = handSide,
                onAppClick = onAppClick,
                onAppLongClick = onAppLongClick
            )
        }
    }
}

/**
 * Layout displaying apps filtered by the rectangular alphabet wheel.
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
    val scrollOffset = remember { Animatable(0f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val density = LocalDensity.current
        val configuration = LocalConfiguration.current
        val scaleFactor = ((configuration.screenWidthDp.dp / 360.dp).coerceIn(0.7f, 1.2f)) * 0.93f
        val stepSize = 34.dp * scaleFactor

        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val animationsEnabled = LocalAnimationsEnabled.current
            val scrollModifier = Modifier
                .fillMaxHeight()
                .weight(1f)
                .alphabetWheelDragGesture(scrollOffset, density, stepSize, animationsEnabled)

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

        SearchToggleBar(handSide, accentColor, primaryTextColor, showShadows) { viewModel.toggleTextSearchMode() }
    }
}

/**
 * Column list of apps matching the search criteria.
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
 * Bottom toggle bar for switching between wheel/grid search and keyboard text search.
 */
@Composable
private fun SearchToggleBar(
    handSide: HandSide, 
    accentColor: AccentColor,
    primaryTextColor: PrimaryTextColor,
    showShadows: Boolean,
    onToggle: () -> Unit
) {
    val shadowSettings = LocalShadowSettings.current
    val shadow = primaryTextColor.getShadow(showShadows, shadowSettings.shadowColorOverride)

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
