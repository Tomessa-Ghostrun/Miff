package com.tomessa.miff.domain.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import java.io.IOException

/** Why a sign-in did not work, in terms the UI can word kindly. No technical detail leaks to the user. */
enum class SignInFailure {
    /** Wrong e-mail or password. Deliberately also covers "no such account", to not reveal which accounts exist. */
    INVALID_CREDENTIALS,
    NETWORK,
    TOO_MANY_ATTEMPTS,
    ACCOUNT_DISABLED,
    UNKNOWN,
}

fun interpretSignInError(error: Throwable): SignInFailure =
    when (error) {
        is FirebaseNetworkException, is IOException -> {
            SignInFailure.NETWORK
        }

        is FirebaseTooManyRequestsException -> {
            SignInFailure.TOO_MANY_ATTEMPTS
        }

        is FirebaseAuthInvalidUserException -> {
            if (error.errorCode == "ERROR_USER_DISABLED") {
                SignInFailure.ACCOUNT_DISABLED
            } else {
                SignInFailure.INVALID_CREDENTIALS
            }
        }

        is FirebaseAuthInvalidCredentialsException -> {
            SignInFailure.INVALID_CREDENTIALS
        }

        is FirebaseAuthException -> {
            when (error.errorCode) {
                "ERROR_INVALID_CREDENTIAL",
                "ERROR_WRONG_PASSWORD",
                "ERROR_INVALID_EMAIL",
                "ERROR_USER_NOT_FOUND",
                -> SignInFailure.INVALID_CREDENTIALS

                "ERROR_USER_DISABLED" -> SignInFailure.ACCOUNT_DISABLED

                "ERROR_TOO_MANY_REQUESTS" -> SignInFailure.TOO_MANY_ATTEMPTS

                "ERROR_NETWORK_REQUEST_FAILED" -> SignInFailure.NETWORK

                else -> SignInFailure.UNKNOWN
            }
        }

        else -> {
            SignInFailure.UNKNOWN
        }
    }

/** Local check done before contacting Firebase (no request for obviously empty or malformed input). */
enum class CredentialsInputProblem { EMAIL_EMPTY, EMAIL_INVALID, PASSWORD_EMPTY, TOO_LONG }

object CredentialsInput {
    private const val MAX_EMAIL_LENGTH = 254
    private const val MAX_PASSWORD_LENGTH = 1024

    // Deliberately loose (Firebase is the real validator): something@something.tld, no spaces.
    private val emailShape = Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""")

    /** Same loose shape check as [check], reusable on its own (the sign-up and sign-in forms). */
    fun looksLikeEmail(email: String): Boolean = emailShape.matches(email.trim())

    fun check(
        email: String,
        password: String,
    ): CredentialsInputProblem? =
        when {
            email.isBlank() -> CredentialsInputProblem.EMAIL_EMPTY
            password.isEmpty() -> CredentialsInputProblem.PASSWORD_EMPTY
            email.length > MAX_EMAIL_LENGTH || password.length > MAX_PASSWORD_LENGTH -> CredentialsInputProblem.TOO_LONG
            !emailShape.matches(email.trim()) -> CredentialsInputProblem.EMAIL_INVALID
            else -> null
        }
}
