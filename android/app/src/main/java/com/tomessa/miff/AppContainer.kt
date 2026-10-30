package com.tomessa.miff

import android.content.Context
import android.os.SystemClock
import com.tomessa.miff.data.auth.AccountRepository
import com.tomessa.miff.data.auth.AndroidNetworkMonitor
import com.tomessa.miff.data.auth.AuthGateway
import com.tomessa.miff.data.auth.FirebaseAuthGateway
import com.tomessa.miff.data.auth.FirebasePasswordResetGateway
import com.tomessa.miff.data.auth.FirestoreAccountRepository
import com.tomessa.miff.data.auth.FirestoreCachePurger
import com.tomessa.miff.data.auth.FirestoreUserProfileRepository
import com.tomessa.miff.data.auth.NetworkMonitor
import com.tomessa.miff.data.auth.PasswordResetGateway
import com.tomessa.miff.data.auth.SharedPreferencesPendingConsentStore
import com.tomessa.miff.data.auth.UserProfileRepository
import com.tomessa.miff.data.onboarding.OnboardingFlags
import com.tomessa.miff.data.privacy.FirebaseAnalyticsSink
import com.tomessa.miff.data.privacy.FirebaseAnalyticsSwitch
import com.tomessa.miff.data.privacy.FirebaseCrashlyticsSwitch
import com.tomessa.miff.data.privacy.PrivacySettings
import com.tomessa.miff.data.privacy.SharedPreferencesBooleanStore
import com.tomessa.miff.data.session.SessionManager
import com.tomessa.miff.domain.analytics.AnalyticsTracker
import com.tomessa.miff.domain.auth.ResendCooldown
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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

    val authGateway: AuthGateway = FirebaseAuthGateway()

    val networkMonitor: NetworkMonitor = AndroidNetworkMonitor(context)

    /** "Mot de passe oublié" (AUTH-2): the e-mail is sent by Firebase; the new password is chosen on the web page (AUTH-W). */
    val passwordResetGateway: PasswordResetGateway = FirebasePasswordResetGateway()

    /** Pause between two sends of the reset e-mail, kept here so that leaving the screen does not give a free resend. */
    val passwordResetCooldown: ResendCooldown = ResendCooldown(nowMillis = SystemClock::elapsedRealtime)

    /** Used by the profile screen of the Onboarding ticket (one write, see ProfileDocument). */
    val userProfiles: UserProfileRepository = FirestoreUserProfileRepository()

    /** Session flags written to disk before returning: "returning user", "Firestore purge pending". */
    private val sessionFlags = SharedPreferencesBooleanStore(context, name = "miff_session", synchronous = true)

    val onboardingFlags: OnboardingFlags = OnboardingFlags(sessionFlags)

    val accountRepository: AccountRepository = FirestoreAccountRepository()

    val sessionManager: SessionManager =
        SessionManager(
            gateway = authGateway,
            accounts = accountRepository,
            pending = SharedPreferencesPendingConsentStore(context),
            flags = sessionFlags,
            privacy = privacySettings,
            tracker = analyticsTracker,
            cache = FirestoreCachePurger(flags = sessionFlags),
            // Nothing to stop yet. The feed (listeners), geofencing (monitoring, status writes) and push (token)
            // tickets register their own SessionTeardown here, so that a sign-out stops them (ticket 7.6).
            teardown = emptyList(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        )
}
