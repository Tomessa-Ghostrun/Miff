package com.tomessa.miff.domain.settings

import java.util.Locale

/**
 * Language of the app as the person chooses it in Settings (Onboarding ticket 6.10, decision Q4 of the feed / profile / settings ticket; ticket
 * I18N-4L for the six languages): German, English, Spanish, French, Italian, Portuguese (of Portugal), or "the language of the phone".
 *
 * The list is the one of the registry `docs/contracts/i18n-languages.json` (one entry per language, `code` = [tag]); the order is the alphabetical one of
 * the NAMES written in their own language (Deutsch, English, Español, Français, Italiano, Português), then the phone's. `AppLanguageRegistryTest` fails if
 * this enum and the registry (or `localeFilters` of the release build) ever list different languages. The tags are written one per line as `NAME("tag")`
 * because `scripts/check-i18n-parity.mjs` reads them from here.
 *
 * Adding a language is also a product decision (tone, texts, screenshots: INIT ticket): it starts in the registry, with its texts in `values-<language>`.
 *
 * [tag] is the BCP 47 tag given to the system; null for [SYSTEM] (no choice of our own: the phone decides, and English is the fallback of the resources
 * when the phone speaks another language).
 */
enum class AppLanguage(
    val tag: String?,
) {
    GERMAN("de"),
    ENGLISH("en"),
    SPANISH("es"),
    FRENCH("fr"),
    ITALIAN("it"),
    PORTUGUESE("pt"),
    SYSTEM(null),
    ;

    /**
     * The tag APPLIED to the system (`LocaleManager`, the locale of the process): the Portuguese of the app is the one of PORTUGAL, `pt-PT`, not the bare `pt`.
     * The plural rules depend on it: for `pt` ICU uses the Brazilian ones ("0 dia", zero is `one`), for `pt-PT` the European ones ("0 dias", zero is `other`),
     * the ones the texts are written for (ticket I18N-4L, criterion 19). The resources `values-pt` serve both. Null for [SYSTEM].
     */
    val localeTag: String?
        get() = if (this == PORTUGUESE) "pt-PT" else tag

    companion object {
        /**
         * The languages offered in Settings > Langue: those whose texts are really in the app ([provided] = codes of the language, `BuildConfig.PROVIDED_LANGUAGES`),
         * then [SYSTEM]. A language declared in the registry but not translated yet (es, it, pt, de before the lot 4) is NOT offered: it would show English under its
         * own name. It appears by itself the day its `values-xx/strings.xml` is in the build.
         */
        fun offered(provided: Set<String>): List<AppLanguage> = entries.filter { it == SYSTEM || it.tag in provided }

        /**
         * Reads a tag given back by the system or kept on the device. Only the language counts ("fr-FR" is French, "es-MX" is Spanish, "pt-BR" is the
         * Portuguese of the app, which is the one of Portugal: the resources `values-pt` serve every region of the language); anything unknown, empty or
         * null is [SYSTEM]: a value written by a future version never breaks the screen.
         *
         * With [provided] (the languages really in the app), a language that is not provided is [SYSTEM] too: it is never applied, so that the dates and numbers do not
         * come out in a language while the texts are English.
         */
        fun fromTag(
            tag: String?,
            provided: Set<String>? = null,
        ): AppLanguage {
            val language =
                tag
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { Locale.forLanguageTag(it.replace('_', '-')).language }
                    ?.lowercase(Locale.ROOT)
            val found = entries.firstOrNull { it.tag != null && it.tag == language } ?: SYSTEM
            return if (provided != null && found != SYSTEM && found.tag !in provided) SYSTEM else found
        }
    }
}
