package com.tomessa.miff.data.alert

import com.tomessa.miff.data.auth.NetworkMonitor
import com.tomessa.miff.data.session.Clearance
import com.tomessa.miff.domain.alert.AlertDoc
import com.tomessa.miff.domain.alert.AlertFailure
import com.tomessa.miff.domain.alert.AlertRules
import com.tomessa.miff.testsupport.DEVICE_A
import com.tomessa.miff.testsupport.FakeActiveDevice
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The state machine of MY alert (docs/tickets/alerte-danger.md section 6, criteria AD3 to AD4, AD21 to AD24, AD42, AD44, AD47, AD48), with a fake server, a fake network and a
 * virtual clock. Each way an alert can fail is its own test: wrong clock or rules (generic refusal), re-arming under 60 s, the daily limit of 3, no network, a replaced phone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AlertControllerTest {
    private class FakeNetwork(
        online: Boolean = true,
    ) : NetworkMonitor {
        val online = MutableStateFlow(online)

        override fun isOnline() = online.value

        override fun onlineChanges(): Flow<Boolean> = online
    }

    private class FakeStore : AlertStore {
        val starts = ArrayDeque<StartResult>()
        val ends = ArrayDeque<EndResult>()
        var mine: MineRead = MineRead.Value(null)
        val startCalls = mutableListOf<String>()
        val endCalls = mutableListOf<String>()
        val reads = mutableListOf<String>()

        override suspend fun start(
            uid: String,
            miffId: String,
            deviceId: String,
            nowMillis: Long,
        ): StartResult {
            startCalls += miffId
            return starts.removeFirstOrNull() ?: StartResult.Started(nowMillis, AlertRules.expiresAtMillis(nowMillis))
        }

        override suspend fun end(
            uid: String,
            miffId: String,
            deviceId: String,
            nowMillis: Long,
        ): EndResult {
            endCalls += miffId
            return ends.removeFirstOrNull() ?: EndResult.Ended
        }

        override suspend fun readMine(
            uid: String,
            miffId: String,
        ): MineRead {
            reads += miffId
            return mine
        }

        override fun observe(miffId: String): Flow<AlertsHeard> = emptyFlow()
    }

    private class World(
        scope: TestScope,
        online: Boolean = true,
    ) {
        val store = FakeStore()
        val device = FakeActiveDevice()
        val network = FakeNetwork(online)
        val local = MemoryAlertLocalStore()
        var uid: String? = "me"
        var positions = 0
        val base = 1_800_000_000_000L
        val controller =
            AlertController(
                store = store,
                device = device,
                network = network,
                local = local,
                currentUid = { uid },
                scope = scope,
                nowMillis = { base + scope.testScheduler.currentTime },
                onSending = { positions++ },
                sendTimeoutMillis = 10_000L,
                retryPausesMillis = listOf(5_000L, 15_000L),
            )
        val events = mutableListOf<AlertEvent>()
    }

    private fun TestScope.world(online: Boolean = true) = World(this, online)

    @Test
    fun theAlertLeavesInTheOriginMiffAndBecomesActive() =
        runTest {
            val w = world()
            assertEquals(TriggerResult.Started, w.controller.trigger("miffA"))
            runCurrent()
            val state = w.controller.state.value as MyAlert.Active
            assertEquals("miffA", state.miffId)
            assertEquals(listOf("miffA"), w.store.startCalls)
            // one measure of the position, asked once at the start
            assertEquals(1, w.positions)
            // the pointer survives a relaunch, and holds no name and no position
            assertEquals(PointerPhase.ACTIVE, w.local.pointer()?.phase)
            // the expiry is the phone's clock + 7 days - 2 h (the contract of the rules)
            assertEquals(AlertRules.expiresAtMillis(w.base), state.expiresAtMillis)
        }

    @Test
    fun aSecondAlertNeverStartsWhileOneIsInProgress_inAnyMiff() =
        runTest {
            val w = world()
            w.controller.trigger("miffA")
            runCurrent()
            val second = w.controller.trigger("miffB")
            runCurrent()
            assertEquals(TriggerResult.AlreadyActive("miffA"), second)
            assertEquals(listOf("miffA"), w.store.startCalls)
            assertEquals(1, w.positions)
        }

    @Test
    fun notSignedIn_nothingStarts() =
        runTest {
            val w = world()
            w.uid = null
            assertEquals(TriggerResult.NotSignedIn, w.controller.trigger("miffA"))
            assertTrue(w.controller.state.value is MyAlert.None)
        }

    // ---- offline ------------------------------------------------------------------------------------------------------------------

    @Test
    fun offline_theAlertWaitsInTheApp_thenLeavesWhenTheNetworkReturns() =
        runTest {
            val w = world(online = false)
            w.controller.trigger("miffA")
            runCurrent()
            assertEquals(MyAlert.Sending("miffA", w.base, SendStep.WAITING_NETWORK), w.controller.state.value)
            assertTrue("nothing is written without a network", w.store.startCalls.isEmpty())
            advanceTimeBy(60_000)
            w.network.online.value = true
            runCurrent()
            assertTrue(w.controller.state.value is MyAlert.Active)
            assertEquals(1, w.store.startCalls.size)
        }

    @Test
    fun noAnswerFromTheServer_isTriedAgainWithPauses_thenLeaves() =
        runTest {
            val w = world()
            w.store.starts += StartResult.Failed(AlertFailure.Offline)
            w.controller.trigger("miffA")
            runCurrent()
            assertEquals(SendStep.WAITING_NETWORK, (w.controller.state.value as MyAlert.Sending).step)
            advanceTimeBy(5_001)
            runCurrent()
            assertTrue(w.controller.state.value is MyAlert.Active)
            assertEquals(2, w.store.startCalls.size)
        }

    @Test
    fun anAlertOlderThan15Minutes_isOnlySentOnceTheAlertIsConfirmedAgain() =
        runTest {
            val w = world(online = false)
            w.controller.trigger("miffA")
            runCurrent()
            advanceTimeBy(AlertRules.STALE_SEND_MILLIS + 1_000)
            w.network.online.value = true
            runCurrent()
            assertEquals(SendStep.NEEDS_CONFIRMATION, (w.controller.state.value as MyAlert.Sending).step)
            assertTrue("nothing leaves without the confirmation", w.store.startCalls.isEmpty())
            w.controller.confirmStale()
            runCurrent()
            assertTrue(w.controller.state.value is MyAlert.Active)
            assertEquals(1, w.store.startCalls.size)
        }

    @Test
    fun aWaitingAlertCanBeCancelled_andNothingLeaves() =
        runTest {
            val w = world(online = false)
            w.controller.trigger("miffA")
            runCurrent()
            w.controller.cancelPending()
            w.network.online.value = true
            runCurrent()
            assertTrue(w.controller.state.value is MyAlert.None)
            assertNull(w.local.pointer())
            assertTrue(w.store.startCalls.isEmpty())
        }

    // ---- the ways a start can be refused ----------------------------------------------------------------------------------------------

    @Test
    fun reArmingUnder60Seconds_waitsTheRemainingSecondsCalmly_andThenLeaves() =
        runTest {
            val w = world()
            w.store.starts += StartResult.TooSoon(20_000L)
            w.controller.trigger("miffA")
            runCurrent()
            // still "Envoi…", no refusal shown
            assertEquals(SendStep.SENDING, (w.controller.state.value as MyAlert.Sending).step)
            advanceTimeBy(20_001)
            runCurrent()
            assertTrue(w.controller.state.value is MyAlert.Active)
            assertEquals(2, w.store.startCalls.size)
        }

    @Test
    fun aRefusalWithNothingToExplainIt_isTheGenericCalmRefusal_witARetry() =
        runTest {
            // a clock far off, or rules not up to date: the server says "permission-denied" and nothing else
            val w = world()
            w.store.starts += StartResult.Failed(AlertFailure.Refused)
            w.controller.trigger("miffA")
            runCurrent()
            assertEquals(SendStep.REFUSED, (w.controller.state.value as MyAlert.Sending).step)
            assertEquals(1, w.device.refusalChecks)
            // "Réessayer" tries again
            w.controller.retry()
            runCurrent()
            assertTrue(w.controller.state.value is MyAlert.Active)
        }

    @Test
    fun theFourthAlertOfTheDay_isReadBeforeWriting_theDailyLimit_noRetryIsOffered() =
        runTest {
            val w = world()
            w.store.starts += StartResult.Failed(AlertFailure.QuotaReached)
            w.controller.trigger("miffA")
            runCurrent()
            assertEquals(SendStep.QUOTA, (w.controller.state.value as MyAlert.Sending).step)
            // no refusal check of the session is needed: the counter said it
            assertEquals(0, w.device.refusalChecks)
            // retrying would be refused again: nothing is sent
            w.controller.retry()
            runCurrent()
            assertEquals(1, w.store.startCalls.size)
            // the person is never stuck: she can close it (the server confirms that nothing is alive, then the pointer goes)
            w.controller.cancelPending()
            assertTrue(w.controller.state.value is MyAlert.None)
            runCurrent()
            assertNull(w.local.pointer())
        }

    @Test
    fun aRefusalWhenTheCounterHadRoom_isNotTheLimit_itIsTheGenericOne() =
        runTest {
            val w = world()
            w.store.starts += StartResult.Failed(AlertFailure.Refused)
            w.controller.trigger("miffA")
            runCurrent()
            assertEquals(SendStep.REFUSED, (w.controller.state.value as MyAlert.Sending).step)
        }

    @Test
    fun aRefusalOfAReplacedPhone_endsTheAlertOnThisPhone_withoutAMessage() =
        runTest {
            val w = world()
            w.device.replacedAfterRefusal = true
            w.store.starts += StartResult.Failed(AlertFailure.Refused)
            w.controller.trigger("miffA")
            runCurrent()
            assertTrue(w.controller.state.value is MyAlert.None)
            assertNull(w.local.pointer())
        }

    @Test
    fun aPhoneAlreadyReplaced_writesNothing() =
        runTest {
            val w = world()
            w.device.answer = Clearance.Replaced
            w.controller.trigger("miffA")
            runCurrent()
            assertTrue(w.controller.state.value is MyAlert.None)
            assertTrue(w.store.startCalls.isEmpty())
        }

    @Test
    fun sessionNotVerified_waitsAsOffline() =
        runTest {
            val w = world()
            w.device.answer = Clearance.Unverified
            w.controller.trigger("miffA")
            runCurrent()
            assertEquals(SendStep.WAITING_NETWORK, (w.controller.state.value as MyAlert.Sending).step)
            w.device.answer = Clearance.Cleared(DEVICE_A)
            advanceTimeBy(5_001)
            runCurrent()
            assertTrue(w.controller.state.value is MyAlert.Active)
        }

    @Test
    fun sharingStoppedInTheOriginMiff_nothingIsSent_andTheScreenIsTold() =
        runTest {
            val w = world()
            w.store.starts += StartResult.Failed(AlertFailure.SharingStopped)
            val collected = mutableListOf<AlertEvent>()
            val job = launchCollect(w, collected)
            w.controller.trigger("miffA")
            runCurrent()
            assertTrue(w.controller.state.value is MyAlert.None)
            assertEquals(listOf<AlertEvent>(AlertEvent.NotSent(AlertFailure.SharingStopped)), collected)
            job.cancel()
        }

    private fun TestScope.launchCollect(
        w: World,
        into: MutableList<AlertEvent>,
    ) = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { w.controller.events.collect { into += it } }

    @Test
    fun anAlertAlreadyActiveOnTheServer_isAdoptedNotWrittenAgain() =
        runTest {
            val w = world()
            val doc = AlertDoc("me", true, w.base - 1000, w.base + 100_000, 2)
            w.store.starts += StartResult.Adopted(doc)
            w.controller.trigger("miffA")
            runCurrent()
            val state = w.controller.state.value as MyAlert.Active
            assertEquals(doc.expiresAtMillis, state.expiresAtMillis)
        }

    // ---- ending ----------------------------------------------------------------------------------------------------------------------

    @Test
    fun endingWritesInTheOriginMiff_andTheAlertGoesOnlyAfterTheServerAccepted() =
        runTest {
            val w = world()
            w.controller.trigger("miffA")
            runCurrent()
            val events = mutableListOf<AlertEvent>()
            val job = launchCollect(w, events)
            w.controller.end()
            runCurrent()
            assertEquals(listOf("miffA"), w.store.endCalls)
            assertTrue(w.controller.state.value is MyAlert.None)
            assertNull(w.local.pointer())
            assertEquals(listOf<AlertEvent>(AlertEvent.Ended("miffA")), events)
            job.cancel()
        }

    @Test
    fun endingOffline_keepsTheAlertActive_withTheEndWaiting_thenEndsWhenTheNetworkReturns() =
        runTest {
            val w = world()
            w.controller.trigger("miffA")
            runCurrent()
            w.network.online.value = false
            w.controller.end()
            runCurrent()
            val waiting = w.controller.state.value as MyAlert.Active
            assertEquals(EndStep.WAITING_NETWORK, waiting.end)
            assertTrue(w.store.endCalls.isEmpty())
            w.network.online.value = true
            runCurrent()
            assertTrue(w.controller.state.value is MyAlert.None)
            assertEquals(1, w.store.endCalls.size)
        }

    @Test
    fun anEndThatTheServerRefuses_leavesTheAlertActive_andSaysItCalmly() =
        runTest {
            val w = world()
            w.controller.trigger("miffA")
            runCurrent()
            w.store.ends += EndResult.Failed(AlertFailure.Refused)
            val events = mutableListOf<AlertEvent>()
            val job = launchCollect(w, events)
            w.controller.end()
            runCurrent()
            val state = w.controller.state.value as MyAlert.Active
            assertEquals(EndStep.NONE, state.end)
            assertEquals(listOf<AlertEvent>(AlertEvent.EndNotSent), events)
            job.cancel()
        }

    @Test
    fun anAlertPastItsDate_justDisappears_noWriteNoEvent() =
        runTest {
            val w = world()
            w.controller.trigger("miffA")
            runCurrent()
            val events = mutableListOf<AlertEvent>()
            val job = launchCollect(w, events)
            advanceTimeBy(AlertRules.DURATION_MILLIS)
            w.controller.tick()
            assertTrue(w.controller.state.value is MyAlert.None)
            assertNull(w.local.pointer())
            assertTrue(w.store.endCalls.isEmpty())
            assertTrue(events.isEmpty())
            job.cancel()
        }

    // ---- scope, restart, new phone -------------------------------------------------------------------------------------------------

    @Test
    fun aRelaunchPicksUpAnActiveAlert_andAnAlertOnItsWay() =
        runTest {
            val w = world()
            w.local.save(AlertPointer("miffA", PointerPhase.ACTIVE, w.base, w.base, w.base + 1_000_000))
            w.controller.restore()
            assertEquals("miffA", (w.controller.state.value as MyAlert.Active).miffId)

            val w2 = world(online = false)
            w2.local.save(AlertPointer("miffB", PointerPhase.SENDING, w2.base))
            w2.controller.restore()
            runCurrent()
            assertEquals(SendStep.WAITING_NETWORK, (w2.controller.state.value as MyAlert.Sending).step)
            w2.controller.onSessionEnded()
        }

    @Test
    fun anExpiredPointerIsForgottenAtRelaunch() =
        runTest {
            val w = world()
            w.local.save(AlertPointer("miffA", PointerPhase.ACTIVE, w.base - 10_000, w.base - 10_000, w.base - 1))
            w.controller.restore()
            assertTrue(w.controller.state.value is MyAlert.None)
            assertNull(w.local.pointer())
        }

    // ---- giving up a sending (the cross of the banner), review of #275 m1 and the retour of the PM ----------------------------------

    @Test
    fun givingUpARefusedSending_freesThePersonAtOnce_andTheServerIsAskedBeforeThePointerGoes() =
        runTest {
            val w = world()
            w.store.starts += StartResult.Failed(AlertFailure.Refused)
            w.controller.trigger("miffA")
            runCurrent()
            w.controller.cancelPending()
            // nothing is shown, but the pointer waits for the server's word
            assertTrue(w.controller.state.value is MyAlert.None)
            runCurrent()
            assertEquals(listOf("miffA"), w.store.reads)
            assertNull(w.local.pointer())
            // no credit spent, none given back: nothing was written
            assertEquals(1, w.store.startCalls.size)
        }

    @Test
    fun givingUpASendingThatHadLanded_theAlertIsActiveAndTheBannerTakesOver() =
        runTest {
            val w = world()
            w.store.starts += StartResult.Failed(AlertFailure.Offline)
            w.controller.trigger("miffA")
            runCurrent()
            // the answer was lost but the write landed: the server holds a live alert
            w.store.mine = MineRead.Value(AlertDoc("me", true, w.base + 1_000, w.base + 500_000, 1))
            w.controller.cancelPending()
            runCurrent()
            val state = w.controller.state.value as MyAlert.Active
            assertEquals("miffA", state.miffId)
            assertEquals(PointerPhase.ACTIVE, w.local.pointer()?.phase)
        }

    @Test
    fun givingUpOffline_thePointerStays_andTheServerIsAskedWhenTheNetworkReturns() =
        runTest {
            val w = world(online = false)
            w.controller.trigger("miffA")
            runCurrent()
            w.controller.cancelPending()
            runCurrent()
            assertEquals(PointerPhase.ABANDONED, w.local.pointer()?.phase)
            assertTrue(w.store.reads.isEmpty())
            w.network.online.value = true
            runCurrent()
            assertEquals(listOf("miffA"), w.store.reads)
            assertNull(w.local.pointer())
        }

    @Test
    fun givingUp_theServerCannotBeReached_triesAgain_andARelaunchPicksTheCheckUp() =
        runTest {
            val w = world()
            w.store.starts += StartResult.Failed(AlertFailure.Refused)
            w.controller.trigger("miffA")
            runCurrent()
            w.store.mine = MineRead.Unavailable
            w.controller.cancelPending()
            runCurrent()
            assertEquals(PointerPhase.ABANDONED, w.local.pointer()?.phase)
            w.store.mine = MineRead.Value(null)
            advanceTimeBy(6_000)
            runCurrent()
            assertNull(w.local.pointer())
        }

    @Test
    fun anAbandonedPointerIsCheckedAtRelaunch() =
        runTest {
            val w = world()
            w.local.save(AlertPointer("miffA", PointerPhase.ABANDONED, w.base))
            w.controller.restore()
            runCurrent()
            assertEquals(listOf("miffA"), w.store.reads)
            assertNull(w.local.pointer())
        }

    @Test
    fun anAlertThatSentIsNotCloseable_whileAWriteIsInFlight() =
        runTest {
            val w = world()
            w.controller.trigger("miffA")
            // still SENDING (nothing ran yet): the cross does nothing
            w.controller.cancelPending()
            assertTrue(w.controller.state.value is MyAlert.Sending)
        }

    @Test
    fun aRefusedAlertDoesNotForbidANewOne_butAWaitingOrActiveOneDoes() =
        runTest {
            val w = world()
            w.store.starts += StartResult.Failed(AlertFailure.Refused)
            w.controller.trigger("miffA")
            runCurrent()
            assertEquals(SendStep.REFUSED, (w.controller.state.value as MyAlert.Sending).step)
            // a new hold is allowed (the daily limit and the rules decide, not the app)
            assertEquals(TriggerResult.Started, w.controller.trigger("miffB"))
            runCurrent()
            assertEquals("miffB", (w.controller.state.value as MyAlert.Active).miffId)
            // while one is active, a second never starts
            assertTrue(w.controller.trigger("miffC") is TriggerResult.AlreadyActive)
        }

    @Test
    fun theAlertOnlyGoesWhenTheLeavingOrTheLossIsConfirmed_neverBecauseAMiffIsMissingFromAList() =
        runTest {
            // review of #275 v2, 2: when the network jumps, a list of Miffs may miss one (a failed read): the controller has no way to end an alert from a list at all
            val w = world()
            w.controller.trigger("miffA")
            runCurrent()
            w.controller.adoptFromServer(listOf("miffB"))
            assertTrue(w.controller.state.value is MyAlert.Active)
            assertEquals(PointerPhase.ACTIVE, w.local.pointer()?.phase)
            // the repository tells the controller when the lot that stops the sharing, leaves or deletes the Miff of ANOTHER Miff went through: nothing happens
            w.controller.onAlertRemoved("miffB")
            assertTrue(w.controller.state.value is MyAlert.Active)
            // and when it is THIS Miff, the alert is gone with it
            w.controller.onAlertRemoved("miffA")
            assertTrue(w.controller.state.value is MyAlert.None)
            assertNull(w.local.pointer())
        }

    @Test
    fun givingUp_aMiffClosedToMe_stopsTheChecking_andTheOldAbandonedPointerIsForgotten() =
        runTest {
            val w = world()
            w.store.starts += StartResult.Failed(AlertFailure.Refused)
            w.controller.trigger("miffA")
            runCurrent()
            w.store.mine = MineRead.Denied
            w.controller.cancelPending()
            runCurrent()
            assertNull(w.local.pointer())
            assertEquals(1, w.store.reads.size)
            // an abandoned pointer older than the life of an alert is dropped without a reading
            w.local.save(AlertPointer("miffA", PointerPhase.ABANDONED, w.base - AlertRules.DURATION_MILLIS - 1))
            w.controller.restore()
            runCurrent()
            assertNull(w.local.pointer())
            assertEquals(1, w.store.reads.size)
        }

    @Test
    fun aPassingRefusalNeverRemovesTheBannerOfAnActiveAlert() =
        runTest {
            val w = world()
            w.controller.trigger("miffA")
            runCurrent()
            w.store.mine = MineRead.Denied
            w.controller.verifyActive()
            assertTrue(w.controller.state.value is MyAlert.Active)
        }

    @Test
    fun aNewPhone_readsMyAlertOfEachMiffOnce_andAdoptsOneInProgress() =
        runTest {
            val w = world()
            w.store.mine = MineRead.Value(AlertDoc("me", true, w.base - 5_000, w.base + 500_000, 1))
            w.controller.adoptFromServer(listOf("miffA", "miffB"))
            val state = w.controller.state.value as MyAlert.Active
            assertEquals("miffA", state.miffId)
            assertEquals("me", w.local.adoptedFor())
            // the next launch reads nothing
            val before = w.store.reads.size
            w.controller.onSessionEnded()
            w.controller.adoptFromServer(listOf("miffA", "miffB"))
            assertEquals(before, w.store.reads.size)
        }

    @Test
    fun adoption_nothingFound_isReadOncePerMiffAndRemembered() =
        runTest {
            val w = world()
            w.controller.adoptFromServer(listOf("miffA", "miffB"))
            assertEquals(listOf("miffA", "miffB"), w.store.reads)
            assertEquals("me", w.local.adoptedFor())
            assertTrue(w.controller.state.value is MyAlert.None)
        }

    @Test
    fun adoption_aMiffThatCouldNotBeRead_isTriedAgainAtTheNextLaunch() =
        runTest {
            val w = world()
            w.store.mine = MineRead.Unavailable
            w.controller.adoptFromServer(listOf("miffA"))
            assertNull(w.local.adoptedFor())
        }

    @Test
    fun backInFront_anAlertGoneFromTheServerLeavesTheBanner_aDoubtChangesNothing() =
        runTest {
            val w = world()
            w.controller.trigger("miffA")
            runCurrent()
            w.store.mine = MineRead.Unavailable
            w.controller.verifyActive()
            assertTrue(w.controller.state.value is MyAlert.Active)
            w.store.mine = MineRead.Value(null)
            w.controller.verifyActive()
            assertTrue(w.controller.state.value is MyAlert.None)
        }

    @Test
    fun noNameAndNoPositionIsKeptInThePointer() =
        runTest {
            val w = world()
            w.controller.trigger("miffA")
            runCurrent()
            val pointer = w.local.pointer()!!
            assertEquals("AlertPointer(redacted)", pointer.toString())
        }
}
