import XCTest
@testable import Miff

/// La navigation de la coquille : UNE seule pile pour toute l'app (celle de `MiffHomeHost`). Une carte « Mes Miffs » touchée sur le
/// Profil faisait planter l'app (build TestFlight 16) : la coquille posait une seconde `NavigationStack` dans l'onglet Profil, à
/// l'intérieur de celle de l'hôte où l'écran de la Miff est empilé. Ces tests gardent la cause et les routes.
final class MainNavigationTests: XCTestCase {
    private func sourceFiles(in folder: String) throws -> [URL] {
        let sources = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Main
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
            .appendingPathComponent("Miff")
            .appendingPathComponent(folder)
        guard let files = FileManager.default.enumerator(at: sources, includingPropertiesForKeys: nil) else {
            throw XCTSkip("Sources introuvables (\(sources.path)).")
        }
        return files.compactMap { $0 as? URL }.filter { $0.pathExtension == "swift" }
    }

    private func codeLines(of file: URL) throws -> [String] {
        try String(contentsOf: file, encoding: .utf8)
            .components(separatedBy: "\n")
            .filter { !$0.trimmingCharacters(in: .whitespaces).hasPrefix("//") }
    }

    func testTheShellAndTheSettingsScreensCreateNoNavigationStack() throws {
        let files = try sourceFiles(in: "Views/Main") + sourceFiles(in: "Views/Settings")

        XCTAssertGreaterThan(files.count, 5, "Presque aucun fichier trouvé : chemin cassé ?")
        for file in files {
            for line in try codeLines(of: file) {
                let message = "\(file.lastPathComponent) crée une pile imbriquée (voir la pile de l'hôte)"
                XCTAssertFalse(line.contains("NavigationStack"), message)
            }
        }
    }

    func testEverySettingsScreenCanBePushedOnTheHostStack() {
        for screen in SettingsRoute.allCases {
            let route = MiffRoute.settings(screen)

            XCTAssertTrue(route.isSettingsScreen)
            XCTAssertFalse(route.belongs(to: "anyMiff"), "Un écran des Réglages reste ouvert")
        }
        let routes = Set(SettingsRoute.allCases.map { MiffRoute.settings($0) })
        XCTAssertEqual(routes.count, SettingsRoute.allCases.count)
    }

    func testAMiffScreenOpenedFromTheProfileSitsOnTheSameStackAsTheSettings() {
        var path: [MiffRoute] = []

        path.append(.miff("abc"))
        path.append(.settings(.settings))

        XCTAssertEqual(path.count, 2)
        XCTAssertFalse(path[0].isSettingsScreen)
        XCTAssertTrue(path[1].isSettingsScreen)
    }

    func testTheLastMiffDisappearingRemovesOnlyTheSettingsScreens() {
        var path: [MiffRoute] = [.settings(.settings), .settings(.account), .join, .miff("abc")]

        path.removeAll { $0.isSettingsScreen }

        XCTAssertEqual(path, [.join, .miff("abc")])
    }

    func testLosingAccessToAMiffLeavesTheSettingsScreensOpen() {
        var path: [MiffRoute] = [.settings(.settings), .miff("abc"), .placeDetail(miffId: "abc", placeId: "p")]

        path.removeAll { $0.belongs(to: "abc") }

        XCTAssertEqual(path, [.settings(.settings)])
    }
}
