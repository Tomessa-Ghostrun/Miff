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
        let sizes = [CGSize(width: 150, height: 40), CGSize(width: 239, height: 120), CGSize(width: 239, height: 175), CGSize(width: 239, height: 400)]

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

    func testThePictogramDiscStaysClearOfACentredButtonCorner() {
        // Largeur d'un iPhone 11 Pro moins les marges de la liste : le bouton le plus large (« Envoyer un message », 210 pt) est centré
        // dans la zone de lecture ; son angle bas droit ne doit pas toucher le disque.
        let bubbleWidth: CGFloat = 343
        let content = bubbleWidth - 2 * BubbleLayout.horizontalInset
        let buttonRight = BubbleLayout.horizontalInset + content / 2 + 105
        let corner = CGPoint(x: bubbleWidth - buttonRight, y: BubbleLayout.verticalInset)
        let radius = BubbleLayout.discSize / 2
        let center = CGPoint(x: BubbleLayout.discTrailing + radius, y: BubbleLayout.discBottom + radius)

        let distance = hypot(center.x - corner.x, center.y - corner.y)

        XCTAssertGreaterThanOrEqual(distance, radius)
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

    func testWithReducedMotionNothingMoves() {
        XCTAssertFalse(BubbleMotion.enabled(reduceMotion: true, active: true))
        XCTAssertFalse(BubbleMotion.enabled(reduceMotion: false, active: false))
    }
}
