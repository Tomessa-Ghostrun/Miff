package com.tomessa.miff.data.place

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.tomessa.miff.data.miff.FirestoreMiffStore
import com.tomessa.miff.data.miff.MiffStore
import com.tomessa.miff.domain.miff.DocWrite
import com.tomessa.miff.domain.miff.MiffName
import com.tomessa.miff.domain.miff.WriteKind
import com.tomessa.miff.domain.place.PlaceRequest
import com.tomessa.miff.domain.place.RequesterCard
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/*
 * SECURITY and COST, for this file: the requests (name, address, coordinates) pass through here and go nowhere else; nothing is logged and no
 * exception message leaves (the repository reduces every failure to a MiffFailure). The identifiers are checked before they become part of a path.
 * COST: a member reads ONLY its own requests (a query filtered on `requestedBy`, which the rules require), at most 3 documents; the creator listens to
 * the requests of ONE Miff (10 documents at most), only while the app is in front, and reads the card of each person who asked ONCE per session.
 */

/** The requests of a Miff as a listener gave them; [fromCache] while the server has not answered yet. */
class RequestsSnapshot(
    val requests: List<PlaceRequest>,
    val fromCache: Boolean,
) {
    override fun toString(): String = "RequestsSnapshot(redacted)"
}

/** The card of the author of a request, read from `members/{uid}` of the Miff. */
sealed interface RequesterRead {
    class Found(
        val card: RequesterCard,
    ) : RequesterRead {
        override fun toString(): String = "Found(redacted)"
    }

    /** The server answered and there is no such card: the person is no longer in the Miff. */
    data object NotMember : RequesterRead
}

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

    /** The creator: every request of [miffId], live (`placeRequests` is readable by the creator only). The flow ends with the exception on a refusal. */
    fun observeAll(miffId: String): Flow<RequestsSnapshot>

    /** The creator: every request of [miffId], once (the phone's copy when the server is out of reach). */
    suspend fun readAll(miffId: String): List<PlaceRequest>

    /** The creator: one request, from the SERVER (null: it does not exist any more). */
    suspend fun readOne(
        miffId: String,
        requestId: String,
    ): PlaceRequest?

    /** The creator: who asked. From the server (an answer of the cache is never "not a member"); throws when it cannot say. */
    suspend fun readRequester(
        miffId: String,
        uid: String,
    ): RequesterRead

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

    private fun requests(miffId: String) =
        firestore()
            .collection("miffs")
            .document(miffId)
            .collection("placeRequests")

    override fun observeAll(miffId: String): Flow<RequestsSnapshot> =
        callbackFlow {
            if (!FirestoreMiffStore.isMiffId(miffId)) {
                close(IllegalArgumentException("bad miff id"))
                return@callbackFlow
            }
            val registration =
                requests(miffId).addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                    } else if (snapshot != null) {
                        val parsed = snapshot.documents.mapNotNull { PlaceRequest.fromServer(it.id, miffId, it.data) }
                        trySend(RequestsSnapshot(parsed, snapshot.metadata.isFromCache))
                    }
                }
            awaitClose { registration.remove() }
        }

    override suspend fun readAll(miffId: String): List<PlaceRequest> {
        if (!FirestoreMiffStore.isMiffId(miffId)) return emptyList()
        return requests(miffId)
            .get()
            .await()
            .documents
            .mapNotNull { PlaceRequest.fromServer(it.id, miffId, it.data) }
    }

    override suspend fun readOne(
        miffId: String,
        requestId: String,
    ): PlaceRequest? {
        if (!FirestoreMiffStore.isMiffId(miffId)) return null
        val snapshot = requests(miffId).document(requestId).get(Source.SERVER).await()
        return if (snapshot.exists()) PlaceRequest.fromServer(snapshot.id, miffId, snapshot.data) else null
    }

    override suspend fun readRequester(
        miffId: String,
        uid: String,
    ): RequesterRead {
        if (!FirestoreMiffStore.isMiffId(miffId)) throw IllegalArgumentException("bad miff id")
        val snapshot =
            firestore()
                .collection("miffs")
                .document(miffId)
                .collection("members")
                .document(uid)
                .get()
                .await()
        if (!snapshot.exists()) {
            // A card missing from the PHONE'S COPY proves nothing: only the server can say that the person left.
            if (snapshot.metadata.isFromCache) throw IllegalStateException("not answered by the server")
            return RequesterRead.NotMember
        }
        val first = MiffName.fromServer(snapshot.getString("firstName").orEmpty()).orEmpty()
        val last = MiffName.fromServer(snapshot.getString("lastName").orEmpty()).orEmpty()
        return RequesterRead.Found(
            RequesterCard.of(uid, first, last, snapshot.getBlob("avatar")?.toBytes()),
        )
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
