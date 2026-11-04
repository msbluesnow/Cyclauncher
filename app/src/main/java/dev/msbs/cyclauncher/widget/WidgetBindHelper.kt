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
        return (estimatedCells * 74).coerceIn(minHeightDp.coerceAtLeast(70), 420)
    }

    /**
     * Builds the default AppWidget options bundle for full-width Highlight screen widgets.
     */
    fun createHighlightWidgetOptions(context: Context, providerInfo: AppWidgetProviderInfo): Bundle {
        val displayDensity = context.resources.displayMetrics.density
        val optimalHeight = calculateOptimalWidgetHeight(providerInfo, displayDensity)
        val screenWidthDp = (context.resources.displayMetrics.widthPixels / displayDensity).toInt()
        val targetWidthDp = (screenWidthDp * 1.0f).toInt() - 24
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
        val screenWidthDp = (context.resources.displayMetrics.widthPixels / displayDensity).toInt()
        val targetWidthDp = (screenWidthDp * 0.5f).toInt()
        return Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, targetWidthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, targetWidthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 140)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 300)
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
    val currentOnConfigure by rememberUpdatedState(onConfigureWidget)
    val currentOptionsProvider by rememberUpdatedState(optionsProvider)
    val currentOnWidgetBound by rememberUpdatedState(onWidgetBound)

    var pendingBindWidgetId by remember { mutableIntStateOf(AppWidgetManager.INVALID_APPWIDGET_ID) }
    var pendingBindProvider by remember { mutableStateOf<AppWidgetProviderInfo?>(null) }
    var pendingBindTarget by remember { mutableStateOf<T?>(null) }

    val checkConfigureAndCommit: (Int, AppWidgetProviderInfo, Bundle?, T) -> Unit = { widgetId, providerInfo, optionsBundle, target ->
        val configLauncher = currentOnConfigure
        if (providerInfo.configure != null && configLauncher != null) {
            configLauncher(widgetId, false, optionsBundle) { success ->
                if (success) {
                    currentOnWidgetBound(widgetId, providerInfo, target)
                } else {
                    try {
                        host?.deleteAppWidgetId(widgetId)
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
        if (result.resultCode == Activity.RESULT_OK && widgetId != AppWidgetManager.INVALID_APPWIDGET_ID && target != null) {
            val provider = manager.getAppWidgetInfo(widgetId) ?: pendingBindProvider
            if (provider != null) {
                val options = currentOptionsProvider(context, provider)
                checkConfigureAndCommit(widgetId, provider, options, target)
            }
        } else if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            try {
                host?.deleteAppWidgetId(widgetId)
            } catch (_: Exception) {}
        }
        pendingBindWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        pendingBindProvider = null
        pendingBindTarget = null
    }

    return { providerInfo, target ->
        if (host != null) {
            val newWidgetId = try {
                host.allocateAppWidgetId()
            } catch (_: Exception) {
                AppWidgetManager.INVALID_APPWIDGET_ID
            }

            if (newWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val options = currentOptionsProvider(context, providerInfo)
                val canBind = try {
                    val profile = providerInfo.profile ?: Process.myUserHandle()
                    manager.bindAppWidgetIdIfAllowed(newWidgetId, profile, providerInfo.provider, options)
                } catch (_: Exception) {
                    false
                }

                try {
                    manager.updateAppWidgetOptions(newWidgetId, options)
                } catch (_: Exception) {}

                if (canBind) {
                    val finalInfo = manager.getAppWidgetInfo(newWidgetId) ?: providerInfo
                    checkConfigureAndCommit(newWidgetId, finalInfo, options, target)
                } else {
                    pendingBindWidgetId = newWidgetId
                    pendingBindProvider = providerInfo
                    pendingBindTarget = target
                    try {
                        val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, newWidgetId)
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, providerInfo.provider)
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, providerInfo.profile ?: Process.myUserHandle())
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, options)
                        }
                        bindWidgetLauncher.launch(bindIntent)
                    } catch (_: Exception) {
                        checkConfigureAndCommit(newWidgetId, providerInfo, options, target)
                    }
                }
            }
        }
    }
}
