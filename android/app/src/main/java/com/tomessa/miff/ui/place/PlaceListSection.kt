package com.tomessa.miff.ui.place

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.tomessa.miff.R
import com.tomessa.miff.data.place.RequestNotice
import com.tomessa.miff.domain.place.Place
import com.tomessa.miff.domain.place.PlaceLimits
import com.tomessa.miff.domain.place.PlaceQuota
import com.tomessa.miff.domain.place.PlaceRequest
import com.tomessa.miff.domain.place.PlaceRequestLimits
import com.tomessa.miff.domain.place.PlaceType
import com.tomessa.miff.domain.place.RequesterState
import com.tomessa.miff.ui.theme.ShadowDefault
import com.tomessa.miff.ui.theme.ShapeMd
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.StrokeHairline
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorActionPrimaryTint5
import com.tomessa.miff.ui.theme.colorBgSurface
import com.tomessa.miff.ui.theme.colorBorderAction
import com.tomessa.miff.ui.theme.colorBorderDefault
import com.tomessa.miff.ui.theme.colorTextDescription
import com.tomessa.miff.ui.theme.colorTextForm
import com.tomessa.miff.ui.theme.colorTextPrimary
import com.tomessa.miff.ui.theme.miffShadow
import com.tomessa.miff.ui.theme.textBodyBold16
import com.tomessa.miff.ui.theme.textBodyRegular14

/** A request the CREATOR received, with who asked (null until the card of the member is read). */
class ReceivedRequest(
    val request: PlaceRequest,
    val requester: RequesterState?,
) {
    override fun toString(): String = "ReceivedRequest(redacted)"
}

/**
 * What the list of places of ONE Miff needs to be drawn.
 *
 * [premium] is null while it is not known yet: nothing about the free plan is claimed then (no counter, no "Miff+" pill).
 * [savedName] is the name of the place just saved, for the calm confirmation (mockup 04L).
 */
class PlaceListUi(
    val places: List<Place>,
    val isCreator: Boolean,
    val premium: Boolean? = null,
    val savedName: String? = null,
    /** A member who is not the creator: its own pending requests (ticket LIEU-REQ-B), newest first; they come BEFORE the places. */
    val requests: List<PlaceRequest> = emptyList(),
    /** "Demande envoyée…" / "Ta demande partira dès que tu seras connecté.", shown after the form of a request closed. */
    val requestNotice: RequestNotice? = null,
    /** The CREATOR: the requests received (ticket LIEU-REQ-C), oldest first; they come BEFORE the places, in cards that tell them apart from the places. */
    val received: List<ReceivedRequest> = emptyList(),
    /** The request being answered: its buttons wait. */
    val busyRequestId: String? = null,
) {
    /** A member who already has the 3 pending requests of the ticket: the button says so calmly instead of opening the form (criterion M5). */
    val atRequestLimit: Boolean
        get() = !isCreator && requests.size >= PlaceRequestLimits.PER_MEMBER

    /** A creator on the free plan who already has 3 places: "Ajouter un lieu" opens the offer instead of the form (criterion LIM1). */
    val atFreeLimit: Boolean
        get() = isCreator && premium == false && PlaceQuota.isAtFreeLimit(false, places.map { it.id }.toSet().size)

    override fun toString(): String = "PlaceListUi(redacted)"
}

/**
 * The rows of "Lieux de la Miff" (mockup 17): the category icon, the name, the address and a chevron; a danger zone carries its
 * label in words; the creator has ONE entry "Ajouter un lieu" (dashed, with the "Miff+" pill at the limit, never hidden or
 * greyed); a member only reads, and a line says who sets the places. Used by the Miff screen and by the Profile tab.
 */
@Composable
fun PlaceRows(
    ui: PlaceListUi,
    onOpen: (Place) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    /** A member's pending request is opened (its detail). */
    onOpenRequest: (PlaceRequest) -> Unit = {},
    /** "Demander l'ajout d'un lieu": a member who is not the creator opens the form of a request. */
    onRequestPlace: () -> Unit = {},
    /** The creator opens the detail of a request received, accepts it, or declines it. */
    onOpenReceived: (PlaceRequest) -> Unit = {},
    onAcceptRequest: (PlaceRequest) -> Unit = {},
    onDeclineRequest: (PlaceRequest) -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        ui.savedName?.let { SavedNotice(it) }
        if (!ui.isCreator) {
            ui.requestNotice?.let {
                Notice(
                    stringResource(if (it.queued) R.string.place_request_sent_offline else R.string.place_request_sent),
                )
            }
        }
        val caption =
            when {
                !ui.isCreator -> {
                    stringResource(R.string.place_list_member_hint)
                }

                ui.premium == false -> {
                    val count =
                        ui.places
                            .map { it.id }
                            .toSet()
                            .size
                    pluralStringResource(R.plurals.place_list_counter_free, count, count)
                }

                else -> {
                    null
                }
            }
        if (caption != null) {
            Text(text = caption, style = textBodyRegular14, color = colorTextDescription)
        }
        // The pending requests of a member come FIRST (decision of the PM, 2026-11-11): what was just asked is what is looked for.
        if (!ui.isCreator) ui.requests.forEach { PlaceRequestCard(it, onClick = { onOpenRequest(it) }) }
        // The CREATOR: the requests received, in first position, under a title, each with who asked and the two answers (criteria C2 to C4).
        if (ui.isCreator && ui.received.isNotEmpty()) {
            Text(
                text = stringResource(R.string.place_request_section_title),
                style = textBodyBold16,
                color = colorTextForm,
                modifier = Modifier.semantics { heading() },
            )
            ui.received.forEach { received ->
                PlaceRequestCard(
                    request = received.request,
                    onClick = { onOpenReceived(received.request) },
                    creator =
                        RequestCreatorUi(
                            requester = received.requester,
                            busy = ui.busyRequestId != null,
                            onAccept = { onAcceptRequest(received.request) },
                            onDecline = { onDeclineRequest(received.request) },
                        ),
                )
            }
        }
        if (ui.places.isEmpty()) {
            Text(
                // A member has nothing to add: a calm "nothing yet" (decision of the PM, 2026-11-11); the creator keeps its sentence.
                text =
                    stringResource(if (ui.isCreator) R.string.miff_places_empty else R.string.place_list_empty_member),
                style = textBodyRegular14,
                color = colorTextDescription,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.s),
            )
        } else {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .miffShadow(ShadowDefault, ShapeMd)
                        .clip(ShapeMd)
                        .background(colorBgSurface)
                        .border(StrokeHairline, colorBorderDefault, ShapeMd),
            ) {
                ui.places.forEach { place -> PlaceRow(place, onClick = { onOpen(place) }) }
            }
        }
        if (ui.isCreator) {
            AddPlaceButton(label = R.string.place_list_add_action, premiumPill = ui.atFreeLimit, onClick = onAdd)
        } else {
            // A member does not add a place: it ASKS the creator for one (same button, same design, another sentence).
            var limitShown by remember { mutableStateOf(false) }
            AddPlaceButton(
                label = R.string.place_request_cta_member,
                premiumPill = false,
                onClick = { if (ui.atRequestLimit) limitShown = true else onRequestPlace() },
            )
            if (limitShown && ui.atRequestLimit) {
                Text(
                    text = stringResource(R.string.place_request_limit_member),
                    style = textBodyRegular14,
                    color = colorTextDescription,
                    modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

@Composable
private fun PlaceRow(
    place: Place,
    onClick: () -> Unit,
) {
    val category = place.category
    val address = place.address
    val described =
        when {
            category != null && !address.isNullOrEmpty() -> {
                stringResource(R.string.place_list_row_a11y, place.name, stringResource(category.labelRes), address)
            }

            category != null -> {
                stringResource(R.string.place_list_row_noaddress_a11y, place.name, stringResource(category.labelRes))
            }

            !address.isNullOrEmpty() -> {
                stringResource(R.string.place_list_row_nocategory_a11y, place.name, address)
            }

            else -> {
                stringResource(R.string.place_list_row_nocategory_noaddress_a11y, place.name)
            }
        }
    // The row is read as ONE block: the visible tag "Zone de danger" must be in it, or a screen reader would hide the type of the place.
    val spoken =
        if (place.type == PlaceType.RISK) {
            stringResource(R.string.place_list_row_type_a11y, described, stringResource(place.type.labelRes))
        } else {
            described
        }
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
        CategoryIcon(category, size = ROW_ICON)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs / 2)) {
            Text(text = place.name, style = textBodyBold16, color = colorTextForm)
            if (place.type == PlaceType.RISK) PlaceTypeTag(PlaceType.RISK)
            if (!place.address.isNullOrEmpty()) {
                Text(text = place.address, style = textBodyRegular14, color = colorTextDescription, maxLines = 2)
            }
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron_end),
            contentDescription = null,
            tint = colorActionPrimary,
        )
    }
}

private val ROW_ICON = Spacing.xl + Spacing.m - Spacing.xs

@Composable
private fun AddPlaceButton(
    @StringRes label: Int,
    premiumPill: Boolean,
    onClick: () -> Unit,
) {
    val spoken =
        stringResource(if (premiumPill) R.string.place_list_add_premium_a11y else label)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(ShapeMd)
                .dashedBorder(colorBorderAction)
                .clickable(role = Role.Button, onClick = onClick)
                .heightIn(min = Spacing.minTouchTarget + Spacing.m)
                .padding(horizontal = Spacing.m, vertical = Spacing.s)
                .semantics(mergeDescendants = true) { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.CenterHorizontally),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_loc_plus),
            contentDescription = null,
            tint = colorActionPrimary,
            modifier = Modifier.size(Spacing.l),
        )
        Text(text = stringResource(label), style = textBodyBold16, color = colorActionPrimary)
        if (premiumPill) PremiumPill()
    }
}

/** "Le lieu « X » est enregistré. Tes proches le prendront en compte dès qu'ils ouvriront Miff, ou dans les prochaines heures." */
@Composable
private fun SavedNotice(name: String) = Notice(stringResource(R.string.place_saved_notice, name))

/** A calm confirmation in the family of the one of a saved place (a pale blue card with a check, read politely by the screen reader). */
@Composable
private fun Notice(text: String) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(ShapeMd)
                .background(colorActionPrimaryTint5)
                .border(StrokeHairline, colorBorderAction, ShapeMd)
                .padding(Spacing.m)
                .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_loc_check),
            contentDescription = null,
            tint = colorActionPrimary,
            modifier = Modifier.size(Spacing.l),
        )
        Text(
            text = text,
            style = textBodyRegular14,
            color = colorTextPrimary,
        )
    }
}

/** The confirmation lives until the person leaves the screen that shows it (mockup 04L). */
@Composable
fun ConsumeNoticeOnLeave(consume: () -> Unit) {
    DisposableEffect(Unit) { onDispose(consume) }
}

/** What a screen that shows the places of a Miff gets from outside: Miff+ or not, the confirmation, and the ways in. */
class PlacesExtras(
    /** Null while it is not known. */
    val premium: Boolean? = null,
    /** The name of the place just saved (the calm confirmation), or null. */
    val savedName: String? = null,
    val onOpenPlace: (Place) -> Unit = {},
    /** "Ajouter un lieu": always the form, which reads the plan on the server and shows the offer itself at the real limit. */
    val onAddPlace: () -> Unit = {},
)
