package com.tomessa.miff.ui.auth

import com.tomessa.miff.data.auth.GoogleIdTokenSource
import com.tomessa.miff.data.session.SessionState
import com.tomessa.miff.domain.auth.AppDestination
import com.tomessa.miff.domain.auth.AuthFailure
import com.tomessa.miff.domain.auth.AuthOutcome
import com.tomessa.miff.domain.auth.ConsentVersions
import com.tomessa.miff.domain.auth.GoogleTokenResult
import com.tomessa.miff.domain.auth.PendingConsent
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

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelsTest {
    private class FakeGoogle(
        var result: GoogleTokenResult,
    ) : GoogleIdTokenSource {
        var calls = 0

        override suspend fun requestIdToken(): GoogleTokenResult {
            calls++
            return result
        }
    }

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        sessionScope.cancel()
        Dispatchers.resetMain()
    }

    // A plain scope on the test dispatcher: advanceUntilIdle() does not run the tasks of backgroundScope.
    private val sessionScope = CoroutineScope(dispatcher + SupervisorJob())

    private fun TestScope.harness(): SessionHarness =
        SessionHarness(sessionScope).also {
            it.manager.start()
            advanceUntilIdle()
        }

    private fun SessionHarness.destination() = (manager.state.value as? SessionState.Ready)?.destination

    // --- Sign-up screen (no box to tick: the tap on "Rejoindre" is the acceptance) ----------------------------

    @Test
    fun signUp_nothingHappensBeforeTheTap_X9() =
        runTest(dispatcher) {
            val h = harness()
            SignUpViewModel(h.manager, FakeNetwork())
            assertEquals(0, h.gateway.createCalls)
            assertTrue("no event before the tap", h.sink.events.isEmpty())
            assertFalse(h.privacy.analyticsEnabled.value)
        }

    @Test
    fun signUp_theTapIsTheAcceptance_termsAndStatistics_andNothingMoreIsAsked() =
        runTest(dispatcher) {
            val h = harness()
            val vm = SignUpViewModel(h.manager, FakeNetwork())
            vm.submitEmail("  a@b.fr ", "password-1")
            advanceUntilIdle()
            assertEquals(1, h.gateway.createCalls)
            assertEquals(AppDestination.PROFILE, h.destination())
            assertEquals(
                "(a) and (b) both recorded: the profile will be written with analyticsConsent.enabled = true",
                PendingConsent("uid-1", ConsentVersions.TERMS, analyticsAccepted = true),
                h.pending.stored,
            )
            assertTrue("collection starts with the tap", h.privacy.analyticsEnabled.value)
            assertEquals(listOf("sign_up_completed"), h.sink.names)
        }

    @Test
    fun signUp_googleTapIsTheSameAcceptance() =
        runTest(dispatcher) {
            val h = harness()
            h.accounts.setLoaded(profileExists = false)
            val vm = SignUpViewModel(h.manager, FakeNetwork())
            vm.submitGoogle(FakeGoogle(GoogleTokenResult.Token("t")))
            advanceUntilIdle()
            assertEquals(AppDestination.PROFILE, h.destination())
            assertEquals(true, h.pending.stored?.analyticsAccepted)
            assertEquals("off at the moment of the read", false to false, h.collectionWhenAccountRead)
            assertEquals(listOf("sign_up_completed"), h.sink.names)
        }

    @Test
    fun signUp_formatErrors_areCaughtOnThePhone_withoutAnyRequest_S2_S3() =
        runTest(dispatcher) {
            val h = harness()
            val vm = SignUpViewModel(h.manager, FakeNetwork())
            vm.submitEmail("not-an-address", "password-1")
            vm.submitEmail("a@b.fr", "1234567")
            advanceUntilIdle()
            assertEquals(0, h.gateway.createCalls)
            assertTrue(vm.uiState.value.showFieldErrors)
            assertFalse(vm.uiState.value.isLoading)
            assertTrue("no acceptance without an account", h.sink.events.isEmpty())
        }

    @Test
    fun signUp_addressAlreadyUsed_showsTheKindMessage_S6() =
        runTest(dispatcher) {
            val h = harness()
            h.gateway.createResult = AuthOutcome.Failure(AuthFailure.EMAIL_ALREADY_IN_USE)
            val vm = SignUpViewModel(h.manager, FakeNetwork())
            vm.submitEmail("a@b.fr", "password-1")
            advanceUntilIdle()
            assertEquals(AuthMessage.EMAIL_IN_USE, vm.uiState.value.message)
            assertFalse(vm.uiState.value.isLoading)
            assertNull("a failed sign-up records nothing", h.pending.stored)
            assertTrue(h.sink.events.isEmpty())
        }

    @Test
    fun signUp_tooManyRequests_isNotAdvisedToResetAPassword() =
        runTest(dispatcher) {
            val h = harness()
            h.gateway.createResult = AuthOutcome.Failure(AuthFailure.TOO_MANY_ATTEMPTS)
            val vm = SignUpViewModel(h.manager, FakeNetwork())
            vm.submitEmail("a@b.fr", "password-1")
            advanceUntilIdle()
            assertEquals(AuthMessage.TOO_MANY_REQUESTS, vm.uiState.value.message)
        }

    @Test
    fun signIn_googleTooManyRequests_isNotAdvisedToResetAPassword() =
        runTest(dispatcher) {
            val h = harness()
            h.gateway.googleResult = AuthOutcome.Failure(AuthFailure.TOO_MANY_ATTEMPTS)
            val vm = SignInViewModel(h.manager, FakeNetwork(), nowMillis = { currentTime })
            vm.submitGoogle(FakeGoogle(GoogleTokenResult.Token("t")))
            advanceUntilIdle()
            assertEquals(AuthMessage.TOO_MANY_REQUESTS, vm.uiState.value.message)
        }

    @Test
    fun signUp_offline_saysSo_andSendsNothing_X2() =
        runTest(dispatcher) {
            val h = harness()
            val vm = SignUpViewModel(h.manager, FakeNetwork(online = false))
            vm.submitEmail("a@b.fr", "password-1")
            advanceUntilIdle()
            assertEquals(AuthMessage.NETWORK, vm.uiState.value.message)
            assertEquals(0, h.gateway.createCalls)
        }

    @Test
    fun signUp_closingTheGoogleDialog_saysNothing_andRecordsNothing_S7() =
        runTest(dispatcher) {
            val h = harness()
            val vm = SignUpViewModel(h.manager, FakeNetwork())
            vm.submitGoogle(FakeGoogle(GoogleTokenResult.Cancelled))
            advanceUntilIdle()
            assertNull(vm.uiState.value.message)
            assertFalse(vm.uiState.value.isLoading)
            assertEquals(0, h.gateway.googleCalls)
            assertNull(h.pending.stored)
            assertTrue(h.sink.events.isEmpty())
        }

    // --- Sign-in screen -----------------------------------------------------------------------------------------

    @Test
    fun signIn_unknownAddressAndWrongPassword_leaveExactlyTheSameState_C4_C6() =
        runTest(dispatcher) {
            val h = harness()
            h.gateway.signInResult = AuthOutcome.Failure(AuthFailure.INVALID_CREDENTIALS)
            val unknown = SignInViewModel(h.manager, FakeNetwork(), nowMillis = { currentTime })
            unknown.submitEmail("nobody@b.fr", "whatever-1")
            advanceUntilIdle()
            val wrong = SignInViewModel(h.manager, FakeNetwork(), nowMillis = { currentTime })
            wrong.submitEmail("someone@b.fr", "wrong-pass-1")
            advanceUntilIdle()
            assertEquals(AuthMessage.INVALID_CREDENTIALS, unknown.uiState.value.message)
            assertEquals(unknown.uiState.value, wrong.uiState.value)
        }

    @Test
    fun signIn_aWrongAnswerIsNeverShownFasterThanTheFloor_C4() =
        runTest(dispatcher) {
            val h = harness()
            h.gateway.signInResult = AuthOutcome.Failure(AuthFailure.INVALID_CREDENTIALS)
            val vm = SignInViewModel(h.manager, FakeNetwork(), nowMillis = { currentTime })
            val before = currentTime
            vm.submitEmail("a@b.fr", "password-1")
            advanceUntilIdle()
            assertTrue(currentTime - before >= SignInViewModel.MIN_WRONG_CREDENTIALS_MILLIS)
        }

    @Test
    fun signIn_emailFormat_isCheckedOnThePhone_C3() =
        runTest(dispatcher) {
            val h = harness()
            val vm = SignInViewModel(h.manager, FakeNetwork())
            vm.submitEmail("not-an-address", "password-1")
            advanceUntilIdle()
            assertEquals(0, h.gateway.signInCalls)
            assertTrue(vm.uiState.value.showFieldErrors)
        }

    @Test
    fun signIn_fiveWrongAnswers_thenAKindPause_withoutBeingBlockedForGood_C5() =
        runTest(dispatcher) {
            val h = harness()
            h.gateway.signInResult = AuthOutcome.Failure(AuthFailure.INVALID_CREDENTIALS)
            val vm = SignInViewModel(h.manager, FakeNetwork(), nowMillis = { currentTime })
            repeat(4) {
                vm.submitEmail("a@b.fr", "password-1")
                advanceUntilIdle()
                assertEquals(AuthMessage.INVALID_CREDENTIALS, vm.uiState.value.message)
            }
            vm.submitEmail("a@b.fr", "password-1")
            advanceUntilIdle()
            assertEquals(AuthMessage.TOO_MANY_ATTEMPTS, vm.uiState.value.message)
            assertEquals(5, h.gateway.signInCalls)

            vm.submitEmail("a@b.fr", "password-1")
            advanceUntilIdle()
            assertEquals("no request during the pause", 5, h.gateway.signInCalls)
            assertEquals(AuthMessage.TOO_MANY_ATTEMPTS, vm.uiState.value.message)

            testScheduler.advanceTimeBy(31_000)
            vm.submitEmail("a@b.fr", "password-1")
            advanceUntilIdle()
            assertEquals(6, h.gateway.signInCalls)
        }

    @Test
    fun signIn_firebaseTemporaryBlock_usesTheSameText_C5() =
        runTest(dispatcher) {
            val h = harness()
            h.gateway.signInResult = AuthOutcome.Failure(AuthFailure.TOO_MANY_ATTEMPTS)
            val vm = SignInViewModel(h.manager, FakeNetwork(), nowMillis = { currentTime })
            vm.submitEmail("a@b.fr", "password-1")
            advanceUntilIdle()
            assertEquals(AuthMessage.TOO_MANY_ATTEMPTS, vm.uiState.value.message)
        }

    @Test
    fun signIn_success_goesToTheStateOfTheAccount_C2() =
        runTest(dispatcher) {
            val h = harness()
            h.accounts.setLoaded(profileExists = true, hasMembership = true)
            val vm = SignInViewModel(h.manager, FakeNetwork(), nowMillis = { currentTime })
            vm.submitEmail("a@b.fr", "password-1")
            advanceUntilIdle()
            assertEquals(AppDestination.FEED, h.destination())
            assertNull(vm.uiState.value.message)
        }

    @Test
    fun signIn_offline_saysSo_X2() =
        runTest(dispatcher) {
            val h = harness()
            val vm = SignInViewModel(h.manager, FakeNetwork(online = false))
            vm.submitEmail("a@b.fr", "password-1")
            advanceUntilIdle()
            assertEquals(AuthMessage.NETWORK, vm.uiState.value.message)
            assertEquals(0, h.gateway.signInCalls)
        }

    @Test
    fun signIn_closingTheGoogleDialog_saysNothing_S7() =
        runTest(dispatcher) {
            val h = harness()
            val vm = SignInViewModel(h.manager, FakeNetwork())
            vm.submitGoogle(FakeGoogle(GoogleTokenResult.Cancelled))
            advanceUntilIdle()
            assertNull(vm.uiState.value.message)
            assertFalse(vm.uiState.value.isLoading)
        }

    @Test
    fun signIn_googleWithANewAccount_leadsToThePanel_C8() =
        runTest(dispatcher) {
            val h = harness()
            h.accounts.setLoaded(profileExists = false)
            val vm = SignInViewModel(h.manager, FakeNetwork())
            vm.submitGoogle(FakeGoogle(GoogleTokenResult.Token("t")))
            advanceUntilIdle()
            assertEquals(AppDestination.CONSENT_PANEL, h.destination())
        }

    @Test
    fun signIn_showsTheReassuringNoteAfterARefusal_andOnlyOnce() =
        runTest(dispatcher) {
            val h = harness()
            h.accounts.setLoaded(profileExists = false)
            h.manager.signInWithGoogle("t", signUpConsent = null)
            h.manager.refuseConsentPanel { GoogleTokenResult.Unavailable }
            val first = SignInViewModel(h.manager, FakeNetwork())
            assertTrue(first.uiState.value.showAccountNotCreatedNote)
            val second = SignInViewModel(h.manager, FakeNetwork())
            assertFalse(second.uiState.value.showAccountNotCreatedNote)
        }

    @Test
    fun signIn_afterASessionLostBehindOurBack_explainsKindly() =
        runTest(dispatcher) {
            val h = harness()
            h.gateway.setSignedIn("uid-1")
            h.accounts.setLoaded(profileExists = true, hasMembership = true)
            h.manager.retry()
            advanceUntilIdle()
            assertEquals(AppDestination.FEED, h.destination())
            h.gateway.setSignedIn(null)
            advanceUntilIdle()
            val vm = SignInViewModel(h.manager, FakeNetwork())
            assertEquals(AuthMessage.ACCOUNT_UNAVAILABLE, vm.uiState.value.message)
        }

    // --- Consent panel (no box: "Continuer" accepts, "Annuler" refuses) --------------------------------------------

    @Test
    fun panel_continueIsTheAcceptance_C11() =
        runTest(dispatcher) {
            val h = harness()
            h.accounts.setLoaded(profileExists = false)
            h.manager.signInWithGoogle("t", signUpConsent = null)
            assertEquals("nothing collected nor sent while the panel is open", true, h.sink.events.isEmpty())
            val vm = ConsentPanelViewModel(h.manager)

            vm.accept()

            assertEquals(AppDestination.PROFILE, h.destination())
            assertEquals(PendingConsent("uid-1", ConsentVersions.TERMS, analyticsAccepted = true), h.pending.stored)
            assertEquals(listOf("sign_up_completed"), h.sink.names)
        }

    @Test
    fun panel_refusal_keepsNothing_andSendsNothing_C12_C13() =
        runTest(dispatcher) {
            val h = harness()
            h.accounts.setLoaded(profileExists = false)
            h.manager.signInWithGoogle("t", signUpConsent = null)
            val vm = ConsentPanelViewModel(h.manager)

            vm.refuse(FakeGoogle(GoogleTokenResult.Unavailable))
            advanceUntilIdle()

            assertEquals(AppDestination.SIGN_IN, h.destination())
            assertNull(h.pending.stored)
            assertTrue("not a single event", h.sink.events.isEmpty())
            assertFalse(h.analyticsSwitch.calls.any { it })
        }

    @Test
    fun panel_refusalThatCannotDeleteTheAccount_staysOnThePanelWithAKindMessage_C14() =
        runTest(dispatcher) {
            val h = harness()
            h.accounts.setLoaded(profileExists = false)
            h.manager.signInWithGoogle("t", signUpConsent = null)
            h.gateway.deleteResults.addLast(AuthFailure.NETWORK)
            val vm = ConsentPanelViewModel(h.manager)

            vm.refuse(FakeGoogle(GoogleTokenResult.Unavailable))
            advanceUntilIdle()

            assertEquals(AppDestination.CONSENT_PANEL, h.destination())
            assertEquals(AuthMessage.NETWORK, vm.uiState.value.message)
            assertFalse(vm.uiState.value.isLoading)
        }
}
