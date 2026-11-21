package com.tomessa.miff.data.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.tomessa.miff.domain.billing.MiffPlusProducts
import com.tomessa.miff.domain.billing.PhaseRecurrence
import com.tomessa.miff.domain.billing.StoreOffer
import com.tomessa.miff.domain.billing.StorePhase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Google Play Billing Library (version 9.1.0), the only way to sell a subscription on Google Play. See the note on tokens in [BillingGateway.kt]: nothing that comes out
 * of the library is logged.
 *
 * - ONE [BillingClient], created on first use (nothing is built while the purchase is switched off), connected on demand, reconnected by the library itself
 *   (`enableAutoServiceReconnection`).
 * - Pending payments are enabled (deferred payment: Google Play says "pending", NOTHING is granted until it is confirmed; the server only grants a purchase that is paid).
 * - The app NEVER acknowledges a purchase and never consumes anything: the server acknowledges it after checking it (ticket section 6, point 4).
 * - Every call is bounded in time and never throws: a failure is a plain value ([CatalogResult.Error], [LaunchResult.NETWORK]...).
 */
class PlayBillingGateway(
    private val context: Context,
    private val timeoutMillis: Long = CALL_TIMEOUT_MILLIS,
) : BillingGateway {
    private val mutableEvents = MutableSharedFlow<PurchaseEvent>(extraBufferCapacity = EVENT_BUFFER)
    override val events: Flow<PurchaseEvent> = mutableEvents.asSharedFlow()

    private val client: BillingClient by lazy {
        BillingClient
            .newBuilder(context.applicationContext)
            .setListener { result, purchases -> mutableEvents.tryEmit(eventOf(result, purchases)) }
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build()
    }

    // The products already fetched, kept for the purchase sheet (it needs the ProductDetails object, not only the token).
    @Volatile private var details: ProductDetails? = null
    private val connectLock = Mutex()

    /** True once the client is connected to the Play Store (connects if needed). */
    private suspend fun connected(): Boolean {
        if (client.isReady) return true
        return connectLock.withLock {
            if (client.isReady) return@withLock true
            withTimeoutOrNull(timeoutMillis) {
                suspendCancellableCoroutine { continuation ->
                    client.startConnection(
                        object : BillingClientStateListener {
                            override fun onBillingSetupFinished(result: BillingResult) {
                                if (continuation.isActive) {
                                    continuation.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                                }
                            }

                            override fun onBillingServiceDisconnected() {
                                // The library reconnects by itself at the next call (enableAutoServiceReconnection).
                            }
                        },
                    )
                }
            } == true
        }
    }

    override suspend fun loadCatalog(): CatalogResult {
        try {
            if (!connected()) return CatalogResult.Error
            val product =
                QueryProductDetailsParams.Product
                    .newBuilder()
                    .setProductId(MiffPlusProducts.PRODUCT_ID)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()
            val result =
                withTimeoutOrNull(timeoutMillis) { client.queryProductDetails(params) } ?: return CatalogResult.Error
            return when (result.billingResult.responseCode) {
                BillingClient.BillingResponseCode.OK -> {
                    val found = result.productDetailsList?.firstOrNull { it.productId == MiffPlusProducts.PRODUCT_ID }
                    details = found
                    val offers = found?.subscriptionOfferDetails
                    if (offers.isNullOrEmpty()) {
                        CatalogResult.NotAvailable
                    } else {
                        CatalogResult.Loaded(
                            offers.map(::toStoreOffer),
                        )
                    }
                }

                else -> {
                    kindOf(result.billingResult.responseCode).let {
                        if (it ==
                            Kind.UNAVAILABLE
                        ) {
                            CatalogResult.NotAvailable
                        } else {
                            CatalogResult.Error
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return CatalogResult.Error
        }
    }

    override suspend fun ownedPurchases(): OwnedResult {
        try {
            if (!connected()) return OwnedResult.Error
            val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
            val result =
                withTimeoutOrNull(timeoutMillis) { client.queryPurchasesAsync(params) } ?: return OwnedResult.Error
            if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) return OwnedResult.Error
            return OwnedResult.Loaded(ownedOf(result.purchasesList.orEmpty()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return OwnedResult.Error
        }
    }

    override suspend fun launch(
        activity: Activity,
        offerToken: String,
        purchaseId: String,
    ): LaunchResult {
        try {
            if (!connected()) return LaunchResult.NETWORK
            // The product details of the last catalogue read: asked again if the process lost them (the offer token is checked by the store).
            val product =
                details ?: (loadCatalog() as? CatalogResult.Loaded)?.let { details } ?: return LaunchResult.UNAVAILABLE
            val productParams =
                BillingFlowParams.ProductDetailsParams
                    .newBuilder()
                    .setProductDetails(product)
                    .setOfferToken(offerToken)
                    .build()
            // The ONLY identifier given to the store: the random purchase id of the server (never the account id, e-mail or any personal data).
            val flow =
                BillingFlowParams
                    .newBuilder()
                    .setProductDetailsParamsList(listOf(productParams))
                    .setObfuscatedAccountId(purchaseId)
                    .build()
            // The sheet of the store is opened from the main thread (a rule of the library).
            val result = withContext(Dispatchers.Main.immediate) { client.launchBillingFlow(activity, flow) }
            return when (result.responseCode) {
                BillingClient.BillingResponseCode.OK -> {
                    LaunchResult.STARTED
                }

                BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                    LaunchResult.ALREADY_OWNED
                }

                else -> {
                    when (kindOf(result.responseCode)) {
                        Kind.UNAVAILABLE -> LaunchResult.UNAVAILABLE
                        Kind.NETWORK -> LaunchResult.NETWORK
                        else -> LaunchResult.FAILED
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return LaunchResult.FAILED
        }
    }

    private fun eventOf(
        result: BillingResult,
        purchases: List<Purchase>?,
    ): PurchaseEvent =
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                PurchaseEvent.Updated(ownedOf(purchases.orEmpty()))
            }

            BillingClient.BillingResponseCode.USER_CANCELED -> {
                PurchaseEvent.Canceled
            }

            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                PurchaseEvent.AlreadyOwned
            }

            else -> {
                when (kindOf(result.responseCode)) {
                    Kind.UNAVAILABLE -> PurchaseEvent.Unavailable
                    Kind.NETWORK -> PurchaseEvent.Network
                    else -> PurchaseEvent.Failed
                }
            }
        }

    private enum class Kind { UNAVAILABLE, NETWORK, OTHER }

    private fun kindOf(responseCode: Int): Kind =
        when (responseCode) {
            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE,
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
            BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED,
            -> Kind.UNAVAILABLE

            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
            BillingClient.BillingResponseCode.NETWORK_ERROR,
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
            -> Kind.NETWORK

            else -> Kind.OTHER
        }

    internal companion object {
        const val CALL_TIMEOUT_MILLIS = 15_000L
        const val EVENT_BUFFER = 16

        /** Only the purchases of Miff+ count; a purchase in any other state than "purchased" or "pending" (unspecified) is ignored, and so is one the store marks as suspended. */
        fun ownedOf(purchases: List<Purchase>): List<OwnedPurchase> =
            purchases
                .filter { MiffPlusProducts.PRODUCT_ID in it.products && !it.isSuspended }
                .mapNotNull { purchase ->
                    val state =
                        when (purchase.purchaseState) {
                            Purchase.PurchaseState.PURCHASED -> OwnedState.PURCHASED
                            Purchase.PurchaseState.PENDING -> OwnedState.PENDING
                            else -> return@mapNotNull null
                        }
                    OwnedPurchase(purchase.purchaseToken, state, purchase.isAcknowledged)
                }

        fun toStoreOffer(offer: ProductDetails.SubscriptionOfferDetails): StoreOffer =
            StoreOffer(
                basePlanId = offer.basePlanId,
                offerId = offer.offerId,
                offerToken = offer.offerToken,
                phases =
                    offer.pricingPhases.pricingPhaseList.map { phase ->
                        StorePhase(
                            priceMicros = phase.priceAmountMicros,
                            currencyCode = phase.priceCurrencyCode,
                            formattedPrice = phase.formattedPrice,
                            billingPeriod = phase.billingPeriod,
                            recurrence =
                                when (phase.recurrenceMode) {
                                    ProductDetails.RecurrenceMode.INFINITE_RECURRING -> PhaseRecurrence.INFINITE
                                    ProductDetails.RecurrenceMode.FINITE_RECURRING -> PhaseRecurrence.FINITE
                                    ProductDetails.RecurrenceMode.NON_RECURRING -> PhaseRecurrence.NON_RECURRING
                                    else -> PhaseRecurrence.UNKNOWN
                                },
                        )
                    },
            )
    }
}
