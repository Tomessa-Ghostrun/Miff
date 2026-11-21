import XCTest
@testable import Miff

/// Les textes du carrousel et de l'écran de lancement : chaque clé écrite dans les écrans d'onboarding existe dans le
/// catalogue (le test général ne cherche pas le préfixe `onboarding_`, qui est aussi celui de noms d'événements Analytics),
/// et les textes validés par le PM côté Android sont les mêmes ici.
final class OnboardingTextTests: XCTestCase {
    private var iosRoot: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
    }

    private func catalog() throws -> [String: Any] {
        let url = iosRoot.appendingPathComponent("Miff/Resources/Localizable.xcstrings")
        let data = try Data(contentsOf: url)
        let root = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        return try XCTUnwrap(root["strings"] as? [String: Any])
    }

    private func text(_ key: String, _ language: String, in strings: [String: Any]) -> String? {
        let entry = strings[key] as? [String: Any]
        let localization = (entry?["localizations"] as? [String: Any])?[language] as? [String: Any]
        return (localization?["stringUnit"] as? [String: Any])?["value"] as? String
    }

    func testEveryOnboardingKeyUsedByTheOnboardingScreensExists() throws {
        let strings = try catalog()
        let folder = iosRoot.appendingPathComponent("Miff/Views/Onboarding")
        let files = try FileManager.default.contentsOfDirectory(at: folder, includingPropertiesForKeys: nil)
        let regex = try NSRegularExpression(pattern: "\"(onboarding_[a-z0-9_]+)\"")
        var used = Set<String>()
        for file in files where file.pathExtension == "swift" {
            let source = try String(contentsOf: file, encoding: .utf8)
            for match in regex.matches(in: source, range: NSRange(source.startIndex..., in: source)) {
                if let range = Range(match.range(at: 1), in: source) {
                    used.insert(String(source[range]))
                }
            }
        }

        XCTAssertGreaterThan(used.count, 15, "presque aucune clé trouvée : motif de recherche cassé ?")
        for key in used.sorted() {
            XCTAssertNotNil(strings[key], "clé « \(key) » utilisée mais absente du catalogue")
        }
    }

    func testTheHeadlineIsOnTwoLinesLikeAndroid() throws {
        let strings = try catalog()

        let french = "Savoir qu\u{2019}ils vont bien,\nsans les déranger"
        XCTAssertEqual(text("onboarding_headline", "fr", in: strings), french)
        XCTAssertEqual(text("onboarding_headline", "en", in: strings), "Knowing they are fine,\nwithout bothering them")
    }

    func testTheOfficialLogoIsNoLongerCalledProvisional() throws {
        let strings = try catalog()

        XCTAssertEqual(text("home_logo_a11y", "fr", in: strings), "Logo de Miff")
        XCTAssertEqual(text("home_logo_a11y", "en", in: strings), "Miff logo")
    }

    func testTheConsentLinesNameTheButtonsThatAcceptTheTerms() throws {
        let strings = try catalog()

        // Sur iOS la ligne de l'inscription cite aussi le bouton Apple, puisqu'il crée un compte comme « Rejoindre ».
        let notice = try XCTUnwrap(text("ios_auth_consent_notice", "fr", in: strings))
        let noticeEn = try XCTUnwrap(text("ios_auth_consent_notice", "en", in: strings))
        let panel = try XCTUnwrap(text("ios_auth_panel_notice", "fr", in: strings))
        XCTAssertTrue(notice.contains("Continuer avec Apple"))
        XCTAssertTrue(noticeEn.contains("Continue with Apple"))
        XCTAssertTrue(panel.contains("« Continuer »"))
    }

    func testTheConsentLinesSayTheChoiceCanBeChangedAtAnyTimeInTheSettings() throws {
        let strings = try catalog()

        // Réglages > Confidentialité a son interrupteur (ONB-3b) : la phrase le promet comme sur Android, dans chaque langue, avec le nom que
        // cette langue donne aux Réglages. Plus d'adresse où écrire pour retirer son accord : la phrase n'a que deux valeurs.
        for key in ["ios_auth_consent_notice", "ios_auth_panel_notice"] {
            for language in try I18nRegistry.shipped() {
                let value = try XCTUnwrap(text(key, language, in: strings), key)
                let settings = try XCTUnwrap(text("settings_title", language, in: strings), language)
                XCTAssertTrue(value.contains(settings), "\(key) (\(language)) : les Réglages ne sont pas cités")
                XCTAssertFalse(value.contains("%3$@"), "\(key) (\(language)) : plus d'adresse de contact")
            }
        }
    }

    func testTheIosConsentLinesEndLikeTheAndroidOnesInEveryLanguage() throws {
        let strings = try catalog()

        // Même fin de phrase qu'Android (la parenthèse sur les Réglages), dans les six langues ; seul le début diffère (le bouton Apple).
        let pairs = [
            ("ios_auth_consent_notice", "auth_consent_notice"),
            ("ios_auth_panel_notice", "auth_panel_notice")
        ]
        for (ios, android) in pairs {
            for language in try I18nRegistry.shipped() {
                let iosText = try XCTUnwrap(text(ios, language, in: strings), ios)
                let androidText = try XCTUnwrap(text(android, language, in: strings), android)
                let open = try XCTUnwrap(androidText.range(of: "(", options: .backwards), android)
                XCTAssertTrue(iosText.hasSuffix(String(androidText[open.lowerBound...])), "\(ios) (\(language))")
            }
        }
    }

    func testTheSignInMessagesPointToThePasswordResetLikeAndroid() throws {
        let strings = try catalog()

        let invalid = try XCTUnwrap(text("auth_error_invalid_credentials", "fr", in: strings))
        let tooMany = try XCTUnwrap(text("auth_error_too_many", "fr", in: strings))
        XCTAssertTrue(invalid.contains("Mot de passe oublié ?"))
        XCTAssertTrue(tooMany.contains("réinitialise ton mot de passe"))
    }
}
