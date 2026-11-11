package com.tomessa.miff.testsupport

import com.google.firebase.Timestamp
import com.tomessa.miff.data.place.MineRead
import com.tomessa.miff.data.place.PlaceRequestRepository
import com.tomessa.miff.data.place.PlaceRequestStore
import com.tomessa.miff.data.place.RequestAcceptResult
import com.tomessa.miff.data.place.RequestContext
import com.tomessa.miff.data.place.RequestContextLoad
import com.tomessa.miff.data.place.RequestDeclineResult
import com.tomessa.miff.data.place.RequestSubmitResult
import com.tomessa.miff.data.place.RequestWithdrawResult
import com.tomessa.miff.data.place.RequesterRead
import com.tomessa.miff.data.place.RequestsObservation
import com.tomessa.miff.data.place.RequestsSnapshot
import com.tomessa.miff.domain.miff.DocWrite
import com.tomessa.miff.domain.place.PlaceRequest
import com.tomessa.miff.domain.place.PlaceRequestDraft
import com.tomessa.miff.domain.place.PlaceType
import com.tomessa.miff.domain.place.RequesterState
import java.util.Date

/** A request of the ticket LIEU-REQ for the tests: `{uid}_{slot}` in [miffId], dated [createdAtMillis] (null: not dated yet). */
fun testRequest(
    uid: String = "u2",
    slot: Int = 1,
    name: String = "Chez Mamie",
    miffId: String = MIFF_ID,
    type: PlaceType = PlaceType.TRUSTED,
    createdAtMillis: Long? = 1_000L,
    address: String? = "12 rue des Lilas, 75020 Paris, France",
    radiusM: Int = 100,
): PlaceRequest =
    PlaceRequest(
        id = "${uid}_$slot",
        miffId = miffId,
        requestedBy = uid,
        name = name,
        type = type,
        address = address,
        lat = 48.8566,
        lng = 2.3522,
        radiusM = radiusM,
        createdAtMillis = createdAtMillis,
    )

/** The fields of a request document as Firestore hands them to the parser. */
fun requestFields(
    uid: String = "u2",
    name: Any? = "Chez Mamie",
    type: Any? = "trusted",
    address: Any? = "12 rue des Lilas",
    lat: Any? = 48.8566,
    lng: Any? = 2.3522,
    radiusM: Any? = 100L,
    createdAtMillis: Long? = 1_000L,
    requestedBy: Any? = uid,
): Map<String, Any?> =
    mapOf(
        "requestedBy" to requestedBy,
        "name" to name,
        "type" to type,
        "address" to address,
        "lat" to lat,
        "lng" to lng,
        "radiusM" to radiusM,
        "createdAt" to createdAtMillis?.let { Timestamp(Date(it)) },
    )

fun testRequestDraft(
    name: String = "Chez Mamie",
    type: PlaceType = PlaceType.TRUSTED,
    radiusM: Int = 100,
) = PlaceRequestDraft(
    name = name,
    address = "12 rue des Lilas, 75020 Paris, France",
    lat = 48.85661,
    lng = 2.35222,
    radiusM = radiusM,
    type = type,
)

/** What Firestore holds for the requests of a member, and what was written (the lots in order, the batches handed to the phone's queue). */
class FakePlaceRequestStore : PlaceRequestStore {
    /** The requests the server holds for the person (what `readMine` answers). */
    var mine: List<PlaceRequest> = emptyList()
    var readFailure: Exception? = null

    /** What each lot did: null = accepted, an exception = the lot failed. */
    val commitFailures = ArrayDeque<Exception?>()
    val commits = mutableListOf<List<DocWrite>>()
    val enqueued = mutableListOf<List<DocWrite>>()
    val reads = mutableListOf<Boolean>()

    /** Every request the server holds in the Miff (what the creator reads and listens to). */
    var all: List<PlaceRequest> = emptyList()
    var readAllFailure: Exception? = null

    /** What `members/{uid}` says by person: a card, or "not a member". A person who is not in the map: the read fails. */
    val requesters = mutableMapOf<String, RequesterRead>()
    val requesterReads = mutableListOf<String>()

    /** The listener of the creator: tests push snapshots (or an error) through it. */
    val snapshots = kotlinx.coroutines.flow.MutableSharedFlow<RequestsSnapshot>(replay = 1, extraBufferCapacity = 8)
    var observed = 0

    /** Runs when a lot is committed, before it counts as done (a lot that leaves but whose answer is lost). */
    var onCommit: (List<DocWrite>) -> Unit = {}

    override suspend fun readMine(
        miffId: String,
        uid: String,
        fromServer: Boolean,
    ): List<PlaceRequest> {
        reads += fromServer
        readFailure?.let { throw it }
        return mine.filter { it.miffId == miffId && it.requestedBy == uid }
    }

    /** A listener that ends with this error (a refusal of the rules, for instance). */
    var observeFailure: Exception? = null

    override fun observeAll(miffId: String): kotlinx.coroutines.flow.Flow<RequestsSnapshot> {
        observed++
        val failure = observeFailure
        return if (failure != null) kotlinx.coroutines.flow.flow { throw failure } else snapshots
    }

    override suspend fun readAll(miffId: String): List<PlaceRequest> {
        readAllFailure?.let { throw it }
        return all.filter { it.miffId == miffId }
    }

    override suspend fun readOne(
        miffId: String,
        requestId: String,
    ): PlaceRequest? {
        readFailure?.let { throw it }
        return (all + mine).firstOrNull { it.miffId == miffId && it.id == requestId }
    }

    override suspend fun readRequester(
        miffId: String,
        uid: String,
    ): RequesterRead {
        requesterReads += uid
        return requesters[uid] ?: throw IllegalStateException("not answered")
    }

    override suspend fun commit(writes: List<DocWrite>) {
        val failure = commitFailures.removeFirstOrNull()
        onCommit(writes)
        if (failure != null) throw failure
        commits += writes
    }

    override fun enqueue(writes: List<DocWrite>) {
        enqueued.add(writes)
    }
}

/** The repository of the requests for the view models: what each call answers, and every call it received. */
class FakePlaceRequestRepository : PlaceRequestRepository {
    var context: RequestContextLoad =
        RequestContextLoad.Loaded(RequestContext("Famille", premium = false, mine = emptyList()))
    var submitResult: RequestSubmitResult = RequestSubmitResult.Done(testRequest())
    var withdrawResult: RequestWithdrawResult = RequestWithdrawResult.Done
    var mineResult: MineRead = MineRead.Loaded(emptyList())

    /** What the listener of the creator tells: tests push into it. */
    val observations = kotlinx.coroutines.flow.MutableSharedFlow<RequestsObservation>(extraBufferCapacity = 8)
    var observed = 0
    var readAllResult: MineRead = MineRead.Loaded(emptyList())
    val requesterStates = mutableMapOf<String, RequesterState>()
    val requesterReads = mutableListOf<String>()
    var acceptResult: RequestAcceptResult = RequestAcceptResult.Done("p1")
    var declineResult: RequestDeclineResult = RequestDeclineResult.Done
    val accepted = mutableListOf<Pair<PlaceRequest, Int>>()
    val declined = mutableListOf<Pair<PlaceRequest, Int>>()
    val purged = mutableListOf<Triple<String, List<String>, Int>>()

    val submitted = mutableListOf<PlaceRequestDraft>()
    val submittedOnline = mutableListOf<Boolean>()
    val withdrawn = mutableListOf<PlaceRequest>()
    val withdrawnAll = mutableListOf<Pair<String, String>>()
    var prepareCalls = 0
    var readMineCalls = 0

    /** Held until released: a call that is "on its way". */
    var gate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    override suspend fun prepare(
        uid: String,
        miffId: String,
    ): RequestContextLoad {
        prepareCalls++
        return context
    }

    override suspend fun submit(
        uid: String,
        miffId: String,
        draft: PlaceRequestDraft,
        context: RequestContext,
        online: Boolean,
    ): RequestSubmitResult {
        gate?.await()
        submitted += draft
        submittedOnline += online
        return submitResult
    }

    override suspend fun withdraw(request: PlaceRequest): RequestWithdrawResult {
        gate?.await()
        withdrawn += request
        return withdrawResult
    }

    override suspend fun readMine(
        uid: String,
        miffId: String,
    ): MineRead {
        readMineCalls++
        return mineResult
    }

    override suspend fun withdrawAllMine(
        uid: String,
        miffId: String,
    ) {
        withdrawnAll += uid to miffId
    }

    override fun observe(miffId: String): kotlinx.coroutines.flow.Flow<RequestsObservation> {
        observed++
        return observations
    }

    override suspend fun readAll(miffId: String): MineRead = readAllResult

    override suspend fun readRequester(
        miffId: String,
        uid: String,
    ): RequesterState {
        requesterReads += uid
        return requesterStates[uid] ?: RequesterState.Unknown
    }

    override suspend fun accept(
        uid: String,
        request: PlaceRequest,
        knownCount: Int,
    ): RequestAcceptResult {
        gate?.await()
        accepted += request to knownCount
        return acceptResult
    }

    override suspend fun decline(
        request: PlaceRequest,
        knownCount: Int,
    ): RequestDeclineResult {
        gate?.await()
        declined += request to knownCount
        return declineResult
    }

    override suspend fun purge(
        miffId: String,
        requestIds: List<String>,
        remaining: Int,
    ) {
        purged += Triple(miffId, requestIds, remaining)
    }
}
