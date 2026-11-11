package com.tomessa.miff.ui.place

import com.tomessa.miff.data.place.PlaceNotices
import com.tomessa.miff.data.place.RequestContext
import com.tomessa.miff.data.place.RequestContextLoad
import com.tomessa.miff.data.place.RequestSubmitResult
import com.tomessa.miff.domain.miff.MiffFailure
import com.tomessa.miff.domain.place.PaywallReason
import com.tomessa.miff.domain.place.PlaceType
import com.tomessa.miff.testsupport.FakeAddressSearch
import com.tomessa.miff.testsupport.FakeNetwork
import com.tomessa.miff.testsupport.FakePlaceRepository
import com.tomessa.miff.testsupport.FakePlaceRequestRepository
import com.tomessa.miff.testsupport.MIFF_ID
import com.tomessa.miff.testsupport.testRequest
import com.tomessa.miff.testsupport.testSuggestion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The form of a REQUEST to add a place (ticket LIEU-REQ-B, criteria M3 to M5, M7, M8, M11): the same form as the creation, another button, a Miff that is fixed,
 * the limits of a member applied before anything is sent. No Firestore, no network: doubles.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaceRequestFormViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val places = FakePlaceRepository()
    private val requests = FakePlaceRequestRepository()
    private val search = FakeAddressSearch()
    private val network = FakeNetwork()
    private val notices = PlaceNotices()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun loaded(
        premium: Boolean = false,
        mine: Int = 0,
    ) = RequestContextLoad.Loaded(
        RequestContext("Famille", premium, (1..mine).map { testRequest(slot = it, createdAtMillis = 1L) }),
    )

    private fun viewModel(load: RequestContextLoad = loaded()): PlaceFormViewModel {
        requests.context = load
        val viewModel =
            PlaceFormViewModel(
                placeId = null,
                miffId = MIFF_ID,
                repository = places,
                search = search,
                network = network,
                currentUid = { "u2" },
                notices = notices,
                language = { "fr" },
                request = true,
                requests = requests,
            )
        dispatcher.scheduler.runCurrent()
        return viewModel
    }

    private fun PlaceFormViewModel.fill(name: String = "Chez Mamie") {
        onNameChanged(name)
        onSuggestionChosen(testSuggestion())
    }

    // --- Opening (M3) -----------------------------------------------------------------------------------------------------------

    @Test
    fun opening_isTheRequestMode_withTheMiffFixed_andNothingReadOfThePlacesOfTheCreator() {
        val vm = viewModel()
        val state = vm.uiState.value
        assertEquals(PlaceFormMode.REQUEST, state.mode)
        assertFalse(state.loading)
        assertEquals(listOf(MIFF_ID), state.selectedMiffIds)
        // ONE chip, no picker.
        assertTrue(state.singleMiff)
        assertEquals("Famille", state.owned.single().name)
        assertEquals(1, requests.prepareCalls)
        assertEquals(0, places.prepareCalls)
        assertEquals(PlaceType.TRUSTED, state.type)
    }

    @Test
    fun opening_theCreatorOfTheMiff_orAMiffThatIsGone_doesNotOpenTheForm() {
        val vm = viewModel(RequestContextLoad.Unavailable)
        assertNotNull(vm.uiState.value.loadFailed)
        assertFalse(vm.uiState.value.canSave)
    }

    @Test
    fun opening_withNoNetwork_saysSoCalmly() {
        network.online = false
        val vm = viewModel()
        assertEquals(PlaceMessage.OFFLINE, vm.uiState.value.loadFailed)
        assertEquals(0, requests.prepareCalls)
    }

    @Test
    fun opening_withThreePendingRequests_saysItAtOnce_andSendsNothing() {
        val vm = viewModel(loaded(mine = 3))
        vm.fill()
        val state = vm.uiState.value
        assertEquals(PlaceMessage.REQUEST_LIMIT_MEMBER, state.displayedMessage)
        assertTrue(state.requestBlocked)
        assertFalse(state.canSave)
        vm.onSave()
        dispatcher.scheduler.runCurrent()
        assertTrue(requests.submitted.isEmpty())
    }

    // --- Sending (M4, M11) ------------------------------------------------------------------------------------------------------

    @Test
    fun saving_isActiveFromANameAndAnAddress_noCategoryNeeded() {
        val vm = viewModel()
        assertFalse(vm.uiState.value.canSave)
        vm.fill()
        assertTrue(vm.uiState.value.canSave)
    }

    @Test
    fun submitting_sendsTheDraft_closesTheForm_andLeavesACalmNoticeOnTheList() {
        val vm = viewModel()
        vm.fill("  Chez Mamie ")
        vm.onSave()
        dispatcher.scheduler.runCurrent()
        assertEquals(PlaceFormEnd.SAVED, vm.uiState.value.end)
        val draft = requests.submitted.single()
        assertEquals(PlaceType.TRUSTED, draft.type)
        assertEquals(100, draft.radiusM)
        assertEquals(listOf(true), requests.submittedOnline)
        assertFalse(notices.requestNotice.value!!.queued)
        // No place was created, whatever happens.
        assertTrue(places.created.isEmpty())
    }

    @Test
    fun submitting_whenTheNetworkDropsAfterTheAddress_isKeptForLater_andSaysSo() {
        val vm = viewModel()
        vm.fill()
        network.online = false
        requests.submitResult = RequestSubmitResult.Queued
        vm.onSave()
        dispatcher.scheduler.runCurrent()
        assertEquals(listOf(false), requests.submittedOnline)
        assertTrue(notices.requestNotice.value!!.queued)
        assertEquals(PlaceFormEnd.SAVED, vm.uiState.value.end)
    }

    @Test
    fun submitting_aSecondTapWhileItIsOnItsWay_doesNothing() {
        val vm = viewModel()
        vm.fill()
        requests.gate = kotlinx.coroutines.CompletableDeferred()
        vm.onSave()
        dispatcher.scheduler.runCurrent()
        assertTrue(vm.uiState.value.saving)
        vm.onSave()
        requests.gate!!.complete(Unit)
        dispatcher.scheduler.runCurrent()
        assertEquals(1, requests.submitted.size)
    }

    // --- The limits (M5, M7, M8) ------------------------------------------------------------------------------------------------

    @Test
    fun theDangerZone_isLockedForAMemberWithoutMiffPlus_andOpensTheOffer() {
        val vm = viewModel(loaded(premium = false))
        vm.onTypeSelected(PlaceType.RISK)
        assertEquals(PaywallReason.DANGER, vm.uiState.value.paywall)
        assertEquals(PlaceType.TRUSTED, vm.uiState.value.type)
    }

    @Test
    fun theDangerZone_canBeChosenByAMiffPlusMember_andIsSent() {
        val vm = viewModel(loaded(premium = true))
        vm.onTypeSelected(PlaceType.RISK)
        assertNull(vm.uiState.value.paywall)
        assertEquals(PlaceType.RISK, vm.uiState.value.type)
        vm.fill()
        vm.onSave()
        dispatcher.scheduler.runCurrent()
        assertEquals(PlaceType.RISK, requests.submitted.single().type)
    }

    @Test
    fun theLimitOfThree_foundWhenSending_isSaidCalmly_notAnError() {
        val vm = viewModel()
        vm.fill()
        requests.submitResult = RequestSubmitResult.MemberLimit
        vm.onSave()
        dispatcher.scheduler.runCurrent()
        assertEquals(PlaceMessage.REQUEST_LIMIT_MEMBER, vm.uiState.value.message)
        assertTrue(vm.uiState.value.requestBlocked)
        assertNull(vm.uiState.value.end)
    }

    @Test
    fun theLimitOfTheMiff_isSaidCalmly_andTheFormStaysOpen() {
        val vm = viewModel()
        vm.fill()
        requests.submitResult = RequestSubmitResult.MiffLimit
        vm.onSave()
        dispatcher.scheduler.runCurrent()
        assertEquals(PlaceMessage.REQUEST_LIMIT_MIFF, vm.uiState.value.message)
        assertFalse(vm.uiState.value.saving)
        assertNull(vm.uiState.value.end)
    }

    @Test
    fun aFailure_isTheGenericCalmSentence_orTheOfflineOne() {
        val vm = viewModel()
        vm.fill()
        requests.submitResult = RequestSubmitResult.Failed(MiffFailure.OFFLINE)
        vm.onSave()
        dispatcher.scheduler.runCurrent()
        assertEquals(PlaceMessage.OFFLINE, vm.uiState.value.message)
        requests.submitResult = RequestSubmitResult.Failed(MiffFailure.UNKNOWN)
        vm.onSave()
        dispatcher.scheduler.runCurrent()
        assertEquals(PlaceMessage.GENERIC, vm.uiState.value.message)
    }

    @Test
    fun aMiffThatIsGone_isSaid_notAnInventedLimit() {
        val vm = viewModel()
        vm.fill()
        requests.submitResult = RequestSubmitResult.MiffUnavailable
        vm.onSave()
        dispatcher.scheduler.runCurrent()
        assertEquals(PlaceMessage.MIFF_GONE, vm.uiState.value.message)
    }
}
