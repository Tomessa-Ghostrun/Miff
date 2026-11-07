import XCTest
@testable import Miff

/// L'identifiant de session d'un appareil (SESS-3) : forme exacte exigée par les règles Firestore (16 à 64 caractères
/// `A-Za-z0-9_-`), aléatoire, jamais dérivé de l'appareil.
final class DeviceIdTests: XCTestCase {
    func testTheRulesShapeIsSixteenToSixtyFourCharactersOfTheUrlSafeAlphabet() {
        XCTAssertTrue(DeviceId.isValid(String(repeating: "a", count: 16)))
        XCTAssertTrue(DeviceId.isValid(String(repeating: "Z", count: 64)))
        XCTAssertTrue(DeviceId.isValid("AZaz09-_AZaz09-_"))
        XCTAssertFalse(DeviceId.isValid(String(repeating: "a", count: 15)))
        XCTAssertFalse(DeviceId.isValid(String(repeating: "a", count: 65)))
        XCTAssertFalse(DeviceId.isValid(""))
    }

    func testAnythingOutsideTheAlphabetIsRefused() {
        let base = String(repeating: "a", count: 20)
        for forbidden in ["/", "+", "=", " ", ".", "é", "\n", "\u{0}", "😀", "a/b"] {
            XCTAssertFalse(DeviceId.isValid(base + forbidden), "« \(forbidden) » ne doit pas être accepté")
        }
    }

    func testEncodingUsesTheUrlSafeAlphabetWithoutPadding() {
        // 0xFB 0xFB 0xFB donne « +/v7 » en base64 classique.
        let encoded = DeviceId.encode([UInt8](repeating: 0xFB, count: DeviceId.randomByteCount))

        XCTAssertEqual(encoded, String(repeating: "-_v7", count: 8))
        XCTAssertTrue(DeviceId.isValid(encoded))
        XCTAssertFalse(encoded.contains("="))
    }

    func testAGeneratedIdentifierIs32CharactersAndComesFromTwentyFourRandomBytes() {
        var asked = 0
        let identifier = DeviceId.generate { count in
            asked = count
            return (0..<count).map { UInt8($0) }
        }

        XCTAssertEqual(asked, 24)
        XCTAssertEqual(identifier?.count, 32)
        XCTAssertTrue(identifier.map(DeviceId.isValid) ?? false)
    }

    func testNoIdentifierIsInventedWhenTheSystemGeneratorFailsOrAnswersWrong() {
        XCTAssertNil(DeviceId.generate { _ in nil })
        XCTAssertNil(DeviceId.generate { _ in [1, 2, 3] })
    }

    func testTheSystemGeneratorGivesDifferentValidIdentifiersEachTime() {
        let identifiers = (0..<20).compactMap { _ in DeviceId.generate(randomBytes: SecureRandom.bytes) }

        XCTAssertEqual(identifiers.count, 20)
        XCTAssertEqual(Set(identifiers).count, 20)
        XCTAssertTrue(identifiers.allSatisfy { DeviceId.isValid($0) })
    }

    func testTheSystemGeneratorRefusesAnEmptyRequest() {
        XCTAssertNil(SecureRandom.bytes(0))
        XCTAssertEqual(SecureRandom.bytes(24)?.count, 24)
    }
}
