import Foundation

/// Petit magasin de booléens (UserDefaults dans l'app, mémoire dans les tests). Données non sensibles : jamais rien de
/// personnel ici.
protocol BooleanStore {
    func value(forKey key: String, default defaultValue: Bool) -> Bool
    func set(_ value: Bool, forKey key: String)
}

/// Interrupteur de collecte d'un SDK (Analytics, Crashlytics).
protocol CollectionSwitch {
    /// `false` = aucun consentement : arrêter de collecter ET oublier ce qui était gardé pour être envoyé (rapports de
    /// plantage restés sur le disque).
    func setEnabled(_ enabled: Bool)

    /// Arrêter de collecter pour l'instant, SANS rien effacer de ce qui est déjà gardé pour être envoyé. Utilisé à chaque
    /// démarrage de l'app (le choix du compte n'est pas encore connu) : le rapport du plantage précédent survit jusqu'à
    /// ce que le compte dise oui (il part) ou non (`setEnabled(false)` l'efface).
    func pause()
}
