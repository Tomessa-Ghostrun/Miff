import XCTest
@testable import Miff

/// Ce que le créateur reçoit dans le centre de notifications et sur la cloche (ticket LIEU-REQ, critère C1, miroir d'Android) : une ligne par
/// demande reçue, la cloche allumée jusqu'à ce que la liste soit vue, les prénoms gardés en mémoire seulement ; et « Réessayer » qui relance
/// l'écouteur d'un créateur.
@MainActor
final class ProfileRequestsCenterTests: XCTestCase {
    private let repository = FakePlaceRequestRepository()
    private let network = FakeNetwork()
    private let now = TestBox(10_000_000)
    private let lea = RequesterCard.of(uid: testMemberUid, firstName: "Léa", lastName: "Martin", avatarJpeg: nil)
    private let store = MemoryNotificationCenterStore()
    private let directory = RequesterDirectory()
    private lazy var center = NotificationCenterService(
        store: store, currentUid: { testCreatorUid }, nowMillis: { [unowned self] in self.now.value }
    )

    private func viewModel() -> ProfileRequestsViewModel {
        let clock = now
        let model = ProfileRequestsViewModel(
            repository: repository,
            network: network,
            myUid: { testCreatorUid },
            nowMillis: { clock.value }
        )
        model.center = center
        model.directory = directory
        return model
    }

    private func listening(_ model: ProfileRequestsViewModel, list: [PlaceRequest]) async -> Task<Void, Never> {
        model.onCardShown(miffId: testMiffId, creator: true)
        let task = Task { await model.watch(miffId: testMiffId) }
        await waitUntil { !self.repository.observedMiffs.isEmpty }
        repository.emit(.data(requests: list, fromCache: false))
        await waitUntil { model.state.views[testMiffId]?.requests.count == list.count }
        return task
    }

    // MARK: - Une ligne par demande, la cloche

    func testEachRequestReceivedWritesOneSilentLineAndLightsTheBell() async {
        let model = viewModel()

        let task = await listening(model, list: [testRequest(slot: 1), testRequest(slot: 2)])

        XCTAssertEqual(center.history.items.count, 2)
        XCTAssertTrue(center.history.items.allSatisfy { $0.type == .n8 && $0.state == .silent })
        XCTAssertTrue(center.history.items.allSatisfy { $0.miffId == testMiffId })
        XCTAssertTrue(center.hasUnread, "le point de la cloche")
        task.cancel()
    }

    func testTheSameRequestHeardAgainIsNotWrittenTwice() async {
        let model = viewModel()
        let first = testRequest(slot: 1)
        let task = await listening(model, list: [first])

        repository.emit(.data(requests: [first], fromCache: false))
        repository.emit(.data(requests: [first, testRequest(slot: 2)], fromCache: false))
        await waitUntil { model.state.views[testMiffId]?.requests.count == 2 }

        XCTAssertEqual(center.history.items.count, 2, "une ligne par demande, jamais deux pour la même")
        task.cancel()
    }

    func testNothingIsWrittenForARequestOfMoreThanThirtyDays() async {
        let day = 24 * 60 * 60 * 1000
        let old = testRequest(slot: 1, createdAtMillis: now.value - 40 * day)
        let recent = testRequest(slot: 2, createdAtMillis: now.value - day)
        let model = viewModel()

        model.onCardShown(miffId: testMiffId, creator: true)
        let task = Task { await model.watch(miffId: testMiffId) }
        await waitUntil { !self.repository.observedMiffs.isEmpty }
        repository.emit(.data(requests: [old, recent], fromCache: false))
        await waitUntil { !self.center.history.isEmpty }

        XCTAssertEqual(center.history.items.map { $0.id }, ["n8|\(testMiffId)|\(recent.id)"])
        task.cancel()
    }

    func testSeeingTheListOfTheMiffReadsItsLinesAndTheBellGoesOutWithTheDot() async {
        let model = viewModel()
        let task = await listening(model, list: [testRequest(slot: 1), testRequest(slot: 2)])
        XCTAssertTrue(center.hasUnread)

        model.onListSeen(miffId: testMiffId)

        XCTAssertFalse(center.hasUnread)
        XCTAssertFalse(model.state.profileDot)
        task.cancel()
    }

    func testAnotherMiffLinesStayUnreadWhenOneListIsSeen() async {
        let model = viewModel()
        center.record(
            PlaceRequestLines.lines(
                miffId: testOtherMiffId, requests: [testRequest(miffId: testOtherMiffId)], nowMillis: now.value
            )[0]
        )
        let task = await listening(model, list: [testRequest(slot: 1)])

        model.onListSeen(miffId: testMiffId)

        XCTAssertEqual(center.unreadCount, 1, "la ligne de l'autre Miff n'a pas été vue")
        task.cancel()
    }

    func testWithoutACentreTheListenerStillWorks() async {
        let model = viewModel()
        model.center = nil

        let task = await listening(model, list: [testRequest(slot: 1)])

        XCTAssertTrue(model.state.profileDot)
        XCTAssertTrue(center.history.isEmpty)
        task.cancel()
    }

    // MARK: - Les prénoms : en mémoire seulement

    func testTheFirstNameOfTheRequesterGoesToTheMemoryDirectoryAndNeverToTheCentre() async {
        repository.requesterResults[testMemberUid] = .known(lea)
        let model = viewModel()

        let task = await listening(model, list: [testRequest(slot: 1)])
        await waitUntil { model.state.requesters.count == 1 }

        XCTAssertEqual(directory.firstName(miffId: testMiffId, uid: testMemberUid), "Léa")
        let stored = center.history.items.map { String(describing: $0) }.joined()
        XCTAssertFalse(stored.contains("Léa"))
        XCTAssertFalse(stored.contains("Club de judo"))
        XCTAssertFalse(store.items.contains { "\($0)".contains("Léa") })
        task.cancel()
    }

    func testAnUnknownRequesterLeavesTheDirectoryWithoutAName() async {
        repository.requesterResults[testMemberUid] = .unknown
        let model = viewModel()

        let task = await listening(model, list: [testRequest(slot: 1)])
        await waitUntil { model.state.requesters.count == 1 }

        XCTAssertNil(directory.firstName(miffId: testMiffId, uid: testMemberUid))
        task.cancel()
    }

    // MARK: - Le centre : toucher une ligne groupée

    private func screen() -> NotificationCenterViewModel {
        NotificationCenterViewModel(
            service: center, permissions: NotificationStubPermissions(.authorized), directory: directory
        )
    }

    func testTappingAGroupedLineReadsEveryRequestItStandsForAndLeadsToThatMiff() async {
        let model = viewModel()
        let task = await listening(model, list: [testRequest(slot: 1), testRequest(slot: 2), testRequest(slot: 3)])
        let centre = screen()
        let shown = PlaceRequestLines.displayed(center.history.items)
        XCTAssertEqual(shown.count, 1, "trois demandes de la même personne : une seule ligne")
        XCTAssertEqual(centre.ids(of: shown[0]).count, 3)

        let destination = centre.open(shown[0])

        XCTAssertEqual(destination, .placeRequests(miffId: testMiffId))
        XCTAssertFalse(center.hasUnread, "toutes les lignes que la ligne représente sont lues")
        task.cancel()
    }

    func testDeletingAGroupedLineDeletesEveryRequestLineItStandsFor() async {
        let model = viewModel()
        let task = await listening(model, list: [testRequest(slot: 1), testRequest(slot: 2)])
        let centre = screen()

        centre.delete(PlaceRequestLines.displayed(center.history.items)[0])

        XCTAssertTrue(center.history.isEmpty)
        task.cancel()
    }

    func testTheCentreFindsTheFirstNameOfTheRequesterAtDrawingTime() async {
        repository.requesterResults[testMemberUid] = .known(lea)
        let model = viewModel()
        let task = await listening(model, list: [testRequest(slot: 1)])
        await waitUntil { model.state.requesters.count == 1 }
        let centre = screen()

        let item = PlaceRequestLines.displayed(center.history.items)[0]

        XCTAssertEqual(centre.requesterName(of: item), "Léa")
        task.cancel()
    }

    // MARK: - « Réessayer » relance l'écouteur d'un créateur

    func testRetryRestartsTheListenerOfACreatorButNotTheReadingOfAMember() {
        let model = viewModel()
        model.onCardShown(miffId: testMiffId, creator: true)
        XCTAssertEqual(model.watchEpoch, 0)

        model.onRetry()

        XCTAssertEqual(model.watchEpoch, 1, "la clé de l'écouteur change : l'hôte le relance")

        model.onCardShown(miffId: testOtherMiffId, creator: false)
        model.onRetry()

        XCTAssertEqual(model.watchEpoch, 1, "un membre relit, il n'écoute pas")
    }
}
