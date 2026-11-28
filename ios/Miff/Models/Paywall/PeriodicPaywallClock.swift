import Foundation

/// L'horloge de l'offre périodique : MONOTONE (elle ne dépend pas de l'heure du téléphone : un changement d'heure ou de fuseau ne change ni le comptage ni l'affichage, critère
/// PP3) et qui CONTINUE pendant que le téléphone dort. `ProcessInfo.systemUptime`, elle, s'arrête pendant le sommeil : une personne qui verrouille son téléphone deux heures
/// reviendrait « après quelques secondes ». `CLOCK_MONOTONIC` (Darwin) continue pendant le sommeil. Aucune date n'est gardée : la valeur ne sert qu'à mesurer des durées dans
/// le processus.
enum PeriodicPaywallClock {
    /// Les millisecondes écoulées depuis un instant arbitraire (le démarrage du téléphone), sommeil compris.
    static func monotonicMillis() -> Int {
        Int(clock_gettime_nsec_np(CLOCK_MONOTONIC) / 1_000_000)
    }
}
