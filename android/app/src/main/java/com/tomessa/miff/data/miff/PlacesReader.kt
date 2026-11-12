package com.tomessa.miff.data.miff

import com.google.firebase.firestore.FirebaseFirestore
import com.tomessa.miff.domain.place.Place
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/*
 * PRIVACY and COST, for this file (places of the Miff chosen in the carousel of the Profile tab, decision of the PM of 2026-11-11): ONE `get` of the
 * collection `miffs/{id}/places`, never a listener. The rules allow it to every member of a Miff that still exists
 * (`allow get, list: if isMember(miffId) && miffAlive(miffId)`, backend/firestore.rules, match /places/{placeId}): a Miff the person left, or that was
 * deleted, is simply refused. It costs one Firestore reading per place (3 for a free creator, more for a Miff+ one) plus the two reads of the rules.
 * The caller reads at most once per Miff and per 5 minutes, and never for the Miff the Feed already read ([com.tomessa.miff.domain.place.MiffPlacesBook]).
 * The places come back as the Miff screen and the Feed have them (same cleaning, same order). Nothing here is logged, and no exception message leaves
 * this file (it can quote a path).
 */

/** What a reading of the places of a Miff gave. */
sealed interface PlacesRead {
    class Loaded(
        val places: List<Place>,
    ) : PlacesRead {
        override fun toString(): String = "Loaded(redacted)"
    }

    /** Refused, offline without a copy, too slow, or anything else: the list says so calmly and tries again later. */
    data object Failed : PlacesRead
}

/** The single reading behind the places of the Profile tab. */
interface PlacesReader {
    suspend fun read(miffId: String): PlacesRead
}

class FirestorePlacesReader(
    private val firestore: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() },
) : PlacesReader {
    override suspend fun read(miffId: String): PlacesRead =
        try {
            // The id is checked before it becomes part of a path (same shape as the rules).
            if (!FirestoreMiffStore.isMiffId(miffId)) {
                PlacesRead.Failed
            } else {
                val snapshot =
                    withTimeout(TIMEOUT_MILLIS) {
                        firestore()
                            .collection("miffs")
                            .document(miffId)
                            .collection("places")
                            .get()
                            .await()
                    }
                PlacesRead.Loaded(placesFromSnapshot(miffId, snapshot))
            }
        } catch (e: CancellationException) {
            // A timeout is a CancellationException too: it is "too slow" (a failure); the caller's own cancellation is rethrown.
            if (e is TimeoutCancellationException) PlacesRead.Failed else throw e
        } catch (e: Exception) {
            PlacesRead.Failed
        }

    private companion object {
        /** Offline, a reading ends with "unavailable" on its own; this is only the ceiling. */
        const val TIMEOUT_MILLIS = 15_000L
    }
}
