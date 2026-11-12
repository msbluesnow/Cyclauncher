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
import androidx.compose.foundation.layout.*
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.msbs.cyclauncher.ui.components.ALPHABET_WHEEL_CHARS
import dev.msbs.cyclauncher.ui.components.alphabetWheelDragGesture
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

import androidx.activity.compose.BackHandler
import dev.msbs.cyclauncher.SearchMethod
import dev.msbs.cyclauncher.ui.components.SideAlphabetSearchLayout

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import dev.msbs.cyclauncher.ui.components.CustomWidgetPickerSheet
import dev.msbs.cyclauncher.ui.components.SearchWidgetCompartment

enum class WidgetPickTarget {
    WHEEL_LEFT,
    WHEEL_RIGHT,
    SIDE_SEARCH_TOP,
    SIDE_SEARCH_BOTTOM,
    SIDE_ALPHABET_TOP,
    SIDE_ALPHABET_BOTTOM
}

/**
 * Search screen supporting alphabet wheel, side alphabet grid, or text search mode.
 */
@Composable
fun SearchScreen(
    viewModel: LauncherViewModel,
    enabled: Boolean = true,
    appWidgetHost: AppWidgetHost? = null,
    appWidgetManager: AppWidgetManager? = null,
    onConfigureWidget: ((widgetId: Int, isReconfigure: Boolean, options: Bundle?, callback: (Boolean) -> Unit) -> Unit)? = null,
    onBackToMain: () -> Unit = {},
    onAppClick: (String) -> Unit,
    onAppLongClick: (AppInfo, Offset) -> Unit
) {
    val context = LocalContext.current
    val isTextSearchMode by viewModel.isTextSearchMode.collectAsState()
    val selectedLetter by viewModel.selectedLetter.collectAsState()
    val selectedColor by viewModel.selectedColor.collectAsState()
    val searchMethod by viewModel.searchMethod.collectAsState()
    val handSide by viewModel.handSide.collectAsState()
    val searchWidgetsConfig by viewModel.searchWidgetsConfig.collectAsState()

    BackHandler(enabled = enabled) {
        if (isTextSearchMode) {
            viewModel.toggleTextSearchMode()
        } else {
            onBackToMain()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.resetSearchFilters()
    }

    val host = appWidgetHost
    val manager = appWidgetManager ?: remember { AppWidgetManager.getInstance(context.applicationContext) }

    var widgetPickTarget by remember { mutableStateOf<WidgetPickTarget?>(null) }

    val onSelectWidgetFromPicker = dev.msbs.cyclauncher.widget.rememberWidgetBindHandler<WidgetPickTarget>(
        host = host,
        manager = manager,
        onConfigureWidget = onConfigureWidget,
        optionsProvider = { ctx, _ ->
            dev.msbs.cyclauncher.widget.WidgetBindHelper.createSearchWidgetOptions(ctx)
        },
        onWidgetBound = { widgetId, _, target ->
            val replaceWidget: (Int?, (Int) -> Unit) -> Unit = { oldId, updateAction ->
                if (oldId != null && oldId != widgetId) {
                    try { host?.deleteAppWidgetId(oldId) } catch (_: Exception) {}
                }
                updateAction(widgetId)
            }
            val sideSearchConfig = viewModel.sideSearchWidgetsConfig.value
            val sideAlphabetConfig = viewModel.sideAlphabetWidgetsConfig.value
            when (target) {
                WidgetPickTarget.WHEEL_LEFT -> replaceWidget(searchWidgetsConfig.leftWidgetId) { viewModel.setSearchWidget(true, it) }
                WidgetPickTarget.WHEEL_RIGHT -> replaceWidget(searchWidgetsConfig.rightWidgetId) { viewModel.setSearchWidget(false, it) }
                WidgetPickTarget.SIDE_SEARCH_TOP -> replaceWidget(sideSearchConfig.topWidgetId) { viewModel.setSideSearchWidget(true, it) }
                WidgetPickTarget.SIDE_SEARCH_BOTTOM -> replaceWidget(sideSearchConfig.bottomWidgetId) { viewModel.setSideSearchWidget(false, it) }
                WidgetPickTarget.SIDE_ALPHABET_TOP -> replaceWidget(sideAlphabetConfig.topWidgetId) { viewModel.setSideAlphabetWidget(true, it) }
                WidgetPickTarget.SIDE_ALPHABET_BOTTOM -> replaceWidget(sideAlphabetConfig.bottomWidgetId) { viewModel.setSideAlphabetWidget(false, it) }
            }
        }
    )

    val widgetCompartment: @Composable (Modifier) -> Unit = { compartmentModifier ->
        SearchWidgetCompartment(
            viewModel = viewModel,
            appWidgetHost = host,
            appWidgetManager = manager,
            onPickWidget = { isLeft ->
                widgetPickTarget = if (isLeft) WidgetPickTarget.WHEEL_LEFT else WidgetPickTarget.WHEEL_RIGHT
            },
            modifier = compartmentModifier
        )
    }

    val onSearchAppClick: (String) -> Unit = { appKey ->
        onAppClick(appKey)
    }

    when (searchMethod) {
        SearchMethod.TEXT -> {
            TextSearchInterface(
                viewModel = viewModel,
                onAppClick = onSearchAppClick,
                onAppLongClick = onAppLongClick
            )
        }
        SearchMethod.SIDE_ALPHABET -> {
            SideAlphabetSearchLayout(
                viewModel = viewModel,
                handSide = handSide,
                appWidgetHost = host,
                appWidgetManager = manager,
                onPickSideWidget = { isTop ->
                    widgetPickTarget = if (isTop) WidgetPickTarget.SIDE_SEARCH_TOP else WidgetPickTarget.SIDE_SEARCH_BOTTOM
                },
                onPickSideAlphabetWidget = { isTop ->
                    widgetPickTarget = if (isTop) WidgetPickTarget.SIDE_ALPHABET_TOP else WidgetPickTarget.SIDE_ALPHABET_BOTTOM
                },
                onAppClick = onSearchAppClick,
                onAppLongClick = onAppLongClick
            )
        }
        SearchMethod.WHEEL -> {
            WheelSearchLayout(
                viewModel = viewModel,
                handSide = handSide,
                widgetCompartment = widgetCompartment,
                onAppClick = onSearchAppClick,
                onAppLongClick = onAppLongClick
            )
        }
    }

    widgetPickTarget?.let { target ->
        val allApps by viewModel.apps.collectAsState()
        val accentColor by viewModel.accentColor.collectAsState()
        val primaryTextColor by viewModel.primaryTextColor.collectAsState()
        val showShadows by viewModel.showShadows.collectAsState()

        CustomWidgetPickerSheet(
            apps = allApps,
            viewModel = viewModel,
            accentColor = accentColor,
            primaryTextColor = primaryTextColor,
            showShadows = showShadows,
            onDismiss = { widgetPickTarget = null },
            onSelectWidget = { providerInfo ->
                widgetPickTarget = null
                onSelectWidgetFromPicker(providerInfo, target)
            }
        )
    }
}

/**
 * Layout displaying apps filtered by the rectangular alphabet wheel.
 */
@Composable
fun WheelSearchLayout(
    viewModel: LauncherViewModel,
    handSide: HandSide,
    widgetCompartment: (@Composable (Modifier) -> Unit)? = null,
    onAppClick: (String) -> Unit,
    onAppLongClick: (AppInfo, Offset) -> Unit
) {
    val filteredApps by viewModel.filteredApps.collectAsState()
    val selectedLetter by viewModel.selectedLetter.collectAsState()
    val listAlignment by viewModel.searchListAlignment.collectAsState()
    val accentColor by viewModel.accentColor.collectAsState()
    val primaryTextColor by viewModel.primaryTextColor.collectAsState()
    val showShadows by viewModel.showShadows.collectAsState()
    val showSearchWidgets by viewModel.showSearchWidgets.collectAsState()
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
        val animationsEnabled = LocalAnimationsEnabled.current
        val haptic = LocalHapticFeedback.current

        val handleTopDragStart = {
            if (viewModel.selectedLetter.value == null) {
                val count = ALPHABET_WHEEL_CHARS.size
                val raw = ((scrollOffset.value % count) + count) % count
                val activeIndex = raw.coerceIn(0f, count - 0.0001f).roundToInt() % count
                viewModel.setSelectedLetter(ALPHABET_WHEEL_CHARS[activeIndex])
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }

        val scrollModifier = Modifier
            .fillMaxHeight()
            .weight(1f)
            .alphabetWheelDragGesture(
                scrollOffset = scrollOffset,
                density = density,
                stepSize = stepSize,
                animationsEnabled = animationsEnabled,
                onDragStart = handleTopDragStart
            )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (selectedLetter == null && showSearchWidgets) {
                widgetCompartment?.invoke(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            Row(modifier = Modifier.fillMaxSize()) {
                val appsSideModifier = if (selectedLetter == null && !showSearchWidgets) {
                    scrollModifier
                } else {
                    Modifier
                        .fillMaxHeight()
                        .weight(1f)
                }

                if (listAlignment == TextAlign.End) {
                    Box(modifier = scrollModifier)
                    Box(modifier = appsSideModifier) {
                        if (selectedLetter != null) {
                            AppListContent(
                                apps = filteredApps,
                                alignment = listAlignment,
                                primaryTextColor = primaryTextColor,
                                showShadows = showShadows,
                                onAppClick = { appKey ->
                                    viewModel.setSelectedLetter(null)
                                    onAppClick(appKey)
                                },
                                onAppLongClick = onAppLongClick
                            )
                        }
                    }
                } else {
                    Box(modifier = appsSideModifier) {
                        if (selectedLetter != null) {
                            AppListContent(
                                apps = filteredApps,
                                alignment = listAlignment,
                                primaryTextColor = primaryTextColor,
                                showShadows = showShadows,
                                onAppClick = { appKey ->
                                    viewModel.setSelectedLetter(null)
                                    onAppClick(appKey)
                                },
                                onAppLongClick = onAppLongClick
                            )
                        }
                    }
                    Box(modifier = scrollModifier)
                }
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
                onAppClick = { appKey ->
                    viewModel.setSelectedLetter(null)
                    onAppClick(appKey)
                },
                onAppLongClick = { componentKey, offset -> 
                    filteredApps.find { it.componentKey == componentKey }?.let { app ->
                        onAppLongClick(app, offset)
                    }
                },
                selectedLetter = selectedLetter,
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
                onClick = { onAppClick(app.componentKey) },
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
