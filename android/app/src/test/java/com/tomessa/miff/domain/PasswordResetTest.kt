package com.tomessa.miff.domain

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.tomessa.miff.data.auth.attemptPasswordReset
import com.tomessa.miff.domain.auth.AuthFailure
import com.tomessa.miff.domain.auth.PasswordResetOutcome
import com.tomessa.miff.domain.auth.ResendCooldown
import com.tomessa.miff.domain.auth.interpretPasswordResetError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/**
 * "Mot de passe oublié": how the answers of Firebase are sorted (criteria M2, X1, X2) and the pause between two sends (M5).
 * The central rule: an unknown address and a real account end in the SAME outcome, so that no screen can tell them apart.
 */
class PasswordResetTest {
    @Test
    fun unknownAddress_aDisabledAccount_andARealAccount_endInTheSameOutcome_M2() {
        val real = PasswordResetOutcome.Accepted
        val unknown = interpretPasswordResetError(FirebaseAuthInvalidUserException("ERROR_USER_NOT_FOUND", "no record"))
        val disabled = interpretPasswordResetError(FirebaseAuthInvalidUserException("ERROR_USER_DISABLED", "off"))
        val plainCode = interpretPasswordResetError(FirebaseAuthException("ERROR_USER_NOT_FOUND", "no record"))
        assertEquals(real, unknown)
        assertEquals(real, disabled)
        assertEquals(real, plainCode)
        assertEquals(real, interpretPasswordResetError(FirebaseAuthException("ERROR_USER_DISABLED", "off")))
    }

    @Test
    fun aMalformedAddressSaidByTheServer_isTheFormatMessage_notAnAccountMessage() {
        val expected = PasswordResetOutcome.EmailRejected
        assertEquals(
            expected,
            interpretPasswordResetError(FirebaseAuthInvalidCredentialsException("ERROR_INVALID_EMAIL", "bad")),
        )
        assertEquals(expected, interpretPasswordResetError(FirebaseAuthException("ERROR_INVALID_EMAIL", "bad")))
    }

    @Test
    fun networkProblems_areReportedAsNetwork_X2() {
        val expected = PasswordResetOutcome.Failed(AuthFailure.NETWORK)
        assertEquals(expected, interpretPasswordResetError(FirebaseNetworkException("offline")))
        assertEquals(expected, interpretPasswordResetError(IOException("timeout")))
        assertEquals(
            expected,
            interpretPasswordResetError(FirebaseAuthException("ERROR_NETWORK_REQUEST_FAILED", "network")),
        )
    }

    @Test
    fun tooManyRequests_isItsOwnFailure() {
        val expected = PasswordResetOutcome.Failed(AuthFailure.TOO_MANY_ATTEMPTS)
        assertEquals(expected, interpretPasswordResetError(FirebaseTooManyRequestsException("slow down")))
        assertEquals(expected, interpretPasswordResetError(FirebaseAuthException("ERROR_TOO_MANY_REQUESTS", "slow")))
    }

    @Test
    fun anythingElse_isTheNeutralUnknownFailure_neverARawCode_X1() {
        val expected = PasswordResetOutcome.Failed(AuthFailure.UNKNOWN)
        assertEquals(
            expected,
            interpretPasswordResetError(FirebaseAuthException("ERROR_SOMETHING_NEW", "secret detail")),
        )
        assertEquals(expected, interpretPasswordResetError(IllegalStateException("boom")))
        assertEquals(
            expected,
            interpretPasswordResetError(FirebaseAuthInvalidCredentialsException("ERROR_OTHER", "x")),
        )
    }

    // --- The call itself ------------------------------------------------------------------------------------------

    @Test
    fun theCall_neverThrows_andSortsItsErrors() =
        runTest {
            assertEquals(PasswordResetOutcome.Accepted, attemptPasswordReset { })
            assertEquals(
                "an unknown address looks exactly like a success",
                PasswordResetOutcome.Accepted,
                attemptPasswordReset { throw FirebaseAuthInvalidUserException("ERROR_USER_NOT_FOUND", "none") },
            )
            assertEquals(
                PasswordResetOutcome.Failed(AuthFailure.NETWORK),
                attemptPasswordReset { throw FirebaseNetworkException("offline") },
            )
            assertEquals(
                PasswordResetOutcome.Failed(AuthFailure.UNKNOWN),
                attemptPasswordReset { throw RuntimeException("anything") },
            )
        }

    @Test
    fun theCall_letsACancellationThrough() =
        runTest {
            try {
                attemptPasswordReset { throw CancellationException("screen gone") }
                fail("a cancellation must not be swallowed")
            } catch (expected: CancellationException) {
                assertTrue(true)
            }
        }

    // --- The pause between two sends (M5) -------------------------------------------------------------------------

    @Test
    fun cooldown_isAboutSixtySeconds_forTheSameAddress_M5() {
        var now = 1_000L
        val cooldown = ResendCooldown(nowMillis = { now })
        assertEquals("nothing sent yet", 0L, cooldown.remainingMillis("a@b.fr"))

        cooldown.start("a@b.fr")
        assertEquals(60_000L, cooldown.remainingMillis("a@b.fr"))
        now += 59_999
        assertEquals(1L, cooldown.remainingMillis("a@b.fr"))
        now += 1
        assertEquals(0L, cooldown.remainingMillis("a@b.fr"))
        now += 10_000
        assertEquals("never negative", 0L, cooldown.remainingMillis("a@b.fr"))
    }

    @Test
    fun cooldown_followsTheAddress_notTheSpellingOfIt() {
        val cooldown = ResendCooldown(nowMillis = { 0L })
        cooldown.start("  Toto@Gmail.com ")
        assertEquals(60_000L, cooldown.remainingMillis("toto@gmail.com"))
        assertEquals(60_000L, cooldown.remainingMillis("TOTO@GMAIL.COM  "))
        assertEquals("a fixed typo is not made to wait", 0L, cooldown.remainingMillis("toto@gmial.com"))
        assertEquals(0L, cooldown.remainingMillis(""))
    }

    @Test
    fun cooldown_aNewSendRestartsTheCount() {
        var now = 0L
        val cooldown = ResendCooldown(nowMillis = { now })
        cooldown.start("a@b.fr")
        now = 70_000
        cooldown.start("a@b.fr")
        assertEquals(60_000L, cooldown.remainingMillis("a@b.fr"))
    }
}
