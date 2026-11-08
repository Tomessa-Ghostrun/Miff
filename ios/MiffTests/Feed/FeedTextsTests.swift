// Les assertions citent les phrases réelles du catalogue : leurs lignes sont plus longues que la limite.
// swiftlint:disable line_length
import XCTest
@testable import Miff

/// Les textes du VRAI catalogue (`Localizable.xcstrings`, lu depuis les sources) dans une langue : les tests des phrases du Feed
/// vérifient donc ce que la personne lira, pas une copie.
private enum Catalog {
    static func lookup(language: String) throws -> (String) -> String {
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
}

// Les phrases du Feed (ticket FEED §7) : heures du téléphone (« 16 h 02 », « 4:02 PM »), tutoiement, « zone de danger », aucune
// « zone à risque », aucune phrase de sécurité. 16 h 00 à Paris est l'instant « maintenant » (voir `FeedTestSupport`).
final class FeedTextsTests: XCTestCase {
    private func french(is24Hour: Bool = true) throws -> FeedTexts {
        FeedTexts(
            lookup: try Catalog.lookup(language: "fr"),
            locale: Locale(identifier: "fr_FR"),
            zone: feedZone,
            is24Hour: is24Hour
        )
    }

    private func english(is24Hour: Bool = false) throws -> FeedTexts {
        FeedTexts(
            lookup: try Catalog.lookup(language: "en"),
            locale: Locale(identifier: "en_US"),
            zone: feedZone,
            is24Hour: is24Hour
        )
    }

    private func moment(_ minutesAgo: Int, day: Day = .today) -> Moment {
        Moment(atMillis: feedNow - feedMinutes(minutesAgo), day: day)
    }

    func testTheClockAndTheDateFollowTheLanguageAndThePhone() throws {
        let fr = try french()
        let en = try english()

        XCTAssertEqual(fr.clock(feedNow - feedMinutes(58)), "15\u{00A0}h\u{00A0}02")
        XCTAssertEqual(en.clock(feedNow - feedMinutes(58)), "3:02\u{00A0}PM")
        XCTAssertEqual(try english(is24Hour: true).clock(feedNow - feedMinutes(58)), "15:02")
        XCTAssertEqual(fr.date(feedNow - 4 * FeedMillis.day), "3 nov.")
        XCTAssertEqual(en.date(feedNow - 4 * FeedMillis.day), "Nov 3")
    }

    func testMomentsAreSaidWithTheirDay() throws {
        let fr = try french()
        let clock = "15\u{00A0}h\u{00A0}02"

        XCTAssertEqual(fr.at(moment(58)), "à \(clock)")
        XCTAssertEqual(fr.at(moment(58, day: .yesterday)), "hier à \(clock)")
        XCTAssertEqual(fr.since(moment(58)), clock)
        XCTAssertEqual(fr.from(moment(58)), "de \(clock)")
        XCTAssertEqual(fr.from(moment(58, day: .yesterday)), "d'hier à \(clock)")
        XCTAssertEqual(fr.at(Moment(atMillis: feedNow - 4 * FeedMillis.day, day: .other)), "le 3 nov. à 16\u{00A0}h\u{00A0}00")
    }

    func testLabels() throws {
        let fr = try french()
        let en = try english()

        XCTAssertEqual(fr.label(.at(.category(.home))), "À la maison")
        XCTAssertEqual(fr.label(.at(.category(.school))), "À l'école")
        XCTAssertEqual(fr.label(.at(.named("Chez Mamie"))), "Chez Mamie", "Loisir, Autre : le nom du lieu, seul")
        XCTAssertEqual(fr.label(.at(.unknown)), "Dans un lieu de confiance")
        XCTAssertEqual(fr.label(.left(.category(.work))), "A quitté le travail")
        XCTAssertEqual(fr.label(.left(.unknown)), "A quitté un lieu de confiance")
        XCTAssertEqual(fr.label(.danger), "Dans une zone de danger")
        XCTAssertEqual(fr.label(.noNews), "Pas encore de nouvelles")
        XCTAssertEqual(fr.label(.sharingStopped), "Partage arrêté")
        XCTAssertEqual(en.label(.danger), "In a danger zone")
        XCTAssertEqual(en.label(.left(.category(.home))), "Left home")
    }

    /// Le mapping décidé par le PM (2026-11-08) : Maison, Travail, École, Sport, Chez un proche ont chacun leur étiquette ; Loisir et Autre
    /// disent le nom du lieu.
    func testTheLabelOfEachCategoryInFrenchAndEnglish() throws {
        let fr = try french()
        let en = try english()
        let frLabels: [PlaceKind: String] = [
            .home: "À la maison", .work: "Au travail", .school: "À l'école", .sport: "Au sport", .relative: "Chez un proche"
        ]
        let enLabels: [PlaceKind: String] = [
            .home: "At home", .work: "At work", .school: "At school", .sport: "At the gym", .relative: "At a loved one's"
        ]

        for kind in [PlaceKind.home, .work, .school, .sport, .relative] {
            XCTAssertEqual(fr.label(.at(.category(kind))), frLabels[kind])
            XCTAssertEqual(en.label(.at(.category(kind))), enLabels[kind])
        }
        XCTAssertEqual(fr.label(.at(.named("Salle Rivoli"))), "Salle Rivoli")
        XCTAssertEqual(en.label(.at(.named("Salle Rivoli"))), "Salle Rivoli")
    }

    func testLeavingAPlaceByCategorySaysItInAFullSentence() throws {
        let fr = try french()
        let en = try english()

        XCTAssertEqual(fr.label(.left(.category(.relative))), "A quitté le domicile d'un proche")
        XCTAssertEqual(en.label(.left(.category(.relative))), "Left a loved one's place")
        XCTAssertEqual(fr.label(.left(.category(.sport))), "A quitté le sport")
        XCTAssertEqual(en.label(.left(.category(.sport))), "Left the gym")
    }

    func testTimeLines() throws {
        let fr = try french()
        let clock = "15\u{00A0}h\u{00A0}02"

        XCTAssertEqual(fr.timeLine(.since(.minutes(12))), "Depuis 12 min")
        XCTAssertEqual(fr.timeLine(.since(.hours(3))), "Depuis 3 h")
        XCTAssertEqual(fr.timeLine(.since(.yesterday(atMillis: feedNow - feedHours(30)))), "Depuis hier à 10\u{00A0}h\u{00A0}00")
        XCTAssertEqual(fr.timeLine(.departedAgo(.minutes(12))), "Départ il y a 12 min")
        XCTAssertEqual(fr.timeLine(.latestNews(moment(58))), "Dernière nouvelle à \(clock)")
        XCTAssertEqual(fr.timeLine(.noNewsSince(moment(58))), "Pas de nouvelles depuis \(clock)")
        XCTAssertEqual(fr.timeLine(.inDangerSince(moment(58))), "Depuis \(clock)")
        XCTAssertNil(fr.timeLine(.nothing))
    }

    func testBatteryIsThePercentageAlone() throws {
        let fr = try french()

        XCTAssertEqual(fr.battery(Battery(pct: 85)), "85 %")
        XCTAssertEqual(fr.batteryA11y(Battery(pct: 85)), "Batterie à 85 %")
        XCTAssertEqual(try english().batteryA11y(Battery(pct: 85)), "Battery at 85 %")
    }

    func testTheBubbleSentences() throws {
        let fr = try french()
        let en = try english()
        let clock = "15\u{00A0}h\u{00A0}02"

        XCTAssertEqual(fr.latestNews(.minutes(2)), "Dernière nouvelle il y a 2 min")
        XCTAssertEqual(en.latestNews(.hours(3)), "Latest news 3 h ago")
        XCTAssertEqual(fr.answered(3, of: 4), "3 sur 4 ont donné des nouvelles")
        XCTAssertEqual(en.answered(3, of: 4), "3 of 4 have shared news")
        XCTAssertEqual(fr.announcement(.allGood(latestNews: nil, answered: nil, total: nil)), "Tout va bien")
        XCTAssertEqual(fr.announcement(.alone), "Personne d'autre dans cette Miff pour l'instant")
        XCTAssertEqual(fr.alertTitle(page(.left(place: .category(.home), at: moment(58)))), "Thomas a quitté la maison à \(clock)")
        XCTAssertEqual(fr.alertTitle(page(.noNewsSince(moment(58)))), "Pas de nouvelles de Thomas depuis \(clock)")
        XCTAssertEqual(fr.alertTitle(page(.danger(place: .unknown, since: moment(58)))), "Thomas est dans une zone de danger")
        XCTAssertEqual(fr.alertTitle(page(.danger(place: .named("Zone A"), since: moment(58)))), "Thomas est à Zone A")
        XCTAssertEqual(en.alertTitle(page(.left(place: .category(.home), at: moment(58)))), "Thomas left home at 3:02\u{00A0}PM")
    }

    func testTheSecondPersonForMyOwnAlerts() throws {
        let fr = try french()
        let clock = "15\u{00A0}h\u{00A0}02"

        XCTAssertEqual(fr.alertTitle(page(.left(place: .category(.work), at: moment(58)), me: true)), "Tu as quitté le travail à \(clock)")
        XCTAssertEqual(
            fr.alertTitle(page(.noNewsSince(moment(58)), me: true)),
            "Tes proches n'ont pas eu de tes nouvelles depuis \(clock)"
        )
        XCTAssertEqual(
            fr.alertTitle(page(.danger(place: .unknown, since: moment(58)), me: true)),
            "Tu es dans une zone de danger de ta Miff"
        )
        XCTAssertEqual(fr.alertNote(page(.danger(place: .unknown, since: moment(58)), me: true)), "Tes proches le voient aussi")
    }

    func testOfflineMentionsAreDatedAndCalm() throws {
        let fr = try french()
        let clock = "15\u{00A0}h\u{00A0}02"

        XCTAssertEqual(fr.offlineBanner(moment(58)), "Dernières informations de \(clock)")
        XCTAssertEqual(fr.offlineBanner(nil), "Pas de connexion pour le moment. Ce que tu vois peut ne pas être à jour.")
        let alert = AlertPage(
            member: feedMember("t", first: "Thomas"), isMe: false, level: .orange,
            kind: .noNewsSince(moment(58)), canMessage: true, offlineSince: moment(58)
        )
        XCTAssertEqual(fr.offlineAlert(alert), "Informations de \(clock) : reconnecte-toi pour les mettre à jour.")
    }

    func testTheSheetSentences() throws {
        let fr = try french()

        XCTAssertEqual(fr.locateTitle("Thomas"), "Dernière position connue de Thomas")
        XCTAssertEqual(
            fr.locateRecent(measuredAt: moment(20), age: .minutes(20)),
            "Mesurée à 15\u{00A0}h\u{00A0}40 (il y a 20 min). Ce n'est pas une position en direct."
        )
        XCTAssertEqual(
            fr.locateOld(moment(60 * 30, day: .yesterday)),
            "Elle date d'hier à 10\u{00A0}h\u{00A0}00 : elle n'est plus affichée."
        )
    }

    func testACardIsReadAsOneBlockWithoutAYouMentionAndMineSaysWhatMyLovedOnesSee() throws {
        let fr = try french()
        let mine = card(first: "Camille", isMe: true, battery: 64)
        let other = card(first: "Thomas", isMe: false, battery: 85)

        XCTAssertEqual(
            fr.cardSpoken(other),
            "Thomas, À la maison, Depuis 12 min, Batterie à 85 %"
        )
        XCTAssertEqual(
            fr.cardSpoken(mine),
            "Camille, À la maison, Depuis 12 min, Batterie à 64 %, Ce que tes proches voient de toi"
        )
    }

    func testNothingSaysWrongWords() throws {
        let catalog = try Catalog.lookup(language: "fr")
        let all = ["feed_label_danger", "feed_bubble_danger", "feed_bubble_all_good", "feed_bubble_no_news_title"]

        for key in all {
            let text = catalog(key).lowercased()
            XCTAssertFalse(text.contains("risque"), key)
            XCTAssertFalse(text.contains("sécurité"), key)
        }
    }

    // MARK: - Aides

    private func page(_ kind: AlertKind, me: Bool = false) -> AlertPage {
        AlertPage(
            member: feedMember("t", first: "Thomas"), isMe: me, level: .orange, kind: kind, canMessage: !me, offlineSince: nil
        )
    }

    private func card(first: String, isMe: Bool, battery: Int) -> FeedMemberCard {
        FeedMemberCard(
            member: feedMember(isMe ? "me" : "t", first: first),
            isMe: isMe,
            level: .green,
            label: .at(.category(.home)),
            time: .since(.minutes(12)),
            battery: Battery(pct: battery),
            limitedLocation: false,
            phone: isMe ? nil : "+33612345678",
            canLocate: !isMe
        )
    }
}
// swiftlint:enable line_length
