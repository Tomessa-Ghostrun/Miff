// Quelques lignes appellent des fabriques à nombreux paramètres : elles dépassent la limite.
// swiftlint:disable line_length
import XCTest
@testable import Miff

/// MODE VOYAGE dans le Feed (VOY-3I, critères F1 à F7 de docs/tickets/mode-voyage.md, décision du PM du 2026-11-10) : la carte d'un voyageur
/// est GRISE, dit le jour choisi, ne montre ni batterie ni « Localisation limitée » ; le voyageur est ignoré dans la couleur de la Miff et
/// hors du décompte « n sur m » ; la bulle le dit ; la fin passe à « Voyage terminé » sans relecture ; ma carte et la bulle d'incitation
/// sont neutres. Mêmes cas qu'Android (`FeedBuilderTravelTest.kt` : les cas des tests sont le contrat).
final class FeedBuilderTravelTests: XCTestCase {
    private let me = "me"

    /// Le voyage finit le 16 nov. 2026 à 00:00 à Paris : le jour choisi est le 15.
    private var until: Int {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = feedZone
        let midnight = calendar.date(from: DateComponents(year: 2026, month: 11, day: 16)) ?? Date(timeIntervalSince1970: 0)
        return Int(midnight.timeIntervalSince1970 * 1000)
    }

    private let day = TravelDate(year: 2026, month: 11, day: 15)

    private func trip(
        untilMillis: Int? = nil,
        day: String? = "2026-11-15",
        battery: Int? = nil,
        access: MemberLocationAccess? = .denied,
        hasPhoto: Bool = false
    ) -> MemberStatus {
        MemberStatus(
            presence: .travel,
            placeId: nil,
            presenceAtMillis: feedNow - feedHours(2),
            lastUpdateAtMillis: feedNow - feedHours(3),
            hasPhoto: hasPhoto,
            batteryPct: battery,
            locationAccess: access,
            updatedAtMillis: feedNow - feedHours(1),
            travelUntilMillis: untilMillis ?? until,
            travelUntilDay: day
        )
    }

    private func green() -> MemberStatus {
        feedStatus(.inTrusted, placeId: "p1", departedAgo: feedMinutes(10), updatedAgo: feedMinutes(10), writtenAgo: feedMinutes(1))
    }

    private let trio = [feedMember("me", first: "Camille"), feedMember("t", first: "Thomas"), feedMember("a", first: "Amandine")]
    private let places = [feedPlace("p1", name: "Chez Mamie", category: .home)]

    private func build(
        _ members: [MiffMember],
        _ statuses: [String: MemberStatus],
        at now: Int = feedNow,
        waiting: Bool = false
    ) -> FeedContent {
        buildFeed(feedInput(members: members, statuses: statuses, places: places, waiting: waiting), now: now)
    }

    private func traveling(_ uid: String, _ content: FeedContent) -> TravelDay? {
        if case .traveling(let until, _)? = content.card(uid)?.label {
            return until
        }
        return nil
    }

    // MARK: - F1 : la carte

    func testTheCardOfATravellerIsGreyWithTheDayChosenAndNoBatteryNorLimitedLocation() throws {
        let content = build(trio, ["me": green(), "t": trip(battery: 40), "a": green()])
        let card = try XCTUnwrap(content.card("t"))

        XCTAssertEqual(card.level, .neutral)
        XCTAssertEqual(card.label, .traveling(TravelDay(date: day, showYear: false), mine: false))
        XCTAssertTrue(card.traveling)
        XCTAssertNil(card.battery, "rien n'est mesuré pendant un voyage")
        XCTAssertFalse(card.limitedLocation)
        // « Depuis le 7 nov. » : le jour où le voyage a commencé.
        XCTAssertEqual(card.time, .travelSince(feedNow - feedHours(2)))
        // Localiser, Message et Appeler restent (la position reste partagée).
        XCTAssertTrue(card.canLocate)
        XCTAssertEqual(card.phone, "+33612345678")
    }

    func testTheDayShownIsTheOneChosenNotTheDayOfTheEndInstant() {
        // Lu depuis un autre fuseau, l'instant de la fin tombe le 15 (UTC-5) ou le 16 : le jour choisi reste le 15.
        let west = TimeZone(secondsFromGMT: -5 * 3_600) ?? .current
        let input = feedInput(members: trio, statuses: ["t": trip()], places: places)

        let content = FeedBuilder.build(input: input, nowMillis: feedNow, zone: west)

        XCTAssertEqual(traveling("t", content)?.date, day)
    }

    func testTheYearIsShownOnlyWhenItIsNotThisYear() {
        let next = 1_798_848_000_000 // le 2 janvier 2027
        let content = build(trio, ["t": trip(untilMillis: next, day: "2027-01-01")])

        XCTAssertEqual(traveling("t", content)?.showYear, true)
    }

    func testAPhotoOfATravellerIsStillOffered() {
        let content = build(trio, ["t": trip(hasPhoto: true)])

        XCTAssertNotEqual(content.card("t")?.photo, PhotoSlot.none)
    }

    // MARK: - F4 : la fin

    func testFromTheEndTheCardSaysTheTripEndedWithoutAnyNewReading() throws {
        let before = build(trio, ["t": trip()], at: until - 1)
        XCTAssertNotNil(traveling("t", before))

        let after = build(trio, ["t": trip()], at: until)
        let card = try XCTUnwrap(after.card("t"))
        XCTAssertEqual(card.label, .travelEnded)
        XCTAssertEqual(card.level, .neutral)
        XCTAssertFalse(card.traveling, "un voyage fini n'est plus un voyage")
        XCTAssertEqual(card.time, .nothing)
        XCTAssertNil(card.battery)
    }

    func testATripWithNoEndIsReadAsNothingKnownNeutralAndWithoutALabelOfTrip() throws {
        let status = MemberStatus(
            presence: .travel,
            placeId: nil,
            presenceAtMillis: feedNow - feedHours(2),
            lastUpdateAtMillis: nil,
            hasPhoto: false,
            batteryPct: nil,
            locationAccess: .background,
            updatedAtMillis: feedNow
        )

        let card = try XCTUnwrap(build(trio, ["t": status]).card("t"))

        XCTAssertEqual(card.label, .noNews)
        XCTAssertEqual(card.level, .neutral)
        XCTAssertFalse(card.traveling)
    }

    func testATravellerWhoStoppedSharingShowsTheStopOfSharingNotATrip() {
        let members = [feedMember("me", first: "Camille"), feedMember("t", first: "Thomas", sharing: false)]

        let content = build(members, ["t": trip()])

        XCTAssertEqual(content.card("t")?.label, .sharingStopped)
    }

    // MARK: - F2 : la couleur de la Miff

    func testATravellerIsIgnoredInTheColourOfTheMiff() {
        let withGreen = build(trio, ["me": green(), "t": trip(), "a": green()])
        XCTAssertEqual(withGreen.miffLevel, .green)

        let alone = build([feedMember("me", first: "Camille"), feedMember("t", first: "Thomas")], ["t": trip()])
        XCTAssertEqual(alone.miffLevel, .neutral)
    }

    // MARK: - F3 : la bulle

    func testTheCountNOnMLeavesTheTravellersOut() {
        // Moi et Amandine verts, Thomas en voyage, Léa sans nouvelles : 3 connus sur 4 serait faux ; le voyageur est hors du décompte : 2 sur 3.
        let members = [feedMember("me"), feedMember("t", first: "Thomas"), feedMember("a"), feedMember("l")]

        let content = build(members, ["me": green(), "t": trip(), "a": green()])

        XCTAssertEqual(content.bubble, .allGood(latestNews: .minutes(10), answered: 2, total: 3))
    }

    func testWithATravellerOnlyAmongTheOthersTheBubbleKeepsItsMessageAndAddsALine() {
        let content = build(trio, ["me": green(), "t": trip(), "a": green()])

        // Personne n'est silencieux une fois le voyageur hors du décompte : pas de « n sur m ».
        XCTAssertEqual(content.bubble, .allGood(latestNews: .minutes(10), answered: nil, total: nil))
        XCTAssertEqual(content.travelNote, .one(name: "Thomas", until: TravelDay(date: day, showYear: false)))
    }

    func testSeveralTravellersAreToldByTheirNumber() {
        let members = [feedMember("me"), feedMember("t"), feedMember("a"), feedMember("l")]

        let content = build(members, ["t": trip(), "a": trip(), "l": green()])

        XCTAssertEqual(content.travelNote, .many(count: 2, all: false))
    }

    func testWhenEveryOtherMemberIsOnATripTheBubbleIsNeutralAndSaysSoNeverAllGoodNeverNoNews() {
        let two = [feedMember("me", first: "Camille"), feedMember("t", first: "Thomas")]
        let one = build(two, ["me": green(), "t": trip()])

        XCTAssertEqual(one.bubble.level, .neutral)
        guard case .travelers(let note) = one.bubble, case .one(let name, _) = note else {
            return XCTFail("la bulle des voyageurs, par le prénom")
        }
        XCTAssertEqual(name, "Thomas")
        // La bulle le dit déjà : pas de seconde ligne.
        XCTAssertNil(one.travelNote)

        let many = build(trio, ["t": trip(), "a": trip()])
        // TOUS les autres membres sont en voyage : « Tes proches sont en voyage. »
        XCTAssertEqual(many.bubble, .travelers(.many(count: 2, all: true)))
    }

    func testSeveralTravellersAndOneWithoutNewsSayNLovedOnesAreTravelingNotThatAllOfThemAre() {
        let members = [feedMember("me"), feedMember("t"), feedMember("a"), feedMember("l")]

        let content = build(members, ["t": trip(), "a": trip()])

        XCTAssertEqual(content.bubble, .travelers(.many(count: 2, all: false)))
    }

    func testATravellerAndSomebodyWithoutNewsIsTheBubbleOfTheTravellersNotNoNews() {
        let content = build(trio, ["t": trip()])

        guard case .travelers = content.bubble else {
            return XCTFail("jamais « Pas encore de nouvelles » pour un voyageur")
        }
    }

    func testATravellerNeverMakesAnAlertAndAlertsKeepTheirPlaceWithTheLineUnder() {
        let late = feedStatus(.out, placeId: "p1", departedAgo: feedHours(6), updatedAgo: feedHours(6), writtenAgo: feedMinutes(1))

        let content = build(trio, ["t": trip(), "a": late])

        guard case .alerts(let pages, _) = content.bubble else {
            return XCTFail("l'alerte de Amandine garde sa place")
        }
        XCTAssertEqual(pages.map { $0.member.uid }, ["a"])
        XCTAssertNotNil(content.travelNote)
    }

    func testTheTravelLineIsNotShownWhenIDoNotShareOrWhenMyAgreementIsWaitedFor() {
        let stopped = [feedMember("me", first: "Camille", sharing: false), feedMember("t", first: "Thomas")]
        XCTAssertNil(build(stopped, ["t": trip()]).travelNote)

        let waiting = build(trio, ["t": trip()], waiting: true)
        XCTAssertNil(waiting.travelNote)
        XCTAssertEqual(waiting.card("t")?.label, .newsHidden)
    }

    // MARK: - F5 : ma carte

    func testMyOwnCardSaysModeVoyageIsNeutralAndInvitesToNothing() throws {
        let content = build(trio, ["me": trip(), "t": green(), "a": green()])
        let mine = try XCTUnwrap(content.card("me"))

        XCTAssertEqual(mine.label, .traveling(TravelDay(date: day, showYear: false), mine: true))
        XCTAssertEqual(content.myLevel, .neutral)
        XCTAssertEqual(content.myLabel, mine.label)
        // Aucune bulle d'incitation, même quand la dernière nouvelle a plusieurs jours.
        let facts = content.myFacts
        XCTAssertEqual(UpdatePrompt.prompt(facts, sharing: true, nowMillis: feedNow), .hidden)
        XCTAssertEqual(UpdatePrompt.level(facts, sharing: true, nowMillis: feedNow), .neutral)
        XCTAssertEqual(UpdatePrompt.prompt(facts, sharing: true, nowMillis: feedNow + feedHours(48)), .hidden)
        // Et je ne suis pas compté parmi les silencieux.
        XCTAssertTrue(mine.traveling)
        XCTAssertEqual(content.bubble, .allGood(latestNews: .minutes(10), answered: nil, total: nil))
    }

    func testMyTripIsOverAtTheEndAndTheBubbleOfEncouragementStaysNeutral() {
        let content = build(trio, ["me": trip(), "t": green(), "a": green()], at: until + feedHours(1))

        XCTAssertEqual(content.myLabel, .travelEnded)
        XCTAssertEqual(UpdatePrompt.prompt(content.myFacts, sharing: true, nowMillis: until + feedHours(1)), .hidden)
    }

    func testMyTripIsNotShownWhileMyAgreementIsWaitedFor() {
        let content = build(trio, ["me": trip(), "t": green(), "a": green()], waiting: true)

        XCTAssertEqual(content.myLabel, .waitingAgreement)
    }

    // MARK: - F7 : « Localiser » est celle d'un autre membre

    func testLocatingATravellerIsTheSameAsLocatingAnyoneElse() {
        let content = build(trio, ["t": trip(), "a": green()])

        XCTAssertEqual(content.card("t")?.canLocate, content.card("a")?.canLocate)
        XCTAssertEqual(content.card("t")?.canLocate, true)
    }
}
