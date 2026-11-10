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

    /// Sans réseau et rien à montrer : la petite grâce de 2 s passe, puis la pop-in est là.
    private func popinAppears(_ model: FeedViewModel) async {
        await sleeper.elapse(2_000)
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
        await waitUntil { self.sleeper.pendingFor(2_000) == 1 }
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
        try? await Task.sleep(nanoseconds: 50_000_000)

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
        try? await Task.sleep(nanoseconds: 50_000_000)

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
        try? await Task.sleep(nanoseconds: 50_000_000)

        XCTAssertEqual(model.restarts, 0)
        XCTAssertEqual(myMiffsCalls(), before, "pas de réseau : aucune lecture")
    }

    func testTheAutomaticTryEveryTenSecondsNeverReadsWhileOffline() async {
        let model = await makeModel()
        model.piece = .loading(testMiffId)
        _ = show(model)
        await popinAppears(model)
        await waitUntil { self.sleeper.pendingFor(10_000) == 1 }
        let before = myMiffsCalls()

        for round in 1...3 {
            sleeper.fire(10_000)
            await waitUntil { self.sleeper.requested.filter { $0 == 10_000 }.count == round + 1 }
        }

        XCTAssertEqual(model.restarts, 0)
        XCTAssertEqual(myMiffsCalls(), before)
        XCTAssertTrue(model.offlineRetry.isShown)
    }

    func testTheAutomaticTryOnlineKeepsTheListenersAndOnlyReadsTheMiffsThatWereNeverRead() async {
        network.online = true
        let model = await makeModel(directory: .unavailable)
        // Rien n'est lisible : l'état « échec » ne montre la pop-in que sans réseau, donc ni pop-in ni essai ici.
        _ = show(model)
        try? await Task.sleep(nanoseconds: 50_000_000)
        XCTAssertFalse(model.offlineRetry.isShown)
        XCTAssertEqual(sleeper.pendingFor(10_000), 0)

        // Une Miff connue dont l'écoute n'a rien dit : l'essai automatique ne relance pas les écoutes (elles se rattachent seules).
        let other = await makeModel()
        other.piece = .loading(testMiffId)
        _ = show(other)
        await waitUntil { self.sleeper.pendingFor(8_000) == 1 }
        sleeper.fire(8_000)
        await waitUntil { self.sleeper.pendingFor(10_000) == 1 }
        sleeper.fire(10_000)
        await waitUntil { self.sleeper.requested.filter { $0 == 10_000 }.count == 2 }

        XCTAssertEqual(other.restarts, 0)
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
        try? await Task.sleep(nanoseconds: 50_000_000)
        XCTAssertEqual(model.restarts, 0, "plus aucun essai après la sortie de l'écran")
    }
}
