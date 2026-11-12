package com.tomessa.miff.ui.place

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tomessa.miff.R
import com.tomessa.miff.domain.place.RequestsView
import com.tomessa.miff.domain.place.asPlace
import com.tomessa.miff.ui.profile.ProfileRequestsViewModel
import com.tomessa.miff.ui.profile.RequestEvent

/*
 * SECURITY, for this file: the request (name, address, coordinates) is read from the state of the view model and drawn; nothing is logged or sent as an event.
 */

/**
 * The detail of a pending request (ticket LIEU-REQ, section 4.2), for its author: the place as it was asked (map, circle, address, radius, type), a calm banner
 * ("En attente de réponse."), and "Retirer ma demande". It reads nothing: the request comes from the view model of the tabs, which read it with the list.
 *
 * A request that is no longer there (answered, expired, taken back on another phone) sends the person back to the list with a neutral sentence; one that the
 * person takes back themselves says "Demande retirée." instead (never both).
 */
@Composable
fun PlaceRequestDetailHost(
    route: PlaceRequestDetailRoute,
    viewModel: ProfileRequestsViewModel,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val view = state.views[route.miffId]
    val request = state.request(route.miffId, route.requestId)
    var leaving by remember { mutableStateOf(false) }
    val goneText = stringResource(R.string.place_request_gone)
    val withdrawnText = stringResource(R.string.place_request_withdrawn)
    val offlineText = stringResource(R.string.miff_message_offline)
    val genericText = stringResource(R.string.miff_message_generic)
    // Nothing known yet (the screen was recreated): the list of this Miff is read, as when its card appears.
    LaunchedEffect(route) { if (view == null) viewModel.onCardShown(route.miffId, creator = false) }
    LaunchedEffect(view, request, leaving) {
        if (!leaving && view is RequestsView.Ready && request == null) {
            Toast.makeText(context, goneText, Toast.LENGTH_LONG).show()
            onClose()
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                RequestEvent.Withdrawn -> {
                    Toast.makeText(context, withdrawnText, Toast.LENGTH_SHORT).show()
                    onClose()
                }

                is RequestEvent.Failed -> {
                    leaving = false
                    Toast.makeText(context, if (event.offline) offlineText else genericText, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    PlaceDetailScreen(
        state =
            if (request != null) {
                PlaceDetailState(status = PlaceDetailStatus.READY, place = request.asPlace())
            } else {
                PlaceDetailState(status = PlaceDetailStatus.LOADING)
            },
        onBack = onClose,
        onEdit = {},
        onRetry = {},
        request =
            RequestDetailUi(
                busy = state.busyRequestId != null,
                onWithdraw = {
                    if (request != null) {
                        leaving = true
                        viewModel.onWithdraw(request)
                    }
                },
            ),
    )
}
