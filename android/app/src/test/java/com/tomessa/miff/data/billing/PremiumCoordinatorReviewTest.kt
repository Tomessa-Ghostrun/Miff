package com.tomessa.miff.data.billing

import android.app.Activity
import com.tomessa.miff.data.subscription.SubscriptionRepository
import com.tomessa.miff.domain.billing.PlanChoice
import com.tomessa.miff.domain.billing.PurchaseOutcome
import com.tomessa.miff.domain.billing.RestoreOutcome
import com.tomessa.miff.domain.paywall.MonotonicClock
import com.tomessa.miff.domain.paywall.PaywallPrice
import com.tomessa.miff.domain.subscription.EntitlementDocument
import com.tomessa.miff.testsupport.FakeBillingBackend
import com.tomessa.miff.testsupport.FakeBillingGateway
import com.tomessa.miff.testsupport.FakeEntitlementStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Points of the independent review of SRV-8A: a subscription on hold, tokens refused for good, and the minute of the server that starts only when it answered. */
@OptIn(ExperimentalCoroutinesApi::class)
class PremiumCoordinatorReviewTest {
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
    }

    private val choice = PlanChoice("offer-token", PaywallPrice("59,88 €", 59_880_000L, "EUR"))

    private fun purchased() = OwnedPurchase(TOKEN, OwnedState.PURCHASED, acknowledged = false)

    private fun onHold() =
        EntitlementDocument(
            active = false,
            expiresAtMillis = NOW - 5,
            source = "google_play",
            state = "on_hold",
            plan = "monthly",
        )

    private fun TestScope.purchase(w: World): PurchaseSession.Finished {
        w.coordinator.startPurchase(w.activity, choice)
        advanceTimeBy(300_000)
        runCurrent()
        return w.coordinator.session.value as PurchaseSession.Finished
    }

    private fun TestScope.restore(w: World): PurchaseSession.Finished {
        w.coordinator.startRestore()
        advanceTimeBy(300_000)
        runCurrent()
        return w.coordinator.session.value as PurchaseSession.Finished
    }

    @Test
    fun alreadyOwned_withNothingListed_andASubscriptionOnHold_saysOnHold_notAFailure() =
        runTest {
            val w = World(this)
            w.gateway.launchResult = LaunchResult.ALREADY_OWNED
            w.store.document = onHold()
            assertEquals(PurchaseOutcome.ON_HOLD, purchase(w).outcome)
            assertTrue(w.backend.syncs.isEmpty())
        }

    @Test
    fun alreadyOwned_withNothingListed_andNothingOnHold_isStillAFailure() =
        runTest {
            val w = World(this)
            w.gateway.launchResult = LaunchResult.ALREADY_OWNED
            assertEquals(PurchaseOutcome.FAILED, purchase(w).outcome)
        }

    @Test
    fun theServerSayingOnHold_isOnHold_forAPurchaseAndForARestoration() =
        runTest {
            val bought = World(this)
            bought.backend.default = SyncResult.Synced("on_hold")
            bought.gateway.afterLaunch = { bought.gateway.shared.tryEmit(PurchaseEvent.Updated(listOf(purchased()))) }
            assertEquals(PurchaseOutcome.ON_HOLD, purchase(bought).outcome)
            val restored = World(this)
            restored.gateway.owned = OwnedResult.Loaded(listOf(purchased()))
            restored.backend.default = SyncResult.Synced("on_hold")
            assertEquals(RestoreOutcome.ON_HOLD, restore(restored).restore)
            // Nothing in the store but the server knows a subscription on hold (the store does not list it).
            val unlisted = World(this)
            unlisted.store.document = onHold()
            assertEquals(RestoreOutcome.ON_HOLD, restore(unlisted).restore)
        }

    @Test
    fun aPurchaseRefusedForGood_isNotSentAgainBySilentPaths() =
        runTest {
            val w = World(this)
            w.gateway.owned = OwnedResult.Loaded(listOf(purchased()))
            w.backend.default = SyncResult.Rejected
            w.coordinator.reconcile()
            assertEquals(1, w.backend.syncs.size)
            advanceTimeBy(PremiumCoordinator.RECONCILE_SPACING_MILLIS + PremiumCoordinator.SYNC_SPACING_MILLIS)
            w.coordinator.reconcile()
            assertEquals(1, w.backend.syncs.size)
        }

    @Test
    fun aCallThatNeverReachedTheServer_doesNotUseTheMinute() =
        runTest {
            val w = World(this)
            w.gateway.owned = OwnedResult.Loaded(listOf(purchased()))
            w.backend.default = SyncResult.Unavailable
            w.coordinator.startRestore()
            runCurrent()
            assertEquals(1, w.backend.syncs.size)
            // No network: the next try is not made to wait a minute for nothing.
            w.backend.default = SyncResult.Synced("active")
            w.coordinator.startRestore()
            runCurrent()
            assertEquals(2, w.backend.syncs.size)
            assertEquals(RestoreOutcome.FOUND, (w.coordinator.session.value as PurchaseSession.Finished).restore)
        }

    private companion object {
        const val NOW = 10_000L
        const val TOKEN = "purchase-token-aaaaaaaaaaaaaaaaaaaa"
    }
}
