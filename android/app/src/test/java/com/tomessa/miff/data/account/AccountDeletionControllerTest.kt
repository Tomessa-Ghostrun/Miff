package com.tomessa.miff.data.account

import com.tomessa.miff.testsupport.FakeAuthGateway
import com.tomessa.miff.testsupport.FakeDeletionSession
import com.tomessa.miff.testsupport.FakeNetwork
import com.tomessa.miff.testsupport.FakeProcedure
import com.tomessa.miff.testsupport.MemoryDeletionMarker
import com.tomessa.miff.testsupport.Timeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The controller of the deletion of the account (ticket AUTH-5, sections 5.5 and 5.6): the marker is on the disk and the app stops what
 * runs BEFORE the first change, one gesture = one operation, a pause without network that resumes by itself, the "Dernière vérification",
 * three tries then a calm stop, a marker that is not this account's is never taken up (criteria SC15, SC23, SC26, SC30, SA5).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountDeletionControllerTest {
    private val timeline = Timeline()
    private val procedure = FakeProcedure()
    private val marker = MemoryDeletionMarker(timeline = timeline)
    private val session = FakeDeletionSession(timeline)
    private val network = FakeNetwork()
    private val gateway = FakeAuthGateway(timeline).also { it.setSignedIn("uid-1") }
    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun cancelScopes() {
        scopes.forEach { it.cancel() }
    }

    private fun TestScope.controller(apple: Boolean = false) =
        AccountDeletionController(
            procedure = procedure,
            marker = marker,
            session = session,
            network = network,
            currentUid = { gateway.currentUid },
            isAppleLinked = { apple },
            // A FOREGROUND scope of the test scheduler: advanceUntilIdle() runs it (it does not wait for backgroundScope tasks).
            scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)).also { scopes += it },
            retryPauseMillis = 10L,
        )

    @Test
    fun start_writesTheMarker_thenStopsWhatRuns_thenTheFirstChange() =
        runTest {
            procedure.onRun = { timeline += "run" }
            val controller = controller()
            controller.start()
            advanceUntilIdle()
            // The marker is on the disk, then the listeners and the monitoring are stopped, THEN the procedure (the first write); the marker is
            // rewritten as "deleted" (Firebase confirmed) just before the local cleaning.
            assertEquals(listOf("marker.write", "begin", "run", "marker.write", "erase"), timeline.entries)
        }

    @Test
    fun theMarkerHoldsTheIdOfTheAccountAndWhetherAppleIsLinked_nothingElse() =
        runTest {
            val controller = controller(apple = true)
            procedure.results += DeletionRun.NeedsRecentLogin
            controller.start()
            advanceUntilIdle()
            assertEquals(AccountDeletionMark("uid-1", appleLinked = true), marker.stored)
        }

    @Test
    fun aCompletedRun_cleansTheLocalData_andEndsOnDone() =
        runTest {
            val controller = controller(apple = true)
            controller.start()
            advanceUntilIdle()
            assertEquals(listOf("uid-1"), session.erased)
            assertEquals(AccountDeletionState.Done(appleLinked = true), controller.state.value)
        }

    @Test
    fun noSession_noMarkerIsWritten_andNothingStarts() =
        runTest {
            gateway.setSignedIn(null)
            val controller = controller()
            controller.start()
            advanceUntilIdle()
            assertNull(marker.stored)
            assertEquals(0, procedure.runs)
            assertEquals(AccountDeletionState.Idle, controller.state.value)
        }

    @Test
    fun oneGesture_oneOperation_aSecondStartWhileItRunsDoesNothing() =
        runTest {
            network.online = false
            procedure.results += DeletionRun.Offline
            val controller = controller()
            controller.start()
            advanceUntilIdle()
            controller.start()
            controller.start()
            advanceUntilIdle()
            assertEquals(1, procedure.runs)
            assertEquals(1, session.begins)
        }

    // --- No network ------------------------------------------------------------------------------------------

    @Test
    fun withoutNetwork_itPausesCalmly_andCarriesOnByItselfWhenTheNetworkIsBack() =
        runTest {
            network.online = false
            procedure.results += listOf(DeletionRun.Offline, DeletionRun.Completed)
            val controller = controller()
            controller.start()
            advanceUntilIdle()
            assertEquals(AccountDeletionState.WaitingForNetwork, controller.state.value)
            assertEquals(1, procedure.runs)
            assertNotNull(marker.stored)
            network.online = true
            advanceUntilIdle()
            assertEquals(2, procedure.runs)
            assertEquals(AccountDeletionState.Done(false), controller.state.value)
        }

    // --- The last verification -------------------------------------------------------------------------------

    @Test
    fun whenFirebaseWantsAMoreRecentSignIn_itStopsOnTheLastVerification_andResumesAfterTheProof() =
        runTest {
            procedure.results += listOf(DeletionRun.NeedsRecentLogin, DeletionRun.Completed)
            val controller = controller()
            controller.start()
            advanceUntilIdle()
            assertEquals(AccountDeletionState.NeedsRecentLogin, controller.state.value)
            assertEquals(emptyList<String>(), session.erased)
            // The marker stays: an app killed here resumes at the next launch.
            assertNotNull(marker.stored)
            controller.reauthenticated()
            advanceUntilIdle()
            assertEquals(AccountDeletionState.Done(false), controller.state.value)
            assertEquals(2, procedure.runs)
        }

    // --- Failures --------------------------------------------------------------------------------------------

    @Test
    fun threeFailures_thenACalmStop_andTheMarkerStaysSoTheNextLaunchTriesAgain() =
        runTest {
            procedure.results += DeletionRun.Failed
            val controller = controller()
            controller.start()
            advanceUntilIdle()
            assertEquals(AccountDeletionState.Stuck, controller.state.value)
            assertEquals(AccountDeletionController.MAX_FAILURES, procedure.runs)
            assertNotNull(marker.stored)
            assertEquals(emptyList<String>(), session.erased)
        }

    @Test
    fun retryAfterAStop_runsAgain_andCanEnd() =
        runTest {
            procedure.results +=
                listOf(DeletionRun.Failed, DeletionRun.Failed, DeletionRun.Failed, DeletionRun.Completed)
            val controller = controller()
            controller.start()
            advanceUntilIdle()
            controller.retry()
            advanceUntilIdle()
            assertEquals(AccountDeletionState.Done(false), controller.state.value)
        }

    @Test
    fun aMiffThatShowsUp_isACancellation_markerErased_appBackOnTrack_nothingErased_noDeadEnd() =
        runTest {
            procedure.results += DeletionRun.MiffsRemain
            val controller = controller()
            controller.start()
            advanceUntilIdle()
            // The marker is gone BEFORE the app is put back on track (a kill in between must never resume a cancelled deletion).
            assertEquals(listOf("marker.write", "begin", "marker.clear", "cancel"), timeline.entries)
            assertNull(marker.stored)
            assertEquals(1, session.cancels)
            assertEquals(emptyList<String>(), session.erased)
            assertEquals(1, procedure.runs)
            // Not a state the person is stuck in: the screens are replaced by the usual ones.
            assertEquals(AccountDeletionState.Idle, controller.state.value)
        }

    @Test
    fun aCancelledDeletion_canBeStartedAgainLater_fromScratch() =
        runTest {
            procedure.results += listOf(DeletionRun.MiffsRemain, DeletionRun.Completed)
            val controller = controller()
            controller.start()
            advanceUntilIdle()
            controller.start()
            advanceUntilIdle()
            assertEquals(AccountDeletionState.Done(false), controller.state.value)
            assertEquals(2, procedure.runs)
        }

    @Test
    fun aMiffThatShowsUpAtResume_cancelsToo_theNextLaunchIsNormal() =
        runTest {
            marker.stored = AccountDeletionMark("uid-1", appleLinked = false)
            procedure.results += DeletionRun.MiffsRemain
            val controller = controller()
            controller.resume()
            advanceUntilIdle()
            assertNull(marker.stored)
            assertEquals(1, session.cancels)
        }

    @Test
    fun anExpiredSession_isNeverDone_theMarkerStays_andNothingSaysDeleted() =
        runTest {
            procedure.results += DeletionRun.SessionExpired
            val controller = controller()
            controller.start()
            advanceUntilIdle()
            assertEquals(AccountDeletionState.NeedsSignIn, controller.state.value)
            assertNotNull(marker.stored)
            assertEquals(false, marker.stored?.accountDeleted)
            assertEquals(emptyList<String>(), session.erased)
        }

    @Test
    fun signInAgain_closesTheSession_withTheMarkerKept() =
        runTest {
            procedure.results += DeletionRun.SessionExpired
            val controller = controller()
            controller.start()
            advanceUntilIdle()
            controller.signInAgain()
            advanceUntilIdle()
            assertEquals(1, session.keptMarkerSignOuts)
            assertNotNull(marker.stored)
        }

    @Test
    fun theDeletionIsMarkedConfirmed_beforeTheLocalCleaning() =
        runTest {
            var confirmedWhenErasing: Boolean? = null
            val controller = controller()
            session.onErase = { confirmedWhenErasing = marker.stored?.accountDeleted }
            controller.start()
            advanceUntilIdle()
            assertEquals(true, confirmedWhenErasing)
        }

    @Test
    fun aNetworkThatIsThereButDoesNotCarry_endsOnACalmStop_afterAFewTries() =
        runTest {
            // The phone says "online", every try says "offline": not an endless loop.
            procedure.results += DeletionRun.Offline
            val controller = controller()
            controller.start()
            advanceUntilIdle()
            assertEquals(AccountDeletionState.Stuck, controller.state.value)
            assertEquals(AccountDeletionController.MAX_ONLINE_FAILURES, procedure.runs)
        }

    @Test
    fun theRetryPauseGrows_notAHammering() =
        runTest {
            procedure.results += DeletionRun.Failed
            val controller = controller()
            controller.start()
            advanceTimeBy(5L)
            assertEquals(1, procedure.runs)
            advanceUntilIdle()
            assertEquals(3, procedure.runs)
        }

    // --- Resuming at launch ----------------------------------------------------------------------------------

    @Test
    fun resume_withTheMarkerOfThisAccount_takesTheDeletionUpAgain_withNoNewProof() =
        runTest {
            marker.stored = AccountDeletionMark("uid-1", appleLinked = false)
            val controller = controller()
            controller.resume()
            advanceUntilIdle()
            assertEquals(1, procedure.runs)
            assertEquals(AccountDeletionState.Done(false), controller.state.value)
            assertEquals(0, gateway.passwordReauthCalls)
        }

    @Test
    fun resume_withAMarkerOfAnotherAccount_orNone_doesNothing() =
        runTest {
            val controller = controller()
            controller.resume()
            marker.stored = AccountDeletionMark("uid-2", appleLinked = false)
            controller.resume()
            advanceUntilIdle()
            assertEquals(0, procedure.runs)
            assertEquals(AccountDeletionState.Idle, controller.state.value)
        }

    @Test
    fun resume_isMarkedAsResumed_forTheScreenThatSaysSo() =
        runTest {
            marker.stored = AccountDeletionMark("uid-1", appleLinked = false)
            procedure.results += DeletionRun.NeedsRecentLogin
            val controller = controller()
            controller.resume()
            // Between the start and the first answer the state says "resumed".
            assertEquals(AccountDeletionState.Running(resumed = true), controller.state.value)
            advanceUntilIdle()
        }

    @Test
    fun acknowledge_ofTheEndScreen_leavesTheControllerIdle() =
        runTest {
            val controller = controller()
            controller.start()
            advanceUntilIdle()
            controller.acknowledge()
            assertEquals(AccountDeletionState.Idle, controller.state.value)
        }
}
