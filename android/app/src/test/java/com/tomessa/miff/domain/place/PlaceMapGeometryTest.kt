package com.tomessa.miff.domain.place

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** The circle drawn on the map (ticket LOC-1, ADR2). */
class PlaceMapGeometryTest {
    private fun distanceM(
        a: PlaceMapGeometry.LatLng,
        lat: Double,
        lng: Double,
    ): Double {
        val r = 6_371_008.8
        val dLat = Math.toRadians(a.lat - lat)
        val dLng = Math.toRadians(a.lng - lng)
        val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat)) * cos(Math.toRadians(a.lat)) * sin(dLng / 2).pow(2)
        return 2 * r * asin(sqrt(h))
    }

    @Test
    fun ring_isClosed_andEveryPointIsAtTheRadius() {
        listOf(50, 100, 800).forEach { radius ->
            val ring = PlaceMapGeometry.ring(48.82927, 2.35679, radius)
            assertEquals(PlaceMapGeometry.STEPS + 1, ring.size)
            assertEquals(ring.first().lat, ring.last().lat, 0.0)
            assertEquals(ring.first().lng, ring.last().lng, 0.0)
            ring.forEach { assertEquals(radius.toDouble(), distanceM(it, 48.82927, 2.35679), radius * 0.005) }
        }
    }

    @Test
    fun bounds_containTheCentre_andGrowWithTheRadius() {
        val small = PlaceMapGeometry.bounds(PlaceMapGeometry.ring(48.0, 2.0, 100))
        val large = PlaceMapGeometry.bounds(PlaceMapGeometry.ring(48.0, 2.0, 800))
        assertTrue(small[0] < 48.0 && small[2] > 48.0 && small[1] < 2.0 && small[3] > 2.0)
        assertTrue(large[2] - large[0] > small[2] - small[0])
    }

    @Test
    fun geoJson_usesADot_whateverTheLanguageOfThePhone() {
        val before = Locale.getDefault()
        try {
            Locale.setDefault(Locale.FRANCE)
            val area = PlaceMapGeometry.areaGeoJson(48.82927, 2.35679, 100)
            val point = PlaceMapGeometry.pointGeoJson(48.82927, 2.35679)
            assertFalse(area.contains("48,82"))
            assertTrue(point.contains("[2.356790,48.829270]"))
            assertTrue(area.startsWith("""{"type":"Feature""""))
            assertTrue(area.contains(""""type":"Polygon""""))
        } finally {
            Locale.setDefault(before)
        }
    }

    @Test
    fun ring_neverGoesOutOfTheLongitudeRange() {
        PlaceMapGeometry.ring(0.0, 179.9995, 800).forEach { assertTrue(it.lng in -180.0..180.0) }
    }

    @Test
    fun noToStringShowsTheCoordinates() {
        assertEquals("LatLng(redacted)", PlaceMapGeometry.LatLng(1.0, 2.0).toString())
    }
}
