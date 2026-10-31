package com.tomessa.miff.domain.miff

import java.text.BreakIterator

/**
 * The name of a Miff (ticket Miff 5.2, decision QM6): 2 to 40 characters once the spaces at both ends are removed. The rules
 * of Firestore accept 1 to 40; the minimum of 2 is a rule of the screen.
 *
 * The 40 are counted in UTF-16 units (what Kotlin's `length` gives), which is what the rules count (emulator test
 * `17-nom-miff-longueur.test.js`: an emoji takes 2 units). The text is cut on a boundary between two whole characters as the
 * reader sees them (a letter with its accent, an emoji with its modifiers), never in the middle of one.
 *
 * Invisible characters that change how text is drawn or read are removed, in what is typed AND in what comes from the
 * server (a name is shown to people who only hold the code, so another phone may have written anything): control
 * characters, the bidirectional controls (they can make a name read backwards or hide the end of a sentence), zero-width
 * spaces, the word joiner, the byte order mark and the soft hyphen. The two joiners U+200C and U+200D stay: emoji sequences
 * and several scripts need them.
 */
object MiffName {
    const val MIN_LENGTH = 2
    const val MAX_LENGTH = 40

    /** Bidirectional controls and invisible format characters that are never part of a name. */
    private val INVISIBLE: Set<Char> =
        buildSet {
            add(0x061C.toChar()) // Arabic letter mark
            add(0x00AD.toChar()) // soft hyphen
            add(0x200B.toChar()) // zero-width space
            add(0x200E.toChar()) // left-to-right mark
            add(0x200F.toChar()) // right-to-left mark
            (0x202A..0x202E).forEach { add(it.toChar()) } // embeddings, overrides, pop
            add(0x2060.toChar()) // word joiner
            (0x2066..0x2069).forEach { add(it.toChar()) } // isolates
            add(0xFEFF.toChar()) // byte order mark
        }

    /** [text] without control characters and invisible format characters. Used on what is typed and on what is read. */
    fun clean(text: String): String = text.filter { !it.isISOControl() && it !in INVISIBLE }

    /**
     * What the field keeps of [typed]: cleaned, at most [MAX_LENGTH] units, cut between two whole characters.
     * [limitReached] is true when something was cut or the field is full, to show "Limite de 40 caractères atteinte."
     */
    fun limit(typed: String): NameInput {
        val clean = clean(typed)
        if (clean.length <= MAX_LENGTH) return NameInput(clean, limitReached = clean.length == MAX_LENGTH)
        return NameInput(clean.substring(0, cutPoint(clean)), limitReached = true)
    }

    /** The last boundary between two whole characters at or before [MAX_LENGTH] units. */
    private fun cutPoint(text: String): Int {
        val boundaries = BreakIterator.getCharacterInstance()
        boundaries.setText(text)
        val end = boundaries.preceding(MAX_LENGTH + 1)
        if (end > 0) return end
        // A single "character" longer than the limit: fall back on the last whole code point.
        var fallback = MAX_LENGTH
        if (Character.isHighSurrogate(text[fallback - 1]) && Character.isLowSurrogate(text[fallback])) fallback--
        return fallback
    }

    /** The name as it is stored: the typed text without the spaces at both ends. */
    fun stored(typed: String): String = limit(typed).text.trim()

    fun isValid(typed: String): Boolean = stored(typed).length in MIN_LENGTH..MAX_LENGTH
}

data class NameInput(
    val text: String,
    val limitReached: Boolean,
) {
    override fun toString(): String = "NameInput(redacted)"
}
