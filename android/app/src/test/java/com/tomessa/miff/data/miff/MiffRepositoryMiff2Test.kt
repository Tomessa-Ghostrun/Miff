package com.tomessa.miff.data.miff

import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.tomessa.miff.domain.miff.JoinCode
import com.tomessa.miff.domain.miff.MembershipRef
import com.tomessa.miff.domain.miff.MiffFailure
import com.tomessa.miff.domain.miff.WriteKind
import com.tomessa.miff.testsupport.CODE
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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MIFF-2 in the repository (tickets N1, R1 to R7, Q1 to Q4) with Firestore replaced by a double: one lot per action, always
 * confirmed by the server, a new code that is valid and different, a lost answer checked before it is called a failure.
 */
class MiffRepositoryMiff2Test {
    private val uid = "uid-owner"
    private val newCode = "BCDFGHK23456"
    private val store = FakeMiffStore().also { it.miffs[MIFF_ID] = miffDetails(owner = uid) }
    private val drawn = ArrayDeque<String>()

    private fun repo() =
        FirestoreMiffRepository(
            store = store,
            observer = FakeMiffObserver(),
            location = FakeLocationProbe(),
            newMiffId = { MIFF_ID },
            newCode = { drawn.removeFirstOrNull() ?: newCode },
            timeoutMillis = 1_000,
            cache = FakeCachePurger(Timeline()),
        )

    @Test
    fun removeMember_isOneLotOfSix_readFromTheMiffInsideTheTransaction_andReturnsTheNewCode() =
        runTest {
            val result = repo().removeMember(MIFF_ID, "uid-2") as CodeChangeResult.Changed
            assertEquals(newCode, result.newCode)
            assertEquals(1, store.commitsAsLots.size)
            val lot = store.commitsAsLots.single()
            assertEquals(6, lot.size)
            // The code to replace is the one the transaction read, not one held by the screen.
            assertEquals("joinCodes/$CODE", lot.last().path)
            assertEquals(WriteKind.DELETE, lot.last().kind)
            assertEquals("joinCodes/$newCode", lot[4].path)
            assertEquals("commitWithMiff", store.calls.last())
        }

    @Test
    fun removeMember_theNewCodeIsValid_freeAndNeverTheOldOne() =
        runTest {
            store.codes["AAAAAAAAAAAA"] = mapOf("miffId" to "x", "miffName" to "y")
            drawn += "AAAAAAAAAAAA" // taken by somebody else: drawn again
            drawn += "CCCCCCCCCCCC"
            val result = repo().removeMember(MIFF_ID, "uid-2") as CodeChangeResult.Changed
            assertEquals("CCCCCCCCCCCC", result.newCode)
            assertTrue(JoinCode.isValid(result.newCode))
            assertTrue("a read of the code before it is used", store.calls.count { it == "code" } == 2)
        }

    @Test
    fun removeMember_offline_isAFailure_nothingIsWritten_andTheSentenceIsTheOfflineOne() =
        runTest {
            store.commitFailures = ArrayDeque(listOf(firestoreError(Code.UNAVAILABLE)))
            // The check afterwards cannot reach the server either.
            store.miffs[MIFF_ID] = firestoreError(Code.UNAVAILABLE)
            val result = repo().removeMember(MIFF_ID, "uid-2")
            assertEquals(CodeChangeResult.Failed(MiffFailure.OFFLINE), result)
            assertTrue(store.commitsAsLots.isEmpty())
        }

    @Test
    fun removeMember_aLotWhoseAnswerWasLost_isCheckedOnTheServer_andNotShownAsAFailure() =
        runTest {
            // The lot left (the Miff now holds the new code) but the answer never came back.
            store.onCommit = { store.miffs[MIFF_ID] = miffDetails(owner = uid, code = newCode) }
            store.commitFailures = ArrayDeque(listOf(firestoreError(Code.DEADLINE_EXCEEDED)))
            val result = repo().removeMember(MIFF_ID, "uid-2")
            assertEquals(newCode, (result as CodeChangeResult.Changed).newCode)
        }

    @Test
    fun removeMember_aRefusalOfTheRules_isTheGenericSentence() =
        runTest {
            store.commitFailures = ArrayDeque(listOf(firestoreError(Code.PERMISSION_DENIED)))
            assertEquals(CodeChangeResult.Failed(MiffFailure.REFUSED), repo().removeMember(MIFF_ID, "uid-2"))
        }

    @Test
    fun removeMember_aMiffThatNoLongerExists_isAFailure_notACrash() =
        runTest {
            store.miffs.remove(MIFF_ID)
            val result = repo().removeMember(MIFF_ID, "uid-2")
            assertTrue(result is CodeChangeResult.Failed)
            assertTrue(store.commitsAsLots.isEmpty())
        }

    @Test
    fun removeMember_whenTheOldCodeDocumentIsMissing_theLotHasFiveWrites() =
        runTest {
            store.codeDocumentExists = false
            repo().removeMember(MIFF_ID, "uid-2")
            assertEquals(5, store.commitsAsLots.single().size)
        }

    @Test
    fun renewCode_isOneLotOfThree_andReturnsTheNewCode() =
        runTest {
            val result = repo().renewCode(MIFF_ID) as CodeChangeResult.Changed
            assertEquals(newCode, result.newCode)
            assertEquals(3, store.commitsAsLots.single().size)
            assertFalse("renewing writes no member", store.commitsAsLots.single().any { it.path.contains("/members/") })
        }

    @Test
    fun renewCode_threeCollisionsInARow_isAFailure_withoutAnyLot() =
        runTest {
            store.codes["AAAAAAAAAAAA"] = mapOf("miffId" to "x", "miffName" to "y")
            repeat(3) { drawn += "AAAAAAAAAAAA" }
            val result = repo().renewCode(MIFF_ID)
            assertEquals(CodeChangeResult.Failed(MiffFailure.UNKNOWN), result)
            assertTrue(store.commitsAsLots.isEmpty())
        }

    @Test
    fun leave_findsTheIndexByItsField_andWritesTheLotOfFour() =
        runTest {
            store.miffs[MIFF_ID] = miffDetails(owner = "someone-else")
            // The id of the document is "free": the Miff is found by the FIELD miffId (criterion R11).
            store.memberships = listOf(MembershipRef("free", MIFF_ID))
            assertEquals(MiffWriteResult.Done, repo().leave(uid, MIFF_ID))
            val lot = store.commitsAsLots.single()
            assertEquals(4, lot.size)
            assertEquals("users/$uid/memberships/free", lot.last().path)
            assertTrue(lot.all { it.kind == WriteKind.DELETE })
        }

    @Test
    fun leave_neverRenewsTheCode_decisionOfThePm_2026_10_31() =
        runTest {
            // A voluntary departure touches the person's own documents only: neither the Miff (its code), nor any code document.
            store.miffs[MIFF_ID] = miffDetails(owner = "someone-else")
            store.memberships = listOf(MembershipRef("free", MIFF_ID))
            assertEquals(MiffWriteResult.Done, repo().leave(uid, MIFF_ID))
            val paths = store.commitsAsLots.flatten().map { it.path }
            assertTrue(paths.none { it == "miffs/$MIFF_ID" || it.startsWith("joinCodes") })
            assertTrue(paths.all { it.contains("/$uid") })
        }

    @Test
    fun leave_withAPremiumIndexKeyedByTheMiffId_deletesThatOne() =
        runTest {
            store.memberships = listOf(MembershipRef(MIFF_ID, MIFF_ID), MembershipRef("free", "ZyXwVuTsRqPoNmLkJiHg"))
            repo().leave(uid, MIFF_ID)
            assertEquals(
                "users/$uid/memberships/$MIFF_ID",
                store.commitsAsLots
                    .single()
                    .last()
                    .path,
            )
        }

    @Test
    fun leave_offline_writesNothing_andIsNotConfirmed() =
        runTest {
            store.membershipsFailure = firestoreError(Code.UNAVAILABLE)
            assertEquals(MiffWriteResult.Failed(MiffFailure.OFFLINE), repo().leave(uid, MIFF_ID))
            assertTrue(store.commitsAsLots.isEmpty())
        }

    @Test
    fun leave_aLotWhoseAnswerWasLost_isDone_onceTheServerRefusesTheMiff() =
        runTest {
            store.memberships = listOf(MembershipRef("free", MIFF_ID))
            store.commitFailures = ArrayDeque(listOf(firestoreError(Code.DEADLINE_EXCEEDED)))
            // After the lot the server no longer lets the person read the Miff: it did leave.
            store.onCommit = { store.miffs[MIFF_ID] = firestoreError(Code.PERMISSION_DENIED) }
            assertEquals(MiffWriteResult.Done, repo().leave(uid, MIFF_ID))
        }

    @Test
    fun leave_aLotWhoseAnswerWasLost_isAFailure_ifThePersonCanStillReadTheMiff() =
        runTest {
            store.memberships = listOf(MembershipRef("free", MIFF_ID))
            store.commitFailures = ArrayDeque(listOf(firestoreError(Code.UNAVAILABLE)))
            assertEquals(MiffWriteResult.Failed(MiffFailure.OFFLINE), repo().leave(uid, MIFF_ID))
        }

    @Test
    fun leave_theCreatorsRefusal_isTheGenericSentence() =
        runTest {
            store.memberships = listOf(MembershipRef("free", MIFF_ID))
            store.commitFailures = ArrayDeque(listOf(firestoreError(Code.PERMISSION_DENIED)))
            assertEquals(MiffWriteResult.Failed(MiffFailure.REFUSED), repo().leave(uid, MIFF_ID))
        }
}
