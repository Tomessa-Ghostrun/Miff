import CoreGraphics

// La mise en page du plein écran de la photo d'un proche (FEED-8, correctif de la recette TestFlight : sur iPhone 11 Pro, le titre était coupé et les
// boutons sortaient de l'écran, poussés par la photo). Règle : la LÉGENDE ET LE BOUTON « Voir où c'était » passent d'abord (à leur hauteur naturelle,
// sinon ils DÉFILENT) ; la photo prend l'espace qui reste sous la zone sûre ; et la barre du haut + la photo (au moins `minPhotoHeight`) + le pied
// (au plus `footerMaxHeight`) tiennent TOUJOURS dans l'écran, paysage compris : la croix et l'icône ne sont jamais rognées. Du Swift pur : aucune
// mesure de vue, aucune lecture, aucune écriture.

enum PhotoFullScreenLayout {
    /// La barre du haut (la croix et l'icône de 48 pt et leur marge).
    static let topBarHeight: CGFloat = 56
    /// La photo garde au moins cette hauteur sur un écran haut.
    static let tallPhotoHeight: CGFloat = 200
    /// Sur un écran court (paysage), la photo n'a besoin que de cette part de l'espace sous la barre : le pied garde le reste.
    static let shortScreenPhotoShare: CGFloat = 0.45

    /// L'espace sous la barre du haut.
    static func available(screenHeight: CGFloat) -> CGFloat {
        max(screenHeight - topBarHeight, 0)
    }

    /// La hauteur minimale de la photo : 200 pt, ou 45 % de l'espace sous la barre sur un écran court.
    static func minPhotoHeight(screenHeight: CGFloat) -> CGFloat {
        min(tallPhotoHeight, available(screenHeight: screenHeight) * shortScreenPhotoShare)
    }

    /// La hauteur maximale du pied : tout ce que la photo (à son minimum) laisse. Plus haut que cela, le pied défile.
    static func footerMaxHeight(screenHeight: CGFloat) -> CGFloat {
        available(screenHeight: screenHeight) - minPhotoHeight(screenHeight: screenHeight)
    }
}
