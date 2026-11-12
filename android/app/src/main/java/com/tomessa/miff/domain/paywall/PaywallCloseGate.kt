package com.tomessa.miff.domain.paywall

import kotlinx.coroutines.delay

/**
 * THE delay before the close cross of the offer (the paywall) appears. A single value, in one place (decision of the PM, 2026-11-12;
 * ticket docs/tickets/paywall-v2.md section 6, criterion CR12): changing it, or putting 0 to remove the rule, is a one-line pull request,
 * never a refactoring. It is a fixed value of the app: no remote setting.
 *
 * Risk assumed by the PM (store review, reviews, consumer law): see section 7 of the ticket. Before a store submission, the release
 * manager rereads the policies of Apple and Google on this point.
 */
object PaywallCloseDelay {
    const val MILLIS: Long = 3_000L
}

/**
 * A clock that only goes forward and keeps counting while the app is in the background (on Android: [android.os.SystemClock.elapsedRealtime],
 * not the wall clock that a person or the network can change). Injected so that no test waits for 3 real seconds.
 */
fun interface MonotonicClock {
    fun nowMillis(): Long

    /** Waits [millis] (production: the coroutine `delay`). Tests that move a fake clock by hand also give their own wait, so that both agree. */
    suspend fun waitFor(millis: Long) {
        delay(millis)
    }
}

/**
 * The rule of the close cross, without any Android: one gate per DISPLAY of the offer (never shared between two displays, criterion CR4).
 *
 * - The countdown starts at [onDisplayed]: the moment the screen is really on view and the app is in the foreground, NOT when the screen
 *   is created or when the navigation starts (criterion CR3). It starts even if the prices are still loading.
 * - The time is the REAL time elapsed on [clock]: a short trip to the background does not stop it; on return, if the delay is over, the cross is there.
 * - Once the cross has appeared it STAYS (latched): a rotation, a change of text size, language or theme, a return from the background never
 *   hide it and never start the delay again (criterion CR5).
 */
class PaywallCloseGate(
    private val clock: MonotonicClock,
    private val delayMillis: Long = PaywallCloseDelay.MILLIS,
) {
    private var shownAt: Long? = null
    private var latched = false

    /** True once the screen has been shown for real. */
    val isDisplayed: Boolean get() = shownAt != null

    /** Marks the effective display. Returns true the FIRST time only (the moment of the `paywall_shown` event); later calls change nothing. */
    fun onDisplayed(): Boolean {
        if (shownAt != null) return false
        shownAt = clock.nowMillis()
        return true
    }

    /** The wait of the delay has just ended (the caller waited the time that was asked): the cross is available, for good. */
    fun markElapsed() {
        latched = true
    }

    /** True once the delay is over (and ever after). A delay of 0 or less is over at once, even before the display (the rule is off). */
    fun isCloseAvailable(): Boolean {
        if (latched) return true
        if (delayMillis <= 0L) {
            latched = true
            return true
        }
        val start = shownAt ?: return false
        if (clock.nowMillis() - start >= delayMillis) {
            latched = true
        }
        return latched
    }

    /** Milliseconds left before the cross appears: 0 when it is already there, the whole delay before the display. */
    fun remainingMillis(): Long {
        if (isCloseAvailable()) return 0L
        val start = shownAt ?: return delayMillis
        return (delayMillis - (clock.nowMillis() - start)).coerceIn(1L, delayMillis)
    }
}
