package com.tomessa.miff.domain.tracking

import com.tomessa.miff.domain.place.PlaceType

/*
 * PRIVACY, for this file: only identifiers of places and times; no coordinate, no name, no address. Pure: nothing is read from the phone.
 */

/** A zone of ONE Miff, as far as the presence is concerned: its [key] names the zone on the ground, its [placeId] is the identifier of the place in that Miff. */
class ZoneRef(
    val key: String,
    val placeId: String,
    val type: PlaceType,
) {
    override fun toString(): String = "ZoneRef(redacted)"
}

/**
 * From the events of the geofencing to the status of ONE Miff (ticket 6.6, criteria DET2, DET3, STA1). The local state "where I am" is a map
 * zone -> time of entry, shared by all the Miffs; each Miff derives its own status from the zones that belong to it, so that leaving a zone
 * of Miff A never changes what Miff B says.
 */
object PresenceMachine {
    /**
     * The presence of a Miff given the zones the person is in, a map zone KEY -> time of the entry (6.6): a trusted zone wins over a danger zone ("la zone de confiance
     * l'emporte", decision of the PM, ticket Statut); among several zones of the same type the last entered is named (leaving A while staying in B is
     * `in_trusted` on B); no zone of the Miff = out. The placeId is the one of the Miff; that of an `out` is filled by the caller (the zone just left).
     */
    fun derive(
        zones: List<ZoneRef>,
        inside: Map<String, Long>,
    ): DerivedPresence {
        fun latest(type: PlaceType): String? =
            zones
                .filter { it.type == type && it.key in inside }
                .sortedWith(compareByDescending<ZoneRef> { inside.getValue(it.key) }.thenBy { it.placeId })
                .firstOrNull()
                ?.placeId
        latest(PlaceType.TRUSTED)?.let { return DerivedPresence(PresenceValue.IN_TRUSTED, it) }
        latest(PlaceType.RISK)?.let { return DerivedPresence(PresenceValue.IN_RISK, it) }
        return DerivedPresence(PresenceValue.OUT, null)
    }

    /**
     * What an event changes in one Miff: null when the place is not one of this Miff or when nothing changes for it (entering a danger zone
     * while in a trusted one, entering the zone the status already names). [insideAfter] is the local state AFTER the event. [last] is the
     * presence this phone last derived for the Miff (null if none yet).
     */
    fun onTransition(
        kind: TransitionKind,
        zoneKey: String,
        atMillis: Long,
        zones: List<ZoneRef>,
        insideAfter: Map<String, Long>,
        last: DerivedPresence?,
    ): Pair<DerivedPresence, StatusFact>? {
        val zone = zones.firstOrNull { it.key == zoneKey } ?: return null
        val raw = derive(zones, insideAfter)
        // A departure that leaves no zone says WHICH zone was left (the label "A quitté {lieu}" of the Feed).
        val derived =
            if (raw.presence == PresenceValue.OUT &&
                kind == TransitionKind.EXIT
            ) {
                raw.copy(placeId = zone.placeId)
            } else {
                raw
            }
        if (derived == last) return null
        return derived to
            StatusFact(
                presence = derived.presence,
                placeId = derived.placeId,
                presenceAtMillis = atMillis,
                // An arrival in a trusted zone is also an update (the reference of the colour moves with it, ticket 6.6).
                setLastUpdate = derived.presence == PresenceValue.IN_TRUSTED,
            )
    }

    /**
     * FEED-REFRESH-POSITION: the zones the person is in, worked out from ONE fresh position, starting from what the phone believed ([inside], zone KEY -> time of the entry).
     * A zone whose radius contains the position is entered (its time is kept when it already was, else [nowMillis]: the instant it is NOTICED, the real one is not known); a zone
     * the phone believed the person in is left only when the position is farther than its radius plus [TrackingLimits.PULL_EXIT_MARGIN_M] (hysteresis); in between nothing changes.
     */
    fun insideAfterReading(
        zones: List<TrackedZone>,
        inside: Map<String, Long>,
        lat: Double,
        lng: Double,
        nowMillis: Long,
    ): Map<String, Long> {
        val next = inside.toMutableMap()
        for (zone in zones) {
            val distance = Geo.distanceMeters(lat, lng, zone.lat, zone.lng)
            when {
                distance <= zone.radiusM -> next.putIfAbsent(zone.key, nowMillis)
                zone.key in next && distance > zone.radiusM + TrackingLimits.PULL_EXIT_MARGIN_M -> next.remove(zone.key)
            }
        }
        return next
    }

    /**
     * FEED-REFRESH-POSITION: what a position read at a pull changes for ONE Miff, or null when nothing does (nothing is written then). [last] is the presence the phone kept for the
     * Miff (null: none yet), [derived] the one the zones give after [insideAfterReading]. An ARRIVAL (in a zone the status does not name) is told at [nowMillis] (the person IS there now).
     * A DEPARTURE the geofencing did not report (out of every zone of the Miff while the status says "in a place") becomes `unknown` at [nowMillis], never `out`: the real time of the
     * departure is not known (it may be hours ago), and an `out` dated now would show a green card for two more hours (the same rule as after a restart of the phone, and as iOS). Never an
     * `out` invented for somebody who has no presence yet or whose presence is `unknown` (no guess), and never a status that only CONFIRMS what is already there (no write, and no
     * "everything is fine" signal made up: the colour keeps ageing from the real arrival, departure or update).
     */
    fun afterReading(
        last: DerivedPresence?,
        derived: DerivedPresence,
        nowMillis: Long,
    ): Pair<DerivedPresence, StatusFact>? {
        val changed =
            when {
                last == null -> {
                    derived.presence != PresenceValue.OUT
                }

                derived.presence == PresenceValue.OUT -> {
                    last.presence == PresenceValue.IN_TRUSTED ||
                        last.presence == PresenceValue.IN_RISK
                }

                else -> {
                    derived != last
                }
            }
        if (!changed) return null
        if (derived.presence == PresenceValue.OUT) {
            return DerivedPresence(PresenceValue.UNKNOWN, null) to unknown(nowMillis)
        }
        return derived to
            StatusFact(
                presence = derived.presence,
                placeId = derived.placeId,
                presenceAtMillis = nowMillis,
                // An arrival in a trusted zone is also an update (the reference of the colour moves with it), as for any other arrival.
                setLastUpdate = derived.presence == PresenceValue.IN_TRUSTED,
            )
    }

    /** The presence cannot be confirmed any more (place deleted or changed, phone restarted somewhere else): "unknown", never a guess. */
    fun unknown(atMillis: Long): StatusFact = StatusFact(PresenceValue.UNKNOWN, null, atMillis, setLastUpdate = false)
}
