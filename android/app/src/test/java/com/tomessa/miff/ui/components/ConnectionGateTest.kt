package com.tomessa.miff.ui.components

import com.tomessa.miff.testsupport.FakeNetwork
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pop-in "Pas de connexion": when it appears, the attempt every 10 s (virtual clock), the return of the network, "Réessayer", the exit of the screen. */
@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionGateTest {
    private val network = FakeNetwork(online = false)
    private val gate = ConnectionGate(network)
    private val loading = MutableStateFlow(true)
    private val unavailable = MutableStateFlow(false)
    private var retries = 0

    private fun kotlinx.coroutines.test.TestScope.watching() = launch { gate.run(loading, unavailable) { retries++ } }

    @Test
    fun offlineWithNothingToShow_thePopInAppearsAfterAMoment_notAtOnce() =
        runTest {
            val job = watching()
            runCurrent()
            assertFalse(gate.state.value.popInVisible)
            advanceTimeBy(1_600)
            assertTrue(gate.state.value.popInVisible)
            assertEquals(10, gate.state.value.secondsLeft)
            job.cancel()
        }

    @Test
    fun offline_theTimerTurnsEvery10Seconds_andReadsNothing() =
        runTest {
            val job = watching()
            advanceTimeBy(1_500 + 10 * 10_000L + 500)
            // Ten rounds of the timer, no network: not one attempt, so not one reading.
            assertEquals(0, retries)
            assertTrue(gate.state.value.popInVisible)
            job.cancel()
        }

    @Test
    fun theCountdownGoesDownOneSecondAtATime() =
        runTest {
            val job = watching()
            advanceTimeBy(1_500 + 3_100)
            assertEquals(7, gate.state.value.secondsLeft)
            job.cancel()
        }

    @Test
    fun theNetworkComesBack_oneAttemptAtOnce_andThePopInLeavesByItself() =
        runTest {
            val job = watching()
            advanceTimeBy(2_000)
            assertTrue(gate.state.value.popInVisible)
            network.online = true
            runCurrent()
            assertEquals(1, retries)
            assertFalse(gate.state.value.active)
            job.cancel()
        }

    @Test
    fun theNetworkComesBack_whileTheScreenIsUnavailableAndNotLoading_oneAttemptAtOnce_review149() =
        runTest {
            // First installation / data cleared, then airplane mode: the screen is "unavailable" and no longer "loading".
            loading.value = false
            unavailable.value = true
            val job = watching()
            advanceTimeBy(2_000)
            assertTrue(gate.state.value.popInVisible)
            assertEquals(0, retries)
            network.online = true
            runCurrent()
            // Before the fix the pop-in was cleared and the gate left WITHOUT any attempt.
            assertEquals(1, retries)
            assertFalse(gate.state.value.active)
            // With a network, an "unavailable" screen has its own sentence and "Réessayer": no loop of attempts from the gate.
            advanceTimeBy(120_000)
            assertEquals(1, retries)
            job.cancel()
        }

    @Test
    fun theNetworkComesBack_whileTheScreenHasItsContent_noAttempt() =
        runTest {
            loading.value = false
            unavailable.value = false
            val job = watching()
            runCurrent()
            network.online = true
            runCurrent()
            assertEquals(0, retries)
            assertFalse(gate.state.value.active)
            job.cancel()
        }

    @Test
    fun theNetworkComesBackAndTheContentArrives_nothingMoreHappens() =
        runTest {
            val job = watching()
            advanceTimeBy(2_000)
            network.online = true
            runCurrent()
            loading.value = false
            advanceTimeBy(120_000)
            assertEquals(1, retries)
            assertFalse(gate.state.value.active)
            job.cancel()
        }

    @Test
    fun theNetworkComesBackButNothingArrives_thePopInComesBackAfterAMoment_withTheAttemptsBounded() =
        runTest {
            val job = watching()
            advanceTimeBy(2_000)
            network.online = true
            runCurrent()
            assertEquals(1, retries)
            // A network that answers nothing: the pop-in waits 8 s, then 3 automatic attempts at most (every 10 s), then only the button.
            advanceTimeBy(7_900)
            assertFalse(gate.state.value.active)
            advanceTimeBy(200)
            assertTrue(gate.state.value.popInVisible)
            advanceTimeBy(10 * 10_000L)
            assertEquals(1 + 3, retries)
            assertNull(gate.state.value.secondsLeft)
            job.cancel()
        }

    @Test
    fun slowButOnline_thePopInAppearsAfterAShortDelay() =
        runTest {
            network.online = true
            val job = watching()
            advanceTimeBy(7_900)
            assertFalse(gate.state.value.popInVisible)
            advanceTimeBy(200)
            assertTrue(gate.state.value.popInVisible)
            job.cancel()
        }

    @Test
    fun contentThatArrivesQuickly_neverShowsThePopIn() =
        runTest {
            network.online = true
            val job = watching()
            advanceTimeBy(3_000)
            loading.value = false
            advanceTimeBy(60_000)
            assertFalse(gate.state.value.active)
            assertEquals(0, retries)
            job.cancel()
        }

    @Test
    fun contentThatArrivesWhileThePopInIsUp_makesItLeaveByItself() =
        runTest {
            val job = watching()
            advanceTimeBy(2_000)
            assertTrue(gate.state.value.popInVisible)
            loading.value = false
            runCurrent()
            assertFalse(gate.state.value.active)
            job.cancel()
        }

    @Test
    fun retryNow_startsTheCountdownAgainFromTheTop_withoutMakingThePopInBlink() =
        runTest {
            val job = watching()
            advanceTimeBy(1_500 + 6_100)
            assertEquals(4, gate.state.value.secondsLeft)
            gate.restartCountdown()
            runCurrent()
            assertTrue(gate.state.value.popInVisible)
            assertEquals(10, gate.state.value.secondsLeft)
            job.cancel()
        }

    @Test
    fun closingThePopIn_keepsTheAttemptsGoing_andTheScreenSaysItInItsPage() =
        runTest {
            network.online = true
            val job = watching()
            advanceTimeBy(8_100)
            gate.dismiss()
            assertFalse(gate.state.value.popInVisible)
            assertTrue(gate.state.value.inlineVisible)
            advanceTimeBy(10_000)
            assertEquals(1, retries)
            // Closed: no countdown is published any more (nobody sees it; no useless recomposition every second).
            assertNull(gate.state.value.secondsLeft)
            job.cancel()
        }

    @Test
    fun leavingTheScreen_stopsEverything_noMoreAttemptsAndNoPopIn() =
        runTest {
            network.online = true
            val job = watching()
            advanceTimeBy(8_100)
            assertTrue(gate.state.value.popInVisible)
            job.cancel()
            runCurrent()
            assertFalse(gate.state.value.active)
            advanceTimeBy(120_000)
            assertEquals(0, retries)
        }

    @Test
    fun unavailableWithANetwork_isLeftToTheScreenItself_noPopIn() =
        runTest {
            network.online = true
            loading.value = false
            unavailable.value = true
            val job = watching()
            advanceTimeBy(60_000)
            assertFalse(gate.state.value.active)
            assertEquals(0, retries)
            job.cancel()
        }

    @Test
    fun unavailableWithoutNetwork_countsAsStuck() =
        runTest {
            loading.value = false
            unavailable.value = true
            val job = watching()
            advanceTimeBy(2_000)
            assertTrue(gate.state.value.popInVisible)
            job.cancel()
        }
}
