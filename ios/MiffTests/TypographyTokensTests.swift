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

    /// Décision du PM (2026-11-10) : la phrase principale de la bulle du Feed, Baloo Thambi 2 ExtraBold 30 / 36, suit Dynamic Type (.title2).
    func testTheBubbleStyleIsBalooExtraBold30Over36() throws {
        XCTAssertEqual(textDisplayBubble.size, 30)
        XCTAssertEqual(textDisplayBubble.lineHeight, 36)
        XCTAssertEqual(textDisplayBubble.variationWeight, 800)
        XCTAssertEqual(textDisplayBubble.face, .baloo)
        XCTAssertEqual(textDisplayBubble.dynamicTypeStyle, .title2)
        XCTAssertFalse(TextStyleTokens.all.contains { $0.name == "textDisplayBubble" }, "hors de la liste d'Android")
        XCTAssertNil(textDisplayHeadline.variationWeight, "le reste du Feed ne change pas")
        let font = textDisplayBubble.uiFont(size: 30)
        XCTAssertEqual(font.familyName, "Baloo Thambi 2")
        XCTAssertEqual(font.pointSize, 30)
    }

    /// La variation de graisse est VRAIMENT appliquée par le système (et pas seulement demandée) : la même phrase est plus large à 800
    /// qu'à 400, et à 800 qu'à 700.
    func testTheWeightVariationIsAppliedSoTheBubbleFontIsWiderThanRegular() {
        let text = "Tout va bien" as NSString
        func width(_ font: UIFont) -> CGFloat {
            text.size(withAttributes: [.font: font]).width
        }

        let regular = width(MiffFontFace.baloo.uiFont(size: 30))
        let bold = width(MiffFontFace.balooUIFont(weight: 700, size: 30))
        let extraBold = width(textDisplayBubble.uiFont(size: 30))

        XCTAssertGreaterThan(bold, regular, "wght 700 non appliqué")
        XCTAssertGreaterThan(extraBold, bold, "wght 800 non appliqué")
        XCTAssertGreaterThan(extraBold, regular)
    }

    func testDisplayStylesUseBalooAndTextStylesUseHanken() {
        XCTAssertEqual(textDisplayHero.face, .baloo)
        XCTAssertEqual(textBodyRegular.face, .hankenRegular)
        XCTAssertEqual(textBodyBold16.face, .hankenBold)
        XCTAssertEqual(textBodyItalic.face, .hankenItalic)
    }
}
