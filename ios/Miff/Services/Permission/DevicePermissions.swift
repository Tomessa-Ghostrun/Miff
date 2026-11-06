import Foundation

// CONFIDENTIALITÉ, pour ce dossier : il LIT L'ÉTAT des autorisations et peut afficher les fenêtres du système APRÈS un toucher de
// la personne sur un bouton qui dit pourquoi. Il ne lit AUCUNE position (aucune coordonnée, aucune mise à jour de position, aucune
// surveillance de zone : PermissionPrivacyTests lit les sources), n'écrit aucun journal et n'envoie aucun événement (critère
// AUT7, décisions (g) et (h) du PM).

/// Ce que le téléphone dit des permissions de Miff, et ce que l'écran peut lui demander (un double dans les tests).
@MainActor
protocol DevicePermissions: AnyObject {
    /// L'état des autorisations, tel qu'il est MAINTENANT.
    func snapshot() async -> PermissionSnapshot

    /// La fenêtre du système pour la localisation, si elle peut encore s'afficher (jamais demandée) ; sinon rien. Ne lit aucune
    /// position.
    func requestLocationAuthorization()

    /// La fenêtre du système pour les notifications, si elle peut encore s'afficher ; rend la main quand la personne a répondu.
    func requestNotifications() async

    /// Les Réglages de Miff dans le système (autorisations de la localisation, de la position exacte, des notifications).
    func openSettings()

    /// Appelé quand le système dit que l'autorisation de localisation a changé (réponse à la fenêtre, retour des Réglages).
    var onLocationChange: (() -> Void)? { get set }
}

/// « L'écran des notifications a été montré à ce compte sur ce téléphone » : il est montré une seule fois, jamais plus (il ne
/// bloque jamais).
protocol NotificationAskStore {
    func wasShown(uid: String) -> Bool
    func markShown(uid: String)
}

/// Un drapeau par compte dans les réglages de l'app : ni secret, ni position, jamais envoyé nulle part. Les réglages de l'app
/// disparaissent avec elle ; rien n'est dans une sauvegarde sensible.
final class UserDefaultsNotificationAskStore: NotificationAskStore {
    private static let prefix = "notification_screen_shown."

    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    func wasShown(uid: String) -> Bool {
        defaults.bool(forKey: Self.prefix + uid)
    }

    func markShown(uid: String) {
        defaults.set(true, forKey: Self.prefix + uid)
    }
}
