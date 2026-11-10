// Les assertions citent des cas entiers : leurs lignes dépassent la limite.
// swiftlint:disable line_length
import XCTest
@testable import Miff

// La mise en page du plein écran de la photo d'un proche (correctif de la recette TestFlight, iPhone 11 Pro) : la légende et les boutons sont TOUJOURS
// entiers ; la photo prend l'espace qui reste sous la zone sûre ; quand le pied ne tient pas il défile dans une hauteur bornée.
final class PhotoFullScreenLayoutTests: XCTestCase {
    private let normal = PhotoFullScreenLayout.footerEstimate

    /// La hauteur sous la zone sûre de quelques iPhone en portrait (points), approximative.
    private let iPhoneSE1: CGFloat = 548
    private let iPhoneSE: CGFloat = 647
    private let iPhone11Pro: CGFloat = 734
    private let iPhone16: CGFloat = 760

    private func isInline(_ footer: PhotoFullScreenLayout.Footer) -> Bool {
        footer == .inline
    }

    func testTheFooterFitsBelowThePhotoOnEveryPortraitIPhoneWithNormalText() {
        for height in [iPhoneSE1, iPhoneSE, iPhone11Pro, iPhone16] {
            XCTAssertTrue(isInline(PhotoFullScreenLayout.footer(screenHeight: height, footerHeight: normal)), "\(height)")
        }
    }

    func testThePhotoKeepsItsMinimumHeightWhenTheFooterIsInline() {
        for height in [iPhoneSE1, iPhoneSE, iPhone11Pro, iPhone16] {
            let left = height - PhotoFullScreenLayout.topBarHeight - normal

            XCTAssertGreaterThanOrEqual(left, PhotoFullScreenLayout.minPhotoHeight, "\(height)")
        }
    }

    func testInLandscapeTheFooterScrollsInsideAFloorHeightAndNeverVanishes() {
        let landscape: CGFloat = 375 - 21

        guard case .scrolling(let maxHeight) = PhotoFullScreenLayout.footer(screenHeight: landscape, footerHeight: normal) else {
            return XCTFail("le pied défile")
        }

        XCTAssertGreaterThanOrEqual(maxHeight, PhotoFullScreenLayout.minScrollingFooterHeight)
    }

    func testWithTheLargestTextTheFooterScrollsAndThePhotoKeepsItsPlace() {
        let large = normal * 2.5

        for height in [iPhoneSE, iPhone11Pro, iPhone16] {
            guard case .scrolling(let maxHeight) = PhotoFullScreenLayout.footer(screenHeight: height, footerHeight: large) else {
                return XCTFail("le pied défile à \(height)")
            }
            let photo = height - PhotoFullScreenLayout.topBarHeight - maxHeight

            XCTAssertGreaterThanOrEqual(photo, PhotoFullScreenLayout.minPhotoHeight, "la photo garde sa place à \(height)")
            XCTAssertLessThan(maxHeight, large, "borné : le pied n'avale pas l'écran")
        }
    }

    func testTheFooterIsInlineExactlyWhenItFitsInTheRoomLeftByTheBarAndThePhoto() {
        let room = iPhone11Pro - PhotoFullScreenLayout.topBarHeight - PhotoFullScreenLayout.minPhotoHeight

        XCTAssertEqual(PhotoFullScreenLayout.footer(screenHeight: iPhone11Pro, footerHeight: room), .inline)
        XCTAssertEqual(
            PhotoFullScreenLayout.footer(screenHeight: iPhone11Pro, footerHeight: room + 1),
            .scrolling(maxHeight: room)
        )
    }

    func testTheEstimateIsGenerousForATwoLineTitleAndTwoButtons() {
        // Deux lignes de titre (2 x 28), deux lignes de lieu (2 x 20), deux boutons de 46 pt, quatre espaces de 8 pt, marges de 2 x 16.
        let tallest: CGFloat = 2 * 28 + 2 * 20 + 2 * 46 + 4 * 8 + 2 * 16

        XCTAssertGreaterThanOrEqual(PhotoFullScreenLayout.footerEstimate, tallest)
    }

    func testTheLayoutNoLongerDependsOnAMeasuredHeightThatCouldStayAtZero() throws {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Feed
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
            .appendingPathComponent("Miff/Views/Feed/FeedPhotoFullScreen.swift")
        let code = UpdatePrivacyTests.codeLines(try String(contentsOf: url, encoding: .utf8)).joined(separator: "\n")

        XCTAssertFalse(code.contains("PreferenceKey"), "plus de hauteur mesurée : le pied ne peut plus rester à 1 pt")
        XCTAssertTrue(code.contains("PhotoFullScreenLayout.footer(screenHeight:"))
        XCTAssertTrue(code.contains(".layoutPriority(1)"), "la légende et les boutons passent d'abord")
        XCTAssertTrue(code.contains(".hidesInAppSwitcher()"), "le voile des applications récentes est conservé")
    }
}
// swiftlint:enable line_length
