// Les assertions citent des cas entiers : leurs lignes dépassent la limite.
// swiftlint:disable line_length
import XCTest
@testable import Miff

/// Ce que « tirer pour actualiser » ne doit JAMAIS faire, vérifié sur les SOURCES pour qu'un changement ultérieur ne le casse pas sans
/// que personne le voie (demande du PM du 2026-11-17, `docs/tickets/feed-refresh-position.md`) : le Feed ne touche ni au système de
/// localisation, ni à une position, ni à Firestore pour cela ; il ne fait que DEMANDER au suivi, sans l'attendre, après les gardes du
/// geste (hors connexion, limite de 15 s) ; une seule porte d'entrée vers le moteur ; aucun journal ni événement hors le journal local.
final class PullRefreshSourcesTests: XCTestCase {
    private var app: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Tracking
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
            .appendingPathComponent("Miff")
    }

    /// Les lignes de code (sans les commentaires) d'un fichier, chemin relatif à `Miff/`.
    private func code(_ relative: String) throws -> String {
        let url = app.appendingPathComponent(relative)
        guard FileManager.default.fileExists(atPath: url.path) else {
            XCTFail("Fichier introuvable : \(relative)")
            return ""
        }
        return strip(try String(contentsOf: url, encoding: .utf8))
    }

    private func strip(_ source: String) -> String {
        source
            .components(separatedBy: "\n")
            .filter { !$0.trimmingCharacters(in: .whitespaces).hasPrefix("//") }
            .joined(separator: "\n")
    }

    private let feedFiles = [
        "ViewModels/FeedViewModel.swift", "ViewModels/FeedViewModel+Actions.swift", "ViewModels/FeedViewModel+Statuses.swift",
        "Views/Feed/FeedTab.swift", "Views/Feed/FeedScreen.swift", "Views/Feed/FeedListView.swift"
    ]

    func testTheFeedNeverTouchesTheLocationSystemNorAPositionNorWritesForThisGesture() throws {
        let forbidden = [
            "CoreLocation", "CLLocation", "askPosition", "requestLocation", "PositionSource", "currentReusingRecent",
            "Fix(", "TrackingEngine", "DocWrite", ".commit(", ".setData", ".updateData", "FieldValue"
        ]
        for file in feedFiles {
            let text = try code(file)
            XCTAssertFalse(text.isEmpty, file)
            for fragment in forbidden {
                XCTAssertFalse(text.contains(fragment), "\(file) : \(fragment)")
            }
        }
    }

    func testTheFeedAsksTheTrackingAfterTheGuardsAndNeverWaitsForIt() throws {
        let text = try code("ViewModels/FeedViewModel+Actions.swift")
        let offlineGuard = try XCTUnwrap(text.range(of: "guard dependencies.network.isOnline()"))
        let rateGuard = try XCTUnwrap(text.range(of: "dependencies.timings.minRefreshIntervalMillis"))
        let asking = try XCTUnwrap(text.range(of: "dependencies.onPullOwnPresence()"))

        XCTAssertLessThan(rateGuard.lowerBound, asking.lowerBound, "après la limite de 15 s du geste")
        XCTAssertLessThan(offlineGuard.lowerBound, asking.lowerBound, "jamais hors connexion")
        XCTAssertEqual(text.components(separatedBy: "onPullOwnPresence").count - 1, 1, "UN seul endroit")
        let line = try XCTUnwrap(text.components(separatedBy: "\n").first { $0.contains("onPullOwnPresence()") })
        XCTAssertFalse(line.contains("await"), "la position n'est jamais attendue : .refreshable ne dépend pas d'elle")
    }

    func testOnlyTheFeedTabAsksTheRuntimeAndOnlyTheRuntimeAsksTheEngineForAPull() throws {
        let files = try XCTUnwrap(FileManager.default.enumerator(at: app, includingPropertiesForKeys: nil))
        var askers: [String] = []
        var engineCallers: [String] = []
        for case let file as URL in files where file.pathExtension == "swift" {
            let text = strip(try String(contentsOf: file, encoding: .utf8))
            if text.contains("feedPulled(") {
                askers.append(file.lastPathComponent)
            }
            if text.contains("refresh(.pulled") {
                engineCallers.append(file.lastPathComponent)
            }
        }

        XCTAssertEqual(askers.sorted(), ["FeedTab.swift", "TrackingRuntime.swift"], "définition + UN appelant")
        XCTAssertEqual(engineCallers, ["TrackingRuntime.swift"], "le moteur n'est demandé que par le pont du suivi")
    }

    func testTheRuntimeLimitsTheFrequencyBeforeAnythingAndLogsOnlyInTheLocalJournal() throws {
        let text = try code("Services/Tracking/TrackingRuntime.swift")
        let start = try XCTUnwrap(text.range(of: "func feedPulled()"))
        let end = try XCTUnwrap(text.range(of: "func appWentToBackground()"))
        XCTAssertLessThan(start.lowerBound, end.lowerBound)
        let body = String(text[start.lowerBound..<end.lowerBound])

        let limit = try XCTUnwrap(body.range(of: "TrackingLimits.pullMinIntervalMillis"))
        let engine = try XCTUnwrap(body.range(of: "engine.refresh(.pulled)"))
        XCTAssertLessThan(limit.lowerBound, engine.lowerBound)
        XCTAssertTrue(body.contains("journal.record(.wakePull)"))
        for forbidden in ["Analytics", "tracker", "print(", "NSLog", "Crashlytics", "UserDefaults"] {
            XCTAssertFalse(body.contains(forbidden), forbidden)
        }
        XCTAssertEqual(TrackingLimits.pullMinIntervalMillis, 30_000, "une revérification par 30 s au plus")
    }

    func testThePullReadsNothingFromTheServerToRebuildMyPresenceBeyondWhatAnOpeningDoes() throws {
        let text = try code("Services/Tracking/TrackingEngine+Refresh.swift")

        // Le geste suit le chemin de l'ouverture : le recalcul des zones est le même (`recheckStates`), aucune lecture forcée du serveur.
        XCTAssertTrue(text.contains("reason == .opened || reason == .pulled"))
        XCTAssertFalse(text.contains("case .sharingChanged, .rearm, .networkBack, .accessChanged, .pulled"))
        XCTAssertTrue(text.contains(".moved, .zoneEntered, .rearm, .sharingChanged, .networkBack, .accessChanged, .pulled:"))
    }
}
// swiftlint:enable line_length
