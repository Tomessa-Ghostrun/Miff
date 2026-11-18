package com.tomessa.miff.data.place

import com.google.firebase.firestore.FirebaseFirestoreException
import com.tomessa.miff.data.miff.EntitlementRead
import com.tomessa.miff.domain.miff.MiffDetails
import com.tomessa.miff.domain.miff.WriteKind
import com.tomessa.miff.domain.place.DangerZoneRights
import com.tomessa.miff.domain.place.OwnedMiff
import com.tomessa.miff.domain.place.PaywallReason
import com.tomessa.miff.domain.place.Place
import com.tomessa.miff.domain.place.PlaceQuota
import com.tomessa.miff.domain.place.PlaceSlot
import com.tomessa.miff.domain.place.PlaceType
import com.tomessa.miff.testsupport.FakePlaceRepository
import com.tomessa.miff.testsupport.FakePlaceRequestStore
import com.tomessa.miff.testsupport.FakePlaceStore
import com.tomessa.miff.testsupport.MIFF_ID
import com.tomessa.miff.testsupport.firestoreError
import com.tomessa.miff.testsupport.testPlace
import com.tomessa.miff.testsupport.testRequest
import com.tomessa.miff.testsupport.testRequestDraft
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * THE MATRIX of the danger zone (type `risk`), decision of the PM of 2026-11-17 (ticket docs/tickets/lieu-danger-demande-gratuite.md), proved on the real code
 * paths (repository, quota, form rules), free and Miff+ side by side:
 *
 * | | ASK (member) | ACCEPT (creator) | DECLARE directly (creator) |
 * |---|---|---|---|
 * | free | yes | no: the offer, the request stays | no: the offer |
 * | Miff+ | yes | yes | yes |
 *
 * No Firestore: doubles.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DangerZoneMatrixTest {
    private val now = 10_000_000_000L
    private val requests = FakePlaceRequestStore()
    private val places = FakePlaceStore()
    private val placeRepository = FakePlaceRepository()
    private val repo =
        FirestorePlaceRequestRepository(requests, places, placeRepository, newPlaceId = {
            "NEWPLACE0000000001"
        }, nowMillis = { now })
    private val creator = "u1"
    private val member = "u2"

    init {
        places.miffs[MIFF_ID] = MiffDetails(MIFF_ID, "Famille", "XTZRSP367988", ownerUid = creator)
    }

    private fun creatorContext(
        premium: Boolean,
        existing: List<Place> = emptyList(),
    ) {
        placeRepository.context =
            PlaceContext(premium, listOf(OwnedMiff(MIFF_ID, "Famille", 100L)), MIFF_ID, existing)
    }

    private val danger = testRequest(uid = member, slot = 1, type = PlaceType.RISK, createdAtMillis = now)

    // --- ASK: a member, free or Miff+, always -----------------------------------------------------------------------------------------

    @Test
    fun ask_aFreeMember_andAMiffPlusMember_bothSendTheSameLot_withoutTheirPlanBeingRead() =
        runTest {
            for (memberIsPremium in listOf(false, true)) {
                requests.commits.clear()
                // Whatever the plan is, it is not read: a failing read of the entitlement proves it.
                places.entitlementFailure = firestoreError(FirebaseFirestoreException.Code.UNAVAILABLE)
                places.entitlement =
                    if (memberIsPremium) EntitlementRead(active = true, expiresAtMillis = now + 1_000_000) else null
                val loaded = (repo.prepare(member, MIFF_ID) as RequestContextLoad.Loaded).context
                val result =
                    repo.submit(
                        member,
                        MIFF_ID,
                        testRequestDraft(type = PlaceType.RISK),
                        loaded,
                        online = true,
                    )
                assertTrue("memberIsPremium=$memberIsPremium", result is RequestSubmitResult.Done)
                val lot = requests.commits.single()
                assertEquals(listOf(WriteKind.SET, WriteKind.MERGE), lot.map { it.kind })
                assertEquals("risk", lot[0].data["type"])
            }
        }

    @Test
    fun ask_theRightsSayYesToEveryone_andTheFormOfARequestNeverCarriesTheLock() {
        assertTrue(DangerZoneRights.canAsk())
        for (premium in listOf(false, true)) {
            assertFalse(
                DangerZoneRights.isLockedInForm(asRequest = true, premium = premium, current = PlaceType.TRUSTED),
            )
            assertNull(PlaceQuota.forTypeChange(premium, PlaceType.TRUSTED, PlaceType.RISK, asRequest = true))
        }
    }

    // --- ACCEPT: the creator, Miff+ only ---------------------------------------------------------------------------------------------

    @Test
    fun accept_aFreeCreator_getsTheOffer_nothingIsWritten_theRequestStays_andCanStillBeDeclined() =
        runTest {
            creatorContext(premium = false)
            requests.all = listOf(danger)
            val result = repo.accept(creator, danger, knownCount = 1)
            assertEquals(PaywallReason.DANGER, (result as RequestAcceptResult.NeedsPremium).reason)
            assertTrue(requests.commits.isEmpty())
            assertEquals(listOf(danger.id), requests.all.map { it.id })
            // "Refuser" stays possible and never reads the plan.
            val calls = placeRepository.prepareCalls
            assertTrue(repo.decline(danger, knownCount = 1) is RequestDeclineResult.Done)
            assertEquals(calls, placeRepository.prepareCalls)
        }

    @Test
    fun accept_aMiffPlusCreator_createsTheDangerZone_inOneLot_withTheRequestDeletedAndTheCounterDown() =
        runTest {
            creatorContext(premium = true)
            requests.all = listOf(danger)
            val result = repo.accept(creator, danger, knownCount = 2)
            assertEquals("NEWPLACE0000000001", (result as RequestAcceptResult.Done).placeId)
            val lot = requests.commits.single()
            assertEquals(listOf(WriteKind.SET, WriteKind.DELETE, WriteKind.SET), lot.map { it.kind })
            assertEquals("risk", lot[0].data["type"])
            assertEquals(1, lot[2].data["pending"])
        }

    @Test
    fun accept_theRightsFollowThePlanOfTheCreatorOnly() {
        assertFalse(DangerZoneRights.canAccept(creatorPremium = false))
        assertTrue(DangerZoneRights.canAccept(creatorPremium = true))
    }

    // --- Edge: the creator loses (or gains) Miff+ while requests are pending ---------------------------------------------------------------

    @Test
    fun edge_theCreatorLosesMiffPlus_withPendingDangerRequests_theyStayPending_declineWorks_acceptOpensTheOffer() =
        runTest {
            val second = testRequest(uid = member, slot = 2, type = PlaceType.RISK, createdAtMillis = now)
            requests.all = listOf(danger, second)
            creatorContext(premium = true)
            // While Miff+, one is accepted.
            assertTrue(repo.accept(creator, danger, knownCount = 2) is RequestAcceptResult.Done)
            requests.commits.clear()
            // The plan ends: the other one is NOT deleted, nothing is written, the offer opens.
            creatorContext(premium = false)
            val afterLoss = repo.accept(creator, second, knownCount = 1)
            assertEquals(PaywallReason.DANGER, (afterLoss as RequestAcceptResult.NeedsPremium).reason)
            assertTrue(requests.commits.isEmpty())
            // It can be declined at any time.
            assertTrue(repo.decline(second, knownCount = 1) is RequestDeclineResult.Done)
        }

    @Test
    fun edge_theCreatorBecomesMiffPlus_thePendingRequestIsAcceptedWithoutBeingAskedAgain() =
        runTest {
            requests.all = listOf(danger)
            creatorContext(premium = false)
            assertTrue(repo.accept(creator, danger, knownCount = 1) is RequestAcceptResult.NeedsPremium)
            // Subscribed meanwhile: the same request, still pending, goes through.
            creatorContext(premium = true)
            assertTrue(repo.accept(creator, danger, knownCount = 1) is RequestAcceptResult.Done)
            assertEquals("risk", requests.commits.single()[0].data["type"])
        }

    @Test
    fun edge_aFreeCreatorWhoAlreadyOwnsADangerZone_cannotAcceptAnother_butATrustedOneWithRoomGoesThrough() =
        runTest {
            // A place made while he was Miff+ stays (the rules do not remove it); it counts among the 3 places of the free plan.
            creatorContext(premium = false, existing = listOf(testPlace("p1", "Ancien", type = PlaceType.RISK)))
            requests.all = listOf(danger)
            val refused = repo.accept(creator, danger, knownCount = 1)
            assertEquals(PaywallReason.DANGER, (refused as RequestAcceptResult.NeedsPremium).reason)
            assertTrue(requests.commits.isEmpty())
            val trusted = testRequest(uid = member, slot = 2, type = PlaceType.TRUSTED, createdAtMillis = now)
            requests.all = listOf(trusted)
            val accepted = repo.accept(creator, trusted, knownCount = 1)
            assertEquals("p2", (accepted as RequestAcceptResult.Done).placeId)
        }

    @Test
    fun edge_theDangerRequestsCountInTheSameQuotasAsOthers_threeAtMostPerMember() =
        runTest {
            requests.mine =
                (1..3).map { testRequest(uid = member, slot = it, type = PlaceType.RISK, createdAtMillis = now) }
            val loaded = (repo.prepare(member, MIFF_ID) as RequestContextLoad.Loaded).context
            requests.commits.clear()
            val result = repo.submit(member, MIFF_ID, testRequestDraft(type = PlaceType.RISK), loaded, online = true)
            assertTrue(result is RequestSubmitResult.MemberLimit)
            assertTrue(requests.commits.isEmpty())
        }

    // --- DECLARE: the creator, directly, Miff+ only ----------------------------------------------------------------------------------------

    @Test
    fun declare_aFreeCreator_getsTheOffer_aMiffPlusCreator_getsAFreshIdentifier() {
        val free = PlaceQuota.forCreate(false, PlaceType.RISK, emptyMap(), 0) { "NEW" }
        assertEquals(PaywallReason.DANGER, (free as PlaceSlot.NeedsPremium).reason)
        val plus = PlaceQuota.forCreate(true, PlaceType.RISK, emptyMap(), 5) { "NEW" }
        assertEquals("NEW", (plus as PlaceSlot.Use).placeId)
    }

    @Test
    fun declare_changingAPlaceToADangerZone_isMiffPlusOnly_goingBackIsAlwaysFree() {
        assertEquals(PaywallReason.DANGER, PlaceQuota.forTypeChange(false, PlaceType.TRUSTED, PlaceType.RISK))
        assertNull(PlaceQuota.forTypeChange(true, PlaceType.TRUSTED, PlaceType.RISK))
        assertNull(PlaceQuota.forTypeChange(false, PlaceType.RISK, PlaceType.TRUSTED))
        assertFalse(DangerZoneRights.canDeclare(creatorPremium = false))
        assertTrue(DangerZoneRights.canDeclare(creatorPremium = true))
    }

    @Test
    fun declare_theLockOfTheForm_isOnlyForAFreeCreator_exceptOnAPlaceThatAlreadyIsADangerZone() {
        assertTrue(DangerZoneRights.isLockedInForm(asRequest = false, premium = false, current = PlaceType.TRUSTED))
        assertFalse(DangerZoneRights.isLockedInForm(asRequest = false, premium = true, current = PlaceType.TRUSTED))
        assertFalse(DangerZoneRights.isLockedInForm(asRequest = false, premium = false, current = PlaceType.RISK))
    }
}
