package dev.msbs.cyclauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.msbs.cyclauncher.ui.theme.PrimaryTextColor

/**
 * Optional background dim behind a screen. Opt-in via settings (alpha 0 = fully transparent).
 * Scrim is black or white based on [isDark].
 */
@Composable
fun ScreenScrim(
    alpha: Float,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val scrimModifier = if (alpha > 0f) {
        val scrimBase = if (isDark) Color.Black else Color.White
        modifier.fillMaxSize().background(scrimBase.copy(alpha = alpha))
    } else {
        modifier.fillMaxSize()
    }
    Box(modifier = scrimModifier) {
        content()
    }
}

@Composable
fun ScreenScrim(
    alpha: Float,
    textColor: PrimaryTextColor,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) = ScreenScrim(alpha, isDark = textColor != PrimaryTextColor.BLACK, modifier = modifier, content = content)
