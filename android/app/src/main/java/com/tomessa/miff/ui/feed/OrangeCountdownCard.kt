package com.tomessa.miff.ui.feed

import android.content.res.Resources
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.tomessa.miff.R
import com.tomessa.miff.domain.tracking.CountdownAnchor
import com.tomessa.miff.domain.tracking.CountdownDuration
import com.tomessa.miff.domain.tracking.CountdownState
import com.tomessa.miff.domain.tracking.OrangeCountdown
import com.tomessa.miff.ui.splash.ReduceMotion
import com.tomessa.miff.ui.theme.RadiusMd
import com.tomessa.miff.ui.theme.ShadowDefault
import com.tomessa.miff.ui.theme.ShapeMd
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.colorBgSurface
import com.tomessa.miff.ui.theme.colorBorderDefault
import com.tomessa.miff.ui.theme.colorStatusSafe
import com.tomessa.miff.ui.theme.colorStatusWarning
import com.tomessa.miff.ui.theme.colorTextDescription
import com.tomessa.miff.ui.theme.colorTextPrimary
import com.tomessa.miff.ui.theme.miffShadow
import com.tomessa.miff.ui.theme.textBodyBold14
import com.tomessa.miff.ui.theme.textBodyRegular14
import kotlinx.coroutines.delay
import kotlin.math.min

/*
 * COUNTDOWN-A (docs/tickets/compte-a-rebours-orange.md): the encart "Tu es en dehors de tes lieux de confiance / Tu passeras orange dans 1 h 12 min", at the top of
 * the Profile and just under the bubble of the Miff on the Feed. ONE component for both tabs (same value at the same instant, criterion CD-22).
 *
 * PRIVACY and COST: nothing is read or written (no Firestore, no network, no notification, no analytics event). It is computed from the anchor of MY status
 * (see OrangeCountdown) and the clock of the phone. The clock runs ONLY while the encart is composed AND the screen is started (the tab in use, the app in front):
 * one refresh per minute of the DEADLINE (not of the wall clock), none in the background, and a recomputation from the absolute time at each return.
 */

/**
 * The state of the countdown for [anchor], refreshed on the minute boundaries of the deadline while the screen is started. The first value is already the right one
 * (computed from the clock when the encart enters the composition), so there is no flash of an old value. [onRefresh] is told of each refresh (tests only).
 */
@Composable
internal fun rememberOrangeCountdown(
    anchor: CountdownAnchor?,
    clock: () -> Long = System::currentTimeMillis,
    onRefresh: () -> Unit = {},
): CountdownState {
    val currentClock by rememberUpdatedState(clock)
    val currentOnRefresh by rememberUpdatedState(onRefresh)
    var state by remember(anchor) { mutableStateOf(OrangeCountdown.at(anchor, clock())) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(anchor, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val now = currentClock()
                state = OrangeCountdown.at(anchor, now)
                currentOnRefresh()
                // Hidden: nothing will change any more until the anchor does (a new update) or the screen comes back (this block starts again).
                val wait = OrangeCountdown.millisUntilNextChange(anchor, now) ?: break
                delay(wait)
            }
        }
    }
    return state
}

/**
 * The encart with its entrance and exit, for a host that has an anchor: nothing at all when hidden. [padding] is INSIDE the animated block (the air above the
 * encart on the Feed, below it on the Profile), so that the content under it moves with it, never with a jump, and takes no room while the encart is absent.
 */
@Composable
fun OrangeCountdownSlot(
    anchor: CountdownAnchor?,
    onOpenUpdate: () -> Unit,
    padding: PaddingValues,
    modifier: Modifier = Modifier,
    clock: () -> Long = System::currentTimeMillis,
    onRefresh: () -> Unit = {},
) {
    val state = rememberOrangeCountdown(anchor, clock, onRefresh)
    val reduceMotion = ReduceMotion.isRequested(LocalContext.current)
    val visible = state as? CountdownState.Visible
    // The last visible value is kept for the fade-out (the state is Hidden by then).
    var last by remember { mutableStateOf(visible) }
    if (visible != null) last = visible
    // The very first image is already as it must be: a tab opened with the countdown running does not fade it in.
    val transition = remember { MutableTransitionState(visible != null) }
    transition.targetState = visible != null
    AnimatedVisibility(
        visibleState = transition,
        modifier = modifier,
        enter =
            if (reduceMotion) {
                EnterTransition.None
            } else {
                fadeIn(tween(FADE_MILLIS)) +
                    expandVertically(tween(FADE_MILLIS))
            },
        exit =
            if (reduceMotion) {
                ExitTransition.None
            } else {
                fadeOut(tween(FADE_MILLIS)) +
                    shrinkVertically(tween(FADE_MILLIS))
            },
    ) {
        last?.let { shown ->
            Box(Modifier.padding(padding)) { OrangeCountdownCard(shown, onOpenUpdate) }
        }
    }
}

/**
 * The card: white, `radius/md`, `shadow/default`, a dynamic border that IS the countdown. The text always says the time left, the border only repeats it
 * (its yellow-green on white is under 3:1: it is never the only carrier of the information). One element for TalkBack, a button, named with the spoken duration
 * and the action "Donner de tes nouvelles"; no live region (the value is read up to date when the element takes the focus, never announced each minute).
 */
@Composable
fun OrangeCountdownCard(
    state: CountdownState.Visible,
    onOpenUpdate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val reduceMotion = ReduceMotion.isRequested(LocalContext.current)
    // Slides to the new length over 600 ms at each minute boundary; still in between; at once with "reduce animations". The first image has no filling animation.
    val fraction by animateFloatAsState(
        targetValue = state.fraction,
        animationSpec = if (reduceMotion) snap() else tween(BORDER_MILLIS, easing = FastOutSlowInEasing),
        label = "countdown-border",
    )
    val duration = CountdownTexts.duration(resources, state.duration)
    val label = stringResource(R.string.countdown_a11y_label, CountdownTexts.spoken(resources, state.duration))
    val action = stringResource(R.string.countdown_a11y_action)
    val density = LocalDensity.current
    // The hourglass follows the size of the text without ever exceeding one and a half times its base size.
    val hourglassSize = with(density) { (HOURGLASS_BASE * min(fontScale, MAX_HOURGLASS_SCALE)).toSp() }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .miffShadow(ShadowDefault, ShapeMd)
                .clip(ShapeMd)
                .background(colorBgSurface)
                .countdownBorder(
                    fraction = { fraction },
                    strokeWidth = BORDER_WIDTH,
                    cornerRadius = RadiusMd,
                ).clickable(onClickLabel = action, role = Role.Button, onClick = onOpenUpdate)
                .clearAndSetSemantics {
                    contentDescription = label
                    role = Role.Button
                    onClick(label = action) {
                        onOpenUpdate()
                        true
                    }
                }.heightIn(min = Spacing.minTouchTarget)
                .padding(horizontal = Spacing.m, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s + Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The hourglass is a character of the display string, not of the translation catalogue (decorative, hidden from the screen reader above).
        Text(text = HOURGLASS, style = TextStyle(fontSize = hourglassSize, lineHeight = hourglassSize * 1.25f))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = stringResource(R.string.countdown_title), style = textBodyBold14, color = colorTextPrimary)
            Text(
                text = stringResource(R.string.countdown_body, duration),
                style = textBodyRegular14,
                color = colorTextPrimary,
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron_end),
            contentDescription = null,
            tint = colorTextDescription,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * The border: the whole outline as a grey track (`border/default`, 3 dp), and over it an arc of the same width whose LENGTH, measured along the outline, is
 * [fraction] of it. The arc is anchored at the top centre and the outline empties clockwise, like a clock (after a quarter of the time, the first quarter after
 * the top centre has gone; after half, the right half). Not mirrored in right-to-left. Green (`status/safe`) then, over the last quarter, sliding towards
 * `status/warning`: never red. [fraction] is read in the draw phase only: the slide of 600 ms redraws, it never recomposes.
 */
private fun Modifier.countdownBorder(
    fraction: () -> Float,
    strokeWidth: Dp,
    cornerRadius: Dp,
): Modifier =
    drawWithCache {
        val stroke = strokeWidth.toPx()
        val half = stroke / 2f
        val outline =
            CountdownOutline.clockwiseFromTopCentre(
                size.width,
                size.height,
                half,
                (cornerRadius.toPx() - half).coerceAtLeast(0f),
            )
        val measure = PathMeasure().apply { setPath(outline, false) }
        val length = measure.length
        onDrawBehind {
            drawPath(outline, colorBorderDefault, style = Stroke(width = stroke))
            val shown = fraction().coerceIn(0f, 1f)
            if (shown > 0f && length > 0f) {
                val arc = Path()
                // From (1 - f) of the outline to its end: the end stays at the top centre, the head moves clockwise as the time runs out.
                measure.getSegment(length * (1f - shown), length, arc, true)
                val color = lerp(colorStatusSafe, colorStatusWarning, OrangeCountdown.warmth(shown))
                drawPath(arc, color, style = Stroke(width = stroke))
            }
        }
    }

/** The outline of a rounded rectangle as ONE path that starts at the top centre and goes clockwise (the path of a rounded rectangle of the system starts elsewhere). */
internal object CountdownOutline {
    fun clockwiseFromTopCentre(
        width: Float,
        height: Float,
        inset: Float,
        radius: Float,
    ): Path {
        val left = inset
        val top = inset
        val right = width - inset
        val bottom = height - inset
        val r = radius.coerceAtMost(min(right - left, bottom - top) / 2f)
        val d = 2f * r
        return Path().apply {
            moveTo((left + right) / 2f, top)
            lineTo(right - r, top)
            arcTo(Rect(right - d, top, right, top + d), -90f, 90f, false)
            lineTo(right, bottom - r)
            arcTo(Rect(right - d, bottom - d, right, bottom), 0f, 90f, false)
            lineTo(left + r, bottom)
            arcTo(Rect(left, bottom - d, left + d, bottom), 90f, 90f, false)
            lineTo(left, top + r)
            arcTo(Rect(left, top, left + d, top + d), 180f, 90f, false)
            close()
        }
    }
}

/** The durations of the encart, written and spoken from the catalogue (the same words as iOS, whatever the system formatter would say). */
internal object CountdownTexts {
    fun duration(
        resources: Resources,
        duration: CountdownDuration,
    ): String =
        when (duration) {
            is CountdownDuration.HoursMinutes -> {
                resources.getString(
                    R.string.countdown_duration_hours_minutes,
                    duration.hours,
                    duration.minutes,
                )
            }

            is CountdownDuration.Hours -> {
                resources.getString(R.string.countdown_duration_hours, duration.hours)
            }

            is CountdownDuration.Minutes -> {
                resources.getString(R.string.countdown_duration_minutes, duration.minutes)
            }

            CountdownDuration.LessThanMinute -> {
                resources.getString(R.string.countdown_duration_less_than_minute)
            }
        }

    /** "1 heure et 12 minutes", "47 minutes", "moins d'une minute": what the screen reader says. */
    fun spoken(
        resources: Resources,
        duration: CountdownDuration,
    ): String =
        when (duration) {
            is CountdownDuration.HoursMinutes -> {
                resources.getString(
                    R.string.countdown_a11y_hours_and_minutes,
                    resources.getQuantityString(R.plurals.countdown_a11y_hours, duration.hours, duration.hours),
                    resources.getQuantityString(R.plurals.countdown_a11y_minutes, duration.minutes, duration.minutes),
                )
            }

            is CountdownDuration.Hours -> {
                resources.getQuantityString(R.plurals.countdown_a11y_hours, duration.hours, duration.hours)
            }

            is CountdownDuration.Minutes -> {
                resources.getQuantityString(R.plurals.countdown_a11y_minutes, duration.minutes, duration.minutes)
            }

            CountdownDuration.LessThanMinute -> {
                resources.getString(R.string.countdown_a11y_less_than_minute)
            }
        }
}

private const val HOURGLASS = "⏳"
private const val FADE_MILLIS = 200
private const val BORDER_MILLIS = 600
private const val MAX_HOURGLASS_SCALE = 1.5f
private val HOURGLASS_BASE = 24.dp
private val BORDER_WIDTH = 3.dp
