package com.tomessa.miff.data.update

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.tomessa.miff.domain.miff.DocWrite
import com.tomessa.miff.domain.miff.MiffSharingConsent
import com.tomessa.miff.domain.miff.WriteKind
import kotlinx.coroutines.tasks.await

/*
 * SECURITY / COST, for this file:
 * - A write of the Update is a TRANSACTION, never a batch: a batch made without network is queued and leaves LATER, by itself, which would be a "publication
 *   fantôme" (an update that says "all is well" hours after the fact, ticket R9). A transaction only exists online: "no confirmation" always means "nothing
 *   was written" (criterion U3).
 * - MY card is READ inside the transaction, in each Miff of the lot: nothing is written where I no longer share (a sharing stopped meanwhile on another
 *   phone is never undone) nor where the text of consent I accepted does not cover the last known location (version 03 or later: the rules refuse the
 *   photo then, and so would the whole lot). Cost: ONE read per Miff per publication (a few a day), on top of the 3 reads of rules per Miff.
 * - Nothing is logged; what leaves this file is a [PhotoCommit] (identifiers of Miffs only) or an exception that the caller reduces to a calm sentence.
 */

/** What a transaction of the Update did, per Miff of the lot. */
class PhotoCommit(
    /** Written. */
    val written: List<String>,
    /** My card says I no longer share (or has no card): nothing written there. */
    val notSharing: List<String>,
    /** I share, but under a text of consent that does not cover the position: nothing written there. */
    val needsAgreement: List<String>,
) {
    override fun toString(): String = "PhotoCommit(redacted)"
}

interface PhotoStore {
    /**
     * ONE transaction for [miffIds] (5 at most: 3 reads of rules per Miff, 20 at most per lot). [build] receives the Miffs where the writes are allowed
     * and makes the lot; it may be called again if Firestore retries. [requireConsent]: also demands that the consent covers the position (publishing a
     * photo with its place); the clean-up of an expired photo does not.
     */
    suspend fun commit(
        uid: String,
        miffIds: List<String>,
        requireConsent: Boolean,
        build: (List<String>) -> List<DocWrite>,
    ): PhotoCommit
}

class FirestorePhotoStore(
    private val firestore: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() },
) : PhotoStore {
    override suspend fun commit(
        uid: String,
        miffIds: List<String>,
        requireConsent: Boolean,
        build: (List<String>) -> List<DocWrite>,
    ): PhotoCommit {
        val db = firestore()
        return db
            .runTransaction { transaction ->
                val allowed = mutableListOf<String>()
                val notSharing = mutableListOf<String>()
                val needsAgreement = mutableListOf<String>()
                // All the reads come before the first write (the rule of Firestore transactions).
                miffIds.forEach { miffId ->
                    val card =
                        transaction.get(
                            db
                                .collection("miffs")
                                .document(miffId)
                                .collection("members")
                                .document(uid),
                        )
                    val sharing = card.exists() && card.getBoolean("sharing.enabled") == true
                    when {
                        !sharing -> {
                            notSharing += miffId
                        }

                        requireConsent &&
                            !MiffSharingConsent.coversPosition(
                                card.getString("sharing.consentVersion"),
                            )
                        -> {
                            needsAgreement +=
                                miffId
                        }

                        else -> {
                            allowed += miffId
                        }
                    }
                }
                if (allowed.isNotEmpty()) {
                    build(allowed).forEach { write ->
                        val ref = db.document(write.path)
                        when (write.kind) {
                            WriteKind.SET -> transaction.set(ref, write.data)
                            WriteKind.UPDATE -> transaction.update(ref, write.data)
                            WriteKind.DELETE -> transaction.delete(ref)
                            WriteKind.MERGE -> transaction.set(ref, write.data, SetOptions.merge())
                        }
                    }
                }
                PhotoCommit(allowed, notSharing, needsAgreement)
            }.await()
    }
}
