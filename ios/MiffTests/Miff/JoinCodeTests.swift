import XCTest
@testable import Miff

/// Le code secret d'une Miff (ticket Miff §5.3, critères C4, J1, J2). Mêmes cas qu'Android.
final class JoinCodeTests: XCTestCase {
    func testAlphabetHasThe31SymbolsOfTheRulesWithoutTheLookAlikes() {
        XCTAssertEqual(JoinCode.alphabet.count, 31)
        XCTAssertEqual(Set(JoinCode.alphabet).count, 31)
        for symbol in "ILO01" {
            XCTAssertFalse(JoinCode.alphabet.contains(symbol), "\(symbol) ne doit pas être dans l'alphabet")
        }
        // Le même alphabet que la règle isJoinCode de firestore.rules : [A-HJ-KM-NP-Z2-9].
        XCTAssertEqual(JoinCode.alphabet, "ABCDEFGH" + "JK" + "MN" + "PQRSTUVWXYZ" + "23456789")
    }

    func testGenerate1000CodesAreAllValidWithoutLookAlikesAndNotAllTheSame() {
        let codes = (0..<1000).map { _ in JoinCode.generate() }
        for code in codes {
            XCTAssertEqual(code.count, 12)
            XCTAssertTrue(JoinCode.isValid(code), "forme valide")
            XCTAssertFalse(code.contains { "ILO01".contains($0) })
        }
        XCTAssertGreaterThan(Set(codes).count, 990, "1000 tirages ne donnent pas 1000 fois le même code")
        // Chaque symbole apparaît sur 12 000 caractères : aucun n'est laissé de côté par le tirage.
        XCTAssertEqual(Set(codes.joined()).count, 31)
    }

    func testGenerateHasNoModuloBiasByRejection() {
        // Un générateur qui ne donne d'abord que 255 (rejeté : >= 248) ne doit ni boucler sans fin ni fausser un symbole : il
        // reçoit 255, puis 0..247, qui tombent sur l'alphabet de façon égale.
        var feed: [UInt8] = Array(repeating: 255, count: 40) + (0..<248).map { UInt8($0) }
        let code = JoinCode.generate(randomBytes: { count in
            let chunk = Array(feed.prefix(count))
            feed.removeFirst(min(count, feed.count))
            return chunk
        })

        XCTAssertTrue(JoinCode.isValid(code))
        // Les premières valeurs acceptées sont 0, 1, 2... : les premiers symboles de l'alphabet, dans l'ordre.
        XCTAssertEqual(code, String(JoinCode.alphabet.prefix(12)))
    }

    func testAGeneratorThatNeverGivesAnythingUsableGivesNoCodeAtAll() {
        // Jamais un code faible : vide, donc invalide et refusé par tous les appelants.
        XCTAssertEqual(JoinCode.generate(randomBytes: { _ in [] }), "")
        XCTAssertEqual(JoinCode.generate(randomBytes: { count in Array(repeating: 255, count: count) }), "")
    }

    func testTheSystemGeneratorGivesTheRequestedNumberOfBytes() {
        XCTAssertEqual(JoinCode.secureRandomBytes(24).count, 24)
        XCTAssertNotEqual(JoinCode.secureRandomBytes(32), JoinCode.secureRandomBytes(32))
    }

    func testNormalizeGivesCapitalsAndNoSpacesOrDashes() {
        XCTAssertEqual(JoinCode.normalize("xtzr sp36 7988"), "XTZRSP367988")
        XCTAssertEqual(JoinCode.normalize(" XTZR-SP36-7988 "), "XTZRSP367988")
        XCTAssertEqual(JoinCode.normalize("xtzr\u{2013}sp36 7988\n"), "XTZRSP367988")
        XCTAssertEqual(JoinCode.normalize("XTZR_SP36\t7988"), "XTZRSP367988")
        XCTAssertEqual(JoinCode.normalize("XTZR\u{00A0}SP36\u{00A0}7988"), "XTZRSP367988")
    }

    func testTwelveValidCharactersAreComplete() {
        let input = JoinCode.analyze("xtzr sp36 7988")

        XCTAssertEqual(input.code, "XTZRSP367988")
        XCTAssertTrue(input.isComplete)
        XCTAssertFalse(input.hasForeignCharacter)
    }

    func testAPartialCodeIsNotCompleteButNotAnError() {
        let input = JoinCode.analyze("XTZR SP")

        XCTAssertFalse(input.isComplete)
        XCTAssertFalse(input.hasForeignCharacter)
    }

    func testALookAlikeCharacterIsForeignAndNeverComplete() {
        for typed in [
            "XTZRSP36798I", "XTZRSP36798L", "XTZRSP36798O", "XTZRSP367980", "XTZRSP367981", "XTZRSP36798!",
            "XTZRSP36798é"
        ] {
            let input = JoinCode.analyze(typed)
            XCTAssertTrue(input.hasForeignCharacter, "\(typed) est étranger")
            XCTAssertFalse(input.isComplete, "\(typed) n'est pas complet")
        }
    }

    func testLongerThan12KeepsTheFirst12() {
        let input = JoinCode.analyze("XTZRSP367988K")

        XCTAssertEqual(input.code, "XTZRSP367988")
        XCTAssertEqual(input.code.count, 12)
    }

    func testGroupedAndSpokenByFour() {
        XCTAssertEqual(JoinCode.grouped("XTZRSP367988"), "XTZR SP36 7988")
        XCTAssertEqual(JoinCode.spoken("XTZRSP367988"), "X T Z R, S P 3 6, 7 9 8 8")
        XCTAssertEqual(JoinCode.grouped("XTZRSP"), "XTZR SP")
        XCTAssertEqual(JoinCode.grouped(""), "")
    }

    func testIsValidRefusesAnythingButTwelveSymbolsOfTheAlphabet() {
        XCTAssertTrue(JoinCode.isValid("XTZRSP367988"))
        XCTAssertFalse(JoinCode.isValid("xtzrsp367988"))
        XCTAssertFalse(JoinCode.isValid("XTZRSP36798"))
        XCTAssertFalse(JoinCode.isValid("XTZRSP3679888"))
        XCTAssertFalse(JoinCode.isValid("XTZRSP367988\n"))
        XCTAssertFalse(JoinCode.isValid("XTZR SP36 7988"))
    }

    func testTheCodeNeverShowsInADescription() {
        XCTAssertFalse(String(describing: JoinCode.analyze("XTZRSP367988")).contains("XTZR"))
    }
}
