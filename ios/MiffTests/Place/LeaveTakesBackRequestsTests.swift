import XCTest
@testable import Miff

/// Quitter une Miff, ou supprimer son compte, reprend d'abord les demandes d'ajout d'un lieu de la personne (ticket LIEU-REQ, critères D9 et L1) :
/// elles portent une adresse et des coordonnées, et les règles ne laissent leur auteur les supprimer que tant qu'il est membre. La reprise
/// vient AVANT le lot « Quitter », et ne le bloque jamais.
@MainActor
final class LeaveTakesBackRequestsTests: XCTestCase {
    private let uid = "uid-owner"
    private let store = FakeMiffStore()
    private let deleter = ScriptedDeleter()
    /// Ce que la reprise a vu, et ce que le magasin avait déjà reçu à ce moment-là.
    private let events = TestBox<[String]>([])

    private func repository(beforeLeave: @escaping (String, String) async -> Void) -> FirestoreMiffRepository {
        var rules = MiffRepositoryRules(newMiffId: { testMiffId }, timeoutSeconds: 1)
        rules.beforeLeave = beforeLeave
        return FirestoreMiffRepository(
            store: store,
            observer: FakeMiffObserver(),
            location: FakeLocationProbe(),
            cache: FakeCachePurger(timeline: Timeline()),
            rules: rules
        )
    }

    func testLeavingAMiffTakesTheRequestsBackBeforeTheLeaveLot() async {
        store.miffs[testMiffId] = testMiffDetails(owner: "someone-else")
        store.memberships = [MembershipRef(key: "free", miffId: testMiffId)]
        let log = events
        let spy = store
        let repo = repository { uid, miffId in
            log.value.append("beforeLeave:\(uid):\(miffId):lots=\(spy.commitsAsLots.count)")
        }

        let result = await repo.leave(uid: uid, miffId: testMiffId)

        XCTAssertEqual(result, .done)
        XCTAssertEqual(events.value, ["beforeLeave:\(uid):\(testMiffId):lots=0"], "avant le lot « Quitter », pas après")
        XCTAssertEqual(store.commitsAsLots.count, 1)
    }

    func testAnInvalidMiffIdTakesNothingBack() async {
        let log = events
        let repo = repository { _, _ in log.value.append("called") }

        let result = await repo.leave(uid: uid, miffId: "not-a-miff-id")

        XCTAssertEqual(result, .failed(.unknown))
        XCTAssertTrue(events.value.isEmpty)
    }

    func testDeletingAnAccountTakesBackTheRequestsOfEveryMiffItLeaves() async {
        let log = events
        let accountStore = FakeMiffStore()
        let miffs = StoreAccountMiffs(
            store: accountStore,
            deleter: deleter,
            timeoutSeconds: 2,
            beforeLeave: { uid, miffId in log.value.append("\(uid):\(miffId)") }
        )

        let outcome = await miffs.leave(uid: testAccountUid, ref: MembershipRef(key: "free", miffId: testMiffId))

        XCTAssertEqual(outcome, .done)
        XCTAssertEqual(events.value, ["\(testAccountUid):\(testMiffId)"])
        XCTAssertEqual(accountStore.commitsAsLots.count, 1)
    }

    func testTheRequestsHookDefaultsToNothingSoExistingCallsAreUnchanged() async {
        let miffs = StoreAccountMiffs(store: store, deleter: deleter, timeoutSeconds: 2)

        let outcome = await miffs.leave(uid: testAccountUid, ref: MembershipRef(key: "free", miffId: testMiffId))

        XCTAssertEqual(outcome, .done)
    }
}
