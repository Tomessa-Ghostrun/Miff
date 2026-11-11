package com.tomessa.miff.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.tomessa.miff.domain.tracking.StatusLevel
import com.tomessa.miff.ui.main.centralButtonColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow

/**
 * The contrasts of design/tab-bar-liquid-glass.md section 6 (WCAG 2.1), recomputed from the real tokens: if a token changes, a figure
 * of the spec stops being true and this test says so. Worst backgrounds = pure black and pure white (a photo is in between).
 */
class TabBarGlassContrastTest {
    private fun channel(value: Int): Double {
        val c = value / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(color: Color): Double {
        val argb = color.toArgb()
        return 0.2126 * channel((argb shr 16) and 0xFF) +
            0.7152 * channel((argb shr 8) and 0xFF) +
            0.0722 * channel(argb and 0xFF)
    }

    private fun contrast(
        a: Color,
        b: Color,
    ): Double {
        val l1 = luminance(a)
        val l2 = luminance(b)
        return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    }

    /** [top] (with its alpha) drawn over an opaque [bottom]. */
    private fun over(
        top: Color,
        bottom: Color,
    ): Color {
        val a = top.alpha

        fun mix(
            t: Float,
            b: Float,
        ) = (t * a + b * (1f - a)).coerceIn(0f, 1f)
        return Color(mix(top.red, bottom.red), mix(top.green, bottom.green), mix(top.blue, bottom.blue), 1f)
    }

    private val black = Color.Black
    private val white = Color.White
    private val plate = GlassCentralPlate

    /**
     * The figure of the spec is a floor: the real ratio must not be below it (beyond the rounding of the figures: 0.05; the spec says 10.15 for the inactive icon on the opaque dark bar, the tokens give 10.11), and
     * must not drift far above it either (0.1), which would mean that a token changed and the spec no longer describes the bar.
     */
    private fun assertRatio(
        label: String,
        expected: Double,
        actual: Double,
    ) {
        assertTrue("$label: the spec says $expected, the bar gives $actual (below)", actual >= expected - 0.05)
        assertTrue("$label: the spec says $expected, the bar gives $actual (token drifted?)", actual <= expected + 0.1)
    }

    /** Ratio of an icon colour over the veil, and over the pad drawn on the veil, for a background under the glass. */
    private class Ratios(
        val active: Double,
        val activeOnPill: Double,
        val inactive: Double,
    )

    private fun ratios(
        palette: GlassPalette,
        veil: Color,
        background: Color,
    ): Ratios {
        val surface = over(veil, background)
        val pill = over(palette.pillActive, surface)
        return Ratios(
            active = contrast(over(palette.iconActive, surface), surface),
            activeOnPill = contrast(over(palette.iconActive, pill), pill),
            inactive = contrast(over(palette.iconInactive, surface), surface),
        )
    }

    // 2026-11-11 (the PM asked for a more translucent bar): veil 55 % instead of 80 %, inactive icons #404040 instead of #595959, and an OPAQUE pad
    // behind the tab in use. The blue icon of the tab in use is therefore 5.73:1 whatever is behind the glass; the inactive icons are the ones
    // that depend on the background (3.09:1 on pure black, the worst case).
    @Test
    fun lightBarWithBlur_theNewFigures_onPureBlackGreyAndWhite() {
        val onBlack = ratios(GlassPaletteLight, GlassPaletteLight.tint, black)
        assertRatio("light/blur/black active on pill", 5.73, onBlack.activeOnPill)
        assertRatio("light/blur/black inactive", 3.09, onBlack.inactive)
        val onGrey = ratios(GlassPaletteLight, GlassPaletteLight.tint, Color(0xFF808080))
        assertRatio("light/blur/grey active on pill", 5.73, onGrey.activeOnPill)
        assertRatio("light/blur/grey inactive", 6.06, onGrey.inactive)
        val onWhite = ratios(GlassPaletteLight, GlassPaletteLight.tint, white)
        assertRatio("light/blur/white active on pill", 5.73, onWhite.activeOnPill)
        assertRatio("light/blur/white inactive", 10.37, onWhite.inactive)
    }

    @Test
    fun lightBarWithoutBlur_matchesTheNewFigures_onPureBlack() {
        val r = ratios(GlassPaletteLight, GlassPaletteLight.tintNoBlur, black)
        assertRatio("light/noblur/black active on pill", 5.73, r.activeOnPill)
        assertRatio("light/noblur/black inactive", 8.67, r.inactive)
    }

    @Test
    fun lightOpaqueBar_matchesTheNewFigures() {
        val r = ratios(GlassPaletteLight, GlassPaletteLight.surfaceOpaque, black)
        assertRatio("light/opaque active on pill", 5.73, r.activeOnPill)
        assertRatio("light/opaque inactive", 10.37, r.inactive)
    }

    @Test
    fun theLightPad_isOpaque_soTheTabInUseDoesNotDependOnTheBackground() {
        assertEquals(1f, GlassPaletteLight.pillActive.alpha, 0f)
        // The blue of the actions at 20 % drawn on white: `#CDD8F6`.
        assertEquals(0xFFCDD8F6.toInt(), GlassPaletteLight.pillActive.toArgb())
    }

    @Test
    fun barOnPhotoWithBlur_matchesSection6_onPureWhiteAndPureBlack() {
        val onWhite = ratios(GlassPaletteOnPhoto, GlassPaletteOnPhoto.tint, white)
        assertRatio("photo/blur/white active", 5.74, onWhite.active)
        assertRatio("photo/blur/white active on pill", 3.42, onWhite.activeOnPill)
        assertRatio("photo/blur/white inactive", 4.24, onWhite.inactive)
        val onBlack = ratios(GlassPaletteOnPhoto, GlassPaletteOnPhoto.tint, black)
        assertRatio("photo/blur/black active", 21.00, onBlack.active)
        assertRatio("photo/blur/black active on pill", 10.83, onBlack.activeOnPill)
        assertRatio("photo/blur/black inactive", 12.41, onBlack.inactive)
    }

    @Test
    fun barOnPhotoWithoutBlur_matchesSection6_theViewfinder() {
        val onWhite = ratios(GlassPaletteOnPhoto, GlassPaletteOnPhoto.tintNoBlur, white)
        assertRatio("photo/noblur/white active", 9.23, onWhite.active)
        assertRatio("photo/noblur/white active on pill", 4.71, onWhite.activeOnPill)
        assertRatio("photo/noblur/white inactive", 6.39, onWhite.inactive)
        val onBlack = ratios(GlassPaletteOnPhoto, GlassPaletteOnPhoto.tintNoBlur, black)
        assertRatio("photo/noblur/black active", 21.00, onBlack.active)
        assertRatio("photo/noblur/black inactive", 12.41, onBlack.inactive)
    }

    @Test
    fun barOnPhotoOpaque_matchesSection6() {
        val r = ratios(GlassPaletteOnPhoto, GlassPaletteOnPhoto.surfaceOpaque, black)
        assertRatio("photo/opaque active", 15.91, r.active)
        assertRatio("photo/opaque inactive", 10.15, r.inactive)
    }

    @Test
    fun everyIconOfEveryVariantAndLevel_reachesTheThresholdOf3to1_onTheWorstBackgrounds() {
        GlassVariant.entries.forEach { variant ->
            val palette = glassPalette(variant)
            listOf(palette.tint, palette.tintNoBlur, palette.surfaceOpaque).forEach { veil ->
                listOf(black, white, Color(0xFF808080)).forEach { background ->
                    val r = ratios(palette, veil, background)
                    val where = "$variant, veil alpha ${veil.alpha}, background ${background.toArgb()}"
                    assertTrue("inactive icon below 3:1 ($where): ${r.inactive}", r.inactive >= 3.0)
                    assertTrue("active icon on the pad below 3:1 ($where): ${r.activeOnPill}", r.activeOnPill >= 3.0)
                }
            }
        }
    }

    @Test
    fun inactiveGreyOfTheLightBar_isTheDarkerOneOfTheNewVeil_notTheOldOnes() {
        assertEquals(0xFF404040.toInt(), GlassPaletteLight.iconInactive.toArgb())
        // The old greys fall under 3:1 on the worst background with the lighter veil: that is why this one is darker.
        val surface = over(GlassPaletteLight.tint, black)
        listOf(colorTextSecondary, Color(0xFF595959)).forEach {
            assertTrue("$it would not be enough on the worst background", contrast(over(it, surface), surface) < 3.0)
        }
    }

    @Test
    fun spec_tokensAreTheValidatedOnes() {
        // 2026-11-11: 55 % (it was 80 %), asked by the PM.
        assertEquals(0.55f, GlassPaletteLight.tint.alpha, 0.005f)
        assertEquals(0.92f, GlassPaletteLight.tintNoBlur.alpha, 0.005f)
        assertEquals(0.60f, GlassPaletteOnPhoto.tint.alpha, 0.005f)
        assertEquals(0.72f, GlassPaletteOnPhoto.tintNoBlur.alpha, 0.005f)
        assertEquals(0xFFFFFFFF.toInt(), GlassPaletteLight.surfaceOpaque.toArgb())
        assertEquals(0xFF222222.toInt(), GlassPaletteOnPhoto.surfaceOpaque.toArgb())
        assertEquals(0xFF043CD1.toInt(), GlassPaletteLight.iconActive.toArgb())
        assertEquals(28f, GlassBlurRadius.value, 0f)
    }

    // --- The central button: the figures of section 7 (item 4), including the limits the PM accepted ---------------------------------

    @Test
    fun centralButton_dotAgainstThePlateAndTheRing_matchesTheSpec() {
        val ring = colorActionPrimary
        // level -> (dot on plate, dot on ring)
        val expected =
            mapOf(
                StatusLevel.GREEN to (1.55 to 5.27),
                StatusLevel.ORANGE to (1.74 to 4.69),
                StatusLevel.RED to (3.13 to 2.60),
            )
        expected.forEach { (level, figures) ->
            val colors = centralButtonColors(level)
            assertRatio("$level dot on plate", figures.first, contrast(colors.dot, plate))
            assertRatio("$level dot on ring", figures.second, contrast(colors.dot, ring))
            assertRatio("$level ring on plate", 8.16, contrast(colors.ring, plate))
        }
    }

    @Test
    fun centralButton_greenAndOrangeAreAlmostTheSameLuminance_aKnownLimitAcceptedByThePm() {
        // Documented in the spec (section 7, item 4) and in the PR: the colour is the only signal, the spoken name carries the rest.
        assertRatio("green against orange", 1.12, contrast(colorStatusSafe, colorStatusWarning))
    }

    @Test
    fun centralButton_neutralIsGreyOnBothRingAndDot_at7to1AgainstThePlate() {
        val neutral = centralButtonColors(StatusLevel.NEUTRAL)
        assertEquals(0xFF595959.toInt(), neutral.ring.toArgb())
        assertEquals(0xFF595959.toInt(), neutral.dot.toArgb())
        assertRatio("neutral on plate", 7.00, contrast(neutral.dot, plate))
        // The PM's grey of 2026-11-06 stays; the inactive icons of the bar are a little darker since the lighter veil of 2026-11-11 (#404040).
        assertTrue(contrast(neutral.dot, plate) >= 7.0 - 0.05)
    }

    @Test
    fun centralButton_plateIsOpaqueWhite_andNeverFollowsTheVariant() {
        assertEquals(0xFFFFFFFF.toInt(), GlassCentralPlate.toArgb())
        assertEquals(1f, GlassCentralPlate.alpha, 0f)
    }

    @Test
    fun centralButton_statusColoursAreTheDesignSystemOnes_fullColour() {
        assertEquals(colorStatusSafe, centralButtonColors(StatusLevel.GREEN).dot)
        assertEquals(colorStatusWarning, centralButtonColors(StatusLevel.ORANGE).dot)
        assertEquals(colorStatusDanger, centralButtonColors(StatusLevel.RED).dot)
        StatusLevel.entries.forEach {
            assertEquals(
                "full colour, never faded",
                1f,
                centralButtonColors(it).dot.alpha,
                0f,
            )
        }
    }
}
