package com.tomessa.miff.ui.main

import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tomessa.miff.AppContainer
import com.tomessa.miff.domain.navigation.MainTab
import com.tomessa.miff.domain.navigation.TabBarConfig
import com.tomessa.miff.ui.components.miffBottomInsets
import com.tomessa.miff.ui.components.miffKeyboardVisible
import com.tomessa.miff.ui.feed.FeedPlaceholderScreen
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
import com.tomessa.miff.ui.settings.PrivacyScreen
import com.tomessa.miff.ui.settings.PrivacyViewModel
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

@Serializable
data object PrivacyRoute

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

    NavHost(navController = navController, startDestination = TabsRoute) {
        composable<TabsRoute> {
            val profileViewModel: ProfileTabViewModel =
                viewModel(
                    key = "profile-tab-$scopeKey",
                    factory = viewModelFactory { initializer { ProfileTabViewModel(container.currentProfile) } },
                )
            val profileState by profileViewModel.uiState.collectAsStateWithLifecycle()
            MainTabs(
                tabBar = tabBar,
                profileState = profileState,
                onOpenMyInfo = { navController.open(MyInfoRoute) },
                onOpenSettings = { navController.open(SettingsRoute) },
                onRetryProfile = profileViewModel::retry,
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(
                onBack = { navController.popBackStackIfSafe() },
                onOpenMyInfo = { navController.open(MyInfoRoute) },
                onOpenAccount = { navController.open(AccountRoute) },
                onOpenLanguage = { navController.open(LanguageRoute) },
                onOpenPrivacy = { navController.open(PrivacyRoute) },
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
                                    signIn = container.authGateway.accountSignIn(),
                                    signOut = session::signOut,
                                    passwordReset = container.passwordResetGateway,
                                    network = container.networkMonitor,
                                    cooldown = container.passwordResetCooldown,
                                    nowMillis = SystemClock::elapsedRealtime,
                                )
                            }
                        },
                )
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            AccountScreen(
                state = state,
                onBack = { navController.popBackStackIfSafe() },
                onChangePassword = viewModel::onChangePasswordRequested,
                onSignOutRequested = viewModel::onSignOutRequested,
                onSignOutConfirmed = viewModel::onSignOutConfirmed,
                onSignOutCancelled = viewModel::onSignOutCancelled,
            )
        }
        composable<PrivacyRoute> {
            val viewModel: PrivacyViewModel =
                viewModel(
                    key = "privacy-$scopeKey",
                    factory =
                        viewModelFactory {
                            initializer {
                                PrivacyViewModel(
                                    privacy = container.privacySettings,
                                    withdrawals = container.consentWithdrawals,
                                    session = session,
                                    network = container.networkMonitor,
                                )
                            }
                        },
                )
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            PrivacyScreen(
                state = state,
                onBack = { navController.popBackStackIfSafe() },
                onAnalyticsToggled = viewModel::onAnalyticsToggled,
                onRetryWithdrawal = viewModel::onRetryWithdrawal,
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
    }
}

/** launchSingleTop: two quick taps open one screen, not two. */
private fun NavController.open(route: Any) = navigate(route) { launchSingleTop = true }

/**
 * The tabs and their bar. The tab in use is kept through a rotation; leaving a tab keeps its own state (scroll position, and
 * everything it remembers) for when the person comes back; tapping the tab already in use brings it back to the top. The bar
 * disappears while the keyboard is open.
 *
 * Safe areas (see ui/components/ScreenInsets.kt): the BAR keeps itself above the navigation bar (3 buttons or gestures); each
 * TAB keeps itself below the status bar with miffTopInsets() on its own root, so that it can draw its background (the gradient
 * of the Profile) under the status bar. This function must not consume insets it does not pad: it did once (Scaffold +
 * consumeWindowInsets), the status bar was "consumed" before the tabs could use it, and the Feed logo was drawn over the clock.
 */
@Composable
fun MainTabs(
    tabBar: TabBarConfig,
    profileState: ProfileTabUiState,
    onOpenMyInfo: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetryProfile: () -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf(MainTab.FEED) }
    var scrollToTop by rememberSaveable { mutableIntStateOf(0) }
    val holder = rememberSaveableStateHolder()
    val keyboardOpen = miffKeyboardVisible()
    Column(modifier = Modifier.fillMaxSize()) {
        holder.SaveableStateProvider(selected) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (selected) {
                    MainTab.FEED -> {
                        FeedPlaceholderScreen(scrollToTopSignal = scrollToTop)
                    }

                    MainTab.PROFILE -> {
                        ProfileTabScreen(
                            state = profileState,
                            scrollToTopSignal = scrollToTop,
                            onOpenMyInfo = onOpenMyInfo,
                            onOpenSettings = onOpenSettings,
                            onRetry = onRetryProfile,
                        )
                    }

                    // No destination until the UPDATE ticket exists: the button is disabled and cannot be selected.
                    MainTab.UPDATE -> {}
                }
            }
        }
        if (!keyboardOpen) {
            MainTabBar(
                config = tabBar,
                selected = selected,
                onSelect = { tab ->
                    if (tab == selected) scrollToTop++ else selected = tab
                },
                modifier = Modifier.miffBottomInsets(),
            )
        }
    }
}
