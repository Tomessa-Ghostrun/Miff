package com.tomessa.miff.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/*
 * The "tabbar" tokens (tabbar/glass-tint and the others) of the frosted-glass tab bar: design/tab-bar-liquid-glass.md section 5
 * (validated by the PM on 2026-11-06).
 * Never write one of these values in a screen. The contrast of every pair is checked by TabBarGlassContrastTest against the
 * figures of section 6 of the same file.
 */

/** Which of the two glasses: chosen by the SCREEN (not a setting): LIGHT on the Feed and the Profile, ON_PHOTO on the viewfinder. */
enum class GlassVariant { LIGHT, ON_PHOTO }

/** The colours of one variant. */
class GlassPalette(
    /** `tabbar/glass-tint`: veil over the blur. */
    val tint: Color,
    /** `tabbar/glass-tint-noblur`: denser veil when there is nothing to blur. */
    val tintNoBlur: Color,
    /** `tabbar/surface-opaque`: no transparency at all. */
    val surfaceOpaque: Color,
    /** `tabbar/glass-border`: 1 dp hairline. */
    val border: Color,
    /** `tabbar/icon-active`. */
    val iconActive: Color,
    /** `tabbar/icon-inactive`. */
    val iconInactive: Color,
    /** `tabbar/pill-active`: pad behind the tab in use. */
    val pillActive: Color,
)

/** Light glass: white veil, dark and blue icons. The inactive grey is `#595959` (not `#7d7c7c`, 2.59:1 on the worst background). */
val GlassPaletteLight =
    GlassPalette(
        tint = Color.White.copy(alpha = 0.80f),
        tintNoBlur = Color.White.copy(alpha = 0.92f),
        surfaceOpaque = Color(0xFFFFFFFF),
        border = Color.White.copy(alpha = 0.55f),
        iconActive = colorActionPrimary,
        iconInactive = Color(0xFF595959),
        pillActive = colorActionPrimaryTint20,
    )

/** Glass on a photo (viewfinder, preview): black veil, white icons. It is the dark set, ready for a future dark mode. */
val GlassPaletteOnPhoto =
    GlassPalette(
        tint = Color.Black.copy(alpha = 0.60f),
        tintNoBlur = Color.Black.copy(alpha = 0.72f),
        surfaceOpaque = Color(0xFF222222),
        border = Color.White.copy(alpha = 0.18f),
        iconActive = Color.White,
        iconInactive = Color.White.copy(alpha = 0.78f),
        pillActive = Color.White.copy(alpha = 0.24f),
    )

fun glassPalette(variant: GlassVariant): GlassPalette =
    when (variant) {
        GlassVariant.LIGHT -> GlassPaletteLight
        GlassVariant.ON_PHOTO -> GlassPaletteOnPhoto
    }

/** `tabbar/blur-radius`. */
val GlassBlurRadius = 20.dp

/** The hairline of the glass. */
val GlassBorderWidth = 1.dp

/** The central button's plate is ALWAYS opaque white (never glass): it isolates the colour of the status from any background. */
val GlassCentralPlate = Color(0xFFFFFFFF)
