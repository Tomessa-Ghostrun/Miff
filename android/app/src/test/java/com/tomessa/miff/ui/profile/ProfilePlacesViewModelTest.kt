package com.tomessa.miff.ui.profile

import com.tomessa.miff.data.miff.PlacesRead
import com.tomessa.miff.data.miff.PlacesReader
import com.tomessa.miff.domain.place.MiffPlacesBook
import com.tomessa.miff.domain.place.MiffPlacesView
import com.tomessa.miff.domain.place.Place
import com.tomessa.miff.domain.place.ReadPlaces
import com.tomessa.miff.testsupport.testPlace
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The places under the carousel of Miffs (decision of the PM, 2026-11-11): the cost rules seen from the screen. The count of readings is the point:
 * each reading of `places` is one Firestore reading per place, and nothing listens.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfilePlacesViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeReader : PlacesReader {
        val reads = mutableListOf<String>()
        var answer: (String) -> PlacesRead = { PlacesRead.Loaded(places(2)) }

        /** When set, the reading waits for it (to look at the state while it is under way). */
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun read(miffId: String): PlacesRead {
            reads += miffId
            gate?.await()
            return answer(miffId)
        }
    }

    private companion object {
        fun places(count: Int): List<Place> = (1..count).map { testPlace("p$it", "Lieu $it") }
    }

    private val reader = FakeReader()
    private val feed = MutableStateFlow<ReadPlaces?>(null)
    private var now = 1_000_000L

    private fun viewModel() = ProfilePlacesViewModel(reader, feed, nowMillis = { now })

    private fun ProfilePlacesViewModel.count(): Int? = (state.value.view as? MiffPlacesView.Ready)?.places?.size

    @Test
    fun nothingIsSelectedNorRead_untilACardAppears() {
        val vm = viewModel()
        dispatcher.scheduler.runCurrent()
        assertNull(vm.state.value.selectedMiffId)
        assertEquals(MiffPlacesView.Loading, vm.state.value.view)
        assertTrue(reader.reads.isEmpty())
    }

    @Test
    fun theMiffOfTheFeed_isDrawnFromWhatTheFeedRead_withNoReading() {
        val vm = viewModel()
        feed.value = ReadPlaces("a", places(3))
        dispatcher.scheduler.runCurrent()
        vm.onCardShown("a")
        dispatcher.scheduler.runCurrent()
        assertEquals("a", vm.state.value.selectedMiffId)
        assertEquals(3, vm.count())
        assertTrue("the Feed already paid for these places", reader.reads.isEmpty())
    }

    @Test
    fun anotherMiff_isReadOneTime_whenItsCardAppears_andTheSelectionFollowsTheCard() {
        val vm = viewModel()
        vm.onCardShown("b")
        dispatcher.scheduler.runCurrent()
        assertEquals(listOf("b"), reader.reads)
        assertEquals("b", vm.state.value.selectedMiffId)
        assertEquals(2, vm.count())
    }

    @Test
    fun swipingBackAndForth_readsEachMiffOnce_withinFiveMinutes() {
        val vm = viewModel()
        repeat(3) {
            vm.onCardShown("b")
            dispatcher.scheduler.runCurrent()
            vm.onCardShown("c")
            dispatcher.scheduler.runCurrent()
        }
        assertEquals(listOf("b", "c"), reader.reads)
        now += MiffPlacesBook.TTL_MILLIS
        vm.onCardShown("b")
        dispatcher.scheduler.runCurrent()
        assertEquals("after 5 minutes, one more", listOf("b", "c", "b"), reader.reads)
    }

    @Test
    fun aCardThatAppearsWhileItsReadingIsUnderWay_doesNotStartAnother_andShowsNothingMeanwhile() {
        reader.gate = CompletableDeferred()
        val vm = viewModel()
        vm.onCardShown("b")
        dispatcher.scheduler.runCurrent()
        assertEquals("no empty sentence while reading", MiffPlacesView.Loading, vm.state.value.view)
        vm.onCardShown("b")
        dispatcher.scheduler.runCurrent()
        assertEquals(1, reader.reads.size)
        reader.gate?.complete(Unit)
        dispatcher.scheduler.runCurrent()
        assertEquals(2, vm.count())
    }

    @Test
    fun aReadingThatEndsAfterTheCardWasLeft_doesNotChangeWhatTheNewCardShows() {
        reader.gate = CompletableDeferred()
        reader.answer = { id -> PlacesRead.Loaded(if (id == "b") places(5) else places(1)) }
        val vm = viewModel()
        vm.onCardShown("b")
        dispatcher.scheduler.runCurrent()
        vm.onCardShown("c")
        dispatcher.scheduler.runCurrent()
        reader.gate?.complete(Unit)
        dispatcher.scheduler.runCurrent()
        assertEquals("c", vm.state.value.selectedMiffId)
        assertEquals(1, vm.count())
    }

    @Test
    fun aFailedReading_isCalm_andWaitsBeforeTryingAgain_andRetryReadsAtOnce() {
        reader.answer = { PlacesRead.Failed }
        val vm = viewModel()
        vm.onCardShown("b")
        dispatcher.scheduler.runCurrent()
        assertEquals(MiffPlacesView.Unavailable, vm.state.value.view)
        vm.onCardShown("b")
        dispatcher.scheduler.runCurrent()
        assertEquals("never a loop", 1, reader.reads.size)
        reader.answer = { PlacesRead.Loaded(places(1)) }
        vm.onRetry()
        dispatcher.scheduler.runCurrent()
        assertEquals("the person asked: no wait", 2, reader.reads.size)
        assertEquals(1, vm.count())
    }

    @Test
    fun aFailureAfterASuccess_keepsTheListThatWasShown() {
        val vm = viewModel()
        vm.onCardShown("b")
        dispatcher.scheduler.runCurrent()
        now += MiffPlacesBook.TTL_MILLIS
        reader.answer = { PlacesRead.Failed }
        vm.onCardShown("b")
        dispatcher.scheduler.runCurrent()
        assertEquals(2, vm.count())
    }

    @Test
    fun aMiffWithNoPlace_isReady_notLoading_soTheCalmEmptySentenceCanShow() {
        reader.answer = { PlacesRead.Loaded(emptyList()) }
        val vm = viewModel()
        vm.onCardShown("b")
        dispatcher.scheduler.runCurrent()
        assertEquals(0, vm.count())
    }

    @Test
    fun theCreatorOpeningAPlaceOrTheForm_makesTheNextAppearanceReadAgain_evenForTheFeedMiff() {
        val vm = viewModel()
        feed.value = ReadPlaces("a", places(3))
        dispatcher.scheduler.runCurrent()
        vm.onCardShown("a")
        vm.onCardShown("b")
        dispatcher.scheduler.runCurrent()
        assertEquals(listOf("b"), reader.reads)
        vm.onPlacesMayChange()
        // The Profile comes back on the card it was on.
        reader.answer = { PlacesRead.Loaded(places(4)) }
        vm.onCardShown("b")
        dispatcher.scheduler.runCurrent()
        assertEquals(listOf("b", "b"), reader.reads)
        assertEquals(4, vm.count())
        vm.onCardShown("a")
        dispatcher.scheduler.runCurrent()
        assertEquals(
            "the Miff of the Feed is read again too: its copy is older than the change",
            listOf("b", "b", "a"),
            reader.reads,
        )
    }

    @Test
    fun aReadingStillUnderWay_whenTheCreatorChangesAPlace_isReadAgainAtTheNextAppearance() {
        reader.gate = CompletableDeferred()
        val vm = viewModel()
        vm.onCardShown("b")
        dispatcher.scheduler.runCurrent()
        vm.onPlacesMayChange()
        reader.gate?.complete(Unit)
        dispatcher.scheduler.runCurrent()
        assertEquals(1, reader.reads.size)
        vm.onCardShown("b")
        dispatcher.scheduler.runCurrent()
        assertEquals("the first answer may predate the change", 2, reader.reads.size)
    }

    @Test
    fun aMemberWhoOpensAPlace_costsNoReading() {
        val vm = viewModel()
        vm.onCardShown("b")
        dispatcher.scheduler.runCurrent()
        // The shell calls onPlacesMayChange() only for the creator of the Miff (see MainShell): nothing else invalidates.
        vm.onCardShown("b")
        dispatcher.scheduler.runCurrent()
        assertEquals(1, reader.reads.size)
    }

    @Test
    fun aMiffThatIsGone_takesItsPlacesAndTheSelectionWithIt() {
        val vm = viewModel()
        vm.onCardShown("b")
        dispatcher.scheduler.runCurrent()
        vm.retainOnly(setOf("a"))
        assertNull(vm.state.value.selectedMiffId)
        assertEquals(MiffPlacesView.Loading, vm.state.value.view)
        vm.onCardShown("b")
        dispatcher.scheduler.runCurrent()
        assertEquals("forgotten, so read again", 2, reader.reads.size)
    }

    @Test
    fun theStateShowsNothingInItsToString() {
        val vm = viewModel()
        vm.onCardShown("b")
        dispatcher.scheduler.runCurrent()
        assertEquals("ProfilePlacesState(redacted)", vm.state.value.toString())
        assertEquals(
            "Ready(redacted)",
            vm.state.value.view
                .toString(),
        )
    }
}
