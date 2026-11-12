import Foundation

/// Ce que l'état de l'autorisation du téléphone permet de montrer et de proposer (Réglages > Notifications, Confidentialité, centre).
/// Une seule source de vérité de l'état et de ses mots.
extension NotificationAuthorization {
    /// Les notifications peuvent-elles arriver ?
    var isAllowed: Bool {
        self == .authorized
    }

    /// La personne les a coupées dans le téléphone : l'app ne peut plus afficher sa fenêtre, seuls les Réglages d'iOS peuvent les
    /// rallumer. Un interrupteur de l'app ne contourne jamais ce refus et ne prétend jamais le faire.
    var needsPhoneSettings: Bool {
        self == .denied
    }

    /// « Autorisées par ton téléphone », « Coupées dans ton téléphone » ou « Pas encore activées ».
    var stateTextKey: String {
        switch self {
        case .authorized:
            return "notif_state_allowed"
        case .denied:
            return "notif_state_off"
        case .notDetermined:
            return "notif_state_notasked"
        }
    }
}

/// Ce que montre l'interrupteur général : allumé seulement si la personne le veut ET que le téléphone l'autorise.
enum NotificationGeneralSwitch {
    static func isOn(preferences: NotificationPreferences, authorization: NotificationAuthorization) -> Bool {
        preferences.general && authorization.isAllowed
    }

    /// Le toucher sur l'interrupteur général est impossible tant que le téléphone a coupé les notifications.
    static func isEnabled(authorization: NotificationAuthorization) -> Bool {
        !authorization.needsPhoneSettings
    }
}
