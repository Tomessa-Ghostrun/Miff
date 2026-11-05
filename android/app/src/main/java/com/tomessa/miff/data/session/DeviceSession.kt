package com.tomessa.miff.data.session

import com.tomessa.miff.data.auth.NetworkMonitor
import com.tomessa.miff.domain.session.DeviceIds
import com.tomessa.miff.domain.session.RemoteSession
import com.tomessa.miff.domain.session.SessionJudge
import com.tomessa.miff.domain.session.SessionVerdict
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/*
 * "Un compte, un seul appareil" (SESS-2, ticket docs/tickets/session-appareil-unique.md; contract: backend/data-model.md, section of the same name).
 *
 * SECURITY / PRIVACY, for this file:
 * - A measure of CONSISTENCY, not of security (a modified client could copy the identifier): its goal is that an old phone left at home does not keep
 *   saying "all is well" for a person who left with another phone.
 * - The identifier is random, drawn at EVERY sign-in, kept in a private file that is excluded from backups, and NEVER logged, sent as an event or put in a
 *   crash report. Nothing here logs anything.
 * - A forced sign-out NEVER writes nor deletes the session (no loop, and the session now belongs to the other phone); a voluntary one deletes it only
 *   when it carries OUR identifier (one transaction); offline, it deletes nothing.
 * - Nothing is written from the phone that was replaced: every status and position write asks [ActiveDevice.clearance] first.
 */

/** The phone's own copy of the identifier of its session (a private file, not backed up). */
interface DeviceIdStore {
    /** The identifier, or null when there is none (or when what is stored does not have the shape of an identifier). */
    fun read(): String?

    fun save(id: String)

    fun clear()
}

/** `users/{uid}/session/active`, as the app uses it. Every call reaches the SERVER (a transaction) or fails: nothing is queued. */
interface SessionDocuments {
    /** Replaces the session with `{deviceId, startedAt = server time}` (the last connection wins). Throws if the server did not confirm. */
    suspend fun write(
        uid: String,
        deviceId: String,
    )

    /** What the SERVER says (never the cache). Throws when it cannot be reached. */
    suspend fun read(uid: String): RemoteSession

    /** One transaction: reads the session and deletes it only when it carries [deviceId]. True when it was deleted. Throws when the server cannot be reached. */
    suspend fun deleteIfMine(
        uid: String,
        deviceId: String,
    ): Boolean

    /** The snapshots that come from the SERVER, from now on (the ones from the local cache say nothing: offline, nobody is signed out). */
    fun observe(uid: String): Flow<RemoteSession>
}

/** The answer to "may this phone write a status or a position?". */
sealed interface Clearance {
    /** The session is this phone's, as the server said recently: [deviceId] goes in the write. */
    class Cleared(
        val deviceId: String,
    ) : Clearance {
        override fun toString(): String = "Cleared(redacted)"
    }

    /** Another phone holds the session (or it vanished): this phone is being signed out, nothing may be written. */
    data object Replaced : Clearance

    /** The server could not be asked (offline, slow) or nobody is signed in: nothing is written on an assumption; the caller keeps what waits. */
    data object Unverified : Clearance
}

/** What the writers of statuses and positions ask of the session. */
interface ActiveDevice {
    /**
     * Is this phone the one the account is used on? Verified against the server before the first write of a series (and at the return of the network,
     * [recheck] = true); a recent verification, or a live listener, is reused so that a write does not cost a read.
     */
    suspend fun clearance(recheck: Boolean): Clearance

    /**
     * A status or position write was REFUSED: that alone does not prove a replacement (consent, sharing stopped...). The session is read from the server;
     * if it is not ours any more the forced sign-out is requested. True when this phone was replaced.
     */
    suspend fun afterRefusal(): Boolean
}

enum class Ensured { VERIFIED, REPLACED, UNREACHABLE }

class DeviceSession(
    private val ids: DeviceIdStore,
    private val documents: SessionDocuments,
    private val network: NetworkMonitor,
    private val currentUid: () -> String?,
    private val scope: CoroutineScope,
    /** The session is no longer ours: asks for the forced sign-out. MUST NOT wait for it (it stops the very engine that may be calling us). */
    private val onReplaced: () -> Unit,
    private val newId: () -> String = { DeviceIds.generate() },
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val timeoutMillis: Long = TIMEOUT_MS,
    private val retryPausesMillis: List<Long> = RETRY_PAUSES_MS,
) : ActiveDevice {
    private val lock = Mutex()

    @Volatile
    private var verifiedAtMillis = NEVER

    /** The live listener runs (so a replacement would be noticed at once). */
    @Volatile
    private var listening = false

    /** Set when the session was found to be someone else's: nothing is written until a new sign-in ([begin]). */
    @Volatile
    private var revoked = false

    private var watchJob: Job? = null

    // --- Sign-in --------------------------------------------------------------------------------------------------------------

    /**
     * Sign-in (e-mail, Google, after the creation of the account): draws a NEW identifier and writes the session, whatever was there before (the last
     * connection wins). The identifier is kept locally only once the server has confirmed. False when it could not be confirmed (the caller then does not
     * keep the person signed in: a phone with no session would write nothing and be signed out at its next launch).
     */
    suspend fun begin(uid: String): Boolean =
        lock.withLock {
            val id = newId()
            if (!DeviceIds.isValid(id) || currentUid() != uid) return@withLock false
            var written = false
            var tries = 0
            while (!written && tries < BEGIN_TRIES) {
                tries++
                written = attempt { documents.write(uid, id) }.isSuccess
            }
            if (!written) {
                ids.clear()
                return@withLock false
            }
            ids.save(id)
            revoked = false
            verifiedAtMillis = nowMillis()
            true
        }

    // --- Launch, return of the network, refused write -------------------------------------------------------------------------

    /**
     * Looks at the session on the SERVER (rule of [SessionJudge]). Ours: verified. None at all and no local identifier (installed before the feature):
     * writes one. Someone else's, or gone: this phone is replaced, [onReplaced] is called, and nothing is written.
     */
    suspend fun ensure(uid: String): Ensured =
        lock.withLock {
            if (revoked) return@withLock Ensured.REPLACED
            if (currentUid() != uid) return@withLock Ensured.UNREACHABLE
            val local = ids.read()
            val remote = attempt { documents.read(uid) }.getOrElse { return@withLock Ensured.UNREACHABLE }
            when (SessionJudge.judge(local, remote)) {
                SessionVerdict.MINE -> {
                    verifiedAtMillis = nowMillis()
                    Ensured.VERIFIED
                }

                SessionVerdict.WRITE_NEW -> {
                    val id = newId()
                    if (DeviceIds.isValid(id) && attempt { documents.write(uid, id) }.isSuccess) {
                        ids.save(id)
                        verifiedAtMillis = nowMillis()
                        Ensured.VERIFIED
                    } else {
                        Ensured.UNREACHABLE
                    }
                }

                SessionVerdict.REPLACED -> {
                    revoke()
                    Ensured.REPLACED
                }
            }
        }

    override suspend fun clearance(recheck: Boolean): Clearance {
        if (revoked) return Clearance.Replaced
        val uid = currentUid() ?: return Clearance.Unverified
        if (!recheck && isFresh()) ids.read()?.let { return Clearance.Cleared(it) }
        return when (ensure(uid)) {
            Ensured.VERIFIED -> ids.read()?.let { Clearance.Cleared(it) } ?: Clearance.Unverified
            Ensured.REPLACED -> Clearance.Replaced
            Ensured.UNREACHABLE -> Clearance.Unverified
        }
    }

    override suspend fun afterRefusal(): Boolean {
        if (revoked) return true
        val uid = currentUid() ?: return false
        val at = verifiedAtMillis
        // The session was verified a moment ago (typically just before this write): the refusal has another cause, and no read is spent on it.
        if (at != NEVER && nowMillis() - at < REFUSAL_RECHECK_MILLIS) return false
        return ensure(uid) == Ensured.REPLACED
    }

    private fun isFresh(): Boolean {
        val at = verifiedAtMillis
        return at != NEVER && (listening || nowMillis() - at < FRESH_MILLIS)
    }

    private fun verifiedJustNow(): Boolean {
        val at = verifiedAtMillis
        return at != NEVER && nowMillis() - at < REFUSAL_RECHECK_MILLIS
    }

    private fun revoke() {
        revoked = true
        verifiedAtMillis = NEVER
        ids.clear()
        onReplaced()
    }

    // --- Listening --------------------------------------------------------------------------------------------------------------

    /**
     * Keeps an eye on the session while the person is signed in: as soon as the network is there (at the start, and again after every cut) the session is
     * verified, THEN listened to. A replacement is therefore noticed live (app open) or at the first return of the network (the limit validated by the
     * PM: an offline phone cannot be signed out). A server that does not answer is asked again three times at most (15 s, 30 s, 60 s), never in a loop.
     */
    fun watch(uid: String) {
        stopWatching()
        // The session was just verified or written by the caller (sign-in, launch): the first look is not repeated at once.
        var first = true
        watchJob =
            scope.launch {
                network.onlineChanges().distinctUntilChanged().collectLatest { online ->
                    val skipLook = first && verifiedJustNow()
                    first = false
                    if (!online) return@collectLatest
                    var pauses = 0
                    while (!skipLook && ensure(uid) == Ensured.UNREACHABLE) {
                        val pause = retryPausesMillis.getOrNull(pauses++) ?: return@collectLatest
                        delay(pause)
                    }
                    if (revoked) return@collectLatest
                    listening = true
                    try {
                        documents.observe(uid).collect { remote -> judgeLive(remote) }
                    } finally {
                        listening = false
                    }
                }
            }
    }

    private suspend fun judgeLive(remote: RemoteSession) {
        lock.withLock {
            if (revoked) return@withLock
            // A "no session" with an identifier of ours = deleted from elsewhere (deletion of the account) = replaced, like another identifier.
            when (SessionJudge.judge(ids.read(), remote)) {
                SessionVerdict.MINE -> verifiedAtMillis = nowMillis()
                SessionVerdict.WRITE_NEW -> Unit
                SessionVerdict.REPLACED -> revoke()
            }
        }
    }

    fun stopWatching() {
        watchJob?.cancel()
        watchJob = null
        listening = false
    }

    // --- Ends of session -----------------------------------------------------------------------------------------------------

    /**
     * Voluntary sign-out (and deletion of an account, which must call it before deleting the account): stops listening, deletes the session ONLY if it
     * carries our identifier (one transaction), then forgets the identifier. Offline or on any failure nothing is deleted (the next sign-in overwrites
     * it; a blind deletion could erase the session of the phone that took over).
     */
    suspend fun release(uid: String?) {
        stopWatching()
        lock.withLock {
            val id = ids.read()
            // Offline there is nothing to try (a transaction needs the server): no waiting for a timeout at the sign-out.
            if (uid != null && id != null && !revoked && network.isOnline()) attempt { documents.deleteIfMine(uid, id) }
            ids.clear()
            verifiedAtMillis = NEVER
            revoked = false
        }
    }

    /**
     * Forced sign-out: forgets the identifier and stops listening. It writes NOTHING and deletes NOTHING (the session belongs to the other phone now).
     * Writes stay refused ([Clearance.Replaced]) until the next sign-in.
     */
    fun forget() {
        stopWatching()
        ids.clear()
        verifiedAtMillis = NEVER
        revoked = true
    }

    private suspend fun <T> attempt(block: suspend () -> T): Result<T> =
        try {
            Result.success(withTimeout(timeoutMillis) { block() })
        } catch (e: TimeoutCancellationException) {
            Result.failure(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    companion object {
        private const val NEVER = Long.MIN_VALUE
        const val TIMEOUT_MS = 8_000L
        private const val BEGIN_TRIES = 2

        /** Without the live listener (a receiver woken in a cold process), a verification is reused for this long. */
        const val FRESH_MILLIS = 10L * 60L * 1000L

        /** A refused write checks the session again unless it was verified less than this long ago. */
        const val REFUSAL_RECHECK_MILLIS = 5_000L
        val RETRY_PAUSES_MS = listOf(15_000L, 30_000L, 60_000L)
    }
}
