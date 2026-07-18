package dev.msbs.cyclauncher.model

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Represents information about an installed application.
 *
 * @property label The display name of the application.
 * @property packageName The Android package name (e.g., "com.example.app").
 * @property activityName The fully qualified launch activity class name.
 * @property icon The cached application icon bitmap, or null if loading failed.
 * @property searchChar The mapped uppercase character used for alphabet wheel indexing.
 */
data class AppInfo(
    val label: String,
    val packageName: String,
    val activityName: String,
    val icon: ImageBitmap? = null,
    val searchChar: Char = ' '
)
