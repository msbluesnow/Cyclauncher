package dev.msbs.cyclauncher.utils

import android.content.Context

/**
 * Returns a device-protected storage context for Direct Boot compatibility.
 * Automatically migrates SharedPreferences from credential-protected storage on first invocation.
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
    } catch (_: Exception) {
        // Credential-protected storage may be locked during Direct Boot
    }
    return deviceProtectedContext
}
