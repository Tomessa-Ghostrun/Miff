package com.tomessa.miff.domain.place

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.tomessa.miff.domain.miff.DocWrite
import com.tomessa.miff.domain.miff.MiffName
import com.tomessa.miff.domain.miff.WriteKind

/*
 * Request to add a place (ticket docs/tickets/demande-ajout-lieu.md, LIEU-REQ): a member who is not the creator of a Miff proposes a place; the creator
 * accepts it or not. Nothing here is ever a real place: it is a document `miffs/{miffId}/placeRequests/{uid}_{1..3}` that only the creator and the
 * person who made it can read.
 *
 * SECURITY, for this file: the name, the address and the coordinates of a request are as sensitive as the ones of a place (they can be the home of
 * somebody who does not use Miff). Every class that carries one hides it in its toString(); nothing here is ever logged or sent as an event.
 * The writes below are the contract with backend/firestore.rules (branch backend/lieu-demande): EVERY key of a request is written (the rules refuse a
 * missing and an unknown one), the date is the server's, `requestedBy` is the uid of the person.
 */

/** The numbers of the ticket (decision D2, D3) in ONE place. */
object PlaceRequestLimits {
    /** Pending requests per member and per Miff: three FIXED slots `{uid}_1`, `{uid}_2`, `{uid}_3` (rules cannot count, they can force an identifier). */
    const val PER_MEMBER = 3

    /** Pending requests per Miff (the counter `meta/placeRequests`, readable by the creator only). */
    const val PER_MIFF = 10

    /** A request that nobody answered goes after 30 days: hidden by both apps and deleted by the one that meets it (no server on the free plan). */
    const val EXPIRY_MILLIS = 30L * 24L * 60L * 60L * 1000L

    val SLOTS = listOf(1, 2, 3)

    /** At most this many deletions in one lot of the creator (the rules read 2 documents per write: backend/data-model.md section 12). */
    const val CREATOR_DELETES_PER_LOT = 4

    fun idOf(
        uid: String,
        slot: Int,
    ): String = "${uid}_$slot"
}

/** One document `miffs/{miffId}/placeRequests/{requestId}`, as the server describes it. */
class PlaceRequest(
    /** `{uid of the requester}_{slot}`. */
    val id: String,
    val miffId: String,
    val requestedBy: String,
    val name: String,
    val type: PlaceType,
    val address: String?,
    val lat: Double,
    val lng: Double,
    val radiusM: Int,
    /** When the server wrote it; null while the write is still on its way (a request just made on this phone). */
    val createdAtMillis: Long?,
) {
    override fun toString(): String = "PlaceRequest(redacted)"

    /** More than 30 days old. A request with no date yet is brand new, never expired. */
    fun isExpired(nowMillis: Long): Boolean =
        createdAtMillis?.let { nowMillis - it > PlaceRequestLimits.EXPIRY_MILLIS } == true

    val slot: Int get() = id.substringAfterLast('_').toInt()

    companion object {
        private val ID = Regex("^([A-Za-z0-9]{1,128})_([123])$")

        // The bounds of the rules (validPlaceShape): wider than the ones of the screen (50 to 800).
        private const val RADIUS_MIN = 50
        private const val RADIUS_MAX = 2000

        /**
         * A request as the server describes it, cleaned before it is shown (the name and the address were written by somebody else). Null when it cannot
         * be a request of this app: bad identifier, `requestedBy` that is not the uid in the identifier (a forged document), no visible name, unknown
         * type, coordinates or radius out of range. It is simply not shown, never a crash.
         */
        fun fromServer(
            id: String,
            miffId: String,
            fields: Map<String, Any?>?,
        ): PlaceRequest? {
            if (fields == null) return null
            val match = ID.matchEntire(id) ?: return null
            val requester = match.groupValues[1]
            if (fields["requestedBy"] != requester) return null
            val name = (fields["name"] as? String)?.let(MiffName::fromServer) ?: return null
            val type = PlaceType.fromValue(fields["type"] as? String) ?: return null
            val lat =
                (fields["lat"] as? Number)?.toDouble()?.takeIf { it.isFinite() && it in -90.0..90.0 } ?: return null
            val lng =
                (fields["lng"] as? Number)?.toDouble()?.takeIf { it.isFinite() && it in -180.0..180.0 } ?: return null
            val radius = (fields["radiusM"] as? Number)?.toInt()?.takeIf { it in RADIUS_MIN..RADIUS_MAX } ?: return null
            return PlaceRequest(
                id = id,
                miffId = miffId,
                requestedBy = requester,
                name = name,
                type = type,
                address = (fields["address"] as? String)?.let(MiffName::fromServer),
                lat = lat,
                lng = lng,
                radiusM = radius,
                createdAtMillis = (fields["createdAt"] as? Timestamp)?.toDate()?.time,
            )
        }
    }
}

/** What the person filled in to ask for a place: what the form builds (same fields and same checks as a place, without the category). */
class PlaceRequestDraft(
    val name: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    val radiusM: Int,
    val type: PlaceType,
) {
    override fun toString(): String = "PlaceRequestDraft(redacted)"

    /** What is wrong with this draft, or null. Mirrors the rules and the form of a place. */
    val problem: DraftProblem?
        get() =
            when {
                !PlaceName.isValid(name) -> DraftProblem.NAME
                address.isBlank() || address.length > PlaceLimits.ADDRESS_MAX -> DraftProblem.ADDRESS
                !PlaceCoordinates.isValid(lat, lng) -> DraftProblem.ADDRESS
                !PlaceRadius.isAllowed(radiusM) -> DraftProblem.RADIUS
                else -> null
            }
}

/** The order of the requests in a list (question Q-R5 of the ticket, default value). */
object PlaceRequestOrder {
    /** The creator: the oldest request first (it has waited the longest). A request not dated yet comes last. */
    fun forCreator(requests: List<PlaceRequest>): List<PlaceRequest> =
        requests.sortedWith(compareBy<PlaceRequest> { it.createdAtMillis ?: Long.MAX_VALUE }.thenBy { it.id })

    /** The requester: the newest request on top (what was just sent is what is looked for). */
    fun forMember(requests: List<PlaceRequest>): List<PlaceRequest> =
        requests.sortedWith(compareByDescending<PlaceRequest> { it.createdAtMillis ?: Long.MAX_VALUE }.thenBy { it.id })
}

/** Which of the three slots of a member is free. */
object PlaceRequestSlots {
    /** The first free slot among the requests that EXIST (an expired one still occupies its slot until it is deleted), or null when the three are taken. */
    fun firstFree(
        uid: String,
        existing: Collection<PlaceRequest>,
    ): Int? {
        val taken = existing.filter { it.requestedBy == uid }.map { it.id }.toSet()
        return PlaceRequestLimits.SLOTS.firstOrNull { PlaceRequestLimits.idOf(uid, it) !in taken }
    }
}

/** The lots of ticket LIEU-REQ, as plain data: tested without Firestore, applied by one transaction each. */
object PlaceRequestWrites {
    fun requestPath(
        miffId: String,
        requestId: String,
    ): String = "miffs/$miffId/placeRequests/$requestId"

    fun counterPath(miffId: String): String = "miffs/$miffId/meta/placeRequests"

    /** A whole new request: every key, as `validRequestShape` wants it. */
    fun requestDocument(
        uid: String,
        draft: PlaceRequestDraft,
    ): Map<String, Any?> =
        mapOf(
            "requestedBy" to uid,
            "name" to PlaceName.stored(draft.name),
            "type" to draft.type.value,
            "address" to PlaceAddressText.stored(draft.address),
            "lat" to PlaceCoordinates.round5(draft.lat),
            "lng" to PlaceCoordinates.round5(draft.lng),
            "radiusM" to draft.radiusM,
            "createdAt" to FieldValue.serverTimestamp(),
        )

    /**
     * The counter of the Miff as a MEMBER moves it: `increment(delta)` merged into the document (so that it works whether or not the document exists yet),
     * tied to THIS request by `lastReqId` (the rules refuse a move that is not tied to a request created or deleted in the same lot).
     */
    fun memberCounter(
        requestId: String,
        delta: Long,
    ): Map<String, Any?> =
        mapOf(
            "pending" to FieldValue.increment(delta),
            "lastReqId" to requestId,
            "updatedAt" to FieldValue.serverTimestamp(),
        )

    /** The counter as the CREATOR writes it: the real number of requests it reads (this also corrects a drift), no request attached. */
    fun creatorCounter(pending: Int): Map<String, Any?> =
        mapOf(
            "pending" to pending.coerceIn(0, PlaceRequestLimits.PER_MIFF),
            "lastReqId" to null,
            "updatedAt" to FieldValue.serverTimestamp(),
        )

    /** ONE lot of 2: the request and the counter (+1). */
    fun create(
        uid: String,
        miffId: String,
        slot: Int,
        draft: PlaceRequestDraft,
    ): List<DocWrite> {
        val id = PlaceRequestLimits.idOf(uid, slot)
        return listOf(
            DocWrite(requestPath(miffId, id), WriteKind.SET, requestDocument(uid, draft)),
            DocWrite(counterPath(miffId), WriteKind.MERGE, memberCounter(id, 1)),
        )
    }

    /** ONE lot of 2: the request is deleted by its author and the counter goes down by one. */
    fun withdraw(
        miffId: String,
        requestId: String,
    ): List<DocWrite> =
        listOf(
            DocWrite(requestPath(miffId, requestId), WriteKind.DELETE),
            DocWrite(counterPath(miffId), WriteKind.MERGE, memberCounter(requestId, -1)),
        )
}

/**
 * The request as the place it would become, for the screens that already know how to draw a place (the detail: map, circle, address, radius, type). It is
 * NOT a place of the Miff: it has no category and its identifier is the one of the request.
 */
fun PlaceRequest.asPlace(): Place =
    Place(
        id = id,
        miffId = miffId,
        name = name,
        type = type,
        category = null,
        address = address,
        lat = lat,
        lng = lng,
        radiusM = radiusM,
        createdBy = requestedBy,
    )
