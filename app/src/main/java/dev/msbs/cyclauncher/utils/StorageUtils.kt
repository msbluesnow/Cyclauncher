package dev.msbs.cyclauncher.utils

import android.content.Context
import android.os.UserManager

@Volatile
private var deMigrationVerified = false

/**
 * Returns a device-protected storage context for Direct Boot compatibility.
 * Automatically migrates SharedPreferences from credential-protected storage on first invocation.
 */
fun Context.getSafeStorageContext(): Context {
    if (this.isDeviceProtectedStorage) {
        return this
    }
    val deviceProtectedContext = this.createDeviceProtectedStorageContext()
    if (deMigrationVerified) {
        return deviceProtectedContext
    }
    try {
        val initialPrefs = deviceProtectedContext.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
        if (initialPrefs.getBoolean("prefs_migrated_to_de", false)) {
            deMigrationVerified = true
            return deviceProtectedContext
        }
        val movedLauncher = deviceProtectedContext.moveSharedPreferencesFrom(this, "launcher_prefs")
        val movedLocale = deviceProtectedContext.moveSharedPreferencesFrom(this, "cyclauncher_prefs")
        val prefs = deviceProtectedContext.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
        val localePrefs = deviceProtectedContext.getSharedPreferences("cyclauncher_prefs", Context.MODE_PRIVATE)
        val isUnlocked = (getSystemService(Context.USER_SERVICE) as? UserManager)?.isUserUnlocked == true
        if (movedLauncher || movedLocale || prefs.all.isNotEmpty() || localePrefs.all.isNotEmpty() || isUnlocked) {
            prefs.edit().putBoolean("prefs_migrated_to_de", true).apply()
            deMigrationVerified = true
        }
    } catch (_: Exception) {
        // Credential-protected storage may be locked during Direct Boot
    }
    return deviceProtectedContext
}
