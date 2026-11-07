import XCTest
@testable import Miff

/// MON statut dans la Miff affichée (D19) : suivi par UN écouteur, relancé quand la Miff affichée change, jamais celui d'une autre Miff le
/// temps de la lecture, rien sans compte ni Miff, effacé à la fin de session.
@MainActor
final class MyStatusStoreTests: XCTestCase {
    private let observer = FakeMyStatusObserver()
    private var uid: String? = updateUid

    private func makeStore() -> MyStatusStore {
        MyStatusStore(observer: observer, currentUid: { [unowned self] in self.uid })
    }

    private let sample = MyStatusFacts(presence: .out, presenceAtMillis: 10, lastUpdateAtMillis: nil, hasPhoto: false)

    func testFollowingAMiffShowsItsStatusAndAChangeOfMiffStartsFromNothing() async {
        let store = makeStore()
        let first = Task { await store.follow(miffId: testMiffId) }
        await waitUntil { self.observer.observed == [testMiffId] }

        observer.send(sample)
        await waitUntil { store.facts == self.sample }
        first.cancel()
        let second = Task { await store.follow(miffId: testOtherMiffId) }
        await waitUntil { self.observer.observed.count == 2 }

        XCTAssertEqual(observer.observed, [testMiffId, testOtherMiffId])
        XCTAssertNil(store.facts, "le statut d'une autre Miff n'est jamais montré le temps de la lecture")
        second.cancel()
    }

    func testAMiffWithNoStatusOfMineIsNil() async {
        let store = makeStore()
        let task = Task { await store.follow(miffId: testMiffId) }
        await waitUntil { self.observer.observed.count == 1 }

        observer.send(sample)
        await waitUntil { store.facts != nil }
        observer.send(nil)
        await waitUntil { store.facts == nil }

        XCTAssertNil(store.facts)
        task.cancel()
    }

    func testWithoutAMiffOrWithoutAnAccountNothingIsFollowed() async {
        let store = makeStore()
        store.update(sample)

        await store.follow(miffId: nil)
        XCTAssertNil(store.facts)
        store.update(sample)
        uid = nil
        await store.follow(miffId: testMiffId)

        XCTAssertNil(store.facts)
        XCTAssertTrue(observer.observed.isEmpty, "aucun écouteur ouvert")
    }

    func testTheFeedCanGiveMyStatusItsOwnListenerAndTheEndOfTheSessionForgetsIt() {
        let store = makeStore()

        store.update(sample)
        XCTAssertEqual(store.facts, sample)
        store.stop()

        XCTAssertNil(store.facts)
    }

    func testMyStatusShowsNothingOfItselfAndHoldsNoCoordinate() {
        XCTAssertEqual("\(sample)", "MyStatusFacts(redacted)")
        let names = Mirror(reflecting: sample).children.compactMap { $0.label }
        XCTAssertEqual(Set(names), ["presence", "presenceAtMillis", "lastUpdateAtMillis", "hasPhoto"])
    }
}
