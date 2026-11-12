package com.tomessa.miff.ui.place

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tomessa.miff.R
import com.tomessa.miff.data.miff.DirectoryState
import com.tomessa.miff.data.place.PlaceNotices
import com.tomessa.miff.domain.place.MiffPlacesView
import com.tomessa.miff.domain.place.PlaceViewerRole
import com.tomessa.miff.ui.components.MiffSecondaryButton
import com.tomessa.miff.ui.profile.ProfilePlacesState
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.colorTextDescription
import com.tomessa.miff.ui.theme.colorTextForm
import com.tomessa.miff.ui.theme.textBodyRegular
import com.tomessa.miff.ui.theme.textDisplayTitle

/*
 * SECURITY, for this file: the places (names, addresses) live in memory in the state given by the host only: never in a log or an event.
 */

/**
 * "Lieux de confiance de <la Miff>" on the Profile tab (mockup 17; decision of the PM of 2026-11-11): the places of the Miff whose card the carousel
 * shows, for EVERY member of that Miff.
 *
 * - A member only READS: the list, the detail of a place (without "Modifier"), never "Ajouter un lieu", never the counter of the free plan, never an
 *   offer "Miff+" (those concern the creator alone, who is the one that sets the places). What is drawn follows the role; what is ALLOWED is the
 *   rules' (`allow create, update, delete: if isOwner(miffId)`).
 * - Nothing is drawn until the places have been read (no empty flash, no invented absence); a list that cannot be read says so calmly and offers to try again.
 *
 * The list is read by [com.tomessa.miff.ui.profile.ProfilePlacesViewModel] (ONE reading per Miff, no listener).
 */
@Composable
fun ProfilePlacesSection(
    state: ProfilePlacesState,
    directory: DirectoryState.Ready,
    myUid: String?,
    notices: PlaceNotices,
    onOpenPlace: (miffId: String, placeId: String) -> Unit,
    onAddPlace: (miffId: String) -> Unit,
    onRetry: () -> Unit,
) {
    val notice by notices.notice.collectAsStateWithLifecycle()
    ConsumeNoticeOnLeave(notices::consume)
    val miff = directory.miffs.firstOrNull { it.id == state.selectedMiffId } ?: return
    val isCreator = PlaceViewerRole.isCreator(ownerUid = miff.ownerUid, myUid = myUid)
    // Nothing is emitted while the places are being read: not even an empty block (it would still take its margin in the column of the tab).
    if (state.view == MiffPlacesView.Loading) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        when (val view = state.view) {
            MiffPlacesView.Loading -> {
                Unit
            }

            MiffPlacesView.Unavailable -> {
                PlacesTitle(miff.name)
                Text(
                    text = stringResource(R.string.place_profile_unavailable),
                    style = textBodyRegular,
                    color = colorTextDescription,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                MiffSecondaryButton(text = stringResource(R.string.auth_retry_action), onClick = onRetry)
            }

            is MiffPlacesView.Ready -> {
                PlacesTitle(miff.name)
                PlaceRows(
                    ui =
                        PlaceListUi(
                            places = view.places,
                            isCreator = isCreator,
                            // The plan only matters to the creator (counter, "Miff+" pill): a member never gets a word about it.
                            premium = if (isCreator) directory.premiumOrNull else null,
                            savedName = if (isCreator) notice?.name else null,
                        ),
                    onOpen = { place -> onOpenPlace(miff.id, place.id) },
                    // Always the form: it reads the subscription on the SERVER and shows the offer itself when the limit is really reached.
                    // The list only knows a copy of the plan, which may be unknown or stale: it must never close the door on a Miff+ creator.
                    onAdd = { onAddPlace(miff.id) },
                )
            }
        }
    }
}

@Composable
private fun PlacesTitle(miffName: String) {
    Text(
        text = stringResource(R.string.place_profile_title_named, miffName),
        style = textDisplayTitle,
        color = colorTextForm,
        modifier = Modifier.semantics { heading() },
    )
}
