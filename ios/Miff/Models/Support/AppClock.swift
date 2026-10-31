import Foundation

/// L'horloge et l'attente dont les view models ont besoin, injectables pour que les tests n'attendent jamais pour de
/// vrai.
struct AppClock {
    /// Horloge monotone en millisecondes (temps depuis le démarrage de l'appareil), insensible à un changement d'heure.
    let nowMillis: () -> Int
    /// Attend ce nombre de millisecondes.
    let pause: (Int) async -> Void

    static let live = AppClock(
        nowMillis: { Int(ProcessInfo.processInfo.systemUptime * 1000) },
        pause: { millis in
            try? await Task.sleep(nanoseconds: UInt64(max(millis, 0)) * 1_000_000)
        }
    )
}
