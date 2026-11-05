package com.tomessa.miff.data.account

import com.tomessa.miff.domain.auth.AuthFailure
import com.tomessa.miff.domain.auth.SessionCheck
import com.tomessa.miff.testsupport.FakeAccountDataStore
import com.tomessa.miff.testsupport.FakeAuthGateway
import com.tomessa.miff.testsupport.FakeCachePurger
import com.tomessa.miff.testsupport.Timeline
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The procedure of the deletion of an account WITHOUT Miff (ticket AUTH-5, sections 5.5, 5.6 and 6): order, idempotence, replay after an
 * interruption at EVERY step, a session that is gone = success, nothing deleted while a Miff is there (criteria SC9, SC22, SC23, SC25,
 * SC26, SC28, SA5).
 */
class AccountDeletionTest {
    private val timeline = Timeline()
    private val store = FakeAccountDataStore(timeline)
    private val gateway = FakeAuthGateway(timeline).also { it.setSignedIn("uid-1") }
    private val cache = FakeCachePurger(timeline)
    private val deletion = AccountDeletion(store, gateway, cache, timeoutMillis = 1_000L)

    @Test
    fun theOrderIsInventory_devices_finalCheck_profile_thenTheQueueLeaves_andTheAccountGoesLast() =
        runTest {
            store.devices += listOf("d1", "d2")
            assertEquals(DeletionRun.Completed, deletion.run("uid-1"))
            assertEquals(
                listOf(
                    "memberships",
                    "devices.read",
                    "device.delete",
                    "device.delete",
                    "memberships",
                    "profile.delete",
                    "markPending",
                    "flush",
                    "delete",
                ),
                timeline.entries,
            )
        }

    @Test
    fun theProfileIsTheLastDocumentDeleted_andTheAuthenticationAccountComesAfterIt() =
        runTest {
            store.devices += "d1"
            deletion.run("uid-1")
            val order = timeline.entries
            assertTrue(order.lastIndexOf("device.delete") < order.indexOf("profile.delete"))
            assertTrue(order.indexOf("profile.delete") < order.indexOf("delete"))
            assertEquals("delete", order.last())
        }

    @Test
    fun theCachePurgeIsRecordedAndTheQueuedWritesLeave_beforeTheAccountIsDeleted() =
        runTest {
            deletion.run("uid-1")
            val order = timeline.entries
            assertTrue(order.indexOf("markPending") < order.indexOf("delete"))
            assertTrue(order.indexOf("flush") < order.indexOf("delete"))
        }

    // --- A Miff is there: nothing is touched (this version cannot delete a Miff) ---------------------------

    @Test
    fun aMiffIsThere_nothingIsDeleted_andTheAccountIsNeverDeleted() =
        runTest {
            store.memberships = 1
            store.devices += "d1"
            assertEquals(DeletionRun.MiffsRemain, deletion.run("uid-1"))
            assertEquals(listOf("memberships"), timeline.entries)
            assertTrue(store.profileExists)
            assertEquals(0, gateway.deleteCalls)
        }

    @Test
    fun aMiffThatAppearsBeforeTheProfile_isSeenByTheFinalCheck_theProfileStays() =
        runTest {
            var counted = 0
            store.onCall = { name ->
                if (name == "memberships" && ++counted == 2) store.memberships = 1
            }
            assertEquals(DeletionRun.MiffsRemain, deletion.run("uid-1"))
            assertTrue(store.profileExists)
            assertEquals(0, gateway.deleteCalls)
        }

    @Test
    fun aMalformedIndexCountsToo_nothingMayBeLeftBehindAProfile() =
        runTest {
            // The store counts every document of `memberships`, whatever its shape: one is enough to stop.
            store.memberships = 1
            assertEquals(DeletionRun.MiffsRemain, deletion.run("uid-1"))
        }

    // --- No network ----------------------------------------------------------------------------------------

    @Test
    fun offline_atAnyStep_pausesAndNothingIsCountedAsDone_thenTheReplayCarriesOn() =
        runTest {
            store.devices += "d1"
            // The deletion of the device does not confirm (no network): nothing is applied.
            store.failures += listOf(null, null, FakeAccountDataStore.offline())
            assertEquals(DeletionRun.Offline, deletion.run("uid-1"))
            assertEquals(listOf("d1"), store.devices)
            assertTrue(store.profileExists)
            assertEquals(0, gateway.deleteCalls)
            // The network is back: the same procedure goes through to the end.
            assertEquals(DeletionRun.Completed, deletion.run("uid-1"))
            assertTrue(store.devices.isEmpty())
            assertFalse(store.profileExists)
            assertEquals(1, gateway.deleteCalls)
        }

    @Test
    fun noNetworkWhenTheAccountIsDeleted_isAPause_notAFailure() =
        runTest {
            gateway.deleteResults += AuthFailure.NETWORK
            assertEquals(DeletionRun.Offline, deletion.run("uid-1"))
        }

    // --- Replay after an interruption, at EVERY step (criterion SC26) -----------------------------------------

    @Test
    fun interruptedAtEveryStep_theReplayAlwaysEndsWithTheAccountDeleted_andNothingTwice() {
        // The number of calls of a full run with one device: 1 memberships, 2 devices.read, 3 device.delete, 4 memberships, 5 profile.delete.
        for (killedAt in 1..5) {
            val t = Timeline()
            val s = FakeAccountDataStore(t).also { it.devices += "d1" }
            val g = FakeAuthGateway(t).also { it.setSignedIn("uid-1") }
            val d = AccountDeletion(s, g, FakeCachePurger(t), timeoutMillis = 1_000L)
            s.failures.addAll(List(killedAt - 1) { null } + FakeAccountDataStore.offline())
            runTest {
                assertEquals("step $killedAt", DeletionRun.Offline, d.run("uid-1"))
                assertEquals("step $killedAt", DeletionRun.Completed, d.run("uid-1"))
            }
            assertFalse("step $killedAt", s.profileExists)
            assertTrue("step $killedAt", s.devices.isEmpty())
            assertEquals("step $killedAt: the account is deleted once", 1, g.deleteCalls)
        }
    }

    @Test
    fun interruptedRightAfterTheProfile_beforeTheAccount_theReplayFinishesTheAccountOnly() =
        runTest {
            gateway.deleteResults += AuthFailure.NETWORK
            assertEquals(DeletionRun.Offline, deletion.run("uid-1"))
            assertFalse(store.profileExists)
            // The profile is gone: the replay deletes it again (no error) and then the account.
            assertEquals(DeletionRun.Completed, deletion.run("uid-1"))
            assertEquals(2, gateway.deleteCalls)
        }

    // --- Firebase wants a recent sign-in (the "Dernière vérification") ----------------------------------------

    @Test
    fun aSignInThatIsTooOld_stopsAtTheLastStep_andTheSecondTryAfterTheProofGoesThrough() =
        runTest {
            gateway.deleteResults += AuthFailure.RECENT_LOGIN_REQUIRED
            assertEquals(DeletionRun.NeedsRecentLogin, deletion.run("uid-1"))
            assertFalse(store.profileExists)
            // The person proved who they are again: the same procedure, which only has the account left to delete.
            assertEquals(DeletionRun.Completed, deletion.run("uid-1"))
            assertEquals(2, gateway.deleteCalls)
        }

    // --- A session that is gone = success (criterion SC28) ---------------------------------------------------

    @Test
    fun noSessionAtAll_isASuccess_andNothingIsWritten() =
        runTest {
            gateway.setSignedIn(null)
            assertEquals(DeletionRun.Completed, deletion.run("uid-1"))
            assertTrue(timeline.entries.isEmpty())
        }

    @Test
    fun theAccountIsDeletedAlready_whenTheLastCallSaysSo_isASuccess() =
        runTest {
            gateway.deleteResults += AuthFailure.ACCOUNT_GONE
            assertEquals(DeletionRun.Completed, deletion.run("uid-1"))
        }

    @Test
    fun aStepRefusedBecauseTheAccountWasDeletedElsewhere_isASuccess() =
        runTest {
            store.failures += FakeAccountDataStore.refused()
            gateway.sessionCheck = SessionCheck.GONE
            assertEquals(DeletionRun.Completed, deletion.run("uid-1"))
            assertEquals(1, gateway.sessionChecks)
        }

    @Test
    fun aStepRefusedWhileTheAccountIsValid_isAFailureToTryAgain_notASuccess() =
        runTest {
            store.failures += FakeAccountDataStore.refused()
            gateway.sessionCheck = SessionCheck.VALID
            assertEquals(DeletionRun.Failed, deletion.run("uid-1"))
            assertEquals(0, gateway.deleteCalls)
        }

    @Test
    fun aRefusalWhenNothingCanBeConcluded_isAFailure_neverASuccess() =
        runTest {
            store.failures += FakeAccountDataStore.refused()
            gateway.sessionCheck = SessionCheck.UNKNOWN
            assertEquals(DeletionRun.Failed, deletion.run("uid-1"))
        }

    @Test
    fun theSessionEndsInTheMiddle_theNextRefusalIsASuccess() =
        runTest {
            store.devices += "d1"
            store.onCall = { name ->
                if (name == "device.delete") {
                    gateway.setSignedIn(null)
                    gateway.sessionCheck = SessionCheck.GONE
                    store.failures += FakeAccountDataStore.refused()
                }
            }
            assertEquals(DeletionRun.Completed, deletion.run("uid-1"))
        }

    @Test
    fun anotherAccountIsNeverDeletedByAMarkerThatIsNotTheirs() =
        runTest {
            gateway.setSignedIn("uid-2")
            assertEquals(DeletionRun.Failed, deletion.run("uid-1"))
            assertTrue(timeline.entries.isEmpty())
            assertEquals(0, gateway.deleteCalls)
        }

    // --- The inventory read by the information screen -------------------------------------------------------

    @Test
    fun theInventory_readsTheServer_andNeverGivesAnIncompleteAnswer() =
        runTest {
            assertEquals(Inventory.NoMiffs, deletion.inventory("uid-1"))
            store.memberships = 2
            assertEquals(Inventory.HasMiffs, deletion.inventory("uid-1"))
            store.failures += FakeAccountDataStore.offline()
            assertEquals(Inventory.Offline, deletion.inventory("uid-1"))
            store.failures += FakeAccountDataStore.refused()
            assertEquals(Inventory.Failed, deletion.inventory("uid-1"))
        }

    @Test
    fun theInventoryNeverWritesAnything() =
        runTest {
            deletion.inventory("uid-1")
            assertEquals(listOf("memberships"), timeline.entries)
        }
}
