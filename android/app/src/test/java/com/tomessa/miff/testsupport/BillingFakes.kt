package com.tomessa.miff.testsupport

import android.app.Activity
import com.tomessa.miff.data.billing.BillingGateway
import com.tomessa.miff.data.billing.CatalogResult
import com.tomessa.miff.data.billing.LaunchResult
import com.tomessa.miff.data.billing.OwnedResult
import com.tomessa.miff.data.billing.PrepareResult
import com.tomessa.miff.data.billing.PurchaseBackend
import com.tomessa.miff.data.billing.PurchaseEvent
import com.tomessa.miff.data.billing.SyncResult
import com.tomessa.miff.data.subscription.SubscriptionStore
import com.tomessa.miff.domain.billing.PhaseRecurrence
import com.tomessa.miff.domain.billing.StoreOffer
import com.tomessa.miff.domain.billing.StorePhase
import com.tomessa.miff.domain.subscription.EntitlementDocument
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow

/** The store of the tests: nothing of Google Play, everything is set by the test (see PremiumCoordinatorTest). */
class FakeBillingGateway : BillingGateway {
    val shared = MutableSharedFlow<PurchaseEvent>(extraBufferCapacity = 16)
    override val events = shared

    var catalog: CatalogResult = CatalogResult.Loaded(emptyList())
    var owned: OwnedResult = OwnedResult.Loaded(emptyList())
    var launchResult = LaunchResult.STARTED
    var afterLaunch: (() -> Unit)? = null
    val launches = mutableListOf<Pair<String, String>>()
    var ownedCalls = 0

    /** A store that is slow to answer (virtual time). */
    var catalogDelayMillis = 0L

    override suspend fun loadCatalog(): CatalogResult {
        if (catalogDelayMillis > 0) delay(catalogDelayMillis)
        return catalog
    }

    override suspend fun ownedPurchases(): OwnedResult {
        ownedCalls++
        return owned
    }

    override suspend fun launch(
        activity: Activity,
        offerToken: String,
        purchaseId: String,
    ): LaunchResult {
        launches += offerToken to purchaseId
        afterLaunch?.invoke()
        return launchResult
    }
}

/** The Miff server of the tests. [clock] gives the time of each check (virtual time). */
class FakeBillingBackend(
    private val clock: () -> Long = { 0L },
) : PurchaseBackend {
    var prepare: PrepareResult = PrepareResult.Ready(PURCHASE_ID)
    val answers = ArrayDeque<SyncResult>()
    var default: SyncResult = SyncResult.Synced("trial")
    val syncs = mutableListOf<Pair<String, Long>>()
    var prepares = 0
    var purges = 0

    override suspend fun preparePurchaseId(): PrepareResult {
        prepares++
        return prepare
    }

    override suspend fun sync(purchaseToken: String): SyncResult {
        syncs += purchaseToken to clock()
        return answers.removeFirstOrNull() ?: default
    }

    override suspend fun purgeSubscriptionData(): Boolean {
        purges++
        return true
    }

    companion object {
        const val PURCHASE_ID = "7b0c9a3e-1f2d-4c5b-8a6e-9d0f1a2b3c4d"
    }
}

/** `entitlements/{uid}` of the tests. */
class FakeEntitlementStore : SubscriptionStore {
    var document: EntitlementDocument? = null
    var failure: Throwable? = null
    var reads = 0

    override suspend fun read(uid: String): EntitlementDocument? {
        reads++
        failure?.let { throw it }
        return document
    }
}

/** The offers of Google Play for Miff+ as the tests need them: the prices are TEST DATA written by the store double, never by the app. */
fun miffPlusStoreOffers(trial: Boolean): List<StoreOffer> =
    buildList {
        add(
            StoreOffer(
                "annuel",
                null,
                "tok-annual",
                listOf(StorePhase(59_880_000, "EUR", "59,88 €", "P1Y", PhaseRecurrence.INFINITE)),
            ),
        )
        if (trial) {
            add(
                StoreOffer(
                    "annuel",
                    "essai",
                    "tok-trial",
                    listOf(
                        StorePhase(0, "EUR", "Gratuit", "P1W", PhaseRecurrence.FINITE),
                        StorePhase(59_880_000, "EUR", "59,88 €", "P1Y", PhaseRecurrence.INFINITE),
                    ),
                ),
            )
        }
        add(
            StoreOffer(
                "mensuel",
                null,
                "tok-monthly",
                listOf(StorePhase(6_990_000, "EUR", "6,99 €", "P1M", PhaseRecurrence.INFINITE)),
            ),
        )
    }
