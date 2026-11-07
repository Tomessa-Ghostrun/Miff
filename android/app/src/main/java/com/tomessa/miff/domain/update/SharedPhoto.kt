package com.tomessa.miff.domain.update

/*
 * PRIVACY, for this file: the photo of somebody's last update, as the Feed shows it (FEED-4), with the place it was taken at. In memory only, for the
 * session (the cache is emptied at sign-out), shows nothing in toString(). What comes from the network is cleaned before use: somebody else wrote it.
 */

/** Where a photo was taken and when this position was MEASURED (not when the photo was taken: the two can differ by a few minutes). */
class PhotoLocation(
    val lat: Double,
    val lng: Double,
    val measuredAtMillis: Long,
) {
    override fun toString(): String = "PhotoLocation(redacted)"
}

/** The photo of an update: the JPEG, when it was published (server time), when it stops being readable, and its place if it has one. */
class SharedPhoto(
    val jpeg: ByteArray,
    val createdAtMillis: Long,
    val expiresAtMillis: Long,
    val location: PhotoLocation?,
) {
    override fun toString(): String = "SharedPhoto(redacted)"

    fun isExpired(nowMillis: Long): Boolean = expiresAtMillis <= nowMillis

    companion object {
        /**
         * A photo as the server describes it, or null when it cannot be shown: no bytes, more than the 150 000 bytes the rules allow (not ours), a time
         * missing, or already expired (a photo past its `expiresAt` is NEVER shown, even from a copy). A place is kept only when the three values are there
         * and valid (the rules write them together); otherwise the photo is shown without a place, never with half of one.
         */
        fun fromServer(
            jpeg: ByteArray?,
            createdAtMillis: Long?,
            expiresAtMillis: Long?,
            lat: Number?,
            lng: Number?,
            measuredAtMillis: Long?,
            nowMillis: Long,
        ): SharedPhoto? {
            if (jpeg == null || jpeg.isEmpty() || jpeg.size > UpdatePhotoFormat.MAX_BYTES) return null
            if (createdAtMillis == null || expiresAtMillis == null) return null
            if (expiresAtMillis <= nowMillis) return null
            val latitude = lat?.toDouble()
            val longitude = lng?.toDouble()
            val location =
                if (latitude != null && longitude != null && measuredAtMillis != null &&
                    latitude.isFinite() && longitude.isFinite() &&
                    latitude in -90.0..90.0 && longitude in -180.0..180.0
                ) {
                    PhotoLocation(latitude, longitude, measuredAtMillis)
                } else {
                    null
                }
            return SharedPhoto(jpeg, createdAtMillis, expiresAtMillis, location)
        }
    }
}
