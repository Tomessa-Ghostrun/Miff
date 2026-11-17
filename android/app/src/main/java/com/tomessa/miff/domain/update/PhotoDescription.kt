package com.tomessa.miff.domain.update

/*
 * The optional DESCRIPTION of an update (ticket docs/tickets/description-update.md, DESC-A). A free text written by the person, shown to the members of the
 * Miff above "Photo de ..." in the full-screen photo. PRIVACY, for this file: pure rules, nothing is read or written here, and nothing is ever logged.
 * A description can hold personal data: it is never sent anywhere but Firestore, and never appears in a notification, a log, an event or a crash report.
 *
 * The contract with backend/firestore.rules (`validPhoto`): the key `description` is ABSENT when there is no description (never `null`, never an empty text),
 * otherwise a text of 1 to 140 UTF-16 units (Kotlin `String.length`, an emoji = 2 or more). The same numbers and the same cleaning as the first names
 * (ProfileForm), so that iOS and Android agree: a text seen by everybody must not be able to pass for something else (an inverted direction, an invisible text).
 */
object PhotoDescription {
    /** The limit of the rules, in UTF-16 units. */
    const val MAX_LENGTH = 140

    /** The small counter ("125/140") only appears from here, and never before. */
    const val COUNTER_FROM = 120

    /** The text of a field after the cleaning and the limit were applied. [wasCut]: something was dropped because of the limit. */
    data class Limited(
        val text: String,
        val wasCut: Boolean,
    )

    /**
     * What the field keeps while the person types or pastes: control, invisible and direction characters removed, tabs and line breaks become one space each,
     * at most 140 units without ever cutting an emoji. Spaces are kept as typed (the cursor of the person does not jump); they are tidied on publication.
     */
    fun limitInput(raw: String): Limited {
        val cleaned = removeUnsafe(raw)
        if (cleaned.length <= MAX_LENGTH) return Limited(cleaned, wasCut = false)
        return Limited(cutWithoutBreakingASymbol(cleaned, MAX_LENGTH), wasCut = true)
    }

    /**
     * What is published: the cleaned text, repeated spaces reduced to one, spaces around removed, 1 to 140 units. Null when nothing is left (empty, only
     * spaces, only invisible characters): the key is then NOT written (D7, criteria P1 and P2).
     */
    fun normalize(raw: String): String? {
        val text = tidy(raw)
        if (text.isEmpty()) return null
        return if (text.length <=
            MAX_LENGTH
        ) {
            text
        } else {
            cutWithoutBreakingASymbol(text, MAX_LENGTH).trim().ifEmpty { null }
        }
    }

    /**
     * A value read from the network (somebody else wrote it, criterion C5): shown only when it is a text; cleaned like an input; at most 140 units; null when
     * nothing is left. Never a link, never a style: the text is drawn as it is.
     */
    fun fromServer(value: Any?): String? {
        val text = (value as? String) ?: return null
        val tidy = tidy(text)
        // The rules never let more than 140 units in: a longer text was not written by us, and a text cut short would mislead. Ignored.
        return if (tidy.isEmpty() || tidy.length > MAX_LENGTH) null else tidy
    }

    private fun tidy(raw: String): String = removeUnsafe(raw).replace(WHITESPACE, " ").trim()

    fun showCounter(length: Int): Boolean = length >= COUNTER_FROM

    fun isAtLimit(length: Int): Boolean = length >= MAX_LENGTH

    private val WHITESPACE = Regex("[\\p{Z}\\s]+")

    private fun removeUnsafe(raw: String): String {
        val out = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val cp = raw.codePointAt(i)
            i += Character.charCount(cp)
            when {
                // A new line, a tab and their equivalents (the line and paragraph separators too) never stay: one space, so that two words never stick together.
                cp == '\t'.code || cp == '\n'.code || cp == '\r'.code || cp == 0x0B || cp == 0x0C || cp == 0x85 ||
                    cp == 0x2028 || cp == 0x2029 -> {
                    out.append(' ')
                }

                Character.isISOControl(cp) -> {
                    Unit
                }

                isInvisibleOrDirectional(cp) -> {
                    Unit
                }

                // Unpaired surrogates (a half emoji from a broken source) would make the text unreadable for the rules and the screen.
                cp in 0xD800..0xDFFF -> {
                    Unit
                }

                else -> {
                    out.appendCodePoint(cp)
                }
            }
        }
        return out.toString()
    }

    /**
     * Zero-width space, direction marks, embeddings, overrides and isolates, word joiner, byte order mark. U+200D (zero-width joiner) is NOT here: it holds
     * the composed emojis together (a family, a person with a profession).
     */
    private fun isInvisibleOrDirectional(cp: Int): Boolean =
        cp == 0x200B || cp == 0x200E || cp == 0x200F || cp in 0x202A..0x202E || cp == 0x2060 ||
            cp in 0x2066..0x2069 || cp == 0xFEFF

    /**
     * The longest start of [text] of at most [max] units that does not cut a symbol in two: not between the two halves of a pair of surrogates, not before a
     * joiner, a variation selector, a skin tone, a combining mark, a keycap or a tag, not after a joiner (a family, a person with a skin tone), and not
     * between the two letters of a flag.
     */
    internal fun cutWithoutBreakingASymbol(
        text: String,
        max: Int,
    ): String {
        var end = minOf(max, text.length)
        while (end in 1 until text.length && cutsASymbol(text, end)) end--
        return text.substring(0, end)
    }

    private fun cutsASymbol(
        text: String,
        end: Int,
    ): Boolean {
        if (Character.isLowSurrogate(text[end]) && Character.isHighSurrogate(text[end - 1])) return true
        if (text[end - 1].code == ZERO_WIDTH_JOINER_CODE) return true
        val next = text.codePointAt(end)
        if (isContinuation(next)) return true
        // A flag is two regional indicators: cut after an odd one, the second letter would be lost.
        if (isRegionalIndicator(next)) {
            var count = 0
            var i = end
            while (i > 0) {
                val before = text.codePointBefore(i)
                if (!isRegionalIndicator(before)) break
                count++
                i -= Character.charCount(before)
            }
            if (count % 2 == 1) return true
        }
        return false
    }

    private fun isRegionalIndicator(cp: Int) = cp in 0x1F1E6..0x1F1FF

    private fun isContinuation(cp: Int): Boolean {
        if (cp == ZERO_WIDTH_JOINER_CODE || cp == 0xFE0E || cp == 0xFE0F || cp == 0x20E3) return true
        if (cp in 0x1F3FB..0x1F3FF || cp in 0xE0020..0xE007F) return true
        return when (Character.getType(cp).toByte()) {
            Character.NON_SPACING_MARK, Character.ENCLOSING_MARK, Character.COMBINING_SPACING_MARK -> true
            else -> false
        }
    }

    private const val ZERO_WIDTH_JOINER_CODE = 0x200D
}
