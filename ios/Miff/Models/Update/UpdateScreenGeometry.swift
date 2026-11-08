import CoreGraphics

/// La géométrie de l'écran Update par rapport à la barre d'onglets (bug du PM sur TestFlight : l'obturateur passait SOUS la barre).
///
/// Principe : on ne devine ni la zone sûre ni une hauteur de barre. La coquille MESURE le haut réel de la barre (en coordonnées globales
/// de la fenêtre) et le donne à l'Update ; chaque vue de l'Update regarde où SON bas se trouve (aussi en coordonnées globales) et se
/// retire de ce que la barre recouvre. Résultat identique que la vue ait déjà été raccourcie par la zone sûre ou qu'elle descende
/// jusqu'au bas de l'écran. Aucun nombre ici ne dépend d'un appareil.
enum UpdateScreenGeometry {
    /// Marge minimale, en points, entre le haut de la barre et le bas de l'obturateur (le design en met 48 ; jamais moins de 16).
    static let minimumGapAboveBar: CGFloat = 16

    /// Ce que la barre recouvre du bas d'une vue : 0 quand la vue s'arrête déjà au-dessus de la barre ou que la barre est inconnue
    /// (masquée, pas encore mesurée).
    static func barOverlap(viewMaxY: CGFloat, barTopY: CGFloat?) -> CGFloat {
        guard let barTopY else {
            return 0
        }
        return max(0, viewMaxY - barTopY)
    }

    /// Où l'obturateur se place dans une vue (coordonnées globales) : centré, `UpdateLayout.shutterAboveBar` au-dessus de ce que la barre
    /// laisse libre.
    static func shutterFrame(in viewFrame: CGRect, barTopY: CGFloat?) -> CGRect {
        let size = UpdateLayout.shutterSize
        let overlap = barOverlap(viewMaxY: viewFrame.maxY, barTopY: barTopY)
        return CGRect(
            x: viewFrame.midX - size / 2,
            y: viewFrame.maxY - overlap - UpdateLayout.shutterAboveBar - size,
            width: size,
            height: size
        )
    }

    /// Où « Recommencer » et « Publier » (la rangée de l'aperçu) se placent : leur bas est à `bottomMargin` au-dessus de ce que la
    /// barre laisse libre.
    static func buttonRowFrame(in viewFrame: CGRect, barTopY: CGFloat?) -> CGRect {
        let overlap = barOverlap(viewMaxY: viewFrame.maxY, barTopY: barTopY)
        return CGRect(
            x: viewFrame.minX,
            y: viewFrame.maxY - overlap - UpdateLayout.previewButtonsBottomMargin - UpdateLayout.actionHeight,
            width: viewFrame.width,
            height: UpdateLayout.actionHeight
        )
    }
}
