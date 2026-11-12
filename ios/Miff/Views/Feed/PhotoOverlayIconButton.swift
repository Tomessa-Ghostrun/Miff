import SwiftUI

// La croix et l'icône « Enregistrer cette photo » du plein écran de la photo d'un proche (FEED-8). Retour du PM sur le build 27 : l'icône « télécharger »
// n'était pas centrée dans son rond (un symbole SF est aligné sur la boîte typographique du texte, pas sur le centre de son dessin). Les deux pictogrammes
// sont donc DESSINÉS ici (`PhotoGlyphShape`), dans un carré dont le centre est EXACTEMENT celui du dessin : centrés par construction, ce que
// `PhotoGlyphTests` vérifie sur le tracé. Même disque sombre translucide de 40 pt dans une zone de 48 pt que le reste de l'Update (« Réduire la
// transparence » le rend opaque). Rien n'est lu ni écrit ici.

enum PhotoOverlayGlyph: Equatable {
    /// La croix de fermeture.
    case close
    /// « Télécharger » : une flèche vers le bas au-dessus d'un plateau ouvert.
    case download
}

/// Le tracé d'un pictogramme, en coordonnées d'un carré : sa boîte englobante est CENTRÉE sur le centre du carré (au dixième de point près).
struct PhotoGlyphShape: Shape {
    let glyph: PhotoOverlayGlyph

    func path(in rect: CGRect) -> Path {
        func point(_ x: CGFloat, _ y: CGFloat) -> CGPoint {
            CGPoint(x: rect.minX + x * rect.width, y: rect.minY + y * rect.height)
        }
        var path = Path()
        switch glyph {
        case .close:
            path.move(to: point(0.12, 0.12))
            path.addLine(to: point(0.88, 0.88))
            path.move(to: point(0.88, 0.12))
            path.addLine(to: point(0.12, 0.88))
        case .download:
            // La flèche (tige puis pointe), de 0,06 à 0,66 ; le plateau ouvert en dessous, de 0,64 à 0,94 : le dessin va de 0,06 à 0,94 en hauteur et de
            // 0,10 à 0,90 en largeur, donc son centre est (0,5 ; 0,5).
            path.move(to: point(0.5, 0.06))
            path.addLine(to: point(0.5, 0.66))
            path.move(to: point(0.26, 0.42))
            path.addLine(to: point(0.5, 0.66))
            path.addLine(to: point(0.74, 0.42))
            path.move(to: point(0.1, 0.64))
            path.addLine(to: point(0.1, 0.94))
            path.addLine(to: point(0.9, 0.94))
            path.addLine(to: point(0.9, 0.64))
        }
        return path
    }
}

/// Le bouton rond : le pictogramme dessiné, centré dans le disque par un `ZStack` (même centre), nom parlé donné par la clé.
struct PhotoOverlayIconButton: View {
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency
    let glyph: PhotoOverlayGlyph
    let labelKey: String
    let onTap: () -> Void

    /// Le carré du pictogramme et l'épaisseur de son trait (arrondi aux bouts, comme les symboles du système).
    static let glyphBox: CGFloat = 20
    static let lineWidth: CGFloat = 2

    var body: some View {
        Button(action: onTap) {
            ZStack {
                Circle()
                    .fill(updateScrimToken(reduceTransparency: reduceTransparency).color)
                    .frame(width: UpdateLayout.roundButtonDisc, height: UpdateLayout.roundButtonDisc)
                PhotoGlyphShape(glyph: glyph)
                    .stroke(
                        colorUpdateOnPhoto.color,
                        style: StrokeStyle(lineWidth: Self.lineWidth, lineCap: .round, lineJoin: .round)
                    )
                    .frame(width: Self.glyphBox, height: Self.glyphBox)
            }
            .frame(width: UpdateLayout.roundButtonSize, height: UpdateLayout.roundButtonSize)
            .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(verbatim: L10n.string(labelKey)))
    }
}
