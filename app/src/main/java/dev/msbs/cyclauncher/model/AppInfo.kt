package dev.msbs.cyclauncher.model

/**
 * Represents information about an installed application.
 *
 * Icons are deliberately NOT stored here. They are loaded lazily by Coil from [iconKey]
 * (the standard `"packageName/activityName"` component key) on the UI side, so the
 * ViewModel flows can hold thousands of [AppInfo] instances cheaply without keeping
 * decoded bitmaps alive for the lifetime of the process.
 *
 * @property label The display name of the application.
 * @property packageName The Android package name (e.g., "com.example.app").
 * @property activityName The fully qualified launch activity class name.
 * @property iconKey Component key `"packageName/activityName"` used as the Coil model.
 * @property searchChar The mapped uppercase character used for alphabet wheel indexing.
 */
data class AppInfo(
    val label: String,
    val packageName: String,
    val activityName: String,
    val iconKey: String,
    val searchChar: Char = ' '
) {
    /** Convenience accessor producing the canonical `"packageName/activityName"` form. */
    val componentKey: String get() = "$packageName/$activityName"
}
