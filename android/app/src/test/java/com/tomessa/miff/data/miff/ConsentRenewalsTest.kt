package com.tomessa.miff.data.miff

import com.google.firebase.firestore.FirebaseFirestoreException
import com.tomessa.miff.domain.miff.MembershipRef
import com.tomessa.miff.domain.miff.MiffFailure
import com.tomessa.miff.domain.miff.WriteKind
import com.tomessa.miff.testsupport.FakeMiffStore
import com.tomessa.miff.testsupport.MIFF_ID
import com.tomessa.miff.testsupport.OTHER_MIFF_ID
import com.tomessa.miff.testsupport.firestoreError
import com.tomessa.miff.testsupport.miffDetails
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The renewal of the consent (LOC-2a, rule R-LIEU-3): what is looked at, and the one lot that is written after a "J'accepte". */
class ConsentRenewalsTest {
    private val store = FakeMiffStore()
    private val renewals = FirestoreConsentRenewals(store, timeoutMillis = 1_000)
    private val uid = "uid-1"

    private fun member(
        miffId: String,
        enabled: Boolean = true,
        version: String? = "2026-10-v2",
    ) {
        store.consents[miffId] = SharingConsentRead(enabled, version)
    }

    @Test
    fun anOldTextWhereThePersonShares_isDue_withTheNameOfTheMiff() =
        runTest {
            store.memberships = listOf(MembershipRef("free", MIFF_ID))
            store.miffs[MIFF_ID] = miffDetails(name = "Chez nous")
            member(MIFF_ID)
            val check = renewals.check(uid) as ConsentRenewalCheck.Due
            assertEquals(listOf(MIFF_ID), check.miffs.map { it.miffId })
            assertEquals("Chez nous", check.miffs.single().name)
        }

    @Test
    fun theCurrentText_aStoppedSharing_orNoCard_isNothingToRenew() =
        runTest {
            store.memberships = listOf(MembershipRef("a", MIFF_ID), MembershipRef("b", OTHER_MIFF_ID))
            member(MIFF_ID, version = "2026-11-v03")
            member(OTHER_MIFF_ID, enabled = false, version = "2026-10-v1")
            assertEquals(ConsentRenewalCheck.NothingToRenew, renewals.check(uid))
            store.consents.clear()
            assertEquals(ConsentRenewalCheck.NothingToRenew, renewals.check(uid))
        }

    @Test
    fun noNetwork_isUnknown_notAClaim() =
        runTest {
            store.memberships = listOf(MembershipRef("free", MIFF_ID))
            store.consents[MIFF_ID] = firestoreError(FirebaseFirestoreException.Code.UNAVAILABLE)
            assertEquals(ConsentRenewalCheck.Unknown, renewals.check(uid))
            store.membershipsFailure = firestoreError(FirebaseFirestoreException.Code.UNAVAILABLE)
            assertEquals(ConsentRenewalCheck.Unknown, renewals.check(uid))
        }

    @Test
    fun aRefusal_meansNotInThatMiffAnyMore_nothingToRenewThere() =
        runTest {
            store.memberships = listOf(MembershipRef("free", MIFF_ID))
            store.consents[MIFF_ID] = firestoreError(FirebaseFirestoreException.Code.PERMISSION_DENIED)
            assertEquals(ConsentRenewalCheck.NothingToRenew, renewals.check(uid))
        }

    @Test
    fun theCheckWritesNothing() =
        runTest {
            store.memberships = listOf(MembershipRef("free", MIFF_ID))
            member(MIFF_ID)
            renewals.check(uid)
            assertTrue(store.commitsAsLots.isEmpty())
        }

    @Test
    fun renew_isOneLot_ofOneUpdatePerMiff_toTheCurrentVersion() =
        runTest {
            val result = renewals.renew(uid, listOf(MIFF_ID, OTHER_MIFF_ID, MIFF_ID))
            assertEquals(MiffWriteResult.Done, result)
            val lot = store.commitsAsLots.single()
            assertEquals(
                listOf("miffs/$MIFF_ID/members/$uid", "miffs/$OTHER_MIFF_ID/members/$uid"),
                lot.map { it.path },
            )
            assertTrue(lot.all { it.kind == WriteKind.UPDATE })
            @Suppress("UNCHECKED_CAST")
            val sharing = lot.first().data["sharing"] as Map<String, Any?>
            assertEquals("2026-11-v03", sharing["consentVersion"])
        }

    @Test
    fun renew_refusesABadIdOrAnEmptyList_beforeAnythingIsWritten() =
        runTest {
            assertEquals(MiffWriteResult.Failed(MiffFailure.UNKNOWN), renewals.renew(uid, emptyList()))
            assertEquals(MiffWriteResult.Failed(MiffFailure.UNKNOWN), renewals.renew(uid, listOf("../x")))
            assertTrue(store.commitsAsLots.isEmpty())
        }

    @Test
    fun aFailedLot_isAFailure_writesNothing_andIsNeverRetriedByItself() =
        runTest {
            store.memberships = listOf(MembershipRef("free", MIFF_ID))
            member(MIFF_ID)
            store.commitFailures.add(firestoreError(FirebaseFirestoreException.Code.UNAVAILABLE))
            val result = renewals.renew(uid, listOf(MIFF_ID))
            assertTrue(result is MiffWriteResult.Failed)
            assertEquals(MiffFailure.OFFLINE, (result as MiffWriteResult.Failed).failure)
            assertTrue(store.commitsAsLots.isEmpty())
        }

    @Test
    fun aRefusalBecauseAnotherPhoneAlreadyRenewed_countsAsDone() =
        runTest {
            store.memberships = listOf(MembershipRef("free", MIFF_ID))
            // The card already carries the current text: the rules refuse a version that is not greater; the check sees nothing due.
            member(MIFF_ID, version = "2026-11-v03")
            store.commitFailures.add(firestoreError(FirebaseFirestoreException.Code.PERMISSION_DENIED))
            assertEquals(MiffWriteResult.Done, renewals.renew(uid, listOf(MIFF_ID)))
        }

    @Test
    fun theNamesAreNeverInTheText() {
        assertFalse(RenewalMiff(MIFF_ID, "Chez nous").toString().contains("Chez nous"))
        assertNotNull(ConsentRenewalCheck.Due(emptyList()).toString())
        assertFalse(ConsentRenewalCheck.Due(listOf(RenewalMiff(MIFF_ID, "Chez nous"))).toString().contains("Chez nous"))
    }
}
