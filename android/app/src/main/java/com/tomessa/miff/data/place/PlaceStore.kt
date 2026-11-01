package com.tomessa.miff.data.place

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.tomessa.miff.data.miff.EntitlementRead
import com.tomessa.miff.data.miff.FirestoreMiffStore
import com.tomessa.miff.data.miff.MiffRead
import com.tomessa.miff.data.miff.MiffStore
import com.tomessa.miff.domain.miff.DocWrite
import com.tomessa.miff.domain.place.Place
import kotlinx.coroutines.tasks.await

/*
 * SECURITY, for this file: places (name, address, coordinates) and the list of Miffs of the person pass through here and go
 * nowhere else. Nothing is logged; the errors that leave this file are the Firestore ones, which the repository reduces to a
 * MiffFailure before anything is shown.
 */

/** A line of `users/{uid}/memberships`: which Miff, and when the person joined it (0 when the document has no date). */
class PlaceMembership(
    val miffId: String,
    val joinedAtMillis: Long,
) {
    override fun toString(): String = "PlaceMembership(redacted)"
}

/**
 * The Firestore operations behind [FirestorePlaceRepository]. Reads that decide a limit come from the SERVER (an old copy would
 * let the wrong slot be chosen); the writes are TRANSACTIONS (the ones of [MiffStore]): nothing is queued while offline, so
 * "no answer" always means "nothing written" (ticket 6.1, PL9). Behind an interface so that the logic is tested with a fake.
 */
interface PlaceStore {
    suspend fun readEntitlement(uid: String): EntitlementRead?

    /** `users/{uid}/memberships` from the SERVER (throws offline). */
    suspend fun readMemberships(uid: String): List<PlaceMembership>

    /** `get miffs/{id}` from the SERVER. */
    suspend fun readMiff(miffId: String): MiffRead

    /** `miffs/{id}/places` from the SERVER; documents that cannot be a place of this app are left out. */
    suspend fun readPlaces(miffId: String): List<Place>

    /** ONE transaction with these writes (at most 10 documents here, see `PlaceWrites.change`). */
    suspend fun commit(writes: List<DocWrite>)
}

class FirestorePlaceStore(
    private val base: MiffStore,
    private val firestore: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() },
) : PlaceStore {
    override suspend fun readEntitlement(uid: String): EntitlementRead? = base.readEntitlement(uid)

    override suspend fun readMemberships(uid: String): List<PlaceMembership> =
        firestore()
            .collection("users")
            .document(uid)
            .collection("memberships")
            .get(Source.SERVER)
            .await()
            .documents
            // The Miff is the FIELD `miffId`, never the id of the document ("free" for a free account).
            .mapNotNull { doc ->
                val miffId = doc.getString("miffId")?.takeIf(FirestoreMiffStore::isMiffId) ?: return@mapNotNull null
                PlaceMembership(miffId, (doc.get("joinedAt") as? Timestamp)?.toDate()?.time ?: 0L)
            }

    override suspend fun readMiff(miffId: String): MiffRead = base.readMiff(miffId, fromServer = true)

    override suspend fun readPlaces(miffId: String): List<Place> =
        firestore()
            .collection("miffs")
            .document(miffId)
            .collection("places")
            .get(Source.SERVER)
            .await()
            .documents
            .mapNotNull { Place.fromServer(it.id, miffId, it.data) }

    override suspend fun commit(writes: List<DocWrite>) = base.commit(writes)
}
