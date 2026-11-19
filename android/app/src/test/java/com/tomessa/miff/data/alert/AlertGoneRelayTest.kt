package com.tomessa.miff.data.alert

import com.tomessa.miff.data.auth.NetworkMonitor
import com.tomessa.miff.data.miff.MiffDeleter
import com.tomessa.miff.data.miff.MiffDeletionCoordinator
import com.tomessa.miff.data.miff.MiffDeletionResult
import com.tomessa.miff.testsupport.FakeActiveDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A Miff deleted by its creator, with an alert of the creator in it (review of #275 v4, 2): the deletion runs on a BACKGROUND thread and ends in the relay, which posts to the thread of the
 * controller; the banner goes, the pointer goes, and a new alert is possible in another Miff. Real coordinator, real controller.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AlertGoneRelayTest {
    private class OkStore : AlertStore {
        override suspend fun start(
            uid: String,
            miffId: String,
            deviceId: String,
            nowMillis: Long,
        ): StartResult =
            StartResult.Started(
                nowMillis,
                nowMillis + 1_000_000,
            )

        override suspend fun end(
            uid: String,
            miffId: String,
            deviceId: String,
            nowMillis: Long,
        ): EndResult = EndResult.Ended

        override suspend fun readMine(
            uid: String,
            miffId: String,
        ): MineRead = MineRead.Value(null)

        override fun observe(miffId: String): Flow<AlertsHeard> = emptyFlow()
    }

    private class Online : NetworkMonitor {
        override fun isOnline() = true
    }

    private fun TestScope.world(): Triple<AlertController, MemoryAlertLocalStore, AlertGoneRelay> {
        val local = MemoryAlertLocalStore()
        val mainLike = CoroutineScope(StandardTestDispatcher(testScheduler))
        val controller = AlertController(OkStore(), FakeActiveDevice(), Online(), local, { "me" }, mainLike)
        return Triple(controller, local, AlertGoneRelay(mainLike) { controller.onAlertRemoved(it) })
    }

    @Test
    fun theCreatorDeletesTheMiffOfHerAlert_theBannerGoes_andANewAlertIsPossibleElsewhere() =
        runTest {
            val (controller, local, relay) = world()
            controller.trigger("X")
            runCurrent()
            assertTrue(controller.state.value is MyAlert.Active)
            val coordinator =
                MiffDeletionCoordinator(
                    deletion = MiffDeleter { _, _ -> MiffDeletionResult.Deleted },
                    pending =
                        com.tomessa.miff.testsupport
                            .MemoryPendingDeletions(),
                    network = Online(),
                    currentUid = { "me" },
                    scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
                    onStarted = {},
                    // the same wiring as AppContainer.onMiffLeft: the relay
                    onDeleted = { relay.miffGone(it) },
                )
            coordinator.start("X")
            runCurrent()
            assertTrue(controller.state.value is MyAlert.None)
            assertNull(local.pointer())
            assertEquals(TriggerResult.Started, controller.trigger("Y"))
        }

    @Test
    fun theLossOfAnotherMiff_leavesTheAlertAlone() =
        runTest {
            val (controller, local, relay) = world()
            controller.trigger("A")
            runCurrent()
            relay.miffGone("B")
            runCurrent()
            assertEquals("A", (controller.state.value as MyAlert.Active).miffId)
            assertEquals(PointerPhase.ACTIVE, local.pointer()?.phase)
        }

    @Test
    fun aPointerThatWasNeverPickedUp_isErasedWhenItsMiffIsLost() =
        runTest {
            val (controller, local, relay) = world()
            // the process restarted: the pointer is on the disk, restore() has not run yet
            local.save(AlertPointer("X", PointerPhase.ACTIVE, 1, 1, Long.MAX_VALUE))
            relay.miffGone("X")
            runCurrent()
            controller.restore()
            assertTrue(controller.state.value is MyAlert.None)
            assertNull(local.pointer())
        }

    @Test
    fun anAbandonedSending_ofALostMiff_isForgotten_andItsCheckStops() =
        runTest {
            val (controller, local, relay) = world()
            local.save(AlertPointer("X", PointerPhase.ABANDONED, 1))
            relay.miffGone("X")
            runCurrent()
            assertNull(local.pointer())
            controller.restore()
            runCurrent()
            assertTrue(controller.state.value is MyAlert.None)
        }
}
