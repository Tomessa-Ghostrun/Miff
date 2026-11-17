import XCTest
@testable import Miff

// Le report de l'ordre des cartes (ticket FEED-ORDRE, critères A6 à A9) : rien ne bouge pendant un geste, l'ordre à jour est appliqué à la
// fin du geste, directement au retour sur le Feed, et l'annonce du lecteur d'écran est unique et ne parle jamais de ma carte.
@MainActor
final class FeedOrderModelTests: XCTestCase {
    private let names = ["a": "Thomas", "b": "Léa", "c": "Inès", "me": "Camille"]
    private var announced: [String] = []

    private func makeModel() -> FeedOrderModel {
        let model = FeedOrderModel()
        model.onAnnounce = { [weak self] text in self?.announced.append(text) }
        return model
    }

    private func context(_ uids: [String], miff: String = "m1") -> FeedOrderContext {
        FeedOrderContext(miffId: miff, uids: uids, myUid: "me", callNames: names)
    }

    func testTheFirstOrderIsShownWithoutAnnouncement() {
        let model = makeModel()

        model.update(context(["a", "b", "me"]), animated: true)

        XCTAssertEqual(model.order.shown, ["a", "b", "me"])
        XCTAssertTrue(announced.isEmpty)
    }

    func testNothingMovesDuringAGestureThenTheOrderIsAppliedOnceAndAnnounced() {
        let model = makeModel()
        model.update(context(["a", "b", "c", "me"]), animated: true)

        model.setGestureActive(true, animated: true)
        model.update(context(["c", "a", "b", "me"]), animated: true)

        XCTAssertEqual(model.order.shown, ["a", "b", "c", "me"], "aucune carte ne bouge sous le doigt")
        XCTAssertTrue(announced.isEmpty)

        model.setGestureActive(false, animated: true)

        XCTAssertEqual(model.order.shown, ["c", "a", "b", "me"])
        XCTAssertEqual(announced.count, 1, "une seule annonce par application d'ordre")
        XCTAssertTrue(announced[0].contains("Inès"), "elle dit qui a donné des nouvelles")
    }

    func testWithoutAGestureTheOrderIsAppliedAtOnce() {
        let model = makeModel()
        model.update(context(["a", "b", "me"]), animated: false)

        model.update(context(["b", "a", "me"]), animated: false)

        XCTAssertEqual(model.order.shown, ["b", "a", "me"])
        XCTAssertEqual(announced.count, 1)
    }

    func testSeveralChangesDuringAGestureGiveOneFinalReorderAndOneAnnouncement() {
        let model = makeModel()
        model.update(context(["a", "b", "c", "me"]), animated: true)
        model.setGestureActive(true, animated: true)

        model.update(context(["b", "a", "c", "me"]), animated: true)
        model.update(context(["c", "b", "a", "me"]), animated: true)
        model.setGestureActive(false, animated: true)

        XCTAssertEqual(model.order.shown, ["c", "b", "a", "me"])
        XCTAssertEqual(announced.count, 1)
    }

    func testMyOwnCardRisingIsNotAnnounced() {
        let model = makeModel()
        model.update(context(["a", "b", "me"]), animated: true)

        model.update(context(["me", "a", "b"]), animated: true)

        XCTAssertEqual(model.order.shown, ["me", "a", "b"])
        XCTAssertTrue(announced.isEmpty, "jamais d'annonce pour ma carte")
    }

    func testReturningToTheFeedAppliesTheCurrentOrderWithoutAnnouncementEvenWithAStuckGesture() {
        let model = makeModel()
        model.update(context(["a", "b", "me"]), animated: true)
        model.setGestureActive(true, animated: true)
        model.update(context(["b", "a", "me"]), animated: true)

        model.flush()

        XCTAssertEqual(model.order.shown, ["b", "a", "me"])
        XCTAssertTrue(announced.isEmpty, "rien n'est annoncé au retour sur le Feed ni au rafraîchissement")
        model.update(context(["a", "b", "me"]), animated: true)
        XCTAssertEqual(model.order.shown, ["a", "b", "me"], "le geste resté en cours ne bloque plus rien")
    }

    func testAnotherMiffIsShownDirectlyWithoutAnnouncement() {
        let model = makeModel()
        model.update(context(["a", "b", "me"]), animated: true)
        model.setGestureActive(true, animated: true)

        model.update(context(["c", "b", "a", "me"], miff: "m2"), animated: true)

        XCTAssertEqual(model.order.shown, ["c", "b", "a", "me"])
        XCTAssertTrue(announced.isEmpty)
    }

    func testTheSkeletonForgetsTheOrderSoTheNextOneIsDirect() {
        let model = makeModel()
        model.update(context(["a", "b", "me"]), animated: true)

        model.contentGone()
        model.update(context(["b", "a", "me"]), animated: true)

        XCTAssertEqual(model.order.shown, ["b", "a", "me"])
        XCTAssertTrue(announced.isEmpty, "retour après un squelette : directement, sans annonce")
    }

    func testSeveralCardsMovingAreAnnouncedByTheGeneralSentence() {
        let model = makeModel()
        model.update(context(["a", "b", "c", "me"]), animated: true)

        model.update(context(["c", "me", "a", "b"]), animated: true)

        XCTAssertEqual(announced, [L10n.string("feed_order_updated_a11y")])
    }

    func testTheAnnouncementTextsExistInBothLanguages() {
        let member = FeedOrderModel.text(for: .member(uid: "a"), names: names)
        let generic = FeedOrderModel.text(for: .generic, names: names)
        let unreadable = FeedOrderModel.text(for: .member(uid: "a"), names: ["a": ""])

        XCTAssertNotEqual(member, "feed_order_member_a11y")
        XCTAssertTrue(member?.contains("Thomas") == true)
        XCTAssertNotEqual(generic, "feed_order_updated_a11y")
        XCTAssertEqual(unreadable, generic, "sans prénom lisible : la phrase générale")
    }
}
