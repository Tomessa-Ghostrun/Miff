package com.tomessa.miff.data.place

import com.google.firebase.firestore.FirebaseFirestoreException
import com.tomessa.miff.data.miff.EntitlementRead
import com.tomessa.miff.domain.miff.MiffDetails
import com.tomessa.miff.domain.miff.MiffFailure
import com.tomessa.miff.domain.miff.WriteKind
import com.tomessa.miff.domain.place.PaywallReason
import com.tomessa.miff.domain.place.PlaceRequestLimits
import com.tomessa.miff.domain.place.PlaceType
import com.tomessa.miff.testsupport.FakePlaceRepository
import com.tomessa.miff.testsupport.FakePlaceRequestStore
import com.tomessa.miff.testsupport.FakePlaceStore
import com.tomessa.miff.testsupport.MIFF_ID
import com.tomessa.miff.testsupport.firestoreError
import com.tomessa.miff.testsupport.testRequest
import com.tomessa.miff.testsupport.testRequestDraft
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a member who is not the creator can do with the requests to add a place (ticket LIEU-REQ-B, criteria M3 to M7, M11, L1): the limits are applied
 * BEFORE anything is sent, a doubt is looked at on the server, nothing sensitive leaves in a failure. No Firestore: doubles.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaceRequestRepositoryTest {
    private val now = 10_000_000_000L
    private val requests = FakePlaceRequestStore()
    private val places = FakePlaceStore()
    private val placeRepository = FakePlaceRepository()
    private val repo =
        FirestorePlaceRequestRepository(requests, places, placeRepository, newPlaceId = {
            "NEWPLACE0000000001"
        }, nowMillis = { now })
    private val uid = "u2"

    init {
        places.miffs[MIFF_ID] = MiffDetails(MIFF_ID, "Famille", "XTZRSP367988", ownerUid = "u1")
    }

    private fun premium() {
        places.entitlement = EntitlementRead(active = true, expiresAtMillis = now + 1_000_000)
    }

    private suspend fun context(mine: List<com.tomessa.miff.domain.place.PlaceRequest> = emptyList()): RequestContext {
        requests.mine = mine
        return (repo.prepare(uid, MIFF_ID) as RequestContextLoad.Loaded).context
    }

    // --- Before the form ----------------------------------------------------------------------------------------------------------

    @Test
    fun prepare_readsThePlanTheMiffAndMyRequests_fromTheServer() =
        runTest {
            premium()
            val loaded = context(listOf(testRequest(uid = uid, slot = 1, createdAtMillis = now)))
            assertTrue(loaded.premium)
            assertEquals("Famille", loaded.miffName)
            assertEquals(1, loaded.mine.size)
            assertEquals(listOf(true), requests.reads)
        }

    @Test
    fun prepare_forTheCreatorOfTheMiff_isUnavailable_theyAddPlacesThemselves() =
        runTest {
            places.miffs[MIFF_ID] = MiffDetails(MIFF_ID, "Famille", "XTZRSP367988", ownerUid = uid)
            assertTrue(repo.prepare(uid, MIFF_ID) is RequestContextLoad.Unavailable)
        }

    @Test
    fun prepare_aMiffThatNoLongerExists_orRefusesTheAccess_isUnavailable() =
        runTest {
            places.miffs.remove(MIFF_ID)
            assertTrue(repo.prepare(uid, MIFF_ID) is RequestContextLoad.Unavailable)
            places.miffs[MIFF_ID] = firestoreError(FirebaseFirestoreException.Code.PERMISSION_DENIED)
            assertTrue(repo.prepare(uid, MIFF_ID) is RequestContextLoad.Unavailable)
        }

    @Test
    fun prepare_anUnknownPlan_isNotFree_theFormIsToldToTryAgain() =
        runTest {
            places.entitlementFailure = firestoreError(FirebaseFirestoreException.Code.UNAVAILABLE)
            val load = repo.prepare(uid, MIFF_ID)
            assertEquals(MiffFailure.OFFLINE, (load as RequestContextLoad.Failed).failure)
        }

    @Test
    fun prepare_deletesMyExpiredRequests_soTheirSlotIsFreeAgain() =
        runTest {
            val old = testRequest(uid = uid, slot = 1, createdAtMillis = 0L)
            val recent = testRequest(uid = uid, slot = 2, createdAtMillis = now)
            val loaded = context(listOf(old, recent))
            assertEquals(listOf("u2_2"), loaded.mine.map { it.id })
            val lot = requests.commits.single()
            assertEquals("miffs/$MIFF_ID/placeRequests/u2_1", lot[0].path)
            assertEquals(WriteKind.DELETE, lot[0].kind)
        }

    @Test
    fun prepare_anExpiredRequestThatCannotBeDeleted_keepsItsSlot_neverOverwritten() =
        runTest {
            requests.commitFailures.add(firestoreError(FirebaseFirestoreException.Code.UNAVAILABLE))
            val old = testRequest(uid = uid, slot = 1, createdAtMillis = 0L)
            val loaded = context(listOf(old))
            assertEquals(listOf("u2_1"), loaded.mine.map { it.id })
        }

    // --- Sending (M3, M4, M5, M7) ---------------------------------------------------------------------------------------------------

    @Test
    fun submit_writesTheRequestAndTheCounter_inOneLot_inTheFirstFreeSlot() =
        runTest {
            val loaded = context(listOf(testRequest(uid = uid, slot = 1, createdAtMillis = now)))
            val result = repo.submit(uid, MIFF_ID, testRequestDraft(), loaded, online = true)
            val done = result as RequestSubmitResult.Done
            assertEquals("u2_2", done.request.id)
            assertEquals(null, done.request.createdAtMillis)
            val lot = requests.commits.single()
            assertEquals(2, lot.size)
            assertEquals("miffs/$MIFF_ID/placeRequests/u2_2", lot[0].path)
            assertEquals("miffs/$MIFF_ID/meta/placeRequests", lot[1].path)
        }

    @Test
    fun submit_theFourthRequest_isAnsweredCalmly_andNothingIsTried() =
        runTest {
            val loaded =
                context(
                    listOf(1, 2, 3).map { testRequest(uid = uid, slot = it, createdAtMillis = now) },
                )
            val before = requests.commits.size
            val result = repo.submit(uid, MIFF_ID, testRequestDraft(), loaded, online = true)
            assertTrue(result is RequestSubmitResult.MemberLimit)
            assertEquals(before, requests.commits.size)
            assertTrue(requests.enqueued.isEmpty())
        }

    @Test
    fun submit_aDangerZone_withoutMiffPlus_opensTheOffer_andNothingIsSent() =
        runTest {
            val loaded = context()
            val result = repo.submit(uid, MIFF_ID, testRequestDraft(type = PlaceType.RISK), loaded, online = true)
            assertEquals(PaywallReason.DANGER, (result as RequestSubmitResult.NeedsPremium).reason)
            assertTrue(requests.commits.isEmpty())
        }

    @Test
    fun submit_aDangerZone_withMiffPlus_isSent() =
        runTest {
            premium()
            val loaded = context()
            val result = repo.submit(uid, MIFF_ID, testRequestDraft(type = PlaceType.RISK), loaded, online = true)
            assertEquals(PlaceType.RISK, (result as RequestSubmitResult.Done).request.type)
            assertEquals("risk", requests.commits.single()[0].data["type"])
        }

    @Test
    fun submit_aDraftThatBreaksTheRules_isNeverSent() =
        runTest {
            val loaded = context()
            val result = repo.submit(uid, MIFF_ID, testRequestDraft(radiusM = 75), loaded, online = true)
            assertTrue(result is RequestSubmitResult.Failed)
            assertTrue(requests.commits.isEmpty())
        }

    @Test
    fun submit_offline_handsTheLotToThePhone_andSaysItWillLeaveLater() =
        runTest {
            val loaded = context()
            val result = repo.submit(uid, MIFF_ID, testRequestDraft(), loaded, online = false)
            assertTrue(result is RequestSubmitResult.Queued)
            assertTrue(requests.commits.isEmpty())
            val queued = requests.enqueued.single()
            assertEquals("miffs/$MIFF_ID/placeRequests/u2_1", queued[0].path)
        }

    @Test
    fun submit_refusedByTheServer_onAMiffThatIsStillThere_isTheLimitOfTheMiff() =
        runTest {
            val loaded = context()
            requests.commitFailures.add(firestoreError(FirebaseFirestoreException.Code.PERMISSION_DENIED))
            val result = repo.submit(uid, MIFF_ID, testRequestDraft(), loaded, online = true)
            assertTrue(result is RequestSubmitResult.MiffLimit)
        }

    @Test
    fun submit_refused_becauseTheMiffIsGone_isNotTheLimit() =
        runTest {
            val loaded = context()
            requests.commitFailures.add(firestoreError(FirebaseFirestoreException.Code.PERMISSION_DENIED))
            places.miffs.remove(MIFF_ID)
            val result = repo.submit(uid, MIFF_ID, testRequestDraft(), loaded, online = true)
            assertTrue(result is RequestSubmitResult.MiffUnavailable)
        }

    @Test
    fun submit_anAnswerThatWasLost_butTheLotLanded_isDone() =
        runTest {
            val loaded = context()
            requests.commitFailures.add(firestoreError(FirebaseFirestoreException.Code.UNAVAILABLE))
            // The lot left: the server now holds the request.
            requests.onCommit = { requests.mine = listOf(testRequest(uid = uid, slot = 1, createdAtMillis = now)) }
            val result = repo.submit(uid, MIFF_ID, testRequestDraft(), loaded, online = true)
            assertTrue(result is RequestSubmitResult.Done)
        }

    @Test
    fun submit_noAnswer_andNothingLanded_isAFailureTheScreenCanSayCalmly() =
        runTest {
            val loaded = context()
            requests.commitFailures.add(firestoreError(FirebaseFirestoreException.Code.UNAVAILABLE))
            val result = repo.submit(uid, MIFF_ID, testRequestDraft(), loaded, online = true)
            assertEquals(MiffFailure.OFFLINE, (result as RequestSubmitResult.Failed).failure)
        }

    // --- Taking back (M6, L1) -----------------------------------------------------------------------------------------------------

    @Test
    fun withdraw_deletesTheRequestAndLowersTheCounter_inOneLot() =
        runTest {
            val mine = testRequest(uid = uid, slot = 2, createdAtMillis = now)
            assertTrue(repo.withdraw(mine) is RequestWithdrawResult.Done)
            val lot = requests.commits.single()
            assertEquals(WriteKind.DELETE, lot[0].kind)
            assertEquals("miffs/$MIFF_ID/placeRequests/u2_2", lot[0].path)
            assertEquals("u2_2", lot[1].data["lastReqId"])
        }

    @Test
    fun withdraw_aRequestAlreadyGone_isDone_whatWasAskedIsTrue() =
        runTest {
            requests.commitFailures.add(firestoreError(FirebaseFirestoreException.Code.PERMISSION_DENIED))
            requests.mine = emptyList()
            assertTrue(
                repo.withdraw(testRequest(uid = uid, slot = 1, createdAtMillis = now)) is RequestWithdrawResult.Done,
            )
        }

    @Test
    fun withdraw_thatFails_andTheRequestIsStillThere_isAFailure() =
        runTest {
            val mine = testRequest(uid = uid, slot = 1, createdAtMillis = now)
            requests.mine = listOf(mine)
            requests.commitFailures.add(firestoreError(FirebaseFirestoreException.Code.UNAVAILABLE))
            val result = repo.withdraw(mine)
            assertEquals(MiffFailure.OFFLINE, (result as RequestWithdrawResult.Failed).failure)
        }

    @Test
    fun withdrawAllMine_takesBackEveryPendingRequestOfThePerson_oneLotEach_beforeLeaving() =
        runTest {
            requests.mine = listOf(1, 2, 3).map { testRequest(uid = uid, slot = it, createdAtMillis = now) }
            repo.withdrawAllMine(uid, MIFF_ID)
            assertEquals(3, requests.commits.size)
            assertTrue(requests.commits.all { it.size == 2 })
            // Never more than 3 requests: the three slots are all there is.
            assertTrue(requests.commits.size <= PlaceRequestLimits.PER_MEMBER)
        }

    @Test
    fun withdrawAllMine_neverBlocksTheDeparture_whateverHappens() =
        runTest {
            requests.readFailure = firestoreError(FirebaseFirestoreException.Code.UNAVAILABLE)
            repo.withdrawAllMine(uid, MIFF_ID)
            requests.readFailure = null
            requests.mine = listOf(testRequest(uid = uid, slot = 1, createdAtMillis = now))
            requests.commitFailures.add(firestoreError(FirebaseFirestoreException.Code.UNAVAILABLE))
            repo.withdrawAllMine(uid, MIFF_ID)
            assertFalse(requests.commits.isNotEmpty())
        }

    // --- The list ---------------------------------------------------------------------------------------------------------------

    @Test
    fun readMine_usesThePhonesCopy_whenTheServerIsOut_andHidesTheExpiredOnes() =
        runTest {
            requests.mine =
                listOf(
                    testRequest(uid = uid, slot = 1, createdAtMillis = 0L),
                    testRequest(uid = uid, slot = 2, createdAtMillis = now),
                )
            val read = repo.readMine(uid, MIFF_ID) as MineRead.Loaded
            assertEquals(listOf("u2_2"), read.requests.map { it.id })
            // Not forced to the server: the requests written offline and not sent yet are in the phone's copy.
            assertEquals(listOf(false), requests.reads)
        }

    @Test
    fun readMine_aFailure_isAMiffFailure_noMessageNoPath() =
        runTest {
            requests.readFailure = firestoreError(FirebaseFirestoreException.Code.PERMISSION_DENIED)
            val read = repo.readMine(uid, MIFF_ID)
            assertEquals(MiffFailure.REFUSED, (read as MineRead.Failed).failure)
        }
}
