import SwiftUI

// Les miniatures des membres et les pastilles de pagination du carrousel de Miffs (séparées de `ProfileMiffCarousel.swift` : une taille de fichier
// raisonnable). Rien ici ne reçoit de geste : le carrousel décide seul du toucher et du glissement.

/// La rangée de miniatures : la photo de chaque membre (ou ses initiales), « +N » pour les suivants. Décorative : le nombre de membres
/// est dit par le texte de la carte. Pendant la lecture, la place est gardée (des pastilles pâles) pour que la carte ne saute pas.
struct MemberThumbsRow: View {
    let members: MiffCardMembers
    let maxVisible: Int
    @ScaledMetric(relativeTo: .body) private var side: CGFloat = 28

    private var diameter: CGFloat {
        min(side, 44)
    }

    var body: some View {
        switch members {
        case .known(let preview):
            thumbs(of: preview)
        case .loading:
            HStack(spacing: Spacing.xs) {
                ForEach(0..<3, id: \.self) { _ in
                    Circle()
                        .fill(colorActionPrimaryTint20.color)
                        .frame(width: diameter, height: diameter)
                }
            }
            .accessibilityHidden(true)
        case .unavailable:
            EmptyView()
        }
    }

    private func thumbs(of preview: MemberPreview) -> some View {
        let layout = MemberStack.layout(total: preview.count, maxVisible: maxVisible)
        return HStack(spacing: Spacing.xs) {
            ForEach(Array(preview.thumbs.prefix(layout.visible)), id: \.uid) { thumb in
                MiffAvatar(jpeg: thumb.avatarJpeg, initials: thumb.initials, size: diameter)
                    .overlay(Circle().stroke(colorActionPrimary.color, lineWidth: strokeHairline))
            }
            if layout.extra > 0 {
                Text(verbatim: "+\(layout.extra)")
                    .miffTextStyle(textCaptionBold12)
                    .foregroundColor(colorActionPrimary.color)
                    .lineLimit(1)
                    .minimumScaleFactor(0.6)
                    .frame(width: diameter, height: diameter)
                    .background(Circle().fill(colorActionPrimaryTint20.color))
                    .overlay(Circle().stroke(colorActionPrimary.color, lineWidth: strokeHairline))
            }
        }
        .accessibilityHidden(true)
    }
}

// MARK: - La pagination

/// Les pastilles (la page courante est plus LONGUE : une forme, pas seulement une couleur). C'est l'élément que VoiceOver règle pour changer
/// de carte SANS balayage : « Carte 1 sur 3 », glissement vers le haut ou le bas du doigt.
struct CarouselDots: View {
    let count: Int
    let current: Int
    let select: (Int) -> Void

    private var spokenPage: String {
        L10n.format("profile_tab_carousel_page_a11y", current + 1, count)
    }

    var body: some View {
        HStack(spacing: Spacing.sm) {
            ForEach(0..<count, id: \.self) { position in
                Capsule()
                    .fill((position == current ? colorActionPrimary : colorActionPrimaryTint20).color)
                    .frame(width: position == current ? 22 : 8, height: 8)
            }
        }
        .frame(maxWidth: .infinity, minHeight: Spacing.minTouchTarget)
        .contentShape(Rectangle())
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: spokenPage))
        .accessibilityAdjustableAction { direction in
            switch direction {
            case .increment:
                select(current + 1)
            case .decrement:
                select(current - 1)
            @unknown default:
                break
            }
        }
        .accessibilityAction(named: Text("profile_tab_carousel_previous_a11y")) { select(current - 1) }
        .accessibilityAction(named: Text("profile_tab_carousel_next_a11y")) { select(current + 1) }
    }
}
