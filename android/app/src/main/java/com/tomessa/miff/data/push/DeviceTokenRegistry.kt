package com.tomessa.miff.data.push

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.tomessa.miff.domain.push.DeviceDocumentId
import com.tomessa.miff.domain.push.DeviceEntryLimits
import kotlinx.coroutines.tasks.await

/*
 * SRV-2, the registry of the devices: `users/{uid}/devices/{deviceId}`. Contract: docs/dev/jetons-appareil-clients.md (backend-data, branch backend/jetons-appareil,
 * rules NOT DEPLOYED until the PM says so); ticket docs/tickets/notification-update-membres.md, section 8.
 *
 * SECURITY / PRIVACY, for this file:
 * - The document holds the push token of THIS phone, its platform, the version of the app, the time zone (for the quiet hours) and one boolean (the switch). The rules let
 *   only the owner read or write it; no other member ever reads a token. No position, no name, no e-mail, no advertising identifier, no identifier of a Miff.
 * - The identifier of the document is the identifier of the session of this phone (SESS), not a new identifier.
 * - Writes are TRANSACTIONS (a read, then a creation or an update), like every other write of the app: a plain write made without network would be queued and leave
 *   LATER (after a sign-out, after another phone took the account). Offline, the call fails and the caller tries again later.
 * - Nothing is logged; the token never appears in a message of ours.
 *
 * COST: one read and one write per registration (see RegistrationRules); [deleteOthers] reads the (at most 3) documents of the account from the server.
 */

/** What this phone says about itself. Never logged: the token is a secret of the phone. */
class DeviceEntry(
    val token: String,
    val timeZoneId: String,
    val notifyUpdates: Boolean,
    val appVersion: String,
    val platform: String = PLATFORM_ANDROID,
) {
    init {
        require(token.length in 1..DeviceEntryLimits.MAX_TOKEN_CHARS) { "token length" }
        require(timeZoneId.length in 1..DeviceEntryLimits.MAX_TIME_ZONE_CHARS) { "time zone length" }
        require(appVersion.length in 1..DeviceEntryLimits.MAX_APP_VERSION_CHARS) { "version length" }
    }

    override fun toString(): String = "DeviceEntry(redacted)"

    companion object {
        const val PLATFORM_ANDROID = "android"
    }
}

/** The registry, as the app uses it. Every call reaches the SERVER (a transaction or a read from the server) or throws: nothing is queued. */
interface DeviceTokenRegistry {
    /** Creates the document of [deviceId], or updates it when it exists. Throws when the server did not confirm. */
    suspend fun upsert(
        uid: String,
        deviceId: String,
        entry: DeviceEntry,
    )

    /** Deletes the other documents of the account ("un compte, un appareil"). Throws when the server cannot be reached. */
    suspend fun deleteOthers(
        uid: String,
        keepDeviceId: String,
    )

    /** Deletes the document of [deviceId]. Throws when the server did not confirm. */
    suspend fun delete(
        uid: String,
        deviceId: String,
    )
}

class FirestoreDeviceTokenRegistry(
    private val firestore: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() },
) : DeviceTokenRegistry {
    // The instance is resolved at each use: it is replaced after a purge of the cache.
    private fun devices(
        db: FirebaseFirestore,
        uid: String,
    ) = db
        .collection("users")
        .document(uid)
        .collection("devices")

    /**
     * Section 4.1 of the contract: a full `set` over an existing document would carry a NEW `createdAt` and be refused, an `update` of a missing one fails. So: read, then
     * create (every key, `createdAt` = `updatedAt` = the server time) or update (every key but `createdAt` and `platform`, which the rule keeps frozen).
     */
    override suspend fun upsert(
        uid: String,
        deviceId: String,
        entry: DeviceEntry,
    ) {
        require(DeviceDocumentId.isValid(deviceId)) { "device id shape" }
        val db = firestore()
        val ref = devices(db, uid).document(deviceId)
        db
            .runTransaction { transaction ->
                val changing =
                    mapOf(
                        "token" to entry.token,
                        "appVersion" to entry.appVersion,
                        "tz" to entry.timeZoneId,
                        "notify" to mapOf("updates" to entry.notifyUpdates),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                if (transaction.get(ref).exists()) {
                    transaction.update(ref, changing)
                } else {
                    transaction.set(
                        ref,
                        changing + mapOf("platform" to entry.platform, "createdAt" to FieldValue.serverTimestamp()),
                    )
                }
                Unit
            }.await()
    }

    override suspend fun deleteOthers(
        uid: String,
        keepDeviceId: String,
    ) {
        val db = firestore()
        val others =
            devices(db, uid)
                .get(Source.SERVER)
                .await()
                .documents
                .map { it.id }
                .filter { it != keepDeviceId }
        if (others.isEmpty()) return
        db
            .runTransaction { transaction ->
                others.forEach { transaction.delete(devices(db, uid).document(it)) }
                Unit
            }.await()
    }

    override suspend fun delete(
        uid: String,
        deviceId: String,
    ) {
        require(DeviceDocumentId.isValid(deviceId)) { "device id shape" }
        val db = firestore()
        val ref = devices(db, uid).document(deviceId)
        db.runTransaction { transaction -> transaction.delete(ref) }.await()
    }
}
