import XCTest
@testable import Miff

// Sans réseau (le système le dit), la lecture des Miffs de la personne va droit au cache du téléphone : aucune question au serveur,
// donc aucun délai de 20 s mis bout à bout (cause du « Feed qui charge à l'infini » sans réseau). Avec un réseau, rien ne change.
@MainActor
final class MiffRepositoryOfflineReadTests: XCTestCase {
    private let uid = "uid-1"
    private let store = FakeMiffStore()
    private let online = TestBox(false)

    private func repo() -> FirestoreMiffRepository {
        var rules = MiffRepositoryRules(newMiffId: { testMiffId }, timeoutSeconds: 1, deviceId: { testDeviceId })
        let network = online
        rules.isOnline = { network.value }
        return FirestoreMiffRepository(
            store: store,
            observer: FakeMiffObserver(),
            location: FakeLocationProbe(),
            cache: FakeCachePurger(timeline: Timeline()),
            rules: rules
        )
    }

    private func cachedMiff() {
        store.memberships = [MembershipRef(key: "free", miffId: testMiffId)]
        store.miffs[testMiffId] = testMiffDetails()
    }

    func testWithoutNetworkTheMiffsComeFromTheCacheWithoutAskingTheServer() async {
        cachedMiff()

        let result = await repo().myMiffs(uid: uid)

        guard case .loaded(let load) = result else {
            return XCTFail("le cache répond")
        }
        XCTAssertEqual(load.miffs.map(\.id), [testMiffId])
        XCTAssertTrue(load.fromCache)
        XCTAssertFalse(
            load.premiumKnown,
            "la formule n'est pas connue sans le serveur : jamais « gratuit » sur un doute"
        )
        XCTAssertFalse(store.calls.contains("memberships:server"), "le serveur n'est pas interrogé")
        XCTAssertFalse(store.calls.contains("entitlement"), "l'abonnement non plus")
    }

    func testWithoutNetworkAndNothingInTheCacheNothingIsAffirmed() async {
        store.memberships = []

        let result = await repo().myMiffs(uid: uid)

        XCTAssertEqual(result, .unavailable, "un cache vide n'est pas « aucune Miff »")
    }

    func testWithTheNetworkTheServerIsAskedAsBefore() async {
        online.value = true
        cachedMiff()

        let result = await repo().myMiffs(uid: uid)

        guard case .loaded(let load) = result else {
            return XCTFail("le serveur répond")
        }
        XCTAssertFalse(load.fromCache)
        XCTAssertTrue(store.calls.contains("memberships:server"))
        XCTAssertTrue(store.calls.contains("entitlement"))
    }
}
