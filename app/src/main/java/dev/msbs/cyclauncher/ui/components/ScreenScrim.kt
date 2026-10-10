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
 * Scrim is the contrast color for the text: black behind white text, white behind black text.
 */
@Composable
fun ScreenScrim(
    alpha: Float,
    textColor: PrimaryTextColor,
    content: @Composable () -> Unit
) {
    val modifier = if (alpha > 0f) {
        val scrimBase = if (textColor == PrimaryTextColor.WHITE) Color.Black else Color.White
        Modifier.fillMaxSize().background(scrimBase.copy(alpha = alpha))
    } else {
        Modifier.fillMaxSize()
    }
    Box(modifier = modifier) {
        content()
    }
}
