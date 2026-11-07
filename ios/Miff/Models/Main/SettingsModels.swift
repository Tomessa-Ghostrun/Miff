import Foundation

/// Les écrans qu'on atteint depuis l'onglet Profil : la liste des Réglages et ses sous-écrans. Seuls des mots fixes voyagent dans une
/// destination : jamais un nom, un numéro ni une adresse.
enum SettingsRoute: Hashable, CaseIterable {
    case settings
    case myInfo
    case account
    case deleteAccount
    case privacy
    case detectionHelp
}

/// La langue en cours de l'app, pour la ligne « Langue » des Réglages. Sur iPhone la langue se choisit dans les réglages du téléphone
/// (décision Q4 du ticket Feed / Profil / Réglages) : l'app n'a pas de sélecteur à elle, elle dit seulement laquelle elle utilise.
enum AppLanguageName {
    /// `preferred` : les langues de la personne dans l'ordre (`Locale.preferredLanguages`) ; `available` : les langues de l'app
    /// (`Bundle.main.localizations`). La première langue de la personne que l'app parle gagne, sinon la langue de repli (l'anglais).
    static func code(preferred: [String], available: [String], fallback: String = "en") -> String {
        let supported = available.filter { $0 != "Base" }
        for language in preferred {
            let base = language.split(separator: "-").first.map(String.init) ?? language
            if supported.contains(base) {
                return base
            }
        }
        return supported.contains(fallback) ? fallback : (supported.first ?? fallback)
    }

    /// Le nom de la langue DANS SA PROPRE LANGUE (« Français », « English ») : on la retrouve même si l'app s'affiche dans une langue
    /// qu'on ne lit pas.
    static func nativeName(code: String) -> String {
        let locale = Locale(identifier: code)
        return (locale.localizedString(forLanguageCode: code) ?? code).capitalized(with: locale)
    }

    static var current: String {
        nativeName(code: code(preferred: Locale.preferredLanguages, available: Bundle.main.localizations))
    }
}

/// Les liens vers l'extérieur construits par l'app. L'adresse d'aide vient de `AppLinks` (contact provisoire, décision du PM du
/// 2026-10-24).
enum ContactLinks {
    /// `mailto:` bâti par composants d'URL : l'adresse n'est jamais collée dans une chaîne à la main.
    static func mailURL(address: String) -> URL? {
        var components = URLComponents()
        components.scheme = "mailto"
        components.path = address
        return components.url
    }

    static func supportMailURL() -> URL? {
        mailURL(address: AppLinks.supportEmail)
    }
}

/// Ce que dit l'écran Confidentialité de l'état des autorisations du téléphone (lu, jamais demandé ici).
struct PrivacyPermissionState: Equatable {
    let locationComplete: Bool
    let notificationsAuthorized: Bool

    init(snapshot: PermissionSnapshot) {
        locationComplete = snapshot.locationComplete
        notificationsAuthorized = snapshot.notifications == .authorized
    }

    var locationStatusKey: String {
        locationComplete ? "settings_privacy_location_status_ok" : "settings_privacy_location_status_todo"
    }

    var notificationsStatusKey: String {
        notificationsAuthorized ? "ios_privacy_notifications_status_on" : "ios_privacy_notifications_status_off"
    }
}
