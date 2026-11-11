import SwiftUI
import UIKit

// La bulle de la Miff (maquettes 02 à 05) : UNE tache, de la couleur de la Miff, sous l'en-tête. Elle INFORME, elle n'alarme pas :
// heures absolues pour l'orange et le rouge, aucun son, aucun défilement automatique, aucun point d'exclamation, jamais « en
// sécurité ». AUCUN pictogramme dans la bulle, quelle que soit sa couleur (décision du PM, 2026-11-11) : les mots et la couleur suffisent. La phrase principale est en Baloo Thambi 2 ExtraBold 30 / 36 (`textDisplayBubble`). Plusieurs alertes forment un carrousel (les rouges
// d'abord), avec des pastilles seulement quand il y a plusieurs pages, et « Alerte précédente / suivante » pour VoiceOver.

/// Une tache douce et organique (une courbe fermée lisse par dix points), comme la tache des maquettes du PM. Symétrique en
/// droite-à-gauche.
struct FeedBubbleShape: Shape {
    private static let points: [CGPoint] = [
        CGPoint(x: 0.50, y: 0.02), CGPoint(x: 0.80, y: 0.07), CGPoint(x: 0.98, y: 0.30), CGPoint(x: 0.97, y: 0.62),
        CGPoint(x: 0.80, y: 0.94), CGPoint(x: 0.50, y: 0.99), CGPoint(x: 0.20, y: 0.93), CGPoint(x: 0.03, y: 0.64),
        CGPoint(x: 0.03, y: 0.30), CGPoint(x: 0.20, y: 0.07)
    ]

    func path(in rect: CGRect) -> Path {
        let scaled = Self.points.map { CGPoint(x: rect.minX + $0.x * rect.width, y: rect.minY + $0.y * rect.height) }
        var path = Path()
        guard let first = scaled.first, let last = scaled.last else {
            return path
        }
        path.move(to: Self.middle(last, first))
        for (index, point) in scaled.enumerated() {
            path.addQuadCurve(to: Self.middle(point, scaled[(index + 1) % scaled.count]), control: point)
        }
        path.closeSubpath()
        return path
    }

    private static func middle(_ from: CGPoint, _ other: CGPoint) -> CGPoint {
        CGPoint(x: (from.x + other.x) / 2, y: (from.y + other.y) / 2)
    }
}

/// Ce que font les boutons de la bulle.
struct BubbleActions {
    let onMessage: (String) -> Void
    let onResumeSharing: () -> Void
}

struct BubbleView: View {
    let bubble: Bubble
    let announceKey: String
    let texts: FeedTexts
    /// Le mouvement n'existe que si la bulle est à l'écran ET l'app au premier plan ET l'onglet en usage.
    let isActive: Bool
    let actions: BubbleActions
    /// MODE VOYAGE : la ligne « {Prénom} est en voyage jusqu'au 15 nov. » sous la bulle habituelle (nil : aucune, ou la bulle le dit déjà).
    var travelNote: TravelNote?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        content
            .onChange(of: announceKey) { _ in
                // La bulle est une région annoncée SEULEMENT quand le niveau (ou l'ensemble des alertes) change, jamais à chaque minute.
                UIAccessibility.post(
                    notification: .announcement, argument: texts.announcement(bubble, travelNote: travelNote)
                )
            }
    }

    private var motion: Bool {
        BubbleMotion.enabled(reduceMotion: reduceMotion, active: isActive)
    }

    @ViewBuilder
    private var content: some View {
        if case .alerts(let pages, _) = bubble {
            VStack(spacing: Spacing.sm) {
                AlertCarousel(pages: pages, texts: texts, motion: motion, onMessage: actions.onMessage)
                // MODE VOYAGE : les autres membres en voyage, sous les alertes.
                if let travelNote {
                    Text(verbatim: texts.travelNote(travelNote))
                        .miffTextStyle(textBodyRegular14)
                        .foregroundColor(colorTextDescription.color)
                        .multilineTextAlignment(.center)
                        .fixedSize(horizontal: false, vertical: true)
                        .frame(maxWidth: .infinity)
                }
            }
        } else {
            BubbleBlob(level: bubble.level, motion: motion) {
                CalmBubbleLines(
                    bubble: bubble, texts: texts, travelNote: travelNote, onResumeSharing: actions.onResumeSharing
                )
            }
        }
    }
}

/// Les lignes d'une bulle calme (tout va bien, pas encore de nouvelles, seul, partage arrêté, accord attendu).
private struct CalmBubbleLines: View {
    let bubble: Bubble
    let texts: FeedTexts
    var travelNote: TravelNote?
    let onResumeSharing: () -> Void

    var body: some View {
        lines
        // MODE VOYAGE : les autres membres en voyage, une ligne de plus sous le message habituel.
        if let travelNote {
            Line(text: texts.travelNote(travelNote))
        }
    }

    @ViewBuilder
    private var lines: some View {
        switch bubble {
        case .allGood(let latestNews, let answered, let total):
            Headline(key: "feed_bubble_all_good")
            if let latestNews {
                Line(text: texts.latestNews(latestNews))
            }
            if let answered, let total {
                Caption(text: texts.answered(answered, of: total))
            }
        case .noNews:
            Headline(key: "feed_bubble_no_news_title")
            Line(text: L10n.string("feed_bubble_no_news_body"))
        case .alone:
            Headline(key: "feed_bubble_alone")
        case .awaitingAgreement:
            Headline(key: "feed_bubble_no_news_title")
            Line(text: L10n.string("miff_waiting_feed_note"))
        case .sharingStopped:
            Headline(key: "feed_bubble_sharing_stopped_title")
            Line(text: L10n.string("feed_bubble_sharing_stopped_body"))
            BubbleButton(key: "feed_bubble_resume_action", action: onResumeSharing)
        case .travelers(let note):
            Headline(text: texts.travelersHeadline(note))
        case .alerts:
            EmptyView()
        }
    }
}

/// La tache (D45) : le contenu est centré dans la forme (voir `BubbleLayout`) ; la FORME seule est écrasée / étirée (les deux axes en déphasage) et s'incline d'une fraction de
/// degré ; le texte et le bouton ne sont PAS déformés, ils suivent seulement la dérive verticale de toute la bulle ; aucun pictogramme n'est posé
/// dessus. Voir `BubbleMotion` (constantes nommées). Immobile avec « Réduire les animations ».
private struct BubbleBlob<Content: View>: View {
    let level: StatusLevel
    let motion: Bool
    @ViewBuilder let content: () -> Content

    var body: some View {
        // En pause (animations réduites, bulle hors écran ou app en arrière-plan) la vue est dessinée UNE fois : aucun rendu continu.
        TimelineView(.animation(minimumInterval: 1.0 / 30.0, paused: !motion)) { timeline in
            let seconds = timeline.date.timeIntervalSinceReferenceDate
            let blob = BubbleMotion.phase(at: seconds, period: BubbleMotion.blobPeriodSeconds)
            // Le contenu est CENTRÉ dans la tache (retour de recette du PM, 2026-11-08) : marges égales à gauche et à droite, en haut et
            // en bas ; texte centré ligne par ligne ; zone plus haute que lui : il reste au milieu. Voir `BubbleLayout`.
            VStack(alignment: .center, spacing: Spacing.sm) {
                content()
            }
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity, minHeight: BubbleLayout.minContentHeight, alignment: .center)
            .padding(.horizontal, BubbleLayout.horizontalInset)
            .padding(.vertical, BubbleLayout.verticalInset)
            .background(
                FeedBubbleShape()
                    .fill(level.tint)
                    .scaleEffect(
                        x: motion ? BubbleMotion.scaleX(blob) : 1,
                        y: motion ? BubbleMotion.scaleY(blob) : 1
                    )
                    .rotationEffect(.degrees(motion ? BubbleMotion.tilt(blob) : 0))
                    .accessibilityHidden(true)
            )
            .offset(y: motion ? BubbleMotion.drift(blob) : 0)
        }
    }
}

// MARK: - Les alertes

/// Le carrousel d'alertes : UNE page à la fois (les rouges d'abord), un balayage horizontal ou les boutons du lecteur d'écran pour
/// en changer, des pastilles seulement avec plusieurs pages, JAMAIS de défilement automatique. La page choisie se retient par le
/// MEMBRE (quand un orange devient rouge l'ordre change : la page reste celle de la même personne).
private struct AlertCarousel: View {
    let pages: [AlertPage]
    let texts: FeedTexts
    let motion: Bool
    let onMessage: (String) -> Void
    @State private var selectedUid: String?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var index: Int {
        pages.firstIndex { $0.member.uid == selectedUid } ?? 0
    }

    var body: some View {
        let current = pages[min(index, pages.count - 1)]
        VStack(spacing: Spacing.sm) {
            BubbleBlob(level: current.level, motion: motion) {
                AlertLines(page: current, texts: texts, onMessage: onMessage)
            }
            .id(current.member.uid)
            if pages.count > 1 {
                PagerDots(count: pages.count, current: index, level: current.level, select: select)
            }
        }
        .contentShape(Rectangle())
        .simultaneousGesture(swipe)
    }

    private var swipe: some Gesture {
        DragGesture(minimumDistance: 24).onEnded { value in
            guard pages.count > 1, abs(value.translation.width) > abs(value.translation.height) else {
                return
            }
            select(index + (value.translation.width < 0 ? 1 : -1))
        }
    }

    private func select(_ target: Int) {
        guard target >= 0, target < pages.count, target != index else {
            return
        }
        // Le changement de page est instantané avec « Réduire les animations ».
        let uid = pages[target].member.uid
        if reduceMotion {
            selectedUid = uid
        } else {
            withAnimation(.easeInOut(duration: 0.2)) {
                selectedUid = uid
            }
        }
    }
}

private struct AlertLines: View {
    let page: AlertPage
    let texts: FeedTexts
    let onMessage: (String) -> Void

    var body: some View {
        Headline(text: texts.alertTitle(page))
        if let detail = texts.alertDetail(page) {
            Line(text: detail)
        }
        if let note = texts.alertNote(page) {
            Caption(text: note)
        }
        if let offline = texts.offlineAlert(page) {
            Caption(text: offline)
        }
        if page.canMessage, let phone = page.member.phone {
            BubbleButton(
                key: "feed_bubble_message_action",
                spokenLabel: "\(L10n.string("feed_bubble_message_action")) \(page.member.callName)"
            ) { onMessage(phone) }
        }
    }
}

// MARK: - Les petits éléments

private struct Headline: View {
    private let text: Text

    init(key: LocalizedStringKey) {
        text = Text(key)
    }

    init(text: String) {
        self.text = Text(verbatim: text)
    }

    var body: some View {
        text
            .miffTextStyle(textDisplayBubble)
            .foregroundColor(colorTextPrimary.color)
            .multilineTextAlignment(.center)
            .fixedSize(horizontal: false, vertical: true)
            .accessibilityAddTraits(.isHeader)
    }
}

private struct Line: View {
    let text: String

    var body: some View {
        Text(verbatim: text)
            .miffTextStyle(textBodyRegular14)
            .foregroundColor(colorTextPrimary.color)
            .fixedSize(horizontal: false, vertical: true)
    }
}

private struct Caption: View {
    let text: String

    var body: some View {
        Text(verbatim: text)
            .miffTextStyle(textBodyCaption)
            .foregroundColor(colorTextDescription.color)
            .fixedSize(horizontal: false, vertical: true)
    }
}

/// L'action douce d'une bulle : contour bleu, fond blanc, un peu plus PETITE qu'avant (texte 14 pt, marges réduites) mais avec une zone de
/// toucher d'au moins 44 pt. TEXTE SEUL, sans pictogramme (décision du PM, 2026-11-11). Jamais un appel, jamais un bouton rouge. Le libellé
/// est lu tel quel par VoiceOver, complété du prénom quand l'action vise quelqu'un (« Contacter Thomas »).
private struct BubbleButton: View {
    let key: LocalizedStringKey
    var spokenLabel: String?
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(key)
                .miffTextStyle(textBodyBold14)
                .foregroundColor(colorActionPrimary.color)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.horizontal, Spacing.md - Spacing.xs)
                .padding(.vertical, Spacing.sm)
                .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorBgSurface.color))
                .overlay(
                    RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                        .stroke(colorBorderAction.color, lineWidth: strokeAccent)
                )
                .frame(minHeight: Spacing.minTouchTarget)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(spokenLabel.map { Text(verbatim: $0) } ?? Text(key))
    }
}

/// Seulement avec plusieurs pages. La page courante est une pastille PLUS LONGUE (une forme, pas seulement une couleur). C'est aussi
/// l'élément que VoiceOver atteint pour changer de page SANS balayage : il annonce « Page 1 sur 2 », se règle d'un glissement vers le
/// haut ou le bas du doigt (action ajustable) et propose « Alerte précédente » / « Alerte suivante » dans les actions.
private struct PagerDots: View {
    let count: Int
    let current: Int
    let level: StatusLevel
    let select: (Int) -> Void

    private var spokenPage: String {
        L10n.format("feed_bubble_page_a11y", current + 1, count)
    }

    var body: some View {
        dots
            .frame(maxWidth: .infinity, minHeight: Spacing.minTouchTarget)
            .contentShape(Rectangle())
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(Text(verbatim: spokenPage))
            .accessibilityAdjustableAction(adjust)
            .accessibilityAction(named: Text("feed_bubble_previous_a11y"), goToPrevious)
            .accessibilityAction(named: Text("feed_bubble_next_a11y"), goToNext)
    }

    private var dots: some View {
        HStack(spacing: Spacing.sm) {
            ForEach(0..<count, id: \.self) { position in
                dot(selected: position == current)
            }
        }
    }

    private func dot(selected: Bool) -> some View {
        let tint: Color = selected ? level.onTint : colorStatusNeutral.color
        let width: CGFloat = selected ? 22 : 8
        return Capsule()
            .fill(tint)
            .frame(width: width, height: 8)
    }

    private func adjust(_ direction: AccessibilityAdjustmentDirection) {
        switch direction {
        case .increment:
            goToNext()
        case .decrement:
            goToPrevious()
        @unknown default:
            break
        }
    }

    private func goToPrevious() {
        select(current - 1)
    }

    private func goToNext() {
        select(current + 1)
    }
}
