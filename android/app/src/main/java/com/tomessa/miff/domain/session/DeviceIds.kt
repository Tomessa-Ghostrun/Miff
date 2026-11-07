package com.tomessa.miff.domain.session

import java.security.SecureRandom
import java.util.Base64

/*
 * PRIVACY / SECURITY, for this file (ticket SESS, "Un compte, un seul appareil"; contract of backend/data-model.md):
 * - The identifier of the session is RANDOM (SecureRandom), drawn at EVERY sign-in, and has no link with the physical phone: no serial number, no
 *   advertising identifier, no platform, no time. A reinstall or a new sign-in simply draws another one.
 * - It is a measure of CONSISTENCY, not a secret: it is copied into the status and the last position, so the members of a Miff can read it. It is
 *   still never logged, never sent as an analytics event, never put in a crash report: every class that carries it hides it in toString().
 */
object DeviceIds {
    /** 24 random bytes = 192 bits, written in 32 characters of the URL-safe alphabet (the rules accept 16 to 64 characters of `[A-Za-z0-9_-]`). */
    private const val BYTES = 24

    private val SHAPE = Regex("^[A-Za-z0-9_-]{16,64}$")

    /** The shape the rules require (`isDeviceId` in backend/firestore.rules). Anything else is never written, and never trusted when read. */
    fun isValid(value: String?): Boolean = value != null && SHAPE.matches(value)

    fun generate(random: SecureRandom = SecureRandom()): String {
        val bytes = ByteArray(BYTES)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}

/** What `users/{uid}/session/active` says, read from the SERVER. */
sealed interface RemoteSession {
    /** The server answered, and there is no session document for this account. */
    data object Missing : RemoteSession

    /** A session exists: the identifier of the phone that holds it (hidden in toString()). */
    class Present(
        val deviceId: String,
    ) : RemoteSession {
        override fun toString(): String = "Present(redacted)"

        override fun equals(other: Any?): Boolean = other is Present && other.deviceId == deviceId

        override fun hashCode(): Int = deviceId.hashCode()
    }
}

/** What to do about the session of this phone once the server has been read. */
enum class SessionVerdict {
    /** The session is this phone's. */
    MINE,

    /** The account has no session and this phone has no identifier (installed before this feature): it writes its own. */
    WRITE_NEW,

    /** Another phone holds the session (or there is one and this phone has no identifier): this phone must sign out (locally, without touching the session). */
    REPLACED,

    /**
     * This phone HAS an identifier but the session DOCUMENT is gone: it was deleted, which only happens when the account is deleted (the deletion of the account
     * deletes it first, AUTH-5b), or from the console. This phone signs out the same way, but must NOT be told that the account "is used on another phone":
     * nobody took the place, and a deletion can still be cancelled. The note says, carefully, that the account is being deleted elsewhere.
     */
    SESSION_GONE,
}

object SessionJudge {
    /**
     * The rule of the launch, of the return of the network and of a refused write, for a phone that IS signed in:
     * - the same identifier as the local one: [SessionVerdict.MINE];
     * - no session at all and no local identifier (an installation from before the feature): [SessionVerdict.WRITE_NEW];
     * - another identifier, or no local identifier but a session: [SessionVerdict.REPLACED] (another phone took the place);
     * - a local identifier but no session any more: [SessionVerdict.SESSION_GONE] (the document was deleted: the account is being deleted).
     * The sign-in itself never goes through here: it writes its own session, whatever was there (the last connection wins).
     */
    fun judge(
        local: String?,
        remote: RemoteSession,
    ): SessionVerdict =
        when (remote) {
            RemoteSession.Missing -> {
                if (local == null) SessionVerdict.WRITE_NEW else SessionVerdict.SESSION_GONE
            }

            is RemoteSession.Present -> {
                if (local != null &&
                    local == remote.deviceId
                ) {
                    SessionVerdict.MINE
                } else {
                    SessionVerdict.REPLACED
                }
            }
        }
}
