package com.tomessa.miff.data.miff

import com.google.firebase.firestore.FirebaseFirestoreException
import com.tomessa.miff.domain.miff.MembershipRef
import com.tomessa.miff.domain.miff.MiffFailure
import com.tomessa.miff.domain.miff.MiffSharingConsent
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
            member(MIFF_ID, version = MiffSharingConsent.VERSION)
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
            member(MIFF_ID)
            member(OTHER_MIFF_ID)
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
            assertEquals("2026-11-v04", sharing["consentVersion"])
            assertEquals(MiffSharingConsent.VERSION, sharing["consentVersion"])
        }

    @Test
    fun renew_readsTheCardInTheTransaction_andNeverTurnsAStoppedSharingBackOn() =
        runTest {
            member(MIFF_ID)
            // Stopped meanwhile (another phone): nothing is written for it, the other Miff is still renewed.
            member(OTHER_MIFF_ID, enabled = false)
            assertEquals(MiffWriteResult.Done, renewals.renew(uid, listOf(MIFF_ID, OTHER_MIFF_ID)))
            assertEquals(listOf("miffs/$MIFF_ID/members/$uid"), store.commitsAsLots.single().map { it.path })
            assertTrue("commitWhereSharing" in store.calls)
            // Nobody shares any more: nothing is written at all.
            store.commitsAsLots.clear()
            member(MIFF_ID, enabled = false)
            assertEquals(MiffWriteResult.Done, renewals.renew(uid, listOf(MIFF_ID)))
            assertTrue(store.commitsAsLots.isEmpty())
        }

    @Test
    fun theCreatorIsToldApart_fromAMember() =
        runTest {
            store.memberships = listOf(MembershipRef("free", MIFF_ID))
            store.miffs[MIFF_ID] = miffDetails(name = "Chez nous", owner = uid)
            member(MIFF_ID)
            assertTrue((renewals.check(uid) as ConsentRenewalCheck.Due).miffs.single().isOwner)
            store.miffs[MIFF_ID] = miffDetails(name = "Chez nous", owner = "someone-else")
            assertFalse((renewals.check(uid) as ConsentRenewalCheck.Due).miffs.single().isOwner)
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
            member(MIFF_ID, version = MiffSharingConsent.VERSION)
            store.commitFailures.add(firestoreError(FirebaseFirestoreException.Code.PERMISSION_DENIED))
            assertEquals(MiffWriteResult.Done, renewals.renew(uid, listOf(MIFF_ID)))
        }

    // --- CONS-4-A: the three populations ------------------------------------------------------------------------------

    @Test
    fun anAccountUnderTheVersion1Or2Text_isDue_blocking_andNotClarified() =
        runTest {
            store.memberships = listOf(MembershipRef("a", MIFF_ID), MembershipRef("b", OTHER_MIFF_ID))
            store.miffs[MIFF_ID] = miffDetails(name = "Chez nous")
            store.miffs[OTHER_MIFF_ID] = miffDetails(name = "Amis")
            member(MIFF_ID, version = "2026-10-v1")
            member(OTHER_MIFF_ID, version = "2026-10-v2")
            val check = renewals.check(uid) as ConsentRenewalCheck.Due
            assertEquals(setOf(MIFF_ID, OTHER_MIFF_ID), check.miffs.map { it.miffId }.toSet())
            assertTrue(
                "nothing is merely clarified for a text that does not cover the location",
                check.clarify.isEmpty(),
            )
        }

    @Test
    fun anAccountUnderTheVersion3Text_isOnlyClarified_nothingIsDueNorPaused() =
        runTest {
            store.memberships = listOf(MembershipRef("free", MIFF_ID))
            store.miffs[MIFF_ID] = miffDetails(name = "Chez nous")
            member(MIFF_ID, version = "2026-11-v03")
            val check = renewals.check(uid) as ConsentRenewalCheck.Due
            assertTrue("a version 3 account is never listed as blocking (no pill 'En attente')", check.miffs.isEmpty())
            assertEquals(listOf(MIFF_ID), check.clarify.map { it.miffId })
            assertEquals("Chez nous", check.clarify.single().name)
        }

    @Test
    fun aNewAccount_underTheCurrentText_hasNothingToRenewNorClarify() =
        runTest {
            store.memberships = listOf(MembershipRef("free", MIFF_ID))
            member(MIFF_ID, version = MiffSharingConsent.VERSION)
            assertEquals(ConsentRenewalCheck.NothingToRenew, renewals.check(uid))
        }

    @Test
    fun aMixOfPopulations_putsEachMiffInOneListOnly() =
        runTest {
            store.memberships = listOf(MembershipRef("a", MIFF_ID), MembershipRef("b", OTHER_MIFF_ID))
            member(MIFF_ID, version = "2026-10-v2")
            member(OTHER_MIFF_ID, version = "2026-11-v03")
            val check = renewals.check(uid) as ConsentRenewalCheck.Due
            assertEquals(listOf(MIFF_ID), check.miffs.map { it.miffId })
            assertEquals(listOf(OTHER_MIFF_ID), check.clarify.map { it.miffId })
        }

    @Test
    fun theCalmCardsAgreement_isTheSameRenewalWrite_toTheCurrentVersion_andNothingElse() =
        runTest {
            member(MIFF_ID, version = "2026-11-v03")
            assertEquals(MiffWriteResult.Done, renewals.renew(uid, listOf(MIFF_ID)))
            val lot = store.commitsAsLots.single()
            assertEquals(listOf("miffs/$MIFF_ID/members/$uid"), lot.map { it.path })
            assertEquals(WriteKind.UPDATE, lot.single().kind)
            @Suppress("UNCHECKED_CAST")
            val sharing = lot.single().data["sharing"] as Map<String, Any?>
            // The four keys of the rules, sharing stays on: no pause, no stop, no deletion.
            assertEquals(setOf("enabled", "consentedAt", "consentVersion", "revokedAt"), sharing.keys)
            assertEquals(true, sharing["enabled"])
            assertEquals("2026-11-v04", sharing["consentVersion"])
        }

    @Test
    fun aVersion3AgreementThatFails_staysClarifiable_andIsNotCountedAsDone() =
        runTest {
            store.memberships = listOf(MembershipRef("free", MIFF_ID))
            member(MIFF_ID, version = "2026-11-v03")
            store.commitFailures.add(firestoreError(FirebaseFirestoreException.Code.UNAVAILABLE))
            assertTrue(renewals.renew(uid, listOf(MIFF_ID)) is MiffWriteResult.Failed)
            assertTrue(renewals.check(uid) is ConsentRenewalCheck.Due)
        }

    @Test
    fun theNamesAreNeverInTheText() {
        assertFalse(RenewalMiff(MIFF_ID, "Chez nous").toString().contains("Chez nous"))
        assertNotNull(ConsentRenewalCheck.Due(emptyList()).toString())
        assertFalse(ConsentRenewalCheck.Due(listOf(RenewalMiff(MIFF_ID, "Chez nous"))).toString().contains("Chez nous"))
        assertFalse(
            ConsentRenewalCheck
                .Due(emptyList(), listOf(RenewalMiff(MIFF_ID, "Chez nous")))
                .toString()
                .contains("Chez nous"),
        )
    }
}
