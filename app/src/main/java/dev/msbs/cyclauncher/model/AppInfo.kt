package dev.msbs.cyclauncher.model

/**
 * Metadata for an installed application.
 * App icons are loaded lazily on demand via Coil using [iconKey]
 * to prevent ViewModels from holding decoded Bitmaps in memory.
 */
data class AppInfo(
    val label: String,
    val packageName: String,
    val activityName: String,
    val iconKey: String,
    val searchChar: Char = ' '
) {
    val componentKey: String get() = "$packageName/$activityName"
}
