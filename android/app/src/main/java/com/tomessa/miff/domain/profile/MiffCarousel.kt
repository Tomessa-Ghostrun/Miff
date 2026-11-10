package com.tomessa.miff.domain.profile

import com.tomessa.miff.domain.miff.MiffSelector
import com.tomessa.miff.domain.miff.MiffSummary
import com.tomessa.miff.domain.miff.SelectorEntry
import com.tomessa.miff.domain.miff.SelectorTap

/**
 * The carousel "Mes Miffs" of the Profile tab (decision of the PM, 2026-11-10): one card per Miff, and the LAST element is always the card
 * "Créer une Miff". This file is the single place of what the carousel holds and of what the last card does; it reads nothing and holds no state.
 */
sealed interface CarouselItem {
    class Card(
        val miff: MiffSummary,
    ) : CarouselItem

    /** The last element: "Créer une Miff". */
    data object Create : CarouselItem
}

object MiffCarousel {
    /** One card per Miff, in the order of the list of Miffs, then "Créer une Miff": always last, always present (even with no Miff at all). */
    fun items(miffs: List<MiffSummary>): List<CarouselItem> = miffs.map { CarouselItem.Card(it) } + CarouselItem.Create

    /** The carousel opens on the Miff the Feed shows (its faces are already known: no reading); else on the first card. */
    fun initialPage(
        miffs: List<MiffSummary>,
        activeId: String?,
    ): Int = miffs.indexOfFirst { it.id == activeId }.coerceAtLeast(0)

    /**
     * What a touch on "Créer une Miff" does: the SAME rule as the entry "Créer une Miff" of the selector of the Feed (decision of the PM, 2026-11-08,
     * [MiffSelector]): a free account that already has a Miff gets the offer (the paywall); Miff+, or no Miff yet, goes on to the creation. An UNKNOWN plan
     * ([premium] null) is never taken for "free": the creation screen's own check, and in the end the rules of Firestore, decide.
     */
    fun onCreateTap(
        premium: Boolean?,
        miffCount: Int,
    ): SelectorTap = MiffSelector.onTap(SelectorEntry.CREATE, premium, miffCount)
}

/**
 * Whether a photo of a member is decoded, and how small: the photo comes from ANOTHER person's account and the rules bound its size in BYTES (20 000)
 * but not its dimensions, so a tiny file could still announce a gigantic image. Pure arithmetic, no Android.
 */
object AvatarDecodePlan {
    /** A photo of the app is square and far below this; anything beyond is not one of ours and is not decoded (the initials are drawn instead). */
    const val MAX_SIDE = 2048

    /** The power of two to subsample by so that the decoded image stays at least [targetPx] wide and high; null when the photo must not be decoded. */
    fun sampleSize(
        width: Int,
        height: Int,
        targetPx: Int,
    ): Int? {
        if (width <= 0 || height <= 0 || width > MAX_SIDE || height > MAX_SIDE || targetPx <= 0) return null
        var sample = 1
        while (width / (sample * 2) >= targetPx && height / (sample * 2) >= targetPx) sample *= 2
        return sample
    }
}
