// Quelques assertions portent un message en clair : elles dépassent la limite de ligne.
// swiftlint:disable line_length
import Combine
import XCTest
@testable import Miff

/// L'achat de Miff+ : un paiement n'est qu'une PREUVE envoyée au serveur, jamais un droit (ticket `docs/tickets/premium-achat-reel.md` §5 à §8, critères AN2 à AN5, AN9, AN12, IO1,
/// IO3). Les doubles remplacent le store, le serveur et la lecture du droit ; l'horloge est manuelle.
@MainActor
final class PremiumCoordinatorTests: XCTestCase {
    private let gateway = StubGateway()
    private let backend = StubBackend()
    private let store = StubSubscriptionStore()
    private let clock = ManualPaywallClock()
    private var uid: String? = "uid-1"
    private var grants = 0
    private var tokens = Set<AnyCancellable>()

    private func makeCoordinator() -> PremiumCoordinator {
        let subscriptions = SubscriptionRepository(
            store: store, currentUid: { [unowned self] in self.uid }, nowMillis: { testNow }
        )
        let coordinator = PremiumCoordinator(
            gateway: gateway, backend: backend, subscriptions: subscriptions,
            currentUid: { [unowned self] in self.uid }, clock: clock
        )
        coordinator.accessGranted.sink { [unowned self] in self.grants += 1 }.store(in: &tokens)
        return coordinator
    }

    private func settle(_ coordinator: PremiumCoordinator) async -> PurchaseSession {
        await coordinator.work?.value
        return coordinator.session
    }

    // MARK: - L'achat

    func testAPaidPurchaseIsCheckedByTheServerThenFinishedThenGranted() async {
        let counter = FinishCounter()
        gateway.purchaseResult = .purchased(makeTransaction(signed: "jws-secret", counter: counter))
        backend.syncResults = [.synced(state: "trial")]
        let coordinator = makeCoordinator()

        coordinator.startPurchase(plan: .annual)
        XCTAssertEqual(coordinator.session, .working(restoring: false))
        let session = await settle(coordinator)

        XCTAssertEqual(session, .finished(serial: 1, outcome: .granted, restore: nil))
        XCTAssertEqual(backend.syncedSigned, ["jws-secret"], "la preuve part au serveur, une seule fois")
        XCTAssertEqual(counter.count, 1, "terminée auprès du store APRÈS l'accord du serveur")
        XCTAssertEqual(grants, 1)
    }

    func testTheOnlyIdentifierGivenToTheStoreIsTheRandomIdOfTheServer() async {
        gateway.purchaseResult = .cancelled
        let coordinator = makeCoordinator()

        coordinator.startPurchase(plan: .monthly)
        _ = await settle(coordinator)

        XCTAssertEqual(gateway.purchases.count, 1)
        XCTAssertEqual(gateway.purchases.first?.purchaseId, testPurchaseId)
        XCTAssertEqual(gateway.purchases.first?.productId, MiffPlusProducts.monthlyId)
        XCTAssertEqual(backend.prepareCalls, 1)
    }

    func testTheAppNeverGrantsAnythingByItself() async {
        // Même un paiement réussi n'allume RIEN tant que le serveur n'a pas dit « vérifié » : sans accord, aucun droit n'est annoncé.
        gateway.purchaseResult = .purchased(makeTransaction())
        backend.syncResults = [.unavailable]
        let coordinator = makeCoordinator()

        coordinator.startPurchase(plan: .annual)
        let session = await settle(coordinator)

        XCTAssertEqual(session, .finished(serial: 1, outcome: .network, restore: nil))
        XCTAssertEqual(grants, 0)
    }

    func testCancellingTheSheetIsSilentAndSendsNothingToTheServer() async {
        gateway.purchaseResult = .cancelled
        let coordinator = makeCoordinator()

        coordinator.startPurchase(plan: .annual)
        let session = await settle(coordinator)

        XCTAssertEqual(session, .finished(serial: 1, outcome: .cancelled, restore: nil))
        XCTAssertTrue(backend.syncedSigned.isEmpty)
        XCTAssertEqual(grants, 0)
    }

    func testAPendingPaymentGrantsNothing() async {
        gateway.purchaseResult = .pending
        let coordinator = makeCoordinator()

        coordinator.startPurchase(plan: .annual)
        let session = await settle(coordinator)

        XCTAssertEqual(session, .finished(serial: 1, outcome: .pending, restore: nil))
        XCTAssertTrue(backend.syncedSigned.isEmpty)
        XCTAssertEqual(grants, 0)
    }

    func testServerStatesDecideTheOutcome() async {
        let cases: [(String?, PurchaseOutcome)] = [
            ("trial", .granted), ("active", .granted), ("canceled", .granted), ("grace", .granted),
            ("pending", .pending), ("on_hold", .onHold), ("expired", .failed), ("revoked", .failed), (nil, .failed)
        ]
        for (state, expected) in cases {
            let single = StubBackend()
            single.syncResults = [.synced(state: state)]
            let alone = PremiumCoordinator(
                gateway: gateway, backend: single,
                subscriptions: SubscriptionRepository(store: store, currentUid: { "uid-1" }),
                currentUid: { "uid-1" }, clock: ManualPaywallClock()
            )
            gateway.purchaseResult = .purchased(makeTransaction())

            alone.startPurchase(plan: .annual)
            await alone.work?.value

            XCTAssertEqual(alone.session, .finished(serial: 1, outcome: expected, restore: nil), "\(String(describing: state))")
        }
    }

    func testNoConnectionBeforeThePurchaseStartsNothing() async {
        backend.prepare = .unavailable
        let coordinator = makeCoordinator()

        coordinator.startPurchase(plan: .annual)
        let session = await settle(coordinator)

        XCTAssertEqual(session, .finished(serial: 1, outcome: .network, restore: nil))
        XCTAssertTrue(gateway.purchases.isEmpty, "aucun achat lancé hors connexion (AN12)")
    }

    func testARefusedPreparationIsAFailureAndTheSheetNeverOpens() async {
        backend.prepare = .refused
        let coordinator = makeCoordinator()

        coordinator.startPurchase(plan: .annual)
        let session = await settle(coordinator)

        XCTAssertEqual(session, .finished(serial: 1, outcome: .failed, restore: nil))
        XCTAssertTrue(gateway.purchases.isEmpty)
    }

    func testNothingStartsWithoutASignedInAccount() async {
        uid = nil
        let coordinator = makeCoordinator()

        coordinator.startPurchase(plan: .annual)
        let session = await settle(coordinator)

        XCTAssertEqual(session, .finished(serial: 1, outcome: .failed, restore: nil))
        XCTAssertEqual(backend.prepareCalls, 0)
    }

    func testAnotherMiffAccountGetsACalmRefusalAndTheTransactionIsNotFinished() async {
        let counter = FinishCounter()
        gateway.purchaseResult = .purchased(makeTransaction(counter: counter))
        backend.syncResults = [.alreadyLinked]
        let coordinator = makeCoordinator()

        coordinator.startPurchase(plan: .annual)
        let session = await settle(coordinator)

        XCTAssertEqual(session, .finished(serial: 1, outcome: .otherAccount, restore: nil))
        XCTAssertEqual(counter.count, 0)
        XCTAssertEqual(grants, 0)
    }

    func testARefusalForGoodIsAFailureAndAnUnreachableServerIsNetwork() async {
        let results: [(PurchaseSyncResult, PurchaseOutcome)] = [(.rejected, .failed), (.unavailable, .network)]
        for (result, expected) in results {
            backend.syncResults = [result]
            gateway.purchaseResult = .purchased(makeTransaction())
            let coordinator = makeCoordinator()

            coordinator.startPurchase(plan: .annual)
            let session = await settle(coordinator)

            XCTAssertEqual(session, .finished(serial: 1, outcome: expected, restore: nil))
        }
    }

    func testStoreErrorsAreTranslatedWithoutAnyMessageOfTheirOwn() async {
        let results: [(StorePurchaseResult, PurchaseOutcome)] = [
            (.unavailable, .unavailable), (.network, .network), (.failed, .failed)
        ]
        for (result, expected) in results {
            gateway.purchaseResult = result
            let coordinator = makeCoordinator()

            coordinator.startPurchase(plan: .annual)
            let session = await settle(coordinator)

            XCTAssertEqual(session, .finished(serial: 1, outcome: expected, restore: nil))
        }
    }

    func testAnAccountThatAlreadyOwnsMiffPlusIsNeverOfferedASecondPurchase() async {
        let counter = FinishCounter()
        gateway.owned = .loaded([makeTransaction(signed: "jws-owned", counter: counter)])
        backend.syncResults = [.synced(state: "active")]
        let coordinator = makeCoordinator()

        coordinator.startPurchase(plan: .annual)
        let session = await settle(coordinator)

        XCTAssertEqual(session, .finished(serial: 1, outcome: .granted, restore: nil))
        XCTAssertTrue(gateway.purchases.isEmpty, "Apple 3.1.2(b) : pas de souscription accidentelle à une seconde variante")
        XCTAssertEqual(backend.prepareCalls, 0)
        XCTAssertEqual(backend.syncedSigned, ["jws-owned"])
        XCTAssertEqual(counter.count, 1)
    }

    func testAlreadyOwnedAnswerWithNothingListedIsAnOnHoldSubscriptionNeverAFailure() async {
        gateway.purchaseResult = .alreadyOwned
        store.document = storeDocument(state: "on_hold", active: false, end: testNow - 1)
        let coordinator = makeCoordinator()

        coordinator.startPurchase(plan: .annual)
        let session = await settle(coordinator)

        XCTAssertEqual(session, .finished(serial: 1, outcome: .onHold, restore: nil))
    }

    func testOnlyOneWorkRunsAtATimeAndASecondTouchIsIgnored() async {
        gateway.purchaseResult = .cancelled
        let coordinator = makeCoordinator()

        coordinator.startPurchase(plan: .annual)
        coordinator.startPurchase(plan: .monthly)
        coordinator.startRestore()
        _ = await settle(coordinator)

        XCTAssertEqual(gateway.purchases.count, 1)
        XCTAssertEqual(gateway.syncCalls, 0)
    }

    func testTheSessionKeepsTheLastResultAndNumbersEachOne() async {
        gateway.purchaseResult = .cancelled
        let coordinator = makeCoordinator()

        coordinator.startPurchase(plan: .annual)
        _ = await settle(coordinator)
        coordinator.startPurchase(plan: .annual)
        let second = await settle(coordinator)

        XCTAssertEqual(second, .finished(serial: 2, outcome: .cancelled, restore: nil))
    }

    // MARK: - La vérification par le serveur, espacée

    func testTwoChecksAreSpacedByMoreThanTheMinuteOfTheServer() async {
        backend.syncResults = [.synced(state: "active")]
        gateway.purchaseResult = .purchased(makeTransaction(id: 1))
        let coordinator = makeCoordinator()

        coordinator.startPurchase(plan: .annual)
        _ = await settle(coordinator)
        gateway.purchaseResult = .purchased(makeTransaction(id: 2))
        coordinator.startPurchase(plan: .annual)
        _ = await settle(coordinator)

        XCTAssertEqual(clock.sleeps, [PremiumCoordinator.defaultSyncSpacing], "la 2e vérification attend son tour")
        XCTAssertGreaterThan(PremiumCoordinator.defaultSyncSpacing, 60)
    }

    func testTooSoonIsRetriedOnceAfterTheSpacingForAnAnswerThePersonWaitsFor() async {
        backend.syncResults = [.rateLimited, .synced(state: "active")]
        gateway.purchaseResult = .purchased(makeTransaction())
        let coordinator = makeCoordinator()

        coordinator.startPurchase(plan: .annual)
        let session = await settle(coordinator)

        XCTAssertEqual(session, .finished(serial: 1, outcome: .granted, restore: nil))
        XCTAssertEqual(backend.syncedSigned.count, 2)
        XCTAssertEqual(clock.sleeps, [PremiumCoordinator.defaultSyncSpacing])
    }

    func testAnUnreachableServerDoesNotUseTheMinute() async {
        backend.syncResults = [.unavailable, .synced(state: "active")]
        gateway.purchaseResult = .purchased(makeTransaction())
        let coordinator = makeCoordinator()

        coordinator.startPurchase(plan: .annual)
        _ = await settle(coordinator)
        coordinator.startPurchase(plan: .annual)
        let session = await settle(coordinator)

        XCTAssertEqual(session, .finished(serial: 2, outcome: .granted, restore: nil))
        XCTAssertTrue(clock.sleeps.isEmpty, "un appel qui n'a pas atteint le serveur n'use pas sa minute")
    }

    // MARK: - Les offres et les textes de sécurité

    func testOffersComeFromTheStoreOnly() async {
        gateway.catalog = .loaded([testProduct(), testMonthlyProduct()])
        let coordinator = makeCoordinator()

        let result = await coordinator.loadOffers()

        guard case .loaded(let offers) = result else {
            return XCTFail("les offres du store devaient arriver")
        }
        XCTAssertTrue(offers.trialEligible)
        XCTAssertEqual(PaywallPriceFormat.string(offers.annual), "59,88 €")
    }

    func testAMissingProductOrAFailingStoreGivesACalmUnavailableOffer() async {
        let coordinator = makeCoordinator()
        gateway.catalog = .loaded([testProduct()])
        let missing = await coordinator.loadOffers()
        gateway.catalog = .notAvailable
        let none = await coordinator.loadOffers()
        gateway.catalog = .error
        let failing = await coordinator.loadOffers()

        XCTAssertEqual(missing, .notAvailable, "il manque le mensuel : rien n'est inventé")
        XCTAssertEqual(none, .notAvailable)
        XCTAssertEqual(failing, .error)
    }

    func testATransactionAndAPurchaseIdNeverAppearInAnyDescription() {
        let transaction = makeTransaction(signed: "jws-very-secret")

        XCTAssertFalse(String(describing: transaction).contains("jws-very-secret"))
        XCTAssertFalse(String(describing: PrepareResult.ready(purchaseId: testPurchaseId)).contains(testPurchaseId))
        XCTAssertFalse(String(reflecting: transaction).contains("jws-very-secret"))
    }
}
// swiftlint:enable line_length
