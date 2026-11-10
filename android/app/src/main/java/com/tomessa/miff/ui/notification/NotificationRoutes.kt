package com.tomessa.miff.ui.notification

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.tomessa.miff.AppContainer
import com.tomessa.miff.data.miff.DirectoryState
import com.tomessa.miff.data.notification.NotificationChannels
import com.tomessa.miff.domain.navigation.MainTab
import com.tomessa.miff.domain.notification.EnableStep
import com.tomessa.miff.domain.notification.NotificationDestination
import com.tomessa.miff.domain.permission.PermissionNames
import com.tomessa.miff.ui.main.DetectionHelpRoute
import com.tomessa.miff.ui.navigation.popBackStackIfSafe
import com.tomessa.miff.ui.permission.NotificationsPopIn
import com.tomessa.miff.ui.settings.ExternalLinks
import kotlinx.serialization.Serializable

/** Settings > Notifications (NOTIF-2A). */
@Serializable
data object NotificationSettingsRoute

/** The centre of notifications, opened by the bell of the Feed (NOTIF-6A). */
@Serializable
data object NotificationCenterRoute

/** "Notifications coupées": the calm help that leads to the settings of the phone (ticket 8.4). */
@Serializable
data object NotificationsHelpRoute

/** launchSingleTop: two quick taps open one screen, not two. */
internal fun NavController.openNotificationSettings() = navigate(NotificationSettingsRoute) { launchSingleTop = true }

internal fun NavController.openNotificationCenter() = navigate(NotificationCenterRoute) { launchSingleTop = true }

/**
 * The three screens of the notifications, in the graph of the tabs (NOT in the restricted Settings of a person with no Miff: nothing to notify without
 * a Miff; the state of the permission stays visible in Settings > Confidentialité).
 */
internal fun NavGraphBuilder.notificationRoutes(
    container: AppContainer,
    navController: NavHostController,
    scopeKey: String,
) {
    composable<NotificationSettingsRoute> {
        val context = LocalContext.current
        val viewModel: NotificationSettingsViewModel =
            viewModel(
                key = "notification-settings-$scopeKey",
                factory =
                    viewModelFactory {
                        initializer {
                            NotificationSettingsViewModel(
                                settings = container.notificationSettings,
                                authorization = container.notificationAuthorization,
                                channelBlocked = { NotificationChannels.isBlocked(context, it) },
                            )
                        }
                    },
            )
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        // The permission or a channel may have been changed in the settings of the phone while the screen was away.
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResumed() }
        val requestSystemWindow = rememberNotificationPermissionRequest(onAnswered = viewModel::onSystemWindowAnswered)
        Box(Modifier.fillMaxSize()) {
            NotificationSettingsScreen(
                state = state,
                actions =
                    NotificationSettingsActions(
                        onBack = { navController.popBackStackIfSafe() },
                        onGeneralToggled = viewModel::onGeneralToggled,
                        onTypeToggled = viewModel::onTypeToggled,
                        onNightCalmToggled = viewModel::onNightCalmToggled,
                        onOpenPhoneSettings = { ExternalLinks.openNotificationSettings(context) },
                        onSeeNotifications = { navController.openNotificationCenter() },
                        // "Si les rappels n'arrivent pas": the help for the phones that stop the apps in the background (LOC-2b), generic settings only.
                        onOpenHelp = { navController.navigate(DetectionHelpRoute) { launchSingleTop = true } },
                    ),
            )
            if (state.primerOpen) {
                // Turned on while the permission had never been asked: the pop-in first (it says what the person will get), then the window of the system.
                NotificationsPopIn(onAllow = requestSystemWindow, onLater = viewModel::onPrimerDismissed)
            }
        }
    }
    composable<NotificationCenterRoute> {
        val context = LocalContext.current
        val viewModel: NotificationCenterViewModel =
            viewModel(
                key = "notification-center-$scopeKey",
                factory =
                    viewModelFactory {
                        initializer {
                            NotificationCenterViewModel(
                                center = container.notificationCenter,
                                settings = container.notificationSettings,
                                authorization = container.notificationAuthorization,
                                miffNames = { severalMiffNames(container) },
                            )
                        }
                    },
            )
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResumed() }
        val requestSystemWindow = rememberNotificationPermissionRequest(onAnswered = viewModel::onSystemWindowAnswered)
        NotificationCenterScreen(
            state = state,
            actions =
                NotificationCenterActions(
                    onBack = { navController.popBackStackIfSafe() },
                    onOpen = { row ->
                        viewModel.onOpen(row.id)
                        // The screen the notification is about (never an error): the Update for N1 and N2, the Feed for the others.
                        container.tabRequests.request(
                            if (row.type.destination ==
                                NotificationDestination.UPDATE
                            ) {
                                MainTab.UPDATE
                            } else {
                                MainTab.FEED
                            },
                        )
                        navController.popBackStackIfSafe()
                    },
                    onDelete = viewModel::onDelete,
                    onMarkAllRead = viewModel::onMarkAllRead,
                    onClearAll = viewModel::onClearAll,
                    onBannerAction = { banner ->
                        when (banner) {
                            // Refused or never asked: the native window when Android can still show it, otherwise the calm help that opens the settings of the phone.
                            CenterBanner.NOT_ASKED, CenterBanner.OFF -> {
                                val state = container.notificationAuthorization.state()
                                val activity = context.findActivity()
                                val stillPossible =
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        activity != null &&
                                        ActivityCompat.shouldShowRequestPermissionRationale(
                                            activity,
                                            PermissionNames.POST_NOTIFICATIONS,
                                        )
                                when (EnableStep.of(state, Build.VERSION.SDK_INT, stillPossible)) {
                                    EnableStep.ASK_SYSTEM -> {
                                        requestSystemWindow()
                                    }

                                    EnableStep.OPEN_HELP -> {
                                        navController.navigate(NotificationsHelpRoute) {
                                            launchSingleTop =
                                                true
                                        }
                                    }
                                }
                            }

                            CenterBanner.DISABLED -> {
                                viewModel.onEnableInApp()
                            }

                            CenterBanner.NONE -> {
                                Unit
                            }
                        }
                    },
                ),
        )
    }
    composable<NotificationsHelpRoute> {
        val context = LocalContext.current
        NotificationsHelpScreen(
            onBack = { navController.popBackStackIfSafe() },
            onOpenPhoneSettings = { ExternalLinks.openNotificationSettings(context) },
        )
    }
}

/** The names of the Miffs by identifier, only when the account has several (the name then tells which Miff a line is about). */
private fun severalMiffNames(container: AppContainer): Map<String, String> {
    val ready = container.miffDirectory.state.value as? DirectoryState.Ready ?: return emptyMap()
    return if (ready.miffs.size > 1) ready.miffs.associate { it.id to it.name } else emptyMap()
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
