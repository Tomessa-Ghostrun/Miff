package com.tomessa.miff.domain.place

/*
 * PRIVACY and COST, for this file (places of the Miff chosen in the carousel of the Profile tab, decision of the PM of 2026-11-11):
 * - the names and addresses of the places stay in memory, for the session of the view model, never logged, never in an analytics event; every class that
 *   carries them hides them in its toString();
 * - COST: the places of a Miff cost ONE reading of `miffs/{id}/places` (one Firestore reading per place). [MiffPlacesBook] decides when a reading is
 *   needed: never for the Miff the Feed already read (reused as of the last visit to the Feed), at most once per Miff and per 5 minutes for the others,
 *   never in a loop after a failure. No listener is ever attached for this list.
 */

/** What the list of places of one Miff knows. */
sealed interface MiffPlacesView {
    /** Nothing is known yet, a reading is under way or about to start: no list, no "empty" sentence (no invented absence). */
    data object Loading : MiffPlacesView

    /** The places could not be read (offline, slow, refused): the list says so calmly and tries again later. */
    data object Unavailable : MiffPlacesView

    /** The places of the Miff, sorted as the Miff screen sorts them. May be empty: that is a true "no place yet". */
    class Ready(
        val places: List<Place>,
    ) : MiffPlacesView {
        override fun toString(): String = "Ready(redacted)"
    }
}

/** The places the Feed has read for a Miff: what the Profile reuses for that Miff (no second reading). */
class ReadPlaces(
    val miffId: String,
    val places: List<Place>,
) {
    override fun toString(): String = "ReadPlaces(redacted)"
}

/**
 * Who knows what about the places of each Miff, and when a reading is needed. Plain Kotlin, no Android, no clock but the one it is given: the rules of
 * the cost live here and are tested here. Used from one thread (the main one) by the view model. Same rules as `MiffFacesBook`.
 */
class MiffPlacesBook(
    private val nowMillis: () -> Long,
) {
    private class Entry(
        val view: MiffPlacesView,
        val source: Source,
        val atMillis: Long,
        val stale: Boolean = false,
        val reading: Boolean = false,
        /** The [generation] at which the reading under way started: a reading that ends after an invalidation does not clear the mark. */
        val startedAt: Int = 0,
    )

    private enum class Source { FEED, READ, FAILED }

    private val entries = mutableMapOf<String, Entry>()

    /** Goes up at each invalidation: lets a reading that was started BEFORE one know that what it brings may already be out of date. */
    private var generation = 0

    /** What the list of [miffId] shows now. */
    fun view(miffId: String): MiffPlacesView = entries[miffId]?.view ?: MiffPlacesView.Loading

    /**
     * The Feed has read these places: the list of this Miff draws them. FREE (no reading). It also clears a "stale" mark: what the Feed has just heard
     * (its listener is attached ONLY while the Feed is on screen) is at least as recent as anything we would read now.
     *
     * KNOWN LIMIT, accepted for the cost: once the Feed is left, nothing updates this entry, so the list shows the places AS THEY WERE at the last visit
     * to the Feed. The creator, who is the only one who changes places, is covered by [invalidateAll] (one reading when the list comes back on screen).
     */
    fun seedFromFeed(read: ReadPlaces) {
        val previous = entries[read.miffId]
        entries[read.miffId] =
            Entry(
                MiffPlacesView.Ready(read.places),
                Source.FEED,
                nowMillis(),
                reading = previous?.reading == true,
                startedAt = previous?.startedAt ?: generation,
            )
    }

    /**
     * Does the list of [miffId], which has just appeared, need a reading of the server? Yes when nothing is known, when a place was touched since
     * ([invalidateAll]), when what we read is older than [TTL_MILLIS], or when the last attempt failed more than [RETRY_MILLIS] ago. No while a reading is
     * under way, and no for what the Feed gave (as of the last visit to the Feed, never aged: see the known limit of [seedFromFeed]).
     */
    fun needsReading(miffId: String): Boolean {
        val entry = entries[miffId] ?: return true
        if (entry.reading) return false
        val age = nowMillis() - entry.atMillis
        return when {
            entry.stale -> true
            entry.source == Source.FEED -> false
            entry.source == Source.FAILED -> age >= RETRY_MILLIS
            else -> age >= TTL_MILLIS
        }
    }

    /** A reading starts: the list keeps what it showed (or nothing), and no second reading starts for it. */
    fun readingStarted(miffId: String) {
        val entry = entries[miffId]
        entries[miffId] =
            if (entry == null) {
                Entry(MiffPlacesView.Loading, Source.READ, nowMillis(), reading = true, startedAt = generation)
            } else {
                Entry(entry.view, entry.source, entry.atMillis, entry.stale, reading = true, startedAt = generation)
            }
    }

    fun readingSucceeded(
        miffId: String,
        places: List<Place>,
    ) {
        entries[miffId] =
            Entry(MiffPlacesView.Ready(places), Source.READ, nowMillis(), stale = startedBeforeInvalidation(miffId))
    }

    /** The reading of [miffId] began before the last invalidation: what it brings may predate a change, so the next appearance reads again. */
    private fun startedBeforeInvalidation(miffId: String): Boolean =
        (entries[miffId]?.startedAt ?: generation) != generation

    /** What was drawn stays drawn (a list that was there does not vanish because the network blinked); only a list with nothing becomes "Unavailable". */
    fun readingFailed(miffId: String) {
        val previous = entries[miffId]
        val keep = previous?.view?.takeIf { it is MiffPlacesView.Ready } ?: MiffPlacesView.Unavailable
        entries[miffId] = Entry(keep, Source.FAILED, nowMillis(), stale = startedBeforeInvalidation(miffId))
    }

    /**
     * The creator opened a place or the form of a new one: a place may be added, changed or deleted (in this Miff or in another one the same place
     * belongs to), so the next appearance of each list reads again. Costs nothing now.
     */
    fun invalidateAll() {
        generation++
        for ((miffId, entry) in entries.toMap()) {
            entries[miffId] =
                Entry(
                    entry.view,
                    entry.source,
                    entry.atMillis,
                    stale = true,
                    reading = entry.reading,
                    startedAt = entry.startedAt,
                )
        }
    }

    /** "Réessayer" on a list that could not be read: the next appearance reads at once (no wait for the 30 seconds). */
    fun invalidate(miffId: String) {
        val entry = entries[miffId] ?: return
        generation++
        entries[miffId] =
            Entry(
                entry.view,
                entry.source,
                entry.atMillis,
                stale = true,
                reading = entry.reading,
                startedAt = entry.startedAt,
            )
    }

    /** A Miff that is no longer in the list (left, deleted, access lost) takes its places with it. */
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

/** What the Profile tab draws depends on the role of the viewer in the Miff; the rules of Firestore, not this, decide what is allowed. */
object PlaceViewerRole {
    /**
     * The creator of the Miff sets its places (adds, changes, deletes); every other member reads. An unknown creator or an unknown viewer is
     * NEVER a creator: the buttons of the creator are not drawn on a doubt.
     */
    fun isCreator(
        ownerUid: String?,
        myUid: String?,
    ): Boolean = !ownerUid.isNullOrEmpty() && ownerUid == myUid
}
