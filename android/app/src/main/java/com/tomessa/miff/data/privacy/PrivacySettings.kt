package com.tomessa.miff.data.privacy

import android.content.Context
import android.os.Bundle
import androidx.core.content.edit
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tomessa.miff.domain.analytics.AnalyticsSink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Small boolean store (SharedPreferences in the app, in memory in tests). Not sensitive data. */
interface BooleanStore {
    fun get(
        key: String,
        default: Boolean,
    ): Boolean

    fun put(
        key: String,
        value: Boolean,
    )
}

/** Switch of an SDK data collection (Analytics, Crashlytics). */
interface CollectionSwitch {
    fun setEnabled(enabled: Boolean)
}

/**
 * User choices for Analytics and crash reports. Both are OFF by default and the manifest also disables them
 * at startup, so nothing is collected before the user opts in (the real consent screen comes with onboarding;
 * for now the only way to switch them on is the debug diagnostic screen).
 *
 * The choice is kept on the device and re-applied at every start, because the manifest always starts disabled.
 */
class PrivacySettings(
    private val store: BooleanStore,
    private val analytics: CollectionSwitch,
    private val crashReports: CollectionSwitch,
) {
    private val analyticsState = MutableStateFlow(store.get(KEY_ANALYTICS, false))
    private val crashState = MutableStateFlow(store.get(KEY_CRASH, false))

    val analyticsEnabled: StateFlow<Boolean> = analyticsState.asStateFlow()
    val crashReportsEnabled: StateFlow<Boolean> = crashState.asStateFlow()

    /** Called once from Application.onCreate: pushes the stored choices to the SDKs. */
    fun applyStoredChoices() {
        analytics.setEnabled(analyticsState.value)
        crashReports.setEnabled(crashState.value)
    }

    fun setAnalyticsEnabled(enabled: Boolean) {
        store.put(KEY_ANALYTICS, enabled)
        analyticsState.value = enabled
        analytics.setEnabled(enabled)
    }

    fun setCrashReportsEnabled(enabled: Boolean) {
        store.put(KEY_CRASH, enabled)
        crashState.value = enabled
        crashReports.setEnabled(enabled)
    }

    companion object {
        const val KEY_ANALYTICS = "analytics_enabled"
        const val KEY_CRASH = "crash_reports_enabled"
    }
}

class SharedPreferencesBooleanStore(
    context: Context,
) : BooleanStore {
    private val prefs = context.applicationContext.getSharedPreferences("miff_privacy", Context.MODE_PRIVATE)

    override fun get(
        key: String,
        default: Boolean,
    ): Boolean = prefs.getBoolean(key, default)

    override fun put(
        key: String,
        value: Boolean,
    ) {
        prefs.edit { putBoolean(key, value) }
    }
}

class FirebaseAnalyticsSwitch(
    context: Context,
) : CollectionSwitch {
    private val analytics = FirebaseAnalytics.getInstance(context.applicationContext)

    override fun setEnabled(enabled: Boolean) {
        analytics.setAnalyticsCollectionEnabled(enabled)
    }
}

class FirebaseCrashlyticsSwitch : CollectionSwitch {
    override fun setEnabled(enabled: Boolean) {
        val crashlytics = FirebaseCrashlytics.getInstance()
        crashlytics.setCrashlyticsCollectionEnabled(enabled)
        // Without consent, a report left on disk must never be sent later by surprise.
        if (!enabled) crashlytics.deleteUnsentReports()
    }
}

/** Hands validated events to Firebase Analytics. Only used through AnalyticsTracker. */
class FirebaseAnalyticsSink(
    context: Context,
) : AnalyticsSink {
    private val analytics = FirebaseAnalytics.getInstance(context.applicationContext)

    override fun log(
        eventName: String,
        params: Map<String, Any>,
    ) {
        val bundle = Bundle()
        params.forEach { (key, value) ->
            when (value) {
                is String -> bundle.putString(key, value)
                is Long -> bundle.putLong(key, value)
                is Int -> bundle.putLong(key, value.toLong())
                is Boolean -> bundle.putLong(key, if (value) 1L else 0L)
            }
        }
        analytics.logEvent(eventName, bundle)
    }
}
