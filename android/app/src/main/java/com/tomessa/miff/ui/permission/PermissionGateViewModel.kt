package com.tomessa.miff.ui.permission

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomessa.miff.data.miff.AwaitingAgreement
import com.tomessa.miff.data.miff.ClarifyAskStore
import com.tomessa.miff.data.miff.ClarifyPolicy
import com.tomessa.miff.data.miff.ConsentRenewalCheck
import com.tomessa.miff.data.miff.ConsentRenewals
import com.tomessa.miff.data.miff.InMemoryClarifyAskStore
import com.tomessa.miff.data.miff.MiffWriteResult
import com.tomessa.miff.data.miff.RenewalMiff
import com.tomessa.miff.data.permission.DevicePermissions
import com.tomessa.miff.data.permission.NotificationAskStore
import com.tomessa.miff.domain.notification.SecondChanceRules
import com.tomessa.miff.domain.permission.BackgroundRequestOutcome
import com.tomessa.miff.domain.permission.GateInput
import com.tomessa.miff.domain.permission.GateScreen
import com.tomessa.miff.domain.permission.LocationAttempt
import com.tomessa.miff.domain.permission.LocationFlow
import com.tomessa.miff.domain.permission.LocationScreenModel
import com.tomessa.miff.domain.permission.PermissionSnapshot
import com.tomessa.miff.domain.permission.RenewalState
import com.tomessa.miff.domain.permission.resolveGate
import com.tomessa.miff.ui.miff.ActionMessage
import com.tomessa.miff.ui.miff.messageFor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/*
 * SECURITY / PRIVACY, for this file: nothing here reads a position (AUT7) or writes anything but the renewal of the consent, and that only
 * after the person tapped "J'accepte". No analytics event, no log. The names of the Miffs live in memory in this state only.
 */

/** The renewal screen (design screens 21 and 22). */
class RenewalUiState(
    val miffs: List<RenewalMiff>,
    /** Writing: the button says so and nothing else can be tapped (one tap = one operation). */
    val busy: Boolean = false,
    val message: ActionMessage? = null,
    /** The question "Quitter la Miff « X » ?" is open (members with one Miff only: the creator cannot leave, he deletes). */
    val leaveAsked: Boolean = false,
) {
    /** The way out of the screen of design 21B: a member whose only Miff is waiting for the agreement can leave it. */
    val leavable: RenewalMiff? get() = miffs.singleOrNull()?.takeIf { !it.isOwner && it.name.isNotEmpty() }

    override fun toString(): String = "RenewalUiState(redacted)"
}

/**
 * The calm card "Une précision sur ce que tu partages" (CONS-4-A, option D of the PM) for the Miffs where the person shares under the version 3 text.
 * NOTHING is paused for them: no position, status or photo stops, whatever the person answers.
 */
class ClarifyUiState(
    val miffs: List<RenewalMiff>,
    /** Writing: the button says so and nothing else can be tapped (one tap = one operation). */
    val busy: Boolean = false,
    val message: ActionMessage? = null,
) {
    override fun toString(): String = "ClarifyUiState(redacted)"
}

class GateUiState(
    val screen: GateScreen,
    val location: LocationScreenModel,
    val renewal: RenewalUiState?,
    /** The calm card, when [screen] is [GateScreen.CLARIFY]. */
    val clarify: ClarifyUiState? = null,
    /** Where the "Toujours" screen is relaxed by the build switch: a "Plus tard" link is shown. */
    val canPutOffLocation: Boolean,
)

/**
 * The permission screens of consent between the person and the Feed (LOC-2a). It re-reads the state of the permissions every time the app
 * comes back to the front (AUT5: a withdrawal makes the blocking screen come back), keeps what happened on the location screen
 * ([LocationAttempt]) and looks ONCE per launch at whether the consent must be renewed (R-LIEU-3).
 */
class PermissionGateViewModel(
    private val permissions: DevicePermissions,
    private val asked: NotificationAskStore,
    private val renewals: ConsentRenewals,
    private val currentUid: () -> String?,
    private val locationBlocking: Boolean,
    /** "Quitter la Miff" of the existing flow (uid, Miff id): the same lot as the button of the Miff screen. */
    private val leaver: suspend (String, String) -> MiffWriteResult,
    /** What the app does once a Miff was left (purge of the cache, fresh list of Miffs). */
    private val onLeft: suspend (String) -> Unit,
    private val consentBeforeLocation: Boolean = com.tomessa.miff.domain.permission.GatePolicy.CONSENT_BEFORE_LOCATION,
    /** LOC-2b (design 23): which Miffs wait for the agreement, for the Feed, the Miff screen and Settings; and "Revoir et accepter". */
    private val awaiting: AwaitingAgreement = AwaitingAgreement(),
    /** CONS-4-A: how many times the calm card was shown to this account (at most [ClarifyPolicy.MAX_SHOWS], then only Settings offers it). */
    private val clarifyAsks: ClarifyAskStore = InMemoryClarifyAskStore(),
) : ViewModel() {
    private var snapshot: PermissionSnapshot = permissions.snapshot()
    private var attempt = LocationAttempt()
    private var locationDeferred = false
    private var renewalDeferred = false

    // Checking from the start when there is a session: the "Toujours" screen waits for the end of the check (bounded by [CHECK_TIMEOUT_MS]).
    private var renewalState = if (currentUid() != null) RenewalState.CHECKING else RenewalState.UNKNOWN
    private var due: List<RenewalMiff> = emptyList()
    private var renewalBusy = false
    private var renewalMessage: ActionMessage? = null
    private var leaveAsked = false

    // The calm card (CONS-4-A). [clarifyDue] = the Miffs under the version 3 text; it is shown when it is offered by itself (the count is under the
    // maximum) or asked for from Settings, and not put off in this launch.
    private var clarifyDue: List<RenewalMiff> = emptyList()
    private var clarifyOffered = false
    private var clarifyAsked = false
    private var clarifyDeferred = false
    private var clarifyCounted = false
    private var clarifyBusy = false
    private var clarifyMessage: ActionMessage? = null
    private var notificationsHandled = currentUid()?.let(asked::wasShown) ?: true

    private val mutableState = MutableStateFlow(build())
    val uiState: StateFlow<GateUiState> = mutableState.asStateFlow()

    init {
        // "Revoir et accepter" (Feed, Miff screen, Settings): the renewal screen comes back, as it was at the opening.
        var reviews = awaiting.reviewRequests.value
        viewModelScope.launch {
            awaiting.reviewRequests.collect { count ->
                if (count != reviews) {
                    reviews = count
                    if (renewalState == RenewalState.DUE) {
                        if (!renewalBusy) {
                            renewalDeferred = false
                            renewalMessage = null
                            publish()
                        }
                    } else if (clarifyDue.isNotEmpty() && !clarifyBusy) {
                        // "Revoir et accepter" of Settings > Confidentialité for the calm card: shown again, whatever the count (never counted).
                        clarifyAsked = true
                        clarifyDeferred = false
                        clarifyMessage = null
                        publish()
                    }
                }
            }
        }
        val uid = currentUid()
        if (uid != null) {
            viewModelScope.launch {
                // A check that is too slow ends as "unknown": the screen never waits for ever (no network, a slow server).
                val check = withTimeoutOrNull(CHECK_TIMEOUT_MS) { renewals.check(uid) } ?: ConsentRenewalCheck.Unknown
                when (check) {
                    is ConsentRenewalCheck.Due -> {
                        // Two lists, two meanings (CONS-4-A, criterion CA-10): only the Miffs whose text does NOT cover the location are
                        // PAUSED ("En attente"); those under the version 3 text are only clarified, nothing is paused for them.
                        due = check.miffs
                        renewalState = if (check.miffs.isEmpty()) RenewalState.NOTHING_DUE else RenewalState.DUE
                        awaiting.replace(check.miffs.map { it.miffId }.toSet())
                        clarifyDue = check.clarify
                        awaiting.replaceClarifying(check.clarify.map { it.miffId }.toSet())
                        clarifyOffered = check.clarify.isNotEmpty() && mayOfferClarify(uid)
                    }

                    ConsentRenewalCheck.NothingToRenew -> {
                        renewalState = RenewalState.NOTHING_DUE
                        awaiting.replace(emptySet())
                        clarifyDue = emptyList()
                        awaiting.replaceClarifying(emptySet())
                    }

                    // Not known: nothing is claimed and nothing is blocked any more; the next launch asks again.
                    ConsentRenewalCheck.Unknown -> {
                        renewalState = RenewalState.UNKNOWN
                    }
                }
                publish()
            }
        }
    }

    /** The app is in front (again): the permissions are read, and a return from the settings of the app is judged. */
    fun onResumed() {
        snapshot = permissions.snapshot()
        attempt = LocationFlow.afterResume(snapshot, attempt)
        publish()
    }

    /** The notification screen was answered (the system window came back, granted or not): it never comes back for this account. */
    fun onNotificationsAnswered() {
        // From now on a refusal means "only the settings of the phone" (the system does not tell "never asked" from "refused for good").
        asked.markSystemAsked()
        currentUid()?.let(asked::markShown)
        notificationsHandled = true
        snapshot = permissions.snapshot()
        // SRV-2: "Autoriser" on the pop-in (which now says Miff registers an identifier of the phone) and the phone lets Miff notify: the consent to be notified, per account.
        if (snapshot.notifications) currentUid()?.let(asked::markPushConsent)
        publish()
    }

    /**
     * "Plus tard" on the pop-in (NOTIF-1A): the window of the system is NOT used up, the pop-in never comes back for this account, and ONE calm card is
     * offered in the Feed after the first update (SecondChance). No notification setting is changed: if the person allows later, everything works.
     */
    fun onNotificationsPutOff() {
        currentUid()?.let { uid ->
            asked.markShown(uid)
            asked.setSecondChance(uid, SecondChanceRules.afterLater(asked.secondChance(uid)))
        }
        notificationsHandled = true
        publish()
    }

    /** The system window of the location was answered; [canAskAgain] says whether the system would show it again. */
    fun onLocationDialogAnswered(canAskAgain: Boolean) {
        snapshot = permissions.snapshot()
        attempt = LocationFlow.afterDialog(snapshot, canAskAgain)
        publish()
    }

    /**
     * The request for "all the time" came back (Android 11+: the system opened its own screen of the permission, or nothing). The state is read
     * again. Returns true when the screen must now open the settings of the app itself, because the system opened nothing.
     */
    fun onBackgroundRequestAnswered(outcome: BackgroundRequestOutcome): Boolean {
        snapshot = permissions.snapshot()
        attempt = LocationFlow.afterBackgroundRequest(snapshot, outcome, attempt)
        publish()
        return LocationFlow.mustOpenSettings(snapshot, outcome)
    }

    fun onSettingsOpened() {
        attempt = LocationFlow.settingsOpened(attempt)
        publish()
    }

    /** "Plus tard": only reachable when the build does not block (the screen shows the link only then). */
    fun onLocationPutOff() {
        if (locationBlocking) return
        locationDeferred = true
        publish()
    }

    /** "Pas maintenant": nothing is written; the screen comes back at the next launch of the app. */
    fun onRenewalPutOff() {
        if (renewalBusy) return
        renewalDeferred = true
        renewalMessage = null
        publish()
    }

    fun onRenewalAccepted() {
        val uid = currentUid() ?: return
        if (renewalBusy || renewalState != RenewalState.DUE) return
        renewalBusy = true
        renewalMessage = null
        publish()
        viewModelScope.launch {
            when (val result = renewals.renew(uid, due.map { it.miffId })) {
                MiffWriteResult.Done -> {
                    due = emptyList()
                    renewalState = RenewalState.NOTHING_DUE
                    awaiting.replace(emptySet())
                }

                is MiffWriteResult.Failed -> {
                    renewalMessage = messageFor(result.failure)
                }
            }
            renewalBusy = false
            publish()
        }
    }

    private fun mayOfferClarify(uid: String): Boolean = ClarifyPolicy.mayShowByItself(clarifyAsks.shownCount(uid))

    /** "Plus tard" on the calm card: nothing is written, nothing is paused; it may come back at the next launch (3 times at most), or from Settings. */
    fun onClarifyPutOff() {
        if (clarifyBusy) return
        clarifyDeferred = true
        clarifyAsked = false
        clarifyMessage = null
        publish()
    }

    /** "J'ai compris": the SAME renewal write as the blocking screen (rule R-LIEU-3), for the Miffs under the version 3 text only. */
    fun onClarifyAccepted() {
        val uid = currentUid() ?: return
        if (clarifyBusy || clarifyDue.isEmpty()) return
        clarifyBusy = true
        clarifyMessage = null
        publish()
        viewModelScope.launch {
            when (val result = renewals.renew(uid, clarifyDue.map { it.miffId })) {
                MiffWriteResult.Done -> {
                    clarifyDue = emptyList()
                    clarifyAsked = false
                    awaiting.replaceClarifying(emptySet())
                }

                is MiffWriteResult.Failed -> {
                    clarifyMessage = messageFor(result.failure)
                }
            }
            clarifyBusy = false
            publish()
        }
    }

    fun onLeaveRequested() {
        if (renewalBusy || renewalState != RenewalState.DUE || RenewalUiState(due).leavable == null) return
        leaveAsked = true
        renewalMessage = null
        publish()
    }

    fun onLeaveCancelled() {
        if (renewalBusy) return
        leaveAsked = false
        publish()
    }

    /** "Quitter la Miff" confirmed: the existing lot (nothing written before this tap); once left, nothing is left to agree to. */
    fun onLeaveConfirmed() {
        val uid = currentUid() ?: return
        val miff = RenewalUiState(due).leavable ?: return
        if (renewalBusy || !leaveAsked) return
        renewalBusy = true
        renewalMessage = null
        publish()
        viewModelScope.launch {
            when (val result = leaver(uid, miff.miffId)) {
                MiffWriteResult.Done -> {
                    onLeft(miff.miffId)
                    due = emptyList()
                    renewalState = RenewalState.NOTHING_DUE
                    awaiting.remove(miff.miffId)
                    leaveAsked = false
                }

                is MiffWriteResult.Failed -> {
                    renewalMessage = messageFor(result.failure)
                }
            }
            renewalBusy = false
            publish()
        }
    }

    private fun publish() {
        val built = build()
        mutableState.value = built
        // The card counts as "shown" the moment it really is on the screen, once per launch, and only when it came by itself (not from Settings).
        if (built.screen == GateScreen.CLARIFY && !clarifyAsked && !clarifyCounted) {
            clarifyCounted = true
            currentUid()?.let(clarifyAsks::markShown)
        }
    }

    /** The calm card is on the way: Miffs to clarify, offered by itself or asked for from Settings, and not put off in this launch. */
    private val clarifyShown: Boolean
        get() = clarifyDue.isNotEmpty() && (clarifyOffered || clarifyAsked) && !clarifyDeferred

    private fun build(): GateUiState {
        val screen =
            resolveGate(
                GateInput(
                    snapshot = snapshot,
                    notificationsHandled = notificationsHandled,
                    locationBlocking = locationBlocking,
                    locationDeferred = locationDeferred,
                    renewal = renewalState,
                    renewalDeferred = renewalDeferred,
                    consentBeforeLocation = consentBeforeLocation,
                    clarify = clarifyShown,
                ),
            )
        return GateUiState(
            screen = screen,
            location = LocationFlow.model(snapshot, attempt),
            clarify = if (clarifyDue.isNotEmpty()) ClarifyUiState(clarifyDue, clarifyBusy, clarifyMessage) else null,
            renewal =
                if (renewalState ==
                    RenewalState.DUE
                ) {
                    RenewalUiState(due, renewalBusy, renewalMessage, leaveAsked)
                } else {
                    null
                },
            canPutOffLocation = !locationBlocking,
        )
    }

    /** The state of the permissions as the view model last read it (for the screen: which window to open). */
    val currentSnapshot: PermissionSnapshot get() = snapshot

    companion object {
        /** The longest the "Toujours" screen waits for the look at the consents (2 reads of the server at most for a free account). */
        const val CHECK_TIMEOUT_MS = 8_000L
    }
}
