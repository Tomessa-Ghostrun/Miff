package com.tomessa.miff.data.push

/*
 * The EXACT shape of the document `users/{uid}/devices/{deviceId}` that this phone writes (contract docs/dev/jetons-appareil-clients.md, section 2): seven keys, nothing else.
 * Kept apart from Firestore so that a test can compare it, key by key, with the contract. [ServerTime] stands for `FieldValue.serverTimestamp()` (the registry swaps it in).
 */

/** The time of the server, never a date of the phone. */
object ServerTime

object DeviceDocuments {
    /** The seven keys of a document, `notify` being a map with the one key `updates`. */
    val CREATE_KEYS = setOf("token", "platform", "appVersion", "tz", "notify", "updatedAt", "createdAt")

    /** An update never writes `createdAt` nor `platform` (the rule keeps them frozen). */
    val UPDATE_KEYS = setOf("token", "appVersion", "tz", "notify", "updatedAt")

    /** The first write: every key, `createdAt` and `updatedAt` both the time of the server. */
    fun forCreate(entry: DeviceEntry): Map<String, Any> =
        forUpdate(entry) + mapOf("platform" to entry.platform, "createdAt" to ServerTime)

    /** Every later write. */
    fun forUpdate(entry: DeviceEntry): Map<String, Any> =
        mapOf(
            "token" to entry.token,
            "appVersion" to entry.appVersion,
            "tz" to entry.timeZoneId,
            "notify" to mapOf("updates" to entry.notifyUpdates),
            "updatedAt" to ServerTime,
        )
}
