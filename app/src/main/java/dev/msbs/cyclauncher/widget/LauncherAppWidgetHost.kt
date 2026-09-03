package dev.msbs.cyclauncher.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout

/**
 * Custom AppWidgetHost for Cyclauncher modeled after modern Android launcher implementations (AOSP Launcher3 / Lawnchair).
 * Automatically instantiates [LauncherAppWidgetHostView] with zeroed system padding,
 * correct touch propagation (disallow-parent-intercept), and responsive size updating.
 */
class LauncherAppWidgetHost(
    context: Context,
    hostId: Int
) : AppWidgetHost(context, hostId) {

    override fun onCreateView(
        context: Context,
        appWidgetId: Int,
        appWidget: AppWidgetProviderInfo?
    ): AppWidgetHostView {
        return LauncherAppWidgetHostView(context).apply {
            setAppWidget(appWidgetId, appWidget)
        }
    }
}

/**
 * Optimized AppWidgetHostView for launcher embedding:
 * 1. Zeroes out legacy/default framework padding so widgets fill the allocated container area without artificial margins.
 * 2. Manages touch interception: disallows parent Compose scroll/gestures on ACTION_DOWN so widgets can be scrolled/clicked properly.
 * 3. Dispatches exact options bundles via [applyWidgetSize] including API 31+ OPTION_APPWIDGET_SIZES.
 */
class LauncherAppWidgetHostView(context: Context) : AppWidgetHostView(context) {

    init {
        super.setPadding(0, 0, 0, 0)
        isFocusable = true
        isClickable = true
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        // Suppress system-injected default widget padding so widgets expand to full container width
        super.setPadding(0, 0, 0, 0)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Request parent (Compose scroll/gesture detectors) to not steal touches
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        return super.dispatchTouchEvent(ev)
    }

    /**
     * Updates widget size options both on the host view and with AppWidgetManager
     * so provider layouts re-measure and adapt responsively to user dimensions.
     */
    @Suppress("DEPRECATION")
    fun applyWidgetSize(widthDp: Int, heightDp: Int) {
        val safeWidth = widthDp.coerceAtLeast(40)
        val safeHeight = heightDp.coerceAtLeast(40)

        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, safeWidth)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, safeWidth)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, safeHeight)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, safeHeight)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val sizes = arrayListOf(
                    SizeF(safeWidth.toFloat(), safeHeight.toFloat())
                )
                putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, sizes)
            }
        }

        try {
            updateAppWidgetSize(options, safeWidth, safeHeight, safeWidth, safeHeight)
        } catch (_: Exception) {}

        try {
            AppWidgetManager.getInstance(context).updateAppWidgetOptions(appWidgetId, options)
        } catch (_: Exception) {}
    }
}
