import XCTest
@testable import Miff

/// « Tirer pour actualiser » demande aussi au suivi de revérifier MA présence (demande du PM du 2026-11-17) : le Feed le DEMANDE
/// (une fois par geste réellement exécuté), n'attend jamais la réponse, ne le demande ni hors connexion ni pendant la fenêtre de 15 s
/// où le geste ne lit rien, et l'actualisation des proches se termine comme avant. Les mesures, les écritures et la limite de 30 s
/// sont celles du suivi (`PullRefreshTests`).
@MainActor
final class FeedPullOwnPresenceTests: XCTestCase {
    private let repo = FakeMiffRepository()
    private let statuses = FakeStatusObserver()
    private let refresher = FakeFeedRefresher()
    private let network = FakeNetwork()
    private let clock = TestBox<Int>(feedNow)
    private let asked = TestBox<Int>(0)
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
        let now = clock
        let counter = asked
        var dependencies = FeedDependencies(
            directory: directory,
            repository: repo,
            services: FeedServices(statuses: statuses, positions: FakePositionReader(), refresher: refresher),
            network: network,
            awaiting: AwaitingAgreement(),
            currentUid: { "me" },
            clock: { now.value },
            sleep: { millis in try? await Task.sleep(nanoseconds: UInt64(max(millis, 0)) * 1_000_000) },
            timings: FeedTimings(
                statusRetryDelaysMillis: [5],
                firstStatusAnswerMillis: 600_000,
                cacheGraceMillis: 30,
                minRefreshDisplayMillis: 0
            )
        )
        dependencies.onPullOwnPresence = { counter.value += 1 }
        return FeedViewModel(dependencies: dependencies)
    }

    private func readyModel() async -> (FeedViewModel, Task<Void, Never>) {
        let model = await makeModel()
        let task = Task {
            await model.observe(miffId: testMiffId)
        }
        repo.observer.emit(
            .data(MiffSnapshot(miff: testMiffDetails(), members: [other, mine], places: [], fromCache: false))
        )
        await waitUntil { self.statuses.active == 1 }
        statuses.emit(["a": feedStatus(.inTrusted)])
        await waitUntil {
            if case .ready = model.uiState {
                return true
            }
            return false
        }
        return (model, task)
    }

    private func stop(_ task: Task<Void, Never>) async {
        task.cancel()
        await task.value
    }

    func testPullingTheFeedAsksTheTrackingToCheckMyPresenceOnceAndTheRefreshEndsAsBefore() async {
        let (model, task) = await readyModel()

        await model.pullToRefresh()

        XCTAssertEqual(asked.value, 1)
        XCTAssertEqual(refresher.calls.count, 1, "les proches sont relus comme avant")
        XCTAssertFalse(model.refreshing, ".refreshable se termine sans dépendre de la position")
        XCTAssertFalse(model.staleAfterRefresh)
        await stop(task)
    }

    func testAGestureTooCloseToThePreviousOneReadsNothingAndAsksNothing() async {
        let (model, task) = await readyModel()

        await model.pullToRefresh()
        clock.value += 14_999
        await model.pullToRefresh()

        XCTAssertEqual(asked.value, 1)
        XCTAssertEqual(refresher.calls.count, 1)

        clock.value += 1
        await model.pullToRefresh()
        XCTAssertEqual(asked.value, 2, "15 s : le geste est de nouveau permis (le suivi borne, lui, à 30 s)")
        await stop(task)
    }

    func testOfflineTheGestureEndsAtOnceCalmlyAndAsksNothing() async {
        let (model, task) = await readyModel()
        network.online = false

        await model.pullToRefresh()

        XCTAssertEqual(asked.value, 0)
        XCTAssertTrue(refresher.calls.isEmpty)
        XCTAssertFalse(model.refreshing)
        await stop(task)
    }

    func testBeforeAMiffIsShownNothingIsAsked() async {
        let model = await makeModel()

        await model.pullToRefresh()

        XCTAssertEqual(asked.value, 0)
    }

    func testTheServerNotAnsweringStillAsksOnceAndEndsWithTheHonestMention() async {
        let (model, task) = await readyModel()
        refresher.answer = .unreachable

        await model.pullToRefresh()

        XCTAssertEqual(asked.value, 1)
        XCTAssertTrue(model.staleAfterRefresh, "« Dernières informations de… » : la position n'y change rien")
        await stop(task)
    }
}
