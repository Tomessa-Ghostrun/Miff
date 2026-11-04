package com.tomessa.miff.domain.tracking

/*
 * PRIVACY, for this file: the position given here stays in this function and is never kept, logged or sent.
 */

/** Which zones of the Miffs where the person shares are watched, and which ones are left out (ticket 6.5, criteria POS2, POS3, POS4). */
object ZoneSelection {
    /** Android allows 100 geofences per app; the PM decided 20, like iOS (decision Q9). */
    const val MAX_WATCHED = 20

    /** One place of one Miff, before the copies are put together. */
    class MiffPlace(
        val miffId: String,
        val place: PlaceFact,
    ) {
        override fun toString(): String = "MiffPlace(redacted)"
    }

    /**
     * The copies of a place in several Miffs are ONE zone (criterion MIF3) ONLY when they are the same zone on the ground: same identifier, same
     * position, radius and type ([ZoneKey]). Two different places that happen to have the same identifier (the fixed p1 / p2 of two free creators, review B1)
     * are two zones, and so are two stale copies that disagree: the next reading of the server makes the stale one go away.
     */
    fun merge(places: List<MiffPlace>): List<TrackedZone> =
        places
            .groupBy { it.place.key }
            .map { (key, copies) ->
                val first = copies.first().place
                TrackedZone(
                    key = key,
                    placeId = first.id,
                    lat = first.lat,
                    lng = first.lng,
                    radiusM = first.radiusM,
                    type = first.type,
                    miffIds = copies.map { it.miffId }.toSet(),
                )
            }.sortedBy { it.key }

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
                Scored(it, edge, centre, here = edge == 0.0 || it.key in insideIds)
            }
        return scored
            .sortedWith(
                compareByDescending<Scored> { it.here }
                    .thenBy { if (it.here) 0.0 else it.edge }
                    .thenBy { it.centre }
                    .thenBy { it.zone.key },
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
