package com.tomessa.miff.domain.tracking

import com.tomessa.miff.domain.place.PlaceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FEED-REFRESH-POSITION: the pure rules of "my presence checked again from ONE position" ([PresenceMachine.insideAfterReading], [PresenceMachine.afterReading]) and the limit of
 * one reading per 30 s ([PullLimit]). iOS must reproduce these cases (docs/tickets/feed-refresh-position.md).
 */
class PresenceFromReadingTest {
    private val lat = 48.8566
    private val lng = 2.3522

    private fun zone(
        id: String,
        dLat: Double,
        type: PlaceType = PlaceType.TRUSTED,
        radius: Int = 100,
    ) = TrackedZone("$id|k", id, lat + dLat, lng, radius, type, setOf("m"))

    private val home = zone("home", 0.0)
    private val work = zone("work", 0.02)
    private val risk = zone("risk", 0.04, PlaceType.RISK)
    private val all = listOf(home, work, risk)

    // --- insideAfterReading ---------------------------------------------------------------------------------------------------------------

    @Test
    fun aPositionInsideTheRadius_entersTheZone_atTheInstantItIsNoticed() {
        val inside = PresenceMachine.insideAfterReading(all, emptyMap(), lat, lng, 1_000L)
        assertEquals(mapOf(home.key to 1_000L), inside)
    }

    @Test
    fun aZoneAlreadyEntered_keepsItsRealTimeOfEntry() {
        val inside = PresenceMachine.insideAfterReading(all, mapOf(home.key to 500L), lat, lng, 1_000L)
        assertEquals(500L, inside[home.key])
    }

    @Test
    fun onTheEdgeOfTheRadius_countsAsInside() {
        // 100 m radius, the position about 99 m away.
        val inside = PresenceMachine.insideAfterReading(all, emptyMap(), lat + 0.00089, lng, 1_000L)
        assertTrue(home.key in inside)
    }

    @Test
    fun justOutsideTheRadius_isNotAnArrival() {
        // About 130 m from the centre of a 100 m place.
        val inside = PresenceMachine.insideAfterReading(all, emptyMap(), lat + 0.00117, lng, 1_000L)
        assertTrue(inside.isEmpty())
    }

    @Test
    fun betweenTheRadiusAndTheMargin_aZoneThePhoneBelievedIn_isKept() {
        // About 180 m: outside 100 m, inside 100 + 100 m.
        val inside = PresenceMachine.insideAfterReading(all, mapOf(home.key to 500L), lat + 0.0016, lng, 1_000L)
        assertEquals(500L, inside[home.key])
    }

    @Test
    fun beyondTheMargin_aZoneThePhoneBelievedIn_isLeft_andOnlyThatOne() {
        // About 250 m from home; work (the person was believed there too, by a stale state) is 2 km away: both are left, a zone never believed in is not touched.
        val before = mapOf(home.key to 500L, work.key to 600L)
        val inside = PresenceMachine.insideAfterReading(all, before, lat + 0.00225, lng, 1_000L)
        assertTrue(inside.isEmpty())
        // The input is never modified.
        assertEquals(2, before.size)
    }

    @Test
    fun theMarginNeverCreatesAnArrival() {
        // 150 m from home, never believed in: still not inside.
        val inside = PresenceMachine.insideAfterReading(all, emptyMap(), lat + 0.00135, lng, 1_000L)
        assertFalse(home.key in inside)
    }

    @Test
    fun twoZonesAtOnce_bothEntered() {
        val twin = zone("twin", 0.0001)
        val inside = PresenceMachine.insideAfterReading(listOf(home, twin), emptyMap(), lat, lng, 1_000L)
        assertEquals(setOf(home.key, twin.key), inside.keys)
    }

    // --- afterReading -----------------------------------------------------------------------------------------------------------------------

    private fun derived(
        presence: PresenceValue,
        placeId: String? = null,
    ) = DerivedPresence(presence, placeId)

    @Test
    fun anArrival_isToldAtNow_andATrustedOneIsAlsoAnUpdate() {
        val (stored, fact) =
            PresenceMachine.afterReading(
                derived(PresenceValue.OUT, "home"),
                derived(PresenceValue.IN_TRUSTED, "work"),
                9_000L,
            )!!
        assertEquals(derived(PresenceValue.IN_TRUSTED, "work"), stored)
        assertEquals(StatusFact(PresenceValue.IN_TRUSTED, "work", 9_000L, setLastUpdate = true), fact)
    }

    @Test
    fun anArrivalInADangerZone_isNotAnUpdate() {
        val (_, fact) =
            PresenceMachine.afterReading(
                derived(PresenceValue.IN_TRUSTED, "home"),
                derived(PresenceValue.IN_RISK, "risk"),
                9_000L,
            )!!
        assertEquals(StatusFact(PresenceValue.IN_RISK, "risk", 9_000L, setLastUpdate = false), fact)
    }

    @Test
    fun aDepartureTheGeofencingDidNotReport_isUnknownAtNow_neverAnOut_andNotAnUpdate() {
        val (stored, fact) =
            PresenceMachine.afterReading(
                derived(PresenceValue.IN_TRUSTED, "home"),
                derived(PresenceValue.OUT),
                9_000L,
            )!!
        assertEquals(derived(PresenceValue.UNKNOWN), stored)
        assertEquals(StatusFact(PresenceValue.UNKNOWN, null, 9_000L, setLastUpdate = false), fact)
        // A departure from a danger zone too.
        val (_, fromRisk) =
            PresenceMachine.afterReading(derived(PresenceValue.IN_RISK, "risk"), derived(PresenceValue.OUT), 9_000L)!!
        assertEquals(PresenceValue.UNKNOWN, fromRisk.presence)
    }

    @Test
    fun nothingChanges_nothingIsToldAndNothingIsConfirmed() {
        assertNull(
            PresenceMachine.afterReading(
                derived(PresenceValue.IN_TRUSTED, "home"),
                derived(PresenceValue.IN_TRUSTED, "home"),
                9_000L,
            ),
        )
        assertNull(
            PresenceMachine.afterReading(
                derived(PresenceValue.IN_RISK, "risk"),
                derived(PresenceValue.IN_RISK, "risk"),
                9_000L,
            ),
        )
        // Out stays out, whichever place was named when the person left.
        assertNull(PresenceMachine.afterReading(derived(PresenceValue.OUT, "home"), derived(PresenceValue.OUT), 9_000L))
    }

    @Test
    fun noOutIsInvented_forSomebodyWithNoPresenceYetOrAnUnknownOne() {
        assertNull(PresenceMachine.afterReading(null, derived(PresenceValue.OUT), 9_000L))
        assertNull(PresenceMachine.afterReading(derived(PresenceValue.UNKNOWN), derived(PresenceValue.OUT), 9_000L))
    }

    @Test
    fun aFirstPresence_inAPlace_isTold() {
        val (_, fact) = PresenceMachine.afterReading(null, derived(PresenceValue.IN_TRUSTED, "home"), 9_000L)!!
        assertEquals(PresenceValue.IN_TRUSTED, fact.presence)
        val (_, afterUnknown) =
            PresenceMachine.afterReading(
                derived(PresenceValue.UNKNOWN),
                derived(PresenceValue.IN_TRUSTED, "home"),
                9_000L,
            )!!
        assertEquals("home", afterUnknown.placeId)
    }

    @Test
    fun leavingOnePlaceForAnother_isAnArrivalAtTheOther() {
        val (_, fact) =
            PresenceMachine.afterReading(
                derived(PresenceValue.IN_TRUSTED, "home"),
                derived(PresenceValue.IN_TRUSTED, "work"),
                9_000L,
            )!!
        assertEquals("work", fact.placeId)
    }

    // --- The limit ---------------------------------------------------------------------------------------------------------------------------

    @Test
    fun oneReadingEvery30Seconds() {
        assertTrue(PullLimit.allowed(null, 1_000L))
        assertFalse(PullLimit.allowed(1_000L, 30_999L))
        assertTrue(PullLimit.allowed(1_000L, 31_000L))
        assertEquals(30_000L, TrackingLimits.PULL_MIN_INTERVAL_MILLIS)
    }

    @Test
    fun aClockThatWentBack_neverBlocksForGood() {
        assertTrue(PullLimit.allowed(100_000L, 50_000L))
    }
}
