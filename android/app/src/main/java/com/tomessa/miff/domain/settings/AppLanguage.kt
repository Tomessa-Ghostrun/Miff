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

    companion object {
        /**
         * Reads a tag given back by the system or kept on the device. Only the language counts ("fr-FR" is French, "es-MX" is Spanish, "pt-BR" is the
         * Portuguese of the app, which is the one of Portugal: the resources `values-pt` serve every region of the language); anything unknown, empty or
         * null is [SYSTEM]: a value written by a future version never breaks the screen.
         */
        fun fromTag(tag: String?): AppLanguage {
            val language =
                tag
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { Locale.forLanguageTag(it.replace('_', '-')).language }
                    ?.lowercase(Locale.ROOT)
            return entries.firstOrNull { it.tag != null && it.tag == language } ?: SYSTEM
        }
    }
}
