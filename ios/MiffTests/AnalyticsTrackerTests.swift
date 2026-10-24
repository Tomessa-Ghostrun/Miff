import XCTest
@testable import Miff

/// Le suiveur applique la liste blanche d'abord, puis le consentement de l'utilisateur.
final class AnalyticsTrackerTests: XCTestCase {
    func testTrackerSendsNothingWhileAnalyticsIsOff() {
        let sink = RecordingSink()
        let tracker = AnalyticsTracker(sink: sink, isEnabled: { false })

        XCTAssertEqual(tracker.track(.miffCreated(isFirstMiff: false)), .disabled)
        XCTAssertTrue(sink.logged.isEmpty)
    }

    func testTrackerSendsAValidEventWhenAnalyticsIsOn() {
        let sink = RecordingSink()
        let tracker = AnalyticsTracker(sink: sink, isEnabled: { true })

        XCTAssertEqual(tracker.track(.miffCreated(isFirstMiff: true)), .sent)
        XCTAssertEqual(sink.logged, ["miff_created"])
    }

    func testTrackerNeverSendsAPositionEvenWhenAnalyticsIsOn() {
        let sink = RecordingSink()
        let tracker = AnalyticsTracker(sink: sink, isEnabled: { true })

        let result = tracker.track(AnalyticsContract.eventDiagnosticTest, params: ["lat": 1, "lng": 2])

        XCTAssertEqual(result, .refused(reason: .locationLikeName, detail: "lat"))
        XCTAssertTrue(sink.logged.isEmpty)
    }

    func testTrackerReportsRefusalEvenWhileAnalyticsIsOff() {
        let tracker = AnalyticsTracker(sink: RecordingSink(), isEnabled: { false })

        let result = tracker.track("miff_created", params: ["address": "x"])

        XCTAssertEqual(result, .refused(reason: .locationLikeName, detail: "address"))
    }

    func testTrackerReadsTheConsentAtEachCall() {
        let sink = RecordingSink()
        var enabled = false
        let tracker = AnalyticsTracker(sink: sink, isEnabled: { enabled })

        tracker.track(.diagnosticTest)
        enabled = true
        tracker.track(.diagnosticTest)
        enabled = false
        tracker.track(.diagnosticTest)

        XCTAssertEqual(sink.logged, ["analytics_diagnostic_test"])
    }
}
