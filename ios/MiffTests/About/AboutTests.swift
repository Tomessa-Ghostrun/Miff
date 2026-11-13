import XCTest
@testable import Miff

/// Réglages > « À propos et aide » (ticket `reglages-a-propos.md`, ABOUT-1I) : la ligne et son ordre, les Réglages restreints, la copie de la
/// version, l'adresse du formulaire (UN endroit, https), « Nous contacter » déplacé hors de Confidentialité, et les textes.
final class AboutTests: XCTestCase {
    private final class SpyPasteboard: TextPasteboard {
        var written: [String] = []

        func writeText(_ text: String) {
            written.append(text)
        }
    }

    // MARK: - La ligne dans les Réglages (AP1, AP4)

    func testTheAboutRowIsTheLastRowOfTheSettings() {
        XCTAssertEqual(SettingsAccess.rows(restricted: false).last, .about)
        XCTAssertEqual(
            SettingsAccess.rows(restricted: false),
            [.myInfo, .account, .notifications, .language, .privacy, .about]
        )
    }

    func testTheAboutRowIsAlsoInTheRestrictedSettingsAndIsLastThere() {
        XCTAssertEqual(SettingsAccess.rows(restricted: true), [.account, .privacy, .about])
    }

    func testTheAboutScreenAndItsLicencesAreAllowedWithoutAMiffAndReachableInTwoTouches() {
        XCTAssertTrue(SettingsAccess.isAllowed(.about, restricted: true))
        XCTAssertTrue(SettingsAccess.isAllowed(.licenses, restricted: true))
        XCTAssertTrue(SettingsAccess.children(of: .settings).contains(.about))
        XCTAssertTrue(SettingsAccess.reachable(restricted: true).contains(.about))
        XCTAssertTrue(SettingsAccess.reachable(restricted: true).contains(.licenses))
        XCTAssertEqual(SettingsAccess.children(of: .about), [.detectionHelp, .notificationsHelp, .licenses])
    }

    func testTheNotificationHelpStaysUnreachableWithoutAMiffEvenThroughTheAboutScreen() {
        XCTAssertFalse(SettingsAccess.isAllowed(.notificationsHelp, restricted: true))
        XCTAssertFalse(SettingsAccess.reachable(restricted: true).contains(.notificationsHelp))
        XCTAssertTrue(SettingsAccess.reachable(restricted: false).contains(.notificationsHelp))
    }

    func testTheDeletionIsStillReachedByMyAccountOnly() {
        let parents = SettingsRoute.allCases.filter { SettingsAccess.children(of: $0).contains(.deleteAccount) }

        XCTAssertEqual(parents, [.account])
    }

    // MARK: - Les lignes d'aide (AP5, AP6, AP8)

    func testTheHelpRowsAreInTheOrderOfTheTicketWithTheFeedbackFormLast() {
        let url = FeedbackForm.url

        XCTAssertEqual(
            AboutContent.helpRows(restricted: false, feedbackURL: url),
            [.detectionHelp, .notificationsHelp, .contact, .feedback]
        )
    }

    func testARestrictedAccountKeepsTheContactButNotTheNotificationHelp() {
        let rows = AboutContent.helpRows(restricted: true, feedbackURL: FeedbackForm.url)

        XCTAssertEqual(rows, [.detectionHelp, .contact, .feedback])
    }

    func testWithoutAValidAddressThereIsNoFeedbackRowAndNothingElseChanges() {
        XCTAssertEqual(
            AboutContent.helpRows(restricted: false, feedbackURL: nil),
            [.detectionHelp, .notificationsHelp, .contact]
        )
        XCTAssertEqual(AboutContent.helpRows(restricted: true, feedbackURL: nil), [.detectionHelp, .contact])
    }

    // MARK: - L'adresse du formulaire (AP9, AP19)

    func testTheFeedbackFormAddressIsHttpsAndIsOpenedExactlyAsWritten() throws {
        XCTAssertTrue(FeedbackForm.address.hasPrefix("https://"))
        let url = try XCTUnwrap(FeedbackForm.url)

        XCTAssertEqual(url.scheme, "https")
        XCTAssertEqual(url.absoluteString, FeedbackForm.address)
    }

    func testTheFeedbackFormAddressCarriesNoPersonalDataNorQuery() throws {
        let url = try XCTUnwrap(FeedbackForm.url)

        XCTAssertNil(url.query)
        XCTAssertNil(url.fragment)
        XCTAssertNil(url.user)
        XCTAssertNil(url.password)
        XCTAssertFalse(FeedbackForm.address.contains("@"))
    }

    func testAnAddressThatIsNotAPlainHttpsLinkIsRefused() {
        let refused = [
            "", "   ", "http://forms.gle/abc", "ftp://forms.gle/abc", "javascript:alert(1)", "https://", "https:///abc",
            "https://forms.gle/a b", "https://forms.gle/a\"b", "https://forms.gle/a\\b", "https://forms.gle/$x",
            "https://forms.gle/a\nb", " https://forms.gle/abc", "HTTPS://forms.gle/abc"
        ]
        for text in refused {
            XCTAssertNil(FeedbackForm.validURL(from: text), "« \(text) » aurait dû être refusée")
        }
        XCTAssertNotNil(FeedbackForm.validURL(from: "https://forms.gle/abc"))
    }

    func testTheAddressIsWrittenInOnePlaceOnly() throws {
        let sources = try sourceFiles(in: ["Miff"])
        let holders = sources.filter { $0.text.contains("forms.gle/") }.map(\.path)

        XCTAssertEqual(holders, ["Miff/Models/About/AboutModels.swift"])
    }

    // MARK: - La version (AP3, Q7)

    func testTheCopiedVersionIsTheNameTheVersionAndTheBuildAndNothingElse() {
        let build = BuildInfo(version: "0.1.0", build: "12", configuration: "Internal")
        let pasteboard = SpyPasteboard()

        AboutVersion.copy(build, to: pasteboard)

        XCTAssertEqual(pasteboard.written, ["Miff 0.1.0 (12)"])
    }

    func testAMissingVersionIsCopiedWithThePlaceholderNeverEmpty() {
        let build = BuildInfo(infoDictionary: nil)

        XCTAssertEqual(build.copyText, "Miff ? (?)")
    }

    func testTheVersionLineReadsTheInstalledBuild() {
        let build = BuildInfo(version: "0.1.0", build: "12")

        XCTAssertEqual(L10n.format("about_version_label", build.version, build.build), "Version 0.1.0 (build 12)")
    }

    // MARK: - « Nous contacter » déplacé (AP7)

    func testContactIsNoLongerInPrivacyAndIsInTheAboutScreen() throws {
        let sources = try sourceFiles(in: ["Miff/Views"])
        let privacy = try XCTUnwrap(sources.first { $0.path.hasSuffix("Settings/AccountAndPrivacyViews.swift") })
        let about = try XCTUnwrap(sources.first { $0.path.hasSuffix("About/AboutView.swift") })

        XCTAssertFalse(privacy.text.contains("settings_privacy_contact"))
        XCTAssertFalse(privacy.text.contains("supportMailURL"))
        XCTAssertTrue(about.text.contains("settings_privacy_contact"))
        XCTAssertTrue(about.text.contains("supportMailURL"))
    }

    func testTheContactIsAMailLinkWithoutAPrefilledText() throws {
        let url = try XCTUnwrap(ContactLinks.supportMailURL())

        XCTAssertEqual(url.scheme, "mailto")
        XCTAssertEqual(url.absoluteString, "mailto:\(AppLinks.supportEmail)")
        XCTAssertNil(url.query)
    }

    func testTheVersionIsNoLongerAtTheBottomOfTheSettingsList() throws {
        let sources = try sourceFiles(in: ["Miff/Views/Settings"])
        let list = try XCTUnwrap(sources.first { $0.path.hasSuffix("Settings/SettingsScreens.swift") })

        XCTAssertFalse(list.text.contains("ios_settings_version"))
        XCTAssertFalse(list.text.contains("build.version"))
    }

    // MARK: - Textes (AP10, AP13, AP16)

    func testEveryAboutKeyUsedInTheCodeExistsInFrenchAndEnglish() throws {
        let catalog = try loadStrings()
        let regex = try NSRegularExpression(pattern: "\"((?:ios_)?about_[a-z0-9_]+)\"")
        var used = Set<String>()
        for file in try sourceFiles(in: ["Miff/Views", "Miff/Models"]) {
            let range = NSRange(file.text.startIndex..., in: file.text)
            for match in regex.matches(in: file.text, range: range) {
                if let keyRange = Range(match.range(at: 1), in: file.text) {
                    used.insert(String(file.text[keyRange]))
                }
            }
        }

        XCTAssertGreaterThan(used.count, 15)
        for key in used.sorted() {
            let entry = try XCTUnwrap(catalog[key], "Clé « \(key) » absente du catalogue")
            for language in ["fr", "en"] {
                XCTAssertFalse(text(of: entry, language).isEmpty, "\(key) : texte \(language) manquant")
            }
        }
    }

    func testTheFeedbackTextsNeverMentionAStoreARatingOrStars() throws {
        let catalog = try loadStrings()
        let words = "store|stores|app store|google play|note|notes|noter|rating|rate|star|stars|étoile|étoiles"
        let forbidden = try NSRegularExpression(pattern: "\\b(\(words))\\b", options: .caseInsensitive)
        for key in catalog.keys where key.hasPrefix("about_feedback_") {
            for language in ["fr", "en"] {
                let value = try text(of: XCTUnwrap(catalog[key]), language)
                let range = NSRange(value.startIndex..., in: value)
                XCTAssertNil(forbidden.firstMatch(in: value, range: range), "\(key) (\(language)) : « \(value) »")
            }
        }
    }

    func testTheAboutTextsNeverWriteTheContactOrTheFormAddress() throws {
        let catalog = try loadStrings()
        for key in catalog.keys where key.hasPrefix("about_") || key.hasPrefix("ios_about_") {
            for language in ["fr", "en"] {
                let value = try text(of: XCTUnwrap(catalog[key]), language)
                XCTAssertFalse(value.contains(AppLinks.supportEmail), "\(key) : le contact est dans un texte traduit")
                XCTAssertFalse(value.contains("gmail"), "\(key) : une adresse e-mail dans un texte traduit")
                XCTAssertFalse(value.contains("http"), "\(key) : une adresse écrite dans un texte traduit")
            }
        }
    }

    func testTheIPhoneAboutTextsDoNotMentionTheAndroidMapServices() throws {
        let catalog = try loadStrings()
        let names = ["maplibre", "openfreemap", "photon", "openmaptiles", "komoot"]
        for key in catalog.keys where key.hasPrefix("about_") || key.hasPrefix("ios_about_") {
            for language in ["fr", "en"] {
                let value = try text(of: XCTUnwrap(catalog[key]), language).lowercased()
                for name in names {
                    XCTAssertFalse(value.contains(name), "\(key) (\(language)) cite \(name)")
                }
            }
        }
    }

    func testTheAppleMapsLineIsThere() throws {
        XCTAssertTrue(L10n.string("ios_about_licenses_maps_apple").contains("MapKit"))
    }

    // MARK: - Aides

    private struct SourceFile {
        let path: String
        let text: String
    }

    private var iosRoot: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // About
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
    }

    private func sourceFiles(in folders: [String]) throws -> [SourceFile] {
        var found: [SourceFile] = []
        for folder in folders {
            let url = iosRoot.appendingPathComponent(folder)
            guard let files = FileManager.default.enumerator(at: url, includingPropertiesForKeys: nil) else {
                throw XCTSkip("Sources introuvables (\(url.path)).")
            }
            for case let file as URL in files where file.pathExtension == "swift" {
                let path = file.path.replacingOccurrences(of: iosRoot.path + "/", with: "")
                found.append(SourceFile(path: path, text: try String(contentsOf: file, encoding: .utf8)))
            }
        }
        return found
    }

    private func loadStrings() throws -> [String: [String: Any]] {
        let url = iosRoot.appendingPathComponent("Miff/Resources/Localizable.xcstrings")
        guard FileManager.default.fileExists(atPath: url.path) else {
            throw XCTSkip("Catalogue source introuvable (\(url.path)).")
        }
        let root = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [String: Any])
        return try XCTUnwrap(root["strings"] as? [String: [String: Any]])
    }

    private func text(of entry: [String: Any], _ language: String) -> String {
        let localization = (entry["localizations"] as? [String: Any])?[language] as? [String: Any]
        return (localization?["stringUnit"] as? [String: Any])?["value"] as? String ?? ""
    }
}
