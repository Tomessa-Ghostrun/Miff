package com.tomessa.miff.data.billing

import android.app.Activity
import com.tomessa.miff.data.subscription.SubscriptionStore
import com.tomessa.miff.domain.billing.PhaseRecurrence
import com.tomessa.miff.domain.billing.StoreOffer
import com.tomessa.miff.domain.billing.StorePhase
import com.tomessa.miff.domain.subscription.EntitlementDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.UUID

/*
 * DEBUG BUILD ONLY (src/debug): a FAKE store, a FAKE server and a FAKE entitlement, to try the whole interface of Miff+ without the app being in the Play Console. It is NOT in
 * a release build: the CI looks for the marker FAKEBILLINGPROBE in the bundle (check-release-bundle.sh, android.yml; ReleaseSigningGuardTest keeps the lists in agreement).
 * Nothing here writes anywhere (no Firestore, no network): it grants NOTHING on the real server, and the places and Miffs limits of the app do not change. The prices below
 * are test data of this fake only; the real app never writes a price (it reads Google Play).
 */
private const val FAKE_BILLING_MARKER = "FAKEBILLINGPROBE"

/** What the diagnostic card of the debug build can set (all in memory, lost when the process ends). */
object FakeBillingControl {
    enum class CatalogMode(
        val label: String,
    ) {
        TRIAL("offre avec essai (compte éligible)"),
        NO_TRIAL("offres sans essai (essai déjà utilisé)"),
        UNAVAILABLE("produit non disponible"),
        ERROR("erreur réseau"),
    }

    enum class PurchaseMode(
        val label: String,
    ) {
        SUCCESS("réussi"),
        PENDING("en attente de validation"),
        CANCELED("annulé par la personne"),
        NETWORK("erreur réseau du store"),
        ALREADY_OWNED("déjà possédé"),
        UNAVAILABLE("non disponible"),
        OTHER_ACCOUNT("déjà utilisé par un autre compte Miff"),
        SERVER_DOWN("serveur Miff injoignable avant l'achat"),
        SYNC_DOWN("payé, mais le serveur ne répond pas à la vérification"),
    }

    /** The state of `entitlements/{uid}` the fake serves. */
    enum class Doc(
        val label: String,
    ) {
        NONE("aucun document (formule gratuite)"),
        TRIAL("essai gratuit"),
        ACTIVE("actif (annuel)"),
        ACTIVE_MONTHLY("actif (mensuel)"),
        CANCELED("annulé, non expiré"),
        GRACE("période de grâce"),
        ON_HOLD("suspendu"),
        EXPIRED("expiré"),
        REVOKED("remboursé"),
        MANUAL("accordé à la main"),
        MANUAL_OLD("ancien document à la main (3 champs)"),
    }

    @Volatile var catalog: CatalogMode = CatalogMode.TRIAL

    @Volatile var purchase: PurchaseMode = PurchaseMode.SUCCESS

    @Volatile var doc: Doc = Doc.NONE

    /** The store holds a purchase for this account (to try the restoration: document absent, purchase present). */
    @Volatile var storeOwns: Boolean = false

    internal fun reset() {
        catalog = CatalogMode.TRIAL
        purchase = PurchaseMode.SUCCESS
        doc = Doc.NONE
        storeOwns = false
    }
}

object FakeBilling {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutableEvents = MutableSharedFlow<PurchaseEvent>(extraBufferCapacity = 16)

    // The offer the last purchase sheet was opened for.
    @Volatile private var chosenToken: String = TOKEN_ANNUAL

    fun stack(): BillingStack = BillingStack(FakeGateway(), FakeBackend(), FakeEntitlements())

    private fun phase(
        micros: Long,
        label: String,
        period: String,
        recurrence: PhaseRecurrence,
    ) = StorePhase(micros, "EUR", label, period, recurrence)

    private fun offers(trial: Boolean): List<StoreOffer> =
        buildList {
            add(
                StoreOffer(
                    "annuel",
                    null,
                    TOKEN_ANNUAL,
                    listOf(phase(59_880_000, "59,88 €", "P1Y", PhaseRecurrence.INFINITE)),
                ),
            )
            if (trial) {
                add(
                    StoreOffer(
                        "annuel",
                        "essai",
                        TOKEN_TRIAL,
                        listOf(
                            phase(0, "Gratuit", "P1W", PhaseRecurrence.FINITE),
                            phase(59_880_000, "59,88 €", "P1Y", PhaseRecurrence.INFINITE),
                        ),
                    ),
                )
            }
            add(
                StoreOffer(
                    "mensuel",
                    null,
                    TOKEN_MONTHLY,
                    listOf(phase(6_990_000, "6,99 €", "P1M", PhaseRecurrence.INFINITE)),
                ),
            )
        }

    private class FakeGateway : BillingGateway {
        override val events: Flow<PurchaseEvent> = mutableEvents.asSharedFlow()

        // The marker the CI looks for in a release bundle: referenced, so that it is in the dex of any build that contains the fake.
        override fun toString(): String = FAKE_BILLING_MARKER

        override suspend fun loadCatalog(): CatalogResult {
            delay(SLOW_MILLIS)
            return when (FakeBillingControl.catalog) {
                FakeBillingControl.CatalogMode.TRIAL -> CatalogResult.Loaded(offers(trial = true))
                FakeBillingControl.CatalogMode.NO_TRIAL -> CatalogResult.Loaded(offers(trial = false))
                FakeBillingControl.CatalogMode.UNAVAILABLE -> CatalogResult.NotAvailable
                FakeBillingControl.CatalogMode.ERROR -> CatalogResult.Error
            }
        }

        override suspend fun ownedPurchases(): OwnedResult =
            OwnedResult.Loaded(
                if (FakeBillingControl.storeOwns) {
                    listOf(
                        OwnedPurchase(
                            FAKE_TOKEN,
                            OwnedState.PURCHASED,
                            acknowledged =
                                FakeBillingControl.doc != FakeBillingControl.Doc.NONE,
                        ),
                    )
                } else {
                    emptyList()
                },
            )

        override suspend fun launch(
            activity: Activity,
            offerToken: String,
            purchaseId: String,
        ): LaunchResult {
            chosenToken = offerToken
            return when (FakeBillingControl.purchase) {
                FakeBillingControl.PurchaseMode.ALREADY_OWNED -> {
                    FakeBillingControl.storeOwns = true
                    LaunchResult.ALREADY_OWNED
                }

                FakeBillingControl.PurchaseMode.UNAVAILABLE -> {
                    LaunchResult.UNAVAILABLE
                }

                else -> {
                    scope.launch {
                        delay(SHEET_MILLIS)
                        mutableEvents.emit(eventOf(FakeBillingControl.purchase))
                    }
                    LaunchResult.STARTED
                }
            }
        }

        private fun eventOf(mode: FakeBillingControl.PurchaseMode): PurchaseEvent =
            when (mode) {
                FakeBillingControl.PurchaseMode.PENDING -> {
                    PurchaseEvent.Updated(listOf(OwnedPurchase(FAKE_TOKEN, OwnedState.PENDING, acknowledged = false)))
                }

                FakeBillingControl.PurchaseMode.CANCELED -> {
                    PurchaseEvent.Canceled
                }

                FakeBillingControl.PurchaseMode.NETWORK -> {
                    PurchaseEvent.Network
                }

                else -> {
                    FakeBillingControl.storeOwns = true
                    PurchaseEvent.Updated(listOf(OwnedPurchase(FAKE_TOKEN, OwnedState.PURCHASED, acknowledged = false)))
                }
            }
    }

    private class FakeBackend : PurchaseBackend {
        override suspend fun preparePurchaseId(): PrepareResult {
            delay(SLOW_MILLIS)
            return if (FakeBillingControl.purchase == FakeBillingControl.PurchaseMode.SERVER_DOWN) {
                PrepareResult.Unavailable
            } else {
                PrepareResult.Ready(UUID.randomUUID().toString())
            }
        }

        override suspend fun sync(purchaseToken: String): SyncResult {
            delay(SLOW_MILLIS)
            return when (FakeBillingControl.purchase) {
                FakeBillingControl.PurchaseMode.OTHER_ACCOUNT -> {
                    SyncResult.AlreadyLinked
                }

                FakeBillingControl.PurchaseMode.SYNC_DOWN -> {
                    SyncResult.Unavailable
                }

                else -> {
                    // A restoration (nothing chosen in this session) gives an active annual subscription; a purchase gives what its button asked for.
                    val doc =
                        when (chosenToken) {
                            TOKEN_TRIAL -> FakeBillingControl.Doc.TRIAL
                            TOKEN_MONTHLY -> FakeBillingControl.Doc.ACTIVE_MONTHLY
                            else -> FakeBillingControl.Doc.ACTIVE
                        }
                    FakeBillingControl.doc = doc
                    SyncResult.Synced(if (doc == FakeBillingControl.Doc.TRIAL) "trial" else "active")
                }
            }
        }

        override suspend fun purgeSubscriptionData(): Boolean = true
    }

    private class FakeEntitlements : SubscriptionStore {
        override suspend fun read(uid: String): EntitlementDocument? {
            val now = System.currentTimeMillis()
            val inAMonth = now + 30 * DAY_MILLIS
            val inAWeek = now + 7 * DAY_MILLIS
            val inAYear = now + 365 * DAY_MILLIS
            return when (FakeBillingControl.doc) {
                FakeBillingControl.Doc.NONE -> {
                    null
                }

                FakeBillingControl.Doc.TRIAL -> {
                    store("trial", "yearly", inAWeek, trial = true)
                }

                FakeBillingControl.Doc.ACTIVE -> {
                    store("active", "yearly", inAYear)
                }

                FakeBillingControl.Doc.ACTIVE_MONTHLY -> {
                    store("active", "monthly", inAMonth)
                }

                FakeBillingControl.Doc.CANCELED -> {
                    store("canceled", "yearly", inAMonth, willRenew = false)
                }

                FakeBillingControl.Doc.GRACE -> {
                    store("grace", "monthly", inAWeek)
                }

                FakeBillingControl.Doc.ON_HOLD -> {
                    store("on_hold", "monthly", now - DAY_MILLIS, active = false)
                }

                FakeBillingControl.Doc.EXPIRED -> {
                    store("expired", "yearly", now - DAY_MILLIS, active = false)
                }

                FakeBillingControl.Doc.REVOKED -> {
                    store("revoked", "yearly", now, active = false)
                }

                FakeBillingControl.Doc.MANUAL -> {
                    EntitlementDocument(
                        active = true,
                        expiresAtMillis = inAYear,
                        source = "manual",
                    )
                }

                FakeBillingControl.Doc.MANUAL_OLD -> {
                    EntitlementDocument(
                        active = true,
                        expiresAtMillis = inAMonth,
                        source = "manual",
                    )
                }
            }
        }

        private fun store(
            state: String,
            plan: String,
            end: Long,
            active: Boolean = true,
            trial: Boolean = false,
            willRenew: Boolean = true,
        ) = EntitlementDocument(
            active = active,
            expiresAtMillis = end,
            source = "google_play",
            state = state,
            plan = plan,
            trial = trial,
            willRenew = willRenew,
            test = true,
        )
    }

    private const val TOKEN_ANNUAL = "fake-offer-annual"
    private const val TOKEN_TRIAL = "fake-offer-trial"
    private const val TOKEN_MONTHLY = "fake-offer-monthly"
    private const val FAKE_TOKEN = "fake-purchase-token-not-a-real-one"
    private const val SLOW_MILLIS = 400L
    private const val SHEET_MILLIS = 900L
    private const val DAY_MILLIS = 24L * 60 * 60 * 1000
}
