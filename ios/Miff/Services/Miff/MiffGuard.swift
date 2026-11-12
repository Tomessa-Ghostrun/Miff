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
    /// L'identifiant de la session de cet appareil (`DeviceSession.currentDeviceId`), SEULEMENT si le serveur l'a confirmé (SESS-3).
    /// Il part dans chaque statut écrit par un lot d'entrée. Par défaut nil : sans session confirmée, AUCUN lot qui écrit un
    /// statut ne part (le refus est la bonne réponse par défaut).
    var deviceId: () -> String? = { nil }
    /// Relit la session auprès du serveur : après le refus d'un lot qui écrit un statut, ou quand aucune session n'est confirmée.
    /// Remplacée par un autre appareil : la déconnexion forcée est lancée par le gestionnaire de session.
    var recheckSession: () async -> Void = {}
    /// Le partage de la personne a changé (entrée dans une Miff, arrêt ou reprise du partage, départ, perte d'accès) : le suivi des lieux
    /// relit les Miffs et les lieux (LOC-4b). Appelé après une écriture CONFIRMÉE par le serveur, depuis n'importe quel fil.
    var onSharingChanged: () -> Void = {}
    /// Le système dit-il qu'un réseau est là ? Faux : la lecture des Miffs de la personne va droit au cache du téléphone au lieu
    /// d'attendre, l'une après l'autre, la réponse (ou le délai de 20 s) d'un serveur injoignable (cause du « Feed qui charge à
    /// l'infini » sans réseau). Rien n'est jamais créé, rejoint ni nettoyé à partir de ce cache. Par défaut vrai (les tests).
    var isOnline: () -> Bool = { true }

    /// La personne vient de REPRENDRE le partage de cette Miff (à la place de `onSharingChanged`) : le suivi remet sa détection à zéro pour
    /// elle, PUIS relit les Miffs. Nil : `onSharingChanged` seul.
    var onSharingResumed: ((String) -> Void)?
}
