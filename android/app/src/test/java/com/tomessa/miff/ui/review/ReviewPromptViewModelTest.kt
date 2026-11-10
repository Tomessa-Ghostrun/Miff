package com.tomessa.miff.ui.review

import com.tomessa.miff.data.review.ReviewStateStore
import com.tomessa.miff.domain.review.ReviewOutcome
import com.tomessa.miff.domain.review.ReviewPromptDefaults
import com.tomessa.miff.domain.review.ReviewSituation
import com.tomessa.miff.domain.review.ReviewState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The screens of the review window and what each way out remembers (docs/tickets/pop-ins-avis.md): "J'adore" or the fifth star = a short "Merci !" then the request of
 * the store, once, for good; "Peux faire mieux..." (or a star under five) = NO request of the store, a calm thank-you, asked again only after 90 days; the cross or
 * the back gesture = a closure, two at most; a debug build can force the window, nothing else can.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReviewPromptViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class MemoryStore(
        var state: ReviewState = ReviewState(),
        var force: Boolean = false,
    ) : ReviewStateStore {
        override fun read(): ReviewState = state

        override fun write(state: ReviewState) {
            this.state = state
        }

        override fun clear() {
            state = ReviewState()
        }

        override fun forceNext(): Boolean = force

        override fun setForceNext(value: Boolean) {
            force = value
        }
    }

    private val day = 86_400_000L
    private var now = 1_000 * day
    private val ready =
        ReviewSituation(hasMiff = true, friendStatusSeen = true, updateScreenOpen = false, keyboardOpen = false)

    private fun oldMiff() = ReviewState(firstMiffSeenAtMillis = now - 10 * day)

    private fun viewModel(
        store: MemoryStore,
        debug: Boolean = false,
        form: String = "",
    ) = ReviewPromptViewModel(store, { now }, debugForceAllowed = debug, feedbackFormUrl = form)

    // --- appearance --------------------------------------------------------------------------------------------------------------------------------

    @Test
    fun atAReturnToTheForeground_withTheRulesMet_theQuestionAppears_andIsRememberedBeforeItIsDrawn() {
        val store = MemoryStore(oldMiff())
        val vm = viewModel(store)
        vm.onForeground(1, ready)
        assertEquals(ReviewStage.Question, vm.stage.value)
        assertEquals(now, store.state.lastShownAtMillis)
        assertEquals(ReviewOutcome.SHOWN, store.state.lastOutcome)
    }

    @Test
    fun aReturnIsLookedAtOnce_aSecondLookWithTheSameReturnDoesNothing() {
        val store = MemoryStore(oldMiff())
        val vm = viewModel(store)
        vm.onForeground(1, ready.copy(friendStatusSeen = false))
        assertEquals(ReviewStage.Hidden, vm.stage.value)
        assertTrue(vm.hasHandled(1))
        vm.onForeground(1, ready)
        assertEquals(ReviewStage.Hidden, vm.stage.value)
        // The next return is looked at.
        assertFalse(vm.hasHandled(2))
        vm.onForeground(2, ready)
        assertEquals(ReviewStage.Question, vm.stage.value)
    }

    @Test
    fun aMiffSeenForLessThanSevenDays_showsNothing_andTheFirstDateIsKept() {
        val store = MemoryStore()
        val vm = viewModel(store)
        vm.onMiffSeen()
        assertEquals(now, store.state.firstMiffSeenAtMillis)
        val first = now
        now += day
        vm.onMiffSeen()
        assertEquals(first, store.state.firstMiffSeenAtMillis)
        vm.onForeground(1, ready)
        assertEquals(ReviewStage.Hidden, vm.stage.value)
        assertEquals(null, store.state.lastShownAtMillis)
    }

    @Test
    fun whileTheWindowIsOnScreen_aNewReturnDoesNotStackAnother() {
        val store = MemoryStore(oldMiff())
        val vm = viewModel(store)
        vm.onForeground(1, ready)
        val shownAt = store.state.lastShownAtMillis
        now += 1_000
        vm.onForeground(2, ready)
        assertEquals(ReviewStage.Question, vm.stage.value)
        assertEquals(shownAt, store.state.lastShownAtMillis)
    }

    // --- J'adore -----------------------------------------------------------------------------------------------------------------------------------

    @Test
    fun love_thanksBriefly_thenAsksTheStoreOnce_andNeverAgain() =
        runTest(dispatcher) {
            val store = MemoryStore(oldMiff())
            val vm = viewModel(store)
            vm.onForeground(1, ready)
            vm.onLove()
            assertEquals(ReviewStage.ThanksForLove, vm.stage.value)
            assertEquals(ReviewOutcome.LOVED, store.state.lastOutcome)
            // Nothing is asked of the store while "Merci !" is on screen.
            advanceTimeBy(ReviewPromptDefaults.THANKS_MILLIS - 1)
            assertEquals(ReviewStage.ThanksForLove, vm.stage.value)
            advanceUntilIdle()
            assertEquals(ReviewStage.Hidden, vm.stage.value)
            assertEquals(ReviewEffect.RequestStoreReview, vm.effects.first())
            // A second tap (a double tap) does not ask twice.
            vm.onLove()
            advanceUntilIdle()
            assertEquals(ReviewStage.Hidden, vm.stage.value)
            // A year later: never again.
            now += 365 * day
            vm.onForeground(2, ready)
            assertEquals(ReviewStage.Hidden, vm.stage.value)
        }

    @Test
    fun theBackGestureDuringTheThanks_doesNothing() =
        runTest(dispatcher) {
            val vm = viewModel(MemoryStore(oldMiff()))
            vm.onForeground(1, ready)
            vm.onLove()
            vm.onClose()
            assertEquals(ReviewStage.ThanksForLove, vm.stage.value)
        }

    // --- Peux faire mieux... -----------------------------------------------------------------------------------------------------------------------

    @Test
    fun canDoBetter_doesNotOpenTheStore_showsACalmThanks_andWaitsNinetyDays() =
        runTest(dispatcher) {
            val store = MemoryStore(oldMiff())
            val vm = viewModel(store)
            vm.onForeground(1, ready)
            vm.onCanDoBetter()
            advanceUntilIdle()
            assertEquals(ReviewStage.ThanksForFeedback, vm.stage.value)
            assertEquals(ReviewOutcome.CAN_DO_BETTER, store.state.lastOutcome)
            // Nothing was sent to the outside: no store request, no mail yet.
            vm.onLater()
            assertEquals(ReviewStage.Hidden, vm.stage.value)
            now += 89 * day
            vm.onForeground(2, ready)
            assertEquals(ReviewStage.Hidden, vm.stage.value)
            now += 2 * day
            vm.onForeground(3, ready)
            assertEquals(ReviewStage.Question, vm.stage.value)
        }

    @Test
    fun writeToUs_opensTheMailOnce_andClosesTheWindow() =
        runTest(dispatcher) {
            val vm = viewModel(MemoryStore(oldMiff()))
            vm.onForeground(1, ready)
            vm.onCanDoBetter()
            vm.onWrite()
            vm.onWrite()
            assertEquals(ReviewStage.Hidden, vm.stage.value)
            assertEquals(ReviewEffect.OpenMail, vm.effects.first())
        }

    @Test
    fun theBackGestureOnTheThanks_justCloses_andTheOutcomeStaysCanDoBetter() {
        val store = MemoryStore(oldMiff())
        val vm = viewModel(store)
        vm.onForeground(1, ready)
        vm.onCanDoBetter()
        vm.onClose()
        assertEquals(ReviewStage.Hidden, vm.stage.value)
        assertEquals(ReviewOutcome.CAN_DO_BETTER, store.state.lastOutcome)
    }

    // --- closing without an answer -----------------------------------------------------------------------------------------------------------------

    @Test
    fun closing_theWindowComesBackAfterThirtyDays_everyTime_withNoLimit() {
        val store = MemoryStore(oldMiff())
        val vm = viewModel(store)
        var tick = 0
        repeat(5) {
            tick++
            vm.onForeground(tick, ready)
            assertEquals("appearance ${it + 1}", ReviewStage.Question, vm.stage.value)
            vm.onClose()
            assertEquals(ReviewStage.Hidden, vm.stage.value)
            assertEquals(ReviewOutcome.CLOSED, store.state.lastOutcome)
            now += 29 * day
            tick++
            vm.onForeground(tick, ready)
            assertEquals(ReviewStage.Hidden, vm.stage.value)
            now += 2 * day
        }
    }

    @Test
    fun closing_twiceInARow_closesOnce() {
        val store = MemoryStore(oldMiff())
        val vm = viewModel(store)
        vm.onForeground(1, ready)
        vm.onClose()
        val after = store.state
        vm.onClose()
        assertEquals(after, store.state)
    }

    // --- "Peux faire mieux..." and the external form -----------------------------------------------------------------------------------------------

    @Test
    fun withAFeedbackForm_canDoBetter_closesTheWindow_andOpensTheFormOnce_withoutTheStore() =
        runTest(dispatcher) {
            val store = MemoryStore(oldMiff())
            val vm = viewModel(store, form = "https://forms.example.org/miff")
            vm.onForeground(1, ready)
            vm.onCanDoBetter()
            vm.onCanDoBetter()
            advanceUntilIdle()
            assertEquals(ReviewStage.Hidden, vm.stage.value)
            assertEquals(ReviewOutcome.CAN_DO_BETTER, store.state.lastOutcome)
            assertEquals(ReviewEffect.OpenFeedbackForm("https://forms.example.org/miff"), vm.effects.first())
            // Asked again only after 90 days.
            now += 89 * day
            vm.onForeground(2, ready)
            assertEquals(ReviewStage.Hidden, vm.stage.value)
            now += 2 * day
            vm.onForeground(3, ready)
            assertEquals(ReviewStage.Question, vm.stage.value)
        }

    @Test
    fun withoutAFeedbackForm_orWithAnAddressThatIsNotHttps_theCalmScreenWithWriteToUsShows() {
        for (form in listOf("", "   ", "http://forms.example.org/miff", "javascript:alert(1)")) {
            val vm = viewModel(MemoryStore(oldMiff()), form = form)
            vm.onForeground(1, ready)
            vm.onCanDoBetter()
            assertEquals(form, ReviewStage.ThanksForFeedback, vm.stage.value)
        }
    }

    @Test
    fun theLoveButton_neverOpensTheForm_andTheFormNeverOpensTheStore() =
        runTest(dispatcher) {
            val love = viewModel(MemoryStore(oldMiff()), form = "https://forms.example.org/miff")
            love.onForeground(1, ready)
            love.onLove()
            advanceUntilIdle()
            assertEquals(ReviewEffect.RequestStoreReview, love.effects.first())
        }

    @Test
    fun aMessageOfTheApp_keepsTheWindowAway_evenForcedInDebug() {
        val store = MemoryStore(oldMiff(), force = true)
        val vm = viewModel(store, debug = true)
        vm.onForeground(1, ready.copy(blockingMessageOpen = true))
        assertEquals(ReviewStage.Hidden, vm.stage.value)
        assertTrue(store.force)
        val plain = viewModel(MemoryStore(oldMiff()))
        plain.onForeground(1, ready.copy(blockingMessageOpen = true))
        assertEquals(ReviewStage.Hidden, plain.stage.value)
    }

    // --- the debug option --------------------------------------------------------------------------------------------------------------------------

    @Test
    fun aDebugBuild_canForceTheWindow_once_whateverTheDates() {
        val store = MemoryStore(ReviewState(), force = true)
        val vm = viewModel(store, debug = true)
        vm.onForeground(1, ready.copy(friendStatusSeen = false))
        assertEquals(ReviewStage.Question, vm.stage.value)
        assertFalse(store.force)
    }

    @Test
    fun aForcedWindow_neverCoversTheUpdateOrTheKeyboard_andWaitsForTheNextReturn() {
        val store = MemoryStore(ReviewState(), force = true)
        val vm = viewModel(store, debug = true)
        vm.onForeground(1, ready.copy(updateScreenOpen = true))
        assertEquals(ReviewStage.Hidden, vm.stage.value)
        vm.onForeground(1, ready.copy(keyboardOpen = true))
        assertEquals(ReviewStage.Hidden, vm.stage.value)
        assertTrue(store.force)
        vm.onForeground(2, ready)
        assertEquals(ReviewStage.Question, vm.stage.value)
    }

    @Test
    fun aBuildThatIsNotDebug_ignoresTheForce() {
        val store = MemoryStore(ReviewState(), force = true)
        val vm = viewModel(store, debug = false)
        vm.onForeground(1, ready)
        assertEquals(ReviewStage.Hidden, vm.stage.value)
    }
}
