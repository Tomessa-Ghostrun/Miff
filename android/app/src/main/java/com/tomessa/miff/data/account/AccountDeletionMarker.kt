package com.tomessa.miff.data.account

import android.content.Context
import androidx.core.content.edit

/**
 * The local marker "the deletion of an account is under way": the id of the account that started it, and whether that account was
 * linked to Apple (to be able to tell, at the end, how to remove that authorization from the Apple settings). Nothing else: no
 * name, no e-mail, no code (ticket AUTH-5, section 5.6). Opaque identifier, useless without the account, in a private file that
 * is excluded from every backup (`backup_rules.xml`, `data_extraction_rules.xml`).
 *
 * It is written BEFORE the first change (criterion SC15) and removed only when everything, local data included, is cleaned up. It
 * has PRIORITY over the routing at launch: without it, a profile already deleted would send the person to "create your profile".
 */
data class AccountDeletionMark(
    val uid: String,
    val appleLinked: Boolean,
) {
    // The id of an account identifies a person: it never ends up in a log or an error message through the default toString.
    override fun toString(): String = "AccountDeletionMark(redacted)"
}

interface AccountDeletionMarker {
    fun read(): AccountDeletionMark?

    /** Written to disk BEFORE returning: an app killed right after still resumes. */
    fun write(mark: AccountDeletionMark)

    fun clear()
}

class SharedPreferencesAccountDeletionMarker(
    context: Context,
) : AccountDeletionMarker {
    private val prefs = context.applicationContext.getSharedPreferences("miff_account_deletion", Context.MODE_PRIVATE)

    override fun read(): AccountDeletionMark? {
        val uid = prefs.getString(KEY_UID, null)?.takeIf { it.isNotEmpty() } ?: return null
        return AccountDeletionMark(uid, prefs.getBoolean(KEY_APPLE, false))
    }

    override fun write(mark: AccountDeletionMark) {
        prefs.edit(commit = true) {
            putString(KEY_UID, mark.uid)
            putBoolean(KEY_APPLE, mark.appleLinked)
        }
    }

    override fun clear() {
        prefs.edit(commit = true) { clear() }
    }

    private companion object {
        const val KEY_UID = "uid"
        const val KEY_APPLE = "apple_linked"
    }
}
