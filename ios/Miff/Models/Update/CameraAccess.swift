import Foundation

// Ce que l'écran Update montre pour l'autorisation de la caméra (ticket Update §5.2, états A à D, critères P1 et P2). Du Swift pur : le
// système donne un fait, ceci donne l'écran. L'autorisation n'est demandée QU'À l'ouverture de l'écran Update et après l'explication,
// jamais au démarrage de l'app.
//
// iPhone : la boîte du système ne s'affiche QU'UNE FOIS ; après un refus, seuls les Réglages peuvent autoriser la caméra. Une
// restriction (contrôle parental, gestion d'appareil) ne peut pas se lever depuis l'app : l'écran le dit sans bouton « Réglages ».
//
// D31 (décision du PM du 2026-11-06) : le repli « appareil photo du système » n'est PAS proposé. `UIImagePickerController` en mode caméra
// et tout autre outil de prise de vue du système exigent la MÊME autorisation de la caméra (la documentation d'Apple la demande pour
// ces classes) ; la seule autre source est la photothèque, que la décision D16 exclut (jamais de photo ancienne). Un refus de la caméra
// veut donc dire « pas de publication », avec « Ouvrir les réglages », jamais une alternative promise.

/// L'autorisation de la caméra, telle que le système la dit.
enum CameraAuthorization: Equatable {
    case notDetermined
    case authorized
    case denied
    case restricted
}

enum CameraAccessScreen: Equatable {
    /// Autorisée : le viseur.
    case viewfinder
    /// Première fois : le pop-in qui dit pourquoi, qui voit la photo et combien de temps, AVANT la boîte du système.
    case explain
    /// Refusée : la caméra est désactivée pour Miff, les Réglages sont la seule sortie.
    case disabled
    /// Restreinte par le contrôle parental ou l'appareil : message calme, sans bouton « Réglages ».
    case restricted
}

enum CameraAccess {
    static func forOpening(_ authorization: CameraAuthorization) -> CameraAccessScreen {
        switch authorization {
        case .authorized:
            return .viewfinder
        case .notDetermined:
            return .explain
        case .denied:
            return .disabled
        case .restricted:
            return .restricted
        }
    }

    /// L'écran après la réponse de la boîte du système.
    static func afterRequest(granted: Bool) -> CameraAccessScreen {
        granted ? .viewfinder : .disabled
    }
}
