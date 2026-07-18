package com.ahmadkharfan.androidstudiolite.core.locale

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** Applies the user's in-app language choice (independent of the system locale). */
object AppLocale {

    const val DEFAULT_LANGUAGE = "en"
    private const val PREFS_NAME = "locale_sync"
    private const val KEY_LANGUAGE = "language"

    fun supported(languageCode: String): String =
        if (languageCode == "ar") "ar" else DEFAULT_LANGUAGE

    fun readLanguage(context: Context): String =
        supported(
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LANGUAGE, DEFAULT_LANGUAGE)
                ?: DEFAULT_LANGUAGE,
        )

    fun writeLanguage(context: Context, languageCode: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, supported(languageCode))
            .apply()
    }

    fun wrap(context: Context, languageCode: String): Context {
        val locale = localeFor(supported(languageCode))
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }

    private fun localeFor(languageCode: String): Locale = when (languageCode) {
        "ar" -> Locale.forLanguageTag("ar")
        else -> Locale.forLanguageTag("en")
    }
}
