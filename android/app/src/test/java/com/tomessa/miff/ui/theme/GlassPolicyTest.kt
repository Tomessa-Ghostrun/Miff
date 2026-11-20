package com.tomessa.miff.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The levels of glass (design/tab-bar-liquid-glass.md sections 4, 9 and 10): which phones and settings get blur, a denser veil, or opacity. */
class GlassPolicyTest {
    private val modern =
        GlassConditions(
            sdkInt = 33,
            animationsRemoved = false,
            contrastIncreased = false,
            lowRamDevice = false,
        )

    /** The blur switch is passed explicitly: these tests are about the rules, not about the value the build ships with. */
    private fun level(
        conditions: GlassConditions,
        variant: GlassVariant = GlassVariant.LIGHT,
        blurAllowed: Boolean = true,
    ) = GlassPolicy.level(conditions, variant, blurAllowed)

    @Test
    fun androidTheOppoRuns_asksNothing_blursTheFeedAndTheProfile_whenTheBlurIsAllowed() {
        assertEquals(GlassLevel.BLUR, level(modern))
    }

    @Test
    fun theShippedBuild_hasALightBlur_onTheFeedAndTheProfile() {
        // Decision of the PM, 2026-11-20: a SLIGHT blur is wanted. The APK without blur still showed the clear rectangle, so the blur was never the
        // cause (the elevation shadow seen through the veil is, see GlassTabBarTest): the switch ships on again.
        assertTrue("the blur ships switched on", GlassPolicy.BLUR_ALLOWED)
        assertEquals(GlassLevel.BLUR, GlassPolicy.level(modern, GlassVariant.LIGHT))
        // The battery saver is not a condition: same level as with nothing asked.
        assertEquals(GlassLevel.NO_BLUR, GlassPolicy.level(modern, GlassVariant.ON_PHOTO))
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
    fun animationsRemovedAndIncreasedContrast_giveAnOpaqueBar_onBothVariants() {
        listOf(
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
        val old = modern.copy(sdkInt = 26, lowRamDevice = true, animationsRemoved = true)
        assertEquals(GlassLevel.OPAQUE, level(old, GlassVariant.ON_PHOTO))
        val oldWithContrast = modern.copy(sdkInt = 26, lowRamDevice = true, contrastIncreased = true)
        assertEquals(GlassLevel.OPAQUE, level(oldWithContrast, GlassVariant.ON_PHOTO))
    }

    @Test
    fun theBatterySaver_noLongerAffectsTheLevel_theBarStaysTranslucent() {
        // Decision of the PM, 2026-11-20: with the battery saver on, the bar keeps its glass (the PM prefers it translucent).
        // The saver is not a condition any more: nothing in GlassConditions can carry it, so no setting of the phone can turn it into opacity.
        val names = GlassConditions::class.java.declaredFields.map { it.name.lowercase() }
        assertFalse(
            "no battery signal in the conditions: $names",
            names.any {
                "power" in it || "batter" in it ||
                    "saver" in it
            },
        )
        // A phone with the saver on is, for the policy, a phone with nothing asked: blur on the usual screens, denser veil on the viewfinder.
        assertEquals(GlassLevel.BLUR, level(modern))
        assertEquals(GlassLevel.NO_BLUR, level(modern, GlassVariant.ON_PHOTO))
        // The other fallbacks stay as they were.
        assertEquals(GlassLevel.OPAQUE, level(modern.copy(animationsRemoved = true)))
        assertEquals(GlassLevel.OPAQUE, level(modern.copy(contrastIncreased = true)))
        assertEquals(GlassLevel.NO_BLUR, level(modern.copy(sdkInt = 30)))
        assertEquals(GlassLevel.NO_BLUR, level(modern.copy(lowRamDevice = true)))
    }

    @Test
    fun theSwitch_turnsTheBlurOffEverywhere_theBarStaysTranslucent_neverOpaque() {
        assertEquals(GlassLevel.TRANSLUCENT, level(modern, blurAllowed = false))
        // Whatever the switch, the other fallbacks decide as before.
        assertEquals(GlassLevel.NO_BLUR, level(modern, GlassVariant.ON_PHOTO, blurAllowed = false))
        assertEquals(GlassLevel.NO_BLUR, level(modern.copy(sdkInt = 30), blurAllowed = false))
        assertEquals(GlassLevel.NO_BLUR, level(modern.copy(lowRamDevice = true), blurAllowed = false))
        assertEquals(GlassLevel.OPAQUE, level(modern.copy(animationsRemoved = true), blurAllowed = false))
        assertEquals(GlassLevel.OPAQUE, level(modern.copy(contrastIncreased = true), blurAllowed = false))
    }

    @Test
    fun theShippedSwitch_decidesTheDefault_whicheverWayItIsSet() {
        // The switch is shipped at true (see GlassPolicy.BLUR_ALLOWED); false is the safe fallback (translucent veil alone): the build must stay green either way.
        val expected = if (GlassPolicy.BLUR_ALLOWED) GlassLevel.BLUR else GlassLevel.TRANSLUCENT
        assertEquals(expected, GlassPolicy.level(modern, GlassVariant.LIGHT))
        assertEquals(31, GlassPolicy.MIN_BLUR_SDK)
    }
}
