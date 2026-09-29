package com.martinrevert.latorrentola.network

import java.util.Locale

/** Provides the language tag TMDB uses for localized response data. */
internal object TmdbLanguage {
    /**
     * Returns the Android system locale as a TMDB language tag.
     *
     * TMDB documents language tags in language-region form, so common English and Spanish
     * locales without an explicit region use en-US and es-ES respectively.
     */
    fun current(): String {
        val locale = Locale.getDefault()
        if (locale.country.isNotBlank()) return locale.toLanguageTag()

        return when (locale.language.lowercase(Locale.ROOT)) {
            "en" -> "en-US"
            "es" -> "es-ES"
            else -> locale.toLanguageTag()
        }
    }
}
