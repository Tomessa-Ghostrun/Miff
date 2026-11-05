import Foundation

// L'écran de la localisation d'iOS (maquettes 08, 09, 10 de design/exports/lieux/claude) comme une machine PURE : rien ici ne
// lit une position ni ne demande quoi que ce soit au système ; l'écran (Views/Permission) appelle le système et la porte relit
// l'état. Il n'y a pas d'historique à garder : l'écran est entièrement déterminé par ce que le système dit MAINTENANT (une
// autorisation retirée app fermée donne donc le bon écran à la réouverture, sans état à retrouver).

/// Quelle variante de l'écran de la localisation est montrée.
enum LocationVariant: Equatable {
    /// 08 : jamais demandée. « Toujours autoriser » ouvre la fenêtre du système.
    case first
    /// 09 : « Pendant l'utilisation » ou « Une fois » accordé. iOS n'accorde « Toujours » que par les Réglages (ou par une
    /// invite que le système peut montrer de lui-même plus tard) : « Plus qu'une étape ! ».
    case upgrade
    /// 10A : refusée, ou retirée plus tard (c'est aussi l'écran de réaffichage quand l'autorisation est retirée app fermée).
    case denied
    /// 10B : le service de localisation est coupé sur tout l'iPhone.
    case servicesOff
    /// Limitée par un contrôle parental ou une gestion d'appareil : aucune action possible pour la personne (non dessiné).
    case restricted
    /// « Toujours » accordé mais « Position exacte » éteinte : la position approximative seule est refusée (décision du PM ;
    /// non dessiné).
    case approximate
}

/// Ce que fait le bouton de l'écran.
enum LocationAction: Equatable {
    /// La fenêtre du système (« Autoriser pendant l'utilisation de l'app », « Autoriser une fois », « Ne pas autoriser »).
    case systemWindow
    /// Les Réglages de l'app dans le système.
    case openSettings
    /// Rien : la personne ne peut pas agir depuis l'app (localisation restreinte).
    case none
}

struct LocationScreenModel: Equatable {
    let variant: LocationVariant
    let action: LocationAction
}

enum LocationFlow {
    /// L'écran que la localisation demande, ou nil quand « Toujours » + position exacte sont accordés. Le service coupé passe
    /// en premier : tant qu'il est coupé, aucune fenêtre du système ne s'afficherait.
    static func model(for snapshot: PermissionSnapshot) -> LocationScreenModel? {
        if snapshot.locationComplete {
            return nil
        }
        if !snapshot.servicesEnabled {
            return LocationScreenModel(variant: .servicesOff, action: .openSettings)
        }
        switch snapshot.location {
        case .notDetermined:
            return LocationScreenModel(variant: .first, action: .systemWindow)
        case .whenInUse:
            return LocationScreenModel(variant: .upgrade, action: .openSettings)
        case .denied:
            return LocationScreenModel(variant: .denied, action: .openSettings)
        case .restricted:
            return LocationScreenModel(variant: .restricted, action: .none)
        case .always:
            // « Toujours » sans la position exacte (`locationComplete` est faux) : les Réglages, « Position exacte ».
            return LocationScreenModel(variant: .approximate, action: .openSettings)
        }
    }
}
