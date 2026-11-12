package com.tomessa.miff.ui.paywall

import androidx.lifecycle.ViewModel
import com.tomessa.miff.domain.paywall.MonotonicClock
import com.tomessa.miff.domain.paywall.PaywallCloseDelay
import com.tomessa.miff.domain.paywall.PaywallCloseGate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The state of ONE display of the offer (the rule of the close cross, ticket paywall-v2 section 6).
 *
 * It lives as long as the navigation entry of the screen: a rotation, a change of language or theme keeps it (the cross stays once it
 * has appeared, the delay does not start again, `paywall_shown` is not sent twice, the announcement is not repeated); closing the screen
 * and opening it again creates a NEW one (a new countdown, nothing shared). If the system kills the app and restores the screen, the
 * entry is new too: 3 seconds again (choice of simplicity, written in the pull request).
 */
class PaywallViewModel(
    private val clock: MonotonicClock,
    delayMillis: Long = PaywallCloseDelay.MILLIS,
) : ViewModel() {
    private val gate = PaywallCloseGate(clock, delayMillis)
    private val mutableCloseAvailable = MutableStateFlow(gate.isCloseAvailable())

    // With a delay of 0 (the rule is off) the cross is there from the start: nothing "appears", so nothing is announced.
    private var announced = mutableCloseAvailable.value

    /** True from the moment the cross may appear (and ever after, until the end of this display). */
    val closeAvailable: StateFlow<Boolean> = mutableCloseAvailable.asStateFlow()

    /** The screen is really on view (foreground, end of its entry animation). True the first time only: the moment of `paywall_shown`. */
    fun onDisplayed(): Boolean = gate.onDisplayed()

    /**
     * Waits for the end of the delay, then lets the cross appear. Run it while the screen is in the foreground (it is cancelled when it
     * leaves and started again when it comes back: the time is read again from the clock, which kept counting).
     */
    suspend fun runCountdown() {
        // ONE wait of what is left (read from the clock, which kept counting if the screen left and came back), then the delay is over: a wait that
        // ends is trusted, so that nothing can spin here.
        if (!gate.isCloseAvailable()) {
            clock.waitFor(gate.remainingMillis())
            gate.markElapsed()
        }
        mutableCloseAvailable.value = true
    }

    /** True ONCE, for the screen reader announcement "Tu peux maintenant fermer cet écran" (never again after a rotation). */
    fun takeAnnouncement(): Boolean {
        if (announced) return false
        announced = true
        return true
    }
}
