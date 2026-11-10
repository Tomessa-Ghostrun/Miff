package com.tomessa.miff.domain.travel

import com.tomessa.miff.domain.tracking.PresenceValue
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/*
 * MODE VOYAGE (docs/tickets/mode-voyage.md, VOY-1A): the pure rules, shared by the engine, the Profile and the Feed. Nothing is read, written or logged here.
 * The end of a trip is an ABSOLUTE instant (00:00 local of the day AFTER the day chosen, so that the whole of the last day is included) and the day chosen is
 * kept apart as a plain `AAAA-MM-JJ` text, for the display only (a loved one in another time zone must read the day the traveller chose).
 * The cases of the tests are the contract with iOS (TravelModeTest, TravelViewTest).
 */

object TravelLimits {
    /**
     * A trip lasts 90 days at most: the furthest day that can be chosen is today + 89, so that its end (00:00 of the NEXT day) is never more than 90 days ahead, a daylight-saving
     * hour included. The rules of the server accept an end up to 91 days ahead.
     */
    const val MAX_DAYS = 90L

    /** The end written must not be further than this from now (90 days and 23 hours): under the 91 days of the rules, with a margin for the clock. */
    const val MAX_END_AHEAD_MILLIS = 90L * 24L * 3_600_000L + 23L * 3_600_000L

    /** The day proposed when the date picker opens: today + 7 days. */
    const val DEFAULT_DAYS = 7L
}

/** A trip as the phone keeps it and writes it: the instant it ends, and the day chosen (display only). */
data class TravelPeriod(
    val untilMillis: Long,
    /** `AAAA-MM-JJ`, the day chosen by the traveller (his last day of travel). */
    val untilDay: String,
) {
    /** The trip is over from the instant [untilMillis] included. */
    fun isOver(nowMillis: Long): Boolean = nowMillis >= untilMillis
}

/** The answer to "can this day be chosen?". */
sealed interface TravelChoice {
    data class Valid(
        val period: TravelPeriod,
    ) : TravelChoice

    /** Before today. */
    data object TooEarly : TravelChoice

    /** After today + 89 days (a trip of 90 days at most). */
    data object TooLate : TravelChoice
}

object TravelPlan {
    private val DAY_SHAPE = Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$")

    fun minDay(today: LocalDate): LocalDate = today

    fun maxDay(today: LocalDate): LocalDate = today.plusDays(TravelLimits.MAX_DAYS - 1)

    fun defaultDay(today: LocalDate): LocalDate = today.plusDays(TravelLimits.DEFAULT_DAYS)

    /** The instant the trip ends for the last day [day]: 00:00 local of the next day. */
    fun untilOf(
        day: LocalDate,
        zone: ZoneId,
    ): Long =
        day
            .plusDays(1)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()

    /** The day [day] as the text written to the status (ISO, `AAAA-MM-JJ`). */
    fun dayText(day: LocalDate): String = day.toString()

    /** Validates a day chosen on [today] and builds the period; the bounds are the ones of the date picker. */
    fun choose(
        day: LocalDate,
        today: LocalDate,
        zone: ZoneId,
    ): TravelChoice =
        when {
            day.isBefore(minDay(today)) -> TravelChoice.TooEarly
            day.isAfter(maxDay(today)) -> TravelChoice.TooLate
            else -> TravelChoice.Valid(TravelPeriod(untilOf(day, zone), dayText(day)))
        }

    /** A day text received from the server, or null when it is not an `AAAA-MM-JJ` date (somebody else wrote it: never trusted). */
    fun parseDay(value: String?): LocalDate? {
        if (value == null || !DAY_SHAPE.matches(value)) return null
        return try {
            LocalDate.parse(value)
        } catch (e: DateTimeException) {
            null
        }
    }

    fun today(
        nowMillis: Long,
        zone: ZoneId,
    ): LocalDate = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
}

/** The day to show for a trip, with whether the year is shown (only when it is not the current year). */
data class TravelDay(
    val date: LocalDate,
    val showYear: Boolean,
) {
    companion object {
        /**
         * The day chosen by the traveller: [untilDay] when it is well formed, else (an application of another version) the day before [untilMillis] in the zone
         * of the reader. Null when neither is usable.
         */
        fun of(
            untilDay: String?,
            untilMillis: Long?,
            today: LocalDate,
            zone: ZoneId,
        ): TravelDay? {
            val date =
                TravelPlan.parseDay(untilDay)
                    ?: untilMillis?.let {
                        Instant
                            .ofEpochMilli(it)
                            .atZone(zone)
                            .toLocalDate()
                            .minusDays(1)
                    }
                    ?: return null
            return TravelDay(date, showYear = date.year != today.year)
        }
    }
}

/** What a loved one sees of a trip. */
sealed interface TravelView {
    /** "En voyage jusqu'au 15 nov." */
    data class Active(
        val until: TravelDay,
    ) : TravelView

    /** The end has passed and the traveller has not given news since: "Voyage terminé" (never a false "en voyage"). */
    data object Ended : TravelView

    companion object {
        /**
         * The trip of a status, or null when it is not a trip (any other presence, or a trip without a usable end: a modified client, read as neutral and
         * without a label). Computed at the time of the reading, with no new read of the server.
         */
        fun of(
            presence: PresenceValue?,
            untilMillis: Long?,
            untilDay: String?,
            nowMillis: Long,
            zone: ZoneId,
        ): TravelView? {
            if (presence != PresenceValue.TRAVEL || untilMillis == null) return null
            if (nowMillis >= untilMillis) return Ended
            val day = TravelDay.of(untilDay, untilMillis, TravelPlan.today(nowMillis, zone), zone) ?: return null
            return Active(day)
        }
    }
}
