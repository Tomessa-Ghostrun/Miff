package com.tomessa.miff.domain.tracking

import com.tomessa.miff.domain.place.PlaceType

/*
 * PRIVACY, for this file: only identifiers of places and times; no coordinate, no name, no address. Pure: nothing is read from the phone.
 */

/** A zone of ONE Miff, as far as the presence is concerned. */
class ZoneRef(
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
     * The presence of a Miff given the zones the person is in (6.6): a trusted zone wins over a danger zone ("la zone de confiance l'emporte",
     * decision of the PM, ticket Statut); among several zones of the same type the last entered is named (leaving A while staying in B is
     * `in_trusted` on B); no zone of the Miff = out. The [placeId] of an `out` is filled by the caller (the zone just left).
     */
    fun derive(
        zones: List<ZoneRef>,
        inside: Map<String, Long>,
    ): DerivedPresence {
        fun latest(type: PlaceType): String? =
            zones
                .filter { it.type == type && it.placeId in inside }
                .sortedWith(compareByDescending<ZoneRef> { inside.getValue(it.placeId) }.thenBy { it.placeId })
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
        placeId: String,
        atMillis: Long,
        zones: List<ZoneRef>,
        insideAfter: Map<String, Long>,
        last: DerivedPresence?,
    ): Pair<DerivedPresence, StatusFact>? {
        if (zones.none { it.placeId == placeId }) return null
        val raw = derive(zones, insideAfter)
        // A departure that leaves no zone says WHICH zone was left (the label "A quitté {lieu}" of the Feed).
        val derived =
            if (raw.presence == PresenceValue.OUT && kind == TransitionKind.EXIT) raw.copy(placeId = placeId) else raw
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

    /** The presence cannot be confirmed any more (place deleted or changed, phone restarted somewhere else): "unknown", never a guess. */
    fun unknown(atMillis: Long): StatusFact = StatusFact(PresenceValue.UNKNOWN, null, atMillis, setLastUpdate = false)
}
