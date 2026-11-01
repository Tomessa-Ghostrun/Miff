package com.tomessa.miff.ui.place

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.tomessa.miff.R
import com.tomessa.miff.domain.place.Place
import com.tomessa.miff.domain.place.PlaceLimits
import com.tomessa.miff.domain.place.PlaceQuota
import com.tomessa.miff.domain.place.PlaceType
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
) {
    /** A creator on the free plan who already has 2 places: "Ajouter un lieu" opens the offer instead of the form (criterion LIM1). */
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
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        ui.savedName?.let { SavedNotice(it) }
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
        if (ui.places.isEmpty()) {
            Text(
                text = stringResource(R.string.miff_places_empty),
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
        if (ui.isCreator) AddPlaceButton(premiumPill = ui.atFreeLimit, onClick = onAdd)
    }
}

@Composable
private fun PlaceRow(
    place: Place,
    onClick: () -> Unit,
) {
    val category = place.category
    val categoryName = stringResource((category ?: com.tomessa.miff.domain.place.PlaceCategory.OTHER).labelRes)
    val spoken =
        if (place.address.isNullOrEmpty()) {
            stringResource(R.string.place_list_row_noaddress_a11y, place.name, categoryName)
        } else {
            stringResource(R.string.place_list_row_a11y, place.name, categoryName, place.address)
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
    premiumPill: Boolean,
    onClick: () -> Unit,
) {
    val spoken =
        stringResource(if (premiumPill) R.string.place_list_add_premium_a11y else R.string.place_list_add_action)
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
        Text(text = stringResource(R.string.place_list_add_action), style = textBodyBold16, color = colorActionPrimary)
        if (premiumPill) PremiumPill()
    }
}

/** "Le lieu « X » est enregistré. Tes proches le prendront en compte dès qu'ils ouvriront Miff, ou dans les prochaines heures." */
@Composable
private fun SavedNotice(name: String) {
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
            text = stringResource(R.string.place_saved_notice, name),
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
    /** "Ajouter un lieu": [atFreeLimit] says that the free plan is full, so the host opens the offer instead of the form. */
    val onAddPlace: (atFreeLimit: Boolean) -> Unit = {},
)
