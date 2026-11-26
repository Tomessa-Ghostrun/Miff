import Foundation
@testable import Miff

// Doubles en mémoire de l'achat de Miff+ (SRV-8I). Aucun ne touche au réseau, à StoreKit ni à Firebase ; les textes signés et les identifiants sont des valeurs d'essai.

let testPurchaseId = "7C9E6679-7425-40DE-944B-E07FC1F90AE7"

/// Attend (2 secondes au plus) qu'une condition devienne vraie, en laissant les autres tâches avancer.
@MainActor
func waitUntil(_ condition: @MainActor () -> Bool) async {
    for _ in 0..<200 where !condition() {
        try? await Task.sleep(nanoseconds: 10_000_000)
    }
}

/// Compte combien de fois le store a été prié de « terminer » une transaction.
final class FinishCounter {
    private(set) var count = 0

    func record() {
        count += 1
    }
}

func makeTransaction(
    id: UInt64 = 1,
    signed: String = "jws-1",
    counter: FinishCounter = FinishCounter()
) -> OwnedTransaction {
    OwnedTransaction(id: id, productId: MiffPlusProducts.annualId, signed: signed, finish: { counter.record() })
}

@MainActor
final class StubGateway: BillingGateway {
    var catalog: CatalogResult = .notAvailable
    var owned: OwnedResult = .loaded([])
    var purchaseResult: StorePurchaseResult = .cancelled
    var syncResult: StoreSyncResult = .done
    private(set) var purchases: [(productId: String, purchaseId: String)] = []
    private(set) var syncCalls = 0
    private(set) var ownedCalls = 0
    private(set) var managementOpened = 0
    private(set) var handler: (@MainActor (OwnedTransaction) -> Void)?

    func loadCatalog() async -> CatalogResult {
        catalog
    }

    func ownedTransactions() async -> OwnedResult {
        ownedCalls += 1
        return owned
    }

    func purchase(productId: String, purchaseId: String) async -> StorePurchaseResult {
        purchases.append((productId, purchaseId))
        return purchaseResult
    }

    func syncWithStore() async -> StoreSyncResult {
        syncCalls += 1
        return syncResult
    }

    func listen(_ handler: @escaping @MainActor (OwnedTransaction) -> Void) {
        self.handler = handler
    }

    func openManagement() async {
        managementOpened += 1
    }
}

final class StubBackend: PurchaseBackend {
    var prepare: PrepareResult = .ready(purchaseId: testPurchaseId)
    /// Les réponses de la vérification, dans l'ordre ; la dernière se répète.
    var syncResults: [SyncResult] = [.synced(state: "trial")]
    var purgeResult = true
    private(set) var prepareCalls = 0
    private(set) var syncedSigned: [String] = []
    private(set) var purgeCalls = 0

    func preparePurchaseId() async -> PrepareResult {
        prepareCalls += 1
        return prepare
    }

    func sync(signedTransaction: String) async -> SyncResult {
        syncedSigned.append(signedTransaction)
        let index = min(syncedSigned.count - 1, syncResults.count - 1)
        return syncResults[index]
    }

    func purgeSubscriptionData() async -> Bool {
        purgeCalls += 1
        return purgeResult
    }
}

final class StubSubscriptionStore: SubscriptionStore {
    var document: EntitlementDocument?
    var failure: Error?
    private(set) var reads = 0

    func read(uid: String) async throws -> EntitlementDocument? {
        reads += 1
        if let failure {
            throw failure
        }
        return document
    }
}

/// Un document `entitlements/{uid}` d'un store, valide à l'instant `testNow`.
let testNow = 1_000_000

func storeDocument(state: String, active: Bool = true, end: Int = testNow + 86_400_000) -> EntitlementDocument {
    EntitlementDocument(active: active, expiresAtMillis: end, source: "app_store", state: state, plan: "yearly")
}

func testProduct(
    id: String = MiffPlusProducts.annualId,
    amount: Decimal = Decimal(5988) / 100,
    display: String = "59,88 €",
    period: StorePeriod = .oneYear,
    trial: StorePeriod? = StorePeriod(unit: .week, value: 1),
    eligible: Bool = true
) -> StoreProductInfo {
    StoreProductInfo(
        id: id, amount: amount, currencyCode: "EUR", displayPrice: display,
        monthlyDisplayPrice: period == .oneYear ? "4,99 €" : nil, period: period,
        freeTrial: trial, eligibleForTrial: eligible
    )
}

func testMonthlyProduct() -> StoreProductInfo {
    testProduct(
        id: MiffPlusProducts.monthlyId, amount: Decimal(699) / 100, display: "6,99 €",
        period: .oneMonth, trial: nil, eligible: false
    )
}
