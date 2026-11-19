package com.tomessa.miff.data.journal

import com.google.firebase.firestore.FirebaseFirestore
import com.tomessa.miff.data.notification.FaceRead
import com.tomessa.miff.data.notification.MemberFaceReader
import com.tomessa.miff.data.notification.actorFaceOf
import com.tomessa.miff.domain.miff.MiffName
import com.tomessa.miff.domain.miff.isAccessLostError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/*
 * CNC-A (ticket docs/tickets/centre-notifications-complet.md, 8.5): ONE `get` of the card `miffs/{miffId}/members/{uid}` of ONE member, when the face of a line of the centre is not in memory
 * (the notification packages themselves never touch Firestore, see NotificationPrivacyTest). The rules let a member of a Miff that still exists read it (`isMember(miffId) && miffAlive(miffId)`);
 * a person who left has no card, a Miff the person left refuses: both are "gone" for the line. Nothing here is logged or kept in a message of an exception (it can quote a path).
 */
class FirestoreMemberFaceReader(
    private val firestore: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() },
) : MemberFaceReader {
    override suspend fun read(
        miffId: String,
        uid: String,
    ): FaceRead =
        try {
            val snapshot =
                withTimeout(TIMEOUT_MILLIS) {
                    firestore()
                        .collection("miffs")
                        .document(miffId)
                        .collection("members")
                        .document(uid)
                        .get()
                        .await()
                }
            if (!snapshot.exists()) {
                FaceRead.Gone
            } else {
                val first = MiffName.fromServer(snapshot.getString("firstName") ?: "").orEmpty()
                val last = MiffName.fromServer(snapshot.getString("lastName") ?: "").orEmpty()
                FaceRead.Found(
                    actorFaceOf(first, last, snapshot.getBlob("avatar")?.toBytes()),
                )
            }
        } catch (e: CancellationException) {
            // A timeout is a CancellationException too: it is "too slow"; the caller's own cancellation is rethrown.
            if (e is TimeoutCancellationException) FaceRead.Failed else throw e
        } catch (e: Exception) {
            // The rules refuse a person who is not in the Miff (any more): gone for her. Any other failure is "not found for now"; nothing of the message leaves this file (it can quote a path).
            if (isAccessLostError(e)) FaceRead.Gone else FaceRead.Failed
        }

    private companion object {
        const val TIMEOUT_MILLIS = 15_000L
    }
}

