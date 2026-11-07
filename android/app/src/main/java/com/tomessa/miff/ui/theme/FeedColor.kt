package com.tomessa.miff.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * Colour tokens of the FEED (maquettes design/exports/feed/claude, decisions of design 1 and 4, to validate by the PM). They live in their OWN file
 * and not in Color.kt on purpose: the iOS test ColorTokensTests.testColorsAreIdenticalToAndroid compares Color.kt with the iOS tokens name by name, and
 * the iOS Feed (FEED-5 and following) adds these five tokens, with the same names and values, together with this test. Until then they are Android only.
 *
 * Text on the 30 % tints in a DARK shade of each status, so that it reaches the WCAG AA contrast of 4.5:1 (the plain status colours are for rings,
 * strips and fills, never for text). Neutral grey for a person whose status is not known: never green when nobody knows. Contrast checked by
 * ColorContrastTest.
 */
val colorStatusSafeText = Color(0xFF4D5200)
val colorStatusWarningText = Color(0xFF7A4300)
val colorStatusDangerText = Color(0xFFA30500)
val colorStatusNeutral = Color(0xFFC9C4BD)
val colorStatusNeutralTint = colorStatusNeutral.copy(alpha = 0.45f)
