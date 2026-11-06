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

/** What the information screen shows of the Miffs of the account (names are personal data: nothing here is ever logged). */
class AccountMiffsSummary(
    /** How many Miffs the account JOINED (it leaves them). */
    val joined: Int,
    /** The Miffs it CREATED, with how many other people are in each: they are deleted. */
    val owned: List<OwnedMiff>,
) {
    /** The account is in at least one Miff, created or joined (stale indexes do not count: they are cleaned without a word). */
    val hasMiffs: Boolean get() = joined > 0 || owned.isNotEmpty()

    override fun toString(): String = "AccountMiffsSummary(redacted)"

    companion object {
        val NONE = AccountMiffsSummary(joined = 0, owned = emptyList())
    }
}

/** What the server says the account holds that the deletion of the account must deal with first (the Miffs). */
sealed interface Inventory {
    /** Read on the SERVER, complete: the Miffs joined and created (possibly none). */
    class Loaded(
        val miffs: AccountMiffsSummary,
    ) : Inventory {
        override fun toString(): String = "Loaded(redacted)"
    }

    /** The server could not be reached. */
    data object Offline : Inventory

    /** Any other reason (refused, unclear). */
    data object Failed : Inventory
}

/** Where the procedure stands, for the progress screen: never a percentage, never the detail of the lots. */
sealed interface DeletionStep {
    /** Reading what the account holds: no line yet. */
    data object Checking : DeletionStep

    /** "Miff {index} sur {total}": the Miffs joined first, then the ones created. [longWait]: more than three people to take out. */
    data class Miff(
        val index: Int,
        val total: Int,
        val longWait: Boolean,
    ) : DeletionStep

    /** The Miffs are done: the devices, the session, the profile and the account. */
    data object Profile : DeletionStep
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
     * Miffs keep appearing in the account (created or joined from another phone while this one was deleting): after [AccountDeletion.MAX_PASSES] passes
     * one is still there. A profile or an account is never deleted with a Miff behind it, so the deletion is CANCELLED: the controller erases the marker and
     * puts the app back on track with one calm message. What was deleted before stays deleted (the Miffs that were handled are gone, which is what the
     * person asked for); nothing of the profile was deleted. NOT used for a failed step: that is [Failed] (three tries, then the calm stop with the contact).
     */
    data object MiffsRemain : DeletionRun

    /** Stopped for another reason: trying again is fine. */
    data object Failed : DeletionRun
}

/**
 * The session "un compte, un seul appareil" (SESS-2) of the account that is deleted. [deleteSession] deletes `users/{uid}/session/active` and
 * THROWS when the server did not confirm: the deletion of the account then pauses (resumable), it never goes on without the acknowledgement.
 */
fun interface AccountSession {
    suspend fun deleteSession(uid: String)
}

/** What the controller needs of the procedure (a double in tests). */
interface AccountDeletionProcedure {
    suspend fun inventory(uid: String): Inventory

    /** [onStep] tells where the procedure stands, for the progress screen (called from the thread of the procedure, cheap). */
    suspend fun run(
        uid: String,
        onStep: (DeletionStep) -> Unit = {},
    ): DeletionRun
}

/**
 * The deletion of an account (ticket AUTH-5, section 5.5, steps 2 to 8), with or without Miffs (AUTH-5b), in one procedure.
 *
 * Order, every step IDEMPOTENT and decided from what the SERVER says, never from a counter kept on the phone:
 * 1. the Miffs, read on the server ([AccountMiffs.inventory]): the stale indexes are dropped, then the Miffs JOINED are left (lot "Quitter", no change of
 *    code), then the Miffs CREATED are deleted one after the other (members one by one, places, last lot), strictly in sequence. The `memberships` are
 *    counted again: a Miff that appeared meanwhile (another phone of the account) starts another pass, [MAX_PASSES] at most, then the deletion is
 *    CANCELLED ([DeletionRun.MiffsRemain]) rather than leaving an account without its guard;
 * 2. devices (push tokens): deleted one by one;
 *    then the session "one account, one device" (SESS-2, `users/{uid}/session/active`): its deletion must be CONFIRMED by the server, else the
 *    deletion of the account pauses here (a session left behind a deleted account would be an orphan, and the other phones must see it vanish);
 * 3. final check: `memberships` read again, still empty, else back to [DeletionRun.MiffsRemain];
 * 4. profile `users/{uid}`: the LAST document, so that its presence tells, on the server, that work remains;
 * 5. the queued writes leave (they would be refused without an account), then the authentication account is deleted: LAST.
 *
 * THE DEAD END (decision of the PM, 2026-10-31 and 2026-11-05): the authentication account and the profile are NEVER deleted while a Miff the person created
 * is still there. A step that keeps failing (a refusal, three tries, see the controller) leaves the marker and the account as they are: the person gets the
 * calm stop with the contact and "Réessayer", and the next launch takes it up again. Nothing is transferred (no transfer of ownership exists).
 *
 * A step refused by the server is not always a failure: the account may be gone already (deleted from the console or from another
 * phone, token revoked), which is the very goal. [AuthGateway.checkSession] tells: gone = success (criterion SC28). The caller
 * stops the listeners and the tracking and writes the local marker BEFORE the first call (step 0 and criterion SC15).
 */
class AccountDeletion(
    private val store: AccountDataStore,
    private val gateway: AuthGateway,
    private val cache: CachePurger,
    private val session: AccountSession,
    private val miffs: AccountMiffs,
    private val timeoutMillis: Long = FirestoreMiffRepository.TIMEOUT_MS,
) : AccountDeletionProcedure {
    override suspend fun inventory(uid: String): Inventory =
        when (val read = miffs.inventory(uid)) {
            is MiffsRead.Read -> Inventory.Loaded(AccountMiffsSummary(read.inventory.joined.size, read.inventory.owned))
            is MiffsRead.Failed -> if (read.failure == MiffFailure.OFFLINE) Inventory.Offline else Inventory.Failed
        }

    override suspend fun run(
        uid: String,
        onStep: (DeletionStep) -> Unit,
    ): DeletionRun = runRound(uid, onStep, 1)

    private suspend fun runRound(
        uid: String,
        onStep: (DeletionStep) -> Unit,
        number: Int,
    ): DeletionRun {
        // The marker belongs to ONE account: another one is never deleted by a marker that is not theirs.
        // Nobody signed in: the account may exist or not (forced sign-out, token revoked...): never taken for "deleted".
        val current = gateway.currentUid ?: return DeletionRun.SessionExpired
        if (current != uid) return DeletionRun.Failed

        // 1. The Miffs: joined first, then created, one after the other; again while something is left.
        onStep(DeletionStep.Checking)
        var pass = 0
        while (true) {
            pass++
            val inventory =
                when (val read = miffs.inventory(uid)) {
                    is MiffsRead.Read -> read.inventory
                    is MiffsRead.Failed -> return afterRefusal(read.failure)
                }
            val worked = !inventory.isEmpty
            leaveEverything(uid, inventory, onStep)?.let { return it }
            when (val left = guardedCall(timeoutMillis) { store.membershipCount(uid) }) {
                is Guarded.Value -> {
                    if (left.value == 0) break
                    // Still something: a Miff that appeared meanwhile gets another pass. After the last one, nothing more is guessed: with new Miffs
                    // the deletion is cancelled, with something that cannot be named (no work was found) it is a failure (the calm stop).
                    if (pass >= MAX_PASSES) return if (worked) DeletionRun.MiffsRemain else DeletionRun.Failed
                }

                is Guarded.Failed -> {
                    return afterRefusal(left.failure)
                }
            }
        }
        onStep(DeletionStep.Profile)

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

        // 2 bis (SESS-2). The session of the account, BEFORE the profile; no acknowledgement = no further step (resumable, idempotent).
        when (val done = guardedCall(timeoutMillis) { session.deleteSession(uid) }) {
            is Guarded.Value -> Unit
            is Guarded.Failed -> return afterRefusal(done.failure)
        }

        // 3. Final check (ticket 5.5, step 6): empty, else back to the Miffs (a Miff that appeared meanwhile), a few rounds at most, then the cancellation.
        when (val left = guardedCall(timeoutMillis) { store.membershipCount(uid) }) {
            is Guarded.Value -> {
                val stillThere = left.value > 0
                if (stillThere) {
                    return if (number >= MAX_PASSES) DeletionRun.MiffsRemain else runRound(uid, onStep, number + 1)
                }
            }

            is Guarded.Failed -> {
                return afterRefusal(left.failure)
            }
        }
        // 4. The profile.
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

    /**
     * The Miffs of one pass, in the order of the ticket (section 5.5, steps 3 and 4): the STALE indexes (silently), the Miffs JOINED, then the Miffs CREATED,
     * strictly one after the other (each removal reads the code that the previous one wrote). Null = all done; otherwise how the run ends.
     */
    private suspend fun leaveEverything(
        uid: String,
        inventory: MiffsInventory,
        onStep: (DeletionStep) -> Unit,
    ): DeletionRun? {
        for (ref in inventory.stale) {
            (miffs.dropStale(uid, ref) as? MiffStep.Failed)?.let { return afterRefusal(it.failure) }
        }
        val total = inventory.visibleCount
        val longWait = inventory.removals > LONG_WAIT_REMOVALS
        var index = 0
        for (ref in inventory.joined) {
            onStep(DeletionStep.Miff(++index, total, longWait))
            (miffs.leave(uid, ref) as? MiffStep.Failed)?.let { return afterRefusal(it.failure) }
        }
        for (owned in inventory.owned) {
            onStep(DeletionStep.Miff(++index, total, longWait))
            (miffs.deleteOwned(uid, owned.miffId) as? MiffStep.Failed)?.let { return afterRefusal(it.failure) }
        }
        return null
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

    companion object {
        /** Passes over the Miffs (and rounds, after the final check) while something new keeps showing up: then the deletion is cancelled. */
        const val MAX_PASSES = 3

        /** More than this many people to take out of the Miffs created: the wait is said to be longer (mockup D3). */
        const val LONG_WAIT_REMOVALS = 3
    }
}
