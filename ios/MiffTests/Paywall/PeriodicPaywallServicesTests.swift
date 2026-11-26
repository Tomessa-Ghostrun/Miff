// Quelques assertions portent un message en clair : elles dépassent la limite de ligne.
// swiftlint:disable line_length
import UIKit
import XCTest
@testable import Miff

/// L'offre périodique branchée sur la vie de l'app (critères PP1, PP2, PP3, PP6) : une « ouverture » est le moment où l'app DEVIENT ACTIVE devant une personne (démarrage à
/// froid, ou retour après 30 minutes ou plus) ; un réveil en arrière-plan par la surveillance des lieux ne rend jamais l'app active et ne compte pas ; la déconnexion et la
/// suppression du compte effacent le compteur. Un centre de notifications PRIVÉ et une horloge à la main : rien n'est lu sur l'appareil ni attendu.
@MainActor
final class PeriodicPaywallServicesTests: XCTestCase {
    private let center = NotificationCenter()
    private let clock = PeriodicClockBox()
    private let store = MemoryPeriodicStore()

    private func makeServices(counter: Int = 0, observes: Bool = true) -> PeriodicPaywallServices {
        store.value = counter
        let box = clock
        return PeriodicPaywallServices(store: store, clock: { box.now }, observesLifecycle: observes, center: center)
    }

    private func becomeActive() {
        center.post(name: UIApplication.didBecomeActiveNotification, object: nil)
    }

    private func enterBackground() {
        center.post(name: UIApplication.didEnterBackgroundNotification, object: nil)
    }

    func testAColdStartThatBecomesActiveWithTheSignedInAppCountsOne() {
        let services = makeServices(counter: 4)

        becomeActive()
        services.rules.onShellShown()

        XCTAssertEqual(store.value, 5)
    }

    func testAWakeUpInTheBackgroundThatNeverBecomesActiveCountsNothing() {
        // La surveillance des lieux relance le processus : aucune fenêtre, l'app n'est jamais active (PP1).
        let services = makeServices(counter: 4)

        services.rules.onShellShown()

        XCTAssertEqual(store.value, 4)
    }

    func testTheControlCenterOrASystemAlertIsNotAnOpening() {
        // « inactive » puis « active » sans passage par l'arrière-plan : le système rappelle seulement que l'app est active.
        let services = makeServices()
        becomeActive()
        services.rules.onShellShown()

        becomeActive()
        becomeActive()

        XCTAssertEqual(store.value, 1)
    }

    func testAReturnAfterThirtyMinutesCountsAndAShorterOneDoesNot() {
        let services = makeServices()
        becomeActive()
        services.rules.onShellShown()

        enterBackground()
        clock.advance(PeriodicPaywallDefaults.backgroundMillis - 1)
        becomeActive()
        XCTAssertEqual(store.value, 1)

        enterBackground()
        clock.advance(PeriodicPaywallDefaults.backgroundMillis)
        becomeActive()
        XCTAssertEqual(store.value, 2)
    }

    func testTheSignOutAndTheDeletionOfTheAccountEraseTheCounter() async {
        let services = makeServices(counter: 6)
        becomeActive()
        services.rules.onShellShown()
        XCTAssertEqual(store.value, 7)

        for teardown in services.teardowns {
            await teardown()
        }

        XCTAssertEqual(store.value, 0)
        XCTAssertEqual(store.clears, 1)
    }

    func testTheRulesHaveTheThresholdOfThePmAndAreOn() {
        let services = makeServices()

        XCTAssertTrue(services.rules.isOn)
        XCTAssertEqual(services.rules.thresholdForDiagnostic, 10)
    }

    func testTheBeaconMovesWhenTheRulesChange() async throws {
        let services = makeServices()
        let before = services.beacon.tick

        services.rules.onShellShown()
        for _ in 0..<50 where services.beacon.tick == before {
            try await Task.sleep(nanoseconds: 20_000_000)
        }

        XCTAssertGreaterThan(services.beacon.tick, before)
    }

    func testWithoutLifecycleObserversNothingListensToTheNotificationCenter() {
        let services = makeServices(observes: false)

        becomeActive()
        services.rules.onShellShown()

        XCTAssertEqual(store.value, 0, "l'ouverture n'a jamais commencé : aucun observateur")
    }

    func testTheAppEnvironmentOwnsTheServicesAndClearsThemWithTheSession() throws {
        let source = try String(
            contentsOf: URL(fileURLWithPath: #filePath)
                .deletingLastPathComponent() // Paywall
                .deletingLastPathComponent() // MiffTests
                .deletingLastPathComponent() // ios
                .appendingPathComponent("Miff/App/AppEnvironment.swift"),
            encoding: .utf8
        )

        XCTAssertTrue(source.contains("let periodic = PeriodicPaywallServices()"))
        XCTAssertTrue(source.contains("allTeardowns += periodic.teardowns"), "effacé à la déconnexion et à la suppression du compte")
        XCTAssertTrue(source.contains("pushServices.delegate.onAlertOpened"), "la session ouverte par une alerte est exclue (P11)")
    }
}
