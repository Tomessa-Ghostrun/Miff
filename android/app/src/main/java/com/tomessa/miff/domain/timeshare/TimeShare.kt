package com.tomessa.miff.domain.timeshare

import com.tomessa.miff.domain.place.PlaceType
import com.tomessa.miff.domain.tracking.PresenceValue

/*
 * The calculation of "Répartition de ton temps" (ticket docs/tickets/profil-repartition-temps.md section 6; the same rules and the same cases T1 to T23 on iOS).
 * PURE: no Android, no clock but the one it is given, nothing read from the phone, no personal data (identifiers of places and instants only).
 */

/** The constants shared with iOS (one definition per platform, the same values). */
object TimeShareRules {
    /** The three windows of the selector, in days. */
    val WINDOWS_DAYS: List<Int> = listOf(7, 14, 30)
    const val DEFAULT_WINDOW_DAYS = 7

    const val HOUR_MILLIS = 60L * 60L * 1000L
    const val DAY_MILLIS = 24L * HOUR_MILLIS

    /**
     * The chart appears only when the journal of the Miff goes back at least this far: 7 days elapsed since its FIRST recorded fact (decision of the PM, 2026-11-17, which
     * replaces the 24 h of the first version). Before that: "pas assez de données", whatever the window chosen, and the window selector is not shown either.
     */
    const val MIN_HISTORY_MILLIS = 7L * DAY_MILLIS

    /** A state is counted 48 h at most without a new fact (D9): beyond, "not measured". */
    const val MAX_STATE_MILLIS = 48L * HOUR_MILLIS

    /** The journal keeps 35 days (D1); the longest window is 30. */
    const val RETENTION_MILLIS = 35L * DAY_MILLIS

    /** Places that are named in the legend (D4); the following ones are grouped. */
    const val MAX_NAMED_PLACES = 5

    fun windowOrDefault(days: Int?): Int = days?.takeIf { it in WINDOWS_DAYS } ?: DEFAULT_WINDOW_DAYS
}

/** A place of the Miff, as the chart needs it: the identifier and the CURRENT type (a type changed since is "not measured", T22). No name here. */
class PlaceRef(
    val id: String,
    val type: PlaceType,
) {
    override fun toString(): String = "PlaceRef(redacted)"
}

/** What a slice of the ring is. The ORDER of the legend is the order of this list: places, other places, danger zone, elsewhere. */
enum class SliceKind { PLACE, OTHER_PLACES, DANGER, ELSEWHERE }

/** One place inside the line "Zone de danger" (the detail shown on a tap). The percentages of the details add up to the line. */
class SliceDetail(
    val placeId: String,
    val millis: Long,
    val percent: Int,
    /** A real share above 0 that rounds to 0: shown "< 1 %". */
    val tiny: Boolean,
) {
    override fun toString(): String = "SliceDetail(redacted)"
}

/**
 * One slice of the ring and one line of the legend. [placeId] is set for [SliceKind.PLACE]; [count] is the number of places grouped by [SliceKind.OTHER_PLACES] or
 * listed under [SliceKind.DANGER]. [percent] is the whole number shown (the sum of all slices is exactly 100); [tiny] = the real share is above 0 and under 1 %.
 */
class Slice(
    val kind: SliceKind,
    val placeId: String?,
    val count: Int,
    val millis: Long,
    val percent: Int,
    val tiny: Boolean,
    val details: List<SliceDetail> = emptyList(),
) {
    override fun toString(): String = "Slice(redacted)"
}

sealed interface TimeShareResult {
    /** The journal goes back less than 7 days, or nothing was measured in the window: no chart, no percentage at all. */
    class NotEnoughData(
        val measuredMillis: Long,
        /** How far back the journal of the Miff goes (from its first fact to now); 0 when it has none. */
        val historyMillis: Long,
    ) : TimeShareResult {
        /** The number of days left before the chart can be shown (see [TimeShare.daysLeft]). */
        val daysLeft: Int get() = TimeShare.daysLeft(historyMillis)
    }

    class Ready(
        val slices: List<Slice>,
        val measuredMillis: Long,
        /** The share in the trusted places only (places and other places; never the danger zone nor "Ailleurs"). */
        val centerPercent: Int,
    ) : TimeShareResult {
        override fun toString(): String = "Ready(redacted)"
    }
}

object TimeShare {
    /**
     * The chart of ONE Miff for a window of [windowDays] days ending at [nowMillis].
     * [facts] are those of the journal, in the order recorded; [places] are the CURRENT places of the Miff (a fact about another place is not measured).
     */
    fun compute(
        facts: List<LoggedPresence>,
        places: List<PlaceRef>,
        windowDays: Int,
        nowMillis: Long,
    ): TimeShareResult {
        val tally = tally(facts, places, windowDays, nowMillis)
        val history = history(facts, nowMillis)
        // The rule of the chart: the journal covers 7 days since its first fact AND something was measured in the window (nothing to share out otherwise).
        if (history < TimeShareRules.MIN_HISTORY_MILLIS || tally.measured <= 0L) {
            return TimeShareResult.NotEnoughData(tally.measured, history)
        }
        return build(tally)
    }

    /**
     * "Ton graphique arrive dans X jours": X = 7 days minus the days elapsed since the first fact of the journal, rounded UP so that it never goes under 1 while the chart cannot be
     * shown (6 days and 2 h elapsed: 1 day), and 7 with no fact at all (decision of the PM, 2026-11-17: this wording is fixed, no variant). Recomputed at each display, no timer.
     */
    fun daysLeft(historyMillis: Long): Int {
        val remaining = (TimeShareRules.MIN_HISTORY_MILLIS - historyMillis).coerceAtLeast(0L)
        val days = ((remaining + TimeShareRules.DAY_MILLIS - 1) / TimeShareRules.DAY_MILLIS).toInt()
        return days.coerceIn(1, (TimeShareRules.MIN_HISTORY_MILLIS / TimeShareRules.DAY_MILLIS).toInt())
    }

    /** Elapsed time from the first fact of the journal to now (0 without a fact; a first fact in the future counts 0). */
    fun history(
        facts: List<LoggedPresence>,
        nowMillis: Long,
    ): Long = facts.firstOrNull()?.let { (nowMillis - it.atMillis).coerceAtLeast(0L) } ?: 0L

    /** The time of each category inside the window, before any rounding (exposed to be tested: T2, T3, T4, T8, T11, T16, T22, T23). */
    class Tally(
        val trusted: Map<String, Long>,
        val danger: Map<String, Long>,
        val elsewhere: Long,
        /** Time of the window that is NOT measured (unknown, over the cap, place gone or changed, no fact). */
        val notMeasured: Long,
    ) {
        val measured: Long get() = trusted.values.sum() + danger.values.sum() + elsewhere

        override fun toString(): String = "Tally(redacted)"
    }

    private class Step(
        val at: Long,
        val presence: PresenceValue,
        val placeId: String?,
    )

    fun tally(
        facts: List<LoggedPresence>,
        places: List<PlaceRef>,
        windowDays: Int,
        nowMillis: Long,
    ): Tally {
        val windowStart = nowMillis - windowDays * TimeShareRules.DAY_MILLIS
        val steps = normalize(facts, nowMillis)
        val typeOf = places.associate { it.id to it.type }
        val trusted = linkedMapOf<String, Long>()
        val danger = linkedMapOf<String, Long>()
        var elsewhere = 0L
        steps.forEachIndexed { index, step ->
            val end = steps.getOrNull(index + 1)?.at ?: nowMillis
            // The cap is counted from the start of the state, before the window clips it (T4).
            val capped = minOf(end, step.at + TimeShareRules.MAX_STATE_MILLIS)
            val from = maxOf(step.at, windowStart)
            val to = minOf(capped, nowMillis)
            if (to <= from) return@forEachIndexed
            val millis = to - from
            when (step.presence) {
                PresenceValue.IN_TRUSTED -> {
                    val id = step.placeId
                    // A place that is gone, or whose type is no longer "trusted": not measured (T11, T22), never reattributed.
                    if (id != null && typeOf[id] == PlaceType.TRUSTED) trusted.merge(id, millis, Long::plus)
                }

                PresenceValue.IN_RISK -> {
                    val id = step.placeId
                    if (id != null && typeOf[id] == PlaceType.RISK) danger.merge(id, millis, Long::plus)
                }

                PresenceValue.OUT -> {
                    elsewhere += millis
                }

                // Unknown (and a trip): not measured, never counted as "Ailleurs" (D7): nothing to add.
                else -> {}
            }
        }
        val measured = trusted.values.sum() + danger.values.sum() + elsewhere
        return Tally(trusted, danger, elsewhere, notMeasured = (nowMillis - windowStart) - measured)
    }

    /**
     * The steps of the presence: a fact dated BEFORE the previous one (the clock moved) is brought back to the instant of the previous one (duration 0, never negative, T6);
     * a fact in the future is brought back to now (T16); a fact that repeats the previous state is the same step (T5). A fact that must name a place and names none is `unknown`.
     */
    private fun normalize(
        facts: List<LoggedPresence>,
        nowMillis: Long,
    ): List<Step> {
        val steps = mutableListOf<Step>()
        for (fact in facts) {
            var at = minOf(fact.atMillis, nowMillis)
            steps.lastOrNull()?.let { if (at < it.at) at = it.at }
            var presence = if (fact.presence == PresenceValue.TRAVEL) PresenceValue.UNKNOWN else fact.presence
            val inAZone = presence == PresenceValue.IN_TRUSTED || presence == PresenceValue.IN_RISK
            if (inAZone && fact.placeId == null) presence = PresenceValue.UNKNOWN
            val placeId =
                if (presence == PresenceValue.IN_TRUSTED ||
                    presence == PresenceValue.IN_RISK
                ) {
                    fact.placeId
                } else {
                    null
                }
            val last = steps.lastOrNull()
            if (last != null && last.presence == presence && last.placeId == placeId) continue
            steps += Step(at, presence, placeId)
        }
        return steps
    }

    private class Entry(
        val kind: SliceKind,
        val placeId: String?,
        val count: Int,
        val millis: Long,
        val details: List<Pair<String, Long>> = emptyList(),
    )

    private fun build(tally: Tally): TimeShareResult.Ready {
        val measured = tally.measured
        // The trusted places, the longest first (an equal time: by identifier, so that the order is the same everywhere).
        val ranked = longestFirst(tally.trusted)
        val entries = mutableListOf<Entry>()
        ranked.take(TimeShareRules.MAX_NAMED_PLACES).forEach { entries += Entry(SliceKind.PLACE, it.key, 0, it.value) }
        val rest = ranked.drop(TimeShareRules.MAX_NAMED_PLACES)
        if (rest.isNotEmpty()) entries += Entry(SliceKind.OTHER_PLACES, null, rest.size, rest.sumOf { it.value })
        val dangerRanked = longestFirst(tally.danger)
        if (dangerRanked.isNotEmpty()) {
            entries +=
                Entry(
                    SliceKind.DANGER,
                    null,
                    dangerRanked.size,
                    dangerRanked.sumOf { it.value },
                    dangerRanked.map { it.key to it.value },
                )
        }
        if (tally.elsewhere > 0) entries += Entry(SliceKind.ELSEWHERE, null, 0, tally.elsewhere)

        val percents = apportion(entries.map { it.millis }, measured, 100)
        val slices =
            entries.mapIndexed { index, entry ->
                val percent = percents[index]
                val details =
                    if (entry.kind == SliceKind.DANGER) {
                        // The detail is rounded INSIDE its line, so that it always adds up to the line.
                        val inside = apportion(entry.details.map { it.second }, entry.millis, percent)
                        entry.details.mapIndexed { i, (id, millis) ->
                            SliceDetail(id, millis, inside[i], tiny = inside[i] == 0 && millis > 0)
                        }
                    } else {
                        emptyList()
                    }
                Slice(
                    entry.kind,
                    entry.placeId,
                    entry.count,
                    entry.millis,
                    percent,
                    tiny = percent == 0,
                    details = details,
                )
            }
        val center =
            slices.filter { it.kind == SliceKind.PLACE || it.kind == SliceKind.OTHER_PLACES }.sumOf { it.percent }
        return TimeShareResult.Ready(slices, measured, center)
    }

    private fun longestFirst(times: Map<String, Long>): List<Map.Entry<String, Long>> =
        times.entries
            .filter { it.value > 0 }
            .sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })

    /**
     * Whole percentages of [times] over [total] that add up to exactly [target] (the method of the largest remainder, on integers: no rounding error). When [target] is 100,
     * a share above 0 and under 1 % counts 0 ("< 1 %"), never receives a leftover point, and the others are shared out from THEIR OWN total. An equal remainder goes to the longer time, then to the earlier line (T1).
     */
    private fun apportion(
        times: List<Long>,
        total: Long,
        target: Int,
    ): List<Int> {
        if (times.isEmpty() || total <= 0L || target <= 0) return times.map { 0 }
        // A share above 0 and under 1 % of the whole counts 0 ("< 1 %"); the OTHERS then share the 100 points according to their own time (largest remainder), the same rule as iOS.
        val tiny = times.indices.filter { target == 100 && times[it] > 0 && times[it] * 100L < total }.toSet()
        val base = total - tiny.sumOf { times[it] }
        if (base <= 0L) return times.map { 0 }
        val floors = times.indices.map { if (it in tiny) 0 else (times[it] * target / base).toInt() }
        val remainders = times.indices.map { if (it in tiny) 0L else (times[it] * target) % base }
        var leftover = target - floors.sum()
        val result = floors.toMutableList()
        val candidates =
            times.indices
                .filter { times[it] > 0 && it !in tiny }
                .sortedWith(compareByDescending<Int> { remainders[it] }.thenByDescending { times[it] }.thenBy { it })
        if (candidates.isEmpty()) return result
        var turn = 0
        while (leftover > 0) {
            result[candidates[turn % candidates.size]]++
            leftover--
            turn++
        }
        return result
    }
}
