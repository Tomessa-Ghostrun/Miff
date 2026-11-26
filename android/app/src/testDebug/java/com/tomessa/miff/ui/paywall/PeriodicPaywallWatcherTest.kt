package com.tomessa.miff.ui.paywall

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.tomessa.miff.domain.paywall.MonotonicClock
import com.tomessa.miff.domain.paywall.PeriodicPaywallCounterStore
import com.tomessa.miff.domain.paywall.PeriodicPaywallRules
import com.tomessa.miff.domain.paywall.PeriodicPaywallSituation
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The watcher that opens the offer (criteria PP9, PP10, PP12, PP15): with the clock of Compose itself (virtual time), the wait of the calm moment, the cancellation by a touch, the situations that
 * stop it and the single opening are tested without waiting for anything real.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PeriodicPaywallWatcherTest {
    @get:Rule
    val composeRule = createComposeRule()

    private class MemoryStore(
        var value: Int = 10,
    ) : PeriodicPaywallCounterStore {
        override fun read() = value

        override fun write(count: Int) {
            value = count
        }

        override fun clear() {
            value = 0
        }
    }

    private val store = MemoryStore()
    private val rules = PeriodicPaywallRules(store, MonotonicClock { composeRule.mainClock.currentTime })
    private var opened = 0

    private val calm =
        PeriodicPaywallSituation(
            miffPlus = false,
            tabsInFront = true,
            updateScreenOpen = false,
            keyboardOpen = false,
            messageOpen = false,
            ownAlertInProgress = false,
            lovedOneAlertUnseen = false,
        )
    private var situation by mutableStateOf(calm)

    private fun host(initial: PeriodicPaywallSituation = calm) {
        // Virtual time moved by hand: nothing is skipped by itself.
        composeRule.mainClock.autoAdvance = false
        situation = initial
        rules.onForeground()
        rules.onShellShown()
        composeRule.setContent {
            PeriodicPaywallWatcher(host = PeriodicPaywallHost(rules, open = { opened++ }), situation = situation)
        }
        composeRule.waitForIdle()
    }

    private fun tap() {
        composeRule.runOnIdle {
            rules.onTouch(pressed = true)
            rules.onActivation()
            rules.onTouch(pressed = false)
        }
    }

    /** Time passes frame by frame, so that a change of the screen is composed BEFORE a wait that ends later in the same stretch (one big jump would run the wait first). */
    private fun pass(millis: Long) {
        var left = millis
        while (left > 0) {
            val step = minOf(left, FRAME_MILLIS)
            composeRule.mainClock.advanceTimeBy(step)
            // Idle after EACH step: the composition of a change of the screen and the restart of the effect run before time goes on.
            composeRule.waitForIdle()
            left -= step
        }
    }

    private companion object {
        const val FRAME_MILLIS = 16L
    }

    @Test
    fun withoutATap_nothingOpens_whateverTheTime() {
        host()
        pass(10 * 60_000L)
        assertEquals(0, opened)
    }

    @Test
    fun afterATap_theOfferOpensOnceTheScreenWasLeftAloneForOneAndAHalfSecond() {
        host()
        tap()
        pass(1_400L)
        assertEquals("not yet", 0, opened)
        pass(200L)
        assertEquals(1, opened)
    }

    @Test
    fun aTouchDuringTheWait_startsTheWaitAgain() {
        host()
        tap()
        pass(1_000L)
        composeRule.runOnIdle { rules.onTouch(pressed = false) }
        pass(1_000L)
        assertEquals("the touch moved the moment", 0, opened)
        pass(600L)
        assertEquals(1, opened)
    }

    @Test
    fun itOpensOnlyOnce_evenAfterManyChangesOfTheScreen() {
        host()
        tap()
        pass(2_000L)
        assertEquals(1, opened)
        composeRule.runOnIdle { situation = calm.copy(keyboardOpen = true) }
        composeRule.runOnIdle { situation = calm }
        pass(10_000L)
        assertEquals(1, opened)
    }

    @Test
    fun aSituationThatForbidsIt_stopsIt_andItComesWhenTheSituationEnds() {
        host(calm.copy(updateScreenOpen = true))
        tap()
        pass(10_000L)
        assertEquals(0, opened)
        assertEquals("the counter is untouched", 10, store.value)
        composeRule.runOnIdle { situation = calm }
        pass(100L)
        assertEquals("tapped and calm for a long time: it comes at once", 1, opened)
    }

    @Test
    fun miffPlusFoundDuringTheWait_cancelsIt() {
        host()
        tap()
        pass(1_000L)
        composeRule.runOnIdle { situation = calm.copy(miffPlus = true) }
        pass(5_000L)
        assertEquals(0, opened)
        assertEquals("the counter went back to zero", 0, store.value)
    }

    @Test
    fun anUnknownRight_waits_thenItComesWhenKnown() {
        host(calm.copy(miffPlus = null))
        tap()
        pass(5_000L)
        assertEquals(0, opened)
        composeRule.runOnIdle { situation = calm }
        pass(100L)
        assertEquals(1, opened)
    }

    @Test
    fun anOverlayOpenOverTheTabs_holdsItBack_andItComesOneAndAHalfSecondAfterTheClosing() {
        host()
        tap()
        composeRule.runOnIdle { rules.onOverlayOpened() }
        pass(30_000L)
        assertEquals("a menu or a sheet is open: nothing", 0, opened)
        composeRule.runOnIdle { rules.onOverlayClosed() }
        pass(1_400L)
        assertEquals(0, opened)
        pass(200L)
        assertEquals(1, opened)
    }

    @Test
    fun aFingerKeptOnTheScreen_holdsItBack() {
        host()
        composeRule.runOnIdle {
            rules.onTouch(pressed = true)
            rules.onActivation()
        }
        pass(30_000L)
        assertEquals(0, opened)
        composeRule.runOnIdle { rules.onTouch(pressed = false) }
        pass(1_600L)
        assertEquals(1, opened)
    }
}
