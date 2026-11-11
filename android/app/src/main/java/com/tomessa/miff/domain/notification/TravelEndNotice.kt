package com.tomessa.miff.domain.notification

import java.time.Instant
import java.time.ZoneId

/*
 * MODE VOYAGE, N7 (VOY-4A, tickets mode-voyage.md section 9.1 and notifications.md section 20.12bis): "Ton mode voyage est terminé". PURE: the hour and the key of the
 * notification of the end of a trip. The end of a trip is an absolute instant (00:00 local of the day AFTER the day chosen); N7 leaves at 08:00 local that morning, never in
 * the middle of the night. Nothing here holds a position, a place or a name: a type, a key and a time.
 */
object TravelEndNotice {
    /** The hour (local) at which N7 leaves. */
    const val HOUR = 8

    /** A N7 whose hour is past is still caught up (a silent line of the centre, the usual rule of lateness) for this long; after it the trip is long forgotten. */
    const val CATCH_UP_WINDOW_MILLIS = 24L * 60L * 60L * 1000L

    /**
     * 08:00 of the local day of the end, and never before the end itself (a time zone changed during the trip must not bring N7 before the trip ends: then the
     * next 08:00 is taken). With the usual end (00:00 local) this is `end + 8 h`, daylight-saving days included.
     */
    fun dueAt(
        untilMillis: Long,
        zone: ZoneId,
    ): Long {
        val day = Instant.ofEpochMilli(untilMillis).atZone(zone).toLocalDate()
        val first =
            day
                .atTime(HOUR, 0)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
        return if (first >
            untilMillis
        ) {
            first
        } else {
            day
                .plusDays(1)
                .atTime(HOUR, 0)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
        }
    }

    /** The key of deduplication (ticket 9.1): one trip ending at [untilMillis] is notified once; a moved end is another key. */
    fun key(
        uid: String,
        untilMillis: Long,
    ): String = "n7|$uid|$untilMillis"
}
