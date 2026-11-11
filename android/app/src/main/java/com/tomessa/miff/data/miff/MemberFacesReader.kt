package com.tomessa.miff.data.miff

import com.google.firebase.firestore.FirebaseFirestore
import com.tomessa.miff.domain.miff.MiffMember
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/*
 * PRIVACY and COST, for this file (carousel of Miffs of the Profile tab): ONE `get` of the collection `miffs/{id}/members`, never a listener.
 * The rules allow it to a member of a Miff that still exists (`allow get, list: if isMember(miffId) && miffAlive(miffId)`, backend/firestore.rules):
 * a Miff the person left, or that was deleted, is simply refused and the card shows its name only. It costs one Firestore reading per member
 * card (15 at most, decision QM5) and about 20 000 bytes of photo each at most (bounded by the rules). The caller reads at most once per Miff and
 * per 5 minutes, and never for the Miff the Feed already read: see docs/dev/surveillance-quotas.md. The members come back as the Feed has them (same
 * cleaning, same order); the caller keeps only the faces. Nothing here is logged, and no exception message leaves this file (it can quote a path).
 */

/** What a reading of the members of a Miff gave. */
sealed interface FacesRead {
    class Loaded(
        val members: List<MiffMember>,
    ) : FacesRead {
        override fun toString(): String = "Loaded(redacted)"
    }

    /** Refused, offline without a copy, too slow, or anything else: the card keeps its name and tries again later. */
    data object Failed : FacesRead
}

/** The single reading behind the faces of a card of the Profile tab. */
interface MemberFacesReader {
    suspend fun read(miffId: String): FacesRead
}

class FirestoreMemberFacesReader(
    private val firestore: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() },
) : MemberFacesReader {
    override suspend fun read(miffId: String): FacesRead =
        try {
            val snapshot =
                withTimeout(TIMEOUT_MILLIS) {
                    firestore()
                        .collection("miffs")
                        .document(miffId)
                        .collection("members")
                        .get()
                        .await()
                }
            FacesRead.Loaded(membersFromSnapshot(snapshot))
        } catch (e: CancellationException) {
            // A timeout is a CancellationException too: it is "too slow" (a failure); the caller's own cancellation is rethrown.
            if (e is TimeoutCancellationException) FacesRead.Failed else throw e
        } catch (e: Exception) {
            FacesRead.Failed
        }

    private companion object {
        /** Offline, a reading ends with "unavailable" on its own; this is only the ceiling. */
        const val TIMEOUT_MILLIS = 15_000L
    }
}
