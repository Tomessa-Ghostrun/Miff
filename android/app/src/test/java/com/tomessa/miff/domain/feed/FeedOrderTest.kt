package com.tomessa.miff.domain.feed

import com.tomessa.miff.domain.miff.MiffMember
import com.tomessa.miff.domain.miff.MiffRole
import com.tomessa.miff.domain.tracking.PresenceValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

/**
 * FEED-ORDRE (ticket docs/tickets/feed-ordre-derniere-update.md, sections 3, 5 and 7): the order of the cards by date of the last news, and the gate that holds
 * the order back during a gesture. The TABLE of cases of [orderTable] is the contract shared with iOS: same arrival order, same dates, same expected order.
 */
class FeedOrderTest {
    private val zone = ZoneOffset.UTC
    private val now = 1_800_000_000_000L
    private val minute = 60_000L
    private val hour = 60 * minute
    private val me = "me"

    private fun member(
        uid: String,
        sharing: Boolean = true,
    ) = MiffMember(
        uid,
        uid.uppercase(),
        "X",
        null,
        if (uid ==
            me
        ) {
            MiffRole.OWNER
        } else {
            MiffRole.MEMBER
        },
        sharing,
        "+33612345678",
    )

    private fun status(
        presence: PresenceValue? = PresenceValue.IN_TRUSTED,
        lastUpdateAgo: Long?,
        hasPhoto: Boolean = false,
        presenceAgo: Long = 20 * hour,
    ) = MemberStatus(
        presence = presence,
        placeId = null,
        presenceAtMillis = now - presenceAgo,
        lastUpdateAtMillis = lastUpdateAgo?.let { now - it },
        hasPhoto = hasPhoto,
        batteryPct = 80,
        locationAccess = MemberLocationAccess.BACKGROUND,
        updatedAtMillis = now - minute,
        travelUntilMillis = if (presence == PresenceValue.TRAVEL) now + 24 * hour else null,
    )

    private fun build(
        members: List<MiffMember>,
        statuses: Map<String, MemberStatus>,
    ) = FeedBuilder.build(
        FeedInput(
            "m1",
            "Chez nous",
            "CODE12345678",
            members,
            emptyList(),
            me,
            StatusesView.Loaded(statuses),
            false,
            false,
        ),
        now,
        zone,
    )

    private fun order(
        members: List<String>,
        ages: Map<String, Long?>,
        presences: Map<String, PresenceValue?> = emptyMap(),
        sharingOff: Set<String> = emptySet(),
        manualAfterActivation: Set<String> = emptySet(),
    ): List<String> {
        val statuses =
            ages
                .mapNotNull { (uid, ago) ->
                    if (ago ==
                        null
                    ) {
                        null
                    } else {
                        val presence = presences[uid] ?: PresenceValue.IN_TRUSTED
                        val presenceAgo =
                            if (presence ==
                                PresenceValue.UNKNOWN
                            ) {
                                ago + (
                                    if (uid in
                                        manualAfterActivation
                                    ) {
                                        10 * hour
                                    } else {
                                        0L
                                    }
                                )
                            } else {
                                20 * hour
                            }
                        uid to status(presence, ago, presenceAgo = presenceAgo)
                    }
                }.toMap()
        return build(members.map { member(it, it !in sharingOff) }, statuses).cards.map { it.member.uid }
    }

    // --- The table shared with iOS (criteria A1 to A4) ---------------------------------------------------------------

    private class Case(
        val name: String,
        val arrival: List<String>,
        /** Hours since the last update; null = no status at all. */
        val agesHours: Map<String, Long?>,
        val expected: List<String>,
        val presences: Map<String, PresenceValue?> = emptyMap(),
        val sharingOff: Set<String> = emptySet(),
        /** Members whose presence is `unknown` and who published an update AFTER the first status (otherwise both times are equal: sharing just activated). */
        val manualAfterActivation: Set<String> = emptySet(),
    )

    private val orderTable =
        listOf(
            Case(
                "A1 most recent first",
                listOf("a", "b", "c"),
                mapOf("a" to 3L, "b" to 1L, "c" to 2L),
                listOf("b", "c", "a"),
            ),
            Case(
                "A2 me newest comes first",
                listOf("a", "b", me),
                mapOf("a" to 3L, "b" to 2L, me to 1L),
                listOf(me, "b", "a"),
            ),
            Case(
                "A2 me in the middle",
                listOf(me, "a", "b"),
                mapOf(me to 2L, "a" to 1L, "b" to 3L),
                listOf("a", me, "b"),
            ),
            Case(
                "A2 me the oldest comes last",
                listOf(me, "a", "b"),
                mapOf(me to 5L, "a" to 1L, "b" to 3L),
                listOf("a", "b", me),
            ),
            Case(
                "A3 no status at all goes after",
                listOf("a", "b", "c"),
                mapOf("a" to null, "b" to 4L, "c" to 1L),
                listOf("c", "b", "a"),
            ),
            Case(
                "A3 undated ones keep the order of arrival",
                listOf("a", "b", "c", "d"),
                mapOf("a" to null, "b" to 9L, "c" to null, "d" to 1L),
                listOf("d", "b", "a", "c"),
            ),
            Case(
                "A3 presence unknown, sharing just activated (equal times) is without a date",
                listOf("a", "b", "c"),
                mapOf("a" to 1L, "b" to 2L, "c" to 3L),
                listOf("b", "c", "a"),
                presences = mapOf("a" to PresenceValue.UNKNOWN),
            ),
            Case(
                "A3 presence unknown but a manual update after the activation: it rises with its date",
                listOf("a", "b", "c"),
                mapOf("a" to 2L, "b" to 1L, "c" to 3L),
                listOf("b", "a", "c"),
                presences = mapOf("a" to PresenceValue.UNKNOWN),
                manualAfterActivation = setOf("a"),
            ),
            Case(
                "A3 sharing stopped is without a date",
                listOf("a", "b", "c"),
                mapOf("a" to 1L, "b" to 2L, "c" to 3L),
                listOf("b", "c", "a"),
                sharingOff = setOf("a"),
            ),
            Case(
                "A4 equal dates keep the order of arrival",
                listOf("a", "b", "c"),
                mapOf(
                    "a" to 2L,
                    "b" to 2L,
                    "c" to 2L,
                ),
                listOf("a", "b", "c"),
            ),
            Case(
                "A4 equal dates, me in the middle",
                listOf("a", me, "b"),
                mapOf("a" to 2L, me to 2L, "b" to 2L),
                listOf("a", me, "b"),
            ),
            Case(
                "nobody has a date: order of arrival, me not forced",
                listOf("a", me, "b"),
                emptyMap(),
                listOf("a", me, "b"),
            ),
            Case("a single member", listOf(me), mapOf(me to 1L), listOf(me)),
            Case(
                "A5 a very old update (the photo is long expired) still sorts by its date",
                listOf("b", "a"),
                mapOf("a" to 30L, "b" to 40L),
                listOf("a", "b"),
            ),
            Case(
                "a trip is dated like any other status",
                listOf("a", "b"),
                mapOf("a" to 5L, "b" to 1L),
                listOf("b", "a"),
                presences = mapOf("b" to PresenceValue.TRAVEL),
            ),
        )

    @Test
    fun orderTable_everyCase() {
        orderTable.forEach { case ->
            assertEquals(
                case.name,
                case.expected,
                order(
                    case.arrival,
                    case.agesHours.mapValues {
                        it.value?.times(hour)
                    },
                    case.presences,
                    case.sharingOff,
                    case.manualAfterActivation,
                ),
            )
        }
    }

    @Test
    fun orderTable_theOrderDoesNotDependOnTheOrderOfTheMapsOrTheNumberOfRuns() {
        val arrival = listOf("a", me, "b", "c", "d", "e")
        val ages = mapOf("a" to 3L, me to 3L, "b" to null, "c" to 1L, "d" to 3L, "e" to null)
        val expected = listOf("c", "a", me, "d", "b", "e")
        val statuses = ages.mapNotNull { (uid, ago) -> ago?.let { uid to status(lastUpdateAgo = it * hour) } }
        repeat(100) { seed ->
            val shuffled = statuses.shuffled(java.util.Random(seed.toLong())).toMap()
            assertEquals(expected, build(arrival.map { member(it) }, shuffled).cards.map { it.member.uid })
        }
    }

    // --- Pure function ------------------------------------------------------------------------------------------------

    @Test
    fun orderMembers_storedDatesOnly_theClockOfTheReaderDoesNotTakePart() {
        val members = listOf("a", "b", "c").map { member(it) }
        val dates = mapOf("a" to 1_000L, "b" to 3_000L, "c" to 2_000L)
        assertEquals(listOf("b", "c", "a"), FeedBuilder.orderMembers(members, dates).map { it.uid })
        // A date in the future (a clock ahead, up to 5 minutes by the rules) is compared as stored, never clamped to "now".
        val ahead = mapOf("a" to now + 5 * minute, "b" to now, "c" to now - hour)
        assertEquals(listOf("a", "b", "c"), FeedBuilder.orderMembers(members, ahead).map { it.uid })
    }

    @Test
    fun newsDate_isTheLastUpdate_exceptWhenTheCardSaysNoNewsAndNothingFollowedTheFirstStatus() {
        val s = status(lastUpdateAgo = hour)
        val expected = now - hour
        assertEquals(expected, FeedBuilder.newsDate(StateLabel.Danger, s))
        assertEquals(expected, FeedBuilder.newsDate(StateLabel.TravelEnded, s))
        assertNull(FeedBuilder.newsDate(StateLabel.NoNews, s))
        // "Pas encore de nouvelles" with presence unknown: dated only by an update strictly AFTER the first status.
        val unknownAfter = status(PresenceValue.UNKNOWN, lastUpdateAgo = hour, presenceAgo = 2 * hour)
        assertEquals(expected, FeedBuilder.newsDate(StateLabel.NoNews, unknownAfter))
        assertNull(
            FeedBuilder.newsDate(
                StateLabel.NoNews,
                status(PresenceValue.UNKNOWN, lastUpdateAgo = hour, presenceAgo = hour),
            ),
        )
        assertNull(
            FeedBuilder.newsDate(
                StateLabel.NoNews,
                status(PresenceValue.UNKNOWN, lastUpdateAgo = 2 * hour, presenceAgo = hour),
            ),
        )
        assertNull(
            FeedBuilder.newsDate(
                StateLabel.NoNews,
                status(PresenceValue.UNKNOWN, lastUpdateAgo = null, presenceAgo = hour),
            ),
        )
        assertNull(FeedBuilder.newsDate(StateLabel.NoNews, status(null, lastUpdateAgo = hour, presenceAgo = 2 * hour)))
        assertNull(FeedBuilder.newsDate(StateLabel.SharingStopped, s))
        assertNull(FeedBuilder.newsDate(StateLabel.NewsHidden, s))
        assertNull(FeedBuilder.newsDate(StateLabel.WaitingAgreement, s))
        assertNull(FeedBuilder.newsDate(StateLabel.Danger, null))
        assertNull(FeedBuilder.newsDate(StateLabel.Danger, status(lastUpdateAgo = null)))
    }

    @Test
    fun orderMembers_mySharingStopped_theNewsOfTheOthersIsHidden_nobodyHasADate() {
        val members = listOf(member("a"), member(me, sharing = false), member("b"))
        val content =
            build(
                members,
                mapOf(
                    "a" to status(lastUpdateAgo = hour),
                    "b" to status(lastUpdateAgo = minute),
                    me to status(lastUpdateAgo = 2 * hour),
                ),
            )
        // Nothing of the others is read while I do not share: no card has a date, the order of arrival stays.
        assertEquals(listOf("a", me, "b"), content.cards.map { it.member.uid })
    }

    @Test
    fun presenceUnknownAfterAManualUpdate_rises_butStaysLastWhenTheTimesAreEqual() {
        // The case flagged by the ticket: a manual update before any departure leaves `presence = unknown`. Sharing just activated: `presenceAt` and
        // `lastUpdateAt` are the same server time, the card has no news and stays last. After a manual update `lastUpdateAt` is later: the card rises with
        // its date (the wording stays "Pas encore de nouvelles": question left to the PM).
        val members = listOf("a", "b", "c").map { member(it) }
        val manual =
            build(
                members,
                mapOf(
                    "a" to
                        status(PresenceValue.UNKNOWN, lastUpdateAgo = minute, hasPhoto = true, presenceAgo = 3 * hour),
                    "b" to status(lastUpdateAgo = 5 * hour),
                    "c" to status(lastUpdateAgo = 6 * hour),
                ),
            )
        assertEquals(listOf("a", "b", "c"), manual.cards.map { it.member.uid })
        assertEquals(StateLabel.NoNews, manual.cards.first().label)
        val activation =
            build(
                members,
                mapOf(
                    "a" to status(PresenceValue.UNKNOWN, lastUpdateAgo = minute, presenceAgo = minute),
                    "b" to status(lastUpdateAgo = 5 * hour),
                    "c" to status(lastUpdateAgo = 6 * hour),
                ),
            )
        assertEquals(listOf("b", "c", "a"), activation.cards.map { it.member.uid })
        assertEquals(StateLabel.NoNews, activation.cards.last().label)
    }

    @Test
    fun theColourAndTheBubbleDoNotDependOnTheOrderOfTheCards() {
        val statuses = mapOf("a" to status(lastUpdateAgo = 3 * hour), "b" to status(lastUpdateAgo = hour))
        val first = build(listOf("a", "b", me).map { member(it) }, statuses)
        val second = build(listOf(me, "b", "a").map { member(it) }, statuses)
        assertEquals(first.miffLevel, second.miffLevel)
        assertEquals(first.bubble::class, second.bubble::class)
        assertEquals(
            first.cards.associate { it.member.uid to it.level },
            second.cards.associate {
                it.member.uid to
                    it.level
            },
        )
    }

    // --- The change of order (announcement) ---------------------------------------------------------------------------

    @Test
    fun orderChange_oneCardRoseToTheTop_namesIt() {
        val change = CardOrderChange.of(listOf("a", "b", "c"), listOf("c", "a", "b"))
        assertEquals("c", change?.mover)
    }

    @Test
    fun orderChange_aSwap_namesTheOneThatRose_severalMovesNameNobody_noChangeIsNull() {
        assertEquals("b", CardOrderChange.of(listOf("a", "b"), listOf("b", "a"))?.mover)
        assertNull(CardOrderChange.of(listOf("a", "b", "c", "d"), listOf("b", "a", "d", "c"))?.mover)
        assertNotNull(CardOrderChange.of(listOf("a", "b", "c", "d"), listOf("b", "a", "d", "c")))
        assertNull(CardOrderChange.of(listOf("a", "b"), listOf("a", "b")))
    }

    @Test
    fun orderChange_aMemberWhoArrivesOrLeaves_isNotAMovement() {
        assertNull(CardOrderChange.of(listOf("a", "b"), listOf("a", "x", "b")))
        assertNull(CardOrderChange.of(listOf("a", "b", "c"), listOf("a", "c")))
        assertEquals("c", CardOrderChange.of(listOf("a", "b", "c"), listOf("c", "x", "a"))?.mover)
    }

    // --- The gate: nothing moves under a finger (criteria A6 to A8) ----------------------------------------------------

    private fun gateAt(
        gate: CardOrderGate,
        live: List<String>,
        hold: Boolean = false,
        t: Long = LATE,
        refreshing: Boolean = false,
        miff: String = "m1",
    ) = gate.resolve(miff, me, live, hold, refreshing, t)

    private fun settledGate(live: List<String>): CardOrderGate = CardOrderGate().also { gateAt(it, live, t = 0L) }

    @Test
    fun gate_firstShow_isTheLiveOrder_noGlide_noAnnouncement() {
        val view = gateAt(CardOrderGate(), listOf("a", "b", "c"), t = 0L)
        assertEquals(listOf("a", "b", "c"), view.order)
        assertFalse(view.animate)
        assertNull(view.announcement)
    }

    @Test
    fun gate_aChangeAlone_glidesAndIsAnnounced_onceWhateverTheRecompositions() {
        val gate = settledGate(listOf("a", "b", "c"))
        val first = gateAt(gate, listOf("c", "a", "b"))
        assertEquals(listOf("c", "a", "b"), first.order)
        assertTrue(first.animate)
        assertEquals("c", first.announcement?.mover)
        // The screen recomposes: same order, same announcement (same id: it is said once); the glide is over, a later change must not inherit it.
        val again = gateAt(gate, listOf("c", "a", "b"))
        assertEquals(first.announcement?.id, again.announcement?.id)
        assertFalse(again.animate)
    }

    @Test
    fun gate_aMemberIsNamedOnlyWhenArrivingAtTheTop_otherwiseTheFeedWasUpdated() {
        val toTop = gateAt(settledGate(listOf("a", "b", "c")), listOf("c", "a", "b"))
        assertEquals("c", toTop.announcement?.mover)
        // "c" rises but not to the top: the general sentence, nobody is named.
        val notTop = gateAt(settledGate(listOf("a", "b", "c")), listOf("a", "c", "b"))
        assertNotNull(notTop.announcement)
        assertNull(notTop.announcement?.mover)
    }

    @Test
    fun gate_theDirectWindowIsCountedOnTheGivenClock_notOnAnyWallClock() {
        val gate = settledGate(listOf("a", "b", "c"))
        gate.applyDirectlyUntil(5_000L)
        // The clock given (monotone) is before the end of the window: direct. At the end exactly: it glides again.
        assertFalse(gateAt(gate, listOf("b", "a", "c"), t = 4_999L).animate)
        assertTrue(gateAt(gate, listOf("a", "b", "c"), t = 5_000L).animate)
    }

    @Test
    fun gate_duringAGesture_nothingMoves_andOneFinalOrderIsAppliedAtTheEnd() {
        val gate = settledGate(listOf("a", "b", "c"))
        // A finger is down (a scroll, a tap on a button): the cards stay, whatever the live order does, even several times.
        assertEquals(listOf("a", "b", "c"), gateAt(gate, listOf("b", "a", "c"), hold = true).order)
        val held = gateAt(gate, listOf("c", "b", "a"), hold = true)
        assertEquals(listOf("a", "b", "c"), held.order)
        assertNull(held.announcement)
        // The finger is lifted: ONE application, the last order, one announcement ("Le Feed a été mis à jour": several cards moved).
        val released = gateAt(gate, listOf("c", "b", "a"))
        assertEquals(listOf("c", "b", "a"), released.order)
        assertTrue(released.animate)
        assertNull(released.announcement?.mover)
        assertEquals(1, released.announcement?.id)
    }

    @Test
    fun gate_duringAGesture_aCardThatLeavesIsDropped_aNewOneIsAddedAtTheEnd_theOthersDoNotMove() {
        val gate = settledGate(listOf("a", "b", "c"))
        assertEquals(listOf("a", "c", "x"), gateAt(gate, listOf("x", "c", "a"), hold = true).order)
    }

    @Test
    fun gate_aChangeThatComesBackToTheShownOrder_saysNothing() {
        val gate = settledGate(listOf("a", "b", "c"))
        gateAt(gate, listOf("b", "a", "c"), hold = true)
        val released = gateAt(gate, listOf("a", "b", "c"))
        assertEquals(listOf("a", "b", "c"), released.order)
        assertNull(released.announcement)
    }

    @Test
    fun gate_myOwnCardMovingAlone_isNotAnnounced_butItStillGlides() {
        val gate = settledGate(listOf("a", "b", me))
        val view = gateAt(gate, listOf(me, "a", "b"))
        assertEquals(listOf(me, "a", "b"), view.order)
        assertTrue(view.animate)
        assertNull(view.announcement)
    }

    @Test
    fun gate_aMemberWhoArrivesOrLeaves_isNotAnnounced() {
        val gate = settledGate(listOf("a", "b", "c"))
        val view = gateAt(gate, listOf("a", "b"))
        assertEquals(listOf("a", "b"), view.order)
        assertNull(view.announcement)
        assertNull(gateAt(gate, listOf("a", "b", "x")).announcement)
    }

    @Test
    fun gate_comingBackToTheFeed_appliesTheOrderDirectly_noGlide_noAnnouncement() {
        val gate = settledGate(listOf("a", "b", "c"))
        gate.applyDirectlyUntil(LATE + 1_000L)
        val view = gateAt(gate, listOf("c", "a", "b"), t = LATE)
        assertEquals(listOf("c", "a", "b"), view.order)
        assertFalse(view.animate)
        assertNull(view.announcement)
        // The window is over: the next change glides again.
        val later = gateAt(gate, listOf("a", "c", "b"), t = LATE + 2_000L)
        assertTrue(later.animate)
    }

    @Test
    fun gate_aGestureStillHoldsTheOrderInsideTheDirectWindow() {
        val gate = settledGate(listOf("a", "b", "c"))
        gate.applyDirectlyUntil(LATE + 1_000L)
        assertEquals(listOf("a", "b", "c"), gateAt(gate, listOf("c", "a", "b"), hold = true, t = LATE).order)
    }

    @Test
    fun gate_aManualRefresh_appliesTheOrderDirectly_atItsStartAndAtItsEnd() {
        val gate = settledGate(listOf("a", "b", "c"))
        val started = gateAt(gate, listOf("b", "a", "c"), refreshing = true)
        assertEquals(listOf("b", "a", "c"), started.order)
        assertFalse(started.animate)
        val ended = gateAt(gate, listOf("c", "b", "a"), refreshing = false, t = LATE + 500L)
        assertEquals(listOf("c", "b", "a"), ended.order)
        assertFalse(ended.animate)
        assertNull(ended.announcement)
    }

    @Test
    fun gate_anotherMiff_orAfterAReset_showsTheLiveOrderAsItComes() {
        val gate = settledGate(listOf("a", "b", "c"))
        val other = gateAt(gate, listOf("c", "b"), miff = "m2")
        assertEquals(listOf("c", "b"), other.order)
        assertFalse(other.animate)
        assertNull(other.announcement)
        gate.reset()
        val after = gateAt(gate, listOf("b", "c"), miff = "m2")
        assertEquals(listOf("b", "c"), after.order)
        assertFalse(after.animate)
    }

    @Test
    fun inOrder_buildsTheCardsInTheShownOrder_andDropsWhatTheOrderDoesNotName() {
        val content = build(listOf("a", "b", "c").map { member(it) }, emptyMap())
        assertEquals(listOf("c", "a"), content.cards.inOrder(listOf("c", "a")).map { it.member.uid })
        assertEquals(listOf("c", "b", "a"), content.cards.inOrder(listOf("c", "x", "b", "a")).map { it.member.uid })
    }

    private companion object {
        /** A moment well after the first show, outside the direct window of a fresh gate (the tests of the gate start it at 0). */
        const val LATE = 1_000_000L
    }
}
