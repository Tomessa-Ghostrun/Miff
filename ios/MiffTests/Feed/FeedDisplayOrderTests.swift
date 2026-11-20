// Les assertions citent des cas entiers : leurs lignes dépassent la limite.
// swiftlint:disable line_length
import XCTest
@testable import Miff

// Le report de l'ordre des cartes pendant un geste et l'annonce du lecteur d'écran (tickets FEED-ORDRE A6 à A9 et FEED-ORDRE-PHOTO B6 à B9) :
// `FeedDisplayOrder` et `FeedOrderChange` ne dépendent pas de la date qui classe les cartes (c'est `FeedOrderTests`). Mêmes cas que le report
// d'ordre d'Android : les tests sont le contrat.
final class FeedDisplayOrderTests: XCTestCase {
    /// Un statut dont la dernière photo remonte à `ago` (ce qui classe les cartes : `photoAt`).
    private func withPhoto(_ ago: Int) -> MemberStatus {
        var status = feedStatus(.inTrusted, departedAgo: ago, updatedAgo: ago)
        status.photoAtMillis = feedNow - ago
        return status
    }

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
        let first = buildFeed(feedInput(members: members, statuses: ["a": withPhoto(feedHours(2)), "b": withPhoto(feedHours(3))]))
        _ = display.receive(first.cards.map { $0.member.uid }, gestureActive: false, immediate: true)

        // `b` publie une update pendant que la liste défile : son contenu est à jour, sa place attend.
        let next = buildFeed(feedInput(members: members, statuses: ["a": withPhoto(feedHours(2)), "b": withPhoto(feedMinutes(1))]))
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

    func testASingleRiserWhoDoesNotReachTheTopIsAnnouncedByTheGeneralSentence() {
        XCTAssertEqual(
            FeedOrderChange.announcement(from: ["a", "b", "c", "d"], to: ["a", "c", "b", "d"], myUid: "me"),
            .generic,
            "c a monté d'une place sans arriver en tête : pas de prénom"
        )
        XCTAssertEqual(
            FeedOrderChange.announcement(from: ["a", "b", "me"], to: ["a", "me", "b"], myUid: "me"),
            .generic,
            "ma carte ne reste muette que lorsqu'elle arrive en tête"
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
// swiftlint:enable line_length
