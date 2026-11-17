package com.tomessa.miff.domain.notification

import com.tomessa.miff.domain.feed.MemberLocationAccess
import com.tomessa.miff.domain.feed.MemberStatus
import com.tomessa.miff.domain.feed.PlaceKind
import com.tomessa.miff.domain.feed.StatusesView
import com.tomessa.miff.domain.miff.MiffMember
import com.tomessa.miff.domain.miff.MiffRole
import com.tomessa.miff.domain.place.Place
import com.tomessa.miff.domain.place.PlaceCategory
import com.tomessa.miff.domain.place.PlaceType
import com.tomessa.miff.domain.tracking.PresenceValue
import com.tomessa.miff.domain.tracking.StatusThresholds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * "Depuis ta dernière visite" (ticket notifications-p1-derniere-visite.md): the vectors C1-T1 to C1-T20 of the common test suite (same table on iOS), on the pure
 * comparison. No Android class, no clock, no server.
 */
class LastVisitTest {
    private val minute = 60_000L
    private val hour = 60 * minute
    private val zone: ZoneId = ZoneOffset.UTC
    private val now = Instant.parse("2026-11-13T20:00:00Z").toEpochMilli()
    private val miff = "miff-1"

    private fun member(
        uid: String,
        presence: PresenceValue? = PresenceValue.IN_TRUSTED,
        place: PlaceKind? = PlaceKind.SCHOOL,
        presenceAt: Long? = now - 10 * hour,
        lastUpdateAt: Long? = presenceAt,
        silent: Boolean = false,
        muted: Boolean = false,
    ) = VisitMember(uid, muted, presence, place, presenceAt, lastUpdateAt, silent)

    private fun state(
        vararg members: VisitMember,
        miffId: String = miff,
    ) = VisitState(miffId, members.toList())

    private fun compare(
        seen: VisitState?,
        current: VisitState,
        at: Long = now,
    ) = LastVisit.compare(seen, current, at)

    // ---- C1-T1, T2: nothing to say --------------------------------------------------------------------------------------------------------------------

    @Test
    fun t1_noSeenPicture_isNoLine() {
        assertTrue(compare(null, state(member("t"))).isEmpty())
    }

    @Test
    fun t2_noChange_isNoLine() {
        val picture = state(member("t"), member("a", PresenceValue.OUT, PlaceKind.HOME))
        assertTrue(compare(picture, picture).isEmpty())
    }

    // ---- C1-T3 to T5: the three sorts of change -----------------------------------------------------------------------------------------------------

    @Test
    fun t3_anArrival_isOneLine_withTheTimeAndTheCategoryOfThePlace() {
        val seen =
            state(
                member(
                    "t",
                    PresenceValue.OUT,
                    PlaceKind.SCHOOL,
                    presenceAt = now - 14 * hour,
                    lastUpdateAt =
                        now - 14 * hour,
                ),
            )
        val current =
            state(
                member(
                    "t",
                    PresenceValue.IN_TRUSTED,
                    PlaceKind.HOME,
                    presenceAt = now - 2 * hour,
                    lastUpdateAt =
                        now - 2 * hour + minute,
                ),
            )
        val line = compare(seen, current).single()
        assertEquals(VisitKind.ARRIVAL, line.kind)
        assertEquals(PlaceKind.HOME, line.place)
        assertEquals(now - 2 * hour, line.shownAtMillis)
        assertEquals("t", line.uid)
    }

    @Test
    fun t4_aDeparture_isOneLine_withTheTimeOfTheExit() {
        val seen = state(member("t", PresenceValue.IN_TRUSTED, PlaceKind.SCHOOL, presenceAt = now - 6 * hour))
        val current =
            state(
                member(
                    "t",
                    PresenceValue.OUT,
                    PlaceKind.SCHOOL,
                    presenceAt = now - 1 * hour,
                    lastUpdateAt =
                        now - 6 * hour,
                ),
            )
        val line = compare(seen, current).single()
        assertEquals(VisitKind.DEPARTURE, line.kind)
        assertEquals(PlaceKind.SCHOOL, line.place)
        assertEquals(now - 1 * hour, line.shownAtMillis)
    }

    @Test
    fun t5_anUpdate_isOneLine_withTheTimeOfTheUpdate() {
        val seen =
            state(
                member(
                    "a",
                    PresenceValue.OUT,
                    PlaceKind.HOME,
                    presenceAt = now - 5 * hour,
                    lastUpdateAt =
                        now - 5 * hour,
                ),
            )
        val current =
            state(
                member(
                    "a",
                    PresenceValue.OUT,
                    PlaceKind.HOME,
                    presenceAt = now - 5 * hour,
                    lastUpdateAt =
                        now - 30 * minute,
                ),
            )
        val line = compare(seen, current).single()
        assertEquals(VisitKind.UPDATE, line.kind)
        assertNull(line.place)
        assertEquals(now - 30 * minute, line.shownAtMillis)
    }

    @Test
    fun anArrivalWritesTheLastUpdateToo_andIsNotAlsoAnUpdate() {
        // Same minute: one event. Without the rule the update (written by the server a moment later) would win and hide the arrival.
        val seen = state(member("t", PresenceValue.OUT, presenceAt = now - 9 * hour, lastUpdateAt = now - 9 * hour))
        val current =
            state(
                member(
                    "t",
                    PresenceValue.IN_TRUSTED,
                    presenceAt = now - 3 * hour,
                    lastUpdateAt =
                        now - 3 * hour + 2 * minute,
                ),
            )
        assertEquals(VisitKind.ARRIVAL, compare(seen, current).single().kind)
        // An update much later than the arrival is a piece of news of its own, and it is the most recent.
        val later =
            state(
                member(
                    "t",
                    PresenceValue.IN_TRUSTED,
                    presenceAt = now - 3 * hour,
                    lastUpdateAt =
                        now - 1 * hour,
                ),
            )
        assertEquals(VisitKind.UPDATE, compare(seen, later).single().kind)
    }

    // ---- C1-T6 to T8: the silence --------------------------------------------------------------------------------------------------------------------

    @Test
    fun t6_aPersonWhoBecameRed_isOneSilenceLine_withTheTimeOfTheLatestNews() {
        val last = now - 5 * hour
        val seen =
            state(
                member("a", PresenceValue.OUT, PlaceKind.HOME, presenceAt = last, lastUpdateAt = last, silent = false),
            )
        val current =
            state(member("a", PresenceValue.OUT, PlaceKind.HOME, presenceAt = last, lastUpdateAt = last, silent = true))
        val line = compare(seen, current).single()
        assertEquals(VisitKind.SILENCE, line.kind)
        assertEquals(last, line.shownAtMillis)
        // Dated when she became red, so that the lines are ordered by the moment of the change.
        assertEquals(last + StatusThresholds.RED_AFTER_MILLIS + 1, line.atMillis)
    }

    @Test
    fun t6b_aPersonWhoLeftAndIsNowRed_isOneSilenceLine_notALeftLine() {
        val left = now - 5 * hour
        val seen = state(member("a", PresenceValue.IN_TRUSTED, PlaceKind.HOME, presenceAt = now - 20 * hour))
        val current =
            state(
                member(
                    "a",
                    PresenceValue.OUT,
                    PlaceKind.HOME,
                    presenceAt = left,
                    lastUpdateAt =
                        now - 20 * hour,
                    silent = true,
                ),
            )
        val line = compare(seen, current).single()
        assertEquals(VisitKind.SILENCE, line.kind)
        assertEquals(left, line.shownAtMillis)
    }

    @Test
    fun t7_aPersonAlreadyRedAtTheLastVisit_andStillRed_isNoLine() {
        val last = now - 9 * hour
        val picture = state(member("a", PresenceValue.OUT, presenceAt = last, lastUpdateAt = last, silent = true))
        assertTrue(compare(picture, picture).isEmpty())
    }

    @Test
    fun t8_aPersonOnlyOrange_hasNoSilenceLine_butHerDepartureIfSheLeft() {
        val seen = state(member("a", PresenceValue.IN_TRUSTED, PlaceKind.WORK, presenceAt = now - 20 * hour))
        val orange =
            state(
                member(
                    "a",
                    PresenceValue.OUT,
                    PlaceKind.WORK,
                    presenceAt = now - 3 * hour,
                    lastUpdateAt =
                        now - 20 * hour,
                    silent = false,
                ),
            )
        val line = compare(seen, orange).single()
        assertEquals(VisitKind.DEPARTURE, line.kind)
        // Orange and nothing else changed: nothing is said.
        val stillOrange =
            state(
                member(
                    "a",
                    PresenceValue.OUT,
                    PlaceKind.WORK,
                    presenceAt = now - 3 * hour,
                    lastUpdateAt =
                        now - 20 * hour,
                    silent = false,
                ),
            )
        assertTrue(compare(stillOrange, stillOrange).isEmpty())
    }

    // ---- C1-T9: how many, in which order -------------------------------------------------------------------------------------------------------------

    @Test
    fun t9_fourPeopleChanged_isThreeLines_theMostRecentFirst() {
        fun update(
            uid: String,
            ago: Long,
        ) = member(uid, PresenceValue.OUT, presenceAt = now - 20 * hour, lastUpdateAt = now - ago)
        val seen =
            state(
                *listOf("p1", "p2", "p3", "p4")
                    .map {
                        member(
                            it,
                            PresenceValue.OUT,
                            presenceAt =
                                now - 20 * hour,
                            lastUpdateAt = now - 20 * hour,
                        )
                    }.toTypedArray(),
            )
        val current =
            state(
                update("p1", 3 * hour),
                update("p2", 1 * hour),
                update("p3", 4 * hour),
                update(
                    "p4",
                    2 * hour,
                ),
            )
        assertEquals(listOf("p2", "p4", "p1"), compare(seen, current).map { it.uid })
    }

    @Test
    fun aSilenceIsOrderedByTheMomentSheBecameRed() {
        val seen =
            state(
                member("quiet", PresenceValue.OUT, presenceAt = now - 6 * hour, lastUpdateAt = now - 6 * hour),
                member("news", PresenceValue.OUT, presenceAt = now - 20 * hour, lastUpdateAt = now - 20 * hour),
            )
        val current =
            state(
                member(
                    "quiet",
                    PresenceValue.OUT,
                    presenceAt = now - 6 * hour,
                    lastUpdateAt = now - 6 * hour,
                    silent = true,
                ),
                member("news", PresenceValue.OUT, presenceAt = now - 20 * hour, lastUpdateAt = now - 5 * hour),
            )
        // "quiet" became red 2 h ago (6 h - 4 h), "news" gave news 5 h ago: the silence is the more recent change.
        assertEquals(listOf("quiet", "news"), compare(seen, current).map { it.uid })
    }

    // ---- C1-T10 to T13: never spoken of ---------------------------------------------------------------------------------------------------------------

    @Test
    fun t10_aPersonOnATrip_orWhoStoppedSharing_orWhoseNewsAreHidden_isNoLine() {
        val seen = state(member("t"), member("a"), member("c"))
        val current =
            state(
                member("t", PresenceValue.TRAVEL, null, presenceAt = now - hour, muted = true),
                member("a", PresenceValue.OUT, presenceAt = now - hour, muted = true),
                member("c", PresenceValue.OUT, presenceAt = now - hour, muted = true),
            )
        assertTrue(compare(seen, current).isEmpty())
        // And the other way round: back from a trip, nothing is told about it either.
        assertTrue(compare(current, seen).isEmpty())
    }

    @Test
    fun t11_aPersonInADangerZone_isNoLine_andLeavingItIsNotADeparture() {
        val seen = state(member("t", PresenceValue.OUT, presenceAt = now - 3 * hour))
        val danger = state(member("t", PresenceValue.IN_RISK, null, presenceAt = now - hour, silent = false))
        assertTrue(compare(seen, danger).isEmpty())
        val leftDanger =
            state(
                member(
                    "t",
                    PresenceValue.OUT,
                    PlaceKind.HOME,
                    presenceAt = now - 10 * minute,
                    lastUpdateAt =
                        now - 3 * hour,
                ),
            )
        assertTrue(compare(danger, leftDanger).isEmpty())
    }

    @Test
    fun t12_myOwnCard_isNeverInThePicture() {
        val members = listOf(miffMember("me"), miffMember("t"))
        val picture =
            LastVisit.sample(
                miff,
                members,
                emptyList(),
                "me",
                loaded(mapOf("me" to status(), "t" to status())),
                false,
                false,
                now,
            )
        assertEquals(listOf("t"), picture!!.members.map { it.uid })
    }

    @Test
    fun t13_aNewMember_orAMemberWhoLeft_isNoLine() {
        val seen = state(member("t"), member("gone"))
        val current = state(member("t"), member("new", PresenceValue.IN_TRUSTED, presenceAt = now - hour))
        assertTrue(compare(seen, current).isEmpty())
    }

    // ---- C1-T14: stale data --------------------------------------------------------------------------------------------------------------------------

    @Test
    fun t14_aPictureIsOnlyTakenFromFreshStatuses() {
        val members = listOf(miffMember("me"), miffMember("t"))
        val statuses = mapOf("me" to status(), "t" to status())

        fun sample(
            view: StatusesView,
            waiting: Boolean = false,
            offline: Boolean = false,
        ) = LastVisit.sample(miff, members, emptyList(), "me", view, waiting, offline, now)
        assertNotNull(sample(loaded(statuses)))
        // From the cache of the phone, offline, not read at all, awaiting my agreement: nothing is compared and nothing is kept as "seen".
        assertNull(sample(loaded(statuses, fromCache = true)))
        assertNull(sample(loaded(statuses), offline = true))
        assertNull(sample(StatusesView.Waiting))
        assertNull(sample(StatusesView.Pending))
        assertNull(sample(StatusesView.Off))
        assertNull(sample(loaded(statuses), waiting = true))
        // I stopped sharing: the news of the others is hidden.
        val stopped = listOf(miffMember("me", sharing = false), miffMember("t"))
        assertNull(LastVisit.sample(miff, stopped, emptyList(), "me", loaded(statuses), false, false, now))
    }

    @Test
    fun theSampleReadsTheCategoryNeverTheName_andSilenceOnlyOutOfEveryZone() {
        val members =
            listOf(
                miffMember("me"),
                miffMember("t"),
                miffMember("a"),
                miffMember("l"),
                miffMember("trip"),
                miffMember("stopped", sharing = false),
            )
        val places =
            listOf(
                place("p1", PlaceCategory.SCHOOL, "École Jean Moulin"),
                place("p2", PlaceCategory.LEISURE, "Club de tango"),
            )
        val statuses =
            mapOf(
                "t" to status(PresenceValue.IN_TRUSTED, "p1", now - hour),
                "a" to status(PresenceValue.OUT, "p2", now - 6 * hour),
                "l" to status(PresenceValue.IN_RISK, "p1", now - 6 * hour),
                "trip" to status(PresenceValue.TRAVEL, null, now - 6 * hour),
                "stopped" to status(PresenceValue.OUT, "p1", now - hour),
            )
        val byUid =
            LastVisit
                .sample(
                    miff,
                    members,
                    places,
                    "me",
                    loaded(statuses),
                    false,
                    false,
                    now,
                )!!
                .members
                .associateBy {
                    it.uid
                }
        assertEquals(PlaceKind.SCHOOL, byUid.getValue("t").place)
        // Loisir: "dans un lieu de confiance" (decision Q5): no category, and the name of the place is nowhere.
        assertNull(byUid.getValue("a").place)
        assertTrue(byUid.getValue("a").silent)
        // A danger zone is red but not a silence; a trip and a person who stopped sharing are muted.
        assertTrue(!byUid.getValue("l").silent)
        assertTrue(byUid.getValue("trip").muted)
        assertTrue(byUid.getValue("stopped").muted)
        assertTrue(!byUid.getValue("t").muted)
    }

    // ---- C1-T15 to T17 -------------------------------------------------------------------------------------------------------------------------------

    @Test
    fun t15_anAbsenceUnder30Minutes_isNotAnOpening() {
        assertTrue(!LastVisit.isReopening(0))
        assertTrue(!LastVisit.isReopening(29 * minute + 59_000))
        assertTrue(LastVisit.isReopening(30 * minute))
        assertTrue(LastVisit.isReopening(3 * hour))
    }

    @Test
    fun t16_oneLinePerPerson_theMostRecent_andNoStoryIsInvented() {
        // Arrived then left between two visits: the status only keeps the departure.
        val seen = state(member("t", PresenceValue.IN_TRUSTED, PlaceKind.HOME, presenceAt = now - 20 * hour))
        val current =
            state(
                member(
                    "t",
                    PresenceValue.OUT,
                    PlaceKind.SCHOOL,
                    presenceAt = now - 2 * hour,
                    lastUpdateAt =
                        now - 10 * hour,
                ),
            )
        val lines = compare(seen, current)
        assertEquals(1, lines.size)
        assertEquals(VisitKind.DEPARTURE, lines.single().kind)
    }

    @Test
    fun t17_anotherMiffIsNeitherComparedNorMixed() {
        val other = state(member("t"), miffId = "miff-2")
        val current = state(member("t", presenceAt = now - hour))
        assertTrue(compare(other, current).isEmpty())
    }

    // ---- C1-T18, T19: times ---------------------------------------------------------------------------------------------------------------------------

    @Test
    fun t18_theDayIsToday_yesterday_theWeekday_orTheDate() {
        fun day(daysAgo: Long) = LastVisit.dayOf(now - daysAgo * 24 * hour, now, zone)
        assertEquals(VisitDay.TODAY, day(0))
        assertEquals(VisitDay.YESTERDAY, day(1))
        assertEquals(VisitDay.WEEKDAY, day(2))
        assertEquals(VisitDay.WEEKDAY, day(6))
        assertEquals(VisitDay.DATE, day(7))
        assertEquals(VisitDay.DATE, day(40))
    }

    @Test
    fun t18b_theDayFollowsTheZoneOfThePhone_notTheHourCount() {
        // 23:30 UTC the evening before is "yesterday" in Paris at 00:30 on the 13th... and "today" for the phone that is in UTC+1 at 01:00.
        val late = Instant.parse("2026-11-12T23:30:00Z").toEpochMilli()
        val morning = Instant.parse("2026-11-13T00:30:00Z").toEpochMilli()
        assertEquals(VisitDay.TODAY, LastVisit.dayOf(late, morning, ZoneOffset.ofHours(-5)))
        assertEquals(VisitDay.YESTERDAY, LastVisit.dayOf(late, morning, ZoneOffset.UTC))
        assertEquals(VisitDay.TODAY, LastVisit.dayOf(late, morning, ZoneOffset.ofHours(1)))
    }

    @Test
    fun t19_aClockSetBack_makesNoAbsurdLine_andNoCrash() {
        val seen = state(member("t", PresenceValue.OUT, presenceAt = now - 20 * hour, lastUpdateAt = now - 20 * hour))
        // The event is two hours in the future of this phone: nothing is said.
        val future =
            state(
                member(
                    "t",
                    PresenceValue.IN_TRUSTED,
                    presenceAt = now + 2 * hour,
                    lastUpdateAt =
                        now + 2 * hour,
                ),
            )
        assertTrue(compare(seen, future).isEmpty())
        // A few minutes ahead (the server accepts up to 5): said as "now", never in the future.
        val close =
            state(
                member(
                    "t",
                    PresenceValue.IN_TRUSTED,
                    presenceAt = now + 2 * minute,
                    lastUpdateAt =
                        now + 2 * minute,
                ),
            )
        val line = compare(seen, close).single()
        assertEquals(now, line.shownAtMillis)
        assertEquals(VisitDay.TODAY, LastVisit.dayOf(now + 2 * hour, now, zone))
        // A time that goes BACK (another phone, a clock) is not a change.
        val older =
            state(
                member(
                    "t",
                    PresenceValue.IN_TRUSTED,
                    presenceAt = now - 30 * hour,
                    lastUpdateAt =
                        now - 30 * hour,
                ),
            )
        assertTrue(compare(seen, older).isEmpty())
    }

    // ---- the lines of the centre, and privacy (C1-7) -----------------------------------------------------------------------------------------------

    @Test
    fun aLineOfTheCentre_isAKeyOfIdentifiersAndTimes_neverANameOrAPlace() {
        val line = VisitLine("uid-1", VisitKind.DEPARTURE, PlaceKind.SCHOOL, now - hour, now - hour)
        val item = LastVisit.items(miff, listOf(line)).single()
        assertEquals(NotificationType.C1, item.type)
        assertEquals(miff, item.miffId)
        assertEquals(ItemState.SILENT, item.state)
        assertTrue("unread by default (decision Q2)", item.unread)
        val parsed = LastVisit.parse(item)!!
        assertEquals("uid-1", parsed.uid)
        assertEquals(VisitKind.DEPARTURE, parsed.kind)
        assertEquals(PlaceKind.SCHOOL, parsed.place)
        assertEquals(now - hour, parsed.shownAtMillis)
        // Nothing but the identifiers, a letter, a category word and a time.
        assertEquals("c1|miff-1|uid-1|d|SCHOOL|${now - hour}", item.id)
        assertTrue("redacted", item.toString().contains("redacted"))
    }

    @Test
    fun aLineWithNoPlace_saysAGenericPlace_andABrokenKeyIsNotALine() {
        val generic = LastVisit.items(miff, listOf(VisitLine("u", VisitKind.ARRIVAL, null, now, now))).single()
        assertNull(LastVisit.parse(generic)!!.place)
        assertNull(LastVisit.parse(generic.copy(id = "c1|only|three")))
        assertNull(LastVisit.parse(generic.copy(id = "c1|m|u|x||1")))
        assertNull(LastVisit.parse(generic.copy(id = "c1|m|u|a|NOWHERE|1")))
        assertNull(LastVisit.parse(generic.copy(id = "c1|m|u|a||notatime")))
        assertNull(LastVisit.parse(generic.copy(type = NotificationType.N1)))
    }

    // ---- the group in the list of the centre ------------------------------------------------------------------------------------------------------------

    private fun visitItem(
        uid: String,
        at: Long = now,
        miffId: String = miff,
    ) = LastVisit.items(miffId, listOf(VisitLine(uid, VisitKind.UPDATE, null, at, at))).single()

    @Test
    fun theGroupIsReplacedAsAWhole_theOtherLinesAreNotTouched_andAReadLineKeepsItsState() {
        val other = NotificationItem("n1|miff-1|u|1", NotificationType.N1, miff, now - hour)
        val old = visitItem("t", now - 3 * hour).copy(readAtMillis = now - hour)
        val stay = visitItem("a", now - 2 * hour)
        val items = listOf(other, old, stay)
        val replaced =
            NotificationList.replaceVisit(
                items,
                listOf(
                    old.copy(readAtMillis = null),
                    visitItem(
                        "b",
                        now - hour,
                    ),
                ),
                now,
            )
        assertEquals(setOf(other.id, old.id, visitItem("b", now - hour).id), replaced.map { it.id }.toSet())
        assertTrue("the line that is still there keeps its state (read)", !replaced.first { it.id == old.id }.unread)
        // Nothing new: the group is removed (never a line of days ago).
        assertEquals(listOf(other.id), NotificationList.replaceVisit(items, emptyList(), now).map { it.id })
    }

    @Test
    fun neverMoreThanThreeLinesOfTheGroup() {
        val four = (1..4).map { visitItem("u$it", now - it * hour) }
        assertEquals(3, NotificationList.replaceVisit(emptyList(), four, now).count { it.type == NotificationType.C1 })
    }

    @Test
    fun theLinesOfAPersonWhoLeftOrOfAMiffThatIsGone_areRemoved_andOnlyThoseOfTheGroup() {
        val n1 = NotificationItem("n1|miff-1|u|1", NotificationType.N1, miff, now)
        val items = listOf(n1, visitItem("t"), visitItem("a"), visitItem("t", miffId = "miff-2"))
        assertEquals(3, NotificationList.removeVisit(items) { LastVisit.uidOf(it) == "t" && it.miffId == miff }.size)
        assertEquals(listOf(n1.id), NotificationList.removeVisit(items) { true }.map { it.id })
    }

    @Test
    fun theGroupIsNotAnyNotificationOfThePhone_noSwitch_noChannel_noAlarm() {
        assertTrue(NotificationType.C1 !in NotificationType.PERIMETER)
        assertTrue(NotificationPreferences(enabled = false).switchOf(NotificationType.C1))
        assertTrue(
            "independent of the general switch (decision Q4)",
            NotificationPreferences(enabled = false).with(NotificationType.C1, false).switchOf(NotificationType.C1),
        )
    }

    // ---- helpers -------------------------------------------------------------------------------------------------------------------------------------

    private fun miffMember(
        uid: String,
        sharing: Boolean = true,
    ) = MiffMember(uid, uid.replaceFirstChar { it.uppercase() }, "X", null, MiffRole.MEMBER, sharing)

    private fun loaded(
        byUid: Map<String, MemberStatus>,
        fromCache: Boolean = false,
    ) = StatusesView.Loaded(byUid, fromCache)

    private fun status(
        presence: PresenceValue = PresenceValue.IN_TRUSTED,
        placeId: String? = "p1",
        at: Long = now - hour,
    ) = MemberStatus(presence, placeId, at, at, false, 80, MemberLocationAccess.BACKGROUND, now)

    private fun place(
        id: String,
        category: PlaceCategory,
        name: String,
    ) = Place(id, miff, name, PlaceType.TRUSTED, category, null, 48.0, 2.0, 100, "me")
}
