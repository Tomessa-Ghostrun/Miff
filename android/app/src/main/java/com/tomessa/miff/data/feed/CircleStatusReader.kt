package com.tomessa.miff.data.feed

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.tomessa.miff.domain.feed.MemberStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/*
 * COST and PRIVACY, for this file (NOTIF-5A, ticket notifications.md section 20.5): the ONE reading of the statuses of a Miff, FROM THE SERVER, that checks
 * "is the Miff still orange / red?" a moment before a notification N3 / N4 is shown (a notification about the circle must never be false because a loved one
 * gave news a minute ago). Reads only: 0 write. Cost of one verification: N reads (the statuses of N members) + 3 reads of the rules = 7 for a Miff of 4
 * members, 18 for 15. At most TWO verifications per episode (the second only when the first could not reach the server) and only for a notification that is
 * still planned (switch on, session valid, not already notified), so a person costs a few reads a day at the very most: nothing next to the 50 000 free
 * reads of the day (docs/dev/surveillance-quotas.md, section 5 quater). What is read is used in memory for the decision and is not kept, shown or logged.
 */

/** What the verification read. */
sealed interface CircleRead {
    /** The server answered: the current statuses of the members who have one, by uid. */
    class Fresh(
        val statuses: Map<String, MemberStatus>,
    ) : CircleRead {
        override fun toString(): String = "Fresh(redacted)"
    }

    /** The rules refused (I no longer share, I was removed, my own status is not published): nothing is shown, no second try. */
    data object Refused : CircleRead

    /** The server could not be reached in time (offline, slow): one more try a few minutes later, then nothing is shown (decision Q33). */
    data object Unreachable : CircleRead
}

interface CircleStatusReader {
    suspend fun read(miffId: String): CircleRead
}

class FirestoreCircleStatusReader(
    private val firestore: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() },
    private val timeoutMillis: Long = TIMEOUT_MILLIS,
) : CircleStatusReader {
    override suspend fun read(miffId: String): CircleRead =
        try {
            withTimeout(timeoutMillis) {
                val snapshot =
                    firestore()
                        .collection("miffs")
                        .document(miffId)
                        .collection("status")
                        .get(Source.SERVER)
                        .await()
                CircleRead.Fresh(snapshot.documents.mapNotNull(::statusOf).toMap())
            }
        } catch (e: TimeoutCancellationException) {
            CircleRead.Unreachable
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if ((e as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                CircleRead.Refused
            } else {
                CircleRead.Unreachable
            }
        }

    companion object {
        /** The "10 s" of the ticket (section 20.5): a receiver has about ten seconds, the rest of the budget goes to the decision. */
        const val TIMEOUT_MILLIS = 7_000L
    }
}
