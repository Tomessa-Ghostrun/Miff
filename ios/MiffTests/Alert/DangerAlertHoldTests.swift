import XCTest
@testable import Miff

/// Le geste de maintien (ticket 7.2) : un appui court est une photo, l'armement à 400 ms, l'alerte à 5 s de plus, l'annulation silencieuse.
final class DangerAlertHoldTests: XCTestCase {
    func testThePhasesFollowTheTime() {
        XCTAssertEqual(DangerAlertHold.phase(heldMillis: 0), .pressing)
        XCTAssertEqual(DangerAlertHold.phase(heldMillis: 399), .pressing)
        XCTAssertEqual(DangerAlertHold.phase(heldMillis: 400), .holding(fraction: 0))
        XCTAssertEqual(DangerAlertHold.phase(heldMillis: 400 + 2_500), .holding(fraction: 0.5))
        XCTAssertEqual(DangerAlertHold.phase(heldMillis: 400 + 4_999), .holding(fraction: 4_999.0 / 5_000.0))
        XCTAssertEqual(DangerAlertHold.phase(heldMillis: 400 + 5_000), .done)
    }

    func testTheNumbersAreTheTicketsOnes() {
        XCTAssertEqual(DangerAlertRules.armMillis, 400)
        XCTAssertEqual(DangerAlertRules.holdMillis, 5_000)
        XCTAssertEqual(DangerAlertRules.holdSlopPoints, 44)
    }

    func testAFingerDriftingMoreThanTheSlopCancels() {
        XCTAssertFalse(DangerAlertHold.movedTooFar(dx: 30, dy: 30))
        XCTAssertTrue(DangerAlertHold.movedTooFar(dx: 40, dy: 40))
        XCTAssertTrue(DangerAlertHold.movedTooFar(dx: 0, dy: -45))
    }

    func testAShortTouchIsATapAndNothingIsArmed() {
        var tracker = DangerHoldTracker()
        tracker.begin(atMillis: 1_000)

        XCTAssertEqual(tracker.advance(nowMillis: 1_200), .nothing)
        XCTAssertEqual(tracker.release(nowMillis: 1_300), .tap)
        XCTAssertFalse(tracker.isTouching)
    }

    func testHoldingPastTheArmingTimeArmsOnceThenReleasingCancelsSilently() {
        var tracker = DangerHoldTracker()
        tracker.begin(atMillis: 0)

        XCTAssertEqual(tracker.advance(nowMillis: 400), .armed)
        XCTAssertEqual(tracker.advance(nowMillis: 500), .nothing, "armé UNE fois")
        XCTAssertTrue(tracker.armed)
        XCTAssertEqual(tracker.progress(nowMillis: 400 + 2_500), 0.5, accuracy: 0.0001)
        XCTAssertEqual(tracker.release(nowMillis: 3_000), .cancelled)
        XCTAssertFalse(tracker.armed)
    }

    func testFiveSecondsHeldFinishAndNothingFiresBefore() {
        var tracker = DangerHoldTracker()
        tracker.begin(atMillis: 0)
        _ = tracker.advance(nowMillis: 400)

        XCTAssertEqual(tracker.advance(nowMillis: 400 + 4_999), .nothing)
        XCTAssertEqual(tracker.advance(nowMillis: 400 + 5_000), .done)
        XCTAssertEqual(tracker.advance(nowMillis: 400 + 6_000), .nothing, "une seule fin")
        XCTAssertEqual(tracker.release(nowMillis: 9_000), .nothing)
    }

    func testCancellingBeforeTheArmingIsNeitherAPhotoNorAnAlert() {
        var tracker = DangerHoldTracker()
        tracker.begin(atMillis: 0)

        XCTAssertEqual(tracker.cancel(), .nothing)
        XCTAssertEqual(tracker.release(nowMillis: 100), .nothing)
    }

    func testCancellingAnArmedGestureIsSilentButReported() {
        var tracker = DangerHoldTracker()
        tracker.begin(atMillis: 0)
        _ = tracker.advance(nowMillis: 1_000)

        XCTAssertEqual(tracker.cancel(), .cancelled)
        XCTAssertFalse(tracker.isTouching)
    }

    func testTheProgressIsZeroUntilArmed() {
        var tracker = DangerHoldTracker()
        tracker.begin(atMillis: 0)

        XCTAssertEqual(tracker.progress(nowMillis: 200), 0)
    }
}
