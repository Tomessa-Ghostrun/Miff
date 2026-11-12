package com.tomessa.miff.ui.profile

import com.tomessa.miff.data.place.MineRead
import com.tomessa.miff.data.place.RequestWithdrawResult
import com.tomessa.miff.domain.miff.MiffFailure
import com.tomessa.miff.domain.place.PlaceRequestBook
import com.tomessa.miff.domain.place.RequestsView
import com.tomessa.miff.testsupport.FakeNetwork
import com.tomessa.miff.testsupport.FakePlaceRequestRepository
import com.tomessa.miff.testsupport.MIFF_ID
import com.tomessa.miff.testsupport.OTHER_MIFF_ID
import com.tomessa.miff.testsupport.testRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
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

/** The pending requests of a member under the list of places (ticket LIEU-REQ-B): what is read, when, and what taking a request back does. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileRequestsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repo = FakePlaceRequestRepository()
    private val network = FakeNetwork()
    private var now = 1_000_000L

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(uid: String? = "u2") = ProfileRequestsViewModel(repo, network, { uid }, nowMillis = { now })

    private fun collectEvents(
        vm: ProfileRequestsViewModel,
        into: MutableList<RequestEvent>,
    ) {
        CoroutineScope(dispatcher).launch { vm.events.collect { into += it } }
    }

    private fun ProfileRequestsViewModel.ids(miffId: String = MIFF_ID): List<String> =
        ((state.value.views[miffId]) as? RequestsView.Ready)?.requests?.map { it.id }.orEmpty()

    @Test
    fun aMember_whenTheCardAppears_readsItsOwnRequests_once() {
        repo.mineResult = MineRead.Loaded(listOf(testRequest(slot = 1, createdAtMillis = now)))
        val vm = viewModel()
        vm.onCardShown(MIFF_ID, creator = false)
        dispatcher.scheduler.runCurrent()
        assertEquals(listOf("u2_1"), vm.ids())
        // The same card again, within five minutes: nothing is read.
        vm.onCardShown(MIFF_ID, creator = false)
        dispatcher.scheduler.runCurrent()
        assertEquals(1, repo.readMineCalls)
        now += PlaceRequestBook.TTL_MILLIS
        vm.onCardShown(MIFF_ID, creator = false)
        dispatcher.scheduler.runCurrent()
        assertEquals(2, repo.readMineCalls)
    }

    @Test
    fun theCreator_readsNothing_here_andOthersMiffsAreNotMixedUp() {
        val vm = viewModel()
        vm.onCardShown(MIFF_ID, creator = true)
        dispatcher.scheduler.runCurrent()
        assertEquals(0, repo.readMineCalls)
        assertEquals(MIFF_ID, vm.state.value.selectedMiffId)
    }

    @Test
    fun nothingIsDrawnWhileItIsBeingRead_andAFailureDrawsNoAlarm() {
        repo.mineResult = MineRead.Failed(MiffFailure.OFFLINE)
        val vm = viewModel()
        vm.onCardShown(MIFF_ID, creator = false)
        assertTrue(vm.state.value.selected is RequestsView.Loading)
        dispatcher.scheduler.runCurrent()
        assertTrue(vm.state.value.selected is RequestsView.Unavailable)
        // Never a loop: the same card again right away reads nothing more.
        vm.onCardShown(MIFF_ID, creator = false)
        dispatcher.scheduler.runCurrent()
        assertEquals(1, repo.readMineCalls)
    }

    @Test
    fun noSignedInPerson_readsNothing() {
        val vm = viewModel(uid = null)
        vm.onCardShown(MIFF_ID, creator = false)
        dispatcher.scheduler.runCurrent()
        assertEquals(0, repo.readMineCalls)
    }

    @Test
    fun openingTheForm_makesTheListReadAgain_whenTheCardComesBack() {
        val vm = viewModel()
        vm.onCardShown(MIFF_ID, creator = false)
        dispatcher.scheduler.runCurrent()
        vm.onRequestsMayChange()
        vm.onCardShown(MIFF_ID, creator = false)
        dispatcher.scheduler.runCurrent()
        assertEquals(2, repo.readMineCalls)
    }

    @Test
    fun anExpiredRequest_isNeverInTheList() {
        now = 0L
        repo.mineResult =
            MineRead.Loaded(
                listOf(testRequest(slot = 1, createdAtMillis = 0L), testRequest(slot = 2, createdAtMillis = 0L)),
            )
        val vm = viewModel()
        vm.onCardShown(MIFF_ID, creator = false)
        dispatcher.scheduler.runCurrent()
        assertEquals(2, vm.ids().size)
        // 31 days later the two requests are no longer drawn (they are deleted by the app that meets them, never shown meanwhile).
        now += 31L * 24 * 60 * 60 * 1000
        vm.onCardShown(MIFF_ID, creator = false)
        assertEquals(0, vm.ids().size)
    }

    @Test
    fun takingBackARequest_removesItAtOnce_andSaysItOnce() {
        val mine = testRequest(slot = 1, createdAtMillis = now)
        repo.mineResult = MineRead.Loaded(listOf(mine, testRequest(slot = 2, createdAtMillis = now)))
        val vm = viewModel()
        vm.onCardShown(MIFF_ID, creator = false)
        dispatcher.scheduler.runCurrent()
        val events = mutableListOf<RequestEvent>()
        collectEvents(vm, events)
        dispatcher.scheduler.runCurrent()
        vm.onWithdraw(mine)
        dispatcher.scheduler.runCurrent()
        assertEquals(listOf("u2_2"), vm.ids())
        assertEquals(listOf(mine), repo.withdrawn)
        assertEquals(listOf<RequestEvent>(RequestEvent.Withdrawn), events)
        assertNull(vm.state.value.busyRequestId)
    }

    @Test
    fun takingBackARequest_withNoNetwork_changesNothing_andSaysSoCalmly() {
        val mine = testRequest(slot = 1, createdAtMillis = now)
        repo.mineResult = MineRead.Loaded(listOf(mine))
        val vm = viewModel()
        vm.onCardShown(MIFF_ID, creator = false)
        dispatcher.scheduler.runCurrent()
        network.online = false
        val events = mutableListOf<RequestEvent>()
        collectEvents(vm, events)
        dispatcher.scheduler.runCurrent()
        vm.onWithdraw(mine)
        dispatcher.scheduler.runCurrent()
        assertEquals(listOf("u2_1"), vm.ids())
        assertTrue(repo.withdrawn.isEmpty())
        assertEquals(listOf<RequestEvent>(RequestEvent.Failed(offline = true)), events)
    }

    @Test
    fun takingBackARequest_thatFails_keepsIt_andASecondTapWhileItIsOnItsWayDoesNothing() {
        val mine = testRequest(slot = 1, createdAtMillis = now)
        repo.mineResult = MineRead.Loaded(listOf(mine))
        val vm = viewModel()
        vm.onCardShown(MIFF_ID, creator = false)
        dispatcher.scheduler.runCurrent()
        repo.gate = CompletableDeferred()
        repo.withdrawResult = RequestWithdrawResult.Failed(MiffFailure.UNKNOWN)
        vm.onWithdraw(mine)
        dispatcher.scheduler.runCurrent()
        assertEquals(mine.id, vm.state.value.busyRequestId)
        vm.onWithdraw(mine)
        repo.gate!!.complete(Unit)
        dispatcher.scheduler.runCurrent()
        assertEquals(1, repo.withdrawn.size)
        assertEquals(listOf("u2_1"), vm.ids())
        assertNull(vm.state.value.busyRequestId)
    }

    @Test
    fun aMiffThePersonLeft_takesItsRequestsWithIt() {
        repo.mineResult = MineRead.Loaded(listOf(testRequest(slot = 1, createdAtMillis = now)))
        val vm = viewModel()
        vm.onCardShown(MIFF_ID, creator = false)
        dispatcher.scheduler.runCurrent()
        vm.retainOnly(setOf(OTHER_MIFF_ID))
        assertNull(vm.state.value.selectedMiffId)
        assertTrue(
            vm.state.value.views
                .isEmpty(),
        )
    }

    @Test
    fun toString_showsNothing() {
        assertEquals("ProfileRequestsState(redacted)", ProfileRequestsState().toString())
    }
}
