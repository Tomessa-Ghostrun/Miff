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

        XCTAssertEqual(text("onboarding_headline", "fr", in: strings), "Savoir qu\u{2019}ils vont bien,\nsans les déranger")
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
        XCTAssertTrue(try XCTUnwrap(text("ios_auth_consent_notice", "fr", in: strings)).contains("Continuer avec Apple"))
        XCTAssertTrue(try XCTUnwrap(text("ios_auth_consent_notice", "en", in: strings)).contains("Continue with Apple"))
        XCTAssertTrue(try XCTUnwrap(text("auth_panel_notice", "fr", in: strings)).contains("« Continuer »"))
    }

    func testTheSignInMessagesPointToThePasswordResetLikeAndroid() throws {
        let strings = try catalog()

        XCTAssertTrue(try XCTUnwrap(text("auth_error_invalid_credentials", "fr", in: strings)).contains("Mot de passe oublié ?"))
        XCTAssertTrue(try XCTUnwrap(text("auth_error_too_many", "fr", in: strings)).contains("réinitialise ton mot de passe"))
    }
}
