package com.tomessa.miff.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tomessa.miff.AppContainer
import com.tomessa.miff.data.miff.DirectoryState
import com.tomessa.miff.domain.navigation.MainTab
import com.tomessa.miff.domain.navigation.TabBarConfig
import com.tomessa.miff.ui.feed.FeedScreen
import com.tomessa.miff.ui.feed.FeedViewModel
import com.tomessa.miff.ui.miff.AccessLostScreen
import com.tomessa.miff.ui.miff.CreateMiffRoute
import com.tomessa.miff.ui.miff.JoinMiffRoute
import com.tomessa.miff.ui.miff.MiffRoute
import com.tomessa.miff.ui.miff.miffEntryRoutes
import com.tomessa.miff.ui.miff.miffScreenRoute
import com.tomessa.miff.ui.navigation.popBackStackIfSafe
import com.tomessa.miff.ui.profile.ProfileTabScreen
import com.tomessa.miff.ui.profile.ProfileTabUiState
import com.tomessa.miff.ui.profile.ProfileTabViewModel
import com.tomessa.miff.ui.settings.AccountScreen
import com.tomessa.miff.ui.settings.AccountViewModel
import com.tomessa.miff.ui.settings.LanguageScreen
import com.tomessa.miff.ui.settings.LanguageViewModel
import com.tomessa.miff.ui.settings.MyInfoScreen
import com.tomessa.miff.ui.settings.MyInfoViewModel
import com.tomessa.miff.ui.settings.SettingsScreen
import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
data object TabsRoute

@Serializable
data object SettingsRoute

@Serializable
data object MyInfoRoute

@Serializable
data object AccountRoute

@Serializable
data object LanguageRoute

/**
 * The app once the onboarding is done (a Miff exists): the three tabs, and, over them, the Settings screens (which hide the
 * tab bar, like the mockups). Reached from [com.tomessa.miff.ui.navigation.AppRoot] only for the destination FEED: a person
 * without a Miff never sees the bar (ticket Onboarding 6.7). The tab in use and the scroll of each tab survive a rotation
 * of the phone and a trip to the Settings.
 */
@Composable
fun MainShell(
    container: AppContainer,
    tabBar: TabBarConfig,
) {
    val session = container.sessionManager
    val navController = rememberNavController()
    // One view model per account and per session: the next person never meets what the previous one saw.
    val scopeKey = "${session.currentUid}-${session.generation}"

    Box(Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = TabsRoute) {
            composable<TabsRoute> {
                val profileViewModel: ProfileTabViewModel =
                    viewModel(
                        key = "profile-tab-$scopeKey",
                        factory = viewModelFactory { initializer { ProfileTabViewModel(container.currentProfile) } },
                    )
                val profileState by profileViewModel.uiState.collectAsStateWithLifecycle()
                val feedViewModel: FeedViewModel =
                    viewModel(
                        key = "feed-$scopeKey",
                        factory = viewModelFactory { initializer { FeedViewModel(container.miffDirectory) } },
                    )
                val directoryState by feedViewModel.directoryState.collectAsStateWithLifecycle()
                MainTabs(
                    tabBar = tabBar,
                    profileState = profileState,
                    onOpenMyInfo = { navController.open(MyInfoRoute) },
                    onOpenSettings = { navController.open(SettingsRoute) },
                    onRetryProfile = profileViewModel::retry,
                    miffs = directoryState,
                    onRetryMiffs = feedViewModel::refresh,
                    onSelectMiff = feedViewModel::onSelect,
                    onCreateMiff = { navController.open(CreateMiffRoute) },
                    onJoinMiff = { navController.open(JoinMiffRoute) },
                    onOpenMiff = { navController.open(MiffRoute(it)) },
                )
            }
            composable<SettingsRoute> {
                SettingsScreen(
                    onBack = { navController.popBackStackIfSafe() },
                    onOpenMyInfo = { navController.open(MyInfoRoute) },
                    onOpenAccount = { navController.open(AccountRoute) },
                    onOpenLanguage = { navController.open(LanguageRoute) },
                )
            }
            composable<MyInfoRoute> {
                val viewModel: MyInfoViewModel =
                    viewModel(
                        key = "my-info-$scopeKey",
                        factory =
                            viewModelFactory {
                                initializer {
                                    MyInfoViewModel(
                                        session = session,
                                        repository = container.myProfiles,
                                        current = container.currentProfile,
                                        avatars = container.avatarCodec,
                                        network = container.networkMonitor,
                                        deviceRegion = { Locale.getDefault().country.takeIf { it.isNotBlank() } },
                                    )
                                }
                            },
                    )
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                MyInfoScreen(
                    state = state,
                    onBack = { navController.popBackStackIfSafe() },
                    onRetry = viewModel::retry,
                    onFirstNameChanged = viewModel::onFirstNameChanged,
                    onLastNameChanged = viewModel::onLastNameChanged,
                    onCountrySelected = viewModel::onCountrySelected,
                    onPhoneChanged = viewModel::onPhoneChanged,
                    onPhoneFocusLost = viewModel::onPhoneFocusLost,
                    onPhotoChosen = viewModel::onPhotoChosen,
                    onRemovePhoto = viewModel::onRemovePhoto,
                    onCameraUnavailable = viewModel::onCameraUnavailable,
                    onCropChanged = viewModel::onCropChanged,
                    onCropConfirm = viewModel::onCropConfirmed,
                    onCropCancel = viewModel::onCropCancelled,
                    onSave = viewModel::onSave,
                )
            }
            composable<AccountRoute> {
                val viewModel: AccountViewModel =
                    viewModel(
                        key = "account-$scopeKey",
                        factory =
                            viewModelFactory {
                                initializer {
                                    AccountViewModel(
                                        email = container.authGateway.currentEmail(),
                                        signOut = session::signOut,
                                    )
                                }
                            },
                    )
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                AccountScreen(
                    state = state,
                    onBack = { navController.popBackStackIfSafe() },
                    onSignOutRequested = viewModel::onSignOutRequested,
                    onSignOutConfirmed = viewModel::onSignOutConfirmed,
                    onSignOutCancelled = viewModel::onSignOutCancelled,
                )
            }
            composable<LanguageRoute> {
                val viewModel: LanguageViewModel =
                    viewModel(factory = viewModelFactory { initializer { LanguageViewModel(container.languageStore) } })
                val selected by viewModel.selected.collectAsStateWithLifecycle()
                LanguageScreen(
                    selected = selected,
                    onBack = { navController.popBackStackIfSafe() },
                    onSelect = viewModel::onSelect,
                )
            }
            miffEntryRoutes(
                container = container,
                navController = navController,
                onCreated = { navController.popBackStackIfSafe() },
                onJoined = { navController.popBackStackIfSafe() },
                onOpenExisting = { miffId ->
                    navController.navigate(MiffRoute(miffId)) {
                        popUpTo<JoinMiffRoute> { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
            miffScreenRoute(container, navController)
        }
        // The single message of a lost access (decision QM8), over everything: nothing of that Miff stays visible.
        val notices by container.miffDirectory.notice.collectAsStateWithLifecycle()
        notices.firstOrNull()?.let { notice ->
            AccessLostScreen(
                miffName = notice.name,
                onAcknowledge = {
                    container.miffDirectory.dismissNotice()
                    val onMiffScreen = navController.currentDestination?.hasRoute<MiffRoute>() == true
                    if (onMiffScreen) navController.popBackStackIfSafe()
                },
            )
        }
    }
}

/** launchSingleTop: two quick taps open one screen, not two. */
private fun NavController.open(route: Any) = navigate(route) { launchSingleTop = true }

/**
 * The tabs and their bar. The tab in use is kept through a rotation; leaving a tab keeps its own state (scroll position, and
 * everything it remembers) for when the person comes back; tapping the tab already in use brings it back to the top. The bar
 * disappears while the keyboard is open.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainTabs(
    tabBar: TabBarConfig,
    profileState: ProfileTabUiState,
    onOpenMyInfo: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetryProfile: () -> Unit,
    miffs: DirectoryState,
    onRetryMiffs: () -> Unit,
    onSelectMiff: (String) -> Unit,
    onCreateMiff: () -> Unit,
    onJoinMiff: () -> Unit,
    onOpenMiff: (String) -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf(MainTab.FEED) }
    var scrollToTop by rememberSaveable { mutableIntStateOf(0) }
    val holder = rememberSaveableStateHolder()
    val keyboardOpen = WindowInsets.isImeVisible
    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            if (!keyboardOpen) {
                MainTabBar(
                    config = tabBar,
                    selected = selected,
                    onSelect = { tab ->
                        if (tab == selected) scrollToTop++ else selected = tab
                    },
                    modifier = Modifier.navigationBarsPadding(),
                )
            }
        },
    ) { padding ->
        // The tab content draws under the status bar itself (gradient of the Profile); only the bar's space is reserved.
        holder.SaveableStateProvider(selected) {
            Box(Modifier.padding(bottom = padding.calculateBottomPadding()).consumeWindowInsets(padding)) {
                when (selected) {
                    MainTab.FEED -> {
                        FeedScreen(
                            state = miffs,
                            scrollToTopSignal = scrollToTop,
                            onRetry = onRetryMiffs,
                            onSelect = onSelectMiff,
                            onCreate = onCreateMiff,
                            onJoin = onJoinMiff,
                        )
                    }

                    MainTab.PROFILE -> {
                        ProfileTabScreen(
                            state = profileState,
                            scrollToTopSignal = scrollToTop,
                            onOpenMyInfo = onOpenMyInfo,
                            onOpenSettings = onOpenSettings,
                            onRetry = onRetryProfile,
                            miffs = miffs,
                            onOpenMiff = onOpenMiff,
                        )
                    }

                    // No destination until the UPDATE ticket exists: the button is disabled and cannot be selected.
                    MainTab.UPDATE -> {}
                }
            }
        }
    }
}
