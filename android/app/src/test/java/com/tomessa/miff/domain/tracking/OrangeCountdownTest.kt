package com.tomessa.miff.domain.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * COUNTDOWN-A (docs/tickets/compte-a-rebours-orange.md): the pure function "(now, my status) -> hidden | visible(time left, duration, fraction, colour)" on the
 * COMMON test vectors of section 2.3, with an injectable clock (the time is a parameter). The end of the green is read from [StatusThresholds], never written here.
 */
class OrangeCountdownTest {
    private val window = StatusThresholds.ORANGE_AFTER_MILLIS
    private val second = 1_000L
    private val minute = 60 * second
    private val reference = 1_800_000_000_000L
    private val anchor = CountdownAnchor(reference)

    /** The clock for a time left of [seconds] seconds before the deadline. */
    private fun nowWhenLeft(seconds: Long) = reference + window - seconds * second

    private fun facts(
        presence: PresenceValue? = PresenceValue.OUT,
        presenceAt: Long? = reference,
        lastUpdate: Long? = null,
        sharing: Boolean = true,
    ) = MemberStatusFacts(sharing, presence, presenceAt, lastUpdate)

    // --- the common vectors of section 2.3 (CD-12, CD-15) -----------------------------------------------------------------------

    private class Vector(
        val leftSeconds: Long,
        val duration: CountdownDuration,
        val fraction: Float,
    )

    private val vectors =
        listOf(
            Vector(7_200, CountdownDuration.Hours(2), 1.000f),
            Vector(7_199, CountdownDuration.Hours(2), 1.000f),
            Vector(7_140, CountdownDuration.HoursMinutes(1, 59), 0.992f),
            Vector(4_320, CountdownDuration.HoursMinutes(1, 12), 0.600f),
            Vector(3_601, CountdownDuration.HoursMinutes(1, 1), 0.508f),
            Vector(3_600, CountdownDuration.Hours(1), 0.500f),
            Vector(3_541, CountdownDuration.Hours(1), 0.500f),
            Vector(3_540, CountdownDuration.Minutes(59), 0.492f),
            Vector(120, CountdownDuration.Minutes(2), 0.017f),
            Vector(61, CountdownDuration.Minutes(2), 0.017f),
            Vector(60, CountdownDuration.LessThanMinute, 0.008f),
            Vector(1, CountdownDuration.LessThanMinute, 0.008f),
        )

    @Test
    fun commonVectors_durationAndFraction() {
        for (v in vectors) {
            val state = OrangeCountdown.at(anchor, nowWhenLeft(v.leftSeconds))
            assertTrue("${v.leftSeconds} s should be visible", state is CountdownState.Visible)
            state as CountdownState.Visible
            assertEquals("duration at ${v.leftSeconds} s", v.duration, state.duration)
            assertEquals("fraction at ${v.leftSeconds} s", v.fraction, state.fraction, 0.0005f)
        }
    }

    @Test
    fun zeroOrLess_isHidden_neverZeroMinutes() {
        assertEquals(CountdownState.Hidden, OrangeCountdown.at(anchor, nowWhenLeft(0)))
        assertEquals(CountdownState.Hidden, OrangeCountdown.at(anchor, nowWhenLeft(-1)))
        assertEquals(CountdownState.Hidden, OrangeCountdown.at(anchor, nowWhenLeft(-3_600)))
    }

    @Test
    fun roundsUpToTheMinute_soNothingShowsZeroBeforeTheDeadline() {
        // 1 ms before the deadline: still "less than a minute", never "0".
        val state = OrangeCountdown.at(anchor, reference + window - 1)
        assertEquals(CountdownState.Visible(1), state)
        assertEquals(CountdownDuration.LessThanMinute, (state as CountdownState.Visible).duration)
    }

    @Test
    fun durationShapes_ofTheTicket() {
        assertEquals(CountdownDuration.Hours(2), OrangeCountdown.duration(120))
        assertEquals(CountdownDuration.HoursMinutes(1, 5), OrangeCountdown.duration(65))
        assertEquals(CountdownDuration.Hours(1), OrangeCountdown.duration(60))
        assertEquals(CountdownDuration.Minutes(59), OrangeCountdown.duration(59))
        assertEquals(CountdownDuration.Minutes(2), OrangeCountdown.duration(2))
        assertEquals(CountdownDuration.LessThanMinute, OrangeCountdown.duration(1))
    }

    // --- the deadline and the level are the same thing (CD-8, CD-10, R3) -------------------------------------------------------

    @Test
    fun beforeTheDeadline_visibleAndGreen_atTheDeadline_hidden_andOneMillisecondAfter_orange() {
        val deadline = reference + window
        val f = facts()
        assertTrue(OrangeCountdown.at(anchor, deadline - 1) is CountdownState.Visible)
        assertEquals(StatusLevel.GREEN, StatusColor.level(f, deadline - 1))
        // The rule of the colour is "more than the end of the green" (decision of the PM, 2026-11-08): at the exact instant it is still green, the encart is
        // already gone (never "0 min"), and one millisecond later the level is orange: an encart is NEVER seen over an orange.
        assertEquals(CountdownState.Hidden, OrangeCountdown.at(anchor, deadline))
        assertEquals(StatusLevel.GREEN, StatusColor.level(f, deadline))
        assertEquals(CountdownState.Hidden, OrangeCountdown.at(anchor, deadline + 1))
        assertEquals(StatusLevel.ORANGE, StatusColor.level(f, deadline + 1))
    }

    @Test
    fun neverVisibleOverANonGreenLevel_acrossTheWholeJourney() {
        val f = facts()
        var t = reference - 5 * minute
        while (t <= reference + 2 * window + 10 * minute) {
            val state = OrangeCountdown.at(anchor, t)
            if (state is CountdownState.Visible) {
                assertEquals("visible at $t implies green", StatusLevel.GREEN, StatusColor.level(f, t))
            }
            if (StatusColor.level(f, t) != StatusLevel.GREEN) {
                assertEquals("level not green at $t implies hidden", CountdownState.Hidden, state)
            }
            t += 7 * second
        }
    }

    @Test
    fun anchorUsesTheReferenceOfTheColour_theMostRecentOfDepartureAndUpdate() {
        val departure = reference
        val update = reference + 30 * minute
        assertEquals(CountdownAnchor(update), OrangeCountdown.anchorOf(facts(presenceAt = departure, lastUpdate = update)))
        assertEquals(CountdownAnchor(departure), OrangeCountdown.anchorOf(facts(presenceAt = departure, lastUpdate = reference - minute)))
        assertEquals(CountdownAnchor(update), OrangeCountdown.anchorOf(facts(presenceAt = null, lastUpdate = update)))
    }

    // --- reset (CD-9) -----------------------------------------------------------------------------------------------------------

    @Test
    fun anUpdateRestartsTheWindow_theAnchorMovesToTheUpdate_andTheDisplayIsTheWholeWindowAgain() {
        val later = nowWhenLeft(600) // 10 minutes left
        assertEquals(CountdownState.Visible(10), OrangeCountdown.at(anchor, later))
        val updated = OrangeCountdown.anchorOf(facts(presenceAt = reference, lastUpdate = later))
        assertEquals(CountdownState.Visible(120), OrangeCountdown.at(updated, later))
        assertEquals(CountdownDuration.Hours(2), (OrangeCountdown.at(updated, later) as CountdownState.Visible).duration)
    }

    @Test
    fun anUpdateFromOrange_makesItComeBackAtTheWholeWindow() {
        val orangeNow = reference + window + 20 * minute
        assertEquals(CountdownState.Hidden, OrangeCountdown.at(anchor, orangeNow))
        val updated = OrangeCountdown.anchorOf(facts(presenceAt = reference, lastUpdate = orangeNow))
        assertEquals(CountdownState.Visible(120), OrangeCountdown.at(updated, orangeNow))
    }

    // --- who sees what: the static conditions (CD-2, CD-4, CD-1) ----------------------------------------------------------------

    @Test
    fun onlyOut_isEligible() {
        assertNotNull(OrangeCountdown.anchorOf(facts(PresenceValue.OUT)))
        assertNull("inside a trusted place", OrangeCountdown.anchorOf(facts(PresenceValue.IN_TRUSTED)))
        assertNull("in a danger zone", OrangeCountdown.anchorOf(facts(PresenceValue.IN_RISK)))
        assertNull("never been through a place / no place", OrangeCountdown.anchorOf(facts(PresenceValue.UNKNOWN)))
        assertNull("on a trip", OrangeCountdown.anchorOf(facts(PresenceValue.TRAVEL)))
        assertNull("no presence", OrangeCountdown.anchorOf(facts(null)))
    }

    @Test
    fun sharingStopped_noStatus_noReference_areNotEligible() {
        assertNull(OrangeCountdown.anchorOf(facts(sharing = false)))
        assertNull(OrangeCountdown.anchorOf(null))
        assertNull("no reference known", OrangeCountdown.anchorOf(facts(presenceAt = null, lastUpdate = null)))
    }

    @Test
    fun noAnchor_isHidden_andNothingToWaitFor() {
        assertEquals(CountdownState.Hidden, OrangeCountdown.at(null, reference))
        assertNull(OrangeCountdown.millisUntilNextChange(null, reference))
    }

    // --- a clock set back (CD-7) ------------------------------------------------------------------------------------------------

    @Test
    fun referenceInTheFuture_isCappedAtTheWholeWindow() {
        val future = CountdownAnchor(reference + 3 * window)
        val state = OrangeCountdown.at(future, reference)
        assertEquals(CountdownState.Visible(120), state)
        assertEquals(CountdownDuration.Hours(2), (state as CountdownState.Visible).duration)
        assertEquals(1f, state.fraction, 0.0001f)
    }

    @Test
    fun absurdReference_isHidden() {
        assertEquals(CountdownState.Hidden, OrangeCountdown.at(CountdownAnchor(-5), reference))
        assertEquals(CountdownState.Hidden, OrangeCountdown.at(CountdownAnchor(Long.MAX_VALUE), reference))
    }

    // --- the minute boundaries of the DEADLINE (CD-18) --------------------------------------------------------------------------

    @Test
    fun nextChange_isAlignedOnTheDeadline_notOnTheWallClock() {
        // 4 min 20 s left: the display says "5 min" until 4 min exactly are left, i.e. for 20 more seconds.
        assertEquals(20 * second, OrangeCountdown.millisUntilNextChange(anchor, nowWhenLeft(260)))
        // Exactly on a boundary (4 min left): the next one is a whole minute away.
        assertEquals(minute, OrangeCountdown.millisUntilNextChange(anchor, nowWhenLeft(240)))
        // The last wait ends at the deadline itself (the encart goes).
        assertEquals(30 * second, OrangeCountdown.millisUntilNextChange(anchor, nowWhenLeft(30)))
    }

    @Test
    fun nextChange_isExact_theDisplayIsConstantUntilThenAndChangesThen() {
        var now = reference
        var wakeUps = 0
        var state = OrangeCountdown.at(anchor, now)
        // Walk the whole window by following the waits: one wake-up per minute at the most, and the display never lags.
        while (true) {
            val wait = OrangeCountdown.millisUntilNextChange(anchor, now) ?: break
            assertTrue("a wait is at least 1 ms and at most a minute", wait in 1..minute)
            assertEquals("constant until the wake-up", state, OrangeCountdown.at(anchor, now + wait - 1))
            now += wait
            wakeUps++
            val next = OrangeCountdown.at(anchor, now)
            assertTrue("it changes at the wake-up", next != state)
            state = next
        }
        assertEquals(CountdownState.Hidden, state)
        // 120 minutes of window: 120 changes (one per minute), the last one being the disappearance.
        assertEquals(120, wakeUps)
    }

    @Test
    fun nextChange_withAReferenceInTheFuture_waitsUntilTheClockReachesIt() {
        val future = CountdownAnchor(reference + 10 * minute)
        // Capped at the whole window while the clock is behind the reference: the first change is one minute after the clock reaches it.
        assertEquals(11 * minute, OrangeCountdown.millisUntilNextChange(future, reference))
    }

    // --- the border (CD-15, CD-16) ----------------------------------------------------------------------------------------------

    @Test
    fun warmth_isZeroUpToAQuarter_thenGoesToOne_neverBeyond() {
        assertEquals(0f, OrangeCountdown.warmth(1f), 0f)
        assertEquals(0f, OrangeCountdown.warmth(0.26f), 0f)
        assertEquals(0f, OrangeCountdown.warmth(OrangeCountdown.WARMTH_FROM_FRACTION), 0f)
        assertEquals(0.5f, OrangeCountdown.warmth(0.125f), 0.0001f)
        assertEquals(1f, OrangeCountdown.warmth(0f), 0f)
        assertEquals(1f, OrangeCountdown.warmth(-1f), 0f)
    }

    @Test
    fun theLastHalfHour_isWhereTheBorderWarmsUp() {
        // Over 30 minutes left: pure green. At 15 minutes left: half way. At less than a minute: almost fully warm.
        assertEquals(0f, (OrangeCountdown.at(anchor, nowWhenLeft(31 * 60)) as CountdownState.Visible).warmth, 0f)
        assertEquals(0.5f, (OrangeCountdown.at(anchor, nowWhenLeft(15 * 60)) as CountdownState.Visible).warmth, 0.001f)
        assertTrue((OrangeCountdown.at(anchor, nowWhenLeft(30)) as CountdownState.Visible).warmth > 0.95f)
    }

    // --- the window is the constant of the thresholds ---------------------------------------------------------------------------

    @Test
    fun theWindowIsTheEndOfTheGreen() {
        assertEquals(StatusThresholds.ORANGE_AFTER_MILLIS, OrangeCountdown.WINDOW_MILLIS)
    }

    @Test
    fun toStringShowsNothing() {
        assertEquals("CountdownAnchor(redacted)", anchor.toString())
    }
}
