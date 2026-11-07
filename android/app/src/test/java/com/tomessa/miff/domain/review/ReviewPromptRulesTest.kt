package com.tomessa.miff.domain.review

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the review window may appear (docs/tickets/pop-ins-avis.md, defaults chosen by the developer and listed in the PR): at most every 90 days after an
 * answer, back after 30 days when closed without an answer and never after two closures, only for a Miff of 7 days or more with a loved one's status seen, never
 * on the Update or with the keyboard, never again after "J'adore".
 */
class ReviewPromptRulesTest {
    private val day = 86_400_000L
    private val now = 1_000 * day

    private val ready =
        ReviewSituation(hasMiff = true, friendStatusSeen = true, updateScreenOpen = false, keyboardOpen = false)
    private val oldMiff = ReviewState(firstMiffSeenAtMillis = now - 8 * day)

    private fun may(
        state: ReviewState,
        situation: ReviewSituation = ready,
        at: Long = now,
    ) = ReviewPromptRules.mayAppear(state, situation, at)

    @Test
    fun theThresholdsAreTheOnesOfTheBrief() {
        assertEquals(7 * day, ReviewPromptDefaults.MIN_MIFF_AGE_MILLIS)
        assertEquals(90 * day, ReviewPromptDefaults.MIN_INTERVAL_AFTER_ANSWER_MILLIS)
        assertEquals(30 * day, ReviewPromptDefaults.MIN_INTERVAL_AFTER_CLOSE_MILLIS)
        assertEquals(2, ReviewPromptDefaults.MAX_CLOSURES)
    }

    // --- the Miff and the Feed ---------------------------------------------------------------------------------------------------------------

    @Test
    fun aMiffOfEightDays_withAFriendStatusSeen_mayShowTheWindow() {
        assertTrue(may(oldMiff))
    }

    @Test
    fun aMiffSeenForLessThanSevenDays_neverShowsIt() {
        assertFalse(may(ReviewState(firstMiffSeenAtMillis = now - 7 * day + 1)))
        assertFalse(may(ReviewState(firstMiffSeenAtMillis = now)))
    }

    @Test
    fun exactlySevenDays_isEnough() {
        assertTrue(may(ReviewState(firstMiffSeenAtMillis = now - 7 * day)))
    }

    @Test
    fun withoutADateForTheFirstMiff_orWithoutAMiff_orWithoutAFriendStatus_nothing() {
        assertFalse(may(ReviewState()))
        assertFalse(may(oldMiff, ready.copy(hasMiff = false)))
        assertFalse(may(oldMiff, ready.copy(friendStatusSeen = false)))
    }

    @Test
    fun aClockThatWentBackwards_waitsInsteadOfShowing() {
        // The first Miff "in the future" and a last appearance "in the future": too soon, never twice.
        assertFalse(may(ReviewState(firstMiffSeenAtMillis = now + day)))
        assertFalse(may(oldMiff.copy(lastShownAtMillis = now + day, lastOutcome = ReviewOutcome.CLOSED)))
    }

    // --- never over the Update, never with the keyboard ------------------------------------------------------------------------------------------

    @Test
    fun neverOnTheUpdate_neverWithTheKeyboard() {
        assertFalse(may(oldMiff, ready.copy(updateScreenOpen = true)))
        assertFalse(may(oldMiff, ready.copy(keyboardOpen = true)))
    }

    // --- 90 days after an answer -----------------------------------------------------------------------------------------------------------------

    @Test
    fun afterLove_neverAgain() {
        val state = oldMiff.copy(lastShownAtMillis = now - 1_000 * day, lastOutcome = ReviewOutcome.LOVED)
        assertFalse(may(state))
    }

    @Test
    fun afterCanDoBetter_theQuestionIsAskedAgainOnlyAfterNinetyDays() {
        val state =
            ReviewState(
                firstMiffSeenAtMillis = now - 400 * day,
                lastShownAtMillis = now - 89 * day,
                lastOutcome = ReviewOutcome.CAN_DO_BETTER,
            )
        assertFalse(may(state))
        assertFalse(may(state, at = now + day - 1))
        assertTrue(may(state, at = now + day))
    }

    // --- closed without an answer: back after 30 days, two closures at most -----------------------------------------------------------------------

    @Test
    fun afterOneClosure_theWindowComesBackAfterThirtyDays_notBefore() {
        val state =
            ReviewState(
                firstMiffSeenAtMillis = now - 400 * day,
                lastShownAtMillis = now - 29 * day,
                lastOutcome = ReviewOutcome.CLOSED,
                closures = 1,
            )
        assertFalse(may(state))
        assertTrue(may(state, at = now + day))
    }

    @Test
    fun afterTwoClosures_neverAgain() {
        val state =
            ReviewState(
                firstMiffSeenAtMillis = now - 900 * day,
                lastShownAtMillis = now - 500 * day,
                lastOutcome = ReviewOutcome.CLOSED,
                closures = 2,
            )
        assertFalse(may(state))
    }

    @Test
    fun aWindowTheAppWasStoppedOn_waitsLikeAClosure_withoutCountingOne() {
        val state =
            ReviewState(
                firstMiffSeenAtMillis = now - 400 * day,
                lastShownAtMillis = now - 10 * day,
                lastOutcome = ReviewOutcome.SHOWN,
            )
        assertFalse(may(state))
        assertTrue(may(state, at = now + 20 * day))
    }

    // --- the transitions of the state --------------------------------------------------------------------------------------------------------------

    @Test
    fun theFirstMiffDateIsKeptTheFirstTimeOnly() {
        val state = ReviewState().withMiffSeen(100L).withMiffSeen(900L)
        assertEquals(100L, state.firstMiffSeenAtMillis)
    }

    @Test
    fun shownThenClosedTwice_countsTwoClosures_andShownResetsTheOutcome() {
        var state = oldMiff.shown(now)
        assertEquals(ReviewOutcome.SHOWN, state.lastOutcome)
        assertEquals(now, state.lastShownAtMillis)
        state = state.closed()
        assertEquals(1, state.closures)
        state = state.shown(now + 31 * day).closed()
        assertEquals(2, state.closures)
        assertEquals(ReviewOutcome.CLOSED, state.lastOutcome)
    }

    @Test
    fun answered_keepsTheClosuresAndRefusesWhatIsNotAnAnswer() {
        val state = oldMiff.copy(closures = 1).shown(now).answered(ReviewOutcome.CAN_DO_BETTER)
        assertEquals(ReviewOutcome.CAN_DO_BETTER, state.lastOutcome)
        assertEquals(1, state.closures)
        val failure = runCatching { state.answered(ReviewOutcome.CLOSED) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun aFreshState_holdsNothing() {
        val state = ReviewState()
        assertNull(state.firstMiffSeenAtMillis)
        assertNull(state.lastShownAtMillis)
        assertNull(state.lastOutcome)
        assertEquals(0, state.closures)
    }
}
