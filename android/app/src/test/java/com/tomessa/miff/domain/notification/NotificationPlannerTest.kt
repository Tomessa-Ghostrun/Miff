package com.tomessa.miff.domain.notification

import com.tomessa.miff.domain.tracking.MemberStatusFacts
import com.tomessa.miff.domain.tracking.PresenceValue
import com.tomessa.miff.domain.tracking.StatusColor
import com.tomessa.miff.domain.tracking.StatusLevel
import com.tomessa.miff.domain.tracking.StatusThresholds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The planner of NOTIF-P0 (ticket section 20.9): the test vectors T1 to T15 that are the contract of `plan` (the same on iOS), then the boundaries.
 * Convention of the ticket: members me, X, Y, all sharing, mode LARGE, the Miff active, the permission granted, "greens" = in a trusted zone.
 * The thresholds are 2 h / 4 h and the rules say "more than": a level changes the FIRST MILLISECOND after, so "15:40" reads here 15:40:00.001.
 */
class NotificationPlannerTest {
    private val paris = ZoneId.of("Europe/Paris")

    /** An instant of the day of the ticket's examples, plus [ms] milliseconds. */
    private fun at(
        hour: Int,
        minute: Int,
        ms: Long = 0,
    ) = ZonedDateTime.of(2026, 11, 8, hour, minute, 0, 0, paris).toInstant().toEpochMilli() + ms

    private val miff = "miff-1"
    private val me = "me"

    private fun out(
        ref: Long,
        update: Long? = null,
    ) = MemberStatusFacts(true, PresenceValue.OUT, ref, update)

    private fun trusted(since: Long = at(9, 0)) = MemberStatusFacts(true, PresenceValue.IN_TRUSTED, since, since)

    private fun risk(
        since: Long,
        update: Long?,
    ) = MemberStatusFacts(true, PresenceValue.IN_RISK, since, update)

    private fun input(
        now: Long,
        members: Map<String, MemberStatusFacts>,
        preferences: NotificationPreferences = NotificationPreferences(),
        permission: Boolean = true,
        session: Boolean = true,
        mode: ResponsibilityMode = ResponsibilityMode.LARGE,
        miffId: String? = miff,
    ) = PlanInput(now, miffId, me, members, preferences, permission, session, mode)

    private fun plan(input: PlanInput) = NotificationPlanner.plan(input).associate { it.type to it.dueAtMillis }

    // --- The thresholds the vectors rely on ------------------------------------------------------------------------------------------------

    @Test
    fun theVectorsRelyOnTheThresholdsOfTheTicket_twoHoursFourHours() {
        assertEquals(2L * 60 * 60 * 1000, StatusThresholds.ORANGE_AFTER_MILLIS)
        assertEquals(4L * 60 * 60 * 1000, StatusThresholds.RED_AFTER_MILLIS)
    }

    // --- T1 to T15 -----------------------------------------------------------------------------------------------------------------------

    @Test
    fun t1_iAmOutSinceTheMorning_n1AtOrange_n2AtRed_andNeitherN3NorN4BecauseIAmResponsible() {
        val members = mapOf(me to out(at(13, 40)), "x" to trusted(), "y" to trusted())
        assertEquals(
            mapOf(NotificationType.N1 to at(15, 40, 1), NotificationType.N2 to at(17, 40, 1)),
            plan(input(at(14, 0), members)),
        )
    }

    @Test
    fun t2_iAmGreen_xIsOut_n3AtThirtyMinutesAfterTheMiffTurnedOrange_n4AtThirtyMinutesAfterRed() {
        val members = mapOf(me to trusted(), "x" to out(at(13, 40)), "y" to trusted())
        assertEquals(
            mapOf(NotificationType.N3 to at(16, 10, 1), NotificationType.N4 to at(18, 10, 1)),
            plan(input(at(14, 0), members)),
        )
    }

    @Test
    fun t3_xAndIAreOut_largeMode_iAmResponsible_strictMode_n3AndN4StillCome() {
        val members = mapOf(me to out(at(13, 50)), "x" to out(at(13, 40)))
        val large = plan(input(at(14, 0), members, mode = ResponsibilityMode.LARGE))
        assertEquals(mapOf(NotificationType.N1 to at(15, 50, 1), NotificationType.N2 to at(17, 50, 1)), large)
        val strict = plan(input(at(14, 0), members, mode = ResponsibilityMode.STRICT))
        assertEquals(
            mapOf(
                NotificationType.N1 to at(15, 50, 1),
                NotificationType.N3 to at(16, 10, 1),
                NotificationType.N2 to at(17, 50, 1),
                NotificationType.N4 to at(18, 10, 1),
            ),
            strict,
        )
    }

    @Test
    fun t4_twoOthersOut_theMiffTurnsOrangeWithTheFirstOne_n3N4FollowTheFirstEpisode() {
        val members = mapOf(me to trusted(), "x" to out(at(13, 40)), "y" to out(at(14, 30)))
        assertEquals(
            mapOf(NotificationType.N3 to at(16, 10, 1), NotificationType.N4 to at(18, 10, 1)),
            plan(input(at(14, 45), members)),
        )
    }

    @Test
    fun t5_xPublishesAt1550_theOldPlanIsReplacedByTheNewEpisode() {
        val before = mapOf(me to trusted(), "x" to out(at(13, 40)))
        assertEquals(
            mapOf(NotificationType.N3 to at(16, 10, 1), NotificationType.N4 to at(18, 10, 1)),
            plan(input(at(15, 45), before)),
        )
        val after = mapOf(me to trusted(), "x" to out(at(13, 40), update = at(15, 50)))
        assertEquals(
            mapOf(NotificationType.N3 to at(18, 20, 1), NotificationType.N4 to at(20, 20, 1)),
            plan(input(at(15, 55), after)),
        )
    }

    @Test
    fun t6_iAmInADangerZone_noN1NoN2_andNoN3N4IfTheOthersAreGreen_decisionQ27() {
        // In a danger zone for an hour and ten minutes without update: I am red, nothing is said TO ME (N6 spoke once, at the entry).
        val members = mapOf(me to risk(at(14, 0), update = at(14, 0)), "x" to trusted())
        assertEquals(emptyMap<NotificationType, Long>(), plan(input(at(15, 10), members)))
    }

    @Test
    fun t6b_xIsInADangerZone_theMiffTurnsRedAnHourAfterHisLastUpdate_n4ThirtyMinutesLater_noN3() {
        val members = mapOf(me to trusted(), "x" to risk(at(15, 0), update = at(15, 0)))
        // Red from 16:00:00.001 (an hour after the update), so N4 at 16:30:00.001; the Miff is never orange: no N3.
        assertEquals(mapOf(NotificationType.N4 to at(16, 30, 1)), plan(input(at(15, 10), members)))
    }

    @Test
    fun t7_yTurnsRedBeforeN3IsDue_n3IsAbandoned_n4ComesThirtyMinutesAfter() {
        // X out 13:40 (orange 15:40:00.001, so N3 would leave at 16:10:00.001); Y is in a danger zone with a last update at 15:05, so he turns red
        // (never orange) at 16:05:00.001, before it. (A member out of a trusted zone cannot skip the orange: it would have started the episode earlier.)
        val members = mapOf(me to trusted(), "x" to out(at(13, 40)), "y" to risk(at(15, 5), update = at(15, 5)))
        listOf(at(16, 5), at(16, 6)).forEach { now ->
            assertEquals("now=$now", mapOf(NotificationType.N4 to at(16, 35, 1)), plan(input(now, members)))
        }
    }

    @Test
    fun t8_theNotificationIsPlanned_theVerificationOfTheDayDecidesAtTheDueTime() {
        val planned = mapOf(me to trusted(), "x" to out(at(13, 40)))
        assertEquals(at(16, 10, 1), plan(input(at(14, 0), planned))[NotificationType.N3])
        // Facts re-read from the server at 16:10: X published at 16:00. The notification is NOT true any more: nothing is shown.
        val fresh = mapOf(me to trusted(), "x" to out(at(13, 40), update = at(16, 0)))
        assertFalse(NotificationPlanner.stillTrue(NotificationType.N3, input(at(16, 10, 1), fresh)))
        // Facts unchanged: it is true.
        assertTrue(NotificationPlanner.stillTrue(NotificationType.N3, input(at(16, 10, 1), planned)))
    }

    @Test
    fun t9_theThirdN1OfTheDay_isOnlyWrittenToTheCentre() {
        val counters = DailyCounters("2026-11-08", mapOf(NotificationType.N1 to 2))
        val decision =
            NotificationDelivery.decide(
                NotificationType.N1,
                NotificationPreferences(),
                counters,
                16,
                false,
                0,
                false,
            )
        assertEquals(Delivery.Silent, decision)
    }

    @Test
    fun t10_aChangeOfActiveMiff_replacesEverything() {
        val members = mapOf(me to out(at(13, 40)), "x" to trusted())
        val before = NotificationPlanner.plan(input(at(16, 0), members))
        assertTrue(before.all { it.miffId == miff })
        // The other Miff: another set of facts (everyone green) and its own identifier: the old plan is not carried over.
        val other =
            NotificationPlanner.plan(
                input(at(16, 5), mapOf(me to trusted(), "z" to trusted()), miffId = "miff-2"),
            )
        assertTrue(other.isEmpty())
        // No active Miff at all: nothing.
        assertTrue(NotificationPlanner.plan(input(at(16, 5), members, miffId = null)).isEmpty())
    }

    @Test
    fun t11_afterARestart_theSamePlanComesBackFromTheSnapshot() {
        val members = mapOf(me to trusted(), "x" to out(at(13, 40)))
        val first = NotificationPlanner.plan(input(at(15, 55), members))
        val afterRestart = NotificationPlanner.plan(input(at(16, 0), members))
        assertEquals(first, afterRestart)
        assertEquals(at(16, 10, 1), afterRestart.first { it.type == NotificationType.N3 }.dueAtMillis)
    }

    @Test
    fun t12_theAppInFront_noSystemNotification_aSilentLine() {
        val decision =
            NotificationDelivery.decide(
                NotificationType.N3,
                NotificationPreferences(),
                DailyCounters("d"),
                16,
                true,
                0,
                false,
            )
        assertEquals(Delivery.Silent, decision)
    }

    @Test
    fun t13_n3DueAtNight_isShownQuietly() {
        val decision =
            NotificationDelivery.decide(
                NotificationType.N3,
                NotificationPreferences(),
                DailyCounters("d"),
                23,
                false,
                0,
                false,
            )
        assertTrue(decision is Delivery.Show && decision.quiet)
        // N4 at the same hour keeps its normal level (decision Q30).
        val red =
            NotificationDelivery.decide(
                NotificationType.N4,
                NotificationPreferences(),
                DailyCounters("d"),
                23,
                false,
                0,
                false,
            )
        assertTrue(red is Delivery.Show && !red.quiet)
    }

    @Test
    fun t14_thePermissionIsRefused_theListIsEmpty() {
        val members = mapOf(me to out(at(13, 40)), "x" to trusted())
        assertTrue(NotificationPlanner.plan(input(at(14, 0), members, permission = false)).isEmpty())
    }

    @Test
    fun t15_mySharingIsStopped_theListIsEmpty() {
        val members = mapOf(me to MemberStatusFacts(false, PresenceValue.OUT, at(13, 40), null), "x" to out(at(13, 40)))
        assertTrue(NotificationPlanner.plan(input(at(14, 0), members)).isEmpty())
    }

    // --- Guards -----------------------------------------------------------------------------------------------------------------------------

    private val leftEarly = mapOf(me to out(at(13, 40)), "x" to trusted())

    @Test
    fun noSessionNoActiveMiffGeneralSwitchOff_orNoStatusOfMine_meansNothing() {
        assertTrue(NotificationPlanner.plan(input(at(14, 0), leftEarly, session = false)).isEmpty())
        assertTrue(
            NotificationPlanner
                .plan(
                    input(at(14, 0), leftEarly, preferences = NotificationPreferences(enabled = false)),
                ).isEmpty(),
        )
        assertTrue(NotificationPlanner.plan(input(at(14, 0), mapOf("x" to out(at(13, 40))))).isEmpty())
    }

    @Test
    fun aTypeSwitchOffRemovesOnlyThatType() {
        val members = mapOf(me to out(at(13, 40)), "x" to trusted())
        val noN1 = plan(input(at(14, 0), members, preferences = NotificationPreferences(n1 = false)))
        assertEquals(setOf(NotificationType.N2), noN1.keys)
        val noN2 = plan(input(at(14, 0), members, preferences = NotificationPreferences(n2 = false)))
        assertEquals(setOf(NotificationType.N1), noN2.keys)
        val others = mapOf(me to trusted(), "x" to out(at(13, 40)))
        assertEquals(
            setOf(NotificationType.N4),
            plan(input(at(14, 0), others, preferences = NotificationPreferences(n3 = false))).keys,
        )
        assertEquals(
            setOf(NotificationType.N3),
            plan(input(at(14, 0), others, preferences = NotificationPreferences(n4 = false))).keys,
        )
    }

    @Test
    fun aloneInTheMiff_neverN3OrN4_iAmResponsible() {
        val alone = mapOf(me to out(at(13, 40)))
        assertEquals(setOf(NotificationType.N1, NotificationType.N2), plan(input(at(14, 0), alone)).keys)
    }

    @Test
    fun inATrustedZoneOrWithAnUnknownPresence_nothingIsToldToMe() {
        listOf(trusted(), MemberStatusFacts(true, PresenceValue.UNKNOWN, at(9, 0), null)).forEach { mine ->
            val members = mapOf(me to mine, "x" to trusted())
            assertTrue(NotificationPlanner.plan(input(at(20, 0), members)).isEmpty())
        }
    }

    // --- Boundaries of N1 and N2 ------------------------------------------------------------------------------------------------------------

    @Test
    fun n1IsPlannedAtTheFirstMillisecondPastTwoHours_notAtTwoHoursSharp() {
        val members = mapOf(me to out(at(13, 40)), "x" to trusted())
        assertEquals(at(15, 40, 1), plan(input(at(15, 39), members))[NotificationType.N1])
        assertEquals(StatusLevel.GREEN, StatusColor.level(members.getValue(me), at(15, 40)))
        assertEquals(StatusLevel.ORANGE, StatusColor.level(members.getValue(me), at(15, 40, 1)))
    }

    @Test
    fun aDueN1_isCaughtUpOnlyWhileIAmStillOrange_alreadyRedN2TakesOver_andNothingIsInventedWhenGreenAgain() {
        // 16:00: orange since 15:40, N1 caught up (due in the past), N2 still ahead.
        val members = mapOf(me to out(at(13, 40)), "x" to trusted())
        val orange = NotificationPlanner.plan(input(at(16, 0), members))
        assertEquals(listOf(NotificationType.N1, NotificationType.N2), orange.map { it.type })
        assertTrue(orange.first().dueAtMillis < at(16, 0))
        // 18:00: red since 17:40: N1 is NOT caught up (a reminder "you turned orange" while red is wrong), N2 is.
        val red = NotificationPlanner.plan(input(at(18, 0), members))
        assertEquals(listOf(NotificationType.N2), red.map { it.type })
        // After an update at 17:30: green again at 18:00, the old orange is gone and a new one is ahead.
        val updated = mapOf(me to out(at(13, 40), update = at(17, 30)), "x" to trusted())
        val green = NotificationPlanner.plan(input(at(18, 0), updated))
        assertEquals(at(19, 30, 1), green.first { it.type == NotificationType.N1 }.dueAtMillis)
    }

    @Test
    fun theKeysChangeWithTheReference_aNewDepartureOrAnUpdateIsANewEpisode() {
        val first = NotificationPlanner.plan(input(at(14, 0), mapOf(me to out(at(13, 40)), "x" to trusted())))
        val later = NotificationPlanner.plan(input(at(15, 0), mapOf(me to out(at(14, 55)), "x" to trusted())))
        assertEquals(first.map { it.type }, later.map { it.type })
        assertTrue(first.map { it.key }.intersect(later.map { it.key }.toSet()).isEmpty())
        // The key says the Miff and the type, nothing else personal: no name, no place.
        assertTrue(first.first().key.startsWith("n1|$miff|$me|"))
    }

    // --- Properties ---------------------------------------------------------------------------------------------------------------------------

    @Test
    fun theListIsSortedByInstant_withAtMostOnePerType_andTheSameInputGivesTheSameList() {
        val members = mapOf(me to out(at(13, 50)), "x" to out(at(13, 40)), "y" to trusted())
        val a = NotificationPlanner.plan(input(at(14, 0), members, mode = ResponsibilityMode.STRICT))
        assertEquals(a, NotificationPlanner.plan(input(at(14, 0), members, mode = ResponsibilityMode.STRICT)))
        assertEquals(a.sortedBy { it.dueAtMillis }, a)
        assertEquals(a.map { it.type }.distinct(), a.map { it.type })
        assertTrue(a.size <= 4)
    }

    @Test
    fun aPlannedNotificationAndTheInputPrintNothing() {
        val planned = NotificationPlanner.plan(input(at(14, 0), leftEarly)).first()
        assertEquals("PlannedNotification(redacted)", planned.toString())
        assertEquals("PlanInput(redacted)", input(at(14, 0), leftEarly).toString())
    }

    @Test
    fun theResponsibilityOfTheCode_isLarge_decisionQ28() {
        assertEquals(ResponsibilityMode.LARGE, NotificationPolicy.RESPONSIBILITY)
    }

    // --- At the moment it is due (stillTrue) -----------------------------------------------------------------------------------------------------

    @Test
    fun stillTrue_n1OnlyWhileOrange_n2OnlyWhileRed_andNeverInADangerZone() {
        val members = mapOf(me to out(at(13, 40)), "x" to trusted())
        assertTrue(NotificationPlanner.stillTrue(NotificationType.N1, input(at(16, 0), members)))
        assertFalse("already red", NotificationPlanner.stillTrue(NotificationType.N1, input(at(18, 0), members)))
        assertFalse("not red yet", NotificationPlanner.stillTrue(NotificationType.N2, input(at(16, 0), members)))
        assertTrue(NotificationPlanner.stillTrue(NotificationType.N2, input(at(18, 0), members)))
        val danger = mapOf(me to risk(at(14, 0), update = at(14, 0)), "x" to trusted())
        assertFalse(NotificationPlanner.stillTrue(NotificationType.N2, input(at(18, 0), danger)))
        // An update published meanwhile: nothing is shown.
        val published = mapOf(me to out(at(13, 40), update = at(15, 50)), "x" to trusted())
        assertFalse(NotificationPlanner.stillTrue(NotificationType.N1, input(at(16, 0), published)))
    }

    @Test
    fun stillTrue_isFalseWithoutSessionPermissionSwitchOrSharing() {
        val members = mapOf(me to out(at(13, 40)), "x" to trusted())
        assertFalse(NotificationPlanner.stillTrue(NotificationType.N1, input(at(16, 0), members, session = false)))
        assertFalse(NotificationPlanner.stillTrue(NotificationType.N1, input(at(16, 0), members, permission = false)))
        assertFalse(
            NotificationPlanner.stillTrue(
                NotificationType.N1,
                input(at(16, 0), members, preferences = NotificationPreferences(n1 = false)),
            ),
        )
        val stopped = mapOf(me to MemberStatusFacts(false, PresenceValue.OUT, at(13, 40), null))
        assertFalse(NotificationPlanner.stillTrue(NotificationType.N1, input(at(16, 0), stopped)))
        assertFalse(
            "N6 is not a planned notification",
            NotificationPlanner.stillTrue(NotificationType.N6, input(at(16, 0), members)),
        )
    }

    @Test
    fun stillTrue_n3N4_theMiffHasThatColourAndIAmNotResponsible() {
        val others = mapOf(me to trusted(), "x" to out(at(13, 40)))
        assertTrue(NotificationPlanner.stillTrue(NotificationType.N3, input(at(16, 10), others)))
        assertFalse("not red yet", NotificationPlanner.stillTrue(NotificationType.N4, input(at(16, 10), others)))
        assertTrue(NotificationPlanner.stillTrue(NotificationType.N4, input(at(18, 10), others)))
        val mine = mapOf(me to out(at(13, 40)), "x" to trusted())
        assertFalse("I am responsible", NotificationPlanner.stillTrue(NotificationType.N3, input(at(16, 10), mine)))
    }
}
