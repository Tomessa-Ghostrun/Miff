package com.tomessa.miff.domain.place

import java.util.Locale
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** The shape drawn on the map: a circle of [radiusM] metres around a point. Pure, so it is tested without a map. */
object PlaceMapGeometry {
    private const val EARTH_RADIUS_M = 6_371_008.8
    const val STEPS = 64

    /** A point of the ring: latitude and longitude in degrees. */
    class LatLng(
        val lat: Double,
        val lng: Double,
    ) {
        override fun toString(): String = "LatLng(redacted)"
    }

    /** The closed ring (first point = last point) of the circle, going round the centre. */
    fun ring(
        lat: Double,
        lng: Double,
        radiusM: Int,
        steps: Int = STEPS,
    ): List<LatLng> {
        val angular = radiusM / EARTH_RADIUS_M
        val lat1 = Math.toRadians(lat)
        val lng1 = Math.toRadians(lng)
        val points =
            (0 until steps).map { i ->
                val bearing = 2 * PI * i / steps
                val lat2 = asin(sin(lat1) * cos(angular) + cos(lat1) * sin(angular) * cos(bearing))
                val lng2 =
                    lng1 + atan2(sin(bearing) * sin(angular) * cos(lat1), cos(angular) - sin(lat1) * sin(lat2))
                LatLng(Math.toDegrees(lat2), normalizeLng(Math.toDegrees(lng2)))
            }
        return points + points.first()
    }

    private fun normalizeLng(lng: Double): Double = ((lng + 540.0) % 360.0) - 180.0

    /** The south, west, north and east edges of a ring, with the longitude of a ring that does not cross the antimeridian. */
    fun bounds(ring: List<LatLng>): DoubleArray =
        doubleArrayOf(
            ring.minOf { it.lat },
            ring.minOf { it.lng },
            ring.maxOf { it.lat },
            ring.maxOf { it.lng },
        )

    /** GeoJSON `Feature` of the circle (a polygon), as a string for the map source. Numbers use a dot, whatever the language. */
    fun areaGeoJson(
        lat: Double,
        lng: Double,
        radiusM: Int,
    ): String {
        val coordinates =
            ring(lat, lng, radiusM).joinToString(",", "[", "]") { "[${number(it.lng)},${number(it.lat)}]" }
        return """{"type":"Feature","properties":{},"geometry":{"type":"Polygon","coordinates":[$coordinates]}}"""
    }

    fun pointGeoJson(
        lat: Double,
        lng: Double,
    ): String =
        """{"type":"Feature","properties":{},"geometry":{"type":"Point","coordinates":[${number(
            lng,
        )},${number(lat)}]}}"""

    private fun number(value: Double): String = String.format(Locale.ROOT, "%.6f", value)
}
