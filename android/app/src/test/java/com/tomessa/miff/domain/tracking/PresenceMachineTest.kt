package com.tomessa.miff.domain.tracking

import com.tomessa.miff.domain.place.PlaceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** From the events of the zones to the status of ONE Miff (ticket 6.6, criteria DET2, DET3, STA1). Pure. */
class PresenceMachineTest {
    private val home = ZoneRef("home", "home", PlaceType.TRUSTED)
    private val work = ZoneRef("work", "work", PlaceType.TRUSTED)
    private val danger = ZoneRef("danger", "danger", PlaceType.RISK)
    private val zones = listOf(home, work, danger)

    private fun enter(
        id: String,
        at: Long,
        inside: Map<String, Long>,
        last: DerivedPresence?,
        zones: List<ZoneRef> = this.zones,
    ) = PresenceMachine.onTransition(TransitionKind.ENTER, id, at, zones, inside + (id to at), last)

    private fun exit(
        id: String,
        at: Long,
        inside: Map<String, Long>,
        last: DerivedPresence?,
        zones: List<ZoneRef> = this.zones,
    ) = PresenceMachine.onTransition(TransitionKind.EXIT, id, at, zones, inside - id, last)

    @Test
    fun arrivalInATrustedZone_isInTrusted_withThePlace_theEventTime_andAnUpdate() {
        val (derived, fact) = enter("home", 1_000, emptyMap(), null)!!
        assertEquals(DerivedPresence(PresenceValue.IN_TRUSTED, "home"), derived)
        assertEquals(StatusFact(PresenceValue.IN_TRUSTED, "home", 1_000, setLastUpdate = true), fact)
    }

    @Test
    fun departureFromATrustedZone_isOut_withThePlaceLeft_andLastUpdateIsNotTouched() {
        val (derived, fact) =
            exit(
                "home",
                5_000,
                mapOf("home" to 1_000L),
                DerivedPresence(PresenceValue.IN_TRUSTED, "home"),
            )!!
        assertEquals(DerivedPresence(PresenceValue.OUT, "home"), derived)
        assertEquals(StatusFact(PresenceValue.OUT, "home", 5_000, setLastUpdate = false), fact)
    }

    @Test
    fun arrivalInADangerZone_withNoTrustedZone_isInRisk_withoutAnUpdate() {
        val (derived, fact) = enter("danger", 2_000, emptyMap(), DerivedPresence(PresenceValue.OUT, "home"))!!
        assertEquals(DerivedPresence(PresenceValue.IN_RISK, "danger"), derived)
        assertFalse(fact.setLastUpdate)
    }

    @Test
    fun departureFromADangerZone_withNoTrustedZone_isOut_withTheZoneLeft() {
        val (derived, _) =
            exit(
                "danger",
                9_000,
                mapOf("danger" to 2_000L),
                DerivedPresence(PresenceValue.IN_RISK, "danger"),
            )!!
        assertEquals(DerivedPresence(PresenceValue.OUT, "danger"), derived)
    }

    @Test
    fun theTrustedZoneWins_whenTheTwoOverlap_enteringTheDangerZoneChangesNothing() {
        val inHome = mapOf("home" to 1_000L)
        assertNull(enter("danger", 3_000, inHome, DerivedPresence(PresenceValue.IN_TRUSTED, "home")))
    }

    @Test
    fun theTrustedZoneWins_whenTheTwoOverlap_enteringTheTrustedZoneAfterTheDangerOne_isInTrusted() {
        val inDanger = mapOf("danger" to 1_000L)
        val (derived, _) = enter("home", 4_000, inDanger, DerivedPresence(PresenceValue.IN_RISK, "danger"))!!
        assertEquals(DerivedPresence(PresenceValue.IN_TRUSTED, "home"), derived)
    }

    @Test
    fun leavingTheTrustedZone_whileStayingInTheDangerZone_isInRisk() {
        val inBoth = mapOf("home" to 1_000L, "danger" to 2_000L)
        val (derived, fact) = exit("home", 6_000, inBoth, DerivedPresence(PresenceValue.IN_TRUSTED, "home"))!!
        assertEquals(DerivedPresence(PresenceValue.IN_RISK, "danger"), derived)
        assertEquals(6_000, fact.presenceAtMillis)
    }

    @Test
    fun leavingZoneA_whileStayingInZoneB_whenTheStatusNamedA_movesToB() {
        val inBoth = mapOf("home" to 1_000L, "work" to 2_000L)
        val (derived, fact) = exit("home", 7_000, inBoth, DerivedPresence(PresenceValue.IN_TRUSTED, "home"))!!
        assertEquals(DerivedPresence(PresenceValue.IN_TRUSTED, "work"), derived)
        assertTrue(fact.setLastUpdate)
    }

    @Test
    fun theLastEnteredTrustedZoneIsTheOneNamed() {
        val inside = mapOf("home" to 1_000L, "work" to 2_000L)
        assertEquals(DerivedPresence(PresenceValue.IN_TRUSTED, "work"), PresenceMachine.derive(zones, inside))
    }

    @Test
    fun noChange_noWrite_theSameStateAgain() {
        // The geofencing reports the arrival again (re-armed while inside): the status already says it.
        assertNull(enter("home", 8_000, emptyMap(), DerivedPresence(PresenceValue.IN_TRUSTED, "home")))
    }

    @Test
    fun aPlaceThatIsNotOfThisMiff_changesNothingThere() {
        val onlyWork = listOf(work)
        assertNull(enter("home", 1_000, emptyMap(), null, zones = onlyWork))
        assertNull(exit("home", 1_000, mapOf("home" to 1L), null, zones = onlyWork))
    }

    @Test
    fun aDepartureWithNoStatusBefore_isStillADeparture_out() {
        val (derived, _) = exit("home", 1_000, emptyMap(), null)!!
        assertEquals(DerivedPresence(PresenceValue.OUT, "home"), derived)
    }

    @Test
    fun unknown_isAFactWithNoPlaceAndNoUpdate() {
        val fact = PresenceMachine.unknown(42)
        assertEquals(StatusFact(PresenceValue.UNKNOWN, null, 42, false), fact)
    }

    @Test
    fun noZoneInside_isOut_withNoPlace_theCallerFillsIt() {
        assertEquals(DerivedPresence(PresenceValue.OUT, null), PresenceMachine.derive(zones, emptyMap()))
    }
}
