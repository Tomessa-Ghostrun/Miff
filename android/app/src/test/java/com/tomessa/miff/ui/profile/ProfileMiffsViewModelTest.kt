package com.tomessa.miff.ui.profile

import com.tomessa.miff.data.miff.FacesRead
import com.tomessa.miff.data.miff.MemberFacesReader
import com.tomessa.miff.domain.miff.MiffMember
import com.tomessa.miff.domain.miff.MiffRole
import com.tomessa.miff.domain.profile.CardFaces
import com.tomessa.miff.domain.profile.MiffFacesBook
import com.tomessa.miff.domain.profile.ReadMembers
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The faces of the carousel of Miffs: the cost rules seen from the screen (what is read, when, how often). The count of readings is the point:
 * each reading of `members` is one Firestore reading per member card.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileMiffsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeReader : MemberFacesReader {
        val reads = mutableListOf<String>()
        var answer: (String) -> FacesRead = { FacesRead.Loaded(members(3)) }

        /** When set, the reading waits for it (to look at the state while it is under way). */
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun read(miffId: String): FacesRead {
            reads += miffId
            gate?.await()
            return answer(miffId)
        }
    }

    private companion object {
        fun members(count: Int) =
            (1..count).map { MiffMember("u$it", "Léa", "Martin", null, MiffRole.MEMBER, sharingEnabled = true) }
    }

    private val reader = FakeReader()
    private val feed = MutableStateFlow<ReadMembers?>(null)
    private var now = 1_000_000L

    private fun viewModel() = ProfileMiffsViewModel(reader, feed, nowMillis = { now })

    private fun ProfileMiffsViewModel.counts() = faces.value.mapValues { (it.value as? CardFaces.Ready)?.count }

    @Test
    fun theMiffOfTheFeed_isDrawnFromWhatTheFeedRead_withNoReading() {
        val vm = viewModel()
        feed.value = ReadMembers("a", members(4))
        dispatcher.scheduler.runCurrent()
        vm.onCardShown("a")
        dispatcher.scheduler.runCurrent()
        assertEquals(mapOf<String, Int?>("a" to 4), vm.counts())
        assertTrue("the Feed already paid for these members", reader.reads.isEmpty())
    }

    @Test
    fun anotherMiff_isReadOneTime_whenItsCardAppears_notBefore() {
        val vm = viewModel()
        dispatcher.scheduler.runCurrent()
        assertTrue("nothing is read until a card appears", reader.reads.isEmpty())
        vm.onCardShown("b")
        dispatcher.scheduler.runCurrent()
        assertEquals(listOf("b"), reader.reads)
        assertEquals(mapOf<String, Int?>("b" to 3), vm.counts())
    }

    @Test
    fun theSameCard_appearingAgain_orTheTabOpenedAgain_readsNothingWithinFiveMinutes() {
        val vm = viewModel()
        vm.onCardShown("b")
        dispatcher.scheduler.runCurrent()
        repeat(5) { vm.onCardShown("b") }
        dispatcher.scheduler.runCurrent()
        assertEquals("one reading, however many times the card comes back", 1, reader.reads.size)
        now += MiffFacesBook.TTL_MILLIS
        vm.onCardShown("b")
        dispatcher.scheduler.runCurrent()
        assertEquals("after 5 minutes, one more", 2, reader.reads.size)
    }

    @Test
    fun aCardThatAppearsWhileItsReadingIsUnderWay_doesNotStartAnother() {
        reader.gate = CompletableDeferred()
        val vm = viewModel()
        vm.onCardShown("b")
        dispatcher.scheduler.runCurrent()
        assertEquals(CardFaces.Loading, vm.faces.value["b"])
        vm.onCardShown("b")
        dispatcher.scheduler.runCurrent()
        assertEquals(1, reader.reads.size)
        reader.gate?.complete(Unit)
        dispatcher.scheduler.runCurrent()
        assertEquals(3, (vm.faces.value["b"] as CardFaces.Ready).count)
    }

    @Test
    fun aFailedReading_showsNothingAlarming_andWaitsBeforeTryingAgain() {
        reader.answer = { FacesRead.Failed }
        val vm = viewModel()
        vm.onCardShown("b")
        dispatcher.scheduler.runCurrent()
        assertEquals(CardFaces.Unavailable, vm.faces.value["b"])
        vm.onCardShown("b")
        dispatcher.scheduler.runCurrent()
        assertEquals("never a loop", 1, reader.reads.size)
        now += MiffFacesBook.RETRY_MILLIS
        reader.answer = { FacesRead.Loaded(members(2)) }
        vm.onCardShown("b")
        dispatcher.scheduler.runCurrent()
        assertEquals(2, reader.reads.size)
        assertEquals(2, (vm.faces.value["b"] as CardFaces.Ready).count)
    }

    @Test
    fun openingAMiff_readsItsFacesAgain_atTheNextAppearanceOfItsCard() {
        val vm = viewModel()
        feed.value = ReadMembers("a", members(4))
        dispatcher.scheduler.runCurrent()
        vm.onMiffOpened("a")
        reader.answer = { FacesRead.Loaded(members(3)) }
        vm.onCardShown("a")
        dispatcher.scheduler.runCurrent()
        assertEquals(listOf("a"), reader.reads)
        assertEquals(3, (vm.faces.value["a"] as CardFaces.Ready).count)
    }

    @Test
    fun aMiffThatLeftTheList_isForgotten() {
        val vm = viewModel()
        vm.onCardShown("b")
        dispatcher.scheduler.runCurrent()
        vm.retainOnly(setOf("a"))
        assertTrue(vm.faces.value.isEmpty())
    }
}
