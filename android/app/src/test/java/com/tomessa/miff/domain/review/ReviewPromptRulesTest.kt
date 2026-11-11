package com.tomessa.miff.domain.review

import com.tomessa.miff.domain.feed.PlaceKind
import com.tomessa.miff.domain.feed.PlaceRef
import com.tomessa.miff.domain.feed.StateLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the review window may appear (docs/tickets/pop-ins-avis.md): at most every 90 days after "Peux faire mieux...", back after 30 days whenever it was closed
 * without an answer (NO limit to the closures, decision of the PM of 2026-11-08), only for a Miff of 7 days or more with a loved one's status seen, never on the
 * Update, with the keyboard or over a message of the app, never again after "J'adore !".
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

    // --- never over the Update, with the keyboard, or over a message -----------------------------------------------------------------------------

    @Test
    fun neverOnTheUpdate_neverWithTheKeyboard() {
        assertFalse(may(oldMiff, ready.copy(updateScreenOpen = true)))
        assertFalse(may(oldMiff, ready.copy(keyboardOpen = true)))
    }

    @Test
    fun neverOverAMessageOfTheApp() {
        assertFalse(may(oldMiff, ready.copy(blockingMessageOpen = true)))
    }

    @Test
    fun theMessagesThatMustNotBeCovered_areTheLostAccessOne_theAgreement_theLocateSheet_andASharingThatStopped() {
        fun open(
            lost: Boolean = false,
            agreement: Boolean = false,
            locate: Boolean = false,
            label: StateLabel? = null,
        ) = ReviewSituation.messageOpen(lost, agreement, locate, label)
        assertFalse(open())
        assertFalse(open(label = StateLabel.At(PlaceRef.Category(PlaceKind.HOME))))
        assertFalse(open(label = StateLabel.NoNews))
        assertTrue(open(lost = true))
        assertTrue(open(agreement = true))
        assertTrue(open(locate = true))
        assertTrue(open(label = StateLabel.SharingStopped))
        assertTrue(open(label = StateLabel.WaitingAgreement))
    }

    @Test
    fun theConnectionPopIn_isAWindowToo_theReviewWindowNeverAppearsOverIt() {
        assertFalse(ReviewSituation.messageOpen(false, false, false, null))
        assertTrue(ReviewSituation.messageOpen(false, false, false, null, connectionPopInOpen = true))
    }

    // --- after "J'adore !": never again --------------------------------------------------------------------------------------------------------------

    @Test
    fun afterLove_neverAgain() {
        val state = oldMiff.copy(lastShownAtMillis = now - 1_000 * day, lastOutcome = ReviewOutcome.LOVED)
        assertFalse(may(state))
    }

    // --- 90 days after "Peux faire mieux..." (no limit to the number of times) -----------------------------------------------------------------

    @Test
    fun afterCanDoBetter_theQuestionIsAskedAgainOnlyAfterNinetyDays_andAgainEachTime() {
        val state =
            ReviewState(
                firstMiffSeenAtMillis = now - 400 * day,
                lastShownAtMillis = now - 89 * day,
                lastOutcome = ReviewOutcome.CAN_DO_BETTER,
            )
        assertFalse(may(state))
        assertFalse(may(state, at = now + day - 1))
        assertTrue(may(state, at = now + day))
        // Asked again and "Peux faire mieux..." again: another 90 days, as many times as it takes.
        val again = state.shown(now + day).answered(ReviewOutcome.CAN_DO_BETTER)
        assertFalse(may(again, at = now + 90 * day))
        assertTrue(may(again, at = now + 91 * day))
    }

    // --- closed without an answer: back after 30 days, with NO limit ---------------------------------------------------------------------------

    @Test
    fun afterAClosure_theWindowComesBackAfterThirtyDays_notBefore() {
        val state =
            ReviewState(
                firstMiffSeenAtMillis = now - 400 * day,
                lastShownAtMillis = now - 29 * day,
                lastOutcome = ReviewOutcome.CLOSED,
            )
        assertFalse(may(state))
        assertTrue(may(state, at = now + day))
    }

    @Test
    fun closuresAreNeverCounted_theWindowKeepsComingBackEveryThirtyDays() {
        var state = oldMiff
        var at = now
        repeat(50) {
            assertTrue("appearance ${it + 1}", may(state, at = at))
            state = state.shown(at).closed()
            assertFalse(may(state, at = at + 30 * day - 1))
            at += 30 * day
        }
    }

    @Test
    fun aWindowTheAppWasStoppedOn_waitsLikeAClosure() {
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
    fun shownThenClosed_setsTheOutcomeAndTheDate() {
        val shown = oldMiff.shown(now)
        assertEquals(ReviewOutcome.SHOWN, shown.lastOutcome)
        assertEquals(now, shown.lastShownAtMillis)
        assertEquals(ReviewOutcome.CLOSED, shown.closed().lastOutcome)
    }

    @Test
    fun answered_refusesWhatIsNotAnAnswer() {
        val state = oldMiff.shown(now).answered(ReviewOutcome.CAN_DO_BETTER)
        assertEquals(ReviewOutcome.CAN_DO_BETTER, state.lastOutcome)
        val failure = runCatching { state.answered(ReviewOutcome.CLOSED) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun aFreshState_holdsNothing() {
        val state = ReviewState()
        assertNull(state.firstMiffSeenAtMillis)
        assertNull(state.lastShownAtMillis)
        assertNull(state.lastOutcome)
    }
}
