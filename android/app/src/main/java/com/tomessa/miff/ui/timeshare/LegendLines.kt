package com.tomessa.miff.ui.timeshare

import com.tomessa.miff.domain.timeshare.SliceKind

/*
 * Which lines the legend of "Répartition du temps" draws (decision of the PM, 2026-11-18; ticket docs/tickets/profil-repartition-temps.md section 5.2). PURE: nothing here reads the phone.
 */
object LegendLines {
    /** The legend has four lines at most; the ring keeps every slice. */
    const val MAX_LINES = 4

    /**
     * The four biggest shares (an equal share: the earlier one in the ring), EXCEPT that the line "Zone de danger" is ALWAYS there when its share rounds to 1 % or more: if it is
     * not already among the four biggest, it takes the place of the fourth (the smallest of them). Under 1 % (a share that rounds to 0, "< 1 %") it NEVER has a line, whatever the number of shares: it is left out of the candidates BEFORE the four biggest are chosen, so a small danger zone does not show up just because there are few lines. The
     * lines are kept in the order of the ring. "Hors-zone" and every place follow the plain rule.
     */
    fun select(items: List<LegendItem>): List<LegendItem> {
        // A danger zone under 1 % is not a candidate at all (no line, even with 2 or 3 shares).
        val candidates = items.filterNot { it.kind == SliceKind.DANGER && it.percent < 1 }
        val ranked =
            candidates
                .withIndex()
                .sortedWith(
                    compareByDescending<IndexedValue<LegendItem>> {
                        it.value.share
                    }.thenBy { it.index },
                ).map { it.value }
        val biggest = ranked.take(MAX_LINES)
        val danger = candidates.firstOrNull { it.kind == SliceKind.DANGER }
        val chosen =
            if (danger != null && danger !in biggest) {
                biggest.dropLast(1) + danger
            } else {
                biggest
            }
        return items.filter { it in chosen }
    }
}
