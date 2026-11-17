import SwiftUI
import UIKit
import XCTest
@testable import Miff

/// La bordure dynamique (§2.4) : le contour est ancré en haut au centre et se vide dans le sens horaire (la longueur se mesure le long du
/// contour), la couleur glisse de `status/safe` vers `status/warning` sur le dernier quart sans jamais devenir rouge, et les animations
/// sont instantanées avec « Réduire les animations ».
final class CountdownBorderTests: XCTestCase {
    private let rect = CGRect(x: 0, y: 0, width: 320, height: 72)
    private var inset: CGFloat {
        CountdownBorder.lineWidth / 2
    }

    // MARK: - La forme

    func testTheStrokeIsThreePointsLikeTheAvatarRing() {
        XCTAssertEqual(CountdownBorder.lineWidth, 3)
        XCTAssertEqual(CountdownBorder.lineWidth, strokeAvatarRing)
    }

    func testTheContourStartsAtTheTopCentre() throws {
        let path = CountdownBorderShape().path(in: rect)
        var first: CGPoint?
        path.cgPath.applyWithBlock { element in
            if first == nil, element.pointee.type == .moveToPoint {
                first = element.pointee.points[0]
            }
        }
        let start = try XCTUnwrap(first)
        XCTAssertEqual(start.x, rect.midX, accuracy: 0.01)
        XCTAssertEqual(start.y, inset, accuracy: 0.01, "trait rentré d une demi-épaisseur")
    }

    func testTheContourStaysInsideTheCardWithTheFullStroke() {
        let box = CountdownBorderShape().path(in: rect).boundingRect
        XCTAssertEqual(box.minX, inset, accuracy: 0.01)
        XCTAssertEqual(box.minY, inset, accuracy: 0.01)
        XCTAssertEqual(box.maxX, rect.maxX - inset, accuracy: 0.01)
        XCTAssertEqual(box.maxY, rect.maxY - inset, accuracy: 0.01)
    }

    func testTheFirstTenthOfTheContourRunsToTheRightOfTheTopCentreClockwise() {
        let early = CountdownBorderShape().path(in: rect).trimmedPath(from: 0, to: 0.1).boundingRect
        XCTAssertGreaterThanOrEqual(early.minX, rect.midX - 0.5, "le début part vers la DROITE : sens horaire")
        XCTAssertEqual(early.minY, inset, accuracy: 0.01, "sur le bord haut")
    }

    func testHalfTheContourEndsAtTheBottomCentreSoTheRightHalfIsDrawnFirst() {
        let half = CountdownBorderShape().path(in: rect).trimmedPath(from: 0, to: 0.5).boundingRect
        XCTAssertEqual(half.maxX, rect.maxX - inset, accuracy: 0.5, "tout le côté droit")
        XCTAssertEqual(half.minX, rect.midX, accuracy: 1.0, "et rien du côté gauche")
        XCTAssertEqual(half.maxY, rect.maxY - inset, accuracy: 0.5)
    }

    func testTheLengthIsMeasuredAlongTheContourNotByAngle() {
        // Sur un encart large et bas, un quart de la LONGUEUR n'atteint pas encore le côté droit (un quart d'angle y serait).
        let quarter = CountdownBorderShape().path(in: rect).trimmedPath(from: 0, to: 0.25).boundingRect
        XCTAssertLessThan(quarter.maxY, rect.maxY - inset, "pas encore en bas")
        XCTAssertGreaterThan(quarter.minX, rect.midX - 0.5)
    }

    // MARK: - La couleur

    func testTheColourIsStatusSafeAboveTheLastQuarter() {
        for fraction in [1.0, 0.9, 0.6, 0.5, 0.26, CountdownBorder.warmingStartsBelow] {
            XCTAssertEqual(CountdownBorder.color(fraction: fraction), colorStatusSafe, "f = \(fraction)")
        }
    }

    func testTheColourWarmsGraduallyTowardsStatusWarningInTheLastQuarter() {
        XCTAssertEqual(CountdownBorder.warmth(fraction: 0.25), 0, accuracy: 0.0001)
        XCTAssertEqual(CountdownBorder.warmth(fraction: 0.125), 0.5, accuracy: 0.0001)
        XCTAssertEqual(CountdownBorder.warmth(fraction: 0), 1, accuracy: 0.0001)
        XCTAssertEqual(CountdownBorder.color(fraction: 0), colorStatusWarning)
        var previousGreen = colorStatusSafe.green
        for step in stride(from: 0.25, through: 0, by: -0.025) {
            let green = CountdownBorder.color(fraction: step).green
            XCTAssertLessThanOrEqual(green, previousGreen, "le vert-jaune devient orange sans retour en arrière")
            previousGreen = green
        }
        let middle = CountdownBorder.color(fraction: 0.125)
        XCTAssertNotEqual(middle, colorStatusSafe)
        XCTAssertNotEqual(middle, colorStatusWarning)
    }

    func testTheColourIsNeverRedForAnyFractionIncludingOutOfRangeOnes() {
        for fraction in stride(from: -0.5, through: 1.5, by: 0.01) {
            let color = CountdownBorder.color(fraction: fraction)
            XCTAssertNotEqual(color, colorStatusDanger)
            XCTAssertGreaterThanOrEqual(color.green, colorStatusWarning.green, "jamais plus rouge que le orange")
        }
    }

    func testTheMinuteFractionsOfTheSharedVectorsGiveTheExpectedColours() {
        // 22 min : la bordure s'est réchauffée ; 1 h : toujours status/safe.
        let last = CountdownBorder.color(fraction: Double(22 * 60) / 7200)
        XCTAssertNotEqual(last, colorStatusSafe)
        XCTAssertEqual(CountdownBorder.color(fraction: 0.5), colorStatusSafe)
    }

    // MARK: - Les animations

    func testTheBorderGlidesIn600MillisecondsAtEachMinuteAndTheFadeIs200() {
        XCTAssertEqual(CountdownBorder.glideDuration(reduceMotion: false), 0.6)
        XCTAssertEqual(CountdownBorder.fadeDuration(reduceMotion: false), 0.2)
    }

    func testReduceMotionMakesEverythingInstant() {
        XCTAssertNil(CountdownBorder.glideDuration(reduceMotion: true))
        XCTAssertNil(CountdownBorder.fadeDuration(reduceMotion: true))
    }
}
