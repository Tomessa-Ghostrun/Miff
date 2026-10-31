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
    fun fullAnimationLastsAboutTwoPointSevenSecondsAndNeverMoreThanThree() {
        assertEquals(2700L, full.totalMs)
        assertTrue("never more than 3.0 s", full.totalMs <= 3000L)
        assertEquals(3000L, SplashTimeline.MAX_ALLOWED_MS)
        assertTrue(full.totalMs <= SplashTimeline.MAX_ALLOWED_MS)
        assertFalse(full.isFinished(full.totalMs - 1))
        assertTrue(full.isFinished(full.totalMs))
        assertTrue(full.isFinished(60_000L))
    }

    /** PM recette: slower swelling and pop ("barely time to understand what happens"), 3 s at the very most. */
    @Test
    fun theSwellingAndThePopAreSlow_andThePopIsReadable() {
        assertTrue(SplashTimeline.INFLATE_MS >= 1400L)
        assertTrue("the pop lasts at least 0.4 s", SplashTimeline.BURST_MS >= 400L)
        val burstStart = SplashTimeline.HOLD_MS + SplashTimeline.APPEAR_MS + SplashTimeline.INFLATE_MS
        // A pause just before the pop: the heart holds at its biggest size.
        val pauseStart = burstStart - SplashTimeline.POP_PAUSE_MS
        assertTrue(SplashTimeline.POP_PAUSE_MS in 80L..250L)
        val held = (pauseStart until burstStart step 10).map { full.frameAt(it).heartScale }.distinct()
        assertEquals(listOf(SplashTimeline.MAX_SCALE), held.map { Math.round(it * 1000f) / 1000f }.distinct())
        // The pieces move slowly enough to be followed: a quarter of the way after a quarter of the pop is not reached too early.
        assertTrue(burstParticles(0.25f).first().distance < 0.65f)
        // The pop starts after at least 2 s of animation.
        assertTrue(burstStart >= 2000L)
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
        assertTrue(samples.last() > samples.first() * 1.1f)
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

    /**
     * PM recette 2026-10-30: the heart was far too big. Scale 1 is the size of the heart on the official logo and icon
     * (SplashArtworkTest ties the drawn heart to the icon's), and it must swell to "a little bigger": 115 to 125 %.
     */
    @Test
    fun theHeartNeverGetsBiggerThanAboutOneAndAQuarterOfItsSizeOnTheOfficialLogo() {
        val scales = (0L until full.totalMs).map { full.frameAt(it).heartScale }
        val max = scales.max()
        assertTrue("max scale $max must be between 1.15 and 1.25", max in 1.15f..1.25f)
        val beforePop = full.frameAt(SplashTimeline.HOLD_MS + SplashTimeline.APPEAR_MS + SplashTimeline.INFLATE_MS - 1)
        assertTrue("it pops at its biggest: ${beforePop.heartScale}", beforePop.heartScale in 1.15f..1.25f)
        assertTrue(SplashTimeline.MAX_SCALE in 1.15f..1.25f)
    }

    /**
     * PM recette 2026-10-31: "I see the M for a long time without the heart". The system launch screen already shows the M for
     * the whole start of the process: the beat of the animation itself stays short, and the heart is there within 0.2 s of the
     * first image.
     */
    @Test
    fun theHeartShowsUpAlmostRightAfterTheFirstImage() {
        assertTrue("first beat <= 150 ms", SplashTimeline.HOLD_MS <= 150L)
        val firstWithHeart = (0L until full.totalMs).first { full.frameAt(it).heartAlpha > 0f }
        assertTrue(
            "the heart starts to show right after the first beat",
            firstWithHeart in SplashTimeline.HOLD_MS..(SplashTimeline.HOLD_MS + 20L),
        )
        assertTrue("fully opaque within 0.25 s of the first image", full.frameAt(250L).heartAlpha == 1f)
    }

    /**
     * The app is composed under the animation (a heavy, freezing step) only once the heart has appeared, and long enough before
     * the exit that the first screen is ready when the animation fades. Reduced motion: nothing moves, so at once.
     */
    @Test
    fun theAppIsComposedUnderTheAnimationOnlyOnceTheHeartHasAppeared() {
        assertEquals(SplashTimeline.HOLD_MS + SplashTimeline.APPEAR_MS, full.contentMountMs)
        assertFalse(full.shouldMountContent(0L))
        assertFalse(full.shouldMountContent(full.contentMountMs - 1))
        assertTrue(full.shouldMountContent(full.contentMountMs))
        // At that moment the heart is fully there and still nearly at its resting size: a stall costs nothing visible.
        val atMount = full.frameAt(full.contentMountMs)
        assertEquals(1f, atMount.heartAlpha, 0f)
        assertEquals(1f, atMount.heartScale, 0.01f)
        // A freeze of 0.5 s right then still leaves the heart small, and the app is ready long before the exit.
        assertTrue(full.frameAt(full.contentMountMs + 500L).heartScale < 1.06f)
        assertTrue(full.contentMountMs + 1000L < full.totalMs - SplashTimeline.EXIT_MS)
        assertEquals(0L, reduced.contentMountMs)
        assertTrue(reduced.shouldMountContent(0L))
    }

    /** Real time, whatever happens: a stall never adds seconds, it only eats animation (the caller reads the real clock). */
    @Test
    fun noMatterHowLateTheClockIsRead_theSplashIsOverAtTheTotalAndNeverLater() {
        assertTrue(full.isFinished(full.totalMs))
        assertTrue(full.isFinished(full.totalMs + 5_000L))
        assertTrue(full.totalMs <= SplashTimeline.MAX_ALLOWED_MS)
    }

    @Test
    fun negativeTimeIsTreatedAsTheStart() {
        assertEquals(full.frameAt(0), full.frameAt(-50))
    }
}
