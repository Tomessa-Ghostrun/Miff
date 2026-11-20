import XCTest
@testable import Miff

/// Les textes `countdown_*` (ticket §2.3 et §2.7) en français et en anglais : exactement ceux d'Android, durées écrites par le catalogue (jamais
/// par le formateur du système), espace insécable entre le nombre et l'unité, tutoiement, aucun mot d'alerte ni point d'exclamation.
final class CountdownTextsTests: XCTestCase {
    private let nbsp = "\u{00A0}"

    private func texts(_ language: String) throws -> CountdownTexts {
        let path = try XCTUnwrap(Bundle.main.path(forResource: language, ofType: "lproj"), language)
        let bundle = try XCTUnwrap(Bundle(path: path))
        return CountdownTexts(bundle: bundle, locale: Locale(identifier: language))
    }

    // MARK: - Les durées (§2.3)

    func testFrenchDurationsFollowTheTable() throws {
        let fr = try texts("fr")
        XCTAssertEqual(fr.duration(.hoursMinutes(hours: 1, minutes: 12)), "1\(nbsp)h 12\(nbsp)min")
        XCTAssertEqual(fr.duration(.hoursMinutes(hours: 1, minutes: 5)), "1\(nbsp)h 05\(nbsp)min")
        XCTAssertEqual(fr.duration(.hoursMinutes(hours: 1, minutes: 59)), "1\(nbsp)h 59\(nbsp)min")
        XCTAssertEqual(fr.duration(.hours(1)), "1\(nbsp)h")
        XCTAssertEqual(fr.duration(.hours(2)), "2\(nbsp)h")
        XCTAssertEqual(fr.duration(.minutes(47)), "47\(nbsp)min")
        XCTAssertEqual(fr.duration(.minutes(2)), "2\(nbsp)min")
        XCTAssertEqual(fr.duration(.lessThanMinute), "moins d'1\(nbsp)min")
    }

    func testEnglishDurationsFollowTheTable() throws {
        let en = try texts("en")
        XCTAssertEqual(en.duration(.hoursMinutes(hours: 1, minutes: 12)), "1\(nbsp)h 12\(nbsp)min")
        XCTAssertEqual(en.duration(.hoursMinutes(hours: 1, minutes: 5)), "1\(nbsp)h 05\(nbsp)min")
        XCTAssertEqual(en.duration(.hours(2)), "2\(nbsp)h")
        XCTAssertEqual(en.duration(.minutes(47)), "47\(nbsp)min")
        XCTAssertEqual(en.duration(.lessThanMinute), "less than 1\(nbsp)min")
    }

    func testNoDurationEverShowsSeconds() throws {
        for language in try I18nRegistry.shipped() {
            let localized = try texts(language)
            for minutes in 1...120 {
                let text = localized.duration(CountdownDuration.make(minutes: minutes))
                XCTAssertFalse(text.contains(" s"), "\(language) \(minutes) : \(text)")
                XCTAssertFalse(text.hasSuffix("s"), "\(language) \(minutes) : \(text)")
            }
        }
    }

    // MARK: - Les phrases

    func testFrenchSentences() throws {
        let fr = try texts("fr")
        XCTAssertEqual(fr.title, "Tu es en dehors de tes lieux de confiance")
        XCTAssertEqual(fr.body(.hoursMinutes(hours: 1, minutes: 12)), "Tu passeras orange dans 1\(nbsp)h 12\(nbsp)min")
        XCTAssertEqual(fr.body(.lessThanMinute), "Tu passeras orange dans moins d'1\(nbsp)min")
        XCTAssertEqual(fr.accessibilityAction, "Donner de tes nouvelles")
    }

    func testEnglishSentences() throws {
        let en = try texts("en")
        XCTAssertEqual(en.title, "You're outside your trusted places")
        XCTAssertEqual(en.body(.minutes(47)), "You'll turn orange in 47\(nbsp)min")
        XCTAssertEqual(en.accessibilityAction, "Share an update")
    }

    func testNoAlertWordNoExclamationMarkAndNoDangerWordInAnyLanguage() throws {
        for language in try I18nRegistry.shipped() {
            let localized = try texts(language)
            let all = [
                localized.title, localized.body(.hours(2)), localized.body(.lessThanMinute),
                localized.accessibilityLabel(.minutes(30)), localized.accessibilityAction
            ].joined(separator: " ").lowercased()
            XCTAssertFalse(all.contains("!"), language)
            for word in ["attention", "vite", "plus que", "urgent", "alert", "hurry", "danger", "rouge", "red"] {
                XCTAssertFalse(all.contains(word), "\(language) : « \(word) »")
            }
        }
    }

    // MARK: - La durée parlée (§2.7, §2.9)

    func testFrenchSpokenDurations() throws {
        let fr = try texts("fr")
        XCTAssertEqual(fr.spoken(.hoursMinutes(hours: 1, minutes: 12)), "1 heure et 12 minutes")
        XCTAssertEqual(fr.spoken(.hoursMinutes(hours: 1, minutes: 59)), "1 heure et 59 minutes")
        XCTAssertEqual(fr.spoken(.hours(2)), "2 heures")
        XCTAssertEqual(fr.spoken(.hours(1)), "1 heure")
        XCTAssertEqual(fr.spoken(.minutes(47)), "47 minutes")
        XCTAssertEqual(fr.spoken(.lessThanMinute), "moins d'une minute")
    }

    func testEnglishSpokenDurations() throws {
        let en = try texts("en")
        XCTAssertEqual(en.spoken(.hoursMinutes(hours: 1, minutes: 12)), "1 hour and 12 minutes")
        XCTAssertEqual(en.spoken(.hours(2)), "2 hours")
        XCTAssertEqual(en.spoken(.minutes(47)), "47 minutes")
        XCTAssertEqual(en.spoken(.lessThanMinute), "less than a minute")
    }

    func testTheAccessibilityLabelSaysBothSentencesWithTheSpokenDuration() throws {
        let fr = try texts("fr")
        XCTAssertEqual(
            fr.accessibilityLabel(.hoursMinutes(hours: 1, minutes: 12)),
            "Tu es en dehors de tes lieux de confiance. Tu passeras orange dans 1 heure et 12 minutes."
        )
        let en = try texts("en")
        XCTAssertEqual(
            en.accessibilityLabel(.minutes(47)),
            "You're outside your trusted places. You'll turn orange in 47 minutes."
        )
    }

    func testTheSpokenLabelNeverCarriesTheNonBreakingSpaceOfTheWrittenDuration() throws {
        for language in try I18nRegistry.shipped() {
            let localized = try texts(language)
            for minutes in [1, 2, 47, 60, 61, 119, 120] {
                let label = localized.accessibilityLabel(CountdownDuration.make(minutes: minutes))
                XCTAssertFalse(label.contains(nbsp), "\(language) \(minutes) : \(label)")
            }
        }
    }

    // MARK: - La durée à partir des minutes

    func testDurationFromMinutes() {
        XCTAssertEqual(CountdownDuration.make(minutes: 120), .hours(2))
        XCTAssertEqual(CountdownDuration.make(minutes: 119), .hoursMinutes(hours: 1, minutes: 59))
        XCTAssertEqual(CountdownDuration.make(minutes: 61), .hoursMinutes(hours: 1, minutes: 1))
        XCTAssertEqual(CountdownDuration.make(minutes: 60), .hours(1))
        XCTAssertEqual(CountdownDuration.make(minutes: 59), .minutes(59))
        XCTAssertEqual(CountdownDuration.make(minutes: 2), .minutes(2))
        XCTAssertEqual(CountdownDuration.make(minutes: 1), .lessThanMinute)
        XCTAssertEqual(CountdownDuration.make(minutes: 0), .lessThanMinute)
    }
}
