import SwiftUI

extension Path {
    /// Dessine des instructions de chemin vectoriel, mises à l'échelle puis décalées.
    init(commands: [PathCommand], scale: CGFloat, origin: CGPoint) {
        self.init()
        func mapped(_ point: PathPoint) -> CGPoint {
            CGPoint(x: origin.x + CGFloat(point.px) * scale, y: origin.y + CGFloat(point.py) * scale)
        }
        for command in commands {
            switch command {
            case .move(let target):
                move(to: mapped(target))
            case .line(let target):
                addLine(to: mapped(target))
            case .curve(let control1, let control2, let end):
                addCurve(to: mapped(end), control1: mapped(control1), control2: mapped(control2))
            case .close:
                closeSubpath()
            }
        }
    }
}

/// Une forme vectorielle tracée dans un cadre de `width` x `height` unités, ajustée au cadre disponible (même échelle sur
/// les deux axes, centrée). Superposer plusieurs formes du même cadre garde leurs positions relatives.
struct VectorShape: Shape {
    let commands: [PathCommand]
    let width: Double
    let height: Double

    func path(in rect: CGRect) -> Path {
        guard width > 0, height > 0 else {
            return Path()
        }
        let scale = min(rect.width / CGFloat(width), rect.height / CGFloat(height))
        let origin = CGPoint(
            x: rect.minX + (rect.width - CGFloat(width) * scale) / 2,
            y: rect.minY + (rect.height - CGFloat(height) * scale) / 2
        )
        return Path(commands: commands, scale: scale, origin: origin)
    }
}

/// Le mot-symbole officiel « Miff » (lettres bleues, cœur jaune-vert sur le « i »), dessiné en vectoriel : net à toute
/// taille, couleurs prises dans les tokens du design system. Il se lit « Miff » pour le lecteur d'écran.
struct MiffWordmark: View {
    var body: some View {
        ZStack {
            VectorShape(
                commands: LogoPaths.wordmarkLetters,
                width: LogoPaths.wordmarkWidth,
                height: LogoPaths.wordmarkHeight
            )
            .fill(colorActionPrimary.color)
            VectorShape(
                commands: LogoPaths.wordmarkHeart,
                width: LogoPaths.wordmarkWidth,
                height: LogoPaths.wordmarkHeight
            )
            .fill(colorBrandPrimary.color)
        }
        .aspectRatio(CGFloat(LogoPaths.wordmarkWidth / LogoPaths.wordmarkHeight), contentMode: .fit)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("home_logo_a11y")
        .accessibilityAddTraits(.isHeader)
    }
}
