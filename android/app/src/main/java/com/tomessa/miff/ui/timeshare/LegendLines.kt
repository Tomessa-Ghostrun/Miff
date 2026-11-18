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
     * not already among the four biggest, it takes the place of the fourth (the smallest of them). Under 1 % (a share that rounds to 0, "< 1 %") it has no line, as before. The
     * lines are kept in the order of the ring. "Hors-zone" and every place follow the plain rule.
     */
    fun select(items: List<LegendItem>): List<LegendItem> {
        val ranked =
            items
                .withIndex()
                .sortedWith(
                    compareByDescending<IndexedValue<LegendItem>> {
                        it.value.share
                    }.thenBy { it.index },
                ).map { it.value }
        val biggest = ranked.take(MAX_LINES)
        val danger = items.firstOrNull { it.kind == SliceKind.DANGER && it.percent >= 1 }
        val chosen =
            if (danger != null && danger !in biggest) {
                biggest.dropLast(1) + danger
            } else {
                biggest
            }
        return items.filter { it in chosen }
    }
}
