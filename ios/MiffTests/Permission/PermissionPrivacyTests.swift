import XCTest
@testable import Miff

/// Ce que les autorisations de LOC-4a ne doivent JAMAIS faire, vérifié sur les SOURCES pour qu'un changement ultérieur ne puisse
/// pas le casser sans que personne ne le voie (critères AUT7, VIE1, VIE4 ; décisions (g), (h) du PM) : aucune position lue nulle
/// part dans cette version, CoreLocation importé dans UN seul fichier (pour lire l'ÉTAT des autorisations), rien de journalisé
/// ni envoyé comme événement par les écrans de permission, les textes de la version 3. Miroir de `PermissionPrivacyTest` d'Android.
final class PermissionPrivacyTests: XCTestCase {
    private var iosRoot: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Permission
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
    }

    /// Les fichiers Swift de l'app (jamais des tests), avec leur chemin relatif à `Miff/`.
    private func appSources() throws -> [(path: String, lines: [String])] {
        let folder = iosRoot.appendingPathComponent("Miff")
        let files = try XCTUnwrap(FileManager.default.enumerator(at: folder, includingPropertiesForKeys: nil))
        var found: [(path: String, lines: [String])] = []
        for case let file as URL in files where file.pathExtension == "swift" {
            let text = try String(contentsOf: file, encoding: .utf8)
            let relative = file.path.replacingOccurrences(of: folder.path + "/", with: "")
            found.append((relative, codeLines(text)))
        }
        return found.sorted { $0.path < $1.path }
    }

    /// Les lignes de code : ni commentaires (`//`, `///`), ni lignes vides.
    private func codeLines(_ text: String) -> [String] {
        text.split(separator: "\n", omittingEmptySubsequences: true)
            .map { String($0) }
            .filter { !$0.trimmingCharacters(in: .whitespaces).hasPrefix("//") }
    }

    private func offenders(in sources: [(path: String, lines: [String])], matching fragments: [String]) -> [String] {
        sources.flatMap { source in
            source.lines.filter { line in fragments.contains { line.contains($0) } }
                .map { "\(source.path) : \($0.trimmingCharacters(in: .whitespaces))" }
        }
    }

    // MARK: - Aucune position lue

    func testNoPositionIsReadAnywhereInThisVersion() throws {
        let sources = try appSources()
        XCTAssertGreaterThan(sources.count, 100, "motif de recherche cassé : presque aucune source trouvée")
        let forbidden = [
            "startUpdatingLocation", "stopUpdatingLocation", "requestLocation(", "startMonitoring",
            "startMonitoringSignificantLocationChanges", "startMonitoringVisits", "startRangingBeacons",
            "CLRegion", "CLCircularRegion", "CLBeaconRegion", "CLGeocoder", "CLLocation(", "CLLocationCoordinate2D",
            "CLMonitor", "CLLocationUpdate", "CLServiceSession", "allowsBackgroundLocationUpdates", "distanceFilter",
            "desiredAccuracy", "pausesLocationUpdatesAutomatically", "showsBackgroundLocationIndicator",
            "manager.location", "didUpdateLocations", "didEnterRegion", "didExitRegion", "MKUserLocation",
            "showsUserLocation"
        ]

        XCTAssertEqual(
            offenders(in: sources, matching: forbidden),
            [],
            "aucune position ne peut être lue avant LOC-4b (et jamais avant l'accord de la personne)"
        )
    }

    func testCoreLocationIsImportedByOneFileOnlyToReadTheStateOfTheAuthorization() throws {
        let sources = try appSources()

        let importing = sources.filter { $0.lines.contains { $0.contains("import CoreLocation") } }.map { $0.path }
        XCTAssertEqual(importing, ["Services/Permission/SystemDevicePermissions.swift"])
        let usingManager = sources.filter { $0.lines.contains { $0.contains("CLLocationManager") } }.map { $0.path }
        XCTAssertEqual(usingManager, ["Services/Permission/SystemDevicePermissions.swift"])

        // Ce que ce fichier appelle sur le gestionnaire : l'état, la fenêtre d'autorisation, rien d'autre.
        let used = try XCTUnwrap(sources.first { $0.path == importing.first })
        let calls = used.lines.joined(separator: "\n")
        let allowedCalls = [
            "authorizationStatus", "accuracyAuthorization", "locationServicesEnabled", "requestAlwaysAuthorization"
        ]
        for allowed in allowedCalls {
            XCTAssertTrue(calls.contains(allowed), "attendu : \(allowed)")
        }
        // La seule fenêtre demandée est « Toujours » (qui montre d'abord « pendant l'utilisation » à iOS 13.4 et plus).
        XCTAssertFalse(calls.contains("requestWhenInUseAuthorization"))
        XCTAssertFalse(calls.contains("requestTemporaryFullAccuracyAuthorization"))
    }

    func testThePositionIsOnlyEverDeletedInTheSourcesNeverWritten() throws {
        let sources = try appSources()
        let positionWrites = sources.flatMap { source in
            source.lines.filter { $0.contains("/positions/") && $0.contains("DocWrite(") }
                .map { "\(source.path) : \($0.trimmingCharacters(in: .whitespaces))" }
        }

        XCTAssertGreaterThanOrEqual(positionWrites.count, 5)
        for line in positionWrites {
            XCTAssertTrue(line.contains(".delete"), "une position n'est ici que supprimée : \(line)")
        }
    }

    // MARK: - Rien de journalisé, rien d'envoyé

    func testThePermissionScreensLogNothingAndSendNoEvent() throws {
        let sources = try appSources().filter { source in
            let path = source.path
            return path.hasPrefix("Models/Permission/") || path.hasPrefix("Services/Permission/")
                || path.hasPrefix("Views/Permission/") || path == "ViewModels/PermissionGateViewModel.swift"
                || path == "Services/Miff/ConsentRenewals.swift" || path == "Views/Miff/ConsentCards.swift"
        }
        XCTAssertGreaterThanOrEqual(sources.count, 10)
        let forbidden = [
            "print(", "NSLog", "os_log", "Logger(", "debugPrint", "dump(", "Crashlytics", "recordError",
            "FirebaseAnalytics", "logEvent", "AnalyticsEvent", "AnalyticsTracker", "tracker.", "Analytics.log"
        ]

        XCTAssertEqual(
            offenders(in: sources, matching: forbidden),
            [],
            "une permission ou un consentement ne doit jamais atteindre un journal ni un événement (décision (g))"
        )
    }

    func testNoAnalyticsEventOrParameterNamesALocationOrAPermission() throws {
        // Le contrat Analytics n'a aucun événement de permission ni de position (critère VIE4, décision (g)).
        // Seul le fichier des événements : le contrat (AnalyticsContract) porte la LISTE NOIRE des mots interdits (position, lieu...).
        let analytics = try appSources().filter { $0.path == "Models/Analytics/AnalyticsEvent.swift" }
        XCTAssertEqual(analytics.count, 1)
        let pattern = "\"[a-z_]*(permission|location|position|consent_renew)[a-z_]*\""

        let hits = analytics.flatMap { source in
            source.lines
                .filter { $0.range(of: pattern, options: .regularExpression) != nil }
                .map { "\(source.path) : \($0)" }
        }
        XCTAssertEqual(hits, [])
    }

    // MARK: - Les textes de la version 3

    private func catalog() throws -> [String: [String: Any]] {
        let url = iosRoot.appendingPathComponent("Miff/Resources/Localizable.xcstrings")
        let data = try Data(contentsOf: url)
        let root = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        return try XCTUnwrap(root["strings"] as? [String: [String: Any]])
    }

    private func text(_ key: String, _ language: String, in catalog: [String: [String: Any]]) throws -> String {
        let entry = try XCTUnwrap(catalog[key], "clé absente : \(key)")
        let localizations = try XCTUnwrap(entry["localizations"] as? [String: Any])
        let localization = try XCTUnwrap(localizations[language] as? [String: Any])
        let unit = try XCTUnwrap(localization["stringUnit"] as? [String: Any])
        return try XCTUnwrap(unit["value"] as? String)
    }

    func testTheOldConsentKeysAreGoneFromTheCatalogAndTheCode() throws {
        let catalog = try catalog()
        let old = [
            "miff_consent_create", "miff_consent_join", "miff_consent_members_see_title",
            "miff_consent_members_see_body", "miff_consent_places", "miff_consent_system_note_create",
            "miff_consent_system_note_join", "miff_sharing_resume_body"
        ]
        for key in old {
            XCTAssertNil(catalog[key], "ancienne clé encore dans le catalogue : \(key)")
        }
        XCTAssertEqual(offenders(in: try appSources(), matching: old.map { "\"\($0)\"" }), [])
    }

    func testTheConsentTextHasTheOptionANoteAndNoMoreYouCanRefuse() throws {
        let catalog = try catalog()
        for language in ["fr", "en"] {
            let note = try text("miff_consent3_system_note", language, in: catalog)
            XCTAssertFalse(note.contains("refuser"), note)
            XCTAssertFalse(note.lowercased().contains("refuse"), note)
            XCTAssertFalse(note.lowercased().contains("say no"), note)
        }
        XCTAssertTrue(try text("miff_consent3_system_note", "fr", in: catalog).contains("« Toujours »"))
        for key in ["miff_consent3_create", "miff_consent3_join"] {
            XCTAssertTrue(try text(key, "fr", in: catalog).contains("dernière position connue"), key)
            XCTAssertTrue(try text(key, "en", in: catalog).contains("last known location"), key)
        }
        let closed = try text("miff_consent3_closed_body", "fr", in: catalog)
        XCTAssertTrue(closed.contains("même quand l'app est fermée"))
        XCTAssertTrue(try text("miff_consent3_erase_body", "fr", in: catalog).contains("effacée"))
        XCTAssertTrue(try text("miff_consent3_who_body", "fr", in: catalog).contains("partagent aussi"))
    }

    func testTheLocationScreenSaysWhenWhoSeesAndThatThePersonCanStop() throws {
        let catalog = try catalog()

        XCTAssertTrue(try text("permission_location_closed_title", "fr", in: catalog).contains("app est fermée"))
        let seen = try text("permission_location_seen_body", "fr", in: catalog)
        XCTAssertTrue(seen.contains("qui partagent aussi") && seen.contains("Pas d'historique"))
        XCTAssertTrue(try text("permission_location_control_body", "fr", in: catalog).contains("effacée"))
        XCTAssertTrue(try text("permission_location_closed_title", "en", in: catalog).contains("app is closed"))
    }

    func testEveryNewTextIsInTutoiement() throws {
        let catalog = try catalog()
        let prefixes = ["permission_", "miff_consent3_", "miff_renewal_", "ios_permission_"]
        let keys = catalog.keys.filter { key in prefixes.contains { key.hasPrefix($0) } }
        XCTAssertGreaterThanOrEqual(keys.count, 40)
        for key in keys {
            let french = try text(key, "fr", in: catalog)
            for word in [" vous ", " votre ", " vos ", "Vous ", "Votre ", "Vos "] {
                XCTAssertFalse(french.contains(word), "vouvoiement dans \(key) : \(french)")
            }
            XCTAssertFalse(french.contains("\u{00A0}!"), "espace insécable avant « ! » dans \(key)")
        }
    }

    func testTheUsageTextsOfTheSystemAreInTheInfoPlistCatalogInBothLanguages() throws {
        let url = iosRoot.appendingPathComponent("Miff/Resources/InfoPlist.xcstrings")
        let data = try Data(contentsOf: url)
        let root = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        let strings = try XCTUnwrap(root["strings"] as? [String: [String: Any]])

        XCTAssertEqual(
            Set(strings.keys),
            ["NSLocationAlwaysAndWhenInUseUsageDescription", "NSLocationWhenInUseUsageDescription"]
        )
        for key in strings.keys {
            let french = try text(key, "fr", in: strings)
            let english = try text(key, "en", in: strings)
            // Elles disent COMPLÈTEMENT l'usage (docs/release/app-store-confidentialite-et-revue.md §4) : la dernière position
            // connue, visible des membres qui partagent aussi, avec l'heure.
            XCTAssertTrue(french.contains("dernière position connue") && french.contains("avec l'heure"), key)
            XCTAssertTrue(english.contains("last known location") && english.contains("with the time"), key)
            XCTAssertTrue(french.contains("partagent aussi") && english.contains("also share"), key)
            // L'ancien texte du ticket (« jamais ta position précise ») est faux depuis la décision du 2026-11-01.
            XCTAssertFalse(french.contains("jamais ta position précise"), key)
            XCTAssertFalse(english.contains("never your precise location"), key)
        }
        let always = try text("NSLocationAlwaysAndWhenInUseUsageDescription", "fr", in: strings)
        XCTAssertTrue(always.contains("même quand l'app est fermée"))
        XCTAssertTrue(always.contains("Tu peux arrêter à tout moment"))
    }
}
