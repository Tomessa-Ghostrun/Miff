package com.tomessa.miff.domain.place

import kotlin.math.max
import kotlin.math.min

/**
 * How far the person may zoom the map of a place (ticket `docs/tickets/lieu-carte-zoom.md`). The limits are RELATIVE to the framing
 * of the circle (the zoom at which the whole circle fits in the box), so that the circle stays readable at every zoom the person
 * can reach: zooming out [LEVELS_OUT] levels makes a circle 8 times smaller (still a visible disc, with the dot of the centre),
 * zooming in [LEVELS_IN] levels makes it 8 times larger. Pure, so it is tested without a map.
 */
object PlaceMapZoom {
    /** Levels below the framing the person may reach (each level halves the scale): a street, then the district, then the town. */
    const val LEVELS_OUT = 3.0

    /** Levels above the framing the person may reach: the surroundings of the place, down to the street. */
    const val LEVELS_IN = 3.0

    /** Whatever the framing, never the whole planet. */
    const val FLOOR = 3.0

    /** Whatever the framing, never closer than the finest detail of the tiles (they are drawn from level 14, then only enlarged). */
    const val CEILING = 19.0

    /** The framing [fit] (kept inside the absolute limits), and the lowest and highest zoom the person may reach from it. */
    class Range(
        val fit: Double,
        val min: Double,
        val max: Double,
    )

    fun range(fitZoom: Double): Range {
        val fit = clamp(if (fitZoom.isFinite()) fitZoom else FLOOR, FLOOR, CEILING)
        return Range(fit = fit, min = max(FLOOR, fit - LEVELS_OUT), max = min(CEILING, fit + LEVELS_IN))
    }

    /** The zoom chosen by the person, as a distance (in levels) from the framing; always within what [range] allows. */
    fun offsetOf(
        zoom: Double,
        fitZoom: Double,
    ): Double {
        val range = range(fitZoom)
        if (!zoom.isFinite()) return 0.0
        return clamp(zoom, range.min, range.max) - range.fit
    }

    /** The zoom to give back from a saved distance to the framing (a rotation, a return to the screen); a bad value gives the framing. */
    fun restore(
        fitZoom: Double,
        offset: Double,
    ): Double {
        val range = range(fitZoom)
        if (!offset.isFinite()) return range.fit
        return clamp(range.fit + offset, range.min, range.max)
    }

    private fun clamp(
        value: Double,
        low: Double,
        high: Double,
    ): Double = max(low, min(high, value))
}
