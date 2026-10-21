package com.tomessa.miff.domain

import com.tomessa.miff.domain.analytics.AnalyticsContract
import com.tomessa.miff.domain.analytics.AnalyticsSink
import com.tomessa.miff.domain.analytics.AnalyticsTracker
import com.tomessa.miff.domain.analytics.AnalyticsValidation
import com.tomessa.miff.domain.analytics.RefusalReason
import com.tomessa.miff.domain.analytics.TrackResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsContractTest {
    private val event = AnalyticsContract.EVENT_DIAGNOSTIC_TEST

    private fun refusalReason(params: Map<String, Any?>): RefusalReason? =
        (AnalyticsContract.validate(event, params) as? AnalyticsValidation.Refused)?.reason

    @Test
    fun allowsTheDiagnosticEventWithItsDeclaredParameter() {
        val result = AnalyticsContract.validate(event, mapOf(AnalyticsContract.PARAM_SOURCE to "diagnostic_screen"))
        assertEquals(AnalyticsValidation.Allowed, result)
    }

    @Test
    fun allowsTheDiagnosticEventWithoutParameters() {
        assertEquals(AnalyticsValidation.Allowed, AnalyticsContract.validate(event))
    }

    @Test
    fun refusesPositionParametersByName() {
        val names =
            listOf(
                "lat",
                "lng",
                "lon",
                "latitude",
                "longitude",
                "address",
                "place",
                "location",
                "coords",
                "gps",
                "geo",
                "position",
            )
        names.forEach { name ->
            assertEquals(
                "'$name' must be refused as position-like",
                RefusalReason.LOCATION_LIKE_PARAM,
                refusalReason(
                    mapOf(name to 1L),
                ),
            )
        }
    }

    @Test
    fun refusesPositionParametersWrittenInOtherStyles() {
        listOf("userLat", "home_lat", "LNG", "placeName", "place_id", "homeAddress", "last_known_location", "Latitude")
            .forEach { name ->
                assertEquals(name, RefusalReason.LOCATION_LIKE_PARAM, refusalReason(mapOf(name to "x")))
            }
    }

    @Test
    fun positionLikeParameterIsRefusedEvenOnAnUnknownEvent() {
        val result = AnalyticsContract.validate("some_unknown_event", mapOf("lat" to 48.85))
        assertEquals(RefusalReason.LOCATION_LIKE_PARAM, (result as AnalyticsValidation.Refused).reason)
    }

    @Test
    fun doesNotMistakeHarmlessWordsForPositions() {
        listOf("template", "platform", "translation", "relation", "source", "screen_name", "slate").forEach {
            assertFalse("'$it' is not position-like", AnalyticsContract.isLocationLikeName(it))
        }
        assertTrue(AnalyticsContract.isLocationLikeName("lat"))
    }

    @Test
    fun refusesUnknownEvents() {
        val result = AnalyticsContract.validate("screen_view_secret")
        assertEquals(RefusalReason.UNKNOWN_EVENT, (result as AnalyticsValidation.Refused).reason)
    }

    @Test
    fun refusesParametersThatAreNotInTheAllowList() {
        assertEquals(RefusalReason.UNKNOWN_PARAM, refusalReason(mapOf("user_name" to "Alice")))
    }

    @Test
    fun refusesWrongValueType() {
        assertEquals(RefusalReason.WRONG_TYPE, refusalReason(mapOf(AnalyticsContract.PARAM_SOURCE to 12L)))
    }

    @Test
    fun refusesTooLongStrings() {
        val tooLong = "a".repeat(AnalyticsContract.MAX_STRING_VALUE_LENGTH + 1)
        assertEquals(RefusalReason.VALUE_TOO_LONG, refusalReason(mapOf(AnalyticsContract.PARAM_SOURCE to tooLong)))
    }

    @Test
    fun refusesValuesThatLookLikeCoordinates() {
        assertEquals(
            RefusalReason.LOCATION_LIKE_VALUE,
            refusalReason(mapOf(AnalyticsContract.PARAM_SOURCE to "48.856614, 2.352222")),
        )
        assertEquals(
            RefusalReason.LOCATION_LIKE_VALUE,
            refusalReason(mapOf(AnalyticsContract.PARAM_SOURCE to "-33.8688;151.2093")),
        )
    }

    @Test
    fun noEventInTheAllowListDeclaresAPositionLikeParameter() {
        AnalyticsContract.allowedEvents.values.forEach { spec ->
            assertFalse(spec.name, AnalyticsContract.isLocationLikeName(spec.name))
            spec.params.keys.forEach { assertFalse("${spec.name}.$it", AnalyticsContract.isLocationLikeName(it)) }
        }
    }

    // ---- AnalyticsTracker: allow-list first, then consent ----

    private class RecordingSink : AnalyticsSink {
        val logged = mutableListOf<String>()

        override fun log(
            eventName: String,
            params: Map<String, Any>,
        ) {
            logged += eventName
        }
    }

    @Test
    fun trackerSendsNothingWhileAnalyticsIsOff() {
        val sink = RecordingSink()
        val tracker = AnalyticsTracker(sink) { false }
        assertEquals(TrackResult.Disabled, tracker.track(event))
        assertTrue(sink.logged.isEmpty())
    }

    @Test
    fun trackerSendsAValidEventWhenAnalyticsIsOn() {
        val sink = RecordingSink()
        val tracker = AnalyticsTracker(sink) { true }
        assertEquals(TrackResult.Sent, tracker.track(event, mapOf(AnalyticsContract.PARAM_SOURCE to "x")))
        assertEquals(listOf(event), sink.logged)
    }

    @Test
    fun trackerNeverSendsAPositionEvenWhenAnalyticsIsOn() {
        val sink = RecordingSink()
        val tracker = AnalyticsTracker(sink) { true }
        val result = tracker.track(event, mapOf("lat" to 1L, "lng" to 2L))
        assertTrue(result is TrackResult.Refused)
        assertTrue(sink.logged.isEmpty())
    }

    @Test
    fun trackerReportsRefusalEvenWhileAnalyticsIsOff() {
        val tracker = AnalyticsTracker(RecordingSink()) { false }
        assertTrue(tracker.track(event, mapOf("address" to "x")) is TrackResult.Refused)
    }
}
