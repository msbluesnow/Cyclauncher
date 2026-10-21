package dev.msbs.cyclauncher.model

import android.os.Process
import android.os.UserHandle
import androidx.compose.runtime.Immutable

/**
 * Metadata for an installed application.
 * App icons are loaded lazily on demand via Coil using [iconKey]
 * to prevent ViewModels from holding decoded Bitmaps in memory.
 */
@Immutable
data class AppInfo(
    val label: String,
    val packageName: String,
    val activityName: String,
    val iconKey: String,
    val searchChar: Char = ' ',
    val userHandle: UserHandle = Process.myUserHandle(),
    val profileType: ProfileType = ProfileType.PERSONAL,
    val firstInstallTime: Long = 0L,
    val lastUpdateTime: Long = 0L,
    val firstSymbol: String = ""
) {
    val baseComponentKey: String = "$packageName/$activityName"
    val componentKey: String = if (userHandle == Process.myUserHandle()) {
        baseComponentKey
    } else {
        "$baseComponentKey#${userHandle.hashCode()}"
    }

    val normalizedLabel: String = label.lowercase().trim()
}
