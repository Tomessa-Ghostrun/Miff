package com.tomessa.miff.domain.tracking

import java.time.Instant
import java.time.ZoneId

/*
 * The limits of the automatic writes of LOC-2b, in ONE place, each with the reason of its value (the PM asked for them to be written down: ticket
 * 6.6 "disjoncteur", criterion POS8 / STA4). Spark plan: 20 000 writes a day for the whole project (docs/dev/surveillance-quotas.md section 3).
 * Pure: nothing is read from the phone.
 */
object TrackingLimits {
    /**
     * Circuit breaker, status: automatic writes of `status/{moi}` a day and per phone, counted per document written (one per Miff). A normal
     * day is 6 to 10 events (ticket 9.3) and a few Miffs; 60 leaves room for a busy day and stops a zone flickering on its edge (no delay is applied:
     * decision of the PM) from burning the quota. Past it, the LAST state stays waiting and is written after midnight if it is still recent.
     * Signal to change it: a tester who reaches it on a normal day (the journal says so), or more than 12 000 writes a day in the console.
     */
    const val MAX_STATUS_WRITES_PER_DAY = 60

    /**
     * Circuit breaker, last position: writes of `positions/{moi}` a day and per phone, counted per document (one per Miff). 150 is about one every
     * ten minutes over a long day with one Miff; the guard below keeps a normal day far under it.
     */
    const val MAX_POSITION_WRITES_PER_DAY = 150

    /** Guard of frequency of the position (decision L4, to be measured at the PM's recette): at most one write every 15 minutes ... */
    const val POSITION_MIN_INTERVAL_MILLIS = 15L * 60L * 1000L

    /** ... unless the person moved by at least this many metres since the last position written ... */
    const val POSITION_MIN_DISTANCE_M = 250.0

    /** ... but never twice in less than this (a drive would otherwise write at every update of the provider). */
    const val POSITION_FLOOR_INTERVAL_MILLIS = 2L * 60L * 1000L

    /** The provider of positions only reports a move of more than this (criterion POS1). */
    const val MOVE_UPDATE_DISTANCE_M = 250f

    /** The rules refuse a time more than 24 h old; 5 minutes of margin for the time the write takes to arrive (criterion STA3). */
    const val MAX_EVENT_AGE_MILLIS = 24L * 60L * 60L * 1000L - 5L * 60L * 1000L

    /** The places and the consent of a Miff are read again from the server when the local copy is older than this (ticket 6.4 (3)). */
    const val PLACES_REREAD_MILLIS = 60L * 60L * 1000L

    /**
     * A position older than this is never used, for any occasion (review I2): the person has moved since, and an old one would write a false arrival or
     * a false position. Another one is asked for, or nothing is done.
     */
    const val FIX_MAX_AGE_MILLIS = 10L * 60L * 1000L

    /** How long the engine waits for a fresh position (a receiver has about ten seconds in all: the zones are armed BEFORE this wait, review I1). */
    const val POSITION_WAIT_MILLIS = 3_000L

    /** The rules accept a time up to 5 minutes ahead of the server's: the same margin is used for a position that looks to be in the future. */
    const val CLOCK_SKEW_MILLIS = 5L * 60L * 1000L

    /**
     * After a refusal of the server the Miff is looked at again in this time, not at every wake-up (a phone whose clock is more than 5 minutes ahead is
     * refused every time and would read the server at each wake-up).
     */
    const val RECHECK_AFTER_REFUSAL_MILLIS = 5L * 60L * 1000L

    /**
     * Circuit breaker, battery refresh (opening / leaving the app): writes of `status/{moi}` a day and per phone that only carry the battery level,
     * counted per document written (one per Miff). It is a DEDICATED counter, not the 60 of [MAX_STATUS_WRITES_PER_DAY]: a battery refresh must never eat the
     * budget of the arrivals and departures (the heart of the product). With the limits below a normal day is a handful of writes (5 to 10 passes at most,
     * each one skipped when the level moved by less than 5 points); 40 stops a loop, or a phone opened hundreds of times, from burning the quota.
     * Signal to change it: a tester who reaches it on a normal day (the journal says so), or more than 12 000 writes a day in the console.
     */
    const val MAX_BATTERY_WRITES_PER_DAY = 40

    /** Two battery refreshes are never closer than this (PM decision, 2026-11-07): the first opening writes, ten openings in a row write once. */
    const val BATTERY_MIN_INTERVAL_MILLIS = 15L * 60L * 1000L

    /** The battery is not written again unless the level moved by at least this many points since the last value written ... */
    const val BATTERY_MIN_DELTA_PCT = 5

    /**
     * ... except when the last write is older than this: the Feed hides a battery older than 6 hours (it reads `updatedAt`), so a steady level is still
     * written every 3 hours at most when the app is opened or left, instead of disappearing from the loved ones' screens for no reason.
     */
    const val BATTERY_FORCE_AFTER_MILLIS = 3L * 60L * 60L * 1000L

    /** One battery write waits this long at most (leaving the app is best effort: the process may be frozen a few seconds later). */
    const val BATTERY_WRITE_TIMEOUT_MILLIS = 4_000L

    /** A whole battery pass (all the Miffs) never goes on longer than this; what is not reached waits for the next opening. */
    const val BATTERY_PASS_MAX_MILLIS = 8_000L
}

/** What the battery refresh decides, with no I/O (PM decision, 2026-11-07: the battery is updated when the app is opened and when it is left). */
object BatteryRefresh {
    /**
     * Must the current level be written to a Miff? [lastPct] / [lastAtMillis]: the last battery level written to it by this phone (any status write counts),
     * both null when none is known. Never twice within 15 minutes; yes after 3 hours; in between only if the level moved by 5 points or more.
     */
    fun shouldWrite(
        lastPct: Int?,
        lastAtMillis: Long?,
        currentPct: Int?,
        nowMillis: Long,
    ): Boolean {
        if (currentPct == null || currentPct !in 0..100) return false
        if (lastAtMillis == null || lastPct == null) return true
        val elapsed = nowMillis - lastAtMillis
        // A clock that went back cannot be compared: write (the dedicated breaker still applies).
        if (elapsed < 0) return true
        if (elapsed < TrackingLimits.BATTERY_MIN_INTERVAL_MILLIS) return false
        if (elapsed >= TrackingLimits.BATTERY_FORCE_AFTER_MILLIS) return true
        return kotlin.math.abs(currentPct - lastPct) >= TrackingLimits.BATTERY_MIN_DELTA_PCT
    }

    /** May a pass be tried now? One attempt at most every 15 minutes, whatever its outcome (a refused or failed attempt is not repeated at every opening). */
    fun passAllowed(
        lastTriedAtMillis: Long,
        nowMillis: Long,
    ): Boolean {
        val elapsed = nowMillis - lastTriedAtMillis
        return lastTriedAtMillis == 0L || elapsed < 0 || elapsed >= TrackingLimits.BATTERY_MIN_INTERVAL_MILLIS
    }
}

/** The time of an event as it is written (criterion STA3). */
object EventTime {
    /**
     * The real time of the event, never later than the clock of the phone, or null when it is more than about 24 h old: the rules would refuse it, so
     * it is given up (no loop). LIMIT: the rules compare with the clock of the SERVER (24 h back, 5 minutes ahead). A phone whose clock is more than
     * 5 minutes ahead is refused even with a time capped at its own now; such a refusal is not retried at every wake-up (see RECHECK_AFTER_REFUSAL_MILLIS).
     */
    fun forWrite(
        eventMillis: Long,
        nowMillis: Long,
    ): Long? {
        val capped = minOf(eventMillis, nowMillis)
        return if (nowMillis - capped > TrackingLimits.MAX_EVENT_AGE_MILLIS) null else capped
    }
}

/** How many automatic writes were made today (the day of the phone). */
data class BreakerState(
    val dayKey: String,
    val statusWrites: Int,
    val positionWrites: Int,
    /** Writes that only carried the battery (opening / leaving the app), counted apart from [statusWrites]. */
    val batteryWrites: Int = 0,
)

object WriteBreaker {
    fun dayKey(
        millis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String =
        Instant
            .ofEpochMilli(millis)
            .atZone(zone)
            .toLocalDate()
            .toString()

    private fun today(
        state: BreakerState?,
        dayKey: String,
    ): BreakerState = if (state != null && state.dayKey == dayKey) state else BreakerState(dayKey, 0, 0, 0)

    fun canWriteStatus(
        state: BreakerState?,
        dayKey: String,
    ): Boolean = today(state, dayKey).statusWrites < TrackingLimits.MAX_STATUS_WRITES_PER_DAY

    fun canWritePosition(
        state: BreakerState?,
        dayKey: String,
    ): Boolean = today(state, dayKey).positionWrites < TrackingLimits.MAX_POSITION_WRITES_PER_DAY

    fun canWriteBattery(
        state: BreakerState?,
        dayKey: String,
    ): Boolean = today(state, dayKey).batteryWrites < TrackingLimits.MAX_BATTERY_WRITES_PER_DAY

    fun recordBattery(
        state: BreakerState?,
        dayKey: String,
    ): BreakerState = today(state, dayKey).let { it.copy(batteryWrites = it.batteryWrites + 1) }

    fun recordStatus(
        state: BreakerState?,
        dayKey: String,
    ): BreakerState = today(state, dayKey).let { it.copy(statusWrites = it.statusWrites + 1) }

    fun recordPosition(
        state: BreakerState?,
        dayKey: String,
    ): BreakerState = today(state, dayKey).let { it.copy(positionWrites = it.positionWrites + 1) }
}

/** The last position written to ONE Miff (kept on the phone, to apply the guard of frequency). */
class WrittenPosition(
    val lat: Double,
    val lng: Double,
    val atMillis: Long,
) {
    override fun toString(): String = "WrittenPosition(redacted)"
}

object PositionThrottle {
    /**
     * Must this fix be written to a Miff? Yes when nothing was written yet; never when the last write is under the floor; yes after 15 minutes;
     * yes before that if the person moved 250 m or more since the last position written.
     */
    fun shouldWrite(
        last: WrittenPosition?,
        lat: Double,
        lng: Double,
        nowMillis: Long,
    ): Boolean {
        if (last == null) return true
        val elapsed = nowMillis - last.atMillis
        // A clock that went back: write (it cannot be compared), the breaker still applies.
        if (elapsed < 0) return true
        if (elapsed < TrackingLimits.POSITION_FLOOR_INTERVAL_MILLIS) return false
        if (elapsed >= TrackingLimits.POSITION_MIN_INTERVAL_MILLIS) return true
        return Geo.distanceMeters(last.lat, last.lng, lat, lng) >= TrackingLimits.POSITION_MIN_DISTANCE_M
    }
}
