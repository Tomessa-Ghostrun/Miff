import XCTest
@testable import Miff

/// Ce que la répartition du temps ne doit JAMAIS faire, vérifié sur les SOURCES pour qu'un changement ultérieur ne le casse pas sans que
/// personne le voie : aucune lecture ni écriture Firestore de plus (TS-1), aucun événement Analytics, journal de debug ou rapport de plantage
/// (TS-4), aucune position lue (TIME-3), aucun code partagé avec `DetectionJournal` (TS-25), aucune bibliothèque de graphique (TS-24), et
/// l'outil de données de test absent du build de publication (TS-23). Miroir des tests de sources d'Android.
final class TimeShareSourcesTests: XCTestCase {
    private var iosRoot: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // TimeShare
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
    }

    private func read(_ relative: String) throws -> String {
        let url = iosRoot.appendingPathComponent(relative)
        guard FileManager.default.fileExists(atPath: url.path) else {
            throw XCTSkip("Fichier introuvable (\(url.path)).")
        }
        return try String(contentsOf: url, encoding: .utf8)
    }

    /// Les sources de la fonctionnalité qui tournent dans le build de publication.
    private func shippedFiles() throws -> [String] {
        var files: [String] = []
        for folder in ["Models/TimeShare", "Services/TimeShare", "Views/Profile"] {
            let url = iosRoot.appendingPathComponent("Miff/\(folder)")
            let found = try FileManager.default.contentsOfDirectory(atPath: url.path)
            let wanted = found.filter { name in
                name.hasSuffix(".swift") && (folder != "Views/Profile" || name.hasPrefix("TimeShare"))
            }
            files += wanted.map { "Miff/\(folder)/\($0)" }
        }
        files += [
            "Miff/ViewModels/TimeShareViewModel.swift", "Miff/Services/Tracking/TrackingEngine+History.swift",
            "Miff/DesignSystem/ChartTokens.swift", "Miff/App/AppEnvironment+TimeShare.swift"
        ]
        return files
    }

    private func codeLines(_ relative: String) throws -> [String] {
        try read(relative).components(separatedBy: "\n").filter {
            !$0.trimmingCharacters(in: .whitespaces).hasPrefix("//")
        }
    }

    private func offenders(_ fragments: [String]) throws -> [String] {
        var found: [String] = []
        for file in try shippedFiles() {
            for line in try codeLines(file) where fragments.contains(where: { line.contains($0) }) {
                found.append("\(file) : \(line.trimmingCharacters(in: .whitespaces))")
            }
        }
        return found
    }

    func testTheSourcesAreFound() throws {
        XCTAssertGreaterThanOrEqual(try shippedFiles().count, 18)
    }

    // MARK: - TS-1, TS-4 : rien n'est envoyé

    func testNothingTouchesFirestoreTheNetworkOrAnalytics() throws {
        let found = try offenders([
            "Firestore", "import Firebase", "AnalyticsTracker", "AnalyticsEvent", "Crashlytics", "URLSession",
            "URLRequest",
            "UIPasteboard"
        ])

        XCTAssertEqual(found, [])
    }

    func testNothingIsPrintedOrLogged() throws {
        let found = try offenders(["print(", "NSLog", "os_log", "Logger(", "debugPrint", "dump("])

        XCTAssertEqual(found, [])
    }

    func testNoPositionIsEverRead() throws {
        let found = try offenders(["import CoreLocation", "CLLocation", "import MapKit", "PositionSource", "Fix("])

        XCTAssertEqual(found, [])
    }

    // MARK: - TS-24, TS-25

    func testNoChartLibraryAndNoSwiftChartsThatNeedIOS17() throws {
        let found = try offenders(["import Charts", "SectorMark", "import SwiftUICharts"])

        XCTAssertEqual(found, [])
        let project = try read("project.yml")
        XCTAssertFalse(project.contains("Charts"), "aucune dépendance de graphique")
    }

    func testTheJournalSharesNoCodeWithTheTestToolDetectionJournal() throws {
        let files = ["PresenceHistory.swift", "PresenceHistoryFileStore.swift"]
        for file in files.map({ "Miff/Services/TimeShare/\($0)" }) {
            let lines = try codeLines(file).joined(separator: "\n")
            XCTAssertFalse(lines.contains("DetectionJournal"), file)
            XCTAssertFalse(lines.contains("JournalKind"), file)
        }
        let environment = try codeLines("Miff/App/TrackingServices.swift").joined(separator: "\n")
        XCTAssertTrue(environment.contains("PresenceHistoryFileStore()"), "actif aussi en publication")
        XCTAssertFalse(environment.contains("PresenceHistoryFileStore() #if"))
    }

    func testTheJournalFileIsExcludedFromBackupsAndWrittenWithTheLockedPhoneInMind() throws {
        let store = try codeLines("Miff/Services/TimeShare/PresenceHistoryFileStore.swift").joined(separator: "\n")

        XCTAssertTrue(store.contains("isExcludedFromBackup = true"))
        XCTAssertTrue(store.contains("completeFileProtectionUntilFirstUserAuthentication"))
        XCTAssertFalse(store.contains("completeFileProtection,"), "la protection complète bloquerait l'écriture")
    }

    // MARK: - Branchement sur le suivi

    func testTheEngineNotesTheHistoryAtEveryPlaceWhereItChangesAMiffPresence() throws {
        let expectations: [(file: String, fragments: [String])] = [
            ("TrackingEngine+Events.swift", ["noteHistory(miffId, change.fact)", "noteUnknown(miffId, at: now)"]),
            ("TrackingEngine+Refresh.swift", ["noteLeft(", "noteUnknown(miffId, at: now)", "history.forgetPlaces("]),
            ("TrackingEngine+Resume.swift", ["noteHistory(miffId, fact)"]),
            ("TrackingEngine+Travel.swift", ["noteUnknown(miffId, at: now)"]),
            ("TrackingEngine+TravelEnd.swift", ["noteHistory(miffId, fact)"]),
            ("TrackingEngine+Writes.swift", ["noteUnknown(miffId, at: now)"]),
            ("TrackingEngine.swift", ["history.clearAll()", "noteUnknown(miffId, at: now)"])
        ]
        for (file, fragments) in expectations {
            let text = try codeLines("Miff/Services/Tracking/\(file)").joined(separator: "\n")
            for fragment in fragments {
                XCTAssertTrue(text.contains(fragment), "\(file) devrait contenir \(fragment)")
            }
        }
    }

    func testThePresenceIsStillComputedByASingleCalculator() throws {
        let history = try codeLines("Miff/Services/Tracking/TrackingEngine+History.swift").joined(separator: "\n")

        XCTAssertFalse(history.contains("derive("), "le journal recopie, il ne recalcule jamais la présence")
        XCTAssertFalse(history.contains("PresenceMachine"))
    }

    // MARK: - TS-23 : les données de test n'existent pas en publication

    /// Les lignes d'un fichier qui sont HORS d'un bloc `#if DEBUG || INTERNAL` (ou `#if DEBUG`).
    private func linesOutsideTestBlocks(_ relative: String) throws -> [String] {
        var depth = 0
        var outside: [String] = []
        for line in try read(relative).components(separatedBy: "\n") {
            let trimmed = line.trimmingCharacters(in: .whitespaces)
            if trimmed.hasPrefix("#if DEBUG") {
                depth += 1
                continue
            }
            if trimmed.hasPrefix("#endif") && depth > 0 {
                depth -= 1
                continue
            }
            if depth == 0 && !trimmed.hasPrefix("//") {
                outside.append(trimmed)
            }
        }
        return outside
    }

    func testTheDemoToolIsCompiledInTestBuildsOnly() throws {
        let demo = try read("Miff/Diagnostic/TimeShareDemo.swift")

        XCTAssertEqual(demo.components(separatedBy: "\n").first, "#if DEBUG || INTERNAL")
        XCTAssertTrue(demo.hasSuffix("#endif\n"))
        XCTAssertTrue(demo.contains("TIMESHAREDEMO"))
    }

    func testNothingOutsideTheTestBlocksReferencesTheDemoTool() throws {
        let app = iosRoot.appendingPathComponent("Miff")
        guard let files = FileManager.default.enumerator(at: app, includingPropertiesForKeys: nil) else {
            throw XCTSkip("Sources introuvables.")
        }
        var offenders: [String] = []
        for case let file as URL in files where file.pathExtension == "swift" {
            let tail = String(file.path.dropFirst(app.path.count + 1)).replacingOccurrences(of: "\\", with: "/")
            let relative = "Miff/" + tail
            if relative.hasPrefix("Miff/Diagnostic/") {
                continue
            }
            for line in try linesOutsideTestBlocks(relative) {
                let names = ["TimeShareDemoData", "TimeShareDemoRow", "TimeShareDemoMode", "cycleDemo", "demoMode"]
                for name in names where line.contains(name) {
                    offenders.append("\(relative) : \(line)")
                }
            }
        }

        XCTAssertEqual(offenders, [])
    }

    func testTheCiLooksForTheDemoMarkerInThePublicationBuild() throws {
        let script = try read("ci/check-binaries.sh")

        XCTAssertTrue(script.contains("TIMESHAREDEMO"))
        XCTAssertTrue(script.contains("TimeShareDemoData"))
    }

    // MARK: - Les données de test elles-mêmes (builds de test)

    func testTheDemoDataGivesAChartForEveryWindowWithoutAnythingInTheFuture() throws {
        let places = [
            TimeSharePlace(id: "p1", name: "Maison", type: .trusted, category: .home),
            TimeSharePlace(id: "p2", name: "Bureau", type: .trusted, category: .work),
            TimeSharePlace(id: "p3", name: "Salle", type: .trusted, category: .sport),
            TimeSharePlace(id: "r1", name: "Chantier", type: .risk, category: nil)
        ]
        let now = TimeShareFixtures.now

        let facts = TimeShareDemoData.facts(miffId: "m1", places: places, nowMillis: now)

        XCTAssertFalse(facts.isEmpty)
        XCTAssertTrue(facts.allSatisfy { $0.atMillis <= now })
        XCTAssertEqual(facts.map { $0.atMillis }, facts.map { $0.atMillis }.sorted())
        for window in TimeShareLimits.windowsDays {
            let outcome = TimeShareCalculator.compute(facts: facts, places: places, windowDays: window, nowMillis: now)
            guard case .ready(let result) = outcome else {
                return XCTFail("\(window) jours : des données de test devraient suffire")
            }
            XCTAssertEqual(result.rows.map { $0.percent }.reduce(0, +), 100)
        }
    }

    func testTheDemoDataWithoutAnyPlaceIsOnlyElsewhereOrNothing() {
        let facts = TimeShareDemoData.facts(miffId: "m1", places: [], nowMillis: TimeShareFixtures.now)

        XCTAssertTrue(facts.allSatisfy { $0.state == .out && $0.placeId == nil })
    }

    func testTheDemoDataCanShowTheNotEnoughDataStateAtTwoAndSixDays() {
        let places = [TimeSharePlace(id: "p1", name: "Maison", type: .trusted, category: .home)]
        let now = TimeShareFixtures.now
        let expected: [(days: Int, left: Int)] = [(2, 5), (6, 1)]

        for item in expected {
            let facts = TimeShareDemoData.facts(miffId: "m1", places: places, nowMillis: now, historyDays: item.days)
            let outcome = TimeShareCalculator.compute(facts: facts, places: places, windowDays: 7, nowMillis: now)

            XCTAssertEqual(outcome, .notEnough(daysLeft: item.left), "\(item.days) jours d'historique")
        }
    }

    func testTheDemoCyclesThroughEveryStateAndComesBack() {
        var mode = TimeShareDemoMode.off
        var seen: [Int?] = []
        for _ in 0..<4 {
            mode = mode.next
            seen.append(mode.historyDays)
        }

        XCTAssertEqual(seen, [TimeShareDemoData.chartDays, 2, 6, nil])
    }
}
