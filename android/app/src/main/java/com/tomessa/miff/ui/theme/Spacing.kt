package com.tomessa.miff.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Layout spacing used by the screens. NOT Figma tokens: design/design-system.md section 6 states that spacing
 * is not tokenized yet. These constants only avoid scattering raw numbers; replace them by the real tokens
 * when the design owner defines them.
 */
object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 16.dp
    val l = 24.dp
    val xl = 32.dp
    val logoSize = 120.dp
    val minTouchTarget = 48.dp
}
