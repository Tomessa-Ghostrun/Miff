package com.tomessa.miff.ui.auth

import com.tomessa.miff.data.auth.GoogleIdTokenSource
import com.tomessa.miff.data.session.SessionState
import com.tomessa.miff.domain.auth.AppDestination
import com.tomessa.miff.domain.auth.AuthFailure
import com.tomessa.miff.domain.auth.AuthOutcome
import com.tomessa.miff.domain.auth.GoogleTokenResult
import com.tomessa.miff.testsupport.FakeNetwork
import com.tomessa.miff.testsupport.SessionHarness
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Every way the Google sign-in can fail, on the sign-up AND the sign-in screen (ticket "message d'erreur clair quand la
 * connexion Google échoue"): a calm message per cause, nothing at all when the person closes the dialog, the message
 * stays until the next attempt, and the Google button (not blocked) is the way to try again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GoogleErrorViewModelsTest {
    /** Answers the scripted results in order; the last one repeats. */
    private class ScriptedGoogle(
        private vararg val results: GoogleTokenResult,
        private val beforeAnswer: () -> Unit = {},
    ) : GoogleIdTokenSource {
        var calls = 0

        override suspend fun requestIdToken(): GoogleTokenResult {
            beforeAnswer()
            return results[minOf(calls++, results.size - 1)]
        }
    }

    private val dispatcher = StandardTestDispatcher()
    private val sessionScope = CoroutineScope(dispatcher + SupervisorJob())

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        sessionScope.cancel()
        Dispatchers.resetMain()
    }

    private fun TestScope.harness(): SessionHarness =
        SessionHarness(sessionScope).also {
            it.manager.start()
            advanceUntilIdle()
        }

    private fun failed(failure: AuthFailure) = GoogleTokenResult.Failed(failure)

    /** What the person must be told for each cause of the Google dialog (the same on both screens). */
    private val causes =
        listOf(
            failed(AuthFailure.GOOGLE_NO_ACCOUNT) to AuthMessage.GOOGLE_NO_ACCOUNT,
            failed(AuthFailure.GOOGLE_SERVICES_UNAVAILABLE) to AuthMessage.GOOGLE_SERVICES_UNAVAILABLE,
            failed(AuthFailure.GOOGLE_INTERRUPTED) to AuthMessage.GOOGLE_INTERRUPTED,
            failed(AuthFailure.UNKNOWN) to AuthMessage.UNKNOWN,
            GoogleTokenResult.Unavailable to AuthMessage.UNKNOWN,
        )

    // --- Sign-up screen -------------------------------------------------------------------------------------

    @Test
    fun signUp_eachCauseGivesItsOwnKindMessage_underTheGoogleButton() =
        runTest(dispatcher) {
            for ((result, expected) in causes) {
                val h = harness()
                val vm = SignUpViewModel(h.manager, FakeNetwork())
                vm.submitGoogle(ScriptedGoogle(result))
                advanceUntilIdle()
                assertEquals("$result", expected, vm.uiState.value.googleMessage)
                assertNull("the e-mail area stays empty", vm.uiState.value.message)
                assertFalse("the screen is not blocked", vm.uiState.value.isLoading)
                assertEquals("no account was created for $result", 0, h.gateway.googleCalls)
                assertTrue(h.sink.events.isEmpty())
            }
        }

    @Test
    fun signUp_closingTheDialog_showsNoMessage_atAll() =
        runTest(dispatcher) {
            val h = harness()
            val vm = SignUpViewModel(h.manager, FakeNetwork())
            vm.submitGoogle(ScriptedGoogle(GoogleTokenResult.Cancelled))
            advanceUntilIdle()
            assertNull(vm.uiState.value.googleMessage)
            assertNull(vm.uiState.value.message)
            assertFalse(vm.uiState.value.isLoading)
        }

    @Test
    fun signUp_theMessageStays_untilANewAttempt_thenGoesAway() =
        runTest(dispatcher) {
            val h = harness()
            h.accounts.setLoaded(profileExists = false)
            val vm = SignUpViewModel(h.manager, FakeNetwork())
            val source = ScriptedGoogle(failed(AuthFailure.GOOGLE_INTERRUPTED), GoogleTokenResult.Token("t"))
            vm.submitGoogle(source)
            advanceUntilIdle()
            assertEquals(AuthMessage.GOOGLE_INTERRUPTED, vm.uiState.value.googleMessage)
            // Time passes, the person types: the message is still there.
            advanceUntilIdle()
            assertEquals(AuthMessage.GOOGLE_INTERRUPTED, vm.uiState.value.googleMessage)

            // The Google button itself tries again, and the message goes away at once.
            vm.submitGoogle(source)
            assertNull("gone as soon as the new attempt starts", vm.uiState.value.googleMessage)
            assertTrue(vm.uiState.value.isLoading)
            advanceUntilIdle()
            assertEquals(2, source.calls)
            assertNull(vm.uiState.value.googleMessage)
            assertEquals(AppDestination.PROFILE, (h.manager.state.value as SessionState.Ready).destination)
        }

    @Test
    fun signUp_aNewFailureReplacesThePreviousOne() =
        runTest(dispatcher) {
            val h = harness()
            val vm = SignUpViewModel(h.manager, FakeNetwork())
            val source =
                ScriptedGoogle(failed(AuthFailure.GOOGLE_NO_ACCOUNT), failed(AuthFailure.GOOGLE_SERVICES_UNAVAILABLE))
            vm.submitGoogle(source)
            advanceUntilIdle()
            vm.submitGoogle(source)
            advanceUntilIdle()
            assertEquals(AuthMessage.GOOGLE_SERVICES_UNAVAILABLE, vm.uiState.value.googleMessage)
        }

    @Test
    fun signUp_closingTheDialogAfterAFailure_clearsTheOldMessage_andShowsNothing() =
        runTest(dispatcher) {
            val h = harness()
            val vm = SignUpViewModel(h.manager, FakeNetwork())
            val source = ScriptedGoogle(failed(AuthFailure.GOOGLE_NO_ACCOUNT), GoogleTokenResult.Cancelled)
            vm.submitGoogle(source)
            advanceUntilIdle()
            vm.submitGoogle(source)
            advanceUntilIdle()
            assertNull(vm.uiState.value.googleMessage)
        }

    @Test
    fun signUp_aTokenRefusedByFirebase_isTold_withoutAnyTechnicalDetail() =
        runTest(dispatcher) {
            val expected =
                mapOf(
                    AuthFailure.NETWORK to AuthMessage.NETWORK,
                    AuthFailure.TOO_MANY_ATTEMPTS to AuthMessage.TOO_MANY_REQUESTS,
                    AuthFailure.ACCOUNT_DISABLED to AuthMessage.ACCOUNT_UNAVAILABLE,
                    AuthFailure.LINKED_TO_OTHER_METHOD to AuthMessage.OTHER_METHOD,
                    AuthFailure.UNKNOWN to AuthMessage.UNKNOWN,
                )
            for ((failure, message) in expected) {
                val h = harness()
                h.gateway.googleResult = AuthOutcome.Failure(failure)
                val vm = SignUpViewModel(h.manager, FakeNetwork())
                vm.submitGoogle(ScriptedGoogle(GoogleTokenResult.Token("t")))
                advanceUntilIdle()
                assertEquals("$failure", message, vm.uiState.value.googleMessage)
                assertFalse(vm.uiState.value.isLoading)
            }
        }

    @Test
    fun signUp_noNetworkAtTheTap_saysSo_withoutOpeningTheDialog() =
        runTest(dispatcher) {
            val h = harness()
            val vm = SignUpViewModel(h.manager, FakeNetwork(online = false))
            val source = ScriptedGoogle(GoogleTokenResult.Token("t"))
            vm.submitGoogle(source)
            advanceUntilIdle()
            assertEquals(AuthMessage.NETWORK, vm.uiState.value.googleMessage)
            assertEquals(0, source.calls)
        }

    @Test
    fun signUp_anUnclearFailureOnAPhoneThatWentOffline_isToldAsNoConnection() =
        runTest(dispatcher) {
            for (unclear in listOf(AuthFailure.UNKNOWN, AuthFailure.GOOGLE_INTERRUPTED)) {
                val h = harness()
                val network = FakeNetwork()
                val vm = SignUpViewModel(h.manager, network)
                vm.submitGoogle(ScriptedGoogle(failed(unclear), beforeAnswer = { network.online = false }))
                advanceUntilIdle()
                assertEquals("$unclear", AuthMessage.NETWORK, vm.uiState.value.googleMessage)
            }
        }

    @Test
    fun signUp_aClearCauseIsNotHiddenBehindNoConnection() =
        runTest(dispatcher) {
            val h = harness()
            val network = FakeNetwork()
            val vm = SignUpViewModel(h.manager, network)
            vm.submitGoogle(
                ScriptedGoogle(failed(AuthFailure.GOOGLE_NO_ACCOUNT), beforeAnswer = { network.online = false }),
            )
            advanceUntilIdle()
            assertEquals(AuthMessage.GOOGLE_NO_ACCOUNT, vm.uiState.value.googleMessage)
        }

    @Test
    fun signUp_anEmailAttemptClearsTheGoogleMessage() =
        runTest(dispatcher) {
            val h = harness()
            val vm = SignUpViewModel(h.manager, FakeNetwork())
            vm.submitGoogle(ScriptedGoogle(failed(AuthFailure.GOOGLE_NO_ACCOUNT)))
            advanceUntilIdle()
            vm.submitEmail("a@b.fr", "password-1")
            assertNull(vm.uiState.value.googleMessage)
        }

    @Test
    fun signUp_whileOneAttemptRuns_aSecondTapDoesNothing() =
        runTest(dispatcher) {
            val h = harness()
            val vm = SignUpViewModel(h.manager, FakeNetwork())
            val source = ScriptedGoogle(failed(AuthFailure.GOOGLE_NO_ACCOUNT))
            vm.submitGoogle(source)
            vm.submitGoogle(source)
            advanceUntilIdle()
            assertEquals(1, source.calls)
        }

    // --- Sign-in screen -------------------------------------------------------------------------------------

    @Test
    fun signIn_eachCauseGivesItsOwnKindMessage_underTheGoogleButton() =
        runTest(dispatcher) {
            for ((result, expected) in causes) {
                val h = harness()
                val vm = SignInViewModel(h.manager, FakeNetwork(), nowMillis = { currentTime })
                vm.submitGoogle(ScriptedGoogle(result))
                advanceUntilIdle()
                assertEquals("$result", expected, vm.uiState.value.googleMessage)
                assertNull(vm.uiState.value.message)
                assertFalse(vm.uiState.value.isLoading)
                assertEquals(0, h.gateway.googleCalls)
            }
        }

    @Test
    fun signIn_closingTheDialog_showsNoMessage_atAll() =
        runTest(dispatcher) {
            val h = harness()
            val vm = SignInViewModel(h.manager, FakeNetwork(), nowMillis = { currentTime })
            vm.submitGoogle(ScriptedGoogle(GoogleTokenResult.Cancelled))
            advanceUntilIdle()
            assertNull(vm.uiState.value.googleMessage)
            assertNull(vm.uiState.value.message)
            assertFalse(vm.uiState.value.isLoading)
        }

    @Test
    fun signIn_theMessageStays_untilANewAttempt_thenGoesAway() =
        runTest(dispatcher) {
            val h = harness()
            h.accounts.setLoaded(profileExists = true)
            val vm = SignInViewModel(h.manager, FakeNetwork(), nowMillis = { currentTime })
            val source = ScriptedGoogle(failed(AuthFailure.GOOGLE_NO_ACCOUNT), GoogleTokenResult.Token("t"))
            vm.submitGoogle(source)
            advanceUntilIdle()
            assertEquals(AuthMessage.GOOGLE_NO_ACCOUNT, vm.uiState.value.googleMessage)

            vm.submitGoogle(source)
            assertNull(vm.uiState.value.googleMessage)
            advanceUntilIdle()
            assertNull(vm.uiState.value.googleMessage)
            assertEquals(1, h.gateway.googleCalls)
        }

    @Test
    fun signIn_aTokenRefusedByFirebase_isTold_withoutAnyTechnicalDetail() =
        runTest(dispatcher) {
            val h = harness()
            h.gateway.googleResult = AuthOutcome.Failure(AuthFailure.ACCOUNT_DISABLED)
            val vm = SignInViewModel(h.manager, FakeNetwork(), nowMillis = { currentTime })
            vm.submitGoogle(ScriptedGoogle(GoogleTokenResult.Token("t")))
            advanceUntilIdle()
            assertEquals(AuthMessage.ACCOUNT_UNAVAILABLE, vm.uiState.value.googleMessage)
            assertNull(vm.uiState.value.message)
        }

    @Test
    fun signIn_noNetworkAtTheTap_saysSo_underTheGoogleButton() =
        runTest(dispatcher) {
            val h = harness()
            val vm = SignInViewModel(h.manager, FakeNetwork(online = false), nowMillis = { currentTime })
            val source = ScriptedGoogle(GoogleTokenResult.Token("t"))
            vm.submitGoogle(source)
            advanceUntilIdle()
            assertEquals(AuthMessage.NETWORK, vm.uiState.value.googleMessage)
            assertEquals(0, source.calls)
        }

    @Test
    fun signIn_anUnclearFailureOnAPhoneThatWentOffline_isToldAsNoConnection() =
        runTest(dispatcher) {
            val h = harness()
            val network = FakeNetwork()
            val vm = SignInViewModel(h.manager, network, nowMillis = { currentTime })
            vm.submitGoogle(ScriptedGoogle(failed(AuthFailure.UNKNOWN), beforeAnswer = { network.online = false }))
            advanceUntilIdle()
            assertEquals(AuthMessage.NETWORK, vm.uiState.value.googleMessage)
        }

    @Test
    fun signIn_anEmailAttemptClearsTheGoogleMessage() =
        runTest(dispatcher) {
            val h = harness()
            val vm = SignInViewModel(h.manager, FakeNetwork(), nowMillis = { currentTime })
            vm.submitGoogle(ScriptedGoogle(failed(AuthFailure.GOOGLE_NO_ACCOUNT)))
            advanceUntilIdle()
            vm.submitEmail("a@b.fr", "password-1")
            assertNull(vm.uiState.value.googleMessage)
        }

    // --- Consent panel: the Google dialog only re-checks who the person is -------------------------------------

    /** Asks for the recent sign-in: the plain request fails like a sign-in button would, the re-check does what is scripted. */
    private class ReauthGoogle(
        private val reauth: GoogleTokenResult,
    ) : GoogleIdTokenSource {
        var plainCalls = 0
        var reauthCalls = 0

        override suspend fun requestIdToken(): GoogleTokenResult {
            plainCalls++
            return GoogleTokenResult.Failed(AuthFailure.GOOGLE_INTERRUPTED)
        }

        override suspend fun requestIdTokenForReauthentication(): GoogleTokenResult {
            reauthCalls++
            return reauth
        }
    }

    private fun TestScope.panelNeedingARecentSignIn(): Pair<SessionHarness, ConsentPanelViewModel> {
        val h = harness()
        h.accounts.setLoaded(profileExists = false)
        h.gateway.googleResult = AuthOutcome.Success("uid-1", isNewUser = true)
        h.gateway.deleteResults.addLast(AuthFailure.RECENT_LOGIN_REQUIRED)
        return h to ConsentPanelViewModel(h.manager)
    }

    @Test
    fun panel_closingTheRecheckDialog_staysSilent_evenWhenItIsQuick() =
        runTest(dispatcher) {
            val (h, vm) = panelNeedingARecentSignIn()
            h.manager.signInWithGoogle("t", signUpConsent = null)
            val source = ReauthGoogle(GoogleTokenResult.Cancelled)
            vm.refuse(source)
            advanceUntilIdle()
            assertEquals("the re-check variant is the one used", 1, source.reauthCalls)
            assertEquals(0, source.plainCalls)
            assertNull(vm.uiState.value.message)
            assertFalse(vm.uiState.value.isLoading)
            assertEquals(AppDestination.CONSENT_PANEL, (h.manager.state.value as SessionState.Ready).destination)
        }

    @Test
    fun panel_aFailureOfTheRecheckDialog_showsThePlainNeutralMessage_notTheSignInOnes() =
        runTest(dispatcher) {
            val causes =
                listOf(
                    AuthFailure.GOOGLE_NO_ACCOUNT,
                    AuthFailure.GOOGLE_SERVICES_UNAVAILABLE,
                    AuthFailure.GOOGLE_INTERRUPTED,
                    AuthFailure.UNKNOWN,
                )
            for (cause in causes) {
                val (h, vm) = panelNeedingARecentSignIn()
                h.manager.signInWithGoogle("t", signUpConsent = null)
                vm.refuse(ReauthGoogle(GoogleTokenResult.Failed(cause)))
                advanceUntilIdle()
                assertEquals("$cause", AuthMessage.UNKNOWN, vm.uiState.value.message)
                assertFalse(vm.uiState.value.isLoading)
            }
        }

    // --- Mapping of the results to messages -------------------------------------------------------------------

    @Test
    fun onlyClosingTheDialog_andAToken_haveNoMessage() {
        assertNull(GoogleTokenResult.Cancelled.toMessage())
        assertNull(GoogleTokenResult.Token("t").toMessage())
        for ((result, expected) in causes) assertEquals("$result", expected, result.toMessage())
    }

    @Test
    fun everyFailureHasAMessage_andOnlyNetworkLikeOnesBecomeNoConnection() {
        for (failure in AuthFailure.entries) failure.toMessage() // total: no failure is left without a message
        assertEquals(AuthMessage.NETWORK, AuthMessage.UNKNOWN.orNoConnectionIf(offline = true))
        assertEquals(AuthMessage.NETWORK, AuthMessage.GOOGLE_INTERRUPTED.orNoConnectionIf(offline = true))
        assertEquals(AuthMessage.UNKNOWN, AuthMessage.UNKNOWN.orNoConnectionIf(offline = false))
        assertEquals(AuthMessage.GOOGLE_NO_ACCOUNT, AuthMessage.GOOGLE_NO_ACCOUNT.orNoConnectionIf(offline = true))
        assertEquals(
            AuthMessage.ACCOUNT_UNAVAILABLE,
            AuthMessage.ACCOUNT_UNAVAILABLE.orNoConnectionIf(offline = true),
        )
    }
}
