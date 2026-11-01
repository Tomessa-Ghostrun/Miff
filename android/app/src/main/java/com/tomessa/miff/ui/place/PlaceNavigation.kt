package com.tomessa.miff.ui.place

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.tomessa.miff.domain.place.PaywallReason
import com.tomessa.miff.ui.navigation.popBackStackIfSafe
import kotlinx.serialization.Serializable
import java.util.Locale

/*
 * Routes of the places. Only identifiers travel in a route (the id of the Miff, the id of the place, a fixed word for the reason of
 * the offer): never a name, an address or a coordinate.
 */

/** The form: a new place, or the place [placeId] to edit. */
@Serializable
data class PlaceFormRoute(
    val placeId: String? = null,
)

@Serializable
data class PlaceDetailRoute(
    val miffId: String,
    val placeId: String,
)

/** The offer, with the reason it shows ([reason] is "limit" or "danger"). */
@Serializable
data class PlacePaywallRoute(
    val reason: String,
) {
    companion object {
        fun of(reason: PaywallReason) = PlacePaywallRoute(if (reason == PaywallReason.LIMIT) "limit" else "danger")
    }
}

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
        val context = LocalContext.current
        val goneText = stringResource(R.string.place_gone)
        val viewModel: PlaceFormViewModel =
            viewModel(
                key = "place-form-${route.placeId.orEmpty()}-${session.currentUid}-${session.generation}",
                factory =
                    viewModelFactory {
                        initializer {
                            PlaceFormViewModel(
                                placeId = route.placeId,
                                repository = container.placeRepository,
                                search = container.addressSearch,
                                network = container.networkMonitor,
                                currentUid = { session.currentUid },
                                notices = container.placeNotices,
                                language = { if (Locale.getDefault().language == "fr") "fr" else "en" },
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
            onEdit = { navController.openPlaceRoute(PlaceFormRoute(route.placeId)) },
            onRetry = { attempt++ },
        )
    }
    composable<PlacePaywallRoute> { entry ->
        val reason =
            if (entry.toRoute<PlacePaywallRoute>().reason ==
                "limit"
            ) {
                PaywallReason.LIMIT
            } else {
                PaywallReason.DANGER
            }
        val tracker = container.analyticsTracker
        // The only analytics of the places: the offer was shown / closed, with a fixed trigger and nothing else (criterion LIM5).
        // The contract has a trigger for the 3rd place only; none exists yet for the danger zone (open point of the PR).
        LaunchedEffect(reason) {
            if (reason == PaywallReason.LIMIT) {
                tracker.track(AnalyticsEvent.PaywallShown(AnalyticsEvent.PaywallTrigger.EXTRA_TRUSTED_PLACE))
            }
        }
        PlacePaywallScreen(
            reason = reason,
            onClose = {
                if (reason == PaywallReason.LIMIT) {
                    tracker.track(AnalyticsEvent.PaywallDismissed(AnalyticsEvent.PaywallTrigger.EXTRA_TRUSTED_PLACE))
                }
                navController.popBackStackIfSafe()
            },
        )
    }
}
