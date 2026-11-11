package com.tomessa.miff.ui.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomessa.miff.data.notification.NotificationAuthorization
import com.tomessa.miff.data.notification.NotificationCenter
import com.tomessa.miff.data.notification.NotificationSettings
import com.tomessa.miff.data.permission.NotificationAskStore
import com.tomessa.miff.domain.notification.NotificationList
import com.tomessa.miff.domain.notification.NotificationPermissionState
import com.tomessa.miff.domain.notification.SecondChanceRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** What the Feed needs of the notifications: the number behind the dot of the bell (never shown as a number) and whether the card of the second chance is due. */
data class FeedNotificationsState(
    val unreadCount: Int = 0,
    val secondChanceVisible: Boolean = false,
)

/**
 * The bell and the card of the second chance (NOTIF-1A, NOTIF-6A). Local only: nothing is read from or written to the server, nothing is sent as an
 * event. The card is due when the person put the pop-in off, has published an update since, and the phone would still let Miff ask (ticket 8.1).
 */
class FeedNotificationsViewModel(
    center: NotificationCenter,
    private val settings: NotificationSettings,
    private val authorization: NotificationAuthorization,
    private val asks: NotificationAskStore,
    private val currentUid: () -> String?,
) : ViewModel() {
    private val tick = MutableStateFlow(0)

    val uiState: StateFlow<FeedNotificationsState> =
        combine(center.items, tick) { items, _ ->
            val uid = currentUid()
            FeedNotificationsState(
                unreadCount = NotificationList.unreadCount(items),
                secondChanceVisible =
                    uid != null && SecondChanceRules.visible(asks.secondChance(uid), authorization.state()),
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, FeedNotificationsState())

    /** The Feed is in front again: the permission may have changed in the settings of the phone. */
    fun refresh() {
        tick.value++
    }

    /** An update was published: for someone who had put the pop-in off, the card becomes due (once). */
    fun onUpdatePublished() {
        val uid = currentUid() ?: return
        asks.setSecondChance(uid, SecondChanceRules.afterUpdatePublished(asks.secondChance(uid)))
        refresh()
    }

    /** "Pas maintenant": the card never comes back. */
    fun onSecondChanceLater() {
        answer()
    }

    /** The window of the system came back (from "Activer"): the card never comes back, and a yes turns the notifications on. */
    fun onSystemWindowAnswered() {
        authorization.onSystemWindowAnswered()
        if (authorization.state() == NotificationPermissionState.ALLOWED) settings.setEnabled(true)
        answer()
    }

    private fun answer() {
        val uid = currentUid() ?: return
        asks.setSecondChance(uid, SecondChanceRules.afterAnswer(asks.secondChance(uid)))
        refresh()
    }
}
