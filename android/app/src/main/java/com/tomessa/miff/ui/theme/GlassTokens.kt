package com.tomessa.miff.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
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

/**
 * Light glass: a very LIGHT white veil (32 %: it was 80 % until the PM asked, on 2026-11-11, for a more and more translucent bar, twice), dark and
 * blue icons. Readability is NOT carried by the density of the veil any more but by the icons: the inactive ones are almost black (`#1A1A1A`), and the
 * tab in use sits on an OPAQUE pad (the blue 20 % of the actions drawn on white, `#CDD8F6`),
 * so that its blue icon is 5.73:1 whatever is behind the glass.
 * KNOWN RISK, accepted by the PM (the transparency comes first): on a PURE BLACK background the inactive icon is 2.21:1 (< 3:1);
 * it is 4.24:1 on a dark grey (25 % white), 7.37:1 on mid grey and 17.4:1 on white. Pinned by TabBarGlassContrastTest.
 */
val GlassPaletteLight =
    GlassPalette(
        tint = Color.White.copy(alpha = 0.32f),
        tintNoBlur = Color.White.copy(alpha = 0.92f),
        surfaceOpaque = Color(0xFFFFFFFF),
        border = Color.White.copy(alpha = 0.55f),
        iconActive = colorActionPrimary,
        iconInactive = Color(0xFF1A1A1A),
        pillActive = colorActionPrimaryTint20.compositeOver(Color.White),
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

/** `tabbar/blur-radius`: 28 dp (it was 20 dp: with a lighter veil, the blur is what keeps the text behind from competing with the icons). */
val GlassBlurRadius = 28.dp

/** The hairline of the glass. */
val GlassBorderWidth = 1.dp

/** The central button's plate is ALWAYS opaque white (never glass): it isolates the colour of the status from any background. */
val GlassCentralPlate = Color(0xFFFFFFFF)
