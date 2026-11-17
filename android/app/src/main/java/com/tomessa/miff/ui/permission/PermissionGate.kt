package com.tomessa.miff.ui.permission

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tomessa.miff.AppContainer
import com.tomessa.miff.BuildConfig
import com.tomessa.miff.domain.permission.GatePolicy
import com.tomessa.miff.domain.permission.GateScreen
import com.tomessa.miff.domain.permission.LocationButton
import com.tomessa.miff.domain.permission.LocationFlow
import com.tomessa.miff.domain.permission.PermissionNames
import com.tomessa.miff.ui.auth.SessionLoadingScreen
import kotlinx.coroutines.launch

/*
 * PRIVACY, for this file: it ASKS the system for permissions, after the person tapped a button that says why; it reads NO position (AUT7)
 * and sends no event. A permission is never asked at start-up, only from these screens.
 */

/**
 * What stands between the person and the Feed (ticket Tracking, LOC-2a): the notification screen, the renewal of the consent, and the BLOCKING
 * location screen (see [com.tomessa.miff.domain.permission.resolveGate] for the order). The state of the permissions is read again each
 * time the app comes back to the front: a permission withdrawn in the settings brings the blocking screen back (AUT5).
 * [content] is the Feed (the tabs), composed only when nothing is left to ask.
 */
@Composable
fun PermissionGate(
    container: AppContainer,
    content: @Composable () -> Unit,
) {
    val session = container.sessionManager
    val viewModel: PermissionGateViewModel =
        viewModel(
            // One per account and per session: the next person never inherits what the previous one answered.
            key = "permission-gate-${session.currentUid}-${session.generation}",
            factory =
                viewModelFactory {
                    initializer {
                        PermissionGateViewModel(
                            permissions = container.devicePermissions,
                            asked = container.notificationAsks,
                            renewals = container.consentRenewals,
                            currentUid = { session.currentUid },
                            locationBlocking = BuildConfig.LOCATION_BLOCKING,
                            leaver = { uid, miffId -> container.miffRepository.leave(uid, miffId) },
                            onLeft = { miffId -> container.onMiffLeft(miffId) },
                            awaiting = container.awaitingAgreement,
                        )
                    }
                },
        )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer =
            LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.onResumed() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notifications =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.onNotificationsAnswered()
        }
    val locationWindow =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            // After the answer: will the system still show its window for what is missing? (Not after "don't ask again".)
            val snapshot = viewModel.currentSnapshot
            val asked =
                if (!snapshot.fineLocation) PermissionNames.FINE_LOCATION else PermissionNames.BACKGROUND_LOCATION
            val activity = context.findActivity()
            val canAskAgain = activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, asked)
            viewModel.onLocationDialogAnswered(canAskAgain)
        }

    // "All the time" on Android 11+: the standard request, by which the system opens ITS OWN screen of the location permission of Miff. The
    // settings of the app (the former behaviour) only when the system opened nothing (it refused at once): then they open on the same tap.
    val backgroundRequest =
        rememberBackgroundLocationRequest { outcome ->
            if (viewModel.onBackgroundRequestAnswered(outcome)) openAppSettings(context)
        }

    when (state.screen) {
        GateScreen.NONE -> {
            content()
        }

        GateScreen.CHECKING -> {
            // The look at the consents is under way (a few seconds at most): neither the Feed nor the "Toujours" screen yet.
            SessionLoadingScreen()
        }

        GateScreen.NOTIFICATIONS -> {
            NotificationsPopIn(
                onAllow = { notifications.launch(PermissionNames.POST_NOTIFICATIONS) },
                onLater = viewModel::onNotificationsPutOff,
            )
        }

        GateScreen.LOCATION -> {
            LocationPopIn(
                model = state.location,
                onAction = {
                    when (state.location.button) {
                        LocationButton.SYSTEM_DIALOG -> {
                            val sdk = viewModel.currentSnapshot.sdk
                            val missingFine = !viewModel.currentSnapshot.fineLocation
                            // Android 11+: this window only asks for the precise location ("all the time" has its own button, above).
                            // Android 10: the precise one with "all the time", then "all the time" alone if it was left out.
                            val wanted = LocationFlow.requestedPermissions(sdk)
                            locationWindow.launch(
                                (
                                    if (missingFine) {
                                        wanted
                                    } else {
                                        listOf(
                                            PermissionNames.BACKGROUND_LOCATION,
                                        )
                                    }
                                ).toTypedArray(),
                            )
                        }

                        LocationButton.SYSTEM_BACKGROUND_SCREEN -> {
                            backgroundRequest()
                        }

                        LocationButton.OPEN_SETTINGS -> {
                            viewModel.onSettingsOpened()
                            openAppSettings(context)
                        }
                    }
                },
                onPutOff = if (state.canPutOffLocation) viewModel::onLocationPutOff else null,
                onSignOut =
                    if (GatePolicy.EXIT_LINKS_ON_BLOCKING_SCREENS) {
                        { scope.launch { session.signOut() } }
                    } else {
                        null
                    },
            )
        }

        GateScreen.RENEWAL -> {
            state.renewal?.let { renewal ->
                RenewalScreen(
                    state = renewal,
                    onAccept = viewModel::onRenewalAccepted,
                    onLater = viewModel::onRenewalPutOff,
                    onLeave = viewModel::onLeaveRequested,
                    onLeaveConfirmed = viewModel::onLeaveConfirmed,
                    onLeaveCancelled = viewModel::onLeaveCancelled,
                )
            }
        }
    }
}

/** The settings page of this app in the system (permissions, then location). If the phone cannot open it, nothing happens. */
private fun openAppSettings(context: Context) {
    val intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // Nothing to open: the screen stays, the person can open the settings of the phone by hand.
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
