package com.tomessa.miff.domain.miff

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The name of a Miff (ticket Miff 5.2, criteria C2 and C3) and the pause after 5 wrong codes (criterion J5). */
class MiffNameAndLimiterTest {
    @Test
    fun name_needsTwoCharacters_afterTrimming() {
        assertFalse(MiffName.isValid(""))
        assertFalse(MiffName.isValid("a"))
        assertFalse(MiffName.isValid("  a  "))
        assertFalse(MiffName.isValid("   "))
        assertTrue(MiffName.isValid("ab"))
        assertTrue(MiffName.isValid("  ab  "))
    }

    @Test
    fun name_isStoredWithoutTheSpacesAtBothEnds_andKeepsTheMiddleOnes() {
        assertEquals("Les  Dupont", MiffName.stored("  Les  Dupont "))
    }

    @Test
    fun name_stopsAt40_andSaysSo() {
        val forty = "a".repeat(40)
        assertEquals(NameInput(forty, limitReached = true), MiffName.limit(forty))
        assertEquals(NameInput(forty, limitReached = true), MiffName.limit(forty + "b"))
        assertEquals(NameInput("a".repeat(39), limitReached = false), MiffName.limit("a".repeat(39)))
        assertTrue(MiffName.isValid(forty))
    }

    @Test
    fun name_neverMoreThan40UnitsStored_emojisIncluded_andNeverCutInTheMiddleOfAnEmoji() {
        val house = "🏠" // an emoji: 2 UTF-16 units
        val typed = "a".repeat(39) + house
        val limited = MiffName.limit(typed)
        // 39 + 2 = 41 units: the emoji does not fit, and it is not cut in half.
        assertEquals("a".repeat(39), limited.text)
        assertTrue(limited.limitReached)
        val twenty = house.repeat(20) // exactly 40 units
        assertEquals(twenty, MiffName.limit(twenty).text)
        assertTrue(MiffName.stored(twenty + house).length <= 40)
    }

    @Test
    fun name_dropsControlCharacters() {
        assertEquals("ab", MiffName.limit("a\u0000\nb").text)
    }

    @Test
    fun serverName_isCleanedOfBidirectionalAndInvisibleCharacters() {
        assertEquals("Chez nous", MiffName.fromServer("\u202EChez\u200B nous\u2066\u0000 "))
        assertEquals("Chez nous", MiffName.fromServer("  Chez nous  "))
    }

    @Test
    fun serverName_withNothingVisibleLeft_isMissing() {
        assertEquals(null, MiffName.fromServer("\u202E\u200B\uFEFF \u2066"))
        assertEquals(null, MiffName.fromServer(""))
    }

    @Test
    fun limiter_pausesAfterFiveFailures_forOneMinute() {
        var now = 0L
        val limiter = JoinAttemptLimiter(nowMillis = { now })
        repeat(4) { limiter.recordFailure() }
        assertFalse(limiter.isPaused())
        limiter.recordFailure()
        assertTrue(limiter.isPaused())
        assertEquals(60_000L, limiter.remainingPauseMillis())
        now = 59_999
        assertTrue(limiter.isPaused())
        now = 60_000
        assertFalse(limiter.isPaused())
    }

    @Test
    fun limiter_aSuccessStartsAgainFromZero() {
        var now = 0L
        val limiter = JoinAttemptLimiter(nowMillis = { now })
        repeat(4) { limiter.recordFailure() }
        limiter.recordSuccess()
        limiter.recordFailure()
        assertFalse("1 failure after a success is not the 5th", limiter.isPaused())
    }

    @Test
    fun limiter_failuresOlderThan10Minutes_areForgotten() {
        var now = 0L
        val limiter = JoinAttemptLimiter(nowMillis = { now })
        repeat(4) { limiter.recordFailure() }
        now = 10 * 60_000L
        limiter.recordFailure()
        assertFalse("the 4 first failures are forgotten after 10 minutes", limiter.isPaused())
    }

    @Test
    fun limiter_afterThePause_aNewSeriesOfFiveIsNeededForAnotherPause() {
        var now = 0L
        val limiter = JoinAttemptLimiter(nowMillis = { now })
        repeat(5) { limiter.recordFailure() }
        now = 61_000
        assertFalse(limiter.isPaused())
        limiter.recordFailure()
        assertFalse(limiter.isPaused())
    }

    @Test
    fun name_isCutBetweenWholeCharacters_aLetterIsNeverSeparatedFromItsAccent() {
        val accented = "e" + 0x0301.toChar() // "é" written as a letter and a combining accent: 2 units, 1 character
        val typed = "a".repeat(39) + accented
        val limited = MiffName.limit(typed)
        assertEquals("the whole accented letter does not fit: it is left out, not cut", "a".repeat(39), limited.text)
        assertTrue(limited.limitReached)
        // It fits when there is room for the whole character.
        assertEquals("a".repeat(38) + accented, MiffName.limit("a".repeat(38) + accented).text)
    }

    @Test
    fun name_removesBidirectionalControlsAndInvisibleCharacters_butKeepsTheJoinersOfEmojiSequences() {
        val rtlOverride = 0x202E.toChar()
        val isolate = 0x2067.toChar()
        val zeroWidthSpace = 0x200B.toChar()
        val joiner = 0x200D.toChar()
        assertEquals("Famille", MiffName.limit("Fam${rtlOverride}ille$isolate$zeroWidthSpace").text)
        assertEquals("ab", MiffName.clean("a" + 0xFEFF.toChar() + 0x2060.toChar() + 0x00AD.toChar() + "b"))
        assertEquals("a${joiner}b", MiffName.clean("a${joiner}b"))
        assertEquals(
            "a name that hides its end cannot be stored",
            "Chez nous",
            MiffName.stored("Chez nous$rtlOverride"),
        )
    }
}
