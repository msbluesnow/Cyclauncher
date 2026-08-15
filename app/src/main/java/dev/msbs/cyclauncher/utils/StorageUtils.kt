package dev.msbs.cyclauncher.utils

import android.content.Context

/**
 * Возвращает device-protected storage context для работы в Direct Boot.
 * Мигрирует SharedPreferences из credential-protected storage при первом вызове.
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
        // Credential-protected storage может быть заблокирован во время Direct Boot
    }
    return deviceProtectedContext
}
