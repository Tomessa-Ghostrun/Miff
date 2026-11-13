import XCTest
@testable import Miff

/// Ce que les lieux ne doivent JAMAIS faire (ticket LOC-3 ; critères ADR3, ADR4, VIE1 pour la part qui est celle de l'app, QA3),
/// vérifié sur les SOURCES pour qu'un changement ultérieur ne le casse pas sans que personne le voie : aucune adresse, aucun nom ni
/// aucune coordonnée dans un journal, un événement, un rapport de plantage ou le presse-papiers ; aucune permission de localisation ;
/// la carte ne montre pas la position d'une personne ; rien d'autre que HTTPS.
final class PlacePrivacyTests: XCTestCase {
    private var iosRoot: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Place
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
    }

    /// Un fichier du dépôt ; le test est sauté quand les sources ne sont pas là (exécution hors du dépôt).
    private func read(_ relative: String) throws -> String {
        let url = iosRoot.appendingPathComponent(relative)
        guard FileManager.default.fileExists(atPath: url.path) else {
            throw XCTSkip("Fichier introuvable (\(url.path)).")
        }
        return try String(contentsOf: url, encoding: .utf8)
    }

    /// Les sources des lieux : modèles, dépôt, recherche, view models, écrans.
    private func placeSources() throws -> [URL] {
        let app = iosRoot.appendingPathComponent("Miff")
        guard FileManager.default.fileExists(atPath: app.path) else {
            throw XCTSkip("Sources introuvables (\(app.path)).")
        }
        let folders = ["Models/Place", "Services/Place", "Views/Place", "Diagnostic"]
        var files: [URL] = []
        for folder in folders {
            let url = app.appendingPathComponent(folder)
            if let found = FileManager.default.enumerator(at: url, includingPropertiesForKeys: nil) {
                files += found.compactMap { $0 as? URL }.filter { $0.pathExtension == "swift" }
            }
        }
        for name in [
            "Services/Contracts/PlaceContracts.swift",
            "Services/Firebase/FirestorePlaceStore.swift",
            "ViewModels/PlaceFormState.swift",
            "ViewModels/PlaceFormViewModel.swift",
            "ViewModels/PlaceFormViewModel+Address.swift",
            "ViewModels/PlaceFormViewModel+Save.swift",
            "ViewModels/PlaceDetailViewModel.swift"
        ] {
            files.append(app.appendingPathComponent(name))
        }
        return files.filter { $0.lastPathComponent.hasPrefix("Place") || $0.lastPathComponent.hasPrefix("Address")
            || $0.lastPathComponent.hasPrefix("Apple") || $0.lastPathComponent.hasPrefix("Firestore") }
    }

    /// Les lignes de code d'un fichier : sans les commentaires.
    private func codeLines(_ file: URL) throws -> [String] {
        try String(contentsOf: file, encoding: .utf8)
            .components(separatedBy: "\n")
            .filter { line in
                let trimmed = line.trimmingCharacters(in: .whitespaces)
                return !trimmed.hasPrefix("//") && !trimmed.hasPrefix("///") && !trimmed.hasPrefix("*")
            }
    }

    private func offenders(_ patterns: [String]) throws -> [String] {
        let expressions = try patterns.map { try NSRegularExpression(pattern: $0) }
        var found: [String] = []
        for file in try placeSources() {
            for line in try codeLines(file) {
                let range = NSRange(line.startIndex..., in: line)
                if expressions.contains(where: { $0.firstMatch(in: line, range: range) != nil }) {
                    found.append("\(file.lastPathComponent): \(line.trimmingCharacters(in: .whitespaces))")
                }
            }
        }
        return found
    }

    func testTheSourcesAreFound() throws {
        let sources = try placeSources()

        XCTAssertGreaterThanOrEqual(sources.count, 25)
    }

    func testNothingIsLoggedNoErrorIsReportedNoEventOrClipboardCarriesAPlace() throws {
        let found = try offenders([
            "\\bprint\\(",
            "\\bdebugPrint\\(",
            "\\bdump\\(",
            "\\bNSLog\\(",
            "\\bos_log\\(",
            "\\bLogger\\(",
            "\\bCrashlytics\\b",
            "\\bFirebaseAnalytics\\b|\\bAnalytics\\.logEvent",
            "\\bUIPasteboard\\b",
            "\\bUserDefaults\\b"
        ])

        XCTAssertEqual(found, [], "aucun lieu dans un journal, un événement, un rapport ou le presse-papiers")
    }

    func testTheOnlyEventsOfThePlacesAreTheOfferWithAFixedTrigger() throws {
        let tracked = try offenders(["\\.track\\("])

        XCTAssertFalse(tracked.isEmpty, "l'offre compte son ouverture et sa fermeture")
        for line in tracked {
            XCTAssertTrue(
                line.contains("tracker.track(event)"),
                "seul un événement de PlacePaywall (déclencheur fixe, rien d'autre) peut partir : \(line)"
            )
        }
        let events = try offenders(["AnalyticsEvent\\."])
        for line in events {
            XCTAssertTrue(line.contains("paywall") || line.contains("Paywall"), "événement inattendu : \(line)")
        }
    }

    func testThePlacesAskNoPermissionOfLocation() throws {
        let found = try offenders([
            "CLLocationManager",
            "requestWhenInUseAuthorization",
            "requestAlwaysAuthorization",
            "requestLocation\\(",
            "startUpdatingLocation",
            "startMonitoringSignificantLocationChanges",
            "startMonitoring\\(for",
            "\\bimport CoreLocation\\b"
        ])
        XCTAssertEqual(found, [], "les lieux ne demandent aucune position (ticket LOC-4 pour la détection)")

        // LOC-4a a ajouté, pour l'autorisation seule, exactement ces deux textes d'usage (ce ne sont pas les lieux qui les
        // utilisent : les sources des lieux ci-dessus ne lisent ni ne demandent rien) ; toute autre clé NSLocation* serait inattendue.
        let project = try read("project.yml")
        let declared = project.components(separatedBy: "\n")
            .filter { $0.contains("NSLocation") && !$0.trimmingCharacters(in: .whitespaces).hasPrefix("#") }
            .map { $0.trimmingCharacters(in: .whitespaces).components(separatedBy: ":").first ?? "" }
        XCTAssertEqual(
            declared,
            ["NSLocationWhenInUseUsageDescription", "NSLocationAlwaysAndWhenInUseUsageDescription"],
            "seules les deux clés d'usage de LOC-4a sont déclarées"
        )
    }

    func testTheMapShowsNoPositionOfAPersonAndHasNoGesture() throws {
        let map = try read("Miff/Views/Place/PlaceMapView.swift")

        XCTAssertTrue(map.contains("showsUserLocation = false"))
        XCTAssertFalse(map.contains("showsUserLocation = true"))
        XCTAssertTrue(map.contains("isScrollEnabled = false"))
        XCTAssertTrue(map.contains("isZoomEnabled = false"))
        XCTAssertFalse(map.contains("userTrackingMode"))
        XCTAssertFalse(map.contains("MKUserTrackingButton"))
    }

    func testTheAddressSearchIsMapKitOnlyNoOtherServiceAndNoNetworkAddressInTheCode() throws {
        let found = try offenders([
            "https?://",
            "URLSession",
            "URLRequest",
            "photon\\.komoot",
            "geoapify",
            "nominatim"
        ])

        XCTAssertEqual(found, [], "le texte tapé ne va qu'au service d'Apple, jamais à une autre adresse")
    }

    func testNoKeyIsCommittedInThePlaces() throws {
        let pattern = "(?i)(api[_-]?key|apikey|secret|token)\\s*[:=]\\s*\"[A-Za-z0-9]{8,}\""
        let keyLike = try NSRegularExpression(pattern: pattern)
        for file in try placeSources() {
            let text = try String(contentsOf: file, encoding: .utf8)
            let range = NSRange(text.startIndex..., in: text)
            XCTAssertNil(keyLike.firstMatch(in: text, range: range), "\(file.lastPathComponent) : une clé en dur ?")
        }
    }

    func testTheViewModelsAndTheStateKeepNoPlaceInARestorableStateNorARoute() throws {
        let hosts = try read("Miff/Views/Miff/MiffHosts.swift")
        let routes = try read("Miff/Views/Miff/MiffRoute.swift")

        XCTAssertFalse(hosts.contains("SceneStorage"))
        XCTAssertFalse(hosts.contains("AppStorage"))
        // Les routes des lieux ne portent que des identifiants (celles des demandes aussi : jamais un nom ni une adresse).
        XCTAssertTrue(routes.contains("case placeForm(placeId: String?, miffId: String?)"))
        XCTAssertTrue(routes.contains("case placeDetail(miffId: String, placeId: String)"))
        XCTAssertTrue(routes.contains("case placeRequestForm(miffId: String)"))
        XCTAssertTrue(routes.contains("case placeRequestDetail(miffId: String, requestId: String)"))
        // L'offre (plein écran) ne porte que sa raison, un mot fixe.
        XCTAssertTrue(hosts.contains("struct PaywallPresentation: Identifiable"))
        XCTAssertTrue(hosts.contains("let reason: PaywallReason"))
    }
}
