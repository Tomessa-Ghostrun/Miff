package com.tomessa.miff.domain.alert

/*
 * The HOLD gesture of the alert (ticket 7.2 and 7.4), as a pure function of time so that it is tested without a screen. The same gesture starts an alert (on the button of the
 * Update) and ends one (on the banner).
 *
 * Phases, from the moment the finger goes down:
 * - [HoldPhase.Pressing] for [AlertRules.ARM_MILLIS]: nothing changes on the screen (a simple long press is not an alert, and a tap is a photo);
 * - [HoldPhase.Holding] for [AlertRules.HOLD_MILLIS]: the screen is in alert mode, the red bar fills up ([HoldPhase.Holding.fraction] from 0 to 1);
 * - [HoldPhase.Done]: 5 seconds were held: the alert leaves (or ends).
 * Releasing at any time before [HoldPhase.Done] cancels, silently. Once in [HoldPhase.Holding] the finger may leave the button: only the release stops it.
 */
sealed interface HoldPhase {
    data object Pressing : HoldPhase

    class Holding(
        /** 0.0 (just armed) to 1.0 (about to leave). */
        val fraction: Float,
    ) : HoldPhase

    data object Done : HoldPhase
}

object AlertHold {
    fun phaseAt(
        heldMillis: Long,
        armMillis: Long = AlertRules.ARM_MILLIS,
        holdMillis: Long = AlertRules.HOLD_MILLIS,
    ): HoldPhase =
        when {
            heldMillis < armMillis -> HoldPhase.Pressing
            heldMillis >= armMillis + holdMillis -> HoldPhase.Done
            else -> HoldPhase.Holding(((heldMillis - armMillis).toFloat() / holdMillis.toFloat()).coerceIn(0f, 1f))
        }

    /** True when the finger moved too far from where it went down (in px; [slopPx] is 48 dp): BEFORE the arming only (a swipe, not a hold) the touch is cancelled; once armed it is not read. */
    fun movedTooFar(
        dxPx: Float,
        dyPx: Float,
        slopPx: Float,
    ): Boolean = dxPx * dxPx + dyPx * dyPx > slopPx * slopPx
}
