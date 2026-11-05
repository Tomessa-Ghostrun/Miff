import Foundation

/// Attend (par petites pauses de 10 ms, 3 secondes au plus) qu'une condition soit vraie : les view models travaillent dans des
/// tâches de fond. Une fonction libre et non une méthode des classes de test : le compilateur refuse (« escaping local function
/// captures non-escaping value ») une méthode asynchrone à fermeture non échappante dans une classe qui n'est pas finale.
@MainActor
func waitUntil(_ condition: () -> Bool) async {
    for _ in 0..<300 where !condition() {
        try? await Task.sleep(nanoseconds: 10_000_000)
    }
}
