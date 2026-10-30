package com.tomessa.miff.data

import com.tomessa.miff.data.auth.ProfileWriteResult
import com.tomessa.miff.data.privacy.AnalyticsConsentRepository
import com.tomessa.miff.data.privacy.ConsentWithdrawals
import com.tomessa.miff.data.privacy.PendingWithdrawals
import com.tomessa.miff.testsupport.FakeNetwork
import com.tomessa.miff.testsupport.MemoryWithdrawalStorage
import com.tomessa.miff.testsupport.SessionHarness
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A withdrawal of the statistics consent made without a network (decision of the PM "durcir le consentement"): it survives a
 * restart, keeps the collection off, follows ITS account only, is recorded in the background when the network is back, and
 * never loops when the server says no.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PendingWithdrawalsTest {
    private val dispatcher = StandardTestDispatcher()
    private val scope = CoroutineScope(dispatcher + SupervisorJob())
    private val network = FakeNetwork()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        scope.cancel()
        Dispatchers.resetMain()
    }

    private class FakeConsents : AnalyticsConsentRepository {
        val calls = mutableListOf<Pair<String, Boolean>>()
        var result = ProfileWriteResult.DONE

        override suspend fun set(
            uid: String,
            enable: Boolean,
        ): ProfileWriteResult {
            calls += uid to enable
            return result
        }
    }

    // --- What is kept on the device ----------------------------------------------------------------------------------------------

    @Test
    fun onlyAHashAndATimeAreKept_neverTheIdOfTheAccount() {
        val storage = MemoryWithdrawalStorage()
        val pending = PendingWithdrawals(storage) { 42L }
        pending.mark("uid-secret-123")
        val (key, time) = storage.stored.entries.single()
        assertEquals(42L, time)
        assertEquals(32, key.length)
        assertFalse("uid-secret-123" in key)
        assertTrue(key.all { it in '0'..'9' || it in 'a'..'f' })
        assertEquals(42L, pending.pendingSince("uid-secret-123"))
    }

    @Test
    fun theMarkSurvivesARestart_andBelongsToOneAccountOnly() {
        val storage = MemoryWithdrawalStorage()
        PendingWithdrawals(storage) { 1L }.mark("uid-1")
        val afterRestart = PendingWithdrawals(storage) { 2L }
        assertTrue(afterRestart.isPending("uid-1"))
        assertFalse("never applied to another account", afterRestart.isPending("uid-2"))
        afterRestart.clear("uid-1")
        assertFalse(PendingWithdrawals(storage) { 3L }.isPending("uid-1"))
    }

    @Test
    fun marking_isWrittenBeforeReturning_andTwiceDoesNotChangeTheTime() {
        val storage = MemoryWithdrawalStorage()
        var now = 10L
        val pending = PendingWithdrawals(storage) { now }
        pending.mark("uid-1")
        now = 99L
        pending.mark("uid-1")
        assertEquals(1, storage.writes)
        assertEquals(10L, pending.pendingSince("uid-1"))
    }

    @Test
    fun atMostTwentyAccountsAreRemembered_theOldestIsDropped() {
        val storage = MemoryWithdrawalStorage()
        var now = 0L
        val pending = PendingWithdrawals(storage) { ++now }
        (1..25).forEach { pending.mark("uid-$it") }
        assertEquals(PendingWithdrawals.MAX_ENTRIES, storage.stored.size)
        assertFalse(pending.isPending("uid-1"))
        assertTrue(pending.isPending("uid-25"))
    }

    @Test
    fun unreadableEntriesOnTheDiskAreIgnored() {
        val storage = MemoryWithdrawalStorage(mapOf("abc" to 5L))
        val pending = PendingWithdrawals(storage) { 1L }
        assertFalse(pending.isPending("anything"))
        assertNull(pending.pendingSince("anything"))
    }

    // --- The start of the app --------------------------------------------------------------------------------------------------------

    private fun harness(
        pending: PendingWithdrawals,
        accountSaysYes: Boolean = true,
        resolved: MutableList<String> = mutableListOf(),
    ) = SessionHarness(scope, withdrawals = pending, onAccountResolved = { resolved += it }).also {
        it.gateway.setSignedIn("uid-1")
        it.accounts.setLoaded(profileExists = true, hasMembership = true, analyticsConsent = accountSaysYes)
    }

    @Test
    fun atTheNextStart_theCollectionStaysOff_whateverTheAccountStillSays() =
        runTest(dispatcher) {
            val storage = MemoryWithdrawalStorage()
            PendingWithdrawals(storage) { 1L }.mark("uid-1")
            // A new process: the mark is read from the disk; the account still says "accepted".
            val h = harness(PendingWithdrawals(storage) { 2L })
            h.manager.start()
            advanceUntilIdle()
            assertFalse(h.privacy.analyticsEnabled.value)
            assertFalse(h.privacy.crashReportsEnabled.value)
            assertFalse("no sign-in event for a person who withdrew", h.sink.names.contains("sign_in_completed"))
        }

    @Test
    fun withoutAMark_theAccountChoiceStillRules() =
        runTest(dispatcher) {
            val h = harness(PendingWithdrawals(MemoryWithdrawalStorage()) { 1L })
            h.manager.start()
            advanceUntilIdle()
            assertTrue(h.privacy.analyticsEnabled.value)
        }

    @Test
    fun aMarkOfAnotherAccountIsNeverAppliedToThisOne() =
        runTest(dispatcher) {
            val pending = PendingWithdrawals(MemoryWithdrawalStorage()) { 1L }
            pending.mark("uid-OTHER")
            val h = harness(pending)
            h.manager.start()
            advanceUntilIdle()
            assertTrue("uid-1 did not withdraw anything", h.privacy.analyticsEnabled.value)
            assertTrue("the mark of the other account waits for it", pending.isPending("uid-OTHER"))
        }

    @Test
    fun theAccountIsHandedToTheRecorderOnceItHasBeenRead() =
        runTest(dispatcher) {
            val resolved = mutableListOf<String>()
            val h = harness(PendingWithdrawals(MemoryWithdrawalStorage()) { 1L }, resolved = resolved)
            h.manager.start()
            advanceUntilIdle()
            assertEquals(listOf("uid-1"), resolved)
        }

    // --- The recorder ---------------------------------------------------------------------------------------------------------------

    private class Env(
        val pending: PendingWithdrawals,
        val consents: FakeConsents,
        val withdrawals: ConsentWithdrawals,
    )

    private var signedInAs: String? = "uid-1"

    private fun env(storage: MemoryWithdrawalStorage = MemoryWithdrawalStorage()): Env {
        val pending = PendingWithdrawals(storage) { 1L }
        val consents = FakeConsents()
        return Env(
            pending,
            consents,
            ConsentWithdrawals(
                pending,
                consents,
                network,
                { signedInAs },
                scope,
                retryDelaysMillis = listOf(1_000L, 2_000L),
            ),
        )
    }

    @Test
    fun aRestartedAppRecordsTheWaitingWithdrawalByItself_whenTheNetworkIsBack() =
        runTest(dispatcher) {
            val storage = MemoryWithdrawalStorage()
            PendingWithdrawals(storage) { 1L }.mark("uid-1")
            val e = env(storage)
            network.online = false
            e.withdrawals.resume("uid-1")
            runCurrent()
            assertTrue(e.consents.calls.isEmpty())
            network.online = true
            runCurrent()
            assertEquals(listOf("uid-1" to false), e.consents.calls)
            assertFalse(e.pending.isPending("uid-1"))
        }

    @Test
    fun withTheNetworkPresentButTheServerUnreachable_itTriesAgainAfterGrowingPauses_thenSucceeds() =
        runTest(dispatcher) {
            val e = env()
            e.consents.result = ProfileWriteResult.OFFLINE
            e.withdrawals.withdraw("uid-1")
            runCurrent()
            assertEquals(1, e.consents.calls.size)
            advanceTimeBy(1_100L)
            runCurrent()
            assertEquals(2, e.consents.calls.size)
            e.consents.result = ProfileWriteResult.DONE
            advanceTimeBy(2_100L)
            runCurrent()
            assertEquals(3, e.consents.calls.size)
            assertFalse(e.pending.isPending("uid-1"))
            advanceTimeBy(60_000L)
            runCurrent()
            assertEquals("nothing more once it is recorded", 3, e.consents.calls.size)
        }

    @Test
    fun aRefusalStopsEverything_noLoop_thePendingMarkStays_nextStartTriesOnce() =
        runTest(dispatcher) {
            val e = env()
            e.consents.result = ProfileWriteResult.REFUSED
            e.withdrawals.withdraw("uid-1")
            advanceTimeBy(10 * 60_000L)
            runCurrent()
            assertEquals(1, e.consents.calls.size)
            assertTrue(e.pending.isPending("uid-1"))
            e.withdrawals.resume("uid-1")
            advanceTimeBy(60_000L)
            runCurrent()
            assertEquals("no automatic try in the same run", 1, e.consents.calls.size)
            // The end of the session, then a new start (the memory of the refusal is gone).
            e.withdrawals.stop()
            e.consents.result = ProfileWriteResult.DONE
            e.withdrawals.resume("uid-1")
            runCurrent()
            assertEquals(2, e.consents.calls.size)
            assertFalse(e.pending.isPending("uid-1"))
        }

    @Test
    fun afterASignOutTheWithdrawalIsNeverWrittenInTheNameOfAnotherAccount() =
        runTest(dispatcher) {
            val e = env()
            network.online = false
            e.withdrawals.withdraw("uid-1")
            runCurrent()
            // The person signs out; somebody else signs in on the same phone.
            e.withdrawals.stop()
            signedInAs = "uid-2"
            network.online = true
            runCurrent()
            advanceTimeBy(60_000L)
            runCurrent()
            assertTrue("nothing was written for anybody", e.consents.calls.isEmpty())
            assertTrue("the withdrawal still waits for uid-1", e.pending.isPending("uid-1"))
            assertFalse(e.pending.isPending("uid-2"))
            e.withdrawals.resume("uid-2")
            runCurrent()
            assertTrue("uid-2 has nothing waiting", e.consents.calls.isEmpty())
            // uid-1 signs in again: now it is recorded, for uid-1.
            signedInAs = "uid-1"
            e.withdrawals.resume("uid-1")
            runCurrent()
            assertEquals(listOf("uid-1" to false), e.consents.calls)
            assertFalse(e.pending.isPending("uid-1"))
        }

    @Test
    fun acceptingAgainCancelsTheMark_onlyAfterTheServerTookTheAcceptance() =
        runTest(dispatcher) {
            val e = env()
            e.pending.mark("uid-1")
            e.consents.result = ProfileWriteResult.OFFLINE
            assertEquals(ProfileWriteResult.OFFLINE, e.withdrawals.accept("uid-1"))
            assertTrue("a failed acceptance cancels nothing", e.pending.isPending("uid-1"))
            e.consents.result = ProfileWriteResult.DONE
            assertEquals(ProfileWriteResult.DONE, e.withdrawals.accept("uid-1"))
            assertFalse(e.pending.isPending("uid-1"))
            assertNotNull(e.consents.calls.last())
            assertEquals("uid-1" to true, e.consents.calls.last())
        }

    @Test
    fun theRecorderDoesNothingWhenNothingIsMarked() =
        runTest(dispatcher) {
            val e = env()
            e.withdrawals.resume("uid-1")
            runCurrent()
            assertEquals(ProfileWriteResult.DONE, e.withdrawals.retryNow("uid-1"))
            assertTrue(e.consents.calls.isEmpty())
        }
}
