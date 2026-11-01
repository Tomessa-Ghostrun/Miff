import Foundation

/// Le résultat d'un appel à Firestore, réduit à ce que la logique des Miffs sait en faire : une valeur, ou un échec sans
/// message (jamais l'erreur elle-même : elle peut citer un chemin, donc un code ou un nom).
enum Guarded<Value> {
    case value(Value)
    /// `lostAccess` : le serveur a refusé l'accès (règles), la SEULE réponse qui peut vouloir dire « retiré ».
    case failed(MiffFailure, lostAccess: Bool)
}

/// Exécute `call` avec un délai maximal et range tout échec dans une `MiffFailure`. Un délai dépassé est « pas de connexion ».
func guardedCall<Value>(
    timeoutSeconds: TimeInterval,
    _ call: @escaping () async throws -> Value
) async -> Guarded<Value> {
    do {
        let value = try await timedOperation(seconds: timeoutSeconds, call)
        return .value(value)
    } catch {
        return .failed(interpretMiffError(error), lostAccess: isAccessLostError(error))
    }
}

/// Les règles du dépôt qui ne viennent pas de Firestore : d'où vient l'identifiant d'une nouvelle Miff, d'où vient un
/// nouveau code, quelle heure il est, combien de temps on attend. Réglables par les tests.
struct MiffRepositoryRules {
    var newMiffId: () -> String
    var newCode: () -> String = { JoinCode.generate() }
    /// L'heure du mur en millisecondes depuis 1970 (la fin d'un abonnement est une vraie date).
    var epochMillis: () -> Int = { Int(Date().timeIntervalSince1970 * 1000) }
    var timeoutSeconds: TimeInterval = 20
}
