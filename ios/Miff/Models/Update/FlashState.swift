import Foundation

/// L'état du bouton « flash » du viseur (demande du PM, 2026-11-11). Logique pure, sans AVFoundation, pour pouvoir la tester.
///
/// Règles :
/// - le flash est ÉTEINT par défaut, à chaque ouverture du viseur et à chaque retour de l'arrière-plan : l'état n'est JAMAIS gardé
///   (ni sur le disque, ni d'une ouverture à l'autre) ;
/// - changer de caméra l'éteint aussi ;
/// - il ne sert qu'à l'instant de la prise de photo (`AVCapturePhotoSettings.flashMode`), jamais en torche continue ;
/// - si la caméra en cours ne peut pas le donner, le bouton est masqué et la photo part sans flash, sans aucune erreur.
struct FlashState: Equatable {
    /// Le flash est demandé pour la prochaine photo.
    private(set) var isOn = false
    /// La caméra en cours peut donner un flash (flash de l'arrière, ou « Retina Flash » de l'écran pour la frontale).
    private(set) var isAvailable = false

    /// Le bouton est montré seulement quand le flash est possible.
    var isVisible: Bool {
        isAvailable
    }

    /// Le flash part avec la photo seulement s'il est demandé ET possible.
    var shouldFire: Bool {
        isOn && isAvailable
    }

    /// Clé du texte (nom parlé et annonce) : l'état est dit en MOTS, jamais par la seule couleur.
    var textKey: String {
        isOn ? "update_flash_on_a11y" : "update_flash_off_a11y"
    }

    /// Pictogramme du système : l'éclair, barré quand le flash est éteint (l'état se voit sans couleur).
    var symbolName: String {
        isOn ? "bolt.fill" : "bolt.slash.fill"
    }

    /// Un toucher sur le bouton. Sans effet quand le flash n'est pas possible.
    mutating func toggle() {
        guard isAvailable else {
            return
        }
        isOn.toggle()
    }

    /// Nouvelle ouverture du viseur, retour de l'arrière-plan, changement de caméra : toujours éteint.
    mutating func reset() {
        isOn = false
    }

    /// Ce que la caméra en cours sait faire. Une caméra sans flash éteint aussi la demande.
    mutating func setAvailable(_ available: Bool) {
        isAvailable = available
        if !available {
            isOn = false
        }
    }
}

/// Le choix du matériel : la caméra peut-elle donner un flash ?
enum FlashSupport {
    /// - `outputSupportsOn` : `AVCapturePhotoOutput.supportedFlashModes` contient `.on` (vrai pour l'arrière avec flash et pour la
    ///   frontale qui sait faire le Retina Flash) ;
    /// - `deviceHasFlash` : `AVCaptureDevice.hasFlash` (faux sur la frontale : son « flash » est l'écran) ;
    /// - `deviceFlashAvailable` : `AVCaptureDevice.isFlashAvailable` (faux quand le système le refuse un moment, par exemple trop
    ///   chaud). Il ne compte que pour une caméra qui a un vrai flash.
    static func isAvailable(outputSupportsOn: Bool, deviceHasFlash: Bool, deviceFlashAvailable: Bool) -> Bool {
        guard outputSupportsOn else {
            return false
        }
        return deviceHasFlash ? deviceFlashAvailable : true
    }
}
