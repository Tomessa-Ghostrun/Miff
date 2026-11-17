import SwiftUI

// L'anneau de la carte « Répartition de ton temps », dessiné à la main avec SwiftUI : aucune bibliothèque tierce, et pas Swift Charts
// (`SectorMark`, le graphique en anneau du système, demande iOS 17 alors que le minimum de l'app est iOS 16 : critère TS-24). Décoratif pour
// VoiceOver : tout ce qu'il montre est dit par la description unique de la carte et par la légende. Les couleurs viennent des jetons
// (`ChartTokens`) ; la zone de danger est HACHURÉE : la couleur n'est jamais seule. Aucun mouvement (pas d'animation en v1).

/// Une tranche d'anneau : le contour est fait de segments de droite (un point tous les 2 degrés), donc le même dessin quelle que soit
/// l'orientation du système de coordonnées. `start` et `end` sont des fractions de tour à partir de midi, dans le sens horaire.
struct DonutSliceShape: Shape {
    let start: Double
    let end: Double
    let thickness: CGFloat
    /// L'espace de la couleur du fond entre deux tranches, en degrés (réparti de chaque côté).
    let gapDegrees: Double

    func path(in rect: CGRect) -> Path {
        let outer = min(rect.width, rect.height) / 2
        let inner = max(0, outer - thickness)
        let center = CGPoint(x: rect.midX, y: rect.midY)
        let from = start * 360 + gapDegrees / 2
        let to = end * 360 - gapDegrees / 2
        guard to > from else {
            return Path()
        }
        let steps = max(2, Int(((to - from) / 2).rounded(.up)))
        var path = Path()
        for step in 0...steps {
            let angle = from + (to - from) * Double(step) / Double(steps)
            let point = Self.point(center: center, radius: outer, degrees: angle)
            if step == 0 {
                path.move(to: point)
            } else {
                path.addLine(to: point)
            }
        }
        for step in stride(from: steps, through: 0, by: -1) {
            let angle = from + (to - from) * Double(step) / Double(steps)
            path.addLine(to: Self.point(center: center, radius: inner, degrees: angle))
        }
        path.closeSubpath()
        return path
    }

    /// Le point à `degrees` depuis midi, dans le sens horaire.
    static func point(center: CGPoint, radius: CGFloat, degrees: Double) -> CGPoint {
        let radians = degrees * .pi / 180
        return CGPoint(
            x: center.x + radius * CGFloat(sin(radians)),
            y: center.y - radius * CGFloat(cos(radians))
        )
    }
}

/// Des hachures diagonales, pour doubler la couleur de la zone de danger.
struct DiagonalHatch: Shape {
    let spacing: CGFloat

    func path(in rect: CGRect) -> Path {
        var path = Path()
        var offset = -rect.height
        while offset < rect.width {
            path.move(to: CGPoint(x: rect.minX + offset, y: rect.maxY))
            path.addLine(to: CGPoint(x: rect.minX + offset + rect.height, y: rect.minY))
            offset += spacing
        }
        return path
    }
}

extension TimeShareSlice: Identifiable {
    var id: Int {
        index
    }
}

/// L'anneau. Sans tranche : l'anneau VIDE et neutre de l'état « pas assez de données » (décor).
struct TimeShareDonut: View {
    let slices: [TimeShareSlice]
    let size: CGFloat

    /// L'épaisseur de l'anneau, en part du diamètre.
    static let thicknessRatio: CGFloat = 0.2

    private var thickness: CGFloat {
        size * Self.thicknessRatio
    }

    var body: some View {
        ZStack {
            if slices.isEmpty {
                Circle()
                    .strokeBorder(colorChartEmpty.color, lineWidth: thickness)
            }
            ForEach(slices) { slice in
                sliceView(slice)
            }
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }

    private func sliceView(_ slice: TimeShareSlice) -> some View {
        let shape = DonutSliceShape(
            start: slice.start,
            end: slice.end,
            thickness: thickness,
            gapDegrees: slices.count > 1 ? 2 : 0
        )
        return shape
            .fill(slice.style.color.color)
            .overlay {
                if slice.style.isHatched {
                    DiagonalHatch(spacing: 6)
                        .stroke(colorBgSurface.color.opacity(0.6), lineWidth: 1.5)
                        .clipShape(shape)
                }
            }
    }
}

/// La pastille de couleur d'une ligne de la légende : le même motif que la tranche (hachurée pour la zone de danger).
struct TimeShareSwatch: View {
    let style: TimeShareSliceStyle

    var body: some View {
        Circle()
            .fill(style.color.color)
            .frame(width: 12, height: 12)
            .overlay {
                if style.isHatched {
                    DiagonalHatch(spacing: 4)
                        .stroke(colorBgSurface.color.opacity(0.6), lineWidth: 1)
                        .clipShape(Circle())
                }
            }
            .accessibilityHidden(true)
    }
}
