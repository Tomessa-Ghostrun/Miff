// Quelques assertions portent un message en clair : elles dépassent la limite de ligne.
// swiftlint:disable line_length
import Combine
import XCTest
@testable import Miff

/// Ce que l'achat de Miff+ branche dans le reste de l'app (SRV-8I) : la formule affichée par l'annuaire des Miffs (bug de « Créer une Miff » vu à la recette Android du 2026-11-25),
/// la ligne « Abonnement » des Réglages, et l'offre de l'achat éteint.
@MainActor
final class PremiumWiringTests: XCTestCase {
    private let repo = FakeMiffRepository()
    private let active = MemoryActiveMiffStore()
    private let timeline = Timeline()
    private lazy var cache = FakeCachePurger(timeline: timeline)
    private let uid = TestBox<String?>("uid-1")

    private func makeDirectory() -> MiffDirectory {
        let current = uid
        return MiffDirectory(repository: repo, currentUid: { current.value }, activeStore: active, cache: cache)
    }

    private func ready(_ directory: MiffDirectory) -> DirectoryReady? {
        if case .ready(let value) = directory.state {
            return value
        }
        return nil
    }

    private func load(premium: Bool, known: Bool = true) -> MyMiffsLoad {
        .loaded(
            MyMiffs(
                miffs: [MiffSummary(id: testMiffId, name: "A")], lost: [], premium: premium, fromCache: !known,
                premiumKnown: known
            )
        )
    }

    // MARK: - La formule de l'annuaire suit l'achat tout de suite

    func testAnAccessGrantedPutsThePlanOfTheListRightAtOnce() async {
        let directory = makeDirectory()
        repo.myMiffsResult = load(premium: false)
        await directory.refresh()
        XCTAssertEqual(FeedSelectorRules.outcome(for: ready(directory)), .openPaywall, "avant : la porte ouvre l'offre")

        directory.onPremiumGranted()

        XCTAssertEqual(ready(directory)?.premium, true)
        XCTAssertEqual(ready(directory)?.premiumKnown, true)
        XCTAssertEqual(FeedSelectorRules.outcome(for: ready(directory)), .openFlow, "après : « Créer une Miff » ouvre le parcours, plus l'offre")
    }

    func testAnUnknownPlanIsAlsoPutRight() async {
        let directory = makeDirectory()
        repo.myMiffsResult = load(premium: false, known: false)
        await directory.refresh()

        directory.onPremiumGranted()

        XCTAssertEqual(ready(directory)?.premium, true)
        XCTAssertEqual(ready(directory)?.premiumKnown, true)
    }

    func testTheGrantChangesNothingElseOfTheList() async {
        let directory = makeDirectory()
        repo.myMiffsResult = load(premium: false)
        await directory.refresh()
        let before = ready(directory)

        directory.onPremiumGranted()

        XCTAssertEqual(ready(directory)?.miffs, before?.miffs)
        XCTAssertEqual(ready(directory)?.activeId, before?.activeId)
    }

    func testBeforeTheListIsReadAGrantDoesNothing() {
        let directory = makeDirectory()

        directory.onPremiumGranted()

        XCTAssertEqual(directory.state, .loading)
    }

    func testEachGrantFixesThePlanAtOnceThenReadsTheServerAgain() async {
        let directory = makeDirectory()
        repo.myMiffsResult = load(premium: false)
        await directory.refresh()
        let grants = PassthroughSubject<Void, Never>()
        let token = directory.followPremiumGrants(grants)
        // Le serveur, relu juste après, dit lui aussi Miff+.
        repo.myMiffsResult = load(premium: true)
        let readsBefore = repo.calls.filter { $0 == "myMiffs" }.count

        grants.send()
        XCTAssertEqual(ready(directory)?.premium, true, "tout de suite, avant même la relecture")
        await waitUntil { repo.calls.filter { $0 == "myMiffs" }.count > readsBefore }

        XCTAssertGreaterThan(repo.calls.filter { $0 == "myMiffs" }.count, readsBefore, "puis le serveur est relu")
        token.cancel()
    }

    // MARK: - La ligne « Abonnement » et l'achat éteint

    func testTheSubscriptionRowIsReachableFromTheSettingsOfAnAccountWithMiffsOnly() {
        XCTAssertTrue(SettingsAccess.children(of: .settings).contains(.subscription))
        XCTAssertTrue(SettingsAccess.isAllowed(.subscription, restricted: false))
        XCTAssertFalse(SettingsAccess.isAllowed(.subscription, restricted: true))
        XCTAssertFalse(SettingsAccess.reachable(restricted: true).contains(.subscription))
        XCTAssertTrue(SettingsAccess.children(of: .subscription).isEmpty)
        XCTAssertEqual(SettingsAccess.rows(restricted: false).last, .about, "« À propos et aide » reste la dernière ligne")
    }

    func testTheSubscriptionScreenLeavesWithTheLastMiff() {
        var path: [MiffRoute] = [.settings(.settings), .settings(.subscription), .miff("abc")]

        path.removeAll { $0.isMyInfoScreen || $0.isLanguageScreen || $0.isNotificationScreen || $0.isSubscriptionScreen }

        XCTAssertEqual(path, [.settings(.settings), .miff("abc")])
    }

    func testTheOfferOpenedFromTheSubscriptionScreenHasItsOwnFixedTrigger() {
        XCTAssertEqual(PlacePaywall.shown(for: .subscription), .paywallShown(trigger: .subscription))
        XCTAssertEqual(PlacePaywall.dismissed(for: .subscription), .paywallDismissed(trigger: .subscription))
        let event = PlacePaywall.shown(for: .subscription)
        XCTAssertEqual(event?.params as? [String: String], ["trigger": "subscription"], "aucun prix, aucun montant, aucun état")
    }

    func testWithThePurchaseOffTheOfferStaysComingSoonWithoutAnyDriver() {
        let display = PlacePaywallDisplay(reason: .limit, premium: nil)

        XCTAssertEqual(display.model.state, .comingSoon)
        XCTAssertNil(display.driver, "aucun pilote, aucun geste d'achat : « Miff+ arrive bientôt »")
    }

    func testWithThePurchaseOnEachDisplayGetsItsOwnDriverOnTheSameModel() {
        let repository = SubscriptionRepository(store: StubSubscriptionStore(), currentUid: { "uid-1" })
        let coordinator = PremiumCoordinator(
            gateway: StubGateway(), backend: StubBackend(), subscriptions: repository, currentUid: { "uid-1" }
        )

        let first = PlacePaywallDisplay(reason: .extraMiff, premium: coordinator)
        let second = PlacePaywallDisplay(reason: .extraMiff, premium: coordinator)

        XCTAssertNotNil(first.driver)
        XCTAssertTrue(first.model !== second.model, "un affichage = un modèle neuf (la croix de 3 secondes repart de zéro)")
    }

    func testTheServicesAreEmptyExactlyWhenThePurchaseIsOffInThisBuild() {
        let services = PremiumServices.make(currentUid: { nil })

        XCTAssertEqual(services.coordinator != nil, BuildFlavor.includesPremiumPurchase)
        XCTAssertEqual(services.subscriptions != nil, BuildFlavor.includesPremiumPurchase)
        XCTAssertEqual(services.purgeSubscriptionData != nil, BuildFlavor.includesPremiumPurchase)
    }
}
// swiftlint:enable line_length
