package com.tomessa.miff.domain.feed

import com.tomessa.miff.domain.tracking.StatusThresholds

/**
 * The durations the screen "Les couleurs de ta Miff" talks about (ticket COULEURS-INFO, D7): READ from [StatusThresholds], never written in a text.
 * If the PM changes a threshold, the screen follows. The texts only hold `%1$s`; the words ("2 heures", "2 hours") come from the formatter of the
 * system, in the language of the phone.
 */
class ColorsInfoDurations(
    /** Orange after this long without news (and green up to it): 2 h today. */
    val orangeAfterMillis: Long = StatusThresholds.ORANGE_AFTER_MILLIS,
    /** Red after this long without news: 4 h today. */
    val redAfterMillis: Long = StatusThresholds.RED_AFTER_MILLIS,
    /** In a danger zone, green for this long after an update: 1 h today. */
    val dangerGreenMillis: Long = StatusThresholds.DANGER_ZONE_RED_AFTER_MILLIS,
) {
    companion object {
        /** The thresholds of the Feed, as they are now. */
        val FromThresholds = ColorsInfoDurations()
    }
}

/** A length of time as a whole number of a unit the formatter of the system writes: hours when it is a whole number of hours, else minutes. */
data class WholeDuration(
    val amount: Long,
    val unit: Unit,
) {
    enum class Unit { HOUR, MINUTE }

    companion object {
        private const val MINUTE_MILLIS = 60_000L
        private const val HOUR_MILLIS = 60L * MINUTE_MILLIS

        /** 7_200_000 -> 2 hours; 5_400_000 -> 90 minutes (no thresholds are in minutes today; the screen must still be right if one is). */
        fun of(millis: Long): WholeDuration =
            if (millis % HOUR_MILLIS == 0L) {
                WholeDuration(millis / HOUR_MILLIS, Unit.HOUR)
            } else {
                WholeDuration(maxOf(1L, millis / MINUTE_MILLIS), Unit.MINUTE)
            }
    }
}
