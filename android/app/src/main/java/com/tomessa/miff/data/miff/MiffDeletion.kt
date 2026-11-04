package com.tomessa.miff.data.miff

import com.tomessa.miff.domain.miff.MiffFailure
import com.tomessa.miff.domain.miff.MiffWrites

/*
 * SECURITY, for this file: ids, names and codes pass through here and go nowhere else (no log, no analytics event, no message).
 * Every failure leaves this file as a MiffFailure.
 */

/** How the deletion of a Miff ended. */
sealed interface MiffDeletionResult {
    /** The Miff is gone (deleted by this run, or by an earlier one whose answer was lost). */
    data object Deleted : MiffDeletionResult

    /** The person is not the creator of this Miff: nothing was done (only the creator deletes a Miff). */
    data object NotOwner : MiffDeletionResult

    /** Stopped before the end (no network, server said no): nothing is lost, running it again carries on from where it stopped. */
    data class Paused(
        val failure: MiffFailure,
    ) : MiffDeletionResult
}

/** What the coordinator needs of a deletion (a double in tests). */
fun interface MiffDeleter {
    suspend fun run(
        uid: String,
        miffId: String,
    ): MiffDeletionResult
}

/**
 * Deleting a Miff, with NO screen (ticket MIFF-2, S2, S4, S8): called by the Miff screen, by the launch (resuming), and later by
 * the deletion of an account (AUTH-5) and by the end of a subscription (Premium ticket).
 *
 * The order is the one the rules need (backend/data-model.md section 8): (1) every other member is removed, one lot each, the code
 * being renewed each time; (2) the places, 5 per lot at most; (3) ONE last lot: the Miff, its tombstone, the code and the creator's
 * own status, photo, card and index. It is IDEMPOTENT and RESUMABLE: it always starts by reading the server (who is still there,
 * which places are left, does the Miff still exist), so it can be run again after any interruption (app killed, network cut,
 * answer lost) and it carries on, or recognises that the job is done. Every step is a transaction confirmed by the server: nothing
 * waits in a queue to leave later by itself (ticket R4).
 */
class MiffDeletion(
    private val store: MiffStore,
    private val repository: MiffRepository,
    private val timeoutMillis: Long = FirestoreMiffRepository.TIMEOUT_MS,
) : MiffDeleter {
    override suspend fun run(
        uid: String,
        miffId: String,
    ): MiffDeletionResult {
        // The Miff, from the SERVER. Refused or missing (and the server reachable) = it is already gone.
        val details =
            when (val read = guardedCall(timeoutMillis) { store.readMiff(miffId, fromServer = true) }) {
                is Guarded.Value -> {
                    when (val miff = read.value) {
                        is MiffRead.Exists -> miff.details
                        MiffRead.Missing -> return alreadyGone(uid, miffId)
                    }
                }

                is Guarded.Failed -> {
                    return if (read.lostAccess && isReachable(uid)) {
                        alreadyGone(uid, miffId)
                    } else {
                        MiffDeletionResult.Paused(read.failure)
                    }
                }
            }
        if (details.ownerUid != uid) return MiffDeletionResult.NotOwner

        // (1) The other members, one lot each. The list is read again after each pass: someone who came in meanwhile is removed too.
        var pass = 0
        while (true) {
            val others =
                when (val read = guardedCall(timeoutMillis) { store.readMemberIds(miffId) }) {
                    is Guarded.Value -> read.value.filter { it != uid }
                    is Guarded.Failed -> return MiffDeletionResult.Paused(read.failure)
                }
            if (others.isEmpty()) break
            if (++pass > MAX_MEMBER_PASSES) return MiffDeletionResult.Paused(MiffFailure.UNKNOWN)
            for (target in others) {
                when (val removed = repository.removeMember(miffId, target)) {
                    is CodeChangeResult.Changed -> Unit
                    is CodeChangeResult.Failed -> return MiffDeletionResult.Paused(removed.failure)
                }
            }
        }

        // (2) The places, 5 per lot.
        val places =
            when (val read = guardedCall(timeoutMillis) { store.readPlaceIds(miffId) }) {
                is Guarded.Value -> read.value
                is Guarded.Failed -> return MiffDeletionResult.Paused(read.failure)
            }
        for (chunk in places.chunked(MiffWrites.PLACES_PER_LOT)) {
            val deleted = guardedCall(timeoutMillis) { store.commit(MiffWrites.deletePlaces(miffId, chunk)) }
            if (deleted is Guarded.Failed) return MiffDeletionResult.Paused(deleted.failure)
        }

        // (3) The last lot. The key of the index is found by its FIELD miffId.
        val key =
            when (val read = guardedCall(timeoutMillis) { store.readMemberships(uid, fromServer = true) }) {
                is Guarded.Value -> read.value.firstOrNull { it.miffId == miffId }?.key
                is Guarded.Failed -> return MiffDeletionResult.Paused(read.failure)
            }
        val last =
            guardedCall(timeoutMillis) {
                store.commitWithMiff(miffId) { view ->
                    MiffWrites.finalDelete(miffId, uid, view.details.joinCode, view.codeDocumentExists, key)
                }
            }
        return when (last) {
            is Guarded.Value -> {
                MiffDeletionResult.Deleted
            }

            is Guarded.Failed -> {
                // The answer may have been lost although the lot left: if the server now refuses the Miff, it is deleted.
                val check = guardedCall(timeoutMillis) { store.readMiff(miffId, fromServer = true) }
                if (check is Guarded.Failed && check.lostAccess && isReachable(uid)) {
                    MiffDeletionResult.Deleted
                } else {
                    MiffDeletionResult.Paused(last.failure)
                }
            }
        }
    }

    /**
     * The Miff cannot be read any more, and the server answers: nothing is left to delete. If the index of the creator is still
     * there (an earlier run could not write it), it is dropped (the rules allow it now that the card is gone).
     */
    private suspend fun alreadyGone(
        uid: String,
        miffId: String,
    ): MiffDeletionResult {
        val refs =
            when (val read = guardedCall(timeoutMillis) { store.readMemberships(uid, fromServer = true) }) {
                is Guarded.Value -> read.value
                is Guarded.Failed -> return MiffDeletionResult.Paused(read.failure)
            }
        refs.filter { it.miffId == miffId }.forEach { ref ->
            val dropped = guardedCall(timeoutMillis) { store.commit(MiffWrites.dropIndex(uid, ref.key)) }
            if (dropped is Guarded.Failed) return MiffDeletionResult.Paused(dropped.failure)
        }
        return MiffDeletionResult.Deleted
    }

    private suspend fun isReachable(uid: String): Boolean =
        (guardedCall(timeoutMillis) { store.serverReachable(uid) } as? Guarded.Value)?.value == true

    private companion object {
        /** A Miff with people still arriving cannot be emptied for ever: after this many passes the deletion stops and says so. */
        const val MAX_MEMBER_PASSES = 4
    }
}
