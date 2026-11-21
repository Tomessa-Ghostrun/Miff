package com.tomessa.miff.data.billing

import android.app.Activity
import com.tomessa.miff.domain.billing.StoreOffer
import kotlinx.coroutines.flow.Flow

/*
 * SECURITY, for this file and the whole package: a PURCHASE TOKEN is the proof of a payment. It goes from Google Play to the Miff server (synchroniserAchat) and nowhere
 * else: never logged, never put in an analytics event, never in an exception message, never written to the phone's storage, never shown (every class that holds one
 * overrides toString). The only identifier given to Google Play is the RANDOM purchase id made by the server (never the account id, the e-mail or any personal data).
 */

/** A purchase of Miff+ as the store lists it (the token is private to the package, see above). */
class OwnedPurchase(
    internal val token: String,
    val state: OwnedState,
    /** The server acknowledges a purchase itself (before Google refunds it, 3 days after the payment): the app never does. An unacknowledged purchase must be sent to the server. */
    val acknowledged: Boolean,
) {
    override fun toString(): String = "OwnedPurchase($state, acknowledged=$acknowledged)"
}

enum class OwnedState { PURCHASED, PENDING }

/** The catalogue of Miff+ in the store: the offers made to this account, or why there are none. */
sealed interface CatalogResult {
    class Loaded(
        val offers: List<StoreOffer>,
    ) : CatalogResult

    /** No such product for this account or this device (not published, no Play Store, unsupported). */
    data object NotAvailable : CatalogResult

    /** The store did not answer (no connection, service down). */
    data object Error : CatalogResult
}

sealed interface OwnedResult {
    class Loaded(
        val purchases: List<OwnedPurchase>,
    ) : OwnedResult

    data object Error : OwnedResult
}

/** What the store says right after the purchase sheet was asked to open. Everything else comes later, from [BillingGateway.events]. */
enum class LaunchResult { STARTED, ALREADY_OWNED, UNAVAILABLE, NETWORK, FAILED }

/** What the store reports about a purchase in progress (the listener of Google Play, or the fake of the debug build). */
sealed interface PurchaseEvent {
    class Updated(
        val purchases: List<OwnedPurchase>,
    ) : PurchaseEvent

    data object Canceled : PurchaseEvent

    data object AlreadyOwned : PurchaseEvent

    data object Unavailable : PurchaseEvent

    data object Network : PurchaseEvent

    data object Failed : PurchaseEvent
}

/**
 * Everything the app needs from the store, behind an interface: the real one is the Google Play Billing Library ([PlayBillingGateway]); the debug build also has a FAKE store
 * (src/debug) to try the whole interface without the app being in the Play Console; the tests use their own double.
 */
interface BillingGateway {
    /** The offers of Miff+ made to this account. Never throws. */
    suspend fun loadCatalog(): CatalogResult

    /** The subscriptions of Miff+ the store knows for this account (a subscription put on hold by the store is not listed). Never throws. */
    suspend fun ownedPurchases(): OwnedResult

    /**
     * Opens the purchase sheet of the store for [offerToken], with the RANDOM [purchaseId] made by the server as the only identifier given to the store. The result of the
     * payment itself arrives on [events]. Never throws.
     */
    suspend fun launch(
        activity: Activity,
        offerToken: String,
        purchaseId: String,
    ): LaunchResult

    /** What happens to a purchase: the answer of a sheet that was opened, a pending payment that is confirmed later, a purchase made elsewhere while the app is open. */
    val events: Flow<PurchaseEvent>
}
