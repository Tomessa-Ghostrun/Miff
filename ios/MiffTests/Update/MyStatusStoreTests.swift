import Combine
import XCTest
@testable import Miff

/// MON statut pour l'Update (D19) : le Feed, UNIQUE écouteur de ma fiche, le donne au magasin ; l'Update n'ouvre aucun écouteur. La couleur du
/// bouton central, la bulle et l'état « partage arrêté » en sortent ; rien n'est donné quand je ne partage pas ou que mon accord est attendu.
@MainActor
final class MyStatusStoreTests: XCTestCase {
    private let sample = MyStatusFacts(presence: .out, presenceAtMillis: 10, lastUpdateAtMillis: nil, hasPhoto: false)

    func testTheFeedGivesMyStatusAndTheStateOfMySharing() {
        let store = MyStatusStore()

        store.update(sample, sharingStopped: false)
        XCTAssertEqual(store.facts, sample)
        XCTAssertFalse(store.sharingStopped)
        store.update(nil, sharingStopped: true)

        XCTAssertNil(store.facts)
        XCTAssertTrue(store.sharingStopped)
    }

    func testTheEndOfTheSessionForgetsEverything() {
        let store = MyStatusStore()
        store.update(sample, sharingStopped: true)

        store.stop()

        XCTAssertNil(store.facts)
        XCTAssertFalse(store.sharingStopped)
    }

    func testAnUnchangedStatusDoesNotPublishAgain() {
        let store = MyStatusStore()
        var publications = 0
        let subscription = store.objectWillChange.sink { _ in publications += 1 }

        store.update(sample, sharingStopped: false)
        let afterFirst = publications
        store.update(sample, sharingStopped: false)

        XCTAssertEqual(publications, afterFirst, "le Feed rappelle à chaque calcul : rien ne change, rien n'est publié")
        subscription.cancel()
    }

    func testMyStatusShowsNothingOfItselfAndHoldsNoCoordinate() {
        XCTAssertEqual("\(sample)", "MyStatusFacts(redacted)")
        let names = Mirror(reflecting: sample).children.compactMap { $0.label }
        XCTAssertEqual(Set(names), ["presence", "presenceAtMillis", "lastUpdateAtMillis", "hasPhoto"])
    }

    // MARK: - Ce que le Feed donne (FeedBuilder)

    func testTheFeedBuilderGivesMyFactsOnlyWhenIShareAndMyAgreementIsNotAwaited() {
        let me = feedMember("me")
        let statuses = ["me": feedStatus(.out, departedAgo: feedMinutes(90))]

        let sharing = buildFeed(feedInput(members: [me], statuses: statuses))
        let waiting = buildFeed(feedInput(members: [me], statuses: statuses, waiting: true))
        let stopped = buildFeed(feedInput(members: [feedMember("me", sharing: false)], statuses: statuses))

        XCTAssertEqual(sharing.myFacts?.presence, .out)
        XCTAssertEqual(sharing.myFacts?.presenceAtMillis, feedNow - feedMinutes(90))
        XCTAssertNil(waiting.myFacts)
        XCTAssertNil(stopped.myFacts)
        XCTAssertEqual(stopped.myLabel, .sharingStopped)
    }

    func testTheFeedBuilderGivesNothingOfSomeoneElseAsMine() {
        let content = buildFeed(
            feedInput(members: [feedMember("me"), feedMember("other")], statuses: ["other": feedStatus(.out)])
        )

        XCTAssertNil(content.myFacts, "pas de statut à moi : le bouton central est neutre")
    }
}
