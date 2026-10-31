package com.tomessa.miff

import android.content.Context
import android.os.SystemClock
import com.google.firebase.firestore.FirebaseFirestore
import com.tomessa.miff.data.auth.AccountRepository
import com.tomessa.miff.data.auth.AndroidNetworkMonitor
import com.tomessa.miff.data.auth.AuthGateway
import com.tomessa.miff.data.auth.CachePurger
import com.tomessa.miff.data.auth.FirebaseAuthGateway
import com.tomessa.miff.data.auth.FirebasePasswordResetGateway
import com.tomessa.miff.data.auth.FirestoreAccountRepository
import com.tomessa.miff.data.auth.FirestoreCachePurger
import com.tomessa.miff.data.auth.FirestoreUserProfileRepository
import com.tomessa.miff.data.auth.NetworkMonitor
import com.tomessa.miff.data.auth.PasswordResetGateway
import com.tomessa.miff.data.auth.PendingConsentStore
import com.tomessa.miff.data.auth.SharedPreferencesPendingConsentStore
import com.tomessa.miff.data.auth.UserProfileRepository
import com.tomessa.miff.data.miff.AndroidLocationAccessProbe
import com.tomessa.miff.data.miff.FirestoreMiffObserver
import com.tomessa.miff.data.miff.FirestoreMiffRepository
import com.tomessa.miff.data.miff.FirestoreMiffStore
import com.tomessa.miff.data.miff.MiffDirectory
import com.tomessa.miff.data.miff.MiffRepository
import com.tomessa.miff.data.miff.SharedPreferencesActiveMiffStore
import com.tomessa.miff.data.onboarding.OnboardingFlags
import com.tomessa.miff.data.onboarding.ProfileDraftStore
import com.tomessa.miff.data.onboarding.SharedPreferencesProfileDraftStore
import com.tomessa.miff.data.onboarding.asTeardown
import com.tomessa.miff.data.privacy.FirebaseAnalyticsSink
import com.tomessa.miff.data.privacy.FirebaseAnalyticsSwitch
import com.tomessa.miff.data.privacy.FirebaseCrashlyticsSwitch
import com.tomessa.miff.data.privacy.PrivacySettings
import com.tomessa.miff.data.privacy.SharedPreferencesBooleanStore
import com.tomessa.miff.data.profile.AndroidAvatarCodec
import com.tomessa.miff.data.profile.AvatarCapture
import com.tomessa.miff.data.profile.CurrentProfile
import com.tomessa.miff.data.profile.FirestoreMyProfileRepository
import com.tomessa.miff.data.profile.MyProfileRepository
import com.tomessa.miff.data.session.SessionManager
import com.tomessa.miff.data.settings.AndroidLanguageStore
import com.tomessa.miff.data.settings.LanguageStore
import com.tomessa.miff.domain.analytics.AnalyticsTracker
import com.tomessa.miff.domain.auth.ResendCooldown
import com.tomessa.miff.domain.profile.AvatarCodec
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

    /** Used by the profile steps of the Onboarding ticket (one confirmed write, see ProfileDocument). */
    val userProfiles: UserProfileRepository = FirestoreUserProfileRepository()

    /** What the person typed in the profile steps, on the device only, erased once the server confirmed (ticket 6.2). */
    val profileDrafts: ProfileDraftStore = SharedPreferencesProfileDraftStore(context)

    /** Photo of the profile: Photo Picker / camera of the system, square crop, JPEG without any metadata. */
    val avatarCodec: AvatarCodec = AndroidAvatarCodec(context)

    /** Reading and changing the profile once it exists (Settings > Mes informations). */
    val myProfiles: MyProfileRepository = FirestoreMyProfileRepository()

    /** The profile of the person signed in, in memory for the session only (Profile tab, Mes informations). */
    val currentProfile: CurrentProfile =
        CurrentProfile(repository = myProfiles, currentUid = { authGateway.currentUid })

    /** Language chosen in Settings (French, English, or the phone's), applied at once. */
    val languageStore: LanguageStore = AndroidLanguageStore(context)

    /** Acceptance of the terms kept on the device between the sign-up and the creation of the profile. */
    val pendingConsent: PendingConsentStore = SharedPreferencesPendingConsentStore(context)

    init {
        // A camera photo left in the cache by a process killed at the wrong moment must not stay there.
        AvatarCapture.deleteAll(context)
    }

    /** Session flags written to disk before returning: "returning user", "Firestore purge pending". */
    private val sessionFlags = SharedPreferencesBooleanStore(context, name = "miff_session", synchronous = true)

    val onboardingFlags: OnboardingFlags = OnboardingFlags(sessionFlags)

    /** Local Firestore cache purge (sign-out, refusal of the panel, and loss of access to a Miff). One instance for all. */
    val cachePurger: CachePurger = FirestoreCachePurger(flags = sessionFlags)

    /** Create, join, rename, stop / resume sharing, clean-up after a loss of access, live view of a Miff (Miff ticket). */
    val miffRepository: MiffRepository =
        FirestoreMiffRepository(
            store = FirestoreMiffStore(),
            observer = FirestoreMiffObserver(),
            location = AndroidLocationAccessProbe(context),
            // An automatic id of Firestore (20 characters), drawn on the phone: no network needed to draw it.
            newMiffId = {
                FirebaseFirestore
                    .getInstance()
                    .collection("miffs")
                    .document()
                    .id
            },
        )

    /** The Miffs of the person signed in, in memory for the session (Feed, Profile tab); also deals with a loss of access. */
    val miffDirectory: MiffDirectory =
        MiffDirectory(
            repository = miffRepository,
            currentUid = { authGateway.currentUid },
            activeStore = SharedPreferencesActiveMiffStore(context),
            cache = cachePurger,
        )
    val accountRepository: AccountRepository = FirestoreAccountRepository()

    val sessionManager: SessionManager =
        SessionManager(
            gateway = authGateway,
            accounts = accountRepository,
            pending = pendingConsent,
            flags = sessionFlags,
            privacy = privacySettings,
            tracker = analyticsTracker,
            cache = cachePurger,
            // The profile draft (names, photo, phone number) goes with the session. The feed (listeners), geofencing
            // (monitoring, status writes) and push (token) tickets add their own SessionTeardown here, so that a
            // sign-out stops them (ticket 7.6).
            teardown = listOf(profileDrafts.asTeardown(), currentProfile, miffDirectory),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            drafts = profileDrafts,
        )
}
