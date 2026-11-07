package com.tomessa.miff.domain.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pure rules of the battery refresh (PM decision, 2026-11-07): 15 minutes, 5 points, 3 hours, and the dedicated daily counter. */
class BatteryRefreshTest {
    private val minute = 60_000L
    private val hour = 60 * minute
    private val t0 = 1_800_000_000_000L

    @Test
    fun theLimitsAreTheOnesTheProductOwnerAsked() {
        assertEquals(15 * minute, TrackingLimits.BATTERY_MIN_INTERVAL_MILLIS)
        assertEquals(5, TrackingLimits.BATTERY_MIN_DELTA_PCT)
        assertEquals(3 * hour, TrackingLimits.BATTERY_FORCE_AFTER_MILLIS)
        assertEquals(40, TrackingLimits.MAX_BATTERY_WRITES_PER_DAY)
    }

    @Test
    fun theForcedWriteComesBeforeTheFeedHidesTheBattery() {
        // The Feed (PR #105) hides a battery whose status was written more than 6 hours ago.
        assertTrue(TrackingLimits.BATTERY_FORCE_AFTER_MILLIS < 6 * hour)
    }

    @Test
    fun nothingKnownYet_writes() {
        assertTrue(BatteryRefresh.shouldWrite(null, null, 80, t0))
    }

    @Test
    fun neverTwiceInFifteenMinutes_whateverTheLevel() {
        assertFalse(BatteryRefresh.shouldWrite(80, t0, 10, t0 + 14 * minute + 59_000))
        assertFalse(BatteryRefresh.shouldWrite(80, t0, 80, t0))
        assertTrue(BatteryRefresh.shouldWrite(80, t0, 10, t0 + 15 * minute))
    }

    @Test
    fun between15MinutesAnd3Hours_onlyAMoveOf5PointsOrMoreWrites_inBothDirections() {
        val at = t0 + 30 * minute
        assertFalse(BatteryRefresh.shouldWrite(80, t0, 80, at))
        assertFalse(BatteryRefresh.shouldWrite(80, t0, 76, at))
        assertFalse(BatteryRefresh.shouldWrite(80, t0, 84, at))
        assertTrue(BatteryRefresh.shouldWrite(80, t0, 75, at))
        assertTrue(BatteryRefresh.shouldWrite(80, t0, 85, at))
        assertTrue(BatteryRefresh.shouldWrite(20, t0, 100, at))
    }

    @Test
    fun after3Hours_aSteadyLevelIsWrittenAnyway_justBeforeNot() {
        assertFalse(BatteryRefresh.shouldWrite(80, t0, 80, t0 + 3 * hour - 1))
        assertTrue(BatteryRefresh.shouldWrite(80, t0, 80, t0 + 3 * hour))
        assertTrue(BatteryRefresh.shouldWrite(80, t0, 79, t0 + 5 * hour))
    }

    @Test
    fun aPhoneThatDoesNotSayItsLevel_orSaysNonsense_writesNothing() {
        assertFalse(BatteryRefresh.shouldWrite(null, null, null, t0))
        assertFalse(BatteryRefresh.shouldWrite(80, t0, null, t0 + 4 * hour))
        assertFalse(BatteryRefresh.shouldWrite(null, null, 101, t0))
        assertFalse(BatteryRefresh.shouldWrite(null, null, -1, t0))
    }

    @Test
    fun aClockThatWentBack_cannotBeCompared_soItWrites_theBreakerStillApplies() {
        assertTrue(BatteryRefresh.shouldWrite(80, t0, 80, t0 - hour))
    }

    @Test
    fun anAttempt_isAllowedOnceEvery15Minutes_whateverItsOutcome() {
        assertTrue(BatteryRefresh.passAllowed(0L, t0))
        assertFalse(BatteryRefresh.passAllowed(t0, t0 + 14 * minute))
        assertTrue(BatteryRefresh.passAllowed(t0, t0 + 15 * minute))
        assertTrue(BatteryRefresh.passAllowed(t0, t0 - minute))
    }

    @Test
    fun theDedicatedBreaker_stopsAt40_resetsTheNextDay_andDoesNotTouchTheOtherCounters() {
        var state: BreakerState? = null
        repeat(TrackingLimits.MAX_BATTERY_WRITES_PER_DAY) {
            assertTrue(WriteBreaker.canWriteBattery(state, "2026-11-07"))
            state = WriteBreaker.recordBattery(state, "2026-11-07")
        }
        assertFalse(WriteBreaker.canWriteBattery(state, "2026-11-07"))
        // The arrivals and departures keep their whole budget, the positions too.
        assertTrue(WriteBreaker.canWriteStatus(state, "2026-11-07"))
        assertTrue(WriteBreaker.canWritePosition(state, "2026-11-07"))
        assertEquals(0, state!!.statusWrites)
        assertEquals(0, state!!.positionWrites)
        assertTrue(WriteBreaker.canWriteBattery(state, "2026-11-08"))
    }

    @Test
    fun theWrite_isAnUpdateOfThreeKeysOnly_neverACreation_andTheIdentifierOfTheSessionIsMandatory() {
        val device = "AAAAAAAAAAAAAAAAAAAA"
        val write = TrackingWrites.batteryUpdate("miff1", "uid1", 42, device)
        assertEquals(com.tomessa.miff.domain.miff.WriteKind.UPDATE, write.kind)
        assertEquals("miffs/miff1/status/uid1", write.path)
        assertEquals(setOf("batteryPct", "updatedAt", "deviceId"), write.data.keys)
        assertEquals(42, write.data["batteryPct"])
        assertEquals(device, write.data["deviceId"])
        assertTrue(runCatching { TrackingWrites.batteryUpdate("miff1", "uid1", 42, "short") }.isFailure)
        assertTrue(runCatching { TrackingWrites.batteryUpdate("miff1", "uid1", 101, device) }.isFailure)
        assertTrue(runCatching { TrackingWrites.batteryUpdate("miff1", "uid1", -1, device) }.isFailure)
    }

    @Test
    fun theOtherCountersDoNotConsumeTheBatteryOne() {
        var state: BreakerState? = null
        repeat(TrackingLimits.MAX_STATUS_WRITES_PER_DAY) { state = WriteBreaker.recordStatus(state, "2026-11-07") }
        assertTrue(WriteBreaker.canWriteBattery(state, "2026-11-07"))
        assertEquals(0, state!!.batteryWrites)
    }
}
