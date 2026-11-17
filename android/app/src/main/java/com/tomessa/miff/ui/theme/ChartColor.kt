package com.tomessa.miff.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * Colour tokens of the chart "Répartition du temps" (docs/tickets/profil-repartition-temps.md, sections 9.2, 10 and 15; Android only until the iOS chart ships, in its OWN file for the
 * same reason as FeedColor.kt: the iOS test ColorTokensTests compares Color.kt only). Decision of the PM (2026-11-17): the trusted places are SHADES OF THE MIFF GREEN AND BLUE, the
 * danger zones are EXACTLY the red of the danger status of the bubble, "Ailleurs" is the grey of the mockup.
 * - The places alternate blue and green so that neighbours are told apart (chart1 and chart3 blue, chart2 and chart4 green, chart5 navy). The green of the brand (#d1d828) is too light
 *   for a ring against the white card (1.5:1), so the greens are DARKER SHADES OF IT; every place colour reaches 3:1 against the card (checked by TimeShareRulesTest).
 * - The danger zone is colorStatusDanger itself (3.1:1), always with hatching, the warning icon and its words: never by colour alone.
 * - "Ailleurs" is the light grey of the mockup (the placeholder grey of the design system): a quiet colour for "somewhere else", under 3:1 on purpose (the mockup asks for it); its
 *   line has its name and its percentage, and the gaps between slices set it apart.
 * The order is the order of the slices: the places in sequence (chart1 to chart5), then "autres lieux", the danger zone, "Ailleurs". [colorChartReserve] is free for a future category.
 */
val colorChart1 = colorActionPrimary
val colorChart2 = Color(0xFF7F8800)
val colorChart3 = Color(0xFF3F74E8)
val colorChart4 = colorStatusSafeText
val colorChart5 = Color(0xFF14297F)
val colorChartReserve = Color(0xFF6E7600)
val colorChartOther = Color(0xFF6C7A9C)
val colorChartElsewhere = colorBgPlaceholder
val colorChartDanger = colorStatusDanger

/** The five colours of the named places, in order. */
val chartPlaceColors: List<Color> = listOf(colorChart1, colorChart2, colorChart3, colorChart4, colorChart5)

// The header badge and the band under the chart, in the green of the mockup (light green disc with dark green bars; very light green band with a green tick).
val colorChartBadge = colorStatusSafeTint
val colorChartBadgeIcon = colorStatusSafeText
val colorChartSummaryBg = colorStatusSafe.copy(alpha = 0.15f)
val colorChartSummaryTick = colorStatusSafeText
