package com.tomessa.miff.data.subscription

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.tomessa.miff.domain.subscription.SubscriptionInfo
import com.tomessa.miff.domain.subscription.interpretSubscription
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** `entitlements/{uid}` as the phone reads it. Every field is optional: the document is written by hand in the console. */
data class EntitlementDocument(
    val active: Boolean?,
    val expiresAtMillis: Long?,
    val source: String?,
)

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
        if (!snapshot.exists()) return null
        // `as?` and not `getBoolean`/`getString`: a field of an unexpected type (typed by hand) must read as "missing", never throw.
        return EntitlementDocument(
            active = snapshot.get("active") as? Boolean,
            expiresAtMillis = (snapshot.get("expiresAt") as? Timestamp)?.toDate()?.time,
            source = snapshot.get("source") as? String,
        )
    }
}

/**
 * The read-only "Abonnement" line of the Settings (ticket ABO-1): Miff+ granted by hand. ONE read per opening of the Settings
 * (one Firestore document read, free tier), no listener, no retry loop.
 */
class SubscriptionRepository(
    private val store: SubscriptionStore,
    private val currentUid: () -> String?,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val timeoutMillis: Long = READ_TIMEOUT_MILLIS,
) {
    /**
     * The active subscription, or null: no document, inactive, expired, offline, refused, too slow, signed out or another account
     * by the time the answer comes. A failure is NOT told to the person (the line stays out, calmly): the free account and an
     * unreadable one look the same, which is the honest minimum since nothing can be bought here.
     */
    suspend fun load(): SubscriptionInfo? {
        val uid = currentUid() ?: return null
        val document =
            try {
                // A phone with no network may wait before failing: after this, the line stays out and the screen is not held up.
                withTimeoutOrNull(timeoutMillis) { store.read(uid) } ?: return null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return null
            }
        if (currentUid() != uid) return null
        return interpretSubscription(
            documentExists = true,
            active = document.active,
            expiresAtMillis = document.expiresAtMillis,
            source = document.source,
            nowMillis = nowMillis(),
        )
    }

    companion object {
        const val READ_TIMEOUT_MILLIS = 8_000L
    }
}
