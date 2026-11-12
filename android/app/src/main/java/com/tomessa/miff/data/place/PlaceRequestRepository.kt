package com.tomessa.miff.data.place

import com.tomessa.miff.data.miff.EntitlementRead
import com.tomessa.miff.data.miff.Guarded
import com.tomessa.miff.data.miff.MiffRead
import com.tomessa.miff.data.miff.guardedCall
import com.tomessa.miff.domain.miff.MiffFailure
import com.tomessa.miff.domain.place.PaywallReason
import com.tomessa.miff.domain.place.PlaceRequest
import com.tomessa.miff.domain.place.PlaceRequestDraft
import com.tomessa.miff.domain.place.PlaceRequestLimits
import com.tomessa.miff.domain.place.PlaceRequestSlots
import com.tomessa.miff.domain.place.PlaceRequestWrites
import com.tomessa.miff.domain.place.PlaceType
import kotlinx.coroutines.withTimeoutOrNull

/*
 * SECURITY, for this file: the requests (name, address, coordinates) are sensitive. They are never logged, never in an analytics event, never in an
 * exception message. Every failure leaves this file as a [MiffFailure] (no message, no path).
 */

/** What the form of a request needs to know about the person before it opens (everything read from the SERVER: a limit is never decided on a copy). */
class RequestContext(
    /** The name of the Miff the request is for (shown in the form, the Miff is fixed). */
    val miffName: String,
    /** Miff+ (an active entitlement that has not expired): only decides whether the danger zone is offered. The rules decide for real. */
    val premium: Boolean,
    /** The pending requests of the person in this Miff (not expired). */
    val mine: List<PlaceRequest>,
) {
    override fun toString(): String = "RequestContext(redacted)"
}

sealed interface RequestContextLoad {
    class Loaded(
        val context: RequestContext,
    ) : RequestContextLoad {
        override fun toString(): String = "Loaded(redacted)"
    }

    /** The Miff no longer exists, or the person is not a member who may ask (it is theirs, or access was lost). */
    data object Unavailable : RequestContextLoad

    data class Failed(
        val failure: MiffFailure,
    ) : RequestContextLoad
}

/** How the sending of a request ended. [Done] only after the SERVER accepted the lot; [Queued] when the phone kept it for the return of the network. */
sealed interface RequestSubmitResult {
    class Done(
        val request: PlaceRequest,
    ) : RequestSubmitResult {
        override fun toString(): String = "Done(redacted)"
    }

    /** No network: the phone keeps the request and sends it when it can (criterion M11). The rules judge it then. */
    data object Queued : RequestSubmitResult

    /** Nothing was sent: the danger zone is Miff+ (a member who is not Miff+ cannot ask for one). */
    data class NeedsPremium(
        val reason: PaywallReason,
    ) : RequestSubmitResult

    /** Nothing was sent: the person already has 3 pending requests in this Miff (criterion M5). */
    data object MemberLimit : RequestSubmitResult

    /** The server refused: the Miff already has all the pending requests it takes (10), or a rule said no. */
    data object MiffLimit : RequestSubmitResult

    /** The Miff is gone, or the person is no longer in it. */
    data object MiffUnavailable : RequestSubmitResult

    /** The server does not take requests for the moment (its rules refuse even the reading of them): nothing was sent, a calm sentence says so. */
    data object RequestsUnavailable : RequestSubmitResult

    data class Failed(
        val failure: MiffFailure,
    ) : RequestSubmitResult
}

sealed interface RequestWithdrawResult {
    data object Done : RequestWithdrawResult

    data class Failed(
        val failure: MiffFailure,
    ) : RequestWithdrawResult
}

/** What a reading of the requests of the person gave. */
sealed interface MineRead {
    class Loaded(
        val requests: List<PlaceRequest>,
    ) : MineRead {
        override fun toString(): String = "Loaded(redacted)"
    }

    data class Failed(
        val failure: MiffFailure,
    ) : MineRead
}

/** What the screens do with the requests to add a place, as a member (ticket LIEU-REQ-B). */
interface PlaceRequestRepository {
    /** Reads who the person is for the form: Miff+ or not, the Miff, the requests they already have. */
    suspend fun prepare(
        uid: String,
        miffId: String,
    ): RequestContextLoad

    suspend fun submit(
        uid: String,
        miffId: String,
        draft: PlaceRequestDraft,
        context: RequestContext,
        online: Boolean,
    ): RequestSubmitResult

    /** The author takes the request back (criterion M6). */
    suspend fun withdraw(request: PlaceRequest): RequestWithdrawResult

    /** The requests of the person in [miffId], for the list: the server when it answers, else the phone's copy. Expired ones are not given. */
    suspend fun readMine(
        uid: String,
        miffId: String,
    ): MineRead

    /**
     * The person is about to leave [miffId] (criterion L1): their pending requests are taken back, one lot each. Never blocks the departure: a failure
     * is ignored (the creator hides the requests of somebody who is no longer a member and deletes them).
     */
    suspend fun withdrawAllMine(
        uid: String,
        miffId: String,
    )
}

class FirestorePlaceRequestRepository(
    private val store: PlaceRequestStore,
    private val places: PlaceStore,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val timeoutMillis: Long = TIMEOUT_MS,
    /** Is there a network? Leaving a Miff skips the withdrawal of the requests when there is none (the departure itself needs the network anyway). */
    private val online: () -> Boolean = { true },
) : PlaceRequestRepository {
    /**
     * The slots handed to the phone's queue while offline (key `miffId|requestId`, time of the hand-over): a SECOND request made offline right after must not take
     * the same slot (the first would be overwritten by a refused write and lost without a word). A mark lives 10 minutes, or until the server shows the request.
     */
    private val queued = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private fun queuedIds(
        miffId: String,
        uid: String,
    ): Set<String> {
        val now = nowMillis()
        queued.entries.removeIf { now - it.value > QUEUED_MARK_MILLIS }
        return queued.keys
            .filter { it.startsWith("$miffId|$uid" + "_") }
            .map { it.substringAfter('|') }
            .toSet()
    }

    // --- Reading ------------------------------------------------------------------------------------------------

    override suspend fun prepare(
        uid: String,
        miffId: String,
    ): RequestContextLoad {
        // An unknown plan is NOT "free" for the danger zone: a failed read says "try again" instead of locking or opening it on a guess.
        val premium =
            when (val read = guarded { places.readEntitlement(uid) }) {
                is Guarded.Value -> isActive(read.value)
                is Guarded.Failed -> return RequestContextLoad.Failed(read.failure)
            }
        val details =
            when (val read = guarded { places.readMiff(miffId) }) {
                is Guarded.Value -> {
                    val miff = read.value
                    // Only a member who is NOT the creator asks (the creator adds places themselves).
                    if (miff !is MiffRead.Exists || miff.details.ownerUid == uid) return RequestContextLoad.Unavailable
                    miff.details
                }

                is Guarded.Failed -> {
                    return if (read.lostAccess) {
                        RequestContextLoad.Unavailable
                    } else {
                        RequestContextLoad.Failed(read.failure)
                    }
                }
            }
        val mine =
            when (val read = guarded { store.readMine(miffId, uid, fromServer = true) }) {
                is Guarded.Value -> {
                    read.value
                }

                // The rules refuse the read of `placeRequests` (they are not deployed yet, or the Miff changed): the form OPENS all the same. Its slots are only
                // needed to SEND; the sending says calmly if requests are not available (see [RequestSubmitResult.Unavailable]).
                is Guarded.Failed -> {
                    if (read.failure != MiffFailure.REFUSED) return RequestContextLoad.Failed(read.failure)
                    emptyList()
                }
            }
        // An expired request still occupies its slot until it is deleted: the author deletes it here, so that the slot is free again (ticket 5.3).
        mine.forEach { queued.remove("$miffId|${it.id}") }
        val (expired, alive) = mine.partition { it.isExpired(nowMillis()) }
        val stillThere = expired.filter { withdraw(it) !is RequestWithdrawResult.Done }
        return RequestContextLoad.Loaded(RequestContext(details.name, premium, alive + stillThere))
    }

    override suspend fun readMine(
        uid: String,
        miffId: String,
    ): MineRead =
        when (val read = guarded { store.readMine(miffId, uid, fromServer = false) }) {
            is Guarded.Value -> MineRead.Loaded(read.value.filterNot { it.isExpired(nowMillis()) })
            is Guarded.Failed -> MineRead.Failed(read.failure)
        }

    /** Miff+ = an active entitlement that has not expired. No document at all = not Miff+. */
    private fun isActive(read: EntitlementRead?): Boolean {
        val expires = read?.expiresAtMillis ?: return false
        return read.active && expires > nowMillis()
    }

    // --- Writing ------------------------------------------------------------------------------------------------

    override suspend fun submit(
        uid: String,
        miffId: String,
        draft: PlaceRequestDraft,
        context: RequestContext,
        online: Boolean,
    ): RequestSubmitResult {
        if (draft.problem != null) return RequestSubmitResult.Failed(MiffFailure.UNKNOWN)
        // The danger zone is Miff+ for the person who asks (D5): decided BEFORE anything is sent, the rules only repeat it.
        if (draft.type == PlaceType.RISK &&
            !context.premium
        ) {
            return RequestSubmitResult.NeedsPremium(PaywallReason.DANGER)
        }
        // 3 pending requests at most (M5): no write is tried at the 4th. The slots waiting in the phone's queue count too.
        val waiting = queuedIds(miffId, uid)
        val taken = context.mine.map { it.id }.toSet() + waiting
        if (taken.size >= PlaceRequestLimits.PER_MEMBER) return RequestSubmitResult.MemberLimit
        val slot =
            PlaceRequestLimits.SLOTS.firstOrNull { PlaceRequestLimits.idOf(uid, it) !in taken }
                ?: return RequestSubmitResult.MemberLimit
        val lot = PlaceRequestWrites.create(uid, miffId, slot, draft)
        val sent = sentRequest(uid, miffId, slot, draft)
        if (!online) {
            store.enqueue(lot)
            queued["$miffId|${sent.id}"] = nowMillis()
            return RequestSubmitResult.Queued
        }
        val failure =
            when (val outcome = guarded { store.commit(lot) }) {
                is Guarded.Value -> return RequestSubmitResult.Done(sent)
                is Guarded.Failed -> outcome.failure
            }
        return afterFailedSubmit(uid, miffId, sent, draft, failure)
    }

    private fun sentRequest(
        uid: String,
        miffId: String,
        slot: Int,
        draft: PlaceRequestDraft,
    ) = PlaceRequest(
        id = PlaceRequestLimits.idOf(uid, slot),
        miffId = miffId,
        requestedBy = uid,
        name = draft.name.trim(),
        type = draft.type,
        address = draft.address,
        lat = draft.lat,
        lng = draft.lng,
        radiusM = draft.radiusM,
        category = draft.category,
        createdAtMillis = null,
    )

    /**
     * A write that was not confirmed is looked at before the person is told it failed: (1) did it land after all (the answer was lost)? then it is done;
     * (2) is the Miff gone, or no longer theirs to ask in? (3) a refusal of the server on a Miff that is still there: the Miff holds all the requests it takes.
     */
    private suspend fun afterFailedSubmit(
        uid: String,
        miffId: String,
        sent: PlaceRequest,
        draft: PlaceRequestDraft,
        failure: MiffFailure,
    ): RequestSubmitResult {
        if (failure == MiffFailure.OFFLINE || failure == MiffFailure.UNKNOWN) {
            val mine = (guarded { store.readMine(miffId, uid, fromServer = true) } as? Guarded.Value)?.value
            if (mine != null && mine.any { it.id == sent.id }) return RequestSubmitResult.Done(sent)
        }
        if (failure == MiffFailure.REFUSED || failure == MiffFailure.UNKNOWN) {
            val gone =
                when (val read = guarded { places.readMiff(miffId) }) {
                    is Guarded.Value -> read.value.let { it !is MiffRead.Exists || it.details.ownerUid == uid }
                    is Guarded.Failed -> read.lostAccess
                }
            if (gone) return RequestSubmitResult.MiffUnavailable
        }
        if (failure != MiffFailure.REFUSED) return RequestSubmitResult.Failed(failure)
        // A refusal on a Miff that is still there (contract of the backend, section 4): three causes are told apart, the last one is the limit of the Miff.
        // (a) a danger zone and the plan ended since the form opened: the offer, nothing else to do.
        if (draft.type == PlaceType.RISK) {
            val read = guarded { places.readEntitlement(uid) }
            if (read is Guarded.Value &&
                !isActive(read.value)
            ) {
                return RequestSubmitResult.NeedsPremium(PaywallReason.DANGER)
            }
        }
        // (b) a race on the slot (another phone took it): the requests are read again and, if a slot is free, the lot is sent once more.
        val freshRead = guarded { store.readMine(miffId, uid, fromServer = true) }
        // Even reading the person's own requests is refused: it is not the limit of the Miff, the requests are simply not available yet.
        if (freshRead is Guarded.Failed &&
            freshRead.failure == MiffFailure.REFUSED
        ) {
            return RequestSubmitResult.RequestsUnavailable
        }
        val fresh = (freshRead as? Guarded.Value)?.value
        if (fresh != null && fresh.any { it.id == sent.id }) {
            val slot = PlaceRequestSlots.firstFree(uid, fresh) ?: return RequestSubmitResult.MemberLimit
            val again = sentRequest(uid, miffId, slot, draft)
            val outcome = guarded { store.commit(PlaceRequestWrites.create(uid, miffId, slot, draft)) }
            return if (outcome is Guarded.Value) RequestSubmitResult.Done(again) else RequestSubmitResult.MiffLimit
        }
        // (c) all the slots were free: the Miff holds all the requests it takes (10), or the counter drifted too high.
        return RequestSubmitResult.MiffLimit
    }

    override suspend fun withdraw(request: PlaceRequest): RequestWithdrawResult {
        val failure =
            when (val outcome = guarded { store.commit(PlaceRequestWrites.withdraw(request.miffId, request.id)) }) {
                is Guarded.Value -> return RequestWithdrawResult.Done
                is Guarded.Failed -> outcome.failure
            }
        // The counter was reset to 0 by the creator while requests exist: -1 is out of bounds and the rules refuse. Once more with the absolute value 0 (contract 2.2).
        if (failure == MiffFailure.REFUSED) {
            val again =
                guarded { store.commit(PlaceRequestWrites.withdrawWithCounterAtZero(request.miffId, request.id)) }
            if (again is Guarded.Value) return RequestWithdrawResult.Done
        }
        // A lot whose answer was lost, or a request already deleted (by the creator, on another phone): it is gone, which is what was asked.
        if (failure != MiffFailure.NO_PROFILE) {
            val left =
                (
                    guarded {
                        store.readMine(
                            request.miffId,
                            request.requestedBy,
                            fromServer = true,
                        )
                    } as? Guarded.Value
                )?.value
            if (left != null && left.none { it.id == request.id }) return RequestWithdrawResult.Done
        }
        return RequestWithdrawResult.Failed(failure)
    }

    override suspend fun withdrawAllMine(
        uid: String,
        miffId: String,
    ) {
        // No network: the departure will fail on its own; nothing is waited for here (the creator hides and deletes what stays).
        if (!online()) return
        // At most a minute in all, whatever the network does: never a long wait before "Quitter".
        withTimeoutOrNull(LEAVE_BUDGET_MILLIS) {
            val mine =
                (guarded { store.readMine(miffId, uid, fromServer = true) } as? Guarded.Value)?.value
                    ?: return@withTimeoutOrNull
            for (request in mine) withdraw(request)
        }
    }

    private suspend fun <T> guarded(call: suspend () -> T): Guarded<T> = guardedCall(timeoutMillis, call)

    companion object {
        const val TIMEOUT_MS = 20_000L
        const val LEAVE_BUDGET_MILLIS = 60_000L
        const val QUEUED_MARK_MILLIS = 10 * 60_000L
    }
}
