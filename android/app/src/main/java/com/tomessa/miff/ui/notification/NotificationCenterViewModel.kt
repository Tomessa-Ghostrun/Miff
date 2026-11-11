package com.tomessa.miff.ui.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomessa.miff.data.notification.NotificationAuthorization
import com.tomessa.miff.data.notification.NotificationCenter
import com.tomessa.miff.data.notification.NotificationSettings
import com.tomessa.miff.domain.notification.NotificationItem
import com.tomessa.miff.domain.notification.NotificationPermissionState
import com.tomessa.miff.domain.notification.NotificationType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/*
 * PRIVACY: the centre is local. Nothing here reads or writes the server, sends an event, or logs. A row carries a type, an identifier, a time and,
 * when the account has several Miffs, the NAME OF THE MIFF (never a place, an address or a person).
 */

/** One line of the centre, ready to draw. */
class CenterRow(
    val id: String,
    val type: NotificationType,
    /** Only when the account has several Miffs and the line is about one of them ("Miff « Famille »"); null otherwise. */
    val miffName: String?,
    val createdAtMillis: Long,
    val unread: Boolean,
) {
    override fun toString(): String = "CenterRow(redacted)"
}

/** The band at the top of the centre ("Les notifications sont coupées"), and what its button does. */
enum class CenterBanner {
    NONE,

    /** The phone refuses (or the person turned them off there): the calm help screen leads to the settings of the phone. */
    OFF,

    /** Android 13+, never asked: the button opens the window of the system. */
    NOT_ASKED,

    /** The phone allows but the general switch of Settings is off: the button turns it back on. */
    DISABLED,
}

data class NotificationCenterUiState(
    val rows: List<CenterRow> = emptyList(),
    val banner: CenterBanner = CenterBanner.NONE,
)

/**
 * The centre of notifications (NOTIF-6A). It shows what happened on THIS phone (reminders shown, caught up late, or written without a notification
 * because the app was in front): "Ici, tu retrouves tes rappels et les nouvelles de ta Miff". Lines are shown as unread during the visit and marked
 * read when the screen is left (so the dot of the bell goes away, calmly), or at a tap, or with "Tout marquer comme lu".
 */
class NotificationCenterViewModel(
    private val center: NotificationCenter,
    private val settings: NotificationSettings,
    private val authorization: NotificationAuthorization,
    /** The names of the Miffs by identifier, only when the account has several (the name tells which one a line is about). Empty otherwise. */
    private val miffNames: () -> Map<String, String>,
) : ViewModel() {
    private val permission = MutableStateFlow(authorization.state())

    val uiState: StateFlow<NotificationCenterUiState> =
        combine(center.items, settings.preferences, permission) { items, preferences, state ->
            val names = miffNames()
            NotificationCenterUiState(
                rows = items.map { it.toRow(names) },
                banner =
                    when {
                        state == NotificationPermissionState.OFF -> CenterBanner.OFF
                        state == NotificationPermissionState.NOT_ASKED -> CenterBanner.NOT_ASKED
                        !preferences.enabled -> CenterBanner.DISABLED
                        else -> CenterBanner.NONE
                    },
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, NotificationCenterUiState())

    init {
        // 30 days or 100 lines, applied when the centre opens.
        center.prune()
    }

    private fun NotificationItem.toRow(names: Map<String, String>) =
        CenterRow(
            id = id,
            type = type,
            miffName = miffId?.let(names::get),
            createdAtMillis = createdAtMillis,
            unread = unread,
        )

    /** The screen is in front again (the permission may have changed in the settings of the phone). */
    fun onResumed() {
        permission.value = authorization.state()
    }

    fun onOpen(id: String) = center.markRead(id)

    fun onDelete(id: String) = center.delete(id)

    fun onMarkAllRead() = center.markAllRead()

    fun onClearAll() = center.clearAll()

    /** "Les réactiver" when only the general switch of the app is off. */
    fun onEnableInApp() {
        settings.setEnabled(true)
        permission.value = authorization.state()
    }

    /** The system window came back (granted or not): from now on a refusal means "only the settings of the phone". */
    fun onSystemWindowAnswered() {
        authorization.onSystemWindowAnswered()
        if (authorization.state() == NotificationPermissionState.ALLOWED) settings.setEnabled(true)
        permission.value = authorization.state()
    }

    override fun onCleared() {
        // The visit is over: what was shown is read, so the dot of the bell goes away.
        center.markAllRead()
    }
}
