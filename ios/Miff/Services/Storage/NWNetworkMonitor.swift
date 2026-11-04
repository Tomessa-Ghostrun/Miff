import Foundation
import Network

/// Y a-t-il un réseau ? Sert à répondre « pas de connexion » tout de suite au lieu d'attendre un long échec. Le moniteur
/// du système met l'état à jour en arrière-plan ; avant sa première mise à jour (quelques millisecondes), on suppose qu'il
/// y en a un : Firebase répondra de toute façon par une vraie erreur si ce n'est pas le cas.
final class NWNetworkMonitor: NetworkMonitor {
    private let monitor = NWPathMonitor()
    private let queue = DispatchQueue(label: "com.tomessa.miff.network")
    private let lock = NSLock()
    private var online = true
    private var listeners: [UUID: AsyncStream<Bool>.Continuation] = [:]

    init() {
        monitor.pathUpdateHandler = { [weak self] path in
            self?.update(path.status == .satisfied)
        }
        monitor.start(queue: queue)
    }

    deinit {
        monitor.cancel()
    }

    func isOnline() -> Bool {
        lock.lock()
        defer { lock.unlock() }
        return online
    }

    func onlineChanges() -> AsyncStream<Bool> {
        AsyncStream { continuation in
            let identifier = UUID()
            self.lock.lock()
            self.listeners[identifier] = continuation
            let current = self.online
            self.lock.unlock()
            continuation.yield(current)
            continuation.onTermination = { [weak self] _ in
                self?.removeListener(identifier)
            }
        }
    }

    private func removeListener(_ identifier: UUID) {
        lock.lock()
        listeners[identifier] = nil
        lock.unlock()
    }

    private func update(_ value: Bool) {
        lock.lock()
        online = value
        let current = Array(listeners.values)
        lock.unlock()
        for continuation in current {
            continuation.yield(value)
        }
    }
}
