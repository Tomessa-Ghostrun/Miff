// Les assertions citent des cas entiers : leurs lignes dépassent la limite.
// swiftlint:disable line_length
import XCTest
@testable import Miff

// L'ordre des cartes du Feed par date de dernière update (ticket FEED-ORDRE, critères A1 à A5, A10 et A11) : la fonction d'ordre, son
// application aux cartes, et le report pendant un geste. Mêmes cas que `FeedOrderTest` d'Android : les tests sont le contrat.
final class FeedOrderTests: XCTestCase {
    private func entry(_ uid: String, _ date: Int?) -> FeedOrderEntry {
        FeedOrderEntry(uid: uid, lastUpdateMillis: date)
    }

    /// Un statut « dans un lieu de confiance » dont la dernière update remonte à `ago` (carte verte, donc datée).
    private func updated(_ ago: Int) -> MemberStatus {
        feedStatus(.inTrusted, departedAgo: ago, updatedAgo: ago)
    }

    private func order(_ members: [MiffMember], _ statuses: [String: MemberStatus]) -> [String] {
        buildFeed(feedInput(members: members, statuses: statuses)).cards.map { $0.member.uid }
    }

    // MARK: - La fonction d'ordre (A1, A3, A4)

    func testTheMostRecentUpdateComesFirst() {
        let sorted = FeedOrder.sorted([entry("a", 100), entry("b", 300), entry("c", 200)])

        XCTAssertEqual(sorted, ["b", "c", "a"])
    }

    func testMembersWithoutADateComeAfterEveryoneInArrivalOrder() {
        let sorted = FeedOrder.sorted([entry("a", nil), entry("b", 100), entry("c", nil), entry("d", 50)])

        XCTAssertEqual(sorted, ["b", "d", "a", "c"])
    }

    func testEqualDatesKeepTheArrivalOrder() {
        let sorted = FeedOrder.sorted([entry("c", 100), entry("a", 100), entry("b", 100)])

        XCTAssertEqual(sorted, ["c", "a", "b"], "l'ordre d'arrivée, jamais l'ordre alphabétique")
    }

    func testTheSameUidTwiceStillGivesAnOrderThatNeverChanges() {
        let sorted = FeedOrder.sorted([entry("a", 100), entry("a", 100)])

        XCTAssertEqual(sorted, ["a", "a"])
    }

    func testNoMemberGivesNothingAndOneMemberGivesHimself() {
        XCTAssertEqual(FeedOrder.sorted([]), [])
        XCTAssertEqual(FeedOrder.sorted([entry("me", nil)]), ["me"])
        XCTAssertEqual(FeedOrder.sorted([entry("me", 5)]), ["me"])
    }

    func testTheOrderIsTheSameOnAHundredShuffledRuns() {
        let dated = (0..<12).map { entry("d\($0)", 1_000 + $0 * 7) }
        let undated = (0..<4).map { entry("u\($0)", nil) }
        let expectedDated = dated.reversed().map { $0.uid }
        for _ in 0..<100 {
            let shuffled = (dated + undated).shuffled()
            let result = FeedOrder.sorted(shuffled)

            XCTAssertEqual(Array(result.prefix(12)), expectedDated, "les dates distinctes ne dépendent pas de l'arrivée")
            XCTAssertEqual(Array(result.suffix(4)), shuffled.filter { $0.lastUpdateMillis == nil }.map { $0.uid })
            XCTAssertEqual(FeedOrder.sorted(shuffled), result, "le même contenu donne toujours le même ordre")
        }
    }

    // MARK: - Les cartes (A1, A2, A3, A5)

    func testTheCardsFollowTheLastUpdateAndNotTheUrgency() {
        let members = [feedMember("me"), feedMember("a"), feedMember("b")]
        // `a` est rouge (silence de 5 h) mais a donné des nouvelles AVANT `b`, vert : la couleur ne change pas l'ordre.
        let statuses = [
            "a": feedStatus(.out, departedAgo: feedHours(5), updatedAgo: feedHours(5)),
            "b": updated(feedMinutes(30)),
        ]

        XCTAssertEqual(order(members, statuses), ["b", "a", "me"])
        XCTAssertEqual(buildFeed(feedInput(members: members, statuses: statuses)).card("a")?.level, .red)
    }

    func testMyCardIsSortedLikeTheOthersEvenInTheMiddle() {
        let members = [feedMember("a"), feedMember("me"), feedMember("b")]
        let statuses = ["a": updated(feedMinutes(50)), "me": updated(feedMinutes(10)), "b": updated(feedMinutes(90))]

        XCTAssertEqual(order(members, statuses), ["me", "a", "b"], "ma carte monte quand ma dernière update est la plus récente")
        XCTAssertEqual(order(members, ["a": updated(feedMinutes(5)), "me": updated(feedMinutes(10)), "b": updated(feedMinutes(90))]), ["a", "me", "b"])
    }

    func testMembersWithoutAUsableDateFollowAllTheOthersInArrivalOrder() {
        let members = [
            feedMember("stopped", sharing: false), feedMember("none"), feedMember("a"),
            feedMember("unknown"), feedMember("b"), feedMember("me"),
        ]
        let statuses = [
            "stopped": updated(feedMinutes(1)),
            "unknown": feedStatus(.unknown, departedAgo: feedMinutes(5), updatedAgo: feedMinutes(5)),
            "a": updated(feedHours(1)),
            "b": updated(feedMinutes(20)),
        ]

        XCTAssertEqual(order(members, statuses), ["b", "a", "stopped", "none", "unknown", "me"])
    }

    func testAMemberWhoPublishedBeforeAnyDepartureStillSaysNoNewsAndFollowsTheOthers() {
        // Limite connue du ticket (§6) : `presence = unknown` avec une `lastUpdateAt` d'une update manuelle. La carte dit « Pas encore de
        // nouvelles » : l'ordre et l'étiquette ne se contredisent pas, la carte suit tous les membres datés.
        let members = [feedMember("a"), feedMember("manual"), feedMember("b")]
        let statuses = [
            "manual": feedStatus(.unknown, placeId: nil, departedAgo: feedHours(2), updatedAgo: feedMinutes(1)),
            "b": updated(feedHours(3)),
        ]

        let content = buildFeed(feedInput(members: members, statuses: statuses))

        XCTAssertEqual(content.card("manual")?.label, .noNews)
        XCTAssertEqual(content.cards.map { $0.member.uid }, ["b", "a", "manual"])
    }

    func testWhenIStopSharingOrMyAgreementIsAwaitedNobodyHasADate() {
        let members = [feedMember("a"), feedMember("b"), feedMember("me")]
        let statuses = ["a": updated(feedHours(2)), "b": updated(feedMinutes(5)), "me": updated(feedMinutes(1))]

        let waiting = buildFeed(feedInput(members: members, statuses: statuses, waiting: true))
        let stopped = buildFeed(feedInput(
            members: [feedMember("a"), feedMember("b"), feedMember("me", sharing: false)],
            statuses: statuses
        ))

        XCTAssertEqual(waiting.cards.map { $0.member.uid }, ["a", "b", "me"], "rien n'est lu des autres, mon accord est attendu")
        XCTAssertEqual(stopped.cards.map { $0.member.uid }, ["a", "b", "me"], "rien des autres n'est montré : arrivée")
    }

    func testStatusesNotReadYetGiveTheArrivalOrder() {
        let members = [feedMember("a"), feedMember("me"), feedMember("b")]
        for view in [StatusesView.pending, .unknown, .off] {
            let content = buildFeed(feedInput(members: members, view: view))

            XCTAssertEqual(content.cards.map { $0.member.uid }, ["a", "me", "b"])
        }
    }

    func testAnExpiredPhotoDoesNotChangeTheOrder() {
        let members = [feedMember("a"), feedMember("b"), feedMember("me")]
        let withPhoto = ["a": feedPhotoStatus(updatedAgo: feedMinutes(10)), "b": feedPhotoStatus(updatedAgo: feedHours(1))]
        let photoGone = ["a": feedPhotoStatus(updatedAgo: feedMinutes(10), hasPhoto: false), "b": feedPhotoStatus(updatedAgo: feedHours(1), hasPhoto: false)]

        XCTAssertEqual(order(members, withPhoto), ["a", "b", "me"])
        XCTAssertEqual(order(members, photoGone), order(members, withPhoto), "la date reste ; seule la vignette disparaît")
        let late = buildFeed(feedInput(members: members, statuses: withPhoto), now: feedNow + feedHours(30))
        XCTAssertEqual(late.cards.map { $0.member.uid }, ["a", "b", "me"], "30 h plus tard : même ordre")
    }

    func testTheReaderClockNeverChangesTheOrder() {
        let members = [feedMember("a"), feedMember("b"), feedMember("me")]
        let statuses = ["a": updated(feedHours(1)), "b": updated(feedMinutes(20))]

        for shift in [-feedHours(10), 0, feedHours(10)] {
            let content = buildFeed(feedInput(members: members, statuses: statuses), now: feedNow + shift)

            XCTAssertEqual(content.cards.map { $0.member.uid }, ["b", "a", "me"])
        }
    }

    func testAloneStillGivesMyCardOnly() {
        XCTAssertEqual(order([feedMember("me")], ["me": updated(feedMinutes(1))]), ["me"])
        XCTAssertEqual(order([feedMember("me")], [:]), ["me"])
    }

    // MARK: - Le bandeau et les alertes ne changent pas (A10)

    func testTheAlertsKeepTheirOwnOrderWhateverTheOrderOfTheCards() {
        let members = [feedMember("me"), feedMember("a"), feedMember("b")]
        // `a` : silence de 5 h ; `b` : silence de 4 h 30. Les cartes : `b` d'abord (update la plus récente) ; les alertes : le plus ancien silence d'abord.
        let statuses = [
            "a": feedStatus(.out, departedAgo: feedHours(6), updatedAgo: feedHours(5)),
            "b": feedStatus(.out, departedAgo: feedHours(5), updatedAgo: feedMinutes(270)),
        ]

        let content = buildFeed(feedInput(members: members, statuses: statuses))

        XCTAssertEqual(content.cards.map { $0.member.uid }, ["b", "a", "me"])
        guard case .alerts(let pages, _) = content.bubble else {
            return XCTFail("deux rouges : une page par membre")
        }
        XCTAssertEqual(pages.map { $0.member.uid }, ["a", "b"])
    }

    // MARK: - Le report de l'ordre pendant un geste (A6, A7, A8)

    func testWhileAFingerIsDownNoCardMovesAndTheLastOrderIsAppliedOnceAtTheEnd() {
        var display = FeedDisplayOrder()
        _ = display.receive(["a", "b", "c"], gestureActive: false, immediate: true)

        XCTAssertEqual(display.receive(["c", "a", "b"], gestureActive: true, immediate: false), .unchanged)
        XCTAssertEqual(display.receive(["b", "c", "a"], gestureActive: true, immediate: false), .unchanged)
        XCTAssertEqual(display.shown, ["a", "b", "c"], "aucune carte ne bouge sous le doigt")

        XCTAssertEqual(display.gestureEnded(), .reordered(from: ["a", "b", "c"], to: ["b", "c", "a"]))
        XCTAssertEqual(display.shown, ["b", "c", "a"], "un seul réordonnancement, vers le dernier ordre reçu")
        XCTAssertEqual(display.gestureEnded(), .unchanged)
    }

    func testWithoutAGestureTheNewOrderIsAppliedAtOnce() {
        var display = FeedDisplayOrder()
        _ = display.receive(["a", "b"], gestureActive: false, immediate: true)

        XCTAssertEqual(display.receive(["b", "a"], gestureActive: false, immediate: false), .reordered(from: ["a", "b"], to: ["b", "a"]))
    }

    func testTheFirstOrderAndAnImmediateOrderAreAppliedDirectlyEvenDuringAGesture() {
        var display = FeedDisplayOrder()

        XCTAssertEqual(display.receive(["a", "b"], gestureActive: true, immediate: false), .unchanged)
        XCTAssertEqual(display.shown, ["a", "b"], "premier affichage : rien à retenir")
        _ = display.receive(["b", "a"], gestureActive: true, immediate: false)
        XCTAssertEqual(display.shown, ["a", "b"])

        XCTAssertEqual(display.receive(["b", "a"], gestureActive: true, immediate: true), .reordered(from: ["a", "b"], to: ["b", "a"]))
    }

    func testFlushAppliesTheWaitingOrderWhateverTheGesture() {
        var display = FeedDisplayOrder()
        _ = display.receive(["a", "b"], gestureActive: false, immediate: true)
        _ = display.receive(["b", "a"], gestureActive: true, immediate: false)

        XCTAssertEqual(display.flush(), .reordered(from: ["a", "b"], to: ["b", "a"]))
        XCTAssertEqual(display.flush(), .unchanged)
    }

    func testAMemberWhoLeavesDisappearsAndANewcomerGoesLastWithoutMovingTheOthers() {
        var display = FeedDisplayOrder()
        _ = display.receive(["a", "b", "c"], gestureActive: false, immediate: true)

        _ = display.receive(["x", "c", "a"], gestureActive: true, immediate: false)

        XCTAssertEqual(display.shown, ["a", "c", "x"])
    }

    func testResolveKeepsTheLiveContentOfEachCardInTheShownOrder() {
        var display = FeedDisplayOrder()
        let members = [feedMember("a"), feedMember("b"), feedMember("me")]
        let first = buildFeed(feedInput(members: members, statuses: ["a": updated(feedHours(2)), "b": updated(feedHours(3))]))
        _ = display.receive(first.cards.map { $0.member.uid }, gestureActive: false, immediate: true)

        // `b` publie une update pendant que la liste défile : son contenu est à jour, sa place attend.
        let next = buildFeed(feedInput(members: members, statuses: ["a": updated(feedHours(2)), "b": updated(feedMinutes(1))]))
        _ = display.receive(next.cards.map { $0.member.uid }, gestureActive: true, immediate: false)
        let shown = display.resolve(next.cards)

        XCTAssertEqual(next.cards.map { $0.member.uid }, ["b", "a", "me"])
        XCTAssertEqual(shown.map { $0.member.uid }, ["a", "b", "me"])
        XCTAssertEqual(shown.first { $0.member.uid == "b" }, next.card("b"), "le contenu est celui du moment")
    }

    func testResetForgetsTheShownOrder() {
        var display = FeedDisplayOrder()
        _ = display.receive(["a", "b"], gestureActive: false, immediate: true)

        display.reset()

        XCTAssertEqual(display.shown, [])
        XCTAssertEqual(display.receive(["b", "a"], gestureActive: true, immediate: false), .unchanged)
        XCTAssertEqual(display.shown, ["b", "a"])
    }

    // MARK: - Ce que dit le lecteur d'écran (A9)

    func testOneRiserIsAnnouncedByHisUid() {
        XCTAssertEqual(
            FeedOrderChange.announcement(from: ["a", "b", "c"], to: ["c", "a", "b"], myUid: "me"),
            .member(uid: "c")
        )
        XCTAssertEqual(
            FeedOrderChange.announcement(from: ["a", "b", "c"], to: ["b", "a", "c"], myUid: "me"),
            .member(uid: "b"),
            "une permutation : celui qui monte"
        )
    }

    func testSeveralCardsMovingGivesTheGeneralSentence() {
        XCTAssertEqual(
            FeedOrderChange.announcement(from: ["a", "b", "c", "d"], to: ["c", "d", "a", "b"], myUid: "me"),
            .generic
        )
    }

    func testMyOwnCardRisingIsNeverAnnounced() {
        XCTAssertNil(FeedOrderChange.announcement(from: ["a", "b", "me"], to: ["me", "a", "b"], myUid: "me"))
    }

    func testAnArrivalOrADepartureIsNeverAnnounced() {
        XCTAssertNil(FeedOrderChange.announcement(from: ["a", "b"], to: ["a", "b", "n"], myUid: "me"))
        XCTAssertNil(FeedOrderChange.announcement(from: ["a", "b", "c"], to: ["a", "c"], myUid: "me"))
        XCTAssertNil(FeedOrderChange.announcement(from: ["a", "b"], to: ["a", "b"], myUid: "me"))
    }
}
