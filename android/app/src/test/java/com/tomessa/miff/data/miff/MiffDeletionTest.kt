package com.tomessa.miff.data.miff

import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.tomessa.miff.domain.miff.JoinCode
import com.tomessa.miff.domain.miff.MembershipRef
import com.tomessa.miff.domain.miff.MiffFailure
import com.tomessa.miff.domain.miff.WriteKind
import com.tomessa.miff.testsupport.FakeCachePurger
import com.tomessa.miff.testsupport.FakeLocationProbe
import com.tomessa.miff.testsupport.FakeMiffObserver
import com.tomessa.miff.testsupport.FakeMiffStore
import com.tomessa.miff.testsupport.MIFF_ID
import com.tomessa.miff.testsupport.Timeline
import com.tomessa.miff.testsupport.firestoreError
import com.tomessa.miff.testsupport.miffDetails
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The deletion of a Miff with no screen (ticket S2, S4, S7, S8): the order, and above all that it can be stopped at ANY point and
 * run again until it is done, without a double write and without a Miff left half emptied. The double simulates what Firestore
 * would hold after each lot ([FakeMiffStore.simulate]).
 */
class MiffDeletionTest {
    private val uid = "uid-owner"
    private var store = FakeMiffStore()
    private var codeCounter = 0

    /** A different valid code at every draw (never an I, L, O, 0 or 1). */
    private fun nextCode(): String {
        val n = ++codeCounter
        return "KKKKKKKKKK" + JoinCode.ALPHABET[n / JoinCode.ALPHABET.length % JoinCode.ALPHABET.length] +
            JoinCode.ALPHABET[n % JoinCode.ALPHABET.length]
    }

    private fun world(
        others: List<String> = listOf("uid-a", "uid-b"),
        places: List<String> = listOf("p1", "p2", "p3", "p4", "p5", "p6", "p7"),
    ) {
        store.simulate = true
        store.miffs[MIFF_ID] = miffDetails(owner = uid)
        store.memberIds = (listOf(uid) + others).toMutableList()
        store.placeIds = places.toMutableList()
        store.memberships = listOf(MembershipRef("free", MIFF_ID))
    }

    private fun repository() =
        FirestoreMiffRepository(
            store = store,
            observer = FakeMiffObserver(),
            location = FakeLocationProbe(),
            newMiffId = { MIFF_ID },
            newCode = ::nextCode,
            timeoutMillis = 1_000,
            cache = FakeCachePurger(Timeline()),
        )

    private fun deletion() = MiffDeletion(store, repository(), timeoutMillis = 1_000)

    private val lotSizes get() = store.commitsAsLots.map { it.size }

    @Test
    fun theWholeDeletion_isRemovals_thenPlaces_thenOneLastLot_inThatOrder() =
        runTest {
            world()
            assertEquals(MiffDeletionResult.Deleted, deletion().run(uid, MIFF_ID))
            // two removals of 6, places 5 + 2, then the last lot of 7
            assertEquals(listOf(6, 6, 5, 2, 7), lotSizes)
            val removed = store.commitsAsLots.take(2).map { it.first().path }
            assertEquals(listOf("miffs/$MIFF_ID/members/uid-a", "miffs/$MIFF_ID/members/uid-b"), removed)
            val last = store.commitsAsLots.last()
            assertEquals("miffs/$MIFF_ID", last.first().path)
            assertTrue(last.any { it.path == "miffsDeleted/$MIFF_ID" && it.kind == WriteKind.SET })
            assertTrue(store.memberships.isEmpty())
            assertTrue(store.placeIds.isEmpty())
        }

    @Test
    fun everyRemoval_renewsTheCode_andTheLastLotDeletesTheCodeThatIsCurrentThen() =
        runTest {
            world()
            deletion().run(uid, MIFF_ID)
            val renewed = store.commitsAsLots.take(2).map { lot -> lot[3].data["joinCode"] as String }
            assertEquals(2, renewed.toSet().size)
            val last = store.commitsAsLots.last()
            assertEquals("joinCodes/${renewed.last()}", last.first { it.path.startsWith("joinCodes/") }.path)
        }

    @Test
    fun aMiffAlone_hasNoRemoval_andNoPlaces_justTheLastLot() =
        runTest {
            world(others = emptyList(), places = emptyList())
            assertEquals(MiffDeletionResult.Deleted, deletion().run(uid, MIFF_ID))
            assertEquals(listOf(7), lotSizes)
        }

    @Test
    fun stoppedBeforeAnyLot_anywhereInTheSequence_thenRunAgain_endsDeleted_withoutADoubleWrite() =
        runTest {
            // The lots in order: 0 and 1 removals, 2 and 3 places, 4 the last lot. The network is cut before each of them in turn.
            for (cut in 0..4) {
                store = FakeMiffStore()
                world()
                val failures = ArrayDeque<Exception?>()
                repeat(cut) { failures.addLast(null) }
                failures.addLast(firestoreError(Code.UNAVAILABLE))
                store.commitFailures = failures
                val first = deletion().run(uid, MIFF_ID)
                assertEquals("cut before lot $cut", MiffDeletionResult.Paused(MiffFailure.OFFLINE), first)
                assertEquals("only what the server confirmed is recorded", cut, store.commitsAsLots.size)
                // Run again (the next launch): it carries on.
                assertEquals("run again after $cut", MiffDeletionResult.Deleted, deletion().run(uid, MIFF_ID))
                val paths = store.commitsAsLots.flatten().map { it.path }
                assertEquals("each member removed once", 1, paths.count { it == "miffs/$MIFF_ID/members/uid-a" })
                assertEquals("each member removed once", 1, paths.count { it == "miffs/$MIFF_ID/members/uid-b" })
                assertEquals("each place deleted once", 7, paths.count { it.contains("/places/") })
                assertEquals(
                    "the Miff is deleted once",
                    1,
                    store.commitsAsLots.count {
                        it.any { w ->
                            w.path ==
                                "miffsDeleted/$MIFF_ID"
                        }
                    },
                )
                assertTrue(store.memberships.isEmpty())
            }
        }

    @Test
    fun theAnswerOfAnyLotIsLost_afterTheLotLeft_theNextRunFinishes_withoutRedoingIt() =
        runTest {
            for (lost in 0..4) {
                store = FakeMiffStore()
                world()
                store.loseAnswerAt = lost
                val first = deletion().run(uid, MIFF_ID)
                if (lost == 4) {
                    // The last lot left: the Miff now refuses its former creator, and the server answers: it is deleted.
                    assertEquals("answer lost on the last lot", MiffDeletionResult.Deleted, first)
                } else {
                    // A removal whose answer was lost is recognised by the new code; a places lot is read again from the server.
                    assertTrue(
                        "answer lost on lot $lost: $first",
                        first == MiffDeletionResult.Deleted || first is MiffDeletionResult.Paused,
                    )
                    store.loseAnswerAt = -1
                    assertEquals(MiffDeletionResult.Deleted, deletion().run(uid, MIFF_ID))
                }
                val paths = store.commitsAsLots.flatten().map { it.path }
                assertEquals(
                    "each place deleted once (answer lost on lot $lost)",
                    7,
                    paths.count { it.contains("/places/") },
                )
                assertEquals(1, store.commitsAsLots.count { it.any { w -> w.path == "miffsDeleted/$MIFF_ID" } })
            }
        }

    @Test
    fun whenTheMiffIsAlreadyGone_butTheIndexRemains_itIsDropped_andTheResultIsDeleted() =
        runTest {
            world()
            store.miffs[MIFF_ID] = firestoreError(Code.PERMISSION_DENIED)
            assertEquals(MiffDeletionResult.Deleted, deletion().run(uid, MIFF_ID))
            assertEquals(
                "users/$uid/memberships/free",
                store.commitsAsLots
                    .single()
                    .single()
                    .path,
            )
        }

    @Test
    fun whenTheMiffIsRefused_butTheServerCannotBeTrusted_nothingIsConcluded() =
        runTest {
            world()
            store.miffs[MIFF_ID] = firestoreError(Code.PERMISSION_DENIED)
            // A refusal of App Check refuses everything: the check of the server fails too, so nobody is told "deleted".
            store.reachable = false
            val result = deletion().run(uid, MIFF_ID)
            assertTrue("$result", result is MiffDeletionResult.Paused)
            assertTrue(store.commitsAsLots.isEmpty())
        }

    @Test
    fun aPersonWhoIsNotTheCreator_deletesNothing() =
        runTest {
            world()
            store.miffs[MIFF_ID] = miffDetails(owner = "someone-else")
            assertEquals(MiffDeletionResult.NotOwner, deletion().run(uid, MIFF_ID))
            assertTrue(store.commitsAsLots.isEmpty())
        }

    @Test
    fun noNetworkAtTheStart_pausesWithoutWritingAnything() =
        runTest {
            world()
            store.miffs[MIFF_ID] = firestoreError(Code.UNAVAILABLE)
            assertEquals(MiffDeletionResult.Paused(MiffFailure.OFFLINE), deletion().run(uid, MIFF_ID))
            assertTrue(store.commitsAsLots.isEmpty())
        }

    @Test
    fun theMembersAreListedFromTheServer_andTheListCanFail() =
        runTest {
            world()
            store.membersFailure = firestoreError(Code.UNAVAILABLE)
            assertEquals(MiffDeletionResult.Paused(MiffFailure.OFFLINE), deletion().run(uid, MIFF_ID))
            assertTrue(store.commitsAsLots.isEmpty())
        }

    @Test
    fun someoneWhoJoinsDuringTheDeletion_isRemovedToo_beforeTheLastLot() =
        runTest {
            world(others = listOf("uid-a"))
            var added = false
            store.onCommit = {
                if (!added) {
                    added = true
                    store.memberIds.add("uid-late")
                }
            }
            assertEquals(MiffDeletionResult.Deleted, deletion().run(uid, MIFF_ID))
            val removed =
                store.commitsAsLots
                    .flatten()
                    .filter {
                        it.path.contains("/members/")
                    }.map { it.path.substringAfterLast('/') }
            assertTrue("uid-late" in removed)
            assertEquals(uid, removed.last())
        }

    @Test
    fun aMiffThatKeepsFillingUp_stopsAfterAFewPasses_insteadOfRunningForEver() =
        runTest {
            world(others = listOf("uid-a"))
            var n = 0
            store.onCommit = { store.memberIds.add("uid-late-${n++}") }
            val result = deletion().run(uid, MIFF_ID)
            assertEquals(MiffDeletionResult.Paused(MiffFailure.UNKNOWN), result)
            assertFalse(store.commitsAsLots.any { it.any { w -> w.path == "miffsDeleted/$MIFF_ID" } })
        }

    @Test
    fun theLastLot_leavesOutTheCodeDocumentThatIsAlreadyMissing_andAnIndexThatWasNotFound() =
        runTest {
            world(others = emptyList(), places = emptyList())
            store.codeDocumentExists = false
            store.memberships = emptyList()
            assertEquals(MiffDeletionResult.Deleted, deletion().run(uid, MIFF_ID))
            val last = store.commitsAsLots.single()
            assertEquals(5, last.size)
            assertFalse(last.any { it.path.startsWith("joinCodes/") || it.path.startsWith("users/") })
        }

    @Test
    fun aPremiumIndexKeyedByTheMiffId_isTheOneThatIsDeleted() =
        runTest {
            world(others = emptyList(), places = emptyList())
            store.memberships =
                listOf(MembershipRef("ZyXwVuTsRqPoNmLkJiHg", "ZyXwVuTsRqPoNmLkJiHg"), MembershipRef(MIFF_ID, MIFF_ID))
            deletion().run(uid, MIFF_ID)
            assertEquals(
                "users/$uid/memberships/$MIFF_ID",
                store.commitsAsLots
                    .single()
                    .last()
                    .path,
            )
        }

    @Test
    fun theCodesDrawnForTheRemovals_areNeverTheOldOnes() =
        runTest {
            world()
            deletion().run(uid, MIFF_ID)
            val used = store.commitsAsLots.take(2).map { it[3].data["joinCode"] as String }
            assertNotEquals(used[0], used[1])
            assertTrue(used.all { it.length == 12 })
        }
}
