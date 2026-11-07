package com.tomessa.miff.domain.update

import com.tomessa.miff.domain.feed.MemberStatus
import com.tomessa.miff.domain.tracking.MemberStatusFacts
import com.tomessa.miff.domain.tracking.PresenceValue
import com.tomessa.miff.domain.tracking.StatusColor
import com.tomessa.miff.domain.tracking.StatusLevel
import com.tomessa.miff.domain.tracking.StatusThresholds
import com.tomessa.miff.testsupport.T0
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * When the bubble of encouragement shows (ticket Update D18, D19, D21, criteria A1, A2) and what colour the central button takes (criterion B1): the colour is
 * [StatusColor.level] UNCHANGED; the bubble adds the one rule of the danger zone. The limits come from [StatusThresholds], never retyped.
 */
class UpdatePromptTest {
    private val hour = 60L * 60L * 1000L

    private fun status(
        presence: PresenceValue?,
        presenceAt: Long? = null,
        lastUpdateAt: Long? = null,
    ) = MemberStatus(
        presence,
        "p1",
        presenceAt,
        lastUpdateAt,
        hasPhoto = false,
        batteryPct = 80,
        locationAccess = null,
        updatedAtMillis = T0,
    )

    private fun prompt(
        status: MemberStatus?,
        now: Long = T0,
        sharing: Boolean = true,
    ) = UpdatePrompt.level(status, sharing, now)

    // --- Orange and red, at the limits of the thresholds --------------------------------------------------------------------------

    @Test
    fun justUnderAnHour_nothing_atAnHour_orange_atTwoHoursIncluded_orange_justOver_red() {
        fun at(elapsed: Long) = prompt(status(PresenceValue.OUT, presenceAt = T0 - elapsed), now = T0)
        assertEquals(PromptLevel.NONE, at(StatusThresholds.ORANGE_AFTER_MILLIS - 1))
        assertEquals(PromptLevel.ORANGE, at(StatusThresholds.ORANGE_AFTER_MILLIS))
        assertEquals(PromptLevel.ORANGE, at(StatusThresholds.RED_AFTER_MILLIS))
        assertEquals(PromptLevel.RED, at(StatusThresholds.RED_AFTER_MILLIS + 1))
    }

    @Test
    fun theColourOfTheBubble_isTheColourOfTheCentralButton_oneCalculation() {
        // For every moment of a long day, the bubble says orange exactly when StatusColor says orange, red when red.
        for (minutes in 0..300 step 5) {
            val s = status(PresenceValue.OUT, presenceAt = T0 - minutes * 60_000L)
            val facts = MemberStatusFacts(true, s.presence, s.presenceAtMillis, s.lastUpdateAtMillis)
            val expected =
                when (StatusColor.level(facts, T0)) {
                    StatusLevel.ORANGE -> PromptLevel.ORANGE
                    StatusLevel.RED -> PromptLevel.RED
                    else -> PromptLevel.NONE
                }
            assertEquals("at $minutes min", expected, prompt(s))
        }
    }

    // --- It goes away when it is no longer orange or red (D18) -----------------------------------------------------------------------

    @Test
    fun anUpdatePublished_bringsBackToGreen_theBubbleGoes() {
        val before = status(PresenceValue.OUT, presenceAt = T0 - 3 * hour)
        assertEquals(PromptLevel.RED, prompt(before))
        // Publishing sets lastUpdateAt = now: the reference of the colour is the most recent of the departure and the update.
        val after = status(PresenceValue.OUT, presenceAt = T0 - 3 * hour, lastUpdateAt = T0)
        assertEquals(PromptLevel.NONE, prompt(after))
    }

    @Test
    fun orangeThenArrivalAtHome_withoutPublishing_theBubbleGoes() {
        val orange = status(PresenceValue.OUT, presenceAt = T0 - 90 * 60_000L)
        assertEquals(PromptLevel.ORANGE, prompt(orange))
        val home = status(PresenceValue.IN_TRUSTED, presenceAt = T0, lastUpdateAt = T0)
        assertEquals(PromptLevel.NONE, prompt(home))
    }

    @Test
    fun theBubbleNeverGoesWithTime_onlyWithAChangeOfLevel() {
        val s = status(PresenceValue.OUT, presenceAt = T0 - 3 * hour)
        listOf(0L, 10 * hour, 40 * hour).forEach { later -> assertEquals(PromptLevel.RED, prompt(s, now = T0 + later)) }
    }

    // --- The danger zone: red stays red, the bubble goes once an update follows the arrival (C8, D8) -------------------------------

    @Test
    fun inADangerZone_theBubbleIsRed_untilAnUpdateIsPublishedAfterTheArrival() {
        val entered = status(PresenceValue.IN_RISK, presenceAt = T0 - 10 * 60_000L, lastUpdateAt = T0 - 5 * hour)
        assertEquals(PromptLevel.RED, prompt(entered))
        val updated = status(PresenceValue.IN_RISK, presenceAt = T0 - 10 * 60_000L, lastUpdateAt = T0 - 60_000L)
        assertEquals(PromptLevel.NONE, prompt(updated))
        // The colour itself stays red in a danger zone: an update does not bring back to green there.
        val facts = MemberStatusFacts(true, updated.presence, updated.presenceAtMillis, updated.lastUpdateAtMillis)
        assertEquals(StatusLevel.RED, StatusColor.level(facts, T0))
    }

    @Test
    fun inADangerZone_anUpdateExactlyAtTheArrival_countsAsAfterIt() {
        val s = status(PresenceValue.IN_RISK, presenceAt = T0 - 1_000L, lastUpdateAt = T0 - 1_000L)
        assertEquals(PromptLevel.NONE, prompt(s))
    }

    @Test
    fun inADangerZone_withNoUpdateAtAll_theBubbleShows() {
        assertEquals(
            PromptLevel.RED,
            prompt(status(PresenceValue.IN_RISK, presenceAt = T0 - 1_000L, lastUpdateAt = null)),
        )
    }

    // --- Nothing to encourage ---------------------------------------------------------------------------------------------------------

    @Test
    fun noStatus_sharingStopped_unknownPresence_neverAnyBubble() {
        assertEquals(PromptLevel.NONE, prompt(null))
        assertEquals(PromptLevel.NONE, prompt(status(PresenceValue.OUT, presenceAt = T0 - 3 * hour), sharing = false))
        // A person who never went through a trusted zone is neutral (N4): no bubble, whatever the clock says.
        assertEquals(PromptLevel.NONE, prompt(status(PresenceValue.UNKNOWN, presenceAt = T0 - 30 * hour)))
        assertEquals(PromptLevel.NONE, prompt(status(null)))
    }

    @Test
    fun aReferenceInTheFuture_aWrongClock_isGreen_noBubble() {
        assertEquals(PromptLevel.NONE, prompt(status(PresenceValue.OUT, presenceAt = T0 + 5 * hour)))
    }
}
