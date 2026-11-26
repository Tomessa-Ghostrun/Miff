package com.tomessa.miff.ui.paywall

import android.app.Activity
import com.tomessa.miff.data.billing.CatalogResult
import com.tomessa.miff.data.billing.OwnedPurchase
import com.tomessa.miff.data.billing.OwnedResult
import com.tomessa.miff.data.billing.OwnedState
import com.tomessa.miff.data.billing.PremiumCoordinator
import com.tomessa.miff.data.billing.PrepareResult
import com.tomessa.miff.data.billing.PurchaseEvent
import com.tomessa.miff.data.billing.SyncResult
import com.tomessa.miff.data.subscription.SubscriptionRepository
import com.tomessa.miff.domain.billing.OffersResult
import com.tomessa.miff.domain.paywall.MonotonicClock
import com.tomessa.miff.domain.paywall.PaywallNotice
import com.tomessa.miff.domain.paywall.PaywallOffer
import com.tomessa.miff.domain.paywall.PaywallProgress
import com.tomessa.miff.domain.subscription.EntitlementDocument
import com.tomessa.miff.testsupport.FakeBillingBackend
import com.tomessa.miff.testsupport.FakeBillingGateway
import com.tomessa.miff.testsupport.FakeEntitlementStore
import com.tomessa.miff.testsupport.miffPlusStoreOffers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The offer screen with the purchase (ticket paywall-v2.md section 5.2, criteria PW4, PW5, PW9 to PW13): the prices are the store's, a person who already has Miff+ is never offered
 * a second subscription, a purchase asks the coordinator (which lives as long as the app), and every outcome has its calm state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PaywallPurchaseViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class World(
        scope: TestScope,
    ) {
        val gateway = FakeBillingGateway()
        val backend = FakeBillingBackend { scope.testScheduler.currentTime }
        val store = FakeEntitlementStore()
        val repository =
            SubscriptionRepository(store, currentUid = { "uid-1" }, nowMillis = { NOW }, timeoutMillis = 8_000L)
        val coordinator =
            PremiumCoordinator(
                gateway,
                backend,
                repository,
                currentUid = { "uid-1" },
                scope = scope.backgroundScope,
                clock = MonotonicClock { scope.testScheduler.currentTime },
            )
        val activity = Activity()

        init {
            gateway.catalog = CatalogResult.Loaded(miffPlusStoreOffers(trial = true))
        }
    }

    private fun TestScope.modelOf(w: World) = PaywallPurchaseViewModel(w.coordinator).also { runCurrent() }

    private fun purchased() = OwnedPurchase("token-token-token-token", OwnedState.PURCHASED, acknowledged = false)

    // ---- the offers ---------------------------------------------------------------------------------------------------------------------------

    @Test
    fun itStartsLoading_thenShowsTheTrialOffer_withThePricesOfTheStore() =
        runTest(dispatcher) {
            val w = World(this)
            val model = PaywallPurchaseViewModel(w.coordinator)
            assertEquals(PaywallOffer.Loading, model.state.value.offer)
            runCurrent()
            val offer = model.state.value.offer as PaywallOffer.TrialEligible
            assertEquals("59,88 €", offer.annual.formatted)
            assertEquals("6,99 €", offer.monthly.formatted)
            assertNull(model.state.value.notice)
            assertEquals(PaywallProgress.NONE, model.state.value.progress)
        }

    @Test
    fun anAccountThatCannotHaveTheTrial_getsTheTwoPlans_criterionAN3() =
        runTest(dispatcher) {
            val w = World(this)
            w.gateway.catalog = CatalogResult.Loaded(miffPlusStoreOffers(trial = false))
            val model = modelOf(w)
            assertTrue(model.state.value.offer is PaywallOffer.Plans)
        }

    @Test
    fun noOffer_orAnError_orAStoreThatIsTooSlow_isTheCalmUnavailableState_noPriceNoButton() =
        runTest(dispatcher) {
            val none = World(this)
            none.gateway.catalog = CatalogResult.NotAvailable
            assertEquals(PaywallOffer.Unavailable, modelOf(none).state.value.offer)
            val error = World(this)
            error.gateway.catalog = CatalogResult.Error
            assertEquals(PaywallOffer.Unavailable, modelOf(error).state.value.offer)
            val partial = World(this)
            partial.gateway.catalog = CatalogResult.Loaded(miffPlusStoreOffers(trial = true).take(1))
            assertEquals(PaywallOffer.Unavailable, modelOf(partial).state.value.offer)
            val slow = World(this)
            slow.gateway.catalogDelayMillis = 60_000
            val slowModel = PaywallPurchaseViewModel(slow.coordinator)
            runCurrent()
            assertEquals(PaywallOffer.Loading, slowModel.state.value.offer)
            advanceTimeBy(PaywallPurchaseViewModel.OFFERS_TIMEOUT_MILLIS + 1)
            runCurrent()
            assertEquals(PaywallOffer.Unavailable, slowModel.state.value.offer)
        }

    @Test
    fun aPersonWhoAlreadyHasMiffPlus_isNeverOfferedASecondSubscription_criterionPW13() =
        runTest(dispatcher) {
            val w = World(this)
            w.store.document = EntitlementDocument(true, NOW + 10, "google_play", state = "active")
            val state = modelOf(w).state.value
            assertEquals(PaywallOffer.Subscribed, state.offer)
            // Not a purchase made here: no celebration (the confetti only play for a person who JUST became a subscriber).
            assertFalse(state.celebrate)
        }

    @Test
    fun anAccountWhoseSubscriptionIsOver_seesTheOfferAgain() =
        runTest(dispatcher) {
            val w = World(this)
            w.store.document = EntitlementDocument(false, NOW - 10, "google_play", state = "expired")
            assertTrue(modelOf(w).state.value.offer is PaywallOffer.TrialEligible)
        }

    @Test
    fun aServerThatCannotBeRead_doesNotHideTheOffer() =
        runTest(dispatcher) {
            val w = World(this)
            w.store.failure = java.io.IOException("offline")
            assertTrue(modelOf(w).state.value.offer is PaywallOffer.TrialEligible)
        }

    // ---- the purchase --------------------------------------------------------------------------------------------------------------------------

    @Test
    fun theTrialButton_buysTheTrialOffer_theAnnualButtonTheBaseOffer_theMonthlyButtonTheMonthlyOne() =
        runTest(dispatcher) {
            listOf<Pair<(PaywallPurchaseViewModel, Activity) -> Unit, String>>(
                ({ m: PaywallPurchaseViewModel, a: Activity -> m.startTrial(a) }) to "tok-trial",
                ({ m: PaywallPurchaseViewModel, a: Activity -> m.buyAnnual(a) }) to "tok-annual",
                ({ m: PaywallPurchaseViewModel, a: Activity -> m.buyMonthly(a) }) to "tok-monthly",
            ).forEach { (action, token) ->
                val w = World(this)
                val model = modelOf(w)
                action(model, w.activity)
                runCurrent()
                assertEquals(
                    token,
                    w.gateway.launches
                        .single()
                        .first,
                )
                // The store is open: the buttons are asleep and the screen says so.
                assertEquals(PaywallProgress.PURCHASING, model.state.value.progress)
            }
        }

    @Test
    fun aSuccessfulPurchase_showsTheConfirmation() =
        runTest(dispatcher) {
            val w = World(this)
            w.gateway.afterLaunch = { w.gateway.shared.tryEmit(PurchaseEvent.Updated(listOf(purchased()))) }
            val model = modelOf(w)
            model.startTrial(w.activity)
            runCurrent()
            assertEquals(PaywallOffer.Subscribed, model.state.value.offer)
            assertTrue("just subscribed: the confirmation is celebrated", model.state.value.celebrate)
            assertEquals(PaywallProgress.NONE, model.state.value.progress)
            assertNull(model.state.value.notice)
        }

    @Test
    fun aPendingPayment_saysItIsBeingConfirmed_andGrantsNothing_criterionPW10() =
        runTest(dispatcher) {
            val w = World(this)
            w.gateway.afterLaunch =
                {
                    w.gateway.shared.tryEmit(
                        PurchaseEvent.Updated(listOf(OwnedPurchase("tok-tok-tok-tok-tok", OwnedState.PENDING, false))),
                    )
                }
            val model = modelOf(w)
            model.buyMonthly(w.activity)
            runCurrent()
            assertEquals(PaywallNotice.PENDING, model.state.value.notice)
            assertTrue(model.state.value.offer is PaywallOffer.TrialEligible)
        }

    @Test
    fun aPersonWhoClosesTheStoreSheet_isBackOnTheOffer_withNoMessage_criterionPW10() =
        runTest(dispatcher) {
            val w = World(this)
            w.gateway.afterLaunch = { w.gateway.shared.tryEmit(PurchaseEvent.Canceled) }
            val model = modelOf(w)
            model.startTrial(w.activity)
            runCurrent()
            assertNull(model.state.value.notice)
            assertEquals(PaywallProgress.NONE, model.state.value.progress)
            assertTrue(model.state.value.offer is PaywallOffer.TrialEligible)
        }

    @Test
    fun aPurchaseOfAnotherMiffAccount_isRefusedCalmly_withNoRetryOfTheSame() =
        runTest(dispatcher) {
            val w = World(this)
            w.backend.default = SyncResult.AlreadyLinked
            w.gateway.afterLaunch = { w.gateway.shared.tryEmit(PurchaseEvent.Updated(listOf(purchased()))) }
            val model = modelOf(w)
            model.startTrial(w.activity)
            runCurrent()
            assertEquals(PaywallNotice.OTHER_ACCOUNT, model.state.value.notice)
        }

    @Test
    fun anErrorOfTheStoreOrOfTheServer_isTheCalmSentence_andNothingIsLost() =
        runTest(dispatcher) {
            val network = World(this)
            network.gateway.afterLaunch = { network.gateway.shared.tryEmit(PurchaseEvent.Network) }
            val m1 = modelOf(network)
            m1.startTrial(network.activity)
            runCurrent()
            assertEquals(PaywallNotice.ERROR, m1.state.value.notice)
            val serverDown = World(this)
            serverDown.backend.prepare = PrepareResult.Unavailable
            val m2 = modelOf(serverDown)
            m2.startTrial(serverDown.activity)
            runCurrent()
            assertEquals(PaywallNotice.ERROR, m2.state.value.notice)
            assertTrue(serverDown.gateway.launches.isEmpty())
            val paidNotChecked = World(this)
            paidNotChecked.backend.default = SyncResult.Unavailable
            paidNotChecked.gateway.afterLaunch =
                { paidNotChecked.gateway.shared.tryEmit(PurchaseEvent.Updated(listOf(purchased()))) }
            val m3 = modelOf(paidNotChecked)
            m3.startTrial(paidNotChecked.activity)
            runCurrent()
            assertEquals(PaywallNotice.ERROR, m3.state.value.notice)
        }

    @Test
    fun aProductThatTheStoreDoesNotKnow_atPurchaseTime_isTheUnavailableState() =
        runTest(dispatcher) {
            val w = World(this)
            w.gateway.afterLaunch = { w.gateway.shared.tryEmit(PurchaseEvent.Unavailable) }
            val model = modelOf(w)
            model.startTrial(w.activity)
            runCurrent()
            assertEquals(PaywallOffer.Unavailable, model.state.value.offer)
        }

    @Test
    fun nothingHappens_whileAWorkIsUnderWay_orWhileThereIsNoOffer() =
        runTest(dispatcher) {
            val w = World(this)
            val model = modelOf(w)
            model.startTrial(w.activity)
            runCurrent()
            model.buyMonthly(w.activity)
            model.startTrial(w.activity)
            model.restore()
            runCurrent()
            assertEquals(1, w.gateway.launches.size)
            val none = World(this)
            none.gateway.catalog = CatalogResult.NotAvailable
            val noOffer = modelOf(none)
            noOffer.startTrial(none.activity)
            runCurrent()
            assertTrue(none.gateway.launches.isEmpty())
        }

    // ---- restoration -----------------------------------------------------------------------------------------------------------------------

    @Test
    fun restore_findsAPurchase_andShowsTheConfirmation_criterionPW12() =
        runTest(dispatcher) {
            val w = World(this)
            w.gateway.owned = OwnedResult.Loaded(listOf(purchased()))
            w.backend.default = SyncResult.Synced("active")
            val model = modelOf(w)
            model.restore()
            runCurrent()
            assertEquals(PaywallOffer.Subscribed, model.state.value.offer)
            assertTrue("just subscribed: the confirmation is celebrated", model.state.value.celebrate)
        }

    @Test
    fun restore_findingNothing_namesNothingButTheStore_andOffersToTryAgain() =
        runTest(dispatcher) {
            val w = World(this)
            val model = modelOf(w)
            model.restore()
            runCurrent()
            assertEquals(PaywallNotice.RESTORE_NONE, model.state.value.notice)
            assertTrue(model.state.value.offer is PaywallOffer.TrialEligible)
        }

    @Test
    fun restore_whileTheWorkRuns_saysRestoring() =
        runTest(dispatcher) {
            val w = World(this)
            w.gateway.owned = OwnedResult.Loaded(listOf(purchased()))
            w.backend.default = SyncResult.RateLimited
            val model = modelOf(w)
            model.restore()
            runCurrent()
            assertEquals(PaywallProgress.RESTORING, model.state.value.progress)
        }

    // ---- retry and old results ---------------------------------------------------------------------------------------------------------------

    @Test
    fun retry_withNoOffers_asksTheStoreAgain_withOffers_justClearsTheSentence() =
        runTest(dispatcher) {
            val w = World(this)
            w.gateway.catalog = CatalogResult.Error
            val model = modelOf(w)
            assertEquals(PaywallOffer.Unavailable, model.state.value.offer)
            w.gateway.catalog = CatalogResult.Loaded(miffPlusStoreOffers(trial = true))
            model.retry()
            runCurrent()
            assertTrue(model.state.value.offer is PaywallOffer.TrialEligible)
            w.gateway.afterLaunch = { w.gateway.shared.tryEmit(PurchaseEvent.Network) }
            model.startTrial(w.activity)
            runCurrent()
            assertEquals(PaywallNotice.ERROR, model.state.value.notice)
            model.retry()
            assertNull(model.state.value.notice)
            assertTrue(model.state.value.offer is PaywallOffer.TrialEligible)
        }

    @Test
    fun aResultThatEndedBeforeThisDisplay_isNotToldToIt() =
        runTest(dispatcher) {
            val w = World(this)
            w.gateway.afterLaunch = { w.gateway.shared.tryEmit(PurchaseEvent.Network) }
            val first = modelOf(w)
            first.startTrial(w.activity)
            runCurrent()
            assertEquals(PaywallNotice.ERROR, first.state.value.notice)
            // A new display of the offer: the old error is not told again.
            val second = modelOf(w)
            assertNull(second.state.value.notice)
            assertEquals(PaywallProgress.NONE, second.state.value.progress)
        }

    @Test
    fun theOfferForEachAnswerOfTheStore() {
        assertEquals(PaywallOffer.Unavailable, PaywallPurchaseViewModel.offerOf(OffersResult.Error))
        assertEquals(PaywallOffer.Unavailable, PaywallPurchaseViewModel.offerOf(OffersResult.NotAvailable))
    }

    private companion object {
        const val NOW = 10_000L
    }
}
