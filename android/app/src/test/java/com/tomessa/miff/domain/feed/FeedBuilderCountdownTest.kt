package com.tomessa.miff.domain.feed

import com.tomessa.miff.domain.miff.MiffMember
import com.tomessa.miff.domain.miff.MiffRole
import com.tomessa.miff.domain.tracking.CountdownAnchor
import com.tomessa.miff.domain.tracking.PresenceValue
import com.tomessa.miff.domain.tracking.StatusThresholds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

/**
 * COUNTDOWN-A: what the Feed hands to the countdown (criteria CD-1 to CD-5). The anchor is built from MY status only, with the facts of my card: a status
 * somebody else wrote, a trip, a stopped sharing or a status still unknown never produce one, and an update moves it.
 */
class FeedBuilderCountdownTest {
    private val now = Instant.parse("2026-11-13T17:00:00Z").toEpochMilli()
    private val minute = 60_000L
    private val me = "me"

    private fun member(
        uid: String,
        sharing: Boolean = true,
    ) = MiffMember(uid, uid, "X", null, if (uid == me) MiffRole.OWNER else MiffRole.MEMBER, sharing, null)

    private fun status(
        presence: PresenceValue?,
        presenceAgo: Long = 30 * minute,
        lastUpdateAgo: Long? = null,
        travelUntil: Long? = null,
    ) = MemberStatus(
        presence = presence,
        placeId = "p1",
        presenceAtMillis = now - presenceAgo,
        lastUpdateAtMillis = lastUpdateAgo?.let { now - it },
        hasPhoto = false,
        batteryPct = 80,
        locationAccess = MemberLocationAccess.BACKGROUND,
        updatedAtMillis = now - minute,
        travelUntilMillis = travelUntil,
    )

    private fun build(
        statuses: StatusesView,
        members: List<MiffMember> = listOf(member(me), member("t")),
        waiting: Boolean = false,
    ) = FeedBuilder.build(
        FeedInput("m1", "Chez nous", "CODE12345678", members, emptyList(), me, statuses, waiting, false),
        now,
        ZoneOffset.UTC,
    )

    private fun loaded(vararg pairs: Pair<String, MemberStatus>) = StatusesView.Loaded(mapOf(*pairs))

    @Test
    fun outAndKnown_givesTheAnchorOfMyReference() {
        val content = build(loaded(me to status(PresenceValue.OUT, presenceAgo = 30 * minute)))
        assertEquals(CountdownAnchor(now - 30 * minute), content.myCountdown)
    }

    @Test
    fun theReferenceIsTheMostRecentOfDepartureAndUpdate() {
        val content =
            build(
                loaded(me to status(PresenceValue.OUT, presenceAgo = 60 * minute, lastUpdateAgo = 10 * minute)),
            )
        assertEquals(CountdownAnchor(now - 10 * minute), content.myCountdown)
    }

    @Test
    fun anUpdateMovesTheAnchor_soTheCountdownStartsAgain() {
        val before = build(loaded(me to status(PresenceValue.OUT, presenceAgo = 90 * minute)))
        val after = build(loaded(me to status(PresenceValue.OUT, presenceAgo = 90 * minute, lastUpdateAgo = 0)))
        assertEquals(CountdownAnchor(now - 90 * minute), before.myCountdown)
        assertEquals(CountdownAnchor(now), after.myCountdown)
    }

    @Test
    fun insideATrustedPlace_inADangerZone_unknown_onATrip_giveNothing() {
        for (presence in listOf(
            PresenceValue.IN_TRUSTED,
            PresenceValue.IN_RISK,
            PresenceValue.UNKNOWN,
            PresenceValue.TRAVEL,
            null,
        )) {
            val content = build(loaded(me to status(presence, travelUntil = now + 60 * minute)))
            assertNull("presence $presence", content.myCountdown)
        }
    }

    @Test
    fun noStatusYet_pendingOrWaitingOrUnknown_givesNothing() {
        assertNull(build(loaded("t" to status(PresenceValue.OUT))).myCountdown)
        assertNull(build(StatusesView.Pending).myCountdown)
        assertNull(build(StatusesView.Waiting).myCountdown)
        assertNull(build(StatusesView.Unknown).myCountdown)
        assertNull(build(StatusesView.Off).myCountdown)
    }

    @Test
    fun sharingStopped_orAgreementWaited_givesNothing() {
        val statuses = loaded(me to status(PresenceValue.OUT))
        assertNull(build(statuses, members = listOf(member(me, sharing = false), member("t"))).myCountdown)
        assertNull(build(statuses, waiting = true).myCountdown)
    }

    @Test
    fun anotherMembersStatus_neverProducesMyAnchor_evenWhenThatMemberIsOut() {
        // CD-5: the countdown is for the person concerned only. A friend who is out and green gives a card, never an anchor of mine.
        val content = build(loaded(me to status(PresenceValue.IN_TRUSTED), "t" to status(PresenceValue.OUT)))
        assertNull(content.myCountdown)
    }

    @Test
    fun aPersonWhoIsNotInTheMiff_hasNoAnchor() {
        val content = build(loaded(me to status(PresenceValue.OUT)), members = listOf(member("t")))
        assertNull(content.myCountdown)
    }

    @Test
    fun theAnchorCarriesNothingButTheReference_andTheEndOfTheGreenIsTheConstant() {
        // No new field in the status, no place, no coordinate: one instant. The window comes from the thresholds.
        val anchor = build(loaded(me to status(PresenceValue.OUT))).myCountdown!!
        assertEquals(now - 30 * minute, anchor.referenceMillis)
        assertEquals(
            StatusThresholds.ORANGE_AFTER_MILLIS,
            com.tomessa.miff.domain.tracking.OrangeCountdown.WINDOW_MILLIS,
        )
    }
}
