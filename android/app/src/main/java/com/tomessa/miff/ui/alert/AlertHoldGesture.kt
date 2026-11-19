package com.tomessa.miff.ui.alert

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.tomessa.miff.domain.alert.AlertHold
import com.tomessa.miff.domain.alert.AlertRules
import kotlinx.coroutines.delay

/*
 * The HOLD gesture of the alert (ticket docs/tickets/alerte-danger.md 7.2 and 7.4), shared by the button of the Update (start) and the banner (end). The rules of time live in
 * [AlertHold] (pure, tested); this file only reads the finger.
 *
 * ANTI-ACCIDENT (criteria AD1 to AD3): a touch shorter than [AlertRules.ARM_MILLIS] is a TAP (a photo, as before); only after that the screen turns into the alert and the 5 seconds
 * count; releasing, the finger drifting more than 48 dp, the window losing the focus (an incoming call, a system window) or the app going to the background cancels, SILENTLY (no
 * message, only a very light haptic): a false start is not commented. Haptics: a light one when the screen arms, a crescendo of short ones that get closer, a firm one at 5 s. No sound.
 */

/** What a screen reads of the gesture in progress. */
@Stable
class AlertHoldState {
    /** True from [AlertRules.ARM_MILLIS] of holding until the end or the cancellation. */
    var armed by mutableStateOf(false)
        internal set

    private var elapsedFraction by mutableFloatStateOf(0f)

    internal fun setFraction(value: Float) {
        elapsedFraction = value
    }

    /** 0.0 to 1.0, the red bar that fills up, worked out from the TIME held (not from an animation); 0 while not armed. */
    val progress: Float get() = if (armed) elapsedFraction else 0f
}

/**
 * Reads the finger on the node. [onTap] is a touch shorter than the arming time; [onDone] the 5 seconds held; [onCancel] anything that ends an ARMED gesture before 5 seconds. A node
 * that must keep a plain tap (the shutter) passes it as [onTap]; one that has none (the banner) leaves it empty.
 */
@Composable
fun Modifier.alertHoldGesture(
    state: AlertHoldState,
    enabled: Boolean = true,
    armMillis: Long = AlertRules.ARM_MILLIS,
    holdMillis: Long = AlertRules.HOLD_MILLIS,
    /** True: a touch that a child (a button of the banner) already consumed never starts the hold. */
    ignoreConsumed: Boolean = false,
    onTap: () -> Unit = {},
    onArmed: () -> Unit = {},
    onDone: () -> Unit,
    onCancel: () -> Unit = {},
): Modifier {
    val haptics = LocalHapticFeedback.current
    val tap by rememberUpdatedState(onTap)
    val armedCallback by rememberUpdatedState(onArmed)
    val done by rememberUpdatedState(onDone)
    val cancelled by rememberUpdatedState(onCancel)
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    // The app goes to the background, or the window loses the focus (an incoming call, a window of the system): an armed gesture is cancelled without a word.
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { state.reset() }
    LaunchedEffect(windowFocused) { if (!windowFocused) state.reset() }
    // The red bar is the TIME held, read at each frame: it fills in a straight line, and "supprimer les animations" (animator scale 0) cannot make it jump to 100 % while the person has
    // only held for a second: the fraction never comes from an animation (review of #275, m6).
    LaunchedEffect(state.armed) {
        state.setFraction(0f)
        if (state.armed) {
            var start = -1L
            while (true) {
                withFrameMillis { now ->
                    if (start < 0) start = now
                    state.setFraction(((now - start).toFloat() / holdMillis.toFloat()).coerceIn(0f, 1f))
                }
            }
        }
    }
    // The crescendo of short haptics (they get closer), while armed.
    LaunchedEffect(state.armed) {
        if (state.armed) {
            var pause = FIRST_PULSE_PAUSE_MILLIS
            while (true) {
                delay(pause)
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                pause = (pause * PULSE_SHRINK).toLong().coerceAtLeast(MIN_PULSE_PAUSE_MILLIS)
            }
        }
    }
    val slopPx = with(androidx.compose.ui.platform.LocalDensity.current) { AlertRules.HOLD_SLOP_DP.dp.toPx() }
    return this.pointerInput(enabled, armMillis, holdMillis, slopPx, ignoreConsumed) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = ignoreConsumed)
            // The touch is OURS from here: consumed like a plain click would (review of #275, m2): the double tap that switches the camera, a parent of the shutter, must never see
            // two quick photos as its own double tap.
            down.consume()
            val origin = down.position

            fun PointerInputChange.tooFar(): Boolean =
                AlertHold.movedTooFar(
                    position.x - origin.x,
                    position.y - origin.y,
                    slopPx,
                )

            // Phase 1, "Pressing": a quick release is a tap; anything else that ends the touch is nothing.
            val first =
                withTimeoutOrNull(armMillis) {
                    var result = Ended.CANCELLED
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        if (!change.pressed) {
                            result = Ended.RELEASED
                            break
                        }
                        if (change.tooFar()) break
                    }
                    result
                }
            if (first == Ended.RELEASED) {
                tap()
                return@awaitEachGesture
            }
            if (first == Ended.CANCELLED) return@awaitEachGesture
            // Phase 2, "Holding": the screen is in alert mode, the bar fills up.
            state.armed = true
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            armedCallback()
            val second =
                withTimeoutOrNull(holdMillis) {
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed || change.tooFar()) break
                        change.consume()
                    }
                    Ended.CANCELLED
                }
            if (!state.armed) {
                // Reset from outside (background, focus lost): already cancelled.
                cancelled()
                return@awaitEachGesture
            }
            state.reset()
            if (second == null) {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                done()
            } else {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                cancelled()
            }
        }
    }
}

private enum class Ended { RELEASED, CANCELLED }

internal fun AlertHoldState.reset() {
    armed = false
}

private const val FIRST_PULSE_PAUSE_MILLIS = 800L
private const val MIN_PULSE_PAUSE_MILLIS = 150L
private const val PULSE_SHRINK = 0.8f
