import XCTest
@testable import Miff

/// Le centre tel que l'app le tient en mémoire : lecture par compte, inscription sans doublon, conservation, purge (déconnexion,
/// suppression du compte), et l'écran du centre (bandeau, lu / non lu, destinations).
@MainActor
final class NotificationCenterServiceTests: XCTestCase {
    private var current = 1_800_000_000_000
    private let day = NotificationFixtures.day

    private func makeService(
        store: MemoryNotificationCenterStore = MemoryNotificationCenterStore(),
        uid: @escaping () -> String? = { "uid-1" }
    ) -> NotificationCenterService {
        NotificationCenterService(store: store, currentUid: uid, nowMillis: { [unowned self] in self.current })
    }

    // MARK: - Inscription

    func testRecordingAnEntryKeepsItOnTheDeviceAndRaisesTheBellDot() {
        let store = MemoryNotificationCenterStore()
        let service = makeService(store: store)
        XCTAssertFalse(service.hasUnread)

        service.record(NotificationFixtures.item("n1|a", at: current))

        XCTAssertTrue(service.hasUnread)
        XCTAssertEqual(service.unreadCount, 1)
        XCTAssertEqual(store.items.map { $0.id }, ["n1|a"])
        XCTAssertEqual(store.storedUid, "uid-1")
    }

    func testTheSameKeyIsRecordedOnce() {
        let store = MemoryNotificationCenterStore()
        let service = makeService(store: store)

        service.record(NotificationFixtures.item("n1|a", at: current))
        let saves = store.saveCalls
        service.record(NotificationFixtures.item("n1|a", at: current + 1))

        XCTAssertEqual(service.history.items.count, 1)
        XCTAssertEqual(store.saveCalls, saves, "rien n'est réécrit pour un doublon")
    }

    func testNothingIsRecordedWithoutASession() {
        let store = MemoryNotificationCenterStore()
        let service = makeService(store: store, uid: { nil })

        service.record(NotificationFixtures.item("n1|a", at: current))

        XCTAssertTrue(service.history.isEmpty)
        XCTAssertEqual(store.saveCalls, 0)
    }

    func testAnEntryRecordedBeforeTheCenterWasOpenedIsAddedToWhatWasKept() {
        // Une notification arrive pendant que l'app est relancée en arrière-plan : le centre n'a pas encore été lu.
        let store = MemoryNotificationCenterStore()
        store.seed([NotificationFixtures.item("n1|old", at: current - 1_000)], uid: "uid-1")
        let service = makeService(store: store)

        service.record(NotificationFixtures.item("n3|new", type: .n3, at: current))

        XCTAssertEqual(service.history.items.map { $0.id }, ["n3|new", "n1|old"])
    }

    // MARK: - Lecture par compte et conservation

    func testTheFileOfAnotherAccountIsNeverShown() {
        let store = MemoryNotificationCenterStore()
        store.seed([NotificationFixtures.item("n1|a", at: current)], uid: "uid-other")
        let service = makeService(store: store)

        service.load()

        XCTAssertTrue(service.history.isEmpty)
    }

    func testLoadingDropsWhatIsOlderThanThirtyDaysAndWritesTheCleanListBack() {
        let store = MemoryNotificationCenterStore()
        store.seed(
            [
                NotificationFixtures.item("keep", at: current - 30 * day),
                NotificationFixtures.item("drop", at: current - 30 * day - 1)
            ],
            uid: "uid-1"
        )
        let service = makeService(store: store)

        service.load()

        XCTAssertEqual(service.history.items.map { $0.id }, ["keep"])
        XCTAssertEqual(store.items.map { $0.id }, ["keep"])
    }

    func testTimePassingPrunesOnTheNextLoad() {
        let store = MemoryNotificationCenterStore()
        let service = makeService(store: store)
        service.record(NotificationFixtures.item("n1|a", at: current))

        current += 31 * day
        service.load()

        XCTAssertTrue(service.history.isEmpty)
        XCTAssertTrue(store.items.isEmpty)
    }

    // MARK: - Actions

    func testReadingDeletingAndClearingAreKept() {
        let store = MemoryNotificationCenterStore()
        let service = makeService(store: store)
        service.record(NotificationFixtures.item("a", at: current))
        service.record(NotificationFixtures.item("b", at: current - 1))
        service.record(NotificationFixtures.item("c", at: current - 2))

        service.markRead(id: "a")
        XCTAssertEqual(service.unreadCount, 2)
        service.delete(id: "b")
        XCTAssertEqual(store.items.map { $0.id }, ["a", "c"])
        service.markAllRead()
        XCTAssertFalse(service.hasUnread)
        service.deleteAll()
        XCTAssertTrue(service.history.isEmpty)
        XCTAssertTrue(store.items.isEmpty)
    }

    // MARK: - Purge

    func testThePurgeEmptiesTheMemoryAndTheFileAndTheNextAccountStartsClean() {
        let store = MemoryNotificationCenterStore()
        var uid: String? = "uid-1"
        let service = makeService(store: store, uid: { uid })
        service.record(NotificationFixtures.item("n1|a", at: current))

        service.purge()

        XCTAssertTrue(service.history.isEmpty)
        XCTAssertEqual(store.clearCalls, 1)
        uid = "uid-2"
        service.load()
        XCTAssertTrue(service.history.isEmpty, "le compte suivant ne retrouve rien du précédent")
    }

    // MARK: - L'écran

    private func makeScreen(
        _ authorization: NotificationAuthorization
    ) -> (NotificationCenterViewModel, NotificationCenterService, NotificationStubPermissions) {
        let permissions = NotificationStubPermissions(authorization)
        let service = makeService()
        return (NotificationCenterViewModel(service: service, permissions: permissions), service, permissions)
    }

    func testTheBannerAppearsOnlyWhenNotificationsCannotArrive() async {
        let cases: [(NotificationAuthorization, NotificationCenterBanner?)] = [
            (.authorized, nil), (.denied, .off), (.notDetermined, .notAsked)
        ]
        for (authorization, expected) in cases {
            let (screen, _, _) = makeScreen(authorization)
            XCTAssertNil(screen.banner, "rien tant que l'état n'est pas lu")

            await screen.onAppear()

            XCTAssertEqual(screen.banner, expected, "\(authorization)")
        }
    }

    func testTheBannerButtonOpensTheHelpWhenTurnedOffAndThePrimeWhenNeverAsked() async {
        let (off, _, _) = makeScreen(.denied)
        await off.onAppear()
        var helpOpened = 0
        off.bannerAction { helpOpened += 1 }
        XCTAssertEqual(helpOpened, 1)
        XCTAssertFalse(off.primeVisible)

        let (fresh, _, permissions) = makeScreen(.notDetermined)
        await fresh.onAppear()
        fresh.bannerAction { helpOpened += 1 }
        XCTAssertEqual(helpOpened, 1)
        XCTAssertTrue(fresh.primeVisible)
        XCTAssertEqual(permissions.notificationRequests, 0, "l'amorce explique AVANT la fenêtre du système")

        await fresh.primeAllowed()
        XCTAssertFalse(fresh.primeVisible)
        XCTAssertEqual(permissions.notificationRequests, 1)
        XCTAssertNil(fresh.banner)
    }

    func testLaterOnThePrimeDoesNotConsumeTheSystemWindow() async {
        let (screen, _, permissions) = makeScreen(.notDetermined)
        await screen.onAppear()
        screen.bannerAction {}

        screen.primeDeferred()

        XCTAssertFalse(screen.primeVisible)
        XCTAssertEqual(permissions.notificationRequests, 0)
        XCTAssertEqual(screen.banner, .notAsked)
    }

    func testTouchingARowMarksItReadAndSaysWhereToGo() async {
        let (screen, service, _) = makeScreen(.authorized)
        service.record(NotificationFixtures.item("n1|a", type: .n1, at: current))
        service.record(NotificationFixtures.item("n3|b", type: .n3, at: current - 1))
        service.record(NotificationFixtures.item("n6|c", type: .n6, at: current - 2))

        let first = screen.open(service.history.items[0])
        let second = screen.open(service.history.items[1])
        let third = screen.open(service.history.items[2])

        XCTAssertEqual([first, second, third], [.update, .feed, .feed])
        XCTAssertEqual(service.unreadCount, 0)
    }

    func testLeavingTheScreenMarksWhatWasSeenAsRead() async {
        let (screen, service, _) = makeScreen(.authorized)
        service.record(NotificationFixtures.item("n2|a", type: .n2, at: current))
        service.record(NotificationFixtures.item("n4|b", type: .n4, at: current - 1))
        await screen.onAppear()
        XCTAssertEqual(service.unreadCount, 2, "la pastille reste le temps de la visite")

        screen.onDisappear()

        XCTAssertEqual(service.unreadCount, 0)
    }

    func testClearingEverythingAsksFirst() async {
        let (screen, service, _) = makeScreen(.authorized)
        service.record(NotificationFixtures.item("n1|a", at: current))

        screen.askDeleteAll()
        XCTAssertTrue(screen.deleteAllAsked)
        XCTAssertFalse(service.history.isEmpty, "rien n'est effacé avant la confirmation")
        screen.confirmDeleteAll()

        XCTAssertFalse(screen.deleteAllAsked)
        XCTAssertTrue(service.history.isEmpty)
    }

    func testDeletingOneRowKeepsTheOthers() {
        let (screen, service, _) = makeScreen(.authorized)
        service.record(NotificationFixtures.item("n1|a", at: current))
        service.record(NotificationFixtures.item("n2|b", type: .n2, at: current - 1))

        screen.delete(service.history.items[0])

        XCTAssertEqual(service.history.items.map { $0.id }, ["n2|b"])
    }
}
