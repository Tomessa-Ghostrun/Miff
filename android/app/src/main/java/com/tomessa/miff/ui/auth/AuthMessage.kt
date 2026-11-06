package com.tomessa.miff.ui.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.tomessa.miff.BuildConfig
import com.tomessa.miff.R
import com.tomessa.miff.domain.auth.AuthFailure
import com.tomessa.miff.domain.auth.AuthForm
import com.tomessa.miff.domain.auth.GoogleTokenResult

/** The user-facing messages of the authentication screens (ticket 7.3). One text per message, no technical code. */
enum class AuthMessage {
    /** Wrong e-mail or password, or unknown address: ONE message for both (criteria C4 and C6). */
    INVALID_CREDENTIALS,

    /** E-mail sign-in only: the text points to "Mot de passe oublié ?" (ticket 7.3). */
    TOO_MANY_ATTEMPTS,

    /** Everywhere else (sign-up, Google, reset e-mail): same idea, without advising to reset a password. */
    TOO_MANY_REQUESTS,
    EMAIL_IN_USE,
    OTHER_METHOD,
    NETWORK,
    UNKNOWN,
    ACCOUNT_UNAVAILABLE,

    /** Google dialog: no usable Google account on the phone. Points to the e-mail way as an alternative. */
    GOOGLE_NO_ACCOUNT,

    /** Google dialog: the phone's Google services are missing, too old or do not answer. */
    GOOGLE_SERVICES_UNAVAILABLE,

    /** Google dialog: Google did not answer this time, trying again is fine. */
    GOOGLE_INTERRUPTED,

    /** Re-authentication before the deletion of the account, with a password: "that password does not match" (AUTH-5, SC10). */
    REAUTH_PASSWORD_WRONG,

    /** Re-authentication with a Google account that is not the one of the session (AUTH-5, SC11). */
    REAUTH_WRONG_ACCOUNT,

    /** The session can no longer be used (token expired or revoked): sign out and in again (AUTH-5). */
    REAUTH_EXPIRED,
}

fun AuthFailure.toMessage(): AuthMessage =
    when (this) {
        AuthFailure.INVALID_CREDENTIALS -> AuthMessage.INVALID_CREDENTIALS
        AuthFailure.NETWORK -> AuthMessage.NETWORK
        AuthFailure.TOO_MANY_ATTEMPTS -> AuthMessage.TOO_MANY_ATTEMPTS
        AuthFailure.ACCOUNT_DISABLED -> AuthMessage.ACCOUNT_UNAVAILABLE
        AuthFailure.EMAIL_ALREADY_IN_USE -> AuthMessage.EMAIL_IN_USE
        AuthFailure.LINKED_TO_OTHER_METHOD -> AuthMessage.OTHER_METHOD
        AuthFailure.RECENT_LOGIN_REQUIRED -> AuthMessage.UNKNOWN
        AuthFailure.GOOGLE_NO_ACCOUNT -> AuthMessage.GOOGLE_NO_ACCOUNT
        AuthFailure.GOOGLE_SERVICES_UNAVAILABLE -> AuthMessage.GOOGLE_SERVICES_UNAVAILABLE
        AuthFailure.GOOGLE_INTERRUPTED -> AuthMessage.GOOGLE_INTERRUPTED
        AuthFailure.UNKNOWN -> AuthMessage.UNKNOWN
        AuthFailure.WRONG_ACCOUNT -> AuthMessage.REAUTH_WRONG_ACCOUNT
        AuthFailure.ACCOUNT_GONE, AuthFailure.SESSION_EXPIRED -> AuthMessage.REAUTH_EXPIRED
    }

/** For the screens where "reset your password" would make no sense as advice (sign-up, Google, reset e-mail). */
fun AuthMessage.withoutResetAdvice(): AuthMessage =
    if (this == AuthMessage.TOO_MANY_ATTEMPTS) AuthMessage.TOO_MANY_REQUESTS else this

/**
 * What to say after the Google dialog. Closing the dialog says NOTHING (criterion S7): null.
 * A token is not a message either (the caller goes on with it).
 */
fun GoogleTokenResult.toMessage(): AuthMessage? =
    when (this) {
        GoogleTokenResult.Cancelled -> null
        is GoogleTokenResult.Token -> null
        GoogleTokenResult.Unavailable -> AuthMessage.UNKNOWN
        is GoogleTokenResult.Failed -> failure.toMessage()
    }

/**
 * A failure whose cause may simply be that the phone has no network (the system dialog gives no clear signal, Firebase
 * may fail late) says so when the phone is offline NOW: "no connection" is then the honest and useful answer.
 */
fun AuthMessage.orNoConnectionIf(offline: Boolean): AuthMessage =
    if (offline &&
        (this == AuthMessage.UNKNOWN || this == AuthMessage.GOOGLE_INTERRUPTED)
    ) {
        AuthMessage.NETWORK
    } else {
        this
    }

@Composable
fun AuthMessage.text(): String =
    when (this) {
        AuthMessage.INVALID_CREDENTIALS -> {
            stringResource(R.string.auth_error_invalid_credentials)
        }

        AuthMessage.TOO_MANY_ATTEMPTS -> {
            stringResource(R.string.auth_error_too_many)
        }

        AuthMessage.TOO_MANY_REQUESTS -> {
            stringResource(R.string.auth_error_too_many_requests)
        }

        AuthMessage.EMAIL_IN_USE -> {
            stringResource(R.string.auth_error_email_in_use)
        }

        AuthMessage.OTHER_METHOD -> {
            stringResource(R.string.auth_error_other_method)
        }

        AuthMessage.NETWORK -> {
            stringResource(R.string.auth_error_network)
        }

        AuthMessage.UNKNOWN -> {
            stringResource(R.string.auth_error_unknown)
        }

        AuthMessage.GOOGLE_NO_ACCOUNT -> {
            stringResource(R.string.auth_error_google_no_account)
        }

        AuthMessage.GOOGLE_SERVICES_UNAVAILABLE -> {
            stringResource(R.string.auth_error_google_services)
        }

        AuthMessage.GOOGLE_INTERRUPTED -> {
            stringResource(R.string.auth_error_google_interrupted)
        }

        AuthMessage.REAUTH_PASSWORD_WRONG -> {
            stringResource(R.string.settings_delete_reauth_password_wrong)
        }

        AuthMessage.REAUTH_WRONG_ACCOUNT -> {
            stringResource(R.string.settings_delete_reauth_wrong_account)
        }

        AuthMessage.REAUTH_EXPIRED -> {
            stringResource(R.string.settings_delete_reauth_expired)
        }

        AuthMessage.ACCOUNT_UNAVAILABLE -> {
            stringResource(
                R.string.auth_error_account_unavailable,
                BuildConfig.SUPPORT_EMAIL,
            )
        }
    }

/** Field-level problems, computed from the text (see [AuthForm]). */
fun emailFieldError(email: String): Boolean = email.isNotBlank() && !AuthForm.isEmailFormatValid(email)
