import XCTest

/// MODE VOYAGE : l'encart « Mode voyage » est le DERNIER bloc de l'onglet Profil (décision du PM après la recette iOS du build 26). Analyse
/// des sources de `ProfileTabView` : la carte vient après tous les autres blocs du défilement (l'en-tête, le message d'indisponibilité et
/// les cartes des Miffs), jamais avant.
final class TravelCardPlacementTests: XCTestCase {
    private func body() throws -> [String] {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Travel
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
            .appendingPathComponent("Miff/Views/Main/ProfileTabView.swift")
        let text = try String(contentsOf: url, encoding: .utf8)
        let lines = text.components(separatedBy: "\n")
        let start = try XCTUnwrap(lines.firstIndex { $0.contains("var body: some View") })
        let end = try XCTUnwrap(lines[start...].firstIndex { $0.contains(".background(gradientProfileHeader") })
        return Array(lines[start..<end]).filter { !$0.trimmingCharacters(in: .whitespaces).hasPrefix("//") }
    }

    func testTheTravelCardIsTheLastBlockOfTheProfile() throws {
        let lines = try body()
        let travel = try XCTUnwrap(lines.firstIndex { $0.contains("TravelCardView(") })
        let others = ["header", "unavailable", "miffCards"].compactMap { name in
            lines.firstIndex { $0.trimmingCharacters(in: .whitespaces) == name }
        }

        XCTAssertEqual(others.count, 3, "les trois autres blocs sont dans le défilement")
        for index in others {
            XCTAssertLessThan(index, travel, "la carte voyage vient après chaque autre bloc")
        }
        let lastBlock = lines.lastIndex { $0.contains("miffCards") || $0.contains("TravelCardView(") }
        XCTAssertEqual(lastBlock, travel)
    }
}
