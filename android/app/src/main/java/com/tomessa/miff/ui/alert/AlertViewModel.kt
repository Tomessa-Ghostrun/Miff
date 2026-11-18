package com.tomessa.miff.ui.alert

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomessa.miff.data.alert.AlertController
import com.tomessa.miff.data.alert.AlertEvent
import com.tomessa.miff.data.alert.AlertLocalStore
import com.tomessa.miff.data.alert.MyAlert
import com.tomessa.miff.data.alert.TriggerResult
import com.tomessa.miff.data.miff.FacesRead
import com.tomessa.miff.data.miff.MemberFacesReader
import com.tomessa.miff.domain.alert.AlertCallList
import com.tomessa.miff.domain.alert.AlertDialTarget
import com.tomessa.miff.domain.alert.AlertFailure
import com.tomessa.miff.domain.alert.EmergencyCatalog
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/*
 * PRIVACY, for this file: the numbers of the members come from the cards the Feed already reads (`members/{uid}.phone`, visible to the members of the Miff): ONE reading of the
 * members of the Miff of origin when the screen of the call opens, nothing else. No permission "Contacts" and no address book. Nothing here is logged or sent as an event. The
 * region of the phone's locale (never the position) picks the emergency numbers.
 */

/** What the list of the screen of the call is. */
sealed interface CallState {
    data object Loading : CallState

    class Ready(
        val list: AlertCallList,
        val selected: AlertDialTarget,
        /** The members could not be read (offline without a copy): only the emergency services are offered, with a calm sentence. */
        val contactsUnavailable: Boolean,
    ) : CallState {
        override fun toString(): String = "Ready(redacted)"
    }
}

class AlertViewModel(
    private val controller: AlertController,
    private val local: AlertLocalStore,
    private val faces: MemberFacesReader,
    private val myUid: () -> String?,
    /** The region of the locale of the PHONE (ISO 3166), never the position. */
    private val region: () -> String?,
) : ViewModel() {
    /** MY alert (none, on its way, in progress). */
    val alert: StateFlow<MyAlert> = controller.state

    val events: SharedFlow<AlertEvent> = controller.events

    private val mutableHintDone = MutableStateFlow(local.hintDone())

    /** The blue bubble of the onboarding is shown while this is false (ticket 7.1, AD7). Local to the phone. */
    val hintDone: StateFlow<Boolean> = mutableHintDone.asStateFlow()

    private val mutableCall = MutableStateFlow<CallState>(CallState.Loading)
    val call: StateFlow<CallState> = mutableCall.asStateFlow()

    private var callJob: Job? = null
    private var callMiffId: String? = null

    /** The person started holding the button (the gesture was discovered), or published an update after the delivery, or declared an alert: the bubble is done for good. */
    fun onHintDone() {
        if (mutableHintDone.value) return
        local.markHintDone()
        mutableHintDone.value = true
    }

    /** The 5 seconds were held on the Update, in the Miff [miffId] (the one the Feed shows). */
    fun onTriggered(miffId: String): TriggerResult {
        onHintDone()
        return controller.trigger(miffId)
    }

    fun onEndRequested() = controller.end()

    /** The app opened: an alert on its way goes on, an active one comes back (the pointer of the phone). */
    fun restore() = controller.restore()

    /** The Miffs of the person are known: an alert whose Miff is gone goes with it; a NEW phone reads my alert of each Miff ONCE (1 read per Miff) and adopts one in progress. */
    suspend fun onMiffsKnown(miffIds: List<String>) {
        controller.onMiffsKnown(miffIds)
        controller.adoptFromServer(miffIds)
    }

    /** At each minute and at the return to the front: an alert past its date disappears; an active one is checked against the server once (1 read). */
    suspend fun onForeground() {
        controller.tick()
        controller.verifyActive()
    }

    fun tick() = controller.tick()

    fun onRetry() = controller.retry()

    fun onConfirmStale() = controller.confirmStale()

    fun onCancelPending() = controller.cancelPending()

    /** The screen of the call opened for [miffId]: its members are read once (and again when another Miff is the one of origin). */
    fun loadCallList(miffId: String) {
        if (callMiffId == miffId && mutableCall.value is CallState.Ready) return
        callMiffId = miffId
        callJob?.cancel()
        mutableCall.value = CallState.Loading
        callJob =
            viewModelScope.launch {
                val me = myUid().orEmpty()
                val read = faces.read(miffId)
                val members = (read as? FacesRead.Loaded)?.members.orEmpty()
                val list = AlertCallList.build(members, me, region())
                mutableCall.value =
                    CallState.Ready(list, list.preselected, contactsUnavailable = read !is FacesRead.Loaded)
            }
    }

    /** A line was chosen in the list (kept while the same alert goes on). */
    fun select(target: AlertDialTarget) {
        val state = mutableCall.value as? CallState.Ready ?: return
        if (state.list.targets.none { it.id == target.id }) return
        mutableCall.value = CallState.Ready(state.list, target, state.contactsUnavailable)
    }

    /** "Appeler les secours": the emergency number (112 where it exists) is chosen. */
    fun selectEmergency() {
        val state = mutableCall.value as? CallState.Ready ?: return
        val entry = state.list.emergency.firstOrNull { it.number == "112" } ?: state.list.emergency.first()
        select(AlertDialTarget.Emergency(entry))
    }

    /** A number that may reach the dialler: a person's E.164 (already checked), or a short number of the table. */
    fun dialable(target: AlertDialTarget): Boolean =
        when (target) {
            is AlertDialTarget.Person -> true
            is AlertDialTarget.Emergency -> EmergencyCatalog.isDialable(target.entry.number)
        }

    /** What the server said about me and that Miff when the alert could not start (sharing stopped, no longer a member): for the calm variant of the screen. */
    fun isNotSent(event: AlertEvent): AlertFailure? = (event as? AlertEvent.NotSent)?.failure
}
