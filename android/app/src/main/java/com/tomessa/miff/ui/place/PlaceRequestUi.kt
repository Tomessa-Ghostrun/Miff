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
import androidx.compose.ui.unit.dp
import com.tomessa.miff.R
import com.tomessa.miff.domain.place.PlaceRequest
import com.tomessa.miff.domain.place.PlaceType
import com.tomessa.miff.domain.place.RequesterState
import com.tomessa.miff.ui.components.MiffButton
import com.tomessa.miff.ui.components.MiffSecondaryButton
import com.tomessa.miff.ui.profile.ProfileAvatar
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
 * SECURITY, for this file: the name and the address of a request, and the first name and photo of the person who asked, are drawn here and nowhere else; they
 * are never logged and never read out in anything but the description of the card for the screen reader.
 */

/** What the card of a request needs when the person who looks at it is the CREATOR of the Miff (ticket LIEU-REQ-C, criteria C2 to C4). */
class RequestCreatorUi(
    /** Who asked: null (not read yet) and unknown are both drawn without a name; never hidden on a doubt. */
    val requester: RequesterState?,
    /** An answer is under way (the buttons wait, a second tap does nothing). */
    val busy: Boolean,
    val onAccept: () -> Unit,
    val onDecline: () -> Unit,
) {
    override fun toString(): String = "RequestCreatorUi(redacted)"
}

/**
 * A pending request in the list of places (ticket LIEU-REQ, criteria M4 and C2). It is NOT a place: a faded background and a DASHED outline tell it apart from the
 * rows of the places that are in the Miff (no solid card, no category emoji), and a pill says "En attente" in words. NEVER red or orange (calm tone, T1).
 *
 * The REQUESTER (no [creator]) reads ONE block: "Ta demande en attente : {lieu}". The CREATOR ([creator] given) sees who asked, with the photo or the initials, and
 * the two answers "Accepter" and "Refuser" (the same weight: refusing is as calm as accepting); the card reads "Demande en attente : {lieu}, demandée par {Prénom}".
 */
@Composable
fun PlaceRequestCard(
    request: PlaceRequest,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    creator: RequestCreatorUi? = null,
) {
    val name = (creator?.requester as? RequesterState.Known)?.card?.firstName.orEmpty()
    // The card is read like the rows of the places: the name (and who asked, for the creator), then the address, then "Zone de danger" when it is one.
    val named =
        when {
            creator == null -> stringResource(R.string.place_request_a11y_pending_mine, request.name)
            name.isNotEmpty() -> stringResource(R.string.place_request_a11y_pending_card, request.name, name)
            else -> stringResource(R.string.place_request_a11y_pending_card_unknown, request.name)
        }
    val withAddress =
        if (request.address.isNullOrEmpty()) {
            named
        } else {
            stringResource(
                R.string.place_list_row_type_a11y,
                named,
                request.address,
            )
        }
    val spoken =
        if (request.type == PlaceType.RISK) {
            stringResource(R.string.place_list_row_type_a11y, withAddress, stringResource(request.type.labelRes))
        } else {
            withAddress
        }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(ShapeMd)
                .background(colorActionPrimaryTint5)
                .dashedBorder(colorBorderAction),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
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
                if (creator != null) {
                    RequesterLine(creator.requester)
                } else {
                    PendingPill(stringResource(R.string.place_request_badge_pending))
                }
            }
            Icon(
                painter = painterResource(R.drawable.ic_chevron_end),
                contentDescription = null,
                tint = colorActionPrimary,
            )
        }
        if (creator != null) {
            AnswerButtons(
                requestName = request.name,
                busy = creator.busy,
                onAccept = creator.onAccept,
                onDecline = creator.onDecline,
                modifier = Modifier.padding(start = Spacing.m, end = Spacing.m, bottom = Spacing.m),
            )
        }
    }
}

private val REQUEST_ICON = Spacing.xl + Spacing.m - Spacing.xs

/** "Demandé par Léa" with the photo (or the initials) of the person: the creator sees WHO asks, in the list and in the detail (criterion C2). */
@Composable
fun RequesterLine(
    requester: RequesterState?,
    modifier: Modifier = Modifier,
) {
    val known = (requester as? RequesterState.Known)?.card
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        ProfileAvatar(jpeg = known?.avatarJpeg, initials = known?.initials ?: "?", size = REQUESTER_AVATAR)
        Text(
            text =
                if (known != null) {
                    stringResource(R.string.place_request_by, known.firstName)
                } else {
                    stringResource(R.string.place_request_by_unknown)
                },
            style = textBodyRegular14,
            color = colorTextDescription,
        )
    }
}

private val REQUESTER_AVATAR = 28.dp

/** "Refuser" and "Accepter": the same size, side by side; "Refuser" is never red (the person who asked is told nothing). Every button names the place for the screen reader. */
@Composable
fun AnswerButtons(
    requestName: String,
    busy: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val declineLabel = stringResource(R.string.place_request_decline_a11y, requestName)
    val acceptLabel = stringResource(R.string.place_request_accept_a11y, requestName)
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        MiffSecondaryButton(
            text = stringResource(R.string.place_request_decline),
            onClick = onDecline,
            enabled = !busy,
            modifier = Modifier.weight(1f).semantics { contentDescription = declineLabel },
        )
        MiffButton(
            text = stringResource(R.string.place_request_accept),
            onClick = onAccept,
            enabled = !busy,
            modifier = Modifier.weight(1f).semantics { contentDescription = acceptLabel },
        )
    }
}

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
    /** A wait is under way (an answer): the buttons wait. */
    val busy: Boolean = false,
    /** The requester: "Retirer ma demande". Null for the creator. */
    val onWithdraw: (() -> Unit)? = null,
    /** The creator: who asked, when, and the two answers. Null for the requester. */
    val creator: RequestCreatorDetail? = null,
    /** The name of the place asked for: the buttons name it for the screen reader. */
    val placeName: String = "",
) {
    override fun toString(): String = "RequestDetailUi(redacted)"
}

/** The creator's part of the detail of a request. */
class RequestCreatorDetail(
    val requester: RequesterState?,
    /** "Le 12 nov.", or null when the request is not dated yet. */
    val dateText: String?,
    val onAccept: () -> Unit,
    val onDecline: () -> Unit,
) {
    override fun toString(): String = "RequestCreatorDetail(redacted)"
}
