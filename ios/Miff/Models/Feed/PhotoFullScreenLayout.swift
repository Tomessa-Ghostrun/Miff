import CoreGraphics

// La mise en page du plein écran de la photo d'un proche (FEED-8, correctifs de la recette TestFlight). Règle : la LÉGENDE ET LE BOUTON « Voir où c'était »
// passent d'abord (à leur hauteur naturelle, sinon ils DÉFILENT) ; la photo prend TOUTE la hauteur qui reste au-dessus du pied (la croix et l'icône
// « Enregistrer cette photo » sont posées SUR la photo : elles ne lui prennent aucune hauteur) ; et la photo (au moins `minPhotoHeight`) + le pied (au
// plus `footerMaxHeight`) tiennent TOUJOURS dans l'écran, paysage compris. Du Swift pur : aucune mesure de vue, aucune lecture, aucune écriture.

enum PhotoFullScreenLayout {
    /// La photo garde au moins cette hauteur sur un écran haut.
    static let tallPhotoHeight: CGFloat = 200
    /// Sur un écran court (paysage), la photo n'a besoin que de cette part de la hauteur : le pied garde le reste.
    static let shortScreenPhotoShare: CGFloat = 0.45

    /// La hauteur partagée entre la photo et le pied : tout l'écran sous la zone sûre.
    static func available(screenHeight: CGFloat) -> CGFloat {
        max(screenHeight, 0)
    }

    /// La hauteur minimale de la photo : 200 pt, ou 45 % de la hauteur sur un écran court.
    static func minPhotoHeight(screenHeight: CGFloat) -> CGFloat {
        min(tallPhotoHeight, available(screenHeight: screenHeight) * shortScreenPhotoShare)
    }

    /// La hauteur maximale du pied : tout ce que la photo (à son minimum) laisse. Plus haut que cela, le pied défile.
    static func footerMaxHeight(screenHeight: CGFloat) -> CGFloat {
        available(screenHeight: screenHeight) - minPhotoHeight(screenHeight: screenHeight)
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
}
