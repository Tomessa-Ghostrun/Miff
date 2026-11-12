package com.tomessa.miff.ui.diagnostic.access

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The hold detector as a modifier, to put at the ROOT of a window (the whole app, a dialog, a sheet). It asks the navigation to open the
 * diagnostic screen after a 10 s hold (see [HoldGestureRules]) and gives a short haptic tick when it does.
 */
@Composable
fun diagnosticHoldModifier(): Modifier {
    val haptic = LocalHapticFeedback.current
    val open =
        rememberUpdatedState<() -> Unit> {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            DiagnosticRequests.request()
        }
    return Modifier.diagnosticHoldDetector { open.value() }
}

/**
 * Watches the touches in the INITIAL pass (before the children) and never consumes anything: taps, scrolls, buttons, the camera and the text
 * fields behave as if it was not there. A finger that moves more than 20 dp, goes up, or a second finger cancels the hold.
 */
internal fun Modifier.diagnosticHoldDetector(onHold: () -> Unit): Modifier =
    pointerInput(DIAGNOSTIC_HOLD_MARKER) {
        val tolerancePx = HoldGestureRules.MOVE_TOLERANCE_DP.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val tracker = HoldGestureTracker(down.position.x, down.position.y, down.uptimeMillis, tolerancePx)
            // null = the 10 s went by with the hold still going on; true = it was cancelled before.
            val cancelled =
                withTimeoutOrNull(HoldGestureRules.HOLD_MILLIS) {
                    var over = false
                    while (!over) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pressed = event.changes.count { it.pressed }
                        val mine = event.changes.firstOrNull { it.id == down.id }
                        val state =
                            if (mine == null || !mine.pressed) {
                                tracker.onPointers(0, 0f, 0f)
                            } else {
                                tracker.onPointers(pressed, mine.position.x, mine.position.y)
                            }
                        over = state == HoldGestureTracker.State.CANCELLED
                    }
                    true
                }
            if (cancelled == null &&
                tracker.onTime(down.uptimeMillis + HoldGestureRules.HOLD_MILLIS) == HoldGestureTracker.State.COMPLETED
            ) {
                onHold()
            }
        }
    }
