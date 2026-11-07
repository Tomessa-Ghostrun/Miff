import Foundation
@testable import Miff

// Les doubles en mémoire du Feed : l'écoute des statuts, la lecture d'une position, l'actualisation forcée. Aucun ne touche au réseau.

final class FakeStatusObserver: StatusObserver {
    private var continuations: [AsyncStream<StatusFeed>.Continuation] = []
    private var last: StatusFeed?
    /// Écoutes ouvertes en ce moment, et nombre d'écoutes attachées depuis le début.
    private(set) var active = 0
    private(set) var total = 0

    func observe(miffId: String) -> AsyncStream<StatusFeed> {
        AsyncStream { continuation in
            self.continuations.append(continuation)
            self.active += 1
            self.total += 1
            if let previous = self.last {
                continuation.yield(previous)
            }
            continuation.onTermination = { [weak self] _ in
                self?.active -= 1
            }
        }
    }

    func emit(_ byUid: [String: MemberStatus], fromCache: Bool = false) {
        let feed = StatusFeed.data(byUid: byUid, fromCache: fromCache)
        last = feed
        for continuation in continuations {
            continuation.yield(feed)
        }
    }

    /// Les règles refusent : l'écoute se termine (sur une erreur, Firestore ne la relance pas).
    func end() {
        last = nil
        let ending = continuations
        continuations = []
        for continuation in ending {
            continuation.yield(.ended)
            continuation.finish()
        }
    }
}

final class FakePositionReader: PositionReader {
    var result = PositionLookup.notFound
    private(set) var calls: [String] = []

    func read(miffId: String, uid: String) async -> PositionLookup {
        calls.append(uid)
        return result
    }
}

final class FakeFeedRefresher: FeedRefresher {
    var answer = ServerRead.fresh(statuses: [:])
    private(set) var calls: [Bool] = []

    func refresh(miffId: String, readStatuses: Bool) async -> ServerRead {
        calls.append(readStatuses)
        return answer
    }
}
