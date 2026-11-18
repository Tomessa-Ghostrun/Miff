// Quelques lignes appellent des fabriques à nombreux paramètres : elles dépassent la limite.
// swiftlint:disable line_length
import XCTest
@testable import Miff

/// Ce que voient les PROCHES (ticket 7.6, 12, AD27 à AD31 ; miroir de `FeedBuilderAlertTest` d'Android) : la carte de la personne en alerte est première, rouge, « Alerte en cours »
/// et « Depuis 15 h 02 » ; la bulle est rouge, sa première page est l'alerte, et elle ne dit jamais « tout va bien » ; l'alerte l'emporte sur le mode voyage ; une alerte
/// terminée ou arrivée à sa date ne laisse aucune trace.
final class DangerAlertFeedTests: XCTestCase {
    private let trio = [feedMember("me", first: "Camille"), feedMember("t", first: "Thomas"), feedMember("a", first: "Amandine")]

    private func green() -> MemberStatus {
        feedStatus(.inTrusted, placeId: "p1", departedAgo: feedMinutes(10), updatedAgo: feedMinutes(10), writtenAgo: feedMinutes(1))
    }

    private func greens() -> [String: MemberStatus] {
        ["me": green(), "t": green(), "a": green()]
    }

    private func alert(
        _ uid: String,
        startedMinutesAgo: Int = 58,
        status: String = "active",
        expiresInMinutes: Int = 60 * 24
    ) -> DangerAlertDoc {
        let parsed = DangerAlertDoc.parse(
            uid: uid,
            status: status,
            startedAtMillis: feedNow - feedMinutes(startedMinutesAgo),
            expiresAtMillis: feedNow + feedMinutes(expiresInMinutes),
            seq: 1
        )
        return parsed ?? DangerAlertDoc(uid: uid, active: false, startedAtMillis: nil, expiresAtMillis: 1, seq: 1)
    }

    private func build(_ alerts: [DangerAlertDoc], members: [MiffMember]? = nil, statuses: [String: MemberStatus]? = nil) -> FeedContent {
        var input = feedInput(members: members ?? trio, statuses: statuses ?? greens())
        input.alerts = alerts
        return buildFeed(input)
    }

    private func pages(_ content: FeedContent) -> [AlertPage] {
        if case .alerts(let pages, _) = content.bubble {
            return pages
        }
        return []
    }

    // MARK: - La carte

    func testTheCardOfTheAlertedPersonIsFirstRedAndSaysAnAlertIsInProgress() throws {
        let content = build([alert("a")])
        let card = try XCTUnwrap(content.cards.first)

        XCTAssertEqual(card.member.uid, "a")
        XCTAssertEqual(card.level, .red)
        XCTAssertEqual(card.label, .alertActive)
        XCTAssertTrue(card.alertActive)
        XCTAssertEqual(card.time, .since(Moment(atMillis: feedNow - feedMinutes(58), day: .today)))
    }

    func testTheOtherCardsKeepTheirOrderAndColour() {
        let without = build([])
        let with = build([alert("a")])

        XCTAssertEqual(
            with.cards.dropFirst().map { $0.member.uid },
            without.cards.map { $0.member.uid }.filter { $0 != "a" }
        )
        XCTAssertTrue(with.cards.dropFirst().allSatisfy { !$0.alertActive && $0.level == .green })
    }

    func testMyOwnAlertCardIsFirstToo() throws {
        let content = build([alert("me")])
        let card = try XCTUnwrap(content.cards.first)

        XCTAssertEqual(card.member.uid, "me")
        XCTAssertTrue(card.isMe)
        XCTAssertEqual(content.myLevel, .red)
        XCTAssertEqual(content.myLabel, .alertActive)
    }

    func testSeveralAlertsAreInTheOrderOfTheStartTheOldestFirst() {
        let content = build([alert("a", startedMinutesAgo: 10), alert("t", startedMinutesAgo: 30)])

        XCTAssertEqual(content.cards.prefix(2).map { $0.member.uid }, ["t", "a"])
        XCTAssertEqual(pages(content).prefix(2).map { $0.member.uid }, ["t", "a"])
    }

    func testAnEndedAlertAnExpiredOneAndOneOfANonMemberLeaveNoTrace() {
        let quiet = build([])
        let ended = build([alert("a", status: "ended")])
        let expired = build([alert("a", expiresInMinutes: -1)])
        let stranger = build([alert("zz")])

        for content in [ended, expired, stranger] {
            XCTAssertEqual(content.cards.map { $0.member.uid }, quiet.cards.map { $0.member.uid })
            XCTAssertTrue(content.cards.allSatisfy { !$0.alertActive })
            XCTAssertEqual(content.bubble, quiet.bubble)
            XCTAssertEqual(content.miffLevel, quiet.miffLevel)
        }
    }

    func testAnAlertWinsOverTheTripAndTheColourComesBackWhenItEnds() throws {
        let trip = MemberStatus(
            presence: .travel, placeId: nil, presenceAtMillis: feedNow - feedHours(2), lastUpdateAtMillis: feedNow - feedHours(3),
            hasPhoto: false, batteryPct: nil, locationAccess: .denied, updatedAtMillis: feedNow - feedHours(1),
            travelUntilMillis: feedNow + feedHours(30), travelUntilDay: "2026-11-15"
        )
        let statuses = ["me": green(), "t": trip, "a": green()]

        let during = try XCTUnwrap(build([alert("t")], statuses: statuses).card("t"))
        let after = try XCTUnwrap(build([], statuses: statuses).card("t"))

        XCTAssertEqual(during.level, .red)
        XCTAssertFalse(during.traveling)
        XCTAssertEqual(after.level, .neutral)
        XCTAssertTrue(after.traveling)
    }

    // MARK: - La bulle

    func testTheBubbleIsRedItsFirstPageIsTheAlertAndItNeverSaysEverythingIsFine() throws {
        let content = build([alert("a")])
        let first = try XCTUnwrap(pages(content).first)

        XCTAssertEqual(content.miffLevel, .red)
        XCTAssertEqual(content.bubble.level, .red)
        XCTAssertEqual(first.member.uid, "a")
        XCTAssertEqual(first.kind, .declared(at: Moment(atMillis: feedNow - feedMinutes(58), day: .today)))
        if case .allGood = content.bubble {
            XCTFail("la bulle ne dit jamais « Tout va bien » pendant une alerte")
        }
    }

    func testTheAlertPageComesBeforeTheAutomaticRedOnes() {
        var statuses = greens()
        statuses["t"] = feedStatus(.out, placeId: "p1", departedAgo: feedHours(6), updatedAgo: feedHours(6), writtenAgo: feedHours(1))
        let content = build([alert("a")], statuses: statuses)
        let list = pages(content)

        XCTAssertEqual(list.first?.member.uid, "a")
        XCTAssertEqual(list.first?.kind, .declared(at: Moment(atMillis: feedNow - feedMinutes(58), day: .today)))
        XCTAssertTrue(list.dropFirst().allSatisfy { $0.member.uid != "a" }, "jamais deux pages pour la même personne")
    }

    func testOnlyAnotherMemberWithANumberCanBeCalledOrWrittenTo() throws {
        let content = build([alert("a"), alert("me")])
        let others = pages(content).filter { !$0.isMe }
        let mine = try XCTUnwrap(pages(content).first { $0.isMe })

        XCTAssertTrue(others.allSatisfy { $0.canCall && $0.canMessage })
        XCTAssertFalse(mine.canCall)
        XCTAssertFalse(mine.canMessage)
        let noNumber = build([alert("a")], members: [feedMember("me"), feedMember("a", phone: nil)], statuses: ["me": green(), "a": green()])
        XCTAssertEqual(pages(noNumber).first?.canCall, false)
    }

    func testMyAlertIsShownEvenWhenMyOwnSharingIsStopped() {
        let members = [feedMember("me", sharing: false), feedMember("a")]
        let content = build([alert("me")], members: members, statuses: ["me": green(), "a": green()])

        XCTAssertEqual(pages(content).first?.member.uid, "me")
    }

    func testTheAnnouncementKeyChangesWhenAnAlertAppears() {
        XCTAssertNotEqual(build([alert("a")]).announceKey, build([]).announceKey)
    }

    // MARK: - Les textes

    private func texts(_ language: String, locale: String) throws -> FeedTexts {
        FeedTexts(
            lookup: try AlertCatalog.lookup(language: language),
            locale: Locale(identifier: locale),
            zone: feedZone,
            is24Hour: language == "fr"
        )
    }

    func testTheSentencesAreAttributedFactualAndCalm() throws {
        let content = build([alert("a")])
        let page = try XCTUnwrap(pages(content).first)
        let fr = try texts("fr", locale: "fr_FR")
        let en = try texts("en", locale: "en_US")

        XCTAssertEqual(fr.label(.alertActive), "Alerte en cours")
        XCTAssertEqual(fr.alertTitle(page), "Amandine a déclaré une alerte à 15\u{00A0}h\u{00A0}02")
        XCTAssertEqual(en.label(.alertActive), "Alert in progress")
        XCTAssertEqual(en.alertTitle(page), "Amandine declared an alert at 3:02\u{00A0}PM")
        for sentence in [fr.alertTitle(page), en.alertTitle(page), fr.label(.alertActive)] {
            XCTAssertFalse(sentence.contains("!"))
            XCTAssertFalse(sentence.lowercased().contains("sécurité"))
            XCTAssertFalse(sentence.lowercased().contains("safe"))
        }
        XCTAssertEqual(fr.timeLine(.since(Moment(atMillis: feedNow - feedMinutes(58), day: .today))), "Depuis 15\u{00A0}h\u{00A0}02")
    }

    func testMyOwnPageSaysYouDeclaredAnAlert() throws {
        let page = try XCTUnwrap(pages(build([alert("me")])).first)

        XCTAssertEqual(try texts("fr", locale: "fr_FR").alertTitle(page), "Tu as déclaré une alerte à 15\u{00A0}h\u{00A0}02")
    }
}

/// Le catalogue de textes du dépôt, lu tel quel (les tests lisent le VRAI catalogue).
enum AlertCatalog {
    static func root(file: StaticString = #filePath) throws -> [String: [String: Any]] {
        let url = URL(fileURLWithPath: "\(file)")
            .deletingLastPathComponent() // Alert
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
            .appendingPathComponent("Miff/Resources/Localizable.xcstrings")
        let data = try Data(contentsOf: url)
        let parsed = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        return try XCTUnwrap(parsed["strings"] as? [String: [String: Any]])
    }

    static func value(_ key: String, language: String, in strings: [String: [String: Any]]) -> String? {
        let localizations = strings[key]?["localizations"] as? [String: Any]
        let entry = localizations?[language] as? [String: Any]
        let unit = entry?["stringUnit"] as? [String: Any]
        return unit?["value"] as? String
    }

    static func lookup(language: String) throws -> (String) -> String {
        let strings = try root()
        return { key in value(key, language: language, in: strings) ?? "MISSING:\(key)" }
    }
}
// swiftlint:enable line_length
