package dev.msbs.cyclauncher.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.Composable
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

/** Global setting for text marquee scrolling (enabled/disabled), provided via CompositionLocal. */
val LocalMarqueeEnabled = compositionLocalOf { true }

/** Opt-in capsule contrast strength (0 = legacy text-color tint), provided via CompositionLocal. */
val LocalCapsuleAlpha = compositionLocalOf { 0f }

/**
 * Fill color for existing capsules/cards. At capsule alpha 0 keeps the legacy text-color tint [baseAlpha];
 * otherwise uses the contrast color (black behind white text, white behind black text) at the user alpha.
 */
@Composable
fun capsuleFill(textColor: Color, baseAlpha: Float): Color {
    val userAlpha = LocalCapsuleAlpha.current
    if (userAlpha <= 0f) return textColor.copy(alpha = baseAlpha)
    val base = if (textColor.luminance() > 0.5f) Color.Black else Color.White
    return base.copy(alpha = userAlpha)
}

@Composable
fun PrimaryTextColor.capsuleColor(baseAlpha: Float): Color = capsuleFill(color, baseAlpha)

/** Global version tracker for active icon pack changes, provided via CompositionLocal. */
val LocalIconPackVersion = compositionLocalOf { 0L }

