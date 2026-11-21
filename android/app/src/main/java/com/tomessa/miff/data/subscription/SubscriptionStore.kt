package com.tomessa.miff.data.subscription

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.tomessa.miff.domain.subscription.EntitlementDocument
import com.tomessa.miff.domain.subscription.SubscriptionStatus
import com.tomessa.miff.domain.subscription.interpretSubscription
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Reads the data of `entitlements/{uid}` (null = no document). Only the EXACT expected types count: `active` a boolean (the string
 * "true" is not one), `expiresAt` a Firestore timestamp (a text "2027-01-12", an integer or a double typed by hand in the console is
 * not one), `source`, `state` and `plan` strings, `trial`, `willRenew` and `test` booleans. Anything else reads as a missing field, never as an error; an unknown extra field is
 * ignored (the server may add fields: a document with fields this version does not know never breaks the screen, criterion AN7). The document is written by the SERVER only (a real
 * purchase) or by hand (premium-manuel.md): the app never writes it.
 */
fun entitlementFrom(data: Map<String, Any?>?): EntitlementDocument? {
    if (data == null) return null
    return EntitlementDocument(
        active = data["active"] as? Boolean,
        expiresAtMillis = (data["expiresAt"] as? Timestamp)?.toDate()?.time,
        source = data["source"] as? String,
        state = data["state"] as? String,
        plan = data["plan"] as? String,
        trial = data["trial"] as? Boolean,
        willRenew = data["willRenew"] as? Boolean,
        test = data["test"] as? Boolean,
    )
}

/** The Firestore read behind [SubscriptionRepository], behind an interface so that the logic is tested with a fake. */
interface SubscriptionStore {
    /** The document, or null if there is none. Throws when the server cannot be reached or refuses. */
    suspend fun read(uid: String): EntitlementDocument?
}

/**
 * ONE `get` of `entitlements/{uid}` from the SERVER (rules: `allow get: if isSelf(uid)`, no list, no write; the app never writes it).
 * From the server and not from the cache: a copy left on the phone could show a subscription that has since ended. No listener.
 */
class FirestoreSubscriptionStore(
    private val firestore: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() },
) : SubscriptionStore {
    override suspend fun read(uid: String): EntitlementDocument? {
        val snapshot =
            firestore()
                .collection("entitlements")
                .document(uid)
                .get(Source.SERVER)
                .await()
        return entitlementFrom(snapshot.data)
    }
}

/**
 * What the app knows of the Miff+ of the signed-in account (tickets ABO-1 and premium-achat-reel.md section 8): ONE read of `entitlements/{uid}` each time it is asked (one Firestore
 * document read, free tier), no listener, no retry loop. The callers ask when the Settings open, when the offer opens, after a purchase or a restoration, and at the opening of
 * the app (spaced).
 */
class SubscriptionRepository(
    private val store: SubscriptionStore,
    private val currentUid: () -> String?,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val timeoutMillis: Long = READ_TIMEOUT_MILLIS,
) {
    /**
     * The status, or null when it cannot be read: offline, refused, too slow, signed out, or another account by the time the answer comes. NOT the same as
     * [SubscriptionStatus.Free] (no document: the server said so): a failed read is never taken for "free", so that a screen never tells a subscriber that nothing is
     * there. A failure is not announced as an error to the person by this class; the caller chooses a calm wording.
     */
    suspend fun load(): SubscriptionStatus? {
        val uid = currentUid() ?: return null
        val read =
            try {
                // A phone with no network may wait before failing: after this, the screen is not held up.
                withTimeoutOrNull(timeoutMillis) { Read(store.read(uid)) } ?: return null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return null
            }
        if (currentUid() != uid) return null
        return interpretSubscription(read.document, nowMillis())
    }

    private class Read(
        val document: EntitlementDocument?,
    )

    companion object {
        const val READ_TIMEOUT_MILLIS = 8_000L
    }
}
