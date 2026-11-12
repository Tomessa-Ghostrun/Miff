package com.tomessa.miff.ui.paywall

import com.tomessa.miff.domain.paywall.MonotonicClock
import kotlinx.coroutines.CompletableDeferred

/**
 * A clock moved by hand: [advance] makes time pass AND wakes the waits that are due, so that the clock and the wait of the countdown always agree
 * (nothing waits for real time, and Compose's own clock is left alone).
 */
class FakePaywallClock(
    var now: Long = 10_000L,
) : MonotonicClock {
    private val waiters = mutableListOf<Pair<Long, CompletableDeferred<Unit>>>()

    override fun nowMillis(): Long = now

    override suspend fun waitFor(millis: Long) {
        val wake = CompletableDeferred<Unit>()
        synchronized(waiters) { waiters += (now + millis) to wake }
        wake.await()
    }

    fun advance(millis: Long) {
        now += millis
        val due = synchronized(waiters) { waiters.filter { it.first <= now }.also { waiters.removeAll(it) } }
        due.forEach { it.second.complete(Unit) }
    }
}
