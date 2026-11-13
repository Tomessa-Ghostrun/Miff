// Les assertions citent des cas entiers : leurs lignes dépassent la limite.
// swiftlint:disable line_length
import XCTest
@testable import Miff

// Le Feed sans réseau (bug du PM, 2026-11-10 : « le Feed se charge à l'infini ») : quand il n'y a RIEN à montrer (ni contenu, ni copie
// du cache) et que le réseau manque, la pop-in « Pas de connexion » vient, avec « Réessayer » et un essai toutes les 10 s ; avec du
// contenu en cache, le Feed garde son comportement (« Dernières informations de… » et la mention hors connexion). Les attentes sont
// commandées par le test (`ManualSleeper`) ; aucune écoute Firestore n'est ouverte ici : l'état est posé à la main.
@MainActor
final class FeedOfflinePopinTests: XCTestCase {
    private let repo = FakeMiffRepository()
    private let statuses = FakeStatusObserver()
    private let network = FakeNetwork(online: false)
    private let sleeper = ManualSleeper()
    private var tasks: [Task<Void, Never>] = []

    override func tearDown() async throws {
        tasks.forEach { $0.cancel() }
        sleeper.fire()
        for task in tasks {
            await task.value
        }
        tasks = []
        try await super.tearDown()
    }

    private func makeModel(
        directory: MyMiffsLoad = .loaded(
            MyMiffs(miffs: [MiffSummary(id: testMiffId, name: "Chez nous")], lost: [], premium: false, fromCache: true)
        ),
        refresh: Bool = true
    ) async -> FeedViewModel {
        repo.myMiffsResult = directory
        let miffDirectory = MiffDirectory(
            repository: repo,
            currentUid: { "me" },
            activeStore: MemoryActiveMiffStore(),
            cache: FakeCachePurger(timeline: Timeline())
        )
        if refresh {
            await miffDirectory.refresh()
        }
        let sleeper = self.sleeper
        return FeedViewModel(
            dependencies: FeedDependencies(
                directory: miffDirectory,
                repository: repo,
                services: FeedServices(statuses: statuses, positions: FakePositionReader(), refresher: FakeFeedRefresher()),
                network: network,
                awaiting: AwaitingAgreement(),
                currentUid: { "me" },
                clock: { feedNow },
                sleep: { millis in await sleeper.sleep(millis) },
                timings: FeedTimings()
            )
        )
    }

    /// Le Feed est à l'écran : la pop-in suit le réseau.
    private func show(_ model: FeedViewModel) -> Task<Void, Never> {
        let task = Task {
            await model.watchConnectivity()
        }
        tasks.append(task)
        return task
    }

    /// Sans réseau et rien à montrer : la petite grâce de 1,5 s passe, puis la pop-in est là.
    private func popinAppears(_ model: FeedViewModel) async {
        await sleeper.elapse(1_500)
        await waitUntil { model.offlineRetry.isShown }
    }

    private func myMiffsCalls() -> Int {
        repo.calls.filter { $0 == "myMiffs" }.count
    }

    private func cachedSnapshot() -> MiffSnapshot {
        MiffSnapshot(miff: testMiffDetails(), members: [feedMember("a", first: "Thomas"), feedMember("me", first: "Camille")], places: [], fromCache: true)
    }

    // MARK: - Rien à montrer

    func testOfflineWithNothingKnownAboutTheMiffTheSkeletonGetsAPopinInsteadOfSpinningForever() async {
        let model = await makeModel()
        model.piece = .loading(testMiffId)
        XCTAssertEqual(model.uiState, .loading)

        _ = show(model)
        await waitUntil { self.sleeper.pendingFor(1_500) == 1 }
        XCTAssertFalse(model.offlineRetry.isShown, "pas de clignotement : une courte grâce d'abord")
        await popinAppears(model)

        XCTAssertTrue(model.offlineRetry.isShown)
    }

    func testOfflineBeforeTheMiffsAreEvenReadThePopinComesToo() async {
        let model = await makeModel(refresh: false)
        XCTAssertEqual(model.directoryState, .loading)

        _ = show(model)
        await popinAppears(model)

        XCTAssertTrue(model.offlineRetry.isShown)
    }

    func testOfflineWithUnreadableMiffsThePopinComesAndGoesWhenTheNetworkIsBack() async {
        let model = await makeModel(directory: .unavailable)
        XCTAssertEqual(model.directoryState, .unavailable)
        _ = show(model)
        await waitUntil { model.offlineRetry.isShown }
        XCTAssertTrue(model.offlineRetry.isShown)

        network.online = true
        await waitUntil { !model.offlineRetry.isShown }

        XCTAssertFalse(model.offlineRetry.isShown)
        XCTAssertEqual(model.directoryState, .unavailable, "l'état illisible garde son propre message et son « Réessayer »")
    }

    func testOnlineAndSlowThePopinComesAfterTheShortDelayOnly() async {
        network.online = true
        let model = await makeModel()
        model.piece = .loading(testMiffId)
        _ = show(model)
        await waitUntil { self.sleeper.pendingFor(8_000) == 1 }
        XCTAssertFalse(model.offlineRetry.isShown, "un chargement normal n'affiche pas la pop-in")

        sleeper.fire(8_000)
        await waitUntil { model.offlineRetry.isShown }

        XCTAssertTrue(model.offlineRetry.isShown)
    }

    func testDataArrivingMakesThePopinGoAway() async {
        let model = await makeModel()
        model.piece = .loading(testMiffId)
        _ = show(model)
        await popinAppears(model)

        model.piece = .data(testMiffId, cachedSnapshot())

        XCTAssertFalse(model.offlineRetry.isShown)
    }

    // MARK: - Avec du contenu en cache : comportement inchangé

    func testWithCachedContentOfflineTheFeedKeepsTheOldBehaviourAndNoPopin() async {
        let model = await makeModel()
        model.offline = true
        model.piece = .data(testMiffId, cachedSnapshot())
        model.statusView = .loaded(byUid: [:], fromCache: true)

        _ = show(model)
        await sleeper.settle()

        XCTAssertFalse(model.offlineRetry.isShown)
        guard case .ready(let content) = model.uiState else {
            return XCTFail("le contenu en cache est montré")
        }
        let hasOfflineBanner = content.banners.contains { banner in
            if case .offline = banner {
                return true
            }
            return false
        }
        XCTAssertTrue(hasOfflineBanner, "« Dernières informations de… » reste la mention hors connexion")
    }

    func testTheMiffKnownButTheStatusesStillWaitingShowsNoPopin() async {
        let model = await makeModel()
        model.piece = .data(testMiffId, cachedSnapshot())
        XCTAssertEqual(model.statusView, .waiting)

        _ = show(model)
        await sleeper.settle()

        XCTAssertFalse(model.offlineRetry.isShown, "la Miff est connue : le squelette attend les statuts (15 s au plus), sans pop-in qui disparaîtrait aussitôt")
    }

    // MARK: - Réessayer et l'essai automatique

    func testRetryRereadsTheMiffsAndRelaunchesTheListeningOnlyWhenTheNetworkIsThere() async {
        network.online = true
        let model = await makeModel()
        model.piece = .loading(testMiffId)
        _ = show(model)
        await waitUntil { self.sleeper.pendingFor(8_000) == 1 }
        sleeper.fire(8_000)
        await waitUntil { model.offlineRetry.isShown }
        let before = myMiffsCalls()

        model.offlineRetry.retryNow()

        XCTAssertEqual(model.restarts, 1, "l'écran relance son observation")
        await waitUntil { self.myMiffsCalls() == before + 1 }
        XCTAssertEqual(myMiffsCalls(), before + 1)
    }

    func testRetryOfflineReadsNothing() async {
        let model = await makeModel()
        model.piece = .loading(testMiffId)
        _ = show(model)
        await popinAppears(model)
        let before = myMiffsCalls()

        model.offlineRetry.retryNow()
        await sleeper.settle()

        XCTAssertEqual(model.restarts, 0)
        XCTAssertEqual(myMiffsCalls(), before, "pas de réseau : aucune lecture")
    }

    func testTheAutomaticTryEveryTenSecondsNeverReadsWhileOffline() async {
        let model = await makeModel()
        model.piece = .loading(testMiffId)
        _ = show(model)
        await popinAppears(model)
        await waitUntil { self.sleeper.pendingFor(1_000) == 1 }
        let before = myMiffsCalls()

        // Quatre tours de dix secondes : sans réseau, la minuterie tourne sans rien lire.
        await sleeper.elapse(1_000, times: 40)
        await sleeper.settle()

        XCTAssertEqual(model.restarts, 0)
        XCTAssertEqual(myMiffsCalls(), before)
        XCTAssertTrue(model.offlineRetry.isShown)
    }

    func testAnUnreadableMiffsListOnlineShowsNeitherPopinNorTimer() async {
        network.online = true
        let model = await makeModel(directory: .unavailable)
        // Rien n'est lisible : l'état « échec » ne montre la pop-in que sans réseau (il garde son message et son « Réessayer »).
        _ = show(model)
        await sleeper.settle()

        XCTAssertFalse(model.offlineRetry.isShown)
        XCTAssertEqual(sleeper.pendingFor(1_000), 0)
    }

    func testTheAutomaticTryOnlineLeavesTheListenersAloneForAMiffThatIsKnown() async {
        network.online = true
        // Une Miff connue dont l'écoute n'a rien dit : l'essai automatique ne relance pas les écoutes (elles se rattachent seules).
        let model = await makeModel()
        model.piece = .loading(testMiffId)
        _ = show(model)
        await waitUntil { self.sleeper.pendingFor(8_000) == 1 }
        sleeper.fire(8_000)
        await waitUntil { model.offlineRetry.isShown }
        let before = myMiffsCalls()

        await sleeper.elapse(1_000, times: 10)
        await waitUntil { self.sleeper.pendingFor(1_000) == 1 }
        await sleeper.settle()

        XCTAssertEqual(model.restarts, 0)
        XCTAssertEqual(myMiffsCalls(), before, "les écoutes se rattachent seules : aucune lecture de plus")
    }

    func testTheAutomaticTryOnlineRereadsTheMiffsThatWereNeverRead() async {
        network.online = true
        let model = await makeModel(refresh: false)
        XCTAssertEqual(model.directoryState, .loading)
        _ = show(model)
        await waitUntil { self.sleeper.pendingFor(8_000) == 1 }
        sleeper.fire(8_000)
        await waitUntil { model.offlineRetry.isShown }
        let before = myMiffsCalls()

        await sleeper.elapse(1_000, times: 10)
        await waitUntil { self.myMiffsCalls() == before + 1 }

        XCTAssertEqual(myMiffsCalls(), before + 1)
    }

    func testTheNetworkComingBackDuringAReadStartedOfflineReplaysItOnce() async {
        let gate = ManualSleeper()
        let model = await makeModel()
        model.piece = .loading(testMiffId)
        _ = show(model)
        await popinAppears(model)
        let before = myMiffsCalls()
        repo.myMiffsGate = { await gate.sleep(-1) }

        // Une lecture commence sans réseau et attend ; le réseau revient pendant ce temps.
        model.retry()
        await waitUntil { self.myMiffsCalls() == before + 1 }
        network.online = true
        await waitUntil { !model.offlineRetry.isShown }
        repo.myMiffsGate = nil
        gate.fire()

        await waitUntil { self.myMiffsCalls() == before + 2 }
        await sleeper.settle()
        XCTAssertEqual(myMiffsCalls(), before + 2, "rejouée UNE fois, pas davantage")
    }

    func testAReadStartedOnlineIsNeverReplayedByASecondRequest() async {
        network.online = true
        let gate = ManualSleeper()
        let model = await makeModel()
        model.piece = .loading(testMiffId)
        let before = myMiffsCalls()
        repo.myMiffsGate = { await gate.sleep(-1) }

        model.retry()
        await waitUntil { self.myMiffsCalls() == before + 1 }
        model.retry()
        repo.myMiffsGate = nil
        gate.fire()
        await sleeper.settle()

        XCTAssertEqual(myMiffsCalls(), before + 1, "une lecture à la fois, et pas de rejeu quand elle a commencé avec du réseau")
    }

    func testClosingThePopinKeepsTheTriesGoingAndTheFeedSaysItInItsPage() async {
        network.online = true
        let model = await makeModel(refresh: false)
        _ = show(model)
        await waitUntil { self.sleeper.pendingFor(8_000) == 1 }
        sleeper.fire(8_000)
        await waitUntil { model.offlineRetry.isShown }
        let before = myMiffsCalls()

        model.offlineRetry.dismiss()
        await sleeper.elapse(1_000, times: 10)
        await waitUntil { self.myMiffsCalls() == before + 1 }

        XCTAssertTrue(model.offlineRetry.isInlineVisible)
        XCTAssertFalse(model.offlineRetry.isPopinVisible)
        XCTAssertEqual(myMiffsCalls(), before + 1, "l'essai automatique continue derrière la page")
    }

    func testTheNetworkComingBackRelaunchesEverythingOnce() async {
        let model = await makeModel()
        model.piece = .loading(testMiffId)
        _ = show(model)
        await popinAppears(model)

        network.online = true
        await waitUntil { !model.offlineRetry.isShown }

        XCTAssertFalse(model.offlineRetry.isShown, "la pop-in disparaît d'elle-même")
        XCTAssertEqual(model.restarts, 1)
    }

    func testLeavingTheFeedRemovesThePopinAndStopsTheTries() async {
        let model = await makeModel()
        model.piece = .loading(testMiffId)
        let task = show(model)
        await popinAppears(model)

        task.cancel()
        await task.value

        XCTAssertFalse(model.offlineRetry.isShown, "onglet quitté ou app en arrière-plan : plus de pop-in")
        network.online = true
        sleeper.fire()
        await sleeper.settle()
        XCTAssertEqual(model.restarts, 0, "plus aucun essai après la sortie de l'écran")
    }
}

// swiftlint:enable line_length
