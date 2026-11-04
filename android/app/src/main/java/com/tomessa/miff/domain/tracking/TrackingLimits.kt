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

    /** A fix older than this is not used to choose the zones (the person has moved since): another one is asked for. */
    const val FIX_MAX_AGE_MILLIS = 10L * 60L * 1000L
}

/** The time of an event as it is written (criterion STA3). */
object EventTime {
    /**
     * The real time of the event, never later than now (a clock that is ahead must not make the rules refuse it: they accept 5 minutes at most),
     * or null when it is more than about 24 h old: the rules would refuse it, so it is given up (no loop).
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
    ): BreakerState = if (state != null && state.dayKey == dayKey) state else BreakerState(dayKey, 0, 0)

    fun canWriteStatus(
        state: BreakerState?,
        dayKey: String,
    ): Boolean = today(state, dayKey).statusWrites < TrackingLimits.MAX_STATUS_WRITES_PER_DAY

    fun canWritePosition(
        state: BreakerState?,
        dayKey: String,
    ): Boolean = today(state, dayKey).positionWrites < TrackingLimits.MAX_POSITION_WRITES_PER_DAY

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
