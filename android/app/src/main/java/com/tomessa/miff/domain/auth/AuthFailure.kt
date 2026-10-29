package com.tomessa.miff.domain.auth

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

    /** Deletion of the account that was just created (refusal of the consent panel). */
    DELETE_ACCOUNT,
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
    UNKNOWN,
}

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
            if (attempt == AuthAttempt.SIGN_IN_EMAIL) AuthFailure.INVALID_CREDENTIALS else AuthFailure.UNKNOWN
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

    /** The sign-in cannot even start (no web OAuth client in the configuration, no Google services...). */
    data object Unavailable : GoogleTokenResult

    data class Failed(
        val failure: AuthFailure,
    ) : GoogleTokenResult
}
