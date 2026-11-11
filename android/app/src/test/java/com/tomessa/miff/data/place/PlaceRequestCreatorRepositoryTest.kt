package com.tomessa.miff.data.place

import com.google.firebase.firestore.FirebaseFirestoreException
import com.tomessa.miff.domain.miff.MiffFailure
import com.tomessa.miff.domain.miff.WriteKind
import com.tomessa.miff.domain.place.OwnedMiff
import com.tomessa.miff.domain.place.PaywallReason
import com.tomessa.miff.domain.place.PlaceCategory
import com.tomessa.miff.domain.place.PlaceDraft
import com.tomessa.miff.domain.place.PlaceType
import com.tomessa.miff.domain.place.PlaceWrites
import com.tomessa.miff.domain.place.RequesterCard
import com.tomessa.miff.domain.place.RequesterState
import com.tomessa.miff.testsupport.FakePlaceRepository
import com.tomessa.miff.testsupport.FakePlaceRequestStore
import com.tomessa.miff.testsupport.FakePlaceStore
import com.tomessa.miff.testsupport.MIFF_ID
import com.tomessa.miff.testsupport.OTHER_MIFF_ID
import com.tomessa.miff.testsupport.firestoreError
import com.tomessa.miff.testsupport.testPlace
import com.tomessa.miff.testsupport.testRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the CREATOR does with the requests it receives (ticket LIEU-REQ-C, criteria C3 to C8, C10, C11): the limits of the free plan are applied BEFORE writing,
 * ONE lot does the whole acceptance, a doubt about the plan never opens the offer, a request already handled is not handled twice. No Firestore: doubles.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaceRequestCreatorRepositoryTest {
    private val now = 10_000_000_000L
    private val requests = FakePlaceRequestStore()
    private val places = FakePlaceStore()
    private val placeRepository = FakePlaceRepository()
    private val repo =
        FirestorePlaceRequestRepository(requests, places, placeRepository, newPlaceId = {
            "NEWPLACE0000000001"
        }, nowMillis = { now })
    private val me = "u1"

    private fun creatorContext(
        premium: Boolean,
        places: List<com.tomessa.miff.domain.place.Place> = emptyList(),
        owned: List<OwnedMiff> = listOf(OwnedMiff(MIFF_ID, "Famille", 100L)),
    ) {
        placeRepository.context = PlaceContext(premium, owned, MIFF_ID, places)
    }

    private fun threePlaces() = listOf("p1", "p2", "p3").map { testPlace(it, "Lieu $it") }

    private val pending = testRequest(uid = "u2", slot = 1, name = "Chez Mamie", createdAtMillis = now)

    // --- Accepting (C3, C6) -----------------------------------------------------------------------------------------------------

    @Test
    fun accept_aFreeCreatorWithRoom_createsThePlaceAsAsked_inTheFirstFreeSlot_inOneLot() =
        runTest {
            creatorContext(premium = false, places = listOf(testPlace("p1", "A"), testPlace("p2", "B")))
            requests.all = listOf(pending)
            val result = repo.accept(me, pending, knownCount = 3)
            assertEquals("p3", (result as RequestAcceptResult.Done).placeId)
            val lot = requests.commits.single()
            assertEquals(3, lot.size)
            // The place, the deletion of the request, the counter: ONE lot, so a refused place leaves the request where it is.
            assertEquals("miffs/$MIFF_ID/places/p3", lot[0].path)
            assertEquals(WriteKind.SET, lot[0].kind)
            assertEquals("miffs/$MIFF_ID/placeRequests/u2_1", lot[1].path)
            assertEquals(WriteKind.DELETE, lot[1].kind)
            assertEquals("miffs/$MIFF_ID/meta/placeRequests", lot[2].path)
            assertEquals(2, lot[2].data["pending"])
            assertNull(lot[2].data["lastReqId"])
        }

    @Test
    fun accept_thePlaceCarriesWhatWasAsked_theCreatorAsAuthor_andTheNeutralCategory() =
        runTest {
            creatorContext(premium = true)
            val asked =
                testRequest(
                    uid = "u2",
                    slot = 2,
                    name = "Chez Mamie",
                    type = PlaceType.RISK,
                    radiusM = 250,
                    createdAtMillis = now,
                )
            requests.all = listOf(asked)
            repo.accept(me, asked, knownCount = 1)
            val doc = requests.commits.single()[0].data
            assertEquals("Chez Mamie", doc["name"])
            assertEquals("risk", doc["type"])
            assertEquals(asked.address, doc["address"])
            assertEquals(250, doc["radiusM"])
            assertEquals(me, doc["createdBy"])
            // A request carries no category: "other" (the neutral pin), changed later with the edit screen (decision D7).
            assertEquals(PlaceCategory.OTHER.value, doc["category"])
            // The same keys as a place created by hand: the rules of the places do not change (ticket 5.4).
            val byHand =
                PlaceWrites.createDocument(
                    me,
                    PlaceDraft("Chez Mamie", listOf(MIFF_ID), "x", 48.0, 2.0, 250, PlaceType.RISK, PlaceCategory.OTHER),
                )
            assertEquals(byHand.keys, doc.keys)
        }

    @Test
    fun accept_aMiffPlusCreator_getsAFreshIdentifier_andACounterAtTheRealNumber() =
        runTest {
            creatorContext(premium = true, places = threePlaces())
            requests.all = listOf(pending)
            val result = repo.accept(me, pending, knownCount = 1)
            assertEquals("NEWPLACE0000000001", (result as RequestAcceptResult.Done).placeId)
            assertEquals(0, requests.commits.single()[2].data["pending"])
        }

    // --- The offer (C5, C7, C8) --------------------------------------------------------------------------------------------------

    @Test
    fun accept_aFreeCreatorWithThreePlaces_getsTheOffer_theRequestStays_nothingIsWritten() =
        runTest {
            creatorContext(premium = false, places = threePlaces())
            requests.all = listOf(pending)
            val result = repo.accept(me, pending, knownCount = 1)
            assertEquals(PaywallReason.LIMIT, (result as RequestAcceptResult.NeedsPremium).reason)
            assertTrue(requests.commits.isEmpty())
            assertEquals(listOf(pending.id), requests.all.map { it.id })
        }

    @Test
    fun accept_aDangerZone_withAFreeCreator_getsTheOffer() =
        runTest {
            creatorContext(premium = false)
            val danger = testRequest(uid = "u2", slot = 1, type = PlaceType.RISK, createdAtMillis = now)
            requests.all = listOf(danger)
            val result = repo.accept(me, danger, knownCount = 1)
            assertEquals(PaywallReason.DANGER, (result as RequestAcceptResult.NeedsPremium).reason)
            assertTrue(requests.commits.isEmpty())
        }

    @Test
    fun accept_anUnknownPlan_isNeverTheOffer_theCreatorIsToldToTryAgain() =
        runTest {
            placeRepository.prepareFailure = MiffFailure.OFFLINE
            requests.all = listOf(pending)
            val result = repo.accept(me, pending, knownCount = 1)
            assertTrue(result is RequestAcceptResult.SubscriptionUnknown)
            assertTrue(requests.commits.isEmpty())
        }

    // --- A request that is no longer there ----------------------------------------------------------------------------------------

    @Test
    fun accept_aRequestAlreadyHandled_isGone_nothingIsCreated() =
        runTest {
            creatorContext(premium = true)
            requests.all = emptyList()
            assertTrue(repo.accept(me, pending, knownCount = 1) is RequestAcceptResult.Gone)
            assertTrue(requests.commits.isEmpty())
        }

    @Test
    fun accept_aMiffThatIsNoLongerTheirs_isGone() =
        runTest {
            creatorContext(premium = true, owned = listOf(OwnedMiff(OTHER_MIFF_ID, "Autre", 1L)))
            requests.all = listOf(pending)
            assertTrue(repo.accept(me, pending, knownCount = 1) is RequestAcceptResult.Gone)
            assertTrue(requests.commits.isEmpty())
        }

    @Test
    fun accept_noAnswerButTheLotLeft_isDone_theRequestIsGone() =
        runTest {
            creatorContext(premium = true)
            requests.all = listOf(pending)
            requests.commitFailures.add(firestoreError(FirebaseFirestoreException.Code.UNAVAILABLE))
            requests.onCommit = { requests.all = emptyList() }
            assertTrue(repo.accept(me, pending, knownCount = 1) is RequestAcceptResult.Done)
        }

    @Test
    fun accept_aFailure_andTheRequestStillThere_isAFailure_thePlaceWasNotCreated() =
        runTest {
            creatorContext(premium = true)
            requests.all = listOf(pending)
            requests.commitFailures.add(firestoreError(FirebaseFirestoreException.Code.UNAVAILABLE))
            val result = repo.accept(me, pending, knownCount = 1)
            assertEquals(MiffFailure.OFFLINE, (result as RequestAcceptResult.Failed).failure)
        }

    // --- Declining (C4) --------------------------------------------------------------------------------------------------------------

    @Test
    fun decline_deletesTheRequestAndSetsTheCounter_inOneLot_andWritesNothingForTheRequester() =
        runTest {
            requests.all = listOf(pending)
            assertTrue(repo.decline(pending, knownCount = 4) is RequestDeclineResult.Done)
            val lot = requests.commits.single()
            assertEquals(listOf(WriteKind.DELETE, WriteKind.SET), lot.map { it.kind })
            assertEquals("miffs/$MIFF_ID/placeRequests/u2_1", lot[0].path)
            assertEquals(3, lot[1].data["pending"])
            // Nothing in the lot is a document of the requester, a notification, a message.
            assertTrue(lot.none { it.path.contains("users/") || it.path.contains("notif") })
        }

    @Test
    fun decline_neverNeedsMiffPlus_evenForADangerZone_neverReadsThePlan() =
        runTest {
            val danger = testRequest(uid = "u2", slot = 1, type = PlaceType.RISK, createdAtMillis = now)
            requests.all = listOf(danger)
            assertTrue(repo.decline(danger, knownCount = 1) is RequestDeclineResult.Done)
            assertEquals(0, placeRepository.prepareCalls)
        }

    @Test
    fun decline_aRequestAlreadyGone_isDone_whatWasAskedIsTrue() =
        runTest {
            requests.all = emptyList()
            requests.commitFailures.add(firestoreError(FirebaseFirestoreException.Code.PERMISSION_DENIED))
            assertTrue(repo.decline(pending, knownCount = 1) is RequestDeclineResult.Done)
        }

    @Test
    fun decline_thatFails_andTheRequestIsStillThere_isAFailure() =
        runTest {
            requests.all = listOf(pending)
            requests.commitFailures.add(firestoreError(FirebaseFirestoreException.Code.UNAVAILABLE))
            assertEquals(
                MiffFailure.OFFLINE,
                (repo.decline(pending, knownCount = 1) as RequestDeclineResult.Failed).failure,
            )
        }

    @Test
    fun theCounter_neverGoesBelowZero_orAboveTen() =
        runTest {
            requests.all = listOf(pending)
            repo.decline(pending, knownCount = 0)
            assertEquals(0, requests.commits.single()[1].data["pending"])
            repo.purge(MIFF_ID, listOf("u2_1"), remaining = 99)
            assertEquals(
                10,
                requests.commits
                    .last()
                    .last()
                    .data["pending"],
            )
        }

    // --- Cleaning up (C10, L2) ---------------------------------------------------------------------------------------------------------

    @Test
    fun purge_deletesFourPerLot_eachLotSettingTheCounter() =
        runTest {
            val ids = (1..9).map { "u${it}_1" }
            repo.purge(MIFF_ID, ids, remaining = 2)
            assertEquals(listOf(5, 5, 2), requests.commits.map { it.size })
            assertTrue(requests.commits.all { lot -> lot.last().path == "miffs/$MIFF_ID/meta/placeRequests" })
            assertEquals(
                ids,
                requests.commits.flatMap { lot ->
                    lot.filter { it.kind == WriteKind.DELETE }.map { it.path.substringAfterLast('/') }
                },
            )
        }

    @Test
    fun purge_aFailure_isIgnored_neverBlocking() =
        runTest {
            requests.commitFailures.add(firestoreError(FirebaseFirestoreException.Code.UNAVAILABLE))
            repo.purge(MIFF_ID, listOf("u2_1", "u3_1", "u4_1", "u5_1", "u6_1"), remaining = 0)
            // The first lot failed: the second is not tried (the next answer of the listener tries again).
            assertTrue(requests.commits.isEmpty())
        }

    // --- Who asked (C2) ------------------------------------------------------------------------------------------------------------------

    @Test
    fun requester_aCard_isKnown_aMissingCardOnTheServer_isNotAMember_aFailureIsNeverAGuess() =
        runTest {
            requests.requesters["u2"] = RequesterRead.Found(RequesterCard.of("u2", "Léa", "Martin", null))
            requests.requesters["u3"] = RequesterRead.NotMember
            val known = repo.readRequester(MIFF_ID, "u2") as RequesterState.Known
            assertEquals("Léa", known.card.firstName)
            assertEquals("LM", known.card.initials)
            assertEquals(RequesterState.NotMember, repo.readRequester(MIFF_ID, "u3"))
            // u4 is not answered by the server (offline): never "not a member".
            assertEquals(RequesterState.Unknown, repo.readRequester(MIFF_ID, "u4"))
        }

    @Test
    fun requesterCard_keepsTheFirstName_neverTheLastName_orAHugePhoto() {
        val card = RequesterCard.of("u2", "", "Martin", ByteArray(30_000))
        assertEquals("Martin", card.firstName)
        assertNull(card.avatarJpeg)
        assertEquals("RequesterCard(redacted)", card.toString())
    }

    // --- Listening (C1) ----------------------------------------------------------------------------------------------------------------

    @Test
    fun observe_tellsTheRequestsOfTheMiff() =
        runTest {
            requests.snapshots.tryEmit(RequestsSnapshot(listOf(pending), fromCache = false))
            val first = repo.observe(MIFF_ID).first() as RequestsObservation.Data
            assertEquals(listOf("u2_1"), first.requests.map { it.id })
        }

    @Test
    fun observe_aRefusalOfTheRules_isAccessLost_anythingElseIsAFailure_neverAnException() =
        runTest {
            requests.observeFailure = firestoreError(FirebaseFirestoreException.Code.PERMISSION_DENIED)
            assertEquals(listOf<RequestsObservation>(RequestsObservation.AccessLost), repo.observe(MIFF_ID).toList())
            requests.observeFailure = firestoreError(FirebaseFirestoreException.Code.INTERNAL)
            assertEquals(listOf<RequestsObservation>(RequestsObservation.Failed), repo.observe(MIFF_ID).toList())
        }

    @Test
    fun readAll_isTheOneShotRead_aFailureIsAMiffFailure() =
        runTest {
            requests.all = listOf(pending)
            assertEquals(1, (repo.readAll(MIFF_ID) as MineRead.Loaded).requests.size)
            requests.readAllFailure = firestoreError(FirebaseFirestoreException.Code.UNAVAILABLE)
            assertEquals(MiffFailure.OFFLINE, (repo.readAll(MIFF_ID) as MineRead.Failed).failure)
        }
}
