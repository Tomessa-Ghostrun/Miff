package com.tomessa.miff.ui.paywall

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import com.tomessa.miff.domain.paywall.PeriodicPaywallDecision
import com.tomessa.miff.domain.paywall.PeriodicPaywallRules
import com.tomessa.miff.domain.paywall.PeriodicPaywallSituation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/*
 * The periodic offer, on screen (ticket docs/tickets/paywall-periodique.md, PAY-PER-A). It adds NO screen: it opens the existing offer (the single offer route, with the
 * reason PERIODIC), so the 3 seconds of the close cross, the two analytics events and the text are the ones of every other entry. Nothing here reads what a touch is ABOUT: only that it
 * happened, where it stopped and whether something consumed it.
 */

/** What the tabs need from the outside to offer the periodic offer: the rules and the way to open the offer. */
class PeriodicPaywallHost(
    val rules: PeriodicPaywallRules,
    /** Opens the offer (a push of the single offer route, reason PERIODIC). */
    val open: () -> Unit,
)

/**
 * Looks at the rules whenever something changes (the situation of the screen, a touch, the first tap, a new session) and, once everything is right, opens the offer. Draws nothing. The wait of
 * the quiet moment is a plain delay that any touch cancels and starts again. It only lives while the tabs are composed: a screen pushed over them (a form, the Settings, the offer itself)
 * stops it, and [PeriodicPaywallSituation.tabsInFront] stops it during a transition.
 */
@Composable
fun PeriodicPaywallWatcher(
    host: PeriodicPaywallHost,
    situation: PeriodicPaywallSituation,
) {
    val rules = host.rules
    LaunchedEffect(rules, situation) {
        rules.changes.collectLatest {
            while (true) {
                when (val decision = rules.evaluate(situation)) {
                    PeriodicPaywallDecision.Show -> {
                        rules.onLaunched()
                        host.open()
                        return@collectLatest
                    }

                    is PeriodicPaywallDecision.Wait -> {
                        delay(decision.millis)
                    }

                    PeriodicPaywallDecision.No -> {
                        return@collectLatest
                    }
                }
            }
        }
    }
}

/**
 * Tells the rules about every touch on the window (a finger down, moving, up: the offer waits for a quiet moment) and about a TAP THAT ACTIVATED SOMETHING: one finger, down and up on the
 * same spot (within the touch slop), and a button, a card or a tab took it (it consumed the lift). A scroll or a swipe (the finger travelled), a touch on nothing (nobody consumed it) and
 * two fingers never count (criterion PP8). Observed AFTER the children (final pass) and never consumed: the tap does what it was meant to do, the offer comes later.
 *
 * Known limit, assumed: an activation by the accessibility services (TalkBack double tap, which sends a click to the element and no touch) and the Enter key are NOT seen here; a person who only
 * uses them does not see the periodic offer (the quiet side of the risk).
 */
fun Modifier.periodicPaywallTouches(rules: PeriodicPaywallRules): Modifier =
    pointerInput(rules) {
        val slop = viewConfiguration.touchSlop
        awaitPointerEventScope {
            var down: Offset? = null
            var candidate = false
            var moved = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val changes = event.changes
                rules.onTouch(pressed = changes.any { it.pressed })
                if (changes.size != 1) {
                    candidate = false
                    continue
                }
                val change = changes[0]
                when {
                    change.changedToDownIgnoreConsumed() -> {
                        down = change.position
                        candidate = true
                        moved = false
                    }

                    change.changedToUpIgnoreConsumed() -> {
                        if (candidate && !moved && change.isConsumed) rules.onActivation()
                        candidate = false
                        down = null
                    }

                    change.pressed -> {
                        val start = down
                        if (start != null && (change.position - start).getDistance() > slop) moved = true
                    }
                }
            }
        }
    }
