package com.tomessa.miff.data.miff

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.tomessa.miff.domain.miff.DocWrite
import com.tomessa.miff.domain.miff.MembershipRef
import com.tomessa.miff.domain.miff.MiffDetails
import com.tomessa.miff.domain.miff.ProfileCopy
import com.tomessa.miff.domain.miff.WriteKind
import kotlinx.coroutines.tasks.await

/*
 * SECURITY, for this file: names, codes and profiles pass through here and go nowhere else. Nothing is logged; the errors that
 * leave this file are the Firestore ones, which the repository reduces to a MiffFailure before anything is shown.
 */

/** `users/{uid}` is missing, or has no valid phone number: the card of a member cannot be written. */
class MissingProfileException : Exception("profile")

/** What `get miffs/{id}` said. A refusal of the rules is an exception (PERMISSION_DENIED), not a value. */
sealed interface MiffRead {
    class Exists(
        val details: MiffDetails,
    ) : MiffRead {
        override fun toString(): String = "Exists(redacted)"
    }

    /** The server answered, and there is no such Miff (deleted). */
    data object Missing : MiffRead
}

/** `entitlements/{uid}`: only what the client needs to choose the key of the index. */
data class EntitlementRead(
    val active: Boolean,
    val expiresAtMillis: Long?,
)

/**
 * The Firestore operations behind [FirestoreMiffRepository]. Writes are TRANSACTIONS (like the profile ones, see
 * [com.tomessa.miff.data.auth.UserProfileRepository]): a plain batch made without network is queued and leaves later, by
 * itself; a transaction only exists online, so "no confirmation" always means "nothing written" (ticket Miff 5.11, R4).
 * Behind an interface so that the logic is tested with a fake.
 */
interface MiffStore {
    /** `joinCodes/{code}`: miffId and miffName, or null if there is no such code. */
    suspend fun readJoinCode(code: String): Map<String, Any?>?

    /** `users/{uid}/memberships`, from the SERVER when [fromServer] (throws offline), else wherever Firestore has it. */
    suspend fun readMemberships(
        uid: String,
        fromServer: Boolean,
    ): List<MembershipRef>

    suspend fun readMiff(
        miffId: String,
        fromServer: Boolean,
    ): MiffRead

    suspend fun readEntitlement(uid: String): EntitlementRead?

    /** A read that only the person can do and that always works when the server is reachable and App Check is fine. */
    suspend fun serverReachable(uid: String): Boolean

    /** ONE transaction: the profile is read first, [build] makes the lot from it (it may be called again if Firestore retries). */
    suspend fun commitWithProfile(
        uid: String,
        build: (ProfileCopy) -> List<DocWrite>,
    )

    /** ONE transaction with these writes. */
    suspend fun commit(writes: List<DocWrite>)
}

class FirestoreMiffStore(
    private val firestore: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() },
) : MiffStore {
    // The instance is resolved at each use: it is replaced after a cache purge.
    private fun source(fromServer: Boolean) = if (fromServer) Source.SERVER else Source.DEFAULT

    override suspend fun readJoinCode(code: String): Map<String, Any?>? {
        val snapshot =
            firestore()
                .collection("joinCodes")
                .document(code)
                .get(Source.SERVER)
                .await()
        return if (snapshot.exists()) snapshot.data else null
    }

    override suspend fun readMemberships(
        uid: String,
        fromServer: Boolean,
    ): List<MembershipRef> =
        firestore()
            .collection("users")
            .document(uid)
            .collection("memberships")
            .get(source(fromServer))
            .await()
            .documents
            // The Miff is the FIELD `miffId`, never the id of the document ("free" for a free account).
            .mapNotNull { doc -> doc.getString("miffId")?.takeIf(::isMiffId)?.let { MembershipRef(doc.id, it) } }

    override suspend fun readMiff(
        miffId: String,
        fromServer: Boolean,
    ): MiffRead {
        val snapshot =
            firestore()
                .collection("miffs")
                .document(miffId)
                .get(source(fromServer))
                .await()
        return detailsOf(snapshot)?.let { MiffRead.Exists(it) } ?: MiffRead.Missing
    }

    override suspend fun readEntitlement(uid: String): EntitlementRead? {
        val snapshot =
            firestore()
                .collection("entitlements")
                .document(uid)
                .get()
                .await()
        if (!snapshot.exists()) return null
        return EntitlementRead(
            active = snapshot.getBoolean("active") == true,
            expiresAtMillis = (snapshot.get("expiresAt") as? Timestamp)?.toDate()?.time,
        )
    }

    override suspend fun serverReachable(uid: String): Boolean =
        try {
            firestore()
                .collection("users")
                .document(uid)
                .get(Source.SERVER)
                .await()
            true
        } catch (e: FirebaseFirestoreException) {
            false
        }

    override suspend fun commitWithProfile(
        uid: String,
        build: (ProfileCopy) -> List<DocWrite>,
    ) {
        val db = firestore()
        val profileRef = db.collection("users").document(uid)
        db
            .runTransaction { transaction ->
                val profile = ProfileCopy.from(transaction.get(profileRef).data) ?: throw MissingProfileException()
                applyWrites(db, transaction, build(profile))
            }.await()
    }

    override suspend fun commit(writes: List<DocWrite>) {
        val db = firestore()
        db.runTransaction { transaction -> applyWrites(db, transaction, writes) }.await()
    }

    private fun applyWrites(
        db: FirebaseFirestore,
        transaction: com.google.firebase.firestore.Transaction,
        writes: List<DocWrite>,
    ) {
        writes.forEach { write ->
            val ref = db.document(write.path)
            when (write.kind) {
                WriteKind.SET -> transaction.set(ref, write.data)
                WriteKind.UPDATE -> transaction.update(ref, write.data)
                WriteKind.DELETE -> transaction.delete(ref)
            }
        }
    }

    companion object {
        private val MIFF_ID = Regex("^[A-Za-z0-9]{20}$")

        /** An identifier read from the server is checked before it becomes part of a path (same shape as the rules). */
        fun isMiffId(value: String): Boolean = MIFF_ID.matches(value)

        /** The Miff of a snapshot, if it exists and has the shape the rules require; null otherwise. */
        fun detailsOf(snapshot: DocumentSnapshot): MiffDetails? {
            if (!snapshot.exists()) return null
            val name = snapshot.getString("name") ?: return null
            val code = snapshot.getString("joinCode") ?: return null
            val owner = snapshot.getString("ownerUid") ?: return null
            return MiffDetails(snapshot.id, name, code, owner)
        }
    }
}
