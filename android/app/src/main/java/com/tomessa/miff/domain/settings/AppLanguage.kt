package com.tomessa.miff.domain.settings

import java.util.Locale

/**
 * Language of the app as the person chooses it in Settings (Onboarding ticket 6.10, decision Q4 of the feed / profile /
 * settings ticket): French, English, or "the language of the phone". Nothing else is offered: adding a language means a
 * review of the tone, of the legal texts and of the screenshots (INIT ticket), so it is a product decision.
 *
 * [tag] is the BCP 47 tag given to the system; null for [SYSTEM] (no choice of our own: the phone decides, and English is
 * the fallback of the resources when the phone speaks another language).
 */
enum class AppLanguage(
    val tag: String?,
) {
    FRENCH("fr"),
    ENGLISH("en"),
    SYSTEM(null),
    ;

    companion object {
        /**
         * Reads a tag given back by the system or kept on the device. Only the language counts ("fr-FR" is French);
         * anything unknown, empty or null is [SYSTEM]: a value written by a future version never breaks the screen.
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
