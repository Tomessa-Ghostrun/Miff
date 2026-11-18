package com.tomessa.miff.data.miff

import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.tomessa.miff.testsupport.FakeActiveDevice
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
 * The alert in the lots of the Miff screen (backend/data-model.md 2.18): once the function is on, stopping the sharing, leaving and removing a member delete the alert of THAT Miff, and
 * a build whose rules are not deployed yet still works (the lot is tried again without the alert deletion), so that no ordinary action ever breaks because of the function.
 */
class MiffRepositoryAlertTest {
    private val uid = "uid-owner"
    private val store = FakeMiffStore().also { it.miffs[MIFF_ID] = miffDetails(owner = uid) }
    private val gone = mutableListOf<String>()

    private fun repo(alerts: Boolean) =
        FirestoreMiffRepository(
            store = store,
            observer = FakeMiffObserver(),
            location = FakeLocationProbe(),
            device = FakeActiveDevice(),
            newMiffId = { MIFF_ID },
            timeoutMillis = 1_000,
            cache = FakeCachePurger(Timeline()),
            alertsEnabled = alerts,
            onAlertGone = { gone += it },
        )

    private fun paths(lot: List<com.tomessa.miff.domain.miff.DocWrite>) = lot.map { it.path }

    @Test
    fun functionOff_stoppingTheSharingNeverMentionsTheAlert_andTellsNobody() =
        runTest {
            repo(alerts = false).setSharing(MIFF_ID, uid, enabled = false)
            assertFalse(paths(store.commitsAsLots.single()).any { it.contains("alerts") })
            assertTrue(gone.isEmpty())
        }

    @Test
    fun functionOn_stoppingTheSharingDeletesTheAlert_andTheControllerIsTold() =
        runTest {
            val result = repo(alerts = true).setSharing(MIFF_ID, uid, enabled = false)
            assertEquals(MiffWriteResult.Done, result)
            assertTrue(paths(store.commitsAsLots.single()).contains("miffs/$MIFF_ID/alerts/$uid"))
            assertEquals(listOf(MIFF_ID), gone)
        }

    @Test
    fun functionOn_resumingTheSharingTouchesNoAlert() =
        runTest {
            repo(alerts = true).setSharing(MIFF_ID, uid, enabled = true)
            assertFalse(paths(store.commitsAsLots.single()).any { it.contains("alerts") })
            assertTrue(gone.isEmpty())
        }

    @Test
    fun rulesNotDeployedYet_theLotIsRefused_thenTriedAgainWithoutTheAlert_andSucceeds() =
        runTest {
            store.commitFailures += firestoreError(Code.PERMISSION_DENIED)
            val result = repo(alerts = true).setSharing(MIFF_ID, uid, enabled = false)
            assertEquals(MiffWriteResult.Done, result)
            // only the second lot went through, and it holds no alert deletion
            assertFalse(paths(store.commitsAsLots.single()).any { it.contains("alerts") })
        }

    @Test
    fun aLotRefusedTwice_isAFailure_asBefore() =
        runTest {
            store.commitFailures += firestoreError(Code.PERMISSION_DENIED)
            store.commitFailures += firestoreError(Code.PERMISSION_DENIED)
            val result = repo(alerts = true).setSharing(MIFF_ID, uid, enabled = false)
            assertTrue(result is MiffWriteResult.Failed)
            assertTrue(gone.isEmpty())
        }

    @Test
    fun leavingDeletesTheAlertOfThatMiff() =
        runTest {
            store.memberships =
                listOf(
                    com.tomessa.miff.domain.miff
                        .MembershipRef("free", MIFF_ID),
                )
            repo(alerts = true).leave(uid, MIFF_ID)
            assertTrue(paths(store.commitsAsLots.last()).contains("miffs/$MIFF_ID/alerts/$uid"))
            assertEquals(listOf(MIFF_ID), gone)
        }

    @Test
    fun removingAMemberDeletesHisAlertInTheRemovalLotItself_oneLotOfEight() =
        runTest {
            repo(alerts = true).removeMember(MIFF_ID, "uid-2")
            assertEquals(1, store.commitsAsLots.size)
            assertEquals(8, store.commitsAsLots.single().size)
            assertTrue(paths(store.commitsAsLots.single()).contains("miffs/$MIFF_ID/alerts/uid-2"))
        }

    @Test
    fun removingAMemberWithTheFunctionOffIsOneLotOfSevenAsBefore() =
        runTest {
            repo(alerts = false).removeMember(MIFF_ID, "uid-2")
            assertEquals(7, store.commitsAsLots.single().size)
        }

    @Test
    fun rulesNotDeployedYet_theRemovalIsRefused_thenTriedAgainWithoutTheAlert() =
        runTest {
            store.commitFailures += firestoreError(Code.PERMISSION_DENIED)
            val result = repo(alerts = true).removeMember(MIFF_ID, "uid-2")
            assertTrue(result is CodeChangeResult.Changed)
            assertEquals(7, store.commitsAsLots.single().size)
        }
}
