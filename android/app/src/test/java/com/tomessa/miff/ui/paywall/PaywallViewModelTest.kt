package com.tomessa.miff.ui.paywall

import com.tomessa.miff.domain.paywall.MonotonicClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** One display of the offer: the countdown, what survives a rotation, the announcement (criteria CR2 to CR5, CR8, CR12). Virtual time, nothing waits. */
@OptIn(ExperimentalCoroutinesApi::class)
class PaywallViewModelTest {
    @Test
    fun theCrossAppearsExactlyAfterTheDelay() =
        runTest {
            val model = PaywallViewModel(MonotonicClock { currentTime })
            assertFalse(model.closeAvailable.value)
            model.onDisplayed()
            val job = launch { model.runCountdown() }
            runCurrent()
            advanceTimeBy(2_999)
            runCurrent()
            assertFalse("at 2.999 s: absent", model.closeAvailable.value)
            advanceTimeBy(1)
            runCurrent()
            assertTrue("at 3.000 s: there", model.closeAvailable.value)
            job.join()
        }

    @Test
    fun theCountdownWaitsForTheDisplay() =
        runTest {
            val model = PaywallViewModel(MonotonicClock { currentTime })
            // Nobody called onDisplayed: the screen is not on view. Nothing starts: the countdown is only run by the screen once it is on view.
            assertFalse(model.closeAvailable.value)
            advanceTimeBy(10_000)
            assertFalse(model.closeAvailable.value)
        }

    @Test
    fun aTripToTheBackground_doesNotStopTheRealTime() =
        runTest {
            val model = PaywallViewModel(MonotonicClock { currentTime })
            model.onDisplayed()
            val first = launch { model.runCountdown() }
            advanceTimeBy(1_000)
            runCurrent()
            first.cancel() // the screen left the foreground: the coroutine is cancelled
            advanceTimeBy(5_000) // time goes by
            runCurrent()
            assertFalse("nothing runs in the background", model.closeAvailable.value)
            val second = launch { model.runCountdown() } // back in the foreground
            runCurrent()
            assertTrue("the clock kept counting: the cross is there on return", model.closeAvailable.value)
            second.join()
        }

    @Test
    fun aTripBeforeTheEnd_onlyWaitsForTheRest() =
        runTest {
            val model = PaywallViewModel(MonotonicClock { currentTime })
            model.onDisplayed()
            val first = launch { model.runCountdown() }
            advanceTimeBy(1_000)
            runCurrent()
            first.cancel()
            val second = launch { model.runCountdown() }
            runCurrent()
            advanceTimeBy(1_999)
            runCurrent()
            assertFalse(model.closeAvailable.value)
            advanceTimeBy(1)
            runCurrent()
            assertTrue("3 s after the first display, not 3 s after the return", model.closeAvailable.value)
            second.join()
        }

    @Test
    fun onceThere_itStays_andANewCountdownIsInstant() =
        runTest {
            val model = PaywallViewModel(MonotonicClock { currentTime })
            assertTrue(model.onDisplayed())
            val job = launch { model.runCountdown() }
            advanceTimeBy(3_000)
            runCurrent()
            job.join()
            assertTrue(model.closeAvailable.value)
            // A rotation: the screen starts its effect again on the same model.
            assertFalse("paywall_shown is not sent twice", model.onDisplayed())
            val again = launch { model.runCountdown() }
            runCurrent()
            assertTrue(again.isCompleted)
            assertTrue(model.closeAvailable.value)
        }

    @Test
    fun theAnnouncementIsGivenOnce_neverAgainAfterARotation() =
        runTest {
            val model = PaywallViewModel(MonotonicClock { currentTime })
            model.onDisplayed()
            val job = launch { model.runCountdown() }
            advanceTimeBy(3_000)
            runCurrent()
            job.join()
            assertTrue(model.takeAnnouncement())
            assertFalse(model.takeAnnouncement())
        }

    @Test
    fun withADelayOfZero_theCrossIsThereAtOnce_andNothingIsAnnounced() =
        runTest {
            val model = PaywallViewModel(MonotonicClock { currentTime }, delayMillis = 0)
            assertTrue(model.closeAvailable.value)
            assertFalse("nothing appears, so nothing is announced", model.takeAnnouncement())
        }

    @Test
    fun twoDisplays_shareNothing() =
        runTest {
            val clock = MonotonicClock { currentTime }
            val first = PaywallViewModel(clock)
            first.onDisplayed()
            val job = launch { first.runCountdown() }
            advanceTimeBy(3_000)
            runCurrent()
            job.join()
            assertTrue(first.closeAvailable.value)
            val second = PaywallViewModel(clock)
            assertFalse(second.closeAvailable.value)
            assertTrue(second.onDisplayed())
            assertFalse(second.closeAvailable.value)
        }
}
