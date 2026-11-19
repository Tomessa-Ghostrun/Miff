package com.tomessa.miff.domain.feed

import com.tomessa.miff.domain.alert.AlertDoc
import com.tomessa.miff.domain.miff.MiffMember
import com.tomessa.miff.domain.miff.MiffRole
import com.tomessa.miff.domain.tracking.PresenceValue
import com.tomessa.miff.domain.tracking.StatusLevel
import com.tomessa.miff.domain.update.PromptLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

/**
 * What the Feed of the ORIGIN Miff shows of an alert (docs/tickets/alerte-danger.md 7.6, criteria AD27 to AD31, AD50): the red card FIRST (the only exception to the order of the
 * cards), "Alerte en cours", the bubble red with the alert as its FIRST page and never "Tout va bien", the alert winning over the trip, nothing for an expired alert, nothing
 * for somebody who is not a member.
 */
class FeedBuilderAlertTest {
    private val zone = ZoneOffset.UTC
    private val now = Instant.parse("2026-11-18T17:00:00Z").toEpochMilli()
    private val minute = 60_000L
    private val me = "me"

    private fun member(
        uid: String,
        first: String,
        sharing: Boolean = true,
        phone: String? = "+33612345678",
    ) = MiffMember(uid, first, "X", null, if (uid == me) MiffRole.OWNER else MiffRole.MEMBER, sharing, phone)

    private fun green(updateAgo: Long = 12 * minute) =
        MemberStatus(PresenceValue.IN_TRUSTED, "p1", now - updateAgo, now - updateAgo, false, 80, null, now - minute)

    /** Out of every place and silent for 5 hours: red by the usual rules, and the bubble of encouragement speaks. */
    private fun silent() =
        MemberStatus(
            PresenceValue.OUT,
            "p1",
            now - 5 * 60 * minute,
            now - 5 * 60 * minute,
            false,
            80,
            null,
            now - minute,
        )

    private fun alert(
        uid: String,
        startedAgo: Long,
        active: Boolean = true,
        expiresIn: Long = 6 * 24 * 60 * minute,
    ) = AlertDoc(uid, active, now - startedAgo, now + expiresIn, 1)

    private val members =
        listOf(member(me, "Camille"), member("t", "Thomas"), member("a", "Amandine"), member("l", "Léa", phone = null))

    private fun build(
        alerts: List<AlertDoc>,
        statuses: Map<String, MemberStatus> =
            mapOf(
                "t" to green(10 * minute),
                "a" to green(5 * minute),
                "l" to green(30 * minute),
                me to green(60 * minute),
            ),
        mine: List<MiffMember> = members,
        waiting: Boolean = false,
    ) = FeedBuilder.build(
        FeedInput(
            "m1",
            "Chez nous",
            "CODE12345678",
            mine,
            emptyList(),
            me,
            StatusesView.Loaded(statuses),
            waiting,
            false,
            alerts,
        ),
        now,
        zone,
    )

    @Test
    fun noAlert_nothingChanges() {
        val content = build(emptyList())
        assertTrue(content.cards.none { it.alertActive })
        assertTrue(content.bubble is Bubble.AllGood)
    }

    @Test
    fun theCardOfTheAlertIsFirst_red_withTheWordsAlerteEnCours_andTheTimeOfTheStart() {
        val content = build(listOf(alert("l", startedAgo = 58 * minute)))
        val first = content.cards.first()
        assertEquals("l", first.member.uid)
        assertTrue(first.alertActive)
        assertEquals(StatusLevel.RED, first.level)
        assertEquals(StateLabel.AlertActive, first.label)
        assertEquals(TimeLine.Since(Moment.of(now - 58 * minute, now, zone)), first.time)
        // the others keep their usual order (most recent news first), nothing else is reordered
        assertEquals(
            listOf("a", "t", "me"),
            content.cards
                .drop(1)
                .map { it.member.uid }
                .filter { it != "l" }
                .let { it },
        )
    }

    @Test
    fun severalAlerts_theOldestFirst_stable() {
        val content = build(listOf(alert("a", startedAgo = 5 * minute), alert("t", startedAgo = 20 * minute)))
        assertEquals(listOf("t", "a"), content.cards.take(2).map { it.member.uid })
    }

    @Test
    fun myOwnAlertCardIsFirstToo_andMyColourIsRed() {
        val content = build(listOf(alert(me, startedAgo = minute)))
        assertEquals(
            me,
            content.cards
                .first()
                .member.uid,
        )
        assertEquals(StatusLevel.RED, content.myLevel)
        assertEquals(StateLabel.AlertActive, content.myLabel)
    }

    @Test
    fun theBubbleIsRed_theAlertIsItsFirstPage_andItNeverSaysAllGood() {
        val content = build(listOf(alert("t", startedAgo = 3 * minute)))
        val bubble = content.bubble as Bubble.Alerts
        assertEquals(StatusLevel.RED, bubble.level)
        val page = bubble.pages.first()
        assertEquals("t", page.member.uid)
        assertTrue(page.kind is AlertKind.Declared)
        assertTrue(page.canCall && page.canMessage)
        assertEquals(StatusLevel.RED, content.miffLevel)
    }

    @Test
    fun theAlertPageComesBeforeTheAutomaticReds_andAPersonHasOnePageOnly() {
        val stale = mapOf("t" to silent(), "a" to silent())
        val content = build(listOf(alert("a", startedAgo = minute)), statuses = stale)
        val pages = (content.bubble as Bubble.Alerts).pages
        assertEquals(listOf("a", "t"), pages.map { it.member.uid })
        assertTrue(pages[0].kind is AlertKind.Declared)
        assertFalse(pages[1].kind is AlertKind.Declared)
    }

    @Test
    fun theAlertWinsOverTheTrip_andTheNeutralGrey() {
        val travelling =
            MemberStatus(
                PresenceValue.TRAVEL,
                null,
                now - 60 * minute,
                now - 60 * minute,
                false,
                null,
                null,
                now - minute,
                now + 3 * 24 * 60 * minute,
                "2026-11-21",
            )
        val content = build(listOf(alert("t", startedAgo = minute)), statuses = mapOf("t" to travelling))
        val card = content.cards.first()
        assertEquals(StatusLevel.RED, card.level)
        assertFalse(card.traveling)
        assertEquals(StateLabel.AlertActive, card.label)
    }

    @Test
    fun anExpiredOrEndedAlert_showsNothing_noLine_noMessage() {
        val expired = alert("t", startedAgo = 8 * 24 * 60 * minute, expiresIn = -minute)
        val ended = alert("a", startedAgo = 10 * minute, active = false)
        val content = build(listOf(expired, ended))
        assertTrue(content.cards.none { it.alertActive })
        assertTrue(content.bubble is Bubble.AllGood)
    }

    @Test
    fun anAlertOfSomebodyWhoIsNotAMember_isIgnored() {
        val content = build(listOf(alert("stranger", startedAgo = minute)))
        assertTrue(content.cards.none { it.alertActive })
    }

    @Test
    fun whileMyAlertIsInProgressHere_theBubbleOfEncouragementAndTheCountdownSayNothing() {
        val old = mapOf(me to silent())
        val without = build(emptyList(), statuses = old)
        assertTrue(without.myPrompt != PromptLevel.NONE)
        val with = build(listOf(alert(me, startedAgo = minute)), statuses = old)
        assertEquals(PromptLevel.NONE, with.myPrompt)
        assertNull(with.myCountdown)
    }

    @Test
    fun aMemberWhoDoesNotShareStillSeesTheAlert_butCannotLocate() {
        // me: sharing stopped. The alert of somebody else is still shown (D16), but "Localiser" stays off (reciprocity unchanged, D15).
        val stopped = listOf(member(me, "Camille", sharing = false), member("t", "Thomas"))
        val content = build(listOf(alert("t", startedAgo = minute)), mine = stopped, statuses = emptyMap())
        val card = content.cards.first()
        assertTrue(card.alertActive)
        assertFalse(card.canLocate)
        assertTrue(card.phone != null)
        assertTrue(content.bubble is Bubble.Alerts)
    }
}
