import SwiftUI
import XCTest
@testable import Miff

// La mise en page de la bulle (retour de recette du PM, 2026-11-08 : texte non centré dans la tache). Tests de GÉOMÉTRIE : ce qui peut
// être vérifié sans écran (marges, centre du contenu, centre de la forme, mouvement autour du centre). Le rendu réel (3 couleurs, pages
// du carrousel, Dynamic Type) reste une ligne de la recette du PM : `docs/qa/checklists/feed-ios-recette-pm.md`.
final class BubbleLayoutTests: XCTestCase {
    func testTheMarginsAreEqualOnBothSides() {
        // Plus de 40 pt à gauche pour 56 pt à droite : le contenu est symétrique, la marge du haut est celle du bas.
        let size = BubbleLayout.outerSize(content: CGSize(width: 200, height: 300))
        let frame = BubbleLayout.contentFrame(content: CGSize(width: 200, height: 300))

        XCTAssertEqual(frame.minX, size.width - frame.maxX, accuracy: 0.001)
        XCTAssertEqual(frame.minY, size.height - frame.maxY, accuracy: 0.001)
    }

    func testTheContentIsCentredWhateverItsSizeFromOneLineToTheLargestTextSize() {
        let sizes = [
            CGSize(width: 150, height: 40), CGSize(width: 239, height: 120),
            CGSize(width: 239, height: 175), CGSize(width: 239, height: 400)
        ]

        for content in sizes {
            let outer = BubbleLayout.outerSize(content: content)
            let frame = BubbleLayout.contentFrame(content: content)

            XCTAssertEqual(frame.midX, outer.width / 2, accuracy: 0.001, "centré en largeur : \(content)")
            XCTAssertEqual(frame.midY, outer.height / 2, accuracy: 0.001, "centré en hauteur : \(content)")
        }
    }

    func testAShortBubbleKeepsItsMinimumHeightAndItsContentInTheMiddle() {
        let outer = BubbleLayout.outerSize(content: CGSize(width: 100, height: 20))

        XCTAssertEqual(outer.height, BubbleLayout.minContentHeight + 2 * BubbleLayout.verticalInset, accuracy: 0.001)
    }

    func testTheBlobIsCentredOnItsBox() {
        let box = CGRect(x: 0, y: 0, width: 100, height: 100)

        let bounds = FeedBubbleShape().path(in: box).boundingRect

        XCTAssertEqual(bounds.midX, box.midX, accuracy: 1.0, "la tache n'est pas décalée vers un côté")
        XCTAssertEqual(bounds.midY, box.midY, accuracy: 1.0)
    }

    func testTheShapeMotionIsAlwaysAroundItsCentreAndNeverWidensItsBox() {
        // L'échelle ne fait que RÉTRÉCIR (jamais au-dessus de 1) et la dérive est une même translation pour la forme et le texte :
        // à aucun instant le texte n'est décalé par rapport à la forme.
        for step in 0..<100 {
            let phase = Double(step) / 100

            XCTAssertLessThanOrEqual(BubbleMotion.scaleX(phase), 1)
            XCTAssertLessThanOrEqual(BubbleMotion.scaleY(phase), 1)
            XCTAssertGreaterThan(BubbleMotion.scaleX(phase), 1 - 2 * BubbleMotion.squashX - 0.0001)
            XCTAssertGreaterThan(BubbleMotion.scaleY(phase), 1 - 2 * BubbleMotion.squashY - 0.0001)
        }
    }

    /// Décision du PM (2026-11-11) : AUCUN symbole dans la bulle, quelle que soit sa couleur (filigrane, avion, pictogramme de bouton).
    func testTheBubbleHasNoSymbolAtAll() throws {
        let source = try String(contentsOf: bubbleSource, encoding: .utf8)

        XCTAssertFalse(source.contains("systemName:"), "un SF Symbol dans la bulle")
        XCTAssertFalse(source.contains("Image("), "une image dans la bulle")
    }

    func testTheBubbleHeadlineUsesTheHeadlineStyleAndTheButtonKeepsATouchTarget() throws {
        let source = try String(contentsOf: bubbleSource, encoding: .utf8)

        XCTAssertTrue(source.contains(".miffTextStyle(textDisplayHeadline)"), "le titre des autres écrans")
        XCTAssertFalse(source.contains("textDisplayBubble"))
        XCTAssertTrue(source.contains("minHeight: Spacing.minTouchTarget"), "zone de toucher d'au moins 44 pt")
        XCTAssertTrue(source.contains(".minimumScaleFactor(BubbleLayout.headlineMinimumScale)"))
        XCTAssertGreaterThanOrEqual(Spacing.minTouchTarget, 44)
    }

    func testTheSideMarginsShrinkAtAccessibilityTextSizesButStayEqualOnBothSides() {
        XCTAssertEqual(BubbleLayout.horizontalInset(accessibilitySize: false), BubbleLayout.horizontalInset)
        XCTAssertLessThan(BubbleLayout.horizontalInset(accessibilitySize: true), BubbleLayout.horizontalInset)
        let size = BubbleLayout.outerSize(content: CGSize(width: 200, height: 100), horizontalInset: 28)
        let frame = BubbleLayout.contentFrame(content: CGSize(width: 200, height: 100), horizontalInset: 28)

        XCTAssertEqual(frame.minX, size.width - frame.maxX, accuracy: 0.001)
    }

    /// Le plus long mot des phrases principales de la bulle (FR et EN) tient sur une ligne à la plus grande taille de texte, une fois la
    /// réduction autorisée appliquée, dans la plus petite largeur d'iPhone (320 pt moins les marges de la liste et de la bulle).
    func testTheLongestHeadlineWordFitsAtTheLargestTextSize() throws {
        let catalogURL = bubbleSource
            .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
            .appendingPathComponent("Resources/Localizable.xcstrings")
        let root = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: catalogURL)) as? [String: Any])
        let strings = try XCTUnwrap(root["strings"] as? [String: [String: Any]])
        var words: [String] = []
        let keys = [
            "feed_bubble_all_good", "feed_bubble_no_news_title",
            "feed_bubble_alone", "feed_bubble_sharing_stopped_title"
        ]
        for key in keys {
            let localizations = try XCTUnwrap(strings[key]?["localizations"] as? [String: [String: Any]])
            for language in ["fr", "en"] {
                let unit = localizations[language]?["stringUnit"] as? [String: Any]
                words += (unit?["value"] as? String ?? "").split(separator: " ").map(String.init)
            }
        }
        let largest = UITraitCollection(preferredContentSizeCategory: .accessibilityExtraExtraExtraLarge)
        let metrics = UIFontMetrics(forTextStyle: .title2)
        let size = metrics.scaledValue(for: textDisplayHeadline.size, compatibleWith: largest)
        let font = textDisplayHeadline.face.uiFont(size: size)
        let widest = words.map { ($0 as NSString).size(withAttributes: [.font: font]).width }.max() ?? 0
        let available: CGFloat = 320 - 2 * Spacing.md - 2 * BubbleLayout.accessibilityHorizontalInset

        XCTAssertFalse(words.isEmpty)
        XCTAssertLessThanOrEqual(widest * BubbleLayout.headlineMinimumScale, available, "un mot serait coupé")
    }

    private var bubbleSource: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Feed
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
            .appendingPathComponent("Miff/Views/Feed/FeedBubbleView.swift")
    }

    func testWithReducedMotionNothingMoves() {
        XCTAssertFalse(BubbleMotion.enabled(reduceMotion: true, active: true))
        XCTAssertFalse(BubbleMotion.enabled(reduceMotion: false, active: false))
    }
}
