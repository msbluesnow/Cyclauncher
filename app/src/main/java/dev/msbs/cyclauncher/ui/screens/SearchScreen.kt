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
    SIDE_SEARCH,
    SIDE_ALPHABET_WIDGET
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
    var pendingBindWidgetId by remember { mutableIntStateOf(AppWidgetManager.INVALID_APPWIDGET_ID) }
    var pendingBindProvider by remember { mutableStateOf<AppWidgetProviderInfo?>(null) }
    var pendingBindTarget by remember { mutableStateOf(WidgetPickTarget.WHEEL_LEFT) }

    val checkConfigureAndAdd: (Int, AppWidgetProviderInfo, Bundle?, WidgetPickTarget) -> Unit = { widgetId, providerInfo, optionsBundle, target ->
        val onConfigSuccess: () -> Unit = {
            val replaceWidget: (Int?, (Int) -> Unit) -> Unit = { oldId, updateAction ->
                if (oldId != null && oldId != widgetId) {
                    try { host?.deleteAppWidgetId(oldId) } catch (_: Exception) {}
                }
                updateAction(widgetId)
            }
            when (target) {
                WidgetPickTarget.WHEEL_LEFT -> replaceWidget(searchWidgetsConfig.leftWidgetId) { viewModel.setSearchWidget(true, it) }
                WidgetPickTarget.WHEEL_RIGHT -> replaceWidget(searchWidgetsConfig.rightWidgetId) { viewModel.setSearchWidget(false, it) }
                WidgetPickTarget.SIDE_SEARCH -> replaceWidget(viewModel.sideSearchWidgetId.value) { viewModel.setSideSearchWidget(it) }
                WidgetPickTarget.SIDE_ALPHABET_WIDGET -> replaceWidget(viewModel.sideAlphabetWidgetId.value) { viewModel.setSideAlphabetWidget(it) }
            }
        }

        if (providerInfo.configure != null && onConfigureWidget != null) {
            onConfigureWidget(widgetId, false, optionsBundle) { success ->
                if (success) {
                    onConfigSuccess()
                } else {
                    try { host?.deleteAppWidgetId(widgetId) } catch (_: Exception) {}
                }
            }
        } else {
            onConfigSuccess()
        }
    }

    val bindWidgetLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && pendingBindWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            val provider = manager.getAppWidgetInfo(pendingBindWidgetId) ?: pendingBindProvider
            if (provider != null) {
                val options = createWidgetOptions(context)
                checkConfigureAndAdd(pendingBindWidgetId, provider, options, pendingBindTarget)
            }
        } else {
            if (pendingBindWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                try { host?.deleteAppWidgetId(pendingBindWidgetId) } catch (_: Exception) {}
            }
        }
        pendingBindWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        pendingBindProvider = null
    }

    val onSelectWidgetFromPicker: (AppWidgetProviderInfo, WidgetPickTarget) -> Unit = { providerInfo, target ->
        if (host != null) {
            val newWidgetId = host.allocateAppWidgetId()
            val options = createWidgetOptions(context)

            val canBind = try {
                val profile = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    providerInfo.profile ?: android.os.Process.myUserHandle()
                } else {
                    null
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && profile != null) {
                    manager.bindAppWidgetIdIfAllowed(newWidgetId, profile, providerInfo.provider, options)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    manager.bindAppWidgetIdIfAllowed(newWidgetId, providerInfo.provider, options)
                } else {
                    manager.bindAppWidgetIdIfAllowed(newWidgetId, providerInfo.provider)
                }
            } catch (_: Exception) {
                false
            }

            try {
                manager.updateAppWidgetOptions(newWidgetId, options)
            } catch (_: Exception) {}

            if (canBind) {
                val finalInfo = manager.getAppWidgetInfo(newWidgetId) ?: providerInfo
                checkConfigureAndAdd(newWidgetId, finalInfo, options, target)
            } else {
                pendingBindWidgetId = newWidgetId
                pendingBindProvider = providerInfo
                pendingBindTarget = target
                try {
                    val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, newWidgetId)
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, providerInfo.provider)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, providerInfo.profile ?: android.os.Process.myUserHandle())
                        }
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, options)
                    }
                    bindWidgetLauncher.launch(bindIntent)
                } catch (_: Exception) {
                    checkConfigureAndAdd(newWidgetId, providerInfo, options, target)
                }
            }
        }
    }

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
        viewModel.logSearchLaunch(appKey)
        viewModel.resetSearchFilters()
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
                onPickSideWidget = { widgetPickTarget = WidgetPickTarget.SIDE_SEARCH },
                onPickSideAlphabetWidget = { widgetPickTarget = WidgetPickTarget.SIDE_ALPHABET_WIDGET },
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

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (selectedLetter == null) {
                if (showSearchWidgets) {
                    widgetCompartment?.invoke(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    val animationsEnabled = LocalAnimationsEnabled.current
                    val scrollModifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                        .alphabetWheelDragGesture(scrollOffset, density, stepSize, animationsEnabled)

                    if (listAlignment == TextAlign.End) {
                        Box(modifier = scrollModifier)
                        Box(modifier = Modifier.weight(1f)) {
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
                    } else {
                        Box(modifier = Modifier.weight(1f)) {
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
                        Box(modifier = scrollModifier)
                    }
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
                    filteredApps.find { "${it.packageName}/${it.activityName}" == componentKey }?.let { app ->
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

private fun createWidgetOptions(context: Context): Bundle {
    val displayDensity = context.resources.displayMetrics.density
    val screenWidthDp = (context.resources.displayMetrics.widthPixels / displayDensity).toInt()
    val targetWidthDp = (screenWidthDp * 0.5f).toInt()
    return Bundle().apply {
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, targetWidthDp)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, targetWidthDp)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 140)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 300)
    }
}
