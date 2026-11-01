package com.tomessa.miff.ui.place

import com.tomessa.miff.data.place.PlaceContext
import com.tomessa.miff.data.place.PlaceNotices
import com.tomessa.miff.data.place.PlaceWriteResult
import com.tomessa.miff.domain.miff.MiffFailure
import com.tomessa.miff.domain.place.AddressSearchResult
import com.tomessa.miff.domain.place.OwnedMiff
import com.tomessa.miff.domain.place.PaywallReason
import com.tomessa.miff.domain.place.PlaceCategory
import com.tomessa.miff.domain.place.PlaceType
import com.tomessa.miff.testsupport.FakeAddressSearch
import com.tomessa.miff.testsupport.FakeNetwork
import com.tomessa.miff.testsupport.FakePlaceRepository
import com.tomessa.miff.testsupport.MIFF_ID
import com.tomessa.miff.testsupport.OTHER_MIFF_ID
import com.tomessa.miff.testsupport.testPlace
import com.tomessa.miff.testsupport.testSuggestion
import com.tomessa.miff.ui.miff.ActionMessage
import kotlinx.coroutines.CompletableDeferred
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

/** The form of a place (ticket LOC-1, 6.1 to 6.4; criteria PL1 to PL9, MIF1, ADR1, HOR2). No Firestore, no network: doubles. */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaceFormViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repo = FakePlaceRepository()
    private val search = FakeAddressSearch()
    private val network = FakeNetwork()
    private val notices = PlaceNotices()

    private val famille = OwnedMiff(MIFF_ID, "Famille", joinedAtMillis = 100)
    private val tuches = OwnedMiff(OTHER_MIFF_ID, "Les tuches", joinedAtMillis = 200)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun context(
        premium: Boolean = false,
        owned: List<OwnedMiff> = listOf(famille),
        lastJoined: String? = null,
        places: List<com.tomessa.miff.domain.place.Place> = emptyList(),
    ) = PlaceContext(premium, owned, lastJoined, places)

    private fun viewModel(
        placeId: String? = null,
        context: PlaceContext = context(),
        uid: String? = "uid-1",
    ): PlaceFormViewModel {
        repo.context = context
        val viewModel =
            PlaceFormViewModel(
                placeId = placeId,
                repository = repo,
                search = search,
                network = network,
                currentUid = { uid },
                notices = notices,
                language = { "fr" },
            )
        dispatcher.scheduler.runCurrent()
        return viewModel
    }

    private fun PlaceFormViewModel.state() = uiState.value

    /** A form filled up to the point where "Enregistrer" is active. */
    private fun PlaceFormViewModel.fill(name: String = "Maison") {
        onNameChanged(name)
        onSuggestionChosen(testSuggestion())
    }

    // --- Opening ----------------------------------------------------------------------------------------------------------

    @Test
    fun opening_defaultsAreTheTicketOnes() {
        val vm = viewModel()
        val state = vm.state()
        assertFalse(state.loading)
        assertEquals(100, state.radiusM)
        assertEquals(PlaceType.TRUSTED, state.type)
        assertEquals(PlaceCategory.HOME, state.category)
        assertEquals(listOf(MIFF_ID), state.selectedMiffIds)
        assertFalse(state.canSave)
    }

    @Test
    fun opening_theMiffTickedIsTheLastJoinedOne_ifTheyCreatedIt() {
        val vm = viewModel(context = context(owned = listOf(famille, tuches), lastJoined = OTHER_MIFF_ID))
        assertEquals(listOf(OTHER_MIFF_ID), vm.state().selectedMiffIds)
        val other = viewModel(context = context(owned = listOf(famille, tuches), lastJoined = "someone-elses"))
        assertEquals(listOf(MIFF_ID), other.state().selectedMiffIds)
    }

    @Test
    fun opening_withASingleMiff_hasNoPicker() {
        val vm = viewModel()
        assertTrue(vm.state().singleMiff)
        vm.onMiffSheetOpened()
        assertFalse(vm.state().miffSheetOpen)
    }

    @Test
    fun opening_offline_saysSo_andRetryReadsAgain() {
        network.online = false
        val vm = viewModel()
        assertEquals(PlaceMessage.OFFLINE, vm.state().loadFailed)
        assertEquals(0, repo.prepareCalls)
        network.online = true
        vm.retry()
        dispatcher.scheduler.runCurrent()
        assertNull(vm.state().loadFailed)
        assertEquals(1, repo.prepareCalls)
    }

    @Test
    fun opening_aReadThatFails_isACalmMessage() {
        repo.prepareFailure = MiffFailure.REFUSED
        val vm = viewModel()
        assertEquals(PlaceMessage.GENERIC, vm.state().loadFailed)
    }

    @Test
    fun opening_withTwoPlacesOnTheFreePlan_opensTheOfferInsteadOfTheForm() {
        val places = listOf(testPlace("p1", "A", MIFF_ID), testPlace("p2", "B", MIFF_ID))
        val vm = viewModel(context = context(places = places))
        assertEquals(PaywallReason.LIMIT, vm.state().paywall)
    }

    // --- The button (PL2) -------------------------------------------------------------------------------------------------------

    @Test
    fun button_needsOneCharacterOfNameAndAnAddressChosen() {
        val vm = viewModel()
        vm.onNameChanged("M")
        assertFalse(vm.state().canSave)
        vm.onSuggestionChosen(testSuggestion())
        assertTrue(vm.state().canSave)
        vm.onNameChanged("")
        assertFalse(vm.state().canSave)
    }

    @Test
    fun button_oneCharacter_saysTwoCharactersMinimum_andWritesNothing() {
        val vm = viewModel()
        vm.fill("M")
        vm.onSave()
        dispatcher.scheduler.runCurrent()
        assertTrue(vm.state().nameTooShort)
        assertTrue(repo.created.isEmpty())
        // Typing again clears the sentence.
        vm.onNameChanged("Ma")
        assertFalse(vm.state().nameTooShort)
    }

    @Test
    fun name_isCutAtFifty() {
        val vm = viewModel()
        vm.onNameChanged("a".repeat(70))
        assertEquals(50, vm.state().name.length)
        assertTrue(vm.state().nameLimitReached)
    }

    // --- The address (ADR1) -----------------------------------------------------------------------------------------------------

    @Test
    fun address_nothingBeforeThreeCharacters_thenOneSearchAfterThePause() {
        val vm = viewModel()
        vm.onAddressChanged("17")
        dispatcher.scheduler.advanceTimeBy(1_000)
        dispatcher.scheduler.runCurrent()
        assertTrue(search.queries.isEmpty())
        assertEquals(SuggestionsState.Idle, vm.state().suggestions)
        vm.onAddressChanged("17 a")
        assertEquals(SuggestionsState.Loading, vm.state().suggestions)
        dispatcher.scheduler.advanceTimeBy(299)
        dispatcher.scheduler.runCurrent()
        assertTrue(search.queries.isEmpty())
        dispatcher.scheduler.advanceTimeBy(2)
        dispatcher.scheduler.runCurrent()
        assertEquals(listOf("17 a"), search.queries)
        assertTrue(vm.state().suggestions is SuggestionsState.Found)
    }

    @Test
    fun address_aNewLetterCancelsThePreviousRequest() {
        val vm = viewModel()
        search.delayMillis = 500
        vm.onAddressChanged("17 av")
        dispatcher.scheduler.advanceTimeBy(350)
        dispatcher.scheduler.runCurrent()
        assertEquals(listOf("17 av"), search.queries) // on its way
        vm.onAddressChanged("17 ave")
        dispatcher.scheduler.advanceTimeBy(5_000)
        dispatcher.scheduler.runCurrent()
        // Only the last answer is kept; the first one never reached the screen.
        assertEquals(listOf("17 av", "17 ave"), search.queries)
        assertTrue(vm.state().suggestions is SuggestionsState.Found)
    }

    @Test
    fun address_choosing_fixesTheAddress_andStopsTheSearch() {
        val vm = viewModel()
        vm.onAddressChanged("17 avenue")
        dispatcher.scheduler.advanceTimeBy(400)
        dispatcher.scheduler.runCurrent()
        val chosen = (vm.state().suggestions as SuggestionsState.Found).items.first()
        vm.onSuggestionChosen(chosen)
        assertEquals(chosen.label, vm.state().addressText)
        assertNotNull(vm.state().chosen)
        assertEquals(SuggestionsState.Idle, vm.state().suggestions)
        // The text already chosen is never searched again.
        vm.onAddressChanged(chosen.label)
        dispatcher.scheduler.advanceTimeBy(1_000)
        dispatcher.scheduler.runCurrent()
        assertEquals(1, search.queries.size)
    }

    @Test
    fun address_typingAfterTheChoice_eraseTheChoice_andTheButtonSleepsAgain() {
        val vm = viewModel()
        vm.fill()
        assertTrue(vm.state().canSave)
        vm.onAddressChanged(vm.state().addressText + "x")
        assertNull(vm.state().chosen)
        assertFalse(vm.state().canSave)
    }

    @Test
    fun address_noResult_andUnavailable_areCalmStates_thatCanBeRetried() {
        val vm = viewModel()
        search.answer = AddressSearchResult.NoResults
        vm.onAddressChanged("zzzzzz")
        dispatcher.scheduler.advanceTimeBy(400)
        dispatcher.scheduler.runCurrent()
        assertEquals(SuggestionsState.NoResults, vm.state().suggestions)
        search.answer = AddressSearchResult.Unavailable
        vm.onAddressChanged("zzzzzzz")
        dispatcher.scheduler.advanceTimeBy(400)
        dispatcher.scheduler.runCurrent()
        assertEquals(SuggestionsState.Unavailable, vm.state().suggestions)
        search.answer = AddressSearchResult.Found(listOf(testSuggestion()))
        vm.onRetrySearch()
        dispatcher.scheduler.advanceTimeBy(400)
        dispatcher.scheduler.runCurrent()
        assertTrue(vm.state().suggestions is SuggestionsState.Found)
    }

    @Test
    fun address_offline_isUnavailable_withoutAskingTheService() {
        val vm = viewModel()
        network.online = false
        vm.onAddressChanged("17 avenue")
        dispatcher.scheduler.advanceTimeBy(400)
        dispatcher.scheduler.runCurrent()
        assertTrue(search.queries.isEmpty())
        assertEquals(SuggestionsState.Unavailable, vm.state().suggestions)
    }

    @Test
    fun address_theCrossStartsAgain() {
        val vm = viewModel()
        vm.fill()
        vm.onAddressCleared()
        assertEquals("", vm.state().addressText)
        assertNull(vm.state().chosen)
    }

    // --- Radius, type, category ------------------------------------------------------------------------------------------------

    @Test
    fun radius_snapsToTheSteps_andWaitsForTheAddress() {
        val vm = viewModel()
        vm.onRadiusChanged(300f)
        assertEquals(100, vm.state().radiusM) // no address chosen yet: the slider is asleep
        vm.fill()
        vm.onRadiusChanged(337f)
        assertEquals(350, vm.state().radiusM)
        vm.onRadiusChanged(10f)
        assertEquals(50, vm.state().radiusM)
    }

    @Test
    fun type_free_dangerZone_opensTheOffer_andTheChoiceStaysTrusted() {
        val vm = viewModel()
        vm.onTypeSelected(PlaceType.RISK)
        assertEquals(PaywallReason.DANGER, vm.state().paywall)
        assertEquals(PlaceType.TRUSTED, vm.state().type)
        vm.onPaywallHandled()
        assertNull(vm.state().paywall)
    }

    @Test
    fun type_premium_canChooseDanger_andComeBack() {
        val vm = viewModel(context = context(premium = true))
        vm.onTypeSelected(PlaceType.RISK)
        assertEquals(PlaceType.RISK, vm.state().type)
        assertNull(vm.state().paywall)
        vm.onTypeSelected(PlaceType.TRUSTED)
        assertEquals(PlaceType.TRUSTED, vm.state().type)
    }

    @Test
    fun category_isAlwaysOneOfTheSeven_homePreselected() {
        val vm = viewModel()
        assertEquals(PlaceCategory.HOME, vm.state().category)
        vm.onCategorySelected(PlaceCategory.WORK)
        assertEquals(PlaceCategory.WORK, vm.state().category)
    }

    // --- Miffs (MIF1) -----------------------------------------------------------------------------------------------------------

    private fun fiveAndOne(): PlaceFormViewModel {
        val owned = (1..6).map { OwnedMiff("MIFFID%014d".format(it), "Miff $it", it.toLong()) }
        return viewModel(context = context(premium = true, owned = owned))
    }

    @Test
    fun miffs_severalCanBeTicked_fiveAtMost_andNotUntickedByAMistake() {
        val vm = fiveAndOne()
        val ids = vm.state().owned.map { it.id }
        ids.drop(1).take(4).forEach { vm.onMiffToggled(it) }
        assertEquals(5, vm.state().selectedMiffIds.size)
        vm.onMiffToggled(ids[5]) // a sixth one: nothing happens
        assertEquals(5, vm.state().selectedMiffIds.size)
        assertFalse(ids[5] in vm.state().selectedMiffIds)
        vm.onMiffToggled(ids[1])
        assertEquals(4, vm.state().selectedMiffIds.size)
    }

    @Test
    fun miffs_noneTicked_meansNoSaving() {
        val vm = viewModel(context = context(owned = listOf(famille, tuches)))
        vm.fill()
        assertTrue(vm.state().canSave)
        vm.onMiffToggled(MIFF_ID)
        assertTrue(vm.state().selectedMiffIds.isEmpty())
        assertFalse(vm.state().canSave)
    }

    @Test
    fun miffs_areKeptInTheOrderOfTheList() {
        val vm = viewModel(context = context(owned = listOf(famille, tuches), lastJoined = OTHER_MIFF_ID))
        vm.onMiffToggled(MIFF_ID)
        assertEquals(listOf(MIFF_ID, OTHER_MIFF_ID), vm.state().selectedMiffIds)
    }

    // --- Saving (PL9, HOR2) -------------------------------------------------------------------------------------------------------

    @Test
    fun save_writesWhatWasFilledIn_andOnlyThenSaysSo() {
        val vm = viewModel(context = context(owned = listOf(famille, tuches)))
        vm.fill("  Maison de Papa ")
        vm.onCategorySelected(PlaceCategory.RELATIVE)
        vm.onRadiusChanged(250f)
        vm.onMiffToggled(OTHER_MIFF_ID)
        val gate = CompletableDeferred<Unit>()
        repo.gate = gate
        vm.onSave()
        dispatcher.scheduler.runCurrent()
        // On its way: nothing is shown as saved, nothing can be pressed.
        assertTrue(vm.state().saving)
        assertNull(vm.state().end)
        assertTrue(notices.notice.value == null)
        gate.complete(Unit)
        dispatcher.scheduler.runCurrent()
        val draft = repo.created.single()
        assertEquals("Maison de Papa", draft.name.trim())
        assertEquals(listOf(MIFF_ID, OTHER_MIFF_ID), draft.miffIds)
        assertEquals(250, draft.radiusM)
        assertEquals(PlaceCategory.RELATIVE, draft.category)
        assertEquals(PlaceType.TRUSTED, draft.type)
        assertEquals(PlaceFormEnd.SAVED, vm.state().end)
        assertEquals("Maison de Papa", notices.notice.value?.name)
    }

    @Test
    fun save_twoTaps_oneWrite() {
        val vm = viewModel()
        vm.fill()
        repo.gate = CompletableDeferred()
        vm.onSave()
        vm.onSave()
        dispatcher.scheduler.runCurrent()
        vm.onSave()
        dispatcher.scheduler.runCurrent()
        assertEquals(1, repo.created.size)
    }

    @Test
    fun save_offline_saysSo_writesNothing_andQueuesNothing() {
        val vm = viewModel()
        vm.fill()
        network.online = false
        vm.onSave()
        dispatcher.scheduler.runCurrent()
        assertEquals(PlaceMessage.OFFLINE, vm.state().message)
        assertTrue(repo.created.isEmpty())
        assertFalse(vm.state().saving)
    }

    @Test
    fun save_theServerSaysMiffPlusIsNeeded_opensTheOffer() {
        val vm = viewModel()
        vm.fill()
        repo.createResult = PlaceWriteResult.NeedsPremium(PaywallReason.LIMIT)
        vm.onSave()
        dispatcher.scheduler.runCurrent()
        assertEquals(PaywallReason.LIMIT, vm.state().paywall)
        assertNull(vm.state().end)
        assertFalse(vm.state().saving)
    }

    @Test
    fun save_aMiffIsGone_andAFailure_areCalmMessages_andTheButtonStaysUsable() {
        val vm = viewModel()
        vm.fill()
        repo.createResult = PlaceWriteResult.MiffUnavailable
        vm.onSave()
        dispatcher.scheduler.runCurrent()
        assertEquals(PlaceMessage.MIFF_GONE, vm.state().message)
        assertTrue(vm.state().canSave)
        repo.createResult = PlaceWriteResult.Failed(MiffFailure.OFFLINE)
        vm.onSave()
        dispatcher.scheduler.runCurrent()
        assertEquals(PlaceMessage.OFFLINE, vm.state().message)
        repo.createResult = PlaceWriteResult.Failed(MiffFailure.REFUSED)
        vm.onSave()
        dispatcher.scheduler.runCurrent()
        assertEquals(PlaceMessage.GENERIC, vm.state().message)
        assertNull(vm.state().end)
    }

    // --- Editing (PL7) ----------------------------------------------------------------------------------------------------------

    private val existing =
        testPlace(
            "p1",
            "Maison de Papa",
            MIFF_ID,
            category = PlaceCategory.HOME,
            address = "26 rue du Volnay, 21000 Dijon",
        )

    private fun editing(
        premium: Boolean = false,
        places: List<com.tomessa.miff.domain.place.Place> = listOf(existing),
    ) = viewModel(
        placeId = "p1",
        context = context(premium = premium, owned = listOf(famille, tuches), places = places),
    )

    @Test
    fun edit_opensFilled_withTheButtonAsleepUntilSomethingChanges() {
        val vm = editing()
        val state = vm.state()
        assertEquals(PlaceFormMode.EDIT, state.mode)
        assertEquals("Maison de Papa", state.name)
        assertEquals("26 rue du Volnay, 21000 Dijon", state.addressText)
        assertNotNull(state.chosen)
        assertEquals(listOf(MIFF_ID), state.selectedMiffIds)
        assertFalse(state.canSave)
        vm.onNameChanged("Chez Papa")
        assertTrue(vm.state().canSave)
        vm.onNameChanged("Maison de Papa")
        assertFalse(vm.state().canSave)
    }

    @Test
    fun edit_everythingCanChange_theMiffsToo() {
        val vm = editing(premium = true)
        vm.onMiffToggled(OTHER_MIFF_ID)
        assertTrue(vm.state().canSave)
        vm.onTypeSelected(PlaceType.RISK)
        vm.onCategorySelected(PlaceCategory.SCHOOL)
        vm.onSave()
        dispatcher.scheduler.runCurrent()
        val draft = repo.updated.single()
        assertEquals(PlaceType.RISK, draft.type)
        assertEquals(PlaceCategory.SCHOOL, draft.category)
        assertEquals(listOf(MIFF_ID, OTHER_MIFF_ID), draft.miffIds)
        assertTrue(repo.created.isEmpty())
    }

    @Test
    fun edit_aPlaceWithoutCategory_mustGetOneBeforeSaving() {
        val vm = editing(places = listOf(testPlace("p1", "Vieille", MIFF_ID, category = null)))
        assertNull(vm.state().category)
        vm.onNameChanged("Vieille maison")
        assertFalse(vm.state().canSave)
        vm.onCategorySelected(PlaceCategory.HOME)
        assertTrue(vm.state().canSave)
    }

    @Test
    fun edit_aPlaceThatDoesNotExistAnyMore_endsTheForm() {
        val vm = viewModel(placeId = "gone", context = context(places = listOf(existing)))
        assertEquals(PlaceFormEnd.PLACE_GONE, vm.state().end)
    }

    @Test
    fun edit_aFreeAccount_cannotMakeItADangerZone() {
        val vm = editing()
        vm.onTypeSelected(PlaceType.RISK)
        assertEquals(PaywallReason.DANGER, vm.state().paywall)
        assertEquals(PlaceType.TRUSTED, vm.state().type)
    }

    // --- Deleting (PL8) ----------------------------------------------------------------------------------------------------------

    @Test
    fun delete_asksFirst_thenDeletesEverywhere() {
        val vm =
            viewModel(
                placeId = "p1",
                context =
                    context(
                        owned = listOf(famille, tuches),
                        places = listOf(existing, testPlace("p1", "Maison de Papa", OTHER_MIFF_ID)),
                    ),
            )
        vm.onDeleteRequested()
        assertTrue(vm.state().deleteDialogOpen)
        assertTrue(repo.deleted.isEmpty())
        vm.onDeleteConfirmed()
        dispatcher.scheduler.runCurrent()
        assertEquals(listOf("p1" to listOf(MIFF_ID, OTHER_MIFF_ID)), repo.deleted)
        assertEquals(PlaceFormEnd.DELETED, vm.state().end)
    }

    @Test
    fun delete_cancel_keepsEverything() {
        val vm = editing()
        vm.onDeleteRequested()
        vm.onDeleteDismissed()
        assertFalse(vm.state().deleteDialogOpen)
        assertTrue(repo.deleted.isEmpty())
    }

    @Test
    fun delete_offline_saysSo_andKeepsTheDialog() {
        val vm = editing()
        vm.onDeleteRequested()
        network.online = false
        vm.onDeleteConfirmed()
        dispatcher.scheduler.runCurrent()
        assertEquals(ActionMessage.OFFLINE, vm.state().deleteMessage)
        assertTrue(vm.state().deleteDialogOpen)
        assertTrue(repo.deleted.isEmpty())
    }

    @Test
    fun delete_aFailure_isACalmSentence_andRetryable() {
        val vm = editing()
        vm.onDeleteRequested()
        repo.deleteResult = PlaceWriteResult.Failed(MiffFailure.REFUSED)
        vm.onDeleteConfirmed()
        dispatcher.scheduler.runCurrent()
        assertEquals(ActionMessage.GENERIC, vm.state().deleteMessage)
        assertNull(vm.state().end)
        assertFalse(vm.state().deleting)
    }

    @Test
    fun delete_isNotOfferedWhenCreating() {
        val vm = viewModel()
        vm.onDeleteRequested()
        assertFalse(vm.state().deleteDialogOpen)
    }

    // --- Privacy ----------------------------------------------------------------------------------------------------------------

    @Test
    fun stateShowsNothingInItsToString() {
        val vm = viewModel()
        vm.fill("Chez Nathan")
        assertFalse(vm.state().toString().contains("Nathan"))
        assertFalse(vm.state().toString().contains("Italie"))
    }
}
