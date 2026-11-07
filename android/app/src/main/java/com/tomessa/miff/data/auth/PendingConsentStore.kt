package com.tomessa.miff.data.auth

import android.content.Context
import androidx.core.content.edit
import com.tomessa.miff.domain.auth.PendingConsent

/**
 * The acceptance of the terms (a) and the statistics choice (b), kept on the device until the profile is created
 * (ticket section 8). Not a secret (a version and a boolean) but never shared with anyone: private app storage,
 * `allowBackup=false`. Cleared at sign-out and once the profile exists.
 */
interface PendingConsentStore {
    fun save(consent: PendingConsent)

    /** The pending consent of THIS account only (never one recorded for someone else). */
    fun read(uid: String): PendingConsent?

    fun clear()
}

class SharedPreferencesPendingConsentStore(
    context: Context,
) : PendingConsentStore {
    private val prefs = context.applicationContext.getSharedPreferences("miff_session", Context.MODE_PRIVATE)

    override fun save(consent: PendingConsent) {
        // commit = true: written to disk before returning, so that a process kill right after cannot lose it.
        prefs.edit(commit = true) {
            putString(KEY_UID, consent.uid)
            putString(KEY_TERMS_VERSION, consent.termsVersion)
            putBoolean(KEY_ANALYTICS, consent.analyticsAccepted)
        }
    }

    override fun read(uid: String): PendingConsent? {
        val savedUid = prefs.getString(KEY_UID, null)
        val version = prefs.getString(KEY_TERMS_VERSION, null)
        if (savedUid == null || savedUid != uid || version == null) return null
        return PendingConsent(savedUid, version, prefs.getBoolean(KEY_ANALYTICS, false))
    }

    override fun clear() {
        prefs.edit(commit = true) {
            remove(KEY_UID)
            remove(KEY_TERMS_VERSION)
            remove(KEY_ANALYTICS)
        }
    }

    private companion object {
        const val KEY_UID = "pending_uid"
        const val KEY_TERMS_VERSION = "pending_terms_version"
        const val KEY_ANALYTICS = "pending_analytics_accepted"
    }
}
