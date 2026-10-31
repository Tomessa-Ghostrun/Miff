package com.tomessa.miff.data.miff

import com.tomessa.miff.domain.miff.DocWrite
import com.tomessa.miff.domain.miff.JoinCode
import com.tomessa.miff.domain.miff.LocationAccess
import com.tomessa.miff.domain.miff.MembershipRef
import com.tomessa.miff.domain.miff.MiffFailure
import com.tomessa.miff.domain.miff.MiffName
import com.tomessa.miff.domain.miff.MiffObservation
import com.tomessa.miff.domain.miff.MiffSummary
import com.tomessa.miff.domain.miff.MiffWrites
import com.tomessa.miff.domain.miff.interpretMiffError
import com.tomessa.miff.domain.miff.isAccessLostError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withTimeout

/*
 * SECURITY, for this file: codes, names and profiles are secrets or personal data. They are never logged, never in an
 * analytics event, never in an exception message. Every failure leaves this file as a MiffFailure (no message, no code).
 */

/** How a write of ticket Miff ended. [Done] only after the SERVER has accepted the lot. */
sealed interface MiffWriteResult {
    data object Done : MiffWriteResult

    data class Failed(
        val failure: MiffFailure,
    ) : MiffWriteResult
}

/** The first step of "Créer": can the Miff be written, and under which key of the index. */
sealed interface CreatePreparation {
    /** [firstMiff]: true when the person has no other Miff yet (analytics parameter `is_first_miff`). */
    data class Ready(
        /** A Premium account keys its index by the id of the Miff; a free account has the one slot "free". */
        val premium: Boolean,
        val firstMiff: Boolean,
    ) : CreatePreparation

    /** A free account that already has a Miff: the Premium ticket decides what the person sees (nothing invented here). */
    data object NeedsPremium : CreatePreparation

    data class Failed(
        val failure: MiffFailure,
    ) : CreatePreparation
}

sealed interface CreateResult {
    data class Created(
        val miffId: String,
        val joinCode: String,
        val name: String,
        val firstMiff: Boolean,
    ) : CreateResult {
        override fun toString(): String = "Created(redacted)"
    }

    data object NeedsPremium : CreateResult

    data class Failed(
        val failure: MiffFailure,
    ) : CreateResult
}

/** What the code typed by the person leads to (ticket 5.4, steps 3 to 5). */
sealed interface JoinPreparation {
    class Ready(
        val miffId: String,
        val miffName: String,
        val code: String,
        val premium: Boolean,
        val firstMiff: Boolean,
    ) : JoinPreparation {
        override fun toString(): String = "Ready(redacted)"
    }

    /** One message whatever the cause (wrong, renewed, deleted): never "your old code was replaced" (ticket 5.4, step 3). */
    data object NotFound : JoinPreparation

    class AlreadyMember(
        val miffId: String,
        val miffName: String,
    ) : JoinPreparation {
        override fun toString(): String = "AlreadyMember(redacted)"
    }

    data object NeedsPremium : JoinPreparation

    data class Failed(
        val failure: MiffFailure,
    ) : JoinPreparation
}

sealed interface JoinResult {
    data class Joined(
        val miffId: String,
        val firstMiff: Boolean,
    ) : JoinResult

    /** The code was renewed or the Miff deleted between the reading and the confirmation: same message as "not found". */
    data object NotFound : JoinResult

    data object NeedsPremium : JoinResult

    data class Failed(
        val failure: MiffFailure,
    ) : JoinResult
}

/** The Miffs of the person, as the Feed and the Profile show them. */
sealed interface MyMiffsLoad {
    class Loaded(
        val miffs: List<MiffSummary>,
        /** Miffs the server says the person has no access to any more: to clean up (the index is still there). */
        val lost: List<LostMiff>,
        val premium: Boolean,
        /** True when the answer comes from the phone's cache because the server could not be reached. */
        val fromCache: Boolean,
    ) : MyMiffsLoad {
        override fun toString(): String = "Loaded(redacted)"
    }

    data object Unavailable : MyMiffsLoad
}

class LostMiff(
    val ref: MembershipRef,
    /** The name, if the phone still knew it (it keeps the message human: "Tu n'as plus accès à la Miff « X »"). */
    val name: String?,
) {
    override fun toString(): String = "LostMiff(redacted)"
}

/**
 * Everything the Miff tickets do with Firestore, as the screens need it (ticket Miff 5.4, 5.7, 5.13): the order of the checks
 * to enter, the lots, the clean-up after a loss of access, the live view of a Miff.
 */
interface MiffRepository {
    suspend fun prepareCreate(uid: String): CreatePreparation

    suspend fun create(
        uid: String,
        name: String,
        preparation: CreatePreparation.Ready,
    ): CreateResult

    suspend fun prepareJoin(
        uid: String,
        typedCode: String,
    ): JoinPreparation

    suspend fun join(
        uid: String,
        preparation: JoinPreparation.Ready,
    ): JoinResult

    suspend fun rename(
        miffId: String,
        code: String,
        name: String,
    ): MiffWriteResult

    suspend fun setSharing(
        miffId: String,
        uid: String,
        enabled: Boolean,
    ): MiffWriteResult

    suspend fun myMiffs(uid: String): MyMiffsLoad

    /** The "Quitter" lot, else the index alone (ticket 5.7). True when the index is gone. */
    suspend fun cleanUpLost(
        uid: String,
        lost: LostMiff,
    ): Boolean

    /** The name and code of a Miff (the "ready" screen reads them again after the creation). Null when not readable. */
    suspend fun details(miffId: String): com.tomessa.miff.domain.miff.MiffDetails?

    fun observe(miffId: String): Flow<MiffObservation>
}

class FirestoreMiffRepository(
    private val store: MiffStore,
    private val observer: MiffObserver,
    private val location: LocationAccessProbe,
    private val newMiffId: () -> String,
    private val newCode: () -> String = { JoinCode.generate() },
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val timeoutMillis: Long = TIMEOUT_MS,
) : MiffRepository {
    // --- Create ---------------------------------------------------------------------------------------------

    override suspend fun prepareCreate(uid: String): CreatePreparation {
        val state =
            when (val loaded = readState(uid)) {
                is StateRead.Value -> loaded.state
                is StateRead.Failed -> return CreatePreparation.Failed(loaded.failure)
            }
        // A stale index (removed, or Miff deleted) is cleaned BEFORE the write, or the rules would refuse it for nothing.
        state.lost.forEach { cleanUpLost(uid, it) }
        if (!state.premium && state.valid.isNotEmpty()) return CreatePreparation.NeedsPremium
        return CreatePreparation.Ready(premium = state.premium, firstMiff = state.valid.isEmpty())
    }

    override suspend fun create(
        uid: String,
        name: String,
        preparation: CreatePreparation.Ready,
    ): CreateResult {
        if (!MiffName.isValid(name)) return CreateResult.Failed(MiffFailure.UNKNOWN)
        val code =
            when (val free = freeCode()) {
                is CodeDraw.Free -> free.code
                is CodeDraw.Failed -> return CreateResult.Failed(free.failure)
            }
        val miffId = newMiffId()
        val access = location.current()
        val result =
            write {
                store.commitWithProfile(uid) { profile ->
                    MiffWrites.create(
                        uid = uid,
                        miffId = miffId,
                        code = code,
                        name = name,
                        profile = profile,
                        membershipKey = keyFor(preparation.premium, miffId),
                        access = access,
                    )
                }
            }
        return when (result) {
            MiffWriteResult.Done -> {
                CreateResult.Created(miffId, code, MiffName.stored(name), preparation.firstMiff)
            }

            is MiffWriteResult.Failed -> {
                // A lot that left but whose answer was lost must not be shown as a failure (ticket H3, one tap = one Miff).
                if (result.failure.mayHaveLanded() && landed(miffId)) {
                    CreateResult.Created(miffId, code, MiffName.stored(name), preparation.firstMiff)
                } else {
                    CreateResult.Failed(result.failure)
                }
            }
        }
    }

    /** A code nobody uses: one read per try, three tries (ticket C7). A collision is about 10^-13: never visible. */
    private suspend fun freeCode(): CodeDraw {
        repeat(MAX_CODE_DRAWS) {
            val code = newCode()
            when (val read = guarded { store.readJoinCode(code) }) {
                is Guarded.Value -> if (read.value == null) return CodeDraw.Free(code)
                is Guarded.Failed -> return CodeDraw.Failed(read.failure)
            }
        }
        return CodeDraw.Failed(MiffFailure.UNKNOWN)
    }

    private sealed interface CodeDraw {
        class Free(
            val code: String,
        ) : CodeDraw

        class Failed(
            val failure: MiffFailure,
        ) : CodeDraw
    }

    // --- Join -----------------------------------------------------------------------------------------------

    override suspend fun prepareJoin(
        uid: String,
        typedCode: String,
    ): JoinPreparation {
        val input = JoinCode.analyze(typedCode)
        if (!input.isComplete) return JoinPreparation.NotFound
        val code = input.code
        // Step 3: one read of the code. "Not found" is one message whatever the cause.
        val found =
            when (val read = guarded { store.readJoinCode(code) }) {
                is Guarded.Value -> read.value
                is Guarded.Failed -> return JoinPreparation.Failed(read.failure)
            }
        val miffId = (found?.get("miffId") as? String)?.takeIf(FirestoreMiffStore::isMiffId)
        val miffName = found?.get("miffName") as? String
        if (found == null || miffId == null || miffName == null) return JoinPreparation.NotFound
        // Step 4: a stale index is cleaned before anything is written. Step 5: already in this Miff?
        val state =
            when (val loaded = readState(uid)) {
                is StateRead.Value -> loaded.state
                is StateRead.Failed -> return JoinPreparation.Failed(loaded.failure)
            }
        state.lost.forEach { cleanUpLost(uid, it) }
        // Compared on the FIELD miffId of the indexes, never on the id of the document ("free").
        if (state.valid.any { it.miffId == miffId }) return JoinPreparation.AlreadyMember(miffId, miffName)
        if (!state.premium && state.valid.isNotEmpty()) return JoinPreparation.NeedsPremium
        return JoinPreparation.Ready(
            miffId = miffId,
            miffName = miffName,
            code = code,
            premium = state.premium,
            firstMiff = state.valid.isEmpty(),
        )
    }

    override suspend fun join(
        uid: String,
        preparation: JoinPreparation.Ready,
    ): JoinResult {
        val access = location.current()
        val result =
            write {
                store.commitWithProfile(uid) { profile ->
                    MiffWrites.join(
                        uid = uid,
                        miffId = preparation.miffId,
                        code = preparation.code,
                        profile = profile,
                        membershipKey = keyFor(preparation.premium, preparation.miffId),
                        access = access,
                    )
                }
            }
        return when (result) {
            MiffWriteResult.Done -> {
                JoinResult.Joined(preparation.miffId, preparation.firstMiff)
            }

            is MiffWriteResult.Failed -> {
                when {
                    result.failure.mayHaveLanded() && landed(preparation.miffId) -> {
                        JoinResult.Joined(preparation.miffId, preparation.firstMiff)
                    }

                    // The rules said no: the code was renewed, or the Miff deleted, since it was read. Same message.
                    result.failure == MiffFailure.REFUSED -> {
                        JoinResult.NotFound
                    }

                    else -> {
                        JoinResult.Failed(result.failure)
                    }
                }
            }
        }
    }

    // --- Rename, sharing -------------------------------------------------------------------------------------

    override suspend fun rename(
        miffId: String,
        code: String,
        name: String,
    ): MiffWriteResult {
        if (!MiffName.isValid(name) || !JoinCode.isValid(code)) return MiffWriteResult.Failed(MiffFailure.UNKNOWN)
        return write { store.commit(MiffWrites.rename(miffId, code, name)) }
    }

    override suspend fun setSharing(
        miffId: String,
        uid: String,
        enabled: Boolean,
    ): MiffWriteResult {
        val writes: List<DocWrite> =
            if (enabled) {
                MiffWrites.resumeSharing(
                    miffId,
                    uid,
                    location.current(),
                )
            } else {
                MiffWrites.stopSharing(miffId, uid)
            }
        return write { store.commit(writes) }
    }

    // --- The Miffs of the person, loss of access -------------------------------------------------------------

    override suspend fun myMiffs(uid: String): MyMiffsLoad {
        val state =
            when (val loaded = readState(uid, withNames = true)) {
                is StateRead.Value -> loaded.state
                is StateRead.Failed -> return MyMiffsLoad.Unavailable
            }
        return MyMiffsLoad.Loaded(
            miffs = state.summaries,
            lost = state.lost,
            premium = state.premium,
            fromCache = state.fromCache,
        )
    }

    override suspend fun cleanUpLost(
        uid: String,
        lost: LostMiff,
    ): Boolean {
        // Clean-up needs the server: without it nothing is touched (the next start will do it).
        val leave = write { store.commit(MiffWrites.leave(lost.ref.miffId, uid, lost.ref.key)) }
        if (leave == MiffWriteResult.Done) return true
        // Removed: the card is already gone, the lot is refused. Only the index is left, and the rules allow deleting it now.
        val indexOnly = write { store.commit(MiffWrites.dropIndex(uid, lost.ref.key)) }
        return indexOnly == MiffWriteResult.Done
    }

    override suspend fun details(miffId: String) =
        (guarded { store.readMiff(miffId, fromServer = false) } as? Guarded.Value)
            ?.let { (it.value as? MiffRead.Exists)?.details }

    override fun observe(miffId: String): Flow<MiffObservation> = observer.observe(miffId)

    // --- Internals --------------------------------------------------------------------------------------------

    /**
     * What the person holds, checked against the server: each index is read, then the Miff it points to. A Miff the server
     * calls "missing" or refuses is LOST, but only when the server is otherwise reachable (a refusal of App Check would refuse
     * everything, and nobody is told they were removed because of that).
     */
    private suspend fun readState(
        uid: String,
        withNames: Boolean = false,
    ): StateRead {
        val premium = premium(uid)
        var fromCache = false
        val refs =
            when (val server = guarded { store.readMemberships(uid, fromServer = true) }) {
                is Guarded.Value -> {
                    server.value
                }

                is Guarded.Failed -> {
                    // Offline: the cache can still tell what to show, but nothing is created, joined or cleaned from it.
                    if (!withNames) return StateRead.Failed(server.failure)
                    when (val cached = guarded { store.readMemberships(uid, fromServer = false) }) {
                        is Guarded.Value -> {
                            fromCache = true
                            cached.value
                        }

                        is Guarded.Failed -> {
                            return StateRead.Failed(server.failure)
                        }
                    }
                }
            }
        val valid = mutableListOf<MembershipRef>()
        val summaries = mutableListOf<MiffSummary>()
        val lost = mutableListOf<LostMiff>()
        for (ref in refs) {
            when (val read = guarded { store.readMiff(ref.miffId, fromServer = !fromCache) }) {
                is Guarded.Value -> {
                    when (val miff = read.value) {
                        is MiffRead.Exists -> {
                            valid += ref
                            summaries += MiffSummary(ref.miffId, miff.details.name)
                        }

                        MiffRead.Missing -> {
                            lost += LostMiff(ref, cachedName(ref.miffId))
                        }
                    }
                }

                is Guarded.Failed -> {
                    val definitelyLost = read.lostAccess && !fromCache && store.reachable(uid)
                    if (definitelyLost) {
                        lost += LostMiff(ref, cachedName(ref.miffId))
                    } else if (read.failure == MiffFailure.OFFLINE || read.lostAccess) {
                        // No conclusion: it stays, with no name until the server answers (never "removed" on a doubt).
                        valid += ref
                    } else {
                        return StateRead.Failed(read.failure)
                    }
                }
            }
        }
        return StateRead.Value(State(valid, summaries, lost, premium, fromCache))
    }

    private suspend fun cachedName(miffId: String): String? =
        ((guarded { store.readMiff(miffId, fromServer = false) } as? Guarded.Value)?.value as? MiffRead.Exists)
            ?.details
            ?.name

    private suspend fun MiffStore.reachable(uid: String): Boolean =
        (guarded { serverReachable(uid) } as? Guarded.Value)?.value == true

    private class State(
        val valid: List<MembershipRef>,
        val summaries: List<MiffSummary>,
        val lost: List<LostMiff>,
        val premium: Boolean,
        val fromCache: Boolean,
    )

    private sealed interface StateRead {
        class Value(
            val state: State,
        ) : StateRead

        class Failed(
            val failure: MiffFailure,
        ) : StateRead
    }

    /** Premium = an active entitlement that has not expired (the rules decide for real: this only chooses the key). */
    private suspend fun premium(uid: String): Boolean {
        val read = (guarded { store.readEntitlement(uid) } as? Guarded.Value)?.value ?: return false
        val expires = read.expiresAtMillis ?: return false
        return read.active && expires > nowMillis()
    }

    private fun keyFor(
        premium: Boolean,
        miffId: String,
    ): String = if (premium) miffId else FREE_KEY

    /** A failure that may hide a lot that did leave (no answer): the server is asked before the person is told it failed. */
    private fun MiffFailure.mayHaveLanded(): Boolean = this == MiffFailure.OFFLINE || this == MiffFailure.UNKNOWN

    private suspend fun landed(miffId: String): Boolean =
        ((guarded { store.readMiff(miffId, fromServer = true) } as? Guarded.Value)?.value) is MiffRead.Exists

    private suspend fun write(action: suspend () -> Unit): MiffWriteResult =
        when (val outcome = guarded(action)) {
            is Guarded.Value -> MiffWriteResult.Done
            is Guarded.Failed -> MiffWriteResult.Failed(outcome.failure)
        }

    private sealed interface Guarded<out T> {
        class Value<T>(
            val value: T,
        ) : Guarded<T>

        class Failed(
            val failure: MiffFailure,
            /** The server refused the access (rules): the only answer that can mean "removed". */
            val lostAccess: Boolean,
        ) : Guarded<Nothing>
    }

    private suspend fun <T> guarded(call: suspend () -> T): Guarded<T> =
        try {
            Guarded.Value(withTimeout(timeoutMillis) { call() })
        } catch (e: TimeoutCancellationException) {
            Guarded.Failed(MiffFailure.OFFLINE, lostAccess = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: MissingProfileException) {
            Guarded.Failed(MiffFailure.NO_PROFILE, lostAccess = false)
        } catch (e: Exception) {
            Guarded.Failed(interpretMiffError(e), lostAccess = isAccessLostError(e))
        }

    companion object {
        const val TIMEOUT_MS = 20_000L
        const val MAX_CODE_DRAWS = 3
        const val FREE_KEY = "free"
    }
}
