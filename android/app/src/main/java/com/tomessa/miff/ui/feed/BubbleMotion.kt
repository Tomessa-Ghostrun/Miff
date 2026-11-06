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
 * The VERY LIGHT, slow and continuous movement of the bubble of the Miff (decision of the PM after his test, 2026-11-06): a breath of scale and
 * a tiny vertical drift, each a pure sine (the softest easing there is), the star of the bubble a little out of phase with the blob.
 *
 * What it is NOT, on purpose (tone of the app, CLAUDE.md: kind, never alarming, even on a red bubble): never a blink, a flash, a bounce or a
 * quick movement; the amplitudes are tiny and the cycle is slow. What it NEVER costs:
 * - with "Animations supprimées" (animator duration scale 0): NO animation at all, the bubble is still;
 * - not in the background, and not when the Feed tab is not the one in use (the composition is left: nothing runs); not off screen either (a
 *   LazyColumn drops what scrolled away);
 * - no server reading, no recomputation of the colour (the two phases are read in the DRAW phase only: no recomposition, no layout, so the
 *   margins and the tab bar never move), and nothing for the screen reader (the movement is a graphics layer, the announcement is a node apart).
 */
object BubbleMotion {
    /** Breath of scale around 1: at most 1.2 % each way (the PM's ceiling is 1.5 %). */
    const val SCALE_AMPLITUDE = 0.012f

    /** Vertical drift: at most 2 dp each way (the PM's ceiling is 3 dp). */
    const val DRIFT_DP = 2f

    /** The star drifts by 1 dp more or less ON TOP of the blob's 2 dp: its total stays under the PM's ceiling of 3 dp. */
    const val STAR_DRIFT_DP = 1f

    /** One slow cycle of the blob (the PM asks 5 to 8 s). */
    const val BLOB_PERIOD_MILLIS = 6_500

    /** The star of the bubble breathes on a slightly different, equally slow cycle, so the two never move in lockstep. */
    const val STAR_PERIOD_MILLIS = 7_500

    /** Where in its cycle the drift starts, so that it is out of phase with the breath (a quarter of a cycle). */
    const val DRIFT_PHASE_SHIFT = 0.25f

    /** No animation when the person asked for none, nor when the screen is not on view (not started). */
    fun enabled(
        reduceMotion: Boolean,
        started: Boolean,
    ): Boolean = started && !reduceMotion

    /** Sine of a phase in [0, 1) (one cycle): the softest possible easing, no corner at the turning points. */
    fun wave(phase: Float): Float = sin(2.0 * PI * phase).toFloat()

    fun scale(phase: Float): Float = 1f + SCALE_AMPLITUDE * wave(phase)

    fun driftDp(phase: Float): Float = DRIFT_DP * wave(phase + DRIFT_PHASE_SHIFT)

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
