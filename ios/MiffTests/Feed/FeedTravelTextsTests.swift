// Les assertions citent les phrases réelles du catalogue : leurs lignes sont plus longues que la limite.
// swiftlint:disable line_length
import FirebaseFirestore
import XCTest
@testable import Miff

/// MODE VOYAGE dans le Feed, les PHRASES (VOY-3I) lues dans le VRAI catalogue : « En voyage jusqu'au 15 nov. », « Voyage terminé »,
/// « Depuis le 7 nov. », la bulle (une personne par son prénom, plusieurs par leur nombre, tous les autres), ma carte et la lecture d'un
/// statut de voyage reçu du réseau (quelqu'un d'autre l'a écrit : tout est vérifié).
final class FeedTravelTextsTests: XCTestCase {
    private let day = TravelDay(date: TravelDate(year: 2026, month: 11, day: 15), showYear: false)

    private func lookup(_ language: String) throws -> (String) -> String {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Feed
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
            .appendingPathComponent("Miff/Resources/Localizable.xcstrings")
        let data = try Data(contentsOf: url)
        let root = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        let strings = try XCTUnwrap(root["strings"] as? [String: [String: Any]])
        return { key in
            let localizations = strings[key]?["localizations"] as? [String: Any]
            let entry = localizations?[language] as? [String: Any]
            let unit = entry?["stringUnit"] as? [String: Any]
            return unit?["value"] as? String ?? "MISSING:\(key)"
        }
    }

    private func french() throws -> FeedTexts {
        FeedTexts(lookup: try lookup("fr"), locale: Locale(identifier: "fr_FR"), zone: feedZone, is24Hour: true)
    }

    private func english() throws -> FeedTexts {
        FeedTexts(lookup: try lookup("en"), locale: Locale(identifier: "en_US"), zone: feedZone, is24Hour: false)
    }

    // MARK: - Les étiquettes

    func testTheLabelsOfATripInFrench() throws {
        let texts = try french()

        let active = texts.label(.traveling(day, mine: false))
        XCTAssertTrue(active.hasPrefix("En voyage jusqu'au 15"), active)
        XCTAssertTrue(active.lowercased().contains("nov"), active)
        let mine = texts.label(.traveling(day, mine: true))
        XCTAssertTrue(mine.hasPrefix("Mode voyage jusqu'au 15"), mine)
        XCTAssertEqual(texts.label(.travelEnded), "Voyage terminé")
    }

    func testTheLabelsOfATripInEnglish() throws {
        let texts = try english()

        let active = texts.label(.traveling(day, mine: false))
        XCTAssertTrue(active.hasPrefix("Traveling until"), active)
        XCTAssertTrue(active.contains("Nov"), active)
        XCTAssertEqual(texts.label(.travelEnded), "Trip ended")
        XCTAssertTrue(texts.label(.traveling(day, mine: true)).hasPrefix("Travel mode until"))
    }

    func testTheYearIsInTheLabelOnlyWhenTheDayAsksForIt() throws {
        let texts = try french()
        let next = TravelDay(date: TravelDate(year: 2027, month: 1, day: 2), showYear: true)

        XCTAssertTrue(texts.label(.traveling(next, mine: false)).contains("2027"))
        XCTAssertFalse(texts.label(.traveling(day, mine: false)).contains("2026"))
    }

    func testTheLineUnderTheLabelSaysTheDayTheTripBegan() throws {
        let french = try french().timeLine(.travelSince(feedNow - feedHours(2)))
        let english = try english().timeLine(.travelSince(feedNow - feedHours(2)))

        XCTAssertTrue(french?.hasPrefix("Depuis le") == true, french ?? "nil")
        XCTAssertTrue(english?.hasPrefix("Since") == true, english ?? "nil")
    }

    // MARK: - La bulle

    func testTheBubbleSentencesInFrench() throws {
        let texts = try french()
        let one = texts.travelNote(.one(name: "Thomas", until: day))

        XCTAssertTrue(one.hasPrefix("Thomas est en voyage jusqu'au 15"), one)
        XCTAssertEqual(texts.travelNote(.many(count: 2, all: false)), "2 proches sont en voyage.")
        XCTAssertEqual(texts.travelersHeadline(.many(count: 2, all: true)), "Tes proches sont en voyage.")
        XCTAssertEqual(texts.travelersHeadline(.many(count: 3, all: false)), "3 proches sont en voyage.")
        XCTAssertEqual(texts.travelersHeadline(.one(name: "Thomas", until: day)), one)
    }

    func testTheBubbleSentencesInEnglish() throws {
        let texts = try english()

        XCTAssertTrue(texts.travelNote(.one(name: "Thomas", until: day)).hasPrefix("Thomas is traveling until"))
        XCTAssertEqual(texts.travelNote(.many(count: 2, all: false)), "2 loved ones are traveling.")
        XCTAssertEqual(texts.travelersHeadline(.many(count: 2, all: true)), "Your loved ones are traveling.")
    }

    func testTheScreenReaderSaysTheBubbleThenTheLine() throws {
        let texts = try french()
        let note = TravelNote.many(count: 2, all: false)

        XCTAssertEqual(
            texts.announcement(.allGood(latestNews: nil, answered: nil, total: nil), travelNote: note),
            "\(texts.announcement(.allGood(latestNews: nil, answered: nil, total: nil))). 2 proches sont en voyage."
        )
        XCTAssertEqual(texts.announcement(.travelers(note)), "2 proches sont en voyage.")
        XCTAssertEqual(
            texts.announcement(.noNews, travelNote: nil),
            texts.announcement(.noNews)
        )
    }

    func testTheCardOfATravellerIsReadAsOneBlockWithoutBattery() throws {
        let content = buildFeed(feedInput(
            members: [feedMember("me"), feedMember("t", first: "Thomas")],
            statuses: ["t": travelStatus(battery: 40)]
        ))
        let card = try XCTUnwrap(content.card("t"))

        let spoken = try french().cardSpoken(card)

        XCTAssertTrue(spoken.hasPrefix("Thomas, En voyage jusqu'au 15"), spoken)
        XCTAssertTrue(spoken.contains("Depuis le"), spoken)
        XCTAssertFalse(spoken.contains("batterie"), spoken)
        XCTAssertFalse(spoken.contains("limitée"), spoken)
    }

    // MARK: - Un statut de voyage reçu du réseau

    private func timestamp(_ millis: Int) -> Timestamp {
        Timestamp(date: Date(timeIntervalSince1970: Double(millis) / 1000))
    }

    private func travelStatus(battery: Int? = nil) -> MemberStatus {
        MemberStatus(
            presence: .travel,
            placeId: nil,
            presenceAtMillis: feedNow - feedHours(2),
            lastUpdateAtMillis: nil,
            hasPhoto: false,
            batteryPct: battery,
            locationAccess: .denied,
            updatedAtMillis: feedNow - feedHours(1),
            travelUntilMillis: feedNow + 5 * 24 * FeedMillis.hour,
            travelUntilDay: "2026-11-15"
        )
    }

    func testATripIsReadWithItsEndAndItsDay() {
        let fields: [String: Any] = [
            "presence": "travel",
            "presenceAt": timestamp(feedNow - feedHours(2)),
            "travelUntil": timestamp(feedNow + 5 * 24 * FeedMillis.hour),
            "travelUntilDay": "2026-11-15",
            "updatedAt": timestamp(feedNow)
        ]

        let status = FeedFirestoreMapping.status(fields: fields)

        XCTAssertEqual(status.presence, .travel)
        XCTAssertEqual(status.travelUntilMillis, feedNow + 5 * 24 * FeedMillis.hour)
        XCTAssertEqual(status.travelUntilDay, "2026-11-15")
    }

    func testAnEndWrittenNextToAnotherPresenceIsDroppedAndAnOrdinaryStatusHasNone() {
        let other: [String: Any] = [
            "presence": "out", "placeId": "p1",
            "travelUntil": timestamp(feedNow + FeedMillis.hour), "travelUntilDay": "2026-11-15"
        ]
        let plain: [String: Any] = ["presence": "in_trusted", "placeId": "p1"]

        let dropped = FeedFirestoreMapping.status(fields: other)
        let ordinary = FeedFirestoreMapping.status(fields: plain)

        XCTAssertNil(dropped.travelUntilMillis)
        XCTAssertNil(dropped.travelUntilDay)
        XCTAssertNil(ordinary.travelUntilMillis)
        XCTAssertNil(ordinary.travelUntilDay)
    }

    func testAnOlderApplicationReadsTheValueAsUnknownNeutralAndWithoutAnError() {
        // Le même calcul que celui d'une version qui ne connaît pas `travel` : une présence que l'on ne sait pas lire est nil.
        XCTAssertNil(PresenceValue.fromWire("teleported"))
        let unknown = MemberStatusFacts(sharingEnabled: true, presence: nil, presenceAtMillis: feedNow, lastUpdateAtMillis: nil)
        XCTAssertEqual(StatusColor.level(unknown, nowMillis: feedNow), .neutral)
    }

    func testTheTripOfAStatusIsNeverInADescription() {
        let status = travelStatus()
        let texts = ["\(status)", "\(TravelNote.one(name: "Thomas", until: day))", "\(TravelNote.many(count: 2, all: true))"]

        for text in texts {
            XCTAssertFalse(text.contains("2026"), text)
            XCTAssertFalse(text.contains("Thomas"), text)
        }
    }
}

// swiftlint:enable line_length
