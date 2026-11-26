package com.tomessa.miff.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomessa.miff.data.billing.PremiumCoordinator
import com.tomessa.miff.data.billing.PurchaseSession
import com.tomessa.miff.data.subscription.SubscriptionRepository
import com.tomessa.miff.domain.billing.OffersResult
import com.tomessa.miff.domain.billing.RestoreOutcome
import com.tomessa.miff.domain.subscription.StoreState
import com.tomessa.miff.domain.subscription.SubscriptionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Whether the "Abonnement" line of the Settings exists. Reads `entitlements/{uid}` ONCE when the Settings open (a ViewModel lives as long as that screen is in the back stack: going to
 * Mon compte and back does not read again, nor does a rotation).
 *
 * - Purchase switched ON ([purchaseOn]): the line is always there, for everybody (a free account finds how to get Miff+, a subscriber how to manage it). It does not wait for the read.
 * - Purchase switched OFF: as before (ticket ABO-1), only an account that HAS Miff+ (given by hand, or a store purchase made elsewhere) sees the line; nothing about a subscription
 *   for the others, no spinner, no message. A failed read leaves the line out.
 */
class SubscriptionRowViewModel(
    repository: SubscriptionRepository,
    purchaseOn: Boolean,
) : ViewModel() {
    private val mutableVisible = MutableStateFlow(purchaseOn)
    val visible: StateFlow<Boolean> = mutableVisible.asStateFlow()

    init {
        if (!purchaseOn) {
            viewModelScope.launch { mutableVisible.value = repository.load()?.hasAccess == true }
        }
    }
}

/** A calm sentence under the status (never red, never an alarm). */
enum class SubscriptionNotice {
    /** The store holds no Miff+ subscription for this store account. */
    NOTHING_FOUND,

    /** The subscription found is used by another Miff account. */
    OTHER_ACCOUNT,

    /** A payment is waiting for the store's confirmation. */
    PENDING,

    /** The refresh did not work this time (no connection...). */
    REFRESH_FAILED,
}

/** The prices the sentences use, as the store gives them (null = the store could not be reached: the sentences are then written without a price). */
data class SubscriptionPrices(
    val annual: String?,
    val monthly: String?,
)

data class SubscriptionUiState(
    val loading: Boolean = true,
    /** Null once [loading] is over = the server could not be read (offline...): a calm message and "Réessayer", never "you have nothing". */
    val status: SubscriptionStatus? = null,
    val prices: SubscriptionPrices? = null,
    /** "Actualiser mon abonnement" is under way. */
    val refreshing: Boolean = false,
    val notice: SubscriptionNotice? = null,
) {
    /**
     * The price in the next-charge sentence; null = unknown (sentence without price, the date only).
     *
     * Only a TRIAL has one: the annual price of the offer the person has just accepted. A subscription already running is renewed at the SAME price (decision of the PM, 2026-10-31,
     * docs/specs/paywall-premium.md), which may no longer be the one of today's catalog (an old subscriber keeps the price he subscribed at): the catalog price would be a promise the
     * store may not keep. The server does not give the price really billed (`entitlements/{uid}` has no amount), so none is shown until it does.
     */
    fun renewalPrice(): String? {
        val store = status as? SubscriptionStatus.Store ?: return null
        return if (store.state == StoreState.TRIAL) prices?.annual else null
    }
}

/**
 * The Abonnement screen (ticket premium-achat-reel.md section 8 and criteria AN2, AN6, AN7, AN8, AN9, AN10): reads `entitlements/{uid}` (the rights, written by the SERVER only),
 * and, only during a trial, the prices of the store for the sentence "Essai gratuit jusqu'au …, puis (prix)" (the price is NEVER read from the document: if the store is out of reach the
 * sentence is written without a price; a running subscription shows its renewal DATE only, see [SubscriptionUiState.renewalPrice]). "Actualiser mon abonnement" asks the coordinator to look at the store and have the server check it.
 *
 * [premium] is null while the purchase is switched off: there is then nothing to refresh and no price to read.
 */
class SubscriptionViewModel(
    private val subscriptions: SubscriptionRepository,
    private val premium: PremiumCoordinator?,
    private val pricesTimeoutMillis: Long = PRICES_TIMEOUT_MILLIS,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SubscriptionUiState())
    val uiState: StateFlow<SubscriptionUiState> = mutableState.asStateFlow()
    private var loadSerial = 0
    private var handledSerial: Long = (premium?.session?.value as? PurchaseSession.Finished)?.serial ?: -1L

    init {
        load()
        if (premium != null) {
            viewModelScope.launch { premium.session.collect(::onSession) }
        }
    }

    /** First read, "Réessayer", and after a refresh. */
    fun load() {
        val serial = ++loadSerial
        mutableState.update { it.copy(loading = true) }
        viewModelScope.launch {
            val status = subscriptions.load()
            val prices = if (premium != null && status.needsPrices()) readPrices(premium) else null
            if (serial != loadSerial) return@launch
            mutableState.update { it.copy(loading = false, status = status, prices = prices) }
        }
    }

    private suspend fun readPrices(coordinator: PremiumCoordinator): SubscriptionPrices? =
        when (val result = withTimeoutOrNull(pricesTimeoutMillis) { coordinator.loadOffers() }) {
            is OffersResult.Loaded -> {
                SubscriptionPrices(
                    result.offers.annual.price.formatted,
                    result.offers.monthly.price.formatted,
                )
            }

            else -> {
                null
            }
        }

    /** The screen is back in front (after the offer, or the page of the store): what the server knows may have changed. Not at the first display (the first read is under way). */
    fun onReturned() {
        if (!mutableState.value.loading) load()
    }

    /** "Actualiser mon abonnement". */
    fun refresh() {
        val coordinator = premium ?: return
        if (mutableState.value.refreshing) return
        mutableState.update { it.copy(notice = null) }
        coordinator.startRestore()
    }

    private fun onSession(session: PurchaseSession) {
        when (session) {
            PurchaseSession.Idle -> {
                mutableState.update { it.copy(refreshing = false) }
            }

            is PurchaseSession.Working -> {
                mutableState.update { it.copy(refreshing = true, notice = null) }
            }

            is PurchaseSession.Finished -> {
                if (session.serial <= handledSerial) return
                handledSerial = session.serial
                val notice =
                    when (session.restore) {
                        RestoreOutcome.NONE_FOUND -> SubscriptionNotice.NOTHING_FOUND
                        RestoreOutcome.OTHER_ACCOUNT -> SubscriptionNotice.OTHER_ACCOUNT
                        RestoreOutcome.PENDING -> SubscriptionNotice.PENDING
                        RestoreOutcome.NETWORK, RestoreOutcome.FAILED -> SubscriptionNotice.REFRESH_FAILED
                        RestoreOutcome.FOUND, RestoreOutcome.ON_HOLD, null -> null
                    }
                mutableState.update { it.copy(refreshing = false, notice = notice) }
                // What the server knows may have changed (a purchase made from the offer, or found by a restoration): read it again.
                load()
            }
        }
    }

    private fun SubscriptionStatus?.needsPrices(): Boolean {
        // Only the trial sentence shows a price (see SubscriptionUiState.renewalPrice): no call to the store for the others.
        val store = this as? SubscriptionStatus.Store ?: return false
        return store.state == StoreState.TRIAL
    }

    companion object {
        const val PRICES_TIMEOUT_MILLIS = 8_000L
    }
}
