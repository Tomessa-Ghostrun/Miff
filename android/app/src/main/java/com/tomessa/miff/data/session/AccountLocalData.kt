package com.tomessa.miff.data.session

import com.tomessa.miff.data.alert.AlertLocalStore
import com.tomessa.miff.data.miff.ClarifyAskStore
import com.tomessa.miff.data.miff.PendingDeletionStore
import com.tomessa.miff.data.permission.NotificationAskStore
import com.tomessa.miff.data.review.ReviewStateStore
import com.tomessa.miff.data.timeshare.TimeShareWindowStore
import com.tomessa.miff.data.update.PublishLedger
import com.tomessa.miff.domain.paywall.PeriodicPaywallRules
import com.tomessa.miff.domain.timeshare.NoPresenceLog
import com.tomessa.miff.domain.timeshare.PresenceLog

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
    /** TIME-1 / TIME-2: the journal of the presence of "Répartition de ton temps" and the window chosen on the card: nothing of them stays for the next account. */
    presenceLog: PresenceLog = NoPresenceLog,
    timeShareWindow: TimeShareWindowStore? = null,
    /** ALERTE « Je suis en danger »: the pointer to my alert and the note of its adoption go with the account (the blue bubble, about the installation, stays). Null: the function is off. */
    alertLocal: AlertLocalStore? = null,
    /** CONS-4-A: how many times the calm card "Une précision" was shown to this account: a counter, erased with the account. */
    clarifyAsks: ClarifyAskStore? = null,
    /** PAY-PER-A: the one whole number of the periodic offer (openings counted since it was last shown) goes with the account. */
    periodicPaywall: PeriodicPaywallRules? = null,
): List<LocalDataEraser> =
    listOf(
        LocalDataEraser { uid -> notificationAsks.forget(uid) },
        LocalDataEraser { uid -> pendingMiffDeletions.ids(uid).forEach { pendingMiffDeletions.remove(uid, it) } },
        // Only the note of THIS account: the single note of the phone may belong to another one.
        LocalDataEraser { uid -> if (publishLedger.photoSentMillis(uid) != null) publishLedger.clearPhotoSent() },
        // The state of the review window (dates and outcome): nothing of it stays for the next account (docs/tickets/pop-ins-avis.md).
        LocalDataEraser { _ -> reviewState.clear() },
        // The local journal of the presence (changes of place, no coordinate, no name) and the window of the chart: erased with the account.
        LocalDataEraser { _ -> presenceLog.clear() },
        LocalDataEraser { _ -> timeShareWindow?.clear() },
        LocalDataEraser { _ -> alertLocal?.clearAccount() },
        LocalDataEraser { uid -> clarifyAsks?.forget(uid) },
        LocalDataEraser { _ -> periodicPaywall?.clear() },
    )
