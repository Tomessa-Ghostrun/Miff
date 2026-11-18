#if ALERT_DANGER
import XCTest
@testable import Miff

// Les doubles en mémoire de l'alerte : le magasin du serveur, la session de l'appareil, le réseau, le stockage local. Aucun n'ouvre Firestore ni le disque.

final class FakeDangerStore: DangerAlertStore {
    var startResults: [DangerStartResult] = []
    var endResults: [DangerEndResult] = []
    var mineReads: [DangerMineRead] = []
    private(set) var startCalls = 0
    private(set) var endCalls = 0
    private(set) var mineCalls = 0
    /// Ce que le dernier appel de début a reçu (la Miff visée), jamais plus.
    private(set) var lastStartMiffId: String?

    private func take<Value>(_ list: inout [Value], fallback: Value) -> Value {
        guard let first = list.first else {
            return fallback
        }
        if list.count > 1 {
            list.removeFirst()
        }
        return first
    }

    func start(uid: String, miffId: String, deviceId: String, nowMillis: Int) async -> DangerStartResult {
        startCalls += 1
        lastStartMiffId = miffId
        return take(&startResults, fallback: .failed(.offline))
    }

    func end(uid: String, miffId: String, deviceId: String, nowMillis: Int) async -> DangerEndResult {
        endCalls += 1
        return take(&endResults, fallback: .failed(.offline))
    }

    func readMine(uid: String, miffId: String) async -> DangerMineRead {
        mineCalls += 1
        return take(&mineReads, fallback: .unavailable)
    }

    func observe(miffId: String) -> AsyncStream<DangerAlertsHeard> {
        AsyncStream { continuation in
            continuation.finish()
        }
    }
}

final class FakeDangerNetwork: NetworkMonitor {
    private var online: Bool
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

    func set(online value: Bool) {
        online = value
        for continuation in continuations {
            continuation.yield(value)
        }
    }
}

final class MemoryDangerLocalStore: DangerAlertLocalStore {
    private(set) var saved: DangerAlertPointer?
    private var hint = false
    private var adopted: String?

    func pointer() -> DangerAlertPointer? {
        saved
    }

    func save(_ pointer: DangerAlertPointer) {
        saved = pointer
    }

    func clearPointer() {
        saved = nil
    }

    func hintDone() -> Bool {
        hint
    }

    func markHintDone() {
        hint = true
    }

    func adoptedFor() -> String? {
        adopted
    }

    func markAdoptedFor(_ uid: String) {
        adopted = uid
    }

    func clearAccount() {
        saved = nil
        adopted = nil
    }
}

/// Attend qu'une condition soit vraie (les tâches de l'alerte tournent sur l'acteur principal) : jamais une attente fixe.
@MainActor
func eventually(
    _ message: String = "condition jamais vraie",
    file: StaticString = #filePath,
    line: UInt = #line,
    _ condition: () -> Bool
) async {
    for _ in 0..<500 where !condition() {
        try? await Task.sleep(nanoseconds: 2_000_000)
    }
    XCTAssertTrue(condition(), message, file: file, line: line)
}
#endif
