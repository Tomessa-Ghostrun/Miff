import SwiftUI
import UIKit

// La bulle de la Miff (maquettes 02 à 05) : UNE tache, de la couleur de la Miff, sous l'en-tête. Elle INFORME, elle n'alarme pas :
// heures absolues pour l'orange et le rouge, aucun son, aucun défilement automatique, aucun point d'exclamation, jamais « en
// sécurité ». Un pictogramme est toujours là (la couleur n'est jamais seule). Plusieurs alertes forment un carrousel (les rouges
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
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        content
            .onChange(of: announceKey) { _ in
                // La bulle est une région annoncée SEULEMENT quand le niveau (ou l'ensemble des alertes) change, jamais à chaque minute.
                UIAccessibility.post(notification: .announcement, argument: texts.announcement(bubble))
            }
    }

    private var motion: Bool {
        BubbleMotion.enabled(reduceMotion: reduceMotion, active: isActive)
    }

    @ViewBuilder
    private var content: some View {
        if case .alerts(let pages, _) = bubble {
            AlertCarousel(pages: pages, texts: texts, motion: motion, onMessage: actions.onMessage)
        } else {
            BubbleBlob(level: bubble.level, pictogram: BubblePictogram.of(bubble), motion: motion) {
                CalmBubbleLines(bubble: bubble, texts: texts, onResumeSharing: actions.onResumeSharing)
            }
        }
    }
}

/// Un pictogramme par couleur (la couleur n'est jamais seule) : l'étoile pour « tout va bien », une horloge, un « ! », un sablier.
private enum BubblePictogram {
    static func of(_ bubble: Bubble) -> String {
        if case .allGood = bubble {
            return "sparkle"
        }
        return of(level: bubble.level)
    }

    static func of(level: StatusLevel) -> String {
        switch level {
        case .orange:
            return "clock"
        case .red:
            return "exclamationmark"
        case .green:
            return "sparkle"
        case .neutral:
            return "hourglass"
        }
    }
}

/// Les lignes d'une bulle calme (tout va bien, pas encore de nouvelles, seul, partage arrêté, accord attendu).
private struct CalmBubbleLines: View {
    let bubble: Bubble
    let texts: FeedTexts
    let onResumeSharing: () -> Void

    var body: some View {
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
            BubbleButton(key: "feed_bubble_resume_action", symbol: "mappin.and.ellipse", action: onResumeSharing)
        case .alerts:
            EmptyView()
        }
    }
}

/// La tache et son pictogramme (D45) : la FORME seule est écrasée / étirée (les deux axes en déphasage) et s'incline d'une fraction de
/// degré ; le texte et le bouton ne sont PAS déformés, ils suivent seulement la dérive verticale de toute la bulle ; le disque du
/// pictogramme respire sur son propre cycle. Voir `BubbleMotion` (constantes nommées). Immobile avec « Réduire les animations ».
private struct BubbleBlob<Content: View>: View {
    let level: StatusLevel
    let pictogram: String
    let motion: Bool
    @ViewBuilder let content: () -> Content

    private static var minHeight: CGFloat { 176 }

    var body: some View {
        // En pause (animations réduites, bulle hors écran ou app en arrière-plan) la vue est dessinée UNE fois : aucun rendu continu.
        TimelineView(.animation(minimumInterval: 1.0 / 30.0, paused: !motion)) { timeline in
            let seconds = timeline.date.timeIntervalSinceReferenceDate
            let blob = BubbleMotion.phase(at: seconds, period: BubbleMotion.blobPeriodSeconds)
            let star = BubbleMotion.phase(at: seconds, period: BubbleMotion.starPeriodSeconds)
            VStack(alignment: .leading, spacing: Spacing.sm) {
                content()
            }
            .frame(maxWidth: .infinity, minHeight: Self.minHeight, alignment: .topLeading)
            .padding(.leading, 40)
            .padding(.trailing, 56)
            .padding(.top, 40)
            .padding(.bottom, 46)
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
            .overlay(alignment: .bottomTrailing) {
                PictogramDisc(symbol: pictogram, level: level)
                    .scaleEffect(motion ? BubbleMotion.starScale(star) : 1)
                    .offset(y: motion ? BubbleMotion.starDrift(star) : 0)
                    .padding(.trailing, 40)
                    .padding(.bottom, 40)
            }
            .offset(y: motion ? BubbleMotion.drift(blob) : 0)
        }
    }
}

private struct PictogramDisc: View {
    let symbol: String
    let level: StatusLevel

    var body: some View {
        Image(systemName: symbol)
            .font(.system(size: 20, weight: .semibold))
            .foregroundColor(level == .neutral ? colorTextDescription.color : level.onTint)
            .frame(width: 40, height: 40)
            .background(Circle().fill(Color.white.opacity(0.7)))
            .accessibilityHidden(true)
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
            BubbleBlob(level: current.level, pictogram: BubblePictogram.of(level: current.level), motion: motion) {
                AlertLines(page: current, texts: texts, onMessage: onMessage)
            }
            .id(current.member.uid)
            if pages.count > 1 {
                PagerDots(count: pages.count, current: index, level: current.level)
            }
        }
        .contentShape(Rectangle())
        .simultaneousGesture(swipe)
        .accessibilityElement(children: .contain)
        .modifier(PagerAccessibility(count: pages.count, index: index, select: select))
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

/// « Alerte 1 sur 2 » et les actions « Alerte précédente / suivante » : chaque page est atteignable sans balayage.
private struct PagerAccessibility: ViewModifier {
    let count: Int
    let index: Int
    let select: (Int) -> Void

    func body(content: Content) -> some View {
        if count > 1 {
            content
                .accessibilityValue(Text(verbatim: L10n.format("feed_bubble_page_a11y", index + 1, count)))
                .accessibilityAction(named: Text("feed_bubble_previous_a11y")) { select(index - 1) }
                .accessibilityAction(named: Text("feed_bubble_next_a11y")) { select(index + 1) }
        } else {
            content
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
            BubbleButton(key: "feed_bubble_message_action", symbol: "message") { onMessage(phone) }
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
            .miffTextStyle(textDisplayHeadline)
            .foregroundColor(colorTextPrimary.color)
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

/// L'action douce d'une bulle : contour bleu, fond blanc, 44 pt de haut. Jamais un appel, jamais un bouton rouge.
private struct BubbleButton: View {
    let key: LocalizedStringKey
    let symbol: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: Spacing.sm) {
                Image(systemName: symbol)
                    .font(.system(size: 18))
                Text(key)
                    .miffTextStyle(textBodyBold16)
            }
            .foregroundColor(colorActionPrimary.color)
            .padding(.horizontal, Spacing.md)
            .frame(minHeight: Spacing.minTouchTarget)
            .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorBgSurface.color))
            .overlay(
                RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                    .stroke(colorBorderAction.color, lineWidth: strokeAccent)
            )
        }
        .buttonStyle(.plain)
    }
}

/// Seulement avec plusieurs pages. La page courante est une pastille PLUS LONGUE (une forme, pas seulement une couleur).
private struct PagerDots: View {
    let count: Int
    let current: Int
    let level: StatusLevel

    var body: some View {
        HStack(spacing: Spacing.sm) {
            ForEach(0..<count, id: \.self) { position in
                let selected = position == current
                Capsule()
                    .fill(selected ? level.onTint : colorStatusNeutral.color)
                    .frame(width: selected ? 22 : 8, height: 8)
            }
        }
        .frame(maxWidth: .infinity)
        .accessibilityHidden(true)
    }
}
