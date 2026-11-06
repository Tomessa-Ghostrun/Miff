package com.tomessa.miff.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

/** The levels of glass (design/tab-bar-liquid-glass.md sections 4, 9 and 10): which phones and settings get blur, a denser veil, or opacity. */
class GlassPolicyTest {
    private val modern =
        GlassConditions(
            sdkInt = 33,
            powerSaveMode = false,
            animationsRemoved = false,
            contrastIncreased = false,
            lowRamDevice = false,
        )

    private fun level(
        conditions: GlassConditions,
        variant: GlassVariant = GlassVariant.LIGHT,
        blurAllowed: Boolean = true,
    ) = GlassPolicy.level(conditions, variant, blurAllowed)

    @Test
    fun androidTheOppoRuns_asksNothing_blursTheFeedAndTheProfile() {
        assertEquals(GlassLevel.BLUR, level(modern))
    }

    @Test
    fun beforeAndroid12_thereIsNoBlur_aDenserVeilInstead() {
        listOf(26, 28, 30).forEach { assertEquals("API $it", GlassLevel.NO_BLUR, level(modern.copy(sdkInt = it))) }
        assertEquals(GlassLevel.BLUR, level(modern.copy(sdkInt = 31)))
        assertEquals(GlassLevel.BLUR, level(modern.copy(sdkInt = 35)))
    }

    @Test
    fun theViewfinder_neverBlurs_whateverThePhone() {
        assertEquals(GlassLevel.NO_BLUR, level(modern, GlassVariant.ON_PHOTO))
        assertEquals(GlassLevel.NO_BLUR, level(modern.copy(sdkInt = 36), GlassVariant.ON_PHOTO))
    }

    @Test
    fun aLowMemoryPhone_getsNoBlur() {
        assertEquals(GlassLevel.NO_BLUR, level(modern.copy(lowRamDevice = true)))
    }

    @Test
    fun batterySaver_animationsRemovedAndIncreasedContrast_giveAnOpaqueBar_onBothVariants() {
        listOf(
            modern.copy(powerSaveMode = true),
            modern.copy(animationsRemoved = true),
            modern.copy(contrastIncreased = true),
        ).forEach { conditions ->
            GlassVariant.entries.forEach { variant ->
                assertEquals("$conditions $variant", GlassLevel.OPAQUE, level(conditions, variant))
            }
        }
    }

    @Test
    fun opaqueWinsOverEverythingElse_evenOnAnOldPhone() {
        val old = modern.copy(sdkInt = 26, lowRamDevice = true, powerSaveMode = true)
        assertEquals(GlassLevel.OPAQUE, level(old, GlassVariant.ON_PHOTO))
    }

    @Test
    fun theSwitch_turnsTheBlurOffEverywhere_withoutMakingTheBarOpaque() {
        assertEquals(GlassLevel.NO_BLUR, level(modern, blurAllowed = false))
        assertEquals(GlassLevel.OPAQUE, level(modern.copy(powerSaveMode = true), blurAllowed = false))
    }

    @Test
    fun theShippedSwitchIsOn_theBlurIsTheDefault() {
        assertEquals(true, GlassPolicy.BLUR_ALLOWED)
        assertEquals(31, GlassPolicy.MIN_BLUR_SDK)
    }
}
