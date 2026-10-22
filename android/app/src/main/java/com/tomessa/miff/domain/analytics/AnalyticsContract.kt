package com.tomessa.miff.domain.analytics

import java.util.Locale

/**
 * Analytics allow-list shared with iOS: implements docs/contracts/analytics-events.md, contract version 1.
 * Adding or changing an event is a product decision (PR on the contract and on both platforms, PM validation).
 *
 * Pure Kotlin on purpose: unit-tested without Android.
 */
sealed interface ParamSpec {
    /** String taken from a closed list (100 characters at most, enforced by the list itself). */
    data class Text(
        val allowed: Set<String>,
    ) : ParamSpec

    /** Integer taken from a closed list. */
    data class IntValue(
        val allowed: Set<Long>,
    ) : ParamSpec

    data object Flag : ParamSpec
}

data class AnalyticsEventSpec(
    val name: String,
    val params: Map<String, ParamSpec> = emptyMap(),
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
    LOCATION_LIKE_NAME,
    WRONG_TYPE,
    VALUE_NOT_ALLOWED,
}

object AnalyticsContract {
    const val CONTRACT_VERSION = 1
    const val MAX_STRING_VALUE_LENGTH = 100

    /** Internal diagnostic event (debug builds only call it). */
    const val EVENT_DIAGNOSTIC_TEST = "analytics_diagnostic_test"

    private val signInMethods = ParamSpec.Text(setOf("email", "google", "apple"))
    private val paywallTriggers = ParamSpec.Text(setOf("extra_miff", "extra_trusted_place"))

    /** The allow-list: the nine events of contract version 1, nothing else. */
    val allowedEvents: Map<String, AnalyticsEventSpec> =
        listOf(
            AnalyticsEventSpec("onboarding_cta_tapped", mapOf("slide_index" to ParamSpec.IntValue(setOf(0L, 1L, 2L)))),
            AnalyticsEventSpec("sign_up_completed", mapOf("method" to signInMethods)),
            AnalyticsEventSpec("sign_in_completed", mapOf("method" to signInMethods)),
            AnalyticsEventSpec("miff_created", mapOf("is_first_miff" to ParamSpec.Flag)),
            AnalyticsEventSpec("miff_joined", mapOf("is_first_miff" to ParamSpec.Flag)),
            AnalyticsEventSpec("onboarding_completed", mapOf("via" to ParamSpec.Text(setOf("created", "joined")))),
            AnalyticsEventSpec("paywall_shown", mapOf("trigger" to paywallTriggers)),
            AnalyticsEventSpec("paywall_dismissed", mapOf("trigger" to paywallTriggers)),
            AnalyticsEventSpec(EVENT_DIAGNOSTIC_TEST),
        ).associateBy { it.name }

    /** Words (name split on "_", case-insensitive) that make a name refused. */
    private val forbiddenWords =
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
            "adresse",
            "place",
            "places",
            "lieu",
            "location",
            "loc",
            "position",
            "geo",
            "geofence",
            "gps",
            "region",
            "zone",
            "city",
            "ville",
            "street",
            "rue",
            "zip",
            "postal",
            "distance",
            "nearby",
        )

    /** Fragments refused anywhere in a name, even glued to another word (e.g. "userlatitude"). */
    private val forbiddenFragments =
        listOf("latitude", "longitude", "location", "address", "coordinate", "geoloc", "gps")

    fun isForbiddenName(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        return forbiddenFragments.any { lower.contains(it) } || lower.split('_').any { it in forbiddenWords }
    }

    fun validate(
        eventName: String,
        params: Map<String, Any?> = emptyMap(),
    ): AnalyticsValidation {
        // A position-like parameter is refused first, whatever the event: it is the most important rule.
        params.keys.firstOrNull { isForbiddenName(it) }?.let {
            return AnalyticsValidation.Refused(RefusalReason.LOCATION_LIKE_NAME, it)
        }
        val spec =
            allowedEvents[eventName]
                ?: return AnalyticsValidation.Refused(RefusalReason.UNKNOWN_EVENT, eventName)
        for ((key, value) in params) {
            val paramSpec = spec.params[key] ?: return AnalyticsValidation.Refused(RefusalReason.UNKNOWN_PARAM, key)
            val problem = checkValue(paramSpec, value)
            if (problem != null) return AnalyticsValidation.Refused(problem, key)
        }
        return AnalyticsValidation.Allowed
    }

    private fun checkValue(
        spec: ParamSpec,
        value: Any?,
    ): RefusalReason? =
        when (spec) {
            is ParamSpec.Text -> {
                when {
                    value !is String -> RefusalReason.WRONG_TYPE
                    value.length > MAX_STRING_VALUE_LENGTH || value !in spec.allowed -> RefusalReason.VALUE_NOT_ALLOWED
                    else -> null
                }
            }

            is ParamSpec.IntValue -> {
                when (value) {
                    is Int -> if (value.toLong() in spec.allowed) null else RefusalReason.VALUE_NOT_ALLOWED
                    is Long -> if (value in spec.allowed) null else RefusalReason.VALUE_NOT_ALLOWED
                    else -> RefusalReason.WRONG_TYPE
                }
            }

            ParamSpec.Flag -> {
                if (value is Boolean) null else RefusalReason.WRONG_TYPE
            }
        }
}
