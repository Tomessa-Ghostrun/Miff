// Quelques lignes appellent des fabriques à nombreux paramètres : elles dépassent la limite.
// swiftlint:disable line_length
import XCTest
@testable import Miff

/// L'ORDRE des vérifications de la transaction qui démarre une alerte (revue indépendante de l'Android, M1) : une alerte de moi déjà vivante est ADOPTÉE avant que la limite
/// du jour soit jugée ; un ré-armement trop rapproché attend sans rien consommer ; un compteur absent n'est jamais « plein ».
final class DangerAlertStartPlanTests: XCTestCase {
    private let now = 1_794_063_600_000

    private func doc(started: Int?, expires: Int, active: Bool = true, seq: Int = 1) -> DangerAlertDoc {
        DangerAlertDoc(uid: "me", active: active, startedAtMillis: started, expiresAtMillis: expires, seq: seq)
    }

    private func fullCounter() -> DangerAlertQuota {
        DangerAlertQuota(slots: [now - 10, now - 20, now - 30])
    }

    private func decide(
        card: Bool = true,
        sharing: Bool = true,
        alert: DangerAlertDoc? = nil,
        counter: DangerAlertQuota? = nil
    ) -> DangerStartDecision {
        DangerAlertStartPlan.decide(cardExists: card, sharing: sharing, alert: alert, counter: counter, nowMillis: now)
    }

    func testNotAMemberAndStoppedSharingComeFirst() {
        XCTAssertEqual(decide(card: false), .notMember)
        XCTAssertEqual(decide(sharing: false), .sharingStopped)
        XCTAssertEqual(decide(card: false, sharing: false), .notMember)
    }

    func testALiveAlertIsAdoptedBeforeTheDailyLimitIsJudged() {
        let live = doc(started: now - 1_000, expires: now + 100_000)

        XCTAssertEqual(decide(alert: live, counter: fullCounter()), .adopt(live), "la 3e alerte dont la réponse s'est perdue")
    }

    func testAnEndedOrExpiredAlertIsNotAdopted() {
        let ended = doc(started: now - 3_600_000, expires: now + 100_000, active: false)
        let expired = doc(started: now - 3_600_000, expires: now - 1)

        for old in [ended, expired] {
            XCTAssertEqual(decide(alert: old, counter: fullCounter()), .quotaFull)
        }
    }

    func testARearmingWithinAMinuteWaitsAndConsumesNothingEvenWithAFullCounter() {
        let recent = doc(started: now - 20_000, expires: now + 100_000, active: false)

        XCTAssertEqual(
            decide(alert: recent, counter: fullCounter()),
            .wait(waitMillis: 60_000 - 20_000 + DangerAlertRules.rearmSafetyMillis)
        )
    }

    func testAFullCounterRefusesTheStartAndAFreeOrStaleSlotIsReplaced() {
        XCTAssertEqual(decide(counter: fullCounter()), .quotaFull)
        XCTAssertEqual(decide(counter: DangerAlertQuota(slots: [now - 10, nil, now - 30])), .write(slot: 1, seq: 1))
    }

    func testAMissingCounterIsNeverFullItsCreationIsTheCredit() {
        XCTAssertEqual(decide(counter: nil), .write(slot: nil, seq: 1))
    }

    func testARearmingCarriesTheNextSeq() {
        let ended = doc(started: now - 3_600_000, expires: now + 100_000, active: false, seq: 4)

        XCTAssertEqual(decide(alert: ended, counter: DangerAlertQuota.empty), .write(slot: 0, seq: 5))
    }
}
