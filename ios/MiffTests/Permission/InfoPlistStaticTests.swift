import XCTest
@testable import Miff

/// Vérification STATIQUE de l'Info.plist de LOC-4a (critère IOS1), faite sur `project.yml` (la source de l'Info.plist, généré par
/// XcodeGen) et sur les catalogues : exactement les deux clés `NSLocation…`, aucun mode d'arrière-plan, l'interrupteur de
/// compilation bloquant par défaut. L'Info.plist du build lui-même est vérifié par `AppConfigurationTests` (dans l'app) et, sur
/// le binaire, par `ci/check-binaries.sh` et le workflow `ios-testflight`.
final class InfoPlistStaticTests: XCTestCase {
    private var iosRoot: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Permission
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
    }

    /// Les lignes de `project.yml` qui ne sont pas des commentaires.
    private func yamlLines() throws -> [String] {
        let url = iosRoot.appendingPathComponent("project.yml")
        let text = try String(contentsOf: url, encoding: .utf8)
        return text.split(separator: "\n", omittingEmptySubsequences: true)
            .map { String($0) }
            .filter { !$0.trimmingCharacters(in: .whitespaces).hasPrefix("#") }
    }

    private func value(of key: String, in lines: [String]) -> String? {
        let prefix = key + ":"
        let line = lines.first { $0.trimmingCharacters(in: .whitespaces).hasPrefix(prefix) }
        return line.map {
            $0.trimmingCharacters(in: .whitespaces).dropFirst(prefix.count).trimmingCharacters(in: .whitespaces)
        }
    }

    private func unquoted(_ text: String?) -> String? {
        guard let text, text.count >= 2, text.hasPrefix("\""), text.hasSuffix("\"") else {
            return text
        }
        return String(text.dropFirst().dropLast())
    }

    func testProjectDeclaresExactlyTheTwoLocationUsageKeysAndNothingElseOfTheKind() throws {
        let lines = try yamlLines()

        let locationKeys = lines
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { $0.hasPrefix("NSLocation") }
            .map { String($0.prefix { $0 != ":" }) }
        XCTAssertEqual(
            Set(locationKeys),
            ["NSLocationWhenInUseUsageDescription", "NSLocationAlwaysAndWhenInUseUsageDescription"]
        )
        XCTAssertEqual(locationKeys.count, 2, "chaque clé une seule fois")
    }

    func testNoBackgroundModeNoOldAlwaysKeyNoATSExceptionIsDeclared() throws {
        let lines = try yamlLines()
        let joined = lines.joined(separator: "\n")

        // Le mode « location » sert aux mises à jour CONTINUES de position : Miff n'en fait pas (guideline 2.5.4). À rouvrir
        // dans LOC-4b seulement si l'essai sur iPhone le démontre.
        XCTAssertFalse(joined.contains("UIBackgroundModes"))
        XCTAssertFalse(joined.contains("NSLocationAlwaysUsageDescription"))
        XCTAssertFalse(joined.contains("NSLocationTemporaryUsageDescriptionDictionary"))
        XCTAssertFalse(joined.contains("NSAppTransportSecurity"))
        XCTAssertFalse(joined.contains("NSLocationDefaultAccuracyReduced"))
    }

    func testTheBlockingSwitchIsOnByDefaultInTheProject() throws {
        let lines = try yamlLines()

        XCTAssertEqual(unquoted(value(of: "MIFF_LOCATION_BLOCKING", in: lines)), "YES")
        XCTAssertEqual(unquoted(value(of: "MiffLocationBlocking", in: lines)), "$(MIFF_LOCATION_BLOCKING)")
        XCTAssertEqual(LocationBlockingSwitch.infoName, "MiffLocationBlocking")
    }

    func testTheDefaultValuesOfTheKeysAreTheEnglishTextsOfTheCatalog() throws {
        // La langue de repli est l'anglais (CFBundleDevelopmentRegion) : la valeur de l'Info.plist = le texte anglais.
        let lines = try yamlLines()
        let url = iosRoot.appendingPathComponent("Miff/Resources/InfoPlist.xcstrings")
        let root = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [String: Any])
        let strings = try XCTUnwrap(root["strings"] as? [String: [String: Any]])

        for key in ["NSLocationWhenInUseUsageDescription", "NSLocationAlwaysAndWhenInUseUsageDescription"] {
            let entry = try XCTUnwrap(strings[key])
            let localizations = try XCTUnwrap(entry["localizations"] as? [String: Any])
            let english = try XCTUnwrap(localizations["en"] as? [String: Any])
            let unit = try XCTUnwrap(english["stringUnit"] as? [String: Any])
            let expected = try XCTUnwrap(unit["value"] as? String)
            XCTAssertEqual(unquoted(value(of: key, in: lines)), expected, key)
        }
    }

    func testThePrivacyManifestDeclaresThePreciseLocationLinkedForTheFunctionOfTheAppWithoutTracking() throws {
        let url = iosRoot.appendingPathComponent("Miff/Resources/PrivacyInfo.xcprivacy")
        let data = try Data(contentsOf: url)
        let manifest = try XCTUnwrap(PropertyListSerialization.propertyList(from: data, format: nil) as? [String: Any])
        let collected = try XCTUnwrap(manifest["NSPrivacyCollectedDataTypes"] as? [[String: Any]])

        let precise = collected.filter {
            $0["NSPrivacyCollectedDataType"] as? String == "NSPrivacyCollectedDataTypePreciseLocation"
        }
        XCTAssertEqual(precise.count, 1)
        XCTAssertEqual(precise.first?["NSPrivacyCollectedDataTypeLinked"] as? Bool, true)
        XCTAssertEqual(precise.first?["NSPrivacyCollectedDataTypeTracking"] as? Bool, false)
        XCTAssertEqual(
            precise.first?["NSPrivacyCollectedDataTypePurposes"] as? [String],
            ["NSPrivacyCollectedDataTypePurposeAppFunctionality"]
        )
        // Aucune autre donnée de localisation (pas de position approximative), et jamais de suivi.
        let locationTypes = collected.compactMap { $0["NSPrivacyCollectedDataType"] as? String }.filter {
            $0.contains("Location")
        }
        XCTAssertEqual(locationTypes, ["NSPrivacyCollectedDataTypePreciseLocation"])
        XCTAssertEqual(manifest["NSPrivacyTracking"] as? Bool, false)
        XCTAssertTrue(collected.allSatisfy { $0["NSPrivacyCollectedDataTypeTracking"] as? Bool == false })
    }

    func testTheCiScriptsAskForExactlyTheTwoKeysAndNoBackgroundMode() throws {
        let binaries = try String(
            contentsOf: iosRoot.appendingPathComponent("ci/check-binaries.sh"),
            encoding: .utf8
        )

        let expectedKeys = "NSLocationAlwaysAndWhenInUseUsageDescription NSLocationWhenInUseUsageDescription"
        XCTAssertTrue(binaries.contains(expectedKeys))
        XCTAssertTrue(binaries.contains("UIBackgroundModes"))
        XCTAssertTrue(binaries.contains("MiffLocationBlocking"))
        // Un build de PUBLICATION qui relâche le blocage est refusé, sauf demande expresse.
        XCTAssertTrue(binaries.contains("MIFF_ALLOW_RELAXED_LOCATION_IN_RELEASE"))
    }
}
