package com.tomessa.miff.domain

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.tomessa.miff.domain.auth.CredentialsInput
import com.tomessa.miff.domain.auth.CredentialsInputProblem
import com.tomessa.miff.domain.auth.SignInFailure
import com.tomessa.miff.domain.auth.interpretSignInError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

class SignInFailureTest {
    @Test
    fun networkProblemsAreReportedAsNetwork() {
        assertEquals(SignInFailure.NETWORK, interpretSignInError(FirebaseNetworkException("offline")))
        assertEquals(SignInFailure.NETWORK, interpretSignInError(IOException("timeout")))
        assertEquals(
            SignInFailure.NETWORK,
            interpretSignInError(FirebaseAuthException("ERROR_NETWORK_REQUEST_FAILED", "network")),
        )
    }

    @Test
    fun wrongCredentialsAndUnknownAccountsLookTheSame() {
        val expected = SignInFailure.INVALID_CREDENTIALS
        assertEquals(
            expected,
            interpretSignInError(FirebaseAuthInvalidCredentialsException("ERROR_INVALID_CREDENTIAL", "bad")),
        )
        assertEquals(expected, interpretSignInError(FirebaseAuthInvalidUserException("ERROR_USER_NOT_FOUND", "none")))
        assertEquals(expected, interpretSignInError(FirebaseAuthException("ERROR_WRONG_PASSWORD", "bad")))
        assertEquals(expected, interpretSignInError(FirebaseAuthException("ERROR_INVALID_EMAIL", "bad")))
    }

    @Test
    fun disabledAccountIsDistinguished() {
        assertEquals(
            SignInFailure.ACCOUNT_DISABLED,
            interpretSignInError(FirebaseAuthInvalidUserException("ERROR_USER_DISABLED", "off")),
        )
    }

    @Test
    fun tooManyRequests() {
        assertEquals(
            SignInFailure.TOO_MANY_ATTEMPTS,
            interpretSignInError(FirebaseTooManyRequestsException("slow down")),
        )
        assertEquals(
            SignInFailure.TOO_MANY_ATTEMPTS,
            interpretSignInError(FirebaseAuthException("ERROR_TOO_MANY_REQUESTS", "slow down")),
        )
    }

    @Test
    fun anythingElseIsUnknown() {
        assertEquals(SignInFailure.UNKNOWN, interpretSignInError(IllegalStateException("boom")))
        assertEquals(SignInFailure.UNKNOWN, interpretSignInError(FirebaseAuthException("ERROR_SOMETHING_NEW", "?")))
    }

    // ---- Local input check ----

    @Test
    fun acceptsPlausibleInput() {
        assertNull(CredentialsInput.check("test@example.com", "a-strong-password"))
        assertNull(CredentialsInput.check("  test@example.com  ", "x"))
    }

    @Test
    fun rejectsEmptyOrMalformedInput() {
        assertEquals(CredentialsInputProblem.EMAIL_EMPTY, CredentialsInput.check("  ", "pw"))
        assertEquals(CredentialsInputProblem.PASSWORD_EMPTY, CredentialsInput.check("a@b.co", ""))
        assertEquals(CredentialsInputProblem.EMAIL_INVALID, CredentialsInput.check("not-an-email", "pw"))
        assertEquals(CredentialsInputProblem.EMAIL_INVALID, CredentialsInput.check("a b@c.de", "pw"))
    }

    @Test
    fun rejectsAbsurdlyLongInput() {
        assertEquals(CredentialsInputProblem.TOO_LONG, CredentialsInput.check("a".repeat(250) + "@b.co", "pw"))
        assertEquals(CredentialsInputProblem.TOO_LONG, CredentialsInput.check("a@b.co", "p".repeat(2000)))
    }
}
