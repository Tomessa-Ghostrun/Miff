package com.tomessa.miff.domain.miff

/**
 * A help of the interface, NOT a protection (ticket Miff 5.3, criterion J5): after 5 failures in a row the person is asked to
 * wait one minute. The count starts again after a success, or when the last failure is older than 10 minutes. The real
 * protection is the entropy of the code and App Check; a modified client skips this pause (documented risk K8).
 *
 * Kept in memory for the session of the screen's owner: nothing about a code is stored.
 */
class JoinAttemptLimiter(
    private val nowMillis: () -> Long,
    private val maxFailures: Int = MAX_FAILURES,
    private val pauseMillis: Long = PAUSE_MILLIS,
    private val forgetAfterMillis: Long = FORGET_AFTER_MILLIS,
) {
    private var failures = 0
    private var lastFailureAt = 0L
    private var pausedUntil = 0L

    /** Milliseconds left of the pause, 0 when the person can try. */
    fun remainingPauseMillis(): Long = (pausedUntil - nowMillis()).coerceAtLeast(0L)

    fun isPaused(): Boolean = remainingPauseMillis() > 0L

    fun recordFailure() {
        val now = nowMillis()
        if (failures > 0 && now - lastFailureAt >= forgetAfterMillis) failures = 0
        failures++
        lastFailureAt = now
        if (failures >= maxFailures) {
            pausedUntil = now + pauseMillis
            // The pause is the sanction: the next series starts from zero once it is over.
            failures = 0
        }
    }

    fun recordSuccess() {
        failures = 0
        pausedUntil = 0L
    }

    companion object {
        const val MAX_FAILURES = 5
        const val PAUSE_MILLIS = 60_000L
        const val FORGET_AFTER_MILLIS = 10 * 60_000L
    }
}
