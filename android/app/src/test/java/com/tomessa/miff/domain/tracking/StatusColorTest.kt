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
    fun theThresholdsAreTwoHoursAndFourHours_inOnePlace_decisionOfThePm_2026_11_08() {
        assertEquals(2 * hour, StatusThresholds.ORANGE_AFTER_MILLIS)
        assertEquals(4 * hour, StatusThresholds.RED_AFTER_MILLIS)
    }

    private fun danger(
        presenceAgo: Long,
        updateAgo: Long?,
    ) = MemberStatusFacts(true, PresenceValue.IN_RISK, now - presenceAgo, updateAgo?.let { now - it })

    @Test
    fun theDangerZoneRule_theConstantIsOneHour_decisionOfThePm_2026_11_08() {
        assertEquals(hour, StatusThresholds.DANGER_ZONE_UPDATE_AGE_MILLIS)
    }

    @Test
    fun inADangerZone_aLastUpdateOfExactlyOneHour_isNotRed_59m59s_neither() {
        assertEquals(StatusLevel.GREEN, level(danger(presenceAgo = 3 * hour, updateAgo = hour - 1_000)))
        assertEquals(StatusLevel.GREEN, level(danger(presenceAgo = 3 * hour, updateAgo = hour)))
    }

    @Test
    fun inADangerZone_aLastUpdateOlderThanOneHour_isRed_1h00m01s() {
        assertEquals(StatusLevel.RED, level(danger(presenceAgo = 3 * hour, updateAgo = hour + 1_000)))
        assertEquals(StatusLevel.RED, level(danger(presenceAgo = 10 * minute, updateAgo = 5 * hour)))
    }

    @Test
    fun inADangerZone_noUpdateAtAll_isRed_evenJustArrived() {
        assertEquals(StatusLevel.RED, level(danger(presenceAgo = 0, updateAgo = null)))
        assertEquals(StatusLevel.RED, level(danger(presenceAgo = 3 * hour, updateAgo = null)))
    }

    @Test
    fun inADangerZone_aRecentUpdate_followsTheNormalRuleOfTheAge() {
        // Arrived 10 minutes ago, updated 5 minutes ago: not forced red; the most recent of the two is 5 minutes old = green.
        assertEquals(StatusLevel.GREEN, level(danger(presenceAgo = 10 * minute, updateAgo = 5 * minute)))
        // A recent update after a long stay is just as recent: green.
        assertEquals(StatusLevel.GREEN, level(danger(presenceAgo = 9 * hour, updateAgo = 30 * minute)))
        // A reference in the future (a wrong clock) counts as now: not forced red.
        assertEquals(StatusLevel.GREEN, level(danger(presenceAgo = hour, updateAgo = -3 * hour)))
    }

    @Test
    fun aTrustedZoneAlwaysWinsOverTheDangerZone_green() {
        // The presence machine hands a single value: in both zones it is in_trusted, whatever the age of the update.
        assertEquals(StatusLevel.GREEN, level(MemberStatusFacts(true, PresenceValue.IN_TRUSTED, now - 5 * hour, null)))
    }

    @Test
    fun aMemberInATrustedZone_isGreen_whateverTheTime() {
        val facts = MemberStatusFacts(true, PresenceValue.IN_TRUSTED, now - 30 * hour, now - 30 * hour)
        assertEquals(StatusLevel.GREEN, level(facts))
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
    fun out_boundsOfTheDecision_1h59_isGreen() {
        assertEquals(StatusLevel.GREEN, level(out(2 * hour - minute)))
    }

    @Test
    fun out_oneHourThirty_isGreenNow_itWasOrangeBefore() {
        assertEquals(StatusLevel.GREEN, level(out(hour + 30 * minute)))
    }

    @Test
    fun out_exactlyTwoHours_isOrange() {
        assertEquals(StatusLevel.ORANGE, level(out(2 * hour)))
    }

    @Test
    fun out_2h01_isOrange() {
        assertEquals(StatusLevel.ORANGE, level(out(2 * hour + minute)))
    }

    @Test
    fun out_3h59_isOrange() {
        assertEquals(StatusLevel.ORANGE, level(out(4 * hour - minute)))
    }

    @Test
    fun out_exactlyFourHours_isStillOrange_theBoundIsIncluded() {
        assertEquals(StatusLevel.ORANGE, level(out(4 * hour)))
    }

    @Test
    fun out_4h01_isRed() {
        assertEquals(StatusLevel.RED, level(out(4 * hour + minute)))
    }

    @Test
    fun out_fourHoursAndOneSecond_isRed() {
        assertEquals(StatusLevel.RED, level(out(4 * hour + 1_000)))
    }

    @Test
    fun out_justLeft_isGreen() {
        assertEquals(StatusLevel.GREEN, level(out(0)))
    }

    @Test
    fun theReferenceIsTheMostRecentOfTheDepartureAndTheLastUpdate() {
        // Left 5 h ago, but an update 30 minutes ago: green. (Decision N2.)
        assertEquals(StatusLevel.GREEN, level(out(presenceAgo = 5 * hour, updateAgo = 30 * minute)))
        // An update older than the departure does not bring the reference back: left 3 h ago, updated 6 h ago = orange.
        assertEquals(StatusLevel.ORANGE, level(out(presenceAgo = 3 * hour, updateAgo = 6 * hour)))
        // Left 5 h ago, the update 3 h ago: the most recent is the update = orange. The departure alone would have said red.
        assertEquals(StatusLevel.ORANGE, level(out(presenceAgo = 5 * hour, updateAgo = 3 * hour)))
        // Both older than 4 h: red. One of the two within 2 h: green. One of the two within 4 h (the other older): orange.
        assertEquals(StatusLevel.RED, level(out(presenceAgo = 5 * hour, updateAgo = 6 * hour)))
        assertEquals(StatusLevel.GREEN, level(out(presenceAgo = 6 * hour, updateAgo = 119 * minute)))
        assertEquals(StatusLevel.GREEN, level(out(presenceAgo = 119 * minute, updateAgo = 6 * hour)))
    }

    @Test
    fun theExampleOfTheTicket_leftAt15h_greenUntil17h_orangeUntil19hIncluded_thenRed() {
        val left = now

        fun at(afterMinutes: Long) =
            StatusColor.level(
                MemberStatusFacts(true, PresenceValue.OUT, left, left),
                left + afterMinutes * minute,
            )
        assertEquals(StatusLevel.GREEN, at(119))
        assertEquals(StatusLevel.ORANGE, at(120))
        assertEquals(StatusLevel.ORANGE, at(240))
        assertEquals(StatusLevel.RED, at(241))
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
        assertEquals(StatusLevel.ORANGE, level(MemberStatusFacts(true, PresenceValue.OUT, null, now - 150 * minute)))
    }
}
