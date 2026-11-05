package com.tomessa.miff.data.account

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await

/*
 * SECURITY, for this file: only the account's own documents (`users/{uid}` and its sub-collections) are touched, always with the
 * id of the account that is signed in; the rules (`isSelf`) refuse anything else. Identifiers pass through here and go nowhere
 * else: no log, no event. Every write is a TRANSACTION (like the profile and Miff ones): a plain write made without network is
 * queued and leaves later by itself; a transaction only exists online, so "no confirmation" always means "nothing was written".
 */

/**
 * What the deletion of the account needs from Firestore, besides the Miffs (AUTH-5b): the indexes `memberships`, the devices
 * and the profile. Behind an interface so that the procedure is tested with a fake, without Firestore.
 */
interface AccountDataStore {
    /**
     * How many documents `users/{uid}/memberships` holds, read from the SERVER (throws offline). EVERY document counts, even one
     * that does not have the shape of an index: nothing may be left behind a profile that is deleted.
     */
    suspend fun membershipCount(uid: String): Int

    /** The ids of `users/{uid}/devices`, read from the SERVER. Empty as long as there is no push. */
    suspend fun deviceIds(uid: String): List<String>

    /** One confirmed transaction: the device document is deleted (a document that is already gone is no error). */
    suspend fun deleteDevice(
        uid: String,
        deviceId: String,
    )

    /** One confirmed transaction: `users/{uid}` is deleted (a profile that is already gone is no error). */
    suspend fun deleteProfile(uid: String)
}

class FirestoreAccountDataStore(
    private val firestore: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() },
) : AccountDataStore {
    // The instance is resolved at each use: it is replaced after a cache purge.
    private fun user(uid: String) = firestore().collection("users").document(uid)

    override suspend fun membershipCount(uid: String): Int =
        user(uid)
            .collection("memberships")
            .get(Source.SERVER)
            .await()
            .size()

    override suspend fun deviceIds(uid: String): List<String> =
        user(uid)
            .collection("devices")
            .get(Source.SERVER)
            .await()
            .documents
            .map { it.id }

    override suspend fun deleteDevice(
        uid: String,
        deviceId: String,
    ) {
        val db = firestore()
        val ref =
            db
                .collection("users")
                .document(uid)
                .collection("devices")
                .document(deviceId)
        db.runTransaction { transaction -> transaction.delete(ref) }.await()
    }

    override suspend fun deleteProfile(uid: String) {
        val db = firestore()
        val ref = db.collection("users").document(uid)
        db.runTransaction { transaction -> transaction.delete(ref) }.await()
    }
}
