import SwiftUI
import UIKit
import XCTest
@testable import Miff

/// Les couleurs du logo viennent des tokens du design system (pas de copie à tenir à jour), et les valeurs de la marque de
/// Google sont celles de leurs directives.
final class LogoAndBrandTests: XCTestCase {
    func testTheGoogleLogoHasItsFourColorsDrawn() {
        for path in [GoogleBrand.redPath, GoogleBrand.bluePath, GoogleBrand.yellowPath, GoogleBrand.greenPath] {
            XCTAssertGreaterThan(path.count, 4)
            for command in path {
                if case .curve(let control1, let control2, let end) = command {
                    for point in [control1, control2, end] {
                        XCTAssertTrue((-1.0...49.0).contains(point.px) && (-1.0...49.0).contains(point.py))
                    }
                }
            }
        }
    }

    func testTheGoogleButtonFollowsTheBrandGuidelines() {
        // Thème clair : fond blanc, bordure #747775, texte #1F1F1F.
        XCTAssertEqual(GoogleBrand.fill.hexString, "#FFFFFF")
        XCTAssertEqual(GoogleBrand.border.hexString, "#747775")
        XCTAssertEqual(GoogleBrand.text.hexString, "#1F1F1F")
        // Le texte du bouton reste lisible sur son fond (contraste d'au moins 4,5 pour 1).
        XCTAssertGreaterThanOrEqual(GoogleBrand.text.contrastRatio(with: GoogleBrand.fill), 4.5)
    }

    func testTheOfficialLogoUsesTheBrandColorsOfTheDesignSystem() {
        // Lettres bleues, coeur jaune-vert de la marque : les tokens, jamais une valeur recopiée.
        XCTAssertEqual(colorActionPrimary.hexString, "#043CD1")
        XCTAssertEqual(colorBrandPrimary.hexString, "#D1D828")
        XCTAssertEqual(colorTextOnAction.hexString, "#FFFFFF")
    }

    func testTheLaunchBackgroundIsTheActionBlue() throws {
        // La couleur de l'écran de lancement système est celle du token (la même qu'Android).
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
            .appendingPathComponent("Miff/Resources/Assets.xcassets/LaunchBackground.colorset/Contents.json")
        let data = try Data(contentsOf: url)
        let root = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        let colors = try XCTUnwrap(root["colors"] as? [[String: Any]])
        let components = try XCTUnwrap((colors.first?["color"] as? [String: Any])?["components"] as? [String: String])

        XCTAssertEqual(components["red"], "0x04")
        XCTAssertEqual(components["green"], "0x3C")
        XCTAssertEqual(components["blue"], "0xD1")
    }

    func testTheCarouselPhotosAreInTheAssetCatalog() throws {
        for name in ["Thomas", "Amandine", "Maman", "Salon", "Map"] {
            XCTAssertNotNil(UIImage(named: "OnboardingPhoto\(name)"), name)
        }
        XCTAssertNotNil(UIImage(named: "LaunchMark"))
    }
}
