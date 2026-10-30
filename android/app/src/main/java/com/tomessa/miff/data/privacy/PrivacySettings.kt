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
    /** `false` = no consent: stop collecting AND forget what was kept for sending (crash reports left on disk). */
    fun setEnabled(enabled: Boolean)

    /**
     * Stop collecting for now, WITHOUT deleting anything already kept for sending. Used at every start of the app
     * (the choice of the account is not known yet), so that the report of the previous crash survives until the
     * account says yes (then it is sent) or no (then [setEnabled] with `false` deletes it).
     */
    fun pause()
}

/**
 * User choices for Analytics and crash reports. Both are OFF by default and the manifest also disables them
 * at startup, so nothing is collected before the user opts in.
 *
 * The choice that counts is the one of the ACCOUNT (`users/{uid}.analyticsConsent`), applied by the SessionManager
 * once it has been read. The value kept on the device is deliberately NOT re-applied at a cold start.
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

    /**
     * Cold start, and every moment the account is being (re)read: collect nothing, but keep the crash report of the
     * previous run on disk (see [CollectionSwitch.pause]); the account's answer then sends it or deletes it.
     */
    fun pauseCollection() {
        store.put(KEY_ANALYTICS, false)
        store.put(KEY_CRASH, false)
        analyticsState.value = false
        crashState.value = false
        analytics.pause()
        crashReports.pause()
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

    /**
     * The single switch of the "statistics and crash reports" consent (one choice, two SDKs): applies the choice of
     * the ACCOUNT (read from `users/{uid}.analyticsConsent`, or given by the tap at sign-up) to both collections at once.
     * `false` cuts both AND deletes the crash reports not yet sent: it is the withdrawal of the consent, the sign-out
     * and the refusal. For "not known yet" use [pauseCollection], which deletes nothing.
     */
    fun applyAccountConsent(enabled: Boolean) {
        setAnalyticsEnabled(enabled)
        setCrashReportsEnabled(enabled)
    }

    companion object {
        const val KEY_ANALYTICS = "analytics_enabled"
        const val KEY_CRASH = "crash_reports_enabled"
    }
}

/**
 * [synchronous] writes to disk before returning (`commit`), for flags that must survive a process kill right after
 * (for example "a cache purge is pending").
 */
class SharedPreferencesBooleanStore(
    context: Context,
    name: String = "miff_privacy",
    private val synchronous: Boolean = false,
) : BooleanStore {
    private val prefs = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    override fun get(
        key: String,
        default: Boolean,
    ): Boolean = prefs.getBoolean(key, default)

    override fun put(
        key: String,
        value: Boolean,
    ) {
        prefs.edit(commit = synchronous) { putBoolean(key, value) }
    }
}

class FirebaseAnalyticsSwitch(
    context: Context,
) : CollectionSwitch {
    private val analytics = FirebaseAnalytics.getInstance(context.applicationContext)

    override fun setEnabled(enabled: Boolean) {
        analytics.setAnalyticsCollectionEnabled(enabled)
    }

    override fun pause() = setEnabled(false)
}

/** The two Crashlytics calls we need, so that the rule "pause keeps the reports, refusal deletes them" is testable. */
interface CrashReportsClient {
    fun setCollectionEnabled(enabled: Boolean)

    fun deleteUnsentReports()
}

class FirebaseCrashReportsClient : CrashReportsClient {
    private val crashlytics get() = FirebaseCrashlytics.getInstance()

    override fun setCollectionEnabled(enabled: Boolean) = crashlytics.setCrashlyticsCollectionEnabled(enabled)

    override fun deleteUnsentReports() = crashlytics.deleteUnsentReports()
}

class FirebaseCrashlyticsSwitch(
    private val client: CrashReportsClient = FirebaseCrashReportsClient(),
) : CollectionSwitch {
    override fun setEnabled(enabled: Boolean) {
        client.setCollectionEnabled(enabled)
        // Without consent, a report left on disk must never be sent later by surprise.
        if (!enabled) client.deleteUnsentReports()
    }

    /** Collection off, but the report of the previous crash stays on disk until the account has answered. */
    override fun pause() = client.setCollectionEnabled(false)
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
