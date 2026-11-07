package com.tomessa.miff.domain.profile

import com.tomessa.miff.domain.auth.ProfileDocument

/**
 * Rules of the first name and last name fields (Onboarding ticket 6.3, decision Q6): 1 to 50 characters once the spaces
 * around are removed, no restriction on the alphabet (accents, hyphens, apostrophes, non-Latin scripts, emoji accepted),
 * no uniqueness. The 50 limit is the one of the Firestore rules.
 *
 * A name is shown to the other members of every Miff, so the characters that could make it look like something else are
 * removed: control characters (a new line would break a card), and the invisible or direction-forcing characters (a
 * right-to-left override could reverse the text around it). Nothing else is touched.
 */
object ProfileForm {
    const val MAX_NAME_LENGTH = ProfileDocument.MAX_NAME_LENGTH

    /** The small counter ("45/50") only appears when the limit gets close. */
    const val COUNTER_FROM = 45

    /** A text field after the limit was applied: [text] is what the field keeps, [wasCut] tells that something was dropped. */
    data class Limited(
        val text: String,
        val wasCut: Boolean,
    )

    /** What a field keeps while typing: no control or spoofing characters, at most 50 characters. Spaces are kept as typed. */
    fun limitInput(raw: String): Limited {
        val cleaned = removeUnsafe(raw)
        return if (cleaned.length > MAX_NAME_LENGTH) {
            Limited(cutWithoutBreakingAPair(cleaned, MAX_NAME_LENGTH), wasCut = true)
        } else {
            Limited(cleaned, wasCut = false)
        }
    }

    /** What is stored: unsafe characters removed, spaces around removed, repeated spaces reduced to one. */
    fun normalizeName(raw: String): String =
        removeUnsafe(raw)
            .replace(WHITESPACE, " ")
            .trim()

    /** The arrow of the profile step is active once both names are complete. */
    fun isNameComplete(raw: String): Boolean = normalizeName(raw).length in 1..MAX_NAME_LENGTH

    fun showCounter(length: Int): Boolean = length >= COUNTER_FROM

    /**
     * Pre-fill from the display name given by the sign-in provider (Google). Only a suggestion, always editable: the first
     * word is the first name, the rest is the last name. Each part is cut to 50 characters. Null/blank gives two empty
     * fields. No photo is ever taken from the provider.
     */
    fun splitDisplayName(displayName: String?): Pair<String, String> {
        val name = normalizeName(displayName.orEmpty())
        if (name.isEmpty()) return "" to ""
        val space = name.indexOf(' ')
        val first = if (space < 0) name else name.substring(0, space)
        val last = if (space < 0) "" else name.substring(space + 1)
        return limitInput(first).text to limitInput(last).text
    }

    private val WHITESPACE = Regex("[\\p{Z}\\s]+")

    private fun removeUnsafe(raw: String): String {
        val out = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val cp = raw.codePointAt(i)
            i += Character.charCount(cp)
            when {
                cp == '\t'.code || cp == '\n'.code || cp == '\r'.code || cp == 0x0B || cp == 0x0C || cp == 0x85 -> {
                    out
                        .append(
                            ' ',
                        )
                }

                Character.isISOControl(cp) -> {
                    Unit
                }

                isInvisibleOrDirectional(cp) -> {
                    Unit
                }

                else -> {
                    out.appendCodePoint(cp)
                }
            }
        }
        return out.toString()
    }

    /** Zero-width space, direction marks, embeddings, overrides and isolates, word joiner, byte order mark, line separators. */
    private fun isInvisibleOrDirectional(cp: Int): Boolean =
        cp == 0x200B || cp == 0x200E || cp == 0x200F || cp in 0x202A..0x202E || cp == 0x2060 ||
            cp in 0x2066..0x2069 || cp == 0xFEFF || cp == 0x2028 || cp == 0x2029

    private fun cutWithoutBreakingAPair(
        text: String,
        max: Int,
    ): String {
        var end = max
        if (end > 0 && Character.isHighSurrogate(text[end - 1])) end--
        return text.substring(0, end)
    }
}
