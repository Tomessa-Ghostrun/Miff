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
    /// Réglages > Langue : français, English ou la langue du téléphone, appliqué tout de suite (miroir d'Android). Absent des Réglages
    /// restreints d'un compte sans Miff, comme la ligne.
    case language
    /// NOTIF-P0 : Réglages > Notifications, le centre de notifications (aussi atteint par la cloche du Feed) et l'aide « notifications
    /// coupées ».
    case notifications
    case notificationCenter
    case notificationsHelp
    /// ABOUT-1I : Réglages > « À propos et aide » (version, aide, contact, avis, textes) et, dessous, « Licences et attributions ».
    /// Permis aussi aux Réglages restreints : c'est là qu'on a le plus besoin d'aide.
    case about
    case licenses

    /// Les écrans des notifications n'existent pas pour un compte sans Miff : rien à notifier (l'état de l'autorisation reste visible
    /// dans Confidentialité).
    var isNotificationScreen: Bool {
        self == .notifications || self == .notificationCenter || self == .notificationsHelp
    }
}

/// Les lignes de la liste des Réglages, et ce qu'un compte SANS Miff en voit. Décision du PM du 2026-11-07 (qui révise la décision Q13 « ni
/// barre ni Réglages sans Miff ») : un compte sans Miff doit pouvoir se supprimer dans l'app (Apple 5.1.1(v)), donc il atteint des Réglages
/// RESTREINTS : « Mon compte » (e-mail, mot de passe, déconnexion, supprimer mon compte), « Confidentialité » (textes légaux,
/// autorisations) et « À propos et aide » (contact, version, licences ; décision du PM du 2026-11-13). Ni Mes informations, ni Langue : le reste vient avec les Miffs. La barre d'onglets reste masquée sans Miff.
enum SettingsListRow: Equatable, CaseIterable {
    case myInfo
    case account
    /// Absente des Réglages restreints d'un compte sans Miff.
    case notifications
    case language
    case privacy
    /// « À propos et aide » : TOUJOURS en dernier, y compris dans les Réglages restreints (décision Q1 du PM, 2026-11-13).
    case about
}

enum SettingsAccess {
    /// Les lignes montrées : toutes, ou seulement Mon compte, Confidentialité et « À propos et aide » pour un compte sans Miff.
    static func rows(restricted: Bool) -> [SettingsListRow] {
        restricted ? [.account, .privacy, .about] : SettingsListRow.allCases
    }

    /// Un écran des Réglages est-il permis à un compte sans Miff ? (Mes informations ne l'est pas.)
    static func isAllowed(_ route: SettingsRoute, restricted: Bool) -> Bool {
        guard restricted else {
            return true
        }
        return route != .myInfo && route != .language && !route.isNotificationScreen
    }

    /// Les écrans qu'ouvre un écran des Réglages (la liste ouvre les trois premiers, Mon compte ouvre la suppression, Confidentialité
    /// l'aide à la détection) : sert à prouver, par un test, que la suppression ne s'atteint que par Réglages > Mon compte.
    static func children(of route: SettingsRoute) -> [SettingsRoute] {
        switch route {
        case .settings:
            return [.myInfo, .account, .notifications, .language, .privacy, .about]
        case .account:
            return [.deleteAccount]
        case .privacy:
            return [.detectionHelp]
        case .about:
            return [.detectionHelp, .notificationsHelp, .licenses]
        case .licenses:
            return []
        case .notifications:
            return [.notificationCenter, .notificationsHelp]
        case .notificationCenter:
            return [.notificationsHelp]
        case .myInfo, .deleteAccount, .detectionHelp, .notificationsHelp, .language:
            return []
        }
    }

    /// Tous les écrans atteignables depuis la liste des Réglages, la liste comprise.
    static func reachable(restricted: Bool) -> Set<SettingsRoute> {
        var found: Set<SettingsRoute> = [.settings]
        var pending: [SettingsRoute] = [.settings]
        while let current = pending.popLast() {
            let next = children(of: current).filter { isAllowed($0, restricted: restricted) && !found.contains($0) }
            for child in next {
                found.insert(child)
                pending.append(child)
            }
        }
        return found
    }
}

/// Les noms des langues et la langue du téléphone que l'app parle. Le CHOIX de la langue (français, English, langue du téléphone) est dans
/// `AppLanguage.swift` ; l'écran est `LanguageView` (décision du PM du 2026-11-13 : le même écran qu'Android, dans l'app, à la place de
/// l'ouverture des réglages de l'iPhone décidée d'abord, Q4 du ticket Feed / Profil / Réglages).
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

    /// Le nom, dans sa langue, de la langue EN COURS dans l'app (le choix de la personne, ou celle du téléphone).
    static var current: String {
        nativeName(code: AppLanguage.code)
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
    let notifications: NotificationAuthorization

    init(snapshot: PermissionSnapshot) {
        locationComplete = snapshot.locationComplete
        notificationsAuthorized = snapshot.notifications == .authorized
        notifications = snapshot.notifications
    }

    var locationStatusKey: String {
        locationComplete ? "settings_privacy_location_status_ok" : "settings_privacy_location_status_todo"
    }

    /// « Autorisées par ton téléphone », « Coupées dans ton téléphone » ou « Pas encore activées » : les mêmes mots que Réglages >
    /// Notifications (une seule source de vérité de l'état).
    var notificationsStatusKey: String {
        notifications.stateTextKey
    }
}
