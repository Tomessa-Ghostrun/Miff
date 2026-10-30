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
        AuthFailure.UNKNOWN -> AuthMessage.UNKNOWN
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

        AuthMessage.ACCOUNT_UNAVAILABLE -> {
            stringResource(
                R.string.auth_error_account_unavailable,
                BuildConfig.SUPPORT_EMAIL,
            )
        }
    }

/** Field-level problems, computed from the text (see [AuthForm]). */
fun emailFieldError(email: String): Boolean = email.isNotBlank() && !AuthForm.isEmailFormatValid(email)
