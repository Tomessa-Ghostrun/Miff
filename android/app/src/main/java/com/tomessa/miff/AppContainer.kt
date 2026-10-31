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
import com.tomessa.miff.data.privacy.AnalyticsConsentRepository
import com.tomessa.miff.data.privacy.ConsentWithdrawals
import com.tomessa.miff.data.privacy.FirebaseAnalyticsSink
import com.tomessa.miff.data.privacy.FirebaseAnalyticsSwitch
import com.tomessa.miff.data.privacy.FirebaseCrashlyticsSwitch
import com.tomessa.miff.data.privacy.FirestoreAnalyticsConsentRepository
import com.tomessa.miff.data.privacy.PendingWithdrawals
import com.tomessa.miff.data.privacy.PrivacySettings
import com.tomessa.miff.data.privacy.SharedPreferencesBooleanStore
import com.tomessa.miff.data.privacy.SharedPreferencesWithdrawalStorage
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
import com.tomessa.miff.domain.miff.JoinAttemptLimiter
import com.tomessa.miff.domain.profile.AvatarCodec
import com.tomessa.miff.startup.StartupTrace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Hand-written dependency container (no Hilt/KSP: keeps the build light on an 8 GB workstation).
 * Created once by [MiffApplication]. Hilt can replace it if the graph grows (justify in the PR).
 *
 * EVERYTHING here is built on first use (`lazy`), not when the container is created: building it used to open several
 * private files and initialise Firebase Authentication, Analytics and Firestore on the main thread before the first
 * screen could even be created, which kept the system launch screen ("M" alone) up for longer. Now only what a moment
 * really needs is built at that moment; [MiffApplication] builds the rest on a background thread, in parallel.
 * Nothing depends on the order in which the members are first used (the graph has no cycle, `lazy` is thread-safe).
 */
class AppContainer(
    private val context: Context,
) {
    val privacySettings: PrivacySettings by lazy {
        StartupTrace.section("Miff.container.privacySettings") {
            PrivacySettings(
                store = SharedPreferencesBooleanStore(context),
                analytics = FirebaseAnalyticsSwitch(context),
                crashReports = FirebaseCrashlyticsSwitch(),
            )
        }
    }

    /** Writes the choice "statistics and crash reports" to the account (Settings > Confidentialité). */
    val analyticsConsents: AnalyticsConsentRepository by lazy { FirestoreAnalyticsConsentRepository() }

    val analyticsTracker: AnalyticsTracker by lazy {
        AnalyticsTracker(
            sink = FirebaseAnalyticsSink(context),
            isEnabled = { privacySettings.analyticsEnabled.value },
        )
    }

    val authGateway: AuthGateway by lazy { FirebaseAuthGateway() }

    val networkMonitor: NetworkMonitor by lazy { AndroidNetworkMonitor(context) }

    /**
     * Withdrawals of the statistics consent not yet recorded in the account (no network at the time): they keep the collection
     * off at every start and are recorded in the background as soon as the network is back (see [ConsentWithdrawals]).
     */
    val pendingWithdrawals: PendingWithdrawals by lazy {
        PendingWithdrawals(SharedPreferencesWithdrawalStorage(context), nowMillis = System::currentTimeMillis)
    }

    val consentWithdrawals: ConsentWithdrawals by lazy {
        ConsentWithdrawals(
            pending = pendingWithdrawals,
            consents = analyticsConsents,
            network = networkMonitor,
            currentUid = { authGateway.currentUid },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    }

    /** "Mot de passe oublié" (AUTH-2): the e-mail is sent by Firebase; the new password is chosen on the web page (AUTH-W). */
    val passwordResetGateway: PasswordResetGateway by lazy { FirebasePasswordResetGateway() }

    /** Pause between two sends of the reset e-mail, kept here so that leaving the screen does not give a free resend. */
    val passwordResetCooldown: ResendCooldown by lazy { ResendCooldown(nowMillis = SystemClock::elapsedRealtime) }

    /** Used by the profile steps of the Onboarding ticket (one confirmed write, see ProfileDocument). */
    val userProfiles: UserProfileRepository by lazy { FirestoreUserProfileRepository() }

    /** What the person typed in the profile steps, on the device only, erased once the server confirmed (ticket 6.2). */
    val profileDrafts: ProfileDraftStore by lazy { SharedPreferencesProfileDraftStore(context) }

    /** Photo of the profile: Photo Picker / camera of the system, square crop, JPEG without any metadata. */
    val avatarCodec: AvatarCodec by lazy { AndroidAvatarCodec(context) }

    /** Reading and changing the profile once it exists (Settings > Mes informations). */
    val myProfiles: MyProfileRepository by lazy { FirestoreMyProfileRepository() }

    /** The profile of the person signed in, in memory for the session only (Profile tab, Mes informations). */
    val currentProfile: CurrentProfile by lazy {
        CurrentProfile(repository = myProfiles, currentUid = { authGateway.currentUid })
    }

    /** Language chosen in Settings (French, English, or the phone's), applied at once. */
    val languageStore: LanguageStore by lazy { AndroidLanguageStore(context) }

    /** Acceptance of the terms kept on the device between the sign-up and the creation of the profile. */
    val pendingConsent: PendingConsentStore by lazy { SharedPreferencesPendingConsentStore(context) }

    /** Session flags written to disk before returning: "returning user", "Firestore purge pending". */
    private val sessionFlags by lazy {
        SharedPreferencesBooleanStore(
            context,
            name = "miff_session",
            synchronous = true,
        )
    }

    val onboardingFlags: OnboardingFlags by lazy { OnboardingFlags(sessionFlags) }

    val accountRepository: AccountRepository by lazy { FirestoreAccountRepository() }

    /** Local Firestore cache purge (sign-out, refusal of the panel, and loss of access to a Miff). One instance for all. */
    val cachePurger: CachePurger by lazy { FirestoreCachePurger(flags = sessionFlags) }

    /**
     * Create, join, rename, stop / resume sharing, clean-up after a loss of access, live view of a Miff (Miff ticket).
     * Built on first use. Constructing it never reaches Firestore (the store and the observer take it through a function,
     * only called when a request is made), and every request comes from a screen shown after [MiffApplication.startSession]
     * has resolved the session, hence after the cache purge of a failed sign-out.
     */
    val miffRepository: MiffRepository by lazy {
        FirestoreMiffRepository(
            store = FirestoreMiffStore(),
            observer = FirestoreMiffObserver(),
            location = AndroidLocationAccessProbe(context),
            cache = cachePurger,
            // An automatic id of Firestore (20 characters), drawn on the phone: no network needed to draw it.
            newMiffId = {
                FirebaseFirestore
                    .getInstance()
                    .collection("miffs")
                    .document()
                    .id
            },
        )
    }

    /**
     * The pause after 5 wrong codes lives as long as the process, not as long as the screen: leaving and coming back does not
     * give a free new series. An aid of the interface, not a protection (ticket Miff 5.3).
     */
    val joinLimiter: JoinAttemptLimiter by lazy { JoinAttemptLimiter(nowMillis = SystemClock::elapsedRealtime) }

    /** The Miffs of the person signed in, in memory for the session (Feed, Profile tab); also deals with a loss of access. */
    val miffDirectory: MiffDirectory by lazy {
        MiffDirectory(
            repository = miffRepository,
            currentUid = { authGateway.currentUid },
            activeStore = SharedPreferencesActiveMiffStore(context),
            cache = cachePurger,
        )
    }

    val sessionManager: SessionManager by lazy {
        StartupTrace.section("Miff.container.sessionManager") {
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
                teardown = listOf(profileDrafts.asTeardown(), currentProfile, consentWithdrawals, miffDirectory),
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
                drafts = profileDrafts,
                withdrawals = pendingWithdrawals,
                onAccountResolved = consentWithdrawals::resume,
            )
        }
    }

    /**
     * A camera photo left in the cache by a process killed at the wrong moment must not stay there. Run at every start
     * (it used to run in the constructor, on the main thread): file work, so call it from a background thread.
     */
    fun deleteCameraLeftovers() {
        AvatarCapture.deleteAll(context)
    }

    /**
     * Builds, on the calling thread, what the first screens will need, so that the main thread finds it ready. Called from
     * a background thread by [MiffApplication]; harmless anywhere else. Nothing here touches Firestore (its first access
     * stays behind the cache purge that the session start performs, see [SessionManager.start]).
     */
    fun warmUp() {
        StartupTrace.section("Miff.container.warmUp") {
            // The graph of the session: opens the private files and initialises Firebase Authentication (reads the stored
            // sign-in) off the main thread.
            sessionManager
            authGateway.currentUid
            onboardingFlags
            languageStore
            analyticsTracker
            networkMonitor
        }
    }
}
