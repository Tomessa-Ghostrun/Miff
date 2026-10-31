package com.tomessa.miff.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * Color tokens of design/design-system.md section 1.
 * Code names are the Figma codeSyntax names; names not spelled out in the design system
 * follow the same pattern (token "bg/canvas" -> colorBgCanvas). Never write a hex value in a screen.
 */

// Brand and actions
val colorBrandPrimary = Color(0xFFD1D828)
val colorActionPrimary = Color(0xFF043CD1)
val colorActionPrimaryTint5 = colorActionPrimary.copy(alpha = 0.05f)
val colorActionPrimaryTint10 = colorActionPrimary.copy(alpha = 0.10f)
val colorActionPrimaryTint20 = colorActionPrimary.copy(alpha = 0.20f)
val colorActionPrimaryTint30 = colorActionPrimary.copy(alpha = 0.30f)
val colorTextOnAction = Color(0xFFFFFFFF)

// Status (core of the product). The status is always also carried by a text label, never by color alone.
val colorStatusSafe = Color(0xFFD1D828)
val colorStatusSafeTint = colorStatusSafe.copy(alpha = 0.30f)
val colorStatusWarning = Color(0xFFFFB650)
val colorStatusWarningTint = colorStatusWarning.copy(alpha = 0.30f)
val colorStatusDanger = Color(0xFFFF5650)
val colorStatusDangerTint = colorStatusDanger.copy(alpha = 0.30f)

// Backgrounds
val colorBgCanvas = Color(0xFFF9F9F7)
val colorBgSurface = Color(0xFFFFFFFF)
val colorBgPlaceholder = Color(0xFFD9D9D9)
val colorBgOverlayHeader = colorBgCanvas.copy(alpha = 0.60f)
val colorBgGradientEnd = Color(0xFFEDEFB9)

// Text
val colorTextPrimary = Color(0xFF000000)
val colorTextForm = Color(0xFF222222)
val colorTextPlaceholder = colorTextForm.copy(alpha = 0.50f)
val colorTextDescription = colorTextPrimary.copy(alpha = 0.70f)
val colorTextMuted = colorTextPrimary.copy(alpha = 0.50f)
val colorTextSecondary = Color(0xFF7D7C7C)

// "text/error" is a darker red than the danger status (same hue and saturation) so that error TEXT reaches the
// WCAG AA contrast of 4.5:1 (5.39:1 on white, 5.11:1 on colorBgCanvas, 4.53:1 on colorBgGradientEnd).
// colorStatusDanger (#FF5650) is kept for status rings, badges and fills, never for body text. Checked by ColorContrastTest.
val colorTextError = Color(0xFFD60700)

// Borders
val colorBorderDefault = Color(0xFFEFEFEF)
val colorBorderStrong = colorTextPrimary.copy(alpha = 0.50f)
val colorBorderSubtle = colorTextPrimary.copy(alpha = 0.30f)
val colorBorderAction = colorActionPrimary

/** "text/error" at half strength: the label of a disabled red-outlined button. */
val colorTextErrorDisabled = colorTextError.copy(alpha = 0.5f)
