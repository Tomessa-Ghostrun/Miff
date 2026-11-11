import XCTest
@testable import Miff

/// Retour du PM (TestFlight, 2026-11-11) : glisser sur le carrousel de Miffs du Profil ouvrait la carte « Créer une Miff » dès le relâchement.
/// Cause : un `Button` sous le doigt (la carte glisse avec le doigt et reste dessous : le bouton se déclenchait). Désormais UN seul geste décide
/// (`CarouselTouch`) et les cartes n'ont aucun bouton.
final class ProfileCarouselTouchTests: XCTestCase {
    // MARK: - Les seuils (règles pures)

    func testAFingerThatStaysPutIsATap() {
        XCTAssertEqual(CarouselTouch.phase(translation: .zero), .undecided)
        XCTAssertTrue(CarouselTouch.isTap(translation: .zero, exceededSlop: false))
        XCTAssertTrue(CarouselTouch.isTap(translation: CGSize(width: 4, height: -3), exceededSlop: false))
    }

    func testTheThresholdIsTenPoints() {
        XCTAssertEqual(CarouselTouch.slop, 10)
        // Pile au seuil : encore un toucher.
        XCTAssertEqual(CarouselTouch.phase(translation: CGSize(width: 10, height: 0)), .undecided)
        XCTAssertEqual(CarouselTouch.phase(translation: CGSize(width: 10.5, height: 0)), .horizontal)
        XCTAssertEqual(CarouselTouch.phase(translation: CGSize(width: 0, height: -10.5)), .vertical)
    }

    func testAnySwipeBeyondTheThresholdIsNeverATap() {
        for width in [11, 20, 80, 200, -11, -90] as [CGFloat] {
            let translation = CGSize(width: width, height: 2)
            XCTAssertEqual(CarouselTouch.phase(translation: translation), .horizontal)
            XCTAssertFalse(CarouselTouch.isTap(translation: translation, exceededSlop: true))
            XCTAssertFalse(CarouselTouch.isTap(translation: translation, exceededSlop: false))
        }
    }

    func testAVerticalScrollIsNeitherATapNorASwipe() {
        let translation = CGSize(width: 3, height: 60)

        XCTAssertEqual(CarouselTouch.phase(translation: translation), .vertical)
        XCTAssertFalse(CarouselTouch.isTap(translation: translation, exceededSlop: true))
    }

    func testAFingerThatWentFarAndCameBackIsNotATap() {
        // Il a glissé puis est revenu au point de départ : la carte a bougé sous son doigt, ce n'est pas un toucher.
        XCTAssertFalse(CarouselTouch.isTap(translation: .zero, exceededSlop: true))
    }

    func testTheDominantAxisDecides() {
        XCTAssertEqual(CarouselTouch.phase(translation: CGSize(width: 40, height: 30)), .horizontal)
        XCTAssertEqual(CarouselTouch.phase(translation: CGSize(width: 30, height: 40)), .vertical)
        // Égalité : le défilement de la page.
        XCTAssertEqual(CarouselTouch.phase(translation: CGSize(width: 30, height: 30)), .vertical)
    }

    // MARK: - Les sources : aucun bouton dans le carrousel

    private func carouselCode() throws -> String {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Main
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
            .appendingPathComponent("Miff/Views/Main/ProfileMiffCarousel.swift")
        guard FileManager.default.fileExists(atPath: url.path) else {
            throw XCTSkip("Source introuvable (\(url.path)).")
        }
        // Le code seulement : les lignes de commentaire (qui expliquent pourquoi il n'y a plus de bouton) ne comptent pas.
        return try String(contentsOf: url, encoding: .utf8)
            .split(separator: "\n", omittingEmptySubsequences: false)
            .filter { !$0.trimmingCharacters(in: .whitespaces).hasPrefix("//") }
            .joined(separator: "\n")
    }

    func testTheCarouselCardsHaveNoButtonAndNoSecondTapHandler() throws {
        let code = try carouselCode()

        // Un bouton sous le doigt se déclenche au relâchement d'un glissement.
        XCTAssertFalse(code.contains("Button("))
        XCTAssertFalse(code.contains("onTapGesture"), "un second geste de toucher concurrencerait le glissement")
        XCTAssertFalse(code.contains("NavigationLink"))
    }

    func testTheSingleGestureDecidesTapOrSwipeWithThePureRules() throws {
        let code = try carouselCode()

        XCTAssertTrue(code.contains("DragGesture(minimumDistance: 0)"), "UN geste pour toucher et glisser")
        XCTAssertTrue(code.contains("CarouselTouch.isTap("), "le toucher passe par la règle du seuil")
        // VoiceOver ouvre la carte par son action par défaut.
        XCTAssertTrue(code.contains(".accessibilityAction(.default"))
        XCTAssertEqual(code.components(separatedBy: "DragGesture(").count - 1, 1, "un seul geste de glissement")
    }
}
