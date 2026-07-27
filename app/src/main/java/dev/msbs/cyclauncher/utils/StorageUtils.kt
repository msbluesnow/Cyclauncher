package dev.msbs.cyclauncher.utils

import android.content.Context

/**
 * Returns a device-protected storage context. If SharedPreferences under the name "launcher_prefs"
 * exist in the default credential-protected storage, they are migrated to the device-protected
 * storage so they remain available during Direct Boot (before PIN/password entry).
 */
fun Context.getSafeStorageContext(): Context {
    if (this.isDeviceProtectedStorage) {
        return this
    }
    val deviceProtectedContext = this.createDeviceProtectedStorageContext()
    try {
        val targetPrefs = deviceProtectedContext.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
        if (!targetPrefs.contains("prefs_migrated_to_de")) {
            val moved = deviceProtectedContext.moveSharedPreferencesFrom(this, "launcher_prefs")
            if (moved || targetPrefs.all.isNotEmpty()) {
                targetPrefs.edit().putBoolean("prefs_migrated_to_de", true).apply()
            }
        }
    } catch (e: Exception) {
        // Fallback gracefully if credential-protected storage is locked during Direct Boot
    }
    return deviceProtectedContext
}
