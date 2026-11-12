package com.tomessa.miff.data.notification

import android.content.Context
import androidx.core.content.edit

/*
 * PRIVACY / SECURITY, for this file (contract docs/dev/jetons-appareil-clients.md, section 4.9): the identifiers of the Miffs of the account signed in, kept on the phone so
 * that a push can be checked against them even when the app is started COLD by that push (no list in memory yet). Identifiers only (not a name, not a place), in private
 * preferences outside every backup, for ONE account at a time, erased at the sign-out and at the deletion of the account. Never logged.
 *
 * A message whose Miff is not in the list is IGNORED: no notification, no line of the centre, no navigation. When the list is not known (nothing remembered yet, another
 * account), nothing is shown: the answer is "no" by default, never "yes".
 */
interface KnownMiffs {
    /** The Miffs [uid] belongs to, as the directory just read them (replaces the previous list). */
    fun remember(
        uid: String,
        miffIds: Set<String>,
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

    // commit(): a cold start by a push, a moment later, must find what was just written.
    override fun remember(
        uid: String,
        miffIds: Set<String>,
    ) {
        prefs.edit(commit = true) {
            putString(KEY_UID, uid)
            putStringSet(KEY_IDS, miffIds.toSet())
        }
    }

    override fun forget(
        uid: String,
        miffId: String,
    ) {
        if (prefs.getString(KEY_UID, null) != uid) return
        val ids = prefs.getStringSet(KEY_IDS, null)?.toSet() ?: return
        prefs.edit(commit = true) { putStringSet(KEY_IDS, ids - miffId) }
    }

    override fun contains(
        uid: String,
        miffId: String,
    ): Boolean = prefs.getString(KEY_UID, null) == uid && prefs.getStringSet(KEY_IDS, null)?.contains(miffId) == true

    override fun clear() {
        prefs.edit(commit = true) { clear() }
    }

    private companion object {
        const val KEY_UID = "uid"
        const val KEY_IDS = "ids"
    }
}
