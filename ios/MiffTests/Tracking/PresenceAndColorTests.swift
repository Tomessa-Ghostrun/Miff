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

/// Les bornes de la couleur (décision du PM, 2026-11-08) : 1 h 59, 2 h 00, 2 h 01, 3 h 59, 4 h 00, 4 h 01 ; mêmes cas qu'Android.
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

    func testTheThresholdsAreTwoHoursAndFourHoursInOnePlace() {
        XCTAssertEqual(StatusThresholds.orangeAfterMillis, 120 * minute)
        XCTAssertEqual(StatusThresholds.redAfterMillis, 240 * minute)
    }

    func testAfterADepartureGreenUntilTwoHoursThenOrangeThenRedAfterFourHours() {
        let left = facts(presence: .out)

        XCTAssertEqual(level(left, after: 0), .green)
        XCTAssertEqual(level(left, after: 59 * minute), .green, "l'ancien seuil de 1 h ne change plus rien")
        XCTAssertEqual(level(left, after: 119 * minute), .green)
        XCTAssertEqual(level(left, after: 120 * minute), .green, "2 h 00 pile : « plus de 2 h » n'est pas atteint")
        XCTAssertEqual(level(left, after: 121 * minute), .orange)
        XCTAssertEqual(level(left, after: 239 * minute), .orange)
        XCTAssertEqual(level(left, after: 240 * minute), .orange, "4 h 00 pile : « plus de 4 h » n'est pas atteint")
        XCTAssertEqual(level(left, after: 240 * minute + second), .red)
        XCTAssertEqual(level(left, after: 241 * minute), .red)
    }

    func testTheReferenceIsTheMostRecentOfTheDepartureAndTheLastUpdate() {
        let updated = facts(presence: .out, presenceAt: reference, lastUpdateAt: reference + 90 * minute)

        // Orange seulement quand la sortie ET la dernière update ont plus de 2 h : la plus récente (1 h 30 après) fait foi.
        XCTAssertEqual(level(updated, after: 200 * minute), .green)
        XCTAssertEqual(level(updated, after: 211 * minute), .orange)
        XCTAssertEqual(level(updated, after: 330 * minute), .orange)
        XCTAssertEqual(level(updated, after: 331 * minute), .red)
    }

    func testAnOldDepartureWithARecentUpdateStaysGreen() {
        let updated = facts(presence: .out, presenceAt: reference - 10 * 60 * minute, lastUpdateAt: reference)

        XCTAssertEqual(level(updated, after: 119 * minute), .green)
        XCTAssertEqual(level(updated, after: 121 * minute), .orange)
    }

    func testInATrustedZoneAlwaysGreenWhateverTheAgeOfTheLastUpdate() {
        XCTAssertEqual(level(facts(presence: .inTrusted), after: 10 * 60 * minute), .green)
        // La zone de confiance l'emporte toujours (une personne dans les deux zones est `in_trusted`, jamais rouge).
        let staleUpdate = facts(presence: .inTrusted, lastUpdateAt: reference - 5 * 60 * minute)
        XCTAssertEqual(level(staleUpdate, after: 3 * 60 * minute), .green)
    }

    func testTheDangerZoneConstantIsOneHour() {
        XCTAssertEqual(StatusThresholds.dangerZoneRedAfterMillis, 60 * minute)
    }

    /// Décision du PM (2026-11-08, version finale) : entrée en zone de danger = rouge ; une update POSTÉRIEURE à l'entrée = vert 1 h
    /// (59:59, 1:00:00 encore vert, 1:00:01 rouge) ; nouvelle update = vert 1 h de plus.
    func testInADangerZoneGreenForOneHourAfterAnUpdatePostedAfterTheEntry() {
        let entry = reference - 30 * minute
        let updated = facts(presence: .inRisk, presenceAt: entry, lastUpdateAt: reference)

        XCTAssertEqual(level(updated, after: 0), .green, "update postérieure à l'entrée, toute fraîche")
        XCTAssertEqual(level(updated, after: 59 * minute + 59 * second), .green)
        XCTAssertEqual(level(updated, after: 60 * minute), .green, "1 h 00 pile : « plus d'1 h » n'est pas atteint")
        XCTAssertEqual(level(updated, after: 60 * minute + second), .red)
        XCTAssertEqual(level(updated, after: 5 * 60 * minute), .red)
    }

    func testASecondUpdateGivesAnotherHourOfGreen() {
        let entry = reference - 30 * minute
        let first = facts(presence: .inRisk, presenceAt: entry, lastUpdateAt: reference)
        let again = facts(presence: .inRisk, presenceAt: entry, lastUpdateAt: reference + 90 * minute)

        XCTAssertEqual(level(first, after: 61 * minute), .red)
        XCTAssertEqual(level(again, after: 91 * minute), .green)
        XCTAssertEqual(level(again, after: 150 * minute), .green)
        XCTAssertEqual(level(again, after: 150 * minute + second), .red)
    }

    func testEnteringADangerZoneIsRedAtOnceEvenAfterARecentUpdate() {
        // Update il y a 10 min, entrée en zone de danger maintenant : l'update est ANTÉRIEURE à l'entrée, elle ne compte pas.
        let enteredAfterUpdate = facts(presence: .inRisk, presenceAt: reference, lastUpdateAt: reference - 10 * minute)

        XCTAssertEqual(level(enteredAfterUpdate, after: 0), .red)
        XCTAssertEqual(level(enteredAfterUpdate, after: 5 * minute), .red)
    }

    func testAnUpdateAtTheSameInstantAsTheEntryIsNotLaterSoItIsRed() {
        let sameInstant = facts(presence: .inRisk, presenceAt: reference, lastUpdateAt: reference)
        let oneMillisecondLater = facts(presence: .inRisk, presenceAt: reference, lastUpdateAt: reference + 1)

        XCTAssertEqual(level(sameInstant, after: 0), .red)
        XCTAssertEqual(level(oneMillisecondLater, after: 1), .green)
    }

    func testInADangerZoneWithoutAnyUpdateOrWithoutEntryTimeItIsRed() {
        XCTAssertEqual(level(facts(presence: .inRisk, lastUpdateAt: nil), after: 0), .red)
        XCTAssertEqual(level(facts(presence: .inRisk, presenceAt: nil, lastUpdateAt: nil), after: 0), .red)
        XCTAssertEqual(level(facts(presence: .inRisk, presenceAt: nil, lastUpdateAt: reference), after: 0), .red)
    }

    func testInADangerZoneAnUpdateFromAWrongClockCountsAsNow() {
        let ahead = facts(presence: .inRisk, presenceAt: reference - minute, lastUpdateAt: reference + 3 * 60 * minute)

        XCTAssertEqual(level(ahead, after: 0), .green)
    }

    func testInADangerZoneItIsNeverOrange() {
        let updated = facts(presence: .inRisk, presenceAt: reference - minute, lastUpdateAt: reference)

        for elapsed in stride(from: 0, through: 6 * 60, by: 5) {
            XCTAssertNotEqual(level(updated, after: elapsed * minute), .orange)
        }
    }

    func testLeavingADangerZoneGoesBackToTheTwoAndFourHourRule() {
        // Sortie : `presence` = out, repère = max(sortie, dernière update) ; la règle d'1 h ne s'applique plus.
        let left = facts(presence: .out, presenceAt: reference, lastUpdateAt: reference - 30 * minute)

        XCTAssertEqual(level(left, after: 90 * minute), .green)
        XCTAssertEqual(level(left, after: 121 * minute), .orange)
    }

    func testSharingStoppedInADangerZoneStaysNeutral() {
        XCTAssertEqual(level(facts(presence: .inRisk, sharing: false), after: 0), .neutral)
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
