package com.tomessa.miff.ui.update

import com.tomessa.miff.data.feed.PhotoCache
import com.tomessa.miff.data.tracking.PhotoPosition
import com.tomessa.miff.data.tracking.PhotoPositionSource
import com.tomessa.miff.data.update.PhotoCleaner
import com.tomessa.miff.data.update.PhotoCommit
import com.tomessa.miff.data.update.PhotoStore
import com.tomessa.miff.data.update.PublishLedger
import com.tomessa.miff.data.update.UpdatePublisher
import com.tomessa.miff.domain.miff.DocWrite
import com.tomessa.miff.domain.tracking.Fix
import com.tomessa.miff.domain.update.UpdatePhoto
import com.tomessa.miff.testsupport.FakeActiveDevice
import com.tomessa.miff.testsupport.FakeNetwork
import com.tomessa.miff.testsupport.MemoryPublishLedger
import com.tomessa.miff.testsupport.T0
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The states of the Update screen (ticket Update, criteria V1, V2, V6, U3, U9, P1, P2): the camera is asked after the explanation, a photo taken is only in
 * memory, "Recommencer" and the cross do the same thing with no write, "Publier" exists only on the preview of a photo, and a publication that does not end keeps the
 * photo on the screen with a calm sentence.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UpdateViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class CountingStore : PhotoStore {
        var commits = 0
        var fail = false

        override suspend fun commit(
            uid: String,
            miffIds: List<String>,
            requireConsent: Boolean,
            build: (List<String>) -> List<DocWrite>,
        ): PhotoCommit {
            commits++
            if (fail) {
                throw com.google.firebase.firestore.FirebaseFirestoreException(
                    "down",
                    com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE,
                )
            }
            build(miffIds)
            return PhotoCommit(miffIds, emptyList(), emptyList())
        }
    }

    private val store = CountingStore()
    private val ledger = MemoryPublishLedger()
    private val network = FakeNetwork(true)
    private var now = T0
    private val fix = Fix(48.0, 2.0, T0)
    private val device = FakeActiveDevice()
    private val photo = UpdatePhoto(ByteArray(50_000), 461, 1024, takenAtMillis = T0)

    private fun viewModel(): UpdateViewModel {
        val position = PhotoPositionSource { PhotoPosition.Found(fix) }
        val photos = PhotoCache()
        val publisher =
            UpdatePublisher(store, device, position, network, ledger, photos, {
                "uid"
            }, { listOf("MiffAAAAAAAAAAAAAAAA") }, { 50 }, { now }, { java.time.ZoneId.of("UTC") })
        val cleaner =
            PhotoCleaner(
                store,
                device,
                network,
                ledger,
                photos,
                { "uid" },
                { listOf("MiffAAAAAAAAAAAAAAAA") },
                { 50 },
                { now },
            )
        return UpdateViewModel(publisher, cleaner, ledger) { now }
    }

    private fun UpdateViewModel.toPreview() {
        onOpened(granted = true, shouldShowRationale = false, sharingStopped = false)
        onPhotoReady(photo)
    }

    // --- The camera: explanation first, never cold --------------------------------------------------------------------------------

    @Test
    fun theFirstOpening_showsTheExplanation_andAsksNothing() {
        val vm = viewModel()
        vm.onOpened(granted = false, shouldShowRationale = false, sharingStopped = false)
        assertSame(UpdateStage.Explain, vm.stage.value)
        assertFalse("the explanation is not yet accepted", ledger.explained)
    }

    @Test
    fun afterTheExplanationAndOneRefusal_itOffersToTryAgain_afterTwo_theSettings() {
        val vm = viewModel()
        vm.onOpened(false, false, false)
        vm.onExplanationAccepted()
        assertTrue(ledger.explained)
        vm.onPermissionAnswered(granted = false, shouldShowRationale = true)
        assertSame(UpdateStage.RetryPermission, vm.stage.value)
        vm.onPermissionAnswered(granted = false, shouldShowRationale = false)
        assertSame(UpdateStage.PermissionDisabled, vm.stage.value)
    }

    @Test
    fun aGrantedPermission_opensTheViewfinder_andTheTipShowsThreeTimesOnly() {
        val vm = viewModel()
        val tips = mutableListOf<Boolean>()
        repeat(5) {
            vm.onOpened(true, false, false)
            tips += (vm.stage.value as UpdateStage.Viewfinder).showTip
            vm.onClosed()
        }
        assertEquals(listOf(true, true, true, false, false), tips)
    }

    @Test
    fun comingBackToTheForeground_doesNotCountANewOpening_noRestartOfTheViewfinder() {
        val vm = viewModel()
        vm.onOpened(true, false, false)
        val first = vm.stage.value
        vm.onOpened(true, false, false)
        assertSame(first, vm.stage.value)
        assertEquals(1, ledger.openings)
    }

    @Test
    fun aPermissionTakenBackWhileTheViewfinderIsOpen_closesIt_withoutCrash() {
        val vm = viewModel()
        ledger.explained = true
        vm.onOpened(true, false, false)
        vm.onOpened(granted = false, shouldShowRationale = false, sharingStopped = false)
        assertSame(UpdateStage.PermissionDisabled, vm.stage.value)
    }

    @Test
    fun sharingStopped_noViewfinder_andNoPermissionAsked() {
        val vm = viewModel()
        vm.onOpened(granted = false, shouldShowRationale = false, sharingStopped = true)
        assertSame(UpdateStage.SharingStopped, vm.stage.value)
        assertFalse(ledger.explained)
    }

    @Test
    fun aCameraThatCannotStart_isACalmScreen_notAnError() {
        val vm = viewModel()
        vm.onOpened(true, false, false)
        vm.onCameraUnavailable()
        assertSame(UpdateStage.CameraUnavailable, vm.stage.value)
        vm.onRetryCamera(granted = true, shouldShowRationale = false)
        assertTrue(vm.stage.value is UpdateStage.Viewfinder)
    }

    // --- The photo in memory ----------------------------------------------------------------------------------------------------------

    @Test
    fun aPhotoTaken_isOnlyInMemory_nothingIsWrittenBeforePublier() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.toPreview()
            advanceUntilIdle()
            assertTrue(vm.stage.value is UpdateStage.Preview)
            assertEquals(0, store.commits)
        }

    @Test
    fun theCross_andRecommencer_doTheSame_backToTheViewfinder_thePhotoForgotten_nothingWritten() =
        runTest(dispatcher) {
            val results =
                (1..2).map {
                    val vm = viewModel()
                    vm.toPreview()
                    // The cross and "Recommencer" both call onRetake: the same function, the same effect (D22).
                    vm.onRetake()
                    advanceUntilIdle()
                    vm.stage.value
                }
            results.forEach {
                assertTrue(it is UpdateStage.Viewfinder)
                assertFalse((it as UpdateStage.Viewfinder).showTip)
            }
            assertEquals("no write, no network", 0, store.commits)
        }

    @Test
    fun leavingTheUpdate_forgetsThePhoto_whateverTheWayOut() {
        val vm = viewModel()
        vm.toPreview()
        vm.onClosed()
        assertSame(UpdateStage.Idle, vm.stage.value)
    }

    @Test
    fun aPhotoThatCouldNotBePrepared_givesACalmSentenceOnTheViewfinder_andNoPreview() {
        val vm = viewModel()
        vm.onOpened(true, false, false)
        vm.onPhotoFailed()
        assertTrue((vm.stage.value as UpdateStage.Viewfinder).prepareFailed)
    }

    // --- "Publier" exists only for a photo (D16, criterion U9) -------------------------------------------------------------------

    @Test
    fun noPublicationWithoutAPhoto_fromAnyStateButThePreview() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.onPublish()
            vm.onOpened(true, false, false)
            vm.onPublish()
            vm.onCameraUnavailable()
            vm.onPublish()
            vm.onClosed()
            vm.onPublish()
            advanceUntilIdle()
            assertEquals(0, store.commits)
        }

    @Test
    fun aPhotoPublishedOnce_leavesTheScreen_andTellsTheTabs() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.toPreview()
            var heard: UpdateEvent? = null
            val job = launch(dispatcher) { heard = vm.events.first() }
            advanceUntilIdle()
            vm.onPublish()
            advanceUntilIdle()
            job.join()
            assertEquals(1, store.commits)
            assertEquals(UpdateEvent.Published(1), heard)
            assertSame(UpdateStage.Idle, vm.stage.value)
        }

    @Test
    fun aPublicationThatFails_keepsThePhotoOnTheScreen_withACalmSentence_andCanBeTriedAgain() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.toPreview()
            store.fail = true
            vm.onPublish()
            advanceUntilIdle()
            val stage = vm.stage.value as UpdateStage.Preview
            assertSame("the photo is still there", photo, stage.photo)
            assertEquals(PreviewNotice.Offline, stage.notice)
            assertFalse(stage.publishing)
            store.fail = false
            now += 31_000L
            vm.onPublish()
            advanceUntilIdle()
            assertSame(UpdateStage.Idle, vm.stage.value)
        }

    @Test
    fun whileThePhotoLeaves_nothingCanBeCancelledNorSentTwice() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.toPreview()
            vm.onPublish()
            // Before the answer: "Recommencer" and a second "Publier" do nothing.
            vm.onRetake()
            vm.onPublish()
            assertTrue((vm.stage.value as UpdateStage.Preview).publishing)
            advanceUntilIdle()
            assertEquals(1, store.commits)
        }

    @Test
    fun aPhotoWaitingMoreThan5Minutes_cannotBePublished_theSentenceSaysSo() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.toPreview()
            now += UpdatePhoto.STALE_MILLIS + 1
            // The app comes back to the foreground on the preview: the photo is old.
            vm.onOpened(true, false, false)
            assertEquals(PreviewNotice.Stale, (vm.stage.value as UpdateStage.Preview).notice)
            vm.onPublish()
            advanceUntilIdle()
            assertEquals("a stale photo is refused before anything is sent", 0, store.commits)
            assertNull(ledger.last)
        }

    @Test
    fun aPhotoStillFreshWhenTheAppComesBack_staysAsItWas() {
        val vm = viewModel()
        vm.toPreview()
        val before = vm.stage.value
        now += 60_000L
        vm.onOpened(true, false, false)
        assertSame(before, vm.stage.value)
    }
}
