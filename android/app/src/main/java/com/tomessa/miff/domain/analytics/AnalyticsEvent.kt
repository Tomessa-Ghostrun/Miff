package com.tomessa.miff.domain.analytics

/**
 * The typed entry point for application code (contract: docs/contracts/analytics-events.md). Screens and
 * view models send these through [AnalyticsTracker.track]; they never call the Analytics SDK directly.
 * The values are closed lists, so a wrong value cannot even be written.
 */
sealed class AnalyticsEvent(
    val name: String,
    val params: Map<String, Any> = emptyMap(),
) {
    enum class Method(
        val value: String,
    ) {
        EMAIL("email"),
        GOOGLE("google"),
        APPLE("apple"),
    }

    enum class OnboardingVia(
        val value: String,
    ) {
        CREATED("created"),
        JOINED("joined"),
    }

    enum class PaywallTrigger(
        val value: String,
    ) {
        EXTRA_MIFF("extra_miff"),
        EXTRA_TRUSTED_PLACE("extra_trusted_place"),
    }

    /** [slideIndex] must be 0, 1 or 2 (anything else is refused by the allow-list). */
    class OnboardingCtaTapped(
        slideIndex: Int,
    ) : AnalyticsEvent(
            "onboarding_cta_tapped",
            mapOf("slide_index" to slideIndex),
        )

    class SignUpCompleted(
        method: Method,
    ) : AnalyticsEvent("sign_up_completed", mapOf("method" to method.value))

    class SignInCompleted(
        method: Method,
    ) : AnalyticsEvent("sign_in_completed", mapOf("method" to method.value))

    class MiffCreated(
        isFirstMiff: Boolean,
    ) : AnalyticsEvent("miff_created", mapOf("is_first_miff" to isFirstMiff))

    class MiffJoined(
        isFirstMiff: Boolean,
    ) : AnalyticsEvent("miff_joined", mapOf("is_first_miff" to isFirstMiff))

    class OnboardingCompleted(
        via: OnboardingVia,
    ) : AnalyticsEvent("onboarding_completed", mapOf("via" to via.value))

    class PaywallShown(
        trigger: PaywallTrigger,
    ) : AnalyticsEvent("paywall_shown", mapOf("trigger" to trigger.value))

    class PaywallDismissed(
        trigger: PaywallTrigger,
    ) : AnalyticsEvent(
            "paywall_dismissed",
            mapOf("trigger" to trigger.value),
        )

    /** Test button of the debug diagnostic screen. */
    data object DiagnosticTest : AnalyticsEvent(AnalyticsContract.EVENT_DIAGNOSTIC_TEST)
}
