// Les assertions citent des cas entiers : leurs lignes dépassent la limite.
// swiftlint:disable line_length
import XCTest
@testable import Miff

// La pop-in « Pas de connexion » et sa reconnexion automatique, sans réseau ni horloge réels : le réseau est un double, et les attentes
// (2 s de grâce hors connexion, 8 s « aucune donnée », 10 s entre deux essais, 0,8 s de « Réessayer ») sont commandées par le test
// (`ManualSleeper`).
@MainActor
final class OfflineRetryModelTests: XCTestCase {
    private let network = FakeNetwork(online: false)
    private let sleeper = ManualSleeper()
    private var retries: [OfflineRetryKind] = []
    private var tasks: [Task<Void, Never>] = []

    override func tearDown() async throws {
        // Rien ne doit rester en cours d'un test à l'autre : l'écran est quitté, puis les attentes en suspens sont libérées.
        tasks.forEach { $0.cancel() }
        sleeper.fire()
        for task in tasks {
            await task.value
        }
        tasks = []
        try await super.tearDown()
    }

    private func makeModel() -> OfflineRetryModel {
        let sleeper = self.sleeper
        let model = OfflineRetryModel(
            network: network,
            sleep: { millis in await sleeper.sleep(millis) },
            timings: OfflinePopinTimings()
        )
        model.onRetry = { [weak self] kind in
            self?.retries.append(kind)
        }
        return model
    }

    /// L'écran est visible : le suivi du réseau tourne.
    private func show(_ model: OfflineRetryModel) -> Task<Void, Never> {
        let task = Task {
            await model.watch()
        }
        tasks.append(task)
        return task
    }

    /// Laisse passer un peu de temps réel pour prouver qu'il ne se passe RIEN.
    private func settle() async {
        try? await Task.sleep(nanoseconds: 50_000_000)
    }

    /// Un écran sans réseau et sans rien à montrer, après sa petite grâce de 2 s : la pop-in est là.
    private func offlineAndShown(_ model: OfflineRetryModel) async -> Task<Void, Never> {
        model.setNeed(.loading)
        let task = show(model)
        await sleeper.elapse(2_000)
        await waitUntil { model.isShown }
        return task
    }

    /// Un écran avec réseau, rien ne vient, après les 8 s : la pop-in est là.
    private func onlineAndShown(_ model: OfflineRetryModel) async -> Task<Void, Never> {
        network.online = true
        model.setNeed(.loading)
        let task = show(model)
        await sleeper.elapse(8_000)
        await waitUntil { model.isShown }
        return task
    }

    // MARK: - Quand la pop-in vient

    func testWithoutNetworkAndNothingToShowThePopinComesAfterAShortGraceNotBefore() async {
        let model = makeModel()
        model.setNeed(.loading)
        _ = show(model)
        await waitUntil { self.sleeper.pendingFor(2_000) == 1 }
        XCTAssertFalse(model.isShown, "un lancement hors connexion qui trouve vite ses informations ne voit jamais la pop-in")
        XCTAssertEqual(sleeper.pendingFor(8_000), 0)

        sleeper.fire(2_000)
        await waitUntil { model.isShown }

        XCTAssertTrue(model.isShown)
    }

    func testInformationFoundDuringTheGraceMeansNoPopinAtAll() async {
        let model = makeModel()
        model.setNeed(.loading)
        _ = show(model)
        await waitUntil { self.sleeper.pendingFor(2_000) == 1 }

        model.setNeed(.none)
        sleeper.fire(2_000)
        await settle()

        XCTAssertFalse(model.isShown)
    }

    func testWithCachedContentThereIsNoPopinEvenOffline() async {
        let model = makeModel()
        model.setNeed(.none)
        _ = show(model)
        await settle()

        XCTAssertFalse(model.isShown)
        XCTAssertEqual(sleeper.pendingTotal, 0, "aucune minuterie quand il y a quelque chose à montrer")
    }

    func testAFailedReadOnlyShowsThePopinWhenTheNetworkIsMissingAndAtOnce() async {
        let model = makeModel()
        model.setNeed(.failed)
        _ = show(model)
        await waitUntil { model.isShown }
        XCTAssertTrue(model.isShown, "il n'y a rien à attendre : pas de grâce")

        // Le réseau revient : l'échec garde son propre message (et ses boutons), la pop-in s'en va.
        network.online = true
        await waitUntil { !model.isShown }
        XCTAssertFalse(model.isShown)
    }

    func testOnlineButNothingArrivesTheNoDataDelayBringsThePopin() async {
        network.online = true
        let model = makeModel()
        model.setNeed(.loading)
        _ = show(model)
        await waitUntil { self.sleeper.pendingFor(8_000) == 1 }
        XCTAssertFalse(model.isShown, "pas avant le délai : un chargement normal n'affiche rien")

        sleeper.fire(8_000)
        await waitUntil { model.isShown }
        XCTAssertTrue(model.isShown)
    }

    func testTheNoDataDelayIsCancelledWhenContentArrives() async {
        network.online = true
        let model = makeModel()
        model.setNeed(.loading)
        _ = show(model)
        await waitUntil { self.sleeper.pendingFor(8_000) == 1 }

        model.setNeed(.none)
        sleeper.fire(8_000)
        await settle()

        XCTAssertFalse(model.isShown)
    }

    func testContentArrivingMakesThePopinDisappear() async {
        let model = makeModel()
        _ = await offlineAndShown(model)

        model.setNeed(.none)

        XCTAssertFalse(model.isShown)
    }

    // MARK: - L'essai automatique

    func testOfflineTheTenSecondTryNeverReadsAnything() async {
        let model = makeModel()
        _ = await offlineAndShown(model)
        await waitUntil { self.sleeper.pendingFor(10_000) == 1 }

        for round in 1...3 {
            sleeper.fire(10_000)
            // La boucle reprend sa pause de 10 s une fois l'essai fait (et non fait : pas de réseau).
            await waitUntil { self.sleeper.requested.filter { $0 == 10_000 }.count == round + 1 }
        }

        XCTAssertEqual(retries, [], "pas de réseau : aucune lecture, jamais de boucle contre un serveur injoignable")
        XCTAssertTrue(model.isShown)
    }

    func testOnlineWithoutDataTheTryHappensEveryTenSecondsWhileThePopinIsShown() async {
        let model = makeModel()
        _ = await onlineAndShown(model)
        await waitUntil { self.sleeper.pendingFor(10_000) == 1 }
        XCTAssertEqual(sleeper.requested.last, 10_000, "le premier essai est dans 10 s")

        sleeper.fire(10_000)
        await waitUntil { self.retries.count == 1 }
        XCTAssertEqual(retries, [.automatic])

        await waitUntil { self.sleeper.pendingFor(10_000) == 1 }
        sleeper.fire(10_000)
        await waitUntil { self.retries.count == 2 }
        XCTAssertEqual(retries, [.automatic, .automatic])
    }

    func testThePopinNeverStacksOrDuplicatesItsTimer() async {
        let model = makeModel()
        _ = await offlineAndShown(model)
        await waitUntil { self.sleeper.pendingFor(10_000) == 1 }

        model.setNeed(.failed)
        model.setNeed(.loading)
        model.setNeed(.failed)
        await settle()

        XCTAssertTrue(model.isShown)
        XCTAssertEqual(sleeper.pendingFor(10_000), 1, "une seule minuterie d'essai, même si l'écran change plusieurs fois d'état")
    }

    // MARK: - Le retour du réseau

    func testWhenTheNetworkComesBackThePopinDisappearsAndTheScreenReadsOnce() async {
        let model = makeModel()
        _ = await offlineAndShown(model)

        network.online = true
        await waitUntil { !model.isShown }

        XCTAssertFalse(model.isShown, "elle disparaît d'elle-même")
        XCTAssertEqual(retries, [.networkBack], "une seule relecture, tout de suite, sans attendre les 10 s")
        // Les 10 s ne comptent plus : l'essai automatique est arrêté.
        sleeper.fire(10_000)
        await settle()
        XCTAssertEqual(retries, [.networkBack])
    }

    func testIfNothingArrivesAfterTheNetworkCameBackTheDelayStartsAgain() async {
        let model = makeModel()
        _ = await offlineAndShown(model)
        network.online = true
        await waitUntil { self.sleeper.pendingFor(8_000) == 1 }

        XCTAssertFalse(model.isShown)
        sleeper.fire(8_000)
        await waitUntil { model.isShown }
        XCTAssertTrue(model.isShown)
    }

    func testTheNetworkGoingAwayWhileWaitingBringsThePopinAfterTheShortGrace() async {
        network.online = true
        let model = makeModel()
        model.setNeed(.loading)
        _ = show(model)
        await waitUntil { self.sleeper.pendingFor(8_000) == 1 }

        network.online = false
        await waitUntil { self.sleeper.pendingFor(2_000) == 1 }
        XCTAssertFalse(model.isShown)
        sleeper.fire(2_000)
        await waitUntil { model.isShown }

        XCTAssertTrue(model.isShown)
        XCTAssertEqual(retries, [])
    }

    func testThePopinAlreadyShownStaysWhenTheNetworkGoesAwayToo() async {
        let model = makeModel()
        _ = await onlineAndShown(model)

        network.online = false
        await settle()

        XCTAssertTrue(model.isShown, "pas de clignotement : elle ne s'efface pas pour revenir deux secondes plus tard")
    }

    // MARK: - « Réessayer »

    func testRetryNowReadsAtOnceWhenTheNetworkIsThere() async {
        let model = makeModel()
        _ = await onlineAndShown(model)

        model.retryNow()

        XCTAssertEqual(retries, [.manual])
        XCTAssertTrue(model.isRetrying, "le bouton montre qu'il a été pris en compte")
        await sleeper.elapse(800)
        await waitUntil { !model.isRetrying }
        XCTAssertFalse(model.isRetrying)
    }

    func testRetryNowWithoutNetworkReadsNothingButAnswers() async {
        let model = makeModel()
        _ = await offlineAndShown(model)

        model.retryNow()

        XCTAssertEqual(retries, [], "pas de réseau : aucune lecture")
        XCTAssertTrue(model.isRetrying)
        await sleeper.elapse(800)
        await waitUntil { !model.isRetrying }
        XCTAssertFalse(model.isRetrying)
        XCTAssertTrue(model.isShown, "la pop-in reste tant que la connexion n'est pas revenue")
    }

    func testTwoQuickTapsOnRetryReadOnce() async {
        let model = makeModel()
        _ = await onlineAndShown(model)

        model.retryNow()
        model.retryNow()

        XCTAssertEqual(retries, [.manual])
    }

    // MARK: - Quitter l'écran

    func testLeavingTheScreenRemovesThePopinAndStopsTheTries() async {
        let model = makeModel()
        let task = await onlineAndShown(model)
        await waitUntil { self.sleeper.pendingFor(10_000) == 1 }
        XCTAssertTrue(model.isShown)

        task.cancel()
        await task.value
        XCTAssertFalse(model.isShown, "l'onglet change ou l'app part : plus de pop-in")
        sleeper.fire()
        await settle()

        XCTAssertEqual(retries, [], "plus aucun essai après la sortie de l'écran")
    }

    func testComingBackToTheScreenStartsFresh() async {
        let model = makeModel()
        let first = await offlineAndShown(model)
        first.cancel()
        await first.value
        XCTAssertFalse(model.isShown)

        _ = show(model)
        await sleeper.elapse(2_000)
        await waitUntil { model.isShown }

        XCTAssertTrue(model.isShown)
    }
}
