package com.tomessa.miff.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Gradient tokens of design/design-system.md section 5 (Figma fill styles).

// The design system keeps the end of the title gradient at #d1d52c until the mockups are retouched.
private val gradientBrandTitleEnd = Color(0xFFD1D52C)

/** gradient/brand-title: headline text, horizontal, action blue to yellow-green. */
val GradientBrandTitle: Brush = Brush.horizontalGradient(listOf(colorActionPrimary, gradientBrandTitleEnd))

/**
 * gradient/auth-background: vertical, canvas color (stop at 17 %) to colorBgGradientEnd.
 * Open point: design-system.md writes "a 17 %"; read here as the position of the first color stop (Figma style),
 * not as an opacity. To confirm with the design owner.
 */
val GradientAuthBackground: Brush =
    Brush.verticalGradient(
        0f to colorBgCanvas,
        0.17f to colorBgCanvas,
        1f to colorBgGradientEnd,
    )
