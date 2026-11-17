package com.tomessa.miff.ui.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomessa.miff.data.notification.NotificationAuthorization
import com.tomessa.miff.data.notification.NotificationCenter
import com.tomessa.miff.data.notification.NotificationSettings
import com.tomessa.miff.data.place.RequesterDirectory
import com.tomessa.miff.domain.notification.NotificationItem
import com.tomessa.miff.domain.notification.NotificationPermissionState
import com.tomessa.miff.domain.notification.NotificationType
import com.tomessa.miff.domain.notification.PlaceRequestLines
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
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
    /** N9 only: the first name of the author (the line says "{Prénom} a donné des nouvelles"). */
    val who: String? = null,
    /** N9 only: the Miff the line is about, for the tap (the Feed shows it if the person belongs to it). */
    val miffId: String? = null,
    /** The keys of the lines this row stands for: one, or several unread requests of the same person in the same Miff ("Léa a proposé 3 lieux"). */
    val ids: List<String> = listOf(id),
    /** A line about a request to add a place (N8): the Miff, ALWAYS named in its sentence. Null for every other line. */
    val requestMiffId: String? = null,
    val requestMiffName: String? = null,
    /** The first name of the person who asked, as the app knows it NOW (never stored in the centre); null when it is not known yet. */
    val requesterName: String? = null,
    /** The requests (identifiers) the row stands for: a tap on it tells if none of them is pending any more. */
    val requestIds: List<String> = emptyList(),
) {
    /** How many requests the row stands for (1 for every other line). */
    val count: Int get() = ids.size

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
    /** The names of ALL the Miffs: the line about a request to add a place always says which Miff it is for ("Léa propose un lieu pour « Famille »"). */
    private val allMiffNames: () -> Map<String, String> = { emptyMap() },
    /** The first names of the people who asked for a place, by Miff and person (memory only: the centre stores no name, the words are made here). */
    requesterNames: Flow<Map<String, String>> = flowOf(emptyMap()),
) : ViewModel() {
    private val permission = MutableStateFlow(authorization.state())

    val uiState: StateFlow<NotificationCenterUiState> =
        combine(
            center.items,
            settings.preferences,
            permission,
            requesterNames,
        ) { items, preferences, state, firstNames ->
            val names = miffNames()
            val everyName = allMiffNames()
            NotificationCenterUiState(
                rows =
                    items.filter { it.type != NotificationType.N8 }.map { it.toRow(names) } +
                        PlaceRequestLines.entriesOf(items).map { it.toRow(everyName, firstNames) },
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
            who = who,
            miffId = if (type == NotificationType.N9) miffId else null,
        )

    /** A line about requests: the unread ones of a person in a Miff are ONE row; the words are made from what is known now. */
    private fun PlaceRequestLines.Entry.toRow(
        miffNames: Map<String, String>,
        firstNames: Map<String, String>,
    ) = CenterRow(
        id = ids.first(),
        type = NotificationType.N8,
        miffName = null,
        createdAtMillis = createdAtMillis,
        unread = unread,
        ids = ids,
        requestMiffId = miffId,
        requestMiffName = miffId?.let(miffNames::get),
        requesterName =
            if (miffId != null &&
                requesterUid != null
            ) {
                firstNames[RequesterDirectory.key(miffId, requesterUid)]
            } else {
                null
            },
        requestIds = ids.mapNotNull { id -> id.split('|', limit = 3).getOrNull(2) },
    )

    /** The screen is in front again (the permission may have changed in the settings of the phone). */
    fun onResumed() {
        permission.value = authorization.state()
    }

    fun onOpen(id: String) = center.markRead(id)

    fun onDelete(id: String) = center.delete(id)

    /** A tap on a row: every line it stands for is read (one line, or the requests of one person that were grouped). */
    fun onOpen(row: CenterRow) = row.ids.forEach(::onOpen)

    fun onMarkAllRead() = center.markAllRead()

    fun onClearAll() = center.clearAll()

    /** "Les réactiver" when only the general switch of the app is off. */
    fun onEnableInApp() {
        settings.setEnabled(true)
        // The band that offers it carries the sentence about the identifier of the phone: this yes is the consent to register it (SRV-2).
        authorization.markConsentGiven()
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
