package com.tomessa.miff.data.auth

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialCustomException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.tomessa.miff.domain.auth.AuthFailure
import com.tomessa.miff.domain.auth.GoogleTokenResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Each outcome of the Credential Manager dialog, played with a fake client (no phone, no Google account): what the
 * person is told depends on the KIND of the failure, and nothing of the system's own message is ever kept.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GoogleIdTokenSourceTest {
    private class FakeClient(
        private val answer: suspend (String) -> String?,
    ) : GoogleCredentialClient {
        var calls = 0
        var lastClientId: String? = null

        override suspend fun fetchIdToken(webClientId: String): String? {
            calls++
            lastClientId = webClientId
            return answer(webClientId)
        }
    }

    private fun failingWith(error: Throwable) = FakeClient { throw error }

    /** The clock of the source: tests move it by hand to say how long the dialog was open. */
    private var now = 10_000L

    private fun sourceOf(
        client: GoogleCredentialClient,
        webClientId: String? = "web-client-id",
        timeoutMs: Long = 180_000L,
    ) = CredentialManagerGoogleSource(client, webClientId, timeoutMs, nowMillis = { now })

    /** A client that answers with a cancellation after the dialog was "open" for [openMs] milliseconds. */
    private fun cancelledAfter(openMs: Long) =
        FakeClient {
            now += openMs
            throw GetCredentialCancellationException("closed")
        }

    private fun failed(failure: AuthFailure) = GoogleTokenResult.Failed(failure)

    @Test
    fun aGoogleToken_isReturnedAsIs_andTheClientIdIsPassedOn() =
        runTest {
            val client = FakeClient { "the-id-token" }
            assertEquals(GoogleTokenResult.Token("the-id-token"), sourceOf(client).requestIdToken())
            assertEquals("web-client-id", client.lastClientId)
        }

    @Test
    fun noWebClientId_cannotStart_andTheDialogIsNeverOpened() =
        runTest {
            for (missing in listOf(null, "", "   ")) {
                val client = FakeClient { "token" }
                assertEquals(GoogleTokenResult.Unavailable, sourceOf(client, webClientId = missing).requestIdToken())
                assertEquals(0, client.calls)
            }
        }

    @Test
    fun thePersonClosesTheDialog_isACancellation_notAnError() =
        runTest {
            for (openMs in listOf(1_500L, 1_501L, 5_000L, 60_000L)) {
                val result = sourceOf(cancelledAfter(openMs)).requestIdToken()
                assertEquals("open for $openMs ms", GoogleTokenResult.Cancelled, result)
            }
        }

    @Test
    fun aCancellationTooQuickToBeAHumanGesture_isATemporaryFailure() =
        runTest {
            assertEquals(1_500L, CredentialManagerGoogleSource.FAST_CANCELLATION_MS)
            for (openMs in listOf(0L, 200L, 1_000L, 1_499L)) {
                val result = sourceOf(cancelledAfter(openMs)).requestIdToken()
                assertEquals("open for $openMs ms", failed(AuthFailure.GOOGLE_INTERRUPTED), result)
            }
        }

    @Test
    fun theThreshold_isExactly1500ms_justBeforeIsAFailure_theValueItselfIsAClosing() =
        runTest {
            assertEquals(failed(AuthFailure.GOOGLE_INTERRUPTED), sourceOf(cancelledAfter(1_499L)).requestIdToken())
            assertEquals(GoogleTokenResult.Cancelled, sourceOf(cancelledAfter(1_500L)).requestIdToken())
        }

    @Test
    fun theSpeedOnlyMattersForCancellations_otherFailuresKeepTheirOwnCause() =
        runTest {
            val quickNoAccount =
                FakeClient {
                    now += 10L
                    throw NoCredentialException("none")
                }
            assertEquals(failed(AuthFailure.GOOGLE_NO_ACCOUNT), sourceOf(quickNoAccount).requestIdToken())
            assertEquals(GoogleTokenResult.Token("t"), sourceOf(FakeClient { "t" }).requestIdToken())
        }

    @Test
    fun noGoogleAccountOnThePhone_hasItsOwnFailure() =
        runTest {
            val result = sourceOf(failingWith(NoCredentialException("no credentials available"))).requestIdToken()
            assertEquals(failed(AuthFailure.GOOGLE_NO_ACCOUNT), result)
        }

    @Test
    fun googleServicesMissingOrTooOld_haveTheirOwnFailure() =
        runTest {
            val missing = failingWith(GetCredentialProviderConfigurationException("no provider"))
            val tooOld = failingWith(GetCredentialUnsupportedException("not supported"))
            assertEquals(failed(AuthFailure.GOOGLE_SERVICES_UNAVAILABLE), sourceOf(missing).requestIdToken())
            assertEquals(failed(AuthFailure.GOOGLE_SERVICES_UNAVAILABLE), sourceOf(tooOld).requestIdToken())
        }

    @Test
    fun googleNotAnswering_isATemporaryFailure() =
        runTest {
            val result = sourceOf(failingWith(GetCredentialInterruptedException("interrupted"))).requestIdToken()
            assertEquals(failed(AuthFailure.GOOGLE_INTERRUPTED), result)
        }

    @Test
    fun everythingElse_isTheHonestGenericFailure() =
        runTest {
            val errors =
                listOf(
                    GetCredentialUnknownException("unknown"),
                    GetCredentialCustomException("some.custom.type", "custom"),
                    GoogleIdTokenParsingException(IllegalStateException("bad token")),
                    IllegalStateException("anything"),
                    SecurityException("denied"),
                )
            for (error in errors) {
                assertEquals("$error", failed(AuthFailure.UNKNOWN), sourceOf(failingWith(error)).requestIdToken())
            }
        }

    @Test
    fun anotherKindOfCredential_orAnEmptyToken_isTheGenericFailure() =
        runTest {
            assertEquals(failed(AuthFailure.UNKNOWN), sourceOf(FakeClient { null }).requestIdToken())
            assertEquals(failed(AuthFailure.UNKNOWN), sourceOf(FakeClient { "" }).requestIdToken())
            assertEquals(failed(AuthFailure.UNKNOWN), sourceOf(FakeClient { "  " }).requestIdToken())
        }

    @Test
    fun nothingOfTheSystemMessage_reachesTheResult() =
        runTest {
            val secret = "someone@example.com ya29.SECRET-TOKEN"
            val errors =
                listOf(
                    NoCredentialException(secret),
                    GetCredentialInterruptedException(secret),
                    GetCredentialUnknownException(secret),
                    RuntimeException(secret),
                )
            for (error in errors) {
                val shown = sourceOf(failingWith(error)).requestIdToken().toString()
                assertFalse(shown, shown.contains("example.com") || shown.contains("SECRET"))
            }
        }

    @Test
    fun aDialogLeftOpenForEver_endsAsACancellation_afterTheTimeout() =
        runTest {
            val client = FakeClient { awaitCancellation() }
            val result = async { sourceOf(client, timeoutMs = 1_000L).requestIdToken() }
            runCurrent()
            advanceTimeBy(999L)
            assertFalse("still waiting just before the timeout", result.isCompleted)
            advanceTimeBy(2L)
            runCurrent()
            assertEquals(GoogleTokenResult.Cancelled, result.await())
        }

    @Test
    fun leavingTheScreenWhileTheDialogIsOpen_cancelsTheRequest_insteadOfBeingTakenForAFailure() =
        runTest {
            val waiting = CompletableDeferred<String?>()
            val result = async { sourceOf(FakeClient { waiting.await() }).requestIdToken() }
            runCurrent()
            result.cancel()
            runCurrent()
            assertTrue(result.isCancelled)
            var cancelled = false
            try {
                result.await()
            } catch (e: CancellationException) {
                cancelled = true
            }
            assertTrue("the cancellation is propagated, not swallowed", cancelled)
        }

    @Test
    fun theKindOfTheExceptionDecides_notItsMessage() {
        assertEquals(GoogleTokenResult.Cancelled, googleFailureOf(GetCredentialCancellationException()))
        assertEquals(failed(AuthFailure.GOOGLE_NO_ACCOUNT), googleFailureOf(NoCredentialException()))
        assertEquals(failed(AuthFailure.GOOGLE_INTERRUPTED), googleFailureOf(GetCredentialInterruptedException()))
        assertEquals(failed(AuthFailure.UNKNOWN), googleFailureOf(GetCredentialUnknownException()))
    }
}
