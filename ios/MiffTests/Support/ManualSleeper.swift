import Foundation

/// Une attente que le test commande : chaque `sleep` reste suspendu jusqu'à `fire()`. Rien n'attend pour de vrai, et le temps ne passe
/// que quand le test le dit (la minuterie de 10 s de la pop-in « Pas de connexion » se teste sans attendre 10 secondes). Une attente dont
/// la tâche est annulée se termine d'elle-même, comme `Task.sleep`.
@MainActor
final class ManualSleeper {
    /// Les durées demandées, dans l'ordre.
    private(set) var requested: [Int] = []
    private var nextId = 0
    private struct Waiter {
        let id: Int
        let millis: Int
        let continuation: CheckedContinuation<Void, Never>
    }

    private var waiters: [Waiter] = []

    /// Combien d'attentes sont en cours, au total ou pour une durée donnée.
    var pendingTotal: Int {
        waiters.count
    }

    func pendingFor(_ millis: Int) -> Int {
        waiters.filter { $0.millis == millis }.count
    }

    func sleep(_ millis: Int) async {
        requested.append(millis)
        let id = nextId
        nextId += 1
        await withTaskCancellationHandler {
            await withCheckedContinuation { continuation in
                if Task.isCancelled {
                    continuation.resume()
                } else {
                    waiters.append(Waiter(id: id, millis: millis, continuation: continuation))
                }
            }
        } onCancel: {
            Task { @MainActor in
                self.release(id)
            }
        }
    }

    private func release(_ id: Int) {
        guard let index = waiters.firstIndex(where: { $0.id == id }) else {
            return
        }
        let waiter = waiters.remove(at: index)
        waiter.continuation.resume()
    }

    /// Attend qu'une attente de cette durée soit en cours (le code testé l'a demandée), puis la termine : « ces N millisecondes passent ».
    /// Sans attente réelle : le test cède la main au code testé jusqu'à ce qu'il ait demandé l'attente.
    func elapse(_ millis: Int) async {
        for _ in 0..<5_000 where pendingFor(millis) == 0 {
            await Task.yield()
        }
        fire(millis)
    }

    /// `count` attentes de cette durée passent l'une après l'autre (les pas du compte à rebours de la pop-in).
    func elapse(_ millis: Int, times count: Int) async {
        for _ in 0..<count {
            await elapse(millis)
        }
    }

    /// Cède la main au code testé assez longtemps pour qu'il ait fait tout ce qu'il pouvait faire : sert à prouver qu'il ne se passe RIEN,
    /// sans attendre de temps réel.
    func settle() async {
        for _ in 0..<200 {
            await Task.yield()
        }
    }

    /// Termine toutes les attentes en cours.
    func fire() {
        let current = waiters
        waiters = []
        for waiter in current {
            waiter.continuation.resume()
        }
    }

    /// Termine seulement les attentes de cette durée.
    func fire(_ millis: Int) {
        let current = waiters.filter { $0.millis == millis }
        waiters = waiters.filter { $0.millis != millis }
        for waiter in current {
            waiter.continuation.resume()
        }
    }
}
