package com.tomessa.miff.ui.notification

import androidx.lifecycle.ViewModel
import com.tomessa.miff.data.notification.NotificationAuthorization
import com.tomessa.miff.data.notification.NotificationSettings
import com.tomessa.miff.domain.notification.NotificationFamily
import com.tomessa.miff.domain.notification.NotificationPermissionState
import com.tomessa.miff.domain.notification.NotificationPreferences
import com.tomessa.miff.domain.notification.NotificationType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/*
 * No analytics event, no write to the account: the choices of this screen stay on the phone (ticket 9.2, decision Q16 for phase 0).
 */

/** What Settings > Notifications draws (ticket section 9). */
data class NotificationSettingsUiState(
    val preferences: NotificationPreferences = NotificationPreferences(),
    val permission: NotificationPermissionState = NotificationPermissionState.ALLOWED,
    /** The person turned the normal channel of a family off in the phone settings (Android only): "Ce type est coupé dans le téléphone". */
    val reminderChannelBlocked: Boolean = false,
    val circleChannelBlocked: Boolean = false,
    /** The pop-in of the permission is open over the screen (the general switch was turned on while the permission had never been asked). */
    val primerOpen: Boolean = false,
) {
    /**
     * The general switch tells the truth: ON only when the phone allows AND the person wants them. A refused permission is never drawn as "on"
     * (criterion N-R4: no interface may suggest the notifications are active when the phone blocks them).
     */
    val generalChecked: Boolean get() = permission == NotificationPermissionState.ALLOWED && preferences.enabled

    /** Not actionable when the phone refuses: only the settings of the phone can change that. */
    val generalEnabled: Boolean get() = permission != NotificationPermissionState.OFF

    /** The switches of the types are greyed (and keep their value) while the general one is off. */
    val typesEnabled: Boolean get() = generalChecked

    fun blocked(family: NotificationFamily): Boolean =
        when (family) {
            NotificationFamily.REMINDER -> reminderChannelBlocked
            NotificationFamily.CIRCLE -> circleChannelBlocked
        }
}

/**
 * Settings > Notifications (NOTIF-2A). A switch applies AT ONCE (no "Enregistrer", like the language): what the switch covers is cancelled by whoever
 * observes [NotificationSettings.preferences] (the planner of phase B), within the second.
 */
class NotificationSettingsViewModel(
    private val settings: NotificationSettings,
    private val authorization: NotificationAuthorization,
    private val channelBlocked: (NotificationFamily) -> Boolean,
) : ViewModel() {
    private val mutable = MutableStateFlow(read(primerOpen = false))
    val uiState: StateFlow<NotificationSettingsUiState> = mutable.asStateFlow()

    private fun read(primerOpen: Boolean) =
        NotificationSettingsUiState(
            preferences = settings.current(),
            permission = authorization.state(),
            reminderChannelBlocked = channelBlocked(NotificationFamily.REMINDER),
            circleChannelBlocked = channelBlocked(NotificationFamily.CIRCLE),
            primerOpen = primerOpen,
        )

    private fun refresh() {
        mutable.value = read(primerOpen = mutable.value.primerOpen)
    }

    /** The screen is in front again (the person may have changed the permission or a channel in the settings of the phone). */
    fun onResumed() = refresh()

    fun onGeneralToggled(on: Boolean) {
        when (authorization.state()) {
            NotificationPermissionState.OFF -> {
                Unit
            }

            // Never asked: turning them on opens the pop-in, then the window of the system (ticket 9.2). Turning them "off" is already true.
            NotificationPermissionState.NOT_ASKED -> {
                if (on) mutable.value = read(primerOpen = true)
            }

            NotificationPermissionState.ALLOWED -> {
                settings.setEnabled(on)
                refresh()
            }
        }
    }

    fun onTypeToggled(
        type: NotificationType,
        on: Boolean,
    ) {
        if (!mutable.value.typesEnabled) return
        settings.setType(type, on)
        refresh()
    }

    fun onNightCalmToggled(on: Boolean) {
        if (!mutable.value.typesEnabled) return
        settings.setNightCalm(on)
        refresh()
    }

    /** "Plus tard" on the pop-in opened from here: nothing changes. */
    fun onPrimerDismissed() {
        mutable.value = read(primerOpen = false)
    }

    /** The system window came back (granted or not). If granted, the person's tap on the general switch is honoured. */
    fun onSystemWindowAnswered() {
        authorization.onSystemWindowAnswered()
        if (authorization.state() == NotificationPermissionState.ALLOWED) settings.setEnabled(true)
        mutable.value = read(primerOpen = false)
    }
}
