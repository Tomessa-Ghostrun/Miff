package com.tomessa.miff.data.session

import com.tomessa.miff.data.account.AccountDeletionMarker
import com.tomessa.miff.data.account.AccountDeletionSession
import com.tomessa.miff.data.auth.AccountLoad
import com.tomessa.miff.data.auth.AccountRepository
import com.tomessa.miff.data.auth.AuthGateway
import com.tomessa.miff.data.auth.CachePurger
import com.tomessa.miff.data.auth.PendingConsentStore
import com.tomessa.miff.data.onboarding.ProfileDraftStore
import com.tomessa.miff.data.privacy.BooleanStore
import com.tomessa.miff.data.privacy.PendingWithdrawals
import com.tomessa.miff.data.privacy.PrivacySettings
import com.tomessa.miff.domain.analytics.AnalyticsEvent
import com.tomessa.miff.domain.analytics.AnalyticsTracker
import com.tomessa.miff.domain.auth.AccountSnapshot
import com.tomessa.miff.domain.auth.AppDestination
import com.tomessa.miff.domain.auth.AuthFailure
import com.tomessa.miff.domain.auth.AuthOutcome
import com.tomessa.miff.domain.auth.ConsentChoice
import com.tomessa.miff.domain.auth.ConsentVersions
import com.tomessa.miff.domain.auth.GoogleTokenResult
import com.tomessa.miff.domain.auth.PendingConsent
import com.tomessa.miff.domain.auth.resolveDestination
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

sealed interface SessionState {
    /** Something is being checked (account read, sign-out in progress). */
    data object Loading : SessionState

    /** Signed in, but the account could not be read (offline...). Retry or sign out; nothing else is reachable. */
    data object LookupFailed : SessionState

    data class Ready(
        val destination: AppDestination,
    ) : SessionState
}

/** One-time message shown on the sign-in screen after the session ended. */
enum class SignedOutNotice {
    /** The consent panel was refused: the account just created was deleted (ticket 7.8). */
    ACCOUNT_NOT_CREATED,

    /** The session ended behind our back (account disabled or deleted from the console, token revoked). */
    ACCOUNT_UNAVAILABLE,

    /**
     * The account is now used on another phone (SESS-2): a calm note, never an error, never "you lost access to a Miff" (criteria D3, SES5). Kept on the
     * phone until the sign-in screen has shown it, because a geofence receiver may have signed the phone out while the app was closed.
     */
    SESSION_REPLACED,

    /**
     * The deletion of the account was finished without the end screen being seen (the app was killed and relaunched, ticket 5.6): the
     * welcome carousel says it once, calmly ("Ton compte a été supprimé.").
     */
    ACCOUNT_DELETED,

    /**
     * The session was closed behind our back while a deletion of the account was under way (token expired or revoked, forced sign-out): the
     * account may still exist, so NOTHING says "deleted". The marker stays; signing in again with the same account resumes the deletion.
     */
    DELETION_PENDING,
}

sealed interface RefuseResult {
    data object Done : RefuseResult

    /** The person closed the Google dialog asked to prove who they are: nothing happened, no message. */
    data object Cancelled : RefuseResult

    data class Failed(
        val failure: AuthFailure,
    ) : RefuseResult
}

/**
 * What must stop when the session ends (Firestore listeners, location monitoring, status writes, push token).
 * Nothing registers yet (those features do not exist); the geofencing, feed and push tickets add theirs to AppContainer.
 */
fun interface SessionTeardown {
    suspend fun stop()
}

/**
 * What the deletion of the account erases from the phone, besides what [SessionTeardown] already stops and forgets: the preferences
 * kept for THIS account (flags per account, markers of deletion of a Miff...). [uid] is the account that was deleted.
 */
fun interface LocalDataEraser {
    suspend fun erase(uid: String)
}

/**
 * The single owner of "who is signed in and where does the app open" (tickets AUTH, sections 5, 7.6, 7.8 and 8).
 *
 * Rules kept here, each covered by a unit test:
 * - the destination comes from the state of the ACCOUNT (profile? Miff?), never from "a sign-in just happened";
 * - nothing is collected (Analytics, crash reports) until the account's own choice has been read, or until the person
 *   tapped "Rejoindre" / "Continuer" (which accepts the terms AND the statistics, PM decision of 2026-10-28); the
 *   consent panel keeps everything off until then. While the choice is not known the collection is only PAUSED (the
 *   crash report of the previous run stays on disk); it is DELETED only when there is no consent: withdrawal, sign-out,
 *   refusal, or no session at all at a cold start;
 * - the profile is never written here (the Onboarding ticket does it, with [com.tomessa.miff.data.auth.UserProfileRepository]);
 * - ending a session (sign-out, refusal, loss) stops the listeners, purges the Firestore cache, clears the pending
 *   consent and cuts the collections.
 *
 * Authentication operations run to completion even if the screen that started them goes away: an abandoned request
 * could otherwise open a session nobody is watching.
 */
class SessionManager(
    private val gateway: AuthGateway,
    private val accounts: AccountRepository,
    private val pending: PendingConsentStore,
    private val flags: BooleanStore,
    private val privacy: PrivacySettings,
    private val tracker: AnalyticsTracker,
    private val cache: CachePurger,
    private val teardown: List<SessionTeardown>,
    private val scope: CoroutineScope,
    /**
     * "Un compte, un seul appareil" (SESS-2): the session of this phone. Written at every sign-in, verified at every launch, listened to while signed in,
     * deleted (if it is ours) at a voluntary sign-out, never touched by a forced one.
     */
    private val device: DeviceSession,
    /**
     * The local draft of the profile steps. Erased whenever it can no longer be of any use: nobody is signed in, or the
     * profile exists (belt and braces next to the teardown of sign-out and the erasure after the confirmed write).
     */
    private val drafts: ProfileDraftStore? = null,
    /**
     * Withdrawals of the statistics consent that the account does not know about yet (no network when the person switched it
     * off). While one waits for THIS account, the collection stays off at every start, whatever the account says.
     */
    private val withdrawals: PendingWithdrawals? = null,
    /** Called with the id of the account once it has been read (the pending withdrawal, if any, is then recorded). */
    private val onAccountResolved: (String) -> Unit = {},
    /**
     * The marker of a deletion of the account under way (AUTH-5). When it exists it has PRIORITY over the routing at launch.
     * Null where deletion does not exist (older tests).
     */
    private val deletionMarker: AccountDeletionMarker? = null,
    /** Preferences kept for one account, erased when that account is deleted (AUTH-5, ticket 5.10). */
    private val localData: List<LocalDataEraser> = emptyList(),
) : AccountDeletionSession {
    private val mutableState = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = mutableState.asStateFlow()

    private val mutableNotice = MutableStateFlow<SignedOutNotice?>(null)

    private var started = false

    /**
     * Goes up at every end of a session (sign-out, refused panel, lost session). A screen that keeps personal data in
     * memory (the profile steps) keys its view model on it, so a new person never meets what the previous one typed.
     */
    var generation: Int = 0
        private set

    /** What the last read of the account said (null before the first read and after a sign-out). */
    var accountSnapshot: AccountSnapshot? = null
        private set

    /** The account that is signed in (null = nobody). Read by the Onboarding steps, which write the profile. */
    val currentUid: String? get() = gateway.currentUid

    /** Name suggested by the sign-in provider, to pre-fill the profile step. Never stored by the session. */
    fun suggestedDisplayName(): String? = gateway.displayName()

    /** True while we end the session ourselves: the "session lost" watcher must not react to our own sign-out. */
    private var ending = false

    /** True from the moment a session is known to exist until it ends: a loss during a transition still counts. */
    private var sessionActive = false

    /** Reads (and forgets) the one-time message for the sign-in screen. */
    fun consumeNotice(): SignedOutNotice? {
        val notice = mutableNotice.value
        mutableNotice.value = null
        if (notice == SignedOutNotice.SESSION_REPLACED) flags.put(KEY_REPLACED_NOTICE_PENDING, false)
        return notice
    }

    /**
     * True once when a deletion of the account finished behind the screen (the app was relaunched, ticket 5.6): the banner "Ton compte a
     * été supprimé." is shown above the welcome carousel. Only that notice is taken; the others stay for the sign-in screen.
     */
    fun consumeAccountDeletedNotice(): Boolean {
        if (mutableNotice.value != SignedOutNotice.ACCOUNT_DELETED) return false
        mutableNotice.value = null
        return true
    }

    private var carouselAfterDeletion = false

    /**
     * True once after the deletion of an account: the welcome carousel is shown again even if it was seen before (decision of the PM,
     * 2026-10-31: "Ton compte a été supprimé", then the carousel, then sign-up / sign-in).
     */
    fun consumeCarouselAfterDeletion(): Boolean {
        val shown = carouselAfterDeletion
        carouselAfterDeletion = false
        return shown
    }

    fun start() {
        if (started) return
        started = true
        // Nothing is collected from the very first instant: the manifest says off, and the choice stored on the device
        // is NOT trusted at a cold start. Collection is only switched on once the account's own choice has been read.
        // PAUSE, not "no consent": the report of the previous crash must survive until the account has answered.
        privacy.pauseCollection()
        scope.launch {
            // A purge that failed last time comes BEFORE any other Firestore access.
            cache.purgeIfPending()
            scope.launch { watchSessionLoss() }
            resolveCurrent()
        }
    }

    /** "Réessayer" after a failed account read. */
    fun retry() {
        scope.launch { resolveCurrent() }
    }

    // --- Operations started by the screens -----------------------------------------------------------------

    /** Null = signed in (the state moves on by itself); otherwise why it did not work. */
    suspend fun signInWithEmail(
        email: String,
        password: String,
    ): AuthFailure? =
        withContext(NonCancellable) {
            cache.purgeIfPending()
            when (val outcome = gateway.signInWithEmail(email, password)) {
                is AuthOutcome.Failure -> {
                    outcome.failure
                }

                is AuthOutcome.Success -> {
                    openDeviceSession(outcome.uid)
                        ?: run {
                            completeAuthentication(outcome.uid, AnalyticsEvent.Method.EMAIL, signUpConsent = null)
                            null
                        }
                }
            }
        }

    /** [consent] must have (a) accepted: an account is never created without it. */
    suspend fun createAccountWithEmail(
        email: String,
        password: String,
        consent: ConsentChoice,
    ): AuthFailure? =
        withContext(NonCancellable) {
            require(consent.termsAccepted) { "terms not accepted" }
            cache.purgeIfPending()
            when (val outcome = gateway.createAccountWithEmail(email, password)) {
                is AuthOutcome.Failure -> {
                    outcome.failure
                }

                is AuthOutcome.Success -> {
                    // A NEW account stays signed in even if its session could not be written (see openDeviceSession).
                    openDeviceSession(outcome.uid, newAccount = true)
                        ?: run {
                            completeEmailSignUp(outcome.uid, consent)
                            null
                        }
                }
            }
        }

    /**
     * Google ID token -> Firebase. [signUpConsent] is the acceptance given by the tap when the gesture comes from the
     * sign-up screen (null from the sign-in screen: a new person then gets the consent panel).
     */
    suspend fun signInWithGoogle(
        idToken: String,
        signUpConsent: ConsentChoice?,
    ): AuthFailure? =
        withContext(NonCancellable) {
            require(signUpConsent == null || signUpConsent.termsAccepted) { "terms not accepted" }
            cache.purgeIfPending()
            when (val outcome = gateway.signInWithGoogle(idToken)) {
                is AuthOutcome.Failure -> {
                    outcome.failure
                }

                is AuthOutcome.Success -> {
                    openDeviceSession(outcome.uid)
                        ?: run {
                            completeAuthentication(outcome.uid, AnalyticsEvent.Method.GOOGLE, signUpConsent)
                            null
                        }
                }
            }
        }

    /** Consent panel, "Continuer". Returns false (and does nothing) unless (a) is accepted. */
    fun acceptConsentPanel(consent: ConsentChoice): Boolean {
        if (!consent.termsAccepted) return false
        // Only from the panel itself: a second call (double tap) must not record twice nor send a second event.
        if ((mutableState.value as? SessionState.Ready)?.destination != AppDestination.CONSENT_PANEL) return false
        val uid = gateway.currentUid ?: return false
        // "Continuer" accepts the terms AND the statistics (decision of 2026-10-28): both are recorded together.
        pending.save(PendingConsent(uid, ConsentVersions.TERMS, analyticsAccepted = true))
        flags.put(KEY_RETURNING_USER, true)
        privacy.applyAccountConsent(true)
        tracker.track(AnalyticsEvent.SignUpCompleted(gateway.signInMethod() ?: AnalyticsEvent.Method.EMAIL))
        mutableState.value = SessionState.Ready(AppDestination.PROFILE)
        return true
    }

    /**
     * Consent panel refused (button, system back, closing): the account that was just created is deleted, nothing is
     * kept, nothing is sent. If Firebase asks for a recent sign-in, [requestGoogleToken] asks the person to pick their
     * Google account again; that is the only extra step, and it is cancellable without any message.
     */
    suspend fun refuseConsentPanel(requestGoogleToken: suspend () -> GoogleTokenResult): RefuseResult {
        // An account that already belongs to a Miff is never deleted by a refusal of the panel (it would orphan the
        // Miff and the other members' view of it): the person keeps the panel, with the neutral message.
        if (accountSnapshot?.hasMembership == true) return RefuseResult.Failed(AuthFailure.UNKNOWN)
        val first =
            withContext(NonCancellable) {
                ending = true
                cache.markPurgePending()
                // SESS-2: the session of the account that is about to disappear is deleted while we are still signed in (nobody could delete it
                // afterwards). If the account is kept after all (see below) the session is written again.
                device.release(gateway.currentUid)
                gateway.deleteCurrentAccount()
            }
        var failure = first
        if (failure == AuthFailure.RECENT_LOGIN_REQUIRED && gateway.signInMethod() != AnalyticsEvent.Method.GOOGLE) {
            // Only a Google account can be asked to prove itself again here: never leave the person stuck on a dialog
            // that cannot work. They keep the panel and can accept; the message is the neutral one.
            failure = AuthFailure.UNKNOWN
        }
        if (failure == AuthFailure.RECENT_LOGIN_REQUIRED) {
            when (val token = requestGoogleToken()) {
                is GoogleTokenResult.Token -> {
                    failure =
                        withContext(NonCancellable) {
                            gateway.reauthenticateWithGoogle(token.idToken) ?: gateway.deleteCurrentAccount()
                        }
                }

                GoogleTokenResult.Cancelled -> {
                    withContext(NonCancellable) { restoreDeviceSession() }
                    ending = false
                    return RefuseResult.Cancelled
                }

                GoogleTokenResult.Unavailable -> {
                    failure = AuthFailure.UNKNOWN
                }

                is GoogleTokenResult.Failed -> {
                    // Whatever the cause of the dialog (no account, services, interrupted...), here the person is not signing
                    // in: the plain neutral message is the only one that makes sense ("or continue with your e-mail" would not).
                    failure = AuthFailure.UNKNOWN
                }
            }
        }
        if (failure != null) {
            // Stay on the panel: never a usable account without (a).
            withContext(NonCancellable) { restoreDeviceSession() }
            ending = false
            return RefuseResult.Failed(failure)
        }
        withContext(NonCancellable) {
            mutableState.value = SessionState.Loading
            stopEverything()
            cache.purge()
            finishSignedOut(AppDestination.SIGN_IN, SignedOutNotice.ACCOUNT_NOT_CREATED, markReturning = false)
        }
        return RefuseResult.Done
    }

    /**
     * The profile (or its phone number) was just WRITTEN AND CONFIRMED by the server: the account is read again and the
     * app moves on to what the account now needs (create / join a Miff, or the feed if an older account already has one).
     * If that read fails, the app moves on from what was just written (a new profile has no Miff yet) rather than showing
     * a "retry" screen right after a success.
     */
    suspend fun onProfileSaved(analyticsConsentEnabled: Boolean) {
        val uid = gateway.currentUid ?: return
        withContext(NonCancellable) {
            val load = accounts.load(uid)
            if (gateway.currentUid != uid) return@withContext
            val read = (load as? AccountLoad.Loaded)?.snapshot
            val snapshot =
                if (read != null && read.profileExists && read.hasPhone) {
                    read
                } else {
                    AccountSnapshot(
                        profileExists = true,
                        hasMembership = read?.hasMembership ?: false,
                        analyticsConsentEnabled = analyticsConsentEnabled,
                    )
                }
            applyAccount(uid, snapshot, method = null, signUpGesture = false)
        }
    }

    /**
     * A Miff was just created or joined and the server CONFIRMED it (Miff ticket). The account now counts as having a Miff.
     * The end of the onboarding is only counted (`onboarding_completed`, once, if the consent allows) when the person was on
     * the "create or join" step; the app does NOT move on here (the "Ta Miff est prête" screen still has to be seen): see
     * [continueAfterMiff].
     */
    fun onMiffEntered(via: AnalyticsEvent.OnboardingVia) {
        accountSnapshot = accountSnapshot?.copy(hasMembership = true)
        if (currentDestination() == AppDestination.MIFF_CHOICE) tracker.track(AnalyticsEvent.OnboardingCompleted(via))
    }

    /** From the "create or join" step to the feed (no effect anywhere else). */
    fun continueAfterMiff() {
        if (currentDestination() ==
            AppDestination.MIFF_CHOICE
        ) {
            mutableState.value = SessionState.Ready(AppDestination.FEED)
        }
    }

    private fun currentDestination(): AppDestination? = (mutableState.value as? SessionState.Ready)?.destination

    /**
     * Does the server already hold a complete profile (with its phone number) for the person signed in? Used after a refused
     * write, to tell a real refusal from a first try that landed late. One read; false if anything is unclear.
     */
    suspend fun profileAlreadySaved(): Boolean {
        val uid = gateway.currentUid ?: return false
        val load = accounts.load(uid) as? AccountLoad.Loaded ?: return false
        return gateway.currentUid == uid && load.snapshot.profileExists && load.snapshot.hasPhone
    }

    /** Voluntary sign-out (ticket 7.6): listeners stop, queued writes leave, cache purged, then the sign-out. */
    suspend fun signOut() {
        withContext(NonCancellable) {
            ending = true
            // The listener of the session stops BEFORE its document is deleted: our own deletion must not look like a replacement.
            device.stopWatching()
            cache.markPurgePending()
            mutableState.value = SessionState.Loading
            stopEverything()
            cache.flushPendingWrites()
            // SESS-2 (contract of the purge, step 2 bis): the session is deleted, in one transaction, ONLY if it carries our identifier, before the client of
            // Firestore is terminated and before the sign-out. Offline: nothing is deleted (the next sign-in overwrites it). Never done by a forced sign-out.
            device.release(gateway.currentUid)
            cache.purge()
            finishSignedOut(AppDestination.SIGN_IN, notice = null, markReturning = true)
        }
    }

    /**
     * The account is now used on another phone, or its session disappeared (SESS-2): a LOCAL sign-out only. Everything stops (zones, position, listeners),
     * the cache is purged (contract of the purge), the identifier is forgotten, the person lands on the sign-in screen with a calm note. It writes NOTHING and
     * deletes NOTHING on the server (the session now belongs to the other phone: no loop), and it is never told as a lost access to a Miff.
     * Does not wait for anything it is called from: it only schedules the work, so that the detection engine (which may be the caller) can release its lock.
     */
    fun onDeviceReplaced() {
        scope.launch { endBecauseReplaced() }
    }

    private suspend fun endBecauseReplaced() {
        withContext(NonCancellable) {
            if (ending || gateway.currentUid == null) return@withContext
            // During a deletion of the account the session is handled by the deletion itself: no forced sign-out and never the "another phone" note.
            if (currentDestination() == AppDestination.ACCOUNT_DELETION) return@withContext
            ending = true
            device.forget()
            cache.markPurgePending()
            mutableState.value = SessionState.Loading
            stopEverything()
            // Signed in but replaced: whatever waits would be refused (and must not be written): no flush.
            cache.purge()
            flags.put(KEY_REPLACED_NOTICE_PENDING, true)
            finishSignedOut(AppDestination.SIGN_IN, SignedOutNotice.SESSION_REPLACED, markReturning = true)
        }
    }

    // --- Internals --------------------------------------------------------------------------------------------

    private suspend fun watchSessionLoss() {
        gateway.uidChanges().collect { uid ->
            if (uid == null && !ending && isSignedInState()) onSessionLost()
        }
    }

    private fun isSignedInState(): Boolean =
        when (val current = mutableState.value) {
            SessionState.LookupFailed -> true
            SessionState.Loading -> sessionActive
            is SessionState.Ready -> current.destination.requiresSession()
        }

    private suspend fun onSessionLost() {
        withContext(NonCancellable) {
            ending = true
            // SESS-2: nothing to delete on the server (we are not authenticated any more); the listener stops and the identifier is forgotten.
            device.forget()
            cache.markPurgePending()
            mutableState.value = SessionState.Loading
            stopEverything()
            // Signed out already: queued writes would be refused, so no flush.
            cache.purge()
            finishSignedOut(AppDestination.SIGN_IN, SignedOutNotice.ACCOUNT_UNAVAILABLE, markReturning = true)
        }
    }

    /**
     * Stops everything that runs for the session, one after the other. Each stop is waited for at most [STOP_WAIT_MILLIS]: the detection engine, for
     * instance, may be in the middle of a pass of several network calls, and the person must not stay on "Chargement" for the whole of it. A stop that is
     * late carries on in the background (it only erases local data, and the engine refuses new passes from the moment it is asked, see
     * [com.tomessa.miff.data.tracking.TrackingEngine.stop]).
     */
    private suspend fun stopEverything() {
        teardown.forEach { item ->
            val stopping =
                scope.launch {
                    try {
                        item.stop()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // A listener that does not stop must not keep the person signed in.
                    }
                }
            withTimeoutOrNull(STOP_WAIT_MILLIS) { stopping.join() }
        }
    }

    private fun finishSignedOut(
        destination: AppDestination,
        notice: SignedOutNotice?,
        markReturning: Boolean,
    ) {
        sessionActive = false
        accountSnapshot = null
        generation++
        gateway.signOut()
        pending.clear()
        privacy.applyAccountConsent(false)
        if (markReturning) flags.put(KEY_RETURNING_USER, true)
        mutableNotice.value = notice
        mutableState.value = SessionState.Ready(destination)
        ending = false
    }

    /** Cold start and "Réessayer": the same rule, whatever happened before. */
    private suspend fun resolveCurrent() {
        val uid = gateway.currentUid
        // A deletion of the account under way has PRIORITY over the routing (ticket 5.6, criteria SC27 to SC29).
        if (resolveAccountDeletion(uid)) return
        if (uid == null) {
            sessionActive = false
            // No session = no consent to send anything: a report left by a crash before the person said yes goes away.
            accountSnapshot = null
            drafts?.clear()
            privacy.applyAccountConsent(false)
            pending.clear()
            // SESS-2: a geofence receiver may have signed this phone out (replaced) while the app was closed: the calm note waits for the sign-in screen.
            if (flags.get(KEY_REPLACED_NOTICE_PENDING, false) && mutableNotice.value == null) {
                mutableNotice.value = SignedOutNotice.SESSION_REPLACED
            }
            mutableState.value = SessionState.Ready(entryDestination())
            return
        }
        sessionActive = true
        // Off until the account has been read (also at "Réessayer"), but the previous crash report is kept for now.
        privacy.pauseCollection()
        mutableState.value = SessionState.Loading
        // SESS-2: the session is verified on the server AT THE SAME TIME as the account is read (no longer wait for the launch), and BEFORE anything is written
        // (the engine asks again at its first write). Replaced: the forced sign-out is under way and the account read is dropped.
        val (load, ensured) = readAccountAndCheckSession(uid)
        // The session may have been lost while we were reading: the loss handler already put the app back to sign-in.
        if (gateway.currentUid != uid || ensured == Ensured.REPLACED) return
        // From here the session is listened to (verified again when the network comes back, if the server could not be asked now).
        device.watch(uid)
        when (load) {
            AccountLoad.Unavailable -> mutableState.value = SessionState.LookupFailed
            is AccountLoad.Loaded -> applyAccount(uid, load.snapshot, method = null, signUpGesture = false)
        }
    }

    /**
     * Reads the account and, at the same time, verifies the session of this phone. Reached only AFTER [resolveAccountDeletion] (see [resolveCurrent]):
     * an account whose deletion is under way never gets its session verified, nor written.
     */
    private suspend fun readAccountAndCheckSession(uid: String): Pair<AccountLoad, Ensured> =
        coroutineScope {
            val check = async { device.ensure(uid) }
            accounts.load(uid) to check.await()
        }

    /**
     * Sign-in, second step: the session of this phone is written (and confirmed by the server) and listened to. A phone with no session would write
     * nothing and be signed out at its next launch, so when the server cannot confirm, the sign-in is undone and the person is told there is no
     * connection. Null = done.
     */
    private suspend fun openDeviceSession(
        uid: String,
        newAccount: Boolean = false,
    ): AuthFailure? {
        if (deletionMarker?.read()?.uid == uid) {
            // The same account signs in again while its deletion is pending: it is going away, NO session is written for it (the deletion resumes
            // and deletes whatever session is there).
            device.suspendForDeletion()
            return null
        }
        if (!device.begin(uid)) {
            // A NEW account (e-mail sign-up) is never undone: it exists, its terms were just accepted on this phone, and a second attempt to create it would
            // only say "this address is already used". It stays signed in; the watcher writes the session (no identifier here + no session there =
            // an installation from before the feature) as soon as the server answers, and nothing is written meanwhile (no verified session).
            if (!newAccount) {
                gateway.signOut()
                return AuthFailure.NETWORK
            }
        }
        flags.put(KEY_REPLACED_NOTICE_PENDING, false)
        device.watch(uid)
        return null
    }

    /** The account was kept after all (deletion refused or cancelled): the session that was released is written again. */
    private suspend fun restoreDeviceSession() {
        val uid = gateway.currentUid ?: return
        // If the write fails, the watcher writes one itself as soon as the server answers (no identifier here + no session there).
        device.begin(uid)
        device.watch(uid)
    }

    /**
     * Launch with a marker of deletion (AUTH-5). True when it decided where the app opens.
     * - same account as the session: the deletion is taken up again (the screens resume it, with no new re-authentication);
     * - no session any more (the account is gone: deleted by the previous run before it was killed, or from the console): that is a
     *   SUCCESS, not a failure: the local data is erased and the carousel says it once;
     * - the session of ANOTHER account: the marker is not theirs, it is ignored and erased.
     */
    private suspend fun resolveAccountDeletion(uid: String?): Boolean {
        val marker = deletionMarker ?: return false
        val mark = marker.read() ?: return false
        if (uid != null && uid != mark.uid) {
            marker.clear()
            return false
        }
        if (uid == null) {
            if (mark.accountDeleted) {
                // Firebase confirmed the deletion before the app was killed: that is a success, not a failure.
                finishDeletionBehindTheScreen(mark.uid)
            } else {
                // No session and no proof of deletion (forced sign-out, token revoked...): neutral and honest, the marker stays.
                endSessionKeepingDeletionMarker()
            }
            return true
        }
        sessionActive = true
        beginAccountDeletion()
        return true
    }

    /** The deletion is over but its end screen was never seen: erase what is left, then the carousel with its banner. */
    private suspend fun finishDeletionBehindTheScreen(uid: String) {
        eraseAfterAccountDeletion(uid)
        mutableNotice.value = SignedOutNotice.ACCOUNT_DELETED
        leaveAfterAccountDeletion()
    }

    // --- Deletion of the account (AUTH-5) ------------------------------------------------------------------

    private val mutableDeletionBlocked = MutableStateFlow(false)

    /** True after a deletion cancelled because a Miff showed up: one calm message, shown over the app until it is acknowledged. */
    val deletionBlockedNotice: StateFlow<Boolean> = mutableDeletionBlocked.asStateFlow()

    fun dismissDeletionBlockedNotice() {
        mutableDeletionBlocked.value = false
    }

    override suspend fun cancelAccountDeletion() {
        withContext(NonCancellable) {
            // What stopped for the deletion starts again: a new generation (tracking and the screens keyed on it), then the usual routing.
            generation++
            ending = false
            sessionActive = true
            mutableDeletionBlocked.value = true
            // The account is kept (SESS-2): this phone is the active one again; its session is verified (and written back if the step that deleted it ran).
            device.reopen()
            resolveCurrent()
        }
    }

    override suspend fun endSessionKeepingDeletionMarker() {
        withContext(NonCancellable) {
            ending = true
            cache.markPurgePending()
            stopEverything()
            cache.purge()
            // Not "account unavailable": the sign-in screen says the deletion will be finished once the person is back.
            finishSignedOut(AppDestination.SIGN_IN, SignedOutNotice.DELETION_PENDING, markReturning = true)
        }
    }

    /**
     * Step 0 of the deletion (criterion SC15): from now on the app shows the deletion screens, and every listener, the monitoring of the
     * places and every write of status or position is stopped, BEFORE the first document goes. Without it the listeners would receive
     * refusals as the documents disappear and announce "access lost", and a late event could write a status or a position again.
     * Idempotent: also called when a deletion is taken up again.
     */
    override suspend fun beginAccountDeletion() {
        withContext(NonCancellable) {
            // Our own end of session: the loss watcher must not react to it.
            ending = true
            sessionActive = true
            mutableState.value = SessionState.Ready(AppDestination.ACCOUNT_DELETION)
            // SESS-2: our own deletion of the session (step 2 bis) must not look like a replacement, and nothing is verified or written meanwhile.
            device.suspendForDeletion()
            stopEverything()
        }
    }

    /**
     * Step 9 (tickets 5.5 and 5.10): the local Firestore cache is purged (the flag is recorded first, so that an app killed here purges at
     * the next launch), the preferences of the account, the draft of the profile, the pending consent and withdrawals are erased, the
     * collection of statistics and crash reports is cut (unsent reports deleted, installation identifier of Analytics reset), the local
     * session is closed. The marker goes LAST: until then, a kill leads to the same cleaning at the next launch. The language and the fact
     * of having seen the carousel are kept (nothing personal).
     */
    override suspend fun eraseAfterAccountDeletion(uid: String) {
        withContext(NonCancellable) {
            ending = true
            cache.markPurgePending()
            stopEverything()
            cache.purge()
            localData.forEach {
                try {
                    it.erase(uid)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // One preference that does not go must not keep the others: all of them are tried.
                }
            }
            drafts?.clear()
            pending.clear()
            withdrawals?.clear(uid)
            privacy.eraseCollectedData()
            accountSnapshot = null
            generation++
            // The identifier of the deleted account is forgotten (the session was deleted at step 2 bis).
            device.release(null)
            gateway.signOut()
            deletionMarker?.clear()
        }
    }

    /** "Continuer" on the end screen (and the relaunch case): back to the welcome carousel, then sign-up / sign-in. */
    fun leaveAfterAccountDeletion() {
        sessionActive = false
        carouselAfterDeletion = true
        mutableState.value = SessionState.Ready(AppDestination.SIGN_UP)
        ending = false
    }

    /** New account created with e-mail + password: we know there is no profile, so no read is needed. */
    private fun completeEmailSignUp(
        uid: String,
        consent: ConsentChoice,
    ) {
        sessionActive = true
        flags.put(KEY_RETURNING_USER, true)
        // The tap on "Rejoindre" accepted the terms AND the statistics (decision of 2026-10-28).
        pending.save(PendingConsent(uid, ConsentVersions.TERMS, analyticsAccepted = true))
        privacy.applyAccountConsent(true)
        tracker.track(AnalyticsEvent.SignUpCompleted(AnalyticsEvent.Method.EMAIL))
        mutableState.value = SessionState.Ready(AppDestination.PROFILE)
    }

    /** Sign-in by any method (or Google from the sign-up screen): read the account, THEN decide what is collected. */
    private suspend fun completeAuthentication(
        uid: String,
        method: AnalyticsEvent.Method,
        signUpConsent: ConsentChoice?,
    ) {
        sessionActive = true
        // The same account signs in again while its deletion is pending (session closed behind our back): the deletion resumes, it is not
        // routed as a normal account (its profile may be gone already).
        if (deletionMarker?.read()?.uid == uid) {
            beginAccountDeletion()
            return
        }
        // Nothing is collected until the account's own choice has been read (paused, nothing deleted yet).
        privacy.pauseCollection()
        if (signUpConsent != null) {
            pending.save(PendingConsent(uid, ConsentVersions.TERMS, analyticsAccepted = true))
        }
        mutableState.value = SessionState.Loading
        val load = accounts.load(uid)
        if (gateway.currentUid != uid) return
        when (load) {
            AccountLoad.Unavailable -> mutableState.value = SessionState.LookupFailed
            is AccountLoad.Loaded -> applyAccount(uid, load.snapshot, method, signUpGesture = signUpConsent != null)
        }
    }

    /**
     * [method] is set when a sign-in or sign-up gesture just happened (events may be sent), null at a cold start.
     * Existing profile: the ACCOUNT's choice rules (even if the sign-up screen was used); no profile: the choice
     * given by the tap on the sign-up screen and recorded on this device rules, and only if it was recorded for this
     * account (a person arriving on the consent panel has recorded nothing yet: nothing is collected).
     */
    private fun applyAccount(
        uid: String,
        snapshot: AccountSnapshot,
        method: AnalyticsEvent.Method?,
        signUpGesture: Boolean,
    ) {
        val recorded: PendingConsent?
        accountSnapshot = snapshot
        if (snapshot.profileExists) {
            recorded = null
            pending.clear()
            drafts?.clear()
            // A withdrawal made without network and not yet recorded wins over what the account still says.
            val collect = snapshot.analyticsConsentEnabled && withdrawals?.isPending(uid) != true
            privacy.applyAccountConsent(collect)
            if (method != null &&
                collect
            ) {
                tracker.track(AnalyticsEvent.SignInCompleted(method))
            }
        } else {
            recorded = pending.read(uid)
            privacy.applyAccountConsent(recorded?.analyticsAccepted == true)
            if (method != null && signUpGesture && recorded?.analyticsAccepted == true) {
                tracker.track(AnalyticsEvent.SignUpCompleted(method))
            }
        }
        val destination =
            resolveDestination(
                signedIn = true,
                account = snapshot,
                hasPendingTerms = recorded != null,
                returningUser = true,
            )
        // The app counts as "used" once a usable account exists; a refused consent panel leaves no trace.
        if (method != null && destination != AppDestination.CONSENT_PANEL) flags.put(KEY_RETURNING_USER, true)
        mutableState.value = SessionState.Ready(destination)
        if (snapshot.profileExists) onAccountResolved(uid)
    }

    private fun entryDestination(): AppDestination =
        resolveDestination(
            signedIn = false,
            account = null,
            hasPendingTerms = false,
            returningUser = flags.get(KEY_RETURNING_USER, false),
        )

    // The deletion of the account ends its own session in the middle of the screens: the loss watcher must never take that for a loss.
    private fun AppDestination.requiresSession(): Boolean =
        this != AppDestination.SIGN_UP && this != AppDestination.SIGN_IN && this != AppDestination.ACCOUNT_DELETION

    companion object {
        const val KEY_RETURNING_USER = "returning_user"

        /** The longest wait for one thing that must stop at the end of a session (the rest of its stop goes on in the background). */
        const val STOP_WAIT_MILLIS = 5_000L

        /** The calm "used on another phone" note has not been shown yet (written by the forced sign-out, read at the next start). */
        const val KEY_REPLACED_NOTICE_PENDING = "session_replaced_notice_pending"
    }
}
