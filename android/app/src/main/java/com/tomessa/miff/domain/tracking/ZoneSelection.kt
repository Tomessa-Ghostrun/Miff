package com.tomessa.miff.domain.tracking

/*
 * PRIVACY, for this file: the position given here stays in this function and is never kept, logged or sent.
 */

/** Which zones of the Miffs where the person shares are watched, and which ones are left out (ticket 6.5, criteria POS2, POS3, POS4). */
object ZoneSelection {
    /** The geofencing of Android would take 100; the PM decided 20, like iOS (decision Q9). */
    const val MAX_WATCHED = 20

    /** One place of one Miff, before the copies are put together. */
    class MiffPlace(
        val miffId: String,
        val place: PlaceFact,
    ) {
        override fun toString(): String = "MiffPlace(redacted)"
    }

    /**
     * The copies of a place (same identifier) in several Miffs are ONE zone (criterion MIF3). Should two copies disagree (one is stale),
     * the copy of the Miff with the smallest identifier wins: deterministic, and the next reading of the server makes them agree.
     */
    fun merge(places: List<MiffPlace>): List<TrackedZone> =
        places
            .groupBy { it.place.id }
            .map { (id, copies) ->
                val first = copies.minBy { it.miffId }.place
                TrackedZone(
                    placeId = id,
                    lat = first.lat,
                    lng = first.lng,
                    radiusM = first.radiusM,
                    type = first.type,
                    miffIds = copies.map { it.miffId }.toSet(),
                )
            }.sortedBy { it.placeId }

    /**
     * The zones to watch: the ones where the person IS now (by the position, or by the local state of the presence) always come first, so
     * that a departure is never missed because a big zone looks "far" from its centre (decision N8); the rest are the nearest by the EDGE
     * of the zone (distance to the centre minus the radius), up to [max]. Ties are broken by the distance to the centre, then by the
     * identifier, so that the same input always gives the same list. Reads only the list given: no request to the server (POS4).
     */
    fun select(
        lat: Double,
        lng: Double,
        zones: List<TrackedZone>,
        insideIds: Set<String>,
        max: Int = MAX_WATCHED,
    ): List<TrackedZone> {
        class Scored(
            val zone: TrackedZone,
            val edge: Double,
            val centre: Double,
            val here: Boolean,
        )
        val scored =
            zones.map {
                val centre = Geo.distanceMeters(lat, lng, it.lat, it.lng)
                val edge = Geo.edgeDistanceMeters(centre, it.radiusM)
                Scored(it, edge, centre, here = edge == 0.0 || it.placeId in insideIds)
            }
        return scored
            .sortedWith(
                compareByDescending<Scored> { it.here }
                    .thenBy { if (it.here) 0.0 else it.edge }
                    .thenBy { it.centre }
                    .thenBy { it.zone.placeId },
            ).take(max)
            .map { it.zone }
    }

    /** Is the position inside this zone? (On the edge counts as inside.) */
    fun contains(
        zone: TrackedZone,
        lat: Double,
        lng: Double,
    ): Boolean = Geo.distanceMeters(lat, lng, zone.lat, zone.lng) <= zone.radiusM
}
