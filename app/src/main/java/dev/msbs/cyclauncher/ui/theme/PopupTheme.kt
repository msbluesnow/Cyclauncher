package dev.msbs.cyclauncher.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Background theme setting for native dialogs and custom popup action menus (DARK or LIGHT).
 */
enum class PopupTheme(val displayName: String) {
    DARK("Dark"),
    LIGHT("Light");

    /**
     * Translucent background color for floating popups and menus.
     */
    fun backgroundColor(alpha: Float = 0.81f): Color = when (this) {
        DARK -> Color.Black.copy(alpha = alpha)
        LIGHT -> Color(0xFFF6F6F6).copy(alpha = alpha)
    }

    val backgroundColor: Color
        get() = backgroundColor(0.81f)

    /**
     * Solid background color for dialogs and nested cards.
     */
    fun solidBackgroundColor(alpha: Float = 0.81f): Color = when (this) {
        DARK -> Color(0xFF1E1E1E).copy(alpha = alpha)
        LIGHT -> Color(0xFFF2F2F2).copy(alpha = alpha)
    }

    val solidBackgroundColor: Color
        get() = solidBackgroundColor(0.81f)

    /**
     * Primary text and icon color inside the popup.
     */
    val contentColor: Color
        get() = when (this) {
            DARK -> Color.White
            LIGHT -> Color(0xFF1C1C1E)
        }

    /**
     * Secondary / subtitle text and icon color inside the popup.
     */
    val secondaryContentColor: Color
        get() = when (this) {
            DARK -> Color.White.copy(alpha = 0.65f)
            LIGHT -> Color(0xFF1C1C1E).copy(alpha = 0.65f)
        }

    /**
     * Border stroke color for popup containers.
     */
    val borderColor: Color
        get() = when (this) {
            DARK -> Color.White.copy(alpha = 0.15f)
            LIGHT -> Color.Black.copy(alpha = 0.12f)
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
