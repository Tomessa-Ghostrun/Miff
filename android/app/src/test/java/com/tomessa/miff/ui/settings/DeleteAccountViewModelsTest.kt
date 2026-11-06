package com.tomessa.miff.ui.settings

import com.tomessa.miff.data.account.Inventory
import com.tomessa.miff.data.auth.GoogleIdTokenSource
import com.tomessa.miff.domain.auth.AuthFailure
import com.tomessa.miff.domain.auth.GoogleTokenResult
import com.tomessa.miff.domain.auth.SignInProvider
import com.tomessa.miff.testsupport.FakeAuthGateway
import com.tomessa.miff.testsupport.FakeNetwork
import com.tomessa.miff.testsupport.FakeProcedure
import com.tomessa.miff.ui.auth.AuthMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
 * The information screen, its dialog and the re-authentication of the deletion of the account (ticket AUTH-5, criteria SC2, SC4, SC6, SC9 to
 * SC14, SC47, SA5): nothing is written before a valid re-authentication, one gesture is one operation, every cause has its calm sentence,
 * and the password is never kept.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeleteAccountViewModelsTest {
    private val dispatcher = StandardTestDispatcher()
    private val network = FakeNetwork()
    private val procedure = FakeProcedure()
    private val gateway = FakeAuthGateway().also { it.setSignedIn("uid-1") }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun info(appleLinked: Boolean = false) =
        DeleteAccountInfoViewModel(procedure, { gateway.currentUid }, network, appleLinked)

    // --- Screen A ----------------------------------------------------------------------------------------------

    @Test
    fun noMiff_theListIsReadFromTheServer_andContinueIsAvailable() =
        runTest(dispatcher) {
            val vm = info()
            assertEquals(DeleteInventory.LOADING, vm.uiState.value.inventory)
            assertFalse("greyed while the list loads", vm.uiState.value.canContinue)
            advanceUntilIdle()
            assertEquals(DeleteInventory.NO_MIFFS, vm.uiState.value.inventory)
            assertTrue(vm.uiState.value.canContinue)
        }

    @Test
    fun aMiff_isDetected_theCalmStateShows_andContinueStaysGreyed_nothingIsTouched() =
        runTest(dispatcher) {
            procedure.inventory = Inventory.HasMiffs
            val vm = info()
            advanceUntilIdle()
            assertEquals(DeleteInventory.HAS_MIFFS, vm.uiState.value.inventory)
            assertFalse(vm.uiState.value.canContinue)
            vm.onContinue()
            assertFalse("no dialog on an account with Miffs", vm.uiState.value.confirming)
            assertEquals(0, procedure.runs)
        }

    @Test
    fun offline_theScreenOpens_continueIsGreyed_andTheListComesByItselfWhenTheNetworkIsBack() =
        runTest(dispatcher) {
            network.online = false
            val vm = info()
            advanceUntilIdle()
            assertEquals(DeleteInventory.OFFLINE, vm.uiState.value.inventory)
            assertFalse(vm.uiState.value.canContinue)
            network.online = true
            advanceUntilIdle()
            assertEquals(DeleteInventory.NO_MIFFS, vm.uiState.value.inventory)
        }

    @Test
    fun aFailedRead_neverShowsAnIncompleteList_andRetryWorks() =
        runTest(dispatcher) {
            procedure.inventory = Inventory.Failed
            val vm = info()
            advanceUntilIdle()
            assertEquals(DeleteInventory.READ_FAILED, vm.uiState.value.inventory)
            assertFalse(vm.uiState.value.canContinue)
            procedure.inventory = Inventory.NoMiffs
            vm.load()
            advanceUntilIdle()
            assertEquals(DeleteInventory.NO_MIFFS, vm.uiState.value.inventory)
        }

    @Test
    fun theAppleLink_isKnownToTheScreen_forTheEncart() =
        runTest(dispatcher) {
            val vm = info(appleLinked = true)
            advanceUntilIdle()
            assertTrue(vm.uiState.value.appleLinked)
        }

    // --- Dialog B ------------------------------------------------------------------------------------------------

    @Test
    fun continue_opensTheDialog_keepClosesItWithNothingTouched() =
        runTest(dispatcher) {
            val vm = info()
            advanceUntilIdle()
            vm.onContinue()
            assertTrue(vm.uiState.value.confirming)
            vm.onKeep()
            assertFalse(vm.uiState.value.confirming)
            assertEquals(0, procedure.runs)
            assertEquals(0, gateway.deleteCalls)
        }

    @Test
    fun confirm_goesOnToTheReauthentication_onlyOnline() =
        runTest(dispatcher) {
            val vm = info()
            advanceUntilIdle()
            vm.onContinue()
            network.online = false
            assertFalse(vm.onConfirm())
            assertTrue(
                "a calm notice, the dialog stays",
                vm.uiState.value.confirming && vm.uiState.value.confirmOffline,
            )
            network.online = true
            assertTrue(vm.onConfirm())
            assertFalse(vm.uiState.value.confirming)
            // Still nothing deleted: the re-authentication comes first.
            assertEquals(0, procedure.runs)
        }

    // --- Screen C --------------------------------------------------------------------------------------------------

    private class Spy {
        var verified = 0
    }

    private fun reauth(
        spy: Spy,
        last: Boolean = false,
    ) = DeleteReauthViewModel(gateway, network, last) { spy.verified++ }

    @Test
    fun theMethodsOffered_areTheOnesReallyLinked_neverAnAppleButtonOnAndroid() {
        gateway.providers = setOf(SignInProvider.PASSWORD, SignInProvider.APPLE)
        val vm = reauth(Spy())
        val state = vm.uiState.value
        assertTrue(state.hasPassword)
        assertFalse(state.hasGoogle)
        assertFalse(SignInProvider.APPLE in state.providers)
    }

    @Test
    fun aGoogleOnlyAccount_isOfferedGoogleOnly() {
        gateway.providers = setOf(SignInProvider.GOOGLE)
        val state = reauth(Spy()).uiState.value
        assertTrue(state.hasGoogle)
        assertFalse(state.hasPassword)
    }

    @Test
    fun anAccountThatOnlyKnowsApple_hasNoMethodThisPhoneCanUse() {
        gateway.providers = setOf(SignInProvider.APPLE)
        assertTrue(reauth(Spy()).uiState.value.noMethod)
    }

    @Test
    fun aRightPassword_startsTheDeletion_exactlyOnce_evenWithADoubleTap() =
        runTest(dispatcher) {
            val spy = Spy()
            val vm = reauth(spy)
            vm.submitPassword("Mimosa2026!")
            vm.submitPassword("Mimosa2026!")
            advanceUntilIdle()
            assertEquals(1, spy.verified)
            assertEquals(1, gateway.passwordReauthCalls)
        }

    @Test
    fun aWrongPassword_staysOnTheScreen_withTheCalmSentence_andNothingStarts() =
        runTest(dispatcher) {
            gateway.passwordReauthResult = AuthFailure.INVALID_CREDENTIALS
            val spy = Spy()
            val vm = reauth(spy)
            vm.submitPassword("nope")
            advanceUntilIdle()
            assertEquals(AuthMessage.REAUTH_PASSWORD_WRONG, vm.uiState.value.message)
            assertFalse(vm.uiState.value.busy)
            assertEquals(0, spy.verified)
            // No punitive counter: the person can try again at once.
            gateway.passwordReauthResult = null
            vm.submitPassword("right")
            advanceUntilIdle()
            assertEquals(1, spy.verified)
        }

    @Test
    fun everyCause_hasItsCalmSentence_andNeverStartsTheDeletion() =
        runTest(dispatcher) {
            val causes =
                mapOf(
                    AuthFailure.TOO_MANY_ATTEMPTS to AuthMessage.TOO_MANY_ATTEMPTS,
                    AuthFailure.NETWORK to AuthMessage.NETWORK,
                    AuthFailure.ACCOUNT_GONE to AuthMessage.REAUTH_EXPIRED,
                    AuthFailure.SESSION_EXPIRED to AuthMessage.REAUTH_EXPIRED,
                    AuthFailure.UNKNOWN to AuthMessage.UNKNOWN,
                    AuthFailure.RECENT_LOGIN_REQUIRED to AuthMessage.UNKNOWN,
                )
            causes.forEach { (failure, message) ->
                gateway.passwordReauthResult = failure
                val spy = Spy()
                val vm = reauth(spy)
                vm.submitPassword("x")
                advanceUntilIdle()
                assertEquals("$failure", message, vm.uiState.value.message)
                assertEquals("$failure", 0, spy.verified)
            }
        }

    @Test
    fun offline_nothingIsSentToFirebase_theNeutralSentenceShows() =
        runTest(dispatcher) {
            network.online = false
            val spy = Spy()
            val vm = reauth(spy)
            vm.submitPassword("x")
            advanceUntilIdle()
            assertEquals(AuthMessage.NETWORK, vm.uiState.value.message)
            assertEquals(0, gateway.passwordReauthCalls)
            assertEquals(0, spy.verified)
        }

    @Test
    fun whenTheScreenIsLeft_nothingStaysBusy_andNoOldMessageStays() =
        runTest(dispatcher) {
            val vm = reauth(Spy())
            // The proof was accepted (the screen stays "busy" while the deletion starts)...
            vm.submitPassword("again")
            advanceUntilIdle()
            assertTrue(vm.uiState.value.busy)
            // ...and the screen is left (the deletion went on, or stopped and came back): the next visit starts clean.
            vm.resetBusy()
            assertFalse(vm.uiState.value.busy)
            assertNull(vm.uiState.value.message)
        }

    @Test
    fun anEmptyPassword_isNeverSent() =
        runTest(dispatcher) {
            val vm = reauth(Spy())
            vm.submitPassword("")
            advanceUntilIdle()
            assertEquals(0, gateway.passwordReauthCalls)
        }

    @Test
    fun thePasswordIsNeverKept_noFieldOfTheStateHoldsIt() =
        runTest(dispatcher) {
            val vm = reauth(Spy())
            vm.submitPassword("Mimosa2026!")
            advanceUntilIdle()
            assertFalse(
                vm.uiState.value
                    .toString()
                    .contains("Mimosa2026!"),
            )
        }

    // --- Google ----------------------------------------------------------------------------------------------------

    private class Google(
        private val result: GoogleTokenResult,
    ) : GoogleIdTokenSource {
        var asked = 0

        override suspend fun requestIdToken(): GoogleTokenResult = result

        override suspend fun requestIdTokenForReauthentication(): GoogleTokenResult {
            asked++
            return result
        }
    }

    @Test
    fun google_aGoodToken_startsTheDeletion() =
        runTest(dispatcher) {
            gateway.providers = setOf(SignInProvider.GOOGLE)
            val spy = Spy()
            val vm = reauth(spy)
            vm.submitGoogle(Google(GoogleTokenResult.Token("token")))
            advanceUntilIdle()
            assertEquals(1, spy.verified)
        }

    @Test
    fun google_anotherAccountThanTheSession_isRefused_andNothingStarts() =
        runTest(dispatcher) {
            gateway.providers = setOf(SignInProvider.GOOGLE)
            gateway.reauthResult = AuthFailure.WRONG_ACCOUNT
            val spy = Spy()
            val vm = reauth(spy)
            vm.submitGoogle(Google(GoogleTokenResult.Token("token")))
            advanceUntilIdle()
            assertEquals(AuthMessage.REAUTH_WRONG_ACCOUNT, vm.uiState.value.message)
            assertEquals(0, spy.verified)
            assertFalse(vm.uiState.value.busy)
        }

    @Test
    fun google_closingTheWindow_saysNothing_andLeavesTheScreenIntact() =
        runTest(dispatcher) {
            gateway.providers = setOf(SignInProvider.GOOGLE)
            val vm = reauth(Spy())
            vm.submitGoogle(Google(GoogleTokenResult.Cancelled))
            advanceUntilIdle()
            assertNull(vm.uiState.value.message)
            assertFalse(vm.uiState.value.busy)
        }

    @Test
    fun google_tooManyTries_usesTheSentenceWithoutPasswordAdvice() =
        runTest(dispatcher) {
            gateway.providers = setOf(SignInProvider.GOOGLE)
            gateway.reauthResult = AuthFailure.TOO_MANY_ATTEMPTS
            val vm = reauth(Spy())
            vm.submitGoogle(Google(GoogleTokenResult.Token("token")))
            advanceUntilIdle()
            assertEquals(AuthMessage.TOO_MANY_REQUESTS, vm.uiState.value.message)
        }

    @Test
    fun theLastVerification_isTheSameScreen_andAsksTheSameProof() =
        runTest(dispatcher) {
            val spy = Spy()
            val vm = reauth(spy, last = true)
            assertTrue(vm.uiState.value.last)
            vm.submitPassword("again")
            advanceUntilIdle()
            assertEquals(1, spy.verified)
        }
}
