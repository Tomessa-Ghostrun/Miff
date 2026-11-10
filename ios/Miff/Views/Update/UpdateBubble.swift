import SwiftUI

// La bulle d'incitation (décision du PM, D21, 2026-11-06 ; design/screens/update.md §4) : la bulle des maquettes, de la couleur du niveau,
// une petite queue qui pointe vers le bouton central, AUCUN titre, AUCUN bouton, AUCUNE croix. Elle n'est pas interactive (le geste est
// le bouton central qu'elle désigne) et elle AVALE les touchers (rien dessous ne se déclenche par erreur). Ni son, ni vibration, ni
// clignotement, ni fenêtre bloquante. Le texte est celui des maquettes, mot pour mot.
//
// Lecteur d'écran (le ticket décide, §5.5 et A1, confirmé par le PM, D34) : annoncée poliment UNE fois à son apparition et au passage de
// l'orange au rouge, jamais à chaque minute (l'annonce est faite par la coquille, `UpdatePrompt.shouldAnnounce`) ; l'élément suivant est
// le bouton central, dont le nom parlé dit l'état. Hauteur : 40 % de l'écran au plus, le texte défile À L'INTÉRIEUR au-delà (design 4.3).

/// La queue : un triangle de 22 x 11 pt, pointe vers le bas.
struct BubbleTail: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.minX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.midX, y: rect.maxY))
        path.closeSubpath()
        return path
    }
}

struct IncentiveBubble: View {
    let level: PromptLevel
    /// Le plus que le corps de la bulle peut prendre : au-delà, le texte défile dans la bulle et ne masque jamais le Feed.
    let maxBodyHeight: CGFloat

    private var fill: Color {
        (level == .red ? colorStatusDangerBubble : colorStatusWarningBubble).color
    }

    private var text: String {
        L10n.string(level == .red ? "update_bubble_red" : "update_bubble_orange")
    }

    private var message: some View {
        Text(verbatim: text)
            .miffTextStyle(textBodyItalic)
            .foregroundColor(colorTextPrimary.color)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, UpdateLayout.bubbleTextInsetX)
            .padding(.vertical, Spacing.md)
    }

    var body: some View {
        if level != .hidden {
            VStack(spacing: 0) {
                ViewThatFits(in: .vertical) {
                    message
                    ScrollView {
                        message
                    }
                }
                .frame(maxHeight: maxBodyHeight)
                .background(RoundedRectangle(cornerRadius: UpdateLayout.bubbleCorner, style: .continuous).fill(fill))
                // Le centre de l'écran : là où est le bouton central (les trois entrées de la barre sont symétriques, dans les deux sens
                // d'écriture).
                BubbleTail()
                    .fill(fill)
                    .frame(width: UpdateLayout.tailWidth, height: UpdateLayout.tailHeight)
                    .accessibilityHidden(true)
                Color.clear.frame(height: UpdateLayout.tailGap)
            }
            .padding(.horizontal, UpdateLayout.bubbleSideMargin)
            // Avale les touchers : rien de ce qui est dessous ne se déclenche.
            .contentShape(Rectangle())
            .onTapGesture {}
            .accessibilityElement(children: .combine)
            .accessibilityLabel(Text(verbatim: text))
        }
    }
}

/// Le message calme après une publication : une pastille sombre, sans son ni vibration, annoncée poliment au lecteur d'écran. Elle part
/// d'elle-même (la coquille la retire après 3 secondes).
struct UpdateConfirmation: View {
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency
    let text: String

    var body: some View {
        Text(verbatim: text)
            .miffTextStyle(textBodyRegular14)
            .foregroundColor(colorUpdateOnPhoto.color)
            .padding(.horizontal, Spacing.md)
            .padding(.vertical, Spacing.sm + Spacing.xs)
            .background(
                RoundedRectangle(cornerRadius: 22, style: .continuous)
                    .fill(updateToastToken(reduceTransparency: reduceTransparency).color)
            )
            .accessibilityElement(children: .combine)
    }
}
