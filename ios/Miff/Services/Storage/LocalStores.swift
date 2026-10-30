import Foundation

/// L'acceptation des conditions (a) et le choix sur les statistiques (b), gardés sur l'appareil jusqu'à la création du
/// profil (ticket §8). Ce n'est pas un secret (une version et un booléen), mais jamais partagé ; lié au compte. Effacé à
/// la déconnexion et une fois le profil créé.
final class UserDefaultsPendingConsentStore: PendingConsentStore {
    private enum Key {
        static let uid = "pending_uid"
        static let termsVersion = "pending_terms_version"
        static let analytics = "pending_analytics_accepted"
    }

    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    func save(_ consent: PendingConsent) {
        defaults.set(consent.uid, forKey: Key.uid)
        defaults.set(consent.termsVersion, forKey: Key.termsVersion)
        defaults.set(consent.analyticsAccepted, forKey: Key.analytics)
    }

    func read(uid: String) -> PendingConsent? {
        guard let savedUid = defaults.string(forKey: Key.uid),
              let version = defaults.string(forKey: Key.termsVersion),
              savedUid == uid else {
            return nil
        }
        let analytics = defaults.bool(forKey: Key.analytics)
        return PendingConsent(uid: savedUid, termsVersion: version, analyticsAccepted: analytics)
    }

    func clear() {
        defaults.removeObject(forKey: Key.uid)
        defaults.removeObject(forKey: Key.termsVersion)
        defaults.removeObject(forKey: Key.analytics)
    }
}

/// Drapeaux locaux de l'onboarding. Pas des données personnelles, jamais envoyés nulle part, gardés sur l'appareil
/// seulement (réinstaller l'app peut les remettre à zéro : sans conséquence). Le carrousel de bienvenue n'apparaît
/// qu'à la première ouverture (décision Q11 du ticket Authentification) : le drapeau est posé quand la personne touche
/// « Commencer » ou « J'ai déjà un compte ».
final class OnboardingFlags {
    static let keyCarouselSeen = "welcome_carousel_seen"

    private let store: BooleanStore

    init(store: BooleanStore) {
        self.store = store
    }

    var carouselSeen: Bool {
        store.value(forKey: Self.keyCarouselSeen, default: false)
    }

    func markCarouselSeen() {
        store.set(true, forKey: Self.keyCarouselSeen)
    }
}
