import XCTest
@testable import Miff

/// Ce que le suivi des lieux ne doit JAMAIS faire, vérifié sur les SOURCES pour qu'un changement ultérieur ne le casse pas sans que
/// personne le voie (critères VIE1 à VIE4, AUT7, QA3) : aucune coordonnée dans un journal, un événement, un rapport de plantage ou le
/// presse-papiers ; aucun appel réseau hors Firestore ; le journal local absent du build de publication ; le gestionnaire de localisation
/// créé tout de suite au démarrage. Miroir de `TrackingPrivacyTest` d'Android.
final class TrackingPrivacyTests: XCTestCase {
    private var iosRoot: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Tracking
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

    /// Les sources du suivi : le moteur, le pont, l'exécution, les écritures, la copie locale, le journal, la carte du diagnostic.
    private func trackingFiles() throws -> [String] {
        let app = iosRoot.appendingPathComponent("Miff")
        var files: [String] = []
        for folder in ["Models/Tracking", "Services/Tracking"] {
            let url = app.appendingPathComponent(folder)
            let found = try FileManager.default.contentsOfDirectory(atPath: url.path)
            files += found.filter { $0.hasSuffix(".swift") }.map { "Miff/\(folder)/\($0)" }
        }
        files += [
            "Miff/App/TrackingServices.swift", "Miff/Views/TrackingLifecycle.swift",
            "Miff/Diagnostic/DetectionJournalCard.swift", "Miff/Models/Support/GenerationCounter.swift"
        ]
        return files
    }

    private func codeLines(_ relative: String) throws -> [String] {
        try read(relative).components(separatedBy: "\n").filter { line in
            let trimmed = line.trimmingCharacters(in: .whitespaces)
            return !trimmed.hasPrefix("//")
        }
    }

    private func offenders(matching fragments: [String]) throws -> [String] {
        var found: [String] = []
        for file in try trackingFiles() {
            for line in try codeLines(file) where fragments.contains(where: { line.contains($0) }) {
                found.append("\(file) : \(line.trimmingCharacters(in: .whitespaces))")
            }
        }
        return found
    }

    func testTheSourcesAreFound() throws {
        let files = try trackingFiles()

        XCTAssertGreaterThanOrEqual(files.count, 25)
    }

    func testNothingIsLoggedReportedSentAsAnEventOrCopied() throws {
        let found = try offenders(matching: [
            "print(", "debugPrint(", "dump(", "NSLog", "os_log", "Logger(", "Crashlytics", "recordError",
            "FirebaseAnalytics",
            "logEvent", "AnalyticsEvent", "AnalyticsTracker", "tracker.", "UIPasteboard", "UserDefaults", "ShareLink",
            "UIActivityViewController"
        ])

        XCTAssertEqual(found, [], "aucune position ni lieu dans un journal, un événement ou le presse-papiers")
    }

    func testNoNetworkCallOtherThanFirestoreAndNoKey() throws {
        let found = try offenders(matching: [
            "URLSession", "URLRequest", "http://", "https://", "WKWebView", "apiKey", "API_KEY"
        ])

        XCTAssertEqual(found, [], "les coordonnées ne vont qu'à Firestore (dernière position), jamais ailleurs")
    }

    func testTheJournalReceivesNeverAZoneKeyOrACoordinateOnlyAPlaceIdentifier() throws {
        for file in try trackingFiles() {
            let lines = try codeLines(file)
            let isEngine = file.contains("Services/Tracking/TrackingEngine")
            for line in lines where isEngine && line.contains("placeId:") && !line.contains("ZoneRef(") {
                let carriesAKey = ["zoneKey", ".key", "lat", "lng"].contains { line.contains($0) }
                if carriesAKey {
                    XCTAssertTrue(line.contains("ZoneKey.placeId("), "\(file) : \(line)")
                }
            }
        }
    }

    func testTheStatusCodeNeverNamesACoordinate() throws {
        let writes = try codeLines("Miff/Models/Tracking/TrackingWrites.swift").joined(separator: "\n")
        let statusPart = try XCTUnwrap(writes.range(of: "static func statusUpdate"))
        let positionPart = try XCTUnwrap(writes.range(of: "static func position"))
        let statusCode = String(writes[statusPart.lowerBound..<positionPart.lowerBound])

        for forbidden in ["\"lat\"", "\"lng\"", "latitude", "longitude", "address", "TrackingGeo"] {
            XCTAssertFalse(statusCode.contains(forbidden), "un statut ne porte aucune coordonnée : \(forbidden)")
        }
    }

    func testTheEngineAndTheRuntimeNeverTouchTheSystemLocation() throws {
        for file in ["Services/Tracking/TrackingEngine", "Services/Tracking/TrackingRuntime"] {
            let text = [".swift", "+Refresh.swift", "+Events.swift", "+Writes.swift"]
                .compactMap { try? read("Miff/\(file)\($0)") }
                .joined(separator: "\n")
            XCTAssertFalse(text.isEmpty, file)
            XCTAssertFalse(text.contains("import CoreLocation"), file)
            XCTAssertFalse(text.contains("CLLocation"), file)
            XCTAssertFalse(text.contains("import MapKit"), file)
        }
    }

    // MARK: - Le journal n'est pas dans le build de publication (VIE2)

    func testTheFileWriterOfTheJournalIsCompiledInTestBuildsOnly() throws {
        let writer = try read("Miff/Services/Tracking/FileDetectionJournal.swift")
        let lines = writer.components(separatedBy: "\n")

        XCTAssertEqual(lines.first, "#if DEBUG || INTERNAL")
        let guardedFactory = try XCTUnwrap(writer.range(of: "enum DetectionJournals"))
        let endOfWriter = try XCTUnwrap(writer.range(of: "#endif"))
        XCTAssertLessThan(endOfWriter.lowerBound, guardedFactory.lowerBound)
        XCTAssertTrue(writer.contains("#else\n        return NoDetectionJournal()"))
        let card = try read("Miff/Diagnostic/DetectionJournalCard.swift")
        XCTAssertEqual(card.components(separatedBy: "\n").first, "#if DEBUG || INTERNAL")
        XCTAssertTrue(card.hasSuffix("#endif\n"))
    }

    func testTheCiLooksForTheJournalMarkerInThePublicationBuild() throws {
        let script = try read("ci/check-binaries.sh")

        XCTAssertTrue(script.contains("DETECTIONJOURNAL"))
        XCTAssertTrue(script.contains("FileDetectionJournal"))
        XCTAssertTrue(script.contains("Release : aucune trace du journal local de la détection"))
    }

    func testTheJournalKindsAreAClosedListOfPlainWords() {
        for kind in JournalKind.allCases {
            XCTAssertNotNil(kind.rawValue.range(of: "^[a-zA-Z]+$", options: .regularExpression), kind.rawValue)
        }
        XCTAssertGreaterThanOrEqual(JournalKind.allCases.count, 24)
    }

    // MARK: - Le démarrage

    func testTheLocationManagerIsCreatedAtLaunchNotOnFirstUse() throws {
        let environment = try codeLines("Miff/App/AppEnvironment.swift").joined(separator: "\n")
        let services = try codeLines("Miff/App/TrackingServices.swift").joined(separator: "\n")
        let app = try codeLines("Miff/MiffApp.swift").joined(separator: "\n")

        XCTAssertTrue(environment.contains("TrackingServices.make("))
        XCTAssertFalse(environment.contains("lazy var tracking"))
        XCTAssertTrue(services.contains("CoreLocationTracking()"))
        XCTAssertTrue(app.contains(".trackingLifecycle(environment.tracking)"))
    }

    func testTheSessionStopsTheTrackingAndTheEngineFollowsTheGenerationOfTheSession() throws {
        let environment = try codeLines("Miff/App/AppEnvironment.swift").joined(separator: "\n")
        let services = try codeLines("Miff/App/TrackingServices.swift").joined(separator: "\n")
        let manager = try codeLines("Miff/Services/Session/SessionManager.swift").joined(separator: "\n")

        XCTAssertTrue(environment.contains("trackingServices.teardown"))
        XCTAssertTrue(services.contains("runtime.stop()"))
        XCTAssertTrue(environment.contains("generationCounter: trackingServices.generation"))
        XCTAssertTrue(manager.contains("generationCounter?.set(generation)"))
    }

    func testNoBackgroundModeIsNeededToBeRelaunchedAndNoneIsDeclared() throws {
        let project = try read("project.yml")
        let declared = project.components(separatedBy: "\n").filter {
            !$0.trimmingCharacters(in: .whitespaces).hasPrefix("#") && $0.contains("UIBackgroundModes")
        }

        XCTAssertEqual(declared, [])
    }
}
