package dev.msbs.cyclauncher.widget

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.util.SizeF
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlin.math.max

/**
 * Shared utility for allocating, binding, and configuring Android AppWidgets across screens.
 */
object WidgetBindHelper {

    private const val HIGHLIGHT_HAND_PADDING_DP = 56
    private const val HIGHLIGHT_CARD_PADDING_DP = 8

    /**
     * Computes the actual inner content width in DP for a Highlight screen widget card,
     * matching HighlightScreen's 56dp hand-side inset and 8dp card horizontal padding.
     */
    fun computeHighlightWidgetWidthDp(screenWidthDp: Int, widthFraction: Float = 1.0f): Int {
        val availableWidthDp = (screenWidthDp - HIGHLIGHT_HAND_PADDING_DP).coerceAtLeast(40)
        return ((availableWidthDp * widthFraction.coerceIn(0.2f, 1.0f)).toInt() - HIGHLIGHT_CARD_PADDING_DP)
            .coerceAtLeast(40)
    }

    /**
     * Calculates a native optimal height in DP for an AppWidget based on target cells or minHeight.
     */
    fun calculateOptimalWidgetHeight(info: AppWidgetProviderInfo?, density: Float): Int {
        if (info == null) return 150
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.targetCellHeight > 0) {
            return (info.targetCellHeight * 74).coerceIn(70, 420)
        }
        val minHeightDp = if (info.minHeight > 0) (info.minHeight / density).toInt() else 140
        val estimatedCells = max(1, (minHeightDp + 15) / 65)
        return (estimatedCells * 74).coerceIn(minHeightDp.coerceIn(70, 420), 420)
    }

    /**
     * Builds the default AppWidget options bundle for full-width Highlight screen widgets.
     */
    fun createHighlightWidgetOptions(context: Context, providerInfo: AppWidgetProviderInfo): Bundle {
        val displayDensity = context.resources.displayMetrics.density
        val optimalHeight = calculateOptimalWidgetHeight(providerInfo, displayDensity)
        val screenWidthDp = context.resources.configuration.screenWidthDp.takeIf { it > 0 }
            ?: (context.resources.displayMetrics.widthPixels / displayDensity).toInt()
        val targetWidthDp = computeHighlightWidgetWidthDp(screenWidthDp, 1.0f)
        return Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, targetWidthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, targetWidthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, optimalHeight)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, optimalHeight)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val sizes = arrayListOf(SizeF(targetWidthDp.toFloat(), optimalHeight.toFloat()))
                putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, sizes)
            }
        }
    }

    /**
     * Builds the default AppWidget options bundle for compact Search screen widgets.
     */
    fun createSearchWidgetOptions(context: Context): Bundle {
        val displayDensity = context.resources.displayMetrics.density
        val screenWidthDp = context.resources.configuration.screenWidthDp.takeIf { it > 0 }
            ?: (context.resources.displayMetrics.widthPixels / displayDensity).toInt()
        val targetWidthDp = (screenWidthDp * 0.5f).toInt().coerceAtLeast(40)
        return Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, targetWidthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, targetWidthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 140)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 300)
        }
    }

    fun safeProfile(providerInfo: AppWidgetProviderInfo): android.os.UserHandle {
        return try {
            providerInfo.profile ?: Process.myUserHandle()
        } catch (_: Throwable) {
            Process.myUserHandle()
        }
    }
}

/**
 * Remembers a stateful widget allocation, permission-binding, and configuration handler.
 *
 * @param host The [AppWidgetHost] used to allocate and clean up widget IDs.
 * @param manager The [AppWidgetManager] instance.
 * @param onConfigureWidget Optional host callback to launch a widget's configuration activity.
 * @param optionsProvider Lambda producing the [Bundle] of widget size options for a given provider.
 * @param onWidgetBound Callback invoked once the widget is bound and (if required) configured.
 */
@Composable
fun <T> rememberWidgetBindHandler(
    host: AppWidgetHost?,
    manager: AppWidgetManager,
    onConfigureWidget: ((widgetId: Int, isReconfigure: Boolean, options: Bundle?, callback: (Boolean) -> Unit) -> Unit)?,
    optionsProvider: (Context, AppWidgetProviderInfo) -> Bundle,
    onWidgetBound: (widgetId: Int, providerInfo: AppWidgetProviderInfo, target: T) -> Unit
): (AppWidgetProviderInfo, T) -> Unit {
    val context = LocalContext.current
    val currentHost by rememberUpdatedState(host)
    val currentOnConfigure by rememberUpdatedState(onConfigureWidget)
    val currentOptionsProvider by rememberUpdatedState(optionsProvider)
    val currentOnWidgetBound by rememberUpdatedState(onWidgetBound)

    var pendingBindWidgetId by remember { mutableIntStateOf(AppWidgetManager.INVALID_APPWIDGET_ID) }
    var pendingBindProvider by remember { mutableStateOf<AppWidgetProviderInfo?>(null) }
    var pendingBindTarget by remember { mutableStateOf<T?>(null) }

    DisposableEffect(host) {
        onDispose {
            val leakedId = pendingBindWidgetId
            if (leakedId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                pendingBindWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
                pendingBindProvider = null
                pendingBindTarget = null
                try {
                    host?.deleteAppWidgetId(leakedId)
                } catch (_: Exception) {}
            }
        }
    }

    val checkConfigureAndCommit: (Int, AppWidgetProviderInfo, Bundle?, T) -> Unit = { widgetId, providerInfo, optionsBundle, target ->
        val configLauncher = currentOnConfigure
        if (providerInfo.configure != null && configLauncher != null) {
            configLauncher(widgetId, false, optionsBundle) { success ->
                if (success) {
                    currentOnWidgetBound(widgetId, providerInfo, target)
                } else {
                    try {
                        currentHost?.deleteAppWidgetId(widgetId)
                    } catch (_: Exception) {}
                }
            }
        } else {
            currentOnWidgetBound(widgetId, providerInfo, target)
        }
    }

    val bindWidgetLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val widgetId = pendingBindWidgetId
        val target = pendingBindTarget
        val fallbackProvider = pendingBindProvider
        pendingBindWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        pendingBindProvider = null
        pendingBindTarget = null

        if (result.resultCode == Activity.RESULT_OK && widgetId != AppWidgetManager.INVALID_APPWIDGET_ID && target != null) {
            val provider = try {
                manager.getAppWidgetInfo(widgetId)
            } catch (_: Exception) {
                null
            } ?: fallbackProvider
            if (provider != null) {
                val options = currentOptionsProvider(context, provider)
                try {
                    manager.updateAppWidgetOptions(widgetId, options)
                } catch (_: Exception) {}
                checkConfigureAndCommit(widgetId, provider, options, target)
            } else {
                try {
                    currentHost?.deleteAppWidgetId(widgetId)
                } catch (_: Exception) {}
            }
        } else if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            try {
                currentHost?.deleteAppWidgetId(widgetId)
            } catch (_: Exception) {}
        }
    }

    return { providerInfo, target ->
        val activeHost = currentHost
        if (activeHost != null) {
            val previousPending = pendingBindWidgetId
            if (previousPending != AppWidgetManager.INVALID_APPWIDGET_ID) {
                pendingBindWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
                pendingBindProvider = null
                pendingBindTarget = null
                try {
                    activeHost.deleteAppWidgetId(previousPending)
                } catch (_: Exception) {}
            }

            val newWidgetId = try {
                activeHost.allocateAppWidgetId()
            } catch (_: Exception) {
                AppWidgetManager.INVALID_APPWIDGET_ID
            }

            if (newWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val options = currentOptionsProvider(context, providerInfo)
                val profile = WidgetBindHelper.safeProfile(providerInfo)
                val canBind = try {
                    manager.bindAppWidgetIdIfAllowed(newWidgetId, profile, providerInfo.provider, options)
                } catch (_: Exception) {
                    false
                }

                if (canBind) {
                    try {
                        manager.updateAppWidgetOptions(newWidgetId, options)
                    } catch (_: Exception) {}
                    val finalInfo = try {
                        manager.getAppWidgetInfo(newWidgetId)
                    } catch (_: Exception) {
                        null
                    } ?: providerInfo
                    checkConfigureAndCommit(newWidgetId, finalInfo, options, target)
                } else {
                    pendingBindWidgetId = newWidgetId
                    pendingBindProvider = providerInfo
                    pendingBindTarget = target
                    try {
                        val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, newWidgetId)
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, providerInfo.provider)
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, profile)
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, options)
                        }
                        bindWidgetLauncher.launch(bindIntent)
                    } catch (_: Exception) {
                        pendingBindWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
                        pendingBindProvider = null
                        pendingBindTarget = null
                        try {
                            activeHost.deleteAppWidgetId(newWidgetId)
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }
}
