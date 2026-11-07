package com.tomessa.miff.ui.place

import com.tomessa.miff.data.place.PlaceNotices
import com.tomessa.miff.domain.miff.MiffMember
import com.tomessa.miff.domain.miff.MiffObservation
import com.tomessa.miff.domain.miff.MiffRole
import com.tomessa.miff.domain.miff.MiffSnapshot
import com.tomessa.miff.testsupport.FakeMiffRepository
import com.tomessa.miff.testsupport.MIFF_ID
import com.tomessa.miff.testsupport.miffDetails
import com.tomessa.miff.testsupport.testPlace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The reading of the places: the detail (design 16), the list of the Profile tab (design 17), the message after a save (04L). */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaceReadingTest {
    private val dispatcher = StandardTestDispatcher()
    private val repo = FakeMiffRepository()
    private val observing = CoroutineScope(dispatcher + SupervisorJob())

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        observing.cancel()
        Dispatchers.resetMain()
    }

    private val creator = MiffMember("uid-owner", "Emmanuelle", "Martin", null, MiffRole.OWNER, sharingEnabled = true)
    private val other = MiffMember("u2", "Ryadh", "Pluchard", null, MiffRole.MEMBER, sharingEnabled = true)

    private fun data(
        fromCache: Boolean = false,
        vararg places: com.tomessa.miff.domain.place.Place,
    ) = MiffObservation.Data(
        MiffSnapshot(
            miffDetails(owner = "uid-owner", name = "Famille Martin"),
            listOf(creator, other),
            places.toList(),
            fromCache,
        ),
    )

    private fun detail(uid: String) = PlaceDetailViewModel(MIFF_ID, "p1", repo, currentUid = { uid })

    private fun startDetail(viewModel: PlaceDetailViewModel) {
        observing.launch { viewModel.observe() }
        dispatcher.scheduler.runCurrent()
    }

    // --- The detail --------------------------------------------------------------------------------------------------------------

    @Test
    fun detail_creator_canEdit_andIsToldTheyMadeIt() {
        val viewModel = detail("uid-owner")
        startDetail(viewModel)
        repo.observer.flowOf(MIFF_ID).tryEmit(data(false, testPlace("p1", "Maison de Papa", createdBy = "uid-owner")))
        dispatcher.scheduler.runCurrent()
        val state = viewModel.uiState.value
        assertEquals(PlaceDetailStatus.READY, state.status)
        assertTrue(state.canEdit)
        assertTrue(state.creatorIsMe)
        assertEquals("Emmanuelle Martin", state.creatorName)
        assertEquals("Famille Martin", state.miffName)
    }

    @Test
    fun detail_member_readsOnly_andSeesWhoMadeIt() {
        val viewModel = detail("u2")
        startDetail(viewModel)
        repo.observer.flowOf(MIFF_ID).tryEmit(data(false, testPlace("p1", "Maison de Papa", createdBy = "uid-owner")))
        dispatcher.scheduler.runCurrent()
        val state = viewModel.uiState.value
        assertFalse(state.canEdit)
        assertFalse(state.creatorIsMe)
        assertEquals("Emmanuelle Martin", state.creatorName)
    }

    @Test
    fun detail_aPlaceThatIsGoneForTheServer_isNoticed_butNotWhenTheCacheSimplyLacksIt() {
        val viewModel = detail("u2")
        startDetail(viewModel)
        repo.observer.flowOf(MIFF_ID).tryEmit(data(true))
        dispatcher.scheduler.runCurrent()
        // "Not in the cache" is not "deleted".
        assertEquals(PlaceDetailStatus.LOADING, viewModel.uiState.value.status)
        repo.observer.flowOf(MIFF_ID).tryEmit(data(false))
        dispatcher.scheduler.runCurrent()
        assertEquals(PlaceDetailStatus.GONE, viewModel.uiState.value.status)
    }

    @Test
    fun detail_aLostAccess_leavesTheMessageToTheHost() {
        val viewModel = detail("u2")
        startDetail(viewModel)
        repo.observer.flowOf(MIFF_ID).tryEmit(MiffObservation.AccessLost)
        dispatcher.scheduler.runCurrent()
        assertEquals(PlaceDetailStatus.ACCESS_LOST, viewModel.uiState.value.status)
    }

    @Test
    fun detail_aFailureThatIsNotALoss_keepsWhatIsShown_orSaysItCalmly() {
        val viewModel = detail("u2")
        startDetail(viewModel)
        repo.observer.flowOf(MIFF_ID).tryEmit(MiffObservation.Failed)
        dispatcher.scheduler.runCurrent()
        assertEquals(PlaceDetailStatus.UNAVAILABLE, viewModel.uiState.value.status)
        repo.observer.flowOf(MIFF_ID).tryEmit(data(false, testPlace("p1", "Maison")))
        dispatcher.scheduler.runCurrent()
        repo.observer.flowOf(MIFF_ID).tryEmit(MiffObservation.Failed)
        dispatcher.scheduler.runCurrent()
        assertEquals(PlaceDetailStatus.READY, viewModel.uiState.value.status)
    }

    @Test
    fun detail_showsNothingInItsToString() {
        assertEquals("PlaceDetailState(redacted)", PlaceDetailState().toString())
    }

    // --- The list of the Profile tab ---------------------------------------------------------------------------------------------

    private fun list(uid: String) = MiffPlacesViewModel(MIFF_ID, repo, currentUid = { uid })

    @Test
    fun list_isEmptyUntilTheMiffIsRead_thenHoldsThePlaces_andWhoIsTheCreator() {
        val viewModel = list("uid-owner")
        assertFalse(viewModel.uiState.value.loaded)
        observing.launch { viewModel.observe() }
        dispatcher.scheduler.runCurrent()
        repo.observer.flowOf(MIFF_ID).tryEmit(data(false, testPlace("p1", "A"), testPlace("p2", "B")))
        dispatcher.scheduler.runCurrent()
        assertTrue(viewModel.uiState.value.loaded)
        assertEquals(2, viewModel.uiState.value.places.size)
        assertTrue(viewModel.uiState.value.isCreator)
    }

    @Test
    fun list_aMember_isNotACreator() {
        val viewModel = list("u2")
        observing.launch { viewModel.observe() }
        dispatcher.scheduler.runCurrent()
        repo.observer.flowOf(MIFF_ID).tryEmit(data(false, testPlace("p1", "A")))
        dispatcher.scheduler.runCurrent()
        assertFalse(viewModel.uiState.value.isCreator)
    }

    @Test
    fun list_aLostAccess_leavesNothingOfThatMiff() {
        val viewModel = list("u2")
        observing.launch { viewModel.observe() }
        dispatcher.scheduler.runCurrent()
        repo.observer.flowOf(MIFF_ID).tryEmit(data(false, testPlace("p1", "A")))
        dispatcher.scheduler.runCurrent()
        repo.observer.flowOf(MIFF_ID).tryEmit(MiffObservation.AccessLost)
        dispatcher.scheduler.runCurrent()
        assertFalse(viewModel.uiState.value.loaded)
        assertTrue(
            viewModel.uiState.value.places
                .isEmpty(),
        )
    }

    @Test
    fun list_showsNothingInItsToString() {
        assertEquals("MiffPlacesState(redacted)", MiffPlacesState().toString())
    }

    // --- The message after a save --------------------------------------------------------------------------------------------------

    @Test
    fun notice_isKeptInMemory_untilTheScreenIsLeft_andGoesWithTheSession() =
        runTest {
            val notices = PlaceNotices()
            assertNull(notices.notice.value)
            notices.saved("Maison de Papa")
            assertEquals("Maison de Papa", notices.notice.value?.name)
            assertFalse(
                notices.notice.value
                    .toString()
                    .contains("Papa"),
            )
            notices.consume()
            assertNull(notices.notice.value)
            notices.saved("Autre")
            notices.stop()
            assertNull(notices.notice.value)
        }
}
