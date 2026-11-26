import XCTest
@testable import Miff

/// Ce qui est un appui et ce qui n'en est pas (critère PP8) : un doigt posé et relevé au même endroit. Un défilement, un balayage, deux doigts : jamais. Miroir de
/// `PeriodicPaywallTouchesTest` d'Android, sans la partie « un bouton a pris l'appui » (iOS ne peut pas la voir : écart listé dans la PR).
final class PeriodicTapTrackerTests: XCTestCase {
    func testOneFingerDownAndUpOnTheSameSpotIsATap() {
        var tracker = PeriodicTapTracker()

        tracker.began(fingers: 1, x: 100, y: 200)

        XCTAssertTrue(tracker.ended(fingers: 1, x: 100, y: 200))
    }

    func testASmallWobbleWithinTheSlopIsStillATap() {
        var tracker = PeriodicTapTracker()

        tracker.began(fingers: 1, x: 100, y: 200)
        tracker.moved(fingers: 1, x: 104, y: 203)

        XCTAssertTrue(tracker.ended(fingers: 1, x: 105, y: 204))
    }

    func testAScrollOrASwipeIsNeverATap() {
        var tracker = PeriodicTapTracker()

        tracker.began(fingers: 1, x: 100, y: 200)
        tracker.moved(fingers: 1, x: 100, y: 260)

        XCTAssertFalse(tracker.ended(fingers: 1, x: 100, y: 260))
    }

    func testAFingerThatTravelsFarAndComesBackIsNotATap() {
        var tracker = PeriodicTapTracker()

        tracker.began(fingers: 1, x: 100, y: 200)
        tracker.moved(fingers: 1, x: 100, y: 300)
        tracker.moved(fingers: 1, x: 100, y: 200)

        XCTAssertFalse(tracker.ended(fingers: 1, x: 100, y: 200))
    }

    func testTwoFingersAreNeverATap() {
        var tracker = PeriodicTapTracker()

        tracker.began(fingers: 1, x: 100, y: 200)
        tracker.began(fingers: 2, x: 140, y: 200)

        XCTAssertFalse(tracker.ended(fingers: 2, x: 140, y: 200))
        XCTAssertFalse(tracker.ended(fingers: 1, x: 100, y: 200), "le premier doigt qui reste ne refait pas un appui")
    }

    func testASecondFingerThatArrivesWhileMovingCancelsTheTap() {
        var tracker = PeriodicTapTracker()

        tracker.began(fingers: 1, x: 100, y: 200)
        tracker.moved(fingers: 2, x: 100, y: 200)

        XCTAssertFalse(tracker.ended(fingers: 1, x: 100, y: 200))
    }

    func testACancelledTouchIsNotATap() {
        var tracker = PeriodicTapTracker()

        tracker.began(fingers: 1, x: 100, y: 200)
        tracker.cancelled()

        XCTAssertFalse(tracker.ended(fingers: 1, x: 100, y: 200))
    }

    func testAnEndWithoutABeginIsNotATap() {
        var tracker = PeriodicTapTracker()

        XCTAssertFalse(tracker.ended(fingers: 1, x: 0, y: 0))
    }

    func testTwoTapsInARowAreTwoTaps() {
        var tracker = PeriodicTapTracker()

        tracker.began(fingers: 1, x: 10, y: 10)
        XCTAssertTrue(tracker.ended(fingers: 1, x: 10, y: 10))
        tracker.began(fingers: 1, x: 300, y: 400)

        XCTAssertTrue(tracker.ended(fingers: 1, x: 300, y: 400))
    }
}
