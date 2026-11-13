package com.tomessa.miff.domain.paywall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rule of the close cross without any phone (criteria CR1 to CR5, CR12): the time comes from a clock the test moves by hand. */
class PaywallCloseGateTest {
    private class FakeClock(
        var now: Long = 10_000L,
    ) : MonotonicClock {
        override fun nowMillis() = now
    }

    @Test
    fun theDelayIsThreeSeconds_inOneSingleValue() {
        assertEquals(3_000L, PaywallCloseDelay.MILLIS)
    }

    @Test
    fun beforeTheDisplay_theCrossIsNotAvailable_whateverTheTime() {
        val clock = FakeClock()
        val gate = PaywallCloseGate(clock)
        clock.now += 60_000
        assertFalse(gate.isDisplayed)
        assertFalse("the screen is created but not on view: no countdown", gate.isCloseAvailable())
        assertEquals(PaywallCloseDelay.MILLIS, gate.remainingMillis())
    }

    @Test
    fun theCountdownStartsAtTheDisplay_notAtTheCreation() {
        val clock = FakeClock()
        val gate = PaywallCloseGate(clock)
        clock.now += 5_000 // created, then slow navigation: this time does not count
        assertTrue(gate.onDisplayed())
        clock.now += 2_999
        assertFalse(gate.isCloseAvailable())
        assertEquals(1L, gate.remainingMillis())
        clock.now += 1
        assertTrue(gate.isCloseAvailable())
        assertEquals(0L, gate.remainingMillis())
    }

    @Test
    fun onDisplayedIsTrueOnce_andNeverRestartsTheCountdown() {
        val clock = FakeClock()
        val gate = PaywallCloseGate(clock)
        assertTrue(gate.onDisplayed())
        clock.now += 2_000
        assertFalse("a second display call (a return from the background) changes nothing", gate.onDisplayed())
        clock.now += 1_000
        assertTrue("3 s after the FIRST display", gate.isCloseAvailable())
    }

    @Test
    fun theTimeKeepsCountingWhileTheAppIsAway() {
        val clock = FakeClock()
        val gate = PaywallCloseGate(clock)
        gate.onDisplayed()
        clock.now += 1_000
        assertEquals(2_000L, gate.remainingMillis())
        clock.now += 10_000 // in the background, nobody asks anything
        assertTrue(gate.isCloseAvailable())
    }

    @Test
    fun onceAvailable_itStays_evenIfTheClockMovedBackwards() {
        val clock = FakeClock()
        val gate = PaywallCloseGate(clock)
        gate.onDisplayed()
        clock.now += 3_000
        assertTrue(gate.isCloseAvailable())
        clock.now -= 50_000
        assertTrue("latched: a rotation or a clock oddity never hides it again", gate.isCloseAvailable())
        assertEquals(0L, gate.remainingMillis())
    }

    @Test
    fun twoGatesShareNothing_aNewDisplayStartsFromZero() {
        val clock = FakeClock()
        val first = PaywallCloseGate(clock)
        first.onDisplayed()
        clock.now += 4_000
        assertTrue(first.isCloseAvailable())
        val second = PaywallCloseGate(clock)
        second.onDisplayed()
        assertFalse(second.isCloseAvailable())
        clock.now += 3_000
        assertTrue(second.isCloseAvailable())
    }

    @Test
    fun aDelayOfZero_switchesTheRuleOff_evenBeforeTheDisplay() {
        val gate = PaywallCloseGate(FakeClock(), delayMillis = 0)
        assertTrue(gate.isCloseAvailable())
        assertEquals(0L, gate.remainingMillis())
    }

    @Test
    fun aDifferentDelayIsHonoured() {
        val clock = FakeClock()
        val gate = PaywallCloseGate(clock, delayMillis = 1_000)
        gate.onDisplayed()
        clock.now += 999
        assertFalse(gate.isCloseAvailable())
        clock.now += 1
        assertTrue(gate.isCloseAvailable())
    }
}
