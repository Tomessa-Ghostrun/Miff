package com.tomessa.miff.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tomessa.miff.R
import com.tomessa.miff.domain.feed.LocateResult
import com.tomessa.miff.domain.feed.PositionFix
import com.tomessa.miff.ui.components.MiffButton
import com.tomessa.miff.ui.components.MiffSecondaryButton
import com.tomessa.miff.ui.components.miffBottomInsets
import com.tomessa.miff.ui.theme.ShapeMd
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorActionPrimaryTint10
import com.tomessa.miff.ui.theme.colorActionPrimaryTint5
import com.tomessa.miff.ui.theme.colorBgSurface
import com.tomessa.miff.ui.theme.colorTextDescription
import com.tomessa.miff.ui.theme.colorTextPrimary
import com.tomessa.miff.ui.theme.textBodyRegular
import com.tomessa.miff.ui.theme.textBodyRegular14
import com.tomessa.miff.ui.theme.textDisplayTitle

/*
 * The sheet "Dernière position connue de {Prénom}" (maquette 08, ticket 5.6). It shows the DATE of the measure first, says that this is not a
 * live position, and hands the COORDINATES ALONE to the maps of the system: no map is drawn here, the Feed keeps nothing of it once the sheet is closed. Five answers, all
 * calm (never the word "erreur", never "quota"): recent, old (the date only, no coordinate, no button), not found, refused (one message for
 * every refusal: nobody is told who stopped sharing), offline. The coordinates live in [result] only while the sheet is open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocateSheet(
    firstName: String,
    result: LocateResult,
    texts: FeedTexts,
    onOpenMap: (PositionFix) -> Boolean,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // "Cette action n'est pas disponible sur cet appareil." when no application of maps answers (the sheet stays open).
    var mapUnavailable by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = colorBgSurface) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .miffBottomInsets()
                    .padding(horizontal = Spacing.l)
                    .padding(bottom = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            when (result) {
                is LocateResult.Recent -> {
                    SheetTitle(stringResource(R.string.feed_locate_title, firstName))
                    Text(
                        text =
                            stringResource(
                                R.string.feed_locate_recent,
                                texts.at(result.measuredAt),
                                texts.ago(result.age),
                            ),
                        style = textBodyRegular,
                        color = colorTextPrimary,
                    )
                    InfoLine(R.drawable.ic_visibility, stringResource(R.string.feed_locate_trust, firstName))
                    if (mapUnavailable) {
                        InfoLine(
                            R.drawable.ic_loc_info,
                            stringResource(R.string.feed_action_unavailable),
                        )
                    }
                    MiffButton(
                        text = stringResource(R.string.feed_locate_open),
                        onClick = { mapUnavailable = !onOpenMap(result.fix) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    MiffSecondaryButton(
                        text = stringResource(R.string.feed_locate_close),
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                is LocateResult.Old -> {
                    SheetTitle(stringResource(R.string.feed_locate_title, firstName))
                    Message(
                        R.drawable.ic_feed_clock,
                        stringResource(R.string.feed_locate_old, texts.from(result.measuredAt)),
                    )
                    CloseButton(onDismiss)
                }

                LocateResult.NotFound -> {
                    Message(R.drawable.ic_feed_pin_off, stringResource(R.string.feed_locate_not_found, firstName))
                    CloseButton(onDismiss)
                }

                LocateResult.Denied -> {
                    Message(R.drawable.ic_lock, stringResource(R.string.feed_locate_denied, firstName))
                    CloseButton(onDismiss)
                }

                LocateResult.Offline -> {
                    Message(R.drawable.ic_loc_cloud_off, stringResource(R.string.feed_locate_offline))
                    CloseButton(onDismiss)
                }
            }
        }
    }
}

@Composable
private fun SheetTitle(text: String) {
    Text(
        text = text,
        style = textDisplayTitle,
        color = colorTextPrimary,
        modifier = Modifier.semantics { heading() },
    )
}

/** An icon in a pale disc and a sentence: the way a calm answer is told. */
@Composable
private fun Message(
    icon: Int,
    text: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = colorActionPrimary,
            modifier =
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(colorActionPrimaryTint10)
                    .padding(10.dp),
        )
        Text(text = text, style = textBodyRegular, color = colorTextPrimary, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun InfoLine(
    icon: Int,
    text: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(ShapeMd)
                .background(colorActionPrimaryTint5)
                .padding(Spacing.s + Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = colorActionPrimary,
            modifier = Modifier.size(18.dp),
        )
        Text(text = text, style = textBodyRegular14, color = colorTextDescription, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun CloseButton(onDismiss: () -> Unit) {
    MiffSecondaryButton(
        text = stringResource(R.string.feed_locate_close),
        onClick = onDismiss,
        modifier = Modifier.fillMaxWidth(),
    )
}
