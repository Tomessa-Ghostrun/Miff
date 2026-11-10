package com.tomessa.miff

import android.content.Context
import android.os.SystemClock
import com.google.firebase.firestore.FirebaseFirestore
import com.tomessa.miff.data.account.AccountDataStore
import com.tomessa.miff.data.account.AccountDeletion
import com.tomessa.miff.data.account.AccountDeletionController
import com.tomessa.miff.data.account.AccountDeletionMarker
import com.tomessa.miff.data.account.AccountDeletionProcedure
import com.tomessa.miff.data.account.AccountMiffs
import com.tomessa.miff.data.account.AccountSession
import com.tomessa.miff.data.account.FirestoreAccountDataStore
import com.tomessa.miff.data.account.SharedPreferencesAccountDeletionMarker
import com.tomessa.miff.data.account.StoreAccountMiffs
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
import com.tomessa.miff.data.feed.FeedRefresher
import com.tomessa.miff.data.feed.FirestoreCircleStatusReader
import com.tomessa.miff.data.feed.FirestoreFeedRefresher
import com.tomessa.miff.data.feed.FirestorePhotoReader
import com.tomessa.miff.data.feed.FirestorePositionReader
import com.tomessa.miff.data.feed.FirestoreStatusObserver
import com.tomessa.miff.data.feed.PhotoCache
import com.tomessa.miff.data.feed.PhotoReader
import com.tomessa.miff.data.feed.PositionReader
import com.tomessa.miff.data.feed.StatusObserver
import com.tomessa.miff.data.miff.AndroidLocationAccessProbe
import com.tomessa.miff.data.miff.AwaitingAgreement
import com.tomessa.miff.data.miff.ConsentRenewals
import com.tomessa.miff.data.miff.DirectoryState
import com.tomessa.miff.data.miff.FirestoreConsentRenewals
import com.tomessa.miff.data.miff.FirestoreMemberFacesReader
import com.tomessa.miff.data.miff.FirestoreMiffObserver
import com.tomessa.miff.data.miff.FirestoreMiffRepository
import com.tomessa.miff.data.miff.FirestoreMiffStore
import com.tomessa.miff.data.miff.MemberFacesReader
import com.tomessa.miff.data.miff.MiffDeletion
import com.tomessa.miff.data.miff.MiffDeletionCoordinator
import com.tomessa.miff.data.miff.MiffDirectory
import com.tomessa.miff.data.miff.MiffRepository
import com.tomessa.miff.data.miff.MiffStore
import com.tomessa.miff.data.miff.PendingDeletionStore
import com.tomessa.miff.data.miff.SharedPreferencesActiveMiffStore
import com.tomessa.miff.data.miff.SharedPreferencesPendingDeletionStore
import com.tomessa.miff.data.notification.AndroidNotificationAlarms
import com.tomessa.miff.data.notification.AndroidNotificationPresenter
import com.tomessa.miff.data.notification.AndroidNotificationTray
import com.tomessa.miff.data.notification.AppVisibility
import com.tomessa.miff.data.notification.FeedNotificationLink
import com.tomessa.miff.data.notification.NotificationAuthorization
import com.tomessa.miff.data.notification.NotificationCenter
import com.tomessa.miff.data.notification.NotificationChannels
import com.tomessa.miff.data.notification.NotificationScheduler
import com.tomessa.miff.data.notification.NotificationSettings
import com.tomessa.miff.data.notification.NotificationTray
import com.tomessa.miff.data.notification.SharedPreferencesNotificationCenterStore
import com.tomessa.miff.data.notification.SharedPreferencesNotificationLedger
import com.tomessa.miff.data.notification.SharedPreferencesNotificationPreferencesStore
import com.tomessa.miff.data.notification.SharedPreferencesPlanSnapshotStore
import com.tomessa.miff.data.onboarding.OnboardingFlags
import com.tomessa.miff.data.onboarding.ProfileDraftStore
import com.tomessa.miff.data.onboarding.SharedPreferencesProfileDraftStore
import com.tomessa.miff.data.onboarding.asTeardown
import com.tomessa.miff.data.permission.AndroidDevicePermissions
import com.tomessa.miff.data.permission.DevicePermissions
import com.tomessa.miff.data.permission.NotificationAskStore
import com.tomessa.miff.data.permission.SharedPreferencesNotificationAskStore
import com.tomessa.miff.data.place.CachedAddressSearch
import com.tomessa.miff.data.place.FallbackAddressSearch
import com.tomessa.miff.data.place.FirestorePlaceRepository
import com.tomessa.miff.data.place.FirestorePlaceStore
import com.tomessa.miff.data.place.GeoapifyAddressSearch
import com.tomessa.miff.data.place.MapTileCachePurger
import com.tomessa.miff.data.place.PhotonAddressSearch
import com.tomessa.miff.data.place.PlaceNotices
import com.tomessa.miff.data.place.PlaceRepository
import com.tomessa.miff.data.place.UrlConnectionFetcher
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
import com.tomessa.miff.data.profile.HttpsGooglePhotoSource
import com.tomessa.miff.data.profile.MyProfileRepository
import com.tomessa.miff.data.review.ForegroundTicks
import com.tomessa.miff.data.review.ReviewStateStore
import com.tomessa.miff.data.review.SharedPreferencesReviewStateStore
import com.tomessa.miff.data.session.Clearance
import com.tomessa.miff.data.session.DeviceSession
import com.tomessa.miff.data.session.FirestoreSessionDocuments
import com.tomessa.miff.data.session.LocalDataEraser
import com.tomessa.miff.data.session.SessionManager
import com.tomessa.miff.data.session.SessionTeardown
import com.tomessa.miff.data.session.SharedPreferencesDeviceIdStore
import com.tomessa.miff.data.session.accountLocalDataErasers
import com.tomessa.miff.data.settings.AndroidLanguageStore
import com.tomessa.miff.data.settings.LanguageStore
import com.tomessa.miff.data.subscription.FirestoreSubscriptionStore
import com.tomessa.miff.data.subscription.SubscriptionRepository
import com.tomessa.miff.data.tracking.AndroidTrackingEnvironment
import com.tomessa.miff.data.tracking.ConnectivityNetworkWake
import com.tomessa.miff.data.tracking.DetectionJournals
import com.tomessa.miff.data.tracking.FirestoreTrackingStore
import com.tomessa.miff.data.tracking.FusedMovementUpdates
import com.tomessa.miff.data.tracking.FusedPhotoPositionSource
import com.tomessa.miff.data.tracking.FusedPositionSource
import com.tomessa.miff.data.tracking.LocalPublishedPositions
import com.tomessa.miff.data.tracking.PlayGeofenceArming
import com.tomessa.miff.data.tracking.PresenceListener
import com.tomessa.miff.data.tracking.SharedPreferencesTrackingLocalStore
import com.tomessa.miff.data.tracking.TrackingEngine
import com.tomessa.miff.data.tracking.TrackingEnvironment
import com.tomessa.miff.data.update.FirestorePhotoStore
import com.tomessa.miff.data.update.PhotoCleaner
import com.tomessa.miff.data.update.PhotoStore
import com.tomessa.miff.data.update.PublishLedger
import com.tomessa.miff.data.update.SharedPreferencesPublishLedger
import com.tomessa.miff.data.update.UpdatePublisher
import com.tomessa.miff.domain.analytics.AnalyticsTracker
import com.tomessa.miff.domain.auth.ResendCooldown
import com.tomessa.miff.domain.auth.SignInProvider
import com.tomessa.miff.domain.miff.JoinAttemptLimiter
import com.tomessa.miff.domain.notification.SnapshotMember
import com.tomessa.miff.domain.profile.AvatarCodec
import com.tomessa.miff.domain.profile.GooglePhotoPrefill
import com.tomessa.miff.domain.tracking.DetectionJournal
import com.tomessa.miff.startup.StartupTrace
import com.tomessa.miff.ui.main.TabRequests
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

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

    /** The Google photo of the account, taken once at the profile step of a Google sign-up (HTTPS, Google hosts only, in memory). */
    val googlePhotoPrefill: GooglePhotoPrefill by lazy { GooglePhotoPrefill(HttpsGooglePhotoSource(), avatarCodec) }

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

    /** The returns of the app to the foreground, counted (read by the review window, which looks at its rules once per return). */
    val foregroundTicks: ForegroundTicks by lazy { ForegroundTicks() }

    /** What the review window remembers (three small values); erased at sign-out and with the account. Built on first use, even in a build without the window. */
    val reviewStateStore: ReviewStateStore by lazy { SharedPreferencesReviewStateStore(context) }

    val accountRepository: AccountRepository by lazy { FirestoreAccountRepository() }

    /** Local Firestore cache purge (sign-out, refusal of the panel, and loss of access to a Miff). One instance for all. */
    val cachePurger: CachePurger by lazy { FirestoreCachePurger(flags = sessionFlags) }

    /** The phone's own copy of the identifier of its session and the markers "replaced" / "closed" (private file): read by the session AND, without any network, by the notifications. */
    private val deviceIdStore: SharedPreferencesDeviceIdStore by lazy { SharedPreferencesDeviceIdStore(context) }

    /**
     * SESS-2, "un compte, un seul appareil": the session of this phone (identifier in a private file not backed up, document `users/{uid}/session/active`).
     * Used by the session manager (sign-in, launch, sign-out), by the detection engine and the Miff repository (nothing is written without it) and by the
     * deletion of a Miff. `onReplaced` only schedules the forced sign-out (the engine that may be calling must be able to release its lock).
     */
    val deviceSession: DeviceSession by lazy {
        DeviceSession(
            ids = deviceIdStore,
            documents = FirestoreSessionDocuments(),
            network = networkMonitor,
            currentUid = { authGateway.currentUid },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            onReplaced = { accountGone -> sessionManager.onDeviceReplaced(accountGone) },
        )
    }

    /**
     * Create, join, rename, stop / resume sharing, clean-up after a loss of access, live view of a Miff (Miff ticket).
     * Built on first use. Constructing it never reaches Firestore (the store and the observer take it through a function,
     * only called when a request is made), and every request comes from a screen shown after [MiffApplication.startSession]
     * has resolved the session, hence after the cache purge of a failed sign-out.
     */
    private val miffStore: MiffStore by lazy { FirestoreMiffStore() }

    val miffRepository: MiffRepository by lazy {
        FirestoreMiffRepository(
            store = miffStore,
            observer = FirestoreMiffObserver(),
            location = AndroidLocationAccessProbe(context),
            device = deviceSession,
            cache = cachePurger,
            onSharingChanged = { trackingLooksAgain(TrackingEngine.Reason.SHARING_CHANGED) },
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
     * LOC-2a: what the phone says about the permissions of Miff (state only, never a position), the flag "notification screen already shown",
     * and the renewal of the consent of the members who share under an older text.
     */
    val devicePermissions: DevicePermissions by lazy { AndroidDevicePermissions(context) }

    val notificationAsks: NotificationAskStore by lazy { SharedPreferencesNotificationAskStore(context) }

    /**
     * NOTIF-P0 (ticket docs/tickets/notifications.md, section 20): notifications that need NO server. Everything here is LOCAL to the phone: the choices of
     * Settings > Notifications, the centre (a list of types and times, never a position or a place), the tray of the system, the state of the permission,
     * and the requests to show a tab. No read or write of Firestore, no event, no log.
     */
    val notificationSettings: NotificationSettings by lazy {
        NotificationSettings(SharedPreferencesNotificationPreferencesStore(context))
    }

    val notificationTray: NotificationTray by lazy { AndroidNotificationTray(context) }

    /** The centre of notifications; reading a line also takes its notification out of the tray. */
    val notificationCenter: NotificationCenter by lazy {
        NotificationCenter(
            store = SharedPreferencesNotificationCenterStore(context),
            onRead = { key -> notificationTray.cancel(key) },
        )
    }

    val notificationAuthorization: NotificationAuthorization by lazy {
        NotificationAuthorization(devicePermissions, notificationAsks)
    }

    /**
     * NOTIF-4A / 5A: plans, cancels and shows the reminders N1 to N4 (alarms, local snapshot, memory of what was notified). Local: nothing is read from or written to
     * the server by N1 and N2; N3 and N4 read the statuses of the Miff once from the server a moment before they are shown (see [NotificationScheduler]).
     */
    val notificationScheduler: NotificationScheduler by lazy {
        NotificationScheduler(
            snapshots = SharedPreferencesPlanSnapshotStore(context),
            settings = notificationSettings,
            permissionAllowed = { notificationAuthorization.allowed() },
            sessionValid = ::notificationSessionValid,
            alarms = AndroidNotificationAlarms(context),
            ledger = SharedPreferencesNotificationLedger(context),
            center = notificationCenter,
            presenter = AndroidNotificationPresenter(context),
            appInForeground = { AppVisibility.inForeground },
            reader = FirestoreCircleStatusReader(),
        )
    }

    /**
     * ONE thread, in order, for what the screens tell the notifications: the disk work (the snapshot, the alarms) never runs on the main thread, and two calls
     * never overtake each other (the last status seen is the last one kept). A failure there never takes the app down (nothing is logged: it could hold a Miff).
     */
    private val notificationScope by lazy {
        CoroutineScope(
            SupervisorJob() + Dispatchers.Default.limitedParallelism(1) + CoroutineExceptionHandler { _, _ -> },
        )
    }

    /** What the Feed tells the notifications (the snapshot of the Miff shown, the choice of another Miff): local, see [FeedNotificationLink]. */
    val feedNotificationLink: FeedNotificationLink by lazy {
        object : FeedNotificationLink {
            override fun onStatuses(
                miffId: String,
                myUid: String,
                members: List<SnapshotMember>,
            ) {
                notificationScope.launch { notificationScheduler.onStatuses(miffId, myUid, members) }
            }

            override fun onActiveMiff(miffId: String) {
                notificationScope.launch { notificationScheduler.onActiveMiffChanged(miffId) }
            }
        }
    }

    /**
     * Is somebody signed in on this phone with the session of [uid]? Answered on the PHONE (no read of the server, no wait): the account, the identifier of the session
     * and the three markers (replaced by another phone, deletion under way, sign-out under way). A reminder never leaves for an account that is not the one in use.
     */
    private fun notificationSessionValid(uid: String): Boolean =
        authGateway.currentUid == uid &&
            deviceIdStore.read() != null &&
            !deviceIdStore.isReplaced() &&
            !deviceIdStore.isClosed() &&
            !deviceIdStore.isGone() &&
            accountDeletionMarker.read() == null

    private val notificationWatchStarted =
        java.util.concurrent.atomic
            .AtomicBoolean(false)

    /**
     * The app came to the front (or woke up): the channels are written again (language), what the person chose in Settings is WATCHED (a switch cancels what it covers at
     * once), and the plan is looked at again (the permission may have changed, the clock moved, a notification became due). Never blocks the caller.
     */
    fun notificationsLookAgain() {
        notificationScope.launch {
            NotificationChannels.ensure(context)
            if (notificationWatchStarted.compareAndSet(false, true)) {
                // Every change of a switch (the first value is the current one: skipped) asks the scheduler to bring the alarms in line, within the second.
                notificationScope.launch {
                    notificationSettings.preferences.drop(1).collect { notificationScheduler.reconcile() }
                }
                // Leaving a Miff, its deletion and the loss of the access all change the list the directory reads: the reminders of a Miff that is gone are cancelled.
                notificationScope.launch {
                    miffDirectory.state.collect { state ->
                        if (state is DirectoryState.Ready) {
                            notificationScheduler.onMiffsKnown(
                                state.miffs.map { it.id }.toSet(),
                            )
                        }
                    }
                }
            }
            notificationScheduler.reconcile()
        }
    }

    /** NOTIF-P0: the centre, the choices, the alarms and what is in the tray go with the session (contract of the purge, DUR-3). */
    private val notificationTeardown: SessionTeardown by lazy {
        SessionTeardown {
            notificationScheduler.clearAll()
            notificationCenter.purge()
            notificationSettings.reset()
            notificationTray.cancelAll()
            tabRequests.clear()
        }
    }

    /** A tap on a line of the centre (or, later, on a notification) asks the tabs to show the Feed or the Update. */
    val tabRequests: TabRequests by lazy { TabRequests() }

    /** LOC-2b (design 23): the Miffs that wait for the agreement of the person, in memory for the session. */
    val awaitingAgreement: AwaitingAgreement by lazy { AwaitingAgreement() }

    val consentRenewals: ConsentRenewals by lazy {
        FirestoreConsentRenewals(miffStore, onRenewed = { trackingLooksAgain(TrackingEngine.Reason.SHARING_CHANGED) })
    }

    /**
     * LOC-2b, the detection of the arrivals and departures. The journal exists in the debug build only (the release one does nothing).
     * Built on first use; the system never starts it by itself: the screens, the receiver and [MiffApplication] ask it to look again.
     */
    val detectionJournal: DetectionJournal by lazy { DetectionJournals.create(context) }

    /** What the detection and the Update ask of the phone (battery, network, clock). One instance for both. */
    private val trackingEnvironment: TrackingEnvironment by lazy {
        AndroidTrackingEnvironment(
            context = context,
            permissions = devicePermissions,
            currentUid = { authGateway.currentUid },
            online = { networkMonitor.isOnline() },
        )
    }

    val trackingEngine: TrackingEngine by lazy {
        TrackingEngine(
            env = trackingEnvironment,
            store = FirestoreTrackingStore(miffStore),
            local = SharedPreferencesTrackingLocalStore(context),
            position = FusedPositionSource(context),
            geofences = PlayGeofenceArming(context),
            movement = FusedMovementUpdates(context),
            networkWake = ConnectivityNetworkWake(context),
            device = deviceSession,
            journal = detectionJournal,
            // What the end of a session stopped stays stopped until the session changes (a lambda: the session manager lists this engine in its teardown).
            generation = { sessionManager.generation },
            // AUTH-5b: while the deletion of THIS account is under way (its marker is on the disk) nothing is armed, read or written, even by a receiver of the
            // system that wakes a cold process (the memory of the engine's stop is gone there).
            suspended = { accountDeletionMarker.read()?.uid?.let { it == authGateway.currentUid } == true },
            // NOTIF-4A: a departure or an arrival of mine moves the reminders at once (local; nothing read or written).
            presenceListener =
                PresenceListener { miffId, presence, atMillis, setLastUpdate ->
                    notificationScheduler.onMyPresence(miffId, presence, atMillis, setLastUpdate)
                },
        )
    }

    // A failure of the detection never takes the app down (nothing is logged: it could hold a place).
    private val trackingScope by lazy {
        CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, _ -> })
    }

    /** Asks the detection to look again (the app opened, went to the background, the sharing changed...). Never blocks the caller. */
    fun trackingLooksAgain(reason: TrackingEngine.Reason) {
        trackingScope.launch {
            // A purge of the cache left by a failed sign-out comes before any other access to Firestore.
            cachePurger.purgeIfPending()
            trackingEngine.lookAgain(reason)
        }
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

    /** FEED: the statuses of the Miff shown (read only, one listener) and the single reading of a position behind "Localiser". Nothing is written. */
    val statusObserver: StatusObserver by lazy { FirestoreStatusObserver() }

    /** The faces of the carousel of Miffs of the Profile tab: ONE reading of the members of a Miff, when its card appears (docs/dev/surveillance-quotas.md). */
    val memberFacesReader: MemberFacesReader by lazy { FirestoreMemberFacesReader() }

    val positionReader: PositionReader by lazy { FirestorePositionReader() }

    /**
     * FEED-4 and UPDATE-1: the photos read (in memory, 16 at most, emptied at sign-out), and the update of the person. The photo and the status of an update are
     * written by [updatePublisher] only, in one transaction per lot of 5 Miffs, with the identifier of the session of this phone and ONE measure of the position
     * (decision of the PM, 2026-11-06: no photo without a position). Nothing of it is logged or sent as an event.
     */
    val photoCache: PhotoCache by lazy { PhotoCache() }

    val photoReader: PhotoReader by lazy {
        FirestorePhotoReader(photoCache, ownPositionPublished = {
            LocalPublishedPositions(SharedPreferencesTrackingLocalStore(context)).isPublished(it)
        })
    }

    val publishLedger: PublishLedger by lazy { SharedPreferencesPublishLedger(context) }

    private val photoStore: PhotoStore by lazy { FirestorePhotoStore() }

    /** The ids of the Miffs of the person, as the directory knows them (read by the Feed): the transaction then keeps the ones where the sharing is on. */
    private fun miffIds(): List<String> =
        ((miffDirectory.state.value as? DirectoryState.Ready)?.miffs.orEmpty()).map {
            it.id
        }

    val updatePublisher: UpdatePublisher by lazy {
        UpdatePublisher(
            store = photoStore,
            device = deviceSession,
            position = FusedPhotoPositionSource(context),
            network = networkMonitor,
            ledger = publishLedger,
            photos = photoCache,
            currentUid = { authGateway.currentUid },
            miffIds = ::miffIds,
            battery = { trackingEnvironment.batteryPct() },
            // The photo is only written where my own position is published (contract of the data model); none yet: the detection is asked to write it.
            published = LocalPublishedPositions(SharedPreferencesTrackingLocalStore(context)),
            onPositionMissing = { trackingLooksAgain(TrackingEngine.Reason.OPENED) },
            onUpdatePublished = {
                miffIds,
                at,
                ->
                notificationScope.launch { notificationScheduler.onMyUpdate(miffIds, at) }
            },
        )
    }

    val photoCleaner: PhotoCleaner by lazy {
        PhotoCleaner(
            store = photoStore,
            device = deviceSession,
            network = networkMonitor,
            ledger = publishLedger,
            photos = photoCache,
            currentUid = { authGateway.currentUid },
            miffIds = ::miffIds,
            battery = { trackingEnvironment.batteryPct() },
            reader = photoReader,
        )
    }

    /** "Actualiser" (pull to refresh): one forced reading from the server, at most every 15 s, reads only. */
    val feedRefresher: FeedRefresher by lazy { FirestoreFeedRefresher() }

    /** After a voluntary "Quitter" the server confirmed: no message of lost access for it, purge of the cache, fresh list of Miffs. */
    suspend fun onMiffLeft(miffId: String) {
        miffDirectory.expectDeparture(miffId)
        notificationScope.launch { notificationScheduler.onMiffGone(miffId) }
        cachePurger.markPurgePending()
        cachePurger.purge()
        miffDirectory.refresh()
        // The zones and the position of the Miff just left are no longer watched or kept (criterion DET4).
        trackingLooksAgain(TrackingEngine.Reason.SHARING_CHANGED)
    }

    /**
     * Deletion of a Miff, with no screen: used by the Miff screen now, and later by the deletion of an account (AUTH-5) and by the
     * end of a subscription (Premium ticket). Runs as long as the app lives and resumes at launch from a local marker. Built on
     * first use: the main shell (shown only once the session is resolved) is the first to use it, so nothing here reaches Firestore
     * before [MiffApplication.startSession].
     */
    private val pendingMiffDeletions: PendingDeletionStore by lazy { SharedPreferencesPendingDeletionStore(context) }

    val miffDeletions: MiffDeletionCoordinator by lazy {
        MiffDeletionCoordinator(
            deletion = MiffDeletion(store = miffStore, repository = miffRepository),
            pending = pendingMiffDeletions,
            network = networkMonitor,
            currentUid = { authGateway.currentUid },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            onStarted = miffDirectory::expectDeparture,
            onDeleted = { miffId -> onMiffLeft(miffId) },
            // SESS-2: a phone that was replaced stops a deletion it started (the rules do not look at deletions).
            replaced = { deviceSession.clearance(recheck = false) is Clearance.Replaced },
        )
    }

    /**
     * The places (ticket LOC-1): create, edit, delete, in the Miffs the person created. Reads that decide a limit come from the
     * server; every write is a transaction (nothing is queued offline). Built on first use, like the rest of the Miff data.
     */
    val placeRepository: PlaceRepository by lazy {
        FirestorePlaceRepository(
            store = FirestorePlaceStore(miffStore),
            // An automatic id of Firestore (20 letters and digits), drawn on the phone and ONCE for all the Miffs of a place.
            newId = {
                FirebaseFirestore
                    .getInstance()
                    .collection("miffs")
                    .document()
                    .collection("places")
                    .document()
                    .id
            },
        )
    }

    /** "Le lieu « X » est enregistré…" shown on the list after a save: in memory only, gone with the session. */
    val placeNotices: PlaceNotices by lazy { PlaceNotices() }

    /**
     * The suggestions of address of the form: Photon (free, no key) with the session cache; the Geoapify fallback exists only when a
     * key was given at build time (never committed, see app/build.gradle.kts and docs/dev/lieux-fournisseurs-gratuits.md).
     */
    val addressSearch: CachedAddressSearch by lazy {
        val fetcher = UrlConnectionFetcher(userAgent = "Miff-Android/${BuildConfig.VERSION_NAME}")
        CachedAddressSearch(
            FallbackAddressSearch(
                primary = PhotonAddressSearch(BuildConfig.PHOTON_URL, fetcher),
                fallback =
                    BuildConfig.GEOAPIFY_KEY.takeIf { it.isNotEmpty() }?.let {
                        GeoapifyAddressSearch(
                            it,
                            fetcher,
                        )
                    },
            ),
        )
    }

    /** The tiles of the map are the picture of where the person goes: erased with the session (sign-out, account deletion). */
    val mapTileCache: MapTileCachePurger by lazy { MapTileCachePurger.forContext(context) }

    /** The marker "the deletion of the account is under way" (AUTH-5): the id of the account, nothing else; it prevails over the routing. */
    val accountDeletionMarker: AccountDeletionMarker by lazy { SharedPreferencesAccountDeletionMarker(context) }

    private val accountDataStore: AccountDataStore by lazy { FirestoreAccountDataStore() }

    /**
     * The Miffs of the account for its deletion (AUTH-5b): the inventory read on the server, the lots "Quitter" for the Miffs joined, and the existing
     * deletion of a Miff (MIFF-2) for the Miffs created. Its own instance of the procedure: it does not go through the coordinator of the Miff screen (its
     * hooks purge the cache and refresh the list at each Miff, which is not wanted in the middle of the deletion of the account).
     */
    private val accountMiffs: AccountMiffs by lazy {
        StoreAccountMiffs(
            store = miffStore,
            deleter = MiffDeletion(store = miffStore, repository = miffRepository),
        )
    }

    /** The deletion of an account, with or without Miffs (AUTH-5a and AUTH-5b). */
    val accountDeletionProcedure: AccountDeletionProcedure by lazy {
        AccountDeletion(
            store = accountDataStore,
            gateway = authGateway,
            cache = cachePurger,
            miffs = accountMiffs,
            // SESS-2: the session of the account goes before its profile, with the acknowledgement of the server.
            session = AccountSession { uid -> deviceSession.releaseForAccountDeletion(uid) },
        )
    }

    /**
     * Runs the deletion for as long as the app lives (the screen may go away), pauses without network, resumes at launch from the marker.
     * Built on first use; the session is the one that stops what runs before the first change and erases the local data at the end.
     */
    val accountDeletion: AccountDeletionController by lazy {
        AccountDeletionController(
            procedure = accountDeletionProcedure,
            marker = accountDeletionMarker,
            session = sessionManager,
            network = networkMonitor,
            currentUid = { authGateway.currentUid },
            isAppleLinked = { SignInProvider.APPLE in authGateway.linkedProviders() },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    }

    /** What is kept on the phone FOR ONE ACCOUNT and goes with it when the account is deleted (AUTH-5, ticket 5.10). */
    private val localDataErasers: List<LocalDataEraser> by lazy {
        accountLocalDataErasers(notificationAsks, pendingMiffDeletions, publishLedger, reviewStateStore)
    }

    /** The read-only "Abonnement" line of the Settings (ticket ABO-1): one read of `entitlements/{uid}` per opening of the Settings. */
    val subscriptionRepository: SubscriptionRepository by lazy {
        SubscriptionRepository(store = FirestoreSubscriptionStore(), currentUid = { authGateway.currentUid })
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
                // LOC-2b: trackingEngine = sign-out, deletion of the account, session lost: no more zones, no more position, the local copy and
                // the journal erased (VIE2, VIE3); awaitingAgreement = nobody waits for anything any more.
                teardown =
                    listOf(
                        profileDrafts.asTeardown(),
                        currentProfile,
                        consentWithdrawals,
                        miffDirectory,
                        trackingEngine,
                        awaitingAgreement,
                        notificationTeardown,
                        photoCache,
                        SessionTeardown { publishLedger.clear() },
                        SessionTeardown { reviewStateStore.clear() },
                        placeNotices,
                        addressSearch,
                        mapTileCache,
                    ),
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
                device = deviceSession,
                drafts = profileDrafts,
                withdrawals = pendingWithdrawals,
                onAccountResolved = consentWithdrawals::resume,
                deletionMarker = accountDeletionMarker,
                localData = localDataErasers,
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
            NotificationChannels.ensure(context)
            analyticsTracker
            networkMonitor
        }
    }
}
