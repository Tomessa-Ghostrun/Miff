#if BILLING_FAKE
import Foundation

#if !(DEBUG || INTERNAL)
// Garde-fou de COMPILATION : le faux magasin n'existe que dans les builds Debug et Internal (essais de l'équipe). Une configuration Release qui poserait `BILLING_FAKE` ne compile pas.
#error("BILLING_FAKE est réservé aux configurations Debug et Internal : jamais dans un build de publication.")
#endif

// BUILDS DE TEST SEULEMENT (Debug et Internal) : un FAUX magasin, un FAUX serveur et un FAUX droit, pour essayer toute l'interface de Miff+ sans produit dans App Store Connect.
// Il n'est PAS dans un build de publication : `ci/check-binaries.sh` cherche le marqueur FAKEBILLINGPROBE dans le binaire Release (et refuse de compiler plus haut). Rien ici n'écrit
// nulle part (ni Firestore, ni réseau) : il n'accorde RIEN sur le vrai serveur, et les limites de lieux et de Miffs de l'app ne changent pas. Les prix ci-dessous sont des
// données d'essai de ce faux seulement ; la vraie app n'écrit jamais un prix (elle lit StoreKit).

/// Ce que la carte « Achat Miff+ » du diagnostic fixe (tout en mémoire, perdu à la fin du processus).
enum FakeBillingControl {
    enum CatalogMode: CaseIterable {
        case trial
        case noTrial
        case unavailable
        case error

        var label: String {
            switch self {
            case .trial:
                return "offre avec essai (compte éligible)"
            case .noTrial:
                return "offres sans essai (essai déjà utilisé)"
            case .unavailable:
                return "produit non disponible"
            case .error:
                return "erreur réseau"
            }
        }
    }

    enum PurchaseMode: CaseIterable {
        case success
        case pending
        case cancelled
        case network
        case alreadyOwned
        case unavailable
        case otherAccount
        case serverDown
        case syncDown

        var label: String {
            switch self {
            case .success:
                return "réussi"
            case .pending:
                return "en attente de validation (Ask to Buy)"
            case .cancelled:
                return "annulé par la personne"
            case .network:
                return "erreur réseau du store"
            case .alreadyOwned:
                return "déjà possédé"
            case .unavailable:
                return "non disponible"
            case .otherAccount:
                return "déjà utilisé par un autre compte Miff"
            case .serverDown:
                return "serveur Miff injoignable avant l'achat"
            case .syncDown:
                return "payé, mais le serveur ne répond pas à la vérification"
            }
        }
    }

    /// L'état de `entitlements/{uid}` que le faux sert.
    enum Doc: CaseIterable {
        case noDocument
        case trial
        case active
        case activeMonthly
        case canceled
        case grace
        case onHold
        case expired
        case revoked
        case manual

        var label: String {
            switch self {
            case .noDocument:
                return "aucun document (formule gratuite)"
            case .trial:
                return "essai gratuit"
            case .active:
                return "actif (annuel)"
            case .activeMonthly:
                return "actif (mensuel)"
            case .canceled:
                return "annulé, non expiré"
            case .grace:
                return "période de grâce"
            case .onHold:
                return "suspendu"
            case .expired:
                return "expiré"
            case .revoked:
                return "remboursé"
            case .manual:
                return "accordé à la main"
            }
        }
    }

    static var catalog = CatalogMode.trial
    static var purchase = PurchaseMode.success
    static var doc = Doc.noDocument
    /// Le store possède un achat pour ce compte (pour essayer la restauration : document absent, achat présent).
    static var storeOwns = false

    static func reset() {
        catalog = .trial
        purchase = .success
        doc = .noDocument
        storeOwns = false
    }
}

/// Le marqueur que la CI cherche dans un build de publication : `FakeBillingGateway.description` le rend, donc il est référencé et présent dans tout binaire qui contient le faux.
let fakeBillingProbe = "FAKEBILLINGPROBE"

/// Le faux magasin.
@MainActor
final class FakeBillingGateway: BillingGateway, CustomStringConvertible {
    private static let fakeSigned = "fake-signed-transaction-not-a-real-one"
    private static let slowSeconds: Double = 0.4
    private static let sheetSeconds: Double = 0.9

    nonisolated var description: String {
        fakeBillingProbe
    }

    func loadCatalog() async -> CatalogResult {
        try? await Task.sleep(nanoseconds: UInt64(Self.slowSeconds * 1_000_000_000))
        switch FakeBillingControl.catalog {
        case .trial:
            return .loaded(Self.products(trial: true))
        case .noTrial:
            return .loaded(Self.products(trial: false))
        case .unavailable:
            return .notAvailable
        case .error:
            return .error
        }
    }

    private static func products(trial: Bool) -> [StoreProductInfo] {
        [
            StoreProductInfo(
                id: MiffPlusProducts.annualId, amount: Decimal(5988) / 100, currencyCode: "EUR",
                displayPrice: "59,88 €", monthlyDisplayPrice: "4,99 €", period: .oneYear,
                freeTrial: StorePeriod(unit: .week, value: 1), eligibleForTrial: trial
            ),
            StoreProductInfo(
                id: MiffPlusProducts.monthlyId, amount: Decimal(699) / 100, currencyCode: "EUR",
                displayPrice: "6,99 €", monthlyDisplayPrice: nil, period: .oneMonth,
                freeTrial: nil, eligibleForTrial: false
            )
        ]
    }

    func ownedTransactions() async -> OwnedResult {
        .loaded(FakeBillingControl.storeOwns ? [Self.transaction()] : [])
    }

    private static func transaction() -> OwnedTransaction {
        OwnedTransaction(id: 1, productId: MiffPlusProducts.annualId, signed: fakeSigned, finish: {})
    }

    func purchase(productId: String, purchaseId: String) async -> StorePurchaseResult {
        FakeBilling.lastProductId = productId
        switch FakeBillingControl.purchase {
        case .alreadyOwned:
            FakeBillingControl.storeOwns = true
            return .alreadyOwned
        case .unavailable:
            return .unavailable
        case .serverDown, .success, .otherAccount, .syncDown:
            try? await Task.sleep(nanoseconds: UInt64(Self.sheetSeconds * 1_000_000_000))
            FakeBillingControl.storeOwns = true
            return .purchased(Self.transaction())
        case .pending:
            try? await Task.sleep(nanoseconds: UInt64(Self.sheetSeconds * 1_000_000_000))
            return .pending
        case .cancelled:
            try? await Task.sleep(nanoseconds: UInt64(Self.sheetSeconds * 1_000_000_000))
            return .cancelled
        case .network:
            try? await Task.sleep(nanoseconds: UInt64(Self.sheetSeconds * 1_000_000_000))
            return .network
        }
    }

    func syncWithStore() async -> StoreSyncResult {
        try? await Task.sleep(nanoseconds: UInt64(Self.slowSeconds * 1_000_000_000))
        return .done
    }

    func listen(_ handler: @escaping @MainActor (OwnedTransaction) -> Void) {}

    func openManagement() async {}
}

/// Le faux serveur : il ne fait rien d'autre que changer le faux droit.
final class FakePurchaseBackend: PurchaseBackend {
    func preparePurchaseId() async -> PrepareResult {
        try? await Task.sleep(nanoseconds: 400_000_000)
        if FakeBillingControl.purchase == .serverDown {
            return .unavailable
        }
        return .ready(purchaseId: UUID().uuidString)
    }

    func sync(signedTransaction: String) async -> PurchaseSyncResult {
        try? await Task.sleep(nanoseconds: 400_000_000)
        switch FakeBillingControl.purchase {
        case .otherAccount:
            return .alreadyLinked
        case .syncDown:
            return .unavailable
        default:
            // Une restauration (rien choisi dans cette session) donne un annuel actif ; un achat donne ce que son bouton demandait.
            let doc: FakeBillingControl.Doc
            switch FakeBilling.lastProductId {
            case MiffPlusProducts.monthlyId:
                doc = .activeMonthly
            case MiffPlusProducts.annualId where FakeBillingControl.catalog == .trial:
                doc = .trial
            default:
                doc = .active
            }
            FakeBillingControl.doc = doc
            return .synced(state: doc == .trial ? "trial" : "active")
        }
    }

    func purgeSubscriptionData() async -> Bool {
        true
    }
}

/// Le faux droit : ce que `entitlements/{uid}` dirait, d'après la carte du diagnostic.
final class FakeSubscriptionStore: SubscriptionStore {
    private static let day: Int = 24 * 60 * 60 * 1000

    func read(uid: String) async throws -> EntitlementDocument? {
        let now = Int(Date().timeIntervalSince1970 * 1000)
        switch FakeBillingControl.doc {
        case .noDocument:
            return nil
        case .trial:
            return store("trial", plan: "yearly", end: now + 7 * Self.day, trial: true)
        case .active:
            return store("active", plan: "yearly", end: now + 365 * Self.day)
        case .activeMonthly:
            return store("active", plan: "monthly", end: now + 30 * Self.day)
        case .canceled:
            return store("canceled", plan: "yearly", end: now + 30 * Self.day, willRenew: false)
        case .grace:
            return store("grace", plan: "monthly", end: now + 7 * Self.day)
        case .onHold:
            return store("on_hold", plan: "monthly", end: now - Self.day, active: false)
        case .expired:
            return store("expired", plan: "yearly", end: now - Self.day, active: false)
        case .revoked:
            return store("revoked", plan: "yearly", end: now, active: false)
        case .manual:
            return EntitlementDocument(active: true, expiresAtMillis: now + 365 * Self.day, source: "manual")
        }
    }

    private func store(
        _ state: String,
        plan: String,
        end: Int,
        active: Bool = true,
        trial: Bool = false,
        willRenew: Bool = true
    ) -> EntitlementDocument {
        EntitlementDocument(
            active: active, expiresAtMillis: end, source: "app_store", state: state, plan: plan,
            trial: trial, willRenew: willRenew, test: true
        )
    }
}

enum FakeBilling {
    /// Le produit pour lequel la dernière feuille d'achat a été ouverte.
    static var lastProductId = MiffPlusProducts.annualId

    @MainActor
    static func stack() -> BillingStack {
        BillingStack(
            gateway: FakeBillingGateway(),
            backend: FakePurchaseBackend(),
            entitlements: FakeSubscriptionStore()
        )
    }
}
#endif
