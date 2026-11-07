package com.tomessa.miff.domain.place

import com.tomessa.miff.domain.miff.MiffName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/*
 * SECURITY, for this file: what the person types is an address (often a home), and what comes back is written by a service we do
 * not control. The typed text is NEVER logged, never in an analytics event, never in an exception message. Everything that comes
 * back is cleaned like any text read from the network (control and invisible characters removed, lengths bounded, coordinates
 * checked) BEFORE it is shown or written anywhere.
 */

/** One suggestion of address: what is shown on two lines, the whole address as it will be stored, and where it is. */
class AddressSuggestion(
    /** First line: the street and its number (or the name of the place, or the town). */
    val title: String,
    /** Second line: postcode, town, country. May be empty. */
    val subtitle: String,
    /** The whole address as it is stored in the place (at most 200 characters). */
    val label: String,
    val lat: Double,
    val lng: Double,
) {
    override fun toString(): String = "AddressSuggestion(redacted)"
}

/** What a search for suggestions answered. */
sealed interface AddressSearchResult {
    class Found(
        val suggestions: List<AddressSuggestion>,
    ) : AddressSearchResult {
        override fun toString(): String = "Found(redacted)"
    }

    /** The service answered and knows no such address: "On ne trouve pas cette adresse. Essaie avec moins de détails." */
    data object NoResults : AddressSearchResult

    /** No network, too slow, refused, an answer that makes no sense: "La recherche d'adresse n'est pas disponible…". Never cached. */
    data object Unavailable : AddressSearchResult
}

/** The search itself, behind an interface: Photon today, Geoapify as a prepared fallback, a fake in the tests. */
fun interface AddressSearch {
    /** [language] is "fr" or "en": the language of the labels asked of the service. Cancellation of the caller is honoured. */
    suspend fun search(
        query: String,
        language: String,
    ): AddressSearchResult
}

/** Reads the answers of the services as [AddressSuggestion]s. Pure: tested with the real shape of the answers. */
object AddressSuggestionParser {
    private val json = Json { isLenient = false }

    private const val PART_MAX = 100

    /**
     * Photon (`photon.komoot.io`, GeoJSON). Each feature has `geometry.coordinates = [longitude, latitude]` and `properties`
     * (`name`, `housenumber`, `street`, `postcode`, `city`, `district`, `locality`, `state`, `country`...).
     * Null when the answer is not what Photon sends (the caller says "unavailable"), an empty list when there is no result.
     */
    fun fromPhoton(body: String): List<AddressSuggestion>? {
        val features = featuresOf(body) ?: return null
        return features
            .mapNotNull { feature ->
                val (lat, lng) = coordinatesOf(feature) ?: return@mapNotNull null
                val properties = (feature as? JsonObject)?.get("properties") as? JsonObject ?: return@mapNotNull null

                fun text(key: String) = (properties[key] as? JsonPrimitive)?.contentOrNull?.let(::part)
                val street = listOfNotNull(text("housenumber"), text("street")).joinToString(" ").ifEmpty { null }
                val name = text("name")
                val town = text("city") ?: text("locality") ?: text("district")
                val postcodeTown = listOfNotNull(text("postcode"), town).joinToString(" ").ifEmpty { null }
                val state = text("state")
                val country = text("country")
                // A place with a name of its own (a shop, a stadium) shows that name first; the street comes after.
                val title = name ?: street ?: town ?: state ?: country ?: return@mapNotNull null
                val rest =
                    buildList {
                        if (name != null && street != null) add(street)
                        if (postcodeTown != null) add(postcodeTown)
                        if (street == null && state != null) add(state)
                        if (country != null) add(country)
                    }
                suggestion(title, rest.filter { it != title }.distinct(), lat, lng)
            }.distinctBy { it.label }
            .take(PlaceLimits.SUGGESTION_COUNT)
    }

    /**
     * Geoapify autocomplete (`format=geojson`): the same envelope, with `properties.formatted`, `address_line1`, `address_line2`
     * and `lat` / `lon`. PREPARED, not switched on (see docs/dev/lieux-fournisseurs-gratuits.md): written from the documentation of
     * the service, not tested against it (no key was available).
     */
    fun fromGeoapify(body: String): List<AddressSuggestion>? {
        val features = featuresOf(body) ?: return null
        return features
            .mapNotNull { feature ->
                val properties = (feature as? JsonObject)?.get("properties") as? JsonObject ?: return@mapNotNull null
                val lat = (properties["lat"] as? JsonPrimitive)?.doubleOrNull
                val lng = (properties["lon"] as? JsonPrimitive)?.doubleOrNull
                val (fallbackLat, fallbackLng) = coordinatesOf(feature) ?: (null to null)
                val finalLat = lat ?: fallbackLat ?: return@mapNotNull null
                val finalLng = lng ?: fallbackLng ?: return@mapNotNull null
                val line1 = (properties["address_line1"] as? JsonPrimitive)?.contentOrNull?.let(::part)
                val line2 = (properties["address_line2"] as? JsonPrimitive)?.contentOrNull?.let(::part)
                val formatted = (properties["formatted"] as? JsonPrimitive)?.contentOrNull?.let(::part)
                val title = line1 ?: formatted ?: return@mapNotNull null
                suggestion(title, listOfNotNull(line2).filter { it != title }, finalLat, finalLng)
            }.distinctBy { it.label }
            .take(PlaceLimits.SUGGESTION_COUNT)
    }

    private fun suggestion(
        title: String,
        rest: List<String>,
        lat: Double,
        lng: Double,
    ): AddressSuggestion? {
        if (!PlaceCoordinates.isValid(lat, lng)) return null
        val subtitle = rest.joinToString(", ")
        val label = PlaceAddressText.stored(listOf(title, subtitle).filter { it.isNotEmpty() }.joinToString(", "))
        if (label.isEmpty()) return null
        return AddressSuggestion(title, subtitle, label, PlaceCoordinates.round5(lat), PlaceCoordinates.round5(lng))
    }

    /** A piece of text from the network: cleaned (no control or invisible character), trimmed, bounded; null when nothing is left. */
    private fun part(raw: String): String? =
        MiffName
            .limit(raw, PART_MAX)
            .text
            .trim()
            .takeIf { it.isNotEmpty() }

    private fun featuresOf(body: String): List<JsonElement>? =
        try {
            ((json.parseToJsonElement(body) as? JsonObject)?.get("features") as? JsonArray)?.toList()
        } catch (e: Exception) {
            null
        }

    /** GeoJSON order is [longitude, latitude]. */
    private fun coordinatesOf(feature: JsonElement): Pair<Double, Double>? {
        val coordinates =
            ((feature as? JsonObject)?.get("geometry") as? JsonObject)?.get("coordinates") as? JsonArray ?: return null
        val lng = (coordinates.getOrNull(0) as? JsonPrimitive)?.doubleOrNull ?: return null
        val lat = (coordinates.getOrNull(1) as? JsonPrimitive)?.doubleOrNull ?: return null
        return lat to lng
    }
}
