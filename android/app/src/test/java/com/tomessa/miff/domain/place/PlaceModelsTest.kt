package com.tomessa.miff.domain.place

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A place as the server describes it: cleaned, and left out when it cannot be one (ticket LOC-1, criteria PL6, ADR4). */
class PlaceModelsTest {
    private val full: Map<String, Any?> =
        mapOf(
            "name" to "Maison de Papa",
            "type" to "trusted",
            "category" to "home",
            "address" to "26 rue du Volnay, 21000 Dijon, France",
            "lat" to 47.32, // Firestore hands back a Double
            "lng" to 5.04,
            "radiusM" to 150L, // ... and a Long for an integer
            "priority" to null,
            "createdBy" to "u1",
        )

    @Test
    fun aWholeDocument_becomesAPlace() {
        val place = Place.fromServer("p1", "m1", full)!!
        assertEquals("Maison de Papa", place.name)
        assertEquals(PlaceType.TRUSTED, place.type)
        assertEquals(PlaceCategory.HOME, place.category)
        assertEquals(150, place.radiusM)
        assertEquals("u1", place.createdBy)
        assertEquals("m1", place.miffId)
    }

    @Test
    fun theTextsAreCleaned() {
        val place =
            Place.fromServer(
                "p1",
                "m1",
                full + ("name" to "\u202EMaison\u200B ") + ("address" to "\u200B26 rue"),
            )!!
        assertEquals("Maison", place.name)
        assertEquals("26 rue", place.address)
    }

    @Test
    fun aPlaceWithNothingVisibleInItsName_isNotShown() {
        assertNull(Place.fromServer("p1", "m1", full + ("name" to "\u200B\u202E ")))
    }

    @Test
    fun aDocumentThatCannotBeAPlace_isLeftOut() {
        assertNull(Place.fromServer("p1", "m1", null))
        assertNull(Place.fromServer("not an id!", "m1", full))
        assertNull(Place.fromServer("p1", "m1", full + ("type" to "weird")))
        assertNull(Place.fromServer("p1", "m1", full + ("lat" to 200.0)))
        assertNull(Place.fromServer("p1", "m1", full + ("lng" to Double.NaN)))
        assertNull(Place.fromServer("p1", "m1", full + ("radiusM" to 10L)))
        assertNull(Place.fromServer("p1", "m1", full + ("radiusM" to 9_999L)))
        assertNull(Place.fromServer("p1", "m1", full - "lat"))
    }

    @Test
    fun aPlaceWrittenBeforeTheCategories_hasNone_andOnlyTheCategoryIsMissing() {
        val place = Place.fromServer("p1", "m1", full - "category")!!
        assertNull(place.category)
        assertEquals(PlaceType.TRUSTED, place.type)
        assertNull(Place.fromServer("p1", "m1", full + ("category" to "pirate"))!!.category)
    }

    @Test
    fun theDangerZone_keepsItsTechnicalValueRisk() {
        assertEquals("risk", PlaceType.RISK.value)
        assertEquals(PlaceType.RISK, Place.fromServer("p1", "m1", full + ("type" to "risk"))!!.type)
    }

    @Test
    fun theSevenCategories_areTheClosedListOfTheRules() {
        assertEquals(
            listOf("home", "work", "school", "sport", "relative", "leisure", "other"),
            PlaceCategory.entries.map { it.value },
        )
        assertEquals(PlaceCategory.HOME, PlaceCategory.DEFAULT)
    }

    @Test
    fun copiesOfOnePlace_inSeveralMiffs_makeOneGroup() {
        val a = Place.fromServer("p1", "m1", full)!!
        val b = Place.fromServer("p1", "m2", full)!!
        val c = Place.fromServer("p2", "m1", full)!!
        val groups = PlaceGroup.groupById(listOf(a, b, c))
        assertEquals(2, groups.size)
        assertEquals(listOf("m1", "m2"), groups.first { it.id == "p1" }.miffIds)
        assertEquals("p2", groups.last().id)
    }

    @Test
    fun noToStringShowsAnything() {
        val place = Place.fromServer("p1", "m1", full)!!
        assertEquals("Place(redacted)", place.toString())
        assertEquals("PlaceGroup(redacted)", PlaceGroup("p1", listOf(place)).toString())
        assertTrue(OwnedMiff("m1", "Famille", 1L).toString().contains("redacted"))
    }
}
