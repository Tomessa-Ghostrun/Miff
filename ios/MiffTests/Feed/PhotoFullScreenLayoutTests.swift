// Les assertions citent des cas entiers : leurs lignes dépassent la limite.
// swiftlint:disable line_length
import XCTest
@testable import Miff

// La mise en page du plein écran de la photo d'un proche (correctif de la recette TestFlight, iPhone 11 Pro) : la barre du haut, la photo (à son minimum)
// et le pied (à son maximum) tiennent TOUJOURS dans l'écran, sur tous les iPhone et en paysage ; la légende et le bouton passent d'abord ; la croix et
// l'icône ne sont jamais rognées.
final class PhotoFullScreenLayoutTests: XCTestCase {
    /// La hauteur sous la zone sûre de quelques iPhone (points), approximative : portrait puis paysage.
    private let portrait: [(String, CGFloat)] = [("SE 1", 548), ("SE", 647), ("11 Pro", 734), ("16", 760)]
    private let landscape: [(String, CGFloat)] = [("SE", 375 - 0), ("11 Pro", 375 - 21), ("16", 393 - 21)]

    private func total(_ screenHeight: CGFloat) -> CGFloat {
        PhotoFullScreenLayout.topBarHeight
            + PhotoFullScreenLayout.minPhotoHeight(screenHeight: screenHeight)
            + PhotoFullScreenLayout.footerMaxHeight(screenHeight: screenHeight)
    }

    func testTheBarThePhotoAtItsMinimumAndTheFooterAtItsMaximumAlwaysFitTheScreen() {
        for (name, height) in portrait + landscape {
            XCTAssertLessThanOrEqual(total(height), height, "\(name) \(height)")
        }
    }

    func testInLandscapeTheSumFitsAndNothingIsCropped() {
        for (name, height) in landscape {
            XCTAssertLessThanOrEqual(total(height), height, "paysage \(name)")
            XCTAssertGreaterThan(PhotoFullScreenLayout.minPhotoHeight(screenHeight: height), 100, "la photo reste visible en paysage \(name)")
            XCTAssertGreaterThan(PhotoFullScreenLayout.footerMaxHeight(screenHeight: height), 100, "le pied reste lisible en paysage \(name)")
        }
    }

    func testOnATallScreenThePhotoKeepsItsFullMinimumAndTheFooterHasRoom() {
        for (name, height) in portrait {
            XCTAssertEqual(PhotoFullScreenLayout.minPhotoHeight(screenHeight: height), PhotoFullScreenLayout.tallPhotoHeight, name)
            XCTAssertGreaterThanOrEqual(PhotoFullScreenLayout.footerMaxHeight(screenHeight: height), 290, "légende + bouton tiennent en portrait \(name)")
        }
    }

    func testOnAShortScreenThePhotoNeedsOnlyAShareOfTheSpace() {
        let height: CGFloat = 354
        let available = height - PhotoFullScreenLayout.topBarHeight

        XCTAssertEqual(PhotoFullScreenLayout.minPhotoHeight(screenHeight: height), available * PhotoFullScreenLayout.shortScreenPhotoShare, accuracy: 0.001)
        XCTAssertLessThan(PhotoFullScreenLayout.minPhotoHeight(screenHeight: height), PhotoFullScreenLayout.tallPhotoHeight)
    }

    func testTheSumNeverExceedsTheScreenEvenOnAbsurdlySmallOrZeroHeights() {
        for height in stride(from: CGFloat(0), through: 900, by: 12) {
            XCTAssertLessThanOrEqual(PhotoFullScreenLayout.available(screenHeight: height), max(height, 0))
            XCTAssertGreaterThanOrEqual(PhotoFullScreenLayout.footerMaxHeight(screenHeight: height), 0, "\(height)")
            XCTAssertLessThanOrEqual(
                PhotoFullScreenLayout.minPhotoHeight(screenHeight: height) + PhotoFullScreenLayout.footerMaxHeight(screenHeight: height),
                PhotoFullScreenLayout.available(screenHeight: height) + 0.001,
                "\(height)"
            )
        }
    }

    func testTheLayoutNoLongerDependsOnAMeasuredHeightThatCouldStayAtZero() throws {
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
        XCTAssertTrue(code.contains(".hidesInAppSwitcher()"), "le voile des applications récentes est conservé")
    }
}
// swiftlint:enable line_length
