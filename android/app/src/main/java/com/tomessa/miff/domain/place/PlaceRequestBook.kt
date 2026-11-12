package com.tomessa.miff.domain.place

/*
 * PRIVACY and COST, for this file (requests to add a place, ticket LIEU-REQ): the names and addresses stay in memory, for the session of the view model,
 * never logged, never in an analytics event; every class that carries them hides them in its toString().
 * COST: [PlaceRequestBook] decides when a reading of the server is needed. A member reads ONLY its own requests (at most 3 documents), at most once per
 * Miff and per 5 minutes, never in a loop after a failure; the creator is told live by ONE listener while the app is in front (the book then never reads).
 */

/** What the list of requests of one Miff knows. */
sealed interface RequestsView {
    /** Nothing is known yet, a reading is under way or about to start: nothing is drawn (no invented absence). */
    data object Loading : RequestsView

    /** The requests could not be read (offline without a copy, refused, slow): the list of places is drawn without them, nothing alarming is said. */
    data object Unavailable : RequestsView

    /** The requests the person may see (those of 30 days or less), in the order of the list. May be empty: that is a true "none". */
    class Ready(
        val requests: List<PlaceRequest>,
    ) : RequestsView {
        override fun toString(): String = "Ready(redacted)"
    }
}

/**
 * Who knows what about the requests of each Miff, and when a reading is needed. Plain Kotlin, no Android, no clock but the one it is given: the rules
 * of the cost and of the expiry live here and are tested here. Used from one thread (the main one) by the view model.
 */
class PlaceRequestBook(
    private val nowMillis: () -> Long,
) {
    private enum class Source { READ, LIVE, FAILED }

    private class Entry(
        val requests: List<PlaceRequest>?,
        val source: Source,
        val atMillis: Long,
        val stale: Boolean = false,
        val reading: Boolean = false,
    )

    private val entries = mutableMapOf<String, Entry>()

    /** What the list of [miffId] shows now. A request of more than 30 days is never in it (criterion C10): it is hidden here, deleted by the one who meets it. */
    fun view(
        miffId: String,
        creator: Boolean = false,
    ): RequestsView {
        val entry = entries[miffId] ?: return RequestsView.Loading
        val requests =
            entry.requests
                ?: return if (entry.source == Source.FAILED) RequestsView.Unavailable else RequestsView.Loading
        val alive = requests.filterNot { it.isExpired(nowMillis()) }
        return RequestsView.Ready(
            if (creator) PlaceRequestOrder.forCreator(alive) else PlaceRequestOrder.forMember(alive),
        )
    }

    /** The requests known for [miffId], expired ones included (to delete them), or null when nothing is known. */
    fun known(miffId: String): List<PlaceRequest>? = entries[miffId]?.requests

    /** Does the list of [miffId], which has just appeared, need a reading of the server? Never while the listener feeds it or a reading is under way. */
    fun needsReading(miffId: String): Boolean {
        val entry = entries[miffId] ?: return true
        if (entry.reading || entry.source == Source.LIVE) return false
        val age = nowMillis() - entry.atMillis
        return when {
            entry.stale -> true
            entry.source == Source.FAILED -> age >= RETRY_MILLIS
            else -> age >= TTL_MILLIS
        }
    }

    /** A reading starts: the list keeps what it showed (or nothing), and no second reading starts for it. */
    fun readingStarted(miffId: String) {
        val entry = entries[miffId]
        entries[miffId] =
            if (entry == null) {
                Entry(null, Source.READ, nowMillis(), reading = true)
            } else {
                Entry(entry.requests, entry.source, entry.atMillis, entry.stale, reading = true)
            }
    }

    fun readingSucceeded(
        miffId: String,
        requests: List<PlaceRequest>,
    ) {
        entries[miffId] = Entry(requests, Source.READ, nowMillis())
    }

    /** What was drawn stays drawn (a list that was there does not vanish because the network blinked); only a list with nothing becomes "Unavailable". */
    fun readingFailed(miffId: String) {
        entries[miffId] = Entry(entries[miffId]?.requests, Source.FAILED, nowMillis())
    }

    /** The listener of the creator gave the requests of [miffId]: they replace whatever was known, and nothing needs reading while it is attached. */
    fun live(
        miffId: String,
        requests: List<PlaceRequest>,
    ) {
        entries[miffId] = Entry(requests, Source.LIVE, nowMillis())
    }

    /** The listener is no longer attached (the app left the front): what it gave is kept but ages like a reading. */
    fun liveStopped(miffId: String) {
        val entry = entries[miffId] ?: return
        if (entry.source ==
            Source.LIVE
        ) {
            entries[miffId] = Entry(entry.requests, Source.READ, entry.atMillis, stale = true)
        }
    }

    /** A request of the person was written or taken back: the next appearance of each list reads again (the phone knows nothing of what changed elsewhere). */
    fun invalidateAll() {
        for ((miffId, entry) in entries.toMap()) {
            if (entry.source == Source.LIVE) continue
            entries[miffId] = Entry(entry.requests, entry.source, entry.atMillis, stale = true, reading = entry.reading)
        }
    }

    /** "Réessayer": the next appearance reads at once (no wait for the 30 seconds). */
    fun invalidate(miffId: String) {
        val entry = entries[miffId] ?: return
        if (entry.source == Source.LIVE) return
        entries[miffId] = Entry(entry.requests, entry.source, entry.atMillis, stale = true, reading = entry.reading)
    }

    /** A request that was answered or taken back leaves the list at once, without waiting for a reading. */
    fun remove(
        miffId: String,
        requestId: String,
    ) {
        val entry = entries[miffId] ?: return
        val requests = entry.requests ?: return
        entries[miffId] =
            Entry(requests.filterNot { it.id == requestId }, entry.source, entry.atMillis, entry.stale, entry.reading)
    }

    /** A request just accepted by the server appears at once in the list of its author. */
    fun add(
        miffId: String,
        request: PlaceRequest,
    ) {
        val entry = entries[miffId]
        val requests = entry?.requests.orEmpty().filterNot { it.id == request.id } + request
        entries[miffId] =
            Entry(
                requests,
                entry?.source ?: Source.READ,
                entry?.atMillis ?: nowMillis(),
                entry?.stale ?: false,
                entry?.reading ?: false,
            )
    }

    /** The access to one Miff was lost: nothing of its requests stays. */
    fun forget(miffId: String) {
        entries.remove(miffId)
    }

    /** A Miff that is no longer in the list (left, deleted, access lost) takes its requests with it. */
    fun retainOnly(miffIds: Set<String>) {
        entries.keys.retainAll(miffIds)
    }

    companion object {
        /** What was read is reused this long: opening the Profile again, or swiping back to a card, reads nothing within it. */
        const val TTL_MILLIS = 5 * 60 * 1000L

        /** After a failure, the next attempt waits at least this long (never a loop; the list shows nothing alarming meanwhile). */
        const val RETRY_MILLIS = 30 * 1000L
    }
}
