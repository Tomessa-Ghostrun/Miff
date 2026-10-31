package com.tomessa.miff.data.session

import com.tomessa.miff.data.auth.AccountLoad
import com.tomessa.miff.data.auth.AccountRepository
import com.tomessa.miff.data.auth.AuthGateway
import com.tomessa.miff.data.auth.CachePurger
import com.tomessa.miff.data.auth.PendingConsentStore
import com.tomessa.miff.data.onboarding.ProfileDraftStore
import com.tomessa.miff.data.privacy.BooleanStore
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
     * The local draft of the profile steps. Erased whenever it can no longer be of any use: nobody is signed in, or the
     * profile exists (belt and braces next to the teardown of sign-out and the erasure after the confirmed write).
     */
    private val drafts: ProfileDraftStore? = null,
) {
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
        return notice
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
                    completeAuthentication(outcome.uid, AnalyticsEvent.Method.EMAIL, signUpConsent = null)
                    null
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
                    completeEmailSignUp(outcome.uid, consent)
                    null
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
                    completeAuthentication(outcome.uid, AnalyticsEvent.Method.GOOGLE, signUpConsent)
                    null
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
                    ending = false
                    return RefuseResult.Cancelled
                }

                GoogleTokenResult.Unavailable -> {
                    failure = AuthFailure.UNKNOWN
                }

                is GoogleTokenResult.Failed -> {
                    failure = token.failure
                }
            }
        }
        if (failure != null) {
            // Stay on the panel: never a usable account without (a).
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
            cache.markPurgePending()
            mutableState.value = SessionState.Loading
            stopEverything()
            cache.flushPendingWrites()
            cache.purge()
            finishSignedOut(AppDestination.SIGN_IN, notice = null, markReturning = true)
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
            cache.markPurgePending()
            mutableState.value = SessionState.Loading
            stopEverything()
            // Signed out already: queued writes would be refused, so no flush.
            cache.purge()
            finishSignedOut(AppDestination.SIGN_IN, SignedOutNotice.ACCOUNT_UNAVAILABLE, markReturning = true)
        }
    }

    private suspend fun stopEverything() {
        teardown.forEach {
            try {
                it.stop()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A listener that does not stop must not keep the person signed in.
            }
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
        if (uid == null) {
            sessionActive = false
            // No session = no consent to send anything: a report left by a crash before the person said yes goes away.
            accountSnapshot = null
            drafts?.clear()
            privacy.applyAccountConsent(false)
            pending.clear()
            mutableState.value = SessionState.Ready(entryDestination())
            return
        }
        sessionActive = true
        // Off until the account has been read (also at "Réessayer"), but the previous crash report is kept for now.
        privacy.pauseCollection()
        mutableState.value = SessionState.Loading
        val load = accounts.load(uid)
        // The session may have been lost while we were reading: the loss handler already put the app back to sign-in.
        if (gateway.currentUid != uid) return
        when (load) {
            AccountLoad.Unavailable -> mutableState.value = SessionState.LookupFailed
            is AccountLoad.Loaded -> applyAccount(uid, load.snapshot, method = null, signUpGesture = false)
        }
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
            privacy.applyAccountConsent(snapshot.analyticsConsentEnabled)
            if (method != null &&
                snapshot.analyticsConsentEnabled
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
    }

    private fun entryDestination(): AppDestination =
        resolveDestination(
            signedIn = false,
            account = null,
            hasPendingTerms = false,
            returningUser = flags.get(KEY_RETURNING_USER, false),
        )

    private fun AppDestination.requiresSession(): Boolean =
        this != AppDestination.SIGN_UP && this != AppDestination.SIGN_IN

    companion object {
        const val KEY_RETURNING_USER = "returning_user"
    }
}
