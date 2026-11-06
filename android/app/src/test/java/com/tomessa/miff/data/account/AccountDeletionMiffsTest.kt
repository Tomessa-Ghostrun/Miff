package com.tomessa.miff.data.account

import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.tomessa.miff.domain.auth.SessionCheck
import com.tomessa.miff.testsupport.AccountWorld
import com.tomessa.miff.testsupport.FakeAccountSession
import com.tomessa.miff.testsupport.FakeAuthGateway
import com.tomessa.miff.testsupport.FakeCachePurger
import com.tomessa.miff.testsupport.Timeline
import com.tomessa.miff.testsupport.firestoreError
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The deletion of an account with Miffs, end to end on a double of the server that applies the constraints of the deployed rules (ticket AUTH-5b,
 * criteria SC16 to SC23, SC26, SC30, SA5): the real procedure of MIFF-2 for the Miffs created, the lots "Quitter" for the Miffs joined, a refusal if a lot breaks
 * a rule. Order, budgets, replay after an interruption at EVERY lot, an answer that was lost, the dead end, the cancellation.
 */
class AccountDeletionMiffsTest {
    private val me = "uid-1"
    private val timeline = Timeline()
    private var world = AccountWorld(me, timeline)
    private var gateway = FakeAuthGateway(timeline).also { it.setSignedIn(me) }
    private var session = FakeAccountSession(timeline)

    private fun deletion(): AccountDeletion =
        AccountDeletion(world, gateway, FakeCachePurger(timeline), session, miffsOn(world), timeoutMillis = 1_000L)

    /** A premium account: a Miff stale, one joined, one created with three others and places, one created alone. */
    private fun fullWorld() {
        world.profileExists = true
        world.devices += "d1"
        world.addStaleIndex(ROOM_3)
        world.addRoom(ROOM_1, owner = "other", others = listOf(me, "x"), indexKey = ROOM_1)
        world.addRoom(ROOM_2, owner = me, others = listOf("a", "b", "c"), places = 6, indexKey = ROOM_2)
        world.addRoom(ROOM_4, owner = me, indexKey = ROOM_4)
    }

    private fun freshWorld(build: () -> Unit = ::fullWorld) {
        world = AccountWorld(me, timeline)
        gateway = FakeAuthGateway(timeline).also { it.setSignedIn(me) }
        session = FakeAccountSession(timeline)
        timeline.entries.clear()
        build()
    }

    private fun assertNothingOfThePersonIsLeft() {
        assertTrue("no index", world.indexes.isEmpty())
        assertFalse("no profile", world.profileExists)
        world.rooms.values.forEach { room ->
            assertFalse("no card", me in room.members)
            assertFalse("no status", me in room.status)
            assertFalse("no photo", me in room.photos)
            assertFalse("no last position", me in room.positions)
        }
        assertTrue("no device", world.devices.isEmpty())
        assertEquals("the account is deleted once", 1, gateway.deleteCalls)
    }

    // --- The whole run -----------------------------------------------------------------------------------------------------

    @Test
    fun aFullAccount_endsWithNothingOfThePersonLeft_theMiffsOfOthersAreIntact() =
        runTest {
            fullWorld()
            assertEquals(DeletionRun.Completed, deletion().run(me))
            assertNothingOfThePersonIsLeft()
            // The Miff of somebody else: only the card, the status, the photo and the position of the person went; the code did NOT change.
            val joined = world.rooms.getValue(ROOM_1)
            assertEquals(setOf("other", "x"), joined.members)
            assertTrue(joined.code in world.codes)
            // The Miffs created are gone with their tombstone, their code and their places.
            assertTrue(ROOM_2 !in world.rooms && ROOM_4 !in world.rooms)
            assertEquals(setOf(ROOM_2, ROOM_4), world.tombstones)
            assertEquals(setOf(ROOM_1), world.codes.values.toSet())
        }

    @Test
    fun theLotsAreInTheOrderOfTheTicket_staleThenJoinedThenCreated_oneAfterTheOther() =
        runTest {
            fullWorld()
            deletion().run(me)
            // stale index alone (2), joined (5), then ROOM_2: 3 removals (7 each), places 5 + 1, last lot (8), then ROOM_4: last lot (8)
            assertEquals(listOf(2, 5, 7, 7, 7, 5, 1, 8, 8), world.lotSizes)
        }

    @Test
    fun everyLotRespectsTheBudgets_andNoRemovalIsMixedWithALastLot() =
        runTest {
            fullWorld()
            deletion().run(me)
            // Budgets of the ticket (6.1): leave 5, index alone 2, removal 7, places 5, last lot 8 at most; none above.
            assertTrue(world.lotSizes.all { it <= 8 })
            assertEquals(3, world.removalLots().size)
            world.removalLots().forEach { assertEquals(7, it.size) }
            world.finalLots().forEach { lot ->
                assertTrue(lot.size <= 8)
                assertTrue(lot.none { it.path.contains("/members/") && !it.path.endsWith("/$me") })
            }
            assertEquals(
                "a lot never touches a Miff of somebody else",
                0,
                world.lots.flatten().count {
                    it.path ==
                        "miffs/$ROOM_1" ||
                        it.path.startsWith("miffs/$ROOM_1/places")
                },
            )
        }

    @Test
    fun theSessionTheDevicesTheProfileAndTheAccount_comeAfterEveryLot_inThatOrder() =
        runTest {
            fullWorld()
            deletion().run(me)
            val order = timeline.entries
            val lastLot = order.lastIndexOf("lot")
            assertTrue(lastLot < order.indexOf("read:devices"))
            assertTrue(order.indexOf("read:device.delete") < order.indexOf("session.delete"))
            assertTrue(order.indexOf("session.delete") < order.indexOf("read:profile"))
            assertTrue(order.indexOf("read:profile") < order.indexOf("markPending"))
            assertEquals("delete", order.last())
        }

    @Test
    fun theMiffsOfThePersonAreNeverTouchedBeforeTheyProvedWhoTheyAre_theProcedureStartsFromTheInventory() =
        runTest {
            fullWorld()
            deletion().run(me)
            assertEquals("read:memberships", timeline.entries.first())
        }

    @Test
    fun aFreeAccountWithOneMiffJoined_leavesWithoutRenewingTheCode() =
        runTest {
            world.addRoom(ROOM_1, owner = "other", others = listOf(me))
            val before = world.rooms.getValue(ROOM_1).code
            assertEquals(DeletionRun.Completed, deletion().run(me))
            assertEquals(before, world.rooms.getValue(ROOM_1).code)
            assertEquals(listOf(5), world.lotSizes)
        }

    @Test
    fun aCreatorOfFifteenMembers_isTheHeavyCase_everyRemovalIsOneLotOfSeven_theLastLotIsOneOfEight() =
        runTest {
            world.addRoom(ROOM_1, owner = me, others = (1..14).map { "m$it" }, places = 3)
            assertEquals(DeletionRun.Completed, deletion().run(me))
            val sizes = world.lotSizes
            assertEquals(14, world.removalLots().size)
            assertEquals(List(14) { 7 } + listOf(3, 8), sizes)
            assertTrue(ROOM_1 in world.tombstones)
        }

    // --- Progress ------------------------------------------------------------------------------------------------------------

    @Test
    fun theProgress_countsTheMiffsJoinedThenCreated_thenTheProfile() =
        runTest {
            fullWorld()
            val steps = mutableListOf<DeletionStep>()
            deletion().run(me) { steps += it }
            assertEquals(
                listOf(
                    DeletionStep.Checking,
                    DeletionStep.Miff(1, 3, longWait = false),
                    DeletionStep.Miff(2, 3, longWait = false),
                    DeletionStep.Miff(3, 3, longWait = false),
                    DeletionStep.Profile,
                ),
                steps,
            )
        }

    // --- Replay after an interruption, at EVERY lot (criteria SC26, SC30) ---------------------------------------------------

    private fun baseline(): Pair<Int, List<Int>> {
        freshWorld()
        runTest { assertEquals(DeletionRun.Completed, deletion().run(me)) }
        return world.attempts to world.lotSizes
    }

    @Test
    fun noNetworkBeforeAnyLot_thenTheReplay_alwaysEndsDeleted_withNoLotDoneTwice() {
        val (attempts, sizes) = baseline()
        for (cut in 0 until attempts) {
            freshWorld()
            world.failLot = { n -> if (n == cut) firestoreError(Code.UNAVAILABLE) else null }
            runTest {
                assertEquals("lot $cut", DeletionRun.Offline, deletion().run(me))
                // The profile, the account and the session were not touched: a Miff was still there.
                assertTrue("lot $cut", world.profileExists)
                assertEquals("lot $cut", 0, gateway.deleteCalls)
                assertTrue("lot $cut", session.deleted.isEmpty())
                world.failLot = { null }
                assertEquals("lot $cut", DeletionRun.Completed, deletion().run(me))
            }
            assertNothingOfThePersonIsLeft()
            assertEquals("lot $cut: every lot is applied exactly once", sizes.sorted(), world.lotSizes.sorted())
        }
    }

    @Test
    fun anAnswerLostAfterAnyLot_thenTheReplay_alwaysEndsDeleted_withNoLotDoneTwice() {
        val (attempts, sizes) = baseline()
        for (lost in 0 until attempts) {
            freshWorld()
            // The lot goes through, then the answer is lost: the phone does not know. (A lot that the rules refuse on its own is not "applied": no loss.)
            world.loseAnswerAt = { n -> n == lost }
            runTest {
                var result = deletion().run(me)
                world.loseAnswerAt = { false }
                var rounds = 0
                while (result != DeletionRun.Completed && rounds++ < 4) result = deletion().run(me)
                assertEquals("lot $lost", DeletionRun.Completed, result)
            }
            assertNothingOfThePersonIsLeft()
            assertEquals("lot $lost: every lot is applied exactly once", sizes.sorted(), world.lotSizes.sorted())
        }
    }

    // --- The dead end, the cancellation ----------------------------------------------------------------------------------------

    @Test
    fun aLotRefusedForGood_isTheDeadEnd_theProfileTheSessionAndTheAccountNeverGo_theMiffStaysWhole() =
        runTest {
            world.devices += "d1"
            world.addRoom(ROOM_2, owner = me, others = listOf("a", "b"), indexKey = ROOM_2)
            world.failLot = { firestoreError(Code.PERMISSION_DENIED) }
            assertEquals(DeletionRun.Failed, deletion().run(me))
            assertEquals(DeletionRun.Failed, deletion().run(me))
            assertTrue(world.profileExists)
            assertEquals(0, gateway.deleteCalls)
            assertTrue(session.deleted.isEmpty())
            assertEquals(listOf("d1"), world.devices)
            assertTrue(
                "the Miff is still there, with its creator",
                ROOM_2 in world.rooms && me in world.rooms.getValue(ROOM_2).members,
            )
            // The server is fine again: the next try takes it from where it stood.
            world.failLot = { null }
            assertEquals(DeletionRun.Completed, deletion().run(me))
            assertNothingOfThePersonIsLeft()
        }

    @Test
    fun aMiffThatKeepsComingBack_isACancellation_theProfileAndTheAccountStay() =
        runTest {
            val pool = listOf(ROOM_1, ROOM_2, ROOM_3, ROOM_4)
            var next = 0
            // Each time the indexes are counted, another phone has just joined one more Miff.
            world.onCall = { name ->
                if (name == "count" && next < pool.size) {
                    world.addRoom(pool[next], owner = "other", others = listOf(me), indexKey = pool[next])
                    next++
                }
            }
            assertEquals(DeletionRun.MiffsRemain, deletion().run(me))
            assertTrue(world.profileExists)
            assertEquals(0, gateway.deleteCalls)
            assertTrue(session.deleted.isEmpty())
            assertEquals(
                "the Miffs that were handled are gone: what the person asked for",
                2,
                world.lotSizes.count {
                    it ==
                        5
                },
            )
        }

    @Test
    fun aMiffJoinedMeanwhile_getsAnotherPass_andTheRunEndsDeleted() =
        runTest {
            var added = false
            world.addRoom(ROOM_1, owner = "other", others = listOf(me))
            world.onCall = { name ->
                if (name == "count" && !added) {
                    added = true
                    world.addRoom(ROOM_2, owner = "other", others = listOf(me), indexKey = ROOM_2)
                }
            }
            assertEquals(DeletionRun.Completed, deletion().run(me))
            assertNothingOfThePersonIsLeft()
        }

    @Test
    fun aMiffThatAppearsAfterTheSession_sendsTheRunBackToTheMiffs_beforeTheProfile() =
        runTest {
            var counts = 0
            world.onCall = { name ->
                if (name == "count" && ++counts == 2) world.addRoom(ROOM_1, owner = "other", others = listOf(me))
            }
            assertEquals(DeletionRun.Completed, deletion().run(me))
            assertNothingOfThePersonIsLeft()
            val order = timeline.entries
            assertTrue(order.lastIndexOf("lot") < order.indexOf("read:profile"))
            assertEquals(2, order.count { it == "session.delete" })
        }

    @Test
    fun anIndexThatNothingCanName_isAFailureNeverACancellation_nothingOfTheProfileGoes() =
        runTest {
            world.indexes["junk"] = null
            assertEquals(DeletionRun.Failed, deletion().run(me))
            assertTrue(world.profileExists)
            assertEquals(0, gateway.deleteCalls)
        }

    // --- Network, session ---------------------------------------------------------------------------------------------------

    @Test
    fun noNetworkAtTheInventory_isAPause_nothingIsDeleted_notEvenTheDevices() =
        runTest {
            fullWorld()
            world.failCall = { if (it == "memberships") firestoreError(Code.UNAVAILABLE) else null }
            assertEquals(DeletionRun.Offline, deletion().run(me))
            assertTrue(world.lots.isEmpty())
            assertEquals(listOf("d1"), world.devices)
        }

    @Test
    fun theAccountDeletedElsewhereMeanwhile_isASuccess_notAFailure() =
        runTest {
            world.addRoom(ROOM_1, owner = "other", others = listOf(me))
            world.failLot = { firestoreError(Code.PERMISSION_DENIED) }
            gateway.sessionCheck = SessionCheck.GONE
            assertEquals(DeletionRun.Completed, deletion().run(me))
        }

    @Test
    fun anExpiredSessionInTheMiddleOfTheMiffs_isNeverASuccess() =
        runTest {
            world.addRoom(ROOM_1, owner = "other", others = listOf(me))
            world.failLot = { firestoreError(Code.PERMISSION_DENIED) }
            gateway.sessionCheck = SessionCheck.EXPIRED
            assertEquals(DeletionRun.SessionExpired, deletion().run(me))
            assertEquals(0, gateway.deleteCalls)
        }
}
