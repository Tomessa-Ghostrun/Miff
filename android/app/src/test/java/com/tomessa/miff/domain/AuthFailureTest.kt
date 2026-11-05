package com.tomessa.miff.domain

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.tomessa.miff.domain.auth.AuthAttempt
import com.tomessa.miff.domain.auth.AuthFailure
import com.tomessa.miff.domain.auth.GoogleTokenResult
import com.tomessa.miff.domain.auth.interpretAuthError
import com.tomessa.miff.ui.auth.AuthMessage
import com.tomessa.miff.ui.auth.toMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

/** Criterion A5: errors are interpreted, never shown raw; criteria C4 and C6: one message for "unknown" and "wrong". */
class AuthFailureTest {
    private fun interpret(
        error: Throwable,
        attempt: AuthAttempt = AuthAttempt.SIGN_IN_EMAIL,
    ) = interpretAuthError(error, attempt)

    @Test
    fun unknownAddressAndWrongPassword_giveTheSameFailure_andTheSameMessage() {
        val unknownAddress = interpret(FirebaseAuthInvalidUserException("ERROR_USER_NOT_FOUND", "no user record"))
        val wrongPassword =
            interpret(FirebaseAuthInvalidCredentialsException("ERROR_INVALID_CREDENTIAL", "bad password"))
        val newStyle = interpret(FirebaseAuthException("ERROR_INVALID_CREDENTIAL", "INVALID_LOGIN_CREDENTIALS"))
        assertEquals(AuthFailure.INVALID_CREDENTIALS, unknownAddress)
        assertEquals(unknownAddress, wrongPassword)
        assertEquals(unknownAddress, newStyle)
        assertEquals(unknownAddress.toMessage(), wrongPassword.toMessage())
    }

    @Test
    fun invalidCredentialsIsOnlyEverAnAnswerOfAnEmailSignIn() {
        val error = FirebaseAuthInvalidCredentialsException("ERROR_INVALID_CREDENTIAL", "bad")
        assertEquals(AuthFailure.UNKNOWN, interpret(error, AuthAttempt.SOCIAL))
        assertEquals(AuthFailure.UNKNOWN, interpret(error, AuthAttempt.SIGN_UP_EMAIL))
        val weak = FirebaseAuthWeakPasswordException("ERROR_WEAK_PASSWORD", "weak", "reason")
        assertEquals(AuthFailure.UNKNOWN, interpret(weak, AuthAttempt.SIGN_UP_EMAIL))
    }

    @Test
    fun addressAlreadyUsed_atSignUp() {
        val error = FirebaseAuthUserCollisionException("ERROR_EMAIL_ALREADY_IN_USE", "exists")
        assertEquals(AuthFailure.EMAIL_ALREADY_IN_USE, interpret(error, AuthAttempt.SIGN_UP_EMAIL))
        assertEquals(AuthMessage.EMAIL_IN_USE, AuthFailure.EMAIL_ALREADY_IN_USE.toMessage())
    }

    @Test
    fun addressLinkedToAnotherMethod_isAnOrientationMessage_notATechnicalError() {
        val differentCredential =
            FirebaseAuthUserCollisionException("ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL", "x")
        val alreadyInUse = FirebaseAuthUserCollisionException("ERROR_CREDENTIAL_ALREADY_IN_USE", "x")
        assertEquals(AuthFailure.LINKED_TO_OTHER_METHOD, interpret(differentCredential, AuthAttempt.SOCIAL))
        assertEquals(AuthFailure.LINKED_TO_OTHER_METHOD, interpret(alreadyInUse, AuthAttempt.SOCIAL))
        assertEquals(AuthMessage.OTHER_METHOD, AuthFailure.LINKED_TO_OTHER_METHOD.toMessage())
    }

    @Test
    fun networkProblems() {
        assertEquals(AuthFailure.NETWORK, interpret(FirebaseNetworkException("offline")))
        assertEquals(AuthFailure.NETWORK, interpret(IOException("reset")))
        val requestFailed = FirebaseAuthException("ERROR_NETWORK_REQUEST_FAILED", "x")
        assertEquals(AuthFailure.NETWORK, interpret(requestFailed, AuthAttempt.SOCIAL))
    }

    @Test
    fun temporaryBlock_andTheLocalPause_shareTheSameMessage() {
        assertEquals(AuthFailure.TOO_MANY_ATTEMPTS, interpret(FirebaseTooManyRequestsException("blocked")))
        assertEquals(AuthMessage.TOO_MANY_ATTEMPTS, AuthFailure.TOO_MANY_ATTEMPTS.toMessage())
    }

    @Test
    fun disabledAccount_isTheUnavailableMessage() {
        val error = FirebaseAuthInvalidUserException("ERROR_USER_DISABLED", "disabled")
        assertEquals(AuthFailure.ACCOUNT_DISABLED, interpret(error))
        assertEquals(AuthMessage.ACCOUNT_UNAVAILABLE, AuthFailure.ACCOUNT_DISABLED.toMessage())
    }

    @Test
    fun recentLoginRequired_isRecognised() {
        val error = FirebaseAuthRecentLoginRequiredException("ERROR_REQUIRES_RECENT_LOGIN", "x")
        assertEquals(AuthFailure.RECENT_LOGIN_REQUIRED, interpret(error, AuthAttempt.DELETE_ACCOUNT))
    }

    @Test
    fun anythingElse_becomesTheNeutralMessage_neverTheRawError() {
        listOf<Throwable>(
            IllegalStateException("secret internals"),
            FirebaseAuthException("ERROR_OPERATION_NOT_ALLOWED", "provider disabled"),
            RuntimeException(),
        ).forEach { error ->
            assertEquals(AuthFailure.UNKNOWN, interpret(error))
            assertEquals(AuthMessage.UNKNOWN, interpret(error).toMessage())
        }
    }

    @Test
    fun closingTheGoogleDialog_saysNothing() {
        assertNull(GoogleTokenResult.Cancelled.toMessage())
        assertNull(GoogleTokenResult.Token("t").toMessage())
        assertEquals(AuthMessage.UNKNOWN, GoogleTokenResult.Unavailable.toMessage())
        assertEquals(AuthMessage.NETWORK, GoogleTokenResult.Failed(AuthFailure.NETWORK).toMessage())
    }

    // --- AUTH-5: re-authentication and deletion of the account ---------------------------------------------------------------

    @Test
    fun reauthenticationWithAWrongPassword_isTheCalmWrongPasswordFailure() {
        val error = FirebaseAuthInvalidCredentialsException("ERROR_WRONG_PASSWORD", "bad")
        assertEquals(AuthFailure.INVALID_CREDENTIALS, interpret(error, AuthAttempt.REAUTH_EMAIL))
        // For Google, "invalid credential" never means "wrong password".
        assertEquals(AuthFailure.UNKNOWN, interpret(error, AuthAttempt.REAUTH_SOCIAL))
    }

    @Test
    fun aCredentialOfAnotherAccount_isTheWrongAccountFailure() {
        val error = FirebaseAuthInvalidCredentialsException("ERROR_USER_MISMATCH", "mismatch")
        assertEquals(AuthFailure.WRONG_ACCOUNT, interpret(error, AuthAttempt.REAUTH_SOCIAL))
        assertEquals(AuthMessage.REAUTH_WRONG_ACCOUNT, AuthFailure.WRONG_ACCOUNT.toMessage())
    }

    @Test
    fun aUserThatDoesNotExistAnyMore_isTheAccountGoneFailure_forTheAttemptsOnTheOpenSession_only() {
        val error = FirebaseAuthInvalidUserException("ERROR_USER_NOT_FOUND", "gone")
        for (
        attempt in
        listOf(
            AuthAttempt.DELETE_ACCOUNT,
            AuthAttempt.CHECK_SESSION,
            AuthAttempt.REAUTH_EMAIL,
            AuthAttempt.REAUTH_SOCIAL,
        )
        ) {
            assertEquals("$attempt", AuthFailure.ACCOUNT_GONE, interpret(error, attempt))
        }
        // At sign-in, "user not found" stays the ONE message of "unknown address or wrong password" (no enumeration).
        val notFound = FirebaseAuthInvalidUserException("ERROR_USER_NOT_FOUND", "gone")
        assertEquals(AuthFailure.INVALID_CREDENTIALS, interpret(notFound, AuthAttempt.SIGN_IN_EMAIL))
    }

    @Test
    fun anExpiredOrRevokedToken_isNEVERTheAccountGone_itIsASessionToRenew() {
        // Honest wording: an expired or revoked token does not prove the account was deleted.
        for (code in listOf("ERROR_USER_TOKEN_EXPIRED", "ERROR_INVALID_USER_TOKEN")) {
            val error = FirebaseAuthInvalidUserException(code, "token")
            for (attempt in listOf(AuthAttempt.DELETE_ACCOUNT, AuthAttempt.CHECK_SESSION, AuthAttempt.REAUTH_EMAIL)) {
                assertEquals("$code $attempt", AuthFailure.SESSION_EXPIRED, interpret(error, attempt))
            }
        }
        assertEquals(AuthMessage.REAUTH_EXPIRED, AuthFailure.SESSION_EXPIRED.toMessage())
    }

    @Test
    fun aDisabledAccount_isNotConfusedWithAGoneOne() {
        val disabled = FirebaseAuthInvalidUserException("ERROR_USER_DISABLED", "disabled")
        assertEquals(AuthFailure.ACCOUNT_DISABLED, interpret(disabled, AuthAttempt.DELETE_ACCOUNT))
    }

    @Test
    fun theNewFailures_neverShowATechnicalCode() {
        assertEquals(AuthMessage.REAUTH_EXPIRED, AuthFailure.ACCOUNT_GONE.toMessage())
    }
}
