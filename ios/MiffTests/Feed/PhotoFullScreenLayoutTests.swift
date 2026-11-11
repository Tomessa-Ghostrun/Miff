// Les assertions citent des cas entiers : leurs lignes dépassent la limite.
// swiftlint:disable line_length
import XCTest
@testable import Miff

// La mise en page du plein écran de la photo d'un proche (correctifs de la recette TestFlight, iPhone 11 Pro) : la photo REMPLIT la zone au-dessus du pied
// (la zone décide, pas la taille de l'image) ; la photo (à son minimum) et le pied (à son maximum) tiennent TOUJOURS dans l'écran, paysage compris ;
// la légende et le bouton passent d'abord ; la croix et l'icône sont posées sur la photo.
final class PhotoFullScreenLayoutTests: XCTestCase {
    /// La hauteur sous la zone sûre de quelques iPhone (points), approximative : portrait puis paysage.
    private let portrait: [(String, CGFloat)] = [("SE 1", 548), ("SE", 647), ("11 Pro", 734), ("16", 760)]
    private let landscape: [(String, CGFloat)] = [("SE", 375), ("11 Pro", 375 - 21), ("16", 393 - 21)]

    private func total(_ screenHeight: CGFloat) -> CGFloat {
        PhotoFullScreenLayout.minPhotoHeight(screenHeight: screenHeight) + PhotoFullScreenLayout.footerMaxHeight(screenHeight: screenHeight)
    }

    func testThePhotoAtItsMinimumAndTheFooterAtItsMaximumAlwaysFitTheScreen() {
        for (name, height) in portrait + landscape {
            XCTAssertLessThanOrEqual(total(height), height + 0.001, "\(name) \(height)")
        }
    }

    func testInLandscapeNothingIsCroppedAndBothStayUsable() {
        for (name, height) in landscape {
            XCTAssertGreaterThan(PhotoFullScreenLayout.minPhotoHeight(screenHeight: height), 100, "la photo reste visible en paysage \(name)")
            XCTAssertGreaterThan(PhotoFullScreenLayout.footerMaxHeight(screenHeight: height), 100, "le pied reste lisible en paysage \(name)")
        }
    }

    func testOnATallScreenThePhotoKeepsItsFullMinimumAndTheFooterHasRoom() {
        for (name, height) in portrait {
            XCTAssertEqual(PhotoFullScreenLayout.minPhotoHeight(screenHeight: height), PhotoFullScreenLayout.tallPhotoHeight, name)
            XCTAssertGreaterThanOrEqual(PhotoFullScreenLayout.footerMaxHeight(screenHeight: height), 340, "légende + bouton tiennent en portrait \(name)")
        }
    }

    func testOnAShortScreenThePhotoNeedsOnlyAShareOfTheHeight() {
        let height: CGFloat = 354

        XCTAssertEqual(PhotoFullScreenLayout.minPhotoHeight(screenHeight: height), height * PhotoFullScreenLayout.shortScreenPhotoShare, accuracy: 0.001)
        XCTAssertLessThan(PhotoFullScreenLayout.minPhotoHeight(screenHeight: height), PhotoFullScreenLayout.tallPhotoHeight)
    }

    func testTheSumNeverExceedsTheScreenEvenOnAbsurdlySmallOrZeroHeights() {
        for height in stride(from: CGFloat(0), through: 900, by: 12) {
            XCTAssertGreaterThanOrEqual(PhotoFullScreenLayout.footerMaxHeight(screenHeight: height), 0, "\(height)")
            XCTAssertLessThanOrEqual(total(height), PhotoFullScreenLayout.available(screenHeight: height) + 0.001, "\(height)")
        }
    }

    // MARK: - La photo remplit la zone (retour du PM : « toute petite au milieu »)

    func testASmallImageIsEnlargedToFillTheAreaItIsNeverLimitedByItsOwnSize() {
        // Le JPEG publié est petit (≤ 150 Ko, ~461 x 1 024 px) : la zone d'un 11 Pro est bien plus grande en points.
        let image = CGSize(width: 200, height: 440)
        let area = CGSize(width: 375, height: 614)

        let shown = PhotoFullScreenLayout.fittedSize(imageSize: image, in: area)

        XCTAssertEqual(shown.height, area.height, accuracy: 0.001, "elle remplit la hauteur de la zone")
        XCTAssertGreaterThan(shown.width, image.width, "agrandie au-delà de sa taille propre")
        XCTAssertLessThanOrEqual(shown.width, area.width + 0.001)
        XCTAssertEqual(shown.width / shown.height, image.width / image.height, accuracy: 0.0001, "jamais déformée")
    }

    func testAWideImageFillsTheWidthAndATallOneTheHeightNeverBeyondTheArea() {
        let area = CGSize(width: 375, height: 600)

        let wide = PhotoFullScreenLayout.fittedSize(imageSize: CGSize(width: 1_000, height: 500), in: area)
        let tall = PhotoFullScreenLayout.fittedSize(imageSize: CGSize(width: 500, height: 1_000), in: area)

        XCTAssertEqual(wide.width, area.width, accuracy: 0.001)
        XCTAssertEqual(tall.height, area.height, accuracy: 0.001)
        for shown in [wide, tall] {
            XCTAssertLessThanOrEqual(shown.width, area.width + 0.001)
            XCTAssertLessThanOrEqual(shown.height, area.height + 0.001)
        }
    }

    func testUnusableSizesGiveNoPhoto() {
        XCTAssertEqual(PhotoFullScreenLayout.fittedSize(imageSize: .zero, in: CGSize(width: 100, height: 100)), .zero)
        XCTAssertEqual(PhotoFullScreenLayout.fittedSize(imageSize: CGSize(width: 10, height: 10), in: .zero), .zero)
    }

    func testTheLayoutDoesNotDependOnAMeasuredHeightAndThePhotoFillsTheZoneWithTheIconsOnTopOfIt() throws {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Feed
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
            .appendingPathComponent("Miff/Views/Feed/FeedPhotoFullScreen.swift")
        let code = UpdatePrivacyTests.codeLines(try String(contentsOf: url, encoding: .utf8)).joined(separator: "\n")

        XCTAssertFalse(code.contains("PreferenceKey"), "plus de hauteur mesurée : le pied ne peut plus rester à 1 pt")
        XCTAssertTrue(code.contains("ViewThatFits(in: .vertical)"), "le pied entier si ça tient, sinon il défile : jamais de bande noire en trop")
        XCTAssertTrue(code.contains("PhotoFullScreenLayout.footerMaxHeight(screenHeight:"))
        XCTAssertTrue(code.contains("PhotoFullScreenLayout.minPhotoHeight(screenHeight:"))
        XCTAssertTrue(code.contains(".layoutPriority(1)"), "la légende et le bouton passent d'abord")
        XCTAssertTrue(code.contains(".scaledToFit()"), "la photo entière, jamais recadrée")
        XCTAssertFalse(code.contains(".scaledToFill()"))
        XCTAssertTrue(code.contains(".frame(width: area.size.width, height: area.size.height)"), "la photo prend toute la zone, pas sa taille propre")
        XCTAssertTrue(code.contains(".zIndex(1)"), "la croix et l'icône sont posées sur la photo")
        XCTAssertTrue(code.contains(".hidesInAppSwitcher()"), "le voile des applications récentes est conservé")
    }
}
// swiftlint:enable line_length
