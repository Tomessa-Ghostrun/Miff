import SwiftUI

/// Un morceau de confettis, avec tout ce qui est fixé au départ (les positions sont des fractions de la largeur et de la hauteur de l'écran).
struct ConfettiPiece: Equatable {
    let startX: Double
    /// La part de l'animation qui passe avant que ce morceau commence à tomber.
    let delay: Double
    /// 1 = atteint le bas de l'écran exactement à la fin ; plus = tombe plus vite.
    let speed: Double
    let drift: Double
    let phase: Double
    let size: Double
    /// Les tours faits sur toute l'animation (négatif = dans l'autre sens).
    let spin: Double
    let colorIndex: Int
    let round: Bool
}

/// Un petit générateur déterministe (SplitMix64) : les mêmes confettis à chaque fois pour une même graine, donc des maths testables.
struct ConfettiRandom {
    private var state: UInt64

    init(seed: UInt64) {
        state = seed
    }

    /// Un nombre dans [0, 1).
    mutating func next() -> Double {
        state &+= 0x9E37_79B9_7F4A_7C15
        var value = state
        value = (value ^ (value >> 30)) &* 0xBF58_476D_1CE4_E5B9
        value = (value ^ (value >> 27)) &* 0x94D0_49BB_1331_11EB
        value ^= value >> 31
        return Double(value >> 11) / Double(1 << 53)
    }
}

/// Les maths de la pluie de confettis, à part du dessin pour qu'elles se testent (aucune horloge, aucun hasard propre : `pieces` reçoit une graine). Miroir de `Confetti`
/// d'Android : 56 morceaux, 3,2 secondes, une seule fois.
enum Confetti {
    static let count = 56
    static let duration: TimeInterval = 3.2
    private static let startY = -0.05
    private static let margin = 0.1
    private static let sway = 0.02
    private static let fadeFrom = 0.85

    static func pieces(seed: UInt64 = 7, count: Int = Confetti.count) -> [ConfettiPiece] {
        var random = ConfettiRandom(seed: seed)
        return (0..<count).map { _ in
            ConfettiPiece(
                startX: random.next(),
                delay: random.next() * 0.3,
                speed: 1 + random.next() * 0.35,
                drift: (random.next() - 0.5) * 0.2,
                phase: random.next() * 2 * Double.pi,
                size: 6 + random.next() * 6,
                spin: (random.next() - 0.5) * 6,
                colorIndex: Int(random.next() * 5),
                round: random.next() < 0.25
            )
        }
    }

    /// Quelle part de sa chute le morceau a faite à `progress` (0 à 1 de l'animation) ; nil tant qu'il n'a pas commencé.
    static func fall(_ piece: ConfettiPiece, _ progress: Double) -> Double? {
        guard progress > piece.delay else {
            return nil
        }
        return min(max((progress - piece.delay) / (1 - piece.delay), 0), 1)
    }

    /// La position verticale, en fraction de la hauteur de l'écran (commence au-dessus, finit en dessous) ; nil tant que le morceau n'a pas commencé.
    static func y(of piece: ConfettiPiece, at progress: Double) -> Double? {
        fall(piece, progress).map { startY + $0 * piece.speed * (1 - startY + margin) }
    }

    /// La position horizontale, en fraction de la largeur : une lente dérive et un petit balancement.
    static func x(of piece: ConfettiPiece, at progress: Double) -> Double {
        let part = fall(piece, progress) ?? 0
        return piece.startX + piece.drift * part + sway * sin(part * 2 * Double.pi * 2 + piece.phase)
    }

    /// Pleinement visible, puis il s'efface pendant la dernière partie de sa chute.
    static func alpha(of piece: ConfettiPiece, at progress: Double) -> Double {
        guard let part = fall(piece, progress) else {
            return 0
        }
        return part < fadeFrom ? 1 : min(max((1 - part) / (1 - fadeFrom), 0), 1)
    }
}

/// Une courte pluie de confettis sur tout l'écran, dessinée avec un `Canvas` (aucune bibliothèque, aucune image) : une seule fois par affichage. Elle ne prend aucun toucher et ne
/// dit rien à VoiceOver. L'appelant ne la montre pas du tout quand la personne a réduit les animations : alors il n'y a pas de confettis, seulement la confirmation.
struct PaywallConfetti: View {
    @State private var finished = false
    @State private var startedAt = Date()
    private let pieces = Confetti.pieces()

    var body: some View {
        ZStack {
            if !finished {
                TimelineView(.animation) { context in
                    Canvas { graphics, size in
                        draw(on: graphics, size: size, progress: progress(at: context.date))
                    }
                }
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
        .task {
            try? await Task.sleep(nanoseconds: UInt64(Confetti.duration * 1_000_000_000))
            finished = true
        }
    }

    private func progress(at date: Date) -> Double {
        min(max(date.timeIntervalSince(startedAt) / Confetti.duration, 0), 1)
    }

    private var palette: [Color] {
        [
            colorBrandPrimary.color, colorActionPrimary.color, colorStatusWarning.color, colorStatusDanger.color,
            colorActionPrimaryTint20.color
        ]
    }

    private func draw(on context: GraphicsContext, size: CGSize, progress: Double) {
        let colors = palette
        for piece in pieces {
            guard let fall = Confetti.y(of: piece, at: progress) else {
                continue
            }
            let width = CGFloat(piece.size)
            let height = piece.round ? width : width * 0.5
            var layer = context
            layer.opacity = Confetti.alpha(of: piece, at: progress)
            let across = CGFloat(Confetti.x(of: piece, at: progress)) * size.width
            layer.translateBy(x: across, y: CGFloat(fall) * size.height)
            let color = colors[piece.colorIndex % colors.count]
            if piece.round {
                let disc = CGRect(x: -width / 2, y: -width / 2, width: width, height: width)
                layer.fill(Path(ellipseIn: disc), with: .color(color))
            } else {
                layer.rotate(by: .degrees(piece.spin * progress * 360))
                let strip = CGRect(x: -width / 2, y: -height / 2, width: width, height: height)
                layer.fill(Path(strip), with: .color(color))
            }
        }
    }
}
