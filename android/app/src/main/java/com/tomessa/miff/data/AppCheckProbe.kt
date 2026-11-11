package com.tomessa.miff.data

import com.google.firebase.appcheck.FirebaseAppCheck
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * AC-4 (lot B): "does this phone hold an App Check token?", asked ONLY after the server refused something, to tell "this device is not
 * recognized" from a refusal of the rules (see [com.tomessa.miff.domain.miff.refineRefusal]). It reads no personal data and writes nothing.
 */
fun interface AppCheckProbe {
    /**
     * `true` ONLY when the SDK answered that it cannot give a token. On a doubt (no answer in time, anything unexpected) it answers
     * `false`: the person then gets the answer of before, never a new sentence on a guess.
     */
    suspend fun tokenIsMissing(): Boolean

    companion object {
        /** No check at all (tests, and every place that does not need the distinction). */
        val None = AppCheckProbe { false }
    }
}

/**
 * Asks the App Check SDK for its token WITHOUT forcing a refresh: a token still valid is returned at once, from memory; otherwise the
 * SDK asks its provider (Play Integrity; up to a few seconds), which is also what any request would have done.
 */
class FirebaseAppCheckProbe(
    private val timeoutMillis: Long = TIMEOUT_MS,
) : AppCheckProbe {
    override suspend fun tokenIsMissing(): Boolean =
        try {
            withTimeout(timeoutMillis) { FirebaseAppCheck.getInstance().getAppCheckToken(false).await() }
            false
        } catch (e: TimeoutCancellationException) {
            false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            true
        }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
