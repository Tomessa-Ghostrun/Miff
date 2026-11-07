package com.tomessa.miff.ui.components

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.dp

/**
 * The height that something drawn OVER the bottom of the tabs takes (the bubble of encouragement of the Update, above the tab bar, on every tab but the
 * Update). Each scrolling tab adds it to the bottom margin of its content, so that no card and no button stays hidden under it (criterion A1: "aucune
 * carte du fil n'est inaccessible"). Zero when nothing is drawn there.
 */
val LocalBottomOverlayPadding = compositionLocalOf { 0.dp }
