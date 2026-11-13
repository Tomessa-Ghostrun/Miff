package com.tomessa.miff.ui.notification

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.tomessa.miff.R
import com.tomessa.miff.data.miff.DirectoryState
import com.tomessa.miff.data.notification.NotificationChannels
import com.tomessa.miff.domain.navigation.MainTab
import com.tomessa.miff.domain.notification.EnableStep
import com.tomessa.miff.domain.notification.NotificationDestination
import com.tomessa.miff.domain.notification.NotificationType
import com.tomessa.miff.domain.permission.PermissionNames
import com.tomessa.miff.domain.place.RequestsView
import com.tomessa.miff.ui.main.DetectionHelpRoute
import com.tomessa.miff.ui.main.FeedFocus
import com.tomessa.miff.ui.main.TabsRoute
import com.tomessa.miff.ui.navigation.popBackStackIfSafe
import com.tomessa.miff.ui.permission.NotificationsPopIn
import com.tomessa.miff.ui.profile.ProfileRequestsViewModel
import com.tomessa.miff.ui.profile.profileRequestsFactory
import com.tomessa.miff.ui.profile.profileRequestsKey
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
                                allMiffNames = { everyMiffName(container) },
                                requesterNames = container.requesterDirectory.firstNames,
                            )
                        }
                    },
            )
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResumed() }
        val requestSystemWindow = rememberNotificationPermissionRequest(onAnswered = viewModel::onSystemWindowAnswered)
        // The requests to add a place live in the view model of the tabs: a line about one is checked against what that view model knows (it reads nothing).
        val tabsEntry = remember(it) { navController.getBackStackEntry(TabsRoute) }
        val requests: ProfileRequestsViewModel =
            viewModel(
                viewModelStoreOwner = tabsEntry,
                key = profileRequestsKey(scopeKey),
                factory = profileRequestsFactory(container),
            )
        NotificationCenterScreen(
            state = state,
            actions =
                NotificationCenterActions(
                    onBack = { navController.popBackStackIfSafe() },
                    onOpen = { row ->
                        viewModel.onOpen(row)
                        if (row.type == NotificationType.N8) {
                            // A request to add a place: the list of the places of THAT Miff, where it is first (ticket LIEU-REQ, section 7.1). If none of the requests
                            // the row stands for is pending any more, the list opens with a neutral sentence (never an error).
                            openRequestsOf(container, requests, row, context)
                            container.tabRequests.request(MainTab.PROFILE)
                        } else if (row.visit != null) {
                            // "Depuis ta dernière visite": the Feed, on the card of the person the line is about (of THAT Miff, which becomes the one shown if it is not).
                            openVisitCard(container, row)
                        } else {
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
                        }
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

/**
 * A tap on a line of "Depuis ta dernière visite": the Miff it is about becomes the Miff of the Feed (the same choice as the selector) and the Feed brings the card of the
 * person into view. A Miff the phone no longer knows, or a person who left it: the Feed opens as it is, never an error.
 */
private fun openVisitCard(
    container: AppContainer,
    row: CenterRow,
) {
    val parsed = row.visit ?: return
    val miffId = row.miffId ?: return
    val ready = container.miffDirectory.state.value as? DirectoryState.Ready
    if (ready != null && ready.miffs.any { it.id == miffId } && ready.activeId != miffId) {
        try {
            container.feedNotificationLink.onActiveMiff(miffId)
        } catch (e: Exception) {
            // The reminders never get in the way of the selector.
        }
        container.miffDirectory.select(miffId)
    }
    container.tabRequests.requestFocus(FeedFocus(miffId, parsed.uid))
    container.tabRequests.request(MainTab.FEED)
}

/** The names of ALL the Miffs by identifier (the lines about a request to add a place always say which Miff they are for). */
private fun everyMiffName(container: AppContainer): Map<String, String> {
    val ready = container.miffDirectory.state.value as? DirectoryState.Ready ?: return emptyMap()
    return ready.miffs.associate { it.id to it.name }
}

/**
 * A tap on a line about requests: the Miff it is about becomes the Miff of the Feed (the one the list of places of the Profile opens on, the same choice as the
 * selector of the Feed) when the person created it, and, if the view model of the tabs knows the requests of that Miff and none of the ones the line stands
 * for is pending, a neutral sentence says so.
 */
private fun openRequestsOf(
    container: AppContainer,
    requests: ProfileRequestsViewModel,
    row: CenterRow,
    context: Context,
) {
    val miffId = row.requestMiffId ?: return
    val ready = container.miffDirectory.state.value as? DirectoryState.Ready
    val mine = ready?.miffs?.firstOrNull { it.id == miffId }
    if (mine != null && ready.activeId != miffId) {
        try {
            container.feedNotificationLink.onActiveMiff(miffId)
        } catch (e: Exception) {
            // The reminders never get in the way of the selector.
        }
        container.miffDirectory.select(miffId)
    }
    val known =
        (requests.state.value.views[miffId] as? RequestsView.Ready)
            ?.requests
            ?.map { it.id }
            .orEmpty()
            .toSet()
    if (requests.state.value.views[miffId] is RequestsView.Ready && row.requestIds.none { it in known }) {
        Toast.makeText(context, R.string.place_request_gone, Toast.LENGTH_LONG).show()
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
