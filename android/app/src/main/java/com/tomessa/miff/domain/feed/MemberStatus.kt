package com.tomessa.miff.domain.feed

import com.tomessa.miff.domain.tracking.PresenceValue

/*
 * PRIVACY, for this file: a status says where somebody is (a place identifier, never a coordinate) and how their phone is. It lives in memory
 * only; toString() shows nothing, so that a log line or a crash report can never leak it.
 */

/** What the phone says about its permission to locate (`locationAccess` of a status). */
enum class MemberLocationAccess(
    val wire: String,
) {
    BACKGROUND("background"),
    FOREGROUND("foreground"),
    DENIED("denied"),
    UNKNOWN("unknown"),
    ;

    companion object {
        fun fromWire(value: String?): MemberLocationAccess? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * The document `miffs/{id}/status/{uid}` of one member, as the Feed reads it (backend/data-model.md 2.7). Times are epoch milliseconds, null when
 * missing (a timestamp the server has not filled in yet). The colour is NEVER stored: it is computed by [com.tomessa.miff.domain.tracking.StatusColor].
 */
class MemberStatus(
    val presence: PresenceValue?,
    val placeId: String?,
    val presenceAtMillis: Long?,
    val lastUpdateAtMillis: Long?,
    val hasPhoto: Boolean,
    val batteryPct: Int?,
    val locationAccess: MemberLocationAccess?,
    val updatedAtMillis: Long?,
    /** MODE VOYAGE: the end of the trip (instant) and the day chosen (`AAAA-MM-JJ`, display only), when [presence] is `travel`. */
    val travelUntilMillis: Long? = null,
    val travelUntilDay: String? = null,
) {
    override fun toString(): String = "MemberStatus(redacted)"

    /** The most recent of the departure and the last update: the reference of the colour (decision N2). Null when neither is known. */
    val referenceMillis: Long? get() = listOfNotNull(presenceAtMillis, lastUpdateAtMillis).maxOrNull()

    companion object {
        private val PLACE_ID = Regex("^[A-Za-z0-9]{1,20}$")

        /**
         * A status as the server describes it, cleaned before use (somebody else wrote it): an unknown presence is null (neutral, never a guess),
         * a place identifier that is not the shape of ours is dropped, a battery outside 0 to 100 is dropped, an unknown access is null.
         */
        fun fromServer(
            presence: String?,
            placeId: String?,
            presenceAtMillis: Long?,
            lastUpdateAtMillis: Long?,
            hasPhoto: Boolean?,
            batteryPct: Number?,
            locationAccess: String?,
            updatedAtMillis: Long?,
            travelUntilMillis: Long? = null,
            travelUntilDay: String? = null,
        ): MemberStatus {
            val value = PresenceValue.fromWire(presence)
            // Only a trip carries an end: whatever somebody else wrote next to another presence is dropped.
            val isTravel = value == PresenceValue.TRAVEL
            return MemberStatus(
                presence = value,
                placeId = placeId?.takeIf { PLACE_ID.matches(it) },
                presenceAtMillis = presenceAtMillis,
                lastUpdateAtMillis = lastUpdateAtMillis,
                hasPhoto = hasPhoto == true,
                batteryPct =
                    batteryPct
                        ?.toDouble()
                        ?.takeIf { it.isFinite() && it >= 0.0 && it <= 100.0 }
                        ?.toInt(),
                locationAccess = MemberLocationAccess.fromWire(locationAccess),
                updatedAtMillis = updatedAtMillis,
                travelUntilMillis = travelUntilMillis.takeIf { isTravel },
                travelUntilDay = travelUntilDay.takeIf { isTravel },
            )
        }
    }
}
