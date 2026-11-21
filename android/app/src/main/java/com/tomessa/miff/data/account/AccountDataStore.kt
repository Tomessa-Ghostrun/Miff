package com.tomessa.miff.data.account

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.tomessa.miff.data.notification.JournalGateway
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

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
     * that does not have the shape of an index: nothing may be left behind a profile that is deleted. A STALE index (its Miff deleted, the
     * person removed) counts too: AUTH-5b cleans those first.
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
    /** ALERTE (BuildConfig.ALERT_DANGER): the daily counter `users/{uid}/quota/alerts` goes with the profile, in the same transaction (backend/data-model.md 2.20). False: nothing added. */
    private val alertsEnabled: Boolean = false,
    /** CNC-A: the journal of the notifications `users/{uid}/notifications`, emptied before the profile when it is given (null while its switch is off). Never throws. */
    private val journal: JournalGateway? = null,
    /**
     * SRV-8A (premium purchase): asks the server to delete the purchase id and the links of billing of the account (`supprimerDonneesAbonnement`; it needs the sign-in, so it
     * runs BEFORE the profile and the authentication account). Returns false on a failure, never throws; null while the purchase is switched off. It does NOT cancel the
     * subscription in the store (the information screen says so). A failure is TOLERATED: what is left is purged by the TTL rules and holds no personal data.
     */
    private val subscriptionData: (suspend () -> Boolean)? = null,
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
        // Billing first (it needs the sign-in): bounded, tolerated, nothing to do when the purchase is switched off.
        subscriptionData?.let { purge -> withTimeoutOrNull(SUBSCRIPTION_PURGE_TIMEOUT_MS) { purge() } }
        // Before `users/{uid}` (contract 2.21): the entries of the journal. Tolerated: it never throws, and what is left is deleted by the TTL and readable by nobody.
        journal?.deleteAll(uid)
        val db = firestore()
        val ref = db.collection("users").document(uid)
        val quota = ref.collection("quota").document("alerts")
        deleteProfileTolerantly(
            withQuota = alertsEnabled,
            deleteWithQuota = {
                db
                    .runTransaction { transaction ->
                        transaction.delete(quota)
                        transaction.delete(ref)
                    }.await()
            },
            deleteProfileOnly = { db.runTransaction { transaction -> transaction.delete(ref) }.await() },
            refused = {
                it is com.google.firebase.firestore.FirebaseFirestoreException &&
                    it.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED
            },
        )
    }
}

/** The purge of the billing data is a courtesy of the deletion, not a condition of it: it never holds the deletion for long. */
private const val SUBSCRIPTION_PURGE_TIMEOUT_MS = 8_000L

/**
 * ALERTE (backend/data-model.md 2.20): the daily counter of the alerts goes with the profile, in the same transaction, when the function is on. The rules may REFUSE that deletion
 * (the counter can only be deleted when its three slots are empty or 24 h old, so a person who started an alert in the last 24 hours cannot delete it; or the rules are not
 * deployed yet): it is then TOLERATED. The orphan counter is purged by the TTL rule on `expireAt`, it holds only dates and a Miff identifier, and the deletion of the account goes on
 * with the profile alone, with no visible error. Any other failure is the caller's, as before.
 */
internal suspend fun deleteProfileTolerantly(
    withQuota: Boolean,
    deleteWithQuota: suspend () -> Unit,
    deleteProfileOnly: suspend () -> Unit,
    refused: (Throwable) -> Boolean,
) {
    if (withQuota) {
        try {
            deleteWithQuota()
            return
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!refused(e)) throw e
        }
    }
    deleteProfileOnly()
}
