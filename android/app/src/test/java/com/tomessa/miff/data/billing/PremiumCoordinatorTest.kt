package com.tomessa.miff.data.billing

import android.app.Activity
import com.tomessa.miff.data.subscription.SubscriptionRepository
import com.tomessa.miff.domain.billing.OffersResult
import com.tomessa.miff.domain.billing.PhaseRecurrence
import com.tomessa.miff.domain.billing.PlanChoice
import com.tomessa.miff.domain.billing.PurchaseOutcome
import com.tomessa.miff.domain.billing.RestoreOutcome
import com.tomessa.miff.domain.billing.StoreOffer
import com.tomessa.miff.domain.billing.StorePhase
import com.tomessa.miff.domain.paywall.MonotonicClock
import com.tomessa.miff.domain.paywall.PaywallPrice
import com.tomessa.miff.domain.subscription.EntitlementDocument
import com.tomessa.miff.testsupport.FakeBillingBackend
import com.tomessa.miff.testsupport.FakeBillingGateway
import com.tomessa.miff.testsupport.FakeEntitlementStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The purchase of Miff+ (ticket premium-achat-reel.md sections 5, 6, 8; criteria AN2, AN5, AN9, AN11, AN12): the states of a purchase (pending, cancelled by the person, network
 * error, already owned, not available, several accounts), the restoration, the silent look at the opening of the app, and the spacing of the calls to the server (one check per
 * minute and per account). The store, the server and the clock are doubles: no Play Store, no network, no real time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PremiumCoordinatorTest {
    // ---- doubles (the store, the server and the rights are in testsupport/BillingFakes.kt) -------------------------------------------------------

    private class World(
        scope: TestScope,
    ) {
        val clock = MonotonicClock { scope.testScheduler.currentTime }
        val gateway = FakeBillingGateway()
        val backend = FakeBillingBackend { scope.testScheduler.currentTime }
        val store = FakeEntitlementStore()
        var uid: String? = "uid-1"
        val repository =
            SubscriptionRepository(store, currentUid = { uid }, nowMillis = { NOW }, timeoutMillis = 8_000L)
        val coordinator =
            PremiumCoordinator(
                gateway,
                backend,
                repository,
                currentUid = { uid },
                scope = scope.backgroundScope,
                clock = clock,
            )
        val activity = Activity()
    }

    private fun world(scope: TestScope) = World(scope)

    private fun purchased(
        token: String = TOKEN,
        acknowledged: Boolean = false,
    ) = OwnedPurchase(token, OwnedState.PURCHASED, acknowledged)

    private fun pending(token: String = TOKEN) = OwnedPurchase(token, OwnedState.PENDING, acknowledged = false)

    private val choice = PlanChoice("offer-token", PaywallPrice("59,88 €", 59_880_000L, "EUR"))

    private fun active(
        source: String = "google_play",
        end: Long = NOW + 1000,
    ) = EntitlementDocument(active = true, expiresAtMillis = end, source = source, state = "active", plan = "yearly")

    /** Starts a purchase and gives its final state. */
    private fun TestScope.finishedAfterPurchase(w: World): PurchaseSession.Finished {
        w.coordinator.startPurchase(w.activity, choice)
        advanceTimeBy(300_000)
        runCurrent()
        return w.coordinator.session.value as PurchaseSession.Finished
    }

    private fun TestScope.finishedAfterRestore(w: World): PurchaseSession.Finished {
        w.coordinator.startRestore()
        advanceTimeBy(300_000)
        runCurrent()
        return w.coordinator.session.value as PurchaseSession.Finished
    }

    // ---- a purchase -------------------------------------------------------------------------------------------------------------------------

    @Test
    fun aPurchase_givesTheStoreOnlyTheRandomIdOfTheServer_andSendsTheTokenToTheServer() =
        runTest {
            val w = world(this)
            w.gateway.afterLaunch = { w.gateway.shared.tryEmit(PurchaseEvent.Updated(listOf(purchased()))) }
            val done = finishedAfterPurchase(w)
            assertEquals(PurchaseOutcome.GRANTED, done.outcome)
            assertNull(done.restore)
            // The identifier given to the store is the one the server made: nothing else (no uid, no e-mail).
            assertEquals(listOf("offer-token" to PURCHASE_ID), w.gateway.launches)
            assertEquals(listOf(TOKEN), w.backend.syncs.map { it.first })
            assertEquals(1, w.backend.prepares)
        }

    @Test
    fun theSessionIsWorkingWhileItRuns_thenFinished() =
        runTest {
            val w = world(this)
            assertEquals(PurchaseSession.Idle, w.coordinator.session.value)
            w.coordinator.startPurchase(w.activity, choice)
            assertEquals(PurchaseSession.Working(restoring = false), w.coordinator.session.value)
            // The sheet of the store is open (the coordinator waits for its answer): the answer comes.
            runCurrent()
            assertEquals(PurchaseSession.Working(restoring = false), w.coordinator.session.value)
            w.gateway.shared.tryEmit(PurchaseEvent.Canceled)
            runCurrent()
            assertTrue(w.coordinator.session.value is PurchaseSession.Finished)
        }

    @Test
    fun aPaymentThatIsPending_grantsNothing_andIsNotSentToTheServer_criterionAN5() =
        runTest {
            val w = world(this)
            w.gateway.afterLaunch = { w.gateway.shared.tryEmit(PurchaseEvent.Updated(listOf(pending()))) }
            assertEquals(PurchaseOutcome.PENDING, finishedAfterPurchase(w).outcome)
            assertTrue(w.backend.syncs.isEmpty())
        }

    @Test
    fun aPersonWhoClosesTheSheet_getsNoMessageAndNothingIsSent() =
        runTest {
            val w = world(this)
            w.gateway.afterLaunch = { w.gateway.shared.tryEmit(PurchaseEvent.Canceled) }
            assertEquals(PurchaseOutcome.CANCELED, finishedAfterPurchase(w).outcome)
            assertTrue(w.backend.syncs.isEmpty())
        }

    @Test
    fun theEventsOfTheStore_mapToCalmOutcomes() =
        runTest {
            val cases =
                listOf(
                    PurchaseEvent.Unavailable to PurchaseOutcome.UNAVAILABLE,
                    PurchaseEvent.Network to PurchaseOutcome.NETWORK,
                    PurchaseEvent.Failed to PurchaseOutcome.FAILED,
                    PurchaseEvent.Updated(emptyList()) to PurchaseOutcome.FAILED,
                )
            cases.forEach { (event, expected) ->
                val w = world(this)
                w.gateway.afterLaunch = { w.gateway.shared.tryEmit(event) }
                assertEquals(event.toString(), expected, finishedAfterPurchase(w).outcome)
            }
        }

    @Test
    fun withoutConnectionToTheServer_noPurchaseIsStarted_theStoreIsNeverOpened_criterionAN12() =
        runTest {
            val w = world(this)
            w.backend.prepare = PrepareResult.Unavailable
            assertEquals(PurchaseOutcome.NETWORK, finishedAfterPurchase(w).outcome)
            assertTrue(w.gateway.launches.isEmpty())
            val refused = world(this)
            refused.backend.prepare = PrepareResult.Refused
            assertEquals(PurchaseOutcome.FAILED, finishedAfterPurchase(refused).outcome)
            assertTrue(refused.gateway.launches.isEmpty())
        }

    @Test
    fun signedOut_nothingIsStarted() =
        runTest {
            val w = world(this)
            w.uid = null
            assertEquals(PurchaseOutcome.FAILED, finishedAfterPurchase(w).outcome)
            assertEquals(0, w.backend.prepares)
        }

    @Test
    fun theStoreRefusingToOpenTheSheet_isAnOutcome_notACrash() =
        runTest {
            mapOf(
                LaunchResult.UNAVAILABLE to PurchaseOutcome.UNAVAILABLE,
                LaunchResult.NETWORK to PurchaseOutcome.NETWORK,
                LaunchResult.FAILED to PurchaseOutcome.FAILED,
            ).forEach { (launch, expected) ->
                val w = world(this)
                w.gateway.launchResult = launch
                assertEquals(launch.toString(), expected, finishedAfterPurchase(w).outcome)
            }
        }

    @Test
    fun aSubscriptionAlreadyOwned_isCheckedByTheServer_insteadOfBoughtTwice() =
        runTest {
            val w = world(this)
            w.gateway.launchResult = LaunchResult.ALREADY_OWNED
            w.gateway.owned = OwnedResult.Loaded(listOf(purchased(acknowledged = true)))
            assertEquals(PurchaseOutcome.GRANTED, finishedAfterPurchase(w).outcome)
            assertEquals(listOf(TOKEN), w.backend.syncs.map { it.first })
            // Same through the event of the sheet.
            val viaEvent = world(this)
            viaEvent.gateway.owned = OwnedResult.Loaded(listOf(purchased()))
            viaEvent.gateway.afterLaunch = { viaEvent.gateway.shared.tryEmit(PurchaseEvent.AlreadyOwned) }
            assertEquals(PurchaseOutcome.GRANTED, finishedAfterPurchase(viaEvent).outcome)
        }

    @Test
    fun alreadyOwned_butTheListCannotBeRead_isANetworkOutcome() =
        runTest {
            val w = world(this)
            w.gateway.launchResult = LaunchResult.ALREADY_OWNED
            w.gateway.owned = OwnedResult.Error
            assertEquals(PurchaseOutcome.NETWORK, finishedAfterPurchase(w).outcome)
        }

    // ---- what the server answers --------------------------------------------------------------------------------------------------------------

    @Test
    fun theAnswersOfTheServer_mapToOutcomes() =
        runTest {
            val cases =
                listOf(
                    SyncResult.Synced("trial") to PurchaseOutcome.GRANTED,
                    SyncResult.Synced("active") to PurchaseOutcome.GRANTED,
                    SyncResult.Synced("grace") to PurchaseOutcome.GRANTED,
                    SyncResult.Synced("pending") to PurchaseOutcome.PENDING,
                    SyncResult.Synced("revoked") to PurchaseOutcome.FAILED,
                    SyncResult.Synced("expired") to PurchaseOutcome.FAILED,
                    SyncResult.Synced(null) to PurchaseOutcome.FAILED,
                    SyncResult.AlreadyLinked to PurchaseOutcome.OTHER_ACCOUNT,
                    SyncResult.Rejected to PurchaseOutcome.FAILED,
                    SyncResult.Unavailable to PurchaseOutcome.NETWORK,
                )
            cases.forEach { (answer, expected) ->
                val w = world(this)
                w.backend.default = answer
                w.gateway.afterLaunch = { w.gateway.shared.tryEmit(PurchaseEvent.Updated(listOf(purchased()))) }
                assertEquals(answer.toString(), expected, finishedAfterPurchase(w).outcome)
            }
        }

    @Test
    fun tooSoon_isRetriedOnceAfterTheDelay_thePersonWaitsForIt() =
        runTest {
            val w = world(this)
            w.backend.answers.add(SyncResult.RateLimited)
            w.backend.default = SyncResult.Synced("trial")
            w.gateway.afterLaunch = { w.gateway.shared.tryEmit(PurchaseEvent.Updated(listOf(purchased()))) }
            assertEquals(PurchaseOutcome.GRANTED, finishedAfterPurchase(w).outcome)
            assertEquals(2, w.backend.syncs.size)
            assertTrue(w.backend.syncs[1].second - w.backend.syncs[0].second >= PremiumCoordinator.SYNC_SPACING_MILLIS)
        }

    @Test
    fun tooSoonAgain_isANetworkOutcome_theNextOpeningChecksAgain() =
        runTest {
            val w = world(this)
            w.backend.default = SyncResult.RateLimited
            w.gateway.afterLaunch = { w.gateway.shared.tryEmit(PurchaseEvent.Updated(listOf(purchased()))) }
            assertEquals(PurchaseOutcome.NETWORK, finishedAfterPurchase(w).outcome)
            assertEquals(2, w.backend.syncs.size)
        }

    @Test
    fun twoChecksAreNeverCloserThanTheServerAllows() =
        runTest {
            val w = world(this)
            w.gateway.owned = OwnedResult.Loaded(listOf(purchased()))
            w.backend.default = SyncResult.Synced("active")
            w.coordinator.startRestore()
            runCurrent()
            assertEquals(RestoreOutcome.FOUND, (w.coordinator.session.value as PurchaseSession.Finished).restore)
            // A second one right away: it waits for its turn instead of hitting the limit of the server.
            w.coordinator.startRestore()
            runCurrent()
            assertEquals(1, w.backend.syncs.size)
            advanceTimeBy(PremiumCoordinator.SYNC_SPACING_MILLIS)
            runCurrent()
            val times = w.backend.syncs.map { it.second }
            assertEquals(2, times.size)
            assertTrue(times[1] - times[0] >= PremiumCoordinator.SYNC_SPACING_MILLIS)
        }

    @Test
    fun aSecondPurchaseWhileOneRuns_isIgnored() =
        runTest {
            val w = world(this)
            w.coordinator.startPurchase(w.activity, choice)
            w.coordinator.startPurchase(w.activity, choice)
            runCurrent()
            assertEquals(1, w.gateway.launches.size)
            assertEquals(1, w.backend.prepares)
            w.coordinator.startRestore()
            runCurrent()
            assertEquals(0, w.gateway.ownedCalls)
        }

    // ---- restoration -------------------------------------------------------------------------------------------------------------------------

    @Test
    fun restore_findsAPurchaseOfANewPhone_andHasItChecked_criterionAN9() =
        runTest {
            val w = world(this)
            w.gateway.owned = OwnedResult.Loaded(listOf(purchased(acknowledged = true)))
            w.backend.default = SyncResult.Synced("active")
            val done = finishedAfterRestore(w)
            assertEquals(RestoreOutcome.FOUND, done.restore)
            assertNull(done.outcome)
            assertEquals(listOf(TOKEN), w.backend.syncs.map { it.first })
        }

    @Test
    fun restore_ofASubscriptionThatIsOver_isNotFound() =
        runTest {
            val w = world(this)
            w.gateway.owned = OwnedResult.Loaded(listOf(purchased()))
            w.backend.default = SyncResult.Synced("expired")
            assertEquals(RestoreOutcome.NONE_FOUND, finishedAfterRestore(w).restore)
        }

    @Test
    fun restore_ofAPurchaseOfAnotherMiffAccount_isRefusedCalmly() =
        runTest {
            val w = world(this)
            w.gateway.owned = OwnedResult.Loaded(listOf(purchased()))
            w.backend.default = SyncResult.AlreadyLinked
            assertEquals(RestoreOutcome.OTHER_ACCOUNT, finishedAfterRestore(w).restore)
        }

    @Test
    fun restore_withNothingInTheStore_asksTheServerWhatItKnows() =
        runTest {
            val none = world(this)
            assertEquals(RestoreOutcome.NONE_FOUND, finishedAfterRestore(none).restore)
            assertTrue(none.backend.syncs.isEmpty())
            // Bought on another device or in another store, or on hold (the list omits it): the server may still say Miff+.
            val known = world(this)
            known.store.document = active(source = "app_store")
            assertEquals(RestoreOutcome.FOUND, finishedAfterRestore(known).restore)
            val unreadable = world(this)
            unreadable.store.failure = java.io.IOException("offline")
            assertEquals(RestoreOutcome.NETWORK, finishedAfterRestore(unreadable).restore)
        }

    @Test
    fun restore_withAPendingPaymentOnly_saysItIsPending() =
        runTest {
            val w = world(this)
            w.gateway.owned = OwnedResult.Loaded(listOf(pending()))
            assertEquals(RestoreOutcome.PENDING, finishedAfterRestore(w).restore)
            assertTrue(w.backend.syncs.isEmpty())
        }

    @Test
    fun restore_whenTheStoreCannotBeRead_isANetworkOutcome() =
        runTest {
            val w = world(this)
            w.gateway.owned = OwnedResult.Error
            assertEquals(RestoreOutcome.NETWORK, finishedAfterRestore(w).restore)
            val down = world(this)
            down.gateway.owned = OwnedResult.Loaded(listOf(purchased()))
            down.backend.default = SyncResult.Unavailable
            assertEquals(RestoreOutcome.NETWORK, finishedAfterRestore(down).restore)
        }

    // ---- the silent look at the opening of the app ---------------------------------------------------------------------------------------------

    @Test
    fun atTheOpening_aPurchaseTheServerDoesNotKnow_isChecked_silently() =
        runTest {
            val w = world(this)
            w.gateway.owned = OwnedResult.Loaded(listOf(purchased()))
            w.store.document = null
            w.coordinator.reconcile()
            assertEquals(listOf(TOKEN), w.backend.syncs.map { it.first })
            // Nothing is shown: no work in progress, no result.
            assertEquals(PurchaseSession.Idle, w.coordinator.session.value)
        }

    @Test
    fun atTheOpening_nothingIsSent_whenThereIsNothingToDo() =
        runTest {
            // No purchase in the store.
            val empty = world(this)
            empty.coordinator.reconcile()
            assertTrue(empty.backend.syncs.isEmpty())
            // A purchase that is acknowledged, and the access is on: the server already knows it.
            val known = world(this)
            known.gateway.owned = OwnedResult.Loaded(listOf(purchased(acknowledged = true)))
            known.store.document = active()
            known.coordinator.reconcile()
            assertTrue(known.backend.syncs.isEmpty())
            // A payment still pending: nothing is granted, nothing is sent.
            val waiting = world(this)
            waiting.gateway.owned = OwnedResult.Loaded(listOf(pending()))
            waiting.coordinator.reconcile()
            assertTrue(waiting.backend.syncs.isEmpty())
        }

    @Test
    fun atTheOpening_aPurchaseNotAcknowledgedYet_isSent_evenIfTheAccessIsOn() =
        runTest {
            val w = world(this)
            w.gateway.owned = OwnedResult.Loaded(listOf(purchased(acknowledged = false)))
            w.store.document = active()
            w.coordinator.reconcile()
            assertEquals(1, w.backend.syncs.size)
        }

    @Test
    fun atTheOpening_anAccessThatIsOff_isSentAgain() =
        runTest {
            val w = world(this)
            w.gateway.owned = OwnedResult.Loaded(listOf(purchased(acknowledged = true)))
            w.store.document =
                EntitlementDocument(
                    active = false,
                    expiresAtMillis = NOW - 5,
                    source = "google_play",
                    state = "expired",
                )
            w.coordinator.reconcile()
            assertEquals(1, w.backend.syncs.size)
        }

    @Test
    fun atTheOpening_withTheServerUnreadable_orSignedOut_nothingIsSent() =
        runTest {
            val unreadable = world(this)
            unreadable.gateway.owned = OwnedResult.Loaded(listOf(purchased()))
            unreadable.store.failure = java.io.IOException("offline")
            unreadable.coordinator.reconcile()
            assertTrue(unreadable.backend.syncs.isEmpty())
            val signedOut = world(this)
            signedOut.uid = null
            signedOut.gateway.owned = OwnedResult.Loaded(listOf(purchased()))
            signedOut.coordinator.reconcile()
            assertTrue(signedOut.backend.syncs.isEmpty())
            assertEquals(0, signedOut.gateway.ownedCalls)
        }

    @Test
    fun theOpeningLooksAtMostOnceEveryFiveMinutes_noPolling() =
        runTest {
            val w = world(this)
            w.gateway.owned = OwnedResult.Loaded(listOf(purchased()))
            w.coordinator.reconcile()
            w.coordinator.reconcile()
            w.coordinator.reconcile()
            assertEquals(1, w.gateway.ownedCalls)
            advanceTimeBy(PremiumCoordinator.RECONCILE_SPACING_MILLIS + 1)
            w.coordinator.reconcile()
            assertEquals(2, w.gateway.ownedCalls)
        }

    @Test
    fun aPurchaseUsedByAnotherMiffAccount_isNotSentAgainBySilentPaths_onlyByAnExplicitRestore() =
        runTest {
            val w = world(this)
            w.gateway.owned = OwnedResult.Loaded(listOf(purchased()))
            w.backend.default = SyncResult.AlreadyLinked
            w.coordinator.reconcile()
            assertEquals(1, w.backend.syncs.size)
            advanceTimeBy(PremiumCoordinator.RECONCILE_SPACING_MILLIS + PremiumCoordinator.SYNC_SPACING_MILLIS)
            w.coordinator.reconcile()
            assertEquals(1, w.backend.syncs.size)
            assertEquals(RestoreOutcome.OTHER_ACCOUNT, finishedAfterRestore(w).restore)
            assertEquals(2, w.backend.syncs.size)
        }

    @Test
    fun aSilentCheck_neverWaitsForItsTurn_itComesBackAtTheNextOpening() =
        runTest {
            val w = world(this)
            w.gateway.owned = OwnedResult.Loaded(listOf(purchased()))
            w.coordinator.reconcile()
            assertEquals(1, w.backend.syncs.size)
            // 10 seconds later, a purchase event arrives while the previous check is still inside the minute: nothing is sent, nothing waits.
            advanceTimeBy(10_000)
            w.gateway.shared.tryEmit(PurchaseEvent.Updated(listOf(purchased("another-token"))))
            runCurrent()
            assertEquals(1, w.backend.syncs.size)
        }

    @Test
    fun aPurchaseThatArrivesOnItsOwn_isCheckedSilently_aPendingOrCancelledOneIsNot() =
        runTest {
            val w = world(this)
            w.gateway.shared.tryEmit(PurchaseEvent.Updated(listOf(purchased())))
            runCurrent()
            assertEquals(listOf(TOKEN), w.backend.syncs.map { it.first })
            val quiet = world(this)
            quiet.gateway.shared.tryEmit(PurchaseEvent.Updated(listOf(pending())))
            quiet.gateway.shared.tryEmit(PurchaseEvent.Canceled)
            quiet.gateway.shared.tryEmit(PurchaseEvent.Network)
            runCurrent()
            assertTrue(quiet.backend.syncs.isEmpty())
        }

    // ---- the signal "Miff+ is on" (the screens that hold a copy of the plan read it again; regression of "Créer une Miff" after the trial) ----------------

    /** Collects what [PremiumCoordinator.accessGranted] says, from the very start. */
    private fun TestScope.grantsOf(w: World): MutableList<Unit> {
        val grants = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            w.coordinator.accessGranted.collect {
                grants +=
                    it
            }
        }
        return grants
    }

    @Test
    fun aPurchaseThatGrantsAccess_saysSo_once() =
        runTest {
            val w = world(this)
            val grants = grantsOf(w)
            w.gateway.afterLaunch = { w.gateway.shared.tryEmit(PurchaseEvent.Updated(listOf(purchased()))) }
            assertEquals(PurchaseOutcome.GRANTED, finishedAfterPurchase(w).outcome)
            assertEquals(1, grants.size)
        }

    @Test
    fun aPurchaseThatGrantsNothing_saysNothing() =
        runTest {
            val w = world(this)
            val grants = grantsOf(w)
            w.gateway.afterLaunch = { w.gateway.shared.tryEmit(PurchaseEvent.Canceled) }
            assertEquals(PurchaseOutcome.CANCELED, finishedAfterPurchase(w).outcome)
            val pendingOne = world(this)
            val pendingGrants = grantsOf(pendingOne)
            pendingOne.gateway.afterLaunch =
                { pendingOne.gateway.shared.tryEmit(PurchaseEvent.Updated(listOf(pending()))) }
            assertEquals(PurchaseOutcome.PENDING, finishedAfterPurchase(pendingOne).outcome)
            assertTrue(grants.isEmpty())
            assertTrue(pendingGrants.isEmpty())
        }

    @Test
    fun aRestorationThatFindsMiffPlus_saysSo_aRestorationThatFindsNothing_doesNot() =
        runTest {
            val found = world(this)
            val foundGrants = grantsOf(found)
            found.gateway.owned = OwnedResult.Loaded(listOf(purchased(acknowledged = true)))
            found.backend.default = SyncResult.Synced("active")
            assertEquals(RestoreOutcome.FOUND, finishedAfterRestore(found).restore)
            assertEquals(1, foundGrants.size)

            val none = world(this)
            val noneGrants = grantsOf(none)
            none.gateway.owned = OwnedResult.Loaded(emptyList())
            assertEquals(RestoreOutcome.NONE_FOUND, finishedAfterRestore(none).restore)
            assertTrue(noneGrants.isEmpty())
        }

    @Test
    fun aPurchaseFoundByTheSilentLook_saysSo() =
        runTest {
            val w = world(this)
            val grants = grantsOf(w)
            w.gateway.owned = OwnedResult.Loaded(listOf(purchased()))
            w.store.document = null
            w.coordinator.reconcile()
            assertEquals(1, grants.size)
        }

    @Test
    fun anOfferThatFindsTheAccountAlreadySubscribed_canSaySo() =
        runTest {
            val w = world(this)
            val grants = grantsOf(w)
            w.coordinator.noteAccessGranted()
            assertEquals(1, grants.size)
        }

    // ---- offers and privacy -------------------------------------------------------------------------------------------------------------------

    @Test
    fun theOffersAreTheStores_orNotAvailable_orAnError() =
        runTest {
            val w = world(this)
            w.gateway.catalog =
                CatalogResult.Loaded(listOf(offer("annuel", "P1Y", 59_880_000), offer("mensuel", "P1M", 6_990_000)))
            val loaded = w.coordinator.loadOffers() as OffersResult.Loaded
            assertEquals("59,88 €", loaded.offers.annual.price.formatted)
            w.gateway.catalog = CatalogResult.Loaded(emptyList())
            assertEquals(OffersResult.NotAvailable, w.coordinator.loadOffers())
            w.gateway.catalog = CatalogResult.NotAvailable
            assertEquals(OffersResult.NotAvailable, w.coordinator.loadOffers())
            w.gateway.catalog = CatalogResult.Error
            assertEquals(OffersResult.Error, w.coordinator.loadOffers())
        }

    @Test
    fun aPurchaseTokenIsNeverPrinted() {
        val text = purchased("secret-token-value").toString()
        assertFalse(text.contains("secret-token-value"))
        assertFalse(PrepareResult.Ready("11111111-2222-3333-4444-555555555555").toString().contains("11111111"))
    }

    private fun offer(
        plan: String,
        period: String,
        micros: Long,
    ) = StoreOffer(
        plan,
        null,
        "tok-$plan",
        listOf(
            StorePhase(
                micros,
                "EUR",
                if (plan ==
                    "annuel"
                ) {
                    "59,88 €"
                } else {
                    "6,99 €"
                },
                period,
                PhaseRecurrence.INFINITE,
            ),
        ),
    )

    private companion object {
        const val NOW = 10_000L
        const val TOKEN = "purchase-token-aaaaaaaaaaaaaaaaaaaa"
        const val PURCHASE_ID = FakeBillingBackend.PURCHASE_ID
    }
}
