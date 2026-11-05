import XCTest
@testable import Miff

/// « Prévenir mes proches » (critère S6, décision QM9) : un message que le créateur envoie LUI-MÊME ; il ne contient que le nom
/// de la Miff, jamais le code ni un identifiant.
final class MiffClosingNoticeTests: XCTestCase {
    func testTheMessageHoldsTheNameOfTheMiffAndNothingElse() {
        let text = MiffSharing.closingNotice(miffName: "Chez nous")

        XCTAssertTrue(text.contains("Chez nous"))
        XCTAssertFalse(text.contains(testCode))
        XCTAssertFalse(text.contains(JoinCode.grouped(testCode)))
        XCTAssertFalse(text.contains(testMiffId))
        XCTAssertFalse(text.contains("http"), "pas de lien : rien à rejoindre, la Miff va disparaître")
    }

    func testInvisibleControlCharactersOfTheNameAreRemoved() {
        let text = MiffSharing.closingNotice(miffName: "Chez\u{202E} nous\u{200B}")

        XCTAssertTrue(text.contains("Chez nous"))
        XCTAssertFalse(text.contains("\u{202E}"))
    }
}
