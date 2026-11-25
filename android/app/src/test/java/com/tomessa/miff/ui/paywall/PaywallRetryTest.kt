package com.tomessa.miff.ui.paywall

import android.app.Activity
import com.tomessa.miff.data.billing.CatalogResult
import com.tomessa.miff.data.billing.PremiumCoordinator
import com.tomessa.miff.data.billing.PurchaseEvent
import com.tomessa.miff.data.subscription.SubscriptionRepository
import com.tomessa.miff.domain.paywall.MonotonicClock
import com.tomessa.miff.domain.paywall.PaywallOffer
import com.tomessa.miff.testsupport.FakeBillingBackend
import com.tomessa.miff.testsupport.FakeBillingGateway
import com.tomessa.miff.testsupport.FakeEntitlementStore
import com.tomessa.miff.testsupport.miffPlusStoreOffers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** "Réessayer" after "not available" (review of SRV-8A): the offers are asked again. */
@OptIn(ExperimentalCoroutinesApi::class)
class PaywallRetryTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.coordinator(
        gateway: FakeBillingGateway,
        store: FakeEntitlementStore = FakeEntitlementStore(),
    ) = PremiumCoordinator(
        gateway,
        FakeBillingBackend { testScheduler.currentTime },
        SubscriptionRepository(store, currentUid = { "uid-1" }, nowMillis = { 10_000L }, timeoutMillis = 8_000L),
        currentUid = { "uid-1" },
        scope = backgroundScope,
        clock = MonotonicClock { testScheduler.currentTime },
    )

    @Test
    fun retryAfterAProductNotAvailableAtPurchaseTime_asksTheStoreAgain() =
        runTest(dispatcher) {
            val gateway =
                FakeBillingGateway().also {
                    it.catalog =
                        CatalogResult.Loaded(miffPlusStoreOffers(trial = true))
                }
            val model = PaywallPurchaseViewModel(coordinator(gateway))
            runCurrent()
            val activity = Activity()
            gateway.afterLaunch = { gateway.shared.tryEmit(PurchaseEvent.Unavailable) }
            model.startTrial(activity)
            runCurrent()
            assertEquals(PaywallOffer.Unavailable, model.state.value.offer)
            // "Réessayer" must do something: the offers are read again (the store answers now).
            gateway.afterLaunch = null
            model.retry()
            runCurrent()
            assertTrue(model.state.value.offer is PaywallOffer.TrialEligible)
        }
}
