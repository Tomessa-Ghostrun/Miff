import Foundation

/// Les choix de la personne dans Réglages > Notifications, gardés sur CET appareil seulement (aucune écriture Firestore, ticket §20.11).
/// Tout est actif par défaut. Désactiver l'interrupteur général garde la valeur des interrupteurs de type (ils sont seulement grisés).
struct NotificationPreferences: Equatable, Codable {
    /// « Recevoir des notifications ».
    var general = true
    /// « Nuit calme » : de 22 h à 7 h, N1, N3 et N6 arrivent sans son ni vibration.
    var nightCalm = true
    /// Les types que la personne a coupés un par un (vide par défaut : tout est actif).
    var disabledTypes: Set<NotificationType> = []

    /// La valeur de l'interrupteur d'un type, indépendamment de l'interrupteur général.
    func typeValue(_ type: NotificationType) -> Bool {
        !disabledTypes.contains(type)
    }

    /// Ce type peut-il être notifié ? Il faut l'interrupteur général ET celui du type.
    func isEnabled(_ type: NotificationType) -> Bool {
        general && typeValue(type)
    }

    func setting(_ type: NotificationType, to value: Bool) -> NotificationPreferences {
        var copy = self
        if value {
            copy.disabledTypes.remove(type)
        } else {
            copy.disabledTypes.insert(type)
        }
        return copy
    }

    func settingGeneral(_ value: Bool) -> NotificationPreferences {
        var copy = self
        copy.general = value
        return copy
    }

    func settingNightCalm(_ value: Bool) -> NotificationPreferences {
        var copy = self
        copy.nightCalm = value
        return copy
    }
}

/// Où les choix sont gardés (un double en mémoire dans les tests). Un jeu de choix par compte ; tout part à la fin de la session.
protocol NotificationPreferencesStore: AnyObject {
    func load(uid: String) -> NotificationPreferences
    func save(_ preferences: NotificationPreferences, uid: String)
    /// Oublie les choix de TOUS les comptes de ce téléphone (déconnexion, suppression du compte).
    func forgetAll()
}
