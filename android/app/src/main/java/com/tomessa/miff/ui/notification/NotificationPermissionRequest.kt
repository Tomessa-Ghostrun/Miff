package com.tomessa.miff.ui.notification

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.tomessa.miff.AppContainer
import com.tomessa.miff.domain.notification.NotificationPermissionState
import com.tomessa.miff.domain.permission.PermissionNames

/**
 * The window of the system for POST_NOTIFICATIONS (Android 13+), to launch from a button the person tapped (never at start-up, never in a loop:
 * ticket 8.5). [onAnswered] runs when the window comes back, granted or not: the caller marks "the window was shown" and reads the state again.
 * Returns the function to call from the button.
 */
@Composable
fun rememberNotificationPermissionRequest(onAnswered: () -> Unit): () -> Unit {
    val answered = rememberUpdatedState(onAnswered)
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { answered.value() }
    return { launcher.launch(PermissionNames.POST_NOTIFICATIONS) }
}

/** The state of the permission for a screen, read again each time the screen comes back to the front (a change made in the phone settings shows at once). */
@Composable
fun rememberNotificationPermissionState(container: AppContainer): NotificationPermissionState {
    var state by remember { mutableStateOf(container.notificationAuthorization.state()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { state = container.notificationAuthorization.state() }
    return state
}
