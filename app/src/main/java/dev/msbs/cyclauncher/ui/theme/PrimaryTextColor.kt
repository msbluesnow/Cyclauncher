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
    WHITE("White", Color.White, Color.Black.copy(alpha = 0.9f)),
    BLACK("Black", Color.Black, Color.White.copy(alpha = 0.9f));

    /**
     * Returns a [Shadow] instance representing a thin text outline if [showShadows] is true, or null if disabled.
     * When MainColor is WHITE, a black outline is applied.
     * When MainColor is BLACK, a white outline is applied.
     */
    fun getShadow(showShadows: Boolean): Shadow? {
        return if (showShadows) {
            when (this) {
                WHITE -> Shadow(
                    color = Color.Black.copy(alpha = 0.9f),
                    offset = Offset.Zero,
                    blurRadius = 2.5f
                )
                BLACK -> Shadow(
                    color = Color.White.copy(alpha = 0.9f),
                    offset = Offset.Zero,
                    blurRadius = 2.5f
                )
            }
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
