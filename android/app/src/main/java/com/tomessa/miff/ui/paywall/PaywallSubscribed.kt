package com.tomessa.miff.ui.paywall

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tomessa.miff.R
import com.tomessa.miff.ui.theme.ShapeMd
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.StrokeHairline
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorActionPrimaryTint20
import com.tomessa.miff.ui.theme.colorBgSurface
import com.tomessa.miff.ui.theme.colorBorderDefault
import com.tomessa.miff.ui.theme.colorBrandPrimary
import com.tomessa.miff.ui.theme.colorStatusDanger
import com.tomessa.miff.ui.theme.colorStatusSafeTint
import com.tomessa.miff.ui.theme.colorStatusWarning
import com.tomessa.miff.ui.theme.colorTextDescription
import com.tomessa.miff.ui.theme.colorTextPrimary
import com.tomessa.miff.ui.theme.textBodyBold16
import com.tomessa.miff.ui.theme.textBodyRegular
import com.tomessa.miff.ui.theme.textTitleBold24Gradient
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * The confirmation after a subscription (decision of the PM, 2026-11-25: "Bienvenue sur Miff+ !", warmer and less empty than "Tu as Miff+. Merci !"): the Miff logo with a "+" on a soft
 * halo, the welcome, one line, and what is now unlocked (the two limits of the free plan: places and Miffs). Not designed in Figma: list of the differences in the pull request.
 * The burst of confetti is drawn over the whole screen by [ConfettiBurst] (see PaywallScreen), not here.
 */
@Composable
internal fun SubscribedContent() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Spacer(Modifier.size(Spacing.l))
        // The logo of Miff on a halo of the brand colour, with a "+" badge (the name of the offer), decorative for a screen reader (the title says it).
        Box(modifier = Modifier.size(HALO_SIZE), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxSize().clip(CircleShape).background(colorStatusSafeTint))
            Image(
                painter = painterResource(R.drawable.ic_miff_logo_avatar),
                contentDescription = null,
                modifier = Modifier.size(LOGO_SIZE).clip(CircleShape),
            )
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = Spacing.s, bottom = Spacing.s)
                        .size(Spacing.xl)
                        .clip(CircleShape)
                        .background(colorBrandPrimary)
                        .border(StrokeHairline, colorTextPrimary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_loc_plus),
                    contentDescription = null,
                    tint = colorTextPrimary,
                    modifier = Modifier.size(Spacing.m),
                )
            }
        }
        Text(
            text = stringResource(R.string.paywall_subscribed),
            style = textTitleBold24Gradient,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().semantics { heading() },
        )
        Text(
            text = stringResource(R.string.paywall_subscribed_detail),
            style = textBodyRegular,
            color = colorTextDescription,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(ShapeMd)
                    .background(colorBgSurface)
                    .border(StrokeHairline, colorBorderDefault, ShapeMd)
                    .padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Unlocked(R.string.place_paywall_feature_places)
            Unlocked(R.string.place_paywall_feature_miffs)
        }
    }
}

/** One thing that is now unlocked: a tick and its name (the same words as the cards of the offer). */
@Composable
private fun Unlocked(title: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(Spacing.xl).clip(CircleShape).background(colorActionPrimaryTint20),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_loc_check),
                contentDescription = null,
                tint = colorActionPrimary,
                modifier = Modifier.size(Spacing.m),
            )
        }
        Text(text = stringResource(title), style = textBodyBold16, color = colorTextPrimary)
    }
}

/**
 * A short burst of confetti over the whole screen, drawn with a canvas (no library, no image): [Confetti.COUNT] small pieces fall for about three seconds, once per display (a rotation does
 * not play it again). It never takes a touch and says nothing to a screen reader. The caller does not call it at all when the animations are turned off ([PaywallScreen]'s `reduceMotion`:
 * then there is no confetti, only the confirmation).
 */
@Composable
internal fun ConfettiBurst() {
    var played by rememberSaveable { mutableStateOf(false) }
    if (played) return
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(Confetti.DURATION_MILLIS, easing = LinearEasing))
        played = true
    }
    val pieces = remember { Confetti.pieces() }
    val colors =
        listOf(colorBrandPrimary, colorActionPrimary, colorStatusWarning, colorStatusDanger, colorActionPrimaryTint20)
    Canvas(modifier = Modifier.fillMaxSize().testTag(CONFETTI_TAG)) {
        val value = progress.value
        pieces.forEach { piece ->
            val y = Confetti.yAt(piece, value) ?: return@forEach
            val alpha = Confetti.alphaAt(piece, value)
            val cx = Confetti.xAt(piece, value) * size.width
            val cy = y * size.height
            val width = piece.sizeDp.dp.toPx()
            val height = if (piece.round) width else width * 0.5f
            val color = colors[piece.colorIndex % colors.size]
            if (piece.round) {
                drawCircle(color = color, radius = width / 2f, center = Offset(cx, cy), alpha = alpha)
            } else {
                rotate(degrees = piece.spin * value * 360f, pivot = Offset(cx, cy)) {
                    drawRect(
                        color = color,
                        topLeft = Offset(cx - width / 2f, cy - height / 2f),
                        size = Size(width, height),
                        alpha = alpha,
                    )
                }
            }
        }
    }
}

/** One piece of confetti, with everything fixed at the start (positions are fractions of the width and of the height of the screen). */
internal data class ConfettiPiece(
    val startX: Float,
    /** The share of the animation that passes before this piece starts to fall. */
    val delay: Float,
    /** 1 = reaches the bottom of the screen exactly at the end; more = falls faster. */
    val speed: Float,
    val drift: Float,
    val phase: Float,
    val sizeDp: Float,
    /** Turns made over the whole animation (negative = the other way). */
    val spin: Float,
    val colorIndex: Int,
    val round: Boolean,
)

/** The maths of the burst, apart from the drawing so that it can be tested (no clock, no random of its own: [pieces] is given a seed). */
internal object Confetti {
    const val COUNT = 56
    const val DURATION_MILLIS = 3200

    fun pieces(
        seed: Long = 7L,
        count: Int = COUNT,
    ): List<ConfettiPiece> {
        val random = Random(seed)
        return List(count) {
            ConfettiPiece(
                startX = random.nextFloat(),
                delay = random.nextFloat() * 0.3f,
                speed = 1f + random.nextFloat() * 0.35f,
                drift = (random.nextFloat() - 0.5f) * 0.2f,
                phase = random.nextFloat() * 2f * PI.toFloat(),
                sizeDp = 6f + random.nextFloat() * 6f,
                spin = (random.nextFloat() - 0.5f) * 6f,
                colorIndex = random.nextInt(0, 5),
                round = random.nextInt(0, 4) == 0,
            )
        }
    }

    /** What share of its fall the piece has made at [progress] (0 to 1 of the animation); null until it starts. */
    private fun fall(
        piece: ConfettiPiece,
        progress: Float,
    ): Float? {
        if (progress <= piece.delay) return null
        return ((progress - piece.delay) / (1f - piece.delay)).coerceIn(0f, 1f)
    }

    /** The vertical position, as a fraction of the height of the screen (starts above it, ends below it); null while the piece has not started. */
    fun yAt(
        piece: ConfettiPiece,
        progress: Float,
    ): Float? = fall(piece, progress)?.let { START_Y + it * piece.speed * (1f - START_Y + MARGIN) }

    /** The horizontal position, as a fraction of the width: a slow drift and a small sway. */
    fun xAt(
        piece: ConfettiPiece,
        progress: Float,
    ): Float {
        val p = fall(piece, progress) ?: 0f
        return piece.startX + piece.drift * p + SWAY * sin(p * 2f * PI.toFloat() * 2f + piece.phase)
    }

    /** Fully visible, then it fades during the last part of its fall. */
    fun alphaAt(
        piece: ConfettiPiece,
        progress: Float,
    ): Float {
        val p = fall(piece, progress) ?: return 0f
        return if (p < FADE_FROM) 1f else ((1f - p) / (1f - FADE_FROM)).coerceIn(0f, 1f)
    }

    private const val START_Y = -0.05f
    private const val MARGIN = 0.1f
    private const val SWAY = 0.02f
    private const val FADE_FROM = 0.85f
}

internal const val CONFETTI_TAG = "paywall_confetti"

private val HALO_SIZE = 144.dp
private val LOGO_SIZE = 96.dp
