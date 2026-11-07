package com.tomessa.miff.ui.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sign

/**
 * The movement of the bubble, revised by the PM after his test on a real phone (D45, 2026-11-07, it replaces the amplitudes of D40): clearly
 * visible, and ORGANIC (squashed and stretched out of phase, a fraction of a degree of tilt, a drift of a few dp), but always slow and calm. It
 * exists only when the person did not remove the animations and the screen is on view.
 */
class BubbleMotionTest {
    private val phases = (0..1000).map { it / 1000f }

    @Test
    fun theSquash_isVisible_aboutPlusOrMinus4To6Percent_andNeverAboveTheFullSize() {
        // Peak to peak 8 % across, 10 % high: +-4 % and +-5 % around the mean.
        assertEquals(0.04f, BubbleMotion.SQUASH_X, 0f)
        assertEquals(0.05f, BubbleMotion.SQUASH_Y, 0f)
        assertEquals(0.92f, phases.minOf(BubbleMotion::scaleX), 0.0005f)
        assertEquals(0.90f, phases.minOf(BubbleMotion::scaleY), 0.0005f)
        // It never grows past the box it was laid out in (a pager or a list would cut an edge).
        assertTrue(phases.maxOf(BubbleMotion::scaleX) <= 1.0001f)
        assertTrue(phases.maxOf(BubbleMotion::scaleY) <= 1.0001f)
        assertEquals(1f, phases.maxOf(BubbleMotion::scaleX), 0.0005f)
        assertEquals(1f, phases.maxOf(BubbleMotion::scaleY), 0.0005f)
    }

    @Test
    fun theShape_deforms_theTwoAxesAreOutOfPhase_neverAUniformScale() {
        assertTrue(BubbleMotion.Y_PHASE_SHIFT > 0f && BubbleMotion.Y_PHASE_SHIFT < 0.5f)
        val steps = phases.zipWithNext()
        // Sometimes the width grows while the height shrinks (stretch and squash), sometimes both move the same way: it is not a uniform scale.
        val opposite =
            steps.count { (a, b) ->
                sign(BubbleMotion.scaleX(b) - BubbleMotion.scaleX(a)) *
                    sign(BubbleMotion.scaleY(b) - BubbleMotion.scaleY(a)) <
                    0f
            }
        val together =
            steps.count { (a, b) ->
                sign(BubbleMotion.scaleX(b) - BubbleMotion.scaleX(a)) *
                    sign(BubbleMotion.scaleY(b) - BubbleMotion.scaleY(a)) >
                    0f
            }
        assertTrue("opposite $opposite", opposite > 200)
        assertTrue("together $together", together > 200)
        // And the aspect ratio really changes by several percent over a cycle.
        val ratios = phases.map { BubbleMotion.scaleX(it) / BubbleMotion.scaleY(it) }
        assertTrue("ratio ${ratios.min()}..${ratios.max()}", ratios.max() - ratios.min() > 0.05f)
    }

    @Test
    fun theTilt_isAFractionOfADegree_andTheDrift_isAFewDp_theStarIncluded() {
        assertTrue(BubbleMotion.TILT_DEGREES in 0.3f..1.5f)
        phases.forEach { assertTrue(abs(BubbleMotion.tiltDegrees(it)) <= BubbleMotion.TILT_DEGREES + 0.0001f) }
        assertTrue(phases.maxOf(BubbleMotion::tiltDegrees) > 0.7f)
        // 3 to 4 dp of drift for the bubble, 4 dp at most for the star (the 0.5 dp it has on top of the bubble included).
        assertTrue(BubbleMotion.DRIFT_DP in 3f..4f)
        assertTrue(BubbleMotion.DRIFT_DP + BubbleMotion.STAR_DRIFT_DP <= 4f)
        phases.forEach { phase ->
            assertTrue(abs(BubbleMotion.driftDp(phase)) <= BubbleMotion.DRIFT_DP + 0.0001f)
            assertTrue(abs(BubbleMotion.starDriftDp(phase)) <= BubbleMotion.STAR_DRIFT_DP + 0.0001f)
        }
        assertTrue(phases.maxOf(BubbleMotion::driftDp) > 3.4f)
        // The pictogram breathes too, around its size.
        assertTrue(phases.maxOf(BubbleMotion::starScale) > 1.04f && phases.minOf(BubbleMotion::starScale) < 0.96f)
    }

    @Test
    fun theCycles_areSlow_5To8Seconds_andTheStarIsOutOfPhase() {
        listOf(BubbleMotion.BLOB_PERIOD_MILLIS, BubbleMotion.STAR_PERIOD_MILLIS).forEach {
            assertTrue("period $it ms", it in 5_000..8_000)
        }
        assertTrue(BubbleMotion.BLOB_PERIOD_MILLIS != BubbleMotion.STAR_PERIOD_MILLIS)
        // The drift is a quarter of a cycle behind the squash: it is never at its extreme when the squash is.
        assertTrue(BubbleMotion.DRIFT_PHASE_SHIFT > 0f)
    }

    @Test
    fun theMovement_isSoft_noJump_noBounce_andSlowInPercentPerSecond() {
        // Over a whole cycle sampled 1000 times, two neighbouring values never differ by more than a hair: no blink, no flash, no bounce.
        val x = phases.zipWithNext { a, b -> abs(BubbleMotion.scaleX(b) - BubbleMotion.scaleX(a)) }
        val y = phases.zipWithNext { a, b -> abs(BubbleMotion.scaleY(b) - BubbleMotion.scaleY(a)) }
        assertTrue("largest x step ${x.max()}", x.max() < 0.0006f)
        assertTrue("largest y step ${y.max()}", y.max() < 0.0006f)
        val drift = phases.zipWithNext { a, b -> abs(BubbleMotion.driftDp(b) - BubbleMotion.driftDp(a)) }
        assertTrue("largest drift step ${drift.max()}", drift.max() < 0.03f)
        // The fastest the shape ever moves: well under 10 % of its size per second (a calm swell, not a pulse).
        val fastestPerSecond = y.max() * 1000f / (BubbleMotion.BLOB_PERIOD_MILLIS / 1000f)
        assertTrue("fastest ${fastestPerSecond * 100} % per second", fastestPerSecond < 0.10f)
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
