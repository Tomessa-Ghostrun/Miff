import SwiftUI

// Les couleurs et mesures propres à l'Update (design/screens/update.md §2 et §4.2 ; mêmes valeurs que `ui/theme/UpdateColor.kt` d'Android).
// Aucune valeur de couleur n'est jamais écrite dans un écran : tout passe par ces jetons.

/// Fond de la bulle d'incitation orange : validé par le PM le 2026-11-06 (environ `status/warning` à 63 % sur blanc). Le texte noir y a 14,3 : 1.
let colorStatusWarningBubble = ColorToken(hex: 0xFACE90)
/// Fond de la bulle d'incitation rouge : validé par le PM le 2026-11-06 (environ `status/danger` à 63 % sur blanc). Le texte noir y a 9,7 : 1.
let colorStatusDangerBubble = ColorToken(hex: 0xFA9490)

/// Le fond sombre de l'aperçu de la photo (autour d'elle).
let colorUpdateStage = ColorToken(hex: 0x1D1F20)
/// Le disque sombre translucide derrière un pictogramme blanc posé sur une photo (bascule de caméra, croix) et derrière l'astuce.
let colorUpdateScrim = ColorToken(hex: 0x000000, alpha: 0.45)
/// Le voile posé sur la photo pendant la publication.
let colorUpdateVeil = ColorToken(hex: 0x000000, alpha: 0.30)
/// La pastille sombre du message calme après une publication.
let colorUpdateToast = ColorToken(hex: 0x222222, alpha: 0.92)
/// Blanc des textes et des pictogrammes posés sur le viseur et sur l'aperçu.
let colorUpdateOnPhoto = ColorToken(hex: 0xFFFFFF)

/// Les mesures de l'écran Update (en points).
enum UpdateLayout {
    /// L'obturateur : 80 pt (le minimum du ticket est 72), posé 48 pt au-dessus de la barre.
    static let shutterSize: CGFloat = 80
    static let shutterAboveBar: CGFloat = 48
    static let shutterRingWidth: CGFloat = 4
    static let shutterInnerInset: CGFloat = 14
    static let shutterInnerRingWidth: CGFloat = 3
    /// La bascule de caméra et la croix : 48 pt de zone, un disque de 40 pt.
    static let roundButtonSize: CGFloat = 48
    static let roundButtonDisc: CGFloat = 40
    static let roundButtonIcon: CGFloat = 22
    /// Le dégradé sombre en haut du viseur (lisibilité de l'heure sur une façade claire).
    static let topGradientHeight: CGFloat = 110
    /// La bulle : 16 pt des bords, arrondi 20, queue 22 x 11 dont la pointe s'arrête 4 pt au-dessus de la barre.
    static let bubbleSideMargin: CGFloat = 16
    static let bubbleCorner: CGFloat = 20
    static let bubbleTextInsetX: CGFloat = 18
    static let tailWidth: CGFloat = 22
    static let tailHeight: CGFloat = 11
    static let tailGap: CGFloat = 4
    /// Au-delà de 40 % de la hauteur de l'écran, le texte de la bulle défile À L'INTÉRIEUR (elle ne masque pas le Feed).
    static let bubbleMaxScreenShare: CGFloat = 0.4
    /// « Recommencer » : 56 x 48 ; « Publier » : 48 de haut.
    static let retakeWidth: CGFloat = 56
    static let actionHeight: CGFloat = 48
    static let previewCorner: CGFloat = 20
    /// Le message calme d'une publication : visible 3 secondes.
    static let confirmationSeconds = 3.0
}
