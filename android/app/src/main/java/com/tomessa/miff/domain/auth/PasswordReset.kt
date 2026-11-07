package com.tomessa.miff.domain.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import java.io.IOException
import java.util.Locale

/*
 * "Mot de passe oublié" (ticket AUTH, sections 3 C, 7.3, 7.4, 7.5 and criteria M1 to M5).
 *
 * The key rule: what the app says and does NEVER depends on whether the address has an account (no enumeration of
 * e-mail addresses). That is decided here, in pure logic that is unit-tested.
 */

/** What happened to a request to send the reset e-mail. */
sealed interface PasswordResetOutcome {
    /**
     * The request was taken into account. This says NOTHING about whether the address has an account: an unknown address,
     * a disabled account and a real account all end here, so that the screen cannot tell them apart.
     */
    data object Accepted : PasswordResetOutcome

    /** The server itself finds the address malformed (the local check normally catches this first). */
    data object EmailRejected : PasswordResetOutcome

    /** Network, quota, unknown problem: nothing was sent, and the person is told so, kindly. */
    data class Failed(
        val failure: AuthFailure,
    ) : PasswordResetOutcome
}

/**
 * Turns any error of `sendPasswordResetEmail` into a [PasswordResetOutcome]. Never exposes a code or a message.
 *
 * "No such user" and "user disabled" are [PasswordResetOutcome.Accepted] on purpose: Firebase answers them with an error
 * when its e-mail enumeration protection is off, and showing anything different would tell a stranger which addresses
 * have an account (ticket 7.3, criteria M2 and C6). With the protection on (the project setting), Firebase already
 * answers "done" in both cases and this branch is never reached.
 */
fun interpretPasswordResetError(error: Throwable): PasswordResetOutcome =
    when (error) {
        is FirebaseNetworkException, is IOException -> {
            PasswordResetOutcome.Failed(AuthFailure.NETWORK)
        }

        is FirebaseTooManyRequestsException -> {
            PasswordResetOutcome.Failed(AuthFailure.TOO_MANY_ATTEMPTS)
        }

        is FirebaseAuthInvalidUserException -> {
            PasswordResetOutcome.Accepted
        }

        is FirebaseAuthInvalidCredentialsException -> {
            if (error.errorCode == "ERROR_INVALID_EMAIL") {
                PasswordResetOutcome.EmailRejected
            } else {
                PasswordResetOutcome.Failed(AuthFailure.UNKNOWN)
            }
        }

        is FirebaseAuthException -> {
            when (error.errorCode) {
                "ERROR_USER_NOT_FOUND", "ERROR_USER_DISABLED" -> PasswordResetOutcome.Accepted
                "ERROR_INVALID_EMAIL" -> PasswordResetOutcome.EmailRejected
                "ERROR_TOO_MANY_REQUESTS" -> PasswordResetOutcome.Failed(AuthFailure.TOO_MANY_ATTEMPTS)
                "ERROR_NETWORK_REQUEST_FAILED" -> PasswordResetOutcome.Failed(AuthFailure.NETWORK)
                else -> PasswordResetOutcome.Failed(AuthFailure.UNKNOWN)
            }
        }

        else -> {
            PasswordResetOutcome.Failed(AuthFailure.UNKNOWN)
        }
    }

/**
 * Comfort pause between two sends of the reset e-mail (ticket 7.4, criterion M5: about 60 seconds). NOT a security
 * protection (it lives in the app, a modified client skips it): the real protection is Firebase's own quota and App Check.
 *
 * The pause is per ADDRESS: somebody who made a typo and fixes the address is not made to wait for the wrong one, while
 * the same address (whatever the case or the spaces around it) stays paused. Only the last address is remembered, in
 * memory, never written to disk and never logged. It is held by the app container, so that leaving the screen and coming
 * back does not give a free resend.
 */
class ResendCooldown(
    private val nowMillis: () -> Long,
    private val durationMillis: Long = DEFAULT_DURATION_MILLIS,
) {
    private var address: String? = null
    private var startedAt = 0L

    /** 0 when a send to [email] is allowed. */
    fun remainingMillis(email: String): Long {
        val key = key(email)
        if (key.isEmpty() || key != address) return 0L
        return (startedAt + durationMillis - nowMillis()).coerceIn(0L, durationMillis)
    }

    fun start(email: String) {
        address = key(email)
        startedAt = nowMillis()
    }

    private fun key(email: String): String = AuthForm.normalizeEmail(email).lowercase(Locale.ROOT)

    companion object {
        const val DEFAULT_DURATION_MILLIS = 60_000L
    }
}
