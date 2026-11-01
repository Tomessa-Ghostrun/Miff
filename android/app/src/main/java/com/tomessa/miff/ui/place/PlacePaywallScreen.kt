package com.tomessa.miff.ui.place

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.tomessa.miff.R
import com.tomessa.miff.domain.place.PaywallReason
import com.tomessa.miff.ui.components.MiffButton
import com.tomessa.miff.ui.components.miffScreenInsets
import com.tomessa.miff.ui.theme.ShadowDefault
import com.tomessa.miff.ui.theme.ShapeMd
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.StrokeAccent
import com.tomessa.miff.ui.theme.StrokeHairline
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorActionPrimaryTint20
import com.tomessa.miff.ui.theme.colorBgCanvas
import com.tomessa.miff.ui.theme.colorBgGradientEnd
import com.tomessa.miff.ui.theme.colorBgPlaceholder
import com.tomessa.miff.ui.theme.colorBgSurface
import com.tomessa.miff.ui.theme.colorBorderAction
import com.tomessa.miff.ui.theme.colorBorderDefault
import com.tomessa.miff.ui.theme.colorBorderStrong
import com.tomessa.miff.ui.theme.colorStatusDangerTint
import com.tomessa.miff.ui.theme.colorTextDescription
import com.tomessa.miff.ui.theme.colorTextError
import com.tomessa.miff.ui.theme.colorTextPrimary
import com.tomessa.miff.ui.theme.miffShadow
import com.tomessa.miff.ui.theme.textBodyBold16
import com.tomessa.miff.ui.theme.textBodyRegular
import com.tomessa.miff.ui.theme.textBodyRegular14
import com.tomessa.miff.ui.theme.textTitleBold20
import com.tomessa.miff.ui.theme.textTitleBold24Gradient

/**
 * The offer shown when a free account asks for a 3rd place or for a danger zone (design 14 and 15): the screen of the offer with a
 * calm sentence at the top that says WHY it appears. PROVISIONAL (decision QL15): no purchase exists yet, so the button of the
 * trial and the prices are not drawn (a price is never written by hand in the app, and a button that does nothing would be a
 * lie); "Miff+ arrive bientôt" says where things stand. Nothing is written by this screen: leaving it changes nothing.
 *
 * "Notifications intelligentes" is NOT listed: no notification is sent yet (risk R13: never promise what does not exist).
 */
@Composable
fun PlacePaywallScreen(
    reason: PaywallReason,
    onClose: () -> Unit,
) {
    val title = stringResource(R.string.place_paywall_soon)
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(colorBgCanvas, colorBgGradientEnd)))
                .miffScreenInsets()
                .semantics { paneTitle = title },
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s)) {
            IconButton(
                onClick = onClose,
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .size(Spacing.minTouchTarget)
                        .clip(CircleShape)
                        .background(colorBgPlaceholder.copy(alpha = 0.7f)),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_loc_close),
                    contentDescription = stringResource(R.string.place_paywall_close_a11y),
                    tint = colorBgSurface,
                )
            }
            Image(
                painter = painterResource(R.drawable.miff_wordmark_color),
                contentDescription = null,
                modifier = Modifier.align(Alignment.Center).width(Spacing.logoWidth / 2),
            )
        }
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.m, vertical = Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            ContextCard(reason)
            Text(
                text = stringResource(R.string.place_paywall_headline),
                style = textTitleBold24Gradient,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().semantics { heading() },
            )
            Feature(
                R.drawable.ic_loc_pin,
                R.string.place_paywall_feature_places,
                R.string.place_paywall_feature_places_desc,
                highlighted = reason == PaywallReason.LIMIT,
            )
            Feature(
                R.drawable.ic_group,
                R.string.place_paywall_feature_miffs,
                R.string.place_paywall_feature_miffs_desc,
                highlighted = false,
            )
            Feature(
                R.drawable.ic_loc_warning,
                R.string.place_paywall_feature_danger,
                R.string.place_paywall_feature_danger_desc,
                highlighted = reason == PaywallReason.DANGER,
            )
            Spacer(Modifier.size(Spacing.s))
            Text(
                text = stringResource(R.string.place_paywall_soon),
                style = textTitleBold20,
                color = colorTextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.place_paywall_soon_body),
                style = textBodyRegular,
                color = colorTextDescription,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        MiffButton(
            text = stringResource(R.string.place_paywall_later),
            onClick = onClose,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s),
        )
    }
}

/** The calm box at the top: why the offer shows (design 14 and 15). */
@Composable
private fun ContextCard(reason: PaywallReason) {
    val limit = reason == PaywallReason.LIMIT
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .miffShadow(ShadowDefault, ShapeMd)
                .clip(ShapeMd)
                .background(colorBgSurface)
                .border(StrokeAccent, colorBorderAction, ShapeMd)
                .padding(Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (limit) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Dot(filled = true)
                Dot(filled = true)
                Dot(filled = false)
            }
        } else {
            Box(
                modifier = Modifier.size(Spacing.xl + Spacing.xs).clip(CircleShape).background(colorStatusDangerTint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_loc_warning),
                    contentDescription = null,
                    tint = colorTextError,
                    modifier = Modifier.size(Spacing.l),
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text =
                    stringResource(
                        if (limit) R.string.place_paywall_limit_title else R.string.place_paywall_danger_title,
                    ),
                style = textBodyBold16,
                color = colorTextPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text =
                    stringResource(
                        if (limit) R.string.place_paywall_limit_body else R.string.place_paywall_danger_body,
                    ),
                style = textBodyRegular,
                color = colorTextDescription,
            )
        }
    }
}

/** One of the three pastilles of the limit: full for a place that exists, dashed for the one that is missing. */
@Composable
private fun Dot(filled: Boolean) {
    Box(
        modifier =
            Modifier
                .size(Spacing.l + Spacing.s)
                .clip(CircleShape)
                .then(
                    if (filled) {
                        Modifier.background(
                            colorActionPrimary,
                        )
                    } else {
                        Modifier.border(StrokeHairline, colorBorderStrong, CircleShape)
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(if (filled) R.drawable.ic_loc_pin else R.drawable.ic_loc_plus),
            contentDescription = null,
            tint = if (filled) colorBgSurface else colorTextDescription,
            modifier = Modifier.size(Spacing.m),
        )
    }
}

@Composable
private fun Feature(
    icon: Int,
    title: Int,
    description: Int,
    highlighted: Boolean,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(ShapeMd)
                .background(colorBgSurface)
                .border(
                    if (highlighted) StrokeAccent else StrokeHairline,
                    if (highlighted) colorBorderAction else colorBorderDefault,
                    ShapeMd,
                ).padding(Spacing.m)
                .heightIn(min = Spacing.minTouchTarget)
                .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(Spacing.xl + Spacing.s).clip(ShapeMd).background(colorActionPrimaryTint20),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = colorActionPrimary,
                modifier = Modifier.size(Spacing.l),
            )
        }
        Column {
            Text(text = stringResource(title), style = textBodyBold16, color = colorTextPrimary)
            Text(text = stringResource(description), style = textBodyRegular14, color = colorTextDescription)
        }
    }
}
