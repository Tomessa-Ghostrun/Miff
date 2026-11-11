import CoreGraphics
import Foundation

// La mise en page du plein écran de la photo d'un proche (FEED-8, retours de la recette TestFlight). VRAI plein écran : la photo (entière, `scaledToFit`)
// occupe TOUT l'écran, zones sûres comprises, sur fond noir ; la LÉGENDE, « Position mesurée… » et « Voir où c'était » sont POSÉES PAR-DESSUS, en bas,
// sur un voile sombre (texte blanc, contraste d'au moins 4,5:1 même sur la photo la plus claire), dans la zone sûre du bas ; leur zone est BORNÉE (jamais
// plus de 45 % de la hauteur) : en grand texte ou en paysage elle défile, et la photo n'est jamais masquée en entier. La croix et l'icône
// « Enregistrer cette photo » restent en haut. Du Swift pur : aucune mesure de vue, aucune lecture, aucune écriture.

enum PhotoFullScreenLayout {
    /// Part maximale de la hauteur que prend la légende (au-delà, elle défile).
    static let footerMaxShare: CGFloat = 0.45
    /// Hauteur du dégradé qui fond la photo dans le voile, au-dessus du texte.
    static let fadeHeight: CGFloat = 40
    /// L'opacité du voile noir derrière le texte.
    static let scrimAlpha: Double = 0.78
    /// Le contrat de lisibilité (WCAG AA, texte courant).
    static let minimumContrast: Double = 4.5

    /// La hauteur maximale de la zone de la légende : au plus 45 % de la hauteur de l'écran. Plus haut, elle défile.
    static func footerMaxHeight(screenHeight: CGFloat) -> CGFloat {
        max(screenHeight, 0) * footerMaxShare
    }

    /// La taille de la photo entière (`scaledToFit`) dans la zone qui lui reste : elle REMPLIT la largeur ou la hauteur de la zone, sans jamais la
    /// dépasser, et quelle que soit la taille propre de l'image (jamais limitée par elle : la zone décide).
    static func fittedSize(imageSize: CGSize, in area: CGSize) -> CGSize {
        guard imageSize.width > 0, imageSize.height > 0, area.width > 0, area.height > 0 else {
            return .zero
        }
        let scale = min(area.width / imageSize.width, area.height / imageSize.height)
        return CGSize(width: imageSize.width * scale, height: imageSize.height * scale)
    }

    /// Le contraste d'un texte BLANC posé sur le voile noir d'opacité `alpha`, dans le PIRE cas (une photo toute blanche dessous), selon WCAG 2.x.
    static func whiteTextContrast(overScrimAlpha alpha: Double) -> Double {
        let gray = max(0, min(1, 1 - alpha))
        let linear = gray <= 0.04045 ? gray / 12.92 : pow((gray + 0.055) / 1.055, 2.4)
        return 1.05 / (linear + 0.05)
    }
}
