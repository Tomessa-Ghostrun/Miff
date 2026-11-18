package com.tomessa.miff.data.notification

import android.content.Context
import androidx.core.content.edit
import com.tomessa.miff.data.session.SessionTeardown
import kotlinx.coroutines.CancellationException

/*
 * PRIVACY / SECURITY, for this file (contract docs/dev/jetons-appareil-clients.md, section 4.9): the identifiers of the Miffs of the account signed in, kept on the phone so
 * that a push can be checked against them even when the app is started COLD by that push (no list in memory yet). Identifiers only (not a name, not a place), in private
 * preferences outside every backup, for ONE account at a time, erased at the sign-out and at the deletion of the account. Never logged.
 *
 * A message whose Miff is not in the list is IGNORED: no notification, no line of the centre, no navigation. When the list is not known (nothing remembered yet, another
 * account), nothing is shown: the answer is "no" by default, never "yes".
 */
interface KnownMiffs {
    /**
     * The Miffs [uid] belongs to, as the directory just read them. [onlyAdd]: the list comes from the CACHE of the phone (no answer of the server): it may be older than what
     * is remembered, so it can add Miffs but never remove one. Otherwise it replaces the previous list.
     */
    fun remember(
        uid: String,
        miffIds: Set<String>,
        onlyAdd: Boolean = false,
    )

    /** [miffId] is no longer one of [uid]'s Miffs (left, deleted, access lost). */
    fun forget(
        uid: String,
        miffId: String,
    )

    /** True only when [uid] is the account remembered AND [miffId] is in its list. Unknown means false. */
    fun contains(
        uid: String,
        miffId: String,
    ): Boolean

    /** The sign-out and the deletion of the account. */
    fun clear()
}

class SharedPreferencesKnownMiffs(
    context: Context,
) : KnownMiffs {
    private val prefs = context.applicationContext.getSharedPreferences("miff_known_miffs", Context.MODE_PRIVATE)

    // Read-modify-write from several threads (the directory, a message that wakes a cold process, the sign-out): one lock for all.
    private val lock = Any()

    // commit(): a cold start by a push, a moment later, must find what was just written.
    override fun remember(
        uid: String,
        miffIds: Set<String>,
        onlyAdd: Boolean,
    ) = synchronized(lock) {
        val kept =
            if (onlyAdd && prefs.getString(KEY_UID, null) == uid) {
                (prefs.getStringSet(KEY_IDS, null) ?: emptySet()) + miffIds
            } else {
                miffIds
            }
        prefs.edit(commit = true) {
            putString(KEY_UID, uid)
            putStringSet(KEY_IDS, kept.toSet())
        }
    }

    override fun forget(
        uid: String,
        miffId: String,
    ) = synchronized(lock) {
        if (prefs.getString(KEY_UID, null) != uid) return@synchronized
        val ids = prefs.getStringSet(KEY_IDS, null)?.toSet() ?: return@synchronized
        prefs.edit(commit = true) { putStringSet(KEY_IDS, ids - miffId) }
    }

    override fun contains(
        uid: String,
        miffId: String,
    ): Boolean =
        synchronized(lock) {
            prefs.getString(KEY_UID, null) == uid && prefs.getStringSet(KEY_IDS, null)?.contains(miffId) == true
        }

    override fun clear() =
        synchronized(lock) {
            prefs.edit(commit = true) { clear() }
        }

    private companion object {
        const val KEY_UID = "uid"
        const val KEY_IDS = "ids"
    }
}

/**
 * What keeps the remembered list of Miffs in line with the app: the directory read a list, a Miff was left, the session ended. A small class so that this wiring is tested
 * (the container only hands it the calls).
 */
class KnownMiffsKeeper(
    private val known: KnownMiffs,
    private val currentUid: () -> String?,
) {
    /** The directory read the list of Miffs of the account; [fromCache]: it did not come from the server. */
    fun onDirectory(
        miffIds: Set<String>,
        fromCache: Boolean,
    ) {
        val uid = currentUid() ?: return
        known.remember(uid, miffIds, onlyAdd = fromCache)
    }

    /** The person left (or deleted) the Miff on purpose. */
    fun onLeft(miffId: String) {
        val uid = currentUid() ?: return
        known.forget(uid, miffId)
    }

    /** The end of the session. */
    fun onSessionEnded() = known.clear()
}

/**
 * The teardown of the notifications at the end of a session, in the order that matters (SRV-2):
 * 1. [closePush] FIRST and at once: the registration of the push is shut (nothing is written again for this session, the token is invalidated), so that the reset of the
 *    choices below cannot wake it;
 * 2. every LOCAL step (the remembered Miffs, the alarms, the centre, the choices, the tray...) BEFORE anything that talks to the server, so that a slow server delays none
 *    of them and a late step can never erase the data of an account that signed in again meanwhile;
 * 3. [forgetPush] last: it may wait for the server (bounded) to delete the document of the device.
 * One failing step never stops the next ones.
 */
class NotificationTeardownSteps(
    private val closePush: () -> Unit,
    private val local: List<() -> Unit>,
    private val forgetPush: suspend () -> Unit,
) : SessionTeardown {
    override suspend fun stop() {
        attempt(closePush)
        local.forEach(::attempt)
        try {
            forgetPush()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Offline or already gone.
        }
    }

    private fun attempt(step: () -> Unit) {
        try {
            step()
        } catch (e: Exception) {
            // The next step still runs.
        }
    }
}
