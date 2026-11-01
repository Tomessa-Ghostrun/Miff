package com.tomessa.miff.testsupport

import com.tomessa.miff.domain.place.Place
import com.tomessa.miff.domain.place.PlaceCategory
import com.tomessa.miff.domain.place.PlaceType

/** A place of a Miff for the tests that only need a name on the list (the places tickets build the full ones). */
fun testPlace(
    id: String,
    name: String,
    miffId: String = MIFF_ID,
    type: PlaceType = PlaceType.TRUSTED,
    category: PlaceCategory? = PlaceCategory.HOME,
    address: String? = "17 Avenue d'Italie, 75013 Paris, France",
    radiusM: Int = 100,
    createdBy: String = "u1",
): Place =
    Place(
        id = id,
        miffId = miffId,
        name = name,
        type = type,
        category = category,
        address = address,
        lat = 48.8292,
        lng = 2.3567,
        radiusM = radiusM,
        createdBy = createdBy,
    )

// --- Doubles of the places tickets -------------------------------------------------------------------------------------------

const val THIRD_MIFF_ID = "QwErTyUiOpAsDfGhJkLz"

/** What Firestore holds for the places, and what was asked of it (the order of the reads, what was written). */
class FakePlaceStore : com.tomessa.miff.data.place.PlaceStore {
    var entitlement: com.tomessa.miff.data.miff.EntitlementRead? = null
    var memberships: List<com.tomessa.miff.data.place.PlaceMembership> = emptyList()
    var membershipsFailure: Exception? = null

    /** `get miffs/{id}`: a value (a [com.tomessa.miff.domain.miff.MiffDetails]) or an exception; absent = the Miff does not exist. */
    val miffs = mutableMapOf<String, Any>()

    /** The places each Miff holds right now. */
    val places = mutableMapOf<String, MutableList<Place>>()
    var placesFailure: Exception? = null

    /** What each lot did: the answer of the server. null = accepted, an exception = the lot failed. */
    val commitFailures = ArrayDeque<Exception?>()

    /** Runs when a lot is committed, before it counts as done (to simulate a lot that leaves but whose answer is lost). */
    var onCommit: (List<com.tomessa.miff.domain.miff.DocWrite>) -> Unit = {}
    val commits = mutableListOf<List<com.tomessa.miff.domain.miff.DocWrite>>()

    override suspend fun readEntitlement(uid: String) = entitlement

    override suspend fun readMemberships(uid: String): List<com.tomessa.miff.data.place.PlaceMembership> {
        membershipsFailure?.let { throw it }
        return memberships
    }

    override suspend fun readMiff(miffId: String): com.tomessa.miff.data.miff.MiffRead =
        when (val answer = miffs[miffId]) {
            null -> {
                com.tomessa.miff.data.miff.MiffRead.Missing
            }

            is Exception -> {
                throw answer
            }

            is com.tomessa.miff.domain.miff.MiffDetails -> {
                com.tomessa.miff.data.miff.MiffRead
                    .Exists(answer)
            }

            else -> {
                error("unexpected")
            }
        }

    override suspend fun readPlaces(miffId: String): List<Place> {
        placesFailure?.let { throw it }
        return places[miffId].orEmpty().toList()
    }

    override suspend fun commit(writes: List<com.tomessa.miff.domain.miff.DocWrite>) {
        val failure = commitFailures.removeFirstOrNull()
        onCommit(writes)
        if (failure != null) throw failure
        commits += writes
    }
}

/** A suggestion of address for the tests. */
fun testSuggestion(
    title: String = "17 Avenue d'Italie",
    subtitle: String = "75013 Paris, France",
    lat: Double = 48.82927,
    lng: Double = 2.35679,
) = com.tomessa.miff.domain.place
    .AddressSuggestion(title, subtitle, "$title, $subtitle", lat, lng)

/** Answers of the search of addresses, in order, and what was asked (to prove the pause, the minimum and the cancellation). */
class FakeAddressSearch(
    var answer: com.tomessa.miff.domain.place.AddressSearchResult =
        com.tomessa.miff.domain.place.AddressSearchResult
            .Found(listOf(testSuggestion())),
) : com.tomessa.miff.domain.place.AddressSearch {
    val queries = mutableListOf<String>()
    var delayMillis = 0L

    override suspend fun search(
        query: String,
        language: String,
    ): com.tomessa.miff.domain.place.AddressSearchResult {
        queries += query
        if (delayMillis > 0) kotlinx.coroutines.delay(delayMillis)
        return answer
    }
}

/** The repository of the places for the view models: what [prepare] says, and every call it received. */
class FakePlaceRepository : com.tomessa.miff.data.place.PlaceRepository {
    var context: com.tomessa.miff.data.place.PlaceContext? = null
    var prepareFailure: com.tomessa.miff.domain.miff.MiffFailure? = null
    var createResult: com.tomessa.miff.data.place.PlaceWriteResult =
        com.tomessa.miff.data.place.PlaceWriteResult.Done(
            "p1",
        )
    var updateResult: com.tomessa.miff.data.place.PlaceWriteResult =
        com.tomessa.miff.data.place.PlaceWriteResult.Done(
            "p1",
        )
    var deleteResult: com.tomessa.miff.data.place.PlaceWriteResult =
        com.tomessa.miff.data.place.PlaceWriteResult.Done(
            "p1",
        )
    val created = mutableListOf<com.tomessa.miff.domain.place.PlaceDraft>()
    val updated = mutableListOf<com.tomessa.miff.domain.place.PlaceDraft>()
    val deleted = mutableListOf<Pair<String, List<String>>>()
    var prepareCalls = 0

    /** Held until released: a write that is "on its way" (to prove that a second tap does nothing). */
    var gate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    override suspend fun prepare(uid: String): com.tomessa.miff.data.place.PlaceContextLoad {
        prepareCalls++
        prepareFailure?.let {
            return com.tomessa.miff.data.place.PlaceContextLoad
                .Failed(it)
        }
        return com.tomessa.miff.data.place.PlaceContextLoad
            .Loaded(requireNotNull(context))
    }

    override suspend fun create(
        uid: String,
        draft: com.tomessa.miff.domain.place.PlaceDraft,
        context: com.tomessa.miff.data.place.PlaceContext,
    ): com.tomessa.miff.data.place.PlaceWriteResult {
        created += draft
        gate?.await()
        return createResult
    }

    override suspend fun update(
        uid: String,
        original: com.tomessa.miff.domain.place.PlaceGroup,
        draft: com.tomessa.miff.domain.place.PlaceDraft,
        context: com.tomessa.miff.data.place.PlaceContext,
    ): com.tomessa.miff.data.place.PlaceWriteResult {
        updated += draft
        gate?.await()
        return updateResult
    }

    override suspend fun delete(
        placeId: String,
        miffIds: List<String>,
    ): com.tomessa.miff.data.place.PlaceWriteResult {
        deleted += placeId to miffIds
        gate?.await()
        return deleteResult
    }
}
