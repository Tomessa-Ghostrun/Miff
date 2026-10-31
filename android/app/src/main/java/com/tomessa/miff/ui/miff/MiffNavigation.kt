package com.tomessa.miff.ui.miff

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.tomessa.miff.domain.miff.JoinAttemptLimiter
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
                                onDirectoryChanged = container.miffDirectory::refresh,
                                limiter = JoinAttemptLimiter(nowMillis = SystemClock::elapsedRealtime),
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
            onOpenMiff = onOpenExisting,
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
                            )
                        }
                    },
            )
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        MiffScreen(
            state = state,
            onBack = { navController.popBackStackIfSafe() },
            onRetry = viewModel::retry,
            onNameChanged = viewModel::onNameInputChanged,
            onSaveName = viewModel::onSaveName,
            onSharingRequested = viewModel::onSharingRequested,
            onSharingConfirmed = viewModel::onSharingConfirmed,
            onSharingDismissed = viewModel::onSharingDialogDismissed,
        )
    }
}
