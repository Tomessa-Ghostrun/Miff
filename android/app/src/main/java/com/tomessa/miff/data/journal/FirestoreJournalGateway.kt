package com.tomessa.miff.data.journal

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.tomessa.miff.data.notification.JournalGateway
import com.tomessa.miff.data.notification.JournalRead
import com.tomessa.miff.domain.notification.JournalEntry
import com.tomessa.miff.domain.notification.NotificationJournal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.util.Date

/*
 * CNC-A (ticket docs/tickets/centre-notifications-complet.md, 8.3 and 8.4): the ONE place that talks to the journal of the server, `users/{uid}/notifications/{entryId}` (the notification
 * packages themselves never touch Firestore, see NotificationPrivacyTest). The rules of the server let the OWNER read a list of 100 at most and delete; nobody writes it but the functions.
 *
 * SECURITY and PRIVACY: nothing here is logged or kept in a message of an exception (it can quote a path with an identifier); every document is validated by [NotificationJournal.parse]
 * before it is used. COST: never a listener; the server only (`Source.SERVER`), 100 entries a page, 2 pages at most; a deletion is a batch sent once.
 */
class FirestoreJournalGateway(
    private val firestore: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() },
) : JournalGateway {
    private fun collection(uid: String) =
        firestore().collection("users").document(uid).collection(NotificationJournal.COLLECTION)

    override suspend fun read(
        uid: String,
        sinceMillis: Long?,
    ): JournalRead =
        try {
            withTimeout(TIMEOUT_MILLIS) {
                val base = collection(uid)
                // Contract of the server (backend/data-model.md 2.21): from a cursor, `createdAt > cursor` ASCENDING (nothing is skipped, the next request goes on from the newest seen); the
                // FIRST synchronization (no cursor) DESCENDING, the 100 most recent (ascending would read the oldest first). The `limit` is mandatory: the rules refuse a list without one.
                val ordered: Query =
                    if (sinceMillis != null) {
                        base
                            .whereGreaterThan(
                                "createdAt",
                                Timestamp(Date(sinceMillis)),
                            ).orderBy("createdAt", Query.Direction.ASCENDING)
                    } else {
                        base.orderBy("createdAt", Query.Direction.DESCENDING)
                    }
                var query: Query = ordered.limit(NotificationJournal.PAGE_SIZE.toLong())
                val entries = mutableListOf<JournalEntry>()
                for (page in 1..NotificationJournal.MAX_PAGES) {
                    // The server only: an answer from the cache of the phone would be a copy of what was already seen.
                    val snapshot = query.get(Source.SERVER).await()
                    snapshot.documents.mapNotNullTo(entries) { doc ->
                        NotificationJournal.parse(
                            doc.id,
                            doc.data.orEmpty(),
                            doc.getTimestamp("createdAt")?.toDate()?.time,
                            doc.getTimestamp("expireAt")?.toDate()?.time,
                        )
                    }
                    if (snapshot.size() < NotificationJournal.PAGE_SIZE) break
                    if (page < NotificationJournal.MAX_PAGES) query = query.startAfter(snapshot.documents.last())
                }
                JournalRead.Loaded(entries)
            }
        } catch (e: CancellationException) {
            // A timeout is a CancellationException too: it is "too slow"; the caller's own cancellation is rethrown.
            if (e is TimeoutCancellationException) JournalRead.Unavailable else throw e
        } catch (e: Exception) {
            // Nothing of the message leaves this file (it can quote a path with an identifier).
            if ((e as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                JournalRead.Refused
            } else {
                JournalRead.Unavailable
            }
        }

    override fun delete(
        uid: String,
        entryIds: List<String>,
    ) {
        try {
            // Batches (500 operations at most each): one send for a whole "Tout effacer". Not awaited: offline, the SDK keeps them until the network is back.
            entryIds.chunked(BATCH_SIZE).forEach { chunk ->
                val db = firestore()
                val batch = db.batch()
                chunk.forEach { batch.delete(collection(uid).document(it)) }
                batch.commit()
            }
        } catch (e: Exception) {
            // The line is already gone from the phone; the entry expires by itself after 30 days.
        }
    }

    override suspend fun deleteAll(uid: String) {
        try {
            withTimeout(DELETE_ALL_TIMEOUT_MILLIS) {
                // "Tout effacer" (contract 2.21, point 3, and the deletion of the account, point 4): a list of 100, one batch of deletions, again, until the list is empty. Bounded.
                repeat(MAX_DELETE_ROUNDS) {
                    val snapshot =
                        collection(
                            uid,
                        ).limit(NotificationJournal.PAGE_SIZE.toLong()).get(Source.SERVER).await()
                    if (snapshot.isEmpty) return@withTimeout
                    val batch = firestore().batch()
                    snapshot.documents.forEach { batch.delete(it.reference) }
                    batch.commit().await()
                }
            }
        } catch (e: CancellationException) {
            if (e !is TimeoutCancellationException) throw e
        } catch (e: Exception) {
            // Best effort, no error shown: what is left is deleted by the TTL after 30 days and is readable by nobody else.
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 20_000L
        const val DELETE_ALL_TIMEOUT_MILLIS = 30_000L
        const val MAX_DELETE_ROUNDS = 20
        const val BATCH_SIZE = 400
    }
}
