package dev.msbs.cyclauncher.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.runtime.compositionLocalOf

/** Primary text color options (White/Black) with adaptive drop shadows. */
enum class PrimaryTextColor(
    val displayName: String,
    val color: Color,
    val shadowColor: Color
) {
    WHITE("White", Color.White, Color.Black.copy(alpha = 0.6f)),
    BLACK("Black", Color.Black, Color.White.copy(alpha = 0.85f));

    fun getShadow(showShadows: Boolean, shadowColorOverride: PrimaryTextColor? = null): Shadow? {
        return if (showShadows) {
            val isWhiteShadow = if (shadowColorOverride != null) {
                shadowColorOverride == WHITE
            } else {
                this == BLACK
            }

            if (isWhiteShadow) {
                Shadow(
                    color = Color.White.copy(alpha = 0.85f),
                    offset = Offset.Zero,
                    blurRadius = 1.8f
                )
            } else {
                Shadow(
                    color = Color.Black.copy(alpha = 0.6f),
                    offset = Offset.Zero,
                    blurRadius = 4f
                )
            }
        } else null
    }

    fun getShadowColor(shadowColorOverride: PrimaryTextColor? = null): Color {
        val isWhiteShadow = if (shadowColorOverride != null) {
            shadowColorOverride == WHITE
        } else {
            this == BLACK
        }
        return if (isWhiteShadow) Color.White.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.6f)
    }

    companion object {
        fun fromName(name: String): PrimaryTextColor {
            return try { valueOf(name) } catch (_: Exception) { WHITE }
        }
    }
}

/** Global settings for adaptive shadows, provided via CompositionLocal. */
data class ShadowSettings(
    val showShadows: Boolean = true,
    val shadowColorOverride: PrimaryTextColor? = null
)

val LocalShadowSettings = compositionLocalOf { ShadowSettings() }

/** Global setting for launcher animations (enabled/disabled), provided via CompositionLocal. */
val LocalAnimationsEnabled = compositionLocalOf { true }

/** Global version tracker for active icon pack changes, provided via CompositionLocal. */
val LocalIconPackVersion = compositionLocalOf { 0L }
