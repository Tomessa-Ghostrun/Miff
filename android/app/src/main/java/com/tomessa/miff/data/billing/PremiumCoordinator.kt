package com.tomessa.miff.data.billing

import android.app.Activity
import com.tomessa.miff.data.subscription.SubscriptionRepository
import com.tomessa.miff.domain.billing.OffersResult
import com.tomessa.miff.domain.billing.PlanChoice
import com.tomessa.miff.domain.billing.PurchaseOutcome
import com.tomessa.miff.domain.billing.RestoreOutcome
import com.tomessa.miff.domain.billing.chooseOffers
import com.tomessa.miff.domain.paywall.MonotonicClock
import com.tomessa.miff.domain.subscription.StoreState
import com.tomessa.miff.domain.subscription.SubscriptionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** What the screens see of the work in progress (a purchase or a restoration). */
sealed interface PurchaseSession {
    /** Nothing under way, nothing to tell. */
    data object Idle : PurchaseSession

    /** The store sheet is open, or the server is checking: the buttons are asleep and the screen says "Un instant…". */
    data class Working(
        val restoring: Boolean,
    ) : PurchaseSession

    /**
     * The last work ended. It STAYS until the next work starts: every screen that is alive may tell it, once (it keeps the [serial] it has already told), so that two screens
     * never take the result from each other. [outcome] for a purchase, [restore] for a restoration (exactly one of the two is set).
     */
    data class Finished(
        val serial: Long,
        val outcome: PurchaseOutcome?,
        val restore: RestoreOutcome?,
    ) : PurchaseSession
}

/**
 * THE purchase of Miff+ (ticket docs/tickets/premium-achat-reel.md sections 5, 6, 8, 10), for the whole life of the app (it does not depend on a screen: the result of a payment
 * is taken into account even if the offer was closed meanwhile, criterion CR11).
 *
 * The rules, in short:
 *  1. The app NEVER grants Miff+ by itself. A payment is only a PROOF that is sent to the server (`synchroniserAchat`), which checks it with Google Play, links it to the
 *     account that is signed in and writes `entitlements/{uid}` (the only place the rights come from; the rules forbid the app to write it).
 *  2. The only identifier given to Google Play is the RANDOM purchase id of the server (`preparerAchat`): no e-mail, no account id, no personal data.
 *  3. The app never acknowledges a purchase: the server does, after checking (otherwise Google refunds it 3 days after the payment).
 *  4. One check per minute and per account on the server side: the calls are spaced here too ([syncSpacingMillis]), one at a time. A purchase that could not be checked
 *     (no connection) is checked again when the app opens ([reconcile]); nothing paid is lost.
 *  5. "Pending" (deferred payment) grants NOTHING until the server says the purchase is paid.
 *  6. A purchase that belongs to another Miff account (answer "already linked") is refused calmly and NOT sent again by itself (only by an explicit restoration).
 *
 * No token, identifier or amount is logged or sent in an event.
 */
class PremiumCoordinator(
    private val gateway: BillingGateway,
    private val backend: PurchaseBackend,
    private val subscriptions: SubscriptionRepository,
    private val currentUid: () -> String?,
    private val scope: CoroutineScope,
    private val clock: MonotonicClock,
    private val syncSpacingMillis: Long = SYNC_SPACING_MILLIS,
    private val reconcileSpacingMillis: Long = RECONCILE_SPACING_MILLIS,
    private val purchaseWaitMaxMillis: Long = PURCHASE_WAIT_MAX_MILLIS,
) {
    private val mutableSession = MutableStateFlow<PurchaseSession>(PurchaseSession.Idle)
    val session: StateFlow<PurchaseSession> = mutableSession.asStateFlow()

    private val mutableAccessGranted =
        MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /**
     * Said each time Miff+ is confirmed for the account (a purchase, a restoration, a purchase found by the silent look, or an offer that finds the account already subscribed): the screens
     * that hold a copy of the plan read it again (see `MiffDirectory.followPremiumGrants`). Nothing is said when nothing changed for the person.
     */
    val accessGranted: SharedFlow<Unit> = mutableAccessGranted.asSharedFlow()

    private val busy = AtomicBoolean(false)
    private val serial = AtomicLong(0L)

    /** The purchase sheet that is waiting for its answer (set BEFORE the sheet opens), completed by the first event of the store. */
    @Volatile private var awaiting: CompletableDeferred<PurchaseEvent>? = null

    private val syncLock = Mutex()

    @Volatile private var lastSyncAt: Long? = null

    @Volatile private var lastReconcileAt: Long? = null

    @Volatile private var reconcileUid: String? = null

    // Purchases (kept in memory only) that the server said belong to another Miff account: not sent again by the silent paths.
    private val refusedTokens: MutableSet<String> = ConcurrentHashMap.newKeySet()

    init {
        // Listens from the start: an event that comes while nobody waits (a pending payment confirmed later, a purchase made from the Play Store) is checked silently.
        scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            gateway.events.collect { event ->
                val waiting = awaiting
                if (waiting != null && !waiting.isCompleted) {
                    waiting.complete(event)
                } else {
                    onUnsolicited(event)
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Offers
    // ---------------------------------------------------------------------------------------------------------------------

    /** The prices and offers of Google Play for this account: nothing is ever written by hand. */
    suspend fun loadOffers(): OffersResult =
        when (val catalog = gateway.loadCatalog()) {
            is CatalogResult.Loaded -> {
                chooseOffers(catalog.offers)?.let { OffersResult.Loaded(it) }
                    ?: OffersResult.NotAvailable
            }

            CatalogResult.NotAvailable -> {
                OffersResult.NotAvailable
            }

            CatalogResult.Error -> {
                OffersResult.Error
            }
        }

    // ---------------------------------------------------------------------------------------------------------------------
    // Purchase and restoration (one at a time)
    // ---------------------------------------------------------------------------------------------------------------------

    /** Starts a purchase of [choice] (the button the person touched). Ignored while another work is under way. The result comes through [session]. */
    fun startPurchase(
        activity: Activity,
        choice: PlanChoice,
    ) {
        if (!busy.compareAndSet(false, true)) return
        mutableSession.value = PurchaseSession.Working(restoring = false)
        // A weak reference: this work lives as long as the app, the screen must not be kept alive by it (a rotation destroys the activity).
        val host = WeakReference(activity)
        scope.launch {
            val outcome =
                try {
                    runPurchase(host, choice)
                } catch (e: CancellationException) {
                    awaiting = null
                    busy.set(false)
                    mutableSession.value = PurchaseSession.Idle
                    throw e
                } catch (e: Exception) {
                    PurchaseOutcome.FAILED
                }
            finish(outcome = outcome, restore = null)
        }
    }

    /** "Restaurer mes achats" / "Actualiser mon abonnement": looks at what the store knows and has the server check it. The result comes through [session]. */
    fun startRestore() {
        if (!busy.compareAndSet(false, true)) return
        mutableSession.value = PurchaseSession.Working(restoring = true)
        scope.launch {
            val outcome =
                try {
                    runRestore()
                } catch (e: CancellationException) {
                    busy.set(false)
                    mutableSession.value = PurchaseSession.Idle
                    throw e
                } catch (e: Exception) {
                    RestoreOutcome.FAILED
                }
            finish(outcome = null, restore = outcome)
        }
    }

    private fun finish(
        outcome: PurchaseOutcome?,
        restore: RestoreOutcome?,
    ) {
        mutableSession.value = PurchaseSession.Finished(serial.incrementAndGet(), outcome, restore)
        busy.set(false)
        if (outcome == PurchaseOutcome.GRANTED || restore == RestoreOutcome.FOUND) noteAccessGranted()
    }

    /** Miff+ is on for this account (confirmed by the server): tells the screens that keep a copy of the plan. */
    fun noteAccessGranted() {
        mutableAccessGranted.tryEmit(Unit)
    }

    private suspend fun runPurchase(
        host: WeakReference<Activity>,
        choice: PlanChoice,
    ): PurchaseOutcome {
        if (currentUid() == null) return PurchaseOutcome.FAILED
        val purchaseId =
            when (val prepared = backend.preparePurchaseId()) {
                is PrepareResult.Ready -> prepared.purchaseId
                PrepareResult.Unavailable -> return PurchaseOutcome.NETWORK
                PrepareResult.Refused -> return PurchaseOutcome.FAILED
            }
        val waiting = CompletableDeferred<PurchaseEvent>()
        awaiting = waiting
        try {
            // The screen may be gone while the id was fetched (a rotation, the person left): nothing was started, nothing is said, the person can touch the button again.
            val launched = open(host, choice.offerToken, purchaseId) ?: return PurchaseOutcome.CANCELED
            when (launched) {
                LaunchResult.STARTED -> Unit
                LaunchResult.ALREADY_OWNED -> return adoptOwned()
                LaunchResult.UNAVAILABLE -> return PurchaseOutcome.UNAVAILABLE
                LaunchResult.NETWORK -> return PurchaseOutcome.NETWORK
                LaunchResult.FAILED -> return PurchaseOutcome.FAILED
            }
            // The person is in the sheet of the store: no timeout of ours while they decide, only a very long safety (an event that never comes must not freeze the buttons).
            val event = withTimeoutOrNull(purchaseWaitMaxMillis) { waiting.await() } ?: return PurchaseOutcome.FAILED
            return when (event) {
                is PurchaseEvent.Updated -> handleUpdated(event.purchases)
                PurchaseEvent.Canceled -> PurchaseOutcome.CANCELED
                PurchaseEvent.AlreadyOwned -> adoptOwned()
                PurchaseEvent.Unavailable -> PurchaseOutcome.UNAVAILABLE
                PurchaseEvent.Network -> PurchaseOutcome.NETWORK
                PurchaseEvent.Failed -> PurchaseOutcome.FAILED
            }
        } finally {
            awaiting = null
        }
    }

    /** Opens the sheet of the store from the activity that is still on screen; the activity is only held for the length of this call. Null = it is gone. */
    private suspend fun open(
        host: WeakReference<Activity>,
        offerToken: String,
        purchaseId: String,
    ): LaunchResult? {
        val activity = host.get()?.takeUnless { it.isFinishing || it.isDestroyed } ?: return null
        return gateway.launch(activity, offerToken, purchaseId)
    }

    private suspend fun handleUpdated(purchases: List<OwnedPurchase>): PurchaseOutcome {
        val paid = purchases.firstOrNull { it.state == OwnedState.PURCHASED }
        if (paid != null) return verify(paid)
        if (purchases.any { it.state == OwnedState.PENDING }) return PurchaseOutcome.PENDING
        return PurchaseOutcome.FAILED
    }

    /** "Already owned": the store already has this subscription for the account; the purchase it lists is checked like a new one. */
    private suspend fun adoptOwned(): PurchaseOutcome {
        val owned = gateway.ownedPurchases() as? OwnedResult.Loaded ?: return PurchaseOutcome.NETWORK
        val outcome = handleUpdated(owned.purchases)
        // "Already owned" with nothing listed: a subscription ON HOLD (the store does not list it): said as such, never "the purchase could not be finalised".
        if (outcome == PurchaseOutcome.FAILED && owned.purchases.isEmpty() &&
            serverStateIsOnHold()
        ) {
            return PurchaseOutcome.ON_HOLD
        }
        return outcome
    }

    private suspend fun serverStateIsOnHold(): Boolean {
        val status = subscriptions.load() as? SubscriptionStatus.Store ?: return false
        return status.state == StoreState.ON_HOLD
    }

    private suspend fun verify(purchase: OwnedPurchase): PurchaseOutcome =
        when (val result = syncSpaced(purchase.token, patient = true)) {
            is SyncResult.Synced -> outcomeOfState(result.state)
            SyncResult.AlreadyLinked -> PurchaseOutcome.OTHER_ACCOUNT
            SyncResult.RateLimited -> PurchaseOutcome.NETWORK
            SyncResult.Rejected -> PurchaseOutcome.FAILED
            SyncResult.Unavailable -> PurchaseOutcome.NETWORK
        }

    private suspend fun runRestore(): RestoreOutcome {
        val owned = gateway.ownedPurchases() as? OwnedResult.Loaded ?: return RestoreOutcome.NETWORK
        val paid = owned.purchases.firstOrNull { it.state == OwnedState.PURCHASED }
        if (paid != null) {
            // An explicit restoration: even a purchase refused before is sent again (the person asks).
            refusedTokens.remove(paid.token)
            return when (val result = syncSpaced(paid.token, patient = true)) {
                is SyncResult.Synced -> {
                    when {
                        grantsAccess(result.state) -> RestoreOutcome.FOUND
                        result.state == STATE_ON_HOLD -> RestoreOutcome.ON_HOLD
                        else -> RestoreOutcome.NONE_FOUND
                    }
                }

                SyncResult.AlreadyLinked -> {
                    RestoreOutcome.OTHER_ACCOUNT
                }

                SyncResult.RateLimited -> {
                    RestoreOutcome.NETWORK
                }

                SyncResult.Rejected -> {
                    RestoreOutcome.FAILED
                }

                SyncResult.Unavailable -> {
                    RestoreOutcome.NETWORK
                }
            }
        }
        if (owned.purchases.any { it.state == OwnedState.PENDING }) return RestoreOutcome.PENDING
        // Nothing in the store: the server may still know a subscription (bought on another device or in another store, or put on hold by the store, which the list omits).
        val status = subscriptions.load() ?: return RestoreOutcome.NETWORK
        return when {
            status.hasAccess -> RestoreOutcome.FOUND
            (status as? SubscriptionStatus.Store)?.state == StoreState.ON_HOLD -> RestoreOutcome.ON_HOLD
            else -> RestoreOutcome.NONE_FOUND
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // The silent paths
    // ---------------------------------------------------------------------------------------------------------------------

    /**
     * Called when the app opens (signed in): looks at what the store holds and has the server check it ONLY if needed: a purchase is there AND (the server does not know it
     * yet, or the access is off, or the purchase is not acknowledged yet). It is the net under a lost notification, a payment made while the phone was offline, a reinstall
     * or a new phone (ticket section 6, point 6). Spaced ([reconcileSpacingMillis]) and silent: nothing is shown, nothing is retried in a loop.
     */
    suspend fun reconcile() {
        val uid = currentUid() ?: return
        val now = clock.nowMillis()
        if (reconcileUid != uid) {
            reconcileUid = uid
            lastReconcileAt = null
            refusedTokens.clear()
        }
        val last = lastReconcileAt
        if (last != null && now - last < reconcileSpacingMillis) return
        lastReconcileAt = now
        if (busy.get()) return
        val owned = gateway.ownedPurchases() as? OwnedResult.Loaded ?: return
        val paid =
            owned.purchases.firstOrNull { it.state == OwnedState.PURCHASED && it.token !in refusedTokens } ?: return
        val status = subscriptions.load() ?: return
        val needed = !status.hasAccess || !paid.acknowledged
        if (!needed) return
        silentSync(paid.token)
    }

    private fun onUnsolicited(event: PurchaseEvent) {
        if (event !is PurchaseEvent.Updated) return
        val paid =
            event.purchases.firstOrNull { it.state == OwnedState.PURCHASED && it.token !in refusedTokens } ?: return
        scope.launch { silentSync(paid.token) }
    }

    private suspend fun silentSync(token: String) {
        // A purchase of another account, or one the server refuses for good (unknown product, other package...), is not sent again by itself: only an explicit restoration does.
        val result = syncSpaced(token, patient = false)
        if (result == SyncResult.AlreadyLinked || result == SyncResult.Rejected) refusedTokens.add(token)
        if (result is SyncResult.Synced && grantsAccess(result.state)) noteAccessGranted()
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // The check by the server, one at a time, spaced
    // ---------------------------------------------------------------------------------------------------------------------

    /**
     * ONE call to `synchroniserAchat` at a time, at least [syncSpacingMillis] after the previous one (the server allows one per minute and per account and counts every call).
     * [patient]: an answer the person waits for may wait for its turn and retry once after "too soon"; a silent path gives up instead (it will come back at the next opening).
     */
    private suspend fun syncSpaced(
        token: String,
        patient: Boolean,
    ): SyncResult =
        syncLock.withLock {
            val last = lastSyncAt
            if (last != null) {
                val wait = last + syncSpacingMillis - clock.nowMillis()
                if (wait > 0) {
                    if (!patient) return@withLock SyncResult.RateLimited
                    clock.waitFor(wait)
                }
            }
            // The minute of the server starts when it ANSWERED (an answer, a refusal, "too soon"): a call that never reached it (no network) does not use the minute.
            var result = backend.sync(token)
            if (result != SyncResult.Unavailable) lastSyncAt = clock.nowMillis()
            if (result == SyncResult.RateLimited && patient) {
                clock.waitFor(syncSpacingMillis)
                result = backend.sync(token)
                if (result != SyncResult.Unavailable) lastSyncAt = clock.nowMillis()
            }
            result
        }

    private fun outcomeOfState(state: String?): PurchaseOutcome =
        when {
            state == STATE_PENDING -> PurchaseOutcome.PENDING
            state == STATE_ON_HOLD -> PurchaseOutcome.ON_HOLD
            grantsAccess(state) -> PurchaseOutcome.GRANTED
            else -> PurchaseOutcome.FAILED
        }

    private fun grantsAccess(state: String?): Boolean = state in GRANTING_STATES

    /** The status after a purchase or a restoration, for the screens (null = unreadable). */
    suspend fun status(): SubscriptionStatus? = subscriptions.load()

    companion object {
        /** The server answers one check per minute and per account: a little more, for the clocks. */
        const val SYNC_SPACING_MILLIS = 61_000L

        /** The silent look at the store at the opening of the app: at most once every 5 minutes. */
        const val RECONCILE_SPACING_MILLIS = 5 * 60_000L

        /** A sheet of the store that gives no answer for 20 minutes is forgotten (a later answer is then handled as an unexpected event). */
        const val PURCHASE_WAIT_MAX_MILLIS = 20 * 60_000L

        private const val STATE_PENDING = "pending"
        private const val STATE_ON_HOLD = "on_hold"
        private val GRANTING_STATES = setOf("trial", "active", "canceled", "grace")
    }
}
