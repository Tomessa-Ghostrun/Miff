package com.tomessa.miff.domain.notification

import com.tomessa.miff.domain.tracking.MemberStatusFacts
import com.tomessa.miff.domain.tracking.PresenceValue
import com.tomessa.miff.domain.tracking.StatusColor
import com.tomessa.miff.domain.tracking.StatusLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * MODE VOYAGE, notifications (VOY-4A; ticket mode-voyage.md section 9, vectors T16 to T20 and N7-1 to N7-3, the contract with iOS): N7 "Ton mode voyage est terminé" at
 * 08:00 local the morning after the last day, and the guards of the planner (my trip cuts N1 to N4 and N6; a traveller of the circle is neutral).
 * Convention of the ticket: members me, X, Y, all sharing, mode LARGE, the Miff active, the permission granted. The trip of the examples ends the night of the 10th
 * (last day chosen: the 9th), at 00:00 local.
 */
class TravelNotificationPlannerTest {
    private val paris = ZoneId.of("Europe/Paris")

    private fun at(
        day: Int,
        hour: Int,
        minute: Int,
        ms: Long = 0,
    ) = ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, paris).toInstant().toEpochMilli() + ms

    private val miff = "miff-1"
    private val me = "me"

    /** The end of a trip whose last day is the 9th: 00:00 local of the 10th. */
    private val tripEnd = at(10, 0, 0)

    private fun out(
        ref: Long,
        update: Long? = null,
    ) = MemberStatusFacts(true, PresenceValue.OUT, ref, update)

    private fun trusted(since: Long = at(8, 9, 0)) = MemberStatusFacts(true, PresenceValue.IN_TRUSTED, since, since)

    private fun travelling(until: Long = tripEnd) =
        MemberStatusFacts(true, PresenceValue.TRAVEL, at(8, 9, 0), at(8, 9, 0), until)

    private fun input(
        now: Long,
        members: Map<String, MemberStatusFacts>,
        myTravelUntil: Long? = null,
        preferences: NotificationPreferences = NotificationPreferences(),
        permission: Boolean = true,
        session: Boolean = true,
        miffId: String? = miff,
    ) = PlanInput(
        nowMillis = now,
        miffId = miffId,
        myUid = me,
        members = members,
        preferences = preferences,
        permissionAllowed = permission,
        sessionValid = session,
        myTravelUntilMillis = myTravelUntil,
        zone = paris,
    )

    private fun plan(input: PlanInput) = NotificationPlanner.plan(input).associate { it.type to it.dueAtMillis }

    // --- N7: the hour, the key --------------------------------------------------------------------------------------------------------------

    @Test
    fun n7_isEightHoursAfterTheEnd_theMorningAfterTheLastDay_neverInTheNight() {
        assertEquals(at(10, 8, 0), TravelEndNotice.dueAt(tripEnd, paris))
        assertEquals(tripEnd + 8L * 3_600_000L, TravelEndNotice.dueAt(tripEnd, paris))
    }

    @Test
    fun n7_onTheDayOfTheEndOfSummerTime_isStillEightLocal_notEightHoursLater() {
        val zone = paris
        // The night of 25 to 26 October 2026 has 25 hours in Paris: the end of the trip is 00:00 of the 26th (still summer time); 08:00 is winter time.
        val end = ZonedDateTime.of(2026, 10, 26, 0, 0, 0, 0, zone).toInstant().toEpochMilli()
        val expected = ZonedDateTime.of(2026, 10, 26, 8, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(expected, TravelEndNotice.dueAt(end, zone))
    }

    @Test
    fun n7_aZoneChangedDuringTheTrip_neverComesBeforeTheEndOfTheTrip() {
        // The end is 00:00 Paris of the 10th = 18:00 of the 9th in New York: 08:00 of the 9th would be BEFORE the end; the next 08:00 (the 10th, New York) is taken.
        val newYork = ZoneId.of("America/New_York")
        val due = TravelEndNotice.dueAt(tripEnd, newYork)
        assertTrue(due > tripEnd)
        assertEquals(ZonedDateTime.of(2026, 11, 10, 8, 0, 0, 0, newYork).toInstant().toEpochMilli(), due)
    }

    @Test
    fun n7_whenEightLocalIsTheEndItself_itLeavesAtOnce_notADayLate() {
        // The end is 00:00 Paris of the 10th = 08:00 Tokyo of the 10th: a phone in Tokyo has 08:00 = the end itself.
        val tokyo = ZoneId.of("Asia/Tokyo")
        val eightTokyo = ZonedDateTime.of(2026, 11, 10, 8, 0, 0, 0, tokyo).toInstant().toEpochMilli()
        assertEquals(eightTokyo, tripEnd)
        assertEquals(tripEnd, TravelEndNotice.dueAt(tripEnd, tokyo))
    }

    @Test
    fun n7_theKeyHoldsTheAccountAndTheEnd_aMovedEndIsAnotherKey() {
        assertEquals("n7|me|$tripEnd", TravelEndNotice.key(me, tripEnd))
        assertFalse(TravelEndNotice.key(me, tripEnd) == TravelEndNotice.key(me, tripEnd + 24L * 3_600_000L))
    }

    // --- T16 to T20 ------------------------------------------------------------------------------------------------------------------------

    @Test
    fun t16_myTripIsOn_theListIsN7Alone_noN1N2N3N4EvenIfIAmOutAndXIsOut() {
        val members = mapOf(me to out(at(8, 13, 40)), "x" to out(at(8, 13, 0)), "y" to trusted())
        assertEquals(
            mapOf(NotificationType.N7 to at(10, 8, 0)),
            plan(input(at(8, 16, 30), members, myTravelUntil = tripEnd)),
        )
    }

    @Test
    fun t17_xTravels_yIsGreen_noN3NoN4_xIsIgnored() {
        val members = mapOf(me to trusted(), "x" to travelling(), "y" to trusted())
        assertEquals(emptyMap<NotificationType, Long>(), plan(input(at(8, 14, 0), members)))
        assertEquals(StatusLevel.GREEN, NotificationPlanner.miffLevelAt(input(at(8, 14, 0), members), at(8, 14, 0)))
    }

    @Test
    fun t17b_xTravelsAlone_theMiffIsNeutral_noNotificationAboutHim_evenLongAfterTheEndOfHisTrip() {
        val members = mapOf(me to trusted(), "x" to travelling())
        assertEquals(emptyMap<NotificationType, Long>(), plan(input(at(8, 14, 0), members)))
        assertEquals(emptyMap<NotificationType, Long>(), plan(input(at(12, 14, 0), members)))
        // The traveller alone makes the Miff neutral, before and after the end of his trip.
        val alone = mapOf("x" to travelling())
        assertEquals(StatusLevel.NEUTRAL, NotificationPlanner.miffLevelAt(input(at(8, 14, 0), alone), at(8, 14, 0)))
        assertEquals(StatusLevel.NEUTRAL, NotificationPlanner.miffLevelAt(input(at(12, 14, 0), alone), at(12, 14, 0)))
    }

    @Test
    fun t18_xWasOrange_thenXTravels_theN3WaitingIsGone() {
        val before = mapOf(me to trusted(), "x" to out(at(8, 13, 40)))
        assertEquals(
            mapOf(NotificationType.N3 to at(8, 16, 10, 1), NotificationType.N4 to at(8, 18, 10, 1)),
            plan(input(at(8, 14, 0), before)),
        )
        val after = mapOf(me to trusted(), "x" to travelling())
        assertEquals(emptyMap<NotificationType, Long>(), plan(input(at(8, 14, 5), after)))
    }

    @Test
    fun t19_theTripEnds_theNormalPlanIsBack_andN7StillWaitsForEight() {
        val members = mapOf(me to out(at(10, 0, 5)), "x" to trusted())
        // 00:30 on the 10th: the trip is over (end = 00:00), I left a place at 00:05.
        val now = at(10, 0, 30)
        assertEquals(
            mapOf(
                NotificationType.N1 to at(10, 2, 5, 1),
                NotificationType.N2 to at(10, 4, 5, 1),
                NotificationType.N7 to at(10, 8, 0),
            ),
            plan(input(now, members, myTravelUntil = tripEnd)),
        )
    }

    @Test
    fun t20_xTravelsAndTheEndOfHisTripIsPast_stillNeutral() {
        val members = mapOf(me to trusted(), "x" to travelling(until = at(8, 10, 0)), "y" to out(at(8, 9, 0)))
        // x is neutral before and after his end; the Miff colour comes from y alone.
        assertEquals(StatusLevel.NEUTRAL, StatusColor.level(members.getValue("x"), at(8, 12, 0)))
        assertEquals(emptyList<Long>(), StatusColor.changeInstants(members.getValue("x")))
        assertEquals(
            mapOf(NotificationType.N3 to at(8, 11, 30, 1), NotificationType.N4 to at(8, 13, 30, 1)),
            plan(input(at(8, 9, 30), members)),
        )
    }

    // --- N7: the conditions ---------------------------------------------------------------------------------------------------------------------

    @Test
    fun n7_needsNoMiff_noSnapshot_aTripAlone() {
        val planned = NotificationPlanner.plan(input(at(8, 16, 0), emptyMap(), myTravelUntil = tripEnd, miffId = null))
        assertEquals(listOf(NotificationType.N7), planned.map { it.type })
        assertEquals("", planned.single().miffId)
    }

    @Test
    fun n7_isNotPlannedWithoutTheSwitch_theGeneralSwitch_thePermission_theSession_orATrip() {
        val members = emptyMap<String, MemberStatusFacts>()

        fun list(i: PlanInput) = NotificationPlanner.plan(i).map { it.type }
        assertEquals(listOf(NotificationType.N7), list(input(at(8, 16, 0), members, tripEnd)))
        assertEquals(
            emptyList<NotificationType>(),
            list(input(at(8, 16, 0), members, tripEnd, NotificationPreferences(n7 = false))),
        )
        assertEquals(
            emptyList<NotificationType>(),
            list(input(at(8, 16, 0), members, tripEnd, NotificationPreferences(enabled = false))),
        )
        assertEquals(emptyList<NotificationType>(), list(input(at(8, 16, 0), members, tripEnd, permission = false)))
        assertEquals(emptyList<NotificationType>(), list(input(at(8, 16, 0), members, tripEnd, session = false)))
        assertEquals(emptyList<NotificationType>(), list(input(at(8, 16, 0), members, null)))
    }

    @Test
    fun n7_theOtherSwitchesDoNotCutIt_andItDoesNotCutTheirs() {
        val members = mapOf(me to out(at(10, 0, 5)), "x" to trusted())
        val prefs = NotificationPreferences(n1 = false, n2 = false, n3 = false, n4 = false, n6 = false)
        assertEquals(
            mapOf(NotificationType.N7 to at(10, 8, 0)),
            plan(input(at(10, 0, 30), members, myTravelUntil = tripEnd, preferences = prefs)),
        )
        assertEquals(
            mapOf(NotificationType.N1 to at(10, 2, 5, 1), NotificationType.N2 to at(10, 4, 5, 1)),
            plan(
                input(
                    at(10, 0, 30),
                    members,
                    myTravelUntil = tripEnd,
                    preferences = NotificationPreferences(n7 = false),
                ),
            ),
        )
    }

    @Test
    fun n7_aCatchUpIsPlannedForADay_thenForgotten() {
        val members = emptyMap<String, MemberStatusFacts>()
        val due = at(10, 8, 0)
        assertEquals(mapOf(NotificationType.N7 to due), plan(input(due + 5L * 3_600_000L, members, tripEnd)))
        assertEquals(emptyMap<NotificationType, Long>(), plan(input(due + 25L * 3_600_000L, members, tripEnd)))
    }

    @Test
    fun n7_aMovedEnd_isAnotherNotification_theSameEndAgain_isTheSame() {
        val members = emptyMap<String, MemberStatusFacts>()
        val a = NotificationPlanner.plan(input(at(8, 9, 0), members, tripEnd)).single()
        val b = NotificationPlanner.plan(input(at(8, 9, 0), members, tripEnd + 24L * 3_600_000L)).single()
        val c = NotificationPlanner.plan(input(at(8, 9, 0), members, tripEnd)).single()
        assertTrue(a.key != b.key)
        assertEquals(a.key, c.key)
        assertEquals(a.dueAtMillis + 24L * 3_600_000L, b.dueAtMillis)
    }

    // --- stillTrue -----------------------------------------------------------------------------------------------------------------------

    @Test
    fun stillTrue_n7_needsAKnownTripAndTheUsualConditions_noLevelNoMiff() {
        val members = emptyMap<String, MemberStatusFacts>()
        assertTrue(
            NotificationPlanner.stillTrue(NotificationType.N7, input(at(10, 8, 0, 5), members, tripEnd, miffId = null)),
        )
        assertFalse(NotificationPlanner.stillTrue(NotificationType.N7, input(at(10, 8, 0, 5), members, null)))
        assertFalse(
            NotificationPlanner.stillTrue(
                NotificationType.N7,
                input(at(10, 8, 0, 5), members, tripEnd, NotificationPreferences(n7 = false)),
            ),
        )
        assertFalse(
            NotificationPlanner.stillTrue(
                NotificationType.N7,
                input(at(10, 8, 0, 5), members, tripEnd, permission = false),
            ),
        )
    }

    @Test
    fun stillTrue_whileMyTripIsOn_noReminderAndNoNewsIsTrue() {
        val members = mapOf(me to out(at(8, 13, 40)), "x" to out(at(8, 13, 0)))
        val now = at(8, 20, 0)
        for (type in listOf(NotificationType.N1, NotificationType.N2, NotificationType.N3, NotificationType.N4)) {
            assertFalse("$type", NotificationPlanner.stillTrue(type, input(now, members, myTravelUntil = tripEnd)))
        }
        // The same facts without a trip: true (so the guard is the trip, nothing else).
        assertTrue(NotificationPlanner.stillTrue(NotificationType.N2, input(now, members)))
    }

    // --- Preferences, delivery -----------------------------------------------------------------------------------------------------------

    @Test
    fun n7_isOnByDefault_hasItsOwnSwitch_andFollowsTheGeneralOne() {
        val prefs = NotificationPreferences()
        assertTrue(prefs.allows(NotificationType.N7))
        assertFalse(prefs.with(NotificationType.N7, false).allows(NotificationType.N7))
        assertTrue(prefs.with(NotificationType.N7, false).allows(NotificationType.N1))
        assertFalse(prefs.copy(enabled = false).allows(NotificationType.N7))
        assertTrue(
            "the choice is kept when the general switch is off",
            prefs.copy(enabled = false).switchOf(NotificationType.N7),
        )
    }

    @Test
    fun n7_isANotificationAboutMe_leadsToTheFeed_neverQuietAtNight() {
        assertEquals(NotificationFamily.REMINDER, NotificationType.N7.family)
        assertEquals(NotificationDestination.FEED, NotificationType.N7.destination)
        assertFalse(NotificationType.N7.quietAtNight)
        assertEquals("n7", NotificationType.N7.code)
        assertEquals(NotificationType.N7, NotificationType.fromCode("n7"))
    }

    @Test
    fun n7_isOutsideTheDailyCaps_neitherCappedNorCounted() {
        var counters = DailyCounters("2026-11-10")
        repeat(NotificationPolicy.MAX_PER_TYPE_PER_DAY) { counters = counters.afterShown(NotificationType.N1) }
        repeat(NotificationPolicy.MAX_PER_TYPE_PER_DAY) { counters = counters.afterShown(NotificationType.N2) }
        repeat(NotificationPolicy.MAX_PER_TYPE_PER_DAY) { counters = counters.afterShown(NotificationType.N3) }
        assertEquals(NotificationPolicy.MAX_TOTAL_PER_DAY, counters.total)
        assertFalse(counters.canShow(NotificationType.N4))
        assertTrue("N7 is shown even when the six of the day are used", counters.canShow(NotificationType.N7))
        // Showing N7 many times never fills the total of the others.
        val many = (1..5).fold(DailyCounters("2026-11-10")) { c, _ -> c.afterShown(NotificationType.N7) }
        assertEquals(0, many.total)
        assertTrue(many.canShow(NotificationType.N7))
    }

    @Test
    fun n7_deliveryRules_shownAtEight_silentInFrontOrLate_droppedWhenOff() {
        val counters = DailyCounters("2026-11-10")
        val prefs = NotificationPreferences()

        fun decide(
            foreground: Boolean = false,
            lateness: Long = 0L,
            hour: Int = 8,
            preferences: NotificationPreferences = prefs,
            already: Boolean = false,
        ) = NotificationDelivery.decide(NotificationType.N7, preferences, counters, hour, foreground, lateness, already)
        assertTrue(decide() is Delivery.Show)
        assertFalse("not quiet: it is never at night", (decide(hour = 23) as Delivery.Show).quiet)
        assertEquals(Delivery.Silent, decide(foreground = true))
        assertEquals(Delivery.Silent, decide(lateness = NotificationPolicy.CATCH_UP_MAX_LATENESS_MILLIS + 1))
        assertEquals(Delivery.Drop, decide(preferences = prefs.with(NotificationType.N7, false)))
        assertEquals(Delivery.Drop, decide(already = true))
    }
}
