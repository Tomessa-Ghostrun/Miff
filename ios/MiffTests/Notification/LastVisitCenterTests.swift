import XCTest
@testable import Miff

/// Le groupe « Depuis ta dernière visite » dans l'écran du centre : visible quoi que dise l'autorisation du téléphone (décision Q4), lu en
/// quittant l'écran même derrière le flou, un toucher mène au Feed sur la carte du proche, le prénom est celui que l'app connaît maintenant.
@MainActor
final class LastVisitCenterTests: XCTestCase {
    private typealias Fixture = VisitFixtures
    private let now = VisitFixtures.now
    private let store = MemoryNotificationCenterStore()
    private let directory = RequesterDirectory()

    private typealias Screen = (NotificationCenterViewModel, NotificationCenterService)

    private func makeScreen(_ authorization: NotificationAuthorization) async -> Screen {
        let service = NotificationCenterService(store: store, currentUid: { "uid-1" }, nowMillis: { [now] in now })
        let model = NotificationCenterViewModel(
            service: service, permissions: NotificationStubPermissions(authorization), directory: directory
        )
        service.record(Fixture.item("t", at: now - 1_000))
        service.record(NotificationFixtures.item("n1|miff-1|moi|1", at: now - 5_000))
        await model.onAppear()
        return (model, service)
    }

    func testTheGroupIsInTheHistoryWhateverThePhoneSaysAndIsNeverAmongTheDays() async {
        for authorization in [NotificationAuthorization.authorized, .denied, .notDetermined] {
            let (_, service) = await makeScreen(authorization)

            XCTAssertEqual(service.history.visitItems.count, 1)
            XCTAssertEqual(service.history.dayItems.map { $0.type }, [.n1])
            store.clear()
        }
    }

    func testATapOnALineLeadsToTheFeedOnTheCardOfThePersonAndReadsTheLine() async {
        let (model, service) = await makeScreen(.authorized)
        let line = Fixture.item("t", at: now - 1_000)

        let destination = model.open(line)

        XCTAssertEqual(destination, .feedCard(miffId: Fixture.miff, uid: "t"))
        XCTAssertEqual(service.history.visitItems.first?.isUnread, false)
    }

    func testABrokenLineLeadsToTheFeedNeverToAnError() async {
        let (model, _) = await makeScreen(.authorized)
        let broken = NotificationItem(
            id: "c1|cassé", type: .c1, miffId: "m", createdAtMillis: now, readAtMillis: nil, state: .silent
        )

        XCTAssertEqual(model.open(broken), .feed)
    }

    func testLeavingTheScreenReadsEverythingWhenAuthorized() async {
        let (model, service) = await makeScreen(.authorized)

        model.onDisappear()

        XCTAssertEqual(service.unreadCount, 0)
    }

    func testLeavingTheScreenBehindTheBlurReadsOnlyTheGroupThatWasSeen() async {
        let (model, service) = await makeScreen(.denied)

        model.onDisappear()

        // Le groupe est visible au-dessus du flou : il a été vu. Derrière le flou, rien n'a été vu.
        XCTAssertEqual(service.history.visitItems.first?.isUnread, false)
        XCTAssertEqual(service.history.dayItems.first?.isUnread, true)
    }

    func testTheNameIsTheOneTheAppKnowsNowAndTheCentreKeepsNone() async {
        let (model, service) = await makeScreen(.authorized)
        let line = Fixture.item("t", at: now - 1_000)
        XCTAssertNil(model.visitName(of: line))

        directory.put(miffId: Fixture.miff, uid: "t", firstName: "Thomas")

        XCTAssertEqual(model.visitName(of: line), "Thomas")
        let stored = store.items.map { $0.id }.joined()
        XCTAssertFalse(stored.contains("Thomas"))
        XCTAssertTrue(service.history.visitItems.allSatisfy { !$0.id.contains("Thomas") })
    }
}
