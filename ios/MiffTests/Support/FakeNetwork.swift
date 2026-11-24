import Foundation
@testable import Miff

/// Le réseau des tests : en ligne ou non, et chaque changement est annoncé aux flux ouverts.
final class FakeNetwork: NetworkMonitor {
    /// Chaque changement est annoncé aux flux ouverts par `onlineChanges()`.
    var online: Bool {
        didSet {
            for continuation in continuations {
                continuation.yield(online)
            }
        }
    }

    private var continuations: [AsyncStream<Bool>.Continuation] = []

    init(online: Bool = true) {
        self.online = online
    }

    func isOnline() -> Bool {
        online
    }

    func onlineChanges() -> AsyncStream<Bool> {
        AsyncStream { continuation in
            self.continuations.append(continuation)
            continuation.yield(self.online)
        }
    }
}
