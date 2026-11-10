import Foundation

/// Une attente que le test commande : chaque `sleep` reste suspendu jusqu'à `fire()`. Rien n'attend pour de vrai, et le temps ne passe
/// que quand le test le dit (la minuterie de 10 s de la pop-in « Pas de connexion » se teste sans attendre 10 secondes).
@MainActor
final class ManualSleeper {
    /// Les durées demandées, dans l'ordre.
    private(set) var requested: [Int] = []
    private var waiters: [(millis: Int, continuation: CheckedContinuation<Void, Never>)] = []

    /// Combien d'attentes sont en cours, au total ou pour une durée donnée.
    var pendingTotal: Int {
        waiters.count
    }

    func pendingFor(_ millis: Int) -> Int {
        waiters.filter { $0.millis == millis }.count
    }

    func sleep(_ millis: Int) async {
        requested.append(millis)
        await withCheckedContinuation { continuation in
            waiters.append((millis: millis, continuation: continuation))
        }
    }

    /// Attend qu'une attente de cette durée soit en cours (le code testé l'a demandée), puis la termine : « ces N millisecondes passent ».
    func elapse(_ millis: Int) async {
        for _ in 0..<300 where pendingFor(millis) == 0 {
            try? await Task.sleep(nanoseconds: 10_000_000)
        }
        fire(millis)
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
