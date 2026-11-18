// Quelques lignes appellent des fabriques à nombreux paramètres : elles dépassent la limite.
// swiftlint:disable line_length
import XCTest
@testable import Miff

/// Vie privée, sécurité, drapeau et textes de l'alerte « Je suis en danger » (docs/tickets/alerte-danger.md AD16, AD33, AD35, AD43, AD45, AD46 ; miroir d'`AlertDangerGuardTest`
/// d'Android), vérifiés sur les SOURCES pour qu'un changement ultérieur ne les casse pas sans bruit :
/// - aucun journal, aucun événement Analytics, aucun presse-papiers, aucune position, aucun carnet d'adresses, aucun appel direct dans le code de l'alerte ;
/// - tout le code qui lit ou écrit une alerte est derrière la condition de compilation `ALERT_DANGER` (Debug et Internal, jamais Release) ;
/// - jamais de Critical Alerts ; aucune permission « Contacts » ni mode d'arrière-plan de plus ;
/// - les textes FR et EN, leurs valeurs insérées, le ton (aucun « ! »), la notification sans position ni nom de Miff, et les mêmes clés qu'Android.
final class DangerAlertPrivacyTests: XCTestCase {
    private struct Source {
        let path: String
        let text: String

        var codeLines: [String] {
            text.components(separatedBy: "\n").filter {
                let line = $0.trimmingCharacters(in: .whitespaces)
                return !line.isEmpty && !line.hasPrefix("//")
            }
        }
    }

    private func iosRoot(file: StaticString = #filePath) -> URL {
        URL(fileURLWithPath: "\(file)")
            .deletingLastPathComponent() // Alert
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
    }

    private func read(_ relative: String) throws -> Source {
        let url = iosRoot().appendingPathComponent(relative)
        return Source(path: relative, text: try String(contentsOf: url, encoding: .utf8))
    }

    /// Les fichiers qui sont la fonction, et ceux qui ne sont que du calcul pur compilé partout.
    private let guardedFiles = [
        "Miff/Services/Alert/DangerAlertContracts.swift",
        "Miff/Services/Alert/DangerAlertController.swift",
        "Miff/Services/Alert/DangerAlertController+Work.swift",
        "Miff/Services/Firebase/FirestoreDangerAlertStore.swift",
        "Miff/Services/Storage/DangerAlertFileStore.swift",
        "Miff/ViewModels/DangerAlertViewModel.swift",
        "Miff/ViewModels/FeedViewModel+Alerts.swift",
        "Miff/Views/Alert/DangerHoldGesture.swift",
        "Miff/Views/Alert/DangerAlertComponents.swift",
        "Miff/Views/Alert/DangerSentView.swift",
        "Miff/Views/Alert/DangerBannerView.swift",
        "Miff/Views/Alert/DangerViewfinderSupport.swift",
        "Miff/Views/Alert/DangerAlertHost.swift",
        "Miff/App/DangerAlertServices.swift",
        "Miff/Models/Push/CircleAlertPayload.swift",
        "Miff/DesignSystem/DangerAlertTokens.swift"
    ]

    private let pureFiles = [
        "Miff/Models/Alert/DangerAlertRules.swift",
        "Miff/Models/Alert/DangerAlertHold.swift",
        "Miff/Models/Alert/DangerAlertStartPlan.swift",
        "Miff/Models/Alert/EmergencyCatalog.swift",
        "Miff/Models/Feed/FeedBuilder+Alert.swift",
        "Miff/Views/Alert/DangerAlertShell.swift"
    ]

    // MARK: - Le drapeau

    func testEveryFileThatReadsOrWritesAnAlertIsEntirelyBehindTheFlag() throws {
        for path in guardedFiles {
            let lines = try read(path).text.components(separatedBy: "\n").filter { !$0.isEmpty }
            XCTAssertEqual(lines.first, "#if ALERT_DANGER", "\(path) : doit commencer par le drapeau")
            XCTAssertEqual(lines.last, "#endif", "\(path) : doit finir par #endif")
        }
    }

    func testTheFlagIsInTheConditionsOfDebugAndInternalAndNotInRelease() throws {
        let project = try read("project.yml").text
        let flagged = project.components(separatedBy: "\n")
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.hasPrefix("#") && $0.contains("ALERT_DANGER") }

        XCTAssertEqual(
            flagged,
            [
                "SWIFT_ACTIVE_COMPILATION_CONDITIONS: DEBUG REVIEW_PROMPT ALERT_DANGER",
                "SWIFT_ACTIVE_COMPILATION_CONDITIONS: INTERNAL REVIEW_PROMPT ALERT_DANGER"
            ],
            "ALERT_DANGER ne doit être que dans Debug et Internal (jamais Release, sauf décision du PM)"
        )
    }

    func testTheFlagIsInTheBuildThisTestRunsIn() {
        let configuration = BuildInfo.current.configuration
        XCTAssertEqual(BuildFlavor.includesAlertDanger, configuration == "Debug" || configuration == "Internal")
    }

    func testTheBinaryCheckKnowsTheFunctionAndItsEscapeHatch() throws {
        let script = try read("ci/check-binaries.sh").text

        XCTAssertTrue(script.contains("MIFF_ALLOW_ALERT_DANGER_IN_RELEASE"))
        XCTAssertTrue(script.contains("DangerAlertController"))
        XCTAssertTrue(script.contains("alert_hits"))
    }

    // MARK: - Ce que le code de l'alerte ne fait jamais

    private let forbidden = [
        "print(", "NSLog", "os_log", "Logger(", "debugPrint", "dump(", "Analytics",
        "Crashlytics", "UIPasteboard", "CNContact", "import Contacts", "ContactsUI", "CXCallController", "telprompt",
        "CoreLocation", "CLLocation", "import MapKit", "criticalAlert", "UNNotificationInterruptionLevel"
    ]

    func testTheCodeOfTheAlertNeverLogsNeverTracksAndNeverTouchesAPositionOrTheContacts() throws {
        for path in guardedFiles + pureFiles {
            let source = try read(path)
            for token in forbidden {
                XCTAssertFalse(source.codeLines.contains { $0.contains(token) }, "\(path) : « \(token) »")
            }
        }
    }

    func testNoCoordinateKeyIsEverWrittenByTheAlert() throws {
        let rules = try read("Miff/Models/Alert/DangerAlertRules.swift")
        let regex = try NSRegularExpression(pattern: "\"(lat|lng|latitude|longitude|text|photo|name|message|uid|miffId)\"")

        for line in rules.codeLines {
            let range = NSRange(line.startIndex..., in: line)
            XCTAssertNil(regex.firstMatch(in: line, range: range), "clé interdite dans le document d'alerte : \(line)")
        }
    }

    func testTheCallGoesThroughTheSystemDiallerOnlyAndNeverDirectly() throws {
        let sent = try read("Miff/Views/Alert/DangerAlertHost.swift")
        let model = try read("Miff/ViewModels/DangerAlertViewModel.swift")
        let catalog = try read("Miff/Models/Alert/EmergencyCatalog.swift")

        XCTAssertTrue(sent.codeLines.contains { $0.contains("openURL(url)") })
        XCTAssertFalse(sent.codeLines.contains { $0.contains("UIApplication.shared.open") })
        XCTAssertTrue(model.codeLines.contains { $0.contains("FeedLinks.call(phone:") })
        XCTAssertTrue(catalog.codeLines.contains { $0.contains("\"tel:") })
        XCTAssertFalse(catalog.codeLines.contains { $0.contains("sms:") })
    }

    func testNoContactsPermissionNorExtraBackgroundModeNorCriticalAlertIsDeclared() throws {
        // project.yml : les commentaires YAML (« # ») citent ces clés pour dire qu'elles sont ABSENTES : seules les lignes de configuration comptent.
        let project = try read("project.yml").text.components(separatedBy: "\n")
            .filter { !$0.trimmingCharacters(in: .whitespaces).hasPrefix("#") }
            .joined(separator: "\n")
        let entitlements = try read("Config/Miff.entitlements").text

        XCTAssertFalse(project.contains("NSContactsUsageDescription"))
        XCTAssertFalse(project.contains("UIBackgroundModes"))
        XCTAssertFalse(entitlements.contains("critical"), "Critical Alerts : non en v1 (D18)")
        XCTAssertFalse(entitlements.contains("contacts"))
    }

    func testTheCatalogOfEmergencyNumbersHoldsNoExoticOrPaidNumber() {
        for entry in EmergencyCatalog.all() {
            XCTAssertTrue(["17", "15", "18", "112"].contains(entry.number), entry.number)
        }
    }

    // MARK: - Les textes

    private func alertKeys(_ strings: [String: [String: Any]]) -> [String] {
        let prefixes = ["alert_", "notif_alert_", "feed_label_alert", "feed_bubble_alert", "feed_bubble_me_alert", "feed_bubble_call_action"]
        let extra = ["miff_alert_removed_warning"]
        return strings.keys.filter { key in prefixes.contains { key.hasPrefix($0) } || extra.contains(key) }.sorted()
    }

    private func placeholders(_ text: String) throws -> [String] {
        let regex = try NSRegularExpression(pattern: "%(?:\\d+\\$)?(?:lld|@|d)")
        let range = NSRange(text.startIndex..., in: text)
        return regex.matches(in: text, range: range).compactMap { Range($0.range, in: text).map { String(text[$0]) } }
    }

    func testEveryAlertTextExistsInFrenchAndEnglishWithTheSameValuesAndNoExclamationMark() throws {
        let strings = try AlertCatalog.root()
        let keys = alertKeys(strings)

        XCTAssertGreaterThan(keys.count, 60)
        for key in keys {
            let french = try XCTUnwrap(AlertCatalog.value(key, language: "fr", in: strings), "\(key) : français manquant")
            let english = try XCTUnwrap(AlertCatalog.value(key, language: "en", in: strings), "\(key) : anglais manquant")
            XCTAssertFalse(french.isEmpty || english.isEmpty, key)
            XCTAssertEqual(Set(try placeholders(french)), Set(try placeholders(english)), "\(key) : valeurs insérées différentes")
            XCTAssertFalse(french.contains("!") || english.contains("!"), "\(key) : ton calme, jamais de point d'exclamation")
            XCTAssertFalse(french.lowercased().contains("urgent") && !key.contains("emergency"), key)
        }
    }

    func testTheNotificationTextsCarryOnlyTheFirstNameAndNeverAPositionOrAMiffName() throws {
        let strings = try AlertCatalog.root()
        let keys = ["notif_alert_start_title", "notif_alert_start_body", "notif_alert_end_title", "notif_alert_end_body"]

        for key in keys {
            for language in ["fr", "en"] {
                let text = try XCTUnwrap(AlertCatalog.value(key, language: language, in: strings), key)
                XCTAssertEqual(try placeholders(text), ["%1$@"], "\(key) : le prénom SEUL en paramètre")
                for word in ["position", "localisation", "location", "lieu", "place", "famille", "family"] {
                    XCTAssertFalse(text.lowercased().contains(word), "\(key) : « \(word) »")
                }
            }
        }
        XCTAssertEqual(AlertCatalog.value("notif_alert_end_body", language: "fr", in: strings), "%1$@ a indiqué que tout va bien.")
        XCTAssertEqual(AlertCatalog.value("notif_alert_start_title", language: "en", in: strings), "%1$@ declared an alert")
    }

    func testTheHonestMentionIsThereAndNeverPromisesAnythingNorBlamesAnyone() throws {
        let strings = try AlertCatalog.root()
        let note = try XCTUnwrap(AlertCatalog.value("alert_not_emergency_note", language: "fr", in: strings))

        XCTAssertTrue(note.contains("n'est pas un service d'urgence"))
        XCTAssertTrue(note.contains("112"))
        for key in alertKeys(strings) {
            let french = AlertCatalog.value(key, language: "fr", in: strings)?.lowercased() ?? ""
            for promise in ["il va répondre", "garanti", "secours arrivent", "sauve", "abus", "faute"] {
                XCTAssertFalse(french.contains(promise) && key != "alert_info_body", "\(key) : « \(promise) »")
            }
        }
    }

    func testTheAlertKeysAreTheSameAsAndroidsOnceItsStringsAreInTheTree() throws {
        let android = iosRoot().deletingLastPathComponent()
            .appendingPathComponent("android/app/src/main/res/values-fr/strings.xml")
        guard FileManager.default.fileExists(atPath: android.path) else {
            throw XCTSkip("Fichier des textes d'Android introuvable (\(android.path)).")
        }
        let xml = try String(contentsOf: android, encoding: .utf8)
        let regex = try NSRegularExpression(pattern: "<string name=\"((?:alert_|notif_alert_)[a-z0-9_]+)\"")
        let range = NSRange(xml.startIndex..., in: xml)
        let androidKeys = Set(regex.matches(in: xml, range: range).compactMap { match in
            Range(match.range(at: 1), in: xml).map { String(xml[$0]) }
        })
        guard !androidKeys.isEmpty else {
            throw XCTSkip("Les textes de l'alerte ne sont pas encore dans les ressources d'Android.")
        }
        let iosKeys = Set(alertKeys(try AlertCatalog.root()).filter { $0.hasPrefix("alert_") || $0.hasPrefix("notif_alert_") })

        XCTAssertEqual(iosKeys, androidKeys, "mêmes clés des deux côtés (docs/contracts/i18n-keys.md)")
    }
}
