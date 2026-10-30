package com.tomessa.miff.testsupport

import com.tomessa.miff.data.auth.AccountLoad
import com.tomessa.miff.data.auth.AccountRepository
import com.tomessa.miff.data.auth.AuthGateway
import com.tomessa.miff.data.auth.CachePurger
import com.tomessa.miff.data.auth.NetworkMonitor
import com.tomessa.miff.data.auth.PendingConsentStore
import com.tomessa.miff.data.privacy.BooleanStore
import com.tomessa.miff.data.privacy.CollectionSwitch
import com.tomessa.miff.data.privacy.PrivacySettings
import com.tomessa.miff.data.session.SessionManager
import com.tomessa.miff.data.session.SessionTeardown
import com.tomessa.miff.domain.analytics.AnalyticsEvent
import com.tomessa.miff.domain.analytics.AnalyticsSink
import com.tomessa.miff.domain.analytics.AnalyticsTracker
import com.tomessa.miff.domain.auth.AccountSnapshot
import com.tomessa.miff.domain.auth.AuthFailure
import com.tomessa.miff.domain.auth.AuthOutcome
import com.tomessa.miff.domain.auth.PendingConsent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory doubles for the authentication tests. None of them records a password. */
class MemoryBooleanStore : BooleanStore {
    val values = mutableMapOf<String, Boolean>()

    override fun get(
        key: String,
        default: Boolean,
    ) = values[key] ?: default

    override fun put(
        key: String,
        value: Boolean,
    ) {
        values[key] = value
    }
}

class MemoryPendingConsentStore : PendingConsentStore {
    var stored: PendingConsent? = null

    override fun save(consent: PendingConsent) {
        stored = consent
    }

    override fun read(uid: String): PendingConsent? = stored?.takeIf { it.uid == uid }

    override fun clear() {
        stored = null
    }
}

class RecordingSwitch : CollectionSwitch {
    /** Every [setEnabled] call (`false` = no consent, which deletes the reports not yet sent). */
    val calls = mutableListOf<Boolean>()

    /** Number of [pause] calls (collection off, nothing deleted). */
    var pauseCalls = 0

    /** All calls in order: "on", "off" (setEnabled) and "pause". */
    val log = mutableListOf<String>()

    /** [log] without immediate repetitions (the manager may pause twice in a row; what matters is the sequence). */
    fun states(): List<String> = log.filterIndexed { i, entry -> i == 0 || log[i - 1] != entry }

    override fun setEnabled(enabled: Boolean) {
        calls += enabled
        log += if (enabled) "on" else "off"
    }

    override fun pause() {
        pauseCalls++
        log += "pause"
    }
}

class RecordingSink : AnalyticsSink {
    val events = mutableListOf<Pair<String, Map<String, Any>>>()

    override fun log(
        eventName: String,
        params: Map<String, Any>,
    ) {
        events += eventName to params
    }

    val names get() = events.map { it.first }
}

class FakeNetwork(
    online: Boolean = true,
) : NetworkMonitor {
    private val state = kotlinx.coroutines.flow.MutableStateFlow(online)

    /** Changing it also tells the ones who wait for the network to come back. */
    var online: Boolean
        get() = state.value
        set(value) {
            state.value = value
        }

    override fun isOnline() = state.value

    override fun onlineChanges(): kotlinx.coroutines.flow.Flow<Boolean> = state
}

/** Shared timeline of the calls that matter for ordering (flush, purge, sign-out...). */
class Timeline {
    val entries = mutableListOf<String>()

    operator fun plusAssign(entry: String) {
        entries += entry
    }
}

class FakeAuthGateway(
    private val timeline: Timeline = Timeline(),
) : AuthGateway {
    private val uidState = MutableStateFlow<String?>(null)
    var method: AnalyticsEvent.Method? = null
    var signInResult: AuthOutcome = AuthOutcome.Success("uid-1", isNewUser = false)
    var createResult: AuthOutcome = AuthOutcome.Success("uid-1", isNewUser = true)
    var googleResult: AuthOutcome = AuthOutcome.Success("uid-1", isNewUser = true)
    var deleteResults = ArrayDeque<AuthFailure?>()
    var reauthResult: AuthFailure? = null
    var signInCalls = 0
    var createCalls = 0
    var googleCalls = 0
    var deleteCalls = 0
    var signOutCalls = 0

    override val currentUid: String? get() = uidState.value

    override fun uidChanges(): Flow<String?> = uidState

    fun setSignedIn(
        uid: String?,
        method: AnalyticsEvent.Method? = AnalyticsEvent.Method.EMAIL,
    ) {
        this.method = method
        uidState.value = uid
    }

    override suspend fun signInWithEmail(
        email: String,
        password: String,
    ): AuthOutcome {
        signInCalls++
        return signInResult.also { if (it is AuthOutcome.Success) setSignedIn(it.uid, AnalyticsEvent.Method.EMAIL) }
    }

    override suspend fun createAccountWithEmail(
        email: String,
        password: String,
    ): AuthOutcome {
        createCalls++
        return createResult.also { if (it is AuthOutcome.Success) setSignedIn(it.uid, AnalyticsEvent.Method.EMAIL) }
    }

    override suspend fun signInWithGoogle(idToken: String): AuthOutcome {
        googleCalls++
        return googleResult.also { if (it is AuthOutcome.Success) setSignedIn(it.uid, AnalyticsEvent.Method.GOOGLE) }
    }

    override suspend fun reauthenticateWithGoogle(idToken: String): AuthFailure? {
        timeline += "reauth"
        return reauthResult
    }

    override suspend fun deleteCurrentAccount(): AuthFailure? {
        deleteCalls++
        timeline += "delete"
        val result = if (deleteResults.isEmpty()) null else deleteResults.removeFirst()
        if (result == null) uidState.value = null
        return result
    }

    override fun signInMethod() = method

    var displayName: String? = null

    override fun displayName(): String? = displayName

    var email: String? = null

    override fun currentEmail(): String? = email

    override fun accountSignIn(): com.tomessa.miff.domain.auth.AccountSignIn =
        when (method) {
            AnalyticsEvent.Method.EMAIL -> com.tomessa.miff.domain.auth.AccountSignIn.PASSWORD
            AnalyticsEvent.Method.GOOGLE -> com.tomessa.miff.domain.auth.AccountSignIn.GOOGLE
            else -> com.tomessa.miff.domain.auth.AccountSignIn.OTHER
        }

    override fun signOut() {
        signOutCalls++
        timeline += "signOut"
        uidState.value = null
    }
}

class FakeAccountRepository(
    private val onLoad: () -> Unit = {},
) : AccountRepository {
    var result: AccountLoad = AccountLoad.Loaded(AccountSnapshot(false, false, false))
    var loadCalls = 0

    fun setLoaded(
        profileExists: Boolean,
        hasMembership: Boolean = false,
        analyticsConsent: Boolean = false,
        hasPhone: Boolean = true,
        hasTermsVersion: Boolean = true,
    ) {
        result =
            AccountLoad.Loaded(
                AccountSnapshot(profileExists, hasMembership, analyticsConsent, hasPhone, hasTermsVersion),
            )
    }

    override suspend fun load(uid: String): AccountLoad {
        loadCalls++
        onLoad()
        return result
    }
}

class FakeCachePurger(
    private val timeline: Timeline,
) : CachePurger {
    var purgeCalls = 0
    var purgeIfPendingCalls = 0

    override suspend fun flushPendingWrites() {
        timeline += "flush"
    }

    override suspend fun purge(): Boolean {
        purgeCalls++
        timeline += "purge"
        return true
    }

    override fun markPurgePending() {
        timeline += "markPending"
    }

    override suspend fun purgeIfPending() {
        purgeIfPendingCalls++
    }
}

/** Everything a SessionManager needs, wired with doubles, and what to look at afterwards. */
class SessionHarness(
    scope: CoroutineScope,
    extraTeardown: List<SessionTeardown> = emptyList(),
    drafts: com.tomessa.miff.data.onboarding.ProfileDraftStore? = null,
    withdrawals: com.tomessa.miff.data.privacy.PendingWithdrawals? = null,
    onAccountResolved: (String) -> Unit = {},
) {
    val timeline = Timeline()
    val gateway = FakeAuthGateway(timeline)
    val flags = MemoryBooleanStore()
    val pending = MemoryPendingConsentStore()
    val analyticsSwitch = RecordingSwitch()
    val crashSwitch = RecordingSwitch()
    val privacy = PrivacySettings(MemoryBooleanStore(), analyticsSwitch, crashSwitch)
    val sink = RecordingSink()
    val tracker = AnalyticsTracker(sink) { privacy.analyticsEnabled.value }
    val cache = FakeCachePurger(timeline)

    /** What was collected at the moment the account was read: it must be nothing (criterion X9). */
    var collectionWhenAccountRead: Pair<Boolean, Boolean>? = null

    /** Runs while the account is being read (to simulate something happening during the read). */
    var duringAccountRead: () -> Unit = {}
    val accounts =
        FakeAccountRepository {
            collectionWhenAccountRead = privacy.analyticsEnabled.value to privacy.crashReportsEnabled.value
            duringAccountRead()
        }
    var teardownCalls = 0

    val manager =
        SessionManager(
            gateway = gateway,
            accounts = accounts,
            pending = pending,
            flags = flags,
            privacy = privacy,
            tracker = tracker,
            cache = cache,
            teardown =
                listOf(
                    SessionTeardown {
                        teardownCalls++
                        timeline += "teardown"
                    },
                ) + extraTeardown,
            scope = scope,
            drafts = drafts,
            withdrawals = withdrawals,
            onAccountResolved = onAccountResolved,
        )
}
