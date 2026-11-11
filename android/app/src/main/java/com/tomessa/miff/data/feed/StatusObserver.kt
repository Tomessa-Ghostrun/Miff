package com.tomessa.miff.data.feed

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.tomessa.miff.domain.feed.MemberStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/*
 * PRIVACY and COST, for this file: the Feed READS the statuses and never writes one (0 write, 0 deletion, ticket Q1). One listener on
 * `miffs/{id}/status` (the list: 3 reads of the rules per request, then one read per changed document), attached by the view model only while
 * the Feed is on screen and only once my own status is published (the rules refuse the list before). Nothing read here is logged.
 */

/** What the listener of the statuses of a Miff said. */
sealed interface StatusFeed {
    /**
     * All the statuses of the Miff. [fromCache] is true while only the cache of the phone vouches for them (no answer of the server yet, or no
     * network): the Feed then says "informations de 16 h 02", never "tout va bien" as if it were now. Keys are the uids of the members.
     */
    class Data(
        val byUid: Map<String, MemberStatus>,
        val fromCache: Boolean,
    ) : StatusFeed {
        override fun toString(): String = "Data(redacted)"
    }

    /**
     * The listener ended: the rules refused the reading (my own status is not published yet, my sharing is stopped, I was removed) or something
     * else ended it. Firestore does not restart a listener that ended on an error: the view model attaches a new one later, never in a loop.
     */
    data object Ended : StatusFeed
}

/** The live view of the statuses of one Miff. Cold: the listener exists while the flow is collected. */
interface StatusObserver {
    fun observe(miffId: String): Flow<StatusFeed>
}

class FirestoreStatusObserver(
    private val firestore: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() },
) : StatusObserver {
    override fun observe(miffId: String): Flow<StatusFeed> =
        callbackFlow {
            val registration =
                firestore()
                    .collection("miffs")
                    .document(miffId)
                    .collection("status")
                    // INCLUDE: the change "the server has now confirmed" (same documents, fromCache goes false) is told too. It costs no read; it is
                    // what lets the Feed know that what it shows is the present and not a copy kept by the phone.
                    .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                        when {
                            // A refusal is a normal state (reciprocity), not an error; any other termination is told the same way.
                            error != null -> {
                                trySend(StatusFeed.Ended)
                                close()
                            }

                            snapshot != null -> {
                                trySend(
                                    StatusFeed.Data(
                                        byUid = snapshot.documents.mapNotNull(::statusOf).toMap(),
                                        fromCache = snapshot.metadata.isFromCache,
                                    ),
                                )
                            }
                        }
                    }
            awaitClose { registration.remove() }
        }
}

/**
 * One document that cannot be read (a field of another type, written by a modified client) is simply ignored: it must never bring the
 * callback of a listener, and so the app, down. Shared by the listener and by the forced reading of "Actualiser".
 */
internal fun statusOf(doc: DocumentSnapshot): Pair<String, MemberStatus>? =
    try {
        readStatus(doc)
    } catch (e: RuntimeException) {
        null
    }

private fun readStatus(doc: DocumentSnapshot): Pair<String, MemberStatus>? {
    if (!doc.exists()) return null
    return doc.id to
        MemberStatus.fromServer(
            presence = doc.getString("presence"),
            placeId = doc.getString("placeId"),
            presenceAtMillis = doc.getTimestamp("presenceAt")?.toDate()?.time,
            lastUpdateAtMillis = doc.getTimestamp("lastUpdateAt")?.toDate()?.time,
            hasPhoto = doc.getBoolean("hasPhoto"),
            batteryPct = doc.getLong("batteryPct"),
            locationAccess = doc.getString("locationAccess"),
            // A write still waiting for the server has no server time yet: the estimate is "now" (see getTimestamp's default).
            updatedAtMillis = doc.getTimestamp("updatedAt")?.toDate()?.time,
            // MODE VOYAGE: the end of the trip and the day chosen; absent from every other status.
            travelUntilMillis = doc.getTimestamp("travelUntil")?.toDate()?.time,
            travelUntilDay = doc.getString("travelUntilDay"),
        )
}
