import XCTest
@testable import Miff

/// Le socle des tests de la pop-in « Pas de connexion » : un réseau et des attentes que le test commande (`ManualSleeper`), sans temps réel.
/// Les assertions des classes qui en héritent citent des cas entiers : leurs lignes dépassent la limite.
@MainActor
class OfflineRetryTestCase: XCTestCase {
    let network = FakeNetwork(online: false)
    let sleeper = ManualSleeper()
    var retries: [OfflineRetryKind] = []
    var tasks: [Task<Void, Never>] = []

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

    func makeModel() -> OfflineRetryModel {
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
    func show(_ model: OfflineRetryModel) -> Task<Void, Never> {
        let task = Task {
            await model.watch()
        }
        tasks.append(task)
        return task
    }

    /// Un écran sans réseau et sans rien à montrer, après sa petite grâce de 1,5 s : la pop-in est là.
    func offlineAndShown(_ model: OfflineRetryModel) async -> Task<Void, Never> {
        model.setNeed(.loading)
        let task = show(model)
        await sleeper.elapse(1_500)
        await waitUntil { model.isShown }
        return task
    }

    /// Un écran avec réseau, rien ne vient, après les 8 s : la pop-in est là.
    func onlineAndShown(_ model: OfflineRetryModel) async -> Task<Void, Never> {
        network.online = true
        model.setNeed(.loading)
        let task = show(model)
        await sleeper.elapse(8_000)
        await waitUntil { model.isShown }
        return task
    }

    /// Les 10 secondes d'un essai automatique passent, une seconde à la fois (le compte à rebours avance d'un cran à chaque fois).
    func oneInterval() async {
        await sleeper.elapse(1_000, times: 10)
    }

    /// Les trois essais automatiques d'un réseau annoncé mais muet ont eu lieu : le plafond est atteint.
    func reachTheCap(_ model: OfflineRetryModel) async {
        for round in 1...3 {
            await oneInterval()
            await waitUntil { self.retries.count == round }
        }
        await waitUntil { model.secondsLeft == nil }
    }
}
