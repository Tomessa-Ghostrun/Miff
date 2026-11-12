package com.tomessa.miff.data

import android.content.Context
import androidx.core.content.edit

/**
 * AC-4 (docs/dev/app-check.md, section 8), DEBUG BUILDS ONLY (this file lives in src/debug and cannot exist in release or
 * releaseLike; the CI looks for the marker below and for the "DebugAppCheck" names in both).
 *
 * The switch "without App Check": when it is on, the next launch installs a provider that gives NO token at all, so that the PM
 * can prove that a request without a token is refused once App Check is enforced (criterion R18 of the hardening ticket).
 * The choice is stored on the phone (not sensitive: a boolean) and read ONCE per launch, by [AppCheckInstaller].
 */
interface DebugAppCheckSetting {
    fun isOffRequested(): Boolean

    fun setOffRequested(enabled: Boolean)
}

class SharedPreferencesDebugAppCheckSetting(
    context: Context,
) : DebugAppCheckSetting {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun isOffRequested(): Boolean = prefs.getBoolean(KEY, false)

    override fun setOffRequested(enabled: Boolean) = prefs.edit(commit = true) { putBoolean(KEY, enabled) }

    companion object {
        private const val FILE = "miff_debug_appcheck"

        /** Marker searched by the CI in the dex of release and releaseLike (together with "DebugAppCheck"): it must never be there. */
        const val KEY = "DEBUGAPPCHECKOFF"

        /** What this launch really did (set by [AppCheckInstaller]); the screen compares it with the stored choice. */
        @Volatile
        var offInEffect: Boolean = false
            internal set
    }
}
