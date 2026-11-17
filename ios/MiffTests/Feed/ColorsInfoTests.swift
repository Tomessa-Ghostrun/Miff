// Les assertions citent les phrases réelles du catalogue : leurs lignes dépassent la limite.
// swiftlint:disable line_length
import XCTest
@testable import Miff

/// COULEURS-2 : l'écran « Les couleurs de ta Miff » et le petit bouton « i » de la bulle (décision du PM du 2026-11-13). Les durées lues dans
/// `StatusThresholds` (C2), l'ordre des blocs et les textes exacts en français et en anglais (C1), le ton (C5, C6), la parité du catalogue (T1),
/// l'écran statique (H1, H2), la mise en page du bouton « i » (zone de toucher, aucun recouvrement du contenu), la navigation (un seul écran
/// ouvert, aucune ouverture en touchant la bulle). Miroir de `ColorsInfoScreenTest.kt` d'Android.
final class ColorsInfoTests: XCTestCase {
    private var iosRoot: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Feed
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
    }

    private func source(_ relative: String) throws -> String {
        try String(contentsOf: iosRoot.appendingPathComponent("Miff").appendingPathComponent(relative), encoding: .utf8)
    }

    private func bundle(_ language: String) throws -> Bundle {
        let path = try XCTUnwrap(Bundle.main.path(forResource: language, ofType: "lproj"), language)
        return try XCTUnwrap(Bundle(path: path))
    }

    private func content(_ language: String, durations: ColorsInfoDurations = .fromThresholds) throws -> ColorsInfoContent {
        let locale = Locale(identifier: language == "fr" ? "fr_FR" : "en_US")
        return .make(durations: durations, locale: locale, bundle: try bundle(language))
    }

    /// Les espaces insécables que le formateur du système écrit parfois sont ramenés à une espace simple.
    private func normalized(_ text: String) -> String {
        text.replacingOccurrences(of: "\u{00A0}", with: " ").replacingOccurrences(of: "\u{202F}", with: " ")
    }

    private func allTexts(_ content: ColorsInfoContent) -> [String] {
        var texts = [content.title, content.intro]
        for item in content.items {
            switch item {
            case .heading(let text), .note(let text), .line(let text):
                texts.append(text)
            case .person(_, let title, let body), .miff(_, let title, let body), .block(let title, let body):
                texts += [title, body]
            }
        }
        return texts.map(normalized)
    }

    // MARK: - Les durées (C2, STA9)

    func testTheDurationsAreTheThresholdsOfTheFeed() {
        let durations = ColorsInfoDurations.fromThresholds

        XCTAssertEqual(durations.orangeAfterMillis, StatusThresholds.orangeAfterMillis)
        XCTAssertEqual(durations.redAfterMillis, StatusThresholds.redAfterMillis)
        XCTAssertEqual(durations.dangerGreenMillis, StatusThresholds.dangerZoneRedAfterMillis)
        XCTAssertEqual(WholeDuration.of(millis: durations.orangeAfterMillis), WholeDuration(amount: 2, unit: .hour))
        XCTAssertEqual(WholeDuration.of(millis: durations.redAfterMillis), WholeDuration(amount: 4, unit: .hour))
        XCTAssertEqual(WholeDuration.of(millis: durations.dangerGreenMillis), WholeDuration(amount: 1, unit: .hour))
    }

    func testADurationThatIsNotWholeHoursIsSaidInMinutesNeverRoundedToWrongHours() {
        XCTAssertEqual(WholeDuration.of(millis: 90 * 60_000), WholeDuration(amount: 90, unit: .minute))
        XCTAssertEqual(WholeDuration.of(millis: 1_000), WholeDuration(amount: 1, unit: .minute))
    }

    func testTheFormatterOfTheSystemWritesTheDurationInTheLanguageOfThePhone() {
        let hours = { (count: Int) in WholeDuration(amount: count, unit: .hour) }
        let french = Locale(identifier: "fr_FR")
        let english = Locale(identifier: "en_US")

        XCTAssertEqual(normalized(hours(2).text(locale: english)), "2 hours")
        XCTAssertEqual(normalized(hours(1).text(locale: english)), "1 hour")
        XCTAssertEqual(normalized(hours(2).text(locale: french)), "2 heures")
        XCTAssertEqual(normalized(hours(1).text(locale: french)), "1 heure")
        XCTAssertEqual(normalized(WholeDuration(amount: 90, unit: .minute).text(locale: french)), "90 minutes")
    }

    func testTheTextsShowTheThresholds2h4h1hInFrench() throws {
        let texts = allTexts(try content("fr"))

        XCTAssertTrue(texts.contains("Tout va bien. La personne est dans un lieu de confiance, ou elle en est partie depuis 2 heures au plus."))
        XCTAssertTrue(texts.contains("Un peu de silence. La personne a quitté un lieu de confiance et n'a pas donné de nouvelles depuis plus de 2 heures (jusqu'à 4 heures)."))
        XCTAssertTrue(texts.contains { $0.hasPrefix("Pas de nouvelles depuis plus de 4 heures après avoir quitté un lieu de confiance.") })
        XCTAssertTrue(texts.contains("Si ta Miff a défini une zone de danger, la personne passe au rouge dès qu'elle y entre. Si elle donne des nouvelles après son arrivée, elle repasse au vert pendant 1 heure."))
        XCTAssertTrue(texts.contains("Le temps qui passe sans nouvelles : orange après 2 heures, rouge après 4 heures."))
    }

    func testInEnglishTheDurationsFollowTheLanguage() throws {
        let english = try content("en")
        let texts = allTexts(english)

        XCTAssertEqual(english.title, "Your Miff's colors")
        XCTAssertTrue(texts.contains("A little quiet. The person left a trusted place and hasn't shared news for more than 2 hours (up to 4 hours)."))
        XCTAssertTrue(texts.contains("Time passing without news: orange after 2 hours, red after 4 hours."))
        XCTAssertTrue(texts.contains("If your Miff has set a danger zone, the person turns red as soon as they enter it. If they share news after arriving, they turn green for 1 hour."))
    }

    func testChangingAThresholdChangesTheTexts() throws {
        let durations = ColorsInfoDurations(
            orangeAfterMillis: 3 * 3_600_000, redAfterMillis: 6 * 3_600_000, dangerGreenMillis: 90 * 60_000
        )
        let texts = allTexts(try content("fr", durations: durations))

        XCTAssertTrue(texts.contains { $0.contains("plus de 3 heures (jusqu'à 6 heures)") })
        XCTAssertTrue(texts.contains { $0.contains("repasse au vert pendant 90 minutes") })
        XCTAssertTrue(texts.contains("Le temps qui passe sans nouvelles : orange après 3 heures, rouge après 6 heures."))
        XCTAssertFalse(texts.contains { $0.contains("2 heures") || $0.contains("4 heures") }, "une durée écrite en dur")
    }

    // MARK: - Les blocs et leur ordre (C1)

    private func kind(_ item: ColorsInfoItem) -> String {
        switch item {
        case .heading:
            return "heading"
        case .note:
            return "note"
        case .person:
            return "person"
        case .miff:
            return "miff"
        case .block:
            return "block"
        case .line:
            return "line"
        }
    }

    func testTheFiveBlocksAreInTheOrderOfTheSpec() throws {
        let screen = try content("fr")
        let expected = [
            "heading", "note", "person", "person", "person", "person", "person", "block",
            "heading", "line", "line", "line", "line", "line",
            "heading", "note", "miff", "miff", "miff", "miff", "line",
            "heading", "line", "line", "line", "line"
        ]

        XCTAssertEqual(screen.items.map(kind), expected)
        XCTAssertEqual(screen.title, "Les couleurs de ta Miff")
        XCTAssertTrue(screen.intro.hasPrefix("Les couleurs te disent, d'un coup d'œil"))
    }

    func testTheColourLinesAreInOrderWithTheirLevelAndTheirNameInFull() throws {
        let screen = try content("fr")
        var people: [(StatusLevel, String)] = []
        var miffs: [(StatusLevel, String)] = []
        for item in screen.items {
            switch item {
            case .person(let level, let title, _):
                people.append((level, title))
            case .miff(let level, let title, _):
                miffs.append((level, title))
            default:
                break
            }
        }

        XCTAssertEqual(people.map(\.0), [.green, .orange, .red, .neutral, .neutral])
        XCTAssertEqual(people.map(\.1), ["Vert", "Orange", "Rouge", "Gris", "Gris, en voyage"])
        XCTAssertEqual(miffs.map(\.0), [.green, .orange, .red, .neutral])
        XCTAssertEqual(miffs.map(\.1), ["Vert", "Orange", "Rouge", "Gris"])
    }

    // MARK: - Les textes eux-mêmes (C2, C5, C6, T1)

    private func catalogTexts(prefix: String) throws -> [String: [String: String]] {
        let url = iosRoot.appendingPathComponent("Miff/Resources/Localizable.xcstrings")
        let root = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [String: Any])
        let strings = try XCTUnwrap(root["strings"] as? [String: [String: Any]])
        var found: [String: [String: String]] = [:]
        for (key, entry) in strings where key.hasPrefix(prefix) {
            let localizations = entry["localizations"] as? [String: Any] ?? [:]
            for language in ["fr", "en"] {
                let unit = (localizations[language] as? [String: Any])?["stringUnit"] as? [String: Any]
                found[key, default: [:]][language] = unit?["value"] as? String
            }
        }
        return found
    }

    func testBothLanguagesHaveTheSame39KeysWithTheSamePlaceholders() throws {
        let texts = try catalogTexts(prefix: "feed_colors_")

        XCTAssertEqual(texts.count, 39)
        let placeholder = try NSRegularExpression(pattern: "%\\d\\$@")
        for (key, languages) in texts {
            let french = try XCTUnwrap(languages["fr"], "\(key) : français manquant")
            let english = try XCTUnwrap(languages["en"], "\(key) : anglais manquant")
            let frenchHoles = placeholder.matches(in: french, range: NSRange(french.startIndex..., in: french)).count
            let englishHoles = placeholder.matches(in: english, range: NSRange(english.startIndex..., in: english)).count
            XCTAssertEqual(frenchHoles, englishHoles, "\(key) : valeurs insérées différentes")
        }
    }

    func testNoCatalogTextWritesAnHourCountOnlyPlaceholders() throws {
        let placeholder = try NSRegularExpression(pattern: "%\\d\\$@")
        for (key, languages) in try catalogTexts(prefix: "feed_colors_") {
            for (language, value) in languages {
                let bare = placeholder.stringByReplacingMatches(
                    in: value, range: NSRange(value.startIndex..., in: value), withTemplate: ""
                ).lowercased()
                XCTAssertNil(bare.firstIndex { $0.isNumber }, "\(language)/\(key) : un chiffre écrit en dur")
                XCTAssertFalse(bare.contains("heures"), "\(language)/\(key) : « heures » écrit en dur")
                XCTAssertFalse(bare.contains("hour"), "\(language)/\(key) : « hour » écrit en dur")
            }
        }
    }

    func testTheToneIsCalmNoExclamationNoSafetyPromiseNoAlarmWord() throws {
        for (key, languages) in try catalogTexts(prefix: "feed_colors_") {
            for (language, value) in languages {
                let lower = value.lowercased()
                let label = "\(language)/\(key)"
                XCTAssertFalse(value.contains("!"), "\(label) : point d'exclamation")
                XCTAssertFalse(lower.contains("sécurité") || lower.contains("safe") || lower.contains("en sûreté"), "\(label) : promesse de sécurité")
                if key != "feed_colors_not_alert" {
                    XCTAssertFalse(lower.contains("urgence") || lower.contains("emergency"), label)
                }
                if key != "feed_colors_danger_title" && key != "feed_colors_danger_body" {
                    XCTAssertFalse(lower.contains("danger"), label)
                }
                XCTAssertNil(value.range(of: "\\b[A-ZÉÈ]{4,}\\b", options: .regularExpression), "\(label) : majuscules d'insistance")
            }
        }
    }

    // MARK: - Un écran statique (H1, H2, H3)

    func testTheScreenAndItsContentReadNothingAndSendNothing() throws {
        let forbidden = ["Firestore", "Firebase", "URLSession", "Analytics", "tracker", "UserDefaults", "Keychain", "print(", "NSLog", "Logger"]
        for file in ["Views/Feed/ColorsInfoView.swift", "Models/Feed/ColorsInfoContent.swift", "Models/Tracking/ColorsInfoDurations.swift", "Views/Feed/BubbleInfoButton.swift"] {
            for line in UpdatePrivacyTests.codeLines(try source(file)) {
                for word in forbidden {
                    XCTAssertFalse(line.contains(word), "\(file) : « \(word) » dans « \(line) »")
                }
            }
        }
    }

    func testTheScreenHasNoImageNoButtonAndNoColourValueOfItsOwn() throws {
        let code = UpdatePrivacyTests.codeLines(try source("Views/Feed/ColorsInfoView.swift")).joined(separator: "\n")

        XCTAssertFalse(code.contains("Image("), "aucune image")
        XCTAssertFalse(code.contains("Button" + "("), "lecture seule : aucun bouton (le retour vient du cadre)")
        XCTAssertNil(code.range(of: "(?<![A-Za-z])Color\\(", options: .regularExpression), "aucune valeur de couleur écrite ici")
    }
}
// swiftlint:enable line_length
