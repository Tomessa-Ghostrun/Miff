package com.tomessa.miff.domain.analytics

/** Where validated events end up (Firebase Analytics in production, a fake in tests). */
interface AnalyticsSink {
    fun log(
        eventName: String,
        params: Map<String, Any>,
    )
}

sealed interface TrackResult {
    /** The event passed the allow-list and was handed to the SDK. */
    data object Sent : TrackResult

    /** The event is valid but the user has not enabled Analytics: nothing is sent. */
    data object Disabled : TrackResult

    data class Refused(
        val validation: AnalyticsValidation.Refused,
    ) : TrackResult
}

/**
 * The only way the app should log an analytics event. Order of checks: allow-list first (so a refused event is
 * reported as refused even when Analytics is off), then user consent.
 */
class AnalyticsTracker(
    private val sink: AnalyticsSink,
    private val isEnabled: () -> Boolean,
) {
    fun track(
        eventName: String,
        params: Map<String, Any> = emptyMap(),
    ): TrackResult =
        when (val validation = AnalyticsContract.validate(eventName, params)) {
            is AnalyticsValidation.Refused -> {
                TrackResult.Refused(validation)
            }

            AnalyticsValidation.Allowed -> {
                if (isEnabled()) {
                    sink.log(eventName, params)
                    TrackResult.Sent
                } else {
                    TrackResult.Disabled
                }
            }
        }
}
