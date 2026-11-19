import SwiftUI

// L'indicateur de « tirer pour actualiser » (voir `FeedPullIndicatorLogic`) : une pastille ronde posée SOUS l'en-tête, au-dessus de tout (dernier
// enfant de la mise en page, z élevé, jamais dans une zone coupée), qui se remplit avec la distance tirée, tourne pendant l'actualisation, et
// dont la couleur ne dépend pas du fond. Aucune donnée n'est lue ni gardée : seulement des nombres de points et trois drapeaux.

/// Ce que la vue de défilement dit à l'indicateur, à chaque mouvement : la distance tirée, un doigt tire-t-il, la roue d'UIKit tourne-t-elle.
/// (Pas d'isolation d'acteur : appelé par les rappels de la vue de défilement, sur le fil principal, comme `FeedHeaderModel`.)
final class FeedPullIndicatorModel: ObservableObject {
    @Published private(set) var state: FeedPullIndicatorState = .hidden

    /// Une horloge monotone, remplaçable dans les tests.
    var uptime: () -> TimeInterval = { ProcessInfo.processInfo.systemUptime }

    private var logic = FeedPullIndicatorLogic()
    private var distance: CGFloat = 0
    private var dragging = false
    private var uikitRefreshing = false
    private var feedRefreshing = false
    private var recheckScheduled = false

    func pulled(distance: CGFloat, dragging: Bool, uikitRefreshing: Bool) {
        self.distance = max(distance, 0)
        self.dragging = dragging
        self.uikitRefreshing = uikitRefreshing
        apply()
    }

    /// L'état « actualisation » du Feed (`FeedViewModel.refreshing`).
    func feedRefreshingChanged(_ value: Bool) {
        feedRefreshing = value
        apply()
    }

    private func apply() {
        let now = uptime()
        let next = logic.update(
            distance: distance, dragging: dragging, refreshing: uikitRefreshing || feedRefreshing, now: now
        )
        if next != state {
            state = next
        }
        scheduleRecheck(now: now)
    }

    /// La fin du minimum d'affichage arrive sans aucun mouvement de la liste : l'indicateur se retire tout seul.
    private func scheduleRecheck(now: TimeInterval) {
        guard !recheckScheduled, let wait = logic.secondsUntilChange(now: now) else {
            return
        }
        recheckScheduled = true
        DispatchQueue.main.asyncAfter(deadline: .now() + wait + 0.02) { [weak self] in
            self?.recheckScheduled = false
            self?.apply()
        }
    }
}

enum FeedPullIndicatorMetrics {
    static let size: CGFloat = 44
    static let ring: CGFloat = 3
    static let fadeSeconds: Double = 0.12
    /// La pastille descend un peu avec le tirage (au plus ceci) : elle suit le doigt ; la roue, elle, est posée sans décalage.
    static let followPoints: CGFloat = 14
}

struct FeedPullIndicator: View {
    @ObservedObject var model: FeedPullIndicatorModel
    /// `FeedViewModel.refreshing` : le signal le plus direct, toujours pris en compte.
    let refreshing: Bool
    /// Les captures de la CI forcent un état (nil : l'état réel).
    let forced: FeedPullIndicatorState?
    /// Le bas de l'en-tête AU REPOS, dans la fenêtre (`HeaderRestBottomKey`) : la pastille se pose juste dessous. CAUSE trouvée sur la capture de CI : la hauteur
    /// de l'en-tête seule ne comptait pas la zone sûre du haut, la pastille tombait sur le logo. Le conteneur ignore la zone sûre : ses points sont ceux de la fenêtre.
    let headerBottom: CGFloat
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var state: FeedPullIndicatorState {
        if let forced {
            return forced
        }
        return refreshing ? .loading : model.state
    }

    var body: some View {
        ZStack(alignment: .top) {
            if state.isShown {
                pill
                    .padding(.top, headerBottom + Spacing.sm + follow)
                    .transition(.opacity)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .ignoresSafeArea(edges: .top)
        .animation(reduceMotion ? nil : .easeOut(duration: FeedPullIndicatorMetrics.fadeSeconds), value: state.isShown)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
        .zIndex(100)
    }

    private var follow: CGFloat {
        if case .pulling(let progress) = state {
            return CGFloat(progress) * FeedPullIndicatorMetrics.followPoints
        }
        return 0
    }

    private var pill: some View {
        ZStack {
            Circle().fill(colorBgSurface.color)
            Circle().stroke(colorBorderStrong.color, lineWidth: strokeHairline)
            content
        }
        .frame(width: FeedPullIndicatorMetrics.size, height: FeedPullIndicatorMetrics.size)
        .miffShadow(shadowDefault)
    }

    @ViewBuilder
    private var content: some View {
        switch state {
        case .hidden:
            EmptyView()
        case .pulling(let progress):
            ZStack {
                Circle()
                    .trim(from: 0, to: CGFloat(progress))
                    .stroke(
                        colorActionPrimary.color,
                        style: StrokeStyle(lineWidth: FeedPullIndicatorMetrics.ring, lineCap: .round)
                    )
                    .rotationEffect(.degrees(-90))
                    .padding(FeedPullIndicatorMetrics.ring)
                Image(systemName: "arrow.down")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(colorActionPrimary.color)
                    .rotationEffect(.degrees(progress >= 1 ? 180 : 0))
            }
        case .loading:
            ProgressView()
                .tint(colorActionPrimary.color)
        }
    }
}
