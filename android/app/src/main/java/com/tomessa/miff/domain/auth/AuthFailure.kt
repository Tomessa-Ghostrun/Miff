package com.tomessa.miff.domain.auth

import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException

/** What the user was trying to do when Firebase answered with an error. It changes how an error is worded. */
enum class AuthAttempt {
    /** E-mail + password sign-in. The ONLY attempt where "wrong credentials" is a possible answer. */
    SIGN_IN_EMAIL,

    /** Creation of an account with e-mail + password. */
    SIGN_UP_EMAIL,

    /** Google (ID token exchanged with Firebase), also used to re-authenticate. */
    SOCIAL,

    /** Deletion of the account that was just created (refusal of the consent panel), or deletion of the account (AUTH-5). */
    DELETE_ACCOUNT,

    /**
     * Proving who the person is again with the password, before the deletion of the account (AUTH-5). The person is already
     * signed in: a wrong password is a plain "that password does not match", not an account enumeration.
     */
    REAUTH_EMAIL,

    /** Proving who the person is again with Google (AUTH-5). */
    REAUTH_SOCIAL,

    /** Asking Firebase whether the session still exists (AUTH-5, resuming a deletion). */
    CHECK_SESSION,
}

/** Why an authentication step did not work, in terms the UI can word kindly. No technical detail leaks. */
enum class AuthFailure {
    /** Wrong e-mail or password. Deliberately also covers "no such account" (ticket 7.3, criterion C4/C6). */
    INVALID_CREDENTIALS,
    NETWORK,
    TOO_MANY_ATTEMPTS,
    ACCOUNT_DISABLED,

    /** Sign-up with an address that already has an account. Only ever shown at sign-up (ticket R3). */
    EMAIL_ALREADY_IN_USE,

    /** The address belongs to an account created with another sign-in method. */
    LINKED_TO_OTHER_METHOD,

    /** Firebase wants a recent sign-in before deleting the account. */
    RECENT_LOGIN_REQUIRED,

    /** Google dialog: no Google account can be used on this phone (none added, or none usable right now). */
    GOOGLE_NO_ACCOUNT,

    /** Google dialog: the Google services of the phone are missing, too old or cannot handle this request. */
    GOOGLE_SERVICES_UNAVAILABLE,

    /** Google dialog: Google did not answer this time (interrupted, short network problem): trying again is fine. */
    GOOGLE_INTERRUPTED,
    UNKNOWN,

    /** Re-authentication with a Google account that is not the one of the session (AUTH-5, criterion SC11). */
    WRONG_ACCOUNT,

    /**
     * Firebase says the account DOES NOT EXIST any more (`ERROR_USER_NOT_FOUND`: deleted from the console or from another phone). Only
     * answered for the attempts on the open session; for the deletion of the account it means "already done" (AUTH-5, criterion SC28).
     * An expired or revoked token is NOT this: see [SESSION_EXPIRED].
     */
    ACCOUNT_GONE,

    /**
     * The session can no longer be used (`ERROR_USER_TOKEN_EXPIRED`, `ERROR_INVALID_USER_TOKEN`: token expired or revoked, password changed
     * elsewhere...) while the account may well still exist. NEVER a success: nothing may say "deleted"; the person signs in again.
     */
    SESSION_EXPIRED,
}

/** The ONLY Firebase code that proves the account is gone. */
private const val USER_NOT_FOUND_CODE = "ERROR_USER_NOT_FOUND"

/** Codes of a session that cannot be used any more without proving the account is gone (token expired or revoked). */
private val sessionExpiredCodes = setOf("ERROR_USER_TOKEN_EXPIRED", "ERROR_INVALID_USER_TOKEN")

/** `ERROR_USER_MISMATCH`: the credential given belongs to another account than the one of the session. */
private const val USER_MISMATCH_CODE = "ERROR_USER_MISMATCH"

/** The attempts on the session already open: there, "user not found" means "the account is gone", not "wrong address". */
private fun AuthAttempt.isAboutTheSession(): Boolean =
    this == AuthAttempt.DELETE_ACCOUNT ||
        this == AuthAttempt.REAUTH_EMAIL ||
        this == AuthAttempt.REAUTH_SOCIAL ||
        this == AuthAttempt.CHECK_SESSION

/**
 * Turns any error of the authentication calls into an [AuthFailure]. Never exposes a code or a message.
 *
 * Only an e-mail sign-in may answer [AuthFailure.INVALID_CREDENTIALS]: for the other attempts, "invalid credential"
 * does not mean "wrong password" and saying so would mislead (a Google token problem is not the user's password).
 */
fun interpretAuthError(
    error: Throwable,
    attempt: AuthAttempt,
): AuthFailure {
    if (error is FirebaseAuthRecentLoginRequiredException) return AuthFailure.RECENT_LOGIN_REQUIRED
    if (error is FirebaseAuthException && error.errorCode == USER_MISMATCH_CODE) return AuthFailure.WRONG_ACCOUNT
    if (error is FirebaseAuthInvalidUserException && attempt.isAboutTheSession()) {
        when (error.errorCode) {
            USER_NOT_FOUND_CODE -> return AuthFailure.ACCOUNT_GONE
            in sessionExpiredCodes -> return AuthFailure.SESSION_EXPIRED
        }
    }
    if (error is FirebaseAuthUserCollisionException) {
        return when (error.errorCode) {
            "ERROR_EMAIL_ALREADY_IN_USE" -> {
                if (attempt ==
                    AuthAttempt.SIGN_UP_EMAIL
                ) {
                    AuthFailure.EMAIL_ALREADY_IN_USE
                } else {
                    AuthFailure.LINKED_TO_OTHER_METHOD
                }
            }

            else -> {
                AuthFailure.LINKED_TO_OTHER_METHOD
            }
        }
    }
    return when (interpretSignInError(error)) {
        SignInFailure.INVALID_CREDENTIALS -> {
            if (attempt == AuthAttempt.SIGN_IN_EMAIL || attempt == AuthAttempt.REAUTH_EMAIL) {
                AuthFailure.INVALID_CREDENTIALS
            } else {
                AuthFailure.UNKNOWN
            }
        }

        SignInFailure.NETWORK -> {
            AuthFailure.NETWORK
        }

        SignInFailure.TOO_MANY_ATTEMPTS -> {
            AuthFailure.TOO_MANY_ATTEMPTS
        }

        SignInFailure.ACCOUNT_DISABLED -> {
            AuthFailure.ACCOUNT_DISABLED
        }

        SignInFailure.UNKNOWN -> {
            AuthFailure.UNKNOWN
        }
    }
}

/** Result of a sign-in or sign-up call. Carries a user id, never a password nor a token. */
sealed interface AuthOutcome {
    data class Success(
        val uid: String,
        val isNewUser: Boolean,
    ) : AuthOutcome

    data class Failure(
        val failure: AuthFailure,
    ) : AuthOutcome
}

/** What the "Continuer avec Google" system dialog gave back. */
sealed interface GoogleTokenResult {
    data class Token(
        val idToken: String,
    ) : GoogleTokenResult

    /** The person closed the dialog: NO message must be shown (criterion S7). */
    data object Cancelled : GoogleTokenResult

    /** The sign-in cannot even start (no web OAuth client in the configuration): nothing the person can do about it. */
    data object Unavailable : GoogleTokenResult

    data class Failed(
        val failure: AuthFailure,
    ) : GoogleTokenResult
}
