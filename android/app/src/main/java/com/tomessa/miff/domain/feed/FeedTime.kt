package com.tomessa.miff.domain.feed

import java.time.Instant
import java.time.ZoneId

/*
 * Times of the Feed, as DATA (no text): the screens turn them into the sentences of the texts `feed_*` (French "16 h 02", "hier à 16 h 02",
 * "le 3 nov. à 16 h 02"; the same cases on iOS, the tests are the contract). Pure functions of an instant, the current instant and a zone:
 * nothing is read or written, nothing is logged.
 */

/** How long ago something happened, said the way the Feed says it ("12 min", "3 h", "hier à 08 h 12", "le 3 nov. à 08 h 12"). */
sealed interface Age {
    /** Less than an hour: at least one minute (the Feed never says "0 min"). */
    data class Minutes(
        val minutes: Int,
    ) : Age

    /** From one hour to less than a day. */
    data class Hours(
        val hours: Int,
    ) : Age

    /** A day or more, and the local date of the instant is yesterday's. */
    data class Yesterday(
        val atMillis: Long,
    ) : Age

    /** A day or more, and before yesterday. */
    data class Date(
        val atMillis: Long,
    ) : Age

    companion object {
        /** A moment in the future (a phone with a wrong clock) counts as "now": one minute. */
        fun of(
            thenMillis: Long,
            nowMillis: Long,
            zone: ZoneId,
        ): Age {
            val elapsed = nowMillis - thenMillis
            return when {
                elapsed < HOUR_MILLIS -> {
                    Minutes((elapsed / MINUTE_MILLIS).toInt().coerceAtLeast(1))
                }

                elapsed < DAY_MILLIS -> {
                    Hours((elapsed / HOUR_MILLIS).toInt())
                }

                else -> {
                    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
                    val then = Instant.ofEpochMilli(thenMillis).atZone(zone).toLocalDate()
                    if (then == today.minusDays(1)) Yesterday(thenMillis) else Date(thenMillis)
                }
            }
        }
    }
}

/** Which local day a clock time belongs to: the phrase says "16 h 02", "hier à 16 h 02" or "le 3 nov. à 16 h 02". */
enum class Day { TODAY, YESTERDAY, OTHER }

/** An absolute clock time, with its day. Orange and red are said with an ABSOLUTE time ("à 16 h 40"), never "il y a 62 min" which gets worse. */
data class Moment(
    val atMillis: Long,
    val day: Day,
) {
    companion object {
        fun of(
            atMillis: Long,
            nowMillis: Long,
            zone: ZoneId,
        ): Moment {
            val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
            val then = Instant.ofEpochMilli(atMillis).atZone(zone).toLocalDate()
            val day =
                when {
                    then == today -> Day.TODAY
                    then == today.minusDays(1) -> Day.YESTERDAY
                    else -> Day.OTHER
                }
            return Moment(atMillis, day)
        }
    }
}

internal const val MINUTE_MILLIS = 60L * 1000L
internal const val HOUR_MILLIS = 60L * MINUTE_MILLIS
internal const val DAY_MILLIS = 24L * HOUR_MILLIS
