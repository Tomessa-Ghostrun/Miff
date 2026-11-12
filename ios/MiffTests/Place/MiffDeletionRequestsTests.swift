import XCTest
@testable import Miff

/// La suppression d'une Miff et les demandes d'ajout d'un lieu (ticket LIEU-REQ-C, critère L3) : les demandes et leur compteur sont supprimés
/// AVANT les lieux et le dernier lot (contrat 2.8), 5 demandes par lot, et rien d'elles ne reste. TOUT échec, refus compris (les règles sont
/// déployées), met la suppression en pause : jamais de demandes orphelines.
@MainActor
final class MiffDeletionRequestsTests: XCTestCase {
    private let uid = "uid-owner"
    private let store = FakeMiffStore()

    private func world(requests: [String], counter: Bool) {
        store.simulate = true
        store.miffs[testMiffId] = testMiffDetails(owner: uid)
        store.memberIds = [uid]
        store.placeIds = ["p1"]
        store.memberships = [MembershipRef(key: "free", miffId: testMiffId)]
        store.requestLeftovers = PlaceRequestLeftovers(requestIds: requests, counterExists: counter)
    }

    private func deletion() -> MiffDeletion {
        let rules = MiffRepositoryRules(newMiffId: { testMiffId }, timeoutSeconds: 1)
        let repository = FirestoreMiffRepository(
            store: store,
            observer: FakeMiffObserver(),
            location: FakeLocationProbe(),
            cache: FakeCachePurger(timeline: Timeline()),
            rules: rules
        )
        return MiffDeletion(store: store, repository: repository, timeoutSeconds: 1)
    }

    private var paths: [[String]] {
        store.commitsAsLots.map { lot in lot.map { $0.path } }
    }

    func testTheRequestsAndTheirCounterGoFirstThenThePlacesThenTheLastLot() async {
        let ids = (1...7).map { "uidmember($0)_1" }
        world(requests: ids, counter: true)

        let result = await deletion().run(uid: uid, miffId: testMiffId)

        XCTAssertEqual(result, .deleted)
        // Contrat 2.8 : 2 lots de demandes (5, puis 2 + le compteur), puis le lot de lieux, puis le dernier lot.
        XCTAssertEqual(store.commitsAsLots.map { $0.count }.prefix(3), [5, 3, 1])
        XCTAssertEqual(paths[0].filter { $0.contains("/placeRequests/") }.count, 5)
        XCTAssertEqual(paths[1].filter { $0.contains("/placeRequests/") }.count, 2)
        // Le compteur part avec le DERNIER lot de demandes.
        XCTAssertEqual(paths[1].last, "miffs/(testMiffId)/meta/placeRequests")
        XCTAssertEqual(paths[2], ["miffs/(testMiffId)/places/p1"])
        XCTAssertTrue(Array(store.commitsAsLots[0...1]).allSatisfy { $0.allSatisfy { $0.kind == .delete } })
        let last = store.commitsAsLots.dropFirst(3).first ?? []
        XCTAssertTrue(last.contains { $0.path == "miffs/(testMiffId)" }, "le dernier lot est celui de la Miff")
    }

    func testOnlyACounterLeftIsDeletedByALotOfItsOwn() async {
        world(requests: [], counter: true)

        _ = await deletion().run(uid: uid, miffId: testMiffId)

        XCTAssertEqual(paths[0], ["miffs/(testMiffId)/meta/placeRequests"])
    }

    func testNothingLeftWritesNothingForTheRequests() async {
        world(requests: [], counter: false)

        _ = await deletion().run(uid: uid, miffId: testMiffId)

        XCTAssertFalse(paths.joined().contains { $0.contains("placeRequests") })
        XCTAssertEqual(store.commitsAsLots.count, 2, "les lieux, puis le dernier lot")
    }

    func testARefusalWhileListingTheRequestsPausesTheDeletionNeverLeavingThemOrphaned() async {
        world(requests: [], counter: false)
        store.requestLeftoversFailure = firestoreError(FirestoreErrorNumber.permissionDenied)

        let result = await deletion().run(uid: uid, miffId: testMiffId)

        // Les règles des demandes sont déployées : un refus n'est plus « elles ne les connaissent pas ».
        XCTAssertEqual(result, .paused(.refused))
        XCTAssertFalse(paths.joined().contains { $0 == "miffs/(testMiffId)" })
    }

    func testNoNetworkWhileListingTheRequestsPausesTheDeletion() async {
        world(requests: [], counter: false)
        store.requestLeftoversFailure = firestoreError(FirestoreErrorNumber.unavailable)

        let result = await deletion().run(uid: uid, miffId: testMiffId)

        XCTAssertEqual(result, .paused(.offline))
        // La Miff n'est pas supprimée à moitié.
        XCTAssertFalse(paths.joined().contains { $0 == "miffs/(testMiffId)" })
    }

    func testARefusedOrOfflineLotOfRequestsStopsTheDeletionBeforeThePlacesAndTheMiff() async {
        world(requests: ["uidmember1_1"], counter: true)
        store.commitFailures = [firestoreError(FirestoreErrorNumber.permissionDenied)]
        let refused = await deletion().run(uid: uid, miffId: testMiffId)

        world(requests: ["uidmember1_1"], counter: true)
        store.commitFailures = [firestoreError(FirestoreErrorNumber.unavailable)]
        let offline = await deletion().run(uid: uid, miffId: testMiffId)

        XCTAssertEqual(refused, .paused(.refused))
        XCTAssertEqual(offline, .paused(.offline))
        XCTAssertFalse(paths.joined().contains { $0.contains("/places/") || $0 == "miffs/(testMiffId)" })
    }
}
