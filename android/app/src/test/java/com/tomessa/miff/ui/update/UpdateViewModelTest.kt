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

        /** The documents built by the lots that reached the server (a failing lot builds nothing). */
        val written = mutableListOf<DocWrite>()

        /** What a failing lot throws (offline by default). */
        var failure: Exception =
            com.google.firebase.firestore.FirebaseFirestoreException(
                "down",
                com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE,
            )

        override suspend fun commit(
            uid: String,
            miffIds: List<String>,
            requireConsent: Boolean,
            build: (List<String>) -> List<DocWrite>,
        ): PhotoCommit {
            commits++
            if (fail) {
                throw failure
            }
            written += build(miffIds)
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
    fun theTipOfTheDoubleTap_showsAsLongAsItWasNeverUsed_everyTime_andNeverAfterTheFirstDoubleTap() {
        val vm = viewModel()
        val tips = mutableListOf<Boolean>()
        repeat(5) {
            vm.onOpened(true, false, false)
            tips += (vm.stage.value as UpdateStage.Viewfinder).showTip
            vm.onClosed()
        }
        // Opening the viewfinder is not using it: the tip stays.
        assertEquals(listOf(true, true, true, true, true), tips)
        vm.onOpened(true, false, false)
        vm.onSwitchedByDoubleTap()
        assertFalse((vm.stage.value as UpdateStage.Viewfinder).showTip)
        assertTrue("remembered for the installation", ledger.doubleTapUsed())
        // After that: never again, on a new opening, after a retake, after a restart of the app (a new view model on the same ledger).
        vm.onClosed()
        vm.onOpened(true, false, false)
        assertFalse((vm.stage.value as UpdateStage.Viewfinder).showTip)
        val restarted = viewModel()
        restarted.onOpened(true, false, false)
        assertFalse((restarted.stage.value as UpdateStage.Viewfinder).showTip)
        restarted.onPhotoReady(photo)
        restarted.onRetake()
        assertFalse((restarted.stage.value as UpdateStage.Viewfinder).showTip)
    }

    @Test
    fun comingBackToTheForeground_noRestartOfTheViewfinder() {
        val vm = viewModel()
        vm.onOpened(true, false, false)
        val first = vm.stage.value
        vm.onOpened(true, false, false)
        assertSame(first, vm.stage.value)
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
                assertTrue(
                    "the tip is back while the double tap was never used",
                    (it as UpdateStage.Viewfinder).showTip,
                )
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

    // --- The optional description (ticket description-update) --------------------------------------------------------------------------

    private fun photoWrite() = store.written.first { it.path.contains("/photos/") }

    @Test
    fun aPreviewStartsWithoutADescription_andPublishingWithoutOneWritesNoKey() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.toPreview()
            assertEquals("", (vm.stage.value as UpdateStage.Preview).description)
            vm.onPublish()
            advanceUntilIdle()
            assertEquals(1, store.commits)
            assertFalse("description" in photoWrite().data)
        }

    @Test
    fun aDescriptionTyped_isPublishedWithThePhoto_cleaned_inTheSameLot() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.toPreview()
            vm.onDescriptionChanged("  Bien   arrivé ")
            assertEquals("  Bien   arrivé ", (vm.stage.value as UpdateStage.Preview).description)
            vm.onPublish()
            advanceUntilIdle()
            assertEquals("one lot, one commit", 1, store.commits)
            assertEquals("Bien arrivé", photoWrite().data["description"])
            assertSame(UpdateStage.Idle, vm.stage.value)
        }

    @Test
    fun aDescriptionOfOnlySpaces_publishesNoKey() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.toPreview()
            vm.onDescriptionChanged("   \n\t ")
            vm.onPublish()
            advanceUntilIdle()
            assertFalse("description" in photoWrite().data)
        }

    @Test
    fun theViewModel_alsoLimitsTheText_to140Units_andReturnsWhatItKept() {
        val vm = viewModel()
        vm.toPreview()
        val kept = vm.onDescriptionChanged("a".repeat(200))
        assertEquals(140, kept.length)
        assertEquals(kept, (vm.stage.value as UpdateStage.Preview).description)
    }

    @Test
    fun aDescriptionOutsideThePreview_isIgnored() {
        val vm = viewModel()
        assertEquals("", vm.onDescriptionChanged("hello"))
        vm.onOpened(true, false, false)
        assertEquals("", vm.onDescriptionChanged("hello"))
        assertTrue(vm.stage.value is UpdateStage.Viewfinder)
    }

    @Test
    fun aPublicationThatFails_keepsThePhotoAndItsDescription_andRetryingSendsBoth() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.toPreview()
            vm.onDescriptionChanged("Pas de réseau pour l'instant")
            store.fail = true
            vm.onPublish()
            advanceUntilIdle()
            val stage = vm.stage.value as UpdateStage.Preview
            assertSame("the photo is still there", photo, stage.photo)
            assertEquals("the description is still there", "Pas de réseau pour l'instant", stage.description)
            assertEquals(PreviewNotice.Offline, stage.notice)
            assertTrue("nothing was written", store.written.isEmpty())
            store.fail = false
            now += 31_000L
            vm.onPublish()
            advanceUntilIdle()
            assertEquals("Pas de réseau pour l'instant", photoWrite().data["description"])
            assertSame(UpdateStage.Idle, vm.stage.value)
        }

    @Test
    fun aRefusalOfTheRules_notDeployedYet_isACalmSentence_withPhotoAndDescriptionKept() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.toPreview()
            vm.onDescriptionChanged("Bien arrivé")
            store.failure =
                com.google.firebase.firestore.FirebaseFirestoreException(
                    "denied",
                    com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED,
                )
            store.fail = true
            vm.onPublish()
            advanceUntilIdle()
            val stage = vm.stage.value as UpdateStage.Preview
            assertEquals(PreviewNotice.Refused, stage.notice)
            assertEquals("Bien arrivé", stage.description)
            assertFalse(stage.publishing)
            assertSame(photo, stage.photo)
        }

    @Test
    fun theDescriptionCannotBeChangedWhileThePhotoLeaves() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.toPreview()
            vm.onDescriptionChanged("avant")
            vm.onPublish()
            assertTrue((vm.stage.value as UpdateStage.Preview).publishing)
            assertEquals("avant", vm.onDescriptionChanged("pendant"))
            assertEquals("avant", (vm.stage.value as UpdateStage.Preview).description)
            advanceUntilIdle()
            assertEquals("avant", photoWrite().data["description"])
        }

    @Test
    fun recommencer_theCross_andLeavingTheUpdate_forgetTheDescriptionWithThePhoto() {
        listOf<(UpdateViewModel) -> Unit>({ it.onRetake() }, { it.onClosed() }).forEach { leave ->
            val vm = viewModel()
            vm.toPreview()
            vm.onDescriptionChanged("à oublier")
            leave(vm)
            assertFalse(vm.stage.value is UpdateStage.Preview)
            // A new photo starts with an empty field, never with the old text.
            vm.onOpened(true, false, false)
            vm.onPhotoReady(photo)
            assertEquals("", (vm.stage.value as UpdateStage.Preview).description)
        }
    }

    @Test
    fun aPhotoThatWentStale_forgetsItsDescription() {
        val vm = viewModel()
        vm.toPreview()
        vm.onDescriptionChanged("trop tard")
        now += UpdatePhoto.STALE_MILLIS + 1
        vm.onOpened(true, false, false)
        val stage = vm.stage.value as UpdateStage.Preview
        assertEquals(PreviewNotice.Stale, stage.notice)
        assertEquals("", stage.description)
    }

    @Test
    fun aPublicationRefusedBecauseThePhotoIsStale_forgetsItsDescription_likeIos() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.toPreview()
            vm.onDescriptionChanged("trop tard")
            // The photo becomes stale while the preview stays open (no return to the foreground in between): the publisher refuses it.
            now += UpdatePhoto.STALE_MILLIS + 1
            vm.onPublish()
            advanceUntilIdle()
            val stage = vm.stage.value as UpdateStage.Preview
            assertEquals(PreviewNotice.Stale, stage.notice)
            assertEquals("", stage.description)
            assertFalse(stage.publishing)
            assertEquals(0, store.commits)
        }

    @Test
    fun aPhotoStillFresh_keepsItsDescription_whenTheAppComesBack() {
        val vm = viewModel()
        vm.toPreview()
        vm.onDescriptionChanged("encore là")
        now += 60_000L
        vm.onOpened(true, false, false)
        assertEquals("encore là", (vm.stage.value as UpdateStage.Preview).description)
    }

    @Test
    fun thePreview_showsNothingOfTheDescription_inItsToString() {
        val vm = viewModel()
        vm.toPreview()
        vm.onDescriptionChanged("secret words")
        assertEquals("Preview(redacted)", vm.stage.value.toString())
    }
}
