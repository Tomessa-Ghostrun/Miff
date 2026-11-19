package com.tomessa.miff.ui.timeshare

import com.tomessa.miff.domain.place.PlaceCategory
import com.tomessa.miff.domain.timeshare.SliceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The lines of the legend (decision of the PM, 2026-11-18): the four biggest shares, and ALWAYS the danger zone when its share is 1 % or more (it takes the place of the fourth);
 * under 1 % it has no line. Same rule as iOS.
 */
class LegendLinesTest {
    private fun place(
        name: String,
        percent: Int,
        rank: Int,
    ) = LegendItem(SliceKind.PLACE, name, PlaceCategory.HOME, 0, percent, false, rank, emptyList(), percent / 100f)

    private fun danger(
        percent: Int,
        share: Float = percent / 100f,
    ) = LegendItem(
        SliceKind.DANGER,
        null,
        null,
        1,
        percent,
        percent == 0,
        0,
        listOf(DetailItem("Le parc", null, percent, percent == 0)),
        share,
    )

    private fun elsewhere(percent: Int) =
        LegendItem(
            SliceKind.ELSEWHERE,
            null,
            null,
            0,
            percent,
            false,
            0,
            emptyList(),
            percent / 100f,
        )

    private fun names(lines: List<LegendItem>) =
        lines.map {
            when (it.kind) {
                SliceKind.PLACE -> it.name
                SliceKind.DANGER -> "danger"
                SliceKind.ELSEWHERE -> "hors"
                SliceKind.OTHER_PLACES -> "autres"
            }
        }

    @Test
    fun withFourLinesOrFewer_everythingIsThere_inTheOrderOfTheRing() {
        val items = listOf(place("A", 50, 0), danger(10), elsewhere(40))
        assertEquals(listOf("A", "danger", "hors"), names(LegendLines.select(items)))
        assertEquals(listOf("A"), names(LegendLines.select(listOf(place("A", 100, 0)))))
        assertEquals(listOf("A", "danger"), names(LegendLines.select(listOf(place("A", 90, 0), danger(10)))))
    }

    @Test
    fun theFourBiggest_whenThereAreMore_andTheDangerZoneIsAlreadyAmongThem() {
        val items =
            listOf(place("A", 30, 0), place("B", 20, 1), danger(18), place("C", 15, 2), place("D", 10, 3), elsewhere(7))
        // Danger (18 %) is the third biggest: nothing changes, "D" (10) and "hors" (7) are left out.
        assertEquals(listOf("A", "B", "danger", "C"), names(LegendLines.select(items)))
    }

    @Test
    fun aDangerZoneOfOnePercentOrMore_takesThePlaceOfTheFourth() {
        val items =
            listOf(
                place("A", 40, 0),
                place("B", 25, 1),
                place("C", 15, 2),
                place("D", 12, 3),
                place("E", 5, 4),
                danger(3),
            )
        // The four biggest are A, B, C, D; the danger zone (3 %) is not among them: it replaces the fourth, D. The order of the ring is kept (danger last here).
        assertEquals(listOf("A", "B", "C", "danger"), names(LegendLines.select(items)))
    }

    @Test
    fun theFourthThatGivesWayCanBeElsewhere() {
        val items =
            listOf(place("A", 30, 0), place("B", 25, 1), place("C", 20, 2), elsewhere(15), place("D", 8, 3), danger(2))
        assertEquals(listOf("A", "B", "C", "danger"), names(LegendLines.select(items)))
    }

    @Test
    fun justUnderOnePercent_noLine_exactlyOnePercent_aLine() {
        val base = listOf(place("A", 40, 0), place("B", 25, 1), place("C", 15, 2), place("D", 10, 3), place("E", 9, 4))
        // A share that rounds to 0 ("< 1 %") has no line.
        assertEquals(listOf("A", "B", "C", "D"), names(LegendLines.select(base + danger(0, share = 0.0095f))))
        // 1 % (rounded) is enough.
        assertEquals(listOf("A", "B", "C", "danger"), names(LegendLines.select(base + danger(1))))
    }

    @Test
    fun aNullShare_isNeverAddedToTheLegend() {
        val items =
            listOf(place("A", 40, 0), place("B", 30, 1), place("C", 20, 2), place("D", 10, 3), danger(0, share = 0f))
        assertEquals(listOf("A", "B", "C", "D"), names(LegendLines.select(items)))
    }

    @Test
    fun equalShares_theEarlierInTheRingWins_andTheDangerZoneStillTakesTheFourthPlace() {
        val items =
            listOf(
                place("A", 20, 0),
                place("B", 20, 1),
                place("C", 20, 2),
                place("D", 20, 3),
                place("E", 19, 4),
                danger(1),
            )
        assertEquals(listOf("A", "B", "C", "danger"), names(LegendLines.select(items)))
        // With no danger zone, an equal share keeps the earlier one.
        val tie = listOf(place("A", 20, 0), place("B", 20, 1), place("C", 20, 2), place("D", 20, 3), place("E", 20, 4))
        assertEquals(listOf("A", "B", "C", "D"), names(LegendLines.select(tie)))
    }

    @Test
    fun aDangerZoneEqualToTheFourth_isShown_whichever_theRankingDecides() {
        // Danger 15 % ties with C (15 %): the earlier in the ring ranks first, so the danger zone (later) is outside the four biggest, and still takes the fourth place.
        val items = listOf(place("A", 30, 0), place("B", 25, 1), place("C", 15, 2), place("D", 15, 3), danger(15))
        val lines = names(LegendLines.select(items))
        assertEquals(4, lines.size)
        assertTrue("danger" in lines)
    }

    @Test
    fun otherPlacesAndTheLineLimit() {
        val items =
            listOf(
                place("A", 40, 0),
                place("B", 20, 1),
                LegendItem(SliceKind.OTHER_PLACES, null, null, 2, 15, false, 0, emptyList(), 0.15f),
                elsewhere(10),
                danger(5),
                place("C", 10, 2),
            )
        val lines = names(LegendLines.select(items))
        assertEquals(4, lines.size)
        assertTrue("danger" in lines && "A" in lines && "B" in lines && "autres" in lines)
    }

    @Test
    fun aDangerZoneUnderOnePercent_hasNoLine_whateverTheNumberOfShares() {
        // Two shares + a danger zone of 0.4 % (rounds to 0): two lines, not three.
        assertEquals(
            listOf("A", "hors"),
            names(LegendLines.select(listOf(place("A", 60, 0), danger(0, share = 0.004f), elsewhere(40)))),
        )
        // Three shares: three lines, not four.
        assertEquals(
            listOf("A", "B", "hors"),
            names(
                LegendLines.select(
                    listOf(place("A", 50, 0), place("B", 30, 1), danger(0, share = 0.004f), elsewhere(20)),
                ),
            ),
        )
        // Four shares without the danger zone, plus one under 1 %: still the four.
        assertEquals(
            listOf("A", "B", "C", "D"),
            names(
                LegendLines.select(
                    listOf(
                        place("A", 40, 0),
                        place("B", 30, 1),
                        place("C", 20, 2),
                        place("D", 10, 3),
                        danger(0, share = 0.004f),
                    ),
                ),
            ),
        )
    }

    @Test
    fun aDangerZoneJustAboveOnePercent_hasALine_withFewShares_too() {
        assertEquals(
            listOf("A", "danger", "hors"),
            names(LegendLines.select(listOf(place("A", 60, 0), danger(1, share = 0.0104f), elsewhere(39)))),
        )
        assertEquals(listOf("A", "danger"), names(LegendLines.select(listOf(place("A", 99, 0), danger(1)))))
    }
}
