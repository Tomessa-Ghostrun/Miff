package com.tomessa.miff.ui.diagnostic.access

import com.tomessa.miff.ui.diagnostic.access.HoldGestureTracker.State
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure rules of the 10 s hold (PM request of 2026-11-12): duration 10 s, tolerance of movement 20 dp, cancellations. No Android. */
class HoldGestureTrackerTest {
    private val tolerance = 20f

    private fun tracker(startMillis: Long = 1_000L) = HoldGestureTracker(100f, 200f, startMillis, tolerance)

    @Test
    fun theConstantsAreTheOnesOfTheRequest() {
        assertEquals(10_000L, HoldGestureRules.HOLD_MILLIS)
        assertEquals(20f, HoldGestureRules.MOVE_TOLERANCE_DP, 0f)
        assertEquals("DIAGNOSTICHOLDGESTURE", DIAGNOSTIC_HOLD_MARKER)
    }

    @Test
    fun stillHolding_justBeforeTenSeconds() {
        val t = tracker()
        assertEquals(State.HOLDING, t.onTime(1_000L + 9_999L))
    }

    @Test
    fun completed_atExactlyTenSeconds_andStaysCompleted() {
        val t = tracker()
        assertEquals(State.COMPLETED, t.onTime(1_000L + 10_000L))
        // Later events do not undo it.
        assertEquals(State.COMPLETED, t.onPointers(0, 100f, 200f))
        assertEquals(State.COMPLETED, t.onTime(99_999L))
    }

    @Test
    fun aSmallJitterWithinTwentyDp_keepsTheHold() {
        val t = tracker()
        assertEquals(State.HOLDING, t.onPointers(1, 100f + 12f, 200f + 12f)) // about 17 dp
        assertEquals(State.HOLDING, t.onPointers(1, 100f + 20f, 200f)) // exactly 20 dp is still tolerated
        assertEquals(State.COMPLETED, t.onTime(11_000L))
    }

    @Test
    fun movingMoreThanTwentyDp_cancels_evenIfItComesBack() {
        val t = tracker()
        assertEquals(State.CANCELLED, t.onPointers(1, 100f, 200f + 20.5f))
        assertEquals(State.CANCELLED, t.onPointers(1, 100f, 200f))
        assertEquals("a cancelled hold never completes", State.CANCELLED, t.onTime(60_000L))
    }

    @Test
    fun liftingTheFinger_cancels() {
        val t = tracker()
        assertEquals(State.CANCELLED, t.onPointers(0, 100f, 200f))
        assertEquals(State.CANCELLED, t.onTime(60_000L))
    }

    @Test
    fun aSecondFinger_cancels() {
        val t = tracker()
        assertEquals(State.CANCELLED, t.onPointers(2, 100f, 200f))
        assertEquals(State.CANCELLED, t.onTime(60_000L))
    }

    @Test
    fun theClockOfTheHoldStartsAtTheTouch() {
        val t = tracker(startMillis = 50_000L)
        assertEquals(State.HOLDING, t.onTime(55_000L))
        assertEquals(State.COMPLETED, t.onTime(60_000L))
    }
}
