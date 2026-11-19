package com.tomessa.miff.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * ALERTE « Je suis en danger » (docs/tickets/alerte-danger.md, section 17). No new colour: the fills are the ones of the design system.
 *
 * - [colorAlertFill] is the red of the mockups (status/danger, #FF5650): the shutter in alert mode, the fill of the bar, the big button "Appeler" (a large bold text: 3:1 is the
 *   WCAG AA ratio for it). Never for small text.
 * - [colorAlertBanner] is the dark red of the error text (#D60700): the banner of the alert carries SMALL white text, so it gets the red that reaches 4.5:1 with white (5.4:1)
 *   instead of the mockup's lighter red (3.1:1). A deviation from maquette 05, listed in the PR.
 * Checked by ColorContrastTest.
 */
val colorAlertFill = colorStatusDanger

val colorAlertBanner = colorTextError

/** White on the red banner and the red button. */
val colorOnAlert = colorTextOnAction

/** The progress of the end of an alert on the banner: a lighter veil over the red, filling from the start. */
val colorAlertBannerProgress = Color.White.copy(alpha = 0.28f)
