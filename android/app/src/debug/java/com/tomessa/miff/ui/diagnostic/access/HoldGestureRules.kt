package com.tomessa.miff.ui.diagnostic.access

import kotlin.math.hypot

/**
 * Marker of the hold detector. It is the key of the detector and the only place of the code where this word is written: the CI (android.yml and
 * android/ci/check-release-bundle.sh) looks for it in the dex of the RELEASE bundle and refuses the build if it finds it, which proves that the
 * secret gesture to the diagnostic screen is not in the app that is distributed. Do not rename it without changing both checks and ReleaseSigningGuardTest.
 */
internal const val DIAGNOSTIC_HOLD_MARKER = "DIAGNOSTICHOLDGESTURE"

/** Constants of the gesture (PM request of 2026-11-12): one finger kept down for 10 seconds, moving less than 20 dp. */
object HoldGestureRules {
    const val HOLD_MILLIS = 10_000L
    const val MOVE_TOLERANCE_DP = 20f
}

/**
 * Pure rules of ONE hold (from the finger going down to the end of the gesture). No Android here, so that the rules are tested on the JVM.
 *
 * - The hold is completed when [onTime] is called at or after [holdMillis] after the finger went down, while the hold is still going on.
 * - It is cancelled at once when the finger goes up (0 finger down), when a second finger comes (2 or more), or when the finger moved
 *   more than [tolerancePx] away from where it went down (a scroll, a swipe, a drag).
 * - Once completed or cancelled it stays so: a new hold is a new tracker (a new finger going down).
 */
class HoldGestureTracker(
    private val startX: Float,
    private val startY: Float,
    private val startMillis: Long,
    private val tolerancePx: Float,
    private val holdMillis: Long = HoldGestureRules.HOLD_MILLIS,
) {
    enum class State { HOLDING, CANCELLED, COMPLETED }

    var state: State = State.HOLDING
        private set

    /** Called at each touch event with the number of fingers CURRENTLY down and the position of the finger that started the hold. */
    fun onPointers(
        pressedCount: Int,
        x: Float,
        y: Float,
    ): State {
        if (state != State.HOLDING) return state
        if (pressedCount != 1 || hypot(x - startX, y - startY) > tolerancePx) state = State.CANCELLED
        return state
    }

    /** Called with the current time (any increasing clock, the same as [startMillis]). */
    fun onTime(nowMillis: Long): State {
        if (state == State.HOLDING && nowMillis - startMillis >= holdMillis) state = State.COMPLETED
        return state
    }
}
