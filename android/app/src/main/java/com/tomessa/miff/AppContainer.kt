package com.tomessa.miff

import android.content.Context
import com.tomessa.miff.data.privacy.FirebaseAnalyticsSink
import com.tomessa.miff.data.privacy.FirebaseAnalyticsSwitch
import com.tomessa.miff.data.privacy.FirebaseCrashlyticsSwitch
import com.tomessa.miff.data.privacy.PrivacySettings
import com.tomessa.miff.data.privacy.SharedPreferencesBooleanStore
import com.tomessa.miff.domain.analytics.AnalyticsTracker

/**
 * Hand-written dependency container (no Hilt/KSP: keeps the build light on an 8 GB workstation).
 * Created once by [MiffApplication]. Hilt can replace it if the graph grows (justify in the PR).
 */
class AppContainer(
    context: Context,
) {
    val privacySettings: PrivacySettings =
        PrivacySettings(
            store = SharedPreferencesBooleanStore(context),
            analytics = FirebaseAnalyticsSwitch(context),
            crashReports = FirebaseCrashlyticsSwitch(),
        )

    val analyticsTracker: AnalyticsTracker =
        AnalyticsTracker(
            sink = FirebaseAnalyticsSink(context),
            isEnabled = { privacySettings.analyticsEnabled.value },
        )
}
