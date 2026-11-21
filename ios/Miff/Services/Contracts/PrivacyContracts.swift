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

    /// Suppression du compte (AUTH-5c) : oublier ce que le SDK garde sur l'installation (Analytics : `resetAnalyticsData`). La collecte
    /// est déjà coupée. Rien n'est envoyé pour cela.
    func forgetInstallation()
}

extension CollectionSwitch {
    /// Un interrupteur qui ne garde rien sur l'installation n'a rien à oublier.
    func forgetInstallation() {
        // Rien à oublier par défaut.
    }
}

/// Écrit le choix « statistiques d'usage et rapports de plantage anonymes » dans le COMPTE (`users/{uid}.analyticsConsent`). C'est le choix
/// du compte qui compte au démarrage suivant (voir `SessionManager`).
///
/// Comme toutes les écritures du profil, elle passe par une TRANSACTION (voir `UserProfileRepository`) : elle n'existe qu'en ligne et ne
/// laisse rien en file derrière elle, donc « pas d'accusé » veut dire « rien d'écrit », et un retrait ne peut jamais partir plus tard, tout
/// seul, après que la personne s'est déconnectée. Aucune valeur n'est jamais mise dans une erreur.
protocol AnalyticsConsentRepository {
    /// `enable` vrai = la personne accepte, faux = elle retire. `.done` aussi quand le compte était déjà dans cet état.
    func set(uid: String, enable: Bool) async -> ProfileWriteResult
}
