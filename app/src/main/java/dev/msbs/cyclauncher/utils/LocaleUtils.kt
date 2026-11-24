package dev.msbs.cyclauncher.utils

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import dev.msbs.cyclauncher.R
import java.util.Locale

/**
 * Utility for querying and setting the application-wide locale.
 * Uses native [LocaleManager] on Android 13+ (API 33+) which seamlessly integrates
 * with system per-app language settings and auto-generated locale configs.
 * On API < 33, falls back to SharedPreferences, preserved system Locale, and Activity recreation.
 */
object LocaleUtils {

    data class SupportedLanguage(
        val code: String,
        val labelRes: Int,
        val englishName: String,
        val nativeName: String
    )

    val SUPPORTED_LANGUAGES = listOf(
        SupportedLanguage(
            code = "",
            labelRes = R.string.settings_language_system,
            englishName = "System Default",
            nativeName = ""
        ),
        SupportedLanguage(
            code = "en",
            labelRes = R.string.settings_language_en,
            englishName = "English",
            nativeName = "English"
        ),
        SupportedLanguage(
            code = "es",
            labelRes = R.string.settings_language_es,
            englishName = "Spanish",
            nativeName = "Español"
        ),
        SupportedLanguage(
            code = "de",
            labelRes = R.string.settings_language_de,
            englishName = "German",
            nativeName = "Deutsch"
        ),
        SupportedLanguage(
            code = "ru",
            labelRes = R.string.settings_language_ru,
            englishName = "Russian",
            nativeName = "Русский"
        ),
        SupportedLanguage(
            code = "zh",
            labelRes = R.string.settings_language_zh,
            englishName = "Chinese (Simplified)",
            nativeName = "简体中文"
        ),
        SupportedLanguage(
            code = "hi",
            labelRes = R.string.settings_language_hi,
            englishName = "Hindi",
            nativeName = "हिंदी"
        ),
        SupportedLanguage(
            code = "ja",
            labelRes = R.string.settings_language_ja,
            englishName = "Japanese",
            nativeName = "日本語"
        )
    )

    private const val PREFS_NAME = "cyclauncher_prefs"
    private const val PREF_APP_LANGUAGE = "pref_app_language"

    // Snapshot of the OS default locale taken before any in-process Locale.setDefault override
    private val initialSystemLocale: Locale = Locale.getDefault()

    private fun getSystemDefaultLocale(): Locale {
        return try {
            val sysLocales = Resources.getSystem().configuration.locales
            if (!sysLocales.isEmpty) sysLocales[0] else initialSystemLocale
        } catch (_: Throwable) {
            initialSystemLocale
        }
    }

    private fun findActivity(context: Context): Activity? {
        var ctx: Context? = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return ctx as? Activity
    }

    private fun persistLanguagePref(context: Context, languageCode: String) {
        try {
            val safeCtx = context.getSafeStorageContext()
            val prefs = safeCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (prefs.getString(PREF_APP_LANGUAGE, null) != languageCode) {
                prefs.edit().putString(PREF_APP_LANGUAGE, languageCode).apply()
            }
        } catch (_: Exception) {
            // Ignore if storage is unavailable during early Direct Boot
        }
    }

    fun setAppLanguage(context: Context, languageCode: String) {
        persistLanguagePref(context, languageCode)

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
                getSystemDefaultLocale()
            } else {
                Locale.forLanguageTag(languageCode)
            }
            Locale.setDefault(locale)
            val config = Configuration(context.resources.configuration)
            config.setLocale(locale)
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
            findActivity(context)?.recreate()
        }
    }

    fun getCurrentLanguageCode(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val localeManager = context.getSystemService(LocaleManager::class.java)
                val locales = localeManager?.applicationLocales
                if (locales != null) {
                    val resolvedCode = if (locales.isEmpty) {
                        ""
                    } else {
                        val locale = locales[0]
                        if (locale == null) {
                            ""
                        } else {
                            val tag = locale.toLanguageTag()
                            val lang = locale.language ?: ""
                            when {
                                SUPPORTED_LANGUAGES.any { it.code.equals(tag, ignoreCase = true) } -> tag
                                SUPPORTED_LANGUAGES.any { it.code.equals(lang, ignoreCase = true) } -> lang
                                else -> lang
                            }
                        }
                    }
                    persistLanguagePref(context, resolvedCode)
                    return resolvedCode
                }
            } catch (_: Exception) {}
        }
        return try {
            val safeCtx = context.getSafeStorageContext()
            val prefs = safeCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getString(PREF_APP_LANGUAGE, "") ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    fun wrapContext(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return base
        }
        val code = getCurrentLanguageCode(base)
        if (code.isBlank()) {
            Locale.setDefault(getSystemDefaultLocale())
            return base
        }
        val locale = Locale.forLanguageTag(code)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
