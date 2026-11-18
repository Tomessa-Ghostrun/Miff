package com.tomessa.miff.data.alert

import com.tomessa.miff.data.auth.NetworkMonitor
import com.tomessa.miff.data.session.ActiveDevice
import com.tomessa.miff.data.session.Clearance
import com.tomessa.miff.domain.alert.AlertFailure
import com.tomessa.miff.domain.alert.AlertRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/*
 * MY alert, from the author's side (ticket docs/tickets/alerte-danger.md, section 6, the state machine). ONE alert at a time (D14), tied to ONE Miff (the Miff of origin, D9).
 *
 * The truth is the document of the server; the phone keeps a POINTER ([AlertLocalStore]: the id of the Miff, the phase, three moments) so that the alert survives a relaunch and the
 * app listens to the right Miff while the Feed shows another one.
 *
 * What it guarantees (each point has a unit test):
 * - NOTHING leaves before the 5 seconds were held (the screen calls [trigger] at that instant, never before);
 * - an alert that cannot leave (no network, no answer) is kept WAITING by the app, visibly, and tried again when the network returns; after 15 minutes it is only sent once the person
 *   confirms again ([SendStep.NEEDS_CONFIRMATION]): an alert sent hours late would worry the circle for nothing;
 * - an end that cannot leave keeps the alert ACTIVE ("Fin en attente d'envoi"): the alert is never considered over locally until the server accepted it (otherwise the circle
 *   would stay alerted while the person thinks it is over);
 * - a second alert is never started while one is in progress, in any Miff (D14);
 * - one measure of the position is asked ONCE when the alert starts ([onSending]), never in a loop, and the alert leaves even when it fails (the position is a bonus);
 * - at 7 days the alert simply disappears: no write, no notification, no reminder (D19);
 * - nothing is logged, nothing is sent as an event: this class never writes a name, a position or an identifier anywhere but to the server's alert document.
 */

enum class SendStep {
    SENDING,
    WAITING_NETWORK,
    NEEDS_CONFIRMATION,

    /** The server said no and nothing explains it (a clock far off, rules not up to date...): "Réessayer". */
    REFUSED,

    /** The daily limit of 3 alerts is reached (decision of the PM): no retry is offered, the emergency numbers come first. */
    QUOTA,
}

enum class EndStep { NONE, ENDING, WAITING_NETWORK }

/** The state of MY alert. */
sealed interface MyAlert {
    /** The Miff of origin, or null when there is no alert. */
    val miffId: String?

    data object None : MyAlert {
        override val miffId: String? = null
    }

    /** From the 5 seconds to the acknowledgement of the server. */
    data class Sending(
        override val miffId: String,
        val armedAtMillis: Long,
        val step: SendStep,
    ) : MyAlert

    data class Active(
        override val miffId: String,
        val startedAtMillis: Long,
        val expiresAtMillis: Long,
        val end: EndStep = EndStep.NONE,
    ) : MyAlert
}

/** What the screens say once, outside their drawing. */
sealed interface AlertEvent {
    /** The end was accepted by the server: "Alerte terminée…" (a calm line, once). */
    data class Ended(
        val miffId: String,
    ) : AlertEvent

    /** The end was refused (not a network problem): "La fin de l'alerte n'est pas partie…". */
    data object EndNotSent : AlertEvent

    /** The alert could not start because of what the server says about me and that Miff (sharing stopped, no longer a member): the screen of the call says it calmly. */
    data class NotSent(
        val failure: AlertFailure,
    ) : AlertEvent
}

sealed interface TriggerResult {
    data object Started : TriggerResult

    /** An alert is already in progress (in any Miff): the calm sentence "Une alerte est déjà en cours pour la Miff {nom}". */
    data class AlreadyActive(
        val miffId: String,
    ) : TriggerResult

    data object NotSignedIn : TriggerResult
}

class AlertController(
    private val store: AlertStore,
    private val device: ActiveDevice,
    private val network: NetworkMonitor,
    private val local: AlertLocalStore,
    private val currentUid: () -> String?,
    private val scope: CoroutineScope,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    /** ONE measure of the position, asked when the alert starts (never awaited, never repeated); the detection publishes it under its own gates. */
    private val onSending: () -> Unit = {},
    private val sendTimeoutMillis: Long = AlertRules.SEND_TIMEOUT_MILLIS,
    private val retryPausesMillis: List<Long> = DEFAULT_RETRY_PAUSES_MILLIS,
) {
    private val mutableState = MutableStateFlow<MyAlert>(MyAlert.None)
    val state: StateFlow<MyAlert> = mutableState.asStateFlow()

    private val mutableEvents = MutableSharedFlow<AlertEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<AlertEvent> = mutableEvents.asSharedFlow()

    private var job: Job? = null
    private var verifyJob: Job? = null

    /** Picks up what the pointer says (a relaunch of the app): an alert on its way goes on, an active one comes back. Idempotent. */
    fun restore() {
        if (mutableState.value !is MyAlert.None) return
        val pointer = local.pointer() ?: return
        val now = nowMillis()
        when (pointer.phase) {
            PointerPhase.SENDING -> {
                mutableState.value = MyAlert.Sending(pointer.miffId, pointer.armedAtMillis, SendStep.WAITING_NETWORK)
                launchSend(pointer.miffId, pointer.armedAtMillis, pointer.staleConfirmed)
            }

            PointerPhase.ABANDONED -> {
                // Closed by the person, but the server has not yet confirmed that nothing is alive there.
                launchVerifyAbandoned(pointer.miffId)
            }

            PointerPhase.ACTIVE -> {
                val expires = pointer.expiresAtMillis
                val started = pointer.startedAtMillis
                if (expires == null || started == null || expires <= now) {
                    local.clearPointer()
                } else {
                    mutableState.value = MyAlert.Active(pointer.miffId, started, expires)
                }
            }
        }
    }

    /** The 5 seconds were held in the Miff [miffId] (the one the Feed shows): the alert leaves. */
    fun trigger(miffId: String): TriggerResult {
        if (currentUid() == null) return TriggerResult.NotSignedIn
        val current = mutableState.value
        // An alert that is only waiting for a decision (refused, the daily limit, older than 15 minutes) does not forbid a new one: it is abandoned, and the new start adopts the old
        // one if it did land. One that is on its way or in progress does (D14).
        val stuck =
            (current as? MyAlert.Sending)?.step in setOf(SendStep.REFUSED, SendStep.QUOTA, SendStep.NEEDS_CONFIRMATION)
        if (!stuck) current.miffId?.let { return TriggerResult.AlreadyActive(it) }
        verifyJob?.cancel()
        val now = nowMillis()
        local.save(AlertPointer(miffId, PointerPhase.SENDING, now))
        mutableState.value = MyAlert.Sending(miffId, now, SendStep.SENDING)
        launchSend(miffId, now, confirmed = false)
        try {
            onSending()
        } catch (e: Exception) {
            // The position is a bonus: the alert never waits for it, never fails because of it.
        }
        return TriggerResult.Started
    }

    /** "Réessayer" after a refusal (not after the daily limit: it would be refused again). */
    fun retry() {
        val current = mutableState.value as? MyAlert.Sending ?: return
        if (current.step == SendStep.SENDING || current.step == SendStep.QUOTA) return
        launchSend(current.miffId, current.armedAtMillis, local.pointer()?.staleConfirmed == true)
    }

    /** "Envoyer" on the question "Veux-tu toujours envoyer ton alerte ?". */
    fun confirmStale() {
        val current = mutableState.value as? MyAlert.Sending ?: return
        if (current.step != SendStep.NEEDS_CONFIRMATION) return
        local.pointer()?.let {
            local.save(
                AlertPointer(
                    it.miffId,
                    it.phase,
                    it.armedAtMillis,
                    it.startedAtMillis,
                    it.expiresAtMillis,
                    staleConfirmed = true,
                ),
            )
        }
        launchSend(current.miffId, current.armedAtMillis, confirmed = true)
    }

    /**
     * "Annuler" / the cross of the banner: an alert that is waiting (no network, refused, the daily limit, older than 15 min) is ABANDONED. Not while a write is under way (it could
     * still land). The person is free at once (nothing is shown, a new alert may start, no credit is spent or given back), but the local pointer is only erased once the SERVER has
     * said that no alert of mine is in progress in that Miff (review of #275, m1): a start that timed out may have landed; if it did, the alert becomes ACTIVE here (the banner and the
     * end take over). Offline, the check waits for the network.
     */
    fun cancelPending() {
        val current = mutableState.value as? MyAlert.Sending ?: return
        if (current.step == SendStep.SENDING) return
        job?.cancel()
        job = null
        local.save(AlertPointer(current.miffId, PointerPhase.ABANDONED, current.armedAtMillis))
        mutableState.value = MyAlert.None
        launchVerifyAbandoned(current.miffId)
    }

    private fun launchVerifyAbandoned(miffId: String) {
        verifyJob?.cancel()
        verifyJob =
            scope.launch {
                var attempt = 0
                while (attempt < MAX_VERIFY_ATTEMPTS) {
                    val uid = currentUid() ?: return@launch
                    if (!network.isOnline()) {
                        network.onlineChanges().first { it }
                        continue
                    }
                    when (val read = store.readMine(uid, miffId)) {
                        is MineRead.Value -> {
                            // A new alert may have started meanwhile: it owns the pointer now.
                            if (mutableState.value !is MyAlert.None) return@launch
                            val doc = read.doc
                            val now = nowMillis()
                            if (doc != null && doc.isLive(now)) {
                                activate(
                                    miffId,
                                    doc.startedAtMillis ?: now,
                                    doc.expiresAtMillis,
                                    armedAt =
                                        doc.startedAtMillis ?: now,
                                )
                            } else {
                                local.clearPointer()
                            }
                            return@launch
                        }

                        MineRead.Unavailable -> {
                            delay(pauseFor(attempt++))
                        }
                    }
                }
            }
    }

    /** The end of the alert (5 s held on the banner, or the confirmation of the accessibility action). */
    fun end() {
        val current = mutableState.value as? MyAlert.Active ?: return
        if (current.end == EndStep.ENDING) return
        if (current.expiresAtMillis <= nowMillis()) {
            // Already past its date: nothing to write (D19), the alert just disappears.
            drop()
            return
        }
        mutableState.value = current.copy(end = EndStep.ENDING)
        job?.cancel()
        job = scope.launch { endLoop(current.miffId) }
    }

    /** At each minute and at the return to the front: an alert past its date disappears (nothing written, nothing notified, no reminder). */
    fun tick() {
        val current = mutableState.value as? MyAlert.Active ?: return
        if (current.expiresAtMillis <= nowMillis()) drop()
    }

    /**
     * The Miffs of the person are known: an alert whose Miff is no longer one of them (left, removed, deleted) is gone with it. The server deleted the document in the same lot as
     * the departure, the removal or the deletion (nothing to write here).
     */
    fun onMiffsKnown(
        miffIds: Collection<String>,
        authoritative: Boolean = true,
    ) {
        // A list read from the cache of the phone, or a transient one, never ends an alert (review of #275, m8): only a list the server confirmed does.
        if (!authoritative) return
        val miffId = mutableState.value.miffId ?: return
        if (miffIds.isNotEmpty() && miffId !in miffIds) drop()
    }

    /** MY sharing in [miffId] was stopped, or I left it: the document went in the same lot (the rules require it), so the alert is gone. */
    fun onAlertRemoved(miffId: String) {
        if (mutableState.value.miffId == miffId) drop()
    }

    /**
     * The return to the front while an alert is ACTIVE: ONE reading of my document from the server (1 read) says whether it is still in progress. Gone, ended or past its date:
     * the banner goes. Not reachable: nothing changes (never a banner removed on a doubt).
     */
    suspend fun verifyActive() {
        val current = mutableState.value as? MyAlert.Active ?: return
        if (current.end != EndStep.NONE) return
        val uid = currentUid() ?: return
        if (!network.isOnline()) return
        val read = store.readMine(uid, current.miffId)
        val again = mutableState.value as? MyAlert.Active ?: return
        if (again.miffId != current.miffId || again.end != EndStep.NONE) return
        if (read is MineRead.Value) {
            val doc = read.doc
            if (doc == null || !doc.isLive(nowMillis())) {
                drop()
            } else if (doc.expiresAtMillis != again.expiresAtMillis) {
                activate(
                    again.miffId,
                    doc.startedAtMillis ?: again.startedAtMillis,
                    doc.expiresAtMillis,
                    armedAt = again.startedAtMillis,
                )
            }
        }
    }

    /**
     * A NEW phone (session unique): my alert of each Miff is read ONCE from the server (1 read per Miff, once per account on this phone) and an alert in progress is ADOPTED: the
     * banner, the end. Nothing is read again at the next launches (the pointer holds it), and nothing when an alert is already known here.
     */
    suspend fun adoptFromServer(miffIds: List<String>) {
        val uid = currentUid() ?: return
        if (local.adoptedFor() == uid) return
        if (mutableState.value !is MyAlert.None) {
            local.markAdoptedFor(uid)
            return
        }
        var complete = true
        for (miffId in miffIds) {
            when (val read = store.readMine(uid, miffId)) {
                is MineRead.Value -> {
                    val doc = read.doc
                    val now = nowMillis()
                    if (doc != null && doc.isLive(now) && mutableState.value is MyAlert.None) {
                        activate(
                            miffId,
                            doc.startedAtMillis ?: now,
                            doc.expiresAtMillis,
                            armedAt =
                                doc.startedAtMillis ?: now,
                        )
                        local.markAdoptedFor(uid)
                        return
                    }
                }

                MineRead.Unavailable -> {
                    complete = false
                }
            }
        }
        if (complete) local.markAdoptedFor(uid)
    }

    // ---- the work --------------------------------------------------------------------------------------------------------------

    private fun launchSend(
        miffId: String,
        armedAt: Long,
        confirmed: Boolean,
    ) {
        job?.cancel()
        job = scope.launch { sendLoop(miffId, armedAt, confirmed) }
    }

    private suspend fun sendLoop(
        miffId: String,
        armedAt: Long,
        confirmed: Boolean,
    ) {
        var attempt = 0
        while (true) {
            val uid = currentUid() ?: return
            if (!confirmed && nowMillis() - armedAt > AlertRules.STALE_SEND_MILLIS) {
                setStep(miffId, armedAt, SendStep.NEEDS_CONFIRMATION)
                return
            }
            if (!network.isOnline()) {
                setStep(miffId, armedAt, SendStep.WAITING_NETWORK)
                network.onlineChanges().first { it }
                continue
            }
            setStep(miffId, armedAt, SendStep.SENDING)
            val deviceId =
                when (val clearance = device.clearance(recheck = true)) {
                    is Clearance.Cleared -> {
                        clearance.deviceId
                    }

                    Clearance.Replaced -> {
                        // This phone is being signed out: nothing is written on it, the pointer goes with the account.
                        drop()
                        return
                    }

                    Clearance.Unverified -> {
                        setStep(miffId, armedAt, SendStep.WAITING_NETWORK)
                        delay(pauseFor(attempt++))
                        continue
                    }
                }
            val now = nowMillis()
            val result =
                withTimeoutOrNull(sendTimeoutMillis) { store.start(uid, miffId, deviceId, now) }
                    ?: StartResult.Failed(AlertFailure.Offline)
            when (result) {
                is StartResult.Started -> {
                    activate(miffId, result.startedAtMillis, result.expiresAtMillis, armedAt)
                    return
                }

                is StartResult.Adopted -> {
                    activate(miffId, result.doc.startedAtMillis ?: now, result.doc.expiresAtMillis, armedAt)
                    return
                }

                is StartResult.TooSoon -> {
                    // The previous alert ended less than a minute ago: the rules would refuse. The app waits the few seconds left, quietly, and the person sees "Envoi…".
                    delay(result.waitMillis)
                }

                is StartResult.Failed -> {
                    when (result.failure) {
                        AlertFailure.Offline, AlertFailure.Replaced -> {
                            setStep(miffId, armedAt, SendStep.WAITING_NETWORK)
                            delay(pauseFor(attempt++))
                        }

                        AlertFailure.SharingStopped, AlertFailure.NotMember -> {
                            drop()
                            mutableEvents.tryEmit(AlertEvent.NotSent(result.failure))
                            return
                        }

                        AlertFailure.QuotaReached -> {
                            // Read before writing: the three starts of the last 24 hours are used. Told calmly, without reproach; the person is never left without a way out (numbers first).
                            setStep(miffId, armedAt, SendStep.QUOTA)
                            return
                        }

                        AlertFailure.Refused -> {
                            // A refusal alone does not prove a replacement of the phone (clock, rules): the session is read, and only then is the person told, calmly.
                            if (device.afterRefusal()) {
                                drop()
                            } else {
                                setStep(miffId, armedAt, SendStep.REFUSED)
                            }
                            return
                        }
                    }
                }
            }
        }
    }

    private suspend fun endLoop(miffId: String) {
        var attempt = 0
        while (true) {
            val uid = currentUid() ?: return
            if (!network.isOnline()) {
                setEnd(miffId, EndStep.WAITING_NETWORK)
                network.onlineChanges().first { it }
                continue
            }
            setEnd(miffId, EndStep.ENDING)
            val deviceId =
                when (val clearance = device.clearance(recheck = true)) {
                    is Clearance.Cleared -> {
                        clearance.deviceId
                    }

                    Clearance.Replaced -> {
                        drop()
                        return
                    }

                    Clearance.Unverified -> {
                        setEnd(miffId, EndStep.WAITING_NETWORK)
                        delay(pauseFor(attempt++))
                        continue
                    }
                }
            val result =
                withTimeoutOrNull(sendTimeoutMillis) { store.end(uid, miffId, deviceId, nowMillis()) }
                    ?: EndResult.Failed(AlertFailure.Offline)
            when (result) {
                EndResult.Ended -> {
                    drop()
                    mutableEvents.tryEmit(AlertEvent.Ended(miffId))
                    return
                }

                is EndResult.Failed -> {
                    if (result.failure == AlertFailure.Offline) {
                        setEnd(miffId, EndStep.WAITING_NETWORK)
                        delay(pauseFor(attempt++))
                    } else {
                        if (device.afterRefusal()) {
                            drop()
                        } else {
                            setEnd(miffId, EndStep.NONE)
                            mutableEvents.tryEmit(AlertEvent.EndNotSent)
                        }
                        return
                    }
                }
            }
        }
    }

    private fun pauseFor(attempt: Int): Long = retryPausesMillis.getOrNull(attempt) ?: retryPausesMillis.last()

    private fun setStep(
        miffId: String,
        armedAt: Long,
        step: SendStep,
    ) {
        mutableState.value = MyAlert.Sending(miffId, armedAt, step)
    }

    private fun setEnd(
        miffId: String,
        step: EndStep,
    ) {
        val current = mutableState.value as? MyAlert.Active ?: return
        if (current.miffId == miffId) mutableState.value = current.copy(end = step)
    }

    private fun activate(
        miffId: String,
        startedAt: Long,
        expiresAt: Long,
        armedAt: Long,
    ) {
        local.save(AlertPointer(miffId, PointerPhase.ACTIVE, armedAt, startedAt, expiresAt))
        mutableState.value = MyAlert.Active(miffId, startedAt, expiresAt)
    }

    private fun drop() {
        job?.cancel()
        job = null
        local.clearPointer()
        mutableState.value = MyAlert.None
    }

    /** Sign-out or end of the session: nothing of the alert stays in memory for the next account (the pointer is erased by [AlertLocalStore.clearAccount]). */
    fun onSessionEnded() {
        job?.cancel()
        job = null
        verifyJob?.cancel()
        verifyJob = null
        mutableState.value = MyAlert.None
    }

    companion object {
        /** The pauses between two attempts when the network is there but the server does not answer: bounded, never a tight loop (the 15 minutes end it anyway). */
        val DEFAULT_RETRY_PAUSES_MILLIS = listOf(5_000L, 15_000L, 30_000L, 60_000L, 120_000L)

        /** The check that follows an abandoned alert tries this many times (the pointer stays, and the next opening of the app checks again). */
        const val MAX_VERIFY_ATTEMPTS = 8
    }
}
