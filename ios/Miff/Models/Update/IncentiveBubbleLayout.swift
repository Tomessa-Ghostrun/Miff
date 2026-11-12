import CoreGraphics

// La taille de la bulle d'incitation (« Tu es passé en orange… »), en règle PURE et testable. Retour du PM (iPhone) : la bulle était BEAUCOUP trop
// grosse ; elle doit avoir le design d'Android (`IncentiveBubble` d'`UpdateBubble.kt`) : compacte, de la hauteur de son texte (marge de 16 pt
// en haut et en bas, texte italique 16 / 22), jamais plus haute que 40 % de l'écran, le texte défilant À L'INTÉRIEUR au-delà (grand texte),
// jamais un grand rectangle vide. La hauteur est MESURÉE (celle du texte), jamais laissée à un conteneur gourmand.
enum IncentiveBubbleLayout {
    /// La hauteur du corps de la bulle : celle de son texte (marges comprises), bornée par `maxBodyHeight`. Une hauteur mesurée nulle
    /// (pas encore mesurée) ne donne aucune hauteur imposée : la bulle prend alors celle de son texte.
    static func bodyHeight(content: CGFloat, maxBodyHeight: CGFloat) -> CGFloat? {
        guard content > 0 else {
            return nil
        }
        return min(content, max(maxBodyHeight, 0))
    }

    /// Le texte défile dans la bulle seulement quand il est plus haut que ce que la bulle peut prendre.
    static func scrolls(content: CGFloat, maxBodyHeight: CGFloat) -> Bool {
        content > max(maxBodyHeight, 0)
    }

    /// La hauteur TOTALE de la bulle : le corps, la queue et l'air sous la pointe (qui s'arrête au-dessus de la barre d'onglets).
    static func totalHeight(content: CGFloat, maxBodyHeight: CGFloat) -> CGFloat {
        let body = bodyHeight(content: content, maxBodyHeight: maxBodyHeight) ?? 0
        return body + UpdateLayout.tailHeight + UpdateLayout.tailGap
    }
}
