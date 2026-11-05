package com.tomessa.miff.data.miff

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.tomessa.miff.domain.miff.DocWrite
import com.tomessa.miff.domain.miff.JoinCode
import com.tomessa.miff.domain.miff.MembershipRef
import com.tomessa.miff.domain.miff.MiffDetails
import com.tomessa.miff.domain.miff.MiffName
import com.tomessa.miff.domain.miff.ProfileCopy
import com.tomessa.miff.domain.miff.WriteKind
import kotlinx.coroutines.tasks.await

/*
 * SECURITY, for this file: names, codes and profiles pass through here and go nowhere else. Nothing is logged; the errors that
 * leave this file are the Firestore ones, which the repository reduces to a MiffFailure before anything is shown.
 */

/** `users/{uid}` is missing, or has no valid phone number: the card of a member cannot be written. */
class MissingProfileException : Exception("profile")

/** The Miff does not exist (or can no longer be read): a lot that needed to read it cannot be built. */
class MiffGoneException : Exception("miff gone")

/** What a transaction read of a Miff: the Miff itself and whether the document of its CURRENT code is there. */
class MiffTxView(
    val details: MiffDetails,
    val codeDocumentExists: Boolean,
) {
    override fun toString(): String = "MiffTxView(redacted)"
}

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

/** What MY card says about the consent: whether I share, and the version of the text I accepted (null if the card has none). */
data class SharingConsentRead(
    val enabled: Boolean,
    val version: String?,
)

/** `entitlements/{uid}`: only what the client needs to choose the key of the index. */
data class EntitlementRead(
    val active: Boolean,
    val expiresAtMillis: Long?,
    /**
     * True when the answer comes from the phone's cache (no answer of the server): a copy that may be old, and a missing document
     * in the cache proves nothing. The plan is then UNKNOWN, still usable as the key of the index offline (limit F1).
     */
    val fromCache: Boolean = false,
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

    /** Whether MY card says I share, read from the SERVER (to check a lot whose answer was lost); null if there is no card. */
    suspend fun readSharing(
        miffId: String,
        uid: String,
    ): Boolean?

    /**
     * My consent as my card says it (LOC-2a: is the version of the text I accepted an old one?), read from the SERVER; null if there is
     * no card. One read of rules (`isMember`, `miffAlive`) and one document.
     */
    suspend fun readSharingConsent(
        miffId: String,
        uid: String,
    ): SharingConsentRead?

    /**
     * ONE transaction (LOC-2a, renewal of the consent): my card is READ in each of [miffIds] inside the transaction, and [build] makes the lot
     * for the ones where I STILL share (`sharing.enabled` is true) only; nothing is written for the others (a sharing stopped meanwhile, on
     * another phone, is never turned back on by a renewal). Returns the ids that were written (empty: nothing was written).
     */
    suspend fun commitWhereSharing(
        uid: String,
        miffIds: List<String>,
        build: (List<String>) -> List<DocWrite>,
    ): List<String>

    /** A read that only the person can do and that always works when the server is reachable and App Check is fine. */
    suspend fun serverReachable(uid: String): Boolean

    /** The ids of the cards of `miffs/{id}/members`, read from the SERVER (the deletion of a Miff must not rely on a cache). */
    suspend fun readMemberIds(miffId: String): List<String>

    /** The ids of `miffs/{id}/places`, read from the SERVER. */
    suspend fun readPlaceIds(miffId: String): List<String>

    /**
     * ONE transaction: the Miff (and whether its code document exists) is read first, [build] makes the lot from what was read
     * (it may be called again if Firestore retries because the Miff changed in the meantime: a code renewed by another phone is
     * never replaced from a stale copy). A Miff that does not exist throws [MiffGoneException].
     */
    suspend fun commitWithMiff(
        miffId: String,
        build: (MiffTxView) -> List<DocWrite>,
    )

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

    override suspend fun readSharing(
        miffId: String,
        uid: String,
    ): Boolean? {
        val snapshot =
            firestore()
                .collection("miffs")
                .document(miffId)
                .collection("members")
                .document(uid)
                .get(Source.SERVER)
                .await()
        return if (snapshot.exists()) snapshot.getBoolean("sharing.enabled") else null
    }

    override suspend fun readSharingConsent(
        miffId: String,
        uid: String,
    ): SharingConsentRead? {
        val snapshot =
            firestore()
                .collection("miffs")
                .document(miffId)
                .collection("members")
                .document(uid)
                .get(Source.SERVER)
                .await()
        if (!snapshot.exists()) return null
        return SharingConsentRead(
            enabled = snapshot.getBoolean("sharing.enabled") == true,
            version = snapshot.getString("sharing.consentVersion"),
        )
    }

    override suspend fun commitWhereSharing(
        uid: String,
        miffIds: List<String>,
        build: (List<String>) -> List<DocWrite>,
    ): List<String> {
        val db = firestore()
        return db
            .runTransaction { transaction ->
                val active =
                    miffIds.filter { miffId ->
                        val card =
                            transaction.get(
                                db
                                    .collection("miffs")
                                    .document(miffId)
                                    .collection("members")
                                    .document(uid),
                            )
                        card.exists() && card.getBoolean("sharing.enabled") == true
                    }
                if (active.isNotEmpty()) applyWrites(db, transaction, build(active))
                active
            }.await()
    }

    override suspend fun readEntitlement(uid: String): EntitlementRead? {
        val snapshot =
            firestore()
                .collection("entitlements")
                .document(uid)
                .get()
                .await()
        val fromCache = snapshot.metadata.isFromCache
        // A missing document is "free" ONLY when the server said so; from the cache it is reported as an unknown plan.
        if (!snapshot.exists()) {
            return if (fromCache) {
                EntitlementRead(
                    active = false,
                    expiresAtMillis = null,
                    fromCache = true,
                )
            } else {
                null
            }
        }
        return EntitlementRead(
            active = snapshot.getBoolean("active") == true,
            expiresAtMillis = (snapshot.get("expiresAt") as? Timestamp)?.toDate()?.time,
            fromCache = fromCache,
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

    override suspend fun readMemberIds(miffId: String): List<String> =
        firestore()
            .collection("miffs")
            .document(miffId)
            .collection("members")
            .get(Source.SERVER)
            .await()
            .documents
            .map { it.id }

    override suspend fun readPlaceIds(miffId: String): List<String> =
        firestore()
            .collection("miffs")
            .document(miffId)
            .collection("places")
            .get(Source.SERVER)
            .await()
            .documents
            .map { it.id }

    override suspend fun commitWithMiff(
        miffId: String,
        build: (MiffTxView) -> List<DocWrite>,
    ) {
        val db = firestore()
        val miffRef = db.collection("miffs").document(miffId)
        db
            .runTransaction { transaction ->
                val details = detailsOf(transaction.get(miffRef)) ?: throw MiffGoneException()
                // Only a well-formed code is turned into a path (it is a document id read from the server).
                val codeExists =
                    JoinCode.isValid(details.joinCode) &&
                        transaction.get(db.collection("joinCodes").document(details.joinCode)).exists()
                applyWrites(db, transaction, build(MiffTxView(details, codeExists)))
            }.await()
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
            // Cleaned and trimmed ([MiffName.fromServer]); a name with nothing visible left stays "" (the screen shows its loading title).
            val name = MiffName.fromServer(snapshot.getString("name") ?: return null).orEmpty()
            val code = snapshot.getString("joinCode") ?: return null
            val owner = snapshot.getString("ownerUid") ?: return null
            return MiffDetails(snapshot.id, name, code, owner)
        }
    }
}
