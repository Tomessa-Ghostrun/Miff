package com.tomessa.miff.domain

import com.tomessa.miff.domain.splash.SplashPhase
import com.tomessa.miff.domain.splash.SplashTimeline
import com.tomessa.miff.domain.splash.burstParticles
import com.tomessa.miff.domain.splash.inflateScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SplashTimelineTest {
    private val full = SplashTimeline(reduceMotion = false)
    private val reduced = SplashTimeline(reduceMotion = true)

    @Test
    fun fullAnimationLastsAboutTwoPointTwoSecondsAndNeverMoreThanTheMaximum() {
        assertEquals(2200L, full.totalMs)
        assertTrue(full.totalMs <= SplashTimeline.MAX_ALLOWED_MS)
        assertFalse(full.isFinished(full.totalMs - 1))
        assertTrue(full.isFinished(full.totalMs))
        assertTrue(full.isFinished(60_000L))
    }

    @Test
    fun phasesFollowTheRequestedStoryInOrder() {
        // Logo without heart -> heart appears -> swells -> bursts -> app.
        val phases = (0L until full.totalMs step 10).map { full.frameAt(it).phase }.distinct()
        assertEquals(
            listOf(
                SplashPhase.HOLD,
                SplashPhase.APPEAR,
                SplashPhase.INFLATE,
                SplashPhase.BURST,
                SplashPhase.EXIT,
            ),
            phases,
        )
        assertEquals(SplashPhase.DONE, full.frameAt(full.totalMs).phase)
    }

    @Test
    fun theFirstInstantMatchesTheSystemLaunchScreen_noHeart() {
        val first = full.frameAt(0)
        assertEquals(SplashPhase.HOLD, first.phase)
        assertEquals(0f, first.heartAlpha, 0f)
        assertEquals(1f, first.overlayAlpha, 0f)
        assertNull(first.burstProgress)
    }

    @Test
    fun theHeartAppearsThenSwellsLikeABalloonUpToItsMaximum() {
        val appear = SplashTimeline.HOLD_MS
        val inflate = appear + SplashTimeline.APPEAR_MS
        val burst = inflate + SplashTimeline.INFLATE_MS

        assertEquals(1f, full.frameAt(inflate).heartScale, 0.01f) // resting size when the swelling starts
        assertEquals(1f, full.frameAt(inflate).heartAlpha, 0f)
        val almostBurst = full.frameAt(burst - 1).heartScale
        assertTrue("heart must be big just before the pop: $almostBurst", almostBurst > SplashTimeline.MAX_SCALE * 0.9f)

        // Growing overall (the wobble may dip a little, never back to the start).
        val samples = (inflate until burst step 100).map { full.frameAt(it).heartScale }
        assertTrue(samples.last() > samples.first() * 3f)
        assertTrue(samples.zipWithNext().all { (a, b) -> b > a * 0.9f })
        // Elastic: the curve is not a straight line.
        val middle = full.frameAt(inflate + SplashTimeline.INFLATE_MS / 2).heartScale
        assertTrue(middle < (1f + SplashTimeline.MAX_SCALE) / 2f)
    }

    @Test
    fun inflationIsContinuousWithTheAppearanceAndStartsAtRestingSize() {
        assertEquals(1f, inflateScale(0f), 1e-4f)
        assertEquals(SplashTimeline.MAX_SCALE, inflateScale(1f), 1e-3f)
    }

    @Test
    fun thePopIsSharp_heartGoneOnTheFirstInstantAndPiecesFlyAway() {
        val burstStart = SplashTimeline.HOLD_MS + SplashTimeline.APPEAR_MS + SplashTimeline.INFLATE_MS
        val justBefore = full.frameAt(burstStart - 1)
        val pop = full.frameAt(burstStart)
        assertTrue(justBefore.heartAlpha > 0f)
        assertEquals(0f, pop.heartAlpha, 0f)
        assertNotNull(pop.burstProgress)
        assertEquals(0f, pop.burstProgress!!, 0.01f)

        val start = burstParticles(0f)
        val end = burstParticles(1f)
        assertEquals(SplashTimeline.PARTICLE_COUNT, start.size)
        assertTrue(start.all { it.distance == 0f && it.alpha == 1f })
        assertTrue(end.all { it.distance > 0.6f && it.alpha == 0f })
        // Pieces fly in different directions.
        assertEquals(SplashTimeline.PARTICLE_COUNT, start.map { it.angleRadians }.distinct().size)
        // Gone by the end of the burst, then the exit shows only the logo.
        val burstEnd = burstStart + SplashTimeline.BURST_MS
        assertNull(full.frameAt(burstEnd).burstProgress)
        assertEquals(0f, full.frameAt(burstEnd).heartAlpha, 0f)
    }

    @Test
    fun theExitFadesTheWholeSplashOutThenHandsOverToTheApp() {
        val exitStart = full.totalMs - SplashTimeline.EXIT_MS
        assertEquals(1f, full.frameAt(exitStart).overlayAlpha, 0.01f)
        assertEquals(0.5f, full.frameAt(exitStart + SplashTimeline.EXIT_MS / 2).overlayAlpha, 0.01f)
        assertEquals(0f, full.frameAt(full.totalMs).overlayAlpha, 0f)
        val alphas = (exitStart until full.totalMs).map { full.frameAt(it).overlayAlpha }
        assertTrue(alphas.zipWithNext().all { (a, b) -> b <= a })
    }

    @Test
    fun noFlashing_theWholeHeartAppearsOnceAndVanishesOnce() {
        // Accessibility SP4: at most 3 flashes per second. The heart is shown, hidden, and never comes back.
        val visibility = (0L until full.totalMs).map { full.frameAt(it).heartAlpha > 0f }
        val changes = visibility.zipWithNext().count { (a, b) -> a != b }
        assertEquals(2, changes)
    }

    // --- "Reduce animations" ------------------------------------------------------------------------------

    @Test
    fun withReducedMotionTheFullLogoStaysStillThenFades() {
        assertEquals(800L, reduced.totalMs)
        assertTrue(reduced.totalMs < full.totalMs)
        (0L until SplashTimeline.STILL_MS step 20).forEach { t ->
            val frame = reduced.frameAt(t)
            assertEquals(SplashPhase.STILL, frame.phase)
            assertEquals(1f, frame.heartScale, 0f) // no swelling
            assertEquals(1f, frame.heartAlpha, 0f) // complete logo from the start
            assertEquals(1f, frame.overlayAlpha, 0f)
            assertNull(frame.burstProgress) // no pieces
        }
        val fade = reduced.frameAt(SplashTimeline.STILL_MS + SplashTimeline.EXIT_MS / 2)
        assertEquals(SplashPhase.EXIT, fade.phase)
        assertEquals(1f, fade.heartScale, 0f)
        assertEquals(0.5f, fade.overlayAlpha, 0.01f)
    }

    @Test
    fun withReducedMotionNothingMovesAtAnyInstant() {
        val scales = (0L until reduced.totalMs).map { reduced.frameAt(it).heartScale }.distinct()
        assertEquals(listOf(1f), scales)
        assertTrue((0L until reduced.totalMs).all { reduced.frameAt(it).burstProgress == null })
        assertTrue(reduced.isFinished(reduced.totalMs))
        assertEquals(SplashPhase.DONE, reduced.frameAt(reduced.totalMs).phase)
    }

    @Test
    fun negativeTimeIsTreatedAsTheStart() {
        assertEquals(full.frameAt(0), full.frameAt(-50))
    }
}
