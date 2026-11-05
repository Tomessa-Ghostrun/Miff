import Foundation

/// Diffuse une valeur à tous ceux qui l'écoutent, sous forme de flux asynchrones (un flux par écoute). Sert à dire l'état de
/// la suppression d'une Miff à l'écran qui la regarde, quel que soit le fil qui l'a changé : un verrou protège la liste des
/// écoutes. Un flux s'arrête (et sort de la liste) quand celui qui l'écoute l'abandonne.
///
/// `replay` : vrai, chaque nouveau flux reçoit d'abord la dernière valeur envoyée (ou la valeur de départ) ; faux, il ne reçoit
/// que ce qui est envoyé après son ouverture (des événements, pas un état).
final class Broadcaster<Value> {
    private let lock = NSLock()
    private let replay: Bool
    private var latest: Value?
    private var continuations: [UUID: AsyncStream<Value>.Continuation] = [:]

    init(replay: Bool, initial: Value? = nil) {
        self.replay = replay
        latest = initial
    }

    func stream() -> AsyncStream<Value> {
        AsyncStream { continuation in
            let identifier = UUID()
            self.lock.lock()
            self.continuations[identifier] = continuation
            let current = self.replay ? self.latest : nil
            self.lock.unlock()
            if let current {
                continuation.yield(current)
            }
            continuation.onTermination = { [weak self] _ in
                self?.remove(identifier)
            }
        }
    }

    func send(_ value: Value) {
        lock.lock()
        latest = value
        let listeners = Array(continuations.values)
        lock.unlock()
        for continuation in listeners {
            continuation.yield(value)
        }
    }

    private func remove(_ identifier: UUID) {
        lock.lock()
        continuations[identifier] = nil
        lock.unlock()
    }
}
