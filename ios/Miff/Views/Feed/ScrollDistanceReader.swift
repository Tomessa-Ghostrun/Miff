import SwiftUI
import UIKit

// Lit, directement sur la vue de défilement UIKit de la liste du Feed, (1) la distance DÉFILÉE (`contentOffset` + marge haute ajustée), pour
// l'en-tête qui se masque (D41), et (2) l'ORIGINE du contenu : l'endroit, dans la fenêtre, où le contenu commence quand la liste est au repos
// (haut de la vue + marge haute automatique). C'est cette origine, comparée au bas de l'en-tête, qui donne la marge haute de la liste
// (`FeedTopInset`). Si aucune vue de défilement n'est trouvée, rien n'est lu et l'écran garde ses valeurs de repli. Aucune donnée n'est lue ni
// gardée : seulement des nombres de points.

struct ScrollDistanceReader: UIViewRepresentable {
    let onChange: (CGFloat) -> Void
    var onOrigin: (CGFloat) -> Void = { _ in }

    func makeUIView(context: Context) -> ProbeView {
        ProbeView()
    }

    func updateUIView(_ view: ProbeView, context: Context) {
        view.onChange = onChange
        view.onOrigin = onOrigin
    }

    /// Une vue invisible, posée dans le contenu de la liste : elle retrouve la vue de défilement qui la contient et en observe le décalage.
    final class ProbeView: UIView {
        var onChange: ((CGFloat) -> Void)?
        var onOrigin: ((CGFloat) -> Void)?
        private var observation: NSKeyValueObservation?
        private var insetGuard = RefreshInsetGuard()
        private var recheckScheduled = false

        override init(frame: CGRect) {
            super.init(frame: frame)
            isUserInteractionEnabled = false
            isAccessibilityElement = false
            backgroundColor = .clear
        }

        @available(*, unavailable)
        required init?(coder: NSCoder) {
            fatalError("init(coder:) n'est pas utilisé")
        }

        override func didMoveToWindow() {
            super.didMoveToWindow()
            observation = nil
            guard window != nil, let scrollView = enclosingScrollView else {
                return
            }
            observation = scrollView.observe(\.contentOffset, options: [.initial, .new]) { [weak self] scroll, _ in
                self?.report(scroll)
            }
        }

        override func layoutSubviews() {
            super.layoutSubviews()
            if let scroll = enclosingScrollView {
                reportOrigin(scroll)
            }
        }

        private var enclosingScrollView: UIScrollView? {
            var current = superview
            while let view = current {
                if let scroll = view as? UIScrollView {
                    return scroll
                }
                current = view.superview
            }
            return nil
        }

        private func report(_ scroll: UIScrollView) {
            onChange?(scroll.contentOffset.y + scroll.adjustedContentInset.top)
            reportOrigin(scroll)
        }

        /// Où le contenu commence AU REPOS, dans la fenêtre : le haut de la vue de défilement + sa marge haute automatique. Pas pendant
        /// « tirer pour actualiser » ni juste après : UIKit y ajoute la hauteur de la roue à la marge, ce qui ferait SAUTER le contenu
        /// (`RefreshInsetGuard`). La mesure ignorée est reprise quand la roue s'est retirée.
        private func reportOrigin(_ scroll: UIScrollView) {
            let now = ProcessInfo.processInfo.systemUptime
            let refreshing = scroll.refreshControl?.isRefreshing == true
            guard insetGuard.allowsMeasure(refreshing: refreshing, now: now) else {
                scheduleOriginRecheck(now: now)
                return
            }
            let frame = scroll.convert(scroll.bounds, to: nil)
            onOrigin?(frame.minY + scroll.adjustedContentInset.top)
        }

        private func scheduleOriginRecheck(now: TimeInterval) {
            guard !recheckScheduled, let wait = insetGuard.secondsUntilMeasure(now: now) else {
                return
            }
            recheckScheduled = true
            DispatchQueue.main.asyncAfter(deadline: .now() + wait + 0.05) { [weak self] in
                guard let self else {
                    return
                }
                self.recheckScheduled = false
                if let scroll = self.enclosingScrollView {
                    self.reportOrigin(scroll)
                }
            }
        }
    }
}
