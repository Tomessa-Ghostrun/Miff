package com.tomessa.miff.domain.feed

import com.google.firebase.Timestamp
import com.tomessa.miff.data.feed.photoAtMillisOf
import com.tomessa.miff.data.miff.OrderedMember
import com.tomessa.miff.data.miff.inArrivalOrder
import com.tomessa.miff.domain.alert.AlertDoc
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
 * FEED-ORDRE-PHOTO (ticket docs/tickets/feed-ordre-photo.md, sections 3, 5 and 9): the order of the cards by date of the LAST PHOTO published (`photoAt`, and nothing else),
 * and the gate that holds the order back during a gesture (unchanged since FEED-ORDRE). The TABLE of cases of [orderTable] (T1 to T19; T20 is in `PhotoDateWritesTest`) is the
 * contract shared with iOS: same arrival order `moi, a, b, c, d`, same dates (hours of the day, a greater one is more recent), same expected order.
 */
class FeedOrderTest {
    private val zone = ZoneOffset.UTC
    private val now = 1_800_000_000_000L
    private val minute = 60_000L
    private val hour = 60 * minute
    private val me = "me"

    /** The start of "the day" of the table: an hour of the day `h` is `dayStart + h hours` (all before [now]). */
    private val dayStart = now - 24 * hour

    private fun at(h: Int) = dayStart + h * hour

    private fun member(
        uid: String,
        sharing: Boolean = true,
    ) = MiffMember(
        uid,
        uid.uppercase(),
        "X",
        null,
        if (uid == me) MiffRole.OWNER else MiffRole.MEMBER,
        sharing,
        "+33612345678",
    )

    /** [photoAt] is the date the Feed sorts by; [lastUpdateAgo] (kept for the tests that are not about the order) is the one of the colour. */
    private fun status(
        presence: PresenceValue? = PresenceValue.IN_TRUSTED,
        lastUpdateAgo: Long? = hour,
        hasPhoto: Boolean = false,
        presenceAgo: Long = 20 * hour,
        photoAt: Long? = null,
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
        photoAtMillis = photoAt,
    )

    private fun build(
        members: List<MiffMember>,
        statuses: Map<String, MemberStatus>,
        alerts: List<AlertDoc> = emptyList(),
        waitingAgreement: Boolean = false,
    ) = FeedBuilder.build(
        FeedInput(
            "m1",
            "Chez nous",
            "CODE12345678",
            members,
            emptyList(),
            me,
            StatusesView.Loaded(statuses),
            waitingAgreement,
            false,
            alerts,
        ),
        now,
        zone,
    )

    // --- The table shared with iOS (criteria B1 to B5, B13, B15) -----------------------------------------------------------

    /**
     * What the status of one member says, in hours of the day. [photoH] null = no `photoAt`. [lastH] is `lastUpdateAt` (by default the photo's own time, as a real
     * publication writes both): the table proves it is IGNORED. [presenceH] is `presenceAt`.
     */
    private class St(
        val photoH: Int?,
        val lastH: Int? = photoH,
        val presence: PresenceValue = PresenceValue.IN_TRUSTED,
        val hasPhoto: Boolean = photoH != null,
        val presenceH: Int = 1,
    )

    /** "—" of the table: a status that exists (a last update at 09, a place) and holds NO `photoAt`. */
    private val noDate = St(null, lastH = 9)

    private class Case(
        val name: String,
        val arrival: List<String>,
        /** A member without an entry has NO document `status`. */
        val statuses: Map<String, St>,
        val expected: List<String>,
        val sharingOff: Set<String> = emptySet(),
        val alerts: List<String> = emptyList(),
    )

    private val everyone = listOf(me, "a", "b", "c", "d")

    private fun statusesOf(table: Map<String, St>): Map<String, MemberStatus> =
        table.mapValues { (_, s) ->
            MemberStatus(
                presence = s.presence,
                placeId = null,
                presenceAtMillis = at(s.presenceH),
                lastUpdateAtMillis = s.lastH?.let { at(it) },
                hasPhoto = s.hasPhoto,
                batteryPct = 80,
                locationAccess = MemberLocationAccess.BACKGROUND,
                updatedAtMillis = now - minute,
                travelUntilMillis = if (s.presence == PresenceValue.TRAVEL) now + 24 * hour else null,
                photoAtMillis = s.photoH?.let { at(it) },
            )
        }

    private val orderTable =
        listOf(
            Case(
                "T1 simple order, me without a date",
                everyone,
                mapOf("a" to St(10), "b" to St(12), "c" to St(11), me to noDate, "d" to noDate),
                listOf("b", "c", "a", me, "d"),
            ),
            Case(
                "T2 me dated, at the top",
                everyone,
                mapOf(me to St(13), "a" to St(12), "b" to St(10), "c" to noDate, "d" to noDate),
                listOf(me, "a", "b", "c", "d"),
            ),
            Case(
                "T3 me in the middle",
                everyone,
                mapOf(me to St(11), "a" to St(12), "b" to St(10), "c" to noDate, "d" to noDate),
                listOf("a", me, "b", "c", "d"),
            ),
            Case(
                "T4 equal dates: order of arrival",
                everyone,
                mapOf("a" to St(12), "b" to St(12), me to noDate, "c" to noDate, "d" to noDate),
                listOf("a", "b", me, "c", "d"),
            ),
            Case(
                "T6 nobody has a date: order of arrival, me not forced",
                everyone,
                mapOf(me to noDate, "a" to noDate, "b" to noDate, "c" to noDate, "d" to noDate),
                listOf(me, "a", "b", "c", "d"),
            ),
            Case(
                "T7 expired photo (hasPhoto false) keeps its rank",
                listOf("a", "b", "c"),
                mapOf("a" to St(10, hasPhoto = false), "b" to St(9), "c" to noDate),
                listOf("a", "b", "c"),
            ),
            Case(
                "T8 an update without a photo (lastUpdateAt later) changes nothing",
                listOf("a", "b"),
                mapOf("a" to St(10, lastH = 14), "b" to St(11, lastH = 11)),
                listOf("b", "a"),
            ),
            Case(
                "T9 arrival in a trusted place without a photo does not make the card rise",
                listOf("a", "b"),
                mapOf("a" to St(null, lastH = 15, presence = PresenceValue.IN_TRUSTED), "b" to St(9)),
                listOf("b", "a"),
            ),
            Case(
                "T10 \"Pas encore de nouvelles\" with a photoAt is sorted by it",
                listOf("a", "b"),
                mapOf("a" to St(12, presence = PresenceValue.UNKNOWN), "b" to St(10)),
                listOf("a", "b"),
            ),
            Case(
                "T11 \"Pas encore de nouvelles\" without photoAt, even with lastUpdateAt after presenceAt, has no date",
                listOf("a", "b"),
                mapOf("a" to St(null, lastH = 14, presence = PresenceValue.UNKNOWN, presenceH = 3), "b" to St(10)),
                listOf("b", "a"),
            ),
            Case(
                "T13 a member without a document status",
                listOf("a", "b"),
                mapOf("b" to St(10)),
                listOf("b", "a"),
            ),
            Case(
                "T15 a single member",
                listOf(me),
                mapOf(me to St(10)),
                listOf(me),
            ),
            Case(
                "T16 a trip: photoAt kept",
                listOf("a", "b"),
                mapOf("a" to St(12, presence = PresenceValue.TRAVEL), "b" to St(10)),
                listOf("a", "b"),
            ),
            Case(
                "T18 a card in alert comes first, the others follow the photos",
                listOf("a", "b", "c"),
                mapOf("c" to noDate, "a" to St(12), "b" to St(13)),
                listOf("c", "b", "a"),
                alerts = listOf("c"),
            ),
            Case(
                "B15 the alert of a member who has the OLDEST photo still comes first",
                listOf("a", "b", "c"),
                mapOf("a" to St(8), "b" to St(13), "c" to St(12)),
                listOf("a", "b", "c"),
                alerts = listOf("a"),
            ),
            Case(
                "B3 sharing stopped is without a date, even with a photoAt left",
                listOf("a", "b", "c"),
                mapOf("a" to St(14), "b" to St(10), "c" to St(9)),
                listOf("b", "c", "a"),
                sharingOff = setOf("a"),
            ),
        )

    @Test
    fun orderTable_everyCase() {
        orderTable.forEach { case ->
            val alerts = case.alerts.map { AlertDoc(it, true, now - minute, now + 6 * 24 * hour, 1) }
            val content =
                build(case.arrival.map { member(it, it !in case.sharingOff) }, statusesOf(case.statuses), alerts)
            assertEquals(case.name, case.expected, content.cards.map { it.member.uid })
        }
    }

    @Test
    fun t4_equalDates_theResultIsTheSameWhateverTheOrderOfTheInput_100Shuffles() {
        val table = mapOf("a" to St(12), "b" to St(12), me to noDate, "c" to noDate, "d" to noDate)
        val expected = listOf("a", "b", me, "c", "d")
        repeat(100) { seed ->
            val shuffled =
                statusesOf(table).entries.shuffled(java.util.Random(seed.toLong())).associate {
                    it.key to
                        it.value
                }
            assertEquals(expected, build(everyone.map { member(it) }, shuffled).cards.map { it.member.uid })
        }
    }

    @Test
    fun t5_sameArrivalAndSameDate_theIdentifierDecides_atEveryLaunch() {
        // Two members who joined at the very same moment: the arrival order itself ends with the identifier, so the order of the Feed is the same at every launch.
        val joined = 1_000L
        val inputs =
            listOf(
                OrderedMember(member("b"), joined),
                OrderedMember(member("a"), joined),
                OrderedMember(member(me), joined),
            )
        val arrival = inputs.inArrivalOrder()
        assertEquals(listOf(me, "a", "b"), arrival.map { it.uid })
        assertEquals(arrival.map { it.uid }, inputs.reversed().inArrivalOrder().map { it.uid })
        // Same photo date for the two: the order of arrival (so the identifier) decides, whichever way the statuses come.
        val same = statusesOf(mapOf("a" to St(12), "b" to St(12)))
        repeat(20) { seed ->
            val shuffled = same.entries.shuffled(java.util.Random(seed.toLong())).associate { it.key to it.value }
            assertEquals(listOf("a", "b", me), build(arrival, shuffled).cards.map { it.member.uid })
        }
    }

    @Test
    fun t12_labelsWithoutADate_evenWithAPhotoAtLeftInTheStatus() {
        // "Partage désactivé" (a, photoAt 14), a dated card (d, 09), others without a date: d first, then the others in the order of arrival.
        val members = listOf("a", "b", "c", "d").map { member(it, sharing = it != "a") }
        val statuses = statusesOf(mapOf("a" to St(14), "b" to noDate, "c" to noDate, "d" to St(9)))
        val stopped = build(members, statuses)
        assertEquals(StateLabel.SharingStopped, stopped.cards.first { it.member.uid == "a" }.label)
        assertEquals(listOf("d", "a", "b", "c"), stopped.cards.map { it.member.uid })

        // "Informations masquées": I stopped sharing, the status of the others is not read, even with a photoAt (b 13, d 09): nobody has a date.
        val mine = listOf(me, "a", "b", "c", "d").map { member(it, sharing = it != me) }
        val hidden = build(mine, statusesOf(mapOf("a" to St(14), "b" to St(13), "c" to St(11), "d" to St(9))))
        assertEquals(StateLabel.NewsHidden, hidden.cards.first { it.member.uid == "b" }.label)
        assertEquals(listOf(me, "a", "b", "c", "d"), hidden.cards.map { it.member.uid })

        // "En attente de mon accord": nobody has a date either (my card included).
        val waiting =
            build(
                everyone.map { member(it) },
                statusesOf(mapOf(me to St(15), "a" to St(14), "b" to St(13), "d" to St(9))),
                waitingAgreement = true,
            )
        assertEquals(StateLabel.WaitingAgreement, waiting.cards.first { it.isMe }.label)
        assertEquals(everyone, waiting.cards.map { it.member.uid })
    }

    @Test
    fun newsDate_isPhotoAtAndNothingElse_exceptForTheThreeLabelsWithoutADate() {
        val s = status(lastUpdateAgo = hour, presenceAgo = 2 * hour, photoAt = at(5))
        listOf(
            StateLabel.Danger,
            StateLabel.TravelEnded,
            StateLabel.NoNews,
        ).forEach { assertEquals(at(5), FeedBuilder.newsDate(it, s)) }
        listOf(StateLabel.SharingStopped, StateLabel.NewsHidden, StateLabel.WaitingAgreement).forEach {
            assertNull(it.toString(), FeedBuilder.newsDate(it, s))
        }
        // No photoAt: no date, whatever the other times say (the old exception of "Pas encore de nouvelles" with lastUpdateAt after presenceAt is gone).
        val without = status(PresenceValue.UNKNOWN, lastUpdateAgo = hour, presenceAgo = 2 * hour)
        listOf(StateLabel.NoNews, StateLabel.Danger, StateLabel.TravelEnded).forEach {
            assertNull(FeedBuilder.newsDate(it, without))
        }
        assertNull(FeedBuilder.newsDate(StateLabel.Danger, null))
        assertNull(FeedBuilder.newsDate(StateLabel.NoNews, null))
        // hasPhoto does not date anything either.
        assertNull(FeedBuilder.newsDate(StateLabel.Danger, status(hasPhoto = true)))
    }

    @Test
    fun t14_anUnreadableDate_isNoDate_andNothingFails() {
        // At the level of the reading: null, a text, a number, a boolean, a map: no date. A real time: its milliseconds.
        assertNull(photoAtMillisOf(null))
        assertNull(photoAtMillisOf("2026-11-20"))
        assertNull(photoAtMillisOf(1_800_000_000_000L))
        assertNull(photoAtMillisOf(true))
        assertNull(photoAtMillisOf(mapOf("seconds" to 1)))
        assertEquals(1_800_000_000_123L, photoAtMillisOf(Timestamp(1_800_000_000L, 123_000_000)))
        // At the level of the Feed: a status whose photoAt could not be read sorts last, in the order of arrival, without a failure.
        val order =
            build(
                listOf("a", "b").map { member(it) },
                mapOf(
                    "a" to status(photoAt = photoAtMillisOf("not a time")),
                    "b" to status(photoAt = at(10)),
                ),
            ).cards.map { it.member.uid }
        assertEquals(listOf("b", "a"), order)
    }

    @Test
    fun t17_theDatesAreCompared_toTheMillisecond() {
        // Two photos that differ only by the microseconds are EQUAL (the same truncation as iOS): the order of arrival decides, in both arrival orders.
        val first = photoAtMillisOf(Timestamp(1_800_000_000L, 1_000))
        val second = photoAtMillisOf(Timestamp(1_800_000_000L, 2_000))
        assertEquals(first, second)
        val statuses = mapOf("a" to status(photoAt = first), "b" to status(photoAt = second))
        assertEquals(listOf("a", "b"), build(listOf("a", "b").map { member(it) }, statuses).cards.map { it.member.uid })
        assertEquals(listOf("b", "a"), build(listOf("b", "a").map { member(it) }, statuses).cards.map { it.member.uid })
        // One whole millisecond later is later.
        val later = photoAtMillisOf(Timestamp(1_800_000_000L, 1_000_000))
        val earlier = photoAtMillisOf(Timestamp(1_800_000_000L, 999_999))
        assertEquals(first, earlier)
        assertEquals(first!! + 1, later)
        val byMs = mapOf("a" to status(photoAt = earlier), "b" to status(photoAt = later))
        assertEquals(listOf("b", "a"), build(listOf("a", "b").map { member(it) }, byMs).cards.map { it.member.uid })
    }

    @Test
    fun t19_myPhotoStillWaitingForTheServer_isDatedNow_thenTheServerValueKeepsTheSameOrder() {
        val members = listOf(me, "a", "b").map { member(it) }
        val others = mapOf("a" to status(photoAt = at(12)), "b" to status(photoAt = at(10)))
        // Pending: the estimate is "now".
        val pending = build(members, others + (me to status(photoAt = now)))
        assertEquals(listOf(me, "a", "b"), pending.cards.map { it.member.uid })
        // Confirmed: the value of the server (a few seconds before) puts me in the same place.
        val confirmed = build(members, others + (me to status(photoAt = now - 3_000L)))
        assertEquals(pending.cards.map { it.member.uid }, confirmed.cards.map { it.member.uid })
    }

    @Test
    fun orderTable_theOrderDoesNotDependOnTheOrderOfTheMapsOrTheNumberOfRuns() {
        val arrival = listOf("a", me, "b", "c", "d", "e")
        val dates = mapOf("a" to 3, me to 3, "b" to null, "c" to 1, "d" to 3, "e" to null)
        // The more recent the photo, the later the hour: 3 is the most recent.
        val expected = listOf("a", me, "d", "c", "b", "e")
        val statuses = dates.map { (uid, h) -> uid to status(photoAt = h?.let { at(it) }) }
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
        // A date in the future (never written by the rules, but possible in a modified client) is compared as stored, never clamped to "now".
        val ahead = mapOf("a" to now + 5 * minute, "b" to now, "c" to now - hour)
        assertEquals(listOf("a", "b", "c"), FeedBuilder.orderMembers(members, ahead).map { it.uid })
    }

    @Test
    fun b5_onlyANewPhotoMovesACard() {
        val members = listOf(me, "a", "b").map { member(it) }
        val base =
            mapOf(
                me to status(photoAt = at(8)),
                "a" to status(photoAt = at(10)),
                "b" to status(photoAt = at(9)),
            )
        val before = build(members, base).cards.map { it.member.uid }
        assertEquals(listOf("a", "b", me), before)
        // An update without a photo (lastUpdateAt much later), an arrival at a place, a flat battery, a trip, a photo that expired: nothing moves.
        val calm =
            base +
                (me to status(lastUpdateAgo = minute, photoAt = at(8))) +
                ("b" to status(PresenceValue.OUT, lastUpdateAgo = minute, hasPhoto = false, photoAt = at(9))) +
                ("a" to status(PresenceValue.TRAVEL, lastUpdateAgo = 2 * hour, photoAt = at(10)))
        assertEquals(before, build(members, calm).cards.map { it.member.uid })
        // A NEW photo (a later photoAt) is what moves it.
        val photo = base + (me to status(lastUpdateAgo = minute, hasPhoto = true, photoAt = now - minute))
        assertEquals(listOf(me, "a", "b"), build(members, photo).cards.map { it.member.uid })
    }

    @Test
    fun orderMembers_mySharingStopped_theNewsOfTheOthersIsHidden_nobodyHasADate() {
        val members = listOf(member("a"), member(me, sharing = false), member("b"))
        val content =
            build(
                members,
                mapOf(
                    "a" to status(photoAt = at(10)),
                    "b" to status(photoAt = at(12)),
                    me to status(photoAt = at(9)),
                ),
            )
        // Nothing of the others is read while I do not share: no card has a date, the order of arrival stays.
        assertEquals(listOf("a", me, "b"), content.cards.map { it.member.uid })
    }

    @Test
    fun presenceUnknown_isDatedByItsPhotoAt_andKeepsItsLabel() {
        // "Pas encore de nouvelles" keeps its wording although it is dated by its photoAt (the question of the label stays open in the ticket).
        val members = listOf("a", "b").map { member(it) }
        val content =
            build(
                members,
                mapOf(
                    "a" to
                        status(
                            PresenceValue.UNKNOWN,
                            lastUpdateAgo = minute,
                            presenceAgo = 3 * hour,
                            hasPhoto = true,
                            photoAt = at(12),
                        ),
                    "b" to status(photoAt = at(10)),
                ),
            )
        assertEquals(listOf("a", "b"), content.cards.map { it.member.uid })
        assertEquals(StateLabel.NoNews, content.cards.first().label)
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
