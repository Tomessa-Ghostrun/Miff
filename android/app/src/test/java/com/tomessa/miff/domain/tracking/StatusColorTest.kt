package com.tomessa.miff.domain.tracking

import org.junit.Assert.assertEquals
import org.junit.Test

/** The colour of a member (ticket 6.9, criteria STA6, STA9): every branch and every bound, with the exact times of the ticket. */
class StatusColorTest {
    private val now = 1_800_000_000_000L
    private val minute = 60_000L
    private val hour = 60 * minute

    private fun out(
        presenceAgo: Long,
        updateAgo: Long? = null,
    ) = MemberStatusFacts(
        sharingEnabled = true,
        presence = PresenceValue.OUT,
        presenceAtMillis = now - presenceAgo,
        lastUpdateAtMillis = updateAgo?.let { now - it },
    )

    private fun level(facts: MemberStatusFacts?) = StatusColor.level(facts, now)

    @Test
    fun theThresholdsAreOneHourAndTwoHours_inOnePlace() {
        assertEquals(hour, StatusThresholds.ORANGE_AFTER_MILLIS)
        assertEquals(2 * hour, StatusThresholds.RED_AFTER_MILLIS)
    }

    @Test
    fun aMemberInATrustedZone_isGreen_whateverTheTime() {
        val facts = MemberStatusFacts(true, PresenceValue.IN_TRUSTED, now - 30 * hour, now - 30 * hour)
        assertEquals(StatusLevel.GREEN, level(facts))
    }

    @Test
    fun aMemberInADangerZone_isRed_evenJustArrived_evenWithAFreshUpdate() {
        assertEquals(StatusLevel.RED, level(MemberStatusFacts(true, PresenceValue.IN_RISK, now, now)))
    }

    @Test
    fun sharingStopped_isNeutral_whateverTheStatusSays() {
        val facts = MemberStatusFacts(false, PresenceValue.IN_TRUSTED, now, now)
        assertEquals(StatusLevel.NEUTRAL, level(facts))
        assertEquals(StatusLevel.NEUTRAL, level(MemberStatusFacts(false, PresenceValue.IN_RISK, now, now)))
    }

    @Test
    fun noStatus_isNeutral() {
        assertEquals(StatusLevel.NEUTRAL, level(null))
    }

    @Test
    fun anUnknownPresence_isNeutral_noEscalation() {
        assertEquals(
            StatusLevel.NEUTRAL,
            level(
                MemberStatusFacts(
                    true,
                    PresenceValue.UNKNOWN,
                    now - 5 * hour,
                    now - 5 * hour,
                ),
            ),
        )
    }

    @Test
    fun aValueNobodyKnows_isNeutral_neverAGuess() {
        assertEquals(null, PresenceValue.fromWire("somewhere"))
        assertEquals(StatusLevel.NEUTRAL, level(MemberStatusFacts(true, PresenceValue.fromWire("somewhere"), now, now)))
    }

    @Test
    fun out_boundsOfTheTicket_59min59s_isGreen() {
        assertEquals(StatusLevel.GREEN, level(out(hour - 1_000)))
    }

    @Test
    fun out_exactlyOneHour_isOrange() {
        assertEquals(StatusLevel.ORANGE, level(out(hour)))
    }

    @Test
    fun out_betweenOneAndTwoHours_isOrange() {
        assertEquals(StatusLevel.ORANGE, level(out(hour + 30 * minute)))
    }

    @Test
    fun out_exactlyTwoHours_isStillOrange_theBoundIsIncluded() {
        assertEquals(StatusLevel.ORANGE, level(out(2 * hour)))
    }

    @Test
    fun out_twoHoursAndOneSecond_isRed() {
        assertEquals(StatusLevel.RED, level(out(2 * hour + 1_000)))
    }

    @Test
    fun out_justLeft_isGreen() {
        assertEquals(StatusLevel.GREEN, level(out(0)))
    }

    @Test
    fun theReferenceIsTheMostRecentOfTheDepartureAndTheLastUpdate() {
        // Left 3 h ago, but an update 30 minutes ago: green. (Decision N2.)
        assertEquals(StatusLevel.GREEN, level(out(presenceAgo = 3 * hour, updateAgo = 30 * minute)))
        // An update older than the departure does not bring the reference back.
        assertEquals(StatusLevel.ORANGE, level(out(presenceAgo = 90 * minute, updateAgo = 5 * hour)))
        // The update 90 minutes ago, the departure 3 hours ago: orange.
        assertEquals(StatusLevel.ORANGE, level(out(presenceAgo = 3 * hour, updateAgo = 90 * minute)))
    }

    @Test
    fun theExampleOfTheTicket_leftAt15h_greenUntil16h_orangeUntil17hIncluded_thenRed() {
        val left = now

        fun at(afterMinutes: Long) =
            StatusColor.level(
                MemberStatusFacts(true, PresenceValue.OUT, left, left),
                left + afterMinutes * minute,
            )
        assertEquals(StatusLevel.GREEN, at(59))
        assertEquals(StatusLevel.ORANGE, at(60))
        assertEquals(StatusLevel.ORANGE, at(120))
        assertEquals(StatusLevel.RED, at(121))
    }

    @Test
    fun aReferenceInTheFuture_aWrongClock_isGreen() {
        assertEquals(StatusLevel.GREEN, level(MemberStatusFacts(true, PresenceValue.OUT, now + 3 * hour, null)))
    }

    @Test
    fun outWithNoTime_isNeutral_nothingIsInvented() {
        assertEquals(StatusLevel.NEUTRAL, level(MemberStatusFacts(true, PresenceValue.OUT, null, null)))
    }

    @Test
    fun onlyTheLastUpdateKnown_isEnough() {
        assertEquals(StatusLevel.ORANGE, level(MemberStatusFacts(true, PresenceValue.OUT, null, now - 90 * minute)))
    }
}
