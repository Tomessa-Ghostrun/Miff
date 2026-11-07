package com.tomessa.miff.domain.tracking

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.tomessa.miff.domain.miff.DocWrite
import com.tomessa.miff.domain.miff.LocationAccess
import com.tomessa.miff.domain.miff.WriteKind
import com.tomessa.miff.domain.session.DeviceIds
import java.util.Date

/*
 * SECURITY / PRIVACY, for this file: the writes below are the contract with backend/firestore.rules (validStatus, validPosition).
 * - The STATUS carries no coordinate: only facts (presence, place identifier, times, battery, access to the location).
 * - The LAST POSITION is the only place where a coordinate leaves the phone: `positions/{moi}` of ONE Miff, written alone (never in a lot of places,
 *   never with another document), 5 decimals, the time of the MEASURE, no history (the document is replaced).
 * - The times are the ones of the REAL event, capped at the clock of the phone (the rules accept 24 h in the past and 5 minutes ahead).
 * - "Un compte, un seul appareil" (SESS-2): EVERY write of a status or of a position carries `deviceId`, the identifier of the session of this phone, the
 *   partial updates included (the rules judge the document that results: a partial update without the key only passes if the base already holds the
 *   session's value, and the next step of the deployment makes the key mandatory). The parameter is required and checked: a write cannot be built without it.
 * - R-LIEU-1 (deployed): an update never sends `presenceAt` / `lastUpdateAt` when it does not change them (the merged document keeps the stored value, which the
 *   rules accept whatever its age). `lastUpdateAt` is never set to the time of an event just to get past the rules.
 */
object TrackingWrites {
    /**
     * An automatic update of `status/{moi}` of one Miff: an UPDATE of the fields that change, so that `updateText` and `hasPhoto` stay as the
     * person left them (ticket 6.6 "conservés") and `lastUpdateAt` stays too unless the fact says an arrival in a trusted zone. The merged
     * document still holds every key of the schema, which is what the rules check. `escalationDueAt` is null (useless on the Spark plan).
     * [presenceAtMillis] has been through [EventTime.forWrite] already.
     */
    fun statusUpdate(
        miffId: String,
        uid: String,
        fact: StatusFact,
        presenceAtMillis: Long,
        batteryPct: Int?,
        access: LocationAccess,
        deviceId: String,
    ): DocWrite {
        requireDeviceId(deviceId)
        val data =
            mutableMapOf<String, Any?>(
                "presence" to fact.presence.wire,
                "placeId" to fact.placeId,
                "presenceAt" to Timestamp(Date(presenceAtMillis)),
                "batteryPct" to batteryPct?.takeIf { it in 0..100 },
                "locationAccess" to access.value,
                "escalationDueAt" to null,
                "updatedAt" to FieldValue.serverTimestamp(),
                "deviceId" to deviceId,
            )
        if (fact.setLastUpdate) data["lastUpdateAt"] = Timestamp(Date(presenceAtMillis))
        return DocWrite("miffs/$miffId/status/$uid", WriteKind.UPDATE, data)
    }

    /** Only the battery and the access to the location (when the permission changed): the presence is not touched. */
    fun accessUpdate(
        miffId: String,
        uid: String,
        batteryPct: Int?,
        access: LocationAccess,
        deviceId: String,
    ): DocWrite {
        requireDeviceId(deviceId)
        return DocWrite(
            "miffs/$miffId/status/$uid",
            WriteKind.UPDATE,
            mapOf(
                "batteryPct" to batteryPct?.takeIf { it in 0..100 },
                "locationAccess" to access.value,
                "updatedAt" to FieldValue.serverTimestamp(),
                "deviceId" to deviceId,
            ),
        )
    }

    /**
     * Only the battery level (the app was opened or left). An UPDATE of three keys: the merged document keeps `presence`, `placeId`, `presenceAt`,
     * `lastUpdateAt`, `updateText`, `hasPhoto`, `locationAccess` and `escalationDueAt` exactly as the server holds them (R-LIEU-1: the stored times are accepted
     * whatever their age), so it passes `validStatus` with no change of the rules. An UPDATE never creates: a status that does not exist (sharing stopped,
     * deleted) is answered by a refusal, never recreated. The level is required: a phone that does not say its level writes nothing.
     */
    fun batteryUpdate(
        miffId: String,
        uid: String,
        batteryPct: Int,
        deviceId: String,
    ): DocWrite {
        requireDeviceId(deviceId)
        require(batteryPct in 0..100) { "battery range" }
        return DocWrite(
            "miffs/$miffId/status/$uid",
            WriteKind.UPDATE,
            mapOf(
                "batteryPct" to batteryPct,
                "updatedAt" to FieldValue.serverTimestamp(),
                "deviceId" to deviceId,
            ),
        )
    }

    /**
     * UPDATE-1 (the manual update, the ONLY status write of the Update screen; it lives here so that it carries `deviceId` like every other one, which
     * `DeviceIdInEveryWriteTest` counts). An UPDATE of the fields of the photo only: `presence`, `placeId`, `presenceAt` and `locationAccess` are NOT sent
     * (the merged document keeps them, R-LIEU-1), `lastUpdateAt` is the SERVER time (it is the moment of the update, and what the colour restarts from).
     * [photoPublished] true: a photo is published (`hasPhoto = true`, `updateText = null`: no text in v1). False: the photo of the author was removed
     * because it expired (`hasPhoto = false`), `lastUpdateAt` is then left alone.
     */
    fun photoStatus(
        miffId: String,
        uid: String,
        photoPublished: Boolean,
        batteryPct: Int?,
        deviceId: String,
    ): DocWrite {
        requireDeviceId(deviceId)
        val data =
            mutableMapOf<String, Any?>(
                "hasPhoto" to photoPublished,
                "batteryPct" to batteryPct?.takeIf { it in 0..100 },
                "updatedAt" to FieldValue.serverTimestamp(),
                "deviceId" to deviceId,
            )
        if (photoPublished) {
            data["lastUpdateAt"] = FieldValue.serverTimestamp()
            data["updateText"] = null
        }
        return DocWrite("miffs/$miffId/status/$uid", WriteKind.UPDATE, data)
    }

    /** The last known position in ONE Miff (data-model.md section 2.12): latitude, longitude with 5 decimals, time of the measure. Nothing else. */
    fun position(
        miffId: String,
        uid: String,
        lat: Double,
        lng: Double,
        measuredAtMillis: Long,
        deviceId: String,
    ): DocWrite {
        requireDeviceId(deviceId)
        return DocWrite(
            "miffs/$miffId/positions/$uid",
            WriteKind.SET,
            mapOf(
                "lat" to Geo.round5(lat),
                "lng" to Geo.round5(lng),
                "measuredAt" to Timestamp(Date(measuredAtMillis)),
                "deviceId" to deviceId,
            ),
        )
    }

    /** A write without a well-formed identifier of session never leaves (the message does not quote the identifier). */
    private fun requireDeviceId(deviceId: String) {
        require(DeviceIds.isValid(deviceId)) { "device id shape" }
    }
}
