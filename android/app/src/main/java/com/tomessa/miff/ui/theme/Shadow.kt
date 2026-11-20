package com.tomessa.miff.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.pow

/**
 * Shadow token. Design values are offset-y / blur / color.
 *
 * Platform gap (accepted in the ticket, to validate by screenshot): Android draws shadows from an elevation,
 * so offset and blur cannot be reproduced exactly. [elevation] is the closest match (elevation = blur radius),
 * and [color] is applied to the ambient and spot shadows on Android 9+ (older versions use the system color).
 */
data class ShadowToken(
    val offsetY: Dp,
    val blur: Dp,
    val color: Color,
) {
    val elevation: Dp get() = blur
}

/** shadow/default: 0 / 4 / 4 / black 25 %. Buttons, cards, chips. */
val ShadowDefault = ShadowToken(offsetY = 4.dp, blur = 4.dp, color = Color.Black.copy(alpha = 0.25f))

/** shadow/subtle: 0 / 0 / 3 / black 8 %. Social sign-in buttons. */
val ShadowSubtle = ShadowToken(offsetY = 0.dp, blur = 3.dp, color = Color.Black.copy(alpha = 0.08f))

fun Modifier.miffShadow(
    token: ShadowToken,
    shape: Shape,
): Modifier =
    shadow(
        elevation = token.elevation,
        shape = shape,
        clip = false,
        ambientColor = token.color,
        spotColor = token.color,
    )

/**
 * How far, in blur units, the hand-drawn outer shadow reaches around the shape ([miffOuterShadow]); the elevation shadow of a card at
 * `shadow/default` spreads about this far.
 */
private const val OUTER_SHADOW_REACH = 1.5f

/** Share of the token's opacity kept at the edge of the shape: a stack of soft rings reads a little denser than the elevation shadow. */
private const val OUTER_SHADOW_PEAK = 0.6f

/**
 * A shadow drawn ONLY OUTSIDE the shape, for a surface that is TRANSLUCENT (the glass tab bar).
 *
 * [miffShadow] is the shadow of the platform (an elevation). On a translucent surface the platform decides by itself whether the inside of the
 * shape is cut out of the shadow, and on the OPPO Find X5 Lite (ColorOS) it is not reliably: the shadow was seen THROUGH the veil, darkening the
 * bar (a bar measured at 240-242 of luminance over a page at 249, where the white 32 % veil should give 251) everywhere but in a clear rectangle,
 * with an intensity that changed from one frame to the next. Here the inside is excluded explicitly: the canvas is clipped with
 * [ClipOp.Difference] against the shape BEFORE anything is drawn, then a few soft rings (strokes of decreasing width around the outline, moved down
 * by the offset of the token) make the fall-off. Nothing is ever drawn under the veil. Plain Compose drawing, no platform shadow layer.
 * Approximation of the elevation shadow (accepted like [ShadowToken]'s): same colour, offset and reach, simplified fall-off.
 */
fun Modifier.miffOuterShadow(
    token: ShadowToken,
    shape: Shape,
): Modifier =
    drawBehind {
        val body =
            when (val outline = shape.createOutline(size, layoutDirection, this@drawBehind)) {
                is Outline.Generic -> outline.path
                is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
                is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
            }
        val steps = ceil(token.blur.toPx() * OUTER_SHADOW_REACH).toInt().coerceAtLeast(1)
        val peak = token.color.alpha * OUTER_SHADOW_PEAK
        // Rings overlap: n rings of alpha a pile up to 1 - (1 - a)^n at the edge, so a is chosen to land on the peak.
        val ringAlpha = 1f - (1f - peak).pow(1f / steps)
        val ring = token.color.copy(alpha = ringAlpha)
        clipPath(body, ClipOp.Difference) {
            translate(top = token.offsetY.toPx()) {
                for (i in steps downTo 1) {
                    drawPath(body, ring, style = Stroke(width = 2f * i, join = StrokeJoin.Round))
                }
            }
        }
    }
