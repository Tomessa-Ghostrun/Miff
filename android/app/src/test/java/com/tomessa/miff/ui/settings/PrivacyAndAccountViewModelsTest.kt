package com.tomessa.miff.ui.settings

import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.tomessa.miff.data.auth.PasswordResetGateway
import com.tomessa.miff.data.auth.ProfileWriteResult
import com.tomessa.miff.data.privacy.AnalyticsConsentRepository
import com.tomessa.miff.data.privacy.ConsentTransactions
import com.tomessa.miff.data.privacy.ConsentWithdrawals
import com.tomessa.miff.data.privacy.FirestoreAnalyticsConsentRepository
import com.tomessa.miff.data.privacy.PendingWithdrawals
import com.tomessa.miff.data.privacy.PrivacySettings
import com.tomessa.miff.domain.auth.AccountSignIn
import com.tomessa.miff.domain.auth.AuthFailure
import com.tomessa.miff.domain.auth.PasswordResetOutcome
import com.tomessa.miff.domain.auth.ResendCooldown
import com.tomessa.miff.domain.privacy.AnalyticsConsentTransition
import com.tomessa.miff.testsupport.FakeNetwork
import com.tomessa.miff.testsupport.MemoryWithdrawalStorage
import com.tomessa.miff.testsupport.RecordingSwitch
import com.tomessa.miff.testsupport.SessionHarness
import com.tomessa.miff.testsupport.firestoreError
import com.tomessa.miff.ui.auth.AuthMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
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

/** Settings > Confidentialité (switch) and Mon compte (password, sign-out): ticket ONB-3b, criteria B1 to B4, RG6. */
@OptIn(ExperimentalCoroutinesApi::class)
class PrivacyAndAccountViewModelsTest {
    private val dispatcher = StandardTestDispatcher()
    private val sessionScope = CoroutineScope(dispatcher + SupervisorJob())
    private val network = FakeNetwork()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        sessionScope.cancel()
        Dispatchers.resetMain()
    }

    // --- The transitions allowed by the rules ----------------------------------------------------------------------------------

    private fun active(version: String = "2026-10-v01") =
        mapOf<String, Any?>("enabled" to true, "consentedAt" to "T1", "consentVersion" to version, "revokedAt" to null)

    private fun withdrawn() =
        mapOf<String, Any?>(
            "enabled" to false,
            "consentedAt" to "T1",
            "consentVersion" to "2026-10-v01",
            "revokedAt" to "T2",
        )

    @Test
    fun activating_fromAbsent_refused_orWithdrawn_writesTheFourKeys_withTheCurrentVersion() {
        val refused =
            mapOf<String, Any?>(
                "enabled" to false,
                "consentedAt" to null,
                "consentVersion" to null,
                "revokedAt" to null,
            )
        listOf(null, refused, withdrawn()).forEach { previous ->
            val change =
                AnalyticsConsentTransition.change(
                    previous,
                    enable = true,
                ) as AnalyticsConsentTransition.Change.Write
            assertEquals(setOf("enabled", "consentedAt", "consentVersion", "revokedAt"), change.block.keys)
            assertEquals(true, change.block["enabled"])
            assertEquals("2026-10-v01", change.block["consentVersion"])
            assertNull("the withdrawal date is cleared", change.block["revokedAt"])
        }
    }

    @Test
    fun withdrawing_keepsTheTraceOfTheConsent_andDatesTheWithdrawal() {
        val change =
            AnalyticsConsentTransition.change(
                active(),
                enable = false,
            ) as AnalyticsConsentTransition.Change.Write
        assertEquals(false, change.block["enabled"])
        assertEquals("T1", change.block["consentedAt"])
        assertEquals("2026-10-v01", change.block["consentVersion"])
        assertTrue("the date of the withdrawal is the server time", change.block["revokedAt"] != null)
        assertEquals(4, change.block.size)
    }

    @Test
    fun nothingIsWrittenWhenTheAccountAlreadyIsInTheWantedState() {
        assertEquals(AnalyticsConsentTransition.Change.None, AnalyticsConsentTransition.change(active(), enable = true))
        assertEquals(
            AnalyticsConsentTransition.Change.None,
            AnalyticsConsentTransition.change(withdrawn(), enable = false),
        )
        assertEquals(AnalyticsConsentTransition.Change.None, AnalyticsConsentTransition.change(null, enable = false))
    }

    @Test
    fun anOlderTextThanTheOneAlreadyAcceptedIsNeverWritten() {
        val newer = withdrawn() + ("consentVersion" to "2027-01-v02")
        assertEquals(AnalyticsConsentTransition.Change.Refused, AnalyticsConsentTransition.change(newer, enable = true))
        assertTrue(
            AnalyticsConsentTransition.change(
                newer,
                enable = true,
                version = "2027-01-v02",
            ) is AnalyticsConsentTransition.Change.Write,
        )
    }

    // --- The Firestore side: one transaction, no value in errors ----------------------------------------------------------------

    private class FakeTransactions(
        var previous: Map<String, Any?>?,
    ) : ConsentTransactions {
        var written: Map<String, Any?>? = null
        var failure: Exception? = null
        var runs = 0

        override suspend fun run(
            uid: String,
            decide: (previous: Map<String, Any?>?) -> Map<String, Any?>?,
        ) {
            runs++
            failure?.let { throw it }
            written = decide(previous)
        }
    }

    @Test
    fun theRepositoryWritesWhatTheTransitionSays_inOneTransaction() =
        runTest(dispatcher) {
            val tx = FakeTransactions(active())
            val repo = FirestoreAnalyticsConsentRepository(tx, timeoutMillis = 1_000)
            assertEquals(ProfileWriteResult.DONE, repo.set("uid-1", enable = false))
            assertEquals(false, tx.written?.get("enabled"))
            assertEquals(1, tx.runs)
        }

    @Test
    fun noWriteWhenNothingChanges_andErrorsAreSorted() =
        runTest(dispatcher) {
            val tx = FakeTransactions(active())
            val repo = FirestoreAnalyticsConsentRepository(tx, timeoutMillis = 1_000)
            assertEquals(ProfileWriteResult.DONE, repo.set("uid-1", enable = true))
            assertNull(tx.written)
            tx.failure = firestoreError(Code.UNAVAILABLE)
            assertEquals(ProfileWriteResult.OFFLINE, repo.set("uid-1", enable = false))
            tx.failure = firestoreError(Code.PERMISSION_DENIED)
            assertEquals(ProfileWriteResult.REFUSED, repo.set("uid-1", enable = false))
            tx.failure = IllegalStateException("x")
            assertEquals(ProfileWriteResult.FAILED, repo.set("uid-1", enable = false))
            tx.failure = null
            tx.previous = withdrawn() + ("consentVersion" to "2099-01-v01")
            assertEquals(
                "a newer accepted text is a refusal, never an older write",
                ProfileWriteResult.REFUSED,
                repo.set("uid-1", enable = true),
            )
        }

    // --- The switch ------------------------------------------------------------------------------------------------------------

    private class FakeConsents : AnalyticsConsentRepository {
        val calls = mutableListOf<Boolean>()
        var results = ArrayDeque<ProfileWriteResult>()

        override suspend fun set(
            uid: String,
            enable: Boolean,
        ): ProfileWriteResult {
            calls += enable
            return if (results.size > 1) results.removeFirst() else results.firstOrNull() ?: ProfileWriteResult.DONE
        }
    }

    private class PrivacyEnv(
        val harness: SessionHarness,
        val analytics: RecordingSwitch,
        val crash: RecordingSwitch,
        val consents: FakeConsents,
        val privacy: PrivacySettings,
        val pending: PendingWithdrawals,
        val withdrawals: ConsentWithdrawals,
    )

    private fun TestScope.privacyEnv(startEnabled: Boolean): PrivacyEnv {
        val harness =
            SessionHarness(sessionScope).also {
                it.gateway.setSignedIn("uid-1")
                it.accounts.setLoaded(profileExists = true, hasMembership = true, analyticsConsent = startEnabled)
                it.manager.start()
                advanceUntilIdle()
            }
        val consents = FakeConsents()
        val pending = PendingWithdrawals(MemoryWithdrawalStorage()) { 1_000L }
        val withdrawals =
            ConsentWithdrawals(
                pending = pending,
                consents = consents,
                network = network,
                currentUid = { harness.gateway.currentUid },
                scope = sessionScope,
                retryDelaysMillis = listOf(1_000L),
            )
        return PrivacyEnv(
            harness,
            harness.analyticsSwitch,
            harness.crashSwitch,
            consents,
            harness.privacy,
            pending,
            withdrawals,
        )
    }

    private fun PrivacyEnv.vm() = PrivacyViewModel(privacy, withdrawals, harness.manager, network)

    @Test
    fun theSwitchShowsWhatIsCollected() =
        runTest(dispatcher) {
            val on = privacyEnv(startEnabled = true)
            assertTrue(
                on
                    .vm()
                    .uiState.value.analyticsEnabled,
            )
            val off = privacyEnv(startEnabled = false)
            assertFalse(
                off
                    .vm()
                    .uiState.value.analyticsEnabled,
            )
        }

    @Test
    fun accepting_isRecordedFirst_thenTheCollectionStarts() =
        runTest(dispatcher) {
            val e = privacyEnv(startEnabled = false)
            val vm = e.vm()
            vm.onAnalyticsToggled(true)
            assertFalse("not collected before the account holds the consent", e.privacy.analyticsEnabled.value)
            advanceUntilIdle()
            assertEquals(listOf(true), e.consents.calls)
            assertTrue(e.privacy.analyticsEnabled.value)
            assertTrue(e.privacy.crashReportsEnabled.value)
            assertTrue(vm.uiState.value.analyticsEnabled)
            assertFalse(vm.uiState.value.isWriting)
        }

    @Test
    fun acceptingWithoutNetwork_changesNothing_andSaysSo() =
        runTest(dispatcher) {
            val e = privacyEnv(startEnabled = false)
            val vm = e.vm()
            network.online = false
            vm.onAnalyticsToggled(true)
            advanceUntilIdle()
            assertTrue(e.consents.calls.isEmpty())
            assertFalse(e.privacy.analyticsEnabled.value)
            assertEquals(PrivacyMessage.NETWORK, vm.uiState.value.message)
        }

    @Test
    fun aRefusedAcceptanceNeverStartsTheCollection() =
        runTest(dispatcher) {
            val e = privacyEnv(startEnabled = false)
            val vm = e.vm()
            e.consents.results = ArrayDeque(listOf(ProfileWriteResult.REFUSED))
            vm.onAnalyticsToggled(true)
            advanceUntilIdle()
            assertFalse(e.privacy.analyticsEnabled.value)
            assertEquals(PrivacyMessage.SAVE_FAILED, vm.uiState.value.message)
        }

    @Test
    fun withdrawing_cutsTheCollectionAtOnce_deletesWhatWasNotSent_thenRecordsIt() =
        runTest(dispatcher) {
            val e = privacyEnv(startEnabled = true)
            val vm = e.vm()
            e.crash.calls.clear()
            vm.onAnalyticsToggled(false)
            // Before the server has answered: already off, on both SDKs (false = also deletes the reports not yet sent).
            assertFalse(e.privacy.analyticsEnabled.value)
            assertFalse(e.privacy.crashReportsEnabled.value)
            assertEquals(false, e.crash.calls.last())
            advanceUntilIdle()
            assertEquals(listOf(false), e.consents.calls)
            assertFalse(vm.uiState.value.withdrawalPending)
            assertFalse("nothing stays marked once the account knows", e.pending.isPending("uid-1"))
            assertNull(vm.uiState.value.message)
        }

    @Test
    fun withdrawingWithoutNetwork_cutsTheCollection_marksTheChoiceOnTheDevice_andShowsIt() =
        runTest(dispatcher) {
            val e = privacyEnv(startEnabled = true)
            val vm = e.vm()
            network.online = false
            vm.onAnalyticsToggled(false)
            runCurrent()
            assertFalse("the person asked for nothing to be collected", e.privacy.analyticsEnabled.value)
            assertTrue("no doomed request without a network", e.consents.calls.isEmpty())
            assertTrue("kept on the device, for the next start too", e.pending.isPending("uid-1"))
            assertTrue(vm.uiState.value.withdrawalPending)
        }

    @Test
    fun theWithdrawalIsRecordedByItselfAsSoonAsTheNetworkIsBack_withoutAnyTap() =
        runTest(dispatcher) {
            val e = privacyEnv(startEnabled = true)
            val vm = e.vm()
            network.online = false
            vm.onAnalyticsToggled(false)
            runCurrent()
            assertTrue(vm.uiState.value.withdrawalPending)
            network.online = true
            advanceTimeBy(1_100L)
            runCurrent()
            assertEquals(listOf(false), e.consents.calls)
            assertFalse(e.pending.isPending("uid-1"))
            assertFalse(vm.uiState.value.withdrawalPending)
            assertFalse("still off", e.privacy.analyticsEnabled.value)
        }

    @Test
    fun theButtonTriesRightNow_andSaysNoConnectionIfTheNetworkIsStillMissing() =
        runTest(dispatcher) {
            val e = privacyEnv(startEnabled = true)
            val vm = e.vm()
            network.online = false
            vm.onAnalyticsToggled(false)
            runCurrent()
            vm.onRetryWithdrawal()
            runCurrent()
            assertEquals(PrivacyMessage.NETWORK, vm.uiState.value.message)
            assertTrue(vm.uiState.value.withdrawalPending)
            // The network comes back: the background task finishes the job by itself.
            network.online = true
            advanceTimeBy(1_100L)
            runCurrent()
            assertFalse(e.pending.isPending("uid-1"))
            assertEquals(listOf(false), e.consents.calls)
        }

    @Test
    fun aServerThatSaysNo_stopsTheAutomaticTries_collectionStaysOff_theButtonCanTryAgain() =
        runTest(dispatcher) {
            val e = privacyEnv(startEnabled = true)
            val vm = e.vm()
            e.consents.results =
                ArrayDeque(listOf(ProfileWriteResult.OFFLINE, ProfileWriteResult.REFUSED, ProfileWriteResult.DONE))
            vm.onAnalyticsToggled(false)
            runCurrent()
            // First answer OFFLINE: the background task tries again, gets REFUSED and gives up (no loop).
            advanceTimeBy(600_000L)
            runCurrent()
            assertEquals("no loop after a refusal", listOf(false, false), e.consents.calls)
            assertTrue(e.pending.isPending("uid-1"))
            assertFalse(e.privacy.analyticsEnabled.value)
            vm.onRetryWithdrawal()
            runCurrent()
            assertEquals(listOf(false, false, false), e.consents.calls)
            assertFalse(e.pending.isPending("uid-1"))
        }

    @Test
    fun acceptingAgainBeforeTheWithdrawalWasRecorded_replacesIt_afterTheServerTookIt() =
        runTest(dispatcher) {
            val e = privacyEnv(startEnabled = true)
            val vm = e.vm()
            network.online = false
            vm.onAnalyticsToggled(false)
            runCurrent()
            assertTrue(vm.uiState.value.withdrawalPending)

            network.online = true
            vm.onAnalyticsToggled(true)
            runCurrent()
            // The account still holds "accepted": the acceptance is written (nothing changes there) and the mark goes away.
            // Whatever the order of the two writes, the LAST one is the acceptance, and it is what the account ends with.
            assertEquals(true, e.consents.calls.last())
            assertFalse(e.pending.isPending("uid-1"))
            assertTrue(e.privacy.analyticsEnabled.value)
            assertFalse(vm.uiState.value.withdrawalPending)
            advanceTimeBy(600_000L)
            runCurrent()
            assertEquals("nothing is written after the acceptance", true, e.consents.calls.last())
        }

    @Test
    fun acceptingAgainWithoutNetworkWhileAWithdrawalWaits_changesNothing() =
        runTest(dispatcher) {
            val e = privacyEnv(startEnabled = true)
            val vm = e.vm()
            network.online = false
            vm.onAnalyticsToggled(false)
            runCurrent()
            vm.onAnalyticsToggled(true)
            runCurrent()
            assertFalse(e.privacy.analyticsEnabled.value)
            assertTrue(e.pending.isPending("uid-1"))
            assertEquals(PrivacyMessage.NETWORK, vm.uiState.value.message)
        }

    @Test
    fun aTapThatChangesNothingDoesNothing_andNoAnalyticsEventIsSent() =
        runTest(dispatcher) {
            val e = privacyEnv(startEnabled = true)
            val vm = e.vm()
            val eventsBefore = e.harness.sink.events.size
            vm.onAnalyticsToggled(true)
            advanceUntilIdle()
            assertTrue(e.consents.calls.isEmpty())
            vm.onAnalyticsToggled(false)
            advanceUntilIdle()
            assertEquals("this screen sends no event", eventsBefore, e.harness.sink.events.size)
        }

    @Test
    fun doubleTapWritesOnce() =
        runTest(dispatcher) {
            val e = privacyEnv(startEnabled = false)
            val vm = e.vm()
            vm.onAnalyticsToggled(true)
            vm.onAnalyticsToggled(true)
            advanceUntilIdle()
            assertEquals(1, e.consents.calls.size)
        }

    // --- Account: password (decision Q5) -----------------------------------------------------------------------------------------

    private class FakeResetGateway : PasswordResetGateway {
        var outcome: PasswordResetOutcome = PasswordResetOutcome.Accepted
        val requests = mutableListOf<String>()

        override suspend fun sendResetEmail(email: String): PasswordResetOutcome {
            requests += email
            return outcome
        }
    }

    private var clock = 0L

    private fun accountVm(
        signIn: AccountSignIn = AccountSignIn.PASSWORD,
        email: String? = "lea@example.org",
        gateway: FakeResetGateway = FakeResetGateway(),
        signOut: suspend () -> Unit = {},
    ) = AccountViewModel(
        email = email,
        signIn = signIn,
        signOut = signOut,
        passwordReset = gateway,
        network = network,
        cooldown = ResendCooldown(nowMillis = { clock }),
        nowMillis = { clock },
    )

    @Test
    fun anEmailAccountCanAskForTheResetEmail_aGoogleAccountCannot() =
        runTest(dispatcher) {
            assertTrue(accountVm().uiState.value.canChangePassword)
            assertFalse(accountVm(signIn = AccountSignIn.GOOGLE).uiState.value.canChangePassword)
            assertFalse(accountVm(signIn = AccountSignIn.OTHER).uiState.value.canChangePassword)
            assertFalse(
                "without an address there is nothing to send to",
                accountVm(email = null).uiState.value.canChangePassword,
            )
        }

    @Test
    fun aGoogleAccountNeverSendsAnything() =
        runTest(dispatcher) {
            val gateway = FakeResetGateway()
            val vm = accountVm(signIn = AccountSignIn.GOOGLE, gateway = gateway)
            vm.onChangePasswordRequested()
            advanceUntilIdle()
            assertTrue(gateway.requests.isEmpty())
        }

    @Test
    fun theResetEmailGoesToTheAddressOfTheAccount_thenOneCalmConfirmation_andAPause() =
        runTest(dispatcher) {
            val gateway = FakeResetGateway()
            val vm = accountVm(gateway = gateway)
            vm.onChangePasswordRequested()
            assertEquals(PasswordEmailStatus.SENDING, vm.uiState.value.passwordStatus)
            runCurrent()
            assertEquals(listOf("lea@example.org"), gateway.requests)
            var s = vm.uiState.value
            assertEquals(PasswordEmailStatus.SENT, s.passwordStatus)
            assertTrue("the button is paused for a while", s.passwordLocked)
            assertNull(s.passwordMessage)

            vm.onChangePasswordRequested()
            runCurrent()
            assertEquals("no second send during the pause", 1, gateway.requests.size)

            clock = 61_000L
            advanceTimeBy(61_000L)
            runCurrent()
            s = vm.uiState.value
            assertFalse(s.passwordLocked)
            vm.onChangePasswordRequested()
            runCurrent()
            assertEquals(2, gateway.requests.size)
        }

    @Test
    fun withoutNetwork_aCalmMessage_andNothingIsSent() =
        runTest(dispatcher) {
            val gateway = FakeResetGateway()
            val vm = accountVm(gateway = gateway)
            network.online = false
            vm.onChangePasswordRequested()
            advanceUntilIdle()
            assertTrue(gateway.requests.isEmpty())
            assertEquals(AuthMessage.NETWORK, vm.uiState.value.passwordMessage)
            assertEquals(PasswordEmailStatus.IDLE, vm.uiState.value.passwordStatus)
        }

    @Test
    fun aFailure_isSaidKindly_withoutPauseAndWithoutAdviceToResetAPassword() =
        runTest(dispatcher) {
            val gateway = FakeResetGateway()
            val vm = accountVm(gateway = gateway)
            gateway.outcome = PasswordResetOutcome.Failed(AuthFailure.TOO_MANY_ATTEMPTS)
            vm.onChangePasswordRequested()
            advanceUntilIdle()
            val s = vm.uiState.value
            assertEquals(AuthMessage.TOO_MANY_REQUESTS, s.passwordMessage)
            assertFalse("nothing was sent: no pause", s.passwordLocked)
            assertEquals(PasswordEmailStatus.IDLE, s.passwordStatus)
            gateway.outcome = PasswordResetOutcome.Accepted
            vm.onChangePasswordRequested()
            advanceUntilIdle()
            assertEquals(PasswordEmailStatus.SENT, vm.uiState.value.passwordStatus)
            assertNull("the old error is gone", vm.uiState.value.passwordMessage)
        }

    @Test
    fun theAddressRejectedByTheServerIsAnUnknownProblem_notAnAccountStatement() =
        runTest(dispatcher) {
            val gateway = FakeResetGateway()
            gateway.outcome = PasswordResetOutcome.EmailRejected
            val vm = accountVm(gateway = gateway)
            vm.onChangePasswordRequested()
            advanceUntilIdle()
            assertEquals(AuthMessage.UNKNOWN, vm.uiState.value.passwordMessage)
        }

    @Test
    fun signOutStillAsksFirst_inTheAccountWithAPassword() =
        runTest(dispatcher) {
            var signedOut = 0
            val vm = accountVm(signOut = { signedOut++ })
            vm.onSignOutRequested()
            vm.onSignOutConfirmed()
            advanceUntilIdle()
            assertEquals(1, signedOut)
            vm.onChangePasswordRequested()
            assertEquals("no send while signing out", PasswordEmailStatus.IDLE, vm.uiState.value.passwordStatus)
        }
}
