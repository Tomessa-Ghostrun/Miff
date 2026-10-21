package com.tomessa.miff.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
