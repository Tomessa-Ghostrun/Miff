import XCTest
@testable import Miff

/// Ce que la répartition du temps ne doit JAMAIS faire, vérifié sur les SOURCES pour qu'un changement ultérieur ne le casse pas sans que
/// personne le voie : aucune lecture ni écriture Firestore de plus (TS-1), aucun événement Analytics, journal de debug ou rapport de plantage
/// (TS-4), aucune position lue (TIME-3), aucun code partagé avec `DetectionJournal` (TS-25), aucune bibliothèque de graphique (TS-24), et
/// aucun outil de données de test (retiré après la recette du PM). Miroir des tests de sources d'Android.
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

    // MARK: - Aucun outil de données de test

    /// Le PM a validé le graphique : les données de test de la recette ont été retirées. Plus aucune trace dans le code, les ressources, le
    /// catalogue ni le script de contrôle du binaire (les mots sont coupés pour que ce test ne se trouve pas lui-même).
    func testNoToolOfTestDataIsLeftInTheSourcesOrTheCi() throws {
        let forbidden = ["TIMESHARE" + "DEMO", "TimeShare" + "Demo", "ios_diag_" + "timeshare", "cycle" + "Demo"]
        var found: [String] = []
        for folder in ["Miff", "ci"] {
            let root = iosRoot.appendingPathComponent(folder)
            guard let files = FileManager.default.enumerator(at: root, includingPropertiesForKeys: nil) else {
                throw XCTSkip("Sources introuvables.")
            }
            for case let file as URL in files where ["swift", "xcstrings", "sh"].contains(file.pathExtension) {
                let text = try String(contentsOf: file, encoding: .utf8)
                for word in forbidden where text.contains(word) {
                    found.append("\(file.lastPathComponent) : \(word)")
                }
            }
        }

        XCTAssertEqual(found, [])
    }

    func testTheNotEnoughDataStateDrawsNoRing() throws {
        let card = try codeLines("Miff/Views/Profile/TimeShareCard.swift").joined(separator: "\n")
        let donut = try codeLines("Miff/Views/Profile/TimeShareDonut.swift").joined(separator: "\n")
        let tokens = try codeLines("Miff/DesignSystem/ChartTokens.swift").joined(separator: "\n")

        XCTAssertFalse(card.contains("TimeShareDonut(slices: [],"), "ticket §7 : pas d'anneau vide, seulement le texte")
        XCTAssertFalse(donut.contains("slices.isEmpty"))
        XCTAssertFalse(tokens.contains("colorChartEmpty"))
    }
}
