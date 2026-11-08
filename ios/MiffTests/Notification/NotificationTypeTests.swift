import XCTest
@testable import Miff

/// Les textes du PM (mot pour mot), leurs clés dans le catalogue, les destinations, la nuit calme, la navigation et les demandes d'onglet.
@MainActor
final class NotificationTypeTests: XCTestCase {
    private var catalogURL: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Notification
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
            .appendingPathComponent("Miff/Resources/Localizable.xcstrings")
    }

    private func catalog() throws -> [String: [String: Any]] {
        let root = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: catalogURL)) as? [String: Any])
        return try XCTUnwrap(root["strings"] as? [String: [String: Any]])
    }

    private func text(_ key: String, _ language: String, in catalog: [String: [String: Any]]) throws -> String {
        let entry = try XCTUnwrap(catalog[key], "clé absente : \(key)")
        let localizations = try XCTUnwrap(entry["localizations"] as? [String: Any])
        let localization = try XCTUnwrap(localizations[language] as? [String: Any])
        let unit = try XCTUnwrap(localization["stringUnit"] as? [String: Any])
        return try XCTUnwrap(unit["value"] as? String)
    }

    // MARK: - Les textes du PM, mot pour mot

    func testTheTextsAreTheOnesOfThePMWordForWord() throws {
        let catalog = try catalog()
        let expected: [(NotificationType, String, String, String, String)] = [
            (.n1, "Tu es passé orange", "Indique à ta Miff que tout va bien.",
             "You've turned orange", "Let your Miff know everything's fine."),
            (.n2, "Tu es passé rouge", "Indique à ta Miff que tout va bien.",
             "You've turned red", "Let your Miff know everything's fine."),
            (.n3, "Ta Miff est passée en orange", "Prends des nouvelles de tes proches.",
             "Your Miff has turned orange", "Check in on your loved ones."),
            (.n4, "Ta Miff est passée en rouge", "Prends des nouvelles de tes proches.",
             "Your Miff has turned red", "Check in on your loved ones."),
            (.n6, "Tu es dans une zone de danger de ta Miff",
             "Pendant que tu y es, tes proches voient un état différent.",
             "You're in a danger zone of your Miff", "While you're there, your circle sees a different status.")
        ]
        for (type, frTitle, frBody, enTitle, enBody) in expected {
            XCTAssertEqual(try text(type.titleKey, "fr", in: catalog), frTitle, "\(type)")
            XCTAssertEqual(try text(type.bodyKey, "fr", in: catalog), frBody, "\(type)")
            XCTAssertEqual(try text(type.titleKey, "en", in: catalog), enTitle, "\(type)")
            XCTAssertEqual(try text(type.bodyKey, "en", in: catalog), enBody, "\(type)")
        }
    }

    func testNoTextOfANotificationCarriesAValueAnHourOrShouting() throws {
        let catalog = try catalog()
        for type in NotificationType.allCases {
            for language in ["fr", "en"] {
                let title = try text(type.titleKey, language, in: catalog)
                let body = try text(type.bodyKey, language, in: catalog)
                for sentence in [title, body] {
                    XCTAssertFalse(sentence.contains("%"), "texte figé à la programmation : aucune valeur")
                    let hour = sentence.range(of: "[0-9]{1,2} ?[h:] ?[0-9]{0,2}", options: .regularExpression)
                    XCTAssertNil(hour, sentence)
                    XCTAssertEqual(sentence, sentence.trimmingCharacters(in: .whitespaces))
                    XCTAssertNil(sentence.range(of: "[A-ZÉ]{3,}", options: .regularExpression), "pas de cris")
                    XCTAssertFalse(sentence.contains("!"), sentence)
                }
            }
        }
    }

    func testEveryTextOfTheNotificationFeatureExistsInFrenchAndEnglish() throws {
        let catalog = try catalog()
        let keys = catalog.keys.filter { $0.hasPrefix("notif_") || $0.hasPrefix("ios_notif_") }

        XCTAssertGreaterThanOrEqual(keys.count, 50)
        for key in keys.sorted() {
            let localizations = try XCTUnwrap(catalog[key]?["localizations"] as? [String: Any], key)
            for language in ["fr", "en"] {
                let localization = try XCTUnwrap(localizations[language] as? [String: Any], "\(key) \(language)")
                let hasText = localization["stringUnit"] != nil || localization["variations"] != nil
                XCTAssertTrue(hasText, "\(key) \(language)")
            }
        }
    }

    func testEveryKeyOfTheTypesResolvesToATextInTheApp() {
        for type in NotificationType.allCases {
            for key in [type.titleKey, type.bodyKey, type.settingTitleKey] {
                XCTAssertNotEqual(L10n.string(key), key, "Pas de texte pour la clé \(key)")
            }
        }
    }

    func testEveryNotifKeyUsedByTheSourcesExistsInTheCatalog() throws {
        let root = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .appendingPathComponent("Miff")
        let catalog = try catalog()
        let regex = try NSRegularExpression(pattern: "\"((?:ios_)?notif_[a-z0-9_]+)\"")
        var missing = Set<String>()
        guard let files = FileManager.default.enumerator(at: root, includingPropertiesForKeys: nil) else {
            throw XCTSkip("Sources introuvables.")
        }
        for case let file as URL in files where file.pathExtension == "swift" {
            let source = try String(contentsOf: file, encoding: .utf8)
            let range = NSRange(source.startIndex..., in: source)
            for match in regex.matches(in: source, range: range) {
                if let key = Range(match.range(at: 1), in: source).map({ String(source[$0]) }), catalog[key] == nil {
                    missing.insert(key)
                }
            }
        }

        XCTAssertEqual(missing, [], "clés utilisées par le code et absentes du catalogue")
    }

    // MARK: - Destinations et nuit

    func testTheDestinationsAreUpdateForNOneAndNTwoAndTheFeedForTheRest() {
        XCTAssertEqual(NotificationType.n1.destination, .update)
        XCTAssertEqual(NotificationType.n2.destination, .update)
        XCTAssertEqual(NotificationType.n3.destination, .feed)
        XCTAssertEqual(NotificationType.n4.destination, .feed)
        XCTAssertEqual(NotificationType.n6.destination, .feed, "en zone de danger publier ne ramène pas au vert")
    }

    func testAtNightOnlyTheRedOnesKeepTheirSound() {
        XCTAssertEqual(NotificationType.allCases.filter { $0.silentAtNight }, [.n1, .n3, .n6])
        XCTAssertFalse(NotificationType.n2.silentAtNight)
        XCTAssertFalse(NotificationType.n4.silentAtNight)
    }

    // MARK: - Navigation

    func testTheUpdateScreenFallsBackToTheFeedWhenTheBuildHasNoCentralButton() {
        XCTAssertEqual(NotificationNavigation.tab(for: .update, showsUpdateButton: true), .update)
        XCTAssertEqual(NotificationNavigation.tab(for: .update, showsUpdateButton: false), .feed)
        XCTAssertEqual(NotificationNavigation.tab(for: .feed, showsUpdateButton: true), .feed)
        XCTAssertEqual(NotificationNavigation.tab(for: .feed, showsUpdateButton: false), .feed)
    }

    func testAskingTheSameTabTwiceIsTwoRequests() {
        let requests = TabRequests()
        XCTAssertNil(requests.latest)

        requests.request(.feed)
        let first = requests.latest
        requests.request(.feed)

        XCTAssertEqual(first?.tab, .feed)
        XCTAssertEqual(requests.latest?.tab, .feed)
        XCTAssertNotEqual(requests.latest, first, "sinon la coquille ne verrait pas la seconde demande")
    }

    // MARK: - L'état de l'autorisation

    func testTheStateWordsAndWhatTheyAllow() {
        XCTAssertEqual(NotificationAuthorization.authorized.stateTextKey, "notif_state_allowed")
        XCTAssertEqual(NotificationAuthorization.denied.stateTextKey, "notif_state_off")
        XCTAssertEqual(NotificationAuthorization.notDetermined.stateTextKey, "notif_state_notasked")
        XCTAssertTrue(NotificationAuthorization.authorized.isAllowed)
        XCTAssertFalse(NotificationAuthorization.denied.isAllowed)
        XCTAssertFalse(NotificationAuthorization.notDetermined.isAllowed)
        XCTAssertTrue(NotificationAuthorization.denied.needsPhoneSettings)
        XCTAssertFalse(NotificationAuthorization.notDetermined.needsPhoneSettings)
    }

    func testTheGeneralSwitchTruthTable() {
        let on = NotificationPreferences()
        let off = NotificationPreferences().settingGeneral(false)
        let authorizations: [NotificationAuthorization] = [.authorized, .denied, .notDetermined]
        for authorization in authorizations {
            XCTAssertEqual(
                NotificationGeneralSwitch.isOn(preferences: on, authorization: authorization),
                authorization == .authorized
            )
            XCTAssertFalse(NotificationGeneralSwitch.isOn(preferences: off, authorization: authorization))
            XCTAssertEqual(NotificationGeneralSwitch.isEnabled(authorization: authorization), authorization != .denied)
        }
    }
}
