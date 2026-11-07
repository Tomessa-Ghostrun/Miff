import XCTest
@testable import Miff

/// Ce que la suppression du compte ne doit JAMAIS faire, vérifié sur les SOURCES pour qu'un changement ultérieur ne le casse pas sans que
/// personne le voie (critères SC38, SC46 et SC47) : aucun journal, aucun événement Analytics, aucun secret, aucune clé Apple, aucun appel
/// réseau hors Firebase ; et chaque clé de texte citée par les écrans existe dans le catalogue (FR et EN) : le compilateur ne voit pas une
/// clé mal écrite. Miroir de `NoSensitiveLoggingTest` d'Android.
final class AccountDeletionSourcesTests: XCTestCase {
    private var iosRoot: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Account
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
    }

    private func sources() throws -> [String] {
        let app = iosRoot.appendingPathComponent("Miff")
        var files: [String] = []
        for folder in ["Models/Account", "Services/Account", "Views/Account"] {
            let url = app.appendingPathComponent(folder)
            guard let found = try? FileManager.default.contentsOfDirectory(atPath: url.path) else {
                throw XCTSkip("Dossier introuvable (\(url.path)).")
            }
            files += found.filter { $0.hasSuffix(".swift") }.map { "Miff/\(folder)/\($0)" }
        }
        files += [
            "Miff/Services/Session/SessionManager+AccountDeletion.swift",
            "Miff/Services/Contracts/AccountDeletionContracts.swift",
            "Miff/Services/Firebase/FirestoreAccountDataStore.swift",
            "Miff/Services/Firebase/FirebaseAppleTokenRevoker.swift",
            "Miff/Services/Storage/AccountDeletionFileMarker.swift",
            "Miff/ViewModels/DeleteAccountViewModels.swift",
            "Miff/App/AppEnvironment+AccountDeletion.swift"
        ]
        return files
    }

    private func codeLines(_ relative: String) throws -> [String] {
        let url = iosRoot.appendingPathComponent(relative)
        guard FileManager.default.fileExists(atPath: url.path) else {
            throw XCTSkip("Fichier introuvable (\(url.path)).")
        }
        return try String(contentsOf: url, encoding: .utf8).components(separatedBy: "\n").filter { line in
            !line.trimmingCharacters(in: .whitespaces).hasPrefix("//")
        }
    }

    private func offenders(matching fragments: [String]) throws -> [String] {
        var found: [String] = []
        for file in try sources() {
            for line in try codeLines(file) where fragments.contains(where: { line.contains($0) }) {
                found.append("\(file) : \(line.trimmingCharacters(in: .whitespaces))")
            }
        }
        return found
    }

    func testTheSourcesAreFound() throws {
        XCTAssertGreaterThanOrEqual(try sources().count, 18)
    }

    func testNothingIsLoggedReportedOrSentAsAnEvent() throws {
        let found = try offenders(matching: [
            "print(", "debugPrint(", "dump(", "NSLog", "os_log", "Logger(", "Crashlytics", "recordError",
            "logEvent", "AnalyticsEvent", "AnalyticsTracker", "tracker.", "FirebaseAnalytics", "UIPasteboard"
        ])

        // Aucun identifiant, mot de passe, jeton ni code Apple dans un journal ou un événement.
        XCTAssertEqual(found, [])
    }

    func testNoNetworkCallOtherThanFirebaseAndNoKeyOrSecret() throws {
        let found = try offenders(matching: [
            "URLSession", "URLRequest", "http://", "https://", "WKWebView", "apiKey", "API_KEY", ".p8", "PRIVATE KEY",
            "client_secret"
        ])

        XCTAssertEqual(found, [], "la clé Apple vit dans la console Firebase, jamais dans l'app ni le dépôt")
    }

    func testTheAppleAuthorizationCodeIsNeverStoredNorKept() throws {
        let files = [
            "Miff/Services/Firebase/FirebaseAppleTokenRevoker.swift",
            "Miff/ViewModels/DeleteAccountViewModels.swift"
        ]
        for file in files {
            for line in try codeLines(file) {
                for fragment in ["UserDefaults", "Keychain", "SecItem", ".write(", "FileManager"] {
                    XCTAssertFalse(line.contains(fragment), "\(file) : \(line)")
                }
            }
        }
    }

    func testTheMarkerAndTheDeviceFilesAreTheOnlyLocalFilesAndTheyAreExcludedFromBackups() throws {
        let marker = try codeLines("Miff/Services/Storage/AccountDeletionFileMarker.swift").joined(separator: "\n")

        XCTAssertTrue(marker.contains("isExcludedFromBackup = true"))
        XCTAssertTrue(marker.contains("completeFileProtectionUntilFirstUserAuthentication"))
        XCTAssertTrue(marker.contains(".atomic"))
    }

    func testEveryTextKeyTheScreensQuoteExistsInBothLanguages() throws {
        let pattern = try NSRegularExpression(pattern: "\"([a-z][a-z0-9]*(?:_[a-z0-9]+)+)\"")
        var keys = Set<String>()
        for file in try sources() {
            let text = try codeLines(file).joined(separator: "\n")
            let range = NSRange(text.startIndex..., in: text)
            for match in pattern.matches(in: text, range: range) {
                if let keyRange = Range(match.range(at: 1), in: text) {
                    keys.insert(String(text[keyRange]))
                }
            }
        }
        // Les clés de texte seulement : ni les clés de réglages ni les noms de fichiers ni les identifiants de la session.
        let textKeys = keys.filter { key in
            key.hasPrefix("settings_") || key.hasPrefix("ios_") || key.hasPrefix("auth_") || key.hasPrefix("miff_")
        }

        XCTAssertGreaterThan(textKeys.count, 40)
        for key in textKeys.sorted() {
            XCTAssertNotEqual(L10n.string(key), key, "Pas de texte pour la clé \(key)")
        }
    }
}
