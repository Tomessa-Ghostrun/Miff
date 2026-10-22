package com.tomessa.miff.domain

import com.tomessa.miff.domain.analytics.AnalyticsContract
import com.tomessa.miff.domain.analytics.AnalyticsEvent
import com.tomessa.miff.domain.analytics.AnalyticsSink
import com.tomessa.miff.domain.analytics.AnalyticsTracker
import com.tomessa.miff.domain.analytics.AnalyticsValidation
import com.tomessa.miff.domain.analytics.RefusalReason
import com.tomessa.miff.domain.analytics.TrackResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Same cases as the iOS tests, from docs/contracts/analytics-events.md (version 1). */
class AnalyticsContractTest {
    private fun reason(
        event: String,
        params: Map<String, Any?> = emptyMap(),
    ): RefusalReason? = (AnalyticsContract.validate(event, params) as? AnalyticsValidation.Refused)?.reason

    // 1. A listed event with correct parameters is accepted.
    @Test
    fun acceptsEveryListedEventWithCorrectParameters() {
        val accepted =
            listOf(
                "onboarding_cta_tapped" to mapOf("slide_index" to 0),
                "onboarding_cta_tapped" to mapOf("slide_index" to 2L),
                "sign_up_completed" to mapOf("method" to "apple"),
                "sign_in_completed" to mapOf("method" to "google"),
                "miff_created" to mapOf("is_first_miff" to true),
                "miff_joined" to mapOf("is_first_miff" to false),
                "onboarding_completed" to mapOf("via" to "joined"),
                "paywall_shown" to mapOf("trigger" to "extra_miff"),
                "paywall_dismissed" to mapOf("trigger" to "extra_trusted_place"),
                "analytics_diagnostic_test" to emptyMap(),
            )
        accepted.forEach { (name, params) ->
            assertEquals("$name $params", AnalyticsValidation.Allowed, AnalyticsContract.validate(name, params))
        }
    }

    @Test
    fun allowListHasExactlyTheNineEventsOfContractVersion1() {
        assertEquals(1, AnalyticsContract.CONTRACT_VERSION)
        assertEquals(
            setOf(
                "onboarding_cta_tapped",
                "sign_up_completed",
                "sign_in_completed",
                "miff_created",
                "miff_joined",
                "onboarding_completed",
                "paywall_shown",
                "paywall_dismissed",
                "analytics_diagnostic_test",
            ),
            AnalyticsContract.allowedEvents.keys,
        )
    }

    // 2. An unknown event is refused.
    @Test
    fun refusesUnknownEvents() {
        assertEquals(RefusalReason.UNKNOWN_EVENT, reason("screen_view_secret"))
        assertEquals(RefusalReason.UNKNOWN_EVENT, reason("diagnostic_test"))
    }

    // 3. An unknown parameter is refused.
    @Test
    fun refusesUnknownParameters() {
        assertEquals(RefusalReason.UNKNOWN_PARAM, reason("miff_created", mapOf("user_name" to "Alice")))
        assertEquals(RefusalReason.UNKNOWN_PARAM, reason("analytics_diagnostic_test", mapOf("source" to "x")))
    }

    // 4. Position-like names are refused, even on a known event.
    @Test
    fun refusesPositionLikeParameterNamesOnAKnownEvent() {
        listOf(
            "lat",
            "lng",
            "latitude",
            "longitude",
            "address",
            "place",
            "location",
            "userLatitude",
            "place_name",
            "user_location",
            "home_lng",
            "GPS",
            "nearby_distance",
            "ville",
        ).forEach { name ->
            assertEquals(name, RefusalReason.LOCATION_LIKE_NAME, reason("miff_created", mapOf(name to true)))
        }
    }

    @Test
    fun positionLikeNameIsRefusedEvenOnAnUnknownEvent() {
        assertEquals(RefusalReason.LOCATION_LIKE_NAME, reason("some_unknown_event", mapOf("lat" to 48L)))
    }

    @Test
    fun doesNotMistakeHarmlessNamesForPositions() {
        listOf("slide_index", "method", "is_first_miff", "via", "trigger", "template", "platform", "slate").forEach {
            assertFalse("'$it' must not be forbidden", AnalyticsContract.isForbiddenName(it))
        }
    }

    @Test
    fun noEventNorParameterOfTheAllowListHasAForbiddenName() {
        AnalyticsContract.allowedEvents.values.forEach { spec ->
            assertFalse(spec.name, AnalyticsContract.isForbiddenName(spec.name))
            spec.params.keys.forEach { assertFalse("${spec.name}.$it", AnalyticsContract.isForbiddenName(it)) }
        }
    }

    // 5. A value outside the closed list is refused.
    @Test
    fun refusesValuesOutsideTheClosedLists() {
        assertEquals(RefusalReason.VALUE_NOT_ALLOWED, reason("sign_up_completed", mapOf("method" to "sms")))
        assertEquals(RefusalReason.VALUE_NOT_ALLOWED, reason("paywall_shown", mapOf("trigger" to "Grand Place")))
        assertEquals(RefusalReason.VALUE_NOT_ALLOWED, reason("onboarding_completed", mapOf("via" to "48.85,2.35")))
        assertEquals(RefusalReason.VALUE_NOT_ALLOWED, reason("onboarding_cta_tapped", mapOf("slide_index" to 3)))
        assertEquals(RefusalReason.VALUE_NOT_ALLOWED, reason("sign_in_completed", mapOf("method" to "x".repeat(101))))
    }

    // 6. A wrong type is refused.
    @Test
    fun refusesWrongTypes() {
        assertEquals(RefusalReason.WRONG_TYPE, reason("onboarding_cta_tapped", mapOf("slide_index" to "1")))
        assertEquals(RefusalReason.WRONG_TYPE, reason("onboarding_cta_tapped", mapOf("slide_index" to 1.0)))
        assertEquals(RefusalReason.WRONG_TYPE, reason("miff_created", mapOf("is_first_miff" to "true")))
        assertEquals(RefusalReason.WRONG_TYPE, reason("sign_up_completed", mapOf("method" to 1)))
    }

    // ---- Typed events ----

    @Test
    fun typedEventsAreAllAcceptedByTheAllowList() {
        listOf(
            AnalyticsEvent.OnboardingCtaTapped(1),
            AnalyticsEvent.SignUpCompleted(AnalyticsEvent.Method.EMAIL),
            AnalyticsEvent.SignInCompleted(AnalyticsEvent.Method.APPLE),
            AnalyticsEvent.MiffCreated(true),
            AnalyticsEvent.MiffJoined(false),
            AnalyticsEvent.OnboardingCompleted(AnalyticsEvent.OnboardingVia.CREATED),
            AnalyticsEvent.PaywallShown(AnalyticsEvent.PaywallTrigger.EXTRA_MIFF),
            AnalyticsEvent.PaywallDismissed(AnalyticsEvent.PaywallTrigger.EXTRA_TRUSTED_PLACE),
            AnalyticsEvent.DiagnosticTest,
        ).forEach { event ->
            assertEquals(event.name, AnalyticsValidation.Allowed, AnalyticsContract.validate(event.name, event.params))
        }
    }

    @Test
    fun anOutOfRangeSlideIndexIsRefusedAtTheTypedEntryPoint() {
        val tracker = AnalyticsTracker(RecordingSink()) { true }
        assertTrue(tracker.track(AnalyticsEvent.OnboardingCtaTapped(7)) is TrackResult.Refused)
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
        assertEquals(TrackResult.Disabled, tracker.track(AnalyticsEvent.DiagnosticTest))
        assertTrue(sink.logged.isEmpty())
    }

    @Test
    fun trackerSendsAValidEventWhenAnalyticsIsOn() {
        val sink = RecordingSink()
        val tracker = AnalyticsTracker(sink) { true }
        assertEquals(TrackResult.Sent, tracker.track(AnalyticsEvent.MiffCreated(true)))
        assertEquals(listOf("miff_created"), sink.logged)
    }

    @Test
    fun trackerNeverSendsAPositionEvenWhenAnalyticsIsOn() {
        val sink = RecordingSink()
        val tracker = AnalyticsTracker(sink) { true }
        val result = tracker.track(AnalyticsContract.EVENT_DIAGNOSTIC_TEST, mapOf("lat" to 1L, "lng" to 2L))
        assertTrue(result is TrackResult.Refused)
        assertTrue(sink.logged.isEmpty())
    }

    @Test
    fun trackerReportsRefusalEvenWhileAnalyticsIsOff() {
        val tracker = AnalyticsTracker(RecordingSink()) { false }
        assertTrue(tracker.track("miff_created", mapOf("address" to "x")) is TrackResult.Refused)
    }
}
