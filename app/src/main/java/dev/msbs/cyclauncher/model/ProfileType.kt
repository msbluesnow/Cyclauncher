package dev.msbs.cyclauncher.model

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Process
import android.os.UserHandle

/**
 * Represents the profile type for an app / user profile (Personal, Work, Private Space).
 */
enum class ProfileType {
    PERSONAL,
    WORK,
    PRIVATE;

    companion object {
        /**
         * Resolves the [ProfileType] for the given [userHandle] using Android 15 [LauncherApps.getLauncherUserInfo].
         */
        fun fromUserHandle(context: Context, userHandle: UserHandle): ProfileType {
            if (userHandle == Process.myUserHandle()) return PERSONAL

            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps

            // Android 15 (API 35+) Private Space detection
            if (Build.VERSION.SDK_INT >= 35 && launcherApps != null) {
                try {
                    val userInfo = launcherApps.getLauncherUserInfo(userHandle)
                    if (userInfo != null) {
                        val userType = userInfo.userType
                        if (userType == "android.os.usertype.profile.PRIVATE" || userType.contains("PRIVATE", ignoreCase = true)) {
                            return PRIVATE
                        }
                        if (userType == "android.os.usertype.profile.MANAGED" || userType.contains("MANAGED", ignoreCase = true)) {
                            return WORK
                        }
                    }
                } catch (_: Throwable) {}
            }

            // On Android <= 14 (API < 35), Private Space does not exist, so secondary profiles are Work Profiles
            return WORK
        }
    }
}
