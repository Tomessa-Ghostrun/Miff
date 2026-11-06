package com.tomessa.miff.ui.update

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tomessa.miff.R
import com.tomessa.miff.domain.update.PromptLevel
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.colorStatusDangerBubble
import com.tomessa.miff.ui.theme.colorStatusWarningBubble
import com.tomessa.miff.ui.theme.colorTextPrimary
import com.tomessa.miff.ui.theme.colorUpdateToast
import com.tomessa.miff.ui.theme.textBodyItalic
import com.tomessa.miff.ui.theme.textBodyRegular14

/*
 * The bubble of encouragement (decision of the PM, D21, 2026-11-06; design/screens/update.md section 4): the bubble of the mockups, in the colour of the level,
 * a small tail pointing down at the central button, NO title, NO button, NO cross. It is not interactive (the gesture is the central button it points at) and
 * it swallows the touches (what is under it is never triggered by accident). No sound, no vibration, no blinking, no blocking window. The text is the one of the
 * mockups, word for word.
 *
 * Screen reader (written in the PR, a difference from the ticket): the text is plain static text, NOT a live region. The ticket (5.5, A1) asks for one
 * announcement when it appears; the design file (4.4) asks for none, because a screen reader that speaks by itself about "passing to red" is the sound
 * equivalent of the alert the PM dropped (D17). The design file is followed: the text stays reachable, and the next element is the central button, whose
 * spoken name says the state. One line to change if the PM prefers the announcement.
 */

/** The tail, and the air between its tip and the bar: the tip stops 4 dp above the bar, never touches it (design 4.2). */
private val TailWidth = 22.dp
private val TailHeight = 11.dp
private val TailGap = 4.dp
private val BubbleShape = RoundedCornerShape(20.dp)

@Composable
fun IncentiveBubble(
    level: PromptLevel,
    modifier: Modifier = Modifier,
) {
    if (level == PromptLevel.NONE) return
    val (color, text) =
        when (level) {
            PromptLevel.ORANGE -> colorStatusWarningBubble to stringResource(R.string.update_bubble_orange)
            else -> colorStatusDangerBubble to stringResource(R.string.update_bubble_red)
        }
    Column(
        // Absorbs the taps: a tap on the bubble never reaches a card or a button under it.
        modifier = modifier.fillMaxWidth().pointerInput(Unit) { detectTapGestures {} },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(BubbleShape)
                    .background(color)
                    .padding(horizontal = 18.dp, vertical = Spacing.m),
        ) {
            Text(text = text, style = textBodyItalic, color = colorTextPrimary)
        }
        // The tail points at the axis of the screen, where the central button is (the three entries of the bar are symmetric, in both directions of writing).
        Canvas(Modifier.size(TailWidth, TailHeight)) {
            val tail =
                Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width / 2f, size.height)
                    close()
                }
            drawPath(tail, color)
        }
        Box(Modifier.height(TailGap))
    }
}

/** The calm message after a publication: a dark pill, no sound, no vibration, announced politely to a screen reader. It goes away by itself. */
@Composable
fun UpdateConfirmation(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = textBodyRegular14,
            color = Color.White,
            modifier =
                Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(colorUpdateToast)
                    .padding(horizontal = Spacing.m, vertical = Spacing.s + Spacing.xs)
                    .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}
