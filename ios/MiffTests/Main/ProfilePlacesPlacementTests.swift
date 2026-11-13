import XCTest

/// L'ordre des blocs de l'onglet Profil (décision du PM du 2026-11-11, inchangée) : l'en-tête (photo, nom, roue), le carrousel « Mes
/// Miffs », les lieux de la Miff du carrousel, puis l'encart « Mode voyage » EN DERNIER. Analyse des sources de `ProfileTabView` : le
/// même principe que `TravelCardPlacementTests`.
final class ProfilePlacesPlacementTests: XCTestCase {
    private func body() throws -> [String] {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Main
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
            .appendingPathComponent("Miff/Views/Main/ProfileTabView.swift")
        let text = try String(contentsOf: url, encoding: .utf8)
        let lines = text.components(separatedBy: "\n")
        let start = try XCTUnwrap(lines.firstIndex { $0.contains("var body: some View") })
        let end = try XCTUnwrap(lines[start...].firstIndex { $0.contains(".background(gradientProfileHeader") })
        return Array(lines[start..<end])
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.hasPrefix("//") }
    }

    func testTheBlocksComeInTheOrderHeaderMiffsPlacesTravel() throws {
        let lines = try body()
        func index(_ name: String) throws -> Int {
            try XCTUnwrap(lines.firstIndex { $0 == name || $0.hasPrefix(name) }, name)
        }

        let header = try index("header")
        let unavailable = try index("unavailable")
        let miffs = try index("miffCards")
        let places = try index("placesSection")
        let travel = try index("TravelCardView(")

        XCTAssertLessThan(header, unavailable)
        XCTAssertLessThan(unavailable, miffs)
        XCTAssertLessThan(miffs, places, "les lieux sont sous le carrousel")
        XCTAssertLessThan(places, travel, "l'encart « Mode voyage » reste en dernier")
        XCTAssertEqual(lines.lastIndex { $0 == "placesSection" || $0.hasPrefix("TravelCardView(") }, travel)
    }

    func testThePlacesAreDrawnForEveryMemberNotJustTheCreator() throws {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .appendingPathComponent("Miff/Views/Main/ProfileTabView.swift")
        let text = try String(contentsOf: url, encoding: .utf8)

        // La section n'est conditionnée que par la lecture (`ProfilePlacesPresentation.make`), jamais par le rôle.
        XCTAssertTrue(text.contains("ProfilePlacesPresentation.make("))
        XCTAssertFalse(text.contains("if presentation.isCreator"))
        XCTAssertFalse(text.contains("isCreator {"), "le Profil ne cache pas la liste aux membres")
    }
}
