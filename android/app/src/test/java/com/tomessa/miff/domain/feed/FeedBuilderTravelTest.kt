package com.tomessa.miff.domain.feed

import com.tomessa.miff.domain.miff.MiffMember
import com.tomessa.miff.domain.miff.MiffRole
import com.tomessa.miff.domain.place.PlaceCategory
import com.tomessa.miff.domain.tracking.PresenceValue
import com.tomessa.miff.domain.tracking.StatusLevel
import com.tomessa.miff.domain.update.PromptLevel
import com.tomessa.miff.testsupport.testPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * MODE VOYAGE in the Feed (VOY-3A, criteria F1 to F6 of docs/tickets/mode-voyage.md): the card of a traveller is grey, says the day chosen, shows neither battery nor
 * "Localisation limitée"; the traveller is ignored in the colour of the Miff and out of the count "n sur m"; the bubble says it; the end passes to "Voyage terminé"
 * with no new reading; my own card and the bubble of encouragement are neutral. The cases are the contract with iOS.
 */
class FeedBuilderTravelTest {
    private val zone = ZoneOffset.UTC
    private val now = Instant.parse("2026-11-10T17:00:00Z").toEpochMilli()
    private val minute = 60_000L
    private val hour = 60 * minute
    private val me = "me"

    // The trip ends at 00:00 UTC of 16 Nov 2026: the day chosen is 15 Nov.
    private val until = Instant.parse("2026-11-16T00:00:00Z").toEpochMilli()

    private fun member(
        uid: String,
        first: String,
        sharing: Boolean = true,
    ) = MiffMember(uid, first, "X", null, if (uid == me) MiffRole.OWNER else MiffRole.MEMBER, sharing, "+33612345678")

    private fun green() =
        MemberStatus(
            PresenceValue.IN_TRUSTED,
            "p1",
            now - 10 * minute,
            now - 10 * minute,
            false,
            80,
            MemberLocationAccess.BACKGROUND,
            now - minute,
        )

    private fun trip(
        untilMillis: Long? = until,
        day: String? = "2026-11-15",
        battery: Int? = null,
        access: MemberLocationAccess? = MemberLocationAccess.DENIED,
        hasPhoto: Boolean = false,
    ) = MemberStatus(
        PresenceValue.TRAVEL,
        null,
        now - 2 * hour,
        now - 3 * hour,
        hasPhoto,
        battery,
        access,
        now - hour,
        untilMillis,
        day,
    )

    private val places = listOf(testPlace("p1", "Chez Mamie", category = PlaceCategory.HOME))

    private fun build(
        members: List<MiffMember>,
        statuses: Map<String, MemberStatus>,
        at: Long = now,
        waiting: Boolean = false,
    ) = FeedBuilder.build(
        FeedInput(
            "m1",
            "Chez nous",
            "CODE12345678",
            members,
            places,
            me,
            StatusesView.Loaded(statuses),
            waiting,
            false,
        ),
        at,
        zone,
    )

    private val trio = listOf(member(me, "Camille"), member("t", "Thomas"), member("a", "Amandine"))

    private fun card(
        content: FeedContent,
        uid: String,
    ) = content.cards.first { it.member.uid == uid }

    // --- F1: the card ---------------------------------------------------------------------------------------------------------

    @Test
    fun theCardOfATraveller_isGrey_withTheDayChosen_andNoBatteryNorLimitedLocation() {
        val content = build(trio, mapOf(me to green(), "t" to trip(battery = 40), "a" to green()))
        val card = card(content, "t")
        assertEquals(StatusLevel.NEUTRAL, card.level)
        val label = card.label as StateLabel.Traveling
        assertEquals(LocalDate.of(2026, 11, 15), label.until.date)
        assertFalse(label.until.showYear)
        assertFalse(label.mine)
        assertTrue(card.traveling)
        assertNull("nothing is measured on a trip", card.battery)
        assertFalse(card.limitedLocation)
        // "Depuis le 10 nov.": the day the trip began.
        assertEquals(TimeLine.TravelSince(now - 2 * hour), card.time)
        // Localiser, Message and Appeler stay (decision 8 of the PM).
        assertTrue(card.canLocate)
        assertEquals("+33612345678", card.phone)
    }

    @Test
    fun theDayShownIsTheOneChosen_notTheDayOfTheEndInstant() {
        // Read from another time zone, the instant of the end falls on the 16th (00:00 UTC) or the 15th (UTC-5): the day chosen is still the 15th.
        val west = ZoneOffset.ofHours(-5)
        val content =
            FeedBuilder.build(
                FeedInput("m1", "X", "C", trio, places, me, StatusesView.Loaded(mapOf("t" to trip())), false, false),
                now,
                west,
            )
        assertEquals(LocalDate.of(2026, 11, 15), (card(content, "t").label as StateLabel.Traveling).until.date)
    }

    @Test
    fun theYearIsShownOnlyWhenItIsNotThisYear() {
        val next = Instant.parse("2027-01-02T00:00:00Z").toEpochMilli()
        val content = build(trio, mapOf("t" to trip(untilMillis = next, day = "2027-01-01")))
        val label = card(content, "t").label as StateLabel.Traveling
        assertTrue(label.until.showYear)
    }

    @Test
    fun aPhotoOfATraveller_isStillOffered() {
        val content = build(trio, mapOf("t" to trip(hasPhoto = true)))
        assertTrue(card(content, "t").photo is PhotoSlot.Candidate)
    }

    // --- F4: the end --------------------------------------------------------------------------------------------------------------

    @Test
    fun fromTheEnd_theCardSaysTheTripEnded_withoutAnyNewReading() {
        val before = build(trio, mapOf("t" to trip()), at = until - 1)
        assertTrue(card(before, "t").label is StateLabel.Traveling)
        val after = build(trio, mapOf("t" to trip()), at = until)
        val card = card(after, "t")
        assertEquals(StateLabel.TravelEnded, card.label)
        assertEquals(StatusLevel.NEUTRAL, card.level)
        assertFalse("an ended trip is no longer a trip", card.traveling)
        assertEquals(TimeLine.None, card.time)
    }

    @Test
    fun aTripWithNoEnd_isReadAsNothingKnown_neutralAndWithoutALabelOfTrip() {
        val card = card(build(trio, mapOf("t" to trip(untilMillis = null))), "t")
        assertEquals(StateLabel.NoNews, card.label)
        assertEquals(StatusLevel.NEUTRAL, card.level)
    }

    @Test
    fun aTravellerWhoStoppedSharing_showsTheStopOfSharing_notATrip() {
        val members = listOf(member(me, "Camille"), member("t", "Thomas", sharing = false))
        assertEquals(StateLabel.SharingStopped, card(build(members, mapOf("t" to trip())), "t").label)
    }

    // --- F2: the colour of the Miff -----------------------------------------------------------------------------------------

    @Test
    fun aTraveller_isIgnoredInTheColourOfTheMiff() {
        val withGreen =
            build(
                listOf(member(me, "Camille"), member("t", "Thomas"), member("a", "Amandine")),
                mapOf(me to green(), "t" to trip(), "a" to green()),
            )
        assertEquals(StatusLevel.GREEN, withGreen.miffLevel)
        val alone = build(listOf(member(me, "Camille"), member("t", "Thomas")), mapOf("t" to trip()))
        assertEquals(StatusLevel.NEUTRAL, alone.miffLevel)
    }

    // --- F3: the bubble -------------------------------------------------------------------------------------------------------

    @Test
    fun theCount_nOnM_leavesTheTravellersOut() {
        // Me and Amandine green, Thomas travelling, Léa without news: 3 known of 4 counted would be wrong; the traveller is out: 2 of 3.
        val members = listOf(member(me, "Camille"), member("t", "Thomas"), member("a", "Amandine"), member("l", "Léa"))
        val content = build(members, mapOf(me to green(), "t" to trip(), "a" to green()))
        val bubble = content.bubble as Bubble.AllGood
        assertEquals(2, bubble.answered)
        assertEquals(3, bubble.total)
    }

    @Test
    fun withATravellerOnlyAmongTheOthers_theBubbleKeepsItsMessage_andAddsALine() {
        val content = build(trio, mapOf(me to green(), "t" to trip(), "a" to green()))
        assertTrue(content.bubble is Bubble.AllGood)
        // Nobody is silent once the traveller is out of the count: no "n sur m".
        assertNull((content.bubble as Bubble.AllGood).answered)
        val note = content.travelNote as TravelNote.One
        assertEquals("Thomas", note.name)
        assertEquals(LocalDate.of(2026, 11, 15), note.until.date)
    }

    @Test
    fun severalTravellers_areTold_byTheirNumber() {
        val members = listOf(member(me, "Camille"), member("t", "Thomas"), member("a", "Amandine"), member("l", "Léa"))
        val content = build(members, mapOf("t" to trip(), "a" to trip(), "l" to green()))
        assertEquals(TravelNote.Many(2), content.travelNote)
    }

    @Test
    fun whenEveryOtherMemberIsOnATrip_theBubbleIsNeutral_andSaysSo_neverAllGood_neverNoNews() {
        val one = build(listOf(member(me, "Camille"), member("t", "Thomas")), mapOf(me to green(), "t" to trip()))
        val bubble = one.bubble as Bubble.Travelers
        assertEquals(StatusLevel.NEUTRAL, bubble.level)
        assertTrue(bubble.note is TravelNote.One)
        // The bubble already says it: no second line.
        assertNull(one.travelNote)

        val many = build(trio, mapOf("t" to trip(), "a" to trip()))
        assertEquals(TravelNote.Many(2), (many.bubble as Bubble.Travelers).note)
    }

    @Test
    fun aTravellerAndSomebodyWithoutNews_isTheBubbleOfTheTravellers_not_NoNews() {
        val content = build(trio, mapOf("t" to trip()))
        assertTrue(content.bubble is Bubble.Travelers)
    }

    @Test
    fun aTraveller_neverMakesAnAlert_andAlertsKeepTheirPlace_withTheLineUnder() {
        val red =
            MemberStatus(
                PresenceValue.OUT,
                "p1",
                now - 6 * hour,
                now - 6 * hour,
                false,
                50,
                MemberLocationAccess.BACKGROUND,
                now - minute,
            )
        val content = build(trio, mapOf("t" to trip(), "a" to red))
        val alerts = content.bubble as Bubble.Alerts
        assertEquals(listOf("a"), alerts.pages.map { it.member.uid })
        assertTrue(content.travelNote is TravelNote.One)
    }

    @Test
    fun theTravelLine_isNotShownWhenIDoNotShare_orWhenMyAgreementIsWaitedFor() {
        val stopped = listOf(member(me, "Camille", sharing = false), member("t", "Thomas"))
        assertNull(build(stopped, mapOf("t" to trip())).travelNote)
        val waiting = build(trio, mapOf("t" to trip()), waiting = true)
        assertNull(waiting.travelNote)
        assertEquals(StateLabel.NewsHidden, card(waiting, "t").label)
    }

    // --- F5: my own card ---------------------------------------------------------------------------------------------------------

    @Test
    fun myOwnCard_saysModeVoyage_isNeutral_andInvitesToNothing() {
        val content = build(trio, mapOf(me to trip(), "t" to green(), "a" to green()))
        val mine = card(content, me)
        val label = mine.label as StateLabel.Traveling
        assertTrue(label.mine)
        assertEquals(StatusLevel.NEUTRAL, content.myLevel)
        assertEquals(label, content.myLabel)
        // No bubble of encouragement, even when the last news is days old.
        assertEquals(PromptLevel.NONE, content.myPrompt)
        // And I am not counted among the silent ones.
        assertTrue(mine.traveling)
        val bubble = content.bubble as Bubble.AllGood
        assertNull(bubble.answered)
    }

    @Test
    fun myTrip_isOver_atTheEnd_andTheBubbleOfEncouragementStaysNeutral() {
        val content = build(trio, mapOf(me to trip(), "t" to green(), "a" to green()), at = until + hour)
        assertEquals(StateLabel.TravelEnded, content.myLabel)
        assertEquals(PromptLevel.NONE, content.myPrompt)
    }
}
