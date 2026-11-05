import Foundation

/// Une porte à un seul passage pour du code asynchrone : les appelants passent l'un après l'autre, dans l'ordre d'arrivée. Sert à
/// `DeviceSession` pour qu'une seule opération qui lit ou écrit la session (écrire, juger, relire) tourne à la fois. Un appelant ne
/// doit JAMAIS rappeler `run` depuis le corps qu'il exécute (la porte n'est pas réentrante).
final class AsyncGate {
    private let lock = NSLock()
    private var busy = false
    private var waiters: [CheckedContinuation<Void, Never>] = []

    func run<Result>(_ body: () async -> Result) async -> Result {
        await acquire()
        defer { release() }
        return await body()
    }

    private func acquire() async {
        await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
            lock.lock()
            if busy {
                waiters.append(continuation)
                lock.unlock()
            } else {
                busy = true
                lock.unlock()
                continuation.resume()
            }
        }
    }

    private func release() {
        lock.lock()
        if waiters.isEmpty {
            busy = false
            lock.unlock()
        } else {
            // La porte reste prise : elle passe directement au suivant.
            let next = waiters.removeFirst()
            lock.unlock()
            next.resume()
        }
    }
}
