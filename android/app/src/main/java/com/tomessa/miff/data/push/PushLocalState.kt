package com.tomessa.miff.data.push

import android.content.Context
import androidx.core.content.edit
import com.google.firebase.messaging.FirebaseMessaging
import com.tomessa.miff.domain.push.RegistrationRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.security.MessageDigest

/*
 * SECURITY / PRIVACY, for this file: the push token is a secret of the phone. It is NEVER written to the disk by us (Firebase keeps its own copy), never logged, never
 * sent as an event. What we keep to know whether to write again is a FINGERPRINT (SHA-256) of the token, the account, the identifier of the session and the time
 * zone: private preferences, outside every backup (backup_rules.xml and data_extraction_rules.xml exclude all preferences).
 */

/** What this phone remembers of its last registration (a double in tests). */
interface PushLocalState {
    fun record(): RegistrationRecord?

    fun saveRecord(record: RegistrationRecord)

    /**
     * Is the push service switched on for this phone (the token may exist)? False from the start: the manifest turns the automatic start of Firebase Messaging off, so
     * that no token exists before the person consented.
     */
    fun serviceOn(): Boolean

    fun setServiceOn(on: Boolean)

    /**
     * The session for which the OTHER documents of the account were already deleted (contract section 4.2: a new session clears the others, even when this phone has not
     * consented yet). Null: none yet.
     */
    fun othersClearedFor(): String?

    fun setOthersClearedFor(deviceId: String)

    /** Forgets everything (sign-out, deletion of the account). */
    fun clear()
}

class SharedPreferencesPushLocalState(
    context: Context,
) : PushLocalState {
    private val prefs = context.applicationContext.getSharedPreferences("miff_push", Context.MODE_PRIVATE)

    override fun record(): RegistrationRecord? {
        val uid = prefs.getString(KEY_UID, null) ?: return null
        val deviceId = prefs.getString(KEY_DEVICE, null) ?: return null
        val fingerprint = prefs.getString(KEY_FINGERPRINT, null) ?: return null
        val zone = prefs.getString(KEY_ZONE, null) ?: return null
        return RegistrationRecord(
            uid = uid,
            deviceId = deviceId,
            tokenFingerprint = fingerprint,
            timeZoneId = zone,
            notifyUpdates = prefs.getBoolean(KEY_NOTIFY, true),
            writtenAtMillis = prefs.getLong(KEY_WRITTEN_AT, 0L),
        )
    }

    // commit(): the record must be on the disk before a receiver wakes a cold process a moment later.
    override fun saveRecord(record: RegistrationRecord) {
        prefs.edit(commit = true) {
            putString(KEY_UID, record.uid)
            putString(KEY_DEVICE, record.deviceId)
            putString(KEY_FINGERPRINT, record.tokenFingerprint)
            putString(KEY_ZONE, record.timeZoneId)
            putBoolean(KEY_NOTIFY, record.notifyUpdates)
            putLong(KEY_WRITTEN_AT, record.writtenAtMillis)
        }
    }

    override fun serviceOn(): Boolean = prefs.getBoolean(KEY_SERVICE_ON, false)

    override fun setServiceOn(on: Boolean) {
        prefs.edit(commit = true) { putBoolean(KEY_SERVICE_ON, on) }
    }

    override fun othersClearedFor(): String? = prefs.getString(KEY_CLEARED_FOR, null)

    override fun setOthersClearedFor(deviceId: String) {
        prefs.edit(commit = true) { putString(KEY_CLEARED_FOR, deviceId) }
    }

    override fun clear() {
        prefs.edit(commit = true) { clear() }
    }

    private companion object {
        const val KEY_CLEARED_FOR = "others_cleared_for"
        const val KEY_UID = "uid"
        const val KEY_DEVICE = "device"
        const val KEY_FINGERPRINT = "fingerprint"
        const val KEY_ZONE = "zone"
        const val KEY_NOTIFY = "notify"
        const val KEY_WRITTEN_AT = "written_at"
        const val KEY_SERVICE_ON = "service_on"
    }
}

/** The push service of the phone (Firebase Messaging) behind a door, so that the rest can be tested without it. */
interface PushTokenSource {
    /** Switches the service on (the automatic start of the token) and returns the current token, or null when it cannot be had now (no network, no Play services). */
    suspend fun enableAndFetch(): String?

    /** Switches the service off AND invalidates the token of this phone with Google (sign-out, withdrawal of the consent). Never throws; does not wait for the network. */
    fun disable()
}

class FirebasePushTokenSource(
    private val messaging: () -> FirebaseMessaging = { FirebaseMessaging.getInstance() },
) : PushTokenSource {
    // getToken() / deleteToken() are marked deprecated in firebase-messaging 26 (a register() / unregister() pair replaces them) but are the documented flow and work with the
    // default manifest (they only throw when the manifest turns the new registration on, which it does not). To revisit when a later BOM removes them.
    @Suppress("DEPRECATION")
    override suspend fun enableAndFetch(): String? =
        try {
            val instance = messaging()
            instance.isAutoInitEnabled = true
            // getToken() answers from the memory of Firebase when the token exists; it only goes to the network the first time. Bounded: the caller is never stuck.
            withTimeoutOrNull(TOKEN_TIMEOUT_MILLIS) { instance.token.await() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    @Suppress("DEPRECATION")
    override fun disable() {
        try {
            val instance = messaging()
            instance.isAutoInitEnabled = false
            // The result is not awaited: the token is invalidated when the network allows; until then nothing refers to it any more on our side.
            instance.deleteToken()
        } catch (e: Exception) {
            // A phone without the services of Google has nothing to switch off.
        }
    }

    private companion object {
        const val TOKEN_TIMEOUT_MILLIS = 10_000L
    }
}

object TokenFingerprint {
    /** A SHA-256 of the token, in hexadecimal: enough to see that the token changed, useless to send a message. */
    fun of(token: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(token.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
