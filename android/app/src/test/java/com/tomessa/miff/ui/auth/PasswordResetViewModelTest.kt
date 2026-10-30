package com.tomessa.miff.ui.auth

import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.tomessa.miff.data.auth.PasswordResetGateway
import com.tomessa.miff.data.auth.attemptPasswordReset
import com.tomessa.miff.domain.auth.AuthFailure
import com.tomessa.miff.domain.auth.PasswordResetOutcome
import com.tomessa.miff.domain.auth.ResendCooldown
import com.tomessa.miff.testsupport.FakeNetwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
 * "Mot de passe oublié" view model, in virtual time: no phone, no Firebase. Covers criteria M1 (format), M2 (same
 * confirmation whatever the address), M5 (pause of about 60 s), X2 (offline) and QA2 (nothing, not even the duration,
 * tells a known address from an unknown one).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PasswordResetViewModelTest {
    private class FakeGateway(
        private val testTime: () -> Long,
    ) : PasswordResetGateway {
        var outcome: PasswordResetOutcome = PasswordResetOutcome.Accepted

        /** How long the "server" takes to answer. */
        var serverDelayMillis = 0L
        val requests = mutableListOf<String>()
        var answeredAt = -1L

        override suspend fun sendResetEmail(email: String): PasswordResetOutcome {
            requests += email
            delay(serverDelayMillis)
            answeredAt = testTime()
            return outcome
        }
    }

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class Rig(
        val gateway: FakeGateway,
        val network: FakeNetwork,
        val cooldown: ResendCooldown,
        val vm: PasswordResetViewModel,
    )

    /** Lets the screen finish what it started (a few seconds): the 60 s pause is NOT run through. */
    private fun TestScope.settle() {
        advanceTimeBy(5_000)
        runCurrent()
    }

    /** Virtual milliseconds until the confirmation shows (50 ms steps, up to 10 s). */
    private fun TestScope.millisUntilSent(vm: PasswordResetViewModel): Long {
        val start = currentTime
        while (!vm.uiState.value.sent && currentTime - start < 10_000) {
            advanceTimeBy(50)
            runCurrent()
        }
        return currentTime - start
    }

    private fun TestScope.rig(
        cooldown: ResendCooldown = ResendCooldown(nowMillis = { currentTime }),
        network: FakeNetwork = FakeNetwork(),
    ): Rig {
        val gateway = FakeGateway { currentTime }
        val vm = PasswordResetViewModel(gateway, network, cooldown, nowMillis = { currentTime })
        return Rig(gateway, network, cooldown, vm)
    }

    // --- Criterion M1: format, no request -------------------------------------------------------------------------

    @Test
    fun anAddressThatCannotExist_isTurnedBackOnThePhone_withoutAnyRequest_M1() =
        runTest(dispatcher) {
            val r = rig()
            r.vm.submit("not-an-address")
            r.vm.submit("a@b")
            r.vm.submit("")
            settle()
            assertEquals("no request", emptyList<String>(), r.gateway.requests)
            assertTrue(r.vm.uiState.value.showFieldError)
            assertFalse("nothing is confirmed", r.vm.uiState.value.sent)
            assertNull("the format error is the field's, not a message", r.vm.uiState.value.message)
        }

    @Test
    fun spacesAroundAPastedAddress_areRemovedBeforeTheRequest() =
        runTest(dispatcher) {
            val r = rig()
            r.vm.submit("  Toto@Gmail.com \n")
            settle()
            assertEquals(listOf("Toto@Gmail.com"), r.gateway.requests)
        }

    // --- Criterion M2 and QA2: one confirmation, whatever the address ----------------------------------------------

    @Test
    fun afterTheSend_theConfirmationIsShown_andTheButtonPauses_M2_M5() =
        runTest(dispatcher) {
            val r = rig()
            r.vm.submit("a@b.fr")
            settle()
            val state = r.vm.uiState.value
            assertTrue(state.sent)
            assertTrue(state.resendLocked)
            assertFalse(state.isLoading)
            assertNull(state.message)
            assertFalse(state.showFieldError)
        }

    @Test
    fun aKnownAndAnUnknownAddress_leaveTheSameStateAfterTheSameTime_M2_QA2() =
        runTest(dispatcher) {
            // A real account: Firebase sends the e-mail, which takes a while.
            val known = rig()
            known.gateway.serverDelayMillis = 1_500
            known.vm.submit("known@b.fr")
            val knownTotal = millisUntilSent(known.vm)

            // An unknown address: Firebase (protection off) answers "no such user" at once, and nothing is sent.
            val unknownGateway =
                object : PasswordResetGateway {
                    override suspend fun sendResetEmail(email: String): PasswordResetOutcome =
                        attemptPasswordReset {
                            throw FirebaseAuthInvalidUserException("ERROR_USER_NOT_FOUND", "no user record")
                        }
                }
            val unknown =
                PasswordResetViewModel(
                    unknownGateway,
                    FakeNetwork(),
                    ResendCooldown(nowMillis = { currentTime }),
                    nowMillis = { currentTime },
                )
            unknown.submit("unknown@b.fr")
            val unknownTotal = millisUntilSent(unknown)

            assertEquals("same screen, word for word", known.vm.uiState.value, unknown.uiState.value)
            assertTrue(unknown.uiState.value.sent)
            assertTrue(
                "the fast answer is held back to a floor: $unknownTotal ms",
                unknownTotal >= 800, // hard-coded on purpose: a floor lowered by mistake must fail here
            )
            assertTrue(
                "and the slow one (a real send) is not held back more: $knownTotal ms",
                knownTotal < 1_500 + 100,
            )
        }

    // --- Criterion M5: the pause ----------------------------------------------------------------------------------

    @Test
    fun theButtonStaysPausedForAboutAMinute_thenComesBack_M5() =
        runTest(dispatcher) {
            val r = rig()
            r.vm.submit("a@b.fr")
            settle()
            assertTrue(r.vm.uiState.value.resendLocked)

            // A tap during the pause does nothing.
            r.vm.submit("a@b.fr")
            settle()
            assertEquals(1, r.gateway.requests.size)

            // The pause started when the confirmation appeared (after the 0.8 s floor) and lasts 60 s.
            val pauseEnd = PasswordResetViewModel.MIN_ANSWER_MILLIS + ResendCooldown.DEFAULT_DURATION_MILLIS
            advanceTimeBy(pauseEnd - 1_000 - currentTime)
            runCurrent()
            assertTrue("still paused a second before the end", r.vm.uiState.value.resendLocked)
            advanceTimeBy(1_001)
            runCurrent()
            assertFalse("back after a minute", r.vm.uiState.value.resendLocked)
            assertTrue("the confirmation stays", r.vm.uiState.value.sent)

            r.vm.submit("a@b.fr")
            settle()
            assertEquals("a second send is possible", 2, r.gateway.requests.size)
        }

    @Test
    fun fixingATypo_isNotMadeToWait_butTheSameAddressIs() =
        runTest(dispatcher) {
            val r = rig()
            r.vm.onEmailChanged("toto@gmial.com")
            r.vm.submit("toto@gmial.com")
            settle()
            assertTrue(r.vm.uiState.value.sent)

            r.vm.onEmailChanged("toto@gmail.com")
            assertFalse("a confirmation is about one address", r.vm.uiState.value.sent)
            assertFalse("another address is free to go", r.vm.uiState.value.resendLocked)

            r.vm.onEmailChanged("  TOTO@gmial.com ")
            assertTrue("same address, other spelling: paused again", r.vm.uiState.value.resendLocked)
            assertTrue("and its confirmation is back", r.vm.uiState.value.sent)
        }

    @Test
    fun leavingTheScreenAndComingBack_doesNotGiveAFreeResend() =
        runTest(dispatcher) {
            val shared = ResendCooldown(nowMillis = { currentTime })
            val first = rig(cooldown = shared)
            first.vm.submit("a@b.fr")
            settle()

            // A new screen (new view model), same container: the pause is still there for this address.
            val second = rig(cooldown = shared)
            second.vm.onEmailChanged("a@b.fr")
            assertTrue(second.vm.uiState.value.resendLocked)
            second.vm.submit("a@b.fr")
            settle()
            assertTrue("no request", second.gateway.requests.isEmpty())

            advanceTimeBy(ResendCooldown.DEFAULT_DURATION_MILLIS)
            runCurrent()
            assertFalse("and it ends by itself", second.vm.uiState.value.resendLocked)
        }

    // --- Offline, errors, double tap ------------------------------------------------------------------------------

    @Test
    fun offline_saysSo_sendsNothing_andStartsNoPause_X2() =
        runTest(dispatcher) {
            val r = rig(network = FakeNetwork(online = false))
            r.vm.submit("a@b.fr")
            settle()
            assertEquals(AuthMessage.NETWORK, r.vm.uiState.value.message)
            assertTrue(r.gateway.requests.isEmpty())
            assertFalse(r.vm.uiState.value.sent)
            assertFalse(r.vm.uiState.value.resendLocked)

            r.network.online = true
            r.vm.submit("a@b.fr")
            settle()
            assertEquals(1, r.gateway.requests.size)
            assertNull(r.vm.uiState.value.message)
            assertTrue(r.vm.uiState.value.sent)
        }

    @Test
    fun aNetworkFailure_isNeutral_andStartsNoPause() =
        runTest(dispatcher) {
            val r = rig()
            r.gateway.outcome = PasswordResetOutcome.Failed(AuthFailure.NETWORK)
            r.vm.submit("a@b.fr")
            settle()
            assertEquals(AuthMessage.NETWORK, r.vm.uiState.value.message)
            assertFalse(r.vm.uiState.value.sent)
            assertFalse("nothing was sent, trying again soon is fine", r.vm.uiState.value.resendLocked)
        }

    @Test
    fun tooManyRequests_isNeverAdvisedToResetAPassword_onTheResetScreen() =
        runTest(dispatcher) {
            val r = rig()
            r.gateway.outcome = PasswordResetOutcome.Failed(AuthFailure.TOO_MANY_ATTEMPTS)
            r.vm.submit("a@b.fr")
            settle()
            assertEquals(AuthMessage.TOO_MANY_REQUESTS, r.vm.uiState.value.message)
        }

    @Test
    fun anUnknownFailure_isTheNeutralMessage() =
        runTest(dispatcher) {
            val r = rig()
            r.gateway.outcome = PasswordResetOutcome.Failed(AuthFailure.UNKNOWN)
            r.vm.submit("a@b.fr")
            settle()
            assertEquals(AuthMessage.UNKNOWN, r.vm.uiState.value.message)
        }

    @Test
    fun anAddressTheServerFindsMalformed_showsTheFormatMessage_andStartsNoPause() =
        runTest(dispatcher) {
            val r = rig()
            r.gateway.outcome = PasswordResetOutcome.EmailRejected
            r.vm.submit("a@b.fr")
            settle()
            assertTrue(r.vm.uiState.value.emailRejected)
            assertFalse(r.vm.uiState.value.sent)
            assertFalse(r.vm.uiState.value.resendLocked)

            r.vm.onEmailChanged("a@b.com")
            assertFalse("editing the address clears the error", r.vm.uiState.value.emailRejected)
        }

    @Test
    fun aSecondTapWhileSending_doesNotSendTwice() =
        runTest(dispatcher) {
            val r = rig()
            r.gateway.serverDelayMillis = 2_000
            r.vm.submit("a@b.fr")
            advanceTimeBy(500)
            assertTrue(r.vm.uiState.value.isLoading)
            r.vm.submit("a@b.fr")
            settle()
            assertEquals(1, r.gateway.requests.size)
        }

    @Test
    fun editingTheField_clearsAnOldMessage() =
        runTest(dispatcher) {
            val r = rig(network = FakeNetwork(online = false))
            r.vm.submit("a@b.fr")
            settle()
            assertEquals(AuthMessage.NETWORK, r.vm.uiState.value.message)
            r.vm.onEmailChanged("a@b.fr2")
            assertNull(r.vm.uiState.value.message)
        }

    private fun TestScope.pastPause() {
        advanceTimeBy(ResendCooldown.DEFAULT_DURATION_MILLIS + 1_000)
        runCurrent()
    }

    @Test
    fun aResendThatFails_takesTheOldConfirmationAway_neverBothAtOnce() =
        runTest(dispatcher) {
            val r = rig()
            r.vm.submit("a@b.fr")
            settle()
            assertTrue(r.vm.uiState.value.sent)
            pastPause()

            r.gateway.outcome = PasswordResetOutcome.Failed(AuthFailure.UNKNOWN)
            r.vm.submit("a@b.fr")
            settle()
            assertEquals(AuthMessage.UNKNOWN, r.vm.uiState.value.message)
            assertFalse("no \"an e-mail has been sent\" next to an error", r.vm.uiState.value.sent)
        }

    @Test
    fun aResendOffline_takesTheOldConfirmationAway_too() =
        runTest(dispatcher) {
            val r = rig()
            r.vm.submit("a@b.fr")
            settle()
            pastPause()

            r.network.online = false
            r.vm.submit("a@b.fr")
            settle()
            assertEquals(AuthMessage.NETWORK, r.vm.uiState.value.message)
            assertFalse(r.vm.uiState.value.sent)
        }

    @Test
    fun eachAcceptedSend_countsOnce_soTheConfirmationIsAnnouncedAgain() =
        runTest(dispatcher) {
            val r = rig()
            assertEquals(0, r.vm.uiState.value.sendCount)
            r.vm.submit("a@b.fr")
            settle()
            assertEquals(1, r.vm.uiState.value.sendCount)
            pastPause()
            r.vm.submit("a@b.fr")
            settle()
            assertEquals(2, r.vm.uiState.value.sendCount)
            assertTrue(r.vm.uiState.value.sent)
        }

    @Test
    fun theFloorUsesTheInjectedClock_notTheWallClock() =
        runTest(dispatcher) {
            // The view model has no default clock any more: whatever is injected rules the floor.
            var fake = 0L
            val gateway = FakeGateway { fake }
            val vm =
                PasswordResetViewModel(
                    gateway,
                    FakeNetwork(),
                    ResendCooldown(nowMillis = { fake }),
                    nowMillis = { fake },
                )
            vm.submit("a@b.fr")
            runCurrent()
            // The injected clock never moves: the floor delay is never considered elapsed, so it waits the full 800 ms.
            advanceTimeBy(799)
            assertFalse(vm.uiState.value.sent)
            advanceTimeBy(2)
            runCurrent()
            assertTrue(vm.uiState.value.sent)
        }
}
