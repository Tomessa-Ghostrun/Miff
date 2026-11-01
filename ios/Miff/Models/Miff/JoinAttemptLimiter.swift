import Foundation

/// Une aide de l'interface, PAS une protection (ticket Miff §5.3, critère J5) : après 5 échecs de suite, la personne est
/// invitée à attendre une minute. Le compte repart de zéro après un succès, ou quand le dernier échec date de plus de 10
/// minutes. La vraie protection est l'entropie du code et App Check ; un client modifié saute cette pause (risque documenté
/// K8).
///
/// Gardé en mémoire pour la durée du processus : rien sur un code n'est conservé.
final class JoinAttemptLimiter {
    static let maxFailures = 5
    static let pauseMillis = 60_000
    static let forgetAfterMillis = 10 * 60_000

    private let nowMillis: () -> Int
    private var failures = 0
    private var lastFailureAt = 0
    private var pausedUntil = 0

    init(nowMillis: @escaping () -> Int) {
        self.nowMillis = nowMillis
    }

    /// Millisecondes restantes de la pause, 0 quand la personne peut essayer.
    func remainingPauseMillis() -> Int {
        max(pausedUntil - nowMillis(), 0)
    }

    func isPaused() -> Bool {
        remainingPauseMillis() > 0
    }

    func recordFailure() {
        let now = nowMillis()
        if failures > 0 && now - lastFailureAt >= Self.forgetAfterMillis {
            failures = 0
        }
        failures += 1
        lastFailureAt = now
        if failures >= Self.maxFailures {
            pausedUntil = now + Self.pauseMillis
            // La pause est la sanction : la série suivante repart de zéro une fois qu'elle est finie.
            failures = 0
        }
    }

    func recordSuccess() {
        failures = 0
        pausedUntil = 0
    }
}
