package com.tomessa.miff.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * Colour tokens of the chart "Répartition de ton temps" (docs/tickets/profil-repartition-temps.md, sections 9.2, 10 and 15; Android only until the iOS chart ships, in its OWN
 * file for the same reason as FeedColor.kt: the iOS test ColorTokensTests compares Color.kt only). Derived from the brand BLUE (`action/primary` #043cd1) and from neutral
 * greys: NO red, orange or yellow-green of the statuses (those colours mean "how someone is", never "where I spent my time"). Every colour reaches 3:1 against the white card
 * (WCAG non-text contrast; checked by ColorContrastTest). The danger zone is the ONE exception: it keeps `status/danger`, always together with a hatch pattern, the warning
 * icon and its words, never by colour alone; the hatch lines are drawn in the dark danger text colour, which is what makes the slice readable against the card.
 *
 * The order is the order of the slices: the places in sequence (chart1 to chart5), then "autres lieux", the danger zone, "Ailleurs". [colorChartReserve] is kept free for a
 * future category (the list of categories is built to grow without touching the calculation, section 10.4).
 */
val colorChart1 = colorActionPrimary
val colorChart2 = Color(0xFF0B7C86)
val colorChart3 = Color(0xFF7240D6)
val colorChart4 = Color(0xFF3F74E8)
val colorChart5 = Color(0xFF14297F)
val colorChartReserve = Color(0xFF9B3E92)
val colorChartOther = Color(0xFF6C7A9C)
val colorChartElsewhere = Color(0xFF8C8C8C)
val colorChartDanger = colorStatusDanger

/** The five colours of the named places, in order. */
val chartPlaceColors: List<Color> = listOf(colorChart1, colorChart2, colorChart3, colorChart4, colorChart5)
