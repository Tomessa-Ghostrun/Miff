package com.tomessa.miff.ui.place

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.tomessa.miff.R
import com.tomessa.miff.domain.place.PlaceRequest
import com.tomessa.miff.domain.place.PlaceType
import com.tomessa.miff.ui.theme.ShapeFull
import com.tomessa.miff.ui.theme.ShapeMd
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorActionPrimaryTint10
import com.tomessa.miff.ui.theme.colorActionPrimaryTint5
import com.tomessa.miff.ui.theme.colorBorderAction
import com.tomessa.miff.ui.theme.colorTextDescription
import com.tomessa.miff.ui.theme.colorTextForm
import com.tomessa.miff.ui.theme.textBodyBold16
import com.tomessa.miff.ui.theme.textBodyRegular14
import com.tomessa.miff.ui.theme.textCaptionBold12

/*
 * SECURITY, for this file: the name and the address of a request are drawn here and nowhere else; they are never logged and never read out in anything but the
 * description of the card for the screen reader.
 */

/**
 * A pending request in the list of places (ticket LIEU-REQ, criteria M4 and C2). It is NOT a place: a faded background and a DASHED outline tell it apart from the
 * rows of the places that are in the Miff (no solid card, no category emoji), and a pill says "En attente" in words. NEVER red or orange (calm tone, T1).
 *
 * The screen reader reads ONE block: "Ta demande en attente : {lieu}" (the member's own request).
 */
@Composable
fun PlaceRequestCard(
    request: PlaceRequest,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spoken = stringResource(R.string.place_request_a11y_pending_mine, request.name)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(ShapeMd)
                .background(colorActionPrimaryTint5)
                .dashedBorder(colorBorderAction)
                .clickable(role = Role.Button, onClick = onClick)
                .heightIn(min = Spacing.minTouchTarget + Spacing.l)
                .padding(horizontal = Spacing.m, vertical = Spacing.s)
                .semantics(mergeDescendants = true) { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        // The category the person chose (the pin when a document has none); the dashed outline, not a solid tile, says it is not a place of the Miff yet.
        CategoryIcon(request.category, size = REQUEST_ICON)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs / 2)) {
            Text(text = request.name, style = textBodyBold16, color = colorTextForm)
            if (request.type == PlaceType.RISK) PlaceTypeTag(PlaceType.RISK)
            if (!request.address.isNullOrEmpty()) {
                Text(
                    text = request.address,
                    style = textBodyRegular14,
                    color = colorTextDescription,
                    maxLines = 2,
                )
            }
            PendingPill(stringResource(R.string.place_request_badge_pending))
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron_end),
            contentDescription = null,
            tint = colorActionPrimary,
        )
    }
}

private val REQUEST_ICON = Spacing.xl + Spacing.m - Spacing.xs

/** The pill "En attente": words on a pale blue, the same family as the other pills of the places (never a warning colour). */
@Composable
fun PendingPill(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = textCaptionBold12,
        color = colorActionPrimary,
        modifier =
            modifier
                .clip(ShapeFull)
                .background(colorActionPrimaryTint10)
                .padding(horizontal = Spacing.s, vertical = Spacing.xs / 2),
    )
}

/** What the detail of a request needs besides the place it describes (ticket LIEU-REQ, section 4.2). */
class RequestDetailUi(
    /** A wait is under way (a withdrawal): the buttons wait. */
    val busy: Boolean = false,
    val onWithdraw: () -> Unit = {},
) {
    override fun toString(): String = "RequestDetailUi(redacted)"
}
