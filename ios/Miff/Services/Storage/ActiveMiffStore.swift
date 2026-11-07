import Foundation

/// La Miff que l'accueil montre quand il y en a plusieurs (Premium), gardée dans les réglages de l'app. Pas un secret : un
/// identifiant de Miff ne sert à rien sans le compte. Jamais envoyé nulle part, effacé à la déconnexion (voir `MiffDirectory`).
/// Une valeur relue qui n'a pas la forme d'un identifiant est ignorée.
final class UserDefaultsActiveMiffStore: ActiveMiffStore {
    private static let key = "active_miff_id"

    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    func chosen() -> String? {
        guard let value = defaults.string(forKey: Self.key), MiffId.isValid(value) else {
            return nil
        }
        return value
    }

    func choose(_ miffId: String) {
        guard MiffId.isValid(miffId) else {
            return
        }
        defaults.set(miffId, forKey: Self.key)
    }

    func clear() {
        defaults.removeObject(forKey: Self.key)
    }
}
