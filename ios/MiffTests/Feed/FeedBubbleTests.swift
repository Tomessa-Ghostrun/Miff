// Les assertions citent les phrases réelles du catalogue : leurs lignes sont plus longues que la limite.
// swiftlint:disable line_length
import XCTest
@testable import Miff

// La bulle de la Miff (ticket FEED, critères B1 à B6) : la couleur de la Miff, le choix de la bulle, le carrousel d'alertes, les
// mentions hors connexion. Mêmes cas que `FeedBuilderTest` d'Android.
final class FeedBubbleTests: XCTestCase {
    private func bubble(
        _ members: [MiffMember],
        _ statuses: [String: MemberStatus],
        places: [Place] = []
    ) -> Bubble {
        buildFeed(feedInput(members: members, statuses: statuses, places: places)).bubble
    }

    private func pages(_ bubble: Bubble) -> [AlertPage] {
        if case .alerts(let pages, _) = bubble {
            return pages
        }
        return []
    }

    // MARK: - La couleur de la Miff

    func testTheColourOfTheMiff() {
        XCTAssertEqual(FeedBuilder.miffLevel([.red, .green, .green]), .red)
        XCTAssertEqual(FeedBuilder.miffLevel([.red, .orange]), .red)
        XCTAssertEqual(FeedBuilder.miffLevel([.orange, .green]), .orange)
        XCTAssertEqual(FeedBuilder.miffLevel([.green, .green]), .green)
        XCTAssertEqual(FeedBuilder.miffLevel([.green, .neutral, .neutral]), .green, "les neutres sont ignorés")
        XCTAssertEqual(FeedBuilder.miffLevel([.neutral, .neutral]), .neutral)
        XCTAssertEqual(FeedBuilder.miffLevel([]), .neutral)
    }

    // MARK: - Les bulles calmes

    func testAllGoodNeedsAnotherKnownMemberAndSaysTheLatestNewsOfTheOthersOnly() {
        let members = [feedMember("a"), feedMember("b"), feedMember("me")]
        let statuses = [
            "a": feedStatus(.inTrusted, departedAgo: feedMinutes(30)),
            "b": feedStatus(.inTrusted, departedAgo: feedMinutes(10)),
            "me": feedStatus(.inTrusted, departedAgo: feedMinutes(1))
        ]

        let result = bubble(members, statuses)

        XCTAssertEqual(result, .allGood(latestNews: .minutes(10), answered: nil, total: nil))
    }

    func testSomeWhoDidNotSpeakAreCountedAndNeverBlockAllGood() {
        let members = [feedMember("a"), feedMember("b"), feedMember("c"), feedMember("me")]
        let statuses = [
            "a": feedStatus(.inTrusted, departedAgo: feedMinutes(5)),
            "b": feedStatus(.inTrusted, departedAgo: feedMinutes(5)),
            "me": feedStatus(.inTrusted, departedAgo: feedMinutes(5))
        ]

        let result = bubble(members, statuses)

        XCTAssertEqual(result, .allGood(latestNews: .minutes(5), answered: 3, total: 4))
    }

    func testNoOtherKnownMemberIsNoNewsNeverAPhraseOfSafety() {
        let members = [feedMember("a"), feedMember("me")]

        XCTAssertEqual(bubble(members, ["me": feedStatus()]), .noNews)
        XCTAssertEqual(bubble(members, [:]), .noNews)
    }

    func testAloneInTheMiff() {
        XCTAssertEqual(bubble([feedMember("me")], ["me": feedStatus()]), .alone)
    }

    func testWhenIStoppedSharingTheBubbleSaysSoAndNothingElse() {
        let members = [feedMember("a"), feedMember("me", sharing: false)]

        XCTAssertEqual(bubble(members, ["a": feedStatus(.inRisk)]), .sharingStopped)
    }

    // MARK: - Les alertes

    func testRedsComeFirstThenOrangesAndTheOldestReferenceFirst() {
        let members = [feedMember("o1"), feedMember("r1"), feedMember("o2"), feedMember("r2"), feedMember("me")]
        let statuses = [
            "o1": feedStatus(.out, departedAgo: feedMinutes(90)),
            "o2": feedStatus(.out, departedAgo: feedMinutes(70)),
            "r1": feedStatus(.out, departedAgo: feedHours(3)),
            "r2": feedStatus(.out, departedAgo: feedHours(5))
        ]

        let order = pages(bubble(members, statuses)).map { $0.member.uid }

        XCTAssertEqual(order, ["r2", "r1", "o1", "o2"])
    }

    func testTheBubbleOfAnAlertTakesTheColourOfTheMiff() {
        let members = [feedMember("a"), feedMember("b"), feedMember("me")]
        let orange = ["a": feedStatus(.out, departedAgo: feedMinutes(70)), "b": feedStatus()]
        let red = ["a": feedStatus(.out, departedAgo: feedMinutes(70)), "b": feedStatus(.inRisk)]

        XCTAssertEqual(bubble(members, orange).level, .orange)
        XCTAssertEqual(bubble(members, red).level, .red)
    }

    func testTheAlertKinds() {
        let places = [feedPlace("p1", category: .home), feedPlace("p2", name: "Salle", category: .leisure)]
        let members = [feedMember("o"), feedMember("r"), feedMember("d"), feedMember("me")]
        let statuses = [
            "o": feedStatus(.out, placeId: "p1", departedAgo: feedMinutes(70)),
            "r": feedStatus(.out, placeId: "p1", departedAgo: feedHours(3)),
            "d": feedStatus(.inRisk, placeId: "p2", departedAgo: feedMinutes(20))
        ]

        let found = pages(bubble(members, statuses, places: places))

        XCTAssertEqual(found.first { $0.member.uid == "o" }?.kind, .left(place: .category(.home), at: moment(70)))
        XCTAssertEqual(found.first { $0.member.uid == "r" }?.kind, .noNewsSince(moment(180)))
        XCTAssertEqual(found.first { $0.member.uid == "d" }?.kind, .danger(place: .named("Salle"), since: moment(20)))
    }

    private func moment(_ minutesAgo: Int) -> Moment {
        Moment(atMillis: feedNow - feedMinutes(minutesAgo), day: .today)
    }

    func testIAmTheOnlyAlertAndMyPageHasNoMessageButOthersDo() {
        let members = [feedMember("a"), feedMember("b", phone: nil), feedMember("me")]
        let statuses = [
            "a": feedStatus(.out, departedAgo: feedMinutes(70)),
            "b": feedStatus(.out, departedAgo: feedMinutes(80)),
            "me": feedStatus(.out, departedAgo: feedMinutes(90))
        ]

        let found = pages(bubble(members, statuses))

        XCTAssertEqual(found.first { $0.member.uid == "me" }?.isMe, true)
        XCTAssertEqual(found.first { $0.member.uid == "me" }?.canMessage, false)
        XCTAssertEqual(found.first { $0.member.uid == "a" }?.canMessage, true)
        XCTAssertEqual(found.first { $0.member.uid == "b" }?.canMessage, false, "sans numéro : pas de message")
    }

    func testAnAlertThatGoesAwayLeavesAnAllGoodBubbleAndAPageLess() {
        let members = [feedMember("a"), feedMember("b"), feedMember("me")]
        let before = ["a": feedStatus(.out, departedAgo: feedMinutes(70)), "b": feedStatus(.out, departedAgo: feedHours(3))]
        let after = ["a": feedStatus(.inTrusted, departedAgo: feedMinutes(1)), "b": before["b"] ?? feedStatus()]

        XCTAssertEqual(pages(bubble(members, before)).count, 2)
        XCTAssertEqual(pages(bubble(members, after)).count, 1)
    }

    // MARK: - Hors connexion, publication, annonce

    func testOfflineOrACopyOfTheCacheIsDatedAndTheAlertsSayToReconnect() {
        let members = [feedMember("a"), feedMember("me")]
        let statuses = ["a": feedStatus(.out, departedAgo: feedMinutes(70), writtenAgo: feedMinutes(30))]

        let offline = buildFeed(feedInput(members: members, statuses: statuses, offline: true))
        let cached = buildFeed(feedInput(members: members, view: .loaded(byUid: statuses, fromCache: true)))
        let online = buildFeed(feedInput(members: members, statuses: statuses))

        XCTAssertEqual(offline.banners, [.offline(latest: moment(30))])
        XCTAssertEqual(cached.banners, [.offline(latest: moment(30))])
        XCTAssertTrue(online.banners.isEmpty)
        XCTAssertEqual(pages(offline.bubble).first?.offlineSince, moment(30))
        XCTAssertNil(pages(online.bubble).first?.offlineSince)
    }

    func testOfflineWithNothingKnownHasNoDate() {
        let content = buildFeed(feedInput(members: [feedMember("a"), feedMember("me")], offline: true))

        XCTAssertEqual(content.banners, [.offline(latest: nil)])
    }

    func testPublishingMyStatusIsABannerNeverAnError() {
        let content = buildFeed(feedInput(members: [feedMember("a"), feedMember("me")], view: .pending))

        XCTAssertEqual(content.banners, [.publishing])
        XCTAssertEqual(content.bubble, .noNews)
    }

    func testTheAnnouncementChangesWithTheLevelNeverWithTheMinute() {
        let members = [feedMember("a"), feedMember("me")]
        let green = ["a": feedStatus(.inTrusted, departedAgo: feedMinutes(5))]
        let out = ["a": feedStatus(.out, departedAgo: feedMinutes(10))]

        let first = buildFeed(feedInput(members: members, statuses: green)).announceKey
        let later = buildFeed(feedInput(members: members, statuses: green), now: feedNow + feedMinutes(3)).announceKey
        let orange = buildFeed(feedInput(members: members, statuses: out), now: feedNow + feedMinutes(70)).announceKey

        XCTAssertEqual(first, later)
        XCTAssertNotEqual(first, orange)
    }

    func testMyHeaderRingFollowsMyOwnStatus() {
        let members = [feedMember("a"), feedMember("me")]

        let orange = buildFeed(feedInput(members: members, statuses: ["me": feedStatus(.out, departedAgo: feedMinutes(70))]))
        let none = buildFeed(feedInput(members: members))

        XCTAssertEqual(orange.myLevel, .orange)
        XCTAssertEqual(none.myLevel, .neutral)
        XCTAssertEqual(none.myLabel, .noNews)
    }
}
// swiftlint:enable line_length
