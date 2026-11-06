package com.tomessa.miff.data.account

import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.tomessa.miff.data.miff.FirestoreMiffRepository
import com.tomessa.miff.data.miff.MiffDeletion
import com.tomessa.miff.domain.miff.JoinCode
import com.tomessa.miff.domain.miff.MembershipRef
import com.tomessa.miff.domain.miff.MiffFailure
import com.tomessa.miff.domain.miff.WriteKind
import com.tomessa.miff.testsupport.AccountWorld
import com.tomessa.miff.testsupport.FakeActiveDevice
import com.tomessa.miff.testsupport.FakeCachePurger
import com.tomessa.miff.testsupport.FakeLocationProbe
import com.tomessa.miff.testsupport.FakeMiffObserver
import com.tomessa.miff.testsupport.Timeline
import com.tomessa.miff.testsupport.firestoreError
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

const val ROOM_1 = "AbCdEfGhIjKlMnOpQrSt"
const val ROOM_2 = "ZyXwVuTsRqPoNmLkJiHg"
const val ROOM_3 = "A1B2C3D4E5F6G7H8J9K2"
const val ROOM_4 = "M3N4P5Q6R7S8T9U2V3W4"

/** The code of a new Miff or of a renewal: valid and different at every draw (never an I, L, O, 0 or 1). */
class CodeCounter {
    private var n = 0

    fun next(): String {
        n++
        return "KKKKKKKKKK" + JoinCode.ALPHABET[n / JoinCode.ALPHABET.length % JoinCode.ALPHABET.length] +
            JoinCode.ALPHABET[n % JoinCode.ALPHABET.length]
    }
}

/** The real procedures (MIFF-2 deletion, repository) on the double of the server: what the deletion of an account runs against. */
fun miffsOn(world: AccountWorld): StoreAccountMiffs {
    val codes = CodeCounter()
    val repository =
        FirestoreMiffRepository(
            store = world,
            observer = FakeMiffObserver(),
            location = FakeLocationProbe(),
            device = FakeActiveDevice(),
            newMiffId = { ROOM_1 },
            newCode = codes::next,
            timeoutMillis = 1_000,
            cache = FakeCachePurger(Timeline()),
        )
    return StoreAccountMiffs(world, MiffDeletion(world, repository, timeoutMillis = 1_000), timeoutMillis = 1_000)
}

/**
 * The Miffs of the deletion of an account (ticket AUTH-5, sections 5.5 and 6.1; criteria SC16 to SC21): what the inventory says, the lot "Quitter" and
 * its fall-back, the deletion of a Miff created through the procedure of MIFF-2, and above all the BUDGETS (writes per lot) and the constraints of the rules.
 */
class AccountMiffsTest {
    private val me = "uid-1"
    private val world = AccountWorld(me)
    private val miffs = miffsOn(world)

    // --- The inventory ------------------------------------------------------------------------------------------------------------

    @Test
    fun theInventory_tellsTheMiffsJoined_theMiffsCreatedWithTheOthers_andTheStaleIndexes() =
        runTest {
            world.addRoom(ROOM_1, owner = "other", others = listOf(me, "x"))
            world.addRoom(ROOM_2, owner = me, others = listOf("a", "b", "c"), name = "Famille")
            world.addStaleIndex(ROOM_3)
            val inventory = (miffs.inventory(me) as MiffsRead.Read).inventory
            assertEquals(listOf(ROOM_1), inventory.joined.map { it.miffId })
            assertEquals(listOf(ROOM_2), inventory.owned.map { it.miffId })
            assertEquals("Famille", inventory.owned.single().name)
            assertEquals("the people OTHER than the creator", 3, inventory.owned.single().otherMembers)
            assertEquals(listOf(ROOM_3), inventory.stale.map { it.miffId })
            assertEquals(2, inventory.visibleCount)
            assertEquals(3, inventory.removals)
        }

    @Test
    fun aCreatorAlone_hasNoOtherMember_theNumberIsZero_theScreenNeverSaysIt() =
        runTest {
            world.addRoom(ROOM_1, owner = me)
            assertEquals(
                0,
                (miffs.inventory(me) as MiffsRead.Read)
                    .inventory.owned
                    .single()
                    .otherMembers,
            )
        }

    @Test
    fun anAccountWithNothing_isAnEmptyInventory() =
        runTest {
            assertTrue((miffs.inventory(me) as MiffsRead.Read).inventory.isEmpty)
        }

    @Test
    fun aMiffTheServerSaysIsMissing_isStale_andSoIsOneItRefusesWhileTheServerIsReachable() =
        runTest {
            world.missingReadsAsNull = true
            world.addStaleIndex(ROOM_1)
            assertEquals(listOf(ROOM_1), (miffs.inventory(me) as MiffsRead.Read).inventory.stale.map { it.miffId })
            world.missingReadsAsNull = false
            assertEquals(listOf(ROOM_1), (miffs.inventory(me) as MiffsRead.Read).inventory.stale.map { it.miffId })
        }

    @Test
    fun aRefusalWhileTheServerIsNotReachable_isNeverTakenForARemoval_noIndexIsTouched() =
        runTest {
            // A refusal of App Check would refuse EVERYTHING: nobody is "removed" because of it.
            world.addStaleIndex(ROOM_1)
            world.reachable = false
            assertEquals(MiffsRead.Failed(MiffFailure.REFUSED), miffs.inventory(me))
            assertTrue(world.lots.isEmpty())
        }

    @Test
    fun noNetwork_isAFailureOfTheRead_neverAnEmptyList() =
        runTest {
            world.addRoom(ROOM_1, owner = me)
            world.failCall = { if (it == "memberships") firestoreError(Code.UNAVAILABLE) else null }
            assertEquals(MiffsRead.Failed(MiffFailure.OFFLINE), miffs.inventory(me))
            world.failCall = { if (it == "miff") firestoreError(Code.UNAVAILABLE) else null }
            assertEquals(MiffsRead.Failed(MiffFailure.OFFLINE), miffs.inventory(me))
            world.failCall = { if (it == "members") firestoreError(Code.UNAVAILABLE) else null }
            assertEquals(MiffsRead.Failed(MiffFailure.OFFLINE), miffs.inventory(me))
        }

    @Test
    fun theInventoryNeverWritesAnything_andReadsTheServer() =
        runTest {
            world.addRoom(ROOM_1, owner = me, others = listOf("a"))
            miffs.inventory(me)
            assertTrue(world.lots.isEmpty())
            assertEquals(0, world.attempts)
        }

    @Test
    fun noNameIsEverInATextOfTheInventory() {
        val owned = OwnedMiff(ROOM_1, "Famille Secrète", 2)
        assertFalse(owned.toString().contains("Secrète"))
        assertFalse(MiffsInventory(emptyList(), listOf(owned), emptyList()).toString().contains("Secrète"))
        assertFalse(AccountMiffsSummary(0, listOf(owned)).toString().contains("Secrète"))
        assertFalse(
            MiffsRead.Read(MiffsInventory(emptyList(), listOf(owned), emptyList())).toString().contains("Secrète"),
        )
    }

    // --- The lot "Quitter" --------------------------------------------------------------------------------------------------

    @Test
    fun leave_isOneLotOfFiveDeletions_theCodeDoesNotChange_theOthersStay() =
        runTest {
            val room = world.addRoom(ROOM_1, owner = "other", others = listOf(me, "x"))
            val code = room.code
            assertEquals(MiffStep.Done, miffs.leave(me, MembershipRef("free", ROOM_1)))
            assertEquals(1, world.lots.size)
            assertEquals("the card, the status, the photo, the last position, the index", 5, world.lots.single().size)
            assertTrue(world.lots.single().all { it.kind == WriteKind.DELETE })
            assertEquals(code, world.rooms.getValue(ROOM_1).code)
            assertEquals(setOf("other", "x"), world.rooms.getValue(ROOM_1).members)
            assertFalse(me in world.rooms.getValue(ROOM_1).positions)
            assertTrue(world.indexes.isEmpty())
        }

    @Test
    fun leave_whenTheCardIsAlreadyGone_fallsBackToTheIndexAlone_withoutAMessage() =
        runTest {
            world.addRoom(ROOM_1, owner = "other", others = listOf(me))
            world.rooms.getValue(ROOM_1).apply {
                members -= me
                status -= me
                photos -= me
            }
            assertEquals(MiffStep.Done, miffs.leave(me, MembershipRef("free", ROOM_1)))
            assertEquals("the lot refused, then the index and the last position alone", 2, world.attempts)
            assertEquals(listOf(2), world.lotSizes)
            assertTrue(world.indexes.isEmpty())
            assertFalse(
                "the last position left behind goes with the index",
                me in world.rooms.getValue(ROOM_1).positions,
            )
        }

    @Test
    fun leave_withNoNetwork_isAPause_theIndexAloneIsNotTried() =
        runTest {
            world.addRoom(ROOM_1, owner = "other", others = listOf(me))
            world.failLot = { firestoreError(Code.UNAVAILABLE) }
            assertEquals(MiffStep.Failed(MiffFailure.OFFLINE), miffs.leave(me, MembershipRef("free", ROOM_1)))
            assertEquals(1, world.attempts)
            assertEquals("free", world.indexes.keys.single())
        }

    @Test
    fun leave_refusedTwice_isAFailure_nothingIsLeftHalfDone() =
        runTest {
            world.addRoom(ROOM_1, owner = "other", others = listOf(me))
            world.failLot = { firestoreError(Code.PERMISSION_DENIED) }
            assertEquals(MiffStep.Failed(MiffFailure.REFUSED), miffs.leave(me, MembershipRef("free", ROOM_1)))
            assertEquals(2, world.attempts)
            assertTrue(world.lots.isEmpty())
            assertTrue(me in world.rooms.getValue(ROOM_1).members)
        }

    @Test
    fun dropStale_onAMiffThatNoLongerExists_isTheIndexAlone() =
        runTest {
            world.addStaleIndex(ROOM_3)
            assertEquals(MiffStep.Done, miffs.dropStale(me, MembershipRef(ROOM_3, ROOM_3)))
            assertTrue(world.indexes.isEmpty())
            assertEquals(listOf(2), world.lotSizes)
        }

    @Test
    fun aPremiumIndex_keyedByTheIdOfTheMiff_isFoundByItsField_notByItsKey() =
        runTest {
            world.addRoom(ROOM_1, owner = "other", others = listOf(me), indexKey = ROOM_1)
            val ref = (miffs.inventory(me) as MiffsRead.Read).inventory.joined.single()
            assertEquals(ROOM_1, ref.key)
            assertEquals(MiffStep.Done, miffs.leave(me, ref))
            assertTrue(world.indexes.isEmpty())
        }

    // --- The Miffs created: the procedure of MIFF-2, with its budgets --------------------------------------------------------

    @Test
    fun aMiffCreated_goesThroughTheExistingProcedure_withinTheBudgets_noRemovalInTheLastLot() =
        runTest {
            world.addRoom(ROOM_1, owner = me, others = listOf("a", "b", "c", "d"), places = 7)
            assertEquals(MiffStep.Done, miffs.deleteOwned(me, ROOM_1))
            // four removals of 7 writes, the places 5 + 2, the last lot of 8
            assertEquals(listOf(7, 7, 7, 7, 5, 2, 8), world.lotSizes)
            assertTrue("never above the 8 writes of the last lot", world.lotSizes.all { it <= 8 })
            val removals = world.removalLots()
            assertEquals(4, removals.size)
            removals.forEach { lot ->
                assertEquals(
                    "the removal lot renews the code (update + new + old)",
                    3,
                    lot.count {
                        it.path.startsWith("joinCodes/") ||
                            it.path == "miffs/$ROOM_1"
                    },
                )
            }
            val last = world.finalLots().single()
            assertTrue(
                "no removal of somebody else's card in the last lot",
                last.none {
                    it.path.contains("/members/") &&
                        !it.path.endsWith("/$me")
                },
            )
            assertTrue(last.any { it.path == "miffsDeleted/$ROOM_1" && it.kind == WriteKind.SET })
            assertTrue("the Miff is gone, with its tombstone", ROOM_1 !in world.rooms && ROOM_1 in world.tombstones)
            assertTrue("the code with it", world.codes.isEmpty())
            assertTrue(world.indexes.isEmpty())
        }

    @Test
    fun aMiffCreatedAlone_isOneLotOfEight() =
        runTest {
            world.addRoom(ROOM_1, owner = me)
            assertEquals(MiffStep.Done, miffs.deleteOwned(me, ROOM_1))
            assertEquals(listOf(8), world.lotSizes)
        }

    @Test
    fun aMiffOfSomebodyElse_isNeverDeleted_thePersonIsNotItsCreator() =
        runTest {
            world.addRoom(ROOM_1, owner = "other", others = listOf(me))
            assertEquals(MiffStep.Failed(MiffFailure.UNKNOWN), miffs.deleteOwned(me, ROOM_1))
            assertTrue(world.lots.isEmpty())
            assertTrue(ROOM_1 in world.rooms)
        }

    @Test
    fun aMiffCreated_withNoNetworkInTheMiddle_isAPause_andTheReplayCarriesOn() =
        runTest {
            world.addRoom(ROOM_1, owner = me, others = listOf("a", "b"))
            world.failLot = { n -> if (n == 1) firestoreError(Code.UNAVAILABLE) else null }
            assertEquals(MiffStep.Failed(MiffFailure.OFFLINE), miffs.deleteOwned(me, ROOM_1))
            assertEquals("a removal went through, the other did not", 1, world.removalLots().size)
            world.failLot = { null }
            assertEquals(MiffStep.Done, miffs.deleteOwned(me, ROOM_1))
            assertEquals("two removals in all: none was done twice", 2, world.removalLots().size)
            assertNotNull(world.tombstones.singleOrNull())
        }
}
