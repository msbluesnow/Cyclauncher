package dev.msbs.cyclauncher

import androidx.compose.ui.graphics.ImageBitmap

data class AppInfo(
    val label: String,
    val packageName: String,
    val activityName: String,
    val icon: ImageBitmap? = null,
    val searchChar: Char = ' '
)
