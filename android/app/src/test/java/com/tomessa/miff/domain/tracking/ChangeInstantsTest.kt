package com.tomessa.miff.domain.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `StatusColor.changeInstants` (NOTIF-3A): the instants at which a member's colour can change. The CONTRACT of the ticket (section 20.2), checked on every
 * shape of facts: at each instant `level(t - 1 ms) != level(t)`, and the level is constant between two instants. The same cases are the contract of iOS.
 */
class ChangeInstantsTest {
    private val h = 60L * 60L * 1000L
    private val min = 60L * 1000L
    private val ref = 1_800_000_000_000L

    private fun facts(
        presence: PresenceValue?,
        presenceAt: Long? = ref,
        update: Long? = null,
        sharing: Boolean = true,
    ) = MemberStatusFacts(sharing, presence, presenceAt, update)

    @Test
    fun outOfATrustedZone_turnsOrangePastTwoHours_andRedPastFourHours_theFirstMillisecondAfter() {
        val instants = StatusColor.changeInstants(facts(PresenceValue.OUT))
        assertEquals(listOf(ref + 2 * h + 1, ref + 4 * h + 1), instants)
        assertEquals(StatusLevel.GREEN, StatusColor.level(facts(PresenceValue.OUT), ref + 2 * h))
        assertEquals(StatusLevel.ORANGE, StatusColor.level(facts(PresenceValue.OUT), ref + 2 * h + 1))
        assertEquals(StatusLevel.ORANGE, StatusColor.level(facts(PresenceValue.OUT), ref + 4 * h))
        assertEquals(StatusLevel.RED, StatusColor.level(facts(PresenceValue.OUT), ref + 4 * h + 1))
    }

    @Test
    fun theReferenceIsTheMostRecentOfTheDepartureAndTheLastUpdate() {
        val f = facts(PresenceValue.OUT, presenceAt = ref, update = ref + 90 * min)
        assertEquals(listOf(ref + 90 * min + 2 * h + 1, ref + 90 * min + 4 * h + 1), StatusColor.changeInstants(f))
        // An update OLDER than the departure does not move the reference.
        val old = facts(PresenceValue.OUT, presenceAt = ref, update = ref - 5 * h)
        assertEquals(listOf(ref + 2 * h + 1, ref + 4 * h + 1), StatusColor.changeInstants(old))
    }

    @Test
    fun noInstantWhenTheLevelNeverChanges() {
        assertTrue(StatusColor.changeInstants(null).isEmpty())
        assertTrue(StatusColor.changeInstants(facts(PresenceValue.OUT, sharing = false)).isEmpty())
        assertTrue(StatusColor.changeInstants(facts(null)).isEmpty())
        assertTrue(StatusColor.changeInstants(facts(PresenceValue.IN_TRUSTED)).isEmpty())
        assertTrue(StatusColor.changeInstants(facts(PresenceValue.UNKNOWN)).isEmpty())
        // No reference at all: neutral for ever.
        assertTrue(StatusColor.changeInstants(facts(PresenceValue.OUT, presenceAt = null, update = null)).isEmpty())
    }

    @Test
    fun inADangerZone_redAtTheEntry_anUpdateAfterTheEntryGivesAnHourOfGreen_theInstantIsTheEndOfThatHour() {
        val entry = ref
        val update = ref + 20 * min
        val f = facts(PresenceValue.IN_RISK, presenceAt = entry, update = update)
        assertEquals(listOf(update + h + 1), StatusColor.changeInstants(f))
        assertEquals(StatusLevel.GREEN, StatusColor.level(f, update + h))
        assertEquals(StatusLevel.RED, StatusColor.level(f, update + h + 1))
    }

    @Test
    fun inADangerZone_anUpdateAtTheEntryOrBeforeIt_orNoUpdate_isRedFromTheStart_soNoInstant_ruleD47() {
        listOf(null, ref, ref - 30 * min).forEach { update ->
            val f = facts(PresenceValue.IN_RISK, presenceAt = ref, update = update)
            assertTrue("update=$update", StatusColor.changeInstants(f).isEmpty())
            assertEquals(StatusLevel.RED, StatusColor.level(f, ref))
            assertEquals(StatusLevel.RED, StatusColor.level(f, ref + 10 * h))
        }
    }

    @Test
    fun anAbsurdReferenceNeverOverflows() {
        assertTrue(StatusColor.changeInstants(facts(PresenceValue.OUT, presenceAt = Long.MAX_VALUE)).isEmpty())
        assertTrue(StatusColor.changeInstants(facts(PresenceValue.OUT, presenceAt = -5L)).isEmpty())
    }

    @Test
    fun theContract_holdsOnEveryShapeOfFacts() {
        val references = listOf(null, ref)
        val updates = listOf(null, ref - 3 * h, ref, ref + 30 * min, ref + 90 * min, ref + 3 * h)
        val presences = listOf(null) + PresenceValue.entries
        var checked = 0
        for (presence in presences) {
            for (sharing in listOf(true, false)) {
                for (r in references) {
                    for (u in updates) {
                        val f = MemberStatusFacts(sharing, presence, r, u)
                        val instants = StatusColor.changeInstants(f)
                        assertEquals(
                            "ascending, no double: $presence $sharing $r $u",
                            instants.distinct().sorted(),
                            instants,
                        )
                        // The level changes at each instant ...
                        instants.forEach { t ->
                            assertTrue(
                                "level must change at $t for $presence $sharing $r $u",
                                StatusColor.level(f, t - 1) != StatusColor.level(f, t),
                            )
                        }
                        // ... and is constant before the first, between two, and after the last.
                        val anchors = listOfNotNull(r, u) + instants
                        val low = (anchors.minOrNull() ?: ref) - 10 * h
                        val high = (anchors.maxOrNull() ?: ref) + 10 * h
                        (listOf(low) + instants + listOf(high + 1)).zipWithNext().forEach { (start, next) ->
                            val end = next - 1
                            if (start <= end) {
                                val expected = StatusColor.level(f, start)
                                listOf(start, (start + end) / 2, end).forEach { t ->
                                    assertEquals(
                                        "constant on [$start, $end] for $presence $sharing $r $u",
                                        expected,
                                        StatusColor.level(f, t),
                                    )
                                }
                            }
                        }
                        checked++
                    }
                }
            }
        }
        assertTrue(checked > 100)
    }
}
