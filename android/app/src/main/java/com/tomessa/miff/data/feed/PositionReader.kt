package com.tomessa.miff.data.feed

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.tomessa.miff.domain.feed.PositionLookup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/*
 * PRIVACY and COST, for this file: ONE `get` of `positions/{uid}` per tap on "Localiser", with Source.SERVER: the answer is never READ from the
 * cache (an old copy must not pass for the current one), no listener, never a list. KNOWN LIMIT, accepted and already written on the backend
 * side (backend/data-model.md, point 27): the Firestore SDK keeps in its local cache the documents it reads, this position included, even
 * for a read with Source.SERVER. That copy stays on the phone until the purge of the cache at sign-out or at the loss of access to a Miff
 * (CachePurger). What the Feed itself guarantees: nothing is displayed, logged or kept in the memory of the screen after the sheet closes;
 * the coordinates go back to the caller and nowhere else (not in an exception, not in an analytics event).
 */

/** The single reading behind the button "Localiser". */
interface PositionReader {
    suspend fun read(
        miffId: String,
        uid: String,
    ): PositionLookup
}

class FirestorePositionReader(
    private val firestore: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() },
) : PositionReader {
    override suspend fun read(
        miffId: String,
        uid: String,
    ): PositionLookup =
        try {
            val doc =
                withTimeout(TIMEOUT_MILLIS) {
                    firestore()
                        .collection("miffs")
                        .document(miffId)
                        .collection("positions")
                        .document(uid)
                        .get(Source.SERVER)
                        .await()
                }
            if (!doc.exists()) {
                PositionLookup.NotFound
            } else {
                found(doc.getDouble("lat"), doc.getDouble("lng"), doc.getTimestamp("measuredAt")?.toDate()?.time)
            }
        } catch (e: CancellationException) {
            // A timeout is a CancellationException too: it is "time exceeded" (an unexpected failure), the caller's own cancellation is rethrown.
            if (e is TimeoutCancellationException) PositionLookup.Failed else throw e
        } catch (e: Exception) {
            lookupForError(e)
        }

    private companion object {
        /** Offline, a read from the server ends with "unavailable" on its own; this is only the ceiling. */
        const val TIMEOUT_MILLIS = 15_000L
    }
}

/**
 * What a failed reading means, in the five calm answers plus the sixth: a refusal of the rules is [PositionLookup.Denied] (ONE message for every
 * refusal); "unavailable" is how the SDK says that the client is offline ([PositionLookup.Offline], checked again against the network by
 * [com.tomessa.miff.domain.feed.Locate.reconcile]); everything else (time exceeded, quota of the plan reached, sign-in expired, an error of the
 * server or of the phone) is [PositionLookup.Failed]. The message of the exception is never read: it can quote a path or a value.
 */
internal fun lookupForError(error: Throwable): PositionLookup =
    when ((error as? FirebaseFirestoreException)?.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> PositionLookup.Denied
        FirebaseFirestoreException.Code.UNAVAILABLE -> PositionLookup.Offline
        else -> PositionLookup.Failed
    }

/**
 * A document that is not a position of this app (a coordinate missing, not a number, out of range, or no time of measure) is "not found":
 * the sheet never draws or opens a point it cannot vouch for.
 */
internal fun found(
    lat: Double?,
    lng: Double?,
    measuredAtMillis: Long?,
): PositionLookup {
    if (lat == null || lng == null || measuredAtMillis == null) return PositionLookup.NotFound
    if (!lat.isFinite() || !lng.isFinite() || lat !in -90.0..90.0 ||
        lng !in -180.0..180.0
    ) {
        return PositionLookup.NotFound
    }
    return PositionLookup.Found(lat, lng, measuredAtMillis)
}
