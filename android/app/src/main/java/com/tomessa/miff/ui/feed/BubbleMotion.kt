package com.tomessa.miff.ui.feed

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tomessa.miff.ui.splash.ReduceMotion
import kotlin.math.PI
import kotlin.math.sin

/*
 * The movement of the bubble of the Miff: slow, continuous and ORGANIC, like a soap bubble or a drop that breathes. First decision of the PM
 * (2026-11-06, D40): a very light breath; revised after his test on a real phone (2026-11-07, D45): too subtle, and it must DEFORM a little, not
 * only swell. So the blob is squashed and stretched on its two axes out of phase (never a uniform scale), tilts by a fraction of a degree and
 * drifts a few dp vertically; the text and the button inside are NOT deformed (only the shape and its background are).
 *
 * Every number below is a named constant, so that the movement can be tuned quickly: the amplitudes (SQUASH_X, SQUASH_Y, TILT_DEGREES,
 * DRIFT_DP...), the cycles (BLOB_PERIOD_MILLIS, STAR_PERIOD_MILLIS) and the phase shifts between the axes.
 *
 * Each movement is a pure sine (the softest easing there is). The shape only ever SHRINKS from its full size (its scale stays between
 * 1 - 2 x amplitude and 1): it never grows past the box it was laid out in, so the scrolling container and the pager never cut an edge.
 *
 * What it is NOT, on purpose (tone of the app, CLAUDE.md: kind, never alarming, even on a red bubble): never a blink, a flash, a bounce or a
 * quick movement; the cycle is slow. What it NEVER costs:
 * - with "Animations supprimées" (animator duration scale 0): NO animation at all, the bubble is still (full size, no tilt, no drift);
 * - not in the background, and not when the Feed tab is not the one in use (the composition is left: nothing runs); not off screen either (a
 *   LazyColumn drops what scrolled away);
 * - no server reading, no recomputation of the colour; the phases are read in the DRAW phase only, in graphics layers (GPU transformations:
 *   no recomposition, no layout, so the margins, the touch areas and the tab bar never move), and nothing for the screen reader (the
 *   announcement is a node apart; the text and the button are not transformed, only translated by the slow drift).
 */
object BubbleMotion {
    // ---- the shape of the blob (squashed and stretched, out of phase) ------------------------------------------------------------

    /** Horizontal squash: the width goes back and forth between 100 % and 92 % (peak to peak 8 %, that is +-4 % around its mean). */
    const val SQUASH_X = 0.04f

    /** Vertical squash: the height goes back and forth between 100 % and 90 % (peak to peak 10 %, that is +-5 % around its mean). */
    const val SQUASH_Y = 0.05f

    /** The vertical axis runs a third of a cycle behind the horizontal one: when it is wide it becomes low, then both soften (it is what makes the shape deform). */
    const val Y_PHASE_SHIFT = 0.33f

    /** A tilt of the shape (not of its text) of a fraction of a degree each way, on its own phase: the contour never "pulses" in place. */
    const val TILT_DEGREES = 0.8f

    /** Where in its cycle the tilt starts. */
    const val TILT_PHASE_SHIFT = 0.6f

    // ---- the drift ------------------------------------------------------------------------------------------------------------------

    /** Vertical drift of the whole bubble (shape and content): at most 3.5 dp each way. */
    const val DRIFT_DP = 3.5f

    /** Where in its cycle the drift starts, so that it is out of phase with the squash (a quarter of a cycle). */
    const val DRIFT_PHASE_SHIFT = 0.25f

    // ---- the pictogram ("star") of the bubble -----------------------------------------------------------------------------------

    /** The round pictogram breathes by +-5 % (it is a circle with an icon: a uniform scale, nothing to deform). */
    const val STAR_SCALE_AMPLITUDE = 0.05f

    /** The star drifts by 0.5 dp more or less ON TOP of the drift of the bubble: its total stays at 4 dp. */
    const val STAR_DRIFT_DP = 0.5f

    // ---- the cycles -------------------------------------------------------------------------------------------------------------------

    /** One slow cycle of the blob (6 s: between the 5 and 8 s of D40, which D45 keeps). */
    const val BLOB_PERIOD_MILLIS = 6_000

    /** The star runs on a slightly different, equally slow cycle, so the two never move in lockstep. */
    const val STAR_PERIOD_MILLIS = 7_000

    /** No animation when the person asked for none, nor when the screen is not on view (not started). */
    fun enabled(
        reduceMotion: Boolean,
        started: Boolean,
    ): Boolean = started && !reduceMotion

    /** Sine of a phase in [0, 1) (one cycle): the softest possible easing, no corner at the turning points. */
    fun wave(phase: Float): Float = sin(2.0 * PI * phase).toFloat()

    /** A scale that goes between 1 - 2 x [amplitude] and 1 (never above 1), following [wave]. */
    private fun shrink(
        amplitude: Float,
        phase: Float,
    ): Float = 1f - amplitude * (1f - wave(phase))

    fun scaleX(phase: Float): Float = shrink(SQUASH_X, phase)

    fun scaleY(phase: Float): Float = shrink(SQUASH_Y, phase + Y_PHASE_SHIFT)

    fun tiltDegrees(phase: Float): Float = TILT_DEGREES * wave(phase + TILT_PHASE_SHIFT)

    fun driftDp(phase: Float): Float = DRIFT_DP * wave(phase + DRIFT_PHASE_SHIFT)

    fun starScale(phase: Float): Float = 1f + STAR_SCALE_AMPLITUDE * wave(phase)

    fun starDriftDp(phase: Float): Float = STAR_DRIFT_DP * wave(phase + DRIFT_PHASE_SHIFT)
}

/** The two phases of a running movement; read them only inside a graphics layer (draw phase). */
class BubblePhases(
    val blob: State<Float>,
    val star: State<Float>,
)

/**
 * The movement of the bubble, or null when there must be none (animations removed by the person, or the screen not started). When it is null no
 * infinite transition exists at all. [reduceMotion] and the lifecycle are read again each time the screen comes back to the foreground.
 */
@Composable
internal fun rememberBubblePhases(): BubblePhases? {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state by lifecycle.currentStateFlow.collectAsState()
    val started = state.isAtLeast(Lifecycle.State.STARTED)
    // Asked again at each return to the foreground: the person may have changed the setting in the meantime.
    val reduceMotion = remember(state) { ReduceMotion.isRequested(context) }
    if (!BubbleMotion.enabled(reduceMotion, started)) return null
    val transition = rememberInfiniteTransition(label = "bubble")
    val blob =
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    tween(BubbleMotion.BLOB_PERIOD_MILLIS, easing = LinearEasing),
                    RepeatMode.Restart,
                ),
            label = "blob",
        )
    val star =
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    tween(BubbleMotion.STAR_PERIOD_MILLIS, easing = LinearEasing),
                    RepeatMode.Restart,
                ),
            label = "star",
        )
    return remember(blob, star) { BubblePhases(blob, star) }
}
