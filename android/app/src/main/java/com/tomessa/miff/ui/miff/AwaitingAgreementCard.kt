package com.tomessa.miff.ui.miff

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.tomessa.miff.R
import com.tomessa.miff.ui.components.MiffSecondaryButton
import com.tomessa.miff.ui.theme.ShapeMd
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.StrokeAccent
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorActionPrimaryTint10
import com.tomessa.miff.ui.theme.colorBgSurface
import com.tomessa.miff.ui.theme.colorBorderAction
import com.tomessa.miff.ui.theme.colorTextDescription
import com.tomessa.miff.ui.theme.colorTextPrimary
import com.tomessa.miff.ui.theme.textBodyBold16
import com.tomessa.miff.ui.theme.textBodyRegular14

/*
 * Design screen 23 (A and B), LOC-2b. Nothing here reads or writes anything: the card only says what is true (nothing is shared, nothing is
 * detected, for that Miff) and offers the one way back to the agreement.
 */

/**
 * What a screen needs to say that a Miff waits for the agreement of the person: the identifiers of the Miffs that wait and the way to see the
 * renewal screen again. The default says nothing waits.
 */
class AgreementUi(
    val waiting: Set<String> = emptySet(),
    val onReview: () -> Unit = {},
) {
    fun isWaiting(miffId: String?): Boolean = miffId != null && miffId in waiting
}

/**
 * "En attente de ton accord": a pause icon + words (never the colour alone), the sentence that tells the truth without putting it on the
 * person, and one secondary button "Revoir et accepter". No close button: it goes with the agreement or with the departure from the Miff.
 */
@Composable
fun AwaitingAgreementCard(
    body: String,
    onReview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(ShapeMd)
                .background(colorBgSurface)
                .border(StrokeAccent, colorBorderAction, ShapeMd)
                .padding(Spacing.m)
                .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Icon(
                painter = painterResource(R.drawable.ic_pause),
                contentDescription = null,
                tint = colorActionPrimary,
                modifier =
                    Modifier
                        .size(Spacing.xl)
                        .clip(ShapeMd)
                        .background(colorActionPrimaryTint10)
                        .padding(Spacing.xs),
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = stringResource(R.string.miff_waiting_title),
                    style = textBodyBold16,
                    color = colorTextPrimary,
                    modifier = Modifier.semantics { heading() },
                )
                Text(text = body, style = textBodyRegular14, color = colorTextDescription)
            }
        }
        MiffSecondaryButton(
            text = stringResource(R.string.miff_waiting_review),
            onClick = onReview,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
