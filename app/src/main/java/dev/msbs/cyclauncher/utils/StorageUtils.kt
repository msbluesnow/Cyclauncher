package dev.msbs.cyclauncher.utils

import android.content.Context
import android.os.UserManager

private val deMigrationLock = Any()

@Volatile
private var deMigrationVerified = false

/**
 * Safely returns whether the current user's credential-encrypted storage is unlocked.
 * Returns false during Direct Boot before the user unlocks the device.
 */
fun Context.isUserUnlockedSafe(): Boolean {
    return try {
        val um = getSystemService(Context.USER_SERVICE) as? UserManager
        um?.isUserUnlocked != false
    } catch (_: Throwable) {
        true
    }
}

/**
 * Returns a device-protected storage context for Direct Boot compatibility.
 * Automatically migrates SharedPreferences from credential-protected storage once unlocked,
 * preserving existing DE data if CE has no preferences.
 */
fun Context.getSafeStorageContext(): Context {
    if (this.isDeviceProtectedStorage) {
        return this
    }
    val ceContext = this.applicationContext?.takeIf { !it.isDeviceProtectedStorage } ?: this
    val deviceProtectedContext = ceContext.createDeviceProtectedStorageContext()
    if (deMigrationVerified) {
        return deviceProtectedContext
    }
    synchronized(deMigrationLock) {
        if (deMigrationVerified) {
            return deviceProtectedContext
        }
        try {
            val initialPrefs = deviceProtectedContext.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
            if (initialPrefs.getBoolean("prefs_migrated_to_de", false)) {
                deMigrationVerified = true
                return deviceProtectedContext
            }
            if (!ceContext.isUserUnlockedSafe()) {
                return deviceProtectedContext
            }
            migratePrefsFromCeToDe(ceContext, deviceProtectedContext, "launcher_prefs")
            migratePrefsFromCeToDe(ceContext, deviceProtectedContext, "cyclauncher_prefs")
            val prefs = deviceProtectedContext.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("prefs_migrated_to_de", true).apply()
            deMigrationVerified = true
        } catch (_: Exception) {
            // Credential-protected storage may be locked during Direct Boot
        }
    }
    return deviceProtectedContext
}

private fun migratePrefsFromCeToDe(ceContext: Context, deContext: Context, name: String) {
    val cePrefs = ceContext.getSharedPreferences(name, Context.MODE_PRIVATE)
    val ceAll = cePrefs.all
    if (ceAll.isEmpty()) {
        // CE has no data; preserve any existing DE preferences untouched
        return
    }
    val dePrefs = deContext.getSharedPreferences(name, Context.MODE_PRIVATE)
    if (dePrefs.all.isEmpty()) {
        deContext.moveSharedPreferencesFrom(ceContext, name)
    } else {
        // Merge legacy CE keys that are missing in DE, preserving existing DE entries
        val editor = dePrefs.edit()
        for ((key, value) in ceAll) {
            if (!dePrefs.contains(key)) {
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is String -> editor.putString(key, value)
                    is Set<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        editor.putStringSet(key, value as Set<String>)
                    }
                }
            }
        }
        editor.commit()
        ceContext.deleteSharedPreferences(name)
    }
}
