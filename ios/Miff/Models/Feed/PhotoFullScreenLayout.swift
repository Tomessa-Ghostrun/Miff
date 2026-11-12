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
    /// Les espaces de la légende, SERRÉS pour qu'elle soit collée en bas, juste au-dessus de la zone sûre du bas (retour du PM sur le build 27 ;
    /// avant : 8 pt de marge basse et 8 pt entre la légende et le bouton). Le dégradé, lui, reste.
    static let captionTopPadding: CGFloat = 8
    static let captionBottomPadding: CGFloat = 8
    static let captionSpacing: CGFloat = 4
    /// L'opacité du voile noir derrière le texte.
    static let scrimAlpha: Double = 0.70
    /// Le contrat de lisibilité (WCAG AA, texte courant).
    static let minimumContrast: Double = 4.5

    /// La hauteur du contenu de la légende en texte normal (titre, position, bouton de 44 pt, espaces), SANS la zone sûre du bas : volontairement
    /// large. Elle grandit avec la taille du texte (`@ScaledMetric` côté écran) ; au-delà de 45 % de la hauteur, la légende défile.
    static let footerEstimate: CGFloat = 170

    /// La légende défile-t-elle ? Oui quand son contenu estimé (zone sûre du bas comprise) dépasse sa hauteur maximale (grand texte, paysage).
    static func footerScrolls(estimate: CGFloat, screenHeight: CGFloat) -> Bool {
        estimate > footerMaxHeight(screenHeight: screenHeight)
    }

    /// La hauteur maximale de la zone de la légende : au plus 45 % de la hauteur de l'écran. Plus haut, elle défile.
    static func footerMaxHeight(screenHeight: CGFloat) -> CGFloat {
        max(screenHeight, 0) * footerMaxShare
    }

    /// La taille de l'écran ENTIER : la zone visible (celle qu'un lecteur de géométrie qui respecte les zones sûres annonce) plus les zones sûres
    /// (haut, bas, côtés). Le contenu qui ignore les zones sûres a cette taille.
    static func screenSize(
        visible: CGSize, top: CGFloat, bottom: CGFloat, leading: CGFloat, trailing: CGFloat
    ) -> CGSize {
        CGSize(
            width: visible.width + leading + trailing,
            height: visible.height + top + bottom
        )
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
