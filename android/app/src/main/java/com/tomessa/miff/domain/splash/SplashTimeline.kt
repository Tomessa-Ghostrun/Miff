package com.tomessa.miff.domain.splash

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Where the launch animation is. Full version: [HOLD] (the system launch screen continues, "M + i" alone), [APPEAR]
 * (the heart shows up on the "i"), [INFLATE] (it swells like a balloon), [BURST] (it pops, small green pieces fly off),
 * [EXIT] (fade to the app), [DONE]. "Reduce animations" version: [STILL] (full logo, nothing moves), [EXIT], [DONE].
 */
enum class SplashPhase { HOLD, APPEAR, INFLATE, BURST, STILL, EXIT, DONE }

/**
 * What to draw at one instant. Everything is a plain number so the whole animation can be unit-tested without a screen.
 *
 * @property heartScale size of the heart relative to its resting size on the "i" (1 = as in the app icon).
 * @property heartAlpha 0 = hidden, 1 = fully visible.
 * @property burstProgress null outside the burst, else 0 (just popped) to 1 (pieces gone).
 * @property overlayAlpha opacity of the whole splash: 1 until the exit, then fades to 0 to reveal the app underneath.
 */
data class SplashFrame(
    val phase: SplashPhase,
    val heartScale: Float,
    val heartAlpha: Float,
    val burstProgress: Float?,
    val overlayAlpha: Float,
)

/** One small piece thrown by the pop. Distances and sizes are fractions, scaled by the screen code. */
data class BurstParticle(
    val angleRadians: Float,
    /** 0 = on the edge of the burst heart, 1 = farthest point reached. */
    val distance: Float,
    /** 1 = full size, shrinking to 0. */
    val size: Float,
    val alpha: Float,
)

/**
 * The launch animation as a pure function of elapsed time (milliseconds since the splash was first drawn).
 *
 * Durations (ticket Onboarding section 5.1 and 15 SP1-SP6, PM requests): 2.7 s in total (2.9 s before the PM recette of
 * 2026-10-31 shortened the first beat), 3.0 s at the very most (the
 * PM wanted the swelling and the pop slower, "barely time to understand what happens"); with "reduce animations"
 * 0.8 s and no movement at all. The caller measures REAL time (frame clock), so a slow
 * device or a "slow animations" developer setting can never stretch the splash past [totalMs].
 *
 * Flash safety (SP4): one single appearance and one single disappearance of the heart, nothing blinks; the colors are
 * only the blue background, the white "M + i" and the brand green, no white flash, no red.
 */
class SplashTimeline(
    val reduceMotion: Boolean,
) {
    private val exitStartMs = if (reduceMotion) STILL_MS else HOLD_MS + APPEAR_MS + INFLATE_MS + BURST_MS

    /** Time after which the splash must be gone. */
    val totalMs: Long = exitStartMs + EXIT_MS

    fun isFinished(elapsedMs: Long): Boolean = elapsedMs >= totalMs

    /**
     * From when the app may be composed UNDER the animation. Its first composition is heavy and freezes the screen for a
     * moment: it must not happen before the heart is there (PM recette 2026-10-31: the "M" alone stayed for seconds because
     * the app was composed first). Full version: once the heart has appeared, at the start of the swelling, where the heart
     * is nearly still, so that a stall costs nothing visible. "Reduce animations": nothing moves, so at once.
     * Always well before the exit ([totalMs] minus [EXIT_MS]), so the first screen is ready when the animation fades.
     */
    val contentMountMs: Long = if (reduceMotion) 0L else HOLD_MS + APPEAR_MS

    fun shouldMountContent(elapsedMs: Long): Boolean = elapsedMs >= contentMountMs

    fun frameAt(elapsedMs: Long): SplashFrame {
        val t = elapsedMs.coerceAtLeast(0L)
        if (t >= totalMs) return SplashFrame(SplashPhase.DONE, 0f, 0f, null, 0f)
        if (t >= exitStartMs) {
            val fade = 1f - (t - exitStartMs).toFloat() / EXIT_MS
            return if (reduceMotion) {
                SplashFrame(SplashPhase.EXIT, 1f, 1f, null, fade)
            } else {
                // The burst is over: only the logo is left, it fades away together with the blue.
                SplashFrame(SplashPhase.EXIT, 0f, 0f, null, fade)
            }
        }
        if (reduceMotion) return SplashFrame(SplashPhase.STILL, 1f, 1f, null, 1f)

        val appearStart = HOLD_MS
        val inflateStart = appearStart + APPEAR_MS
        val burstStart = inflateStart + INFLATE_MS
        return when {
            t < appearStart -> {
                SplashFrame(SplashPhase.HOLD, 0f, 0f, null, 1f)
            }

            t < inflateStart -> {
                val p = (t - appearStart).toFloat() / APPEAR_MS
                SplashFrame(SplashPhase.APPEAR, easeOutBack(p), (p * 3f).coerceAtMost(1f), null, 1f)
            }

            t < burstStart -> {
                // The last [POP_PAUSE_MS] of the swelling: the heart holds at its biggest size for an instant, so that the
                // pop that follows can be read.
                val p = ((t - inflateStart).toFloat() / (INFLATE_MS - POP_PAUSE_MS)).coerceAtMost(1f)
                SplashFrame(SplashPhase.INFLATE, inflateScale(p), 1f, null, 1f)
            }

            else -> {
                // Sharp pop: the heart is gone on the very first instant of the burst.
                SplashFrame(SplashPhase.BURST, 0f, 0f, (t - burstStart).toFloat() / BURST_MS, 1f)
            }
        }
    }

    companion object {
        /**
         * "M + i" alone, after the first image. The system launch screen has ALREADY shown the same picture for the whole
         * start of the process, so this beat is short (PM recette 2026-10-31: "I see the M for a long time without the heart";
         * it was 300 ms). Total: 2.7 s, 3.0 s at the very most.
         */
        const val HOLD_MS = 100L
        const val APPEAR_MS = 400L

        /** Swelling, including the final [POP_PAUSE_MS] where the heart holds at its biggest size. */
        const val INFLATE_MS = 1500L
        const val POP_PAUSE_MS = 120L
        const val BURST_MS = 500L
        const val EXIT_MS = 200L

        /** Reduced version: the full logo stays still this long, then fades. */
        const val STILL_MS = 600L

        /** Upper bound promised to the PM: the animated version never exceeds it. */
        const val MAX_ALLOWED_MS = 3000L

        /**
         * Size reached by the heart just before it pops, relative to its resting size (1 = the size of the heart on the
         * official logo and icon): "a little bigger than on the official logo" (PM, recette 2026-10-30), 115 to 125 %.
         */
        const val MAX_SCALE = 1.2f

        const val PARTICLE_COUNT = 12
    }
}

/** Soft overshoot then settle: 0 -> about 1.1 -> 1. */
internal fun easeOutBack(p: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    val x = p.coerceIn(0f, 1f) - 1f
    return 1f + c3 * x * x * x + c1 * x * x
}

/**
 * Balloon being blown up: starts slowly (1 = resting size, p = 0), then swells faster and faster (progressive acceleration) up to
 * [SplashTimeline.MAX_SCALE], with a small elastic wobble that grows as the balloon gets tense. The wobble is zero at
 * both ends so the scale is continuous with the appearance before it.
 */
internal fun inflateScale(p: Float): Float {
    val x = p.coerceIn(0f, 1f)
    val growth = 1f + (SplashTimeline.MAX_SCALE - 1f) * x.pow(2.2f)
    val envelope = (0.3f + 0.7f * x) * sin(PI.toFloat() * x).coerceAtLeast(0f).pow(0.5f)
    val wobble = 1f + 0.03f * envelope * sin(2f * PI.toFloat() * (2.5f * x + 4f * x * x))
    return growth * wobble
}

/** The pieces thrown by the pop, at [progress] 0 (just popped) to 1 (gone). Same on every launch (no randomness). */
fun burstParticles(progress: Float): List<BurstParticle> {
    val p = progress.coerceIn(0f, 1f)
    val outward = 1f - (1f - p).pow(2.2f) // fast at first, then slowing down (slower than before: the pop is read)
    val alpha = (1f - p * p).coerceIn(0f, 1f)
    return List(SplashTimeline.PARTICLE_COUNT) { i ->
        val big = i % 2 == 0
        BurstParticle(
            // Upper 240 degrees only (from 150 to 390 degrees, y pointing down): nothing flies down over the "M" and the "i".
            angleRadians = PI.toFloat() * (150f + 240f * (i + 0.5f) / SplashTimeline.PARTICLE_COUNT) / 180f,
            distance = outward * (if (big) 1f else 0.65f),
            size = (1f - 0.8f * p) * (if (big) 1f else 0.6f),
            alpha = alpha,
        )
    }
}

/** Unit direction of a particle, handy for drawing. */
fun BurstParticle.directionX(): Float = cos(angleRadians)

fun BurstParticle.directionY(): Float = sin(angleRadians)
