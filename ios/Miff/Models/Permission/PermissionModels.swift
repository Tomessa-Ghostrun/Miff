import Foundation

// LOC-4a : les permissions du téléphone (localisation, notifications) telles que les écrans de consentement ont besoin d'en
// raisonner. Du Swift PUR : rien ici ne demande quoi que ce soit au système, et SURTOUT RIEN NE LIT UNE POSITION (critère AUT7 :
// aucune position n'est lue avant l'accord de la personne, et ce ticket n'en lit aucune). Les appels au système sont dans
// `Services/Permission`. Miroir de `domain/permission` d'Android (LOC-2a).

/// Ce que dit l'autorisation de localisation du système pour Miff (`CLAuthorizationStatus`, sans en importer le type).
enum LocationAuthorization: Equatable {
    /// Jamais demandée.
    case notDetermined
    /// Limitée par un contrôle parental ou une gestion d'appareil : la personne ne peut rien y changer depuis l'app.
    case restricted
    /// Refusée pour Miff.
    case denied
    /// « Pendant l'utilisation » (ou « Une fois »).
    case whenInUse
    /// « Toujours ».
    case always
}

/// Ce que dit l'autorisation des notifications (`UNAuthorizationStatus`, sans en importer le type).
enum NotificationAuthorization: Equatable {
    /// Jamais demandée : la fenêtre du système peut encore s'afficher.
    case notDetermined
    /// Refusée : le système n'affichera plus sa fenêtre (la personne peut changer d'avis dans les Réglages).
    case denied
    /// Accordée (y compris provisoire ou éphémère).
    case authorized
}

/// Ce que le téléphone dit MAINTENANT des permissions de Miff.
struct PermissionSnapshot: Equatable {
    let location: LocationAuthorization
    /// Le service de localisation de TOUT l'iPhone est-il allumé ? (Réglages > Confidentialité et sécurité.)
    let servicesEnabled: Bool
    /// « Position exacte » : faux quand la personne n'a donné que la position approximative.
    let preciseLocation: Bool
    let notifications: NotificationAuthorization

    init(
        location: LocationAuthorization,
        servicesEnabled: Bool = true,
        preciseLocation: Bool = true,
        notifications: NotificationAuthorization
    ) {
        self.location = location
        self.servicesEnabled = servicesEnabled
        self.preciseLocation = preciseLocation
        self.notifications = notifications
    }

    /// Miff ne marche qu'avec « Toujours » ET la position exacte (décision du PM : sans « Toujours », l'onboarding ne se termine
    /// pas). La position approximative seule ne suffit jamais : la surveillance des zones a besoin de l'exacte.
    var locationComplete: Bool {
        location == .always && preciseLocation && servicesEnabled
    }

    /// La fenêtre des notifications peut-elle encore s'afficher ? (Une fois refusées, le système ne la montre plus : un écran
    /// qui promet une fenêtre qui ne vient pas n'aurait aucun sens.)
    var notificationsAskable: Bool {
        notifications == .notDetermined
    }
}
