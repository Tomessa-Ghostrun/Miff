package com.tomessa.miff.data.account

import com.tomessa.miff.domain.auth.AuthFailure
import com.tomessa.miff.domain.auth.SessionCheck
import com.tomessa.miff.domain.miff.MembershipRef
import com.tomessa.miff.domain.miff.MiffFailure
import com.tomessa.miff.testsupport.FakeAccountDataStore
import com.tomessa.miff.testsupport.FakeAccountMiffs
import com.tomessa.miff.testsupport.FakeAccountSession
import com.tomessa.miff.testsupport.FakeAuthGateway
import com.tomessa.miff.testsupport.FakeCachePurger
import com.tomessa.miff.testsupport.Timeline
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The procedure of the deletion of an account (ticket AUTH-5, sections 5.5, 5.6 and 6): order, idempotence, replay after an interruption at EVERY
 * step, a session that is gone = success, nothing deleted while a Miff is there (criteria SC9, SC19, SC22, SC23, SC25, SC26, SC28, SA5). The Miffs are
 * scripted here ([FakeAccountMiffs]): the orchestration only. The lots themselves, against a double of the server, are in [AccountDeletionMiffsTest].
 */
class AccountDeletionTest {
    private val timeline = Timeline()
    private val store = FakeAccountDataStore(timeline)
    private val gateway = FakeAuthGateway(timeline).also { it.setSignedIn("uid-1") }
    private val cache = FakeCachePurger(timeline)
    private val session = FakeAccountSession(timeline)
    private val miffs = FakeAccountMiffs(timeline)
    private val deletion = AccountDeletion(store, gateway, cache, session, miffs, timeoutMillis = 1_000L)

    private val joined = MembershipRef("free", "AbCdEfGhIjKlMnOpQrSt")
    private val owned = OwnedMiff("ZyXwVuTsRqPoNmLkJiHg", "Famille", otherMembers = 2)
    private val stale = MembershipRef("old", "Zz0Zz0Zz0Zz0Zz0Zz0Zz")

    private fun miffsRead(
        joined: List<MembershipRef> = emptyList(),
        owned: List<OwnedMiff> = emptyList(),
        stale: List<MembershipRef> = emptyList(),
    ) = MiffsRead.Read(MiffsInventory(joined, owned, stale))

    @Test
    fun theOrderIsInventory_devices_finalCheck_profile_thenTheQueueLeaves_andTheAccountGoesLast() =
        runTest {
            store.devices += listOf("d1", "d2")
            assertEquals(DeletionRun.Completed, deletion.run("uid-1"))
            assertEquals(
                listOf(
                    "miffs.inventory",
                    "session.delete",
                    "memberships",
                    "devices.read",
                    "device.delete",
                    "device.delete",
                    "session.delete",
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

    // --- Miffs: joined first, then created; never a profile or an account with a Miff behind it ----------------------------

    @Test
    fun theStaleIndexes_thenTheMiffsJoined_thenTheMiffsCreated_inThatOrder_beforeTheDevicesAndTheProfile() =
        runTest {
            miffs.inventory = miffsRead(joined = listOf(joined), owned = listOf(owned), stale = listOf(stale))
            store.memberships = 3
            store.onCall = { name -> if (name == "memberships" && miffs.deleted.isNotEmpty()) store.memberships = 0 }
            assertEquals(DeletionRun.Completed, deletion.run("uid-1"))
            val order = timeline.entries
            val dropStale = order.indexOf("miffs.dropStale")
            val leave = order.indexOf("miffs.leave")
            val delete = order.indexOf("miffs.deleteOwned")
            assertTrue("stale, then joined, then created", dropStale < leave && leave < delete)
            assertTrue("the Miffs before the devices", delete < order.indexOf("devices.read"))
            assertTrue(
                "the session goes BEFORE the first lot (decision of 2026-11-06)",
                order.indexOf("session.delete") < dropStale,
            )
            assertTrue("the Miffs before the profile", delete < order.indexOf("profile.delete"))
            assertEquals("delete", order.last())
        }

    @Test
    fun theMiffsCreated_areDeletedOneAfterTheOther_inTheOrderOfTheInventory() =
        runTest {
            val second = OwnedMiff("A1B2C3D4E5F6G7H8J9K2", "Amis", otherMembers = 0)
            miffs.inventory = miffsRead(owned = listOf(owned, second))
            store.memberships = 2
            store.onCall = { name -> if (name == "memberships" && miffs.deleted.size == 2) store.memberships = 0 }
            assertEquals(DeletionRun.Completed, deletion.run("uid-1"))
            assertEquals(listOf(owned.miffId, second.miffId), miffs.deleted)
        }

    @Test
    fun theProgress_isMiffIndexOnTotal_joinedFirst_thenTheProfile() =
        runTest {
            miffs.inventory = miffsRead(joined = listOf(joined), owned = listOf(owned))
            store.memberships = 2
            store.onCall = { name -> if (name == "memberships" && miffs.deleted.isNotEmpty()) store.memberships = 0 }
            val steps = mutableListOf<DeletionStep>()
            deletion.run("uid-1") { steps += it }
            assertEquals(
                listOf(
                    DeletionStep.Checking,
                    DeletionStep.Miff(1, 2, longWait = false),
                    DeletionStep.Miff(2, 2, longWait = false),
                    DeletionStep.Profile,
                ),
                steps,
            )
        }

    @Test
    fun moreThanThreePeopleToTakeOut_saysTheWaitIsLonger_threeOrFewerDoNot() =
        runTest {
            miffs.inventory = miffsRead(owned = listOf(OwnedMiff("ZyXwVuTsRqPoNmLkJiHg", "Famille", otherMembers = 4)))
            store.memberships = 1
            store.onCall = { name -> if (name == "memberships" && miffs.deleted.isNotEmpty()) store.memberships = 0 }
            val steps = mutableListOf<DeletionStep>()
            deletion.run("uid-1") { steps += it }
            assertTrue(steps.contains(DeletionStep.Miff(1, 1, longWait = true)))
            miffs.deleted.clear()
            gateway.setSignedIn("uid-1")
            store.memberships = 1
            miffs.inventory = miffsRead(owned = listOf(OwnedMiff("ZyXwVuTsRqPoNmLkJiHg", "Famille", otherMembers = 3)))
            val few = mutableListOf<DeletionStep>()
            deletion.run("uid-1") { few += it }
            assertTrue(few.contains(DeletionStep.Miff(1, 1, longWait = false)))
        }

    @Test
    fun aStepOfAMiffThatIsRefused_isAFailureToTryAgain_andNeverTheAccountIsTouched() =
        runTest {
            miffs.inventory = miffsRead(owned = listOf(owned))
            miffs.failures += MiffFailure.REFUSED
            store.devices += "d1"
            assertEquals(DeletionRun.Failed, deletion.run("uid-1"))
            // The dead end of the ticket: a Miff created is still there, so no device, no profile, no account. The session was deleted at the start
            // (decision of 2026-11-06): the other phones are already signed out, and this one is closed.
            assertEquals(listOf("d1"), store.devices)
            assertTrue(store.profileExists)
            assertEquals(0, gateway.deleteCalls)
            assertEquals(listOf("uid-1"), session.deleted)
        }

    @Test
    fun noNetworkInTheMiddleOfTheMiffs_isAPause_nothingAfterIt_andTheReplayGoesOn() =
        runTest {
            miffs.inventory = miffsRead(joined = listOf(joined), owned = listOf(owned))
            miffs.failures += MiffFailure.OFFLINE
            assertEquals(DeletionRun.Offline, deletion.run("uid-1"))
            assertTrue(store.profileExists)
            assertEquals(0, gateway.deleteCalls)
            assertFalse("devices.read" in timeline.entries)
            store.memberships = 2
            store.onCall = { name -> if (name == "memberships" && miffs.deleted.isNotEmpty()) store.memberships = 0 }
            assertEquals(DeletionRun.Completed, deletion.run("uid-1"))
            assertEquals("the Miff joined was tried again, and only then the one created", 2, miffs.leaves.size)
        }

    @Test
    fun aMiffRefusedBecauseTheAccountWasDeletedElsewhere_isASuccess() =
        runTest {
            miffs.inventory = miffsRead(joined = listOf(joined))
            miffs.failures += MiffFailure.REFUSED
            gateway.sessionCheck = SessionCheck.GONE
            assertEquals(DeletionRun.Completed, deletion.run("uid-1"))
        }

    @Test
    fun anInventoryThatCannotBeRead_isNeverTakenForAnEmptyAccount() =
        runTest {
            miffs.inventory = MiffsRead.Failed(MiffFailure.OFFLINE)
            assertEquals(DeletionRun.Offline, deletion.run("uid-1"))
            miffs.inventory = MiffsRead.Failed(MiffFailure.REFUSED)
            assertEquals(DeletionRun.Failed, deletion.run("uid-1"))
            assertTrue(store.profileExists)
            assertEquals(0, gateway.deleteCalls)
            assertFalse("profile.delete" in timeline.entries)
        }

    @Test
    fun anIndexThatCannotBeNamed_isAFailureWithTheCalmStop_neverACancellation_theProfileStays() =
        runTest {
            // The server counts a document that is not an index (nothing the inventory can name): nothing is deleted, three passes, then a failure.
            store.memberships = 1
            store.devices += "d1"
            assertEquals(DeletionRun.Failed, deletion.run("uid-1"))
            assertEquals(3, timeline.entries.count { it == "miffs.inventory" })
            assertEquals(listOf("d1"), store.devices)
            assertTrue(store.profileExists)
            assertEquals(0, gateway.deleteCalls)
        }

    @Test
    fun aMiffThatKeepsComingBack_isACancellationAfterThreePasses_nothingOfTheProfileIsDeleted() =
        runTest {
            miffs.inventory = miffsRead(joined = listOf(joined))
            store.memberships = 1
            assertEquals(DeletionRun.MiffsRemain, deletion.run("uid-1"))
            assertEquals(3, miffs.leaves.size)
            assertTrue(store.profileExists)
            assertEquals(0, gateway.deleteCalls)
            // Deleted once, at the start (the cancellation writes a new one: SessionManager.cancelAccountDeletion).
            assertEquals(listOf("uid-1"), session.deleted)
        }

    @Test
    fun aMiffThatShowsUpMeanwhile_getsAnotherPass_andTheRunEndsDeleted() =
        runTest {
            var counted = 0
            store.onCall = { name ->
                if (name == "memberships") {
                    counted++
                    if (counted == 1) {
                        // A Miff is joined from another phone while this one was finishing the first pass: the next pass takes it.
                        store.memberships = 1
                        miffs.inventory = miffsRead(joined = listOf(joined))
                    } else if (counted == 2) {
                        store.memberships = 0
                    }
                }
            }
            assertEquals(DeletionRun.Completed, deletion.run("uid-1"))
            assertEquals(listOf(joined.miffId), miffs.leaves)
        }

    @Test
    fun aMiffThatShowsUpAfterTheSession_sendsTheRunBackToTheMiffs_theProfileWaitsForIt() =
        runTest {
            var counted = 0
            store.onCall = { name ->
                if (name == "memberships") {
                    counted++
                    // 1: the end of the first pass (empty); 2: the final check, after the session (a Miff arrived); 3: the end of the new pass.
                    if (counted == 2) {
                        store.memberships = 1
                        miffs.inventory = miffsRead(joined = listOf(joined))
                    } else if (counted == 3) {
                        store.memberships = 0
                    }
                }
            }
            assertEquals(DeletionRun.Completed, deletion.run("uid-1"))
            val order = timeline.entries
            assertTrue(order.indexOf("miffs.leave") < order.indexOf("profile.delete"))
            assertEquals(
                "once at the start, once again before the profile of each round that gets there",
                3,
                order.count {
                    it ==
                        "session.delete"
                },
            )
            assertEquals(1, gateway.deleteCalls)
        }

    @Test
    fun aMiffThatKeepsShowingUpAfterTheSession_endsAsACancellation_theProfileStays() =
        runTest {
            miffs.inventory = miffsRead(joined = listOf(joined))
            store.onCall = { name -> if (name == "memberships" && miffs.leaves.isNotEmpty()) store.memberships = 1 }
            assertEquals(DeletionRun.MiffsRemain, deletion.run("uid-1"))
            assertTrue(store.profileExists)
            assertEquals(0, gateway.deleteCalls)
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

    // --- The session "un compte, un seul appareil" (SESS-2) --------------------------------------------------------

    @Test
    fun theSessionIsDeletedRightAfterTheInventory_beforeAnyLot_andAgainBeforeTheFinalCheckAndTheProfile() =
        runTest {
            store.devices += "d1"
            miffs.inventory = miffsRead(joined = listOf(joined))
            store.memberships = 1
            store.onCall = { name -> if (name == "memberships" && miffs.leaves.isNotEmpty()) store.memberships = 0 }
            assertEquals(DeletionRun.Completed, deletion.run("uid-1"))
            val order = timeline.entries
            assertTrue("right after the inventory", order.indexOf("miffs.inventory") < order.indexOf("session.delete"))
            assertTrue("before the first lot", order.indexOf("session.delete") < order.indexOf("miffs.leave"))
            assertTrue("again after the devices", order.indexOf("device.delete") < order.lastIndexOf("session.delete"))
            assertTrue(order.lastIndexOf("session.delete") < order.lastIndexOf("memberships"))
            assertTrue(order.lastIndexOf("session.delete") < order.indexOf("profile.delete"))
            assertEquals(listOf("uid-1", "uid-1"), session.deleted)
        }

    @Test
    fun theSessionIsDeletedOnlyOnceBeforeTheMiffs_notAtEachPass() =
        runTest {
            miffs.inventory = miffsRead(joined = listOf(joined))
            store.memberships = 1
            deletion.run("uid-1")
            // Three passes over a Miff that keeps coming back: ONE deletion of the session at the start (and none at the end: the run is cancelled).
            assertEquals(1, timeline.entries.count { it == "session.delete" })
        }

    @Test
    fun withoutTheAcknowledgementOfTheSessionAtTheStart_noLotIsTried_theMiffsAreUntouched() =
        runTest {
            miffs.inventory = miffsRead(joined = listOf(joined), owned = listOf(owned))
            session.failures += FakeAccountDataStore.offline()
            assertEquals(DeletionRun.Offline, deletion.run("uid-1"))
            assertTrue(miffs.leaves.isEmpty() && miffs.deleted.isEmpty())
            assertEquals(0, gateway.deleteCalls)
        }

    @Test
    fun withoutTheAcknowledgementOfTheSession_theDeletionDoesNotGoOn_andIsResumable() =
        runTest {
            session.failures += FakeAccountDataStore.offline()
            assertEquals(DeletionRun.Offline, deletion.run("uid-1"))
            assertTrue("the profile is still there: the deletion paused before it", store.profileExists)
            assertEquals("the authentication account is untouched", 0, gateway.deleteCalls)
            assertFalse("profile.delete" in timeline.entries)
            // Resumed: the session is deleted again (idempotent), then everything goes through.
            assertEquals(DeletionRun.Completed, deletion.run("uid-1"))
            assertFalse(store.profileExists)
            assertEquals(1, gateway.deleteCalls)
            // The failed try, then the replay: once at the start and once before the profile.
            assertEquals(3, timeline.entries.count { it == "session.delete" })
        }

    @Test
    fun aSessionDeletionRefusedByTheServer_isAFailure_theProfileStays() =
        runTest {
            session.failures += FakeAccountDataStore.refused()
            assertEquals(DeletionRun.Failed, deletion.run("uid-1"))
            assertTrue(store.profileExists)
            assertEquals(0, gateway.deleteCalls)
        }

    // --- Replay after an interruption, at EVERY step (criterion SC26) -----------------------------------------

    @Test
    fun interruptedAtEveryStep_theReplayAlwaysEndsWithTheAccountDeleted_andNothingTwice() {
        // The number of calls of a full run with one device: 1 memberships, 2 devices.read, 3 device.delete, 4 memberships, 5 profile.delete.
        for (killedAt in 1..5) {
            val t = Timeline()
            val s = FakeAccountDataStore(t).also { it.devices += "d1" }
            val g = FakeAuthGateway(t).also { it.setSignedIn("uid-1") }
            val d =
                AccountDeletion(
                    s,
                    g,
                    FakeCachePurger(t),
                    FakeAccountSession(t),
                    FakeAccountMiffs(t),
                    timeoutMillis = 1_000L,
                )
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
    fun noSessionAtAll_isNeverTakenForADeletion_nothingIsWritten() =
        runTest {
            // Signed out behind our back: the account may well still exist. Not "Completed".
            gateway.setSignedIn(null)
            assertEquals(DeletionRun.SessionExpired, deletion.run("uid-1"))
            assertTrue(timeline.entries.isEmpty())
        }

    @Test
    fun anExpiredOrRevokedToken_atTheLastCall_isNotASuccess() =
        runTest {
            gateway.deleteResults += AuthFailure.SESSION_EXPIRED
            assertEquals(DeletionRun.SessionExpired, deletion.run("uid-1"))
        }

    @Test
    fun aRefusalWithAnExpiredToken_isNotASuccess_theSessionIsToBeRenewed() =
        runTest {
            store.failures += FakeAccountDataStore.refused()
            gateway.sessionCheck = SessionCheck.EXPIRED
            assertEquals(DeletionRun.SessionExpired, deletion.run("uid-1"))
            assertEquals(0, gateway.deleteCalls)
        }

    @Test
    fun theSessionClosesJustBeforeTheLastCall_isNotASuccess_theAccountIsNotAsked() =
        runTest {
            // deleteCurrentAccount answers "done" when nobody is signed in: that must never count as a deletion.
            store.onCall = { name -> if (name == "profile.delete") gateway.setSignedIn(null) }
            assertEquals(DeletionRun.SessionExpired, deletion.run("uid-1"))
            assertEquals(0, gateway.deleteCalls)
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
            val none = deletion.inventory("uid-1") as Inventory.Loaded
            assertFalse(none.miffs.hasMiffs)
            miffs.inventory = miffsRead(joined = listOf(joined), owned = listOf(owned), stale = listOf(stale))
            val some = deletion.inventory("uid-1") as Inventory.Loaded
            assertEquals(1, some.miffs.joined)
            assertEquals(listOf(owned.miffId), some.miffs.owned.map { it.miffId })
            assertTrue(some.miffs.hasMiffs)
            miffs.inventory = MiffsRead.Failed(MiffFailure.OFFLINE)
            assertEquals(Inventory.Offline, deletion.inventory("uid-1"))
            miffs.inventory = MiffsRead.Failed(MiffFailure.REFUSED)
            assertEquals(Inventory.Failed, deletion.inventory("uid-1"))
        }

    @Test
    fun aStaleIndexAlone_isNotAMiffForThePerson_theScreenSaysNothingOfIt() =
        runTest {
            miffs.inventory = miffsRead(stale = listOf(stale))
            assertFalse((deletion.inventory("uid-1") as Inventory.Loaded).miffs.hasMiffs)
        }

    @Test
    fun theInventoryNeverWritesAnything() =
        runTest {
            deletion.inventory("uid-1")
            assertEquals(listOf("miffs.inventory"), timeline.entries)
        }
}
