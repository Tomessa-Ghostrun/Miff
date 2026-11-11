package com.tomessa.miff.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tomessa.miff.AppContainer
import com.tomessa.miff.data.auth.NetworkMonitor
import com.tomessa.miff.data.notification.NotificationCenter
import com.tomessa.miff.data.place.MineRead
import com.tomessa.miff.data.place.PlaceRequestRepository
import com.tomessa.miff.data.place.RequestAcceptResult
import com.tomessa.miff.data.place.RequestDeclineResult
import com.tomessa.miff.data.place.RequestWithdrawResult
import com.tomessa.miff.data.place.RequesterDirectory
import com.tomessa.miff.data.place.RequestsObservation
import com.tomessa.miff.domain.miff.MiffFailure
import com.tomessa.miff.domain.notification.NotificationType
import com.tomessa.miff.domain.notification.PlaceRequestLines
import com.tomessa.miff.domain.place.PaywallReason
import com.tomessa.miff.domain.place.PlaceRequest
import com.tomessa.miff.domain.place.PlaceRequestBook
import com.tomessa.miff.domain.place.RequesterState
import com.tomessa.miff.domain.place.RequestsView
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/*
 * PRIVACY and COST, for this file: the requests (names, addresses) and the people who asked stay in memory, for this view model only (one per account and per
 * session: the next person never meets them); nothing is logged or sent as an event. The reading rules are [PlaceRequestBook]'s: a MEMBER reads ONLY its own
 * requests (a query of 3 documents at most), at most once per Miff and per 5 minutes; the CREATOR is told by ONE listener on ONE Miff (10 documents at most),
 * attached only while the app is in front, and reads the card of each person who asked once per session.
 */

/** The Miff the carousel is on, and what is known of the requests of every Miff shown so far. */
data class ProfileRequestsState(
    val selectedMiffId: String? = null,
    /** By id of Miff. A Miff that is not in the map has nothing yet. For a creator: the requests of people who are still in the Miff, oldest first. */
    val views: Map<String, RequestsView> = emptyMap(),
    /** The request being taken back, accepted or declined (its buttons wait, a second tap does nothing). */
    val busyRequestId: String? = null,
    /** Who asked, by Miff and by person (see [RequesterDirectory.key]). A person who is not in the map has not been read yet. */
    val requesters: Map<String, RequesterState> = emptyMap(),
    /** The dot on the Profile tab: a request waits that the creator has not seen in its list yet (ticket LIEU-REQ, question Q-R1). */
    val profileDot: Boolean = false,
) {
    /** What the list of the selected Miff shows. */
    val selected: RequestsView get() = selectedMiffId?.let { views[it] } ?: RequestsView.Loading

    fun request(
        miffId: String,
        requestId: String,
    ): PlaceRequest? = (views[miffId] as? RequestsView.Ready)?.requests?.firstOrNull { it.id == requestId }

    fun requester(request: PlaceRequest): RequesterState? =
        requesters[RequesterDirectory.key(request.miffId, request.requestedBy)]

    override fun toString(): String = "ProfileRequestsState(redacted)"
}

/** What the screens say once, calmly, after a gesture on a request. */
sealed interface RequestEvent {
    /** "Demande retirée." (by its author). */
    data object Withdrawn : RequestEvent

    /** "« X » a été ajouté à la Miff." (the creator accepted: the places of that Miff are read again). */
    data class Accepted(
        val miffId: String,
        val placeName: String,
    ) : RequestEvent {
        override fun toString(): String = "Accepted(redacted)"
    }

    /** "Demande retirée de la liste." (the creator declined). */
    data object Declined : RequestEvent

    /** The offer opens: the free plan has its 3 places, or the request is a danger zone. The request stays pending. */
    data class NeedsPremium(
        val reason: PaywallReason,
    ) : RequestEvent

    /** "Impossible de vérifier ton abonnement pour l'instant." (no offer is opened on a doubt). */
    data object SubscriptionUnknown : RequestEvent

    /** "Cette demande n'est plus en attente." */
    data object Gone : RequestEvent

    /** The gesture did not work: the calm generic sentence, or the "no connection" one. */
    data class Failed(
        val offline: Boolean,
    ) : RequestEvent
}

/**
 * The requests to add a place, under the list of places of the Profile tab (ticket LIEU-REQ, decision of the PM of 2026-11-11).
 *
 * MEMBER (B): the pending requests of the person are read when the card of a Miff appears ([onCardShown]), at the top of its list; one is taken back with
 * [onWithdraw].
 * CREATOR (C): [watch] listens to the requests of ONE Miff (the one of the card shown, else the active one: [tracked]) while the app is in front; each
 * request writes ONE line in the centre of notifications, lights the dot of the Profile tab until the list is seen, and is shown in the list with who asked;
 * [onAccept] and [onDecline] answer it. Requests older than 30 days, or made by somebody who left the Miff, are hidden and deleted by the creator.
 */
class ProfileRequestsViewModel(
    private val repository: PlaceRequestRepository,
    private val network: NetworkMonitor,
    private val myUid: () -> String?,
    /** The centre of notifications (local): one line per request received. Null in the tests that do not need it. */
    private val center: NotificationCenter? = null,
    /** The first names behind the lines of the centre (memory only). */
    private val directory: RequesterDirectory = RequesterDirectory(),
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val book = PlaceRequestBook(nowMillis)
    private val creatorMiffs = mutableSetOf<String>()
    private var activeCreatorMiff: String? = null
    private val seen = mutableSetOf<String>()
    private val resolving = mutableSetOf<String>()
    private val unknownSince = mutableMapOf<String, Long>()
    private val purging = mutableSetOf<String>()

    /** The Miffs whose requests are drawn (a card shown, a detail opened, a listener attached): the list of each is drawn again at every change. */
    private val drawn = mutableSetOf<String>()
    private val mutableState = MutableStateFlow(ProfileRequestsState())
    private val mutableTracked = MutableStateFlow<String?>(null)
    private val mutableEvents = MutableSharedFlow<RequestEvent>(extraBufferCapacity = 4)

    val state: StateFlow<ProfileRequestsState> = mutableState.asStateFlow()

    /**
     * The Miff whose requests the creator is told about live: the one of the card shown if the person created it, else the active Miff of the Feed if the
     * person created it, else none (a member is told nothing: it reads only its own requests). ONE listener, never one per Miff.
     */
    val tracked: StateFlow<String?> = mutableTracked.asStateFlow()

    val events: SharedFlow<RequestEvent> = mutableEvents.asSharedFlow()

    /** The Miff the Feed shows, and whether the person created it (from the list of Miffs, no reading). Decides [tracked] while no card has been shown. */
    fun onActiveMiff(
        miffId: String?,
        creator: Boolean,
    ) {
        activeCreatorMiff = miffId?.takeIf { creator }
        if (miffId != null) {
            if (creator) creatorMiffs += miffId else creatorMiffs -= miffId
        }
        updateTracked()
    }

    /**
     * The card of [miffId] has appeared (it is the page the person looks at). [creator]: the person created this Miff. A member reads its own requests if,
     * and only if, the book says so; a creator reads nothing here (the listener of [watch] feeds the list).
     */
    fun onCardShown(
        miffId: String,
        creator: Boolean,
    ) {
        if (creator) creatorMiffs += miffId else creatorMiffs -= miffId
        drawn += miffId
        mutableState.value = mutableState.value.copy(selectedMiffId = miffId)
        updateTracked()
        publish()
        if (creator || !book.needsReading(miffId)) return
        val uid = myUid() ?: return
        book.readingStarted(miffId)
        publish()
        viewModelScope.launch {
            when (val answer = repository.readMine(uid, miffId)) {
                is MineRead.Loaded -> book.readingSucceeded(miffId, answer.requests)
                is MineRead.Failed -> book.readingFailed(miffId)
            }
            publish()
        }
    }

    /** The detail of a request was opened with nothing known (the screen was recreated): the requests of that Miff are read once. */
    fun onRequestOpened(
        miffId: String,
        creator: Boolean,
    ) {
        if (mutableState.value.views[miffId] != null) return
        if (!creator) {
            onCardShown(miffId, creator = false)
            return
        }
        creatorMiffs += miffId
        drawn += miffId
        if (!book.needsReading(miffId)) return
        book.readingStarted(miffId)
        viewModelScope.launch {
            when (val answer = repository.readAll(miffId)) {
                is MineRead.Loaded -> onKnown(miffId, answer.requests, live = false)
                is MineRead.Failed -> book.readingFailed(miffId)
            }
            publish()
        }
    }

    /** "Réessayer"-like: reads again at the next appearance (a quiet refresh, nothing is said on a failure). */
    fun onRetry() {
        val miffId = mutableState.value.selectedMiffId ?: return
        book.invalidate(miffId)
        onCardShown(miffId, miffId in creatorMiffs)
    }

    /** The person opens the form or a request: whatever happens there, the lists are read again when they come back. */
    fun onRequestsMayChange() = book.invalidateAll()

    /** The Miffs the person is in now: the requests of any other are forgotten. */
    fun retainOnly(miffIds: Set<String>) {
        book.retainOnly(miffIds)
        creatorMiffs.retainAll(miffIds)
        drawn.retainAll(miffIds)
        if (activeCreatorMiff !in miffIds) activeCreatorMiff = null
        val current = mutableState.value
        // Nothing of a Miff the person left stays: its requests, and who asked there.
        mutableState.value =
            current.copy(
                selectedMiffId = current.selectedMiffId?.takeIf { it in miffIds },
                views = current.views.filterKeys { it in miffIds },
                requesters = current.requesters.filterKeys { key -> miffIds.any { key.startsWith("$it|") } },
            )
        updateTracked()
        publish()
    }

    // --- The creator: the listener ---------------------------------------------------------------------------------------------

    /**
     * Listens to the requests of [miffId] until cancelled (called inside `repeatOnLifecycle(STARTED)`: the listener lives only while the app is in front).
     * Each answer replaces what was known; what it holds is told to the centre, to the dot and to the list.
     */
    suspend fun watch(miffId: String) {
        creatorMiffs += miffId
        drawn += miffId
        try {
            repository.observe(miffId).collect { observation ->
                when (observation) {
                    is RequestsObservation.Data -> onKnown(miffId, observation.requests, live = true)

                    // Removed, or the Miff is gone: nothing of that Miff stays (the loss itself is announced once, over everything, by the host of the tabs).
                    RequestsObservation.AccessLost -> book.forget(miffId)

                    RequestsObservation.Failed -> Unit
                }
                publish()
            }
        } finally {
            book.liveStopped(miffId)
        }
    }

    private fun onKnown(
        miffId: String,
        requests: List<PlaceRequest>,
        live: Boolean,
    ) {
        if (live) book.live(miffId, requests) else book.readingSucceeded(miffId, requests)
        resolveRequesters(miffId, requests)
        val now = nowMillis()
        val received = requests.filterNot { it.isExpired(now) || requesterState(it) == RequesterState.NotMember }
        // ONE line per request in the centre (written without a notification of the system: the app is in front, the line IS the information).
        center?.let { c -> PlaceRequestLines.linesFor(miffId, received, now).forEach { c.add(it) } }
        purgeLater(miffId)
    }

    private fun requesterState(request: PlaceRequest): RequesterState? =
        mutableState.value.requesters[RequesterDirectory.key(request.miffId, request.requestedBy)]

    /** Reads the card of each person who asked, ONCE (a read that failed is tried again after a minute at the earliest, never in a loop). */
    private fun resolveRequesters(
        miffId: String,
        requests: List<PlaceRequest>,
    ) {
        for (uid in requests.map { it.requestedBy }.distinct()) {
            val key = RequesterDirectory.key(miffId, uid)
            val known = mutableState.value.requesters[key]
            val retryDue =
                known == RequesterState.Unknown && nowMillis() - (unknownSince[key] ?: 0L) >= UNKNOWN_RETRY_MILLIS
            if ((known != null && !retryDue) || key in resolving) continue
            resolving += key
            viewModelScope.launch {
                val answer = repository.readRequester(miffId, uid)
                resolving -= key
                if (answer == RequesterState.Unknown) unknownSince[key] = nowMillis()
                if (answer is RequesterState.Known) directory.put(miffId, uid, answer.card.firstName)
                mutableState.value =
                    mutableState.value.copy(requesters = mutableState.value.requesters + (key to answer))
                if (answer == RequesterState.NotMember) purgeLater(miffId)
                publish()
            }
        }
    }

    /** Requests that nobody will answer (older than 30 days, or made by somebody who left) are deleted by the creator, 4 per lot, once each. */
    private fun purgeLater(miffId: String) {
        if (miffId !in creatorMiffs) return
        val known = book.known(miffId) ?: return
        val now = nowMillis()
        val targets =
            known.filter {
                (it.isExpired(now) || requesterState(it) == RequesterState.NotMember) && it.id !in purging
            }
        if (targets.isEmpty()) return
        val ids = targets.map { it.id }
        purging += ids
        viewModelScope.launch {
            repository.purge(miffId, ids, remaining = (known.size - ids.size).coerceAtLeast(0))
            // Whether the lot went through or not, they stay hidden; a failure is tried again with the next answer of the listener.
            purging -= ids.toSet()
            ids.forEach { book.remove(miffId, it) }
            publish()
        }
    }

    // --- The creator: the list is seen ---------------------------------------------------------------------------------------------

    /** The list of [miffId] was drawn with its requests: the dot of the Profile tab goes, and so do the lines of that Miff in the centre (they are seen). */
    fun onListSeen(miffId: String) {
        val view = mutableState.value.views[miffId] as? RequestsView.Ready ?: return
        if (view.requests.isEmpty()) return
        seen += view.requests.map { seenKey(miffId, it) }
        center
            ?.items
            ?.value
            ?.filter { it.type == NotificationType.N8 && it.miffId == miffId && it.unread }
            ?.forEach { center.markRead(it.id) }
        publish()
    }

    private fun seenKey(
        miffId: String,
        request: PlaceRequest,
    ) = "$miffId|${request.id}"

    // --- The creator: answering ------------------------------------------------------------------------------------------------------

    /** "Accepter" (criteria C3, C5 to C8): the free plan is applied BEFORE writing; the request is left pending whenever the offer opens. */
    fun onAccept(request: PlaceRequest) {
        val uid = myUid() ?: return
        if (!startAnswer(request)) return
        viewModelScope.launch {
            when (val result = repository.accept(uid, request, knownCount(request.miffId))) {
                is RequestAcceptResult.Done -> {
                    book.remove(request.miffId, request.id)
                    mutableEvents.tryEmit(RequestEvent.Accepted(request.miffId, request.name))
                }

                is RequestAcceptResult.NeedsPremium -> {
                    mutableEvents.tryEmit(RequestEvent.NeedsPremium(result.reason))
                }

                RequestAcceptResult.SubscriptionUnknown -> {
                    mutableEvents.tryEmit(RequestEvent.SubscriptionUnknown)
                }

                RequestAcceptResult.Gone -> {
                    book.remove(request.miffId, request.id)
                    mutableEvents.tryEmit(RequestEvent.Gone)
                }

                is RequestAcceptResult.Failed -> {
                    mutableEvents.tryEmit(RequestEvent.Failed(offline = result.failure == MiffFailure.OFFLINE))
                }
            }
            endAnswer()
        }
    }

    /** "Refuser" (criterion C4): always possible, whatever the plan; nothing is written for the person who asked. */
    fun onDecline(request: PlaceRequest) {
        if (!startAnswer(request)) return
        viewModelScope.launch {
            when (val result = repository.decline(request, knownCount(request.miffId))) {
                RequestDeclineResult.Done -> {
                    book.remove(request.miffId, request.id)
                    mutableEvents.tryEmit(RequestEvent.Declined)
                }

                is RequestDeclineResult.Failed -> {
                    mutableEvents.tryEmit(RequestEvent.Failed(offline = result.failure == MiffFailure.OFFLINE))
                }
            }
            endAnswer()
        }
    }

    private fun knownCount(miffId: String): Int = book.known(miffId)?.size ?: 1

    private fun startAnswer(request: PlaceRequest): Boolean {
        if (mutableState.value.busyRequestId != null) return false
        if (!network.isOnline()) {
            mutableEvents.tryEmit(RequestEvent.Failed(offline = true))
            return false
        }
        mutableState.value = mutableState.value.copy(busyRequestId = request.id)
        return true
    }

    private fun endAnswer() {
        mutableState.value = mutableState.value.copy(busyRequestId = null)
        publish()
    }

    // --- The member: taking a request back -----------------------------------------------------------------------------------------

    /** "Retirer ma demande" (criterion M6): deletes the request, then it leaves the list at once. */
    fun onWithdraw(request: PlaceRequest) {
        if (!startAnswer(request)) return
        viewModelScope.launch {
            when (val result = repository.withdraw(request)) {
                RequestWithdrawResult.Done -> {
                    book.remove(request.miffId, request.id)
                    mutableEvents.tryEmit(RequestEvent.Withdrawn)
                }

                is RequestWithdrawResult.Failed -> {
                    mutableEvents.tryEmit(RequestEvent.Failed(offline = result.failure == MiffFailure.OFFLINE))
                }
            }
            endAnswer()
        }
    }

    // --- Publishing ---------------------------------------------------------------------------------------------------------------

    private fun updateTracked() {
        val selected = mutableState.value.selectedMiffId
        mutableTracked.value = selected?.takeIf { it in creatorMiffs } ?: activeCreatorMiff
    }

    private fun publish() {
        val current = mutableState.value
        val views = current.views.toMutableMap()
        // Every Miff that is drawn is drawn again (the clock moved: a request may have expired).
        val ids = views.keys + drawn + listOfNotNull(current.selectedMiffId, mutableTracked.value)
        for (id in ids) views[id] = visibleView(id)
        val tracked = mutableTracked.value
        val dot =
            tracked != null &&
                ((views[tracked] as? RequestsView.Ready)?.requests.orEmpty()).any { seenKey(tracked, it) !in seen }
        mutableState.value = current.copy(views = views, profileDot = dot)
    }

    /** The requests of a Miff as the list draws them: for a creator, only those of people who are still in the Miff (the others are being deleted). */
    private fun visibleView(miffId: String): RequestsView {
        val creator = miffId in creatorMiffs
        val view = book.view(miffId, creator)
        if (!creator || view !is RequestsView.Ready) return view
        return RequestsView.Ready(view.requests.filterNot { requesterState(it) == RequesterState.NotMember })
    }

    private companion object {
        const val UNKNOWN_RETRY_MILLIS = 60_000L
    }
}

/** The key of the view model of the requests: ONE per account and per session, shared by the tabs and the detail of a request. */
fun profileRequestsKey(scopeKey: String): String = "profile-requests-$scopeKey"

/** How the view model of the requests is made (the same recipe for the tabs, which create it, and for the detail, which finds it again). */
fun profileRequestsFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            ProfileRequestsViewModel(
                repository = container.placeRequestRepository,
                network = container.networkMonitor,
                myUid = { container.authGateway.currentUid },
                center = container.notificationCenter,
                directory = container.requesterDirectory,
            )
        }
    }
