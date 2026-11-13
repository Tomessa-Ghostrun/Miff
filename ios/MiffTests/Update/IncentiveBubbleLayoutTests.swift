// Les assertions citent des cas chiffrés : leurs lignes sont plus longues que la limite.
// swiftlint:disable line_length
import UIKit
import XCTest
@testable import Miff

// La bulle d'incitation (« Tu es passé en orange… ») compacte, de la hauteur de son texte, comme sur Android (retour du PM sur iPhone : elle
// était beaucoup trop grosse). Règle pure + hauteur réelle des vrais textes du catalogue.
final class IncentiveBubbleLayoutTests: XCTestCase {
    func testTheBodyIsAsTallAsItsTextAndNeverMoreThanTheLimit() {
        XCTAssertEqual(IncentiveBubbleLayout.bodyHeight(content: 98, maxBodyHeight: 325), 98, "compacte : la hauteur du texte")
        XCTAssertEqual(IncentiveBubbleLayout.bodyHeight(content: 325, maxBodyHeight: 325), 325)
        XCTAssertEqual(IncentiveBubbleLayout.bodyHeight(content: 900, maxBodyHeight: 325), 325, "jamais plus que la limite")
        XCTAssertNil(IncentiveBubbleLayout.bodyHeight(content: 0, maxBodyHeight: 325), "pas encore mesurée : la hauteur du texte")
        XCTAssertEqual(IncentiveBubbleLayout.bodyHeight(content: 50, maxBodyHeight: -10), 0, "une limite négative ne donne jamais une hauteur négative")
    }

    func testTheTextScrollsInsideOnlyWhenItIsTallerThanTheLimit() {
        XCTAssertFalse(IncentiveBubbleLayout.scrolls(content: 98, maxBodyHeight: 325))
        XCTAssertFalse(IncentiveBubbleLayout.scrolls(content: 325, maxBodyHeight: 325), "à la limite exacte : pas de défilement")
        XCTAssertTrue(IncentiveBubbleLayout.scrolls(content: 325.5, maxBodyHeight: 325))
    }

    func testTheTotalHeightAddsTheTailAndTheAirUnderIt() {
        let total = IncentiveBubbleLayout.totalHeight(content: 98, maxBodyHeight: 325)

        XCTAssertEqual(total, 98 + UpdateLayout.tailHeight + UpdateLayout.tailGap)
        XCTAssertLessThan(total, 325, "compacte")
    }

    /// Les VRAIS textes (français et anglais, orange et rouge), à la taille de texte par défaut, sur le plus petit iPhone courant : quelques
    /// lignes, une bulle bien plus petite que 40 % de l'écran (et très loin des ~420 pt de la capture du PM).
    func testTheRealTextsMakeACompactBubbleOnTheSmallestPhone() throws {
        let catalogURL = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Update
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
            .appendingPathComponent("Miff/Resources/Localizable.xcstrings")
        let root = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: catalogURL)) as? [String: Any])
        let strings = try XCTUnwrap(root["strings"] as? [String: [String: Any]])
        let width: CGFloat = 320 - 2 * UpdateLayout.bubbleSideMargin - 2 * UpdateLayout.bubbleTextInsetX
        let font = textBodyItalic.face.uiFont(size: textBodyItalic.size)

        for key in ["update_bubble_orange", "update_bubble_red"] {
            let localizations = try XCTUnwrap(strings[key]?["localizations"] as? [String: [String: Any]])
            for language in ["fr", "en"] {
                let unit = try XCTUnwrap(localizations[language]?["stringUnit"] as? [String: Any])
                let text = try XCTUnwrap(unit["value"] as? String) as NSString
                let rect = text.boundingRect(
                    with: CGSize(width: width, height: .greatestFiniteMagnitude),
                    options: [.usesLineFragmentOrigin],
                    attributes: [.font: font],
                    context: nil
                )
                let lines = (rect.height / font.lineHeight).rounded(.up)
                let content = lines * textBodyItalic.lineHeight + 2 * Spacing.md
                let total = IncentiveBubbleLayout.totalHeight(content: content, maxBodyHeight: 0.4 * 568)

                XCTAssertLessThanOrEqual(lines, 6, "\(key) \(language)")
                XCTAssertLessThanOrEqual(total, 0.4 * 568, "\(key) \(language) : au plus 40 % de l'écran")
                XCTAssertLessThan(total, 200, "\(key) \(language) : compacte")
            }
        }
    }

    func testTheBubbleSizeIsMeasuredNotLeftToAGreedyContainer() throws {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Update
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
            .appendingPathComponent("Miff/Views/Update/UpdateBubble.swift")
        let source = try String(contentsOf: url, encoding: .utf8)

        XCTAssertTrue(source.contains("IncentiveBubbleLayout.scrolls"))
        XCTAssertFalse(source.contains("ViewThatFits"), "la hauteur est mesurée, pas choisie par un conteneur")
    }
}
// swiftlint:enable line_length
