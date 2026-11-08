import CoreGraphics

// La mise en page de la bulle de la Miff, en constantes nommées et testables. Retour de recette du PM (2026-11-08) : le texte n'était pas
// centré dans la tache (aligné en haut à gauche, avec des marges inégales : 40 pt à gauche pour 56 pt à droite, 40 pt en haut pour
// 46 pt en bas). Désormais le contenu (titre, lignes, bouton) est CENTRÉ dans la forme, avec des marges égales de chaque côté ; la forme,
// elle, est centrée sur sa boîte (voir `FeedBubbleShape`). Le mouvement (`BubbleMotion`) ne décale jamais le texte par rapport à la
// forme : l'échelle et l'inclinaison de la tache se font autour de son centre, la dérive verticale s'applique à toute la bulle.
// Miroir d'Android (mêmes valeurs).
enum BubbleLayout {
    /// Le pictogramme d'une bulle selon sa couleur (la couleur n'est jamais seule pour l'orange, le rouge et le neutre). Décision du PM
    /// (2026-11-08, build 22) : AUCUN pictogramme sur une bulle VERTE (« Tout va bien » parle de lui-même) ; l'orange garde l'horloge, le
    /// rouge le point d'exclamation, le neutre le sablier. nil = pas de disque, et le contenu reste centré dans la tache.
    static func pictogram(for level: StatusLevel) -> String? {
        switch level {
        case .orange:
            return "clock"
        case .red:
            return "exclamationmark"
        case .neutral:
            return "hourglass"
        case .green:
            return nil
        }
    }

    /// Marge de chaque côté du contenu (identique à gauche et à droite).
    static let horizontalInset: CGFloat = 52

    /// Marge en haut et en bas du contenu (identique).
    static let verticalInset: CGFloat = 44

    /// Hauteur minimale de la zone du contenu (avant les marges) : une bulle courte reste ample, son texte au milieu.
    static let minContentHeight: CGFloat = 176

    /// Le disque du pictogramme (coin bas droit) : sa taille, et sa distance au bord droit et au bord bas de la bulle. Posé pour rester
    /// DANS la tache (dont le coin bas droit se referme) et hors de l'angle d'un bouton centré (le bas du contenu est à `verticalInset`).
    static let discSize: CGFloat = 40
    static let discTrailing: CGFloat = 36
    static let discBottom: CGFloat = 44

    /// La boîte totale de la bulle pour un contenu de la taille donnée : les marges s'ajoutent de chaque côté.
    static func outerSize(content: CGSize) -> CGSize {
        CGSize(
            width: content.width + 2 * horizontalInset,
            height: max(content.height, minContentHeight) + 2 * verticalInset
        )
    }

    /// Où se pose le contenu (de la taille donnée) dans la boîte de la bulle : au milieu, en largeur comme en hauteur, quelle que soit la
    /// taille du texte (Dynamic Type) ; dans une zone plus haute que lui (hauteur minimale), il est centré verticalement.
    static func contentFrame(content: CGSize) -> CGRect {
        let zoneHeight = max(content.height, minContentHeight)
        return CGRect(
            x: horizontalInset,
            y: verticalInset + (zoneHeight - content.height) / 2,
            width: content.width,
            height: content.height
        )
    }
}
