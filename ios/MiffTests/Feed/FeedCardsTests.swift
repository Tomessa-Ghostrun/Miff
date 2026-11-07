// Les assertions citent les phrases réelles du catalogue : leurs lignes sont plus longues que la limite.
// swiftlint:disable line_length
import XCTest
@testable import Miff

// Les cartes du Feed (ticket FEED, critères C1 à C7) : ordre (moi en dernier, D43), couleur aux limites 0 h 59, 1 h 00, 2 h 00 et
// 2 h 01 (les constantes sont lues dans `StatusThresholds`), étiquettes, lignes de temps, batterie sans heure (D44). Mêmes cas
// que `FeedBuilderTest` d'Android : les tests sont le contrat.
final class FeedCardsTests: XCTestCase {
    private func level(departedAgo: Int) -> StatusLevel {
        let input = feedInput(
            members: [feedMember("me"), feedMember("t")],
            statuses: ["t": feedStatus(.out, departedAgo: departedAgo)]
        )
        return buildFeed(input).card("t")?.level ?? .neutral
    }

    // MARK: - L'ordre

    func testMyCardIsLastAndTheOthersKeepTheOrderTheyCameIn() {
        let members = [feedMember("c"), feedMember("me"), feedMember("a"), feedMember("b")]

        let ordered = FeedBuilder.orderMembers(members, myUid: "me").map { $0.uid }

        XCTAssertEqual(ordered, ["c", "a", "b", "me"])
    }

    func testTheOrderNeverFollowsTheUrgency() {
        let members = [feedMember("a"), feedMember("b"), feedMember("me")]
        let statuses = ["a": feedStatus(.inTrusted), "b": feedStatus(.inRisk, departedAgo: feedMinutes(5))]

        let content = buildFeed(feedInput(members: members, statuses: statuses))

        XCTAssertEqual(content.cards.map { $0.member.uid }, ["a", "b", "me"])
        XCTAssertEqual(content.cards.last?.isMe, true)
    }

    // MARK: - La couleur, aux limites

    func testTheColourAtTheLimitsOfOneAndTwoHours() {
        XCTAssertEqual(level(departedAgo: feedMinutes(59)), .green)
        XCTAssertEqual(level(departedAgo: feedHours(1)), .orange)
        XCTAssertEqual(level(departedAgo: feedHours(2)), .orange)
        XCTAssertEqual(level(departedAgo: feedHours(2) + 1), .red)
        XCTAssertEqual(StatusThresholds.orangeAfterMillis, FeedMillis.hour)
        XCTAssertEqual(StatusThresholds.redAfterMillis, 2 * FeedMillis.hour)
    }

    func testAReferenceInTheFutureIsGreen() {
        XCTAssertEqual(level(departedAgo: -feedMinutes(10)), .green)
    }

    func testAnUpdateMoreRecentThanTheDepartureIsTheReference() {
        let input = feedInput(
            members: [feedMember("me"), feedMember("t")],
            statuses: ["t": feedStatus(.out, departedAgo: feedHours(5), updatedAgo: feedMinutes(10))]
        )

        XCTAssertEqual(buildFeed(input).card("t")?.level, .green)
    }

    func testNoStatusAnUnknownPresenceAndAStoppedSharingAreNeutral() {
        let members = [feedMember("a"), feedMember("b"), feedMember("c", sharing: false), feedMember("me")]
        let statuses = ["b": feedStatus(.unknown), "c": feedStatus(.inTrusted)]

        let content = buildFeed(feedInput(members: members, statuses: statuses))

        XCTAssertEqual(content.card("a")?.level, .neutral)
        XCTAssertEqual(content.card("b")?.level, .neutral)
        XCTAssertEqual(content.card("c")?.level, .neutral)
        XCTAssertEqual(content.card("c")?.label, .sharingStopped)
    }

    // MARK: - Les étiquettes

    func testLabelsAtAndLeftAPlaceByCategoryByNameAndWhenTheyAreUnknown() {
        let places = [
            feedPlace("p1", category: .home),
            feedPlace("p2", name: "Chez Mamie", category: .relative)
        ]
        let members = [feedMember("a"), feedMember("b"), feedMember("c"), feedMember("d"), feedMember("me")]
        let statuses = [
            "a": feedStatus(.inTrusted, placeId: "p1"),
            "b": feedStatus(.inTrusted, placeId: "p2"),
            "c": feedStatus(.inTrusted, placeId: "gone"),
            "d": feedStatus(.out, placeId: "p1", departedAgo: feedMinutes(10))
        ]

        let content = buildFeed(feedInput(members: members, statuses: statuses, places: places))

        XCTAssertEqual(content.card("a")?.label, .at(.category(.home)))
        XCTAssertEqual(content.card("b")?.label, .at(.named("Chez Mamie")))
        XCTAssertEqual(content.card("c")?.label, .at(.unknown))
        XCTAssertEqual(content.card("d")?.label, .left(.category(.home)))
    }

    func testTheFourCategoriesAreSaidByTheirCategory() {
        let places = [
            feedPlace("p1", category: .work), feedPlace("p2", category: .school),
            feedPlace("p3", category: .sport), feedPlace("p4", category: nil)
        ]
        let members = [feedMember("w"), feedMember("s"), feedMember("x"), feedMember("n"), feedMember("me")]
        let statuses = [
            "w": feedStatus(.inTrusted, placeId: "p1"), "s": feedStatus(.inTrusted, placeId: "p2"),
            "x": feedStatus(.inTrusted, placeId: "p3"), "n": feedStatus(.inTrusted, placeId: "p4")
        ]

        let content = buildFeed(feedInput(members: members, statuses: statuses, places: places))

        XCTAssertEqual(content.card("w")?.label, .at(.category(.work)))
        XCTAssertEqual(content.card("s")?.label, .at(.category(.school)))
        XCTAssertEqual(content.card("x")?.label, .at(.category(.sport)))
        XCTAssertEqual(content.card("n")?.label, .at(.named("Chez Léa")))
    }

    func testADangerZoneIsDangerAndRedWhateverTheTime() {
        let input = feedInput(
            members: [feedMember("a"), feedMember("me")],
            statuses: ["a": feedStatus(.inRisk, departedAgo: feedMinutes(5))]
        )

        let card = buildFeed(input).card("a")

        XCTAssertEqual(card?.label, .danger)
        XCTAssertEqual(card?.level, .red)
    }

    // MARK: - Les lignes de temps

    func testTimeLines() {
        let members = [feedMember("a"), feedMember("b"), feedMember("c"), feedMember("d"), feedMember("e"), feedMember("me")]
        let statuses = [
            "a": feedStatus(.inTrusted, departedAgo: feedMinutes(12)),
            "b": feedStatus(.out, departedAgo: feedMinutes(12)),
            "c": feedStatus(.out, departedAgo: feedMinutes(70)),
            "d": feedStatus(.out, departedAgo: feedHours(3)),
            "e": feedStatus(.inRisk, departedAgo: feedMinutes(30))
        ]

        let content = buildFeed(feedInput(members: members, statuses: statuses))

        XCTAssertEqual(content.card("a")?.time, .since(.minutes(12)))
        XCTAssertEqual(content.card("b")?.time, .departedAgo(.minutes(12)))
        XCTAssertEqual(content.card("c")?.time, .latestNews(Moment(atMillis: feedNow - feedMinutes(70), day: .today)))
        XCTAssertEqual(content.card("d")?.time, .noNewsSince(Moment(atMillis: feedNow - feedHours(3), day: .today)))
        XCTAssertEqual(content.card("e")?.time, .inDangerSince(Moment(atMillis: feedNow - feedMinutes(30), day: .today)))
    }

    func testANeutralCardHasNoTimeLine() {
        let content = buildFeed(feedInput(members: [feedMember("a"), feedMember("me")]))

        XCTAssertEqual(content.card("a")?.time, .nothing)
        XCTAssertEqual(content.card("a")?.label, .noNews)
    }

    // MARK: - La batterie (D44)

    func testTheBatteryIsThePercentageAloneAndHiddenAfterSixHoursOrWhenAbsent() {
        let members = [feedMember("a"), feedMember("b"), feedMember("c"), feedMember("d"), feedMember("me")]
        let statuses = [
            "a": feedStatus(battery: 85, writtenAgo: feedMinutes(5)),
            "b": feedStatus(battery: 85, writtenAgo: feedHours(5)),
            "c": feedStatus(battery: 85, writtenAgo: feedHours(6) + 1),
            "d": feedStatus(battery: nil)
        ]

        let content = buildFeed(feedInput(members: members, statuses: statuses))

        XCTAssertEqual(content.card("a")?.battery, Battery(pct: 85))
        XCTAssertEqual(content.card("b")?.battery, Battery(pct: 85), "plus d'heure de mesure à côté du niveau (D44)")
        XCTAssertNil(content.card("c")?.battery, "au-delà de 6 h : masquée (D44)")
        XCTAssertEqual(FeedBuilder.batteryMaxAgeMillis, feedHours(6))
        XCTAssertNil(content.card("d")?.battery)
    }

    func testAWriteStillWaitingForTheServerCountsAsNow() {
        let content = buildFeed(
            feedInput(
                members: [feedMember("a"), feedMember("me")],
                statuses: ["a": feedStatus(battery: 40, writtenAgo: nil)]
            )
        )

        XCTAssertEqual(content.card("a")?.battery, Battery(pct: 40))
    }

    // MARK: - Les gestes

    func testMyCardHasNeitherANumberNorLocate() {
        let members = [feedMember("a"), feedMember("me")]
        let statuses = ["a": feedStatus(), "me": feedStatus()]

        let content = buildFeed(feedInput(members: members, statuses: statuses))

        XCTAssertNil(content.card("me")?.phone)
        XCTAssertEqual(content.card("me")?.canLocate, false)
        XCTAssertEqual(content.card("a")?.phone, "+33612345678")
        XCTAssertEqual(content.card("a")?.canLocate, true)
    }

    func testLocateNeedsAnotherMemberWhoSharesAndRealStatusReading() {
        let members = [feedMember("a"), feedMember("b", sharing: false), feedMember("me")]

        let reading = buildFeed(feedInput(members: members))
        let pending = buildFeed(feedInput(members: members, view: .pending))
        let iStopped = buildFeed(feedInput(members: [feedMember("a"), feedMember("me", sharing: false)]))

        XCTAssertEqual(reading.card("a")?.canLocate, true, "même sans statut connu : « pas de position » est une réponse calme")
        XCTAssertEqual(reading.card("b")?.canLocate, false)
        XCTAssertEqual(pending.card("a")?.canLocate, false)
        XCTAssertEqual(iStopped.card("a")?.canLocate, false)
    }

    func testAMemberWithoutANumberHasNoMessageButCanBeLocated() {
        let content = buildFeed(feedInput(members: [feedMember("a", phone: nil), feedMember("me")]))

        XCTAssertNil(content.card("a")?.phone)
        XCTAssertEqual(content.card("a")?.canLocate, true)
    }

    func testLimitedLocationIsASoftBadgeWithNoEffectOnTheColour() {
        let members = [feedMember("a"), feedMember("b"), feedMember("c"), feedMember("me")]
        let statuses = [
            "a": feedStatus(access: .denied), "b": feedStatus(access: .foreground), "c": feedStatus(access: .background)
        ]

        let content = buildFeed(feedInput(members: members, statuses: statuses))

        XCTAssertEqual(content.card("a")?.limitedLocation, true)
        XCTAssertEqual(content.card("b")?.limitedLocation, true)
        XCTAssertEqual(content.card("c")?.limitedLocation, false)
        XCTAssertEqual(content.card("a")?.level, .green)
    }

    // MARK: - Moi, et ce qui est caché

    func testWhenIStoppedSharingTheOthersAreHiddenAndMyCardSaysNothingIsRead() {
        let members = [feedMember("a"), feedMember("me", sharing: false)]
        let statuses = ["a": feedStatus(.inRisk, departedAgo: feedMinutes(5))]

        let content = buildFeed(feedInput(members: members, statuses: statuses))

        XCTAssertEqual(content.card("a")?.label, .newsHidden)
        XCTAssertEqual(content.card("a")?.level, .neutral)
        XCTAssertEqual(content.card("me")?.label, .sharingStopped)
    }

    func testWhileMyAgreementIsWaitedForTheOthersAreHiddenAndMyCardSaysSo() {
        let members = [feedMember("a"), feedMember("me")]
        let statuses = ["a": feedStatus(), "me": feedStatus()]

        let content = buildFeed(feedInput(members: members, statuses: statuses, waiting: true))

        XCTAssertEqual(content.card("me")?.label, .waitingAgreement)
        XCTAssertEqual(content.card("a")?.label, .newsHidden)
        XCTAssertEqual(content.bubble, .awaitingAgreement)
    }

    func testTheFullNoteAppearsAtFifteenMembers() {
        let many = (0..<14).map { feedMember("m\($0)") } + [feedMember("me")]

        XCTAssertTrue(buildFeed(feedInput(members: many)).isFull)
        XCTAssertFalse(buildFeed(feedInput(members: Array(many.dropFirst()))).isFull)
    }
}
// swiftlint:enable line_length
