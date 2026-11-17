package com.tomessa.miff.domain.timeshare

import com.tomessa.miff.domain.place.PlaceType
import com.tomessa.miff.domain.tracking.PresenceValue
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/*
 * DEBUG BUILD ONLY (criterion TS-23). A set of TEST facts for the journal of the presence, so that the PM can see the chart of the Profile without waiting for days of real
 * data (docs/qa/checklists/profil-repartition-temps-android-recette-pm.md). It is in the debug source set: the release build has none of it (the CI looks for [MARKER] in the
 * release bundle and in releaseLike, and a unit test reads the sources). The facts hold only identifiers of places and instants, like the real ones.
 */
object DemoPresence {
    /** Looked for in the release bundle by the CI: it must never be there. */
    const val MARKER = "TIMESHAREDEMO"

    /** The days of test data that show the CHART (the journal goes back more than 7 days; the longest window is 30, three weeks are enough to see the windows differ). */
    const val DAYS = 21

    /** Test data that does NOT show the chart yet (under 7 days of history): the card says "Ton graphique arrive dans 5 jours" (2 days of data)... */
    const val TWO_DAYS = 2

    /** ... or "dans 1 jour" (6 days of data: the last day before the chart). */
    const val SIX_DAYS = 6

    /**
     * A believable routine over the last [DAYS] days, in the time zone [zone], for the places given as (identifier, type): nights at the first trusted place, the working days at the
     * second (or the first when there is only one), a few evenings at the third, the rest "out"; every second day a short visit to the first danger zone if there is one. The facts
     * are in chronological order, from the oldest to [now]. Deterministic: the same places give the same facts.
     */
    fun facts(
        places: List<Pair<String, PlaceType>>,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        days: Int = DAYS,
    ): List<LoggedPresence> {
        val trusted = places.filter { it.second == PlaceType.TRUSTED }.map { it.first }
        if (trusted.isEmpty()) return emptyList()
        val danger = places.firstOrNull { it.second == PlaceType.RISK }?.first
        val home = trusted[0]
        val work = trusted.getOrElse(1) { home }
        val leisure = trusted.getOrNull(2)
        val out = mutableListOf<LoggedPresence>()

        fun at(
            day: LocalDate,
            time: LocalTime,
        ): Long =
            day
                .atTime(time)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()

        fun add(
            millis: Long,
            presence: PresenceValue,
            place: String?,
        ) {
            if (millis < now) out += LoggedPresence(millis, presence, place)
        }
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        for (offset in days downTo 0) {
            val day = today.minusDays(offset.toLong())
            val weekday = day.dayOfWeek.value in 1..5
            add(at(day, LocalTime.of(0, 0)), PresenceValue.IN_TRUSTED, home)
            if (weekday) {
                add(at(day, LocalTime.of(8, 10)), PresenceValue.OUT, null)
                add(at(day, LocalTime.of(8, 50)), PresenceValue.IN_TRUSTED, work)
                add(at(day, LocalTime.of(17, 30)), PresenceValue.OUT, null)
            } else {
                add(at(day, LocalTime.of(10, 30)), PresenceValue.OUT, null)
            }
            if (danger != null && offset % 2 == 1) {
                add(at(day, LocalTime.of(12, 15)), PresenceValue.IN_RISK, danger)
                add(at(day, LocalTime.of(12, 45)), PresenceValue.OUT, null)
            }
            val leisureEvening = leisure != null && (offset % 3 == 0 || !weekday)
            if (leisure != null && leisureEvening) {
                add(at(day, LocalTime.of(19, 0)), PresenceValue.IN_TRUSTED, leisure)
                add(at(day, LocalTime.of(21, 0)), PresenceValue.OUT, null)
            }
            val backHome =
                if (leisureEvening) {
                    LocalTime.of(21, 30)
                } else if (weekday) {
                    LocalTime.of(19, 45)
                } else {
                    LocalTime.of(21, 45)
                }
            add(at(day, backHome), PresenceValue.IN_TRUSTED, home)
        }
        return out
    }

    /** Erases what the journal says about the Miff, then writes the test facts. */
    fun fill(
        log: PresenceLog,
        miffId: String,
        places: List<Pair<String, PlaceType>>,
        now: Long = System.currentTimeMillis(),
        days: Int = DAYS,
    ) {
        log.forgetMiff(miffId)
        facts(places, now, days = days).forEach { log.record(miffId, it.presence, it.placeId, it.atMillis) }
    }
}
