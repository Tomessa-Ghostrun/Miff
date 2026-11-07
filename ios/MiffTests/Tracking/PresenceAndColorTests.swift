import XCTest
@testable import Miff

/// La machine de présence (ticket §6.6, critères DET2, DET3, STA1) et la fonction de la couleur (ticket §6.9, critères STA6, STA9).
final class PresenceMachineTests: XCTestCase {
    private let trusted = ZoneRef(key: "k-trusted", placeId: "p1", type: .trusted)
    private let other = ZoneRef(key: "k-other", placeId: "p2", type: .trusted)
    private let danger = ZoneRef(key: "k-danger", placeId: "r1", type: .risk)

    private func transition(_ kind: TransitionKind, _ key: String, at: Int = 1_000) -> ZoneTransition {
        ZoneTransition(kind: kind, zoneKey: key, atMillis: at)
    }

    func testNoZoneMeansOutAndTrustedWinsOverDanger() {
        XCTAssertEqual(PresenceMachine.derive(zones: [trusted, danger], inside: [:]).presence, .out)
        XCTAssertEqual(
            PresenceMachine.derive(zones: [trusted, danger], inside: ["k-danger": 5]),
            DerivedPresence(presence: .inRisk, placeId: "r1")
        )
        XCTAssertEqual(
            PresenceMachine.derive(zones: [trusted, danger], inside: ["k-danger": 9, "k-trusted": 5]),
            DerivedPresence(presence: .inTrusted, placeId: "p1")
        )
    }

    func testTheLastEnteredZoneOfTheSameTypeIsNamed() {
        let derived = PresenceMachine.derive(zones: [trusted, other], inside: ["k-trusted": 5, "k-other": 9])

        XCTAssertEqual(derived, DerivedPresence(presence: .inTrusted, placeId: "p2"))
    }

    func testArrivingInATrustedZoneWritesInTrustedAndMovesTheLastUpdate() throws {
        let change = try XCTUnwrap(PresenceMachine.onTransition(
            transition(.enter, "k-trusted", at: 7_000),
            zones: [trusted],
            insideAfter: ["k-trusted": 7_000],
            last: nil
        ))

        XCTAssertEqual(change.fact.presence, .inTrusted)
        XCTAssertEqual(change.fact.placeId, "p1")
        XCTAssertEqual(change.fact.presenceAtMillis, 7_000)
        XCTAssertTrue(change.fact.setLastUpdate)
    }

    func testLeavingNamesTheZoneThatWasLeftAndDoesNotTouchTheLastUpdate() throws {
        let change = try XCTUnwrap(PresenceMachine.onTransition(
            transition(.exit, "k-trusted", at: 9_000),
            zones: [trusted],
            insideAfter: [:],
            last: DerivedPresence(presence: .inTrusted, placeId: "p1")
        ))

        XCTAssertEqual(change.fact.presence, .out)
        XCTAssertEqual(change.fact.placeId, "p1")
        XCTAssertFalse(change.fact.setLastUpdate)
    }

    func testLeavingOneZoneWhileStayingInAnotherStaysInTrustedOnTheOther() throws {
        let change = try XCTUnwrap(PresenceMachine.onTransition(
            transition(.exit, "k-trusted"),
            zones: [trusted, other],
            insideAfter: ["k-other": 3],
            last: DerivedPresence(presence: .inTrusted, placeId: "p1")
        ))

        XCTAssertEqual(change.derived, DerivedPresence(presence: .inTrusted, placeId: "p2"))
    }

    func testEnteringDangerWhileInTrustedChangesNothing() {
        let change = PresenceMachine.onTransition(
            transition(.enter, "k-danger"),
            zones: [trusted, danger],
            insideAfter: ["k-trusted": 1, "k-danger": 2],
            last: DerivedPresence(presence: .inTrusted, placeId: "p1")
        )

        XCTAssertNil(change)
    }

    func testLeavingTrustedWhileInDangerIsInRisk() throws {
        let change = try XCTUnwrap(PresenceMachine.onTransition(
            transition(.exit, "k-trusted"),
            zones: [trusted, danger],
            insideAfter: ["k-danger": 2],
            last: DerivedPresence(presence: .inTrusted, placeId: "p1")
        ))

        XCTAssertEqual(change.derived, DerivedPresence(presence: .inRisk, placeId: "r1"))
        XCTAssertFalse(change.fact.setLastUpdate)
    }

    func testAPlaceOfAnotherMiffChangesNothingForThisOne() {
        let change = PresenceMachine.onTransition(
            transition(.enter, "k-elsewhere"),
            zones: [trusted],
            insideAfter: ["k-elsewhere": 1],
            last: nil
        )

        XCTAssertNil(change)
    }

    func testEnteringTheZoneTheStatusAlreadyNamesChangesNothing() {
        let change = PresenceMachine.onTransition(
            transition(.enter, "k-trusted"),
            zones: [trusted],
            insideAfter: ["k-trusted": 1],
            last: DerivedPresence(presence: .inTrusted, placeId: "p1")
        )

        XCTAssertNil(change)
    }

    func testAMissedExitBecomesUnknownNeverOut() throws {
        let change = try XCTUnwrap(PresenceMachine.onMissedExit(
            zoneKey: "k-trusted",
            zones: [trusted],
            insideAfter: [:],
            last: DerivedPresence(presence: .inTrusted, placeId: "p1"),
            atMillis: 5_000
        ))

        XCTAssertEqual(change.derived, DerivedPresence(presence: .unknown, placeId: nil))
        XCTAssertEqual(change.fact.presence, .unknown)
        XCTAssertNil(change.fact.placeId)
        XCTAssertFalse(change.fact.setLastUpdate)
    }

    func testAMissedExitWhileStillInAnotherZoneNamesThatZone() throws {
        let change = try XCTUnwrap(PresenceMachine.onMissedExit(
            zoneKey: "k-trusted",
            zones: [trusted, other],
            insideAfter: ["k-other": 3],
            last: DerivedPresence(presence: .inTrusted, placeId: "p1"),
            atMillis: 5_000
        ))

        XCTAssertEqual(change.derived, DerivedPresence(presence: .inTrusted, placeId: "p2"))
        XCTAssertEqual(change.fact.presenceAtMillis, 5_000)
    }

    func testAMissedExitWhenNothingWasSaidInsideChangesNothing() {
        let outside = DerivedPresence(presence: .out, placeId: "p1")
        let unknown = DerivedPresence(presence: .unknown, placeId: nil)
        for last in [nil, outside, unknown] {
            XCTAssertNil(PresenceMachine.onMissedExit(
                zoneKey: "k-trusted",
                zones: [trusted],
                insideAfter: [:],
                last: last,
                atMillis: 1
            ))
        }
    }
}

/// Les bornes de la couleur : 59 min 59 s, 1 h, 2 h, 2 h 00 min 01 s (critère STA6) ; mêmes cas qu'Android.
final class StatusColorTests: XCTestCase {
    private let minute = 60 * 1000
    private let second = 1000
    private let reference = 10_000_000

    private func facts(
        presence: PresenceValue?,
        presenceAt: Int? = 10_000_000,
        lastUpdateAt: Int? = nil,
        sharing: Bool = true
    ) -> MemberStatusFacts {
        MemberStatusFacts(
            sharingEnabled: sharing,
            presence: presence,
            presenceAtMillis: presenceAt,
            lastUpdateAtMillis: lastUpdateAt
        )
    }

    private func level(_ facts: MemberStatusFacts?, after elapsed: Int) -> StatusLevel {
        StatusColor.level(facts, nowMillis: reference + elapsed)
    }

    func testTheThresholdsAreOneHourAndTwoHoursInOnePlace() {
        XCTAssertEqual(StatusThresholds.orangeAfterMillis, 60 * minute)
        XCTAssertEqual(StatusThresholds.redAfterMillis, 120 * minute)
    }

    func testAfterADepartureGreenUntilOneHourThenOrangeThenRed() {
        let left = facts(presence: .out)

        XCTAssertEqual(level(left, after: 0), .green)
        XCTAssertEqual(level(left, after: 59 * minute + 59 * second), .green)
        XCTAssertEqual(level(left, after: 60 * minute), .orange)
        XCTAssertEqual(level(left, after: 120 * minute), .orange)
        XCTAssertEqual(level(left, after: 120 * minute + second), .red)
    }

    func testTheReferenceIsTheMostRecentOfTheDepartureAndTheLastUpdate() {
        let updated = facts(presence: .out, presenceAt: reference, lastUpdateAt: reference + 90 * minute)

        XCTAssertEqual(level(updated, after: 100 * minute), .green)
        XCTAssertEqual(level(updated, after: 150 * minute), .orange)
        XCTAssertEqual(level(updated, after: 211 * minute), .red)
    }

    func testInATrustedZoneAlwaysGreenInADangerZoneAlwaysRed() {
        XCTAssertEqual(level(facts(presence: .inTrusted), after: 10 * 60 * minute), .green)
        XCTAssertEqual(level(facts(presence: .inRisk), after: 0), .red)
    }

    func testNeutralWhenSharingStoppedNoStatusUnknownOrNothingToMeasure() {
        XCTAssertEqual(level(facts(presence: .out, sharing: false), after: 0), .neutral)
        XCTAssertEqual(level(nil, after: 0), .neutral)
        XCTAssertEqual(level(facts(presence: .unknown), after: 5 * 60 * minute), .neutral)
        XCTAssertEqual(level(facts(presence: nil), after: 0), .neutral)
        XCTAssertEqual(level(facts(presence: .out, presenceAt: nil, lastUpdateAt: nil), after: 0), .neutral)
    }

    func testAReferenceInTheFutureCountsAsNow() {
        XCTAssertEqual(level(facts(presence: .out, presenceAt: reference + 3 * 60 * minute), after: 0), .green)
    }

    func testStatusFactsHideTheirContentInADescription() {
        XCTAssertEqual("\(facts(presence: .out))", "MemberStatusFacts(redacted)")
    }
}
