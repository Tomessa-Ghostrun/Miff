package com.tomessa.miff.domain.miff

/**
 * The selector of Miffs in the header of the Feed (decision of the PM, 2026-11-08, which REPLACES D12 / Q2 of the Feed ticket: "avec une seule Miff,
 * pastille non cliquable; Rejoindre / Créer réservés au Premium").
 *
 * - EVERYONE sees the same selector, with a single Miff too: it lists the Miff(s) and the two entries "Rejoindre une Miff" and "Créer une Miff".
 * - What a touch on one of the two entries does depends on the plan, and only on it. A free account that already has a Miff is taken to the offer
 *   (the paywall); a Miff+ account goes on to the screen of the entry. An UNKNOWN plan (the subscription could not be read, or the answer comes
 *   from the cache of the phone) is never taken for "free": the person goes on to the entry, whose own check (and, in the end, the rules of
 *   Firestore: the slot "free", `backend/firestore.rules`) decides. Showing the offer on a doubt would lock out a paying person, who is the worse mistake.
 *
 * This file is the single place of that rule; it holds no state and reads nothing.
 */
object MiffSelector {
    /** The two entries under the list of Miffs, in this order, for every account. */
    val entries: List<SelectorEntry> = listOf(SelectorEntry.JOIN, SelectorEntry.CREATE)

    /**
     * What a touch on [entry] does. [premium] is `DirectoryState.Ready.premiumOrNull` (null = unknown), [miffCount] the number of Miffs the person
     * is already in. A free account WITHOUT a Miff (not reachable from the Feed, kept for safety) is not taken to the offer: it has not reached any limit.
     */
    fun onTap(
        entry: SelectorEntry,
        premium: Boolean?,
        miffCount: Int,
    ): SelectorTap =
        when {
            premium == false && miffCount >= 1 -> SelectorTap.OpenOffer
            entry == SelectorEntry.JOIN -> SelectorTap.OpenJoin
            else -> SelectorTap.OpenCreate
        }
}

enum class SelectorEntry { JOIN, CREATE }

/** Where a touch on an entry of the selector leads. */
enum class SelectorTap { OpenJoin, OpenCreate, OpenOffer }
