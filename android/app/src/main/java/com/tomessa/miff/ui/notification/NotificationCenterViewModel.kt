package com.tomessa.miff.ui.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomessa.miff.data.notification.NotificationAuthorization
import com.tomessa.miff.data.notification.NotificationCenter
import com.tomessa.miff.data.notification.NotificationSettings
import com.tomessa.miff.data.place.RequesterDirectory
import com.tomessa.miff.domain.notification.CenterAvatar
import com.tomessa.miff.domain.notification.CenterAvatars
import com.tomessa.miff.domain.notification.CenterSubject
import com.tomessa.miff.domain.notification.FaceLookup
import com.tomessa.miff.domain.notification.LastVisit
import com.tomessa.miff.domain.notification.NotificationItem
import com.tomessa.miff.domain.notification.NotificationPermissionState
import com.tomessa.miff.domain.notification.NotificationType
import com.tomessa.miff.domain.notification.PlaceRequestLines
import com.tomessa.miff.domain.profile.MyProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/*
 * PRIVACY: the centre keeps its lines on the phone. Nothing here sends an event or logs. A row carries a type, an identifier, a time, the round picture the line is about (a photo read from
 * MEMORY at this moment, never stored by the centre: CNC-A) and, when the account has several Miffs, the NAME OF THE MIFF (never a place or an address).
 * The only things this screen asks the server for are the ones the ticket allows (docs/tickets/centre-notifications-complet.md, 8.4 and 8.5): the journal of the notifications from a cursor when
 * it opens, and the card of a member whose face is not in memory yet (one reading per member, a dozen at the most, never in a loop).
 */

/** One line of the centre, ready to draw. */
class CenterRow(
    val id: String,
    val type: NotificationType,
    /** Only when the account has several Miffs and the line is about one of them ("Miff « Famille »"); null otherwise. */
    val miffName: String?,
    val createdAtMillis: Long,
    val unread: Boolean,
    /**
     * N9, N10 and N11: the first name of the loved one the line is about, as the app knows it NOW (the same card as the picture); null says "Un proche" (somebody who is gone, or who cannot
     * be read). Never a name taken from the line when the person is not in the Miff any more.
     */
    val who: String? = null,
    /** N9, N10, N11 and C1: the Miff the line is about, for the tap (the Feed shows it if the person belongs to it). */
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
    /** A line "Depuis ta dernière visite" (C1): what it says, read from its key (a kind, a category, a time, the member by identifier). Null for every other line. */
    val visit: LastVisit.Parsed? = null,
    /** C1: the first name of the member as the app knows it NOW (never stored in the centre); null when it is not known yet. */
    val visitName: String? = null,
    /** CNC-A: the round picture of the line (a person's photo or initials, the logo of Miff, or the neutral round while it is being read). */
    val avatar: CenterAvatar = CenterAvatar.Logo,
    /** CNC-A: the loved one the line is about (N9, N10, N11), for the tap that opens his card in the Feed. Null when the line does not say. */
    val actorUid: String? = null,
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
    /** NOTIF-P1A: the group "Depuis ta dernière visite" (at most 3 lines, newest first), drawn above the days. Visible whatever the state of the notifications (decision Q4). */
    val visitRows: List<CenterRow> = emptyList(),
)

/**
 * The centre of notifications (NOTIF-6A). It shows what happened on THIS phone (reminders shown, caught up late, or written without a notification
 * because the app was in front): "Ici, tu retrouves tes rappels et les nouvelles de ta Miff". Lines are shown as unread during the visit and marked
 * read when the screen is left (so the dot of the bell goes away, calmly), or at a tap, or with "Tout marquer comme lu".
 *
 * CNC-A: each line also carries its round picture (a person's photo, her initials, the logo of Miff), resolved from memory ([faces], [myProfile]); the faces missing from memory are read once
 * ([ensureFaces], a dozen at the most) and the journal of the server is read once from a cursor ([syncJournal]) when the screen opens. Both are asked in the background: the screen never waits.
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
    /** CNC-A: what memory knows about a member of a Miff (never reads). */
    private val faces: (miffId: String, uid: String) -> FaceLookup = { _, _ -> FaceLookup.Pending },
    /** CNC-A: changes when memory learned a face: the rows are drawn again. */
    facesChanged: Flow<Int> = flowOf(0),
    /** CNC-A: the profile of the person signed in (for the picture of her own reminders); null while it is not read. */
    myProfile: Flow<MyProfile?> = flowOf(null),
    /** CNC-A: reads the faces that memory does not have yet, for these (Miff, member) pairs (one reading each, a dozen at the most). */
    private val ensureFaces: suspend (Collection<Pair<String, String>>) -> Unit = {},
    /** CNC-A: reads the profile of the person when it is not in memory (once). */
    private val loadMyProfile: suspend () -> Unit = {},
    /** CNC-A: reads the journal of the server from the cursor and brings its lines in (calm: nothing is ever shown about a failure). */
    private val syncJournal: suspend () -> Unit = {},
    /** CNC-A: is this Miff one of the account's (the list the phone remembers)? A line about a loved one of a Miff that is gone (left, deleted, access lost) is not drawn (ticket 6.6). */
    private val isMyMiff: (miffId: String) -> Boolean = { true },
) : ViewModel() {
    private val permission = MutableStateFlow(authorization.state())

    private val profile: StateFlow<MyProfile?> =
        myProfile.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val uiState: StateFlow<NotificationCenterUiState> =
        combine(
            center.items,
            settings.preferences,
            permission,
            requesterNames,
            combine(facesChanged, myProfile) { _, mine -> mine },
        ) { items, preferences, state, firstNames, mine ->
            val names = miffNames()
            val everyName = allMiffNames()
            val known = HashMap<String, Boolean>()
            NotificationCenterUiState(
                visitRows =
                    items
                        .filter { it.type == NotificationType.C1 }
                        .mapNotNull { item ->
                            LastVisit.parse(item)?.let { item.toVisitRow(it, names, firstNames, mine) }
                        }.sortedByDescending { it.createdAtMillis },
                rows =
                    items
                        .filter { it.type != NotificationType.N8 && it.type != NotificationType.C1 && isDrawn(it, known) }
                        .map { it.toRow(names, mine) } +
                        PlaceRequestLines.entriesOf(items).map { it.toRow(everyName, firstNames, mine) },
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
        // CNC-A: in the background: the journal (it may add lines), then the faces of the people the lines are about (again each time a line is added). The screen never waits for either.
        viewModelScope.launch {
            try {
                syncJournal()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Calm: a failure is never shown (the lines of the phone are there).
            }
        }
        viewModelScope.launch {
            center.items.map { neededFaces(it) }.distinctUntilChanged().collectLatest { wanted ->
                try {
                    if (wanted.isNotEmpty()) ensureFaces(wanted)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // The faces stay what they were: the logo or the initials, never an error.
                }
            }
        }
        viewModelScope.launch {
            val hasOwnLines = center.items.value.any { CenterAvatars.subjectOf(it) == CenterSubject.Myself }
            if (hasOwnLines && profile.value == null) {
                try {
                    loadMyProfile()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Her initials are not known: the neutral round stays.
                }
            }
        }
    }

    /** A line about a loved one of a Miff that is not one of the account's any more is not drawn (the Miff is gone: left, deleted, access lost). The others are always drawn. */
    private fun isDrawn(
        item: NotificationItem,
        known: MutableMap<String, Boolean> = HashMap(),
    ): Boolean =
        item.type !in NotificationType.ABOUT_A_LOVED_ONE || item.miffId == null || known.getOrPut(item.miffId) { isMyMiff(item.miffId) }

    /** The (Miff, member) pairs whose face the lines in the centre need. */
    private fun neededFaces(items: List<NotificationItem>): Set<Pair<String, String>> =
        buildSet {
            for (item in items) {
                when (item.type) {
                    NotificationType.C1 -> {
                        val parsed = LastVisit.parse(item)
                        if (parsed != null && item.miffId != null) add(item.miffId to parsed.uid)
                    }

                    NotificationType.N8 -> {
                        val miffId = item.miffId
                        val uid = PlaceRequestLines.requesterOf(item)
                        if (miffId != null && uid != null) add(miffId to uid)
                    }

                    else -> {
                        val subject = CenterAvatars.subjectOf(item)
                        if (subject is CenterSubject.Person && isDrawn(item)) add(subject.miffId to subject.uid)
                    }
                }
            }
        }

    private fun look(
        subject: CenterSubject,
        stored: String?,
        mine: MyProfile?,
    ) = CenterAvatars.look(subject, faces, mine, stored)

    private fun NotificationItem.toRow(
        names: Map<String, String>,
        mine: MyProfile?,
    ): CenterRow {
        val subject = CenterAvatars.subjectOf(this)
        val seen = look(subject, who, mine)
        val aboutLovedOne = type in NotificationType.ABOUT_A_LOVED_ONE
        return CenterRow(
            id = id,
            type = type,
            miffName = miffId?.let(names::get),
            createdAtMillis = createdAtMillis,
            unread = unread,
            who = if (aboutLovedOne) seen.name else null,
            miffId = if (aboutLovedOne) miffId else null,
            avatar = seen.avatar,
            actorUid = (subject as? CenterSubject.Person)?.uid,
        )
    }

    /** A line of "Depuis ta dernière visite": the words are made when it is drawn; the name of the Miff only when the account has several. */
    private fun NotificationItem.toVisitRow(
        parsed: LastVisit.Parsed,
        names: Map<String, String>,
        firstNames: Map<String, String>,
        mine: MyProfile?,
    ) = CenterRow(
        id = id,
        type = type,
        miffName = miffId?.let(names::get),
        createdAtMillis = createdAtMillis,
        unread = unread,
        visit = parsed,
        miffId = miffId,
        visitName = miffId?.let { firstNames[RequesterDirectory.key(it, parsed.uid)] },
        avatar = look(CenterAvatars.person(miffId, parsed.uid), null, mine).avatar,
    )

    /** A line about requests: the unread ones of a person in a Miff are ONE row; the words are made from what is known now. */
    private fun PlaceRequestLines.Entry.toRow(
        miffNames: Map<String, String>,
        firstNames: Map<String, String>,
        mine: MyProfile?,
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
        avatar = look(CenterAvatars.person(miffId, requesterUid), null, mine).avatar,
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
