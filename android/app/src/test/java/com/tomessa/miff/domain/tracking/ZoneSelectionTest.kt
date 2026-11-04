package com.tomessa.miff.domain.tracking

import com.tomessa.miff.domain.place.PlaceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The choice of the 20 zones (ticket 6.5, criteria POS2, POS3, POS4, MIF3, decisions Q9 and N8). Pure: no phone, no network. */
class ZoneSelectionTest {
    // About 111 m per 0.001 degree of latitude. The person is at (48.8566, 2.3522).
    private val lat = 48.8566
    private val lng = 2.3522

    private fun place(
        id: String,
        northMetres: Double,
        radius: Int = 100,
        type: PlaceType = PlaceType.TRUSTED,
    ) = PlaceFact(id, lat + northMetres / 111_195.0, lng, radius, type)

    private fun miffPlace(
        miff: String,
        fact: PlaceFact,
    ) = ZoneSelection.MiffPlace(miff, fact)

    @Test
    fun twentyTwoPlacesOnTwoMiffs_theTwentyNearestAreChosen_theTwoFarthestAreLeftOut() {
        // Notion's use case: 2 Miffs of 11 places = 22 places; the farther the place, the larger its number.
        val places =
            (1..22).map { n ->
                val miff = if (n % 2 == 0) "miffA" else "miffB"
                miffPlace(miff, place("p$n", northMetres = n * 1_000.0))
            }
        val zones = ZoneSelection.merge(places)
        assertEquals(22, zones.size)
        val chosen = ZoneSelection.select(lat, lng, zones, insideIds = emptySet())
        assertEquals(20, chosen.size)
        val ids = chosen.map { it.placeId }.toSet()
        assertFalse("p21" in ids)
        assertFalse("p22" in ids)
        assertTrue((1..20).all { "p$it" in ids })
        // The nearest comes first.
        assertEquals("p1", chosen.first().placeId)
    }

    @Test
    fun aPlaceAttachedToTwoMiffs_isOneZone_withBothMiffs() {
        val shared = place("shared", 500.0)
        val zones =
            ZoneSelection.merge(
                listOf(
                    miffPlace("miffA", shared),
                    miffPlace("miffB", shared),
                    miffPlace("miffB", place("only", 900.0)),
                ),
            )
        assertEquals(2, zones.size)
        assertEquals(setOf("miffA", "miffB"), zones.first { it.placeId == "shared" }.miffIds)
        assertEquals(setOf("miffB"), zones.first { it.placeId == "only" }.miffIds)
    }

    @Test
    fun twoDifferentPlacesWithTheSameIdentifier_areTwoZones_neverMerged_reviewB1() {
        // The fixed identifier p1 of two free creators: two different places, two Miffs.
        val atA = place("p1", 100.0, radius = 100)
        val atB = place("p1", 5_000.0, radius = 100)
        val zones = ZoneSelection.merge(listOf(miffPlace("miffA", atA), miffPlace("miffB", atB)))
        assertEquals(2, zones.size)
        assertEquals(2, zones.map { it.key }.toSet().size)
        assertEquals(setOf(setOf("miffA"), setOf("miffB")), zones.map { it.miffIds }.toSet())
        assertEquals(setOf("p1"), zones.map { it.placeId }.toSet())
    }

    @Test
    fun twoStaleCopiesOfOnePlace_thatDisagree_areTwoZones_untilTheServerIsReadAgain() {
        val a = place("p", 100.0, radius = 100)
        val b = place("p", 100.0, radius = 300)
        assertEquals(2, ZoneSelection.merge(listOf(miffPlace("miffB", b), miffPlace("miffA", a))).size)
    }

    @Test
    fun theKeyIsTheShapeTheReceiverAccepts_andOnlyThat() {
        val key = place("p1", 100.0).key
        assertTrue(ZoneKey.isValid(key))
        assertEquals("p1", ZoneKey.placeId(key))
        assertFalse(ZoneKey.isValid("p1"))
        assertFalse(ZoneKey.isValid("home"))
        assertFalse(ZoneKey.isValid(key + "|x"))
        assertFalse(ZoneKey.isValid("../../etc|1|2|3|trusted"))
        assertTrue(key.length <= 100)
    }

    @Test
    fun theDistanceIsMeasuredToTheEdge_notTheCentre() {
        // A big zone (radius 800) whose centre is 1 000 m away has its EDGE 200 m away: nearer than a small zone 600 m away.
        val big = place("big", 1_000.0, radius = 800)
        val small = place("small", 600.0, radius = 50)
        val zones = ZoneSelection.merge(listOf(miffPlace("m", big), miffPlace("m", small)))
        val chosen = ZoneSelection.select(lat, lng, zones, insideIds = emptySet(), max = 1)
        assertEquals("big", chosen.single().placeId)
    }

    @Test
    fun theZoneWherePersonIs_isAlwaysKept_evenWhenItsCentreIsFarther_thanTwentyOthers() {
        // A big zone with the centre 700 m away and a radius of 800 m: the person is inside. 25 small zones are nearer by the centre.
        val inside = place("inside", 700.0, radius = 800)
        val others = (1..25).map { miffPlace("m", place("o$it", it * 20.0 + 150.0, radius = 50)) }
        val zones = ZoneSelection.merge(listOf(miffPlace("m", inside)) + others)
        val chosen = ZoneSelection.select(lat, lng, zones, insideIds = emptySet())
        assertEquals(20, chosen.size)
        assertEquals("inside", chosen.first().placeId)
    }

    @Test
    fun aZoneTheLocalStateSaysThePersonIsIn_isKept_evenIfThePositionIsJustOutside() {
        // The person has just left (the position is outside, 600 m from the edge) but the departure was not reported yet: the zone stays watched so that it can be.
        val edge = place("edge", 700.0, radius = 100)
        val others = (1..25).map { miffPlace("m", place("o$it", it * 20.0 + 50.0, radius = 50)) }
        val zones = ZoneSelection.merge(listOf(miffPlace("m", edge)) + others)
        val withState = ZoneSelection.select(lat, lng, zones, insideIds = setOf(edge.key))
        assertTrue("edge" in withState.map { it.placeId })
        val without = ZoneSelection.select(lat, lng, zones, insideIds = emptySet())
        assertFalse("edge" in without.map { it.placeId })
    }

    @Test
    fun theSameInputAlwaysGivesTheSameList_whateverTheOrderOfTheInput() {
        val places = (1..30).map { miffPlace("m", place("p$it", (it % 7) * 100.0 + 10.0)) }
        val a = ZoneSelection.select(lat, lng, ZoneSelection.merge(places), emptySet()).map { it.placeId }
        val b = ZoneSelection.select(lat, lng, ZoneSelection.merge(places.reversed()), emptySet()).map { it.placeId }
        assertEquals(a, b)
    }

    @Test
    fun fewerThanTwentyZones_allAreKept() {
        val zones = ZoneSelection.merge((1..5).map { miffPlace("m", place("p$it", it * 100.0)) })
        assertEquals(5, ZoneSelection.select(lat, lng, zones, emptySet()).size)
    }

    @Test
    fun theLimitIsTwenty_likeIos() {
        assertEquals(20, ZoneSelection.MAX_WATCHED)
    }

    @Test
    fun anEdgeIsInside_whenTheDistanceEqualsTheRadius() {
        val zone =
            ZoneSelection
                .merge(
                    listOf(miffPlace("m", PlaceFact("z", lat, lng, 100, PlaceType.TRUSTED))),
                ).single()
        assertTrue(ZoneSelection.contains(zone, lat, lng))
        assertFalse(ZoneSelection.contains(zone, lat + 0.002, lng))
    }
}
