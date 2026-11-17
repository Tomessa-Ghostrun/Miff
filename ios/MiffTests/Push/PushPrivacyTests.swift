import XCTest
@testable import Miff

/// SRV-2I / SRV-4I : ce que les notifications push ne doivent JAMAIS faire, vérifié sur les SOURCES et la configuration pour qu'un changement
/// ultérieur ne puisse pas le casser sans que personne le voie : aucun jeton dans un journal ni dans Analytics, Firebase Messaging confiné à UN
/// fichier, le service éteint au démarrage, la substitution automatique coupée, aucun mode d'arrière-plan. Miroir de `PushPrivacyTest` d'Android.
final class PushPrivacyTests: XCTestCase {
    private var iosRoot: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Push
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
    }

    private static let tokenSource = "Services/Firebase/FirebasePushTokenSource.swift"

    /// Les lignes de code (ni commentaires ni lignes vides) de tous les fichiers de l'app, avec leur chemin relatif à `Miff/`.
    private func appSources() throws -> [(path: String, lines: [String])] {
        let folder = iosRoot.appendingPathComponent("Miff")
        let files = try XCTUnwrap(FileManager.default.enumerator(at: folder, includingPropertiesForKeys: nil))
        var found: [(path: String, lines: [String])] = []
        for case let file as URL in files where file.pathExtension == "swift" {
            let text = try String(contentsOf: file, encoding: .utf8)
            let relative = file.path.replacingOccurrences(of: folder.path + "/", with: "")
            let code = text.split(separator: "\n", omittingEmptySubsequences: true)
                .map { String($0) }
                .filter { !$0.trimmingCharacters(in: .whitespaces).hasPrefix("//") }
            found.append((relative, code))
        }
        return found
    }

    private func pushFiles(_ sources: [(path: String, lines: [String])]) -> [(path: String, lines: [String])] {
        sources.filter { source in
            source.path.hasPrefix("Models/Push/") || source.path.hasPrefix("Services/Push/")
                || source.path == Self.tokenSource
                || source.path == "Services/Firebase/FirestoreDeviceTokenRegistry.swift"
                || source.path == "App/PushServices.swift" || source.path == "Views/PushLifecycle.swift"
        }
    }

    func testThePushFilesExist() throws {
        XCTAssertGreaterThanOrEqual(pushFiles(try appSources()).count, 12)
    }

    func testNothingInThePushCodeWritesToALogOrToAnalytics() throws {
        let forbidden = [
            "print(", "NSLog", "os_log", "Logger(", "debugPrint", "dump(", "tracker", "AnalyticsEvent", "Analytics.",
            "Crashlytics", "setCustomValue", "recordError"
        ]
        for source in pushFiles(try appSources()) {
            for line in source.lines {
                for fragment in forbidden where line.contains(fragment) {
                    XCTFail("\(source.path) : « \(fragment) » interdit dans le code des notifications push")
                }
            }
        }
    }

    func testFirebaseMessagingAndTheRegistrationAreConfinedToTheTokenSource() throws {
        let confined = [
            "import FirebaseMessaging", "Messaging.messaging()", "registerForRemoteNotifications", "isAutoInitEnabled",
            "deleteToken", "apnsToken"
        ]
        for source in try appSources() where source.path != Self.tokenSource {
            for line in source.lines {
                for fragment in confined where line.contains(fragment) {
                    XCTFail("\(source.path) : « \(fragment) » n'est permis que dans \(Self.tokenSource)")
                }
            }
        }
    }

    func testTheServiceIsOnlySwitchedOnByTheTokenSourceAndSwitchedOffOnExit() throws {
        let lines = try XCTUnwrap(try appSources().first { $0.path == Self.tokenSource }?.lines)
        XCTAssertEqual(lines.filter { $0.contains("isAutoInitEnabled = true") }.count, 1)
        XCTAssertEqual(lines.filter { $0.contains("isAutoInitEnabled = false") }.count, 1)
        XCTAssertTrue(lines.contains { $0.contains("deleteToken()") })
        XCTAssertTrue(lines.contains { $0.contains("unregisterForRemoteNotifications") })
    }

    func testTheTokenIsNeverPartOfAnyDescriptionOrString() throws {
        for source in pushFiles(try appSources()) {
            for line in source.lines where line.contains("description") && line.contains("token") {
                XCTFail("\(source.path) : une description ne doit jamais contenir le jeton : \(line)")
            }
            for line in source.lines where line.contains("\\(token") || line.contains("\\(entry.token") {
                XCTFail("\(source.path) : le jeton ne doit pas être interpolé dans un texte : \(line)")
            }
        }
    }

    func testOnlyTheGateRecordsTheConsentAndTheEntitlementIsThere() throws {
        // Un seul endroit enregistre l'accord à être notifiée : la porte des autorisations (l'écran qui porte la phrase sur l'identifiant).
        let callers = try appSources().filter { source in
            source.path != "Services/Push/PushPreferenceStores.swift"
                && source.lines.contains { $0.contains("pushConsent.give(") || $0.contains("consent.give(") }
        }.map { $0.path }
        XCTAssertEqual(callers, ["ViewModels/PermissionGateViewModel.swift"])
    }

    // MARK: - Configuration

    private func projectLines() throws -> [String] {
        let text = try String(contentsOf: iosRoot.appendingPathComponent("project.yml"), encoding: .utf8)
        return text.split(separator: "\n", omittingEmptySubsequences: true)
            .map { String($0).trimmingCharacters(in: .whitespaces) }
            .filter { !$0.hasPrefix("#") }
    }

    func testTheProjectSwitchesTheTokenServiceOffAndTheAutomaticSubstitutionOff() throws {
        let lines = try projectLines()
        XCTAssertTrue(lines.contains("FirebaseMessagingAutoInitEnabled: false"))
        XCTAssertTrue(lines.contains("FirebaseAppDelegateProxyEnabled: false"))
        XCTAssertTrue(lines.contains("product: FirebaseMessaging"))
    }

    func testNoBackgroundModeIsDeclaredForThePush() throws {
        // Les alertes sont affichées par le système : « remote-notification » (réveil de l'app) n'est pas nécessaire (guideline 2.5.4).
        let lines = try projectLines()
        XCTAssertFalse(lines.contains { $0.contains("UIBackgroundModes") || $0.contains("remote-notification") })
    }

    func testTheEntitlementsDeclareTheApnsEnvironment() throws {
        let text = try String(
            contentsOf: iosRoot.appendingPathComponent("Config/Miff.entitlements"), encoding: .utf8
        )
        XCTAssertTrue(text.contains("<key>aps-environment</key>"))
        // Une seule valeur écrite : celle du profil de signature la remplace (voir docs/dev/fcm-ios.md).
        XCTAssertTrue(text.contains("<string>development</string>"))
    }

    func testExactlyOneDelegateIsSetOnTheNotificationCenter() throws {
        // Un seul délégué peut être posé : celui des messages du serveur, qui chaîne les rappels locaux par next (docs/dev/fcm-ios.md §7).
        let setters = try appSources().filter { source in
            source.lines.contains { $0.contains("UNUserNotificationCenter.current().delegate") }
        }.map { $0.path }
        XCTAssertEqual(setters, ["App/PushServices.swift"])
    }

    func testTheRunningAppHasTheSameSwitchesInItsInfoPlist() {
        let info = Bundle.main.infoDictionary ?? [:]
        XCTAssertEqual(info["FirebaseMessagingAutoInitEnabled"] as? Bool, false)
        XCTAssertEqual(info["FirebaseAppDelegateProxyEnabled"] as? Bool, false)
        XCTAssertNil(info["UIBackgroundModes"])
    }

    func testTheScreenThatRecordsTheConsentCarriesTheSentenceAboutThePhoneIdentifier() throws {
        // Toute surface qui ouvre la fenêtre du système doit porter cette phrase (consentement explicite, ticket §15).
        let url = iosRoot.appendingPathComponent("Miff/Resources/Localizable.xcstrings")
        let root = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [String: Any])
        let strings = try XCTUnwrap(root["strings"] as? [String: [String: Any]])
        let key = "ios_permission_notifications_note"
        let entry = try XCTUnwrap(strings[key]?["localizations"] as? [String: [String: Any]])
        let sentences = [
            "fr": "un identifiant technique de ton téléphone, fourni par Apple pour envoyer les notifications",
            "en": "a technical identifier of your phone, provided by Apple to send notifications"
        ]
        for (language, sentence) in sentences {
            let unit = try XCTUnwrap(entry[language]?["stringUnit"] as? [String: Any])
            XCTAssertTrue((unit["value"] as? String ?? "").contains(sentence), language)
        }
        let screens = try String(
            contentsOf: iosRoot.appendingPathComponent("Miff/Views/Permission/PermissionScreens.swift"), encoding: .utf8
        )
        XCTAssertTrue(screens.contains("Text(\"ios_permission_notifications_note\")"))
    }

    func testTheNotificationTextsAreInTheCatalogForTheServerKeys() throws {
        // Le serveur envoie ces DEUX clés de texte (backend/functions/src/notifRegles.ts) : le système les résout dans la langue du téléphone.
        let url = iosRoot.appendingPathComponent("Miff/Resources/Localizable.xcstrings")
        let root = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [String: Any])
        let strings = try XCTUnwrap(root["strings"] as? [String: [String: Any]])
        for key in ["notif_circle_update_title", "notif_circle_update_body"] {
            let localizations = try XCTUnwrap(strings[key]?["localizations"] as? [String: [String: Any]], key)
            XCTAssertEqual(Set(localizations.keys), ["fr", "en"], key)
        }
        let title = try XCTUnwrap(
            ((strings["notif_circle_update_title"]?["localizations"] as? [String: [String: Any]])?["fr"]?["stringUnit"]
                as? [String: Any])?["value"] as? String
        )
        XCTAssertEqual(title, "%1$@ a donné des nouvelles")
    }
}
