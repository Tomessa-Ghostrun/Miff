import CoreGraphics

// La mise en page de la bulle de la Miff, en constantes nommées et testables. Retour de recette du PM (2026-11-08) : le texte n'était pas
// centré dans la tache (aligné en haut à gauche, avec des marges inégales : 40 pt à gauche pour 56 pt à droite, 40 pt en haut pour
// 46 pt en bas). Désormais le contenu (titre, lignes, bouton) est CENTRÉ dans la forme, avec des marges égales de chaque côté ; la forme,
// elle, est centrée sur sa boîte (voir `FeedBubbleShape`). Le mouvement (`BubbleMotion`) ne décale jamais le texte par rapport à la
// forme : l'échelle et l'inclinaison de la tache se font autour de son centre, la dérive verticale s'applique à toute la bulle.
// Miroir d'Android (mêmes valeurs).
enum BubbleLayout {
    /// Marge de chaque côté du contenu (identique à gauche et à droite).
    static let horizontalInset: CGFloat = 52

    /// Aux plus grandes tailles de texte (tailles d'accessibilité) la marge de chaque côté est réduite : un mot comme « nouvelles » doit
    /// pouvoir tenir sur une ligne (retour de revue, 2026-11-11). Toujours identique à gauche et à droite.
    static let accessibilityHorizontalInset: CGFloat = 28

    static func horizontalInset(accessibilitySize: Bool) -> CGFloat {
        accessibilitySize ? accessibilityHorizontalInset : horizontalInset
    }

    /// La phrase principale ne dépasse pas ce nombre de lignes et peut rétrécir jusqu'à cette échelle plutôt que de couper un mot (Dynamic
    /// Type maximal) ; aux tailles courantes rien ne rétrécit (le texte passe à la ligne avant).
    static let headlineMaxLines = 6
    static let headlineMinimumScale: CGFloat = 0.5

    /// Marge en haut et en bas du contenu (identique). Elle est aussi la bande où se pose le bouton « i » (voir `infoTouchSize`) : le contenu
    /// n'y entre JAMAIS, quelle que soit la taille du texte.
    static let verticalInset: CGFloat = 52

    /// Hauteur minimale de la zone du contenu (avant les marges) : une bulle courte reste ample, son texte au milieu. (160 + 2 x 52 = 264 :
    /// la hauteur d'une bulle courte n'a pas changé en passant la marge de 44 à 52 pour le bouton « i ».)
    static let minContentHeight: CGFloat = 160

    // LE BOUTON « i » (« Comprendre les couleurs », décision du PM du 2026-11-13) : en bas à droite de la bulle, dans la bande de la marge
    // du bas. Le glyphe est petit (discret), la zone de toucher fait au moins 44 pt.
    /// Côté de la zone de toucher du bouton « i » (au moins `Spacing.minTouchTarget`).
    static let infoTouchSize: CGFloat = 44
    /// Côté du glyphe dessiné au milieu de la zone de toucher.
    static let infoGlyphSize: CGFloat = 22
    /// Écart entre la zone de toucher et le bord de la bulle : à droite (à gauche en droite-à-gauche), assez pour que le glyphe reste DANS
    /// la tache (son coin bas est arrondi), et en bas.
    static let infoTrailingInset: CGFloat = 72
    static let infoBottomInset: CGFloat = 8

    /// La zone de toucher du bouton tient tout entière dans la bande de la marge du bas : elle ne peut recouvrir ni le texte, ni « Contacter »,
    /// ni « Reprendre le partage », quelle que soit la taille du texte.
    static var infoButtonFitsBottomBand: Bool {
        infoBottomInset + infoTouchSize <= verticalInset
    }

    /// La boîte totale de la bulle pour un contenu de la taille donnée : les marges s'ajoutent de chaque côté.
    static func outerSize(content: CGSize, horizontalInset: CGFloat = BubbleLayout.horizontalInset) -> CGSize {
        CGSize(
            width: content.width + 2 * horizontalInset,
            height: max(content.height, minContentHeight) + 2 * verticalInset
        )
    }

    /// Où se pose le contenu (de la taille donnée) dans la boîte de la bulle : au milieu, en largeur comme en hauteur, quelle que soit la
    /// taille du texte (Dynamic Type) ; dans une zone plus haute que lui (hauteur minimale), il est centré verticalement.
    static func contentFrame(content: CGSize, horizontalInset: CGFloat = BubbleLayout.horizontalInset) -> CGRect {
        let zoneHeight = max(content.height, minContentHeight)
        return CGRect(
            x: horizontalInset,
            y: verticalInset + (zoneHeight - content.height) / 2,
            width: content.width,
            height: content.height
        )
    }
}
