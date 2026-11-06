package com.tomessa.miff.domain.tracking

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.tomessa.miff.domain.miff.DocWrite
import com.tomessa.miff.domain.miff.LocationAccess
import com.tomessa.miff.domain.miff.WriteKind
import java.util.Date

/*
 * SECURITY / PRIVACY, for this file: the writes below are the contract with backend/firestore.rules (validStatus, validPosition).
 * - The STATUS carries no coordinate: only facts (presence, place identifier, times, battery, access to the location).
 * - The LAST POSITION is the only place where a coordinate leaves the phone: `positions/{moi}` of ONE Miff, written alone (never in a lot of places,
 *   never with another document), 5 decimals, the time of the MEASURE, no history (the document is replaced).
 * - The times are the ones of the REAL event, capped at the clock of the phone (the rules accept 24 h in the past and 5 minutes ahead).
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
        alsoLastUpdate: Boolean = fact.setLastUpdate,
    ): DocWrite {
        val data =
            mutableMapOf<String, Any?>(
                "presence" to fact.presence.wire,
                "placeId" to fact.placeId,
                "presenceAt" to Timestamp(Date(presenceAtMillis)),
                "batteryPct" to batteryPct?.takeIf { it in 0..100 },
                "locationAccess" to access.value,
                "escalationDueAt" to null,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        if (alsoLastUpdate) data["lastUpdateAt"] = Timestamp(Date(presenceAtMillis))
        return DocWrite("miffs/$miffId/status/$uid", WriteKind.UPDATE, data)
    }

    /** Only the battery and the access to the location (when the permission changed): the presence is not touched. */
    fun accessUpdate(
        miffId: String,
        uid: String,
        batteryPct: Int?,
        access: LocationAccess,
    ): DocWrite =
        DocWrite(
            "miffs/$miffId/status/$uid",
            WriteKind.UPDATE,
            mapOf(
                "batteryPct" to batteryPct?.takeIf { it in 0..100 },
                "locationAccess" to access.value,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        )

    /** The last known position in ONE Miff (data-model.md section 2.12): latitude, longitude with 5 decimals, time of the measure. Nothing else. */
    fun position(
        miffId: String,
        uid: String,
        lat: Double,
        lng: Double,
        measuredAtMillis: Long,
    ): DocWrite =
        DocWrite(
            "miffs/$miffId/positions/$uid",
            WriteKind.SET,
            mapOf(
                "lat" to Geo.round5(lat),
                "lng" to Geo.round5(lng),
                "measuredAt" to Timestamp(Date(measuredAtMillis)),
            ),
        )
}
