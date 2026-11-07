import Foundation

/// Une copie de `SessionManager.generation` lisible depuis n'importe quel fil (le moteur de suivi des lieux tourne hors du fil
/// principal, alors que le gestionnaire de session est isolé à l'acteur principal). Le gestionnaire la met à jour à chaque fin de
/// session ; le moteur s'en sert pour qu'un arrêt reste arrêté jusqu'à ce que la session change (revue I4 d'Android).
final class GenerationCounter {
    private let lock = NSLock()
    private var value = 0

    var current: Int {
        lock.lock()
        defer { lock.unlock() }
        return value
    }

    func set(_ newValue: Int) {
        lock.lock()
        value = newValue
        lock.unlock()
    }
}
