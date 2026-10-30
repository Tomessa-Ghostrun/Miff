package com.tomessa.miff.ui.navigation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.tomessa.miff.AppContainer
import com.tomessa.miff.data.auth.CredentialManagerGoogleSource
import com.tomessa.miff.data.auth.GoogleIdTokenSource
import com.tomessa.miff.data.session.SessionState
import com.tomessa.miff.domain.auth.AppDestination
import com.tomessa.miff.ui.auth.AccountLookupFailedScreen
import com.tomessa.miff.ui.auth.ConsentPanelScreen
import com.tomessa.miff.ui.auth.ConsentPanelViewModel
import com.tomessa.miff.ui.auth.NextStepPlaceholderScreen
import com.tomessa.miff.ui.auth.PasswordResetScreen
import com.tomessa.miff.ui.auth.PasswordResetViewModel
import com.tomessa.miff.ui.auth.SessionLoadingScreen
import com.tomessa.miff.ui.auth.SignInScreen
import com.tomessa.miff.ui.auth.SignInViewModel
import com.tomessa.miff.ui.auth.SignUpScreen
import com.tomessa.miff.ui.auth.SignUpViewModel
import com.tomessa.miff.ui.onboarding.WelcomeCarouselScreen
import com.tomessa.miff.ui.startup.StartupSlot
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data object SignUpRoute

@Serializable
data object SignInRoute

@Serializable
data object WelcomeRoute

/** "Mot de passe oublié" (AUTH-2). [email] is the address already typed on the sign-in screen, if any. */
@Serializable
data class PasswordResetRoute(
    val email: String = "",
)

/**
 * Root of the real app: shows what the SESSION says (ticket section 5): loading, sign-up / sign-in, consent panel, or
 * the next step of the account. The decision lives in [com.tomessa.miff.data.session.SessionManager], not here.
 */
@Composable
fun AppRoot(container: AppContainer) {
    // The splash comes first (extension point), then the session decides. Once per launch, kept across a rotation.
    var startupDone by rememberSaveable { mutableStateOf(false) }
    if (!startupDone) {
        StartupSlot(onFinished = { startupDone = true })
        return
    }
    val session = container.sessionManager
    val state by session.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val googleSource = remember(context) { googleSourceFor(context) }

    // Kept at this level (not inside the panel branch) so that it can be emptied when the panel is left:
    // the boxes ticked on a refused panel must be forgotten (ticket 7.8).
    val panelViewModel: ConsentPanelViewModel =
        viewModel(factory = viewModelFactory { initializer { ConsentPanelViewModel(session) } })
    val destination = (state as? SessionState.Ready)?.destination
    LaunchedEffect(destination) {
        if (destination != AppDestination.CONSENT_PANEL) panelViewModel.reset()
    }

    when (val current = state) {
        SessionState.Loading -> {
            SessionLoadingScreen()
        }

        SessionState.LookupFailed -> {
            AccountLookupFailedScreen(
                onRetry = session::retry,
                onSignOut = { scope.launch { session.signOut() } },
            )
        }

        is SessionState.Ready -> {
            when (current.destination) {
                AppDestination.SIGN_UP, AppDestination.SIGN_IN -> {
                    // key: a sign-out lands on a fresh navigation (sign-in screen), never on a stale one.
                    key(current.destination) { SignedOutHost(container, current.destination, googleSource) }
                }

                AppDestination.CONSENT_PANEL -> {
                    val panelState by panelViewModel.uiState.collectAsStateWithLifecycle()
                    ConsentPanelScreen(
                        state = panelState,
                        onContinue = panelViewModel::accept,
                        onRefuse = { panelViewModel.refuse(googleSource) },
                    )
                }

                AppDestination.PROFILE, AppDestination.MIFF_CHOICE, AppDestination.FEED -> {
                    NextStepPlaceholderScreen(
                        destination = current.destination,
                        onSignOut = { scope.launch { session.signOut() } },
                    )
                }
            }
        }
    }
}

@Composable
private fun SignedOutHost(
    container: AppContainer,
    start: AppDestination,
    googleSource: GoogleIdTokenSource,
) {
    val navController = rememberNavController()
    // Re-read when the back stack changes: the back arrow only exists when there is a previous screen to go back to.
    val currentEntry by navController.currentBackStackEntryAsState()
    val canGoBack = currentEntry != null && navController.previousBackStackEntry != null

    // The carousel only on the very first opening (no session, app never used, carousel never validated).
    val showCarousel = remember { start == AppDestination.SIGN_UP && !container.onboardingFlags.carouselSeen }
    val startDestination: Any =
        when {
            showCarousel -> WelcomeRoute
            start == AppDestination.SIGN_IN -> SignInRoute
            else -> SignUpRoute
        }
    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable<WelcomeRoute> {
            WelcomeCarouselScreen(
                onStart = {
                    container.onboardingFlags.markCarouselSeen()
                    navController.navigate(SignUpRoute)
                },
                onHaveAccount = {
                    container.onboardingFlags.markCarouselSeen()
                    navController.navigate(SignInRoute)
                },
            )
        }
        composable<SignUpRoute> {
            val viewModel: SignUpViewModel =
                viewModel(
                    factory =
                        viewModelFactory {
                            initializer { SignUpViewModel(container.sessionManager, container.networkMonitor) }
                        },
                )
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            SignUpScreen(
                state = uiState,
                onBack = if (canGoBack) ({ navController.popBackStack() }) else null,
                onSubmitEmail = viewModel::submitEmail,
                onGoogle = { viewModel.submitGoogle(googleSource) },
                onGoToSignIn = {
                    if (!navController.popBackStack<SignInRoute>(inclusive = false)) navController.navigate(SignInRoute)
                },
            )
        }
        composable<SignInRoute> {
            val viewModel: SignInViewModel =
                viewModel(
                    factory =
                        viewModelFactory {
                            initializer { SignInViewModel(container.sessionManager, container.networkMonitor) }
                        },
                )
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            SignInScreen(
                state = uiState,
                onBack = if (canGoBack) ({ navController.popBackStack() }) else null,
                onSubmitEmail = viewModel::submitEmail,
                onGoogle = { viewModel.submitGoogle(googleSource) },
                onGoToSignUp = {
                    if (!navController.popBackStack<SignUpRoute>(inclusive = false)) navController.navigate(SignUpRoute)
                },
                onForgotPassword = { email ->
                    // launchSingleTop: two quick taps open one screen, not two.
                    navController.navigate(PasswordResetRoute(email)) { launchSingleTop = true }
                },
            )
        }
        composable<PasswordResetRoute> { entry ->
            val route = entry.toRoute<PasswordResetRoute>()
            val viewModel: PasswordResetViewModel =
                viewModel(
                    factory =
                        viewModelFactory {
                            initializer {
                                PasswordResetViewModel(
                                    gateway = container.passwordResetGateway,
                                    network = container.networkMonitor,
                                    cooldown = container.passwordResetCooldown,
                                    nowMillis = SystemClock::elapsedRealtime,
                                )
                            }
                        },
                )
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            PasswordResetScreen(
                state = uiState,
                initialEmail = route.email,
                // System back and the predictive back gesture pop this screen too (NavHost), like the arrow.
                onBack = { navController.popBackStackIfSafe() },
                onEmailChanged = viewModel::onEmailChanged,
                onSubmit = viewModel::submit,
                onBackToSignIn = { navController.popBackStackIfSafe() },
            )
        }
    }
}

/** Credential Manager needs an Activity to show its dialog: unwrap the themed contexts of Compose. */
private fun googleSourceFor(context: Context): GoogleIdTokenSource {
    var current: Context = context
    while (current is ContextWrapper && current !is Activity) current = current.baseContext
    return CredentialManagerGoogleSource(current, CredentialManagerGoogleSource.webClientIdFrom(context))
}

/**
 * Pops one screen, but only if there is a screen to go back to AND the current one has finished its transition. Two quick
 * taps (or a tap and a back gesture) must not pop the sign-in screen as well and leave an empty stack (white screen).
 */
internal fun NavController.popBackStackIfSafe(): Boolean {
    val current = currentBackStackEntry ?: return false
    if (previousBackStackEntry == null || current.lifecycle.currentState != Lifecycle.State.RESUMED) return false
    return popBackStack()
}
