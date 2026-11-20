package com.tomessa.miff.domain.place

import java.util.Locale

/**
 * The language asked of an address-search service for the labels of the answers (country, city...): the language of the app when the service knows it, English
 * otherwise (ticket I18N-4L, criterion 14). One table per service, one place: a language that is added to the app is NOT silently sent to a service that refuses it.
 *
 * Photon (`photon.komoot.io`), MEASURED on 2026-11-19: `lang=fr`, `en`, `de` answer; `lang=it`, `es` and `pt` answer HTTP 400 ("Language is not supported. Supported
 * are: default, de, en, fr"). The ticket expected Italian to be supported: it is not on the public instance. So Spanish, Italian and Portuguese ask for English labels
 * ("Germany", not "Alemania"): the NAMES of the streets and of the places are the ones of OpenStreetMap whatever the language, only the generic labels change. A 400 would
 * have been read as "service unavailable" and the search would have been broken for those languages.
 * Signal to extend the table: the answer of `https://photon.komoot.io/api?q=Berlin&lang=<code>` is 200 for a new code (re-measure before adding it).
 *
 * Geoapify (prepared fallback, built only with a key, never tested here: no key): the table is the same one on purpose. The service accepts more languages, but a code
 * it would refuse makes the fallback unavailable exactly when it is needed; extend this list only after a test with a real key.
 */
object AddressSearchLanguage {
    const val FALLBACK = "en"

    val PHOTON: Set<String> = setOf("de", "en", "fr")
    val GEOAPIFY: Set<String> = setOf("de", "en", "fr")

    /**
     * [appLanguage] is a code of language or a tag ("fr", "pt", "pt-BR", "es_MX"): only the language counts; anything unknown, empty or not supported by the service
     * gives [FALLBACK].
     */
    fun forService(
        appLanguage: String,
        supported: Set<String>,
    ): String {
        val language =
            appLanguage
                .trim()
                .replace('_', '-')
                .substringBefore('-')
                .lowercase(Locale.ROOT)
        return if (language in supported) language else FALLBACK
    }
}
