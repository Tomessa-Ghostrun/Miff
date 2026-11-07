package com.tomessa.miff.domain.update

import java.time.Instant
import java.time.ZoneId

/*
 * The guard-rail of the publications (ticket Update, V5, criterion U6): 20 a day and 30 seconds between two, per phone. It helps the person (a thumb that
 * taps twice) and the quota of the Spark plan (an update is 2 writes per Miff and up to 150 KB of photo); the real protection against abuse stays App Check and
 * the monitoring of the quotas. Pure: the day and the counts are given by the caller (a private file of the phone), so that the cases are tested.
 */
object PublishGuard {
    const val MAX_PER_DAY = 20
    const val MIN_GAP_MILLIS = 30_000L

    /** What the guard says: go, wait a little (the last one was less than 30 s ago), or come back later (20 today). */
    enum class Verdict { GO, TOO_SOON, TOO_MANY }

    /** The day of the phone, as a key: it changes at midnight of the phone (the count starts again). */
    fun dayKey(
        nowMillis: Long,
        zone: ZoneId,
    ): String =
        Instant
            .ofEpochMilli(nowMillis)
            .atZone(zone)
            .toLocalDate()
            .toString()

    /**
     * [countToday] is the number of publications counted for [countDay]; a count of another day is ignored. [lastMillis] is the moment of the last one
     * (a moment in the future, from a clock that was set back, is ignored: nobody waits for a clock).
     */
    fun check(
        nowMillis: Long,
        zone: ZoneId,
        countDay: String?,
        countToday: Int,
        lastMillis: Long?,
    ): Verdict {
        val today = dayKey(nowMillis, zone)
        if (countDay == today && countToday >= MAX_PER_DAY) return Verdict.TOO_MANY
        if (lastMillis != null && lastMillis <= nowMillis &&
            nowMillis - lastMillis < MIN_GAP_MILLIS
        ) {
            return Verdict.TOO_SOON
        }
        return Verdict.GO
    }

    /** The count after one more publication at [nowMillis]. */
    fun next(
        nowMillis: Long,
        zone: ZoneId,
        countDay: String?,
        countToday: Int,
    ): Pair<String, Int> {
        val today = dayKey(nowMillis, zone)
        return today to (if (countDay == today) countToday + 1 else 1)
    }
}
