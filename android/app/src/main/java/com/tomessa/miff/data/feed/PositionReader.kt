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
        } catch (e: TimeoutCancellationException) {
            PositionLookup.Offline
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            if (e.code ==
                FirebaseFirestoreException.Code.PERMISSION_DENIED
            ) {
                PositionLookup.Denied
            } else {
                PositionLookup.Offline
            }
        } catch (e: Exception) {
            // Whatever else happened, the person reads the calm "Pas de connexion pour le moment": never a technical word.
            PositionLookup.Offline
        }

    private companion object {
        /** Offline, a read from the server ends with "unavailable" on its own; this is only the ceiling. */
        const val TIMEOUT_MILLIS = 15_000L
    }
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
