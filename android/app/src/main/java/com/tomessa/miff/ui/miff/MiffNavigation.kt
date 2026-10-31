package com.tomessa.miff.ui.miff

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.tomessa.miff.AppContainer
import com.tomessa.miff.domain.analytics.AnalyticsEvent
import com.tomessa.miff.ui.navigation.popBackStackIfSafe
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** Routes of the Miff flow. The code and the name never travel in a route: only the id of the Miff, which is not a secret. */
@Serializable
data object MiffChoiceRoute

@Serializable
data object CreateMiffRoute

@Serializable
data object JoinMiffRoute

@Serializable
data class MiffRoute(
    val miffId: String,
)

/** launchSingleTop: two quick taps open one screen, not two. */
fun NavController.openMiffRoute(route: Any) = navigate(route) { launchSingleTop = true }

/**
 * "Créer" and "Rejoindre", shared by the two places that offer them: the onboarding (a person with no Miff yet) and the Feed
 * (empty state, Premium selector). [onCreated] and [onJoined] say what happens next (the host knows: leave the onboarding, or
 * go back to the tabs). [onOpenExisting] is "Ouvrir la Miff" of the message "tu fais déjà partie de cette Miff".
 */
fun NavGraphBuilder.miffEntryRoutes(
    container: AppContainer,
    navController: NavController,
    onCreated: () -> Unit,
    onJoined: () -> Unit,
    onOpenExisting: (String) -> Unit,
) {
    val session = container.sessionManager
    composable<CreateMiffRoute> {
        val viewModel: CreateMiffViewModel =
            viewModel(
                factory =
                    viewModelFactory {
                        initializer {
                            CreateMiffViewModel(
                                repository = container.miffRepository,
                                network = container.networkMonitor,
                                currentUid = { session.currentUid },
                                tracker = container.analyticsTracker,
                                onEntered = session::onMiffEntered,
                                onProfileMissing = session::retry,
                                onDirectoryChanged = container.miffDirectory::refresh,
                            )
                        }
                    },
            )
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        CreateMiffScreen(
            state = state,
            onBack = { navController.popBackStackIfSafe() },
            onNameChanged = viewModel::onNameChanged,
            onSubmit = viewModel::onSubmit,
            onContinue = onCreated,
        )
    }
    composable<JoinMiffRoute> {
        val scope = rememberCoroutineScope()
        val viewModel: JoinMiffViewModel =
            viewModel(
                factory =
                    viewModelFactory {
                        initializer {
                            JoinMiffViewModel(
                                repository = container.miffRepository,
                                network = container.networkMonitor,
                                currentUid = { session.currentUid },
                                tracker = container.analyticsTracker,
                                onEntered = session::onMiffEntered,
                                onProfileMissing = session::retry,
                                onDirectoryChanged = container.miffDirectory::refresh,
                                limiter = container.joinLimiter,
                            )
                        }
                    },
            )
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        // The lot was confirmed by the server: leave the screen (the confirmation step has nothing more to say).
        LaunchedEffect(state.joinedMiffId) {
            if (state.joinedMiffId != null) onJoined()
        }
        JoinMiffScreen(
            state = state,
            onBack = { navController.popBackStackIfSafe() },
            onCodeChanged = viewModel::onCodeChanged,
            onContinue = viewModel::onContinue,
            onConfirm = viewModel::onConfirm,
            onCancelConfirm = viewModel::onCancelConfirm,
            onOpenMiff = { miffId ->
                // "Ouvrir la Miff": the person is in that Miff, so the account has one (the end of the onboarding is counted).
                session.onMiffEntered(AnalyticsEvent.OnboardingVia.JOINED)
                scope.launch { container.miffDirectory.refresh() }
                onOpenExisting(miffId)
            },
        )
    }
}

/**
 * The onboarding step "create or join" (a person with a profile and no Miff, destination MIFF_CHOICE): the choice, then the
 * creation or the entry. It replaces the provisional screen of the Auth ticket, including its debug-only "Aperçu interne"
 * button (see the PR). The onboarding is complete only when a Miff was created or joined (CLAUDE.md): this host is left when
 * the session moves on to the Feed, never before.
 */
@Composable
fun MiffOnboardingHost(container: AppContainer) {
    val navController = rememberNavController()
    val session = container.sessionManager
    val scope = rememberCoroutineScope()
    NavHost(navController = navController, startDestination = MiffChoiceRoute) {
        composable<MiffChoiceRoute> {
            MiffChoiceScreen(
                onCreate = { navController.openMiffRoute(CreateMiffRoute) },
                onJoin = { navController.openMiffRoute(JoinMiffRoute) },
                onSignOut = { scope.launch { session.signOut() } },
            )
        }
        miffEntryRoutes(
            container = container,
            navController = navController,
            onCreated = session::continueAfterMiff,
            onJoined = session::continueAfterMiff,
            onOpenExisting = { session.continueAfterMiff() },
        )
    }
}

/** The screen of a Miff, for the Feed host ([com.tomessa.miff.ui.main.MainShell]). */
fun NavGraphBuilder.miffScreenRoute(
    container: AppContainer,
    navController: NavController,
) {
    composable<MiffRoute> { entry ->
        val route = entry.toRoute<MiffRoute>()
        val session = container.sessionManager
        val viewModel: MiffScreenViewModel =
            viewModel(
                key = "miff-${route.miffId}",
                factory =
                    viewModelFactory {
                        initializer {
                            MiffScreenViewModel(
                                miffId = route.miffId,
                                repository = container.miffRepository,
                                network = container.networkMonitor,
                                currentUid = { session.currentUid },
                                onAccessLost = container.miffDirectory::onAccessLost,
                                onNameChanged = container.miffDirectory::refresh,
                                deletions = container.miffDeletions,
                                onLeft = { id -> container.onMiffLeft(id) },
                            )
                        }
                    },
            )
        MiffScreenHost(viewModel = viewModel, onBack = { navController.popBackStackIfSafe() })
    }
}

/**
 * The screen of a Miff wired to its view model: it READS the Miff while it is on view. The three Firestore listeners and the
 * network watch run inside `repeatOnLifecycle(STARTED)` (cancelled in the background, attached again on return). Without this
 * effect the screen would never read anything (an empty, frozen list): `MiffScreenHostTest` fails in that case.
 */
@Composable
fun MiffScreenHost(
    viewModel: MiffScreenViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.observe() }
    }
    // The person left this Miff and the server confirmed: the screen of a Miff that is no longer theirs closes by itself.
    LaunchedEffect(state.departed) {
        if (state.departed) onBack()
    }
    MiffScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::retry,
        onNameChanged = viewModel::onNameInputChanged,
        onSaveName = viewModel::onSaveName,
        onSharingRequested = viewModel::onSharingRequested,
        onSharingConfirmed = viewModel::onSharingConfirmed,
        onSharingDismissed = viewModel::onSharingDialogDismissed,
        onRemoveRequested = viewModel::onRemoveRequested,
        onRenewRequested = viewModel::onRenewRequested,
        onLeaveRequested = viewModel::onLeaveRequested,
        onDeleteRequested = viewModel::onDeleteRequested,
        onActionConfirmed = viewModel::onActionConfirmed,
        onActionDismissed = viewModel::onActionDismissed,
        onInviteLater = viewModel::onInviteLater,
        onDeletionRetry = viewModel::onDeletionRetry,
    )
}
