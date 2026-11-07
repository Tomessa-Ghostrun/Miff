import Foundation

/// Le délai d'une opération est dépassé (la personne en est informée par un message neutre, jamais par ce type).
struct OperationTimedOut: Error, Equatable {}

/// Garantit qu'une opération n'est « reprise » qu'une fois : par son résultat ou par le délai, le premier arrivé.
private final class ResumeGate {
    private let lock = NSLock()
    private var claimed = false

    func claim() -> Bool {
        lock.lock()
        defer { lock.unlock() }
        if claimed {
            return false
        }
        claimed = true
        return true
    }
}

/// Exécute `operation` avec un délai maximal. Les appels Firebase ne sont pas toujours interruptibles : on ne les
/// attend donc pas au-delà du délai (ils se terminent seuls en arrière-plan, et peuvent même aboutir plus tard : « rien
/// n'a été écrit » n'est donc pas garanti à 100 % après un délai dépassé, voir ios/README.md).
/// Lève `OperationTimedOut` si le délai est dépassé.
func timedOperation<T>(seconds: TimeInterval, _ operation: @escaping () async throws -> T) async throws -> T {
    try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<T, Error>) in
        let gate = ResumeGate()
        Task {
            do {
                let value = try await operation()
                if gate.claim() {
                    continuation.resume(returning: value)
                }
            } catch {
                if gate.claim() {
                    continuation.resume(throwing: error)
                }
            }
        }
        Task {
            try? await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
            if gate.claim() {
                continuation.resume(throwing: OperationTimedOut())
            }
        }
    }
}
