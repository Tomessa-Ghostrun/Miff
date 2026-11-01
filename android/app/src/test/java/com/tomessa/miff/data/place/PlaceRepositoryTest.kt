package com.tomessa.miff.data.place

import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.tomessa.miff.data.miff.EntitlementRead
import com.tomessa.miff.domain.miff.MiffFailure
import com.tomessa.miff.domain.miff.WriteKind
import com.tomessa.miff.domain.place.PaywallReason
import com.tomessa.miff.domain.place.Place
import com.tomessa.miff.domain.place.PlaceCategory
import com.tomessa.miff.domain.place.PlaceDraft
import com.tomessa.miff.domain.place.PlaceGroup
import com.tomessa.miff.domain.place.PlaceType
import com.tomessa.miff.testsupport.FakePlaceStore
import com.tomessa.miff.testsupport.MIFF_ID
import com.tomessa.miff.testsupport.OTHER_MIFF_ID
import com.tomessa.miff.testsupport.THIRD_MIFF_ID
import com.tomessa.miff.testsupport.miffDetails
import com.tomessa.miff.testsupport.testPlace
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the places do with Firestore (ticket LOC-1), against the CONTRACT of the rules of PR #54 (not deployed): two free slots
 * p1 / p2, a danger zone and any further place need Miff+, one document per Miff with the same identifier, 5 Miffs at most,
 * the person must have created the Miff. The store is a double: the real rules are exercised by the PR of the rules.
 */
class PlaceRepositoryTest {
    private val uid = "uid-1"
    private val store = FakePlaceStore()
    private var now = 1_000_000L
    private var ids = 0
    private val repository =
        FirestorePlaceRepository(
            store = store,
            newId = { "FRESHID${(++ids).toString().padStart(13, '0')}" },
            nowMillis = { now },
            timeoutMillis = 1_000L,
        )

    private fun offline() = FirebaseFirestoreException("x", Code.UNAVAILABLE)

    private fun refused() = FirebaseFirestoreException("x", Code.PERMISSION_DENIED)

    /** I created MIFF_ID (first) and OTHER_MIFF_ID; I only joined THIRD_MIFF_ID (the latest one I entered). */
    private fun setUpMiffs() {
        store.memberships =
            listOf(
                PlaceMembership(OTHER_MIFF_ID, joinedAtMillis = 200),
                PlaceMembership(MIFF_ID, joinedAtMillis = 100),
                PlaceMembership(THIRD_MIFF_ID, joinedAtMillis = 300),
            )
        store.miffs[MIFF_ID] = miffDetails(id = MIFF_ID, name = "Famille", owner = uid)
        store.miffs[OTHER_MIFF_ID] = miffDetails(id = OTHER_MIFF_ID, name = "Les tuches", owner = uid)
        store.miffs[THIRD_MIFF_ID] = miffDetails(id = THIRD_MIFF_ID, name = "Copains", owner = "somebody-else")
    }

    private fun premium() {
        store.entitlement = EntitlementRead(active = true, expiresAtMillis = now + 10_000)
    }

    private suspend fun context() = (repository.prepare(uid) as PlaceContextLoad.Loaded).context

    private fun draft(
        miffIds: List<String> = listOf(MIFF_ID),
        type: PlaceType = PlaceType.TRUSTED,
        name: String = "Maison",
        radiusM: Int = 100,
    ) = PlaceDraft(
        name,
        miffIds,
        "17 Avenue d'Italie, 75013 Paris",
        48.82927,
        2.35679,
        radiusM,
        type,
        PlaceCategory.HOME,
    )

    // --- Reading: who the person is --------------------------------------------------------------------------------------

    @Test
    fun prepare_keepsOnlyTheMiffsThePersonCreated_firstCreatedFirst_andKnowsTheLastJoined() =
        runTest {
            setUpMiffs()
            val context = context()
            assertEquals(listOf(MIFF_ID, OTHER_MIFF_ID), context.ownedMiffs.map { it.id })
            assertEquals(THIRD_MIFF_ID, context.lastJoinedMiffId)
            assertEquals(false, context.premium)
        }

    @Test
    fun prepare_premiumIsAnActiveEntitlementThatHasNotExpired() =
        runTest {
            setUpMiffs()
            store.entitlement = EntitlementRead(active = true, expiresAtMillis = now - 1)
            assertEquals(false, context().premium)
            store.entitlement = EntitlementRead(active = false, expiresAtMillis = now + 10)
            assertEquals(false, context().premium)
            premium()
            assertEquals(true, context().premium)
        }

    @Test
    fun prepare_readsThePlacesOfMyMiffs_notOfTheOthers() =
        runTest {
            setUpMiffs()
            store.places[MIFF_ID] = mutableListOf(testPlace("p1", "Maison", MIFF_ID))
            store.places[THIRD_MIFF_ID] = mutableListOf(testPlace("p9", "Pas à moi", THIRD_MIFF_ID))
            val context = context()
            assertEquals(listOf("p1"), context.places.map { it.id })
            assertEquals(1, context.distinctPlaces)
        }

    @Test
    fun prepare_offline_isAFailure_neverAGuess() =
        runTest {
            setUpMiffs()
            store.membershipsFailure = offline()
            val result = repository.prepare(uid)
            assertEquals(MiffFailure.OFFLINE, (result as PlaceContextLoad.Failed).failure)
        }

    @Test
    fun prepare_aMiffThatCannotBeReadAnyMore_isLeftOut() =
        runTest {
            setUpMiffs()
            store.miffs[OTHER_MIFF_ID] = refused()
            assertEquals(listOf(MIFF_ID), context().ownedMiffs.map { it.id })
        }

    // --- Creating: free plan (LIM1, LIM3) ---------------------------------------------------------------------------------

    @Test
    fun create_free_firstPlaceTakesP1_secondTakesP2_oneDocumentEach() =
        runTest {
            setUpMiffs()
            val first = repository.create(uid, draft(), context())
            assertEquals("p1", (first as PlaceWriteResult.Done).placeId)
            assertEquals(listOf("miffs/$MIFF_ID/places/p1"), store.commits.single().map { it.path })
            store.places[MIFF_ID] = mutableListOf(testPlace("p1", "Maison", MIFF_ID))
            val second = repository.create(uid, draft(name = "Travail"), context())
            assertEquals("p2", (second as PlaceWriteResult.Done).placeId)
        }

    @Test
    fun create_free_thirdPlace_isRefusedBeforeAnythingIsSent() =
        runTest {
            setUpMiffs()
            store.places[MIFF_ID] = mutableListOf(testPlace("p1", "A", MIFF_ID), testPlace("p2", "B", MIFF_ID))
            val result = repository.create(uid, draft(), context())
            assertEquals(PaywallReason.LIMIT, (result as PlaceWriteResult.NeedsPremium).reason)
            assertTrue(store.commits.isEmpty())
        }

    @Test
    fun create_free_dangerZone_isRefusedBeforeAnythingIsSent() =
        runTest {
            setUpMiffs()
            val result = repository.create(uid, draft(type = PlaceType.RISK), context())
            assertEquals(PaywallReason.DANGER, (result as PlaceWriteResult.NeedsPremium).reason)
            assertTrue(store.commits.isEmpty())
        }

    // --- Creating: Miff+ and several Miffs (MIF2) ----------------------------------------------------------------------------

    @Test
    fun create_premium_oneLot_theSameFreshIdInEveryMiff() =
        runTest {
            setUpMiffs()
            premium()
            val result =
                repository.create(
                    uid,
                    draft(miffIds = listOf(MIFF_ID, OTHER_MIFF_ID), type = PlaceType.RISK),
                    context(),
                )
            val id = (result as PlaceWriteResult.Done).placeId
            assertTrue(Regex("[A-Za-z0-9]{20}").matches(id))
            assertEquals(1, store.commits.size)
            assertEquals(
                listOf(
                    "miffs/$MIFF_ID/places/$id",
                    "miffs/$OTHER_MIFF_ID/places/$id",
                ),
                store.commits.single().map {
                    it.path
                },
            )
            assertTrue(store.commits.single().all { it.kind == WriteKind.SET && it.data["type"] == "risk" })
        }

    @Test
    fun create_aMiffThePersonDidNotCreate_isRefused_andNothingIsSent() =
        runTest {
            setUpMiffs()
            premium()
            val result = repository.create(uid, draft(miffIds = listOf(MIFF_ID, THIRD_MIFF_ID)), context())
            assertEquals(MiffFailure.UNKNOWN, (result as PlaceWriteResult.Failed).failure)
            assertTrue(store.commits.isEmpty())
        }

    @Test
    fun create_moreThanFiveMiffs_isRefused() =
        runTest {
            setUpMiffs()
            premium()
            val six = (1..6).map { "m$it" }
            val result = repository.create(uid, draft(miffIds = six), context())
            assertTrue(result is PlaceWriteResult.Failed)
            assertTrue(store.commits.isEmpty())
        }

    @Test
    fun create_aDraftTheRulesWouldRefuse_neverLeaves() =
        runTest {
            setUpMiffs()
            val result = repository.create(uid, draft(name = "M"), context())
            assertTrue(result is PlaceWriteResult.Failed)
            assertTrue(store.commits.isEmpty())
        }

    // --- Creating: when it goes wrong (PL9, case 14) ------------------------------------------------------------------------------

    @Test
    fun create_offline_saysSo_andNothingIsQueued() =
        runTest {
            setUpMiffs()
            store.commitFailures += offline()
            val result = repository.create(uid, draft(), context())
            assertEquals(MiffFailure.OFFLINE, (result as PlaceWriteResult.Failed).failure)
            assertTrue(store.commits.isEmpty())
        }

    @Test
    fun create_answerLost_butTheLotLanded_isNotShownAsAFailure() =
        runTest {
            setUpMiffs()
            store.commitFailures += offline()
            // The lot left and the server did write it; only the answer was lost.
            store.onCommit = { writes ->
                store.places[MIFF_ID] =
                    mutableListOf(
                        Place(
                            "p1",
                            MIFF_ID,
                            "Maison",
                            PlaceType.TRUSTED,
                            PlaceCategory.HOME,
                            "17 Avenue d'Italie, 75013 Paris",
                            48.82927,
                            2.35679,
                            100,
                            uid,
                        ),
                    )
                assertEquals(1, writes.size)
            }
            val result = repository.create(uid, draft(), context())
            assertEquals("p1", (result as PlaceWriteResult.Done).placeId)
        }

    @Test
    fun create_refusedBecauseAMiffIsGone_saysThatMiff_notAGenericError() =
        runTest {
            setUpMiffs()
            premium()
            val loaded = context()
            store.miffs.remove(OTHER_MIFF_ID) // deleted between the opening of the form and the tap
            store.commitFailures += refused()
            val result = repository.create(uid, draft(miffIds = listOf(MIFF_ID, OTHER_MIFF_ID)), loaded)
            assertEquals(PlaceWriteResult.MiffUnavailable, result)
            assertTrue(store.commits.isEmpty())
        }

    @Test
    fun create_refusedForAnotherReason_isTheGenericSentence() =
        runTest {
            setUpMiffs()
            store.commitFailures += refused()
            val result = repository.create(uid, draft(), context())
            assertEquals(MiffFailure.REFUSED, (result as PlaceWriteResult.Failed).failure)
        }

    // --- Editing (PL7, MIF2, LIM3) ------------------------------------------------------------------------------------------------

    private fun group(
        id: String,
        vararg miffs: String,
        type: PlaceType = PlaceType.TRUSTED,
    ) = PlaceGroup(id, miffs.map { testPlace(id, "Maison", it, type = type) })

    private fun seed(
        id: String,
        vararg miffs: String,
        type: PlaceType = PlaceType.TRUSTED,
    ) = miffs.forEach { store.places.getOrPut(it) { mutableListOf() }.add(testPlace(id, "Maison", it, type = type)) }

    @Test
    fun update_changesTheFieldsInEveryCopy_inOneLot() =
        runTest {
            setUpMiffs()
            premium()
            seed("PLACEID0000000000001", MIFF_ID, OTHER_MIFF_ID)
            val original = group("PLACEID0000000000001", MIFF_ID, OTHER_MIFF_ID)
            val result =
                repository.update(
                    uid,
                    original,
                    draft(miffIds = listOf(MIFF_ID, OTHER_MIFF_ID), name = "Chez Papa", radiusM = 300),
                    context(),
                )
            assertTrue(result is PlaceWriteResult.Done)
            val lot = store.commits.single()
            assertTrue(lot.all { it.kind == WriteKind.UPDATE })
            assertEquals(setOf("Chez Papa"), lot.map { it.data["name"] }.toSet())
            assertEquals(setOf(300), lot.map { it.data["radiusM"] }.toSet())
        }

    @Test
    fun update_theMiffsCanChange_addOne_removeOne() =
        runTest {
            setUpMiffs()
            premium()
            seed("PLACEID0000000000001", MIFF_ID)
            val result =
                repository.update(
                    uid,
                    group("PLACEID0000000000001", MIFF_ID),
                    draft(miffIds = listOf(OTHER_MIFF_ID)),
                    context(),
                )
            assertTrue(result is PlaceWriteResult.Done)
            val byPath = store.commits.single().associateBy { it.path }
            assertEquals(WriteKind.SET, byPath.getValue("miffs/$OTHER_MIFF_ID/places/PLACEID0000000000001").kind)
            assertEquals(WriteKind.DELETE, byPath.getValue("miffs/$MIFF_ID/places/PLACEID0000000000001").kind)
        }

    @Test
    fun update_free_toADangerZone_isRefusedBeforeAnythingIsSent() =
        runTest {
            setUpMiffs()
            seed("p1", MIFF_ID)
            val result = repository.update(uid, group("p1", MIFF_ID), draft(type = PlaceType.RISK), context())
            assertEquals(PaywallReason.DANGER, (result as PlaceWriteResult.NeedsPremium).reason)
            assertTrue(store.commits.isEmpty())
        }

    @Test
    fun update_free_backFromADangerZone_isAlwaysAllowed() =
        runTest {
            setUpMiffs()
            seed("p1", MIFF_ID, type = PlaceType.RISK)
            val result =
                repository.update(
                    uid,
                    group("p1", MIFF_ID, type = PlaceType.RISK),
                    draft(type = PlaceType.TRUSTED),
                    context(),
                )
            assertTrue(result is PlaceWriteResult.Done)
        }

    @Test
    fun update_free_aMiffAddedToAPlaceOfAFixedSlot_staysFree() =
        runTest {
            setUpMiffs()
            seed("p1", MIFF_ID)
            val result =
                repository.update(
                    uid,
                    group("p1", MIFF_ID),
                    draft(miffIds = listOf(MIFF_ID, OTHER_MIFF_ID)),
                    context(),
                )
            assertTrue(result is PlaceWriteResult.Done)
        }

    @Test
    fun update_free_aMiffAddedWhereTheSlotIsTakenByAnotherPlace_isTheLimit() =
        runTest {
            setUpMiffs()
            seed("p1", MIFF_ID)
            seed("p1", OTHER_MIFF_ID) // another place of the same slot name in the other Miff
            val result =
                repository.update(
                    uid,
                    group("p1", MIFF_ID),
                    draft(miffIds = listOf(MIFF_ID, OTHER_MIFF_ID)),
                    context(),
                )
            assertEquals(PaywallReason.LIMIT, (result as PlaceWriteResult.NeedsPremium).reason)
            assertTrue(store.commits.isEmpty())
        }

    @Test
    fun update_aPlaceDeletedElsewhere_isNoticed() =
        runTest {
            setUpMiffs()
            premium()
            seed("PLACEID0000000000001", MIFF_ID)
            val loaded = context()
            store.places[MIFF_ID] = mutableListOf() // deleted from another phone
            store.commitFailures += FirebaseFirestoreException("x", Code.NOT_FOUND)
            val result = repository.update(uid, group("PLACEID0000000000001", MIFF_ID), draft(), loaded)
            assertEquals(PlaceWriteResult.PlaceGone, result)
        }

    @Test
    fun update_offline_isAFailure_andTheOldPlaceStays() =
        runTest {
            setUpMiffs()
            premium()
            seed("PLACEID0000000000001", MIFF_ID)
            store.commitFailures += offline()
            val result =
                repository.update(
                    uid,
                    group("PLACEID0000000000001", MIFF_ID),
                    draft(name = "Autre nom"),
                    context(),
                )
            assertEquals(MiffFailure.OFFLINE, (result as PlaceWriteResult.Failed).failure)
            assertTrue(store.commits.isEmpty())
        }

    // --- Deleting (PL8) ------------------------------------------------------------------------------------------------------------

    @Test
    fun delete_isOneLot_inEveryMiff_withNoScreen() =
        runTest {
            val result = repository.delete("PLACEID0000000000001", listOf(MIFF_ID, OTHER_MIFF_ID))
            assertTrue(result is PlaceWriteResult.Done)
            val lot = store.commits.single()
            assertEquals(2, lot.size)
            assertTrue(lot.all { it.kind == WriteKind.DELETE })
        }

    @Test
    fun delete_offline_isAFailure() =
        runTest {
            store.places[MIFF_ID] = mutableListOf(testPlace("p1", "Maison", MIFF_ID))
            store.commitFailures += offline()
            val result = repository.delete("p1", listOf(MIFF_ID))
            assertEquals(MiffFailure.OFFLINE, (result as PlaceWriteResult.Failed).failure)
        }

    @Test
    fun delete_aPlaceAlreadyGone_isDone() =
        runTest {
            store.places[MIFF_ID] = mutableListOf()
            store.commitFailures += refused()
            assertTrue(repository.delete("p1", listOf(MIFF_ID)) is PlaceWriteResult.Done)
        }

    @Test
    fun delete_moreThanFiveMiffs_isRefused() =
        runTest {
            assertTrue(repository.delete("p1", (1..6).map { "m$it" }) is PlaceWriteResult.Failed)
            assertTrue(store.commits.isEmpty())
        }
}
