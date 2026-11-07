package com.tomessa.miff.domain.update

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FieldValue
import com.tomessa.miff.domain.miff.DocWrite
import com.tomessa.miff.domain.miff.WriteKind
import com.tomessa.miff.domain.tracking.TrackingWrites
import java.util.Date

/*
 * SECURITY / PRIVACY, for this file: the writes of the Update are the contract with backend/firestore.rules (validPhoto, validPhotoLocation, validStatus).
 * - The PHOTO document has exactly these keys: `jpeg`, `createdAt`, `expiresAt`, and ALWAYS `lat`, `lng`, `measuredAt` together (decision of the PM of
 *   2026-11-06: no photo without a position; the rules accept the three together or none, the client never sends one without the others, nor `deviceId`).
 *   It is written with `set` (never a partial update): the new photo REPLACES the old one, so no coordinate of a previous photo survives.
 * - `createdAt` is the SERVER time (the rules demand it), `expiresAt` is the clock of the phone + 23 h (the rules tolerate 25 h: two hours of margin for a
 *   phone whose clock is ahead).
 * - The STATUS is the one of [TrackingWrites.photoStatus] (the only place that writes it, with `deviceId`): it carries NO coordinate.
 * - No coordinate, no photo and no name is ever in a log, an event or a crash report: [DocWrite.toString] shows only the kind.
 */
object UpdateWrites {
    /** `expiresAt` = now + 23 h (backend/data-model.md section 8 (iv): the rules refuse more than 25 h). */
    const val PHOTO_LIFETIME_MILLIS = 23L * 60L * 60L * 1000L

    /** The photo of the author in ONE Miff: a `set` (replaces), with its place. The caller has checked the size ([UpdatePhotoFormat.MAX_BYTES]). */
    fun photo(
        miffId: String,
        uid: String,
        photo: UpdatePhoto,
        place: PhotoPlace,
        nowMillis: Long,
    ): DocWrite {
        require(photo.jpeg.isNotEmpty() && photo.jpeg.size <= UpdatePhotoFormat.MAX_BYTES) { "photo size" }
        return DocWrite(
            "miffs/$miffId/photos/$uid",
            WriteKind.SET,
            mapOf(
                "jpeg" to Blob.fromBytes(photo.jpeg),
                "createdAt" to FieldValue.serverTimestamp(),
                "expiresAt" to Timestamp(Date(nowMillis + PHOTO_LIFETIME_MILLIS)),
                "lat" to place.lat,
                "lng" to place.lng,
                "measuredAt" to Timestamp(Date(place.measuredAtMillis)),
            ),
        )
    }

    /** What publishing writes in ONE Miff: the photo AND the status, in the same lot (a status that says `hasPhoto` without a photo never exists). */
    fun publish(
        miffId: String,
        uid: String,
        photo: UpdatePhoto,
        place: PhotoPlace,
        nowMillis: Long,
        batteryPct: Int?,
        deviceId: String,
    ): List<DocWrite> =
        listOf(
            photo(miffId, uid, photo, place, nowMillis),
            TrackingWrites.photoStatus(
                miffId,
                uid,
                photoPublished = true,
                batteryPct = batteryPct,
                deviceId = deviceId,
            ),
        )

    /** The clean-up by the author of a photo that expired: the photo is deleted and the status says `hasPhoto = false`. */
    fun removeExpired(
        miffId: String,
        uid: String,
        batteryPct: Int?,
        deviceId: String,
    ): List<DocWrite> =
        listOf(
            DocWrite("miffs/$miffId/photos/$uid", WriteKind.DELETE),
            TrackingWrites.photoStatus(
                miffId,
                uid,
                photoPublished = false,
                batteryPct = batteryPct,
                deviceId = deviceId,
            ),
        )
}
