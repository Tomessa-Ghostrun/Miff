import XCTest
@testable import Miff

/// Les lots de MIFF-4 en données (critères R2, N1, Q1, S2) : les chemins, l'ordre et le nombre d'écritures sont le contrat avec
/// backend/firestore.rules (tests/rules/18-miff2-retirer-quitter-supprimer.test.js prouve que les règles acceptent
/// exactement ceux-ci : ce sont ceux d'Android).
final class MiffRemovalWritesTests: XCTestCase {
    private let miffId = testMiffId
    private let oldCode = "XTZRSP367988"
    private let newCode = "BCDFGHK23456"

    private func summary(_ lot: [DocWrite]) -> [String] {
        lot.map { "\($0.path) \($0.kind)" }
    }

    func testRemovingIsExactlySevenWritesThePersonsFourThenTheNewCodesThreeAndNothingElse() {
        let lot = MiffWrites.removeMember(
            miffId: miffId, targetUid: "uid-2", name: "Chez nous", oldCode: oldCode, newCode: newCode
        )

        XCTAssertEqual(
            summary(lot),
            [
                "miffs/\(miffId)/members/uid-2 delete",
                "miffs/\(miffId)/status/uid-2 delete",
                "miffs/\(miffId)/photos/uid-2 delete",
                "miffs/\(miffId)/positions/uid-2 delete",
                "miffs/\(miffId) update",
                "joinCodes/\(newCode) set",
                "joinCodes/\(oldCode) delete"
            ]
        )
        // Rien n'est ajouté à ce lot : les règles lisent jusqu'à 18 documents sur 20 (critère R2) ; supprimer la dernière
        // position connue de la personne (LOC-4a, comme Android) n'en lit aucun de plus.
        XCTAssertEqual(lot.count, 7)
        let touchesUserIndex = lot.contains { $0.path.hasPrefix("users/") }
        XCTAssertFalse(touchesUserIndex, "le créateur ne touche jamais à l'index de la personne")
    }

    func testRemovingAlwaysChangesTheCodeAndTheNewCodeDocumentCarriesTheCurrentName() {
        let lot = MiffWrites.removeMember(
            miffId: miffId, targetUid: "uid-2", name: "Chez nous", oldCode: oldCode, newCode: newCode
        )

        XCTAssertEqual(lot[3].data, ["joinCode": .string(newCode)])
        XCTAssertEqual(lot[4].data["miffId"], .string(miffId))
        XCTAssertEqual(lot[4].data["miffName"], .string("Chez nous"))
        XCTAssertEqual(Set(lot[4].data.keys), ["miffId", "miffName", "createdAt"])
    }

    func testARemovalThatKeepsTheCodeOrUsesAMalformedOneIsRefusedBeforeAnyLotIsBuilt() {
        let same = MiffWrites.removeMember(
            miffId: miffId, targetUid: "uid-2", name: "Chez nous", oldCode: oldCode, newCode: oldCode
        )
        let malformed = MiffWrites.removeMember(
            miffId: miffId, targetUid: "uid-2", name: "Chez nous", oldCode: oldCode, newCode: "abc"
        )

        XCTAssertTrue(same.isEmpty, "critère R4 : jamais de retrait sans nouveau code")
        XCTAssertTrue(malformed.isEmpty)
    }

    func testAPersonIdentifierThatCouldLeaveItsPathIsRefused() {
        let escaping = MiffWrites.removeMember(
            miffId: miffId, targetUid: "a/b", name: "Chez nous", oldCode: oldCode, newCode: newCode
        )
        let empty = MiffWrites.removeMember(
            miffId: miffId, targetUid: "", name: "Chez nous", oldCode: oldCode, newCode: newCode
        )

        XCTAssertTrue(escaping.isEmpty)
        XCTAssertTrue(empty.isEmpty)
    }

    func testWhenTheOldCodeDocumentIsAlreadyGoneItsDeletionIsLeftOut() {
        let lot = MiffWrites.removeMember(
            miffId: miffId, targetUid: "uid-2", name: "Chez nous", oldCode: oldCode, newCode: newCode,
            oldCodeExists: false
        )

        XCTAssertEqual(lot.count, 5)
        XCTAssertFalse(lot.contains { $0.path == "joinCodes/\(oldCode)" })
    }

    func testRenewingIsThreeWritesInTheOrderOfTheRules() {
        let lot = MiffWrites.renewCode(miffId: miffId, name: "Chez nous", oldCode: oldCode, newCode: newCode)

        XCTAssertEqual(
            summary(lot),
            ["miffs/\(miffId) update", "joinCodes/\(newCode) set", "joinCodes/\(oldCode) delete"]
        )
        XCTAssertFalse(lot.contains { $0.path.contains("/members/") }, "renouveler ne touche à aucun membre")
    }

    func testLeavingIsFiveDeletionsAndTheIndexIsLeftOutOnlyWhenItWasNotFound() {
        let lot = MiffWrites.leave(miffId: miffId, uid: "uid-2", membershipKey: "free")

        XCTAssertEqual(
            lot.map { $0.path },
            [
                "miffs/\(miffId)/members/uid-2",
                "miffs/\(miffId)/status/uid-2",
                "miffs/\(miffId)/photos/uid-2",
                "miffs/\(miffId)/positions/uid-2",
                "users/uid-2/memberships/free"
            ]
        )
        XCTAssertTrue(lot.allSatisfy { $0.kind == .delete })
        XCTAssertEqual(MiffWrites.leave(miffId: miffId, uid: "uid-2", membershipKey: nil).count, 4)
        // Un compte Premium met son index sous l'identifiant de la Miff.
        XCTAssertEqual(
            MiffWrites.leave(miffId: miffId, uid: "uid-2", membershipKey: miffId).last?.path,
            "users/uid-2/memberships/\(miffId)"
        )
    }

    func testLeavingNeverTouchesTheMiffNorAnyCode() {
        // Décision du PM du 2026-10-31 : une sortie volontaire ne renouvelle PAS le code.
        let lot = MiffWrites.leave(miffId: miffId, uid: "uid-2", membershipKey: "free")

        XCTAssertFalse(lot.contains { $0.path == "miffs/\(miffId)" || $0.path.hasPrefix("joinCodes") })
        XCTAssertTrue(lot.allSatisfy { $0.path.contains("/uid-2") })
    }

    func testDeletingPlacesTakesFiveAtMost() {
        XCTAssertEqual(MiffWrites.deletePlaces(miffId: miffId, placeIds: ["p1", "p2", "p3", "p4", "p5"]).count, 5)
        XCTAssertTrue(MiffWrites.deletePlaces(miffId: miffId, placeIds: ["a", "b", "c", "d", "e", "f"]).isEmpty)
        XCTAssertTrue(MiffWrites.deletePlaces(miffId: miffId, placeIds: []).isEmpty)
        XCTAssertEqual(
            MiffWrites.deletePlaces(miffId: miffId, placeIds: ["p1"]).first?.path,
            "miffs/\(miffId)/places/p1"
        )
    }

    func testTheFinalLotIsTheMiffItsTombstoneTheCurrentCodeAndTheCreatorsOwnTraces() {
        let lot = MiffWrites.finalDelete(
            miffId: miffId, uid: "uid-1", code: oldCode, codeExists: true, membershipKey: "free"
        )

        XCTAssertEqual(
            summary(lot),
            [
                "miffs/\(miffId) delete",
                "miffsDeleted/\(miffId) set",
                "joinCodes/\(oldCode) delete",
                "miffs/\(miffId)/status/uid-1 delete",
                "miffs/\(miffId)/photos/uid-1 delete",
                "miffs/\(miffId)/positions/uid-1 delete",
                "miffs/\(miffId)/members/uid-1 delete",
                "users/uid-1/memberships/free delete"
            ]
        )
        XCTAssertEqual(Set(lot[1].data.keys), ["deletedAt"], "seulement la date, rien sur personne")
        XCTAssertFalse(
            lot.contains { $0.path.contains("members/") && !$0.path.hasSuffix("uid-1") },
            "aucun retrait d'un autre membre dans le lot qui supprime la Miff"
        )
    }

    func testTheFinalLotLeavesOutWhatDoesNotExist() {
        let noCode = MiffWrites.finalDelete(
            miffId: miffId, uid: "uid-1", code: oldCode, codeExists: false, membershipKey: "free"
        )
        let noIndex = MiffWrites.finalDelete(
            miffId: miffId, uid: "uid-1", code: oldCode, codeExists: true, membershipKey: nil
        )
        let neither = MiffWrites.finalDelete(
            miffId: miffId, uid: "uid-1", code: oldCode, codeExists: false, membershipKey: nil
        )

        XCTAssertFalse(noCode.contains { $0.path.hasPrefix("joinCodes/") })
        XCTAssertFalse(noIndex.contains { $0.path.hasPrefix("users/") })
        XCTAssertEqual(neither.count, 6)
    }

    func testTheFinalLotIsEightWritesOneMoreThanTheContractOfTheRulesWhichLeavesThePhotoOut() {
        let lot = MiffWrites.finalDelete(
            miffId: miffId, uid: "uid-1", code: oldCode, codeExists: true, membershipKey: "free"
        )

        // Le contrat des règles en compte 7 (sans la photo) ; la photo et la dernière position ne coûtent aucune lecture.
        XCTAssertEqual(lot.count, 8)
        XCTAssertEqual(lot.filter { $0.path.contains("/positions/") }.map { $0.kind }, [.delete])
    }

    func testTheLotsNeverShowTheirContentInADescription() {
        let lot = MiffWrites.removeMember(
            miffId: miffId, targetUid: "uid-2", name: "Chez nous", oldCode: oldCode, newCode: newCode
        )

        let shown = lot.map { String(describing: $0) }.joined()
        XCTAssertFalse(shown.contains(newCode))
        XCTAssertFalse(shown.contains("Chez nous"))
    }
}
