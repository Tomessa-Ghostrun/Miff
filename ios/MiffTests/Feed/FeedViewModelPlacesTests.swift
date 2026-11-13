import XCTest
@testable import Miff

/// Les lieux que le Feed a DÉJÀ lus servent à la liste des lieux du Profil (zéro lecture de plus) : mais seulement quand le serveur les
/// a confirmés. Une copie du téléphone encore vide ne doit jamais être dessinée comme « aucun lieu ».
@MainActor
final class FeedViewModelPlacesTests: XCTestCase {
    private let repo = FakeMiffRepository()
    private let given = TestBox<[(String, [Place])]>([])

    private func makeModel() async -> FeedViewModel {
        repo.myMiffsResult = .loaded(
            MyMiffs(miffs: [MiffSummary(id: testMiffId, name: "Chez nous")], lost: [], premium: false, fromCache: false)
        )
        let directory = MiffDirectory(
            repository: repo,
            currentUid: { "me" },
            activeStore: MemoryActiveMiffStore(),
            cache: FakeCachePurger(timeline: Timeline())
        )
        await directory.refresh()
        let box = given
        var dependencies = FeedDependencies(
            directory: directory,
            repository: repo,
            services: FeedServices(
                statuses: FakeStatusObserver(), positions: FakePositionReader(), refresher: FakeFeedRefresher()
            ),
            network: FakeNetwork(),
            awaiting: AwaitingAgreement(),
            currentUid: { "me" },
            clock: { feedNow },
            sleep: { millis in try? await Task.sleep(nanoseconds: UInt64(max(millis, 0)) * 1_000_000) }
        )
        dependencies.onPlaces = { miffId, places in box.value.append((miffId, places)) }
        return FeedViewModel(dependencies: dependencies)
    }

    private func emit(places: [Place], fromCache: Bool) {
        let snapshot = MiffSnapshot(miff: testMiffDetails(), members: [], places: places, fromCache: fromCache)
        repo.observer.emit(.data(snapshot))
    }

    func testThePlacesTheServerConfirmedAreHandedOverAndACachedCopyIsNot() async {
        let model = await makeModel()
        let task = Task {
            await model.observe(miffId: testMiffId)
        }
        defer {
            task.cancel()
        }
        let home = testPlace(id: "p1", name: "Maison de Papa")

        emit(places: [], fromCache: true)
        await waitUntil { self.repo.observer.subscriptions == 1 }
        try? await Task.sleep(nanoseconds: 50_000_000)
        XCTAssertTrue(given.value.isEmpty, "une copie du cache, vide parce que rien n'était gardé, n'est jamais donnée")

        emit(places: [home], fromCache: false)
        await waitUntil { !self.given.value.isEmpty }

        XCTAssertEqual(given.value.count, 1)
        XCTAssertEqual(given.value.first?.0, testMiffId)
        XCTAssertEqual(given.value.first?.1, [home])
    }
}
