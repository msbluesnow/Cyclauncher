package dev.msbs.cyclauncher.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow

/**
 * Defines the user selectable primary (non-accent) text color theme.
 *
 * @property displayName Human-readable label for settings UI.
 * @property color The primary color for general text labels.
 * @property shadowColor The contrasting shadow color used when adaptive shadows are enabled.
 */
enum class PrimaryTextColor(
    val displayName: String,
    val color: Color,
    val shadowColor: Color
) {
    WHITE("White", Color.White, Color.Black.copy(alpha = 0.6f)),
    BLACK("Black", Color.Black, Color.White.copy(alpha = 0.6f));

    /**
     * Returns a [Shadow] instance if [showShadows] is true, or null if disabled.
     */
    fun getShadow(showShadows: Boolean): Shadow? {
        return if (showShadows) {
            Shadow(
                color = shadowColor,
                offset = Offset(2f, 2f),
                blurRadius = 4f
            )
        } else null
    }

    companion object {
        /**
         * Resolves the [PrimaryTextColor] enum element corresponding to the given string name.
         * Falls back to [WHITE] if no match is found.
         *
         * @param name The name of the text color option to lookup.
         * @return The resolved PrimaryTextColor instance.
         */
        fun fromName(name: String): PrimaryTextColor {
            return try {
                valueOf(name)
            } catch (e: Exception) {
                WHITE
            }
        }
    }
}
