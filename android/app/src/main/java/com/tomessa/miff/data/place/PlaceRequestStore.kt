package com.tomessa.miff.data.place

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.tomessa.miff.data.miff.FirestoreMiffStore
import com.tomessa.miff.data.miff.MiffStore
import com.tomessa.miff.domain.miff.DocWrite
import com.tomessa.miff.domain.miff.WriteKind
import com.tomessa.miff.domain.place.PlaceRequest
import kotlinx.coroutines.tasks.await

/*
 * SECURITY and COST, for this file: the requests (name, address, coordinates) pass through here and go nowhere else; nothing is logged and no
 * exception message leaves (the repository reduces every failure to a MiffFailure). The identifiers are checked before they become part of a path.
 * COST: a member reads ONLY its own requests (a query filtered on `requestedBy`, which the rules require), at most 3 documents.
 */

/** The Firestore operations behind [FirestorePlaceRequestRepository], behind an interface so that the logic is tested with a fake. */
interface PlaceRequestStore {
    /**
     * The requests of [uid] in [miffId] (3 at most). [fromServer] true: an answer of the server or an exception (the slot of a new request is chosen on
     * it); false: the phone's copy when the server is out of reach, which holds the requests written offline and not sent yet.
     */
    suspend fun readMine(
        miffId: String,
        uid: String,
        fromServer: Boolean,
    ): List<PlaceRequest>

    /** ONE transaction with these writes (needs the server: nothing is queued, "no answer" means "nothing written"). */
    suspend fun commit(writes: List<DocWrite>)

    /**
     * ONE batch with these writes, handed to the phone's queue: it leaves when the network is back (even after the app was closed) and the rules judge it
     * THEN (a refusal then makes the request disappear without a word, risk R10 of the ticket). Returns at once.
     */
    fun enqueue(writes: List<DocWrite>)
}

class FirestorePlaceRequestStore(
    private val base: MiffStore,
    private val firestore: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() },
) : PlaceRequestStore {
    override suspend fun readMine(
        miffId: String,
        uid: String,
        fromServer: Boolean,
    ): List<PlaceRequest> {
        if (!FirestoreMiffStore.isMiffId(miffId)) return emptyList()
        val query =
            firestore()
                .collection("miffs")
                .document(miffId)
                .collection("placeRequests")
                .whereEqualTo("requestedBy", uid)
        val snapshot = (if (fromServer) query.get(Source.SERVER) else query.get()).await()
        return snapshot.documents.mapNotNull { PlaceRequest.fromServer(it.id, miffId, it.data) }
    }

    override suspend fun commit(writes: List<DocWrite>) = base.commit(writes)

    override fun enqueue(writes: List<DocWrite>) {
        val db = firestore()
        val batch = db.batch()
        writes.forEach { write ->
            val ref = db.document(write.path)
            when (write.kind) {
                WriteKind.SET -> batch.set(ref, write.data)
                WriteKind.UPDATE -> batch.update(ref, write.data)
                WriteKind.DELETE -> batch.delete(ref)
                WriteKind.MERGE -> batch.set(ref, write.data, SetOptions.merge())
            }
        }
        // Not awaited: offline the task only ends when the server has answered. The phone keeps the batch until then.
        batch.commit()
    }
}
