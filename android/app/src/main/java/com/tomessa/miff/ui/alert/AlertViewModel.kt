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
    /**
     * The list, ALWAYS usable: the emergency services come from the LOCAL catalogue and are there at once, 112 chosen; the people of the Miff join them when they are read (review of
     * #275, M2: never an empty box while the members load, up to 15 s offline).
     */
    class Ready(
        val list: AlertCallList,
        val selected: AlertDialTarget,
        /** The members could not be read (offline without a copy): only the emergency services are offered, with a calm sentence. */
        val contactsUnavailable: Boolean,
        /** The people of the Miff are still being read: the emergency services are already there. */
        val loadingPeople: Boolean = false,
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

    private val emergencyOnly: AlertCallList get() = AlertCallList.build(emptyList(), myUid().orEmpty(), region())

    private val mutableCall =
        MutableStateFlow<CallState>(
            emergencyOnly.let {
                CallState.Ready(it, it.preselected, contactsUnavailable = false, loadingPeople = true)
            },
        )

    /** The person chose a line herself: what arrives later never replaces her choice. */
    private var chosen = false
    val call: StateFlow<CallState> = mutableCall.asStateFlow()

    private var callJob: Job? = null
    private var callMiffId: String? = null

    /** The person touched the capture button for the first time (a press, a hold, a click, or the accessible actions), or published an update after the delivery, or declared an alert: the bubble is done for good. */
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

    /** The Miffs of the person are known: a NEW phone reads my alert of each Miff ONCE (1 read per Miff) and adopts one in progress. */
    suspend fun onMiffsKnown(miffIds: List<String>) {
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

    /**
     * The screen of the call opened for [miffId] (empty: no Miff is known, only the emergency services): the emergency services are shown AT ONCE, then the members are read once and
     * join the list (the creator or first member with a number becomes the choice, unless the person already chose).
     */
    fun loadCallList(miffId: String) {
        val state = mutableCall.value as? CallState.Ready
        // A reading that failed is read again when the screen is opened again for the same Miff.
        if (callMiffId == miffId && state != null && !state.loadingPeople && !state.contactsUnavailable) return
        callMiffId = miffId
        callJob?.cancel()
        chosen = false
        val services = emergencyOnly
        mutableCall.value =
            CallState.Ready(
                services,
                services.preselected,
                contactsUnavailable = false,
                loadingPeople = miffId.isNotEmpty(),
            )
        if (miffId.isEmpty()) return
        callJob =
            viewModelScope.launch {
                val me = myUid().orEmpty()
                val read = faces.read(miffId)
                val members = (read as? FacesRead.Loaded)?.members.orEmpty()
                val list = AlertCallList.build(members, me, region())
                val current = (mutableCall.value as? CallState.Ready)?.selected
                val keep = current?.takeIf { chosen && list.targets.any { t -> t.id == it.id } }
                mutableCall.value =
                    CallState.Ready(list, keep ?: list.preselected, contactsUnavailable = read !is FacesRead.Loaded)
            }
    }

    /** A line was chosen in the list (kept while the same alert goes on). */
    fun select(target: AlertDialTarget) {
        val state = mutableCall.value as? CallState.Ready ?: return
        if (state.list.targets.none { it.id == target.id }) return
        chosen = true
        // The same line again changes nothing and EMITS nothing: a new state object for the same choice made the screen of the daily limit re-run its effect without end (review of #275 v2, 1).
        if (state.selected.id == target.id) return
        mutableCall.value = CallState.Ready(state.list, target, state.contactsUnavailable, state.loadingPeople)
    }

    /** "Appeler les secours": the emergency number (112 where it exists) is chosen. */
    fun selectEmergency() {
        val state = mutableCall.value as? CallState.Ready ?: return
        // Already on an emergency service (112 from the start, or the one the person picked): nothing to do, and the choice of the person is never replaced.
        if (state.selected is AlertDialTarget.Emergency) return
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
