package com.tomessa.miff.domain.update

import com.tomessa.miff.domain.tracking.Fix
import com.tomessa.miff.domain.tracking.Geo
import com.tomessa.miff.domain.tracking.TrackingLimits

/*
 * PRIVACY, for this file: the position that goes with a photo (decision of the PM of 2026-11-06: "une photo doit être attachée à une position, forcément").
 * A coordinate is only ever in memory here, shows nothing in toString(), and goes to Firestore only inside the document of the photo (lat, lng and
 * measuredAt, ALWAYS the three together: backend/firestore.rules validPhotoLocation). Never in a log, an event, a crash report, the clipboard or the photo.
 */

/** The place of a photo: 5 decimals (the precision the data model asks for), and the time of the MEASURE. */
class PhotoPlace private constructor(
    val lat: Double,
    val lng: Double,
    val measuredAtMillis: Long,
) {
    override fun toString(): String = "PhotoPlace(redacted)"

    companion object {
        /**
         * The place of a photo from a measure of the phone, or null when it cannot be used: not finite or out of range (the rules would refuse the whole
         * photo), or older than the maximum age of a fix (10 minutes: an old position is a place the person has left). A measure in the future (a
         * wrong clock) is capped at [nowMillis]. The rules accept a measure up to 24 h old; the client is stricter on purpose ("mesure récente").
         */
        fun from(
            fix: Fix?,
            nowMillis: Long,
        ): PhotoPlace? {
            if (fix == null) return null
            if (!fix.lat.isFinite() || !fix.lng.isFinite()) return null
            if (fix.lat !in -90.0..90.0 || fix.lng !in -180.0..180.0) return null
            val measuredAt = minOf(fix.measuredAtMillis, nowMillis)
            if (nowMillis - measuredAt > TrackingLimits.FIX_MAX_AGE_MILLIS) return null
            return PhotoPlace(Geo.round5(fix.lat), Geo.round5(fix.lng), measuredAt)
        }
    }
}
