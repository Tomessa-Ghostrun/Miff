package com.tomessa.miff.data.feed

import com.tomessa.miff.data.tracking.PullOutcome

/*
 * PRIVACY and COST, for this file: FEED-REFRESH-POSITION (docs/tickets/feed-refresh-position.md). It carries NO position: the Feed only asks the detection engine to check MY presence again, and
 * hears back a keyword ([PullOutcome]). The position is read, kept and written by the engine alone, under its own gates (access, consent, session, trip, breakers) and its own limit of one reading
 * every 30 s. Nothing here reads or writes Firestore, nothing is logged or sent as an event.
 */

/** What the pull to refresh of the Feed asks of the detection: look at where I am, once, and tell my loved ones only what changed. */
fun interface PresenceRefresh {
    /** Never throws; returns when the pass is over (the Feed does NOT wait for it to end its own indicator). */
    suspend fun refreshMyPresence(): PullOutcome

    companion object {
        /** No detection behind it (tests, previews). */
        val None = PresenceRefresh { PullOutcome.UNAVAILABLE }
    }
}
