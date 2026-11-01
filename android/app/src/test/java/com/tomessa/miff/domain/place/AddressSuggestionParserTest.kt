package com.tomessa.miff.domain.place

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The answers of the services of suggestions (ticket LOC-1, 12; criteria ADR1, ADR4). */
class AddressSuggestionParserTest {
    // The real shape of an answer of photon.komoot.io (query "17 avenue d'italie paris", read on 2026-11-01).
    private val house =
        """{"type":"Feature","properties":{"osm_type":"N","osm_id":2542388587,"osm_key":"place",""" +
            """"osm_value":"house",""" +
            """"type":"house","housenumber":"17","street":"Avenue d'Italie","locality":"Maison-Blanche",""" +
            """"district":"Paris","city":"Paris","state":"Île-de-France","country":"France",""" +
            """"postcode":"75013",""" +
            """"countrycode":"FR"},"geometry":{"type":"Point","coordinates":[2.3567938,48.8292675]}}"""
    private val dentist =
        """{"type":"Feature","properties":{"osm_key":"amenity","osm_value":"dentist","type":"house",""" +
            """"housenumber":"17","name":"Docteur Dieb","street":"Avenue d'Italie","city":"Paris",""" +
            """"state":"Île-de-France","country":"France","postcode":"75013"},""" +
            """"geometry":{"type":"Point","coordinates":[2.3572249,48.8292783]}}"""
    private val photon = """{"type":"FeatureCollection","features":[$house,$dentist]}"""

    @Test
    fun photon_streetAddress_showsTheStreetFirst_thenTownAndCountry() {
        val first = AddressSuggestionParser.fromPhoton(photon)!!.first()
        assertEquals("17 Avenue d'Italie", first.title)
        assertEquals("75013 Paris, France", first.subtitle)
        assertEquals("17 Avenue d'Italie, 75013 Paris, France", first.label)
        // GeoJSON gives [longitude, latitude]; the position has 5 decimals.
        assertEquals(48.82927, first.lat, 0.0)
        assertEquals(2.35679, first.lng, 0.0)
    }

    @Test
    fun photon_aPlaceWithAName_showsTheNameFirst_thenTheStreet() {
        val second = AddressSuggestionParser.fromPhoton(photon)!![1]
        assertEquals("Docteur Dieb", second.title)
        assertEquals("17 Avenue d'Italie, 75013 Paris, France", second.subtitle)
    }

    @Test
    fun photon_noResult_isAnEmptyList_notAFailure() {
        val empty = AddressSuggestionParser.fromPhoton("""{"type":"FeatureCollection","features":[]}""")
        assertEquals(emptyList<AddressSuggestion>(), empty)
    }

    @Test
    fun photon_somethingThatIsNotWhatPhotonSends_isAFailure() {
        assertNull(AddressSuggestionParser.fromPhoton("not json"))
        assertNull(AddressSuggestionParser.fromPhoton("""{"hello":"world"}"""))
        assertNull(AddressSuggestionParser.fromPhoton(""))
        assertNull(AddressSuggestionParser.fromPhoton("<html>blocked</html>"))
    }

    @Test
    fun photon_aFeatureWithoutAUsablePosition_isDropped() {
        val body =
            """
            {"features":[
            {"properties":{"street":"A","city":"B"},"geometry":{"type":"Point","coordinates":[200,10]}},
            {"properties":{"street":"A","city":"B"},"geometry":{"type":"Point","coordinates":[10,95]}},
            {"properties":{"street":"A","city":"B"},"geometry":{"type":"Point","coordinates":["x","y"]}},
            {"properties":{"street":"A","city":"B"},"geometry":{"type":"Point"}},
            {"properties":{"street":"Rue Ok","city":"Ville"},"geometry":{"type":"Point","coordinates":[2.0,48.0]}}
            ]}
            """.trimIndent()
        val parsed = AddressSuggestionParser.fromPhoton(body)!!
        assertEquals(1, parsed.size)
        assertEquals("Rue Ok", parsed.single().title)
    }

    @Test
    fun photon_atMostFive_andNoDuplicate() {
        val features =
            (1..9).joinToString(",") { i ->
                """{"properties":{"street":"Rue $i","city":"Ville"},""" +
                    """"geometry":{"type":"Point","coordinates":[2.$i,48.$i]}}"""
            }
        assertEquals(5, AddressSuggestionParser.fromPhoton("""{"features":[$features]}""")!!.size)
        val twice =
            """{"properties":{"street":"Rue","city":"V"},"geometry":{"type":"Point","coordinates":[2.0,48.0]}}"""
        assertEquals(1, AddressSuggestionParser.fromPhoton("""{"features":[$twice,$twice]}""")!!.size)
    }

    @Test
    fun photon_whatComesFromTheNetworkIsCleaned_andBounded() {
        val body =
            """{"features":[{"properties":{"street":"\u202ERue\u200B piégée","city":"Ville","name":"${"x".repeat(
                500,
            )}"},""" +
                """"geometry":{"type":"Point","coordinates":[2.0,48.0]}}]}"""
        val suggestion = AddressSuggestionParser.fromPhoton(body)!!.single()
        assertFalse(suggestion.title.contains('\u202E'))
        assertFalse(suggestion.label.contains('\u200B'))
        assertTrue(suggestion.title.length <= 100)
        assertTrue(suggestion.label.length <= PlaceLimits.ADDRESS_MAX)
    }

    @Test
    fun photon_aTownOnly_hasATitleAndNoStreet() {
        val body =
            """{"features":[{"properties":{"name":"Paris","state":"Île-de-France","country":"France",""" +
                """"type":"city"},""" +
                """"geometry":{"type":"Point","coordinates":[2.35,48.85]}}]}"""
        val town = AddressSuggestionParser.fromPhoton(body)!!.single()
        assertEquals("Paris", town.title)
        assertEquals("Île-de-France, France", town.subtitle)
    }

    @Test
    fun geoapify_isReadFromItsDocumentedShape() {
        val body =
            """
            {"type":"FeatureCollection","features":[{"type":"Feature","properties":{
            "formatted":"17 Avenue d'Italie, 75013 Paris, France",
            "address_line1":"17 Avenue d'Italie","address_line2":"75013 Paris, France",
            "lat":48.8292675,"lon":2.3567938},
            "geometry":{"type":"Point","coordinates":[2.3567938,48.8292675]}}]}
            """.trimIndent()
        val suggestion = AddressSuggestionParser.fromGeoapify(body)!!.single()
        assertEquals("17 Avenue d'Italie", suggestion.title)
        assertEquals("75013 Paris, France", suggestion.subtitle)
        assertEquals(48.82927, suggestion.lat, 0.0)
        assertNotNull(AddressSuggestionParser.fromGeoapify("""{"features":[]}"""))
        assertNull(AddressSuggestionParser.fromGeoapify("{"))
    }

    @Test
    fun noToStringShowsTheAddress() {
        val suggestion = AddressSuggestionParser.fromPhoton(photon)!!.first()
        assertFalse(suggestion.toString().contains("Italie"))
        assertFalse(AddressSearchResult.Found(listOf(suggestion)).toString().contains("Italie"))
    }
}
