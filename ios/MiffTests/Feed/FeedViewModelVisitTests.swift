import XCTest
@testable import Miff

/// Ce que le Feed dit à « Depuis ta dernière visite » : seulement une image FRAÎCHE (statuts confirmés par le serveur, en ligne), avec mon
/// identifiant, les autres membres et leurs prénoms (mémoire seulement) ; jamais une copie du cache ni une image hors connexion.
@MainActor
final class FeedViewModelVisitTests: XCTestCase {
    private struct Call {
        let myUid: String
        let state: VisitState
        let names: [String: String]
    }

    private final class Calls {
        var all: [Call] = []
    }

    private let repo = FakeMiffRepository()
    private let statuses = FakeStatusObserver()
    private let network = FakeNetwork()
    private let calls = Calls()
    private let other = feedMember("a", first: "Thomas")
    private let mine = feedMember("me", first: "Camille")

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
        var dependencies = FeedDependencies(
            directory: directory,
            repository: repo,
            services: FeedServices(
                statuses: statuses, positions: FakePositionReader(), refresher: FakeFeedRefresher()
            ),
            network: network,
            awaiting: AwaitingAgreement(),
            currentUid: { "me" },
            clock: { feedNow },
            sleep: { millis in try? await Task.sleep(nanoseconds: UInt64(max(millis, 0)) * 1_000_000) },
            timings: FeedTimings(statusRetryDelaysMillis: [5], firstStatusAnswerMillis: 600_000, cacheGraceMillis: 30)
        )
        let calls = self.calls
        dependencies.onFreshPicture = { myUid, state, names in
            calls.all.append(Call(myUid: myUid, state: state, names: names))
        }
        return FeedViewModel(dependencies: dependencies)
    }

    private func start(_ model: FeedViewModel) -> Task<Void, Never> {
        Task {
            await model.observe(miffId: testMiffId)
        }
    }

    private func miffData() {
        repo.observer.emit(
            .data(MiffSnapshot(miff: testMiffDetails(), members: [other, mine], places: [], fromCache: false))
        )
    }

    private func isShown(_ model: FeedViewModel) -> Bool {
        if case .ready = model.uiState {
            return true
        }
        return false
    }

    func testAFreshPictureIsToldWithMyIdentifierTheOthersAndTheirFirstNames() async throws {
        let model = await makeModel()
        let task = start(model)
        miffData()
        await waitUntil { self.statuses.active == 1 }

        statuses.emit(["a": feedStatus(.inTrusted, departedAgo: feedMinutes(12), updatedAgo: feedMinutes(12))])
        await waitUntil { !self.calls.all.isEmpty }

        let call = try XCTUnwrap(calls.all.last)
        XCTAssertEqual(call.myUid, "me")
        XCTAssertEqual(call.state.miffId, testMiffId)
        XCTAssertEqual(call.state.members.map { $0.uid }, ["a"], "jamais moi")
        XCTAssertEqual(call.names, ["a": "Thomas"])
        task.cancel()
        await task.value
    }

    func testACopyFromThePhoneCacheIsNeverTold() async {
        let model = await makeModel()
        let task = start(model)
        miffData()
        await waitUntil { self.statuses.active == 1 }

        statuses.emit(["a": feedStatus(.inTrusted, departedAgo: feedMinutes(12))], fromCache: true)
        await waitUntil { self.isShown(model) }

        XCTAssertTrue(isShown(model), "la copie est montrée avec sa date")
        XCTAssertTrue(calls.all.isEmpty)
        task.cancel()
        await task.value
    }

    func testNothingIsToldWhileOffline() async {
        network.online = false
        let model = await makeModel()
        let task = start(model)
        miffData()
        await waitUntil { self.statuses.active == 1 && model.offline }

        statuses.emit(["a": feedStatus(.inTrusted, departedAgo: feedMinutes(12))])
        await waitUntil { self.isShown(model) }

        XCTAssertTrue(model.offline)
        XCTAssertTrue(calls.all.isEmpty)
        task.cancel()
        await task.value
    }
}
