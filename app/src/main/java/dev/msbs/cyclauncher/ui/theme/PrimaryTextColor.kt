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

/** Capsule base color toggle: true for Black capsules, false for White capsules. */
val LocalCapsuleIsDark = compositionLocalOf { true }

/** Opt-in popup opacity strength (default 0.81f = 81% opaque, 19% transparent), provided via CompositionLocal. */
val LocalPopupAlpha = compositionLocalOf { 0.81f }

/**
 * Fill color for existing capsules, cards, and interactive chips.
 *
 * Design principles:
 * 1. Base color is configured via [LocalCapsuleIsDark] (defaults to opposite of text luminance),
 *    guaranteeing immediate readability over wallpaper by default.
 * 2. Visual hierarchy between 1st-order containers (outer cards) and 2nd-order nested elements (buttons/chips)
 *    is strictly preserved:
 *    - 2nd-order / nested elements ([isNested] = true) can scale up to 100% opacity (1.0f, solid) at max slider.
 *    - 1st-order / bottom containers ([isNested] = false) cap at 81% opacity (0.81f, exactly 19% lower),
 *      guaranteeing that nested items NEVER blend into the parent background even at maximum opacity.
 */
@Composable
fun capsuleFill(textColor: Color, baseAlpha: Float, isNested: Boolean = false): Color {
    val isDark = LocalCapsuleIsDark.current
    val contrastColor = if (isDark) Color.Black else Color.White
    val userBoost = LocalCapsuleAlpha.current.coerceIn(0f, 1f)
    val maxAlpha = if (isNested) 1.0f else 0.81f
    val effectiveAlpha = (baseAlpha + (maxAlpha - baseAlpha) * userBoost).coerceIn(0f, 1f)
    return contrastColor.copy(alpha = effectiveAlpha)
}

@Composable
fun PrimaryTextColor.capsuleColor(baseAlpha: Float, isNested: Boolean = false): Color =
    capsuleFill(color, baseAlpha, isNested)

/**
 * Proportional border color for capsules, ensuring crisp outline definition
 * even when strong background dimming is active.
 */
@Composable
fun PrimaryTextColor.capsuleBorderColor(baseAlpha: Float = 0.15f): Color {
    val isDark = LocalCapsuleIsDark.current
    val baseBorder = if (isDark) Color.White else Color.Black
    val userBoost = LocalCapsuleAlpha.current.coerceIn(0f, 1f)
    val effectiveAlpha = (baseAlpha * (1f + userBoost * 1.5f)).coerceIn(0f, 0.50f)
    return baseBorder.copy(alpha = effectiveAlpha)
}

/** Global version tracker for active icon pack changes, provided via CompositionLocal. */
val LocalIconPackVersion = compositionLocalOf { 0L }

