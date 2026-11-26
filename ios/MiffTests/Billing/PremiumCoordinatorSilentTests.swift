// Quelques assertions portent un message en clair : elles dépassent la limite de ligne.
// swiftlint:disable line_length
import Combine
import XCTest
@testable import Miff

/// La restauration (sur le geste de la personne seulement) et les chemins SILENCIEUX de l'achat de Miff+ : le regard à l'ouverture de l'app et les transactions qui arrivent
/// hors d'un achat en cours (ticket `docs/tickets/premium-achat-reel.md` §7, critères AN9, IO2, IO3).
@MainActor
final class PremiumCoordinatorSilentTests: XCTestCase {
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

    private func restore(_ coordinator: PremiumCoordinator) async -> RestoreOutcome? {
        coordinator.startRestore()
        await coordinator.work?.value
        if case .finished(_, _, let restore) = coordinator.session {
            return restore
        }
        return nil
    }

    // MARK: - « Restaurer mes achats »

    func testRestoringAsksTheStoreToSyncThenHasTheServerCheckWhatItFinds() async {
        let counter = FinishCounter()
        gateway.owned = .loaded([makeTransaction(signed: "jws-restored", counter: counter)])
        backend.syncResults = [.synced(state: "active")]
        let coordinator = makeCoordinator()

        let outcome = await restore(coordinator)

        XCTAssertEqual(outcome, .found)
        XCTAssertEqual(gateway.syncCalls, 1, "AppStore.sync, sur le geste de la personne")
        XCTAssertEqual(backend.syncedSigned, ["jws-restored"])
        XCTAssertEqual(counter.count, 1)
        XCTAssertEqual(grants, 1)
    }

    func testRestoringWhenTheStoreHoldsNothingAndTheServerKnowsNothing() async {
        store.document = nil
        let coordinator = makeCoordinator()

        let outcome = await restore(coordinator)

        XCTAssertEqual(outcome, .noneFound)
        XCTAssertEqual(grants, 0)
    }

    func testRestoringWhenTheServerAlreadyKnowsTheSubscription() async {
        store.document = storeDocument(state: "active")
        let coordinator = makeCoordinator()

        let outcome = await restore(coordinator)

        XCTAssertEqual(outcome, .found, "acheté sur un autre appareil ou chez l'autre store")
        XCTAssertEqual(grants, 1)
    }

    func testRestoringFindsASuspendedSubscriptionThatTheStoreDoesNotList() async {
        store.document = storeDocument(state: "on_hold", active: false, end: testNow - 1)
        let coordinator = makeCoordinator()

        let outcome = await restore(coordinator)

        XCTAssertEqual(outcome, .onHold)
        XCTAssertEqual(grants, 0)
    }

    func testRestoringAnAccountUsedByAnotherMiffAccountIsRefusedCalmly() async {
        gateway.owned = .loaded([makeTransaction()])
        backend.syncResults = [.alreadyLinked]
        let coordinator = makeCoordinator()

        let outcome = await restore(coordinator)

        XCTAssertEqual(outcome, .otherAccount)
        XCTAssertEqual(grants, 0)
    }

    func testTheAppleSignInRefusedByThePersonIsSilentAndNothingIsSent() async {
        gateway.syncResult = .cancelled
        gateway.owned = .loaded([makeTransaction()])
        let coordinator = makeCoordinator()

        let outcome = await restore(coordinator)

        XCTAssertEqual(outcome, .cancelled)
        XCTAssertTrue(backend.syncedSigned.isEmpty)
    }

    func testRestorationErrorsAreCalm() async {
        gateway.syncResult = .network
        let coordinator = makeCoordinator()
        let network = await restore(coordinator)
        gateway.syncResult = .failed
        let failed = await restore(coordinator)

        XCTAssertEqual(network, .network)
        XCTAssertEqual(failed, .failed)
    }

    func testTheServerUnreadableAfterAnEmptyStoreIsNetworkNeverNothingFound() async {
        store.failure = URLError(.notConnectedToInternet)
        let coordinator = makeCoordinator()

        let outcome = await restore(coordinator)

        XCTAssertEqual(outcome, .network, "une lecture ratée n'est jamais prise pour « gratuit »")
    }

    // MARK: - Le regard silencieux à l'ouverture (IO3 : jamais d'identification Apple)

    func testTheSilentLookNeverAsksTheStoreToSync() async {
        gateway.owned = .loaded([makeTransaction()])
        store.document = nil
        backend.syncResults = [.synced(state: "active")]
        let coordinator = makeCoordinator()

        await coordinator.reconcile()

        XCTAssertEqual(gateway.syncCalls, 0, "AppStore.sync ouvre l'identification Apple : jamais tout seul")
        XCTAssertEqual(backend.syncedSigned.count, 1)
        XCTAssertEqual(grants, 1)
    }

    func testTheSilentLookDoesNothingWhenTheStoreHoldsNothing() async {
        let coordinator = makeCoordinator()

        await coordinator.reconcile()

        XCTAssertTrue(backend.syncedSigned.isEmpty)
        XCTAssertEqual(store.reads, 0)
    }

    func testTheSilentLookDoesNothingWhenTheServerAlreadyGrantsMiffPlus() async {
        gateway.owned = .loaded([makeTransaction()])
        store.document = storeDocument(state: "active")
        let coordinator = makeCoordinator()

        await coordinator.reconcile()

        XCTAssertTrue(backend.syncedSigned.isEmpty)
    }

    func testTheSilentLookIsSpacedByFiveMinutes() async {
        gateway.owned = .loaded([makeTransaction()])
        backend.syncResults = [.unavailable]
        let coordinator = makeCoordinator()

        await coordinator.reconcile()
        await coordinator.reconcile()
        XCTAssertEqual(backend.syncedSigned.count, 1, "deux ouvertures rapprochées : un seul regard")

        clock.set(PremiumCoordinator.defaultReconcileSpacing)
        await coordinator.reconcile()
        XCTAssertEqual(backend.syncedSigned.count, 2)
    }

    func testTheSilentLookDoesNothingWithoutAnAccountAndRestartsForAnotherOne() async {
        gateway.owned = .loaded([makeTransaction()])
        backend.syncResults = [.unavailable]
        uid = nil
        let coordinator = makeCoordinator()
        await coordinator.reconcile()
        XCTAssertEqual(gateway.ownedCalls, 0)

        uid = "uid-1"
        await coordinator.reconcile()
        uid = "uid-2"
        await coordinator.reconcile()

        XCTAssertEqual(backend.syncedSigned.count, 2, "un autre compte repart de zéro (pas d'espacement hérité)")
    }

    func testAPurchaseOfAnotherAccountIsNotSentAgainByTheSilentPaths() async {
        gateway.owned = .loaded([makeTransaction(id: 9)])
        backend.syncResults = [.alreadyLinked]
        let coordinator = makeCoordinator()

        await coordinator.reconcile()
        clock.set(PremiumCoordinator.defaultReconcileSpacing * 2)
        await coordinator.reconcile()

        XCTAssertEqual(backend.syncedSigned.count, 1, "refusé : plus renvoyé tout seul, seule une restauration le refait")
    }

    // MARK: - Les transactions qui arrivent seules (IO2)

    func testTheListenerIsInPlaceBeforeAnyScreen() {
        let coordinator = makeCoordinator()

        XCTAssertNil(gateway.handler)
        coordinator.startListening()

        XCTAssertNotNil(gateway.handler, "l'écoute démarre au lancement : les transactions non terminées ne sont livrées qu'une fois")
    }

    func testAnApprovedAskToBuyIsCheckedSilentlyFinishedAndAnnounced() async {
        let counter = FinishCounter()
        backend.syncResults = [.synced(state: "active")]
        let coordinator = makeCoordinator()
        coordinator.startListening()

        gateway.handler?(makeTransaction(id: 5, signed: "jws-late", counter: counter))
        await waitUntil { grants > 0 }

        XCTAssertEqual(backend.syncedSigned, ["jws-late"])
        XCTAssertEqual(counter.count, 1)
        XCTAssertEqual(grants, 1)
    }

    func testATransactionThatArrivesWithoutAnAccountIsLeftUnfinishedForTheNextLaunch() async {
        uid = nil
        let counter = FinishCounter()
        let coordinator = makeCoordinator()
        coordinator.startListening()

        gateway.handler?(makeTransaction(counter: counter))
        try? await Task.sleep(nanoseconds: 50_000_000)

        XCTAssertTrue(backend.syncedSigned.isEmpty)
        XCTAssertEqual(counter.count, 0, "non terminée : le store la re-présente")
    }

    func testATransactionThatIsRefusedStaysUnfinishedAndIsNotSentTwice() async {
        let counter = FinishCounter()
        backend.syncResults = [.alreadyLinked]
        let coordinator = makeCoordinator()
        coordinator.startListening()

        gateway.handler?(makeTransaction(id: 7, counter: counter))
        await waitUntil { !backend.syncedSigned.isEmpty }
        try? await Task.sleep(nanoseconds: 50_000_000)
        gateway.handler?(makeTransaction(id: 7, counter: counter))
        try? await Task.sleep(nanoseconds: 50_000_000)

        XCTAssertEqual(backend.syncedSigned.count, 1)
        XCTAssertEqual(counter.count, 0)
        XCTAssertEqual(grants, 0)
    }
}
// swiftlint:enable line_length
