import XCTest
@testable import Miff

/// L'offre branchée sur le coordinateur de l'achat (SRV-8I ; critères PW9 à PW13, AN2, AN5, AN11, AN12) : les prix viennent du store, le résultat de chaque geste devient un événement
/// de l'écran, et Miff+ n'est jamais accordé par l'écran lui-même.
@MainActor
final class PaywallStoreDriverTests: XCTestCase {
    private let gateway = StubGateway()
    private let backend = StubBackend()
    private let store = StubSubscriptionStore()
    private let announcer = RecordingPaywallAnnouncer()
    private lazy var coordinator = PremiumCoordinator(
        gateway: gateway, backend: backend,
        subscriptions: SubscriptionRepository(store: store, currentUid: { "uid-1" }, nowMillis: { testNow }),
        currentUid: { "uid-1" }, clock: ManualPaywallClock()
    )

    private func makeDriver(timeout: TimeInterval = 5) -> (PaywallStoreDriver, PaywallViewModel) {
        let model = PaywallViewModel(reason: .limit, clock: ManualPaywallClock(), announcer: announcer)
        return (PaywallStoreDriver(model: model, premium: coordinator, offersTimeout: timeout), model)
    }

    private func catalog(trial: Bool = true) {
        gateway.catalog = .loaded([testProduct(eligible: trial), testMonthlyProduct()])
    }

    // MARK: - Les offres

    func testTheOfferStartsLoadingThenShowsThePricesOfTheStore() async {
        catalog()
        let (driver, model) = makeDriver()
        XCTAssertEqual(model.state, .comingSoon)

        await driver.start()

        guard case .offers(let offers) = model.state else {
            return XCTFail("les offres du store devaient s'afficher : \(model.state)")
        }
        XCTAssertTrue(offers.trialEligible)
        XCTAssertEqual(model.layout, .trial)
    }

    func testAnAccountWithoutTheTrialSeesTheTwoPlans() async {
        catalog(trial: false)
        let (driver, model) = makeDriver()

        await driver.start()

        XCTAssertEqual(model.layout, .plans)
    }

    func testAMissingProductOrAFailingStoreIsACalmUnavailableStateWithoutAnyPrice() async {
        gateway.catalog = .notAvailable
        let (driver, model) = makeDriver()

        await driver.start()

        XCTAssertEqual(model.state, .unavailable)
        XCTAssertNil(model.state.offers)
    }

    func testRetryingAsksTheStoreAgain() async {
        gateway.catalog = .error
        let (driver, model) = makeDriver()
        await driver.start()
        XCTAssertEqual(model.state, .unavailable)
        catalog()

        await driver.load()

        XCTAssertNotNil(model.state.offers)
    }

    func testAnAccountThatAlreadyHasMiffPlusSeesTheConfirmationNeverAnOffer() async {
        catalog()
        store.document = storeDocument(state: "active")
        var grants = 0
        let token = coordinator.accessGranted.sink { grants += 1 }
        let (driver, model) = makeDriver()

        await driver.start()

        XCTAssertEqual(model.state, .subscribed)
        XCTAssertEqual(grants, 1, "les écrans qui gardent la formule la relisent (bug de « Créer une Miff »)")
        token.cancel()
    }

    func testPricesThatTakeTooLongBecomeUnavailable() async {
        let slow = SlowGateway()
        slow.catalog = .loaded([testProduct(), testMonthlyProduct()])
        let slowCoordinator = PremiumCoordinator(
            gateway: slow, backend: backend,
            subscriptions: SubscriptionRepository(store: store, currentUid: { "uid-1" }, nowMillis: { testNow }),
            currentUid: { "uid-1" }, clock: ManualPaywallClock()
        )
        let model = PaywallViewModel(reason: .limit, clock: ManualPaywallClock(), announcer: announcer)
        let driver = PaywallStoreDriver(model: model, premium: slowCoordinator, offersTimeout: 0.05)

        await driver.start()

        XCTAssertEqual(model.state, .unavailable, "au-delà du délai : les offres ne sont pas disponibles (PW9)")
    }

    // MARK: - Les gestes

    func testATrialPurchaseGoesThroughTheStoreThenTheServerThenTheWelcome() async {
        catalog()
        gateway.purchaseResult = .purchased(makeTransaction())
        backend.syncResults = [.synced(state: "trial")]
        let (driver, model) = makeDriver()
        await driver.start()

        driver.purchase(.annual)
        guard case .purchasing(_, let plan) = model.state else {
            return XCTFail("l'achat est en cours : \(model.state)")
        }
        XCTAssertEqual(plan, .annual)
        await coordinator.work?.value

        XCTAssertEqual(model.state, .subscribed)
        XCTAssertEqual(gateway.purchases.first?.productId, MiffPlusProducts.annualId)
    }

    func testCancellingTheStoreSheetGoesBackToTheOfferWithoutAMessage() async {
        catalog()
        gateway.purchaseResult = .cancelled
        let (driver, model) = makeDriver()
        await driver.start()

        driver.purchase(.monthly)
        await coordinator.work?.value

        guard case .offers = model.state else {
            return XCTFail("retour à l'offre, sans message : \(model.state)")
        }
        XCTAssertEqual(gateway.purchases.first?.productId, MiffPlusProducts.monthlyId)
    }

    func testAPendingPaymentGrantsNothingAndKeepsTheButtonsAsleep() async {
        catalog()
        gateway.purchaseResult = .pending
        let (driver, model) = makeDriver()
        await driver.start()

        driver.purchase(.annual)
        await coordinator.work?.value

        guard case .pending = model.state else {
            return XCTFail("« Ask to Buy » : en attente, aucun droit : \(model.state)")
        }
        XCTAssertTrue(model.state.isBusy)
    }

    func testAnApprovedAskToBuyTurnsThePendingOfferIntoTheWelcome() async {
        catalog()
        gateway.purchaseResult = .pending
        backend.syncResults = [.synced(state: "active")]
        let (driver, model) = makeDriver()
        await driver.start()
        coordinator.startListening()
        driver.purchase(.annual)
        await coordinator.work?.value

        gateway.handler?(makeTransaction(id: 12))
        await waitUntil { model.state == .subscribed }

        XCTAssertEqual(model.state, .subscribed)
    }

    func testAnotherAccountAndAnOnHoldSubscriptionHaveTheirCalmStates() async {
        catalog()
        gateway.purchaseResult = .purchased(makeTransaction())
        backend.syncResults = [.alreadyLinked]
        let (driver, model) = makeDriver()
        await driver.start()

        driver.purchase(.annual)
        await coordinator.work?.value

        guard case .otherAccount = model.state else {
            return XCTFail("un achat = un compte : \(model.state)")
        }
        XCTAssertEqual(PaywallMessage.otherAccount.text, L10n.format("paywall_other_account", AppLinks.supportEmail))
        XCTAssertEqual(PaywallMessage.onHold.text, L10n.string("settings_subscription_on_hold"))
    }

    func testAFailureIsCalmAndRestoreStaysAvailable() async {
        catalog()
        gateway.purchaseResult = .failed
        let (driver, model) = makeDriver()
        await driver.start()

        driver.purchase(.annual)
        await coordinator.work?.value

        guard case .failed = model.state else {
            return XCTFail("erreur calme : \(model.state)")
        }
        XCTAssertFalse(model.state.isBusy)
        driver.restore()
        guard case .restoring = model.state else {
            return XCTFail("« Restaurer mes achats » reste disponible : \(model.state)")
        }
        await coordinator.work?.value
    }

    func testRestoringNeverStartsByItselfAndAskedByThePersonItReachesTheStore() async {
        catalog()
        let (driver, model) = makeDriver()
        await driver.start()
        XCTAssertEqual(gateway.syncCalls, 0, "ouvrir l'offre ne restaure rien (PW12, IO3)")

        driver.restore()
        await coordinator.work?.value

        XCTAssertEqual(gateway.syncCalls, 1)
        guard case .restoreNone = model.state else {
            return XCTFail("rien trouvé : message calme : \(model.state)")
        }
    }

    func testRestoringFindsTheSubscriptionAndShowsTheWelcome() async {
        catalog()
        gateway.owned = .loaded([makeTransaction()])
        backend.syncResults = [.synced(state: "active")]
        let (driver, model) = makeDriver()
        await driver.start()

        driver.restore()
        await coordinator.work?.value

        XCTAssertEqual(model.state, .subscribed)
    }

    func testATouchWhileAnotherWorkRunsIsIgnored() async {
        catalog()
        gateway.purchaseResult = .cancelled
        let (driver, model) = makeDriver()
        await driver.start()
        coordinator.startRestore()

        driver.purchase(.annual)

        guard case .offers = model.state else {
            return XCTFail("pas de double travail : \(model.state)")
        }
        XCTAssertTrue(gateway.purchases.isEmpty)
        await coordinator.work?.value
    }

    func testAResultThatEndedBeforeTheOfferOpenedIsNotToldToIt() async {
        catalog()
        gateway.purchaseResult = .failed
        coordinator.startPurchase(plan: .annual)
        await coordinator.work?.value
        let (driver, model) = makeDriver()

        await driver.start()

        guard case .offers = model.state else {
            return XCTFail("l'ancien échec n'est pas raconté à ce nouvel affichage : \(model.state)")
        }
    }

    // MARK: - Les correspondances

    func testEveryOutcomeBecomesOneEventOfTheScreen() {
        let purchases: [(PurchaseOutcome, PaywallEvent)] = [
            (.granted, .purchaseSucceeded), (.pending, .purchasePending), (.cancelled, .purchaseCancelled),
            (.otherAccount, .purchaseOtherAccount), (.unavailable, .purchaseUnavailable), (.onHold, .purchaseOnHold),
            (.network, .purchaseFailed), (.failed, .purchaseFailed)
        ]
        for (outcome, event) in purchases {
            XCTAssertEqual(PaywallStoreDriver.event(for: outcome), event, "\(outcome)")
        }
        let restores: [(RestoreOutcome, PaywallEvent)] = [
            (.found, .restoreFound), (.noneFound, .restoreNothing), (.otherAccount, .restoreOtherAccount),
            (.pending, .restorePending), (.onHold, .restoreOnHold), (.cancelled, .restoreCancelled),
            (.network, .restoreFailed), (.failed, .restoreFailed)
        ]
        for (outcome, event) in restores {
            XCTAssertEqual(PaywallStoreDriver.event(for: outcome), event, "\(outcome)")
        }
    }
}

/// Un store qui ne répond pas à temps : le catalogue attend bien plus longtemps que le délai de l'offre.
@MainActor
final class SlowGateway: BillingGateway {
    var catalog: CatalogResult = .notAvailable

    func loadCatalog() async -> CatalogResult {
        try? await Task.sleep(nanoseconds: 2_000_000_000)
        return catalog
    }

    func ownedTransactions() async -> OwnedResult {
        .loaded([])
    }

    func purchase(productId: String, purchaseId: String) async -> StorePurchaseResult {
        .cancelled
    }

    func syncWithStore() async -> StoreSyncResult {
        .done
    }

    func listen(_ handler: @escaping @MainActor (OwnedTransaction) -> Void) {}

    func openManagement() async {}
}
