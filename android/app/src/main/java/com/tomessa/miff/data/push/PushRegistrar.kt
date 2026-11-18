package com.tomessa.miff.data.push

import com.tomessa.miff.domain.push.DeviceDocumentId
import com.tomessa.miff.domain.push.RegistrationAction
import com.tomessa.miff.domain.push.RegistrationRecord
import com.tomessa.miff.domain.push.RegistrationRules
import com.tomessa.miff.domain.push.RegistrationWish
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/*
 * SRV-2: keeps the document of this phone in the registry of devices in line with what the person chose (ticket docs/tickets/notification-update-membres.md, 6 and 9;
 * contract of the backend: docs/dev/jetons-appareil-clients.md).
 *
 * THE RULES, in one place:
 * - Nothing is registered without an account signed in on this phone WITH a valid session (SESS: one account, one phone; the identifier of the document is the
 *   identifier of the session), and without the CONSENT of the person to be notified ([consent]: the explanation was answered, the phone lets Miff notify, the general
 *   switch is on). Until then the push service of the phone stays OFF: no token exists.
 * - A NEW session of the account on this phone deletes the other documents of the account at once, consent or not (contract 4.2: "un compte, un appareil", the old
 *   phone must not stay reachable), once per session.
 * - The switch "Quand un proche donne des nouvelles" is written as `notify.updates`; the general switch off, or the permission withdrawn, DELETES the document and
 *   invalidates the token.
 * - A document is written again only when the token, the switch or (once a day at most) the time zone changed, or after 30 days (see [RegistrationRules]); a failed write waits 10 minutes before the next try.
 * - The sign-out and the deletion of the account call [forget]: the document is deleted when the account is still known to the server, the token is invalidated with
 *   Google, the memory of this phone is erased.
 *
 * Nothing here logs, sends an event or keeps the token. A failure is never shown: the registration is a convenience, the Feed stays the source of truth.
 */
class PushRegistrar(
    private val registry: DeviceTokenRegistry,
    private val local: PushLocalState,
    private val tokens: PushTokenSource,
    /** The account signed in on this phone, or null. */
    private val currentUid: () -> String?,
    /** The identifier of the session of this phone (SESS), or null when there is none. */
    private val sessionDeviceId: () -> String?,
    /** Is this session still the valid one of [uid] (not replaced, not closed, no deletion under way)? Answered on the phone. */
    private val sessionValid: (uid: String) -> Boolean,
    /** The person consented to be notified: explanation answered, permission granted, general switch on. */
    private val consent: (uid: String) -> Boolean,
    /** The switch "Quand un proche donne des nouvelles" (value written as `notify.updates`). */
    private val notifyUpdates: () -> Boolean,
    private val timeZoneId: () -> String,
    /** "1.2.3 (45)", 32 characters at most. */
    private val appVersion: () -> String,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private var failedAtMillis: Long? = null
    private var cleaningFailedAtMillis: Long? = null

    /**
     * The (account, session) that [close] shut: nothing is registered for it again. Lifted only when ANOTHER account or ANOTHER session shows up (a sign-in makes a new
     * identifier of session). Without it, the reset of the choices that follows a voluntary sign-out would wake [sync] while the account is still signed in, and the
     * document just deleted would be written again (contract 4.6).
     */
    @Volatile
    private var closedFor: Pair<String?, String?>? = null

    /** The wish the last call tried, to tell a retry from a change (a change of the wish is tried at once). */
    private var lastTriedWish: RegistrationWish? = null

    /**
     * Looks at what must be written or deleted and does it. Safe to call often (when the app comes to the front, when a switch changes, when the permission may have
     * changed): it costs nothing when nothing changed. [freshToken]: a token just given by the system (onNewToken).
     */
    suspend fun sync(freshToken: String? = null) {
        mutex.withLock {
            val uid = currentUid()
            val deviceId = sessionDeviceId()
            if (uid == null || deviceId == null || !DeviceDocumentId.isValid(deviceId) || !sessionValid(uid)) {
                // Nobody is signed in with a valid session: the sign-out has already taken care of the document, there is nothing to do and nothing to write.
                return
            }
            closedFor?.let { shut ->
                if (shut.first == uid && shut.second == deviceId) return
                closedFor = null
            }
            clearOtherDevices(uid, deviceId)
            val record = local.record()
            if (!consent(uid)) {
                withdraw(uid, deviceId, record)
                return
            }
            val token = freshToken ?: tokens.enableAndFetch() ?: return
            local.setServiceOn(true)
            val wish =
                RegistrationWish(
                    uid = uid,
                    deviceId = deviceId,
                    tokenFingerprint = TokenFingerprint.of(token),
                    timeZoneId = timeZoneId(),
                    notifyUpdates = notifyUpdates(),
                )
            val now = nowMillis()
            val action = RegistrationRules.decide(wish, record, now)
            if (action !is RegistrationAction.Write) return
            if (wish == lastTriedWish && !RegistrationRules.mayRetry(failedAtMillis, now)) return
            lastTriedWish = wish
            try {
                // Bounded: the mutex is held during this call, and the sign-out (forget) must never wait long behind a server that does not answer.
                val done =
                    withTimeoutOrNull(WRITE_WAIT_MILLIS) {
                        registry.upsert(
                            uid,
                            deviceId,
                            DeviceEntry(
                                token = token,
                                timeZoneId = wish.timeZoneId,
                                notifyUpdates = wish.notifyUpdates,
                                appVersion = appVersion(),
                            ),
                        )
                    }
                if (done == null) {
                    failedAtMillis = now
                    return
                }
                local.saveRecord(
                    RegistrationRecord(
                        uid,
                        deviceId,
                        wish.tokenFingerprint,
                        wish.timeZoneId,
                        wish.notifyUpdates,
                        now,
                    ),
                )
                failedAtMillis = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Offline, rules not deployed yet, session replaced: tried again later. Never shown, never logged.
                failedAtMillis = now
            }
        }
    }

    /**
     * A new session on this phone: the other documents of the account are deleted (contract 4.2, step 3), whether this phone consented or not, ONCE per session (one
     * read of the server, 1 to 3 documents). A failure is tried again later, not at every return to the front.
     */
    private suspend fun clearOtherDevices(
        uid: String,
        deviceId: String,
    ) {
        if (local.othersClearedFor() == deviceId) return
        val now = nowMillis()
        if (!RegistrationRules.mayRetry(cleaningFailedAtMillis, now)) return
        try {
            if (withTimeoutOrNull(WRITE_WAIT_MILLIS) { registry.deleteOthers(uid, deviceId) } == null) {
                cleaningFailedAtMillis = now
                return
            }
            local.setOthersClearedFor(deviceId)
            cleaningFailedAtMillis = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            cleaningFailedAtMillis = now
        }
    }

    /** The consent is gone for the account in use: the document is deleted, the token invalidated, the service switched off. */
    private suspend fun withdraw(
        uid: String,
        deviceId: String,
        record: RegistrationRecord?,
    ) {
        if (record == null && !local.serviceOn()) return
        if (record != null && record.uid == uid) {
            try {
                withTimeoutOrNull(WRITE_WAIT_MILLIS) { registry.delete(uid, record.deviceId) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The document stays until the server notices the token is gone (FCM answers "unknown token" once it is invalidated, and the server then deletes it).
            }
        }
        tokens.disable()
        local.clear()
        // The other documents of this session were cleared above: not asked again.
        local.setOthersClearedFor(deviceId)
        lastTriedWish = null
    }

    /**
     * The first thing of a sign-out, instant and without waiting for anything: this account and this session are shut (see [closedFor]), the token is invalidated with Google
     * and the service switched off. A registration under way holds the lock while it talks to the server, and offline that can last: none of this waits for it.
     */
    fun close() {
        val uid = currentUid()
        if (uid != null) closedFor = uid to sessionDeviceId()
        if (local.record() != null || local.serviceOn()) {
            tokens.disable()
            local.setServiceOn(false)
        }
    }

    /**
     * The session is OPEN AGAIN with the same account and the same identifier (a deletion of the account that was cancelled, a restore after a failed step): [close] must not
     * keep it shut for ever. A real sign-in has a new identifier and needs no such call.
     */
    fun reopen() {
        closedFor = null
    }

    /**
     * The sign-out, the loss of the session, the deletion of the account: the document is deleted if the account can still be reached (bounded wait: the sign-out must not
     * be held up), the token is invalidated with Google, everything remembered is erased. Never throws.
     */
    suspend fun forget() {
        close()
        mutex.withLock {
            val record = local.record()
            if (record != null && currentUid() == record.uid) {
                try {
                    withTimeoutOrNull(FORGET_WAIT_MILLIS) { registry.delete(record.uid, record.deviceId) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Offline or already gone: see withdraw().
                }
            }
            // A registration that was under way may have switched the service on again meanwhile: off again.
            if (local.serviceOn()) tokens.disable()
            local.clear()
            lastTriedWish = null
            failedAtMillis = null
            cleaningFailedAtMillis = null
        }
    }

    private companion object {
        /** The sign-out waits at most this long for the server to confirm the deletion of the document. */
        const val FORGET_WAIT_MILLIS = 4_000L

        /** One call to the registry (write, clearing of the others, deletion) is given up after this long. */
        const val WRITE_WAIT_MILLIS = 15_000L
    }
}
