package dev.msbs.cyclauncher.model

import android.os.Process
import android.os.UserHandle

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
    val searchChar: Char = ' ',
    val userHandle: UserHandle = Process.myUserHandle(),
    val profileType: ProfileType = ProfileType.PERSONAL
) {
    val baseComponentKey: String = "$packageName/$activityName"
    val componentKey: String = if (userHandle == Process.myUserHandle()) {
        baseComponentKey
    } else {
        "$baseComponentKey#${userHandle.hashCode()}"
    }

    val normalizedLabel: String
        get() = label.lowercase().trim()
}
