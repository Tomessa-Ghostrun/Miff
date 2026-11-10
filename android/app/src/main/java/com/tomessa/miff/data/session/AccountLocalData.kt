package com.tomessa.miff.data.session

import com.tomessa.miff.data.miff.PendingDeletionStore
import com.tomessa.miff.data.permission.NotificationAskStore
import com.tomessa.miff.data.review.ReviewStateStore
import com.tomessa.miff.data.update.PublishLedger

/**
 * What is kept on the phone FOR ONE ACCOUNT and goes with it when the account is deleted (AUTH-5, ticket 5.10): the flag "notification screen
 * already shown", the local markers of deletion of a Miff, and the state of the review window, and the note of the photo of the Update sent by this account (UPDATE-1: it goes with the
 * account; a simple sign-out keeps it, so that the photo is still cleaned up 24 h later). Whatever STOPS a follow-up (listeners, places, position) goes through
 * [SessionTeardown] instead. Any new storage tied to an account must be listed in one of the two.
 */
fun accountLocalDataErasers(
    notificationAsks: NotificationAskStore,
    pendingMiffDeletions: PendingDeletionStore,
    publishLedger: PublishLedger,
    reviewState: ReviewStateStore,
): List<LocalDataEraser> =
    listOf(
        LocalDataEraser { uid -> notificationAsks.forget(uid) },
        LocalDataEraser { uid -> pendingMiffDeletions.ids(uid).forEach { pendingMiffDeletions.remove(uid, it) } },
        // Only the note of THIS account: the single note of the phone may belong to another one.
        LocalDataEraser { uid -> if (publishLedger.photoSentMillis(uid) != null) publishLedger.clearPhotoSent() },
        // The state of the review window (dates and outcome): nothing of it stays for the next account (docs/tickets/pop-ins-avis.md).
        LocalDataEraser { _ -> reviewState.clear() },
    )
