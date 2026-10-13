package dev.msbs.cyclauncher.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Background theme setting for native dialogs and custom popup action menus (DARK or LIGHT).
 */
enum class PopupTheme(val displayName: String) {
    DARK("Dark"),
    LIGHT("Light");

    /**
     * Translucent background color for floating popups and menus.
     * Guaranteed strictly pure Black or pure White with custom alpha.
     */
    fun backgroundColor(alpha: Float): Color = when (this) {
        DARK -> Color.Black.copy(alpha = alpha)
        LIGHT -> Color.White.copy(alpha = alpha)
    }

    /**
     * Dynamically reads the user-configured [LocalPopupAlpha] transparency.
     */
    val backgroundColor: Color
        @Composable
        @ReadOnlyComposable
        get() = backgroundColor(LocalPopupAlpha.current)

    /**
     * Solid background color for dialogs and nested cards.
     * Unified to strictly pure Black or pure White with custom alpha.
     */
    fun solidBackgroundColor(alpha: Float): Color = when (this) {
        DARK -> Color.Black.copy(alpha = alpha)
        LIGHT -> Color.White.copy(alpha = alpha)
    }

    /**
     * Dynamically reads the user-configured [LocalPopupAlpha] transparency.
     */
    val solidBackgroundColor: Color
        @Composable
        @ReadOnlyComposable
        get() = solidBackgroundColor(LocalPopupAlpha.current)

    /**
     * Primary text and icon color inside the popup.
     */
    val contentColor: Color
        get() = when (this) {
            DARK -> Color.White
            LIGHT -> Color.Black
        }

    /**
     * Secondary / subtitle text and icon color inside the popup.
     */
    val secondaryContentColor: Color
        get() = when (this) {
            DARK -> Color.White.copy(alpha = 0.65f)
            LIGHT -> Color.Black.copy(alpha = 0.65f)
        }

    /**
     * Border stroke color for popup containers.
     */
    val borderColor: Color
        get() = when (this) {
            DARK -> Color.White.copy(alpha = 0.15f)
            LIGHT -> Color.Black.copy(alpha = 0.15f)
        }

    /**
     * Divider color inside popup menus.
     */
    val dividerColor: Color
        get() = when (this) {
            DARK -> Color.White.copy(alpha = 0.08f)
            LIGHT -> Color.Black.copy(alpha = 0.08f)
        }

    companion object {
        fun fromName(name: String): PopupTheme {
            return try {
                valueOf(name)
            } catch (_: Exception) {
                DARK
            }
        }
    }
}

val LocalPopupTheme = compositionLocalOf { PopupTheme.DARK }

