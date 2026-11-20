package com.tomessa.miff.domain.feed

/*
 * FEED-ORDRE (ticket section 3, decision 6): the CONTENT of a card is updated at once, but the ORDER of the cards is held back while the person scrolls or
 * keeps a finger down (even on a button), so that nothing moves under the finger. This gate decides, from the order the Feed computed ([live]) and from
 * the order the person SEES, what is shown now. Pure: nothing is read or written, no clock of its own, no logging; the screen only gives it facts.
 * The same rules are meant to be written identically on iOS.
 */

/** What the screen draws now: the order of the members, whether the cards slide to it, and the announcement owed to the screen reader, if any. */
class CardOrderView(
    val order: List<String>,
    /** True only for an order applied after a held-back change or a change that comes alone: the cards glide; false for an order applied directly. */
    val animate: Boolean,
    val announcement: OrderAnnouncement?,
)

/**
 * What the screen reader is told (polite, never interrupting) when the order really changes: [mover] is the member who rose to a new place ("{Prénom}
 * a publié une photo"); null when several cards moved ("Le Feed a été mis à jour"). [id] grows with each announcement, so that the screen says each
 * one once, even when two are alike.
 */
class OrderAnnouncement(
    val id: Int,
    val mover: String?,
)

class CardOrderGate {
    private var shown: List<String>? = null
    private var miffId: String? = null
    private var animate = false
    private var announcement: OrderAnnouncement? = null
    private var directUntilMillis = Long.MIN_VALUE
    private var lastRefreshing = false
    private var announcements = 0

    /**
     * Until [untilMillis] a new order is applied directly: no animation, no announcement. Used when the person comes back to the Feed (foreground) or asks
     * for a refresh (ticket, "retour sur l'onglet" and "rafraîchissement manuel"). A gesture under way still holds the order back ([resolve]).
     */
    fun applyDirectlyUntil(untilMillis: Long) {
        directUntilMillis = maxOf(directUntilMillis, untilMillis)
    }

    /** Nothing to show (the Feed is loading or lost): what was kept is forgotten, so that the next order is shown as it comes, with no movement. */
    fun reset() {
        shown = null
        miffId = null
        animate = false
        announcement = null
    }

    /**
     * The order to draw. [hold] is true while a gesture is under way (scroll, fling, a finger down). [nowMillis] is a MONOTONE clock (`SystemClock.elapsedRealtime()`):
     * a change of the wall clock or of the time zone never opens or closes the direct window. Idempotent for the same arguments: the screen may call it
     * at every recomposition. The first call, and a change of [miffId], show [live] as it is, without animation or announcement.
     */
    fun resolve(
        miffId: String,
        myUid: String?,
        live: List<String>,
        hold: Boolean,
        refreshing: Boolean,
        nowMillis: Long,
    ): CardOrderView {
        if (refreshing != lastRefreshing) {
            // A manual refresh starts or ends: what it brings is shown at once.
            lastRefreshing = refreshing
            applyDirectlyUntil(nowMillis + DIRECT_WINDOW_MILLIS)
        }
        val before = shown
        if (before == null || this.miffId != miffId) {
            this.miffId = miffId
            shown = live
            animate = false
            announcement = null
            return view(live)
        }
        if (hold) {
            // Nothing moves: the cards that left are dropped, the new ones are added at the end. A change of order waits for the end of the gesture.
            val kept = before.filter { it in live } + live.filter { it !in before }
            shown = kept
            return view(kept)
        }
        if (before == live) {
            // No change: the glide of an earlier change is over (a card that arrives or a later held order must not glide by leftover).
            animate = false
            return view(before)
        }
        val direct = nowMillis < directUntilMillis
        shown = live
        animate = !direct
        if (!direct) {
            // Nothing is said when the order of the members present before AND after did not change, nor for my own card moving alone. A member is named
            // only when he or she arrives at the TOP of the list ("{Prénom} a publié une photo"); any other movement is "Le Feed a été mis à jour".
            val change = CardOrderChange.of(before, live)
            if (change != null && (change.mover == null || change.mover != myUid)) {
                announcement = OrderAnnouncement(++announcements, change.mover?.takeIf { it == live.firstOrNull() })
            }
        }
        return view(live)
    }

    private fun view(order: List<String>) = CardOrderView(order, animate, announcement)

    companion object {
        /** How long, after a return to the Feed or a refresh, the order is applied directly (counted on a MONOTONE clock, see [CardOrderGate.resolve]). */
        const val DIRECT_WINDOW_MILLIS = 4_000L
    }
}

/** How an order changed, over the members present in BOTH lists (a member who arrives or leaves is not a movement). */
object CardOrderChange {
    class Result(
        /** The one member who rose to a new place; null when several cards moved. */
        val mover: String?,
    )

    /** Null when the common members are in the same order. */
    fun of(
        before: List<String>,
        after: List<String>,
    ): Result? {
        val common = before.toSet() intersect after.toSet()
        val from = before.filter { it in common }
        val to = after.filter { it in common }
        if (from == to) return null
        // One card that moved up, the others keeping their relative order: removing it from both lists gives the same list.
        val risers =
            to.filter { uid ->
                from.filter { it != uid } == to.filter { it != uid } && to.indexOf(uid) < from.indexOf(uid)
            }
        return Result(risers.singleOrNull())
    }
}

/** The cards in the order the person sees ([order] holds the identifiers of the members); a card that [order] does not name is not drawn, so the gate always names all of them. */
fun List<MemberCard>.inOrder(order: List<String>): List<MemberCard> {
    val byUid = associateBy { it.member.uid }
    return order.mapNotNull { byUid[it] }
}
