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
}
