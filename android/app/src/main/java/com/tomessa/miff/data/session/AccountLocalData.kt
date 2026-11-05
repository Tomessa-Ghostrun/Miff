package com.tomessa.miff.data.session

import com.tomessa.miff.data.miff.PendingDeletionStore
import com.tomessa.miff.data.permission.NotificationAskStore

/**
 * What is kept on the phone FOR ONE ACCOUNT and goes with it when the account is deleted (AUTH-5, ticket 5.10): the flag "notification screen
 * already shown" and the local markers of deletion of a Miff. Whatever STOPS a follow-up (listeners, places, position) goes through
 * [SessionTeardown] instead. Any new storage tied to an account must be listed in one of the two.
 */
fun accountLocalDataErasers(
    notificationAsks: NotificationAskStore,
    pendingMiffDeletions: PendingDeletionStore,
): List<LocalDataEraser> =
    listOf(
        LocalDataEraser { uid -> notificationAsks.forget(uid) },
        LocalDataEraser { uid -> pendingMiffDeletions.ids(uid).forEach { pendingMiffDeletions.remove(uid, it) } },
    )
