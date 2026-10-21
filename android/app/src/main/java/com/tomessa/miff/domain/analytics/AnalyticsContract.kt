package com.tomessa.miff.domain.analytics

/**
 * Analytics contract shared with iOS: an allow-list of events and parameters (name and type).
 * Anything outside the list is refused, and any parameter whose name evokes a position is refused
 * with a dedicated reason (a second safety net, in case someone adds such a parameter to the list by mistake).
 *
 * Pure Kotlin on purpose: unit-tested without Android.
 */
enum class ParamType { STRING, LONG, BOOLEAN }

data class AnalyticsEventSpec(
    val name: String,
    val params: Map<String, ParamType> = emptyMap(),
)

sealed interface AnalyticsValidation {
    data object Allowed : AnalyticsValidation

    data class Refused(
        val reason: RefusalReason,
        val detail: String,
    ) : AnalyticsValidation
}

enum class RefusalReason {
    UNKNOWN_EVENT,
    UNKNOWN_PARAM,
    LOCATION_LIKE_PARAM,
    LOCATION_LIKE_VALUE,
    WRONG_TYPE,
    VALUE_TOO_LONG,
}

object AnalyticsContract {
    /** Firebase limits: event name 40 chars, string value 100 chars. */
    const val MAX_STRING_VALUE_LENGTH = 100

    /** Name of the internal diagnostic event (debug builds only call it). */
    const val EVENT_DIAGNOSTIC_TEST = "diagnostic_test"

    const val PARAM_SOURCE = "source"

    /** The allow-list. Adding an event here is a product decision (consent, privacy review). */
    val allowedEvents: Map<String, AnalyticsEventSpec> =
        listOf(
            AnalyticsEventSpec(EVENT_DIAGNOSTIC_TEST, mapOf(PARAM_SOURCE to ParamType.STRING)),
        ).associateBy { it.name }

    /** Whole words (after splitting snake_case / camelCase) that evoke a position. */
    private val locationWords =
        setOf(
            "lat",
            "lng",
            "lon",
            "long",
            "latitude",
            "longitude",
            "coord",
            "coords",
            "coordinate",
            "coordinates",
            "address",
            "addr",
            "place",
            "places",
            "location",
            "loc",
            "geo",
            "gps",
            "position",
            "pos",
            "street",
            "city",
            "zip",
            "postcode",
            "venue",
            "geofence",
            "radius",
        )

    /** Fragments refused anywhere in a name, even glued to other letters (e.g. "homeaddress"). */
    private val locationFragments =
        listOf("latitude", "longitude", "address", "location", "geofence", "coordinate")

    private val coordinatePair = Regex("""-?\d{1,3}\.\d{3,}\s*[,;/ ]\s*-?\d{1,3}\.\d{3,}""")

    fun isLocationLikeName(name: String): Boolean {
        val lower = name.lowercase()
        if (locationFragments.any { lower.contains(it) }) return true
        return splitWords(name).any { it in locationWords }
    }

    private fun splitWords(name: String): List<String> =
        name
            .replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
            .split(Regex("[^A-Za-z0-9]+"))
            .filter { it.isNotEmpty() }
            .map { it.lowercase() }

    fun validate(
        eventName: String,
        params: Map<String, Any?> = emptyMap(),
    ): AnalyticsValidation {
        // A position-like parameter is refused first, whatever the event: it is the most important rule.
        params.keys.firstOrNull { isLocationLikeName(it) }?.let {
            return AnalyticsValidation.Refused(RefusalReason.LOCATION_LIKE_PARAM, it)
        }
        val spec =
            allowedEvents[eventName]
                ?: return AnalyticsValidation.Refused(RefusalReason.UNKNOWN_EVENT, eventName)
        for ((key, value) in params) {
            val type = spec.params[key] ?: return AnalyticsValidation.Refused(RefusalReason.UNKNOWN_PARAM, key)
            val typeOk =
                when (type) {
                    ParamType.STRING -> value is String
                    ParamType.LONG -> value is Long || value is Int
                    ParamType.BOOLEAN -> value is Boolean
                }
            if (!typeOk) return AnalyticsValidation.Refused(RefusalReason.WRONG_TYPE, key)
            if (value is String) {
                if (value.length > MAX_STRING_VALUE_LENGTH) {
                    return AnalyticsValidation.Refused(RefusalReason.VALUE_TOO_LONG, key)
                }
                if (coordinatePair.containsMatchIn(value)) {
                    return AnalyticsValidation.Refused(RefusalReason.LOCATION_LIKE_VALUE, key)
                }
            }
        }
        return AnalyticsValidation.Allowed
    }
}
