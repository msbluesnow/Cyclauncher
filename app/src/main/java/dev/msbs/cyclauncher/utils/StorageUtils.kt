package dev.msbs.cyclauncher.utils

import android.content.Context

/**
 * Returns a device-protected storage context. If SharedPreferences under the name "launcher_prefs"
 * exist in the default credential-protected storage, they are migrated to the device-protected
 * storage so they remain available during Direct Boot (before PIN/password entry).
 */
fun Context.getSafeStorageContext(): Context {
    val safeContext = this.createDeviceProtectedStorageContext()
    safeContext.moveSharedPreferencesFrom(this, "launcher_prefs")
    return safeContext
}
