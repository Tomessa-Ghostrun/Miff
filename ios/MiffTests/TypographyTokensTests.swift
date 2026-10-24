import UIKit
import XCTest
@testable import Miff

/// Polices embarquées (Baloo Thambi 2, Hanken Grotesk) et styles de texte du design system.
final class TypographyTokensTests: XCTestCase {
    func testEmbeddedFontsAreRegistered() {
        for face in MiffFontFace.allCases {
            XCTAssertTrue(face.isAvailable, "Police introuvable : \(face.rawValue) (UIAppFonts ?)")
        }
    }

    func testEmbeddedFontsAreTheRightFamilies() throws {
        let baloo = try XCTUnwrap(UIFont(name: MiffFontFace.baloo.rawValue, size: 20))
        let hanken = try XCTUnwrap(UIFont(name: MiffFontFace.hankenBold.rawValue, size: 20))

        XCTAssertEqual(baloo.familyName, "Baloo Thambi 2")
        XCTAssertEqual(hanken.familyName, "Hanken Grotesk")
    }

    func testFontFilesDeclaredInInfoPlistAreInTheBundle() throws {
        let declared = try XCTUnwrap(Bundle.main.object(forInfoDictionaryKey: "UIAppFonts") as? [String])

        XCTAssertEqual(declared.count, 4)
        for file in declared {
            XCTAssertNotNil(Bundle.main.url(forResource: file, withExtension: nil), "\(file) absent de l'app")
        }
    }

    func testTextStylesMatchTheDesignSystem() {
        let expected: [String: (CGFloat, CGFloat)] = [
            "textDisplayHero": (64, 72), "textDisplayHeadline": (24, 30), "textDisplayTitle": (20, 26),
            "textDisplayLabel": (16, 22), "textTitleBold24": (24, 30), "textTitleBold20": (20, 26),
            "textBodyLarge": (20, 26), "textBodyRegular": (16, 22), "textBodyItalic": (16, 22),
            "textBodyBold16": (16, 22), "textBodyRegular14": (14, 20), "textBodyBold14": (14, 20),
            "textBodyCaption": (12, 16), "textCaptionBold12": (12, 16), "textCaptionMicro10": (10, 14),
            "textCaptionTag8": (8, 10)
        ]

        XCTAssertEqual(TextStyleTokens.all.count, expected.count)
        for token in TextStyleTokens.all {
            let sizes = expected[token.name]
            XCTAssertEqual(token.size, sizes?.0, token.name)
            XCTAssertEqual(token.lineHeight, sizes?.1, token.name)
        }
    }

    func testDisplayStylesUseBalooAndTextStylesUseHanken() {
        XCTAssertEqual(textDisplayHero.face, .baloo)
        XCTAssertEqual(textBodyRegular.face, .hankenRegular)
        XCTAssertEqual(textBodyBold16.face, .hankenBold)
        XCTAssertEqual(textBodyItalic.face, .hankenItalic)
    }
}
