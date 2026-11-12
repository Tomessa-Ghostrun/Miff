package com.tomessa.miff.domain.push

/*
 * SRV-2, the registry of the devices (ticket docs/tickets/notification-update-membres.md, sections 8 and 9; contract of the backend:
 * docs/dev/jetons-appareil-clients.md): WHEN this phone writes (or deletes) its document `users/{uid}/devices/{deviceId}`. Pure rules, tested on the JVM; the
 * Android and Firestore parts are in data/push.
 *
 * COST: the document is small and rarely written. Nothing is written without a signed-in account, without a valid session and without the consent of the person to
 * be notified; a document is written again only when something CHANGED (the token, the time zone, the language, the version, the switch) or after
 * [RegistrationRules.REFRESH_AFTER_MILLIS] (one day, section 4.3 of the contract).
 */

/** What this phone last wrote (kept on the phone, outside every backup). The token itself is NOT kept: only a fingerprint, enough to see that it changed. */
data class RegistrationRecord(
    val uid: String,
    val deviceId: String,
    val tokenFingerprint: String,
    val timeZoneId: String,
    val notifyUpdates: Boolean,
    val appVersion: String,
    val locale: String,
    val writtenAtMillis: Long,
) {
    override fun toString(): String = "RegistrationRecord(redacted)"
}

/** What this phone would like the registry to say now. */
data class RegistrationWish(
    val uid: String,
    val deviceId: String,
    val tokenFingerprint: String,
    val timeZoneId: String,
    val notifyUpdates: Boolean,
    val appVersion: String,
    val locale: String,
) {
    override fun toString(): String = "RegistrationWish(redacted)"
}

sealed interface RegistrationAction {
    /** The registry already says what it should. */
    data object None : RegistrationAction

    /** Write the document (create it, or update it: the data layer reads first and chooses, section 4.1 of the contract). */
    data class Write(
        val reason: Reason,
    ) : RegistrationAction

    /** The consent is gone (switch off, permission withdrawn, signed out): delete the document of this phone. */
    data object Remove : RegistrationAction

    enum class Reason { FIRST, NEW_SESSION, TOKEN_CHANGED, SETTINGS_CHANGED, REFRESH }
}

object RegistrationRules {
    /** An unchanged document is written again once a day at most, when the app opens: it keeps the document alive and heals one the server removed. */
    const val REFRESH_AFTER_MILLIS = 24L * 60L * 60L * 1000L

    /** After a failed write the next try waits this long (the app coming to the front must not hammer a server that cannot be reached). */
    const val RETRY_AFTER_FAILURE_MILLIS = 10L * 60L * 1000L

    /**
     * @param wish null when nothing may be registered now (no account, no valid session, no consent, no token): the document written before, if any, is removed.
     * @param record what this phone last wrote, or null.
     */
    fun decide(
        wish: RegistrationWish?,
        record: RegistrationRecord?,
        nowMillis: Long,
    ): RegistrationAction {
        if (wish == null) return if (record != null) RegistrationAction.Remove else RegistrationAction.None
        if (record == null) return RegistrationAction.Write(RegistrationAction.Reason.FIRST)
        if (record.uid != wish.uid || record.deviceId != wish.deviceId) {
            return RegistrationAction.Write(RegistrationAction.Reason.NEW_SESSION)
        }
        if (record.tokenFingerprint != wish.tokenFingerprint) {
            return RegistrationAction.Write(RegistrationAction.Reason.TOKEN_CHANGED)
        }
        if (record.timeZoneId != wish.timeZoneId ||
            record.notifyUpdates != wish.notifyUpdates ||
            record.appVersion != wish.appVersion ||
            record.locale != wish.locale
        ) {
            return RegistrationAction.Write(RegistrationAction.Reason.SETTINGS_CHANGED)
        }
        if (nowMillis - record.writtenAtMillis >= REFRESH_AFTER_MILLIS) {
            return RegistrationAction.Write(RegistrationAction.Reason.REFRESH)
        }
        return RegistrationAction.None
    }

    /** May a new try be made after a failed one made at [failedAtMillis]? (A change of the wish is a reason to try at once: the caller passes null then.) */
    fun mayRetry(
        failedAtMillis: Long?,
        nowMillis: Long,
    ): Boolean =
        failedAtMillis == null || nowMillis - failedAtMillis >= RETRY_AFTER_FAILURE_MILLIS || nowMillis < failedAtMillis
}

/** The shape of the identifier of a document of the registry (the rule of the registry accepts nothing else): the identifier of the session. */
object DeviceDocumentId {
    private val shape = Regex("^[A-Za-z0-9_-]{1,64}$")

    fun isValid(id: String?): Boolean = id != null && shape.matches(id)
}

/** The fields of a document of the registry, with the limits of the rules (contract section 2). */
object DeviceEntryLimits {
    const val MAX_TOKEN_CHARS = 4096
    const val MAX_TIME_ZONE_CHARS = 64
    const val MAX_APP_VERSION_CHARS = 32
}

/** The language written in the document: "fr" or "en" only (the rule refuses anything else). */
object DeviceLocale {
    /** @param chosenTag the language chosen in Settings ("fr", "en") or null for the phone's; @param systemLanguage the phone's language ("fr", "de"...). */
    fun resolve(
        chosenTag: String?,
        systemLanguage: String?,
    ): String {
        val language = (chosenTag ?: systemLanguage).orEmpty().lowercase()
        return if (language == "fr") "fr" else "en"
    }
}

/** The version written in the document: the name and the build number, cut to the 32 characters the rule accepts (never empty). */
object DeviceAppVersion {
    fun of(
        versionName: String?,
        versionCode: Int,
    ): String {
        val text = "${versionName.orEmpty().ifBlank { "0" }} ($versionCode)"
        return text.take(DeviceEntryLimits.MAX_APP_VERSION_CHARS)
    }
}
