package com.tomessa.miff.data.feed

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.tomessa.miff.domain.feed.MemberStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

/*
 * COST and PRIVACY, for this file: "Actualiser" (pull to refresh, decision of the PM, 2026-11-06) forces ONE fresh reading FROM THE SERVER of what feeds
 * the cards: the Miff, its members, its places and its statuses. Reads only: 0 write, 0 deletion. What is read goes into the cache of the SDK, so the
 * listeners of the Miff deliver the changes by themselves. Cost of one refresh: 1 (Miff) + N (members) + P (places) + N (statuses) documents and 8 reads of
 * the rules, i.e. 9 + 2 x members + places (about 20 for 4 members and 3 places, about 44 for 15 members and 5 places): the same as a cold opening. The view
 * model allows at most ONE refresh every 15 s, so at most 4 a minute, never a loop. Nothing read here is logged.
 */

/** What the forced reading of the statuses said. */
sealed interface ServerRead {
    /** The server answered: [statuses] are its current statuses (null when the rules refuse the reading: my own status is not published yet). */
    class Fresh(
        val statuses: Map<String, MemberStatus>?,
    ) : ServerRead {
        override fun toString(): String = "Fresh(redacted)"
    }

    /** The server could not be reached (or answered something unexpected): the Feed keeps what it has, dated, and says so calmly. */
    data object Unreachable : ServerRead
}

/** The single forced reading behind "Actualiser". */
interface FeedRefresher {
    /**
     * [readStatuses] is false while the statuses are not read at all (I do not share, or my agreement is waited for): the rules would refuse and nothing
     * is wanted, so they are not asked for.
     */
    suspend fun refresh(
        miffId: String,
        readStatuses: Boolean,
    ): ServerRead
}

class FirestoreFeedRefresher(
    private val firestore: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() },
) : FeedRefresher {
    override suspend fun refresh(
        miffId: String,
        readStatuses: Boolean,
    ): ServerRead =
        coroutineScope {
            val miff = firestore().collection("miffs").document(miffId)
            // Best effort: they only freshen the cache that the listeners of the Miff read (a failure here is not the answer of "Actualiser").
            val others =
                listOf(
                    async { attempt { miff.get(Source.SERVER).await() } },
                    async { attempt { miff.collection("members").get(Source.SERVER).await() } },
                    async { attempt { miff.collection("places").get(Source.SERVER).await() } },
                )
            val statuses =
                if (readStatuses) {
                    async {
                        try {
                            val snapshot = miff.collection("status").get(Source.SERVER).await()
                            ServerRead.Fresh(snapshot.documents.mapNotNull(::statusOf).toMap())
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            lookForRefusal(e)
                        }
                    }
                } else {
                    null
                }
            val answer = statuses?.await()
            others.forEach { it.await() }
            // Without statuses to read, reaching the server at all is what tells that the refresh went through: the Miff document is the proof.
            answer ?: miffReached(others.first().await())
        }

    /** A failure of one reading is a value, never an exception; the cancellation of the caller goes through. */
    private suspend fun <T> attempt(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    private fun lookForRefusal(error: Exception): ServerRead =
        if ((error as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
            ServerRead.Fresh(null)
        } else {
            ServerRead.Unreachable
        }

    private fun miffReached(result: Result<*>): ServerRead =
        if (result.isSuccess) ServerRead.Fresh(emptyMap()) else ServerRead.Unreachable
}
