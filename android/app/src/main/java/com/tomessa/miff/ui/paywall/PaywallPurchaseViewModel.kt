package com.tomessa.miff.ui.paywall

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomessa.miff.data.billing.PremiumCoordinator
import com.tomessa.miff.data.billing.PurchaseSession
import com.tomessa.miff.domain.billing.MiffPlusOffers
import com.tomessa.miff.domain.billing.OffersResult
import com.tomessa.miff.domain.billing.PlanChoice
import com.tomessa.miff.domain.billing.PurchaseOutcome
import com.tomessa.miff.domain.billing.RestoreOutcome
import com.tomessa.miff.domain.paywall.PaywallNotice
import com.tomessa.miff.domain.paywall.PaywallOffer
import com.tomessa.miff.domain.paywall.PaywallProgress
import com.tomessa.miff.domain.paywall.PaywallUiState
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * What the offer screen shows when the purchase is switched on (ticket paywall-v2.md section 5.2 and premium-achat-reel.md section 6): the prices and offers of Google Play, the
 * progress of a purchase or a restoration, and the calm sentence about how the last attempt ended. ONE per display of the offer (it lives as long as the navigation entry: a
 * rotation keeps it, closing the screen and opening it again makes a new one).
 *
 * Nothing is written by hand: no price, no currency, no duration comes from here, they come from the store. A purchase is ASKED to [PremiumCoordinator], which lives as long as
 * the app: closing this screen does not cancel it, and its result is taken into account anyway (criterion CR11).
 *
 * A person who already has Miff+ (a race: another phone, a purchase just confirmed) is never offered a second subscription: the screen shows the confirmation (ticket 7.10, US4).
 */
class PaywallPurchaseViewModel(
    private val premium: PremiumCoordinator,
    private val offersTimeoutMillis: Long = OFFERS_TIMEOUT_MILLIS,
) : ViewModel() {
    private val mutableState = MutableStateFlow(PaywallUiState(PaywallOffer.Loading))
    val state: StateFlow<PaywallUiState> = mutableState.asStateFlow()

    private var offers: MiffPlusOffers? = null
    private var loadSerial = 0

    // A result that ended before this display opened is not told to it (the server is read again just below, and tells whether Miff+ is on).
    private var handledSerial: Long = (premium.session.value as? PurchaseSession.Finished)?.serial ?: -1L

    init {
        load()
        viewModelScope.launch { premium.session.collect(::onSession) }
    }

    /** Asks Google Play for the offers (and the server whether Miff+ is already on), at most [offersTimeoutMillis]; then the offer, "not available", or the confirmation. */
    fun load() {
        val serial = ++loadSerial
        mutableState.update { it.copy(offer = PaywallOffer.Loading, notice = null) }
        viewModelScope.launch {
            val subscribed = async { premium.status()?.hasAccess == true }
            val result = withTimeoutOrNull(offersTimeoutMillis) { premium.loadOffers() } ?: OffersResult.Error
            val already = subscribed.await()
            if (serial != loadSerial) return@launch
            when {
                already -> {
                    mutableState.update { it.copy(offer = PaywallOffer.Subscribed, notice = null) }
                    // The offer was opened by a gate that did not know yet (a copy of the plan read before the purchase): the screens that hold such a copy read it again.
                    premium.noteAccessGranted()
                }

                else -> {
                    offers = (result as? OffersResult.Loaded)?.offers
                    mutableState.update { it.copy(offer = offerOf(result)) }
                }
            }
        }
    }

    /** "Réessayer": the offers again when there are none, else just the calm sentence goes away (the buttons are still there). */
    fun retry() {
        // No offers, or the store said "not available" at purchase time: ask again; otherwise just the calm sentence goes away (the buttons are still there).
        if (offers == null ||
            mutableState.value.offer == PaywallOffer.Unavailable
        ) {
            load()
        } else {
            mutableState.update { it.copy(notice = null) }
        }
    }

    fun startTrial(activity: Activity) = buy(activity) { it.annualTrial }

    fun buyAnnual(activity: Activity) = buy(activity) { it.annual }

    fun buyMonthly(activity: Activity) = buy(activity) { it.monthly }

    private fun buy(
        activity: Activity,
        choose: (MiffPlusOffers) -> PlanChoice?,
    ) {
        if (mutableState.value.busy) return
        val choice = offers?.let(choose) ?: return
        mutableState.update { it.copy(notice = null) }
        premium.startPurchase(activity, choice)
    }

    fun restore() {
        if (mutableState.value.busy) return
        mutableState.update { it.copy(notice = null) }
        premium.startRestore()
    }

    private fun onSession(session: PurchaseSession) {
        when (session) {
            PurchaseSession.Idle -> {
                mutableState.update { it.copy(progress = PaywallProgress.NONE) }
            }

            is PurchaseSession.Working -> {
                mutableState.update {
                    it.copy(
                        progress = if (session.restoring) PaywallProgress.RESTORING else PaywallProgress.PURCHASING,
                        notice = null,
                    )
                }
            }

            is PurchaseSession.Finished -> {
                if (session.serial <= handledSerial) return
                handledSerial = session.serial
                session.outcome?.let(::onPurchased)
                session.restore?.let(::onRestored)
            }
        }
    }

    private fun onPurchased(outcome: PurchaseOutcome) {
        mutableState.update { current ->
            val idle = current.copy(progress = PaywallProgress.NONE)
            when (outcome) {
                PurchaseOutcome.GRANTED -> {
                    idle.copy(offer = PaywallOffer.Subscribed, notice = null, celebrate = true)
                }

                PurchaseOutcome.PENDING -> {
                    idle.copy(notice = PaywallNotice.PENDING)
                }

                // The person closed the sheet of the store: back to the offer, with no message at all.
                PurchaseOutcome.CANCELED -> {
                    idle.copy(notice = null)
                }

                PurchaseOutcome.OTHER_ACCOUNT -> {
                    idle.copy(notice = PaywallNotice.OTHER_ACCOUNT)
                }

                PurchaseOutcome.UNAVAILABLE -> {
                    idle.copy(offer = PaywallOffer.Unavailable, notice = null).also {
                        offers =
                            null
                    }
                }

                PurchaseOutcome.ON_HOLD -> {
                    idle.copy(notice = PaywallNotice.ON_HOLD)
                }

                PurchaseOutcome.NETWORK, PurchaseOutcome.FAILED -> {
                    idle.copy(notice = PaywallNotice.ERROR)
                }
            }
        }
    }

    private fun onRestored(outcome: RestoreOutcome) {
        mutableState.update { current ->
            val idle = current.copy(progress = PaywallProgress.NONE)
            when (outcome) {
                RestoreOutcome.FOUND -> idle.copy(offer = PaywallOffer.Subscribed, notice = null, celebrate = true)
                RestoreOutcome.NONE_FOUND -> idle.copy(notice = PaywallNotice.RESTORE_NONE)
                RestoreOutcome.OTHER_ACCOUNT -> idle.copy(notice = PaywallNotice.OTHER_ACCOUNT)
                RestoreOutcome.PENDING -> idle.copy(notice = PaywallNotice.PENDING)
                RestoreOutcome.ON_HOLD -> idle.copy(notice = PaywallNotice.ON_HOLD)
                RestoreOutcome.NETWORK, RestoreOutcome.FAILED -> idle.copy(notice = PaywallNotice.ERROR)
            }
        }
    }

    companion object {
        /** More than this without an answer from the store: "the offers are not available for the moment" (ticket paywall-v2 section 5.2). */
        const val OFFERS_TIMEOUT_MILLIS = 10_000L

        /**
         * The offer for what the store answered: with the trial when Google Play offers it to this account (it only does to an account that is eligible), else the two plans.
         * "Not available" and "error" both give the calm unavailable state: no price, no purchase button.
         */
        fun offerOf(result: OffersResult): PaywallOffer =
            when (result) {
                is OffersResult.Loaded -> {
                    val offers = result.offers
                    if (offers.annualTrial != null) {
                        PaywallOffer.TrialEligible(annual = offers.annualTrial.price, monthly = offers.monthly.price)
                    } else {
                        PaywallOffer.Plans(annual = offers.annual.price, monthly = offers.monthly.price)
                    }
                }

                OffersResult.NotAvailable, OffersResult.Error -> {
                    PaywallOffer.Unavailable
                }
            }
    }
}
