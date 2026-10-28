package com.tomessa.miff.data.auth

import com.google.firebase.firestore.FirebaseFirestore
import com.tomessa.miff.data.privacy.BooleanStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Local Firestore cache purge at sign-out, account deletion or refusal of the consent panel (ticket 7.6, contract of
 * backend/data-model.md section 8 "Purge du cache local"). Nothing of a Miff may stay readable offline afterwards.
 */
interface CachePurger {
    /** Lets queued writes leave BEFORE the sign-out (signed out, the rules would refuse them). Short delay. */
    suspend fun flushPendingWrites()

    /**
     * `terminate()` then `clearPersistence()`, with a "purge pending" flag around it. True when done.
     * On failure the flag stays: [purgeIfPending] retries at the next start, before any other Firestore access.
     */
    suspend fun purge(): Boolean

    /** Writes the "purge to do" flag at once, at the very start of a sign-out / refusal / session loss. */
    fun markPurgePending()

    suspend fun purgeIfPending()
}

class FirestoreCachePurger(
    private val flags: BooleanStore,
    private val firestore: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() },
) : CachePurger {
    override suspend fun flushPendingWrites() {
        try {
            withTimeoutOrNull(FLUSH_TIMEOUT_MS) { firestore().waitForPendingWrites().await() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Best effort: the sign-out must go on.
        }
    }

    override suspend fun purge(): Boolean {
        flags.put(KEY_PURGE_PENDING, true)
        return try {
            // Same instance for both calls: clearPersistence() is only allowed on a terminated (or not started) client.
            val client = firestore()
            client.terminate().await()
            client.clearPersistence().await()
            flags.put(KEY_PURGE_PENDING, false)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    override fun markPurgePending() {
        flags.put(KEY_PURGE_PENDING, true)
    }

    override suspend fun purgeIfPending() {
        if (flags.get(KEY_PURGE_PENDING, false)) purge()
    }

    companion object {
        const val KEY_PURGE_PENDING = "firestore_purge_pending"
        private const val FLUSH_TIMEOUT_MS = 5_000L
    }
}
