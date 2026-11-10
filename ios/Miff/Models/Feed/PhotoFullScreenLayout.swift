import CoreGraphics

// La mise en page du plein écran de la photo d'un proche (FEED-8, correctif de la recette TestFlight : sur iPhone 11 Pro, le titre était coupé et les
// boutons « Voir où c'était » et « Enregistrer la photo » sortaient de l'écran, poussés par la photo). Règle : la LÉGENDE ET LES BOUTONS passent
// d'abord, la photo prend l'espace qui reste sous la zone sûre ; quand le pied ne tient pas (grand texte, paysage, petit écran), il défile dans une
// hauteur bornée, et la photo garde au moins `minPhotoHeight`. Du Swift pur : aucune mesure de vue, aucune lecture, aucune écriture.

enum PhotoFullScreenLayout {
    /// La barre du haut (la croix de 48 pt et sa marge).
    static let topBarHeight: CGFloat = 56
    /// La photo garde au moins cette hauteur.
    static let minPhotoHeight: CGFloat = 200
    /// Un pied qui défile garde au moins cette hauteur (un titre et un bouton).
    static let minScrollingFooterHeight: CGFloat = 120
    /// La hauteur du pied (titre sur deux lignes, lieu sur deux lignes, deux boutons de 46 pt, marges) en texte normal : volontairement large, pour
    /// que le pied ne soit jamais coupé ; elle grandit avec la taille du texte (`@ScaledMetric` côté écran).
    static let footerEstimate: CGFloat = 280

    enum Footer: Equatable {
        /// Le pied tient entier sous la photo : sa hauteur naturelle, sans défilement.
        case inline
        /// Le pied ne tient pas : il défile dans `maxHeight`.
        case scrolling(maxHeight: CGFloat)
    }

    /// Comment poser le pied dans un écran de `screenHeight` (hauteur sous la zone sûre) quand il mesure environ `footerHeight`.
    static func footer(screenHeight: CGFloat, footerHeight: CGFloat) -> Footer {
        let room = screenHeight - topBarHeight - minPhotoHeight
        if footerHeight <= room {
            return .inline
        }
        return .scrolling(maxHeight: max(room, minScrollingFooterHeight))
    }
}
