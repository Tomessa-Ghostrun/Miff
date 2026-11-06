import XCTest
@testable import Miff

/// LOC-4a : la version 3 du consentement et son renouvellement (règle R-LIEU-3), et la suppression de la dernière position connue
/// dans les lots, face au contrat de backend/firestore.rules : les quatre clés exactes de `sharing`, la date du serveur, une
/// version de largeur fixe strictement supérieure, aucune écriture de position nulle part.
final class MiffConsentWritesTests: XCTestCase {
    private let uid = "uid-1"
    private let miffId = testMiffId
    private let code = testCode

    private func write(_ writes: [DocWrite], _ path: String) throws -> DocWrite {
        let matching = writes.filter { $0.path == path }
        XCTAssertEqual(matching.count, 1, "un seul document à \(path)")
        return try XCTUnwrap(matching.first)
    }

    func testTheConsentIsVersionThreeInTheFixedWidthFormatAndCoversThePosition() {
        // LOC-4a : « 2026-11-v03 », la même constante qu'Android ; AAAA-MM-vNN de largeur fixe (les règles comparent des chaînes).
        XCTAssertEqual(MiffSharingConsent.version, "2026-11-v03")
        XCTAssertTrue(MiffSharingConsent.isFixedWidth(MiffSharingConsent.version))
        XCTAssertTrue(MiffSharingConsent.coversPosition(MiffSharingConsent.version))
        XCTAssertTrue(MiffSharingConsent.coversPosition("2026-12-v04"))
        XCTAssertTrue(MiffSharingConsent.coversPosition("2027-01-v99"))
        // Les deux anciens textes ne couvrent PAS la position et ne sont pas de largeur fixe.
        for old in ["2026-10-v1", "2026-10-v2", "2026-11-v02", "2026-11-v3", "2026-11-v003", "", "v03"] {
            XCTAssertFalse(MiffSharingConsent.coversPosition(old), old)
        }
        XCTAssertFalse(MiffSharingConsent.coversPosition(nil))
        XCTAssertFalse(MiffSharingConsent.isFixedWidth("2026-10-v2"))
        XCTAssertFalse(MiffSharingConsent.isFixedWidth("2026-13-v03"))
        XCTAssertFalse(MiffSharingConsent.isFixedWidth("2026-11-v03\n"), "un retour à la ligne n'est pas une version")
        XCTAssertFalse(MiffSharingConsent.isFixedWidth("x2026-11-v03"))
    }

    func testARenewalIsDueOnlyForAnOlderKnownText() {
        for old in ["2026-10-v1", "2026-10-v2", "2026-11-v02", "2025-12-v01", "2026-10-v10"] {
            XCTAssertTrue(MiffSharingConsent.isRenewalDue(old), old)
        }
        // Le texte en vigueur, un texte plus récent (jamais « renouvelé » en arrière), une forme inconnue, aucune version : rien.
        for current in [MiffSharingConsent.version, "2026-12-v04", "2027-01-v05", "texte libre", "", "2026-13-v02"] {
            XCTAssertFalse(MiffSharingConsent.isRenewalDue(current), current)
        }
        XCTAssertFalse(MiffSharingConsent.isRenewalDue(nil))
    }

    // MARK: - Renouvellement (R-LIEU-3)

    func testRenewingAnActiveConsentIsOneUpdateWithTheFourKeysExactly() throws {
        let writes = MiffWrites.renewConsent(miffId: miffId, uid: uid)

        XCTAssertEqual(writes.count, 1)
        let card = try write(writes, "miffs/\(miffId)/members/\(uid)")
        XCTAssertEqual(card.kind, .update)
        // Une seule clé de premier niveau : `sharing`, remplacée en entier (les quatre clés, ni plus ni moins).
        XCTAssertEqual(Set(card.data.keys), ["sharing"])
        guard case .map(let nested)? = card.data["sharing"] else {
            return XCTFail("sharing doit être une carte")
        }
        XCTAssertEqual(Set(nested.keys), ["enabled", "consentedAt", "consentVersion", "revokedAt"])
        XCTAssertEqual(nested["enabled"], .bool(true))
        // La date est celle du serveur, jamais celle du téléphone.
        XCTAssertEqual(nested["consentedAt"], .serverTimestamp)
        XCTAssertEqual(nested["consentVersion"], .string("2026-11-v03"))
        XCTAssertEqual(nested["revokedAt"], .null)
    }

    func testRenewingTouchesNeitherTheStatusNorThePhotoNorThePosition() {
        let paths = MiffWrites.renewConsent(miffId: miffId, uid: uid).map { $0.path }

        XCTAssertEqual(paths, ["miffs/\(miffId)/members/\(uid)"])
    }

    func testRenewingRefusesABadIdentifierBeforeAnythingIsWritten() {
        XCTAssertTrue(MiffWrites.renewConsent(miffId: "../x", uid: uid).isEmpty)
        XCTAssertTrue(MiffWrites.renewConsent(miffId: miffId, uid: "").isEmpty)
        XCTAssertTrue(MiffWrites.renewConsent(miffId: miffId, uid: "a/b").isEmpty)
    }

    func testThePositionIsOnlyEverDeletedHereNeverWritten() {
        // LOC-4a ne lit ni n'écrit aucune position : dans TOUS les lots, un chemin `positions/` n'est jamais qu'une suppression.
        let removal = MiffWrites.removeMember(
            miffId: miffId, targetUid: "uid-2", name: "Chez nous", oldCode: code, newCode: "BCDFGHK23456"
        )
        let finalLot = MiffWrites.finalDelete(
            miffId: miffId, uid: uid, code: code, codeExists: true, membershipKey: "free"
        )
        let lots: [[DocWrite]] = [
            MiffWrites.stopSharing(miffId: miffId, uid: uid),
            MiffWrites.leave(miffId: miffId, uid: uid, membershipKey: "free"),
            MiffWrites.dropIndex(miffId: miffId, uid: uid, membershipKey: "free"),
            MiffWrites.renewConsent(miffId: miffId, uid: uid),
            MiffWrites.resumeSharing(miffId: miffId, uid: uid, access: .unknown),
            removal,
            finalLot
        ]
        let positionWrites = lots.flatMap { $0 }.filter { $0.path.contains("/positions/") }
        XCTAssertEqual(positionWrites.count, 5)
        XCTAssertTrue(positionWrites.allSatisfy { $0.kind == .delete })
    }
}
