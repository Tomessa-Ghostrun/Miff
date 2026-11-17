// Les assertions citent les phrases réelles du catalogue : leurs lignes sont plus longues que la limite.
// swiftlint:disable line_length
import XCTest
@testable import Miff

/// Les phrases de « Depuis ta dernière visite » (décisions du PM du 2026-11-13), lues dans le VRAI catalogue : sans accord de genre,
/// sans durée ni verdict, l'heure au format du téléphone, le jour de la semaine de 2 à 6 jours puis la date courte, « Un proche » quand le
/// prénom n'est pas encore connu. Miroir de `LastVisitTextsTest.kt` d'Android.
final class LastVisitTextsTests: XCTestCase {
    private let hour = VisitFixtures.hour
    private let minute = VisitFixtures.minute
    private let now = VisitFixtures.now
    private let day = 24 * VisitFixtures.hour

    private func lookup(_ language: String) throws -> (String) -> String {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Notification
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
            .appendingPathComponent("Miff/Resources/Localizable.xcstrings")
        let root = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [String: Any])
        let strings = try XCTUnwrap(root["strings"] as? [String: [String: Any]])
        return { key in
            let localizations = strings[key]?["localizations"] as? [String: Any]
            let entry = localizations?[language] as? [String: Any]
            let unit = entry?["stringUnit"] as? [String: Any]
            return unit?["value"] as? String ?? "MISSING:\(key)"
        }
    }

    private func french() throws -> LastVisitTexts {
        let feed = FeedTexts(lookup: try lookup("fr"), locale: Locale(identifier: "fr_FR"), zone: feedZone, is24Hour: true)
        return LastVisitTexts(feed: feed, zone: feedZone)
    }

    private func english() throws -> LastVisitTexts {
        let feed = FeedTexts(lookup: try lookup("en"), locale: Locale(identifier: "en_US"), zone: feedZone, is24Hour: false)
        return LastVisitTexts(feed: feed, zone: feedZone)
    }

    private func parsed(_ kind: VisitKind, place: PlaceKind? = nil, shownAt: Int) -> VisitParsed {
        VisitParsed(uid: "t", kind: kind, place: place, shownAtMillis: shownAt)
    }

    // MARK: - Les quatre lignes

    func testTheFourLinesInFrenchHaveNoGenderAgreementAndNoDuration() throws {
        let fr = try french()
        let clock = "15\u{00A0}h\u{00A0}02"

        XCTAssertEqual(
            fr.sentence(parsed(.arrival, place: .school, shownAt: now - 58 * minute), firstName: "Thomas", nowMillis: now),
            "Thomas a rejoint l'école à \(clock)"
        )
        XCTAssertEqual(
            fr.sentence(parsed(.departure, place: .home, shownAt: now - 20 * hour), firstName: "Thomas", nowMillis: now),
            "Thomas a quitté la maison hier à 20\u{00A0}h\u{00A0}00"
        )
        XCTAssertEqual(
            fr.sentence(parsed(.update, shownAt: now - 58 * minute), firstName: "Amandine", nowMillis: now),
            "Amandine a donné de ses nouvelles à \(clock)"
        )
    }

    func testTheSilenceIsAFactAndAnHourWithTheRightElision() throws {
        let fr = try french()
        let shown = now - 140 * minute
        let clock = "13\u{00A0}h\u{00A0}40"

        XCTAssertEqual(fr.sentence(parsed(.silence, shownAt: shown), firstName: "Amandine", nowMillis: now), "Dernière nouvelle d'Amandine : \(clock)")
        XCTAssertEqual(fr.sentence(parsed(.silence, shownAt: shown), firstName: "Thomas", nowMillis: now), "Dernière nouvelle de Thomas : \(clock)")
        XCTAssertEqual(fr.sentence(parsed(.silence, shownAt: shown), firstName: "Élodie", nowMillis: now), "Dernière nouvelle d'Élodie : \(clock)")
        XCTAssertEqual(fr.sentence(parsed(.silence, shownAt: shown), firstName: "Hugo", nowMillis: now), "Dernière nouvelle d'Hugo : \(clock)")
    }

    func testTheLinesInEnglish() throws {
        let en = try english()

        XCTAssertEqual(
            en.sentence(parsed(.arrival, place: .school, shownAt: now - 58 * minute), firstName: "Thomas", nowMillis: now),
            "Thomas arrived at school at 3:02\u{00A0}PM"
        )
        XCTAssertEqual(
            en.sentence(parsed(.departure, place: .home, shownAt: now - 20 * hour), firstName: "Thomas", nowMillis: now),
            "Thomas left home yesterday at 8:00\u{00A0}PM"
        )
        XCTAssertEqual(
            en.sentence(parsed(.update, shownAt: now - 58 * minute), firstName: "Amandine", nowMillis: now),
            "Amandine shared an update at 3:02\u{00A0}PM"
        )
        XCTAssertEqual(
            en.sentence(parsed(.silence, shownAt: now - 140 * minute), firstName: "Amandine", nowMillis: now),
            "Amandine's latest news: 1:40\u{00A0}PM"
        )
    }

    // MARK: - Les jours

    func testTheWeekdayFromTwoToSixDaysBackThenTheShortDate() throws {
        let fr = try french()
        let en = try english()
        let wednesday = parsed(.departure, place: .school, shownAt: now - 3 * day)
        let older = parsed(.departure, place: .school, shownAt: now - 10 * day)

        XCTAssertEqual(fr.sentence(wednesday, firstName: "Thomas", nowMillis: now), "Thomas a quitté l'école mercredi à 16\u{00A0}h\u{00A0}00")
        XCTAssertEqual(en.sentence(wednesday, firstName: "Thomas", nowMillis: now), "Thomas left school on Wednesday at 4:00\u{00A0}PM")
        XCTAssertEqual(fr.sentence(older, firstName: "Thomas", nowMillis: now), "Thomas a quitté l'école le 28 oct. à 16\u{00A0}h\u{00A0}00")
        XCTAssertEqual(en.sentence(older, firstName: "Thomas", nowMillis: now), "Thomas left school on Oct 28 at 4:00\u{00A0}PM")
        // Le silence dit l'heure sans « à » devant le jour de la semaine ni la date.
        let silence = parsed(.silence, shownAt: now - 3 * day)
        XCTAssertEqual(fr.sentence(silence, firstName: "Thomas", nowMillis: now), "Dernière nouvelle de Thomas : mercredi à 16\u{00A0}h\u{00A0}00")
    }

    // MARK: - Le prénom, le lieu

    func testAnUnknownNameIsSomeoneNeverABlankAndTheSentenceStartsWithACapital() throws {
        let fr = try french()
        let en = try english()
        let line = parsed(.departure, shownAt: now - hour)

        XCTAssertEqual(fr.sentence(line, firstName: nil, nowMillis: now), "Un proche a quitté un lieu de confiance à 15\u{00A0}h\u{00A0}00")
        XCTAssertEqual(fr.sentence(line, firstName: "  ", nowMillis: now), "Un proche a quitté un lieu de confiance à 15\u{00A0}h\u{00A0}00")
        XCTAssertEqual(en.sentence(line, firstName: nil, nowMillis: now), "Someone left a trusted place at 3:00\u{00A0}PM")
    }

    func testNoSentenceCarriesADurationAVerdictOrAnAlarmWord() throws {
        let kinds: [VisitKind] = [.arrival, .departure, .update, .silence]
        let forbidden = ["depuis", "pas de nouvelles", "inquiet", "alerte", "since", "no news", "worried", "alert", "!"]

        for texts in [try french(), try english()] {
            for kind in kinds {
                let sentence = texts.sentence(parsed(kind, place: .home, shownAt: now - 5 * hour), firstName: "Léa", nowMillis: now)
                for word in forbidden {
                    XCTAssertFalse(sentence.lowercased().contains(word), "« \(sentence) » contient « \(word) »")
                }
            }
        }
    }
}

// swiftlint:enable line_length
