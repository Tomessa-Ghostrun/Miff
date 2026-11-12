import XCTest
@testable import Miff

/// SRV-4I : le message que le serveur envoie quand un proche donne des nouvelles. La donnée vient du réseau : tout ce qui ne ressemble pas
/// EXACTEMENT à ce qu'on attend est ignoré, et la Miff doit être une Miff du compte connecté (miroir de `CircleNewsTest` d'Android).
final class CircleNewsParserTests: XCTestCase {
    private let miffId = "AbCdEf0123456789xyz_"
    private let author = "uidAuthor1234567890abcdefgh"

    private func message(_ overrides: [AnyHashable: Any] = [:]) -> [AnyHashable: Any] {
        var base: [AnyHashable: Any] = [
            "type": "circle_update",
            "miffId": miffId,
            "authorUid": author,
            // Ce que le système ajoute lui-même.
            "aps": ["alert": ["title-loc-key": "notif_circle_update_title"], "thread-id": author],
            "gcm.message_id": "0:123"
        ]
        for (key, value) in overrides {
            base[key] = value
        }
        return base
    }

    func testAValidMessageGivesTheMiffAndTheAuthorAndNothingElse() {
        XCTAssertEqual(CircleNewsParser.parse(message()), CircleNewsTarget(miffId: miffId, authorUid: author))
    }

    func testUnknownTypesAreIgnored() {
        XCTAssertNil(CircleNewsParser.parse(message(["type": "n9_update"])))
        XCTAssertNil(CircleNewsParser.parse(message(["type": ""])))
        XCTAssertNil(CircleNewsParser.parse(message(["type": 3])))
        var withoutType = message()
        withoutType.removeValue(forKey: "type")
        XCTAssertNil(CircleNewsParser.parse(withoutType))
    }

    func testAMissingOrMalformedMiffIdIgnoresTheMessage() {
        var missing = message()
        missing.removeValue(forKey: "miffId")
        XCTAssertNil(CircleNewsParser.parse(missing))
        let bad = ["", "a/b", "https://example.com/x", "miff id", "..", "a\nb", String(repeating: "a", count: 65), "é"]
        for value in bad {
            XCTAssertNil(CircleNewsParser.parse(message(["miffId": value])), value)
        }
        XCTAssertNil(CircleNewsParser.parse(message(["miffId": 12])))
        XCTAssertNil(CircleNewsParser.parse(message(["miffId": ["x"]])))
    }

    func testTheAuthorIsRequiredSoTheSelfFilterCanNeverBeBypassed() {
        var missing = message()
        missing.removeValue(forKey: "authorUid")
        XCTAssertNil(CircleNewsParser.parse(missing))
        XCTAssertNil(CircleNewsParser.parse(message(["authorUid": ""])))
        XCTAssertNil(CircleNewsParser.parse(message(["authorUid": "a/b"])))
        XCTAssertNil(CircleNewsParser.parse(message(["authorUid": 7])))
        XCTAssertNil(CircleNewsParser.parse(message(["authorUid": String(repeating: "a", count: 129)])))
    }

    func testAnIdentifierOfTheMaximumLengthIsAccepted() {
        let longest = String(repeating: "a", count: PushIdentifier.miffMaxLength)
        XCTAssertEqual(CircleNewsParser.parse(message(["miffId": longest]))?.miffId, longest)
    }

    func testTheDescriptionHidesTheIdentifiers() throws {
        let value = try XCTUnwrap(CircleNewsParser.parse(message()))
        let text = "\(value)"
        XCTAssertFalse(text.contains(miffId))
        XCTAssertFalse(text.contains(author))
        XCTAssertEqual(text, "CircleNewsTarget(redacted)")
    }

    // MARK: - Qui peut ouvrir quoi

    private var target: CircleNewsTarget {
        CircleNewsTarget(miffId: miffId, authorUid: author)
    }

    func testAMiffOfTheSignedInAccountIsAccepted() {
        XCTAssertTrue(PushOpenRules.accepts(target, currentUid: "me", miffIds: ["other", miffId]))
    }

    func testAForeignMiffIsIgnored() {
        XCTAssertFalse(PushOpenRules.accepts(target, currentUid: "me", miffIds: ["other"]))
        XCTAssertFalse(PushOpenRules.accepts(target, currentUid: "me", miffIds: []))
    }

    func testAnUnknownMembershipIsIgnored() {
        // Démarrage à froid : l'annuaire des Miffs n'est pas lu.
        XCTAssertFalse(PushOpenRules.accepts(target, currentUid: "me", miffIds: nil))
    }

    func testNobodySignedInIgnoresTheMessage() {
        XCTAssertFalse(PushOpenRules.accepts(target, currentUid: nil, miffIds: [miffId]))
        XCTAssertFalse(PushOpenRules.accepts(target, currentUid: "", miffIds: [miffId]))
    }

    func testMyOwnUpdateIsIgnored() {
        XCTAssertFalse(PushOpenRules.accepts(target, currentUid: author, miffIds: [miffId]))
    }
}
