package com.tomessa.miff.data.account

import com.tomessa.miff.data.auth.AuthGateway
import com.tomessa.miff.data.auth.CachePurger
import com.tomessa.miff.data.miff.FirestoreMiffRepository
import com.tomessa.miff.data.miff.Guarded
import com.tomessa.miff.data.miff.guardedCall
import com.tomessa.miff.domain.auth.AuthFailure
import com.tomessa.miff.domain.auth.SessionCheck
import com.tomessa.miff.domain.miff.MiffFailure

/*
 * SECURITY, for this file: nothing that identifies a person (id, e-mail, name) is logged, sent as an event, or put in an exception
 * message here. Every failure leaves this file as a plain value of [DeletionRun] or [Inventory]. NOTHING here is called before the
 * person has proved who they are again (the screens only start the controller after a successful re-authentication).
 */

/** What the server says the account holds that the deletion of the account must deal with first (AUTH-5a: the Miffs). */
sealed interface Inventory {
    /** No index of a Miff: the account can be deleted by this version. */
    data object NoMiffs : Inventory

    /** At least one Miff, created or joined: the deletion with Miffs is AUTH-5b, nothing is touched. */
    data object HasMiffs : Inventory

    /** The server could not be reached. */
    data object Offline : Inventory

    /** Any other reason (refused, unclear). */
    data object Failed : Inventory
}

/** How one run of the procedure ended. Running it again always carries on from the state read on the server. */
sealed interface DeletionRun {
    /**
     * The authentication account is DELETED: by this run, or Firebase says it does not exist any more (`ERROR_USER_NOT_FOUND`). Only that
     * counts: no session, an expired or revoked token are NOT a deletion ([SessionExpired]).
     */
    data object Completed : DeletionRun

    /**
     * The session can no longer be used (nobody is signed in any more, token expired or revoked) while the account may still exist: the
     * marker stays, nothing says "deleted", the person signs in again and the deletion resumes (same account).
     */
    data object SessionExpired : DeletionRun

    /** Stopped for lack of network: nothing is lost, it carries on when the network is back. */
    data object Offline : DeletionRun

    /** Firebase wants a more recent sign-in before deleting the account (the "Dernière vérification"). */
    data object NeedsRecentLogin : DeletionRun

    /**
     * An index of a Miff is still there: this version cannot delete a Miff (AUTH-5b), and a profile or an account is never deleted
     * with a Miff behind it. Nothing of the profile was deleted; at most the push tokens of `devices` (read after the first check, they
     * are written again by the next sign-in on that phone). The controller treats it as a CANCELLATION: marker erased, app back on track.
     * NOTE for AUTH-5b: an account whose only indexes are STALE (Miff deleted, member removed: the index is still there) counts as "has
     * Miffs" here, since [AccountDataStore.membershipCount] counts every document; AUTH-5b cleans those up (`cleanUpLost`) before this check.
     */
    data object MiffsRemain : DeletionRun

    /** Stopped for another reason: trying again is fine. */
    data object Failed : DeletionRun
}

/** What the controller needs of the procedure (a double in tests). */
interface AccountDeletionProcedure {
    suspend fun inventory(uid: String): Inventory

    suspend fun run(uid: String): DeletionRun
}

/**
 * The deletion of an account WITHOUT Miff (ticket AUTH-5, section 5.5, steps 2, 5, 6, 7 and 8; the Miffs are AUTH-5b).
 *
 * Order, every step IDEMPOTENT and decided from what the SERVER says, never from a counter kept on the phone:
 * 1. inventory: `memberships` read from the server; any index = [DeletionRun.MiffsRemain], nothing is touched;
 * 2. devices (push tokens): deleted one by one;
 *    (the session "one account, one device" will be deleted here the day it exists: after the Miffs, before the profile);
 * 3. final check: `memberships` read again, still empty, else back to [DeletionRun.MiffsRemain];
 * 4. profile `users/{uid}`: the LAST document, so that its presence tells, on the server, that work remains;
 * 5. the queued writes leave (they would be refused without an account), then the authentication account is deleted: LAST.
 *
 * A step refused by the server is not always a failure: the account may be gone already (deleted from the console or from another
 * phone, token revoked), which is the very goal. [AuthGateway.checkSession] tells: gone = success (criterion SC28). The caller
 * stops the listeners and writes the local marker BEFORE the first call (step 0 and criterion SC15).
 */
class AccountDeletion(
    private val store: AccountDataStore,
    private val gateway: AuthGateway,
    private val cache: CachePurger,
    private val timeoutMillis: Long = FirestoreMiffRepository.TIMEOUT_MS,
) : AccountDeletionProcedure {
    override suspend fun inventory(uid: String): Inventory =
        when (val read = guardedCall(timeoutMillis) { store.membershipCount(uid) }) {
            is Guarded.Value -> if (read.value == 0) Inventory.NoMiffs else Inventory.HasMiffs
            is Guarded.Failed -> if (read.failure == MiffFailure.OFFLINE) Inventory.Offline else Inventory.Failed
        }

    override suspend fun run(uid: String): DeletionRun {
        // The marker belongs to ONE account: another one is never deleted by a marker that is not theirs.
        // Nobody signed in: the account may exist or not (forced sign-out, token revoked...): never taken for "deleted".
        val current = gateway.currentUid ?: return DeletionRun.SessionExpired
        if (current != uid) return DeletionRun.Failed

        // 1. Nothing is touched while a Miff is there.
        when (val miffs = guardedCall(timeoutMillis) { store.membershipCount(uid) }) {
            is Guarded.Value -> if (miffs.value > 0) return DeletionRun.MiffsRemain
            is Guarded.Failed -> return afterRefusal(miffs.failure)
        }

        // 2. Devices (push tokens), one confirmed deletion each.
        val devices =
            when (val read = guardedCall(timeoutMillis) { store.deviceIds(uid) }) {
                is Guarded.Value -> read.value
                is Guarded.Failed -> return afterRefusal(read.failure)
            }
        for (deviceId in devices) {
            when (val done = guardedCall(timeoutMillis) { store.deleteDevice(uid, deviceId) }) {
                is Guarded.Value -> Unit
                is Guarded.Failed -> return afterRefusal(done.failure)
            }
        }

        // 3. Final check, then 4. the profile.
        when (val miffs = guardedCall(timeoutMillis) { store.membershipCount(uid) }) {
            is Guarded.Value -> if (miffs.value > 0) return DeletionRun.MiffsRemain
            is Guarded.Failed -> return afterRefusal(miffs.failure)
        }
        when (val profile = guardedCall(timeoutMillis) { store.deleteProfile(uid) }) {
            is Guarded.Value -> Unit
            is Guarded.Failed -> return afterRefusal(profile.failure)
        }

        // 5. The purge is recorded BEFORE the account goes: killed in between, the next launch purges before any other access.
        cache.markPurgePending()
        cache.flushPendingWrites()
        // `deleteCurrentAccount` answers "done" when nobody is signed in: that would not be a deletion.
        if (gateway.currentUid != uid) return DeletionRun.SessionExpired
        return when (gateway.deleteCurrentAccount()) {
            null, AuthFailure.ACCOUNT_GONE -> DeletionRun.Completed
            AuthFailure.SESSION_EXPIRED -> DeletionRun.SessionExpired
            AuthFailure.RECENT_LOGIN_REQUIRED -> DeletionRun.NeedsRecentLogin
            AuthFailure.NETWORK -> DeletionRun.Offline
            else -> afterRefusal(MiffFailure.UNKNOWN)
        }
    }

    /** A step failed: no network is a pause, an account that is gone is the success we wanted, anything else is a failure. */
    private suspend fun afterRefusal(failure: MiffFailure): DeletionRun {
        if (failure == MiffFailure.OFFLINE) return DeletionRun.Offline
        return when (gateway.checkSession()) {
            SessionCheck.GONE -> DeletionRun.Completed
            SessionCheck.EXPIRED -> DeletionRun.SessionExpired
            SessionCheck.VALID, SessionCheck.UNKNOWN -> DeletionRun.Failed
        }
    }
}
