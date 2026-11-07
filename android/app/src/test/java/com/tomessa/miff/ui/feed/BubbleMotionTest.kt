package com.tomessa.miff.ui.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The very light movement of the bubble (decision of the PM, 2026-11-06): its limits are the ones he gave (scale <= 1.5 %, drift <= 3 dp,
 * cycle of 5 to 8 s, soft easing, never a jump), and it exists only when the person did not remove the animations and the screen is on view.
 */
class BubbleMotionTest {
    private val phases = (0..1000).map { it / 1000f }

    @Test
    fun theBreath_staysUnder1Point5Percent() {
        assertTrue(BubbleMotion.SCALE_AMPLITUDE <= 0.015f)
        phases.forEach { phase ->
            val scale = BubbleMotion.scale(phase)
            assertTrue("scale $scale at $phase", scale in 0.985f..1.015f)
        }
        // It really breathes (it is not a still bubble in disguise), around 1.
        assertTrue(phases.maxOf(BubbleMotion::scale) > 1.01f)
        assertTrue(phases.minOf(BubbleMotion::scale) < 0.99f)
        assertEquals(1f, BubbleMotion.scale(0f), 0.0001f)
    }

    @Test
    fun theDrift_staysUnder3dp_theStarIncluded() {
        assertTrue(BubbleMotion.DRIFT_DP <= 3f)
        // The star moves with the blob AND a little on its own: the sum never goes over the ceiling.
        assertTrue(BubbleMotion.DRIFT_DP + BubbleMotion.STAR_DRIFT_DP <= 3f)
        phases.forEach { phase ->
            assertTrue(abs(BubbleMotion.driftDp(phase)) <= BubbleMotion.DRIFT_DP + 0.0001f)
            assertTrue(abs(BubbleMotion.starDriftDp(phase)) <= BubbleMotion.STAR_DRIFT_DP + 0.0001f)
        }
    }

    @Test
    fun theCycles_areSlow_5To8Seconds_andTheStarIsOutOfPhase() {
        listOf(BubbleMotion.BLOB_PERIOD_MILLIS, BubbleMotion.STAR_PERIOD_MILLIS).forEach {
            assertTrue("period $it ms", it in 5_000..8_000)
        }
        assertTrue(BubbleMotion.BLOB_PERIOD_MILLIS != BubbleMotion.STAR_PERIOD_MILLIS)
        // The drift is a quarter of a cycle behind the breath: it is never at its extreme when the breath is.
        assertTrue(BubbleMotion.DRIFT_PHASE_SHIFT > 0f)
    }

    @Test
    fun theMovement_isSoft_noJump_noBounce() {
        // Over a whole cycle sampled 1000 times, two neighbouring values never differ by more than a hair: no blink, no flash, no bounce.
        val steps = phases.zipWithNext { a, b -> abs(BubbleMotion.scale(b) - BubbleMotion.scale(a)) }
        assertTrue("largest step ${steps.max()}", steps.max() < 0.0001f)
        val drift = phases.zipWithNext { a, b -> abs(BubbleMotion.driftDp(b) - BubbleMotion.driftDp(a)) }
        assertTrue("largest drift step ${drift.max()}", drift.max() < 0.02f)
    }

    @Test
    fun theMovement_existsOnlyWhenNothingForbidsIt() {
        assertTrue(BubbleMotion.enabled(reduceMotion = false, started = true))
        // "Animations supprimées": no animation at all.
        assertFalse(BubbleMotion.enabled(reduceMotion = true, started = true))
        // Not on view (background, tab left): none either.
        assertFalse(BubbleMotion.enabled(reduceMotion = false, started = false))
        assertFalse(BubbleMotion.enabled(reduceMotion = true, started = false))
    }
}
