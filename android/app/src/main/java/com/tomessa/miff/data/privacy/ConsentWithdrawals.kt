package com.tomessa.miff.data.privacy

import com.tomessa.miff.data.auth.NetworkMonitor
import com.tomessa.miff.data.auth.ProfileWriteResult
import com.tomessa.miff.data.session.SessionTeardown
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The one place that decides what happens to a WITHDRAWAL of the "statistiques et rapports de plantage" consent
 * (ticket ONB-3b, decision of the PM "durcir le consentement").
 *
 * The rule: the person who switched the collection off is never collected again because a network was missing.
 * - [withdraw] marks the withdrawal on the device FIRST ([PendingWithdrawals], on disk), then tries to record it in the
 *   account. While it is marked, [com.tomessa.miff.data.session.SessionManager] keeps the collection OFF at every start,
 *   whatever the account still says.
 * - If the account could not be reached, a background task waits for the network (without blocking anything) and records the
 *   withdrawal as soon as it is back, with a growing pause between two tries. It stops at the first answer "no" of the server
 *   (rules, App Check: [ProfileWriteResult.REFUSED] or [ProfileWriteResult.FAILED]): it never loops. The collection then stays
 *   off, the screen keeps offering "Enregistrer mon choix", and the next start of the app tries once more.
 * - The mark belongs to ONE account: it is only ever recorded for that account, and only while that account is the one signed
 *   in. After a sign-out or a change of account it waits for its account to come back; it is never applied to another one.
 * - Accepting again before the withdrawal was recorded goes through [accept]: both operations share one lock, and the
 *   write of the acceptance cancels the mark only once the server has taken it.
 */
class ConsentWithdrawals(
    private val pending: PendingWithdrawals,
    private val consents: AnalyticsConsentRepository,
    private val network: NetworkMonitor,
    private val currentUid: () -> String?,
    private val scope: CoroutineScope,
    private val retryDelaysMillis: List<Long> = DEFAULT_DELAYS_MILLIS,
) : SessionTeardown {
    private val lock = Mutex()
    private var job: Job? = null

    /** Accounts for which the server said "no" in this run: no automatic try before the next start. */
    private val gaveUp = mutableSetOf<String>()

    fun isPending(uid: String): Boolean = pending.isPending(uid)

    fun pendingFlow(uid: String): Flow<Boolean> = pending.pendingFlow(uid)

    /** The person switched the collection off: mark it, try to record it, and keep trying in the background if needed. */
    suspend fun withdraw(uid: String): ProfileWriteResult {
        pending.mark(uid)
        gaveUp.remove(uid)
        return afterTry(uid, settle(uid))
    }

    /** "Enregistrer mon choix": one more try now. */
    suspend fun retryNow(uid: String): ProfileWriteResult {
        gaveUp.remove(uid)
        return afterTry(uid, settle(uid))
    }

    /** What follows a try made right now: wait and try again if the account could not be reached, stop if the server said no. */
    private fun afterTry(
        uid: String,
        result: ProfileWriteResult,
    ): ProfileWriteResult {
        when (result) {
            ProfileWriteResult.OFFLINE -> startBackground(uid, pauseFirst = true)
            ProfileWriteResult.REFUSED, ProfileWriteResult.FAILED -> gaveUp += uid
            ProfileWriteResult.DONE -> Unit
        }
        return result
    }

    /** The person accepts again: recorded in the account; only then is a pending withdrawal cancelled. */
    suspend fun accept(uid: String): ProfileWriteResult =
        lock.withLock {
            val result = consents.set(uid, enable = true)
            if (result == ProfileWriteResult.DONE) pending.clear(uid)
            result
        }

    /** The account [uid] was just read at sign-in or at the start: if a withdrawal waits for it, record it in the background. */
    fun resume(uid: String) {
        if (pending.isPending(uid) && uid !in gaveUp) startBackground(uid)
    }

    /** One try. DONE = recorded (or nothing left to record) and the mark is gone. */
    private suspend fun settle(uid: String): ProfileWriteResult =
        lock.withLock {
            when {
                !pending.isPending(uid) -> {
                    ProfileWriteResult.DONE
                }

                // The write goes in the name of the account that is signed in: never in the name of another one.
                currentUid() != uid -> {
                    ProfileWriteResult.OFFLINE
                }

                // No network: no doomed request (its answer would only come after a long wait).
                !network.isOnline() -> {
                    ProfileWriteResult.OFFLINE
                }

                else -> {
                    val result = consents.set(uid, enable = false)
                    if (result == ProfileWriteResult.DONE) pending.clear(uid)
                    result
                }
            }
        }

    private fun startBackground(
        uid: String,
        pauseFirst: Boolean = false,
    ) {
        if (job?.isActive == true) return
        job =
            scope.launch {
                var attempt = 0
                // A try has just failed: no new one before the first pause.
                if (pauseFirst) delay(pauseFor(attempt++))
                while (isActive && pending.isPending(uid) && currentUid() == uid) {
                    // Without a network there is nothing to try: wait for it (no polling).
                    if (!network.isOnline() &&
                        network.onlineChanges().firstOrNull { it } == null
                    ) {
                        delay(pauseFor(attempt))
                    }
                    when (settle(uid)) {
                        ProfileWriteResult.DONE -> {
                            return@launch
                        }

                        ProfileWriteResult.OFFLINE -> {
                            attempt++
                            delay(pauseFor(attempt - 1))
                        }

                        // The server said "no": the collection stays off, no loop.
                        ProfileWriteResult.REFUSED, ProfileWriteResult.FAILED -> {
                            gaveUp += uid
                            return@launch
                        }
                    }
                }
            }
    }

    private fun pauseFor(attempt: Int): Long = retryDelaysMillis[attempt.coerceAtMost(retryDelaysMillis.lastIndex)]

    /** End of the session: the background task stops; the mark stays and waits for its account. */
    override suspend fun stop() {
        job?.cancel()
        job = null
        gaveUp.clear()
    }

    companion object {
        val DEFAULT_DELAYS_MILLIS = listOf(5_000L, 15_000L, 60_000L, 300_000L)
    }
}
