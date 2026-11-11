import XCTest
@testable import Miff

/// `StatusColor.changeInstants` (ticket Notifications §20.2) : le contrat avec la couleur. À chaque instant rendu le niveau CHANGE
/// (`level(t - 1 ms) != level(t)`), il est CONSTANT entre deux instants, et c'est la seule source des durées (aucun 2 h ni 4 h recopié).
final class ChangeInstantsTests: XCTestCase {
    private let ref = 1_800_000_000_000
    private let hour = 3_600_000

    private func facts(
        _ presence: PresenceValue?,
        presenceAt: Int? = nil,
        update: Int? = nil,
        sharing: Bool = true
    ) -> MemberStatusFacts {
        MemberStatusFacts(
            sharingEnabled: sharing, presence: presence, presenceAtMillis: presenceAt, lastUpdateAtMillis: update
        )
    }

    private func fixtures() -> [(String, MemberStatusFacts?)] {
        [
            ("sorti", facts(.out, presenceAt: ref)),
            ("sorti, update plus récente", facts(.out, presenceAt: ref, update: ref + hour)),
            ("sorti, update plus ancienne", facts(.out, presenceAt: ref, update: ref - 5 * hour)),
            ("sorti, update seule", facts(.out, update: ref)),
            ("zone de danger, update récente", facts(.inRisk, presenceAt: ref - hour, update: ref)),
            (
                "zone de danger, update ancienne mais postérieure à l'entrée",
                facts(.inRisk, presenceAt: ref - 5 * hour, update: ref - 3 * hour)
            ),
            ("zone de danger, update avant l'entrée", facts(.inRisk, presenceAt: ref, update: ref - 3 * hour)),
            ("zone de danger, update à l'instant d'entrée", facts(.inRisk, presenceAt: ref, update: ref)),
            ("zone de danger, entrée sans heure", facts(.inRisk, update: ref)),
            ("zone de danger, jamais d'update", facts(.inRisk, presenceAt: ref)),
            ("zone de confiance", facts(.inTrusted, presenceAt: ref, update: ref)),
            ("inconnu", facts(.unknown, presenceAt: ref)),
            ("partage arrêté", facts(.out, presenceAt: ref, sharing: false)),
            ("sans repère", facts(.out)),
            ("sans présence", facts(nil, presenceAt: ref)),
            ("sans statut", nil)
        ]
    }

    func testTheLevelChangesAtEveryInstantAndIsConstantBetweenTwoInstants() {
        for (name, value) in fixtures() {
            let instants = StatusColor.changeInstants(value)
            XCTAssertEqual(instants, instants.sorted(), name)
            for instant in instants {
                XCTAssertNotEqual(
                    StatusColor.level(value, nowMillis: instant - 1),
                    StatusColor.level(value, nowMillis: instant),
                    "\(name) : le niveau doit changer à \(instant)"
                )
            }
            // Constant entre deux instants : des points avant le premier, entre chaque paire et après le dernier.
            let edges = [ref - 10 * hour] + instants + [ref + 10 * hour]
            for (low, high) in zip(edges, edges.dropFirst()) where high - low > 2 {
                let first = StatusColor.level(value, nowMillis: low + 1)
                let middle = StatusColor.level(value, nowMillis: (low + high) / 2)
                let last = StatusColor.level(value, nowMillis: high - 1)
                XCTAssertEqual(first, middle, "\(name) entre \(low) et \(high)")
                XCTAssertEqual(middle, last, "\(name) entre \(low) et \(high)")
            }
        }
    }

    func testOutGivesOrangeAfterMoreThanTwoHoursAndRedAfterMoreThanFourHours() {
        let instants = StatusColor.changeInstants(facts(.out, presenceAt: ref))

        XCTAssertEqual(
            instants,
            [ref + StatusThresholds.orangeAfterMillis + 1, ref + StatusThresholds.redAfterMillis + 1]
        )
        XCTAssertEqual(StatusColor.level(facts(.out, presenceAt: ref), nowMillis: instants[0] - 1), .green)
        XCTAssertEqual(StatusColor.level(facts(.out, presenceAt: ref), nowMillis: instants[0]), .orange)
        XCTAssertEqual(StatusColor.level(facts(.out, presenceAt: ref), nowMillis: instants[1] - 1), .orange)
        XCTAssertEqual(StatusColor.level(facts(.out, presenceAt: ref), nowMillis: instants[1]), .red)
    }

    func testTheReferenceIsTheMostRecentOfTheDepartureAndTheLastUpdate() {
        let instants = StatusColor.changeInstants(facts(.out, presenceAt: ref, update: ref + hour))

        XCTAssertEqual(instants.first, ref + hour + StatusThresholds.orangeAfterMillis + 1)
    }

    func testInADangerZoneItIsGreenOnlyAfterAnUpdateFollowingTheEntryAndRedMoreThanAnHourLater() {
        let entered = ref - hour
        XCTAssertEqual(
            StatusColor.changeInstants(facts(.inRisk, presenceAt: entered, update: ref)),
            [ref + StatusThresholds.dangerZoneRedAfterMillis + 1]
        )
        // Rouge depuis l'entrée, sans changement à attendre : pas d'update, update antérieure ou égale à l'entrée, entrée sans heure.
        XCTAssertEqual(StatusColor.changeInstants(facts(.inRisk, presenceAt: ref)), [])
        XCTAssertEqual(StatusColor.changeInstants(facts(.inRisk, presenceAt: ref, update: ref)), [])
        XCTAssertEqual(StatusColor.changeInstants(facts(.inRisk, presenceAt: ref, update: ref - hour)), [])
        XCTAssertEqual(StatusColor.changeInstants(facts(.inRisk, update: ref)), [])
        XCTAssertEqual(StatusColor.level(facts(.inRisk, presenceAt: ref), nowMillis: ref), .red)
    }

    func testNothingChangesInATrustedZoneOrWithoutSharingOrStatus() {
        XCTAssertEqual(StatusColor.changeInstants(facts(.inTrusted, presenceAt: ref, update: ref)), [])
        XCTAssertEqual(StatusColor.changeInstants(facts(.unknown, presenceAt: ref)), [])
        XCTAssertEqual(StatusColor.changeInstants(facts(.out, presenceAt: ref, sharing: false)), [])
        XCTAssertEqual(StatusColor.changeInstants(nil), [])
    }
}
