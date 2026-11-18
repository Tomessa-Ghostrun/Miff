// Quelques lignes appellent des fabriques à nombreux paramètres : elles dépassent la limite.
// swiftlint:disable line_length
import XCTest
@testable import Miff

/// Les lots qui arrêtent le partage, quittent, retirent un membre ou suppriment la Miff (docs/tickets/alerte-danger.md §10, AD41 ; backend/data-model.md §2.18) : avec la fonction allumée
/// ils suppriment AUSSI l'alerte de CETTE Miff dans le même lot (les règles l'exigent) ; éteinte, rien n'est ajouté (les règles en production ne connaissent pas ce chemin). Aucun
/// de ces lots ne touche une autre Miff.
final class DangerAlertLotsTests: XCTestCase {
    private let miffId = "abcdefghijklmnopqrst"
    private let uid = "user1"
    private let alertPath = "miffs/abcdefghijklmnopqrst/alerts/user1"

    private func paths(_ writes: [DocWrite]) -> [String] {
        writes.map { $0.path }
    }

    func testStoppingTheSharingAddsTheAlertDeletionOnlyWhenAsked() {
        let plain = MiffWrites.stopSharing(miffId: miffId, uid: uid)
        let withAlert = MiffWrites.stopSharing(miffId: miffId, uid: uid, withAlert: true)

        XCTAssertEqual(plain.count, 4)
        XCTAssertFalse(paths(plain).contains(alertPath))
        XCTAssertEqual(withAlert.count, 5)
        XCTAssertEqual(withAlert.last, DocWrite(path: alertPath, kind: .delete))
        XCTAssertEqual(Array(withAlert.dropLast()), plain)
    }

    func testLeavingAddsItToo() {
        let plain = MiffWrites.leave(miffId: miffId, uid: uid, membershipKey: "free")
        let withAlert = MiffWrites.leave(miffId: miffId, uid: uid, membershipKey: "free", withAlert: true)

        XCTAssertFalse(paths(plain).contains(alertPath))
        XCTAssertEqual(withAlert.count, plain.count + 1)
        XCTAssertTrue(paths(withAlert).contains(alertPath))
        XCTAssertEqual(withAlert.filter { $0.kind == .delete }.count, withAlert.count, "un lot « Quitter » ne fait que supprimer")
    }

    func testRemovingAMemberDeletesHisAlertInTheSameLot() {
        let old = JoinCode.generate()
        var fresh = JoinCode.generate()
        while fresh == old {
            fresh = JoinCode.generate()
        }
        let plain = MiffWrites.removeMember(
            miffId: miffId, targetUid: "target", name: "Chez nous", oldCode: old, newCode: fresh
        )
        let withAlert = MiffWrites.removeMember(
            miffId: miffId, targetUid: "target", name: "Chez nous", oldCode: old, newCode: fresh, withAlert: true
        )

        XCTAssertEqual(plain.count, 7)
        XCTAssertEqual(withAlert.count, 8, "contrat du 2026-11-19 : 8 écritures, 20 lectures de règles sur 20")
        XCTAssertTrue(paths(withAlert).contains("miffs/abcdefghijklmnopqrst/alerts/target"))
        XCTAssertFalse(paths(withAlert).contains(alertPath), "jamais l'alerte de quelqu'un d'autre que le membre retiré")
    }

    func testDeletingTheMiffDeletesTheCreatorsAlertToo() {
        let code = JoinCode.generate()
        let plain = MiffWrites.finalDelete(miffId: miffId, uid: uid, code: code, codeExists: true, membershipKey: "free")
        let withAlert = MiffWrites.finalDelete(
            miffId: miffId, uid: uid, code: code, codeExists: true, membershipKey: "free", withAlert: true
        )

        XCTAssertFalse(paths(plain).contains(alertPath))
        XCTAssertEqual(withAlert.count, plain.count + 1)
        XCTAssertTrue(paths(withAlert).contains(alertPath))
    }

    func testNoLotEverTouchesAnotherMiffsAlert() {
        let all = MiffWrites.stopSharing(miffId: miffId, uid: uid, withAlert: true)
            + MiffWrites.leave(miffId: miffId, uid: uid, membershipKey: "free", withAlert: true)

        for path in paths(all) where path.contains("/alerts/") {
            XCTAssertEqual(path, alertPath)
        }
    }

    func testTheFunctionFlagFollowsTheBuild() {
        #if ALERT_DANGER
        XCTAssertTrue(BuildFlavor.includesAlertDanger)
        #else
        XCTAssertFalse(BuildFlavor.includesAlertDanger)
        #endif
    }

    func testTheRulesOfTheRepositoryAreOffByDefault() {
        let rules = MiffRepositoryRules(newMiffId: { "abcdefghijklmnopqrst" })

        XCTAssertFalse(rules.alertsEnabled)
    }
}
// swiftlint:enable line_length
