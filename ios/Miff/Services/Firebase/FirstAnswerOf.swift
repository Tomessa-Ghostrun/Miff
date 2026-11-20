import Foundation

/// Rend une réponse UNE seule fois, la première arrivée (la lecture ou le délai) : la suite est ignorée. Le SDK Firestore peut attendre longtemps le
/// réseau avant de répondre et ne s'interrompt pas quand on l'annule : sans cela, une lecture sans réseau ferait attendre l'écran (une tâche
/// d'un groupe ne rend la main que quand tous ses enfants ont fini). Même principe que la lecture des membres du Profil.
final class FirstAnswerOf<Value> {
    private let lock = NSLock()
    private var continuation: CheckedContinuation<Value, Never>?

    init(_ continuation: CheckedContinuation<Value, Never>) {
        self.continuation = continuation
    }

    func give(_ answer: Value) {
        lock.lock()
        let waiting = continuation
        continuation = nil
        lock.unlock()
        waiting?.resume(returning: answer)
    }
}

enum TimedAnswer {
    /// Le résultat de `work`, ou `fallback` si `work` n'a pas répondu en `nanoseconds`. `work` n'est pas interrompu (il finit en silence).
    static func of<Value>(
        nanoseconds: UInt64,
        fallback: Value,
        _ work: @escaping () async -> Value
    ) async -> Value {
        await withCheckedContinuation { continuation in
            let first = FirstAnswerOf<Value>(continuation)
            Task {
                first.give(await work())
            }
            Task {
                try? await Task.sleep(nanoseconds: nanoseconds)
                first.give(fallback)
            }
        }
    }
}
