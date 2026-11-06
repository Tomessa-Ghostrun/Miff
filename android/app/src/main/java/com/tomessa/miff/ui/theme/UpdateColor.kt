package com.tomessa.miff.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * Colour tokens of the UPDATE (design/screens/update.md, section 2). In their OWN file, like FeedColor.kt: the iOS test ColorTokensTests compares Color.kt
 * with the iOS tokens name by name, and the iOS Update (UPDATE-3 and following) adds these tokens with the same names and values.
 *
 * - The two bubbles of encouragement: `status/warning-bubble` #face90 and `status/danger-bubble` #fa9490, validated by the PM on 2026-11-06 (about the
 *   status colours at 63 % on white). Black text on them: 14.3:1 and 9.7:1.
 * - The dark stage behind the photo of the preview, the veil over a photo being sent, and the calm pill of the confirmation.
 */
val colorStatusWarningBubble = Color(0xFFFACE90)
val colorStatusDangerBubble = Color(0xFFFA9490)
val colorUpdateStage = Color(0xFF1D1F20)
val colorUpdateScrim = Color(0xFF000000).copy(alpha = 0.45f)
val colorUpdateVeil = Color(0xFF000000).copy(alpha = 0.30f)
val colorUpdateToast = Color(0xFF222222).copy(alpha = 0.92f)

/** The neutral grey of the central button: the one of the other buttons of the bar on a white plate (contrast 7:1 on it). */
val colorUpdateNeutral = Color(0xFF595959)
