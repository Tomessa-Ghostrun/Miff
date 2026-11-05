package com.tomessa.miff.ui.navigation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.tomessa.miff.AppContainer
import com.tomessa.miff.BuildConfig
import com.tomessa.miff.data.auth.CredentialManagerClient
import com.tomessa.miff.data.auth.CredentialManagerGoogleSource
import com.tomessa.miff.data.auth.GoogleIdTokenSource
import com.tomessa.miff.data.session.SessionState
import com.tomessa.miff.domain.auth.AppDestination
import com.tomessa.miff.domain.auth.shouldShowWelcomeCarousel
import com.tomessa.miff.domain.navigation.TabBarConfig
import com.tomessa.miff.ui.auth.AccountLookupFailedScreen
import com.tomessa.miff.ui.auth.ConsentPanelScreen
import com.tomessa.miff.ui.auth.ConsentPanelViewModel
import com.tomessa.miff.ui.auth.PasswordResetScreen
import com.tomessa.miff.ui.auth.PasswordResetViewModel
import com.tomessa.miff.ui.auth.SessionLoadingScreen
import com.tomessa.miff.ui.auth.SignInScreen
import com.tomessa.miff.ui.auth.SignInViewModel
import com.tomessa.miff.ui.auth.SignUpScreen
import com.tomessa.miff.ui.auth.SignUpViewModel
import com.tomessa.miff.ui.main.MainShell
import com.tomessa.miff.ui.miff.MiffOnboardingHost
import com.tomessa.miff.ui.onboarding.WelcomeCarouselScreen
import com.tomessa.miff.ui.permission.PermissionGate
import com.tomessa.miff.ui.profile.OnboardingMode
import com.tomessa.miff.ui.profile.ProfileFlow
import com.tomessa.miff.ui.profile.ProfileViewModel
import com.tomessa.miff.ui.settings.AccountDeletedBanner
import com.tomessa.miff.ui.settings.AccountDeletionHost
import com.tomessa.miff.ui.startup.StartupSlot
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.util.Locale

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

                AppDestination.PROFILE, AppDestination.PHONE_ONLY -> {
                    val mode =
                        if (current.destination ==
                            AppDestination.PHONE_ONLY
                        ) {
                            OnboardingMode.PHONE_ONLY
                        } else {
                            OnboardingMode.FULL
                        }
                    // One view model per account, per mode and per session: after a sign-out the next person never meets
                    // what the previous one typed (it also keeps the crop through a rotation of the phone).
                    val profileViewModel: ProfileViewModel =
                        viewModel(
                            key = "profile-${mode.name}-${session.currentUid}-${session.generation}",
                            factory =
                                viewModelFactory {
                                    initializer {
                                        ProfileViewModel(
                                            mode = mode,
                                            session = session,
                                            pending = container.pendingConsent,
                                            drafts = container.profileDrafts,
                                            profiles = container.userProfiles,
                                            avatars = container.avatarCodec,
                                            network = container.networkMonitor,
                                            deviceRegion = { Locale.getDefault().country.takeIf { it.isNotBlank() } },
                                        )
                                    }
                                },
                        )
                    ProfileFlow(profileViewModel)
                }

                AppDestination.MIFF_CHOICE -> {
                    // Create or join a Miff (Miff ticket). The onboarding is complete only with a Miff: this host is left
                    // when the session moves on to the Feed.
                    MiffOnboardingHost(container)
                }

                AppDestination.ACCOUNT_DELETION -> {
                    // The deletion of the account is under way (or taken up again at launch): it has PRIORITY over every other screen.
                    AccountDeletionHost(container)
                }

                AppDestination.FEED -> {
                    // LOC-2a: the notification screen, the renewal of the consent and the blocking location screen come BEFORE the Feed.
                    PermissionGate(container) {
                        MainShell(
                            container = container,
                            tabBar = TabBarConfig.forBuild(isInternalBuild = BuildConfig.DEBUG),
                        )
                    }
                }
            }
        }
    }
}

/** The signed-out screens, and over them the one-time banner of a deletion of the account that finished behind the screen. */
@Composable
private fun SignedOutHost(
    container: AppContainer,
    start: AppDestination,
    googleSource: GoogleIdTokenSource,
) {
    // The deletion was finished behind the screen (app relaunched): one calm banner above the carousel, then it disappears.
    var deletedNotice by remember { mutableStateOf(container.sessionManager.consumeAccountDeletedNotice()) }
    Box(modifier = Modifier.fillMaxSize()) {
        SignedOutNavigation(container, start, googleSource)
        if (deletedNotice) AccountDeletedBanner(onDismiss = { deletedNotice = false })
    }
}

@Composable
private fun SignedOutNavigation(
    container: AppContainer,
    start: AppDestination,
    googleSource: GoogleIdTokenSource,
) {
    val navController = rememberNavController()
    // Re-read when the back stack changes: the back arrow only exists when there is a previous screen to go back to.
    val currentEntry by navController.currentBackStackEntryAsState()
    val canGoBack = currentEntry != null && navController.previousBackStackEntry != null

    // The carousel on the very first opening (no session, app never used, carousel never validated), and once after the deletion of an
    // account (decision of the PM, 2026-10-31): "Ton compte a été supprimé", then the carousel, then sign-up / sign-in. Both are read ONCE.
    val showCarousel =
        remember {
            val afterDeletion = container.sessionManager.consumeCarouselAfterDeletion()
            afterDeletion || shouldShowWelcomeCarousel(start, container.onboardingFlags.carouselSeen)
        }
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
        passwordResetRoute(container, navController)
    }
}

/** "Mot de passe oublié" (AUTH-2), shared by the signed-out screens and the re-authentication of the deletion of the account (AUTH-5). */
internal fun NavGraphBuilder.passwordResetRoute(
    container: AppContainer,
    navController: NavController,
) {
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

/** Credential Manager needs an Activity to show its dialog: unwrap the themed contexts of Compose. */
internal fun googleSourceFor(context: Context): GoogleIdTokenSource {
    var current: Context = context
    while (current is ContextWrapper && current !is Activity) current = current.baseContext
    return CredentialManagerGoogleSource(
        CredentialManagerClient(current),
        CredentialManagerGoogleSource.webClientIdFrom(context),
    )
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
