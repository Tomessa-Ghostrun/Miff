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

    private func update(_ value: Bool) {
        lock.lock()
        online = value
        lock.unlock()
    }
}
