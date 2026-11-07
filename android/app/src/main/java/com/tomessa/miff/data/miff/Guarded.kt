package com.tomessa.miff.data.miff

import com.tomessa.miff.domain.miff.MiffFailure
import com.tomessa.miff.domain.miff.interpretMiffError
import com.tomessa.miff.domain.miff.isAccessLostError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/*
 * SECURITY, for this file: what leaves a call of the store is a [MiffFailure] (no message, no path, no code): the messages of
 * Firestore can quote a document or a value.
 */

/** What a call to the store gave: its value, or the one reason it failed, reduced to what a screen can say. */
internal sealed interface Guarded<out T> {
    class Value<T>(
        val value: T,
    ) : Guarded<T>

    class Failed(
        val failure: MiffFailure,
        /** The server refused the access (rules): the only answer that can mean "removed". */
        val lostAccess: Boolean,
    ) : Guarded<Nothing>
}

/** Runs [call] with a time limit; a cancellation of the caller is never swallowed. */
internal suspend fun <T> guardedCall(
    timeoutMillis: Long,
    call: suspend () -> T,
): Guarded<T> =
    try {
        Guarded.Value(withTimeout(timeoutMillis) { call() })
    } catch (e: TimeoutCancellationException) {
        Guarded.Failed(MiffFailure.OFFLINE, lostAccess = false)
    } catch (e: CancellationException) {
        throw e
    } catch (e: MissingProfileException) {
        Guarded.Failed(MiffFailure.NO_PROFILE, lostAccess = false)
    } catch (e: Exception) {
        Guarded.Failed(interpretMiffError(e), lostAccess = isAccessLostError(e))
    }
