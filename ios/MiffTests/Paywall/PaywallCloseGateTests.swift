import XCTest
@testable import Miff

/// La règle de la croix différée, sans écran (ticket `paywall-v2.md` §6 ; critères CR1 à CR5, CR12). L'horloge est celle du test :
/// aucun test n'attend réellement 3 secondes.
final class PaywallCloseGateTests: XCTestCase {
    func testTheDelayIsThreeSecondsAndWrittenOnce() {
        XCTAssertEqual(PaywallTiming.closeDelay, 3, "décision du PM du 2026-11-12")
        XCTAssertEqual(PaywallCloseGate().delay, PaywallTiming.closeDelay, "la valeur par défaut est la valeur unique")
    }

    func testTheCrossDoesNotExistBeforeTheScreenIsDisplayed() {
        var gate = PaywallCloseGate(delay: 3)

        XCTAssertFalse(gate.isRevealed)
        XCTAssertFalse(gate.refresh(at: 1_000), "sans affichage il n'y a pas de compte à rebours")
        XCTAssertFalse(gate.isRevealed)
    }

    func testItIsAbsentAtTwoPointNineSecondsAndPresentAtThree() {
        var gate = PaywallCloseGate(delay: 3)
        gate.start(at: 10)

        XCTAssertFalse(gate.refresh(at: 12.9))
        XCTAssertFalse(gate.isRevealed)
        XCTAssertTrue(gate.refresh(at: 13), "à 3,0 s la croix apparaît, et on le dit une seule fois")
        XCTAssertTrue(gate.isRevealed)
    }

    func testTheCountdownStartsOnceAndNeverRestarts() {
        var gate = PaywallCloseGate(delay: 3)
        gate.start(at: 5)
        gate.start(at: 7)
        gate.start(at: 100)

        XCTAssertEqual(gate.shownAt, 5, "revenir à l'écran (autre fenêtre, arrière-plan) ne relance pas le délai")
        XCTAssertEqual(gate.remaining(at: 6), 2, accuracy: 0.0001)
    }

    func testOnceRevealedTheCrossStaysWhateverHappensNext() {
        var gate = PaywallCloseGate(delay: 3)
        gate.start(at: 0)
        gate.refresh(at: 3)

        XCTAssertFalse(gate.refresh(at: 3.5), "révélée une seule fois : rien à annoncer de plus")
        gate.start(at: 50)
        XCTAssertTrue(gate.isRevealed, "rotation, taille de texte, thème, arrière-plan : la croix ne se cache plus")
        XCTAssertEqual(gate.remaining(at: 51), 0)
    }

    func testTimeSpentInTheBackgroundCounts() {
        var gate = PaywallCloseGate(delay: 3)
        gate.start(at: 0)

        // L'app passe en arrière-plan à 1 s et revient à 60 s : 3 s se sont écoulées.
        XCTAssertTrue(gate.refresh(at: 60))
    }

    func testRemainingTimeGoesDownToZeroButNeverNegative() {
        var gate = PaywallCloseGate(delay: 3)
        XCTAssertEqual(gate.remaining(at: 0), 3, "avant l'affichage : le délai entier")
        gate.start(at: 0)
        XCTAssertEqual(gate.remaining(at: 1), 2, accuracy: 0.0001)
        XCTAssertEqual(gate.remaining(at: 2.9), 0.1, accuracy: 0.0001)
        XCTAssertEqual(gate.remaining(at: 99), 0)
    }

    func testAZeroDelayDisablesTheRule() {
        var zero = PaywallCloseGate(delay: 0)

        XCTAssertTrue(zero.isRevealed, "délai 0 : la croix est là d'emblée (parade au risque de rejet des stores)")
        zero.start(at: 0)
        XCTAssertFalse(zero.refresh(at: 1), "rien à révéler, donc rien à annoncer")

        let negative = PaywallCloseGate(delay: -5)
        XCTAssertTrue(negative.isRevealed)
        XCTAssertEqual(negative.delay, 0)
    }

    func testTwoGatesShareNothing() {
        var first = PaywallCloseGate(delay: 3)
        first.start(at: 0)
        first.refresh(at: 10)
        var second = PaywallCloseGate(delay: 3)
        second.start(at: 10)

        XCTAssertTrue(first.isRevealed)
        XCTAssertFalse(second.isRevealed, "un nouvel affichage repart de zéro, même une seconde après le précédent")
    }

    func testTheSystemClockOnlyGoesForward() {
        let clock = SystemPaywallClock()
        let first = clock.now()
        let second = clock.now()

        XCTAssertGreaterThanOrEqual(first, 0)
        XCTAssertGreaterThanOrEqual(second, first)
    }
}
