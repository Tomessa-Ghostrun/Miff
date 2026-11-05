package com.tomessa.miff.domain.tracking

import com.google.firebase.Timestamp
import com.tomessa.miff.domain.miff.LocationAccess
import com.tomessa.miff.domain.miff.WriteKind
import com.tomessa.miff.domain.permission.PermissionSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/** The limits of the automatic writes (criteria STA3, STA4, POS8) and the documents they build (criteria STA1, STA2, POS6). Pure. */
class TrackingLimitsTest {
    private val minute = 60_000L
    private val hour = 60 * minute
    private val now = 1_800_000_000_000L

    // --- Time of the event (STA3) --------------------------------------------------------------------------------------------------------

    @Test
    fun anEventTime_isKept_whenRecent() {
        assertEquals(now - 10 * minute, EventTime.forWrite(now - 10 * minute, now))
    }

    @Test
    fun anEventTimeInTheFuture_isCappedAtNow() {
        assertEquals(now, EventTime.forWrite(now + 3 * hour, now))
    }

    @Test
    fun anEventOlderThanTheRulesAccept_isGivenUp() {
        assertNull(EventTime.forWrite(now - 24 * hour, now))
        assertNull(EventTime.forWrite(now - 30 * hour, now))
        assertNotNull(EventTime.forWrite(now - 23 * hour, now))
    }

    // --- The breaker (STA4) ---------------------------------------------------------------------------------------------------------------

    private val zone = ZoneId.of("Europe/Paris")

    @Test
    fun theBreakerOpensAfterTheMaximumOfStatusWrites_andClosesTheNextDay() {
        val today = WriteBreaker.dayKey(now, zone)
        var state: BreakerState? = null
        repeat(TrackingLimits.MAX_STATUS_WRITES_PER_DAY) {
            assertTrue(WriteBreaker.canWriteStatus(state, today))
            state = WriteBreaker.recordStatus(state, today)
        }
        assertFalse(WriteBreaker.canWriteStatus(state, today))
        val tomorrow = WriteBreaker.dayKey(now + 24 * hour, zone)
        assertTrue(WriteBreaker.canWriteStatus(state, tomorrow))
    }

    @Test
    fun theBreakerOfThePositionsIsSeparateFromTheOneOfTheStatuses() {
        val today = WriteBreaker.dayKey(now, zone)
        var state: BreakerState? = null
        repeat(TrackingLimits.MAX_POSITION_WRITES_PER_DAY) { state = WriteBreaker.recordPosition(state, today) }
        assertFalse(WriteBreaker.canWritePosition(state, today))
        assertTrue(WriteBreaker.canWriteStatus(state, today))
    }

    @Test
    fun theDayIsTheDayOfThePhone() {
        // 23:30 in Paris on 3 June is still 3 June there, even though it is already 4 June in Tokyo.
        val millis =
            java.time.ZonedDateTime
                .of(2026, 6, 3, 23, 30, 0, 0, zone)
                .toInstant()
                .toEpochMilli()
        assertEquals("2026-06-03", WriteBreaker.dayKey(millis, zone))
        assertEquals("2026-06-04", WriteBreaker.dayKey(millis, ZoneId.of("Asia/Tokyo")))
    }

    @Test
    fun theValuesAreTheOnesDocumented() {
        assertEquals(60, TrackingLimits.MAX_STATUS_WRITES_PER_DAY)
        assertEquals(150, TrackingLimits.MAX_POSITION_WRITES_PER_DAY)
        assertEquals(15 * minute, TrackingLimits.POSITION_MIN_INTERVAL_MILLIS)
        assertEquals(250.0, TrackingLimits.POSITION_MIN_DISTANCE_M, 0.0)
        assertEquals(250f, TrackingLimits.MOVE_UPDATE_DISTANCE_M, 0f)
    }

    // --- The guard of frequency of the position (POS8) --------------------------------------------------------------------------------------

    private val lat = 48.8566
    private val lng = 2.3522
    private val metre = 1.0 / 111_195.0

    @Test
    fun theFirstPositionIsWritten() {
        assertTrue(PositionThrottle.shouldWrite(null, lat, lng, now))
    }

    @Test
    fun underTheFloor_neverWritten_evenIfTheMoveIsBig() {
        val last = WrittenPosition(lat, lng, now)
        assertFalse(PositionThrottle.shouldWrite(last, lat + 5_000 * metre, lng, now + minute))
    }

    @Test
    fun afterFifteenMinutes_written_evenIfNotMoved() {
        val last = WrittenPosition(lat, lng, now)
        assertFalse(PositionThrottle.shouldWrite(last, lat, lng, now + 14 * minute + 59_000))
        assertTrue(PositionThrottle.shouldWrite(last, lat, lng, now + 15 * minute))
    }

    @Test
    fun beforeFifteenMinutes_writtenOnlyIfMovedByAtLeast250m() {
        val last = WrittenPosition(lat, lng, now)
        assertFalse(PositionThrottle.shouldWrite(last, lat + 200 * metre, lng, now + 5 * minute))
        assertTrue(PositionThrottle.shouldWrite(last, lat + 260 * metre, lng, now + 5 * minute))
    }

    @Test
    fun aClockThatWentBack_isWritten() {
        assertTrue(PositionThrottle.shouldWrite(WrittenPosition(lat, lng, now), lat, lng, now - hour))
    }

    // --- The documents written (STA1, STA2, POS6) -------------------------------------------------------------------------------------------

    @Test
    fun aStatusUpdate_hasNoCoordinate_andOnlyTheFieldsThatChange() {
        val fact = StatusFact(PresenceValue.OUT, "home", now - 5 * minute, setLastUpdate = false)
        val write = TrackingWrites.statusUpdate("miffA", "uid1", fact, now - 5 * minute, 80, LocationAccess.BACKGROUND)
        assertEquals("miffs/miffA/status/uid1", write.path)
        assertEquals(WriteKind.UPDATE, write.kind)
        assertEquals(
            setOf("presence", "placeId", "presenceAt", "batteryPct", "locationAccess", "escalationDueAt", "updatedAt"),
            write.data.keys,
        )
        // updateText, hasPhoto and lastUpdateAt are NOT in the update: they stay as the person left them.
        assertFalse("updateText" in write.data || "hasPhoto" in write.data || "lastUpdateAt" in write.data)
        assertEquals("out", write.data["presence"])
        assertEquals("home", write.data["placeId"])
        assertEquals(80, write.data["batteryPct"])
        assertEquals("background", write.data["locationAccess"])
        assertNull(write.data["escalationDueAt"])
        assertEquals(Timestamp(java.util.Date(now - 5 * minute)), write.data["presenceAt"])
        assertNoCoordinate(write.data.keys)
    }

    @Test
    fun anArrivalInATrustedZone_alsoMovesTheLastUpdate_toTheEventTime() {
        val fact = StatusFact(PresenceValue.IN_TRUSTED, "home", now, setLastUpdate = true)
        val write = TrackingWrites.statusUpdate("miffA", "uid1", fact, now, null, LocationAccess.BACKGROUND)
        assertEquals(Timestamp(java.util.Date(now)), write.data["lastUpdateAt"])
        assertNull(write.data["batteryPct"])
    }

    @Test
    fun aBatteryOutOfRange_isNotWritten() {
        val fact = StatusFact(PresenceValue.OUT, "home", now, false)
        assertNull(TrackingWrites.statusUpdate("m", "u", fact, now, 140, LocationAccess.BACKGROUND).data["batteryPct"])
        assertNull(TrackingWrites.statusUpdate("m", "u", fact, now, -1, LocationAccess.BACKGROUND).data["batteryPct"])
    }

    @Test
    fun anAccessUpdate_changesTheAccessAndTheBatteryOnly() {
        val write = TrackingWrites.accessUpdate("m", "u", 50, LocationAccess.FOREGROUND)
        assertEquals(setOf("batteryPct", "locationAccess", "updatedAt"), write.data.keys)
    }

    @Test
    fun thePosition_isLatLngAndTheMeasureTime_withFiveDecimals_nothingElse() {
        val write = TrackingWrites.position("miffA", "uid1", 48.856614123, 2.352222987, now)
        assertEquals("miffs/miffA/positions/uid1", write.path)
        assertEquals(WriteKind.SET, write.kind)
        assertEquals(setOf("lat", "lng", "measuredAt"), write.data.keys)
        assertEquals(48.85661, write.data["lat"] as Double, 0.0)
        assertEquals(2.35222, write.data["lng"] as Double, 0.0)
        assertEquals(Timestamp(java.util.Date(now)), write.data["measuredAt"])
    }

    @Test
    fun roundingToFiveDecimals() {
        assertEquals(1.23457, Geo.round5(1.234567), 0.0)
        assertEquals(-0.00001, Geo.round5(-0.0000149), 0.0)
    }

    private fun assertNoCoordinate(keys: Set<String>) {
        assertFalse(
            keys.any {
                it.equals("lat", true) || it.equals("lng", true) || it.contains("coord", true) ||
                    it.contains("address", true)
            },
        )
    }

    // --- locationAccess (6.7) ---------------------------------------------------------------------------------------------------------------

    private fun snapshot(
        fine: Boolean = false,
        coarse: Boolean = false,
        background: Boolean = false,
    ) = PermissionSnapshot(
        sdk = 33,
        fineLocation = fine,
        coarseLocation = coarse,
        backgroundLocation = background,
        notifications = true,
    )

    @Test
    fun locationAccess_hasTheFourValuesOfTheTicket() {
        assertEquals(LocationAccess.BACKGROUND, LocationAccessRule.of(snapshot(true, true, true), true))
        assertEquals(LocationAccess.FOREGROUND, LocationAccessRule.of(snapshot(true, true, false), true))
        assertEquals(LocationAccess.FOREGROUND, LocationAccessRule.of(snapshot(false, true, true), true))
        assertEquals(LocationAccess.UNKNOWN, LocationAccessRule.of(snapshot(), true))
        // The location of the phone itself switched off: denied, whatever the permission says.
        assertEquals(LocationAccess.DENIED, LocationAccessRule.of(snapshot(true, true, true), false))
    }
}
