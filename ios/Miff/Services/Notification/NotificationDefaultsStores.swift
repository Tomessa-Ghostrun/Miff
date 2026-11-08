import Foundation

// Les petits réglages locaux des notifications, dans les réglages de l'app (UserDefaults) : ni secret, ni position, jamais envoyés
// nulle part. Un jeu par compte, effacé à la fin de la session (les choix) ou à la suppression du compte (la carte).

/// Les choix de Réglages > Notifications, un jeu par compte. Une valeur illisible redonne les valeurs par défaut (tout actif), jamais une
/// erreur.
final class UserDefaultsNotificationPreferencesStore: NotificationPreferencesStore {
    private static let prefix = "notification_preferences."

    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    func load(uid: String) -> NotificationPreferences {
        guard let data = defaults.data(forKey: Self.prefix + uid),
              let preferences = try? JSONDecoder().decode(NotificationPreferences.self, from: data) else {
            return NotificationPreferences()
        }
        return preferences
    }

    func save(_ preferences: NotificationPreferences, uid: String) {
        guard let data = try? JSONEncoder().encode(preferences) else {
            return
        }
        defaults.set(data, forKey: Self.prefix + uid)
    }

    func forgetAll() {
        for key in defaults.dictionaryRepresentation().keys where key.hasPrefix(Self.prefix) {
            defaults.removeObject(forKey: key)
        }
    }
}

/// Où en est la carte « deuxième chance », un jeu par compte.
final class UserDefaultsNotificationSecondChanceStore: NotificationSecondChanceStore {
    private static let seenPrefix = "notification_second_chance_seen."
    private static let answeredPrefix = "notification_second_chance_answered."

    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    func state(uid: String) -> SecondChanceState {
        SecondChanceState(
            firstUpdateSeen: defaults.bool(forKey: Self.seenPrefix + uid),
            answered: defaults.bool(forKey: Self.answeredPrefix + uid)
        )
    }

    func save(_ state: SecondChanceState, uid: String) {
        defaults.set(state.firstUpdateSeen, forKey: Self.seenPrefix + uid)
        defaults.set(state.answered, forKey: Self.answeredPrefix + uid)
    }

    func forget(uid: String) {
        defaults.removeObject(forKey: Self.seenPrefix + uid)
        defaults.removeObject(forKey: Self.answeredPrefix + uid)
    }
}
