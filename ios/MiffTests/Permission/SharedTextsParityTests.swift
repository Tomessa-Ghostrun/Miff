import XCTest
@testable import Miff

/// LOC-4a : le CONTENU (et pas seulement les clés) des textes de consentement, de localisation, de départ et d'arrêt du partage
/// est le même sur Android et sur iOS (le contrôle de parité du dépôt ne compare que les clés). Lit les sources des deux
/// plateformes ; ignoré si le dossier Android n'est pas là.
final class SharedTextsParityTests: XCTestCase {
    private var repoRoot: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Permission
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
            .deletingLastPathComponent() // dépôt
    }

    private static let sharedPrefixes = [
        "permission_", "miff_consent3_", "miff_renewal_", "miff_leave_", "miff_sharing_", "settings_privacy_location"
    ]

    /// Les `<string>` d'un fichier de textes d'Android, mis en forme comme ceux du catalogue iOS (apostrophes, valeurs insérées).
    private func androidStrings(_ relativePath: String) throws -> [String: String] {
        let url = repoRoot.appendingPathComponent(relativePath)
        guard FileManager.default.fileExists(atPath: url.path) else {
            throw XCTSkip("Textes d'Android introuvables (\(url.path)).")
        }
        let xml = try String(contentsOf: url, encoding: .utf8)
        let regex = try NSRegularExpression(pattern: "<string name=\"([^\"]+)\"[^>]*>([\\s\\S]*?)</string>")
        var result: [String: String] = [:]
        let whole = NSRange(xml.startIndex..., in: xml)
        for match in regex.matches(in: xml, range: whole) {
            guard let name = Range(match.range(at: 1), in: xml), let value = Range(match.range(at: 2), in: xml) else {
                continue
            }
            result[String(xml[name])] = normalized(String(xml[value]))
        }
        return result
    }

    private func normalized(_ text: String) -> String {
        var value = text
            .replacingOccurrences(of: "\\'", with: "'")
            .replacingOccurrences(of: "\\\"", with: "\"")
            .replacingOccurrences(of: "&amp;", with: "&")
            .replacingOccurrences(of: "&lt;", with: "<")
            .replacingOccurrences(of: "&gt;", with: ">")
        // Valeur insérée : %1$s (Android) = %1$@ (iOS).
        if let regex = try? NSRegularExpression(pattern: "%(\\d)\\$s") {
            let range = NSRange(value.startIndex..., in: value)
            value = regex.stringByReplacingMatches(in: value, range: range, withTemplate: "%$1\\$@")
        }
        return value
    }

    private func catalog() throws -> [String: [String: Any]] {
        let url = repoRoot.appendingPathComponent("ios/Miff/Resources/Localizable.xcstrings")
        let root = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [String: Any])
        return try XCTUnwrap(root["strings"] as? [String: [String: Any]])
    }

    private func iosText(_ key: String, _ language: String, in catalog: [String: [String: Any]]) -> String? {
        let localizations = catalog[key]?["localizations"] as? [String: Any]
        let localization = localizations?[language] as? [String: Any]
        let unit = localization?["stringUnit"] as? [String: Any]
        return (unit?["value"] as? String)?.replacingOccurrences(of: "\u{2019}", with: "'")
    }

    func testTheSharedConsentAndLocationTextsSayTheSameThingOnBothPlatforms() throws {
        let catalog = try catalog()
        let sources = [
            ("fr", try androidStrings("android/app/src/main/res/values-fr/strings.xml")),
            ("en", try androidStrings("android/app/src/main/res/values/strings.xml"))
        ]
        var compared = 0
        for (language, android) in sources {
            for (key, androidText) in android.sorted(by: { $0.key < $1.key })
            where Self.sharedPrefixes.contains(where: { key.hasPrefix($0) }) {
                guard let ios = iosText(key, language, in: catalog) else {
                    continue
                }
                compared += 1
                XCTAssertEqual(ios, androidText, "\(key) (\(language)) : texte différent entre Android et iOS")
            }
        }
        XCTAssertGreaterThanOrEqual(compared, 80, "motif de recherche cassé : trop peu de textes comparés")
    }

    func testThePrivacyLocationTextIsTrueNowThatAPermissionIsAsked() throws {
        let catalog = try catalog()

        let french = try XCTUnwrap(iosText("settings_privacy_location_body", "fr", in: catalog))
        let english = try XCTUnwrap(iosText("settings_privacy_location_body", "en", in: catalog))
        // L'ancien texte promettait une explication « avant de te demander quoi que ce soit » : faux depuis LOC-4a.
        XCTAssertFalse(french.contains("avant de te demander"), french)
        XCTAssertFalse(english.contains("before asking you"), english)
        // Le texte est celui d'Android (LOC-2b, fusionné dans main), que la parité ci-dessus impose : il dit l'usage de la
        // localisation (dernière position, effacée à l'arrêt) sans parler de l'autorisation.
        XCTAssertTrue(french.contains("dernière position connue"), french)
        XCTAssertTrue(english.contains("last known location"), english)
    }

    func testStoppingTheSharingSaysThatTheLastKnownLocationIsDeletedToo() throws {
        let catalog = try catalog()

        let french = try XCTUnwrap(iosText("miff_sharing_stop_body", "fr", in: catalog))
        let english = try XCTUnwrap(iosText("miff_sharing_stop_body", "en", in: catalog))
        XCTAssertTrue(french.contains("dernière position"), french)
        XCTAssertTrue(english.contains("last known location"), english)
    }
}
