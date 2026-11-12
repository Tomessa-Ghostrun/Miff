package com.tomessa.miff.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tomessa.miff.AppContainer
import com.tomessa.miff.data.auth.NetworkMonitor
import com.tomessa.miff.data.place.MineRead
import com.tomessa.miff.data.place.PlaceRequestRepository
import com.tomessa.miff.data.place.RequestWithdrawResult
import com.tomessa.miff.domain.miff.MiffFailure
import com.tomessa.miff.domain.place.PlaceRequest
import com.tomessa.miff.domain.place.PlaceRequestBook
import com.tomessa.miff.domain.place.RequestsView
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/*
 * PRIVACY and COST, for this file: the requests (names, addresses) stay in memory, for this view model only (one per account and per session: the next
 * person never meets them); nothing is logged or sent as an event. The reading rules are [PlaceRequestBook]'s; a member reads ONLY its own requests
 * (a query of 3 documents at most), at most once per Miff and per 5 minutes.
 */

/** The Miff the carousel is on, and what is known of the requests of every Miff shown so far. */
data class ProfileRequestsState(
    val selectedMiffId: String? = null,
    /** By id of Miff. A Miff that is not in the map has nothing yet. */
    val views: Map<String, RequestsView> = emptyMap(),
    /** The request being taken back (its buttons wait, a second tap does nothing). */
    val busyRequestId: String? = null,
) {
    /** What the list of the selected Miff shows. */
    val selected: RequestsView get() = selectedMiffId?.let { views[it] } ?: RequestsView.Loading

    fun request(
        miffId: String,
        requestId: String,
    ): PlaceRequest? = (views[miffId] as? RequestsView.Ready)?.requests?.firstOrNull { it.id == requestId }

    override fun toString(): String = "ProfileRequestsState(redacted)"
}

/** What the screens say once, calmly, after a gesture on a request. */
sealed interface RequestEvent {
    /** "Demande retirée." (by its author). */
    data object Withdrawn : RequestEvent

    /** The gesture did not work: the calm generic sentence, or the "no connection" one. */
    data class Failed(
        val offline: Boolean,
    ) : RequestEvent
}

/**
 * The pending requests of the person under the list of places of the Profile tab (ticket LIEU-REQ-B, decision of the PM of 2026-11-11): a member who
 * is not the creator of the Miff sees its own pending requests at the top of the list, and may take one back.
 *
 * - [onCardShown]: the card of a Miff appeared; the requests of the person are read if, and only if, the book says so.
 * - [onRequestsMayChange]: the person opened the form or a request: the lists are read again when they come back.
 */
class ProfileRequestsViewModel(
    private val repository: PlaceRequestRepository,
    private val network: NetworkMonitor,
    private val myUid: () -> String?,
    nowMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val book = PlaceRequestBook(nowMillis)
    private val creatorMiffs = mutableSetOf<String>()
    private val mutableState = MutableStateFlow(ProfileRequestsState())
    private val mutableEvents = MutableSharedFlow<RequestEvent>(extraBufferCapacity = 4)

    val state: StateFlow<ProfileRequestsState> = mutableState.asStateFlow()

    val events: SharedFlow<RequestEvent> = mutableEvents.asSharedFlow()

    /**
     * The card of [miffId] has appeared (it is the page the person looks at). [creator]: the person created this Miff; they never ask for a place there
     * (their requests are the ones they RECEIVE, which a later ticket draws), so nothing is read for them here.
     */
    fun onCardShown(
        miffId: String,
        creator: Boolean,
    ) {
        if (creator) creatorMiffs += miffId else creatorMiffs -= miffId
        mutableState.value = mutableState.value.copy(selectedMiffId = miffId)
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
        if (mutableState.value.selectedMiffId !in miffIds) mutableState.value = ProfileRequestsState()
        publish()
    }

    /** "Retirer ma demande" (criterion M6): deletes the request, then it leaves the list at once. */
    fun onWithdraw(request: PlaceRequest) {
        if (mutableState.value.busyRequestId != null) return
        if (!network.isOnline()) {
            mutableEvents.tryEmit(RequestEvent.Failed(offline = true))
            return
        }
        mutableState.value = mutableState.value.copy(busyRequestId = request.id)
        viewModelScope.launch {
            when (val result = repository.withdraw(request)) {
                RequestWithdrawResult.Done -> {
                    book.remove(request.miffId, request.id)
                    mutableEvents.tryEmit(RequestEvent.Withdrawn)
                }

                is RequestWithdrawResult.Failed -> {
                    mutableEvents.tryEmit(
                        RequestEvent.Failed(offline = result.failure == MiffFailure.OFFLINE),
                    )
                }
            }
            mutableState.value = mutableState.value.copy(busyRequestId = null)
            publish()
        }
    }

    private fun publish() {
        val views = mutableState.value.views.toMutableMap()
        // Every Miff the book knows is drawn again (the clock moved: a request may have expired).
        val ids = views.keys + listOfNotNull(mutableState.value.selectedMiffId)
        for (id in ids) views[id] = book.view(id, creator = id in creatorMiffs)
        mutableState.value = mutableState.value.copy(views = views)
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
            )
        }
    }
