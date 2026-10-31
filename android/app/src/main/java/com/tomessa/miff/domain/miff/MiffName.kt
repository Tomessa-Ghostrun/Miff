package com.tomessa.miff.domain.miff

/**
 * The name of a Miff (ticket Miff 5.2, decision QM6): 2 to 40 characters once the spaces at both ends are removed. The rules
 * of Firestore accept 1 to 40; the minimum of 2 is a rule of the screen.
 *
 * The 40 are counted in UTF-16 units (what Kotlin's `length` gives), which is never fewer than the characters the rules may
 * count: an emoji takes 2 units here, so a name accepted here is accepted by the rules whatever way they count (the
 * emulator test `17-nom-miff-longueur.test.js` documents it). The text never gets cut in the middle of an emoji.
 */
object MiffName {
    const val MIN_LENGTH = 2
    const val MAX_LENGTH = 40

    /**
     * What the field keeps of [typed]: no control character, at most [MAX_LENGTH] units, never half of a surrogate pair.
     * [limitReached] is true when something was cut or the field is full, to show "Limite de 40 caractères atteinte."
     */
    fun limit(typed: String): NameInput {
        val clean = typed.filter { !it.isISOControl() }
        if (clean.length <= MAX_LENGTH) return NameInput(clean, limitReached = clean.length == MAX_LENGTH)
        var end = MAX_LENGTH
        if (Character.isHighSurrogate(clean[end - 1]) && Character.isLowSurrogate(clean[end])) end--
        return NameInput(clean.substring(0, end), limitReached = true)
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
