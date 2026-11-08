package com.tomessa.miff.domain.feed

import com.tomessa.miff.domain.miff.MiffMember
import com.tomessa.miff.domain.miff.MiffRole
import com.tomessa.miff.domain.place.PlaceCategory
import com.tomessa.miff.domain.place.PlaceType
import com.tomessa.miff.domain.tracking.PresenceValue
import com.tomessa.miff.domain.tracking.StatusLevel
import com.tomessa.miff.domain.tracking.StatusThresholds
import com.tomessa.miff.testsupport.testPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

/**
 * The logic of the Feed (ticket FEED, criteria B1 to B6, C1 to C7), with no screen: the colour of the Miff, the bubble, the labels, the
 * lines of time, the battery, the order, the states of sharing. The thresholds are NOT written here: they are read from [StatusThresholds]
 * (criterion STA9), and the limits 1 h 59, 2 h 00, 2 h 01, 3 h 59, 4 h 00, 4 h 01 are built from them.
 */
class FeedBuilderTest {
    private val zone = ZoneOffset.UTC
    private val now = Instant.parse("2026-11-05T17:00:00Z").toEpochMilli()
    private val minute = 60_000L
    private val hour = 60 * minute

    private val me = "me"

    private fun member(
        uid: String,
        first: String,
        sharing: Boolean = true,
        phone: String? = "+33612345678",
    ) = MiffMember(uid, first, "X", null, if (uid == me) MiffRole.OWNER else MiffRole.MEMBER, sharing, phone)

    private fun status(
        presence: PresenceValue? = PresenceValue.IN_TRUSTED,
        placeId: String? = "p1",
        presenceAgo: Long = 12 * minute,
        lastUpdateAgo: Long? = presenceAgo,
        battery: Int? = 85,
        updatedAgo: Long = 2 * minute,
        access: MemberLocationAccess? = MemberLocationAccess.BACKGROUND,
    ) = MemberStatus(
        presence = presence,
        placeId = placeId,
        presenceAtMillis = now - presenceAgo,
        lastUpdateAtMillis = lastUpdateAgo?.let { now - it },
        hasPhoto = false,
        batteryPct = battery,
        locationAccess = access,
        updatedAtMillis = now - updatedAgo,
    )

    private fun input(
        members: List<MiffMember>,
        statuses: StatusesView,
        waiting: Boolean = false,
        offline: Boolean = false,
        places: List<com.tomessa.miff.domain.place.Place> =
            listOf(
                testPlace("p1", "Chez Mamie", category = PlaceCategory.HOME),
                testPlace("p2", "Chez Léa", category = PlaceCategory.RELATIVE),
                testPlace("p3", "Zone chantier", type = PlaceType.RISK, category = PlaceCategory.OTHER),
            ),
    ) = FeedInput("m1", "Chez nous", "CODE12345678", members, places, me, statuses, waiting, offline)

    private fun build(
        members: List<MiffMember>,
        statuses: Map<String, MemberStatus>,
        waiting: Boolean = false,
        offline: Boolean = false,
    ) = FeedBuilder.build(input(members, StatusesView.Loaded(statuses), waiting, offline), now, zone)

    private val trio = listOf(member(me, "Camille"), member("t", "Thomas"), member("a", "Amandine"))

    private fun card(
        content: FeedContent,
        uid: String,
    ) = content.cards.first { it.member.uid == uid }

    // --- Colour of the Miff (B1) ---------------------------------------------------------------------------------

    @Test
    fun miffColour_oneRedAmongGreens_isRed() {
        assertEquals(
            StatusLevel.RED,
            FeedBuilder.miffLevel(listOf(StatusLevel.GREEN, StatusLevel.RED, StatusLevel.GREEN)),
        )
    }

    @Test
    fun miffColour_orangeAndGreen_isOrange_redWinsOverOrange_allGreenIsGreen() {
        assertEquals(StatusLevel.ORANGE, FeedBuilder.miffLevel(listOf(StatusLevel.ORANGE, StatusLevel.GREEN)))
        assertEquals(StatusLevel.RED, FeedBuilder.miffLevel(listOf(StatusLevel.ORANGE, StatusLevel.RED)))
        assertEquals(StatusLevel.GREEN, FeedBuilder.miffLevel(listOf(StatusLevel.GREEN, StatusLevel.GREEN)))
    }

    @Test
    fun miffColour_neutralMembersAreIgnored_andNobodyKnownIsNeutral() {
        assertEquals(
            StatusLevel.GREEN,
            FeedBuilder.miffLevel(listOf(StatusLevel.GREEN, StatusLevel.NEUTRAL, StatusLevel.GREEN)),
        )
        assertEquals(StatusLevel.ORANGE, FeedBuilder.miffLevel(listOf(StatusLevel.NEUTRAL, StatusLevel.ORANGE)))
        assertEquals(StatusLevel.NEUTRAL, FeedBuilder.miffLevel(listOf(StatusLevel.NEUTRAL, StatusLevel.NEUTRAL)))
        assertEquals(StatusLevel.NEUTRAL, FeedBuilder.miffLevel(emptyList()))
    }

    // --- Thresholds, from the constants of LOC-2b (C2) -------------------------------------------------------------

    private fun levelAfter(elapsed: Long): StatusLevel {
        val content =
            build(trio, mapOf("t" to status(PresenceValue.OUT, presenceAgo = elapsed, lastUpdateAgo = elapsed)))
        return card(content, "t").level
    }

    @Test
    fun levels_atTheLimits_use_theConstantsOfStatusThresholds() {
        val orange = StatusThresholds.ORANGE_AFTER_MILLIS
        val red = StatusThresholds.RED_AFTER_MILLIS
        assertEquals(StatusLevel.GREEN, levelAfter(orange - minute)) // 1 h 59
        assertEquals(StatusLevel.ORANGE, levelAfter(orange)) // 2 h 00
        assertEquals(StatusLevel.ORANGE, levelAfter(orange + minute)) // 2 h 01
        assertEquals(StatusLevel.ORANGE, levelAfter(red - minute)) // 3 h 59
        assertEquals(StatusLevel.ORANGE, levelAfter(red)) // 4 h 00 included
        assertEquals(StatusLevel.RED, levelAfter(red + minute)) // 4 h 01
    }

    @Test
    fun theColourOfAMemberMovesWithTheClock_withoutAnyNewReading() {
        val statuses = mapOf("t" to status(PresenceValue.OUT, presenceAgo = 110 * minute, lastUpdateAgo = 110 * minute))
        val before = FeedBuilder.build(input(trio, StatusesView.Loaded(statuses)), now, zone)
        val later = FeedBuilder.build(input(trio, StatusesView.Loaded(statuses)), now + 15 * minute, zone)
        assertEquals(StatusLevel.GREEN, card(before, "t").level)
        assertEquals(StatusLevel.ORANGE, card(later, "t").level)
    }

    // --- Cards (C1, C7) --------------------------------------------------------------------------------------------

    @Test
    fun cards_areMineLast_theOthersKeepTheOrderOfTheList_neverByUrgency() {
        val members = listOf(member("t", "Thomas"), member(me, "Camille"), member("a", "Amandine"))
        val content =
            build(
                members,
                mapOf(
                    "t" to status(PresenceValue.IN_TRUSTED),
                    "a" to status(PresenceValue.OUT, presenceAgo = 5 * hour, lastUpdateAgo = 5 * hour),
                    me to status(PresenceValue.IN_TRUSTED),
                ),
            )
        // Decision of the PM (2026-11-07): me last; Thomas stays before Amandine although she is red (never reordered by urgency).
        assertEquals(listOf("t", "a", me), content.cards.map { it.member.uid })
        assertEquals(StatusLevel.RED, card(content, "a").level)
        assertTrue(content.cards.last().isMe)
    }

    @Test
    fun cards_meLast_whateverMyPlaceInTheList() {
        val others = listOf(member("o", "Olivia"), member("t", "Thomas"), member("a", "Amandine"))
        // First, in the middle, last: the others always come before me, in exactly the order they were given.
        listOf(0, 1, 2, 3).forEach { index ->
            val members = others.toMutableList().apply { add(index, member(me, "Camille")) }
            assertEquals(listOf("o", "t", "a", me), FeedBuilder.orderMembers(members, me).map { it.uid })
            val content = build(members, emptyMap())
            assertEquals(listOf("o", "t", "a", me), content.cards.map { it.member.uid })
            assertEquals(listOf(false, false, false, true), content.cards.map { it.isMe })
        }
    }

    @Test
    fun cards_meAlone_isTheOnlyCard_andNobodyIsMissing() {
        val alone = listOf(member(me, "Camille"))
        assertEquals(listOf(me), build(alone, emptyMap()).cards.map { it.member.uid })
        assertEquals(emptyList<String>(), FeedBuilder.orderMembers(emptyList(), me).map { it.uid })
    }

    @Test
    fun myCard_hasNoGesture_andOthersHaveThem_whenKnown() {
        val content = build(trio, mapOf(me to status(), "t" to status(), "a" to status()))
        val mine = card(content, me)
        assertTrue(mine.isMe)
        assertNull(mine.phone)
        assertFalse(mine.canLocate)
        val thomas = card(content, "t")
        assertEquals("+33612345678", thomas.phone)
        assertTrue(thomas.canLocate)
    }

    @Test
    fun aMemberWithoutANumber_hasNoMessageNorCall_butCanBeLocated() {
        val members = listOf(member(me, "Camille"), member("t", "Thomas", phone = null))
        val thomas = card(build(members, mapOf("t" to status())), "t")
        assertNull(thomas.phone)
        assertTrue(thomas.canLocate)
    }

    @Test
    fun aNeutralMember_canBeLocated_andCalled_theSheetAnswersCalmlyWhenThereIsNoPosition() {
        val content = build(trio, mapOf(me to status(), "t" to status(PresenceValue.UNKNOWN, placeId = null)))
        val thomas = card(content, "t")
        assertEquals(StatusLevel.NEUTRAL, thomas.level)
        assertEquals(StateLabel.NoNews, thomas.label)
        // Ticket 5.6: another member whose sharing is on can be located even with no known status ("pas encore de position connue").
        assertTrue(thomas.canLocate)
        assertNotNull(thomas.phone)
        assertEquals(TimeLine.None, thomas.time)
    }

    @Test
    fun locate_isOfferedOnlyWhenTheStatusesAreReallyRead() {
        listOf(StatusesView.Pending, StatusesView.Off, StatusesView.Unknown, StatusesView.Waiting).forEach { view ->
            val content = FeedBuilder.build(input(trio, view), now, zone)
            assertFalse("$view", card(content, "t").canLocate)
        }
        // Read, even if nobody has a status yet.
        assertTrue(card(build(trio, emptyMap()), "t").canLocate)
        // Never on my own card, never for a member who stopped sharing.
        val members = listOf(member(me, "Camille"), member("t", "Thomas", sharing = false))
        val content = build(members, mapOf(me to status()))
        assertFalse(card(content, "t").canLocate)
        assertFalse(card(content, me).canLocate)
    }

    @Test
    fun aMemberWhoStoppedSharing_isNeutral_andSaysSo() {
        val members = listOf(member(me, "Camille"), member("t", "Thomas", sharing = false))
        val content = build(members, mapOf(me to status()))
        val thomas = card(content, "t")
        assertEquals(StateLabel.SharingStopped, thomas.label)
        assertEquals(StatusLevel.NEUTRAL, thomas.level)
        assertFalse(thomas.canLocate)
    }

    // --- Labels (C2) -----------------------------------------------------------------------------------------------

    @Test
    fun labels_followTheCategoryOfThePlace_decisionOfThePm_2026_11_08() {
        val places =
            listOf(
                testPlace("home", "Chez Mamie", category = PlaceCategory.HOME),
                testPlace("work", "Bureau", category = PlaceCategory.WORK),
                testPlace("school", "Lycée Hugo", category = PlaceCategory.SCHOOL),
                testPlace("sport", "Salle Fit", category = PlaceCategory.SPORT),
                testPlace("relative", "Chez Léa", category = PlaceCategory.RELATIVE),
                testPlace("leisure", "Cinéma Gaumont", category = PlaceCategory.LEISURE),
                testPlace("other", "Chez le coiffeur", category = PlaceCategory.OTHER),
                testPlace("none", "Lieu ancien", category = null),
                testPlace("blank", "   ", category = PlaceCategory.OTHER),
            )

        fun labelAt(placeId: String) =
            card(
                FeedBuilder.build(
                    input(trio, StatusesView.Loaded(mapOf("t" to status(placeId = placeId))), places = places),
                    now,
                    zone,
                ),
                "t",
            ).label
        assertEquals(StateLabel.At(PlaceRef.Category(PlaceKind.HOME)), labelAt("home"))
        assertEquals(StateLabel.At(PlaceRef.Category(PlaceKind.WORK)), labelAt("work"))
        assertEquals(StateLabel.At(PlaceRef.Category(PlaceKind.SCHOOL)), labelAt("school"))
        assertEquals(StateLabel.At(PlaceRef.Category(PlaceKind.SPORT)), labelAt("sport"))
        assertEquals(StateLabel.At(PlaceRef.Category(PlaceKind.RELATIVE)), labelAt("relative"))
        // Loisir, Autre and a place with no category show the NAME of the place.
        assertEquals(StateLabel.At(PlaceRef.Named("Cinéma Gaumont")), labelAt("leisure"))
        assertEquals(StateLabel.At(PlaceRef.Named("Chez le coiffeur")), labelAt("other"))
        assertEquals(StateLabel.At(PlaceRef.Named("Lieu ancien")), labelAt("none"))
        // A blank name is never shown as an empty label.
        assertEquals(StateLabel.At(PlaceRef.Unknown), labelAt("blank"))
        // Same rule for MY card.
        val mine =
            FeedBuilder.build(
                input(trio, StatusesView.Loaded(mapOf(me to status(placeId = "relative"))), places = places),
                now,
                zone,
            )
        assertEquals(StateLabel.At(PlaceRef.Category(PlaceKind.RELATIVE)), mine.myLabel)
    }

    @Test
    fun labels_aSentenceAboutAPlaceLeft_stillNamesARelativesPlace() {
        val places = listOf(testPlace("relative", "Chez Léa", category = PlaceCategory.RELATIVE))
        val left =
            FeedBuilder.build(
                input(
                    trio,
                    StatusesView.Loaded(mapOf("t" to status(PresenceValue.OUT, "relative", 30 * minute, 30 * minute))),
                    places = places,
                ),
                now,
                zone,
            )
        assertEquals(StateLabel.Left(PlaceRef.Named("Chez Léa")), card(left, "t").label)
    }

    @Test
    fun labels_leftAPlace_keepsThePlace_inTheColourOfTheLevel() {
        val left = card(build(trio, mapOf("t" to status(PresenceValue.OUT, "p1", 30 * minute, 30 * minute))), "t")
        assertEquals(StateLabel.Left(PlaceRef.Category(PlaceKind.HOME)), left.label)
        assertEquals(StatusLevel.GREEN, left.level)
        assertEquals(TimeLine.DepartedAgo(Age.Minutes(30)), left.time)
    }

    @Test
    fun labels_aDeletedPlace_isGeneric_neverAGuess() {
        val gone = card(build(trio, mapOf("t" to status(placeId = "zzz"))), "t")
        assertEquals(StateLabel.At(PlaceRef.Unknown), gone.label)
        val goneLeft = card(build(trio, mapOf("t" to status(PresenceValue.OUT, "zzz", 90 * minute, 90 * minute))), "t")
        assertEquals(StateLabel.Left(PlaceRef.Unknown), goneLeft.label)
    }

    @Test
    fun labels_aDangerZone_isRed_withItsOwnLabel() {
        val danger = card(build(trio, mapOf("t" to status(PresenceValue.IN_RISK, "p3", 20 * minute, 20 * minute))), "t")
        assertEquals(StatusLevel.RED, danger.level)
        assertEquals(StateLabel.Danger, danger.label)
        assertEquals(TimeLine.InDangerSince(Moment(now - 20 * minute, Day.TODAY)), danger.time)
    }

    // --- Lines of time (C2) ----------------------------------------------------------------------------------------

    @Test
    fun timeLines_relativeWhileGreen_absoluteFromOrange() {
        val inside = card(build(trio, mapOf("t" to status(presenceAgo = 12 * minute))), "t")
        assertEquals(TimeLine.Since(Age.Minutes(12)), inside.time)
        val hours = card(build(trio, mapOf("t" to status(presenceAgo = 3 * hour))), "t")
        assertEquals(TimeLine.Since(Age.Hours(3)), hours.time)
        val orange = card(build(trio, mapOf("t" to status(PresenceValue.OUT, presenceAgo = 130 * minute))), "t")
        assertEquals(TimeLine.LatestNews(Moment(now - 130 * minute, Day.TODAY)), orange.time)
        val red = card(build(trio, mapOf("t" to status(PresenceValue.OUT, presenceAgo = 5 * hour))), "t")
        assertEquals(TimeLine.NoNewsSince(Moment(now - 5 * hour, Day.TODAY)), red.time)
    }

    @Test
    fun timeLine_afterMidnight_saysYesterday() {
        val yesterday = card(build(trio, mapOf("t" to status(presenceAgo = 20 * hour))), "t")
        assertEquals(TimeLine.Since(Age.Hours(20)), yesterday.time)
        val older = card(build(trio, mapOf("t" to status(presenceAgo = 30 * hour))), "t")
        assertEquals(TimeLine.Since(Age.Yesterday(now - 30 * hour)), older.time)
        val redYesterday =
            card(build(trio, mapOf("t" to status(PresenceValue.OUT, presenceAgo = 18 * hour))), "t").time
        assertEquals(TimeLine.NoNewsSince(Moment(now - 18 * hour, Day.YESTERDAY)), redYesterday)
    }

    @Test
    fun anAgeNeverSaysZeroMinutes_andAFutureReferenceIsOneMinute() {
        assertEquals(Age.Minutes(1), Age.of(now - 10_000, now, zone))
        assertEquals(Age.Minutes(1), Age.of(now + 5 * minute, now, zone))
    }

    // --- Battery (C4) ----------------------------------------------------------------------------------------------

    @Test
    fun battery_isThePercentageAlone_neverDated_hiddenAfterSixHours_orWhenAbsent() {
        // Decision of the PM (2026-11-07): the card never says WHEN the level was measured, whatever the age of the value.
        val recent = card(build(trio, mapOf("t" to status(updatedAgo = 59 * minute))), "t")
        assertEquals(Battery(85), recent.battery)
        val anHourAndAHalf = card(build(trio, mapOf("t" to status(updatedAgo = 90 * minute))), "t")
        assertEquals(Battery(85), anHourAndAHalf.battery)
        // Decision of the PM (2026-11-07): hidden past 6 h (BATTERY_MAX_AGE), the limit itself still shown.
        assertEquals(6 * hour, FeedBuilder.BATTERY_MAX_AGE_MILLIS)
        val almostSixHours = card(build(trio, mapOf("t" to status(updatedAgo = 5 * hour + 59 * minute))), "t")
        assertEquals(Battery(85), almostSixHours.battery)
        val sixHours = card(build(trio, mapOf("t" to status(updatedAgo = 6 * hour))), "t")
        assertEquals(Battery(85), sixHours.battery)
        val justOver = card(build(trio, mapOf("t" to status(updatedAgo = 6 * hour + minute))), "t")
        assertNull(justOver.battery)
        val old = card(build(trio, mapOf("t" to status(updatedAgo = 25 * hour))), "t")
        assertNull(old.battery)
        val absent = card(build(trio, mapOf("t" to status(battery = null))), "t")
        assertNull(absent.battery)
    }

    @Test
    fun battery_ageIsCountedFromUpdatedAt_notFromTheLastUpdate() {
        // A write that only refreshes the battery moves updatedAt and nothing else: the old photo (lastUpdateAt, 10 h ago) must not hide it...
        val refreshed =
            status(PresenceValue.IN_TRUSTED, presenceAgo = 10 * hour, lastUpdateAgo = 10 * hour, updatedAgo = minute)
        assertEquals(Battery(85), card(build(trio, mapOf("t" to refreshed)), "t").battery)
        // ...and a recent photo does not keep an old battery on screen.
        val stale =
            status(PresenceValue.IN_TRUSTED, presenceAgo = minute, lastUpdateAgo = minute, updatedAgo = 7 * hour)
        assertNull(card(build(trio, mapOf("t" to stale)), "t").battery)
    }

    // --- "Localisation limitée" (C5) ---------------------------------------------------------------------------------

    @Test
    fun limitedLocation_isABadge_withNoEffectOnTheColourOrTheBubble() {
        val content =
            build(
                trio,
                mapOf(
                    me to status(),
                    "t" to status(access = MemberLocationAccess.DENIED),
                    "a" to status(access = MemberLocationAccess.FOREGROUND),
                ),
            )
        assertTrue(card(content, "t").limitedLocation)
        assertTrue(card(content, "a").limitedLocation)
        assertFalse(card(content, me).limitedLocation)
        assertEquals(StatusLevel.GREEN, card(content, "t").level)
        assertTrue(content.bubble is Bubble.AllGood)
    }

    // --- Bubble (B2 to B6) -----------------------------------------------------------------------------------------

    @Test
    fun bubble_allGood_needsAtLeastOneOtherKnown_andAllKnownGreen() {
        val content = build(trio, mapOf(me to status(), "t" to status(presenceAgo = 25 * minute), "a" to status()))
        val bubble = content.bubble as Bubble.AllGood
        // "Dernière nouvelle": the most recent of the OTHERS, never mine.
        assertEquals(Age.Minutes(12), bubble.latestNews)
        assertNull(bubble.answered)
        assertEquals(StatusLevel.GREEN, content.miffLevel)
    }

    @Test
    fun bubble_latestNews_neverUsesMyOwnUpdate() {
        val content =
            build(
                trio,
                mapOf(
                    me to status(presenceAgo = 1 * minute),
                    "t" to status(presenceAgo = 40 * minute),
                    "a" to status(presenceAgo = 50 * minute),
                ),
            )
        assertEquals(Age.Minutes(40), (content.bubble as Bubble.AllGood).latestNews)
    }

    @Test
    fun bubble_withSilentMembers_addsNOutOfM_andStaysGreen() {
        val four = trio + member("n", "Nathan")
        val content = build(four, mapOf(me to status(), "t" to status(), "a" to status()))
        val bubble = content.bubble as Bubble.AllGood
        assertEquals(3, bubble.answered)
        assertEquals(4, bubble.total)
    }

    @Test
    fun bubble_noOtherKnown_isNeutral_neverASentenceOfSafety() {
        val content = build(trio, mapOf(me to status()))
        assertEquals(Bubble.NoNews, content.bubble)
        assertEquals(StatusLevel.NEUTRAL, content.bubble.level)
        val none = build(trio, emptyMap())
        assertEquals(Bubble.NoNews, none.bubble)
    }

    @Test
    fun bubble_aloneInTheMiff_isNeutral() {
        val content = build(listOf(member(me, "Camille")), mapOf(me to status()))
        assertEquals(Bubble.Alone, content.bubble)
        assertEquals(1, content.cards.size)
    }

    @Test
    fun bubble_alerts_redFirst_thenOrange_oldestFirstInALevel() {
        val four = trio + member("n", "Nathan")
        val content =
            build(
                four,
                mapOf(
                    me to status(),
                    "t" to status(PresenceValue.OUT, presenceAgo = 130 * minute),
                    "a" to status(PresenceValue.OUT, presenceAgo = 5 * hour),
                    "n" to status(PresenceValue.OUT, presenceAgo = 170 * minute),
                ),
            )
        val bubble = content.bubble as Bubble.Alerts
        // Amandine is red; between the two oranges the OLDEST reference (Nathan, 170 min) comes first.
        assertEquals(listOf("a", "n", "t"), bubble.pages.map { it.member.uid })
        assertEquals(StatusLevel.RED, bubble.level)
        assertEquals(StatusLevel.RED, content.miffLevel)
    }

    @Test
    fun bubble_aliveAlertDisappearing_removesItsPage() {
        val before =
            build(
                trio,
                mapOf(
                    "t" to status(PresenceValue.OUT, presenceAgo = 130 * minute),
                    "a" to status(PresenceValue.OUT, presenceAgo = 150 * minute),
                ),
            )
        assertEquals(2, (before.bubble as Bubble.Alerts).pages.size)
        val after =
            build(
                trio,
                mapOf(
                    "t" to status(PresenceValue.IN_TRUSTED),
                    "a" to status(PresenceValue.OUT, presenceAgo = 150 * minute),
                ),
            )
        assertEquals(1, (after.bubble as Bubble.Alerts).pages.size)
    }

    @Test
    fun alertKinds_orangeLeft_redStale_dangerZone() {
        val content =
            build(
                trio + member("n", "Nathan"),
                mapOf(
                    "t" to status(PresenceValue.OUT, "p1", 130 * minute, 130 * minute),
                    "a" to status(PresenceValue.OUT, "p2", 5 * hour, 5 * hour),
                    "n" to status(PresenceValue.IN_RISK, "p3", 20 * minute, 20 * minute),
                ),
            )
        val pages = (content.bubble as Bubble.Alerts).pages.associateBy { it.member.uid }
        assertEquals(
            AlertKind.Left(PlaceRef.Category(PlaceKind.HOME), Moment(now - 130 * minute, Day.TODAY)),
            pages.getValue("t").kind,
        )
        assertEquals(AlertKind.NoNewsSince(Moment(now - 5 * hour, Day.TODAY)), pages.getValue("a").kind)
        // The danger zone says its NAME for the loved ones ("{Prénom} est à {lieu}").
        assertEquals(
            AlertKind.Danger(PlaceRef.Named("Zone chantier"), Moment(now - 20 * minute, Day.TODAY)),
            pages.getValue("n").kind,
        )
    }

    @Test
    fun message_isOfferedForAnotherMemberWithANumber_neverOnMyOwnPage() {
        val members = listOf(member(me, "Camille"), member("t", "Thomas"), member("a", "Amandine", phone = null))
        val content =
            build(
                members,
                mapOf(
                    me to status(PresenceValue.OUT, presenceAgo = 5 * hour),
                    "t" to status(PresenceValue.OUT, presenceAgo = 5 * hour),
                    "a" to status(PresenceValue.OUT, presenceAgo = 5 * hour),
                ),
            )
        val pages = (content.bubble as Bubble.Alerts).pages.associateBy { it.member.uid }
        assertFalse(pages.getValue(me).canMessage)
        assertTrue(pages.getValue(me).isMe)
        assertTrue(pages.getValue("t").canMessage)
        assertFalse(pages.getValue("a").canMessage)
    }

    @Test
    fun iAmTheOnlyOrangeOrRed_theBubbleSpeaksToMe() {
        val content =
            build(trio, mapOf(me to status(PresenceValue.OUT, "p1", 130 * minute, 130 * minute), "t" to status()))
        val pages = (content.bubble as Bubble.Alerts).pages
        assertEquals(1, pages.size)
        assertTrue(pages.single().isMe)
        assertEquals(StatusLevel.ORANGE, content.myLevel)
    }

    @Test
    fun aRecentUpdateAfterAnOldDeparture_isGreen_theReferenceBeingTheMostRecent() {
        // Decision N2: the reference of the colour is max(departure, last update). Left 3 h ago but gave news 10 min ago: green.
        val departed =
            card(
                build(
                    trio,
                    mapOf("t" to status(PresenceValue.OUT, "p1", presenceAgo = 3 * hour, lastUpdateAgo = 10 * minute)),
                ),
                "t",
            )
        assertEquals(StatusLevel.GREEN, departed.level)
        assertEquals(StateLabel.Left(PlaceRef.Category(PlaceKind.HOME)), departed.label)
        assertEquals(TimeLine.DepartedAgo(Age.Hours(3)), departed.time)
    }

    @Test
    fun theColourOfTheBubble_andTheColourOfTheMiff_areTheSameSource() {
        val content =
            build(
                trio,
                mapOf(
                    me to status(),
                    "t" to status(PresenceValue.OUT, presenceAgo = 130 * minute),
                    "a" to status(PresenceValue.OUT, presenceAgo = 5 * hour),
                ),
            )
        assertEquals(content.miffLevel, content.bubble.level)
        assertEquals(StatusLevel.RED, content.bubble.level)
        val orangeOnly =
            build(trio, mapOf(me to status(), "t" to status(PresenceValue.OUT, presenceAgo = 130 * minute)))
        assertEquals(StatusLevel.ORANGE, orangeOnly.bubble.level)
        assertEquals(orangeOnly.miffLevel, orangeOnly.bubble.level)
    }

    @Test
    fun dataThatOnlyTheCacheVouchesFor_isToldWithItsDate_evenOnline() {
        val cached =
            FeedBuilder.build(
                input(
                    trio,
                    StatusesView.Loaded(
                        mapOf(
                            me to status(updatedAgo = 20 * minute),
                            "t" to status(updatedAgo = 30 * minute),
                        ),
                        fromCache = true,
                    ),
                ),
                now,
                zone,
            )
        val banner = cached.banners.filterIsInstance<FeedBanner.Offline>().single()
        assertEquals(Moment(now - 20 * minute, Day.TODAY), banner.latest)
        val confirmed = build(trio, mapOf(me to status(), "t" to status()))
        assertTrue(confirmed.banners.isEmpty())
    }

    // --- Sharing and hidden states (V6, V7, V8) --------------------------------------------------------------------

    @Test
    fun iStoppedSharing_theOthersAreHidden_neutral_andTheBubbleSaysSo() {
        val members = listOf(member(me, "Camille", sharing = false), member("t", "Thomas"))
        val content =
            FeedBuilder.build(input(members, StatusesView.Off), now, zone)
        assertEquals(Bubble.SharingStopped, content.bubble)
        assertEquals(StateLabel.SharingStopped, card(content, me).label)
        val thomas = card(content, "t")
        assertEquals(StateLabel.NewsHidden, thomas.label)
        assertEquals(StatusLevel.NEUTRAL, thomas.level)
        assertFalse(thomas.canLocate)
        // Message and call stay.
        assertNotNull(thomas.phone)
    }

    @Test
    fun waitingForMyAgreement_nothingIsShown_theBubbleIsNeutral_myCardSaysWhy() {
        val content =
            FeedBuilder.build(input(trio, StatusesView.Off, waiting = true), now, zone)
        assertEquals(Bubble.AwaitingAgreement, content.bubble)
        assertEquals(StateLabel.WaitingAgreement, card(content, me).label)
        assertEquals(StateLabel.NewsHidden, card(content, "t").label)
        assertEquals(StatusLevel.NEUTRAL, content.miffLevel)
    }

    @Test
    fun whileMyStatusIsNotPublished_aBannerSaysSo_andNothingIsAnError() {
        val content = FeedBuilder.build(input(trio, StatusesView.Pending), now, zone)
        assertTrue(content.banners.contains(FeedBanner.Publishing))
        assertEquals(Bubble.NoNews, content.bubble)
        assertEquals(StatusLevel.NEUTRAL, content.miffLevel)
    }

    @Test
    fun theBannerOfPublishingIsNotShownWhenISharenothing() {
        val members = listOf(member(me, "Camille", sharing = false), member("t", "Thomas"))
        val content = FeedBuilder.build(input(members, StatusesView.Pending), now, zone)
        assertFalse(content.banners.contains(FeedBanner.Publishing))
    }

    // --- Offline (E4) ----------------------------------------------------------------------------------------------

    @Test
    fun offline_aBannerGivesTheDateOfTheLatestInformation_andAnAlertAddsItsOwn() {
        val content =
            build(
                trio,
                mapOf(
                    me to status(updatedAgo = 5 * minute),
                    "t" to status(PresenceValue.OUT, presenceAgo = 130 * minute, updatedAgo = 40 * minute),
                ),
                offline = true,
            )
        val banner = content.banners.filterIsInstance<FeedBanner.Offline>().single()
        assertEquals(Moment(now - 5 * minute, Day.TODAY), banner.latest)
        val page = (content.bubble as Bubble.Alerts).pages.single()
        assertEquals(Moment(now - 5 * minute, Day.TODAY), page.offlineSince)
    }

    @Test
    fun offline_withNothingKnown_hasNoDate_andNeverAGreenBubble() {
        val content = FeedBuilder.build(input(trio, StatusesView.Loaded(emptyMap()), offline = true), now, zone)
        assertNull(
            content.banners
                .filterIsInstance<FeedBanner.Offline>()
                .single()
                .latest,
        )
        assertEquals(Bubble.NoNews, content.bubble)
    }

    @Test
    fun online_noOfflineBanner() {
        val content = build(trio, mapOf(me to status(), "t" to status()))
        assertTrue(content.banners.isEmpty())
    }

    // --- Miff full, announcements, privacy -------------------------------------------------------------------------

    @Test
    fun aMiffOfFifteenMembers_isFull() {
        val many = (1..14).map { member("u$it", "P$it") } + member(me, "Camille")
        assertTrue(build(many, emptyMap()).isFull)
        assertFalse(build(many.drop(1), emptyMap()).isFull)
    }

    @Test
    fun announceKey_changesWithTheLevelOrTheAlerts_notWithTheMinutes() {
        val statuses = mapOf("t" to status(PresenceValue.OUT, presenceAgo = 130 * minute, lastUpdateAgo = 130 * minute))
        val first = FeedBuilder.build(input(trio, StatusesView.Loaded(statuses)), now, zone)
        val later = FeedBuilder.build(input(trio, StatusesView.Loaded(statuses)), now + 3 * minute, zone)
        assertEquals(first.announceKey, later.announceKey)
        val calm = build(trio, mapOf("t" to status()))
        assertTrue(first.announceKey != calm.announceKey)
        val red = FeedBuilder.build(input(trio, StatusesView.Loaded(statuses)), now + 2 * hour, zone)
        assertTrue(first.announceKey != red.announceKey)
    }

    @Test
    fun theClassesThatCarryPersonalDataHideThemInToString() {
        val content = build(trio, mapOf("t" to status()))
        listOf<Any>(
            content,
            card(content, "t"),
            status(),
            PlaceRef.Named("Chez Mamie"),
            input(trio, StatusesView.Loaded(emptyMap())),
        ).forEach {
            val text = it.toString()
            assertFalse(text, "Thomas" in text || "Mamie" in text || "CODE12345678" in text)
        }
    }
}
