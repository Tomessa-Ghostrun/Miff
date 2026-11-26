package com.tomessa.miff.ui.settings

import com.tomessa.miff.data.billing.CatalogResult
import com.tomessa.miff.data.billing.OwnedPurchase
import com.tomessa.miff.data.billing.OwnedResult
import com.tomessa.miff.data.billing.OwnedState
import com.tomessa.miff.data.billing.PremiumCoordinator
import com.tomessa.miff.data.billing.SyncResult
import com.tomessa.miff.data.subscription.SubscriptionRepository
import com.tomessa.miff.domain.paywall.MonotonicClock
import com.tomessa.miff.domain.subscription.EntitlementDocument
import com.tomessa.miff.domain.subscription.StoreState
import com.tomessa.miff.domain.subscription.SubscriptionStatus
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
import java.io.IOException

/**
 * The Abonnement screen (ticket premium-achat-reel.md section 8; criteria AN2, AN6 to AN10): the status read from `entitlements/{uid}`, the store's own prices for the renewal
 * sentence (never the document's), "Actualiser mon abonnement", and the line of the Settings (always there when the purchase is on, only for an active Miff+ otherwise).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SubscriptionViewModelTest {
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

        init {
            gateway.catalog = CatalogResult.Loaded(miffPlusStoreOffers(trial = true))
        }
    }

    private fun store(
        state: String,
        plan: String = "yearly",
        active: Boolean = true,
        end: Long = NOW + 1_000,
    ) = EntitlementDocument(active, end, "google_play", state = state, plan = plan, trial = state == "trial")

    // ---- the screen ------------------------------------------------------------------------------------------------------------------------

    @Test
    fun itReadsTheServerOnce_andShowsTheStatus() =
        runTest(dispatcher) {
            val w = World(this)
            w.store.document = store("active")
            val model = SubscriptionViewModel(w.repository, w.coordinator)
            assertTrue(model.uiState.value.loading)
            runCurrent()
            assertFalse(model.uiState.value.loading)
            assertEquals(StoreState.ACTIVE, (model.uiState.value.status as SubscriptionStatus.Store).state)
            assertEquals(1, w.store.reads)
        }

    @Test
    fun theTrialPrice_isTheStoresAnnualPrice_neverTheDocuments() =
        runTest(dispatcher) {
            val trial = World(this)
            trial.store.document = store("trial")
            val model = SubscriptionViewModel(trial.repository, trial.coordinator)
            runCurrent()
            assertEquals("59,88 €", model.uiState.value.renewalPrice())
        }

    @Test
    fun aRunningSubscription_showsNoRenewalPrice_theBilledOneIsNotKnown_andNeverAsksTheStore() =
        runTest(dispatcher) {
            // Renewed at the SAME price (decision of the PM): an old subscriber may no longer pay today's catalog price, and the server gives no billed amount.
            listOf("yearly", "monthly").forEach { plan ->
                val w = World(this)
                w.store.document = store("active", plan = plan)
                val model = SubscriptionViewModel(w.repository, w.coordinator)
                runCurrent()
                assertTrue(model.uiState.value.status is SubscriptionStatus.Store)
                assertNull(plan, model.uiState.value.renewalPrice())
                assertNull(plan, model.uiState.value.prices)
                assertEquals(plan, 0, w.gateway.catalogCalls)
            }
        }

    @Test
    fun withTheStoreOutOfReach_theSentenceHasNoPrice() =
        runTest(dispatcher) {
            val w = World(this)
            w.store.document = store("trial")
            w.gateway.catalog = CatalogResult.Error
            val model = SubscriptionViewModel(w.repository, w.coordinator)
            runCurrent()
            assertNull(model.uiState.value.renewalPrice())
            assertTrue(model.uiState.value.status is SubscriptionStatus.Store)
            // A store that is too slow does not hold the screen for ever.
            val slow = World(this)
            slow.store.document = store("trial")
            slow.gateway.catalogDelayMillis = 60_000
            val slowModel = SubscriptionViewModel(slow.repository, slow.coordinator)
            runCurrent()
            advanceTimeBy(SubscriptionViewModel.PRICES_TIMEOUT_MILLIS + 1)
            runCurrent()
            assertFalse(slowModel.uiState.value.loading)
            assertNull(slowModel.uiState.value.renewalPrice())
        }

    @Test
    fun pricesAreOnlyAskedWhenASentenceNeedsThem() =
        runTest(dispatcher) {
            val w = World(this)
            w.store.document = store("canceled")
            val model = SubscriptionViewModel(w.repository, w.coordinator)
            runCurrent()
            assertNull(model.uiState.value.prices)
            val manual = World(this)
            manual.store.document = EntitlementDocument(true, NOW + 5, "manual")
            val manualModel = SubscriptionViewModel(manual.repository, manual.coordinator)
            runCurrent()
            assertEquals(SubscriptionStatus.Manual(NOW + 5), manualModel.uiState.value.status)
            assertNull(manualModel.uiState.value.prices)
        }

    @Test
    fun anAccountOnTheFreePlan_isFree_notAnError() =
        runTest(dispatcher) {
            val w = World(this)
            val model = SubscriptionViewModel(w.repository, w.coordinator)
            runCurrent()
            assertEquals(SubscriptionStatus.Free, model.uiState.value.status)
        }

    @Test
    fun aServerThatCannotBeRead_isNotTakenForFree() =
        runTest(dispatcher) {
            val w = World(this)
            w.store.failure = IOException("offline")
            val model = SubscriptionViewModel(w.repository, w.coordinator)
            runCurrent()
            assertFalse(model.uiState.value.loading)
            assertNull(model.uiState.value.status)
            // "Réessayer".
            w.store.failure = null
            w.store.document = store("active")
            model.load()
            runCurrent()
            assertTrue(model.uiState.value.status is SubscriptionStatus.Store)
        }

    @Test
    fun theOldDocumentOfTheTeam_isShownAsBefore_criterionAN7() =
        runTest(dispatcher) {
            val w = World(this)
            w.store.document = EntitlementDocument(true, NOW + 9, "manual")
            val model = SubscriptionViewModel(w.repository, premium = null)
            runCurrent()
            assertEquals(SubscriptionStatus.Manual(NOW + 9), model.uiState.value.status)
        }

    // ---- "Actualiser mon abonnement" ---------------------------------------------------------------------------------------------------------

    @Test
    fun refresh_findsAPurchaseOfANewPhone_andReadsTheServerAgain_criterionAN9() =
        runTest(dispatcher) {
            val w = World(this)
            val model = SubscriptionViewModel(w.repository, w.coordinator)
            runCurrent()
            assertEquals(SubscriptionStatus.Free, model.uiState.value.status)
            w.gateway.owned =
                OwnedResult.Loaded(listOf(OwnedPurchase("token-token-token-token", OwnedState.PURCHASED, true)))
            w.backend.default = SyncResult.Synced("active")
            // The server wrote the document meanwhile.
            w.store.document = store("active")
            model.refresh()
            assertNull(model.uiState.value.notice)
            runCurrent()
            assertFalse(model.uiState.value.refreshing)
            assertNull(model.uiState.value.notice)
            assertTrue(model.uiState.value.status is SubscriptionStatus.Store)
        }

    @Test
    fun refresh_findingNothing_orAPurchaseOfAnotherAccount_orAFailure_hasACalmSentence() =
        runTest(dispatcher) {
            val none = World(this)
            val m1 = SubscriptionViewModel(none.repository, none.coordinator)
            runCurrent()
            m1.refresh()
            runCurrent()
            assertEquals(SubscriptionNotice.NOTHING_FOUND, m1.uiState.value.notice)
            val other = World(this)
            other.gateway.owned =
                OwnedResult.Loaded(listOf(OwnedPurchase("token-token-token-token", OwnedState.PURCHASED, false)))
            other.backend.default = SyncResult.AlreadyLinked
            val m2 = SubscriptionViewModel(other.repository, other.coordinator)
            runCurrent()
            m2.refresh()
            runCurrent()
            assertEquals(SubscriptionNotice.OTHER_ACCOUNT, m2.uiState.value.notice)
            val down = World(this)
            down.gateway.owned = OwnedResult.Error
            val m3 = SubscriptionViewModel(down.repository, down.coordinator)
            runCurrent()
            m3.refresh()
            runCurrent()
            assertEquals(SubscriptionNotice.REFRESH_FAILED, m3.uiState.value.notice)
        }

    @Test
    fun refresh_doesNothing_whenThePurchaseIsOff() =
        runTest(dispatcher) {
            val w = World(this)
            val model = SubscriptionViewModel(w.repository, premium = null)
            runCurrent()
            model.refresh()
            runCurrent()
            assertEquals(0, w.gateway.ownedCalls)
        }

    @Test
    fun comingBackFromTheOffer_theServerIsReadAgain_butNotAtTheFirstDisplay() =
        runTest(dispatcher) {
            val w = World(this)
            val model = SubscriptionViewModel(w.repository, w.coordinator)
            model.onReturned()
            runCurrent()
            assertEquals(1, w.store.reads)
            w.store.document = store("active")
            model.onReturned()
            runCurrent()
            assertEquals(2, w.store.reads)
            assertTrue(model.uiState.value.status is SubscriptionStatus.Store)
        }

    // ---- the line of the Settings ---------------------------------------------------------------------------------------------------------------

    @Test
    fun withThePurchaseOn_theLineIsThere_forEverybody_withoutAnyRead() =
        runTest(dispatcher) {
            val w = World(this)
            val row = SubscriptionRowViewModel(w.repository, purchaseOn = true)
            assertTrue(row.visible.value)
            runCurrent()
            assertEquals(0, w.store.reads)
        }

    @Test
    fun withThePurchaseOff_theLineExistsOnlyForAnActiveMiffPlus_afterOneRead() =
        runTest(dispatcher) {
            val active = World(this)
            active.store.document = EntitlementDocument(true, NOW + 1, "manual")
            val row = SubscriptionRowViewModel(active.repository, purchaseOn = false)
            assertFalse(row.visible.value)
            runCurrent()
            assertTrue(row.visible.value)
            assertEquals(1, active.store.reads)
            val free = World(this)
            val freeRow = SubscriptionRowViewModel(free.repository, purchaseOn = false)
            runCurrent()
            assertFalse(freeRow.visible.value)
            val expired = World(this)
            expired.store.document = EntitlementDocument(false, NOW - 1, "google_play", state = "expired")
            val expiredRow = SubscriptionRowViewModel(expired.repository, purchaseOn = false)
            runCurrent()
            assertFalse(expiredRow.visible.value)
            val offline = World(this)
            offline.store.failure = IOException("offline")
            val offlineRow = SubscriptionRowViewModel(offline.repository, purchaseOn = false)
            runCurrent()
            assertFalse(offlineRow.visible.value)
        }

    private companion object {
        const val NOW = 10_000L
    }
}
