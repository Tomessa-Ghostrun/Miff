package com.tomessa.miff.ui.place

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.tomessa.miff.AppContainer
import com.tomessa.miff.R
import com.tomessa.miff.domain.analytics.AnalyticsEvent
import com.tomessa.miff.domain.analytics.AnalyticsTracker
import com.tomessa.miff.domain.paywall.MonotonicClock
import com.tomessa.miff.domain.paywall.PaywallUiState
import com.tomessa.miff.domain.place.PaywallReason
import com.tomessa.miff.ui.main.TabsRoute
import com.tomessa.miff.ui.navigation.popBackStackIfSafe
import com.tomessa.miff.ui.paywall.PaywallActions
import com.tomessa.miff.ui.paywall.PaywallScreen
import com.tomessa.miff.ui.paywall.PaywallViewModel
import com.tomessa.miff.ui.paywall.currentPaywallState
import com.tomessa.miff.ui.profile.ProfileRequestsViewModel
import com.tomessa.miff.ui.profile.profileRequestsFactory
import com.tomessa.miff.ui.profile.profileRequestsKey
import kotlinx.serialization.Serializable
import java.util.Locale

/*
 * Routes of the places. Only identifiers travel in a route (the id of the Miff, the id of the place, a fixed word for the reason of
 * the offer): never a name, an address or a coordinate.
 */

/** The form: a new place, or the place [placeId] to edit, or (ticket LIEU-REQ) a REQUEST to add a place to the Miff [miffId] ([request] true: a member who is not its creator). */
@Serializable
data class PlaceFormRoute(
    val placeId: String? = null,
    /** The Miff the place is opened from: two different places can share an identifier in two Miffs; for a NEW place, the Miff ticked first (if the person created it). For a request: the Miff it is for. */
    val miffId: String? = null,
    val request: Boolean = false,
)

/** The detail of a pending request ([requestId] is `{uid}_{1..3}`: an identifier, never a name or an address). */
@Serializable
data class PlaceRequestDetailRoute(
    val miffId: String,
    val requestId: String,
)

@Serializable
data class PlaceDetailRoute(
    val miffId: String,
    val placeId: String,
)

/** The offer, with the reason it shows ([reason] is "limit", "danger" or "miff": a fixed word, nothing else travels). */
@Serializable
data class PlacePaywallRoute(
    val reason: String,
    /** The offer opened by "Accepter" on a request received (ticket LIEU-REQ-C): the context card says the request stays pending. A flag, nothing else travels. */
    val request: Boolean = false,
) {
    /** An unknown word (never produced by the app) falls back to the danger zone, as before. */
    fun paywallReason(): PaywallReason =
        when (reason) {
            "limit" -> PaywallReason.LIMIT
            "miff" -> PaywallReason.MIFF
            else -> PaywallReason.DANGER
        }

    companion object {
        fun of(
            reason: PaywallReason,
            request: Boolean = false,
        ) = PlacePaywallRoute(
            when (reason) {
                PaywallReason.LIMIT -> "limit"
                PaywallReason.MIFF -> "miff"
                PaywallReason.DANGER -> "danger"
            },
            request,
        )
    }
}

/** The trigger of the analytics for an offer: a fixed value of the contract (never a place, a name or a position). */
internal fun paywallTrigger(reason: PaywallReason): AnalyticsEvent.PaywallTrigger =
    when (reason) {
        PaywallReason.LIMIT -> AnalyticsEvent.PaywallTrigger.EXTRA_TRUSTED_PLACE
        PaywallReason.MIFF -> AnalyticsEvent.PaywallTrigger.EXTRA_MIFF
        PaywallReason.DANGER -> AnalyticsEvent.PaywallTrigger.DANGER_ZONE
    }

/**
 * The offer opened by "Accepter" on a request received (ticket LIEU-REQ-C): ONLY after the repository has read the plan on the SERVER and said so ("limit" or
 * "danger"); the screens that call it never decide from their own copy of the plan. The request is left as it is.
 */
fun NavController.openRequestOffer(reason: PaywallReason) =
    navigate(PlacePaywallRoute.of(reason, request = true)) { launchSingleTop = true }

/** launchSingleTop: two quick taps open one screen, not two. */
fun NavController.openPlaceRoute(route: Any) = navigate(route) { launchSingleTop = true }

/** The screens of the places, for the main host ([com.tomessa.miff.ui.main.MainShell]). */
fun NavGraphBuilder.placeRoutes(
    container: AppContainer,
    navController: NavController,
) {
    val session = container.sessionManager
    composable<PlaceFormRoute> { entry ->
        val route = entry.toRoute<PlaceFormRoute>()
        // One view model per account and per session (the next person never meets what the previous one typed).
        val scopeKey = "${session.currentUid}-${session.generation}"
        val context = LocalContext.current
        val goneText = stringResource(R.string.place_gone)
        val viewModel: PlaceFormViewModel =
            viewModel(
                key = "place-form-${route.placeId.orEmpty()}-${route.miffId.orEmpty()}-${route.request}-$scopeKey",
                factory =
                    viewModelFactory {
                        initializer {
                            PlaceFormViewModel(
                                placeId = route.placeId,
                                miffId = route.miffId,
                                repository = container.placeRepository,
                                search = container.addressSearch,
                                network = container.networkMonitor,
                                currentUid = { session.currentUid },
                                notices = container.placeNotices,
                                language = { if (Locale.getDefault().language == "fr") "fr" else "en" },
                                request = route.request,
                                requests = container.placeRequestRepository,
                            )
                        }
                    },
            )
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        LaunchedEffect(state.end) {
            when (state.end) {
                PlaceFormEnd.SAVED -> {
                    navController.popBackStackIfSafe()
                }

                PlaceFormEnd.DELETED -> {
                    // The detail of a place that no longer exists is not worth going back to: back to the list.
                    if (!navController.popBackStack<PlaceDetailRoute>(
                            inclusive = true,
                        )
                    ) {
                        navController.popBackStackIfSafe()
                    }
                }

                PlaceFormEnd.PLACE_GONE -> {
                    Toast.makeText(context, goneText, Toast.LENGTH_LONG).show()
                    if (!navController.popBackStack<PlaceDetailRoute>(
                            inclusive = true,
                        )
                    ) {
                        navController.popBackStackIfSafe()
                    }
                }

                null -> {
                    Unit
                }
            }
        }
        LaunchedEffect(state.paywall) {
            val reason = state.paywall ?: return@LaunchedEffect
            viewModel.onPaywallHandled()
            // The limit found when the form opens: the form has nothing to offer, the offer replaces it.
            val replaceForm =
                reason == PaywallReason.LIMIT && state.mode == PlaceFormMode.CREATE && state.owned.isEmpty()
            navController.navigate(PlacePaywallRoute.of(reason)) {
                launchSingleTop = true
                if (replaceForm) popUpTo<PlaceFormRoute> { inclusive = true }
            }
        }
        PlaceFormScreen(
            state = state,
            actions =
                PlaceFormActions(
                    onBack = { navController.popBackStackIfSafe() },
                    onRetryLoad = viewModel::retry,
                    onNameChanged = viewModel::onNameChanged,
                    onMiffSheetOpened = viewModel::onMiffSheetOpened,
                    onMiffSheetClosed = viewModel::onMiffSheetClosed,
                    onMiffToggled = viewModel::onMiffToggled,
                    onAddressChanged = viewModel::onAddressChanged,
                    onSuggestionChosen = viewModel::onSuggestionChosen,
                    onAddressCleared = viewModel::onAddressCleared,
                    onRetrySearch = viewModel::onRetrySearch,
                    onRadiusChanged = viewModel::onRadiusChanged,
                    onTypeSelected = viewModel::onTypeSelected,
                    onCategorySelected = viewModel::onCategorySelected,
                    onSave = viewModel::onSave,
                    onDeleteRequested = viewModel::onDeleteRequested,
                    onDeleteDismissed = viewModel::onDeleteDismissed,
                    onDeleteConfirmed = viewModel::onDeleteConfirmed,
                ),
        )
    }
    composable<PlaceDetailRoute> { entry ->
        val route = entry.toRoute<PlaceDetailRoute>()
        val context = LocalContext.current
        val goneText = stringResource(R.string.place_gone)
        val viewModel: PlaceDetailViewModel =
            viewModel(
                key = "place-detail-${route.miffId}-${route.placeId}-${session.currentUid}-${session.generation}",
                factory =
                    viewModelFactory {
                        initializer {
                            PlaceDetailViewModel(
                                miffId = route.miffId,
                                placeId = route.placeId,
                                repository = container.miffRepository,
                                currentUid = { session.currentUid },
                            )
                        }
                    },
            )
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        var attempt by remember { mutableIntStateOf(0) }
        val lifecycleOwner = LocalLifecycleOwner.current
        // The Miff is READ while the screen is on view (listeners cancelled in the background, attached again on return).
        LaunchedEffect(viewModel, attempt) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.observe() }
        }
        LaunchedEffect(state.status) {
            if (state.status == PlaceDetailStatus.GONE) {
                Toast.makeText(context, goneText, Toast.LENGTH_LONG).show()
                navController.popBackStackIfSafe()
            }
        }
        PlaceDetailScreen(
            state = state,
            onBack = { navController.popBackStackIfSafe() },
            onEdit = { navController.openPlaceRoute(PlaceFormRoute(route.placeId, route.miffId)) },
            onRetry = { attempt++ },
        )
    }
    composable<PlaceRequestDetailRoute> { entry ->
        val route = entry.toRoute<PlaceRequestDetailRoute>()
        // The requests live in the view model of the tabs (they are read there, one time, with the list): the detail shares it, it reads nothing of its own.
        val tabsEntry = remember(entry) { navController.getBackStackEntry(TabsRoute) }
        val viewModel: ProfileRequestsViewModel =
            viewModel(
                viewModelStoreOwner = tabsEntry,
                key = profileRequestsKey("${session.currentUid}-${session.generation}"),
                factory = profileRequestsFactory(container),
            )
        PlaceRequestDetailHost(
            route = route,
            myUid = session.currentUid,
            viewModel = viewModel,
            onClose = { navController.popBackStackIfSafe() },
            // "Accepter" at the limit of the free plan: the offer, with the sentence that says the request stays pending; the request is left as it is.
            onPaywall = navController::openRequestOffer,
        )
    }
    paywallRoute(container.analyticsTracker, container.paywallClock, navController)
}

/**
 * THE offer (paywall): the single destination of every entry (a 3rd place, a danger zone, a 2nd Miff from the selector of the Feed or from the carousel of
 * the Profile: they all open a [PlacePaywallRoute]), so that the rule of the close cross (3 seconds, at every display) lives in ONE place. A test checks it for
 * each entry (PaywallEntryPointsTest).
 */
fun NavGraphBuilder.paywallRoute(
    tracker: AnalyticsTracker,
    clock: MonotonicClock,
    navController: NavController,
) {
    composable<PlacePaywallRoute> { entry ->
        val paywall = entry.toRoute<PlacePaywallRoute>()
        PaywallDestination(
            reason = paywall.paywallReason(),
            request = paywall.request,
            tracker = tracker,
            clock = clock,
            state = currentPaywallState(),
            onClose = { navController.popBackStackIfSafe() },
        )
    }
}

/** One DISPLAY of the offer: its own [PaywallViewModel] (scoped to the navigation entry), so that nothing is shared between two displays. */
@Composable
internal fun PaywallDestination(
    reason: PaywallReason,
    request: Boolean,
    tracker: AnalyticsTracker,
    clock: MonotonicClock,
    state: PaywallUiState,
    onClose: () -> Unit,
) {
    val model: PaywallViewModel = viewModel(factory = viewModelFactory { initializer { PaywallViewModel(clock) } })
    val trigger = paywallTrigger(reason)
    // Two quick taps on the cross close ONE display: one `paywall_dismissed`.
    var closing by remember { mutableStateOf(false) }
    // The only analytics of the offer: it was shown (when it is really on view) and closed, with a fixed trigger and nothing else (criteria LIM5, CR14).
    PaywallScreen(
        reason = reason,
        request = request,
        model = model,
        state = state,
        actions =
            PaywallActions(
                onClose = {
                    if (!closing) {
                        closing = true
                        tracker.track(AnalyticsEvent.PaywallDismissed(trigger))
                        onClose()
                    }
                },
            ),
        onShown = { tracker.track(AnalyticsEvent.PaywallShown(trigger)) },
    )
}
