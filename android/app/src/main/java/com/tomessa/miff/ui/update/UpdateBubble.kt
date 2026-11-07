package com.tomessa.miff.ui.update

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
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
 * Screen reader (the ticket decides, 5.5 and A1, confirmed by the PM): the text is a POLITE live region: announced once when the bubble appears and again when it
 * goes from orange to red, never in a loop; the next element is the central button, whose spoken name says the state. Height: at most 40 % of the screen, the text
 * scrolls inside the bubble beyond that (design 4.3, case E8).
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
    /** The most the body of the bubble may take (40 % of the screen, design 4.3): above it, the text scrolls INSIDE the bubble and never hides the Feed. */
    maxBodyHeight: Dp = Dp.Unspecified,
) {
    if (level == PromptLevel.NONE) return
    val (color, text) =
        when (level) {
            PromptLevel.ORANGE -> colorStatusWarningBubble to stringResource(R.string.update_bubble_orange)
            else -> colorStatusDangerBubble to stringResource(R.string.update_bubble_red)
        }
    Column(
        // Absorbs the taps: a tap on the bubble never reaches a card or a button under it.
        modifier = modifier.fillMaxWidth().testTag(BUBBLE_TAG).pointerInput(Unit) { detectTapGestures {} },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxBodyHeight)
                    .clip(BubbleShape)
                    .background(color)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = Spacing.m),
        ) {
            // Announced politely to a screen reader, once when it appears and again when it goes from orange to red (the text changes): no sound, no vibration.
            Text(
                text = text,
                style = textBodyItalic,
                color = colorTextPrimary,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
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
    Box(modifier = modifier.testTag(CONFIRMATION_TAG), contentAlignment = Alignment.Center) {
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

const val BUBBLE_TAG = "update_bubble"
const val CONFIRMATION_TAG = "update_confirmation"

/**
 * What the Update draws just above the tab bar, in ONE column so that the two can never overlap: the calm message after a publication ABOVE, the bubble of
 * encouragement BELOW it, whose bottom edge is at the top of the bar ([barInset] = the height of the bar as the host measures it, `LocalTabBarInset`; the bubble
 * brings its own 4 dp of air under its tail). Without a bubble the message sits [Spacing.m] above the bar. [onBubbleHeight] gives the measured height of the bubble
 * (0 when there is none), so that the tabs keep a margin as tall as it.
 */
@Composable
fun UpdateBottomLayer(
    barInset: Dp,
    prompt: PromptLevel,
    confirmation: String?,
    maxBodyHeight: Dp,
    onBubbleHeight: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bubbleShown = prompt != PromptLevel.NONE
    Column(
        modifier = modifier.padding(bottom = barInset),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        confirmation?.let { text ->
            UpdateConfirmation(
                text = text,
                modifier = Modifier.padding(bottom = if (bubbleShown) Spacing.s else Spacing.m),
            )
        }
        if (bubbleShown) {
            IncentiveBubble(
                level = prompt,
                maxBodyHeight = maxBodyHeight,
                modifier =
                    Modifier
                        .padding(
                            start = Spacing.m,
                            end = Spacing.m,
                        ).onSizeChanged { onBubbleHeight(it.height) },
            )
        }
    }
}
