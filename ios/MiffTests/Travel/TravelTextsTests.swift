// Les assertions citent les phrases réelles du catalogue : leurs lignes sont plus longues que la limite.
// swiftlint:disable line_length
import XCTest
@testable import Miff

/// MODE VOYAGE, les TEXTES (VOY-2I, critères P4 et P5 de docs/tickets/mode-voyage.md) lus dans le VRAI catalogue : tutoiement, ton calme,
/// et surtout la vérité sur la position (critère BLOQUANT de confiance) : aucun texte ne dit ni ne laisse croire que le voyage arrête,
/// suspend ou masque le partage de la position ; la confirmation et l'explication disent que la position reste partagée.
final class TravelTextsTests: XCTestCase {
    private func catalog() throws -> [String: [String: Any]] {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Travel
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
            .appendingPathComponent("Miff/Resources/Localizable.xcstrings")
        let data = try Data(contentsOf: url)
        let root = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        return try XCTUnwrap(root["strings"] as? [String: [String: Any]])
    }

    private func text(_ key: String, _ language: String, in strings: [String: [String: Any]]) -> String {
        let localizations = strings[key]?["localizations"] as? [String: Any]
        let entry = localizations?[language] as? [String: Any]
        let unit = entry?["stringUnit"] as? [String: Any]
        return unit?["value"] as? String ?? "MISSING:\(key)"
    }

    private func voyageKeys(_ strings: [String: [String: Any]]) -> [String] {
        strings.keys.filter { $0.hasPrefix("voyage_") }.sorted()
    }

    func testTheKeysOfTheTripAreAllThereInBothLanguages() throws {
        let strings = try catalog()
        let keys = voyageKeys(strings)

        XCTAssertGreaterThanOrEqual(keys.count, 27)
        for key in keys {
            for language in ["fr", "en"] {
                XCTAssertFalse(text(key, language, in: strings).isEmpty, "\(key) \(language)")
                XCTAssertFalse(text(key, language, in: strings).hasPrefix("MISSING"), "\(key) \(language)")
            }
        }
    }

    /// P5 : aucune phrase ne dit que le voyage arrête, suspend ou masque la POSITION.
    func testNoTextSaysThatTheTripStopsPausesOrHidesTheLocation() throws {
        let strings = try catalog()
        let frenchVerbs = ["pause", "arrête", "suspend", "désactive", "masque", "privé", "invisible", "cach"]
        let englishVerbs = ["pause", "stop", "suspend", "disable", "hide", "hidden", "private", "invisible", "mask"]
        let frenchTargets = ["position", "localis"]
        let englishTargets = ["location", "position"]
        for key in voyageKeys(strings) {
            for (language, verbs, targets) in [("fr", frenchVerbs, frenchTargets), ("en", englishVerbs, englishTargets)] {
                let sentences = text(key, language, in: strings).components(separatedBy: CharacterSet(charactersIn: ".!?"))
                for sentence in sentences {
                    let lowered = sentence.lowercased()
                    let namesTheLocation = targets.contains { lowered.contains($0) }
                    let hasForbiddenVerb = verbs.contains { lowered.contains($0) }
                    XCTAssertFalse(namesTheLocation && hasForbiddenVerb, "\(key) \(language) : « \(sentence) »")
                }
            }
        }
    }

    /// P5 : la confirmation et l'explication disent que la position reste partagée.
    func testTheConfirmationAndTheExplanationSayThatTheLocationStaysShared() throws {
        let strings = try catalog()
        for key in ["voyage_confirm_body", "voyage_card_explain"] {
            XCTAssertTrue(text(key, "fr", in: strings).contains("Tes proches continuent de voir ta position"), key)
            XCTAssertTrue(text(key, "en", in: strings).contains("Your circle still sees your location"), key)
        }
    }

    func testTheTextsAreInTheSecondPersonSingularAndCalm() throws {
        let strings = try catalog()
        for key in voyageKeys(strings) {
            let french = text(key, "fr", in: strings).lowercased()
            XCTAssertFalse(french.contains("vous "), key)
            XCTAssertFalse(french.contains("votre "), key)
            for language in ["fr", "en"] {
                let value = text(key, language, in: strings)
                XCTAssertFalse(value.contains("!"), "\(key) \(language) : jamais de point d'exclamation")
                XCTAssertFalse(value.lowercased().contains("alerte"), "\(key) \(language)")
                XCTAssertFalse(value.lowercased().contains("danger"), "\(key) \(language)")
            }
        }
    }

    func testTheLabelsOfTheCardAreTheOnesOfTheTicket() throws {
        let strings = try catalog()

        XCTAssertEqual(text("voyage_card_title", "fr", in: strings), "Mode voyage")
        XCTAssertEqual(text("voyage_card_title", "en", in: strings), "Travel mode")
        XCTAssertEqual(text("voyage_feed_label_ended", "fr", in: strings), "Voyage terminé")
        XCTAssertEqual(text("voyage_card_end_label", "fr", in: strings), "Date de fin")
        XCTAssertEqual(text("voyage_confirm_action", "fr", in: strings), "Activer")
        XCTAssertEqual(text("voyage_confirm_cancel", "fr", in: strings), "Annuler")
        XCTAssertEqual(text("voyage_feed_mine_edit", "fr", in: strings), "Modifier")
        XCTAssertEqual(text("voyage_bubble_all", "fr", in: strings), "Tes proches sont en voyage.")
        XCTAssertEqual(text("voyage_card_on_status", "fr", in: strings), "En voyage jusqu'au %1$@")
        XCTAssertEqual(text("voyage_card_on_status", "en", in: strings), "Traveling until %1$@")
    }
}

/// Le jour d'un voyage écrit par le formateur du système, dans la langue du lecteur.
final class TravelDayFormatTests: XCTestCase {
    private let day = TravelDate(year: 2026, month: 11, day: 15)

    func testTheShortFormIsTheOneOfTheLanguageWithoutTheYear() {
        let french = TravelDayFormat.short(TravelDay(date: day, showYear: false), locale: Locale(identifier: "fr_FR"))
        let english = TravelDayFormat.short(TravelDay(date: day, showYear: false), locale: Locale(identifier: "en_US"))

        XCTAssertTrue(french.hasPrefix("15"), french)
        XCTAssertTrue(french.lowercased().contains("nov"), french)
        XCTAssertFalse(french.contains("2026"), french)
        XCTAssertTrue(english.contains("Nov"), english)
        XCTAssertTrue(english.contains("15"), english)
        XCTAssertFalse(english.contains("2026"), english)
    }

    func testTheYearIsAddedOnlyWhenTheDayAsksForIt() {
        let withYear = TravelDayFormat.short(TravelDay(date: day, showYear: true), locale: Locale(identifier: "fr_FR"))
        XCTAssertTrue(withYear.contains("2026"), withYear)
        XCTAssertTrue(TravelDayFormat.withYear(day, locale: Locale(identifier: "en_US")).contains("2026"))
    }

    func testTheSpokenFormHasTheFullMonthName() {
        let french = TravelDayFormat.spoken(day, locale: Locale(identifier: "fr_FR"))
        let english = TravelDayFormat.spoken(day, locale: Locale(identifier: "en_US"))

        XCTAssertTrue(french.contains("novembre"), french)
        XCTAssertTrue(french.contains("2026"), french)
        XCTAssertTrue(english.contains("November"), english)
    }

    func testTheDayIsNeverShiftedByTheTimeZoneOfThePhone() {
        // Le jour du calendrier ne bouge pas, où que soit le lecteur : posé à midi UTC et formaté en UTC, donc le 31 reste le 31 et le 1er
        // janvier reste le 1er janvier, y compris autour de minuit.
        let english = Locale(identifier: "en_US")
        XCTAssertEqual(TravelDayFormat.withYear(TravelDate(year: 2026, month: 12, day: 31), locale: english), "Dec 31, 2026")
        XCTAssertEqual(TravelDayFormat.withYear(TravelDate(year: 2027, month: 1, day: 1), locale: english), "Jan 1, 2027")
    }
}
