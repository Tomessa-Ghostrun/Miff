import Foundation

// Les deux petits réglages des notifications push, un par compte, dans les réglages de l'app (UserDefaults) : ni secret, ni position, jamais
// envoyés nulle part par eux-mêmes (l'interrupteur part dans `notify.updates` du registre des appareils, rien d'autre). Ils restent à la
// déconnexion (la personne retrouve ses choix) et partent avec la suppression du compte.

/// L'accord à être notifiée, un drapeau par compte. Posé SEULEMENT par un écran qui dit que Miff enregistre un identifiant technique du
/// téléphone (fourni par Apple pour envoyer les notifications). Les anciens écrans (« Miff n'envoie pas encore de notification ») ne le posent
/// jamais : les comptes qui avaient autorisé les notifications AVANT cette version ne sont enregistrés qu'après avoir vu le texte corrigé.
final class UserDefaultsPushConsentStore: PushConsentStore {
    private static let prefix = "push_consent."

    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    func isGiven(uid: String) -> Bool {
        defaults.bool(forKey: Self.prefix + uid)
    }

    func give(uid: String) {
        defaults.set(true, forKey: Self.prefix + uid)
    }

    func forget(uid: String) {
        defaults.removeObject(forKey: Self.prefix + uid)
    }
}

/// « Quand un proche donne des nouvelles » : ACTIF par défaut (une valeur jamais choisie se lit « actif »).
final class UserDefaultsCircleNewsSwitchStore: CircleNewsSwitchStore {
    private static let prefix = "circle_news_switch."

    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    func isOn(uid: String) -> Bool {
        (defaults.object(forKey: Self.prefix + uid) as? Bool) ?? true
    }

    func set(_ value: Bool, uid: String) {
        defaults.set(value, forKey: Self.prefix + uid)
    }

    func forget(uid: String) {
        defaults.removeObject(forKey: Self.prefix + uid)
    }
}
