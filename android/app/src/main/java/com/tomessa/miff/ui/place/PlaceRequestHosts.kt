package com.tomessa.miff.ui.place

import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tomessa.miff.R
import com.tomessa.miff.domain.place.PaywallReason
import com.tomessa.miff.domain.place.RequestsView
import com.tomessa.miff.domain.place.asPlace
import com.tomessa.miff.ui.profile.ProfileRequestsViewModel
import com.tomessa.miff.ui.profile.RequestEvent

/*
 * SECURITY, for this file: the request (name, address, coordinates) is read from the state of the view model and drawn; nothing is logged or sent as an event.
 */

/**
 * The detail of a pending request (ticket LIEU-REQ, section 4.2): the place as it was asked (map, circle, address, radius, type), a calm banner, and the buttons of
 * the person who looks at it. It reads nothing of its own: the request comes from the view model of the tabs, which read it with the list.
 *
 * - The REQUESTER ([myUid] is the one in the identifier of the request): "En attente de réponse." and "Retirer ma demande".
 * - The CREATOR: "Ce lieu n'est pas encore dans la Miff. Il y sera si tu acceptes.", "Demandé par {prénom}" with the photo or the initials, the date, and
 *   "Refuser" / "Accepter" (criteria C2 to C8). The offer opens at the limit of the free plan ([onPaywall]); the request stays pending.
 *
 * A request that is no longer there (answered, expired, taken back on another phone) sends the person back to the list with a neutral sentence; one that
 * the person answers or takes back themselves says what happened instead (never both).
 */
@Composable
fun PlaceRequestDetailHost(
    route: PlaceRequestDetailRoute,
    myUid: String?,
    viewModel: ProfileRequestsViewModel,
    onClose: () -> Unit,
    onPaywall: (PaywallReason) -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val view = state.views[route.miffId]
    val request = state.request(route.miffId, route.requestId)
    // The identifier of a request is `{uid of the requester}_{slot}`: whoever is not the requester and sees it is the creator of the Miff.
    val isCreator = route.requestId.substringBeforeLast('_') != myUid
    var leaving by remember { mutableStateOf(false) }
    val goneText = stringResource(R.string.place_request_gone)
    val withdrawnText = stringResource(R.string.place_request_withdrawn)
    val declinedText = stringResource(R.string.place_request_declined_toast)
    val unknownPlanText = stringResource(R.string.place_request_subscription_unknown)
    val offlineText = stringResource(R.string.miff_message_offline)
    val genericText = stringResource(R.string.miff_message_generic)
    // Nothing known yet (the screen was recreated): the requests of this Miff are read once.
    LaunchedEffect(route) { if (view == null) viewModel.onRequestOpened(route.miffId, isCreator) }
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

                is RequestEvent.Accepted -> {
                    Toast
                        .makeText(
                            context,
                            resources.getString(R.string.place_request_accepted_toast, event.placeName),
                            Toast.LENGTH_SHORT,
                        ).show()
                    onClose()
                }

                RequestEvent.Declined -> {
                    Toast.makeText(context, declinedText, Toast.LENGTH_SHORT).show()
                    onClose()
                }

                RequestEvent.Gone -> {
                    Toast.makeText(context, goneText, Toast.LENGTH_LONG).show()
                    onClose()
                }

                is RequestEvent.NeedsPremium -> {
                    // The request stays pending: the person comes back to it after the offer.
                    leaving = false
                    onPaywall(event.reason)
                }

                RequestEvent.SubscriptionUnknown -> {
                    leaving = false
                    Toast.makeText(context, unknownPlanText, Toast.LENGTH_LONG).show()
                }

                is RequestEvent.Failed -> {
                    leaving = false
                    Toast.makeText(context, if (event.offline) offlineText else genericText, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    val dateText =
        request?.createdAtMillis?.let {
            resources.getString(
                R.string.place_request_date,
                DateUtils.formatDateTime(context, it, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH),
            )
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
                placeName = request?.name.orEmpty(),
                onWithdraw =
                    if (isCreator) {
                        null
                    } else {
                        {
                            if (request != null) {
                                leaving = true
                                viewModel.onWithdraw(request)
                            }
                        }
                    },
                creator =
                    if (isCreator && request != null) {
                        RequestCreatorDetail(
                            requester = state.requester(request),
                            dateText = dateText,
                            onAccept = {
                                leaving = true
                                viewModel.onAccept(request)
                            },
                            onDecline = {
                                leaving = true
                                viewModel.onDecline(request)
                            },
                        )
                    } else {
                        null
                    },
            ),
    )
}
