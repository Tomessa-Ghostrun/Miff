package com.tomessa.miff.data.account

import com.tomessa.miff.data.miff.Guarded
import com.tomessa.miff.data.miff.MiffDeleter
import com.tomessa.miff.data.miff.MiffDeletionResult
import com.tomessa.miff.data.miff.MiffRead
import com.tomessa.miff.data.miff.MiffStore
import com.tomessa.miff.data.miff.guardedCall
import com.tomessa.miff.domain.miff.MembershipRef
import com.tomessa.miff.domain.miff.MiffFailure
import com.tomessa.miff.domain.miff.MiffWrites

/*
 * SECURITY, for this file (AUTH-5b): the names of the Miffs, the ids and the members are personal data. They pass through here and go nowhere else (no log,
 * no analytics event, no message); every failure leaves this file as a [MiffFailure]. Every document touched is the account's OWN card, status, photo,
 * last position and index (the lots "Quitter"), or a Miff the account CREATED (the deletion of a Miff, which is the existing procedure of MIFF-2).
 */

/** A Miff the account created, as the information screen lists it: its name and how many OTHER people are in it. */
class OwnedMiff(
    val miffId: String,
    val name: String,
    val otherMembers: Int,
) {
    override fun toString(): String = "OwnedMiff(redacted)"
}

/**
 * What the account holds, read on the SERVER (never the cache): the Miffs it joined, the Miffs it created, and the STALE indexes (the Miff was deleted or the
 * person was removed: only the index is left).
 */
class MiffsInventory(
    val joined: List<MembershipRef>,
    val owned: List<OwnedMiff>,
    val stale: List<MembershipRef>,
) {
    val isEmpty: Boolean get() = joined.isEmpty() && owned.isEmpty() && stale.isEmpty()

    /** The Miffs the person knows about (stale indexes are cleaned without a word). */
    val visibleCount: Int get() = joined.size + owned.size

    /** The people that the deletion of the created Miffs takes out, one lot each: decides whether the wait is said to be long. */
    val removals: Int get() = owned.sumOf { it.otherMembers }

    override fun toString(): String = "MiffsInventory(redacted)"
}

sealed interface MiffsRead {
    class Read(
        val inventory: MiffsInventory,
    ) : MiffsRead {
        override fun toString(): String = "Read(redacted)"
    }

    data class Failed(
        val failure: MiffFailure,
    ) : MiffsRead
}

/** How one Miff of the deletion ended: done (on the server, acknowledged), or the one reason it did not. */
sealed interface MiffStep {
    data object Done : MiffStep

    data class Failed(
        val failure: MiffFailure,
    ) : MiffStep
}

/** What the deletion of the account asks of the Miffs (a double in tests). Every call reaches the SERVER and is acknowledged, or fails. */
interface AccountMiffs {
    suspend fun inventory(uid: String): MiffsRead

    /** A STALE index: its Miff is gone or the person was removed. Silent, never a message. */
    suspend fun dropStale(
        uid: String,
        ref: MembershipRef,
    ): MiffStep

    /** The lot "Quitter" of a Miff the account JOINED (no change of code: a voluntary exit, decision of the PM). */
    suspend fun leave(
        uid: String,
        ref: MembershipRef,
    ): MiffStep

    /** The deletion of a Miff the account CREATED: the existing procedure (members one by one, places, last lot), idempotent and resumable. */
    suspend fun deleteOwned(
        uid: String,
        miffId: String,
    ): MiffStep
}

/**
 * The deletion of the account, for the Miffs (ticket AUTH-5, sections 5.5 steps 2 to 4 and 6.1). The lots are the ones of [MiffWrites] (4 or 5 writes for
 * "Quitter", at most 4 lectures of the rules), and the creator's Miffs go through [MiffDeleter] (MIFF-2, already merged: 7 writes per removal, 18 of the 20 reads of
 * the rules at worst; places 5 per lot; one last lot of 8, 13 reads): NOTHING is added to those lots and no removal is mixed with the last lot.
 *
 * It does not use `MiffRepository.leave` / `cleanUpLost` on purpose: they ask the detection to look at the Miffs again (the deletion has stopped it) and
 * `cleanUpLost` only says yes or no, whereas here the reason of a failure decides between a pause (no network) and a failure (a refusal).
 */
class StoreAccountMiffs(
    private val store: MiffStore,
    private val deleter: MiffDeleter,
    private val timeoutMillis: Long = com.tomessa.miff.data.miff.FirestoreMiffRepository.TIMEOUT_MS,
) : AccountMiffs {
    override suspend fun inventory(uid: String): MiffsRead {
        val refs =
            when (val read = guardedCall(timeoutMillis) { store.readMemberships(uid, fromServer = true) }) {
                is Guarded.Value -> read.value
                is Guarded.Failed -> return MiffsRead.Failed(read.failure)
            }
        val joined = mutableListOf<MembershipRef>()
        val owned = mutableListOf<OwnedMiff>()
        val stale = mutableListOf<MembershipRef>()
        var reachable: Boolean? = null
        val seen = mutableSetOf<String>()
        for (ref in refs) {
            // Two indexes of the same Miff (never expected) are one Miff: the second would only find it gone.
            if (!seen.add(ref.miffId)) {
                stale += ref
                continue
            }
            when (val read = guardedCall(timeoutMillis) { store.readMiff(ref.miffId, fromServer = true) }) {
                is Guarded.Value -> {
                    when (val miff = read.value) {
                        MiffRead.Missing -> {
                            stale += ref
                        }

                        is MiffRead.Exists -> {
                            if (miff.details.ownerUid != uid) {
                                joined += ref
                            } else {
                                val members =
                                    when (val ids = guardedCall(timeoutMillis) { store.readMemberIds(ref.miffId) }) {
                                        is Guarded.Value -> ids.value
                                        is Guarded.Failed -> return MiffsRead.Failed(ids.failure)
                                    }
                                owned += OwnedMiff(ref.miffId, miff.details.name, members.count { it != uid })
                            }
                        }
                    }
                }

                is Guarded.Failed -> {
                    // A refusal means "removed" only when the server is otherwise reachable (a refusal of App Check would refuse everything).
                    if (read.lostAccess) {
                        if (reachable == null) {
                            // The question itself did not get through (no network): a pause, not a refusal (and no failure more to count).
                            when (val probe = guardedCall(timeoutMillis) { store.serverReachable(uid) }) {
                                is Guarded.Value -> reachable = probe.value
                                is Guarded.Failed -> return MiffsRead.Failed(probe.failure)
                            }
                        }
                        if (reachable == true) stale += ref else return MiffsRead.Failed(read.failure)
                    } else {
                        return MiffsRead.Failed(read.failure)
                    }
                }
            }
        }
        return MiffsRead.Read(MiffsInventory(joined, owned, stale))
    }

    override suspend fun dropStale(
        uid: String,
        ref: MembershipRef,
    ): MiffStep = leaveOrDropIndex(uid, ref)

    override suspend fun leave(
        uid: String,
        ref: MembershipRef,
    ): MiffStep = leaveOrDropIndex(uid, ref)

    /**
     * The lot "Quitter"; if the server refuses it (the card is already gone: the creator removed the person, or is deleting the Miff), the index alone
     * (the rules allow it once the card is gone). Nobody is told: the person is on their way out.
     */
    private suspend fun leaveOrDropIndex(
        uid: String,
        ref: MembershipRef,
    ): MiffStep {
        val leave = guardedCall(timeoutMillis) { store.commit(MiffWrites.leave(ref.miffId, uid, ref.key)) }
        if (leave is Guarded.Value) return MiffStep.Done
        if (leave is Guarded.Failed && leave.failure == MiffFailure.OFFLINE) return MiffStep.Failed(MiffFailure.OFFLINE)
        val index = guardedCall(timeoutMillis) { store.commit(MiffWrites.dropIndex(ref.miffId, uid, ref.key)) }
        return when (index) {
            is Guarded.Value -> MiffStep.Done

            // Both refused: it is the first answer that tells why (a lot that is refused for a reason other than "already gone").
            is Guarded.Failed -> MiffStep.Failed(index.failure)
        }
    }

    override suspend fun deleteOwned(
        uid: String,
        miffId: String,
    ): MiffStep =
        when (val result = deleter.run(uid, miffId)) {
            MiffDeletionResult.Deleted -> MiffStep.Done

            // Nobody else deletes a Miff for its creator: if the Miff is not the account's any more, nothing is done and the run stops (never guessed).
            MiffDeletionResult.NotOwner -> MiffStep.Failed(MiffFailure.UNKNOWN)

            is MiffDeletionResult.Paused -> MiffStep.Failed(result.failure)
        }
}
