package com.tomessa.miff.testsupport

import com.google.firebase.firestore.FirebaseFirestoreException
import com.tomessa.miff.data.account.ROOM_1
import com.tomessa.miff.domain.miff.DocWrite
import com.tomessa.miff.domain.miff.WriteKind
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The double of the server used by the tests of the deletion of an account must REFUSE what the deployed rules refuse (backend/firestore.rules, ticket §6.2):
 * otherwise "the procedure passes against it" would prove nothing. Each constraint is tried by itself, on a lot that breaks only that one.
 */
class AccountWorldTest {
    private val me = "uid-1"
    private val world = AccountWorld(me)

    private fun del(path: String) = DocWrite(path, WriteKind.DELETE)

    private suspend fun assertRefused(vararg writes: DocWrite) {
        try {
            world.commit(writes.toList())
            fail("the lot should have been refused")
        } catch (e: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        }
        assertTrue("a refused lot applies NOTHING", world.lots.isEmpty())
    }

    private fun ownedRoom() = world.addRoom(ROOM_1, owner = me, others = listOf("a"))

    private fun ownerCardLot() =
        arrayOf(
            del("miffs/$ROOM_1/members/$me"),
            del("miffs/$ROOM_1/status/$me"),
            del("miffs/$ROOM_1/photos/$me"),
            del("miffs/$ROOM_1/positions/$me"),
        )

    @Test
    fun aRemovalThatDoesNotRenewTheCode_isRefused() =
        runTest {
            ownedRoom()
            assertRefused(
                del("miffs/$ROOM_1/members/a"),
                del("miffs/$ROOM_1/status/a"),
                del("miffs/$ROOM_1/photos/a"),
                del("miffs/$ROOM_1/positions/a"),
            )
        }

    @Test
    fun aRemovalThatLeavesTheLastPositionBehind_isRefused() =
        runTest {
            val room = ownedRoom()
            // The code IS renewed here: only the last position is left behind.
            assertRefused(
                del("miffs/$ROOM_1/members/a"),
                del("miffs/$ROOM_1/status/a"),
                del("miffs/$ROOM_1/photos/a"),
                DocWrite("miffs/$ROOM_1", WriteKind.UPDATE, mapOf("joinCode" to "NEWCODE23456")),
                DocWrite("joinCodes/NEWCODE23456", WriteKind.SET, mapOf("miffId" to ROOM_1)),
                del("joinCodes/${room.code}"),
            )
        }

    @Test
    fun theCardOfACreator_isOnlyDeletedWithTheMiff() =
        runTest {
            ownedRoom()
            assertRefused(*ownerCardLot(), del("users/$me/memberships/free"))
        }

    @Test
    fun theLastLot_withARemovalInsideIt_isRefused() =
        runTest {
            val room = ownedRoom()
            assertRefused(
                del("miffs/$ROOM_1"),
                DocWrite("miffsDeleted/$ROOM_1", WriteKind.SET),
                del("joinCodes/${room.code}"),
                del("miffs/$ROOM_1/members/a"),
                *ownerCardLot(),
            )
        }

    @Test
    fun theLastLot_withoutTheTombstoneOrTheCode_isRefused() =
        runTest {
            val room = ownedRoom()
            assertRefused(del("miffs/$ROOM_1"), del("joinCodes/${room.code}"), *ownerCardLot())
            assertRefused(del("miffs/$ROOM_1"), DocWrite("miffsDeleted/$ROOM_1", WriteKind.SET), *ownerCardLot())
        }

    @Test
    fun anIndex_isDeletedOnlyOnceTheCardIsGone() =
        runTest {
            world.addRoom(ROOM_1, owner = "other", others = listOf(me))
            assertRefused(del("users/$me/memberships/free"))
        }

    @Test
    fun aCardThatDoesNotExist_cannotBeDeleted() =
        runTest {
            world.addRoom(ROOM_1, owner = "other", others = listOf(me))
            assertRefused(
                del("miffs/$ROOM_1/members/nobody"),
                del("miffs/$ROOM_1/status/nobody"),
            )
        }

    @Test
    fun aMemberCannotRemoveSomebodyElse_orDeleteAMiffThatIsNotTheirs() =
        runTest {
            val room = world.addRoom(ROOM_1, owner = "other", others = listOf(me, "x"))
            assertRefused(
                del("miffs/$ROOM_1/members/x"),
                del("miffs/$ROOM_1/status/x"),
                del("miffs/$ROOM_1/photos/x"),
                del("miffs/$ROOM_1/positions/x"),
            )
            assertRefused(
                del("miffs/$ROOM_1"),
                DocWrite("miffsDeleted/$ROOM_1", WriteKind.SET),
                del("joinCodes/${room.code}"),
            )
        }

    @Test
    fun aLeaveLot_withTheStatusLeftBehind_isRefused() =
        runTest {
            world.addRoom(ROOM_1, owner = "other", others = listOf(me))
            assertRefused(
                del("miffs/$ROOM_1/members/$me"),
                del("miffs/$ROOM_1/photos/$me"),
                del("miffs/$ROOM_1/positions/$me"),
                del("users/$me/memberships/free"),
            )
        }
}
