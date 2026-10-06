package dev.msbs.cyclauncher.utils

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import dev.msbs.cyclauncher.R
import java.util.Locale

/**
 * Utility for querying and setting the application-wide locale.
 * Uses native [LocaleManager] on Android 13+ (API 33+) which seamlessly integrates
 * with system per-app language settings and auto-generated locale configs.
 * On API < 33, falls back to SharedPreferences and Configuration.
 */
object LocaleUtils {

    data class SupportedLanguage(
        val code: String,
        val labelRes: Int,
        val nativeName: String
    )

    val SUPPORTED_LANGUAGES = listOf(
        SupportedLanguage(code = "", labelRes = R.string.settings_language_system, nativeName = "System default"),
        SupportedLanguage(code = "en", labelRes = R.string.settings_language_en, nativeName = "English"),
        SupportedLanguage(code = "ru", labelRes = R.string.settings_language_ru, nativeName = "Русский")
    )

    private const val PREFS_NAME = "cyclauncher_prefs"
    private const val PREF_APP_LANGUAGE = "pref_app_language"

    fun setAppLanguage(context: Context, languageCode: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_APP_LANGUAGE, languageCode).apply()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(LocaleManager::class.java)
            val localeList = if (languageCode.isBlank()) {
                LocaleList.getEmptyLocaleList()
            } else {
                LocaleList.forLanguageTags(languageCode)
            }
            localeManager?.applicationLocales = localeList
        } else {
            val locale = if (languageCode.isBlank()) {
                Locale.getDefault()
            } else {
                Locale.forLanguageTag(languageCode)
            }
            Locale.setDefault(locale)
            val config = context.resources.configuration
            config.setLocale(locale)
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
        }
    }

    fun getCurrentLanguageCode(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(LocaleManager::class.java)
            val locales = localeManager?.applicationLocales
            if (locales != null && !locales.isEmpty) {
                return locales[0]?.language ?: ""
            }
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(PREF_APP_LANGUAGE, "") ?: ""
    }

    fun wrapContext(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return base
        }
        val code = getCurrentLanguageCode(base)
        if (code.isBlank()) return base
        val locale = Locale.forLanguageTag(code)
        Locale.setDefault(locale)
        val config = android.content.res.Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}

